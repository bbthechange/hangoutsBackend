package com.bbthechange.inviter.dto;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores grouped participation data in HangoutPointer for efficient group feed rendering.
 * NOT the same as ParticipationDTO (which is for API responses).
 * This DTO groups users by participation type instead of listing individual participations.
 */
@DynamoDbBean
public class ParticipationSummaryDTO {

    // Grouped user lists by participation type
    private List<UserSummary> usersNeedingTickets;     // type=TICKET_NEEDED
    private List<UserSummary> usersWithTickets;        // type=TICKET_PURCHASED
    private List<UserSummary> usersWithClaimedSpots;   // type=CLAIMED_SPOT

    // Count for extras (no user list needed)
    private Integer extraTicketCount;                  // count of TICKET_EXTRA

    // All reservation offers (not filtered by status)
    private List<ReservationOfferDTO> reservationOffers;

    // Default constructor for DynamoDB
    public ParticipationSummaryDTO() {
        this.usersNeedingTickets = new ArrayList<>();
        this.usersWithTickets = new ArrayList<>();
        this.usersWithClaimedSpots = new ArrayList<>();
        this.extraTicketCount = 0;
        this.reservationOffers = new ArrayList<>();
    }

    // Getters and setters

    public List<UserSummary> getUsersNeedingTickets() {
        return usersNeedingTickets != null ? usersNeedingTickets : new ArrayList<>();
    }

    public void setUsersNeedingTickets(List<UserSummary> usersNeedingTickets) {
        this.usersNeedingTickets = usersNeedingTickets != null ? usersNeedingTickets : new ArrayList<>();
    }

    public List<UserSummary> getUsersWithTickets() {
        return usersWithTickets != null ? usersWithTickets : new ArrayList<>();
    }

    public void setUsersWithTickets(List<UserSummary> usersWithTickets) {
        this.usersWithTickets = usersWithTickets != null ? usersWithTickets : new ArrayList<>();
    }

    public List<UserSummary> getUsersWithClaimedSpots() {
        return usersWithClaimedSpots != null ? usersWithClaimedSpots : new ArrayList<>();
    }

    public void setUsersWithClaimedSpots(List<UserSummary> usersWithClaimedSpots) {
        this.usersWithClaimedSpots = usersWithClaimedSpots != null ? usersWithClaimedSpots : new ArrayList<>();
    }

    public Integer getExtraTicketCount() {
        return extraTicketCount != null ? extraTicketCount : 0;
    }

    public void setExtraTicketCount(Integer extraTicketCount) {
        this.extraTicketCount = extraTicketCount != null ? extraTicketCount : 0;
    }

    public List<ReservationOfferDTO> getReservationOffers() {
        return reservationOffers != null ? reservationOffers : new ArrayList<>();
    }

    public void setReservationOffers(List<ReservationOfferDTO> reservationOffers) {
        this.reservationOffers = reservationOffers != null ? reservationOffers : new ArrayList<>();
    }
}
