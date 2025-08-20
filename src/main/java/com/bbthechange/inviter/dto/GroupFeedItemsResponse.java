package com.bbthechange.inviter.dto;

import java.util.List;

/**
 * Response DTO for the group feed items API.
 * Contains paginated list of polymorphic feed items and pagination token.
 */
public class GroupFeedItemsResponse {
    
    private List<FeedItemDTO> items;
    private String nextPageToken;
    
    public GroupFeedItemsResponse() {}
    
    public GroupFeedItemsResponse(List<FeedItemDTO> items, String nextPageToken) {
        this.items = items;
        this.nextPageToken = nextPageToken;
    }
    
    public List<FeedItemDTO> getItems() {
        return items;
    }
    
    public void setItems(List<FeedItemDTO> items) {
        this.items = items;
    }
    
    public String getNextPageToken() {
        return nextPageToken;
    }
    
    public void setNextPageToken(String nextPageToken) {
        this.nextPageToken = nextPageToken;
    }
}