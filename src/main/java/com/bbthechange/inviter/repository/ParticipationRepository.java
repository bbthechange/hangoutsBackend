package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.model.Participation;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for participation management operations in the InviterTable.
 *
 * Key Pattern: PK = HANGOUT#{hangoutId}, SK = PARTICIPATION#{participationId}
 *
 * Participations represent user involvement in hangout tickets/reservations.
 */
public interface ParticipationRepository {

    /**
     * Save a participation (create or update).
     * Performs upsert operation.
     *
     * @param participation The participation to save
     * @return The saved participation
     */
    Participation save(Participation participation);

    /**
     * Find a specific participation by hangout ID and participation ID.
     * Uses direct key access for optimal performance.
     *
     * @param hangoutId The hangout ID
     * @param participationId The participation ID
     * @return Optional containing the participation if found
     */
    Optional<Participation> findById(String hangoutId, String participationId);

    /**
     * Find all participations for a hangout in a single query.
     * Uses single-partition query with PK=HANGOUT#{hangoutId} and SK begins_with PARTICIPATION#
     *
     * @param hangoutId The hangout ID
     * @return List of participations for the hangout
     */
    List<Participation> findByHangoutId(String hangoutId);

    /**
     * Find all participations linked to a specific reservation offer.
     * Useful for completing offers and converting TICKET_NEEDED to TICKET_PURCHASED.
     *
     * @param hangoutId The hangout ID
     * @param offerId The reservation offer ID
     * @return List of participations linked to this offer
     */
    List<Participation> findByOfferId(String hangoutId, String offerId);

    /**
     * Delete a participation by hangout ID and participation ID.
     * Idempotent operation - succeeds even if participation doesn't exist.
     *
     * @param hangoutId The hangout ID
     * @param participationId The participation ID
     */
    void delete(String hangoutId, String participationId);
}
