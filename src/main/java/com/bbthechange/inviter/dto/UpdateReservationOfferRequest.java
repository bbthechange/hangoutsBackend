package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.OfferStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating an existing reservation offer.
 * All fields are optional. Null values mean "no change".
 * Service layer will query to get current version for optimistic locking.
 */
public class UpdateReservationOfferRequest {

    private TimeInfo buyDate;       // Optional - null = no change

    @Size(max = 200, message = "Section must not exceed 200 characters")
    private String section;         // Optional - null = no change

    @Min(value = 1, message = "Capacity must be at least 1")
    @Max(value = 1000, message = "Capacity must not exceed 1000")
    private Integer capacity;       // Optional - null = no change

    private OfferStatus status;     // Optional - null = no change

    /**
     * Check if this request contains any actual updates.
     * @return true if at least one field is set to be updated
     */
    public boolean hasUpdates() {
        return buyDate != null || section != null || capacity != null || status != null;
    }

    // Getters and setters

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
