package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.Address;
import com.bbthechange.inviter.dto.PollOptionDTO;
import com.bbthechange.inviter.dto.PollWithOptionsDTO;
import com.bbthechange.inviter.dto.SuggestedAttributeDTO;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.AttributeSuggestionService;
import com.bbthechange.inviter.service.GroupTimestampService;
import com.bbthechange.inviter.service.MomentumService;
import com.bbthechange.inviter.util.HangoutPointerFactory;
import com.bbthechange.inviter.util.InviterKeyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implements attribute suggestions via the polls system.
 * Suggestion polls are regular polls with a non-null {@code attributeType}.
 */
@Service
public class AttributeSuggestionServiceImpl implements AttributeSuggestionService {

    private static final Logger logger = LoggerFactory.getLogger(AttributeSuggestionServiceImpl.class);
    private static final long TWENTY_FOUR_HOURS_MS = 24 * 60 * 60 * 1000L;

    private final HangoutRepository hangoutRepository;
    private final PointerUpdateService pointerUpdateService;
    private final MomentumService momentumService;
    private final GroupTimestampService groupTimestampService;

    @Autowired
    public AttributeSuggestionServiceImpl(HangoutRepository hangoutRepository,
                                           PointerUpdateService pointerUpdateService,
                                           MomentumService momentumService,
                                           GroupTimestampService groupTimestampService) {
        this.hangoutRepository = hangoutRepository;
        this.pointerUpdateService = pointerUpdateService;
        this.momentumService = momentumService;
        this.groupTimestampService = groupTimestampService;
    }

    @Override
    public Map<String, SuggestedAttributeDTO> computeSuggestedAttributes(Hangout hangout, List<PollWithOptionsDTO> polls) {
        if (polls == null || polls.isEmpty()) {
            return Map.of();
        }

        Map<String, SuggestedAttributeDTO> result = new HashMap<>();
        long now = System.currentTimeMillis();

        for (PollWithOptionsDTO poll : polls) {
            if (poll.getAttributeType() == null || poll.getPromotedAt() != null) {
                continue;
            }

            List<PollOptionDTO> options = poll.getOptions();
            if (options == null || options.isEmpty()) {
                continue;
            }

            // Find the leading option by vote count
            PollOptionDTO leader = options.stream()
                .max(Comparator.comparingInt(PollOptionDTO::getVoteCount))
                .orElse(null);

            if (leader == null) {
                continue;
            }

            // Compute status
            boolean contested = options.size() > 1 && options.stream()
                .filter(o -> !o.getOptionId().equals(leader.getOptionId()))
                .anyMatch(o -> o.getVoteCount() > 0);

            String status;
            if (contested) {
                status = "CONTESTED";
            } else if (poll.getCreatedAtMillis() != null
                    && (now - poll.getCreatedAtMillis()) >= TWENTY_FOUR_HOURS_MS) {
                status = "READY_TO_PROMOTE";
            } else {
                status = "PENDING";
            }

            long createdAt = poll.getCreatedAtMillis() != null ? poll.getCreatedAtMillis() : 0L;

            SuggestedAttributeDTO dto = new SuggestedAttributeDTO(
                poll.getAttributeType(),
                leader.getText(),
                leader.getStructuredValue(),
                leader.getCreatedBy(),
                poll.getPollId(),
                status,
                leader.getVoteCount(),
                createdAt
            );

            result.put(poll.getAttributeType(), dto);
        }

        return result;
    }

    @Override
    public void supersedeSuggestionPolls(String hangoutId, String attributeType) {
        logger.info("Superseding {} suggestion polls for hangout {}", attributeType, hangoutId);

        List<BaseItem> allPollData = hangoutRepository.getAllPollData(hangoutId);

        List<Poll> activePolls = allPollData.stream()
            .filter(item -> InviterKeyFactory.isPollItem(item.getSk()))
            .map(item -> (Poll) item)
            .filter(p -> p.isActive() && attributeType.equals(p.getAttributeType()) && p.getPromotedAt() == null)
            .collect(Collectors.toList());

        for (Poll poll : activePolls) {
            poll.setActive(false);
            hangoutRepository.savePoll(poll);
            logger.info("Superseded suggestion poll {} for hangout {}", poll.getPollId(), hangoutId);
        }

        if (!activePolls.isEmpty()) {
            // Update pointers to reflect deactivated polls
            updatePointersWithPolls(hangoutId, allPollData);
        }
    }

