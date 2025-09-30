package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.HangoutPointer;

/**
 * Data Transfer Object for Hangout summary information in group feeds.
 * Represents a standalone hangout event in the feed.
 */
// TODO @Data?
public class HangoutSummaryDTO implements FeedItem {
    
    private String hangoutId;
    private String title;
    private String status;
    private TimeInfo timeInfo; // Fuzzy time information for display
    private String locationName;
    private int participantCount;
    private String mainImagePath; // Denormalized image path from HangoutPointer
    private String type = "hangout"; // Type discriminator for client-side handling

    public HangoutSummaryDTO(HangoutPointer pointer) {
        this.hangoutId = pointer.getHangoutId();
        this.title = pointer.getTitle();
        this.status = pointer.getStatus();
        this.timeInfo = pointer.getTimeInput(); // Set timeInfo from pointer's timeInput
        this.locationName = pointer.getLocationName();
        this.participantCount = pointer.getParticipantCount();
        this.mainImagePath = pointer.getMainImagePath(); // Get denormalized image path
    }
    
    public String getHangoutId() {
        return hangoutId;
    }
    
    public void setHangoutId(String hangoutId) {
        this.hangoutId = hangoutId;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public TimeInfo getTimeInfo() {
        return timeInfo;
    }
    
    public void setTimeInfo(TimeInfo timeInfo) {
        this.timeInfo = timeInfo;
    }
    
    public String getLocationName() {
        return locationName;
    }
    
    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }
    
    public int getParticipantCount() {
        return participantCount;
    }

    public void setParticipantCount(int participantCount) {
        this.participantCount = participantCount;
    }

    public String getMainImagePath() {
        return mainImagePath;
    }

    public void setMainImagePath(String mainImagePath) {
        this.mainImagePath = mainImagePath;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}