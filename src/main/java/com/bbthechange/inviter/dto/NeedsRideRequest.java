package com.bbthechange.inviter.dto;

import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating or updating a ride request.
 */
public class NeedsRideRequest {
    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String notes;

    public NeedsRideRequest() {}

    public NeedsRideRequest(String notes) {
        this.notes = notes;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}