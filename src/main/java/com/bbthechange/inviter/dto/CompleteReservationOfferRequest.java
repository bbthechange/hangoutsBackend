package com.bbthechange.inviter.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request DTO for completing a reservation offer.
 * Marks the offer as completed and optionally converts TICKET_NEEDED participations to TICKET_PURCHASED.
 */
public class CompleteReservationOfferRequest {

    @NotNull(message = "convertAll flag is required")
    private Boolean convertAll;           // Convert all or specific participations?

    private List<String> participationIds;         // Required if convertAll=false

    @Min(value = 0, message = "Ticket count must be at least 0")
    private Integer ticketCount;           // Optional

    @DecimalMin(value = "0.0", message = "Total price must be at least 0.00")
    @Digits(integer = 10, fraction = 2, message = "Total price must have at most 10 digits and 2 decimal places")
    private BigDecimal totalPrice;         // Optional

    // Getters and setters

    public Boolean getConvertAll() {
        return convertAll;
    }

    public void setConvertAll(Boolean convertAll) {
        this.convertAll = convertAll;
    }

    public List<String> getParticipationIds() {
        return participationIds;
    }

    public void setParticipationIds(List<String> participationIds) {
        this.participationIds = participationIds;
    }

    public Integer getTicketCount() {
        return ticketCount;
    }

    public void setTicketCount(Integer ticketCount) {
        this.ticketCount = ticketCount;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }
}
