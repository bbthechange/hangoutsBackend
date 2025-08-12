package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.NeedsRide;

/**
 * Response DTO for ride requests.
 */
public class NeedsRideDTO {
    private String userId;
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}