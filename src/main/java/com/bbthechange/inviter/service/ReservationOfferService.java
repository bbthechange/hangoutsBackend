package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.CompleteReservationOfferRequest;
import com.bbthechange.inviter.dto.CreateReservationOfferRequest;
import com.bbthechange.inviter.dto.ParticipationDTO;
import com.bbthechange.inviter.dto.ReservationOfferDTO;
import com.bbthechange.inviter.dto.UpdateReservationOfferRequest;

import java.util.List;

/**
 * Service interface for reservation offer management within hangouts.
 * Handles CRUD operations and special transaction endpoints (claim, unclaim, complete).
 */
public interface ReservationOfferService {

    /**
     * Create a new reservation offer.
     * Authorization: Any group member can create offers.
     *
     * @param hangoutId The hangout ID
     * @param request The offer creation request
     * @param userId The ID of the user creating the offer
     * @return The created offer DTO with denormalized user info
     */
    ReservationOfferDTO createOffer(String hangoutId, CreateReservationOfferRequest request, String userId);

    /**
     * Get all reservation offers for a hangout.
     * Authorization: Any group member can view offers.
     *
     * @param hangoutId The hangout ID
     * @param userId The ID of the requesting user
     * @return List of all offers with denormalized user info
     */
    List<ReservationOfferDTO> getOffers(String hangoutId, String userId);

    /**
     * Get a specific reservation offer by ID.
     * Authorization: Any group member can view offers.
     *
     * @param hangoutId The hangout ID
     * @param offerId The offer ID
     * @param userId The ID of the requesting user
     * @return The offer DTO with denormalized user info
     */
    ReservationOfferDTO getOffer(String hangoutId, String offerId, String userId);

    /**
     * Update a reservation offer.
     * Authorization: Offer creator OR any group member can update.
     *
     * @param hangoutId The hangout ID
     * @param offerId The offer ID
     * @param request The update request
     * @param userId The ID of the requesting user
     * @return The updated offer DTO
     */
    ReservationOfferDTO updateOffer(String hangoutId, String offerId, UpdateReservationOfferRequest request, String userId);

    /**
     * Delete a reservation offer.
     * Authorization: Offer creator OR any group member can delete.
     *
     * @param hangoutId The hangout ID
     * @param offerId The offer ID
     * @param userId The ID of the requesting user
     */
    void deleteOffer(String hangoutId, String offerId, String userId);

    /**
     * Complete a reservation offer and convert participations.
     * Authorization: Offer creator only.
     *
     * Atomic operation that:
     * 1. Updates offer status to COMPLETED
     * 2. Converts TICKET_NEEDED participations to TICKET_PURCHASED (all or specified)
     * 3. Updates all HangoutPointers
     *
     * @param hangoutId The hangout ID
     * @param offerId The offer ID
     * @param request The completion request (convertAll flag + optional participationIds)
     * @param userId The ID of the requesting user (must be offer creator)
     * @return The completed offer DTO
     */
    ReservationOfferDTO completeOffer(String hangoutId, String offerId, CompleteReservationOfferRequest request, String userId);

    /**
     * Claim a spot in a reservation offer.
     * Authorization: Any group member can claim spots.
     *
     * Atomic transaction that:
     * 1. Increments offer's claimedSpots (with capacity check)
     * 2. Creates a CLAIMED_SPOT participation for the user
     * 3. Updates all HangoutPointers
     *
     * @param hangoutId The hangout ID
     * @param offerId The offer ID
     * @param userId The ID of the user claiming the spot
     * @return The created CLAIMED_SPOT participation DTO
     */
    ParticipationDTO claimSpot(String hangoutId, String offerId, String userId);

    /**
     * Unclaim a spot in a reservation offer.
     * Authorization: User must have claimed a spot.
     *
     * Atomic transaction that:
     * 1. Decrements offer's claimedSpots
     * 2. Deletes the user's CLAIMED_SPOT participation
     * 3. Updates all HangoutPointers
     *
     * @param hangoutId The hangout ID
     * @param offerId The offer ID
     * @param userId The ID of the user unclaiming the spot
     */
    void unclaimSpot(String hangoutId, String offerId, String userId);
}
