package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.ParticipationType;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating participations via PUT /hangouts/{id}/participations/{participationId}.
 *
 * All fields are optional - null values mean "no change" to that field.
 *
 * Validation rules:
 * - type: Optional - null = no change
 * - section: Optional - null = no change, max 200 characters
 * - seat: Optional - null = no change, max 50 characters
 */
public class UpdateParticipationRequest {

    private ParticipationType type;

    @Size(max = 200, message = "Section must not exceed 200 characters")
    private String section;

    @Size(max = 50, message = "Seat must not exceed 50 characters")
    private String seat;

    // Default constructor for JSON deserialization
    public UpdateParticipationRequest() {}

    public ParticipationType getType() {
        return type;
    }

    public void setType(ParticipationType type) {
        this.type = type;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getSeat() {
        return seat;
    }

    public void setSeat(String seat) {
        this.seat = seat;
    }

    /**
     * Check if this request has any updates.
     */
    public boolean hasUpdates() {
        return type != null || section != null || seat != null;
    }
}
