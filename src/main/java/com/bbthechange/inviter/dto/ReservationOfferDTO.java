package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.OfferStatus;
import com.bbthechange.inviter.model.OfferType;
import com.bbthechange.inviter.model.ReservationOffer;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO for reservation offer responses with denormalized user information.
 * Used in API responses and HangoutPointer denormalization.
 */
@DynamoDbBean
public class ReservationOfferDTO {

    private String offerId;
    private String userId;
    private String displayName;              // Denormalized from User
    private String mainImagePath;            // Denormalized from User
    private OfferType type;
    private TimeInfo buyDate;
    private String section;
    private Integer capacity;
    private Integer claimedSpots;
    private Integer remainingSpots;          // Calculated: capacity - claimedSpots (null if unlimited)
    private OfferStatus status;
    private Long completedDate;
    private Integer ticketCount;
    private BigDecimal totalPrice;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    // Default constructor for JSON deserialization
    public ReservationOfferDTO() {
    }

    /**
     * Constructor for creating DTO from entity with denormalized user info.
     */
    public ReservationOfferDTO(ReservationOffer offer, String displayName, String mainImagePath) {
        this.offerId = offer.getOfferId();
        this.userId = offer.getUserId();
        this.displayName = displayName;
        this.mainImagePath = mainImagePath;
        this.type = offer.getType();
        this.buyDate = offer.getBuyDate();
        this.section = offer.getSection();
        this.capacity = offer.getCapacity();
        this.claimedSpots = offer.getClaimedSpots();

        // Calculate remainingSpots (null if unlimited capacity)
        if (offer.getCapacity() != null) {
            this.remainingSpots = offer.getCapacity() - offer.getClaimedSpots();
        } else {
            this.remainingSpots = null;  // Unlimited capacity
        }

        this.status = offer.getStatus();
        this.completedDate = offer.getCompletedDate();
        this.ticketCount = offer.getTicketCount();
        this.totalPrice = offer.getTotalPrice();
        this.version = offer.getVersion();

        // BaseItem already returns Instant
        this.createdAt = offer.getCreatedAt();
        this.updatedAt = offer.getUpdatedAt();
    }

    // Getters and setters

    public String getOfferId() {
        return offerId;
    }

    public void setOfferId(String offerId) {
        this.offerId = offerId;
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

    public Integer getClaimedSpots() {
        return claimedSpots;
    }

    public void setClaimedSpots(Integer claimedSpots) {
        this.claimedSpots = claimedSpots;
    }

    public Integer getRemainingSpots() {
        return remainingSpots;
    }

    public void setRemainingSpots(Integer remainingSpots) {
        this.remainingSpots = remainingSpots;
    }

    public OfferStatus getStatus() {
        return status;
    }

    public void setStatus(OfferStatus status) {
        this.status = status;
    }

    public Long getCompletedDate() {
        return completedDate;
    }

    public void setCompletedDate(Long completedDate) {
        this.completedDate = completedDate;
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
