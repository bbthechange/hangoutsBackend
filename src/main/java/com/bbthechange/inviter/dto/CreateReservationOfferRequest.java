package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.OfferStatus;
import com.bbthechange.inviter.model.OfferType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new reservation offer.
 * Only required field is type - all others are optional.
 */
public class CreateReservationOfferRequest {

    @NotNull(message = "Offer type is required")
    private OfferType type;         // TICKET or RESERVATION

    private TimeInfo buyDate;       // Optional - deadline for collecting commitments

    @Size(max = 200, message = "Section must not exceed 200 characters")
    private String section;         // Optional

    @Min(value = 1, message = "Capacity must be at least 1")
    @Max(value = 1000, message = "Capacity must not exceed 1000")
    private Integer capacity;       // Optional, null = unlimited

    private OfferStatus status;     // Optional, defaults to COLLECTING if not specified

    // Getters and setters

    public OfferType getType() {
        return type;
    }

    public void setType(OfferType type) {
        this.type = type;
    }

    public TimeInfo getBuyDate() {
        return buyDate;
    }

    public void setBuyDate(TimeInfo buyDate) {
        this.buyDate = buyDate;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public OfferStatus getStatus() {
        return status;
    }

    public void setStatus(OfferStatus status) {
        this.status = status;
    }
}
