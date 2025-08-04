package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.HangoutPointer;

import java.time.Instant;

/**
 * Data Transfer Object for Hangout summary information in group feeds.
 */
public class HangoutSummaryDTO {
    
    private String hangoutId;
    private String title;
    private String status;
    private Instant hangoutTime;
    private String locationName;
    private int participantCount;
    
    public HangoutSummaryDTO(HangoutPointer pointer) {
        this.hangoutId = pointer.getHangoutId();
        this.title = pointer.getTitle();
        this.status = pointer.getStatus();
        this.hangoutTime = pointer.getHangoutTime();
        this.locationName = pointer.getLocationName();
        this.participantCount = pointer.getParticipantCount();
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
    
    public Instant getHangoutTime() {
        return hangoutTime;
    }
    
    public void setHangoutTime(Instant hangoutTime) {
        this.hangoutTime = hangoutTime;
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
}