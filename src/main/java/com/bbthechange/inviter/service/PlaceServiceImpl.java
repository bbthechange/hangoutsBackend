package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.exception.InvalidPlaceOwnerException;
import com.bbthechange.inviter.exception.PlaceNotFoundException;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.exception.ValidationException;
import com.bbthechange.inviter.model.Place;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.PlaceRepository;
import com.bbthechange.inviter.util.InviterKeyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementation of PlaceService with full business logic.
 */
@Service
public class PlaceServiceImpl implements PlaceService {

    private static final Logger logger = LoggerFactory.getLogger(PlaceServiceImpl.class);

    private final PlaceRepository placeRepository;
    private final GroupRepository groupRepository;

    @Autowired
    public PlaceServiceImpl(PlaceRepository placeRepository, GroupRepository groupRepository) {
        this.placeRepository = placeRepository;
        this.groupRepository = groupRepository;
    }

    @Override
    public PlacesResponse getPlaces(String userId, String groupId, String authenticatedUserId) {
        String currentUserId = authenticatedUserId;

        // Validate that at least one parameter is provided
        if (userId == null && groupId == null) {
            throw new ValidationException("At least one of userId or groupId must be provided");
        }

        List<PlaceDto> userPlaces = List.of();
        List<PlaceDto> groupPlaces = List.of();

        // Fetch user places if userId provided
        if (userId != null) {
            // Permission check: user can only view their own places
            if (!userId.equals(currentUserId)) {
                throw new UnauthorizedException("You can only view your own places");
            }

            String userOwnerPk = InviterKeyFactory.getUserGsi1Pk(userId);
            List<Place> userPlacesList = placeRepository.findPlacesByOwner(userOwnerPk);

            userPlaces = userPlacesList.stream()
                .filter(place -> InviterKeyFactory.STATUS_ACTIVE.equals(place.getStatus()))
                .map(this::mapToDto)
                .collect(Collectors.toList());
        }

        // Fetch group places if groupId provided
        if (groupId != null) {
            // Permission check: user must be a member of the group
            if (!groupRepository.isUserMemberOfGroup(groupId, currentUserId)) {
                throw new UnauthorizedException("You are not a member of this group");
            }

            String groupOwnerPk = InviterKeyFactory.getGroupPk(groupId);
            List<Place> groupPlacesList = placeRepository.findPlacesByOwner(groupOwnerPk);

            groupPlaces = groupPlacesList.stream()
                .filter(place -> InviterKeyFactory.STATUS_ACTIVE.equals(place.getStatus()))
                .map(this::mapToDto)
                .collect(Collectors.toList());
        }

        return new PlacesResponse(userPlaces, groupPlaces);
    }

    @Override
    public PlaceDto createPlace(CreatePlaceRequest request, String authenticatedUserId) {
        String currentUserId = authenticatedUserId;
        String ownerId = request.getOwner().getId();
        String ownerType = request.getOwner().getType();

        // Permission checks
        if (InviterKeyFactory.OWNER_TYPE_GROUP.equals(ownerType)) {
            if (!groupRepository.isUserMemberOfGroup(ownerId, currentUserId)) {
                throw new UnauthorizedException("You are not a member of this group");
            }
            // Groups cannot have primary places
            if (request.isPrimary()) {
                throw new InvalidPlaceOwnerException("Groups cannot have primary places");
            }
        } else if (InviterKeyFactory.OWNER_TYPE_USER.equals(ownerType)) {
            if (!ownerId.equals(currentUserId)) {
                throw new UnauthorizedException("You can only create places for yourself");
            }
        } else {
            throw new InvalidPlaceOwnerException("Invalid owner type: " + ownerType);
        }

        // Handle primary place logic for users
        if (request.isPrimary() && InviterKeyFactory.OWNER_TYPE_USER.equals(ownerType)) {
            handlePrimaryPlaceChange(ownerId);
        }

        // Create the place
        Place newPlace;
        if (InviterKeyFactory.OWNER_TYPE_USER.equals(ownerType)) {
            newPlace = new Place(ownerId, request.getNickname(), request.getAddress(),
                request.getNotes(), request.isPrimary(), currentUserId);

            // Set GSI keys if primary
            if (request.isPrimary()) {
                newPlace.setGsi1pk(InviterKeyFactory.getUserGsi1Pk(ownerId));
                newPlace.setGsi1sk(InviterKeyFactory.PRIMARY_PLACE);
            }
        } else {
            newPlace = new Place(ownerId, currentUserId, request.getNickname(),
                request.getAddress(), request.getNotes());
        }

        Place savedPlace = placeRepository.save(newPlace);
        logger.info("Created place {} for {} {}", savedPlace.getPlaceId(), ownerType, ownerId);

        return mapToDto(savedPlace);
    }

