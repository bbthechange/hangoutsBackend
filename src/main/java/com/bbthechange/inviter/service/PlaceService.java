package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.CreatePlaceRequest;
import com.bbthechange.inviter.dto.PlaceDto;
import com.bbthechange.inviter.dto.PlacesResponse;
import com.bbthechange.inviter.dto.UpdatePlaceRequest;

/**
 * Service interface for place management operations.
 */
public interface PlaceService {

    /**
     * Get all places for a user or group.
     * @param userId User ID (optional)
     * @param groupId Group ID (optional)
     * @param authenticatedUserId Current authenticated user ID
     * @return Response containing list of places
     */
    PlacesResponse getPlaces(String userId, String groupId, String authenticatedUserId);

    /**
     * Create a new place.
     * @param request Place creation request
     * @param authenticatedUserId Current authenticated user ID
     * @return Created place DTO
     */
    PlaceDto createPlace(CreatePlaceRequest request, String authenticatedUserId);

    /**
     * Update an existing place.
     * @param placeId Place ID to update
     * @param userId User ID (if user-owned)
     * @param groupId Group ID (if group-owned)
     * @param request Update request
     * @param authenticatedUserId Current authenticated user ID
     * @return Updated place DTO
     */
    PlaceDto updatePlace(String placeId, String userId, String groupId, UpdatePlaceRequest request, String authenticatedUserId);

    /**
     * Delete (archive) a place.
     * @param placeId Place ID to delete
     * @param userId User ID (if user-owned)
     * @param groupId Group ID (if group-owned)
     * @param authenticatedUserId Current authenticated user ID
     */
    void deletePlace(String placeId, String userId, String groupId, String authenticatedUserId);
}
