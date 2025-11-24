package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.model.ReservationOffer;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for ReservationOffer operations.
 * Provides CRUD operations for reservation offers within hangouts.
 */
public interface ReservationOfferRepository {

    /**
     * Save a reservation offer (create or update).
     * @param offer The reservation offer to save
     * @return The saved reservation offer
     */
    ReservationOffer save(ReservationOffer offer);

    /**
     * Find a reservation offer by ID.
     * @param hangoutId The hangout ID
     * @param offerId The offer ID
     * @return Optional containing the offer if found
     */
    Optional<ReservationOffer> findById(String hangoutId, String offerId);

    /**
     * Find all reservation offers for a hangout.
     * @param hangoutId The hangout ID
     * @return List of all reservation offers for the hangout
     */
    List<ReservationOffer> findByHangoutId(String hangoutId);

    /**
     * Delete a reservation offer.
     * @param hangoutId The hangout ID
     * @param offerId The offer ID
     */
    void delete(String hangoutId, String offerId);
}
