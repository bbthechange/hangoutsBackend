package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.NeedsRide;

/**
 * Response DTO for ride requests.
 */
public class NeedsRideDTO {
    private String userId;
    private String displayName;
    private String mainImagePath;
    private String notes;

    public NeedsRideDTO() {}

    public NeedsRideDTO(String userId, String notes) {
        this.userId = userId;
        this.notes = notes;
    }

    public NeedsRideDTO(NeedsRide needsRide) {
        this.userId = needsRide.getUserId();
        this.notes = needsRide.getNotes();
    }

    public NeedsRideDTO(NeedsRide needsRide, String displayName, String mainImagePath) {
        this(needsRide);
        this.displayName = displayName;
        this.mainImagePath = mainImagePath;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getMainImagePath() {
        return mainImagePath;
    }

    public void setMainImagePath(String mainImagePath) {
        this.mainImagePath = mainImagePath;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}