    @Override
    public void promoteEligibleSuggestions() {
        logger.info("Starting attribute suggestion auto-promotion scan");
        long now = System.currentTimeMillis();
        int promoted = 0;

        // Scan for active suggestion polls (DynamoDB scan filtered by itemType + attributeType)
        List<Poll> activeSuggestionPolls = hangoutRepository.findActiveSuggestionPolls();

        // Group by hangout (eventId) to batch queries
        Map<String, List<Poll>> pollsByHangout = activeSuggestionPolls.stream()
            .collect(Collectors.groupingBy(Poll::getEventId));

        for (Map.Entry<String, List<Poll>> entry : pollsByHangout.entrySet()) {
            String hangoutId = entry.getKey();
            List<Poll> suggestionPolls = entry.getValue();

            try {
                Hangout hangout = hangoutRepository.findHangoutById(hangoutId).orElse(null);
                if (hangout == null) {
                    logger.warn("Hangout {} not found for suggestion poll promotion — skipping", hangoutId);
                    continue;
                }

                List<BaseItem> allPollData = hangoutRepository.getAllPollData(hangoutId);

                Map<String, List<PollOption>> optionsByPoll = allPollData.stream()
                    .filter(item -> InviterKeyFactory.isPollOption(item.getSk()))
                    .map(item -> (PollOption) item)
                    .collect(Collectors.groupingBy(PollOption::getPollId));

                Map<String, List<Vote>> votesByPoll = allPollData.stream()
                    .filter(item -> InviterKeyFactory.isVoteItem(item.getSk()))
                    .map(item -> (Vote) item)
                    .collect(Collectors.groupingBy(Vote::getPollId));

                for (Poll poll : suggestionPolls) {
                    java.time.Instant createdAt = poll.getCreatedAt();
                    if (createdAt == null || (now - createdAt.toEpochMilli()) < TWENTY_FOUR_HOURS_MS) {
                        continue; // Not yet past the 24h window
                    }

                    List<PollOption> options = optionsByPoll.getOrDefault(poll.getPollId(), List.of());
                    List<Vote> votes = votesByPoll.getOrDefault(poll.getPollId(), List.of());

                    if (options.isEmpty()) continue;

                    // Check if there's a single unopposed leader
                    if (options.size() == 1) {
                        applyPromotion(hangout, poll, options.get(0));
                        promoted++;
                    } else {
                        // Multiple options — check if only one has votes
                        Map<String, Long> voteCounts = votes.stream()
                            .collect(Collectors.groupingBy(Vote::getOptionId, Collectors.counting()));

                        List<PollOption> optionsWithVotes = options.stream()
                            .filter(o -> voteCounts.getOrDefault(o.getOptionId(), 0L) > 0)
                            .collect(Collectors.toList());

                        if (optionsWithVotes.size() <= 1) {
                            PollOption winner = optionsWithVotes.isEmpty() ? options.get(0) : optionsWithVotes.get(0);
                            applyPromotion(hangout, poll, winner);
                            promoted++;
                        }
                        // Multiple options with votes = contested; skip
                    }
                }
            } catch (Exception e) {
                logger.warn("Error during suggestion promotion for hangout {}: {}",
                    hangoutId, e.getMessage());
            }
        }

        logger.info("Attribute suggestion auto-promotion completed: {} suggestions promoted", promoted);
    }

    private void applyPromotion(Hangout hangout, Poll poll, PollOption winningOption) {
        String hangoutId = hangout.getHangoutId();
        logger.info("Auto-promoting {} suggestion for hangout {}: '{}'",
            poll.getAttributeType(), hangoutId, winningOption.getText());

        // Apply winning value to hangout
        if ("LOCATION".equals(poll.getAttributeType())) {
            Address location = new Address();
            location.setName(winningOption.getText());
            // If structuredValue contains richer data, it could be parsed here
            hangout.setLocation(location);
        } else if ("DESCRIPTION".equals(poll.getAttributeType())) {
            hangout.setDescription(winningOption.getText());
        }

        // Save hangout
        hangoutRepository.createHangout(hangout);

        // Mark poll as promoted and inactive
        poll.setActive(false);
        poll.setPromotedAt(System.currentTimeMillis());
        hangoutRepository.savePoll(poll);

        // Update pointers with new hangout fields
        if (hangout.getAssociatedGroups() != null) {
            for (String groupId : hangout.getAssociatedGroups()) {
                pointerUpdateService.updatePointerWithRetry(groupId, hangoutId,
                    pointer -> HangoutPointerFactory.applyHangoutFields(pointer, hangout),
                    "suggestion promotion");
            }
            groupTimestampService.updateGroupTimestamps(hangout.getAssociatedGroups());
        }

        // Recompute momentum
        try {
            momentumService.recomputeMomentum(hangoutId);
        } catch (Exception e) {
            logger.warn("Failed to recompute momentum after promotion for hangout {}: {}",
                hangoutId, e.getMessage());
        }
    }

    /**
     * Update pointer records with current poll data after supersession.
     */
    private void updatePointersWithPolls(String hangoutId, List<BaseItem> allPollData) {
        Optional<Hangout> hangoutOpt = hangoutRepository.findHangoutById(hangoutId);
        if (hangoutOpt.isEmpty()) return;

        Hangout hangout = hangoutOpt.get();
        List<String> associatedGroups = hangout.getAssociatedGroups();
        if (associatedGroups == null || associatedGroups.isEmpty()) return;

        List<Poll> polls = allPollData.stream()
            .filter(item -> InviterKeyFactory.isPollItem(item.getSk()))
            .map(item -> (Poll) item)
            .collect(Collectors.toList());

        List<PollOption> pollOptions = allPollData.stream()
            .filter(item -> InviterKeyFactory.isPollOption(item.getSk()))
            .map(item -> (PollOption) item)
            .collect(Collectors.toList());

        List<Vote> votes = allPollData.stream()
            .filter(item -> InviterKeyFactory.isVoteItem(item.getSk()))
            .map(item -> (Vote) item)
            .collect(Collectors.toList());

        for (String groupId : associatedGroups) {
            pointerUpdateService.updatePointerWithRetry(groupId, hangoutId, pointer -> {
                pointer.setPolls(new ArrayList<>(polls));
                pointer.setPollOptions(new ArrayList<>(pollOptions));
                pointer.setVotes(new ArrayList<>(votes));
            }, "poll supersession");
        }

        groupTimestampService.updateGroupTimestamps(associatedGroups);
    }
}
