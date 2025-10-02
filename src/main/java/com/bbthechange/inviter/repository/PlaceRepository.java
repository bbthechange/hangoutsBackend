package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.model.Place;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for place management operations in the InviterTable.
 */
public interface PlaceRepository {

    /**
     * Find all places owned by a user or group.
     * @param ownerPk The partition key (USER#{userId} or GROUP#{groupId})
     * @return List of places owned by the entity
     */
    List<Place> findPlacesByOwner(String ownerPk);

    /**
     * Find the primary place for a user using GSI query.
     * @param userId The user ID
     * @return Optional containing the primary place if one exists
     */
    Optional<Place> findPrimaryPlaceForUser(String userId);

    /**
     * Save a place (create or update).
     * @param place The place to save
     * @return The saved place
     */
    Place save(Place place);

    /**
     * Find a specific place by owner and place ID.
     * @param ownerPk The partition key (USER#{userId} or GROUP#{groupId})
     * @param placeId The place ID
     * @return Optional containing the place if found
     */
    Optional<Place> findByOwnerAndPlaceId(String ownerPk, String placeId);
}
