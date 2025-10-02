package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.CreatePlaceRequest;
import com.bbthechange.inviter.dto.PlaceDto;
import com.bbthechange.inviter.dto.PlacesResponse;
import com.bbthechange.inviter.dto.UpdatePlaceRequest;
import com.bbthechange.inviter.service.PlaceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for saved places management.
 * Handles operations for user and group saved places.
 */
@RestController
@RequestMapping("/places")
@Validated
public class PlaceController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(PlaceController.class);

    private final PlaceService placeService;

    @Autowired
    public PlaceController(PlaceService placeService) {
        this.placeService = placeService;
    }

    /**
     * Get all places for a user and/or group.
     * GET /places?userId={userId} - Get user places only
     * GET /places?groupId={groupId} - Get group places only
     * GET /places?userId={userId}&groupId={groupId} - Get both in one call
     *
     * @param userId User ID (optional, can be combined with groupId)
     * @param groupId Group ID (optional, can be combined with userId)
     * @param request HTTP request to extract authenticated user
     * @return 200 OK with userPlaces and groupPlaces lists
     */
    @GetMapping
    public ResponseEntity<PlacesResponse> getPlaces(
            @RequestParam(required = false) @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid user ID format") String userId,
            @RequestParam(required = false) @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid group ID format") String groupId,
            HttpServletRequest request) {

        logger.info("Getting places for userId={}, groupId={}", userId, groupId);

        validateUuidFormat(userId, "user ID");
        validateUuidFormat(groupId, "group ID");

        String authenticatedUserId = extractUserId(request);
        PlacesResponse response = placeService.getPlaces(userId, groupId, authenticatedUserId);

        int totalPlaces = response.getUserPlaces().size() + response.getGroupPlaces().size();
        logger.info("Retrieved {} places (user: {}, group: {})",
            totalPlaces, response.getUserPlaces().size(), response.getGroupPlaces().size());

        return ResponseEntity.ok(response);
    }

    /**
     * Create a new saved place.
     * POST /places
     *
     * @param createRequest Place creation request
     * @param httpRequest HTTP request to extract authenticated user
     * @return 201 Created with the new place DTO
     */
    @PostMapping
    public ResponseEntity<PlaceDto> createPlace(
            @Valid @RequestBody CreatePlaceRequest createRequest,
            HttpServletRequest httpRequest) {

        logger.info("Creating place '{}' for {} {}",
            createRequest.getNickname(), createRequest.getOwner().getType(), createRequest.getOwner().getId());

        String authenticatedUserId = extractUserId(httpRequest);
        PlaceDto createdPlace = placeService.createPlace(createRequest, authenticatedUserId);

        logger.info("Successfully created place {} for {} {}",
            createdPlace.getPlaceId(), createdPlace.getOwnerType(), createdPlace.getCreatedBy());

        return ResponseEntity.status(HttpStatus.CREATED).body(createdPlace);
    }

    /**
     * Update an existing saved place.
     * PUT /places/{placeId}?userId={userId} OR PUT /places/{placeId}?groupId={groupId}
     *
     * @param placeId Place ID to update
     * @param userId User ID (optional, mutually exclusive with groupId)
     * @param groupId Group ID (optional, mutually exclusive with userId)
     * @param updateRequest Update request
     * @param httpRequest HTTP request to extract authenticated user
     * @return 200 OK with the updated place DTO
     */
    @PutMapping("/{placeId}")
    public ResponseEntity<PlaceDto> updatePlace(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid place ID format") String placeId,
            @RequestParam(required = false) @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid user ID format") String userId,
            @RequestParam(required = false) @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid group ID format") String groupId,
            @Valid @RequestBody UpdatePlaceRequest updateRequest,
            HttpServletRequest httpRequest) {

        logger.info("Updating place {} for userId={}, groupId={}", placeId, userId, groupId);

        validateUuidFormat(placeId, "place ID");
        validateUuidFormat(userId, "user ID");
        validateUuidFormat(groupId, "group ID");

        String authenticatedUserId = extractUserId(httpRequest);
        PlaceDto updatedPlace = placeService.updatePlace(placeId, userId, groupId, updateRequest, authenticatedUserId);

        logger.info("Successfully updated place {} for {} {}",
            placeId, updatedPlace.getOwnerType(), updatedPlace.getCreatedBy());

        return ResponseEntity.ok(updatedPlace);
    }

    /**
     * Delete (archive) a saved place.
     * DELETE /places/{placeId}?userId={userId} OR DELETE /places/{placeId}?groupId={groupId}
     *
     * Idempotent operation - returns 200 OK even if place is already archived.
     *
     * @param placeId Place ID to delete
     * @param userId User ID (optional, mutually exclusive with groupId)
     * @param groupId Group ID (optional, mutually exclusive with userId)
     * @param httpRequest HTTP request to extract authenticated user
     * @return 200 OK
     */
    @DeleteMapping("/{placeId}")
    public ResponseEntity<Void> deletePlace(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid place ID format") String placeId,
            @RequestParam(required = false) @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid user ID format") String userId,
            @RequestParam(required = false) @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid group ID format") String groupId,
            HttpServletRequest httpRequest) {

        logger.info("Deleting place {} for userId={}, groupId={}", placeId, userId, groupId);

        String authenticatedUserId = extractUserId(httpRequest);
        placeService.deletePlace(placeId, userId, groupId, authenticatedUserId);

        logger.info("Successfully deleted place {}", placeId);

        return ResponseEntity.ok().build();
    }

    /**
     * Validate UUID format for request parameters.
     */
    private void validateUuidFormat(String value, String fieldName) {
        if (value != null && !value.matches("[0-9a-f-]{36}")) {
            throw new com.bbthechange.inviter.exception.ValidationException("Invalid " + fieldName + " format");
        }
    }
}
