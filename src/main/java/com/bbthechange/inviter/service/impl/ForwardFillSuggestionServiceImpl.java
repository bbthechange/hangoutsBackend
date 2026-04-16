package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.config.MomentumTuningProperties;
import com.bbthechange.inviter.dto.HangoutSummaryDTO;
import com.bbthechange.inviter.dto.IdeaDTO;
import com.bbthechange.inviter.dto.IdeaFeedItemDTO;
import com.bbthechange.inviter.dto.IdeaListDTO;
import com.bbthechange.inviter.service.ForwardFillSuggestionService;
import com.bbthechange.inviter.service.IdeaListService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Fills empty forward weeks with stale floats first, then supported ideas, then
 * unsupported ideas. Budget is the number of empty ISO weeks in the next
 * {@link MomentumTuningProperties#getForwardWeeksToFill()} weeks.
 *
 * <p>Replaces the old "all-or-nothing over 3 weeks" idea-surfacing behavior.
 */
@Service
public class ForwardFillSuggestionServiceImpl implements ForwardFillSuggestionService {

    private static final Logger logger = LoggerFactory.getLogger(ForwardFillSuggestionServiceImpl.class);

    public static final String REASON_SUPPORTED_IDEA   = "SUPPORTED_IDEA";
    public static final String REASON_UNSUPPORTED_IDEA = "UNSUPPORTED_IDEA";

    private final WeekCoverageCalculator weekCoverageCalculator;
    private final IdeaListService ideaListService;
    private final MomentumTuningProperties tuning;

    public ForwardFillSuggestionServiceImpl(WeekCoverageCalculator weekCoverageCalculator,
                                            IdeaListService ideaListService,
                                            MomentumTuningProperties tuning) {
        this.weekCoverageCalculator = weekCoverageCalculator;
        this.ideaListService = ideaListService;
        this.tuning = tuning;
    }

    @Override
    public ForwardFillResult getForwardFill(String groupId,
                                            long nowTimestamp,
                                            String requestingUserId,
                                            List<HangoutSummaryDTO> heldStaleFloats) {

        int emptyWeeks;
        try {
            emptyWeeks = weekCoverageCalculator.countEmptyWeeks(groupId, nowTimestamp);
        } catch (Exception e) {
            logger.warn("Failed to compute week coverage for group {}; skipping forward-fill", groupId, e);
            return ForwardFillResult.empty();
        }

        if (emptyWeeks <= 0) {
            return ForwardFillResult.empty();
        }

        int budget = emptyWeeks;

        // Priority 1: stale floats, sorted by interestLevels.size() desc, createdAt desc.
        List<HangoutSummaryDTO> sortedStale = new ArrayList<>(
                heldStaleFloats == null ? List.of() : heldStaleFloats);
        sortedStale.sort(Comparator
                .comparingInt((HangoutSummaryDTO h) -> sizeOf(h.getInterestLevels())).reversed()
                .thenComparing(Comparator.comparingLong(
                        (HangoutSummaryDTO h) -> h.getCreatedAt() != null ? h.getCreatedAt() : 0L).reversed()));

        List<HangoutSummaryDTO> chosenStale = new ArrayList<>();
        for (HangoutSummaryDTO h : sortedStale) {
            if (budget == 0) break;
            h.setSurfaceReason(FeedSortingService.REASON_STALE_FILLER);
            chosenStale.add(h);
            budget--;
        }

        // Priority 2 & 3: ideas.
        List<IdeaFeedItemDTO> ideas;
        if (budget == 0) {
            ideas = List.of();
        } else {
            ideas = fetchIdeas(groupId, requestingUserId, budget);
        }

        return new ForwardFillResult(chosenStale, ideas);
    }

    private List<IdeaFeedItemDTO> fetchIdeas(String groupId,
                                             String requestingUserId,
                                             int budget) {
        List<IdeaListDTO> lists;
        try {
            lists = ideaListService.getIdeaListsForGroup(groupId, requestingUserId);
        } catch (Exception e) {
            logger.warn("Failed to fetch idea lists for group {} during forward-fill", groupId, e);
            return List.of();
        }

        List<IdeaWithList> supported   = new ArrayList<>();
        List<IdeaWithList> unsupported = new ArrayList<>();

        int minSupported = tuning.getIdeaMinInterestCount();
        for (IdeaListDTO list : lists) {
            if (list.getIdeas() == null) continue;
            for (IdeaDTO idea : list.getIdeas()) {
                if (idea.getInterestCount() >= minSupported) {
                    supported.add(new IdeaWithList(idea, list));
                } else if (idea.getInterestCount() > 0) {
                    unsupported.add(new IdeaWithList(idea, list));
                }
                // interestCount == 0 ideas are intentionally excluded — noise.
            }
        }

        Comparator<IdeaWithList> byInterestDesc =
                Comparator.comparingInt((IdeaWithList p) -> p.idea.getInterestCount()).reversed();
        supported.sort(byInterestDesc);
        unsupported.sort(byInterestDesc);

        List<IdeaFeedItemDTO> out = new ArrayList<>();
        int remaining = budget;
        for (IdeaWithList p : supported) {
            if (remaining == 0) break;
            out.add(toFeedItem(p.idea, p.list, groupId, REASON_SUPPORTED_IDEA));
            remaining--;
        }
        for (IdeaWithList p : unsupported) {
            if (remaining == 0) break;
            out.add(toFeedItem(p.idea, p.list, groupId, REASON_UNSUPPORTED_IDEA));
            remaining--;
        }
        return out;
    }

    private record IdeaWithList(IdeaDTO idea, IdeaListDTO list) {}

    private static int sizeOf(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private IdeaFeedItemDTO toFeedItem(IdeaDTO idea, IdeaListDTO list, String groupId, String reason) {
        IdeaFeedItemDTO dto = new IdeaFeedItemDTO(
                idea.getId(),
                list.getId(),
                groupId,
                idea.getName(),
                list.getName(),
                idea.getImageUrl(),
                idea.getNote(),
                idea.getInterestCount(),
                idea.getGooglePlaceId(),
                idea.getAddress(),
                idea.getLatitude(),
                idea.getLongitude(),
                idea.getPlaceCategory()
        );
        dto.setSurfaceReason(reason);
        return dto;
    }
}
