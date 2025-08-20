package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.GroupFeedItemsResponse;

/**
 * Service interface for group feed operations.
 * Provides polymorphic feed items aggregated from upcoming events within a group.
 */
public interface GroupFeedService {
    
    /**
     * Get paginated polymorphic feed items for a group.
     * Aggregates actionable items (polls, undecided attributes) from upcoming events.
     * 
     * Uses backend loop aggregator pattern - loops through upcoming events to find
     * enough content to fill a page. Returns all items found in the final batch of events
     * processed, which may be slightly more than the requested limit.
     * 
     * @param groupId The group ID to get feed items for
     * @param limit Target number of feed items to return (1-50)
     * @param startToken Opaque pagination token from previous response (null for first page)
     * @param requestingUserId User making the request (must be group member)
     * @return Paginated feed items response with polymorphic items and next page token
     */
    GroupFeedItemsResponse getFeedItems(String groupId, Integer limit, String startToken, String requestingUserId);
}