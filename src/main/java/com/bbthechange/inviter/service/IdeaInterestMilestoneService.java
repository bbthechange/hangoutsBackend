package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.UserSummaryDTO;
import com.bbthechange.inviter.model.GroupMembership;
import com.bbthechange.inviter.model.IdeaListMember;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.IdeaListRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Detects interest milestones on ideas and triggers notifications.
 *
 * Milestones:
 * - FIRST_INTEREST: interestCount >= 2 → notify idea adder
 * - BROAD_INTEREST: interestCount >= 3 → notify all group members (adder gets special copy)
 * - GROUP_CONSENSUS: interestCount >= ceil(groupSize * 0.5) AND >= 4 → always fires
 *
 * Each milestone is one-shot per idea (tracked via lastMilestoneSent field).
 * Deduplication uses conditional DynamoDB updates to prevent race conditions.
 */
@Service
public class IdeaInterestMilestoneService {

    private static final Logger logger = LoggerFactory.getLogger(IdeaInterestMilestoneService.class);

    public static final String SIGNAL_IDEA_FIRST_INTEREST = "IDEA_FIRST_INTEREST";
    public static final String SIGNAL_IDEA_BROAD_INTEREST = "IDEA_BROAD_INTEREST";

    private final GroupRepository groupRepository;
    private final IdeaListRepository ideaListRepository;
    private final NotificationService notificationService;
    private final AdaptiveNotificationService adaptiveNotificationService;
    private final UserService userService;
    private final NotificationTextGenerator textGenerator;
    private final MeterRegistry meterRegistry;

