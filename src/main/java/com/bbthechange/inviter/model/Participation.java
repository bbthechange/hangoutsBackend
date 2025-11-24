package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InviterKeyFactory;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

/**
 * Participation entity for the InviterTable.
 * Represents a user's participation in a hangout regarding tickets or reservations.
 *
 * Key Pattern: PK = HANGOUT#{hangoutId}, SK = PARTICIPATION#{participationId}
 */
@DynamoDbBean
public class Participation extends BaseItem {

    private String participationId;
    private String userId;                  // Who this participation is for
    private ParticipationType type;         // TICKET_NEEDED, TICKET_PURCHASED, TICKET_EXTRA, SECTION, CLAIMED_SPOT
    private String section;                 // Optional - seat section
    private String seat;                    // Optional - specific seat
    private String reservationOfferId;      // Optional - links to ReservationOffer

    // Future expansion fields (documented but NOT implemented yet):
    // private String purchasedBy;          // Who purchased the ticket (for batch purchases)
    // private String ticketHolder;         // For TICKET_EXTRA assigned to someone else

    // Default constructor for DynamoDB
    public Participation() {
        super();
        setItemType(InviterKeyFactory.PARTICIPATION_PREFIX);
    }

    /**
     * Create a new participation record for a hangout.
     */
    public Participation(String hangoutId, String participationId, String userId, ParticipationType type) {
        super();
        setItemType(InviterKeyFactory.PARTICIPATION_PREFIX);
        this.participationId = participationId;
        this.userId = userId;
        this.type = type;

        // Set keys using InviterKeyFactory
        setPk(InviterKeyFactory.getEventPk(hangoutId));
        setSk(InviterKeyFactory.getParticipationSk(participationId));
    }

    public String getParticipationId() {
        return participationId;
    }

    public void setParticipationId(String participationId) {
        this.participationId = participationId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public ParticipationType getType() {
        return type;
    }

    public void setType(ParticipationType type) {
        this.type = type;
        touch();
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
        touch();
    }

    public String getSeat() {
        return seat;
    }

    public void setSeat(String seat) {
        this.seat = seat;
        touch();
    }

    public String getReservationOfferId() {
        return reservationOfferId;
    }

    public void setReservationOfferId(String reservationOfferId) {
        this.reservationOfferId = reservationOfferId;
        touch();
    }
}
