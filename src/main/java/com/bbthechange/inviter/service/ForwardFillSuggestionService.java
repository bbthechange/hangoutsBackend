package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.HangoutSummaryDTO;
import com.bbthechange.inviter.dto.IdeaFeedItemDTO;

import java.util.List;

/**
 * Service for filling gaps in the group's forward schedule.
 *
 * <p>Counts empty ISO weeks in the next {@code forwardWeeksToFill} and fills them
 * in priority order:
 * <ol>
 *   <li>Stale BUILDING floats held back by the feed sort (fade candidates).</li>
 *   <li>Ideas with {@code interestCount >= ideaMinInterestCount} (supported).</li>
 *   <li>Remaining ideas with {@code interestCount > 0} (last-resort fallback).</li>
 * </ol>
 *
 * <p>The result contains both stale floats (appended to {@code needsDay}) and ideas
 * (appended to {@code withDay}), so the caller integrates them into the right parts
 * of the feed response.
 */
public interface ForwardFillSuggestionService {

    /**
     * Compute the forward-fill result for a group.
     *
     * @param groupId               the group being rendered
     * @param nowTimestamp          Unix seconds representing "now"
     * @param requestingUserId      user ID of the requesting user (already verified as group member)
     * @param heldStaleFloats       stale unsupported floats held back by {@code FeedSortingService}
     * @param freshFloatCount       number of <em>dateless</em> FRESH_FLOAT hangouts already
     *                              surfacing in the feed ({@code needsDay}). Deducted from the
     *                              empty-week budget because they can't cover a specific week
     *                              via {@code WeekCoverageCalculator} but still occupy a
     *                              suggestion slot in the user's view. Dated fresh BUILDING
     *                              items must NOT be counted here — their week is already
     *                              covered and counting them again would double-deduct.
     * @return stale floats and ideas to append to the feed (possibly empty)
     */
    ForwardFillResult getForwardFill(String groupId,
                                     long nowTimestamp,
                                     String requestingUserId,
                                     List<HangoutSummaryDTO> heldStaleFloats,
                                     int freshFloatCount);

    /** Result bundle for {@link #getForwardFill}. */
    final class ForwardFillResult {
        private final List<HangoutSummaryDTO> staleFloats;
        private final List<IdeaFeedItemDTO> ideas;

        public ForwardFillResult(List<HangoutSummaryDTO> staleFloats, List<IdeaFeedItemDTO> ideas) {
            this.staleFloats = staleFloats;
            this.ideas = ideas;
        }

        public List<HangoutSummaryDTO> getStaleFloats() { return staleFloats; }
        public List<IdeaFeedItemDTO> getIdeas() { return ideas; }

        public static ForwardFillResult empty() {
            return new ForwardFillResult(List.of(), List.of());
        }
    }
}
