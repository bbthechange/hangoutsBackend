package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.IdeaFeedItemDTO;

import java.util.List;

/**
 * Service for surfacing high-interest ideas from idea lists into the group feed.
 *
 * <p>Ideas with interestCount >= 3 are candidates for surfacing. Surfacing is
 * suppressed for a given week when the group already has at least one confirmed
 * hangout scheduled in that week. If all 3 upcoming weeks are covered, no ideas
 * are surfaced.
 */
public interface IdeaFeedSurfacingService {

    /**
     * Get high-interest ideas that should appear in the group feed.
     *
     * <p>Returns ideas with interestCount >= 3, sorted by interestCount descending,
     * unless suppression applies (group has confirmed hangouts covering all 3 upcoming weeks).
     *
     * @param groupId          the group whose idea lists to scan
     * @param nowTimestamp     Unix timestamp (seconds) representing "now"
     * @param requestingUserId user ID of the requesting user (already verified as group member)
     * @return list of idea feed items to inject into the feed, possibly empty
     */
    List<IdeaFeedItemDTO> getSurfacedIdeas(String groupId, long nowTimestamp, String requestingUserId);
}
