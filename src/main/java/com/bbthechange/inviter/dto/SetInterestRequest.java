package com.bbthechange.inviter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for setting user interest level on a hangout.
 */
public class SetInterestRequest {

    @NotBlank(message = "Status is required")
    @Pattern(regexp = "GOING|INTERESTED|NOT_GOING", message = "Status must be GOING, INTERESTED, or NOT_GOING")
    private String status;

    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String notes;

    public SetInterestRequest() {}

    public SetInterestRequest(String status, String notes) {
        this.status = status;
        this.notes = notes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}