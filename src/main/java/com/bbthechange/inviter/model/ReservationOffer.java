package com.bbthechange.inviter.model;

import com.bbthechange.inviter.dto.TimeInfo;
import com.bbthechange.inviter.util.InviterKeyFactory;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbVersionAttribute;

import java.math.BigDecimal;

/**
 * ReservationOffer entity for the InviterTable.
 * Represents an offer to purchase tickets or make reservations for a hangout.
 *
 * Key Pattern: PK = HANGOUT#{hangoutId}, SK = RESERVEOFFER#{offerId}
 */
@DynamoDbBean
public class ReservationOffer extends BaseItem {

    private String offerId;
    private String userId;                  // Offer creator
    private OfferType type;                 // TICKET or RESERVATION
    private TimeInfo buyDate;               // Optional - deadline for collecting commitments
    private String section;                 // Optional - seat section
    private OfferStatus status;             // COLLECTING (default), COMPLETED, CANCELLED
    private Integer capacity;               // Optional - max spots (null = unlimited)
    private Integer claimedSpots;           // Current claimed count (default 0)
    private Long version;                   // Optimistic locking

    // Completion fields (set when status=COMPLETED)
    private Long completedDate;             // When offer was completed
    private Integer ticketCount;            // Number of tickets purchased
    private BigDecimal totalPrice;          // Total cost

    // Default constructor for DynamoDB
    public ReservationOffer() {
        super();
        setItemType(InviterKeyFactory.RESERVEOFFER_PREFIX);
        this.claimedSpots = 0;
        // version is null for new items - DynamoDB Enhanced Client will set it to 1 on first save
        this.status = OfferStatus.COLLECTING;
    }

    /**
     * Create a new reservation offer for a hangout.
     */
    public ReservationOffer(String hangoutId, String offerId, String userId, OfferType type) {
        super();
        setItemType(InviterKeyFactory.RESERVEOFFER_PREFIX);
        this.offerId = offerId;
        this.userId = userId;
        this.type = type;
        this.claimedSpots = 0;
        // version is null for new items - DynamoDB Enhanced Client will set it to 1 on first save
        this.status = OfferStatus.COLLECTING;

        // Set keys using InviterKeyFactory
        setPk(InviterKeyFactory.getEventPk(hangoutId));
        setSk(InviterKeyFactory.getReservationOfferSk(offerId));
    }

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

    public OfferType getType() {
        return type;
    }

    public void setType(OfferType type) {
        this.type = type;
        touch();
    }

    public TimeInfo getBuyDate() {
        return buyDate;
    }

    public void setBuyDate(TimeInfo buyDate) {
        this.buyDate = buyDate;
        touch();
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
        touch();
    }

    public OfferStatus getStatus() {
        return status;
    }

    public void setStatus(OfferStatus status) {
        this.status = status;
        touch();
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
        touch();
    }

    public Integer getClaimedSpots() {
        return claimedSpots;
    }

    public void setClaimedSpots(Integer claimedSpots) {
        this.claimedSpots = claimedSpots;
        touch();
    }

    @DynamoDbVersionAttribute
    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Long getCompletedDate() {
        return completedDate;
    }

    public void setCompletedDate(Long completedDate) {
        this.completedDate = completedDate;
        touch();
    }

    public Integer getTicketCount() {
        return ticketCount;
    }

    public void setTicketCount(Integer ticketCount) {
        this.ticketCount = ticketCount;
        touch();
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
        touch();
    }

    /**
     * Check if the offer has available capacity.
     * Returns true if capacity is unlimited (null) or if there are remaining spots.
     */
    public boolean hasAvailableCapacity() {
        return capacity == null || claimedSpots < capacity;
    }

    /**
     * Get remaining spots (null if unlimited capacity).
     */
    public Integer getRemainingSpots() {
        return capacity != null ? capacity - claimedSpots : null;
    }
}