    @Override
    public PlaceDto updatePlace(String placeId, String userId, String groupId, UpdatePlaceRequest request, String authenticatedUserId) {
        String currentUserId = authenticatedUserId;

        // Validate that exactly one of userId or groupId is provided
        if ((userId == null && groupId == null) || (userId != null && groupId != null)) {
            throw new ValidationException("Exactly one of userId or groupId must be provided");
        }

        String ownerPk;
        String ownerType;
        if (userId != null) {
            if (!userId.equals(currentUserId)) {
                throw new UnauthorizedException("You can only update your own places");
            }
            ownerPk = InviterKeyFactory.getUserGsi1Pk(userId);
            ownerType = InviterKeyFactory.OWNER_TYPE_USER;
        } else {
            if (!groupRepository.isUserMemberOfGroup(groupId, currentUserId)) {
                throw new UnauthorizedException("You are not a member of this group");
            }
            ownerPk = InviterKeyFactory.getGroupPk(groupId);
            ownerType = InviterKeyFactory.OWNER_TYPE_GROUP;
        }

        // Find the place
        Place place = placeRepository.findByOwnerAndPlaceId(ownerPk, placeId)
            .orElseThrow(() -> new PlaceNotFoundException("Place not found: " + placeId));

        // Check if archived
        if (InviterKeyFactory.STATUS_ARCHIVED.equals(place.getStatus())) {
            throw new PlaceNotFoundException("Place is archived: " + placeId);
        }

        // Update fields if provided
        if (request.getNickname() != null) {
            place.setNickname(request.getNickname());
        }
        if (request.getAddress() != null) {
            place.setAddress(request.getAddress());
        }
        if (request.getNotes() != null) {
            place.setNotes(request.getNotes());
        }

        // Handle primary place changes for user-owned places
        if (request.getIsPrimary() != null && InviterKeyFactory.OWNER_TYPE_USER.equals(ownerType)) {
            boolean newIsPrimary = request.getIsPrimary();
            if (newIsPrimary != place.isPrimary()) {
                if (newIsPrimary) {
                    // Unset any existing primary place
                    handlePrimaryPlaceChange(userId);
                    place.setPrimary(true);
                    place.setGsi1pk(InviterKeyFactory.getUserGsi1Pk(userId));
                    place.setGsi1sk(InviterKeyFactory.PRIMARY_PLACE);
                } else {
                    // Unset primary flag
                    place.setPrimary(false);
                    place.setGsi1pk(null);
                    place.setGsi1sk(null);
                }
            }
        }

        // Groups cannot have primary places
        if (request.getIsPrimary() != null && request.getIsPrimary() && InviterKeyFactory.OWNER_TYPE_GROUP.equals(ownerType)) {
            throw new InvalidPlaceOwnerException("Groups cannot have primary places");
        }

        Place updatedPlace = placeRepository.save(place);
        logger.info("Updated place {} for {} {}", placeId, ownerType, ownerPk);

        return mapToDto(updatedPlace);
    }

    @Override
    public void deletePlace(String placeId, String userId, String groupId, String authenticatedUserId) {
        String currentUserId = authenticatedUserId;

        // Validate that exactly one of userId or groupId is provided
        if ((userId == null && groupId == null) || (userId != null && groupId != null)) {
            throw new ValidationException("Exactly one of userId or groupId must be provided");
        }

        String ownerPk;
        if (userId != null) {
            if (!userId.equals(currentUserId)) {
                throw new UnauthorizedException("You can only delete your own places");
            }
            ownerPk = InviterKeyFactory.getUserGsi1Pk(userId);
        } else {
            if (!groupRepository.isUserMemberOfGroup(groupId, currentUserId)) {
                throw new UnauthorizedException("You are not a member of this group");
            }
            ownerPk = InviterKeyFactory.getGroupPk(groupId);
        }

        // Find the place
        Place place = placeRepository.findByOwnerAndPlaceId(ownerPk, placeId)
            .orElseThrow(() -> new PlaceNotFoundException("Place not found: " + placeId));

        // Soft delete
        place.setStatus(InviterKeyFactory.STATUS_ARCHIVED);

        // If this was a primary place, unset the primary flag and GSI keys
        if (place.isPrimary()) {
            place.setPrimary(false);
            place.setGsi1pk(null);
            place.setGsi1sk(null);
        }

        placeRepository.save(place);
        logger.info("Archived place {} for owner {}", placeId, ownerPk);
    }

    /**
     * Unset the primary flag on any existing primary place for a user.
     */
    private void handlePrimaryPlaceChange(String userId) {
        Optional<Place> existingPrimary = placeRepository.findPrimaryPlaceForUser(userId);
        if (existingPrimary.isPresent()) {
            Place oldPrimary = existingPrimary.get();
            oldPrimary.setPrimary(false);
            oldPrimary.setGsi1pk(null);
            oldPrimary.setGsi1sk(null);
            placeRepository.save(oldPrimary);
            logger.info("Unset primary flag for place {}", oldPrimary.getPlaceId());
        }
    }

    /**
     * Map Place entity to PlaceDto.
     */
    private PlaceDto mapToDto(Place place) {
        return PlaceDto.builder()
            .placeId(place.getPlaceId())
            .nickname(place.getNickname())
            .address(place.getAddress())
            .notes(place.getNotes())
            .primary(place.isPrimary())
            .status(place.getStatus())
            .ownerType(place.getOwnerType())
            .createdBy(place.getCreatedBy())
            .createdAt(place.getCreatedAt() != null ? place.getCreatedAt().getEpochSecond() : null)
            .updatedAt(place.getUpdatedAt() != null ? place.getUpdatedAt().getEpochSecond() : null)
            .build();
    }
}