    @Autowired
    public IdeaInterestMilestoneService(GroupRepository groupRepository,
                                         IdeaListRepository ideaListRepository,
                                         NotificationService notificationService,
                                         AdaptiveNotificationService adaptiveNotificationService,
                                         UserService userService,
                                         NotificationTextGenerator textGenerator,
                                         MeterRegistry meterRegistry) {
        this.groupRepository = groupRepository;
        this.ideaListRepository = ideaListRepository;
        this.notificationService = notificationService;
        this.adaptiveNotificationService = adaptiveNotificationService;
        this.userService = userService;
        this.textGenerator = textGenerator;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Check whether an interest milestone was reached and send notifications if so.
     * Called from IdeaListServiceImpl.addIdeaInterest() after the interest is recorded.
     *
     * Fire-and-forget: failures are logged but never propagated.
     */
    public void checkAndNotify(String groupId, String listId,
                                IdeaListMember idea, String triggeringUserId) {
        try {
            int interestCount = computeInterestCount(idea);
            List<GroupMembership> members = groupRepository.findMembersByGroupId(groupId);
            int groupSize = members.size();

            String milestone = determineHighestReachedMilestone(interestCount, groupSize);
            if (milestone == null) return;

            // Check if this milestone (or higher) was already sent
            String currentMilestone = idea.getLastMilestoneSent();
            if (currentMilestone != null && milestoneRank(milestone) <= milestoneRank(currentMilestone)) {
                return;
            }

            // For FIRST_INTEREST, skip if the adder is the triggering user (no one to notify).
            // Don't claim the milestone so the next external interest can trigger it.
            if ("FIRST_INTEREST".equals(milestone)
                    && idea.getAddedBy() != null
                    && idea.getAddedBy().equals(triggeringUserId)) {
                return;
            }

            // Budget check for non-consensus milestones
            String signalType = milestoneToSignal(milestone);
            if (!"GROUP_CONSENSUS".equals(milestone)) {
                boolean hasBudget = adaptiveNotificationService.hasBudgetForNotification(groupId, signalType);
                if (!hasBudget) {
                    logger.debug("Budget exhausted for milestone {} on idea {} in group {}",
                            milestone, idea.getIdeaId(), groupId);
                    meterRegistry.counter("idea_interest_milestone_total",
                            "status", "suppressed_budget", "milestone", milestone).increment();
                    return;
                }
            }

            // Conditional update to claim this milestone (prevents concurrent double-fire)
            boolean claimed = ideaListRepository.updateLastMilestoneSentConditionally(
                    groupId, listId, idea.getIdeaId(), milestone, currentMilestone);
            if (!claimed) {
                logger.debug("Lost race to claim milestone {} on idea {}", milestone, idea.getIdeaId());
                meterRegistry.counter("idea_interest_milestone_total",
                        "status", "suppressed_duplicate", "milestone", milestone).increment();
                return;
            }

            // Send milestone notification (pass members to avoid re-fetching)
            sendMilestoneNotification(groupId, listId, idea, milestone,
                    interestCount, triggeringUserId, members);

            meterRegistry.counter("idea_interest_milestone_total",
                    "status", "sent", "milestone", milestone).increment();

        } catch (Exception e) {
            logger.warn("Error checking interest milestones for idea {} in group {}",
                    idea.getIdeaId(), groupId, e);
            meterRegistry.counter("idea_interest_milestone_total",
                    "status", "error").increment();
        }
    }

    int computeInterestCount(IdeaListMember idea) {
        Set<String> ids = idea.getInterestedUserIds();
        int explicit = (ids != null) ? ids.size() : 0;
        if (idea.getAddedBy() == null) return explicit;
        boolean creatorInSet = ids != null && ids.contains(idea.getAddedBy());
        return explicit + (creatorInSet ? 0 : 1);
    }

    String determineHighestReachedMilestone(int interestCount, int groupSize) {
        int consensusThreshold = Math.max(4, (int) Math.ceil(groupSize * 0.5));
        if (interestCount >= consensusThreshold) return "GROUP_CONSENSUS";
        if (interestCount >= 3) return "BROAD_INTEREST";
        if (interestCount >= 2) return "FIRST_INTEREST";
        return null;
    }

    private int milestoneRank(String milestone) {
        return switch (milestone) {
            case "FIRST_INTEREST" -> 1;
            case "BROAD_INTEREST" -> 2;
            case "GROUP_CONSENSUS" -> 3;
            default -> 0;
        };
    }

    private String milestoneToSignal(String milestone) {
        return switch (milestone) {
            case "FIRST_INTEREST" -> SIGNAL_IDEA_FIRST_INTEREST;
            case "BROAD_INTEREST" -> SIGNAL_IDEA_BROAD_INTEREST;
            default -> "IDEA_MILESTONE";
        };
    }

    private void sendMilestoneNotification(String groupId, String listId,
                                            IdeaListMember idea, String milestone,
                                            int interestCount, String triggeringUserId,
                                            List<GroupMembership> members) {
        String ideaName = idea.getName();
        String triggeringUserName = resolveDisplayName(triggeringUserId);

        switch (milestone) {
            case "FIRST_INTEREST" -> {
                // Notify idea adder only (unless the adder is the triggering user)
                String adderId = idea.getAddedBy();
                if (adderId != null && !adderId.equals(triggeringUserId)) {
                    String body = textGenerator.getFirstInterestBody(triggeringUserName, ideaName);
                    notificationService.notifyIdeaInterestMilestone(
                            groupId, listId, idea.getIdeaId(),
                            Set.of(adderId), body);
                }
            }
            case "BROAD_INTEREST" -> {
                Set<String> allMemberIds = members.stream()
                        .map(GroupMembership::getUserId)
                        .collect(Collectors.toSet());
                allMemberIds.remove(triggeringUserId);

                String adderId = idea.getAddedBy();

                // Send to adder with special copy
                if (adderId != null && allMemberIds.remove(adderId)) {
                    String adderBody = textGenerator.getBroadInterestAdderBody(ideaName, interestCount);
                    notificationService.notifyIdeaInterestMilestone(
                            groupId, listId, idea.getIdeaId(),
                            Set.of(adderId), adderBody);
                }

                // Send to everyone else
                if (!allMemberIds.isEmpty()) {
                    String body = textGenerator.getBroadInterestBody(ideaName, interestCount);
                    notificationService.notifyIdeaInterestMilestone(
                            groupId, listId, idea.getIdeaId(),
                            allMemberIds, body);
                }
            }
            case "GROUP_CONSENSUS" -> {
                Set<String> recipientIds = members.stream()
                        .map(GroupMembership::getUserId)
                        .collect(Collectors.toSet());
                recipientIds.remove(triggeringUserId);

                if (!recipientIds.isEmpty()) {
                    String body = textGenerator.getGroupConsensusBody(ideaName);
                    notificationService.notifyIdeaInterestMilestone(
                            groupId, listId, idea.getIdeaId(),
                            recipientIds, body);
                }
            }
        }
    }

    private String resolveDisplayName(String userId) {
        try {
            Optional<UserSummaryDTO> summary = userService.getUserSummary(UUID.fromString(userId));
            return summary.map(UserSummaryDTO::getDisplayName).orElse("Someone");
        } catch (Exception e) {
            logger.warn("Failed to resolve display name for user {}", userId, e);
            return "Someone";
        }
    }
}
