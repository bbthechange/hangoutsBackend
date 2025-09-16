package com.bbthechange.inviter.dto;

import java.util.List;

/**
 * Data Transfer Object for Group Feed - shows hangouts organized by status.
 * Enhanced to support chronological ordering with pagination tokens.
 * Now supports both standalone hangouts and multi-part series.
 */
public class GroupFeedDTO {
    
    private String groupId;
    private List<FeedItem> withDay;                // Current/future hangouts and series (chronologically ordered)
    private List<HangoutSummaryDTO> needsDay;      // Hangouts that need scheduling
    private String nextPageToken;                  // For paginating to future events
    private String previousPageToken;              // For paginating to past events
    
    public GroupFeedDTO(String groupId, List<FeedItem> withDay, List<HangoutSummaryDTO> needsDay) {
        this.groupId = groupId;
        this.withDay = withDay != null ? withDay : List.of();
        this.needsDay = needsDay != null ? needsDay : List.of();
    }
    
    public GroupFeedDTO(String groupId, List<FeedItem> withDay, List<HangoutSummaryDTO> needsDay,
                       String nextPageToken, String previousPageToken) {
        this.groupId = groupId;
        this.withDay = withDay != null ? withDay : List.of();
        this.needsDay = needsDay != null ? needsDay : List.of();
        this.nextPageToken = nextPageToken;
        this.previousPageToken = previousPageToken;
    }
    
    public String getGroupId() {
        return groupId;
    }
    
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
    
    public List<FeedItem> getWithDay() {
        return withDay;
    }
    
    public void setWithDay(List<FeedItem> withDay) {
        this.withDay = withDay;
    }
    
    public List<HangoutSummaryDTO> getNeedsDay() {
        return needsDay;
    }
    
    public void setNeedsDay(List<HangoutSummaryDTO> needsDay) {
        this.needsDay = needsDay;
    }
    
    public String getNextPageToken() {
        return nextPageToken;
    }
    
    public void setNextPageToken(String nextPageToken) {
        this.nextPageToken = nextPageToken;
    }
    
    public String getPreviousPageToken() {
        return previousPageToken;
    }
    
    public void setPreviousPageToken(String previousPageToken) {
        this.previousPageToken = previousPageToken;
    }
}