package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.ParticipationType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating new participations via POST /hangouts/{id}/participations.
 *
 * Validation rules:
 * - type: Required (TICKET_NEEDED, TICKET_PURCHASED, TICKET_EXTRA, SECTION, CLAIMED_SPOT)
 * - section: Optional, max 200 characters
 * - seat: Optional, max 50 characters
 * - reservationOfferId: Optional, links to a reservation offer
 */
public class CreateParticipationRequest {

    @NotNull(message = "Participation type is required")
    private ParticipationType type;

    @Size(max = 200, message = "Section must not exceed 200 characters")
    private String section;

    @Size(max = 50, message = "Seat must not exceed 50 characters")
    private String seat;

    private String reservationOfferId;

    // Default constructor for JSON deserialization
    public CreateParticipationRequest() {}

    /**
     * Constructor for creating request objects.
     */
    public CreateParticipationRequest(ParticipationType type) {
        this.type = type;
    }

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

    public String getReservationOfferId() {
        return reservationOfferId;
    }

    public void setReservationOfferId(String reservationOfferId) {
        this.reservationOfferId = reservationOfferId;
    }
}
