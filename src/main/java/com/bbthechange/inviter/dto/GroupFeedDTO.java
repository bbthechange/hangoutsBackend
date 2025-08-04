package com.bbthechange.inviter.dto;

import java.util.List;

/**
 * Data Transfer Object for Group Feed - shows hangouts organized by status.
 */
public class GroupFeedDTO {
    
    private String groupId;
    private List<HangoutSummaryDTO> withDay;       // Hangouts with scheduled time
    private List<HangoutSummaryDTO> needsDay;      // Hangouts that need scheduling
    
    public GroupFeedDTO(String groupId, List<HangoutSummaryDTO> withDay, List<HangoutSummaryDTO> needsDay) {
        this.groupId = groupId;
        this.withDay = withDay != null ? withDay : List.of();
        this.needsDay = needsDay != null ? needsDay : List.of();
    }
    
    public String getGroupId() {
        return groupId;
    }
    
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
    
    public List<HangoutSummaryDTO> getWithDay() {
        return withDay;
    }
    
    public void setWithDay(List<HangoutSummaryDTO> withDay) {
        this.withDay = withDay;
    }
    
    public List<HangoutSummaryDTO> getNeedsDay() {
        return needsDay;
    }
    
    public void setNeedsDay(List<HangoutSummaryDTO> needsDay) {
        this.needsDay = needsDay;
    }
}