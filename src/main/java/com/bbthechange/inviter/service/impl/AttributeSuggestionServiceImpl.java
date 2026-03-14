package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.PollOptionDTO;
import com.bbthechange.inviter.dto.PollWithOptionsDTO;
import com.bbthechange.inviter.dto.SuggestedAttributeDTO;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.AttributeSuggestionService;
import com.bbthechange.inviter.service.GroupTimestampService;
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
    private final GroupTimestampService groupTimestampService;

    @Autowired
    public AttributeSuggestionServiceImpl(HangoutRepository hangoutRepository,
                                           PointerUpdateService pointerUpdateService,
                                           GroupTimestampService groupTimestampService) {
        this.hangoutRepository = hangoutRepository;
        this.pointerUpdateService = pointerUpdateService;
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

            String status = computeSuggestionStatus(poll, now);

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

    /**
     * Compute the status of a suggestion poll: PENDING, CONTESTED, or READY_TO_PROMOTE.
     * Used by both suggestion computation and nudge filtering.
     */
    public static String computeSuggestionStatus(PollWithOptionsDTO poll, long nowMillis) {
        List<PollOptionDTO> options = poll.getOptions();
        if (options == null || options.isEmpty()) {
            return "PENDING";
        }

        boolean contested = isContested(options);
        if (contested) {
            return "CONTESTED";
        } else if (poll.getCreatedAtMillis() != null
                && (nowMillis - poll.getCreatedAtMillis()) >= TWENTY_FOUR_HOURS_MS) {
            return "READY_TO_PROMOTE";
        }
        return "PENDING";
    }

    /**
     * Check if a suggestion poll has been resolved (ready to promote) — i.e., past 24h with no opposition.
     */
    public static boolean isReadyToPromote(PollWithOptionsDTO poll, long nowMillis) {
        return "READY_TO_PROMOTE".equals(computeSuggestionStatus(poll, nowMillis));
    }

    private static boolean isContested(List<PollOptionDTO> options) {
        if (options.size() <= 1) return false;
        PollOptionDTO leader = options.stream()
            .max(Comparator.comparingInt(PollOptionDTO::getVoteCount))
            .orElse(null);
        if (leader == null) return false;
        return options.stream()
            .filter(o -> !o.getOptionId().equals(leader.getOptionId()))
            .anyMatch(o -> o.getVoteCount() > 0);
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
