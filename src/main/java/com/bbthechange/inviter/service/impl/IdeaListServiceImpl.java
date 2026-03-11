package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.service.IdeaListService;
import com.bbthechange.inviter.service.PlaceEnrichmentService;
import com.bbthechange.inviter.service.S3Service;
import com.bbthechange.inviter.service.UserService;
import com.bbthechange.inviter.repository.IdeaListRepository;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of IdeaListService with authorization checks and efficient query patterns.
 */
@Service
@Transactional
public class IdeaListServiceImpl implements IdeaListService {
    
    private static final Logger logger = LoggerFactory.getLogger(IdeaListServiceImpl.class);
    
    private final IdeaListRepository ideaListRepository;
    private final GroupRepository groupRepository;
    private final UserService userService;
    private final PlaceEnrichmentService placeEnrichmentService;
    private final S3Service s3Service;

    @Autowired
    public IdeaListServiceImpl(IdeaListRepository ideaListRepository, GroupRepository groupRepository,
                               UserService userService, S3Service s3Service,
                               @Autowired(required = false) PlaceEnrichmentService placeEnrichmentService) {
        this.ideaListRepository = ideaListRepository;
        this.groupRepository = groupRepository;
        this.userService = userService;
        this.s3Service = s3Service;
        this.placeEnrichmentService = placeEnrichmentService;
    }
    
    @Override
    public List<IdeaListDTO> getIdeaListsForGroup(String groupId, String requestingUserId) {
        // Verify user is group member
        ensureUserIsGroupMember(groupId, requestingUserId);
        
        // Get all idea lists with their members
        List<IdeaList> ideaLists = ideaListRepository.findAllIdeaListsWithMembersByGroupId(groupId);

        // Trigger re-enrichment for stale place data across all lists (async, non-blocking)
        if (placeEnrichmentService != null && placeEnrichmentService.isEnabled()) {
            for (IdeaList ideaList : ideaLists) {
                placeEnrichmentService.triggerStaleReEnrichment(ideaList.getMembers(), groupId, ideaList.getListId());
            }
        }

        // Convert to DTOs
        return ideaLists.stream()
                .map(this::convertToDTO)
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
    }
    
    @Override
    public IdeaListDTO getIdeaList(String groupId, String listId, String requestingUserId) {
        // Verify user is group member
        ensureUserIsGroupMember(groupId, requestingUserId);

        // Get idea list with all its members
        IdeaList ideaList = ideaListRepository.findIdeaListWithMembersById(groupId, listId)
                .orElseThrow(() -> new ResourceNotFoundException("Idea list not found: " + listId));

        // Trigger re-enrichment for stale place data (async, non-blocking)
        if (placeEnrichmentService != null && placeEnrichmentService.isEnabled()) {
            placeEnrichmentService.triggerStaleReEnrichment(ideaList.getMembers(), groupId, listId);
        }

        return convertToDTO(ideaList);
    }
    
    @Override
    public IdeaListDTO createIdeaList(String groupId, CreateIdeaListRequest request, String requestingUserId) {
        // Input validation
        validateCreateIdeaListRequest(request);
        
        // Verify user is group member
        ensureUserIsGroupMember(groupId, requestingUserId);
        
        // Create idea list (empty initially)
        IdeaList ideaList = new IdeaList(
                groupId,
                request.getName(),
                request.getCategory(),
                request.getNote(),
                requestingUserId
        );
        ideaList.setIsLocation(request.getIsLocation());

        IdeaList savedList = ideaListRepository.saveIdeaList(ideaList);
        logger.debug("Created idea list: {} in group: {} by user: {}", savedList.getListId(), groupId, requestingUserId);
        
        return new IdeaListDTO(savedList);
    }
    
    @Override
    public IdeaListDTO updateIdeaList(String groupId, String listId, UpdateIdeaListRequest request, String requestingUserId) {
        // Verify user is group member
        ensureUserIsGroupMember(groupId, requestingUserId);
        
        // Get existing idea list
        IdeaList existingList = ideaListRepository.findIdeaListById(groupId, listId)
                .orElseThrow(() -> new ResourceNotFoundException("Idea list not found: " + listId));
        
        // Update fields if provided
        boolean updated = false;
        if (request.getName() != null) {
            existingList.setName(request.getName());
            updated = true;
        }
        if (request.getCategory() != null) {
            existingList.setCategory(request.getCategory());
            updated = true;
        }
        if (request.getNote() != null) {
            existingList.setNote(request.getNote());
            updated = true;
        }
        if (request.getIsLocation() != null) {
            existingList.setIsLocation(request.getIsLocation());
            updated = true;
        }

        if (updated) {
            existingList.touch(); // Update timestamp
            IdeaList savedList = ideaListRepository.saveIdeaList(existingList);
            logger.debug("Updated idea list: {} in group: {} by user: {}", listId, groupId, requestingUserId);
            return convertToDTO(savedList);
        }
        
        return convertToDTO(existingList);
    }
    
    @Override
    public void deleteIdeaList(String groupId, String listId, String requestingUserId) {
        // Verify user is group member
        ensureUserIsGroupMember(groupId, requestingUserId);
        
        // Verify idea list exists
        if (!ideaListRepository.ideaListExists(groupId, listId)) {
            throw new ResourceNotFoundException("Idea list not found: " + listId);
        }
        
        // Delete idea list with all its members
        ideaListRepository.deleteIdeaListWithAllMembers(groupId, listId);
        logger.debug("Deleted idea list: {} from group: {} by user: {}", listId, groupId, requestingUserId);
    }
    
    @Override
    public IdeaDTO addIdeaToList(String groupId, String listId, CreateIdeaRequest request, String requestingUserId) {
        // Input validation
        validateCreateIdeaRequest(request);
        
        // Verify user is group member
        ensureUserIsGroupMember(groupId, requestingUserId);
        
        // Verify idea list exists
        if (!ideaListRepository.ideaListExists(groupId, listId)) {
            throw new ResourceNotFoundException("Idea list not found: " + listId);
        }
        
        // Create idea list member
        IdeaListMember member = new IdeaListMember(
                groupId,
                listId,
                request.getName(),
                request.getUrl(),
                request.getNote(),
                requestingUserId
        );
        member.setImageUrl(request.getImageUrl());
        member.setExternalId(request.getExternalId());
        member.setExternalSource(request.getExternalSource());

        // Validate coordinates (must be provided together)
        validateCoordinates(request.getLatitude(), request.getLongitude());

        // Set place fields if provided
        member.setGooglePlaceId(request.getGooglePlaceId());
        member.setApplePlaceId(request.getApplePlaceId());
        member.setAddress(request.getAddress());
        member.setLatitude(request.getLatitude());
        member.setLongitude(request.getLongitude());
        member.setPlaceCategory(request.getPlaceCategory());

        // Set enrichment status based on whether enrichment is possible
        if (request.getGooglePlaceId() != null && !request.getGooglePlaceId().isBlank()) {
            member.setEnrichmentStatus("PENDING");
        } else {
            member.setEnrichmentStatus("NOT_APPLICABLE");
        }

        IdeaListMember savedMember = ideaListRepository.saveIdeaListMember(member);
        logger.debug("Added idea: {} to list: {} in group: {} by user: {}",
                savedMember.getIdeaId(), listId, groupId, requestingUserId);

        // Trigger async enrichment if a Google Place ID is provided
        if (savedMember.getGooglePlaceId() != null && !savedMember.getGooglePlaceId().isBlank()
                && placeEnrichmentService != null && placeEnrichmentService.isEnabled()) {
            placeEnrichmentService.enrichPlaceAsync(groupId, listId, savedMember.getIdeaId(), savedMember.getGooglePlaceId());
        }

        IdeaDTO dto = new IdeaDTO(savedMember);
        populateEnrichedData(dto, savedMember);
        return dto;
    }
    
    @Override
    public IdeaDTO updateIdea(String groupId, String listId, String ideaId, UpdateIdeaRequest request, String requestingUserId) {
        // Verify user is group member
        ensureUserIsGroupMember(groupId, requestingUserId);
        
        // Get existing idea
        IdeaListMember existingMember = ideaListRepository.findIdeaListMemberById(groupId, listId, ideaId)
                .orElseThrow(() -> new ResourceNotFoundException("Idea not found: " + ideaId));
        
        // Update fields if provided (PATCH semantics)
        boolean updated = false;
        if (request.getName() != null) {
            existingMember.setName(request.getName());
            updated = true;
        }
        if (request.getUrl() != null) {
            existingMember.setUrl(request.getUrl());
            updated = true;
        }
        if (request.getNote() != null) {
            existingMember.setNote(request.getNote());
            updated = true;
        }
        if (request.getImageUrl() != null) {
            existingMember.setImageUrl(request.getImageUrl());
            updated = true;
        }
        if (request.getExternalId() != null) {
            existingMember.setExternalId(request.getExternalId());
            updated = true;
        }
        if (request.getExternalSource() != null) {
            existingMember.setExternalSource(request.getExternalSource());
            updated = true;
        }
        // Validate coordinates (must be provided together)
        validateCoordinates(request.getLatitude(), request.getLongitude());

        // Place fields (PATCH semantics)
        // Capture old googlePlaceId before updating for enrichment trigger
        String oldGooglePlaceId = existingMember.getGooglePlaceId();
        if (request.getGooglePlaceId() != null) {
            existingMember.setGooglePlaceId(request.getGooglePlaceId());
            updated = true;
        }
        if (request.getApplePlaceId() != null) {
            existingMember.setApplePlaceId(request.getApplePlaceId());
            updated = true;
        }
        if (request.getAddress() != null) {
            existingMember.setAddress(request.getAddress());
            updated = true;
        }
        if (request.getLatitude() != null) {
            existingMember.setLatitude(request.getLatitude());
            updated = true;
        }
        if (request.getLongitude() != null) {
            existingMember.setLongitude(request.getLongitude());
            updated = true;
        }
        if (request.getPlaceCategory() != null) {
            existingMember.setPlaceCategory(request.getPlaceCategory());
            updated = true;
        }

        // Check if googlePlaceId was changed (new value differs from old)
        boolean googlePlaceIdChanged = request.getGooglePlaceId() != null
                && !request.getGooglePlaceId().equals(oldGooglePlaceId);

        if (updated) {
            // If googlePlaceId changed, update enrichment status and trigger re-enrichment
            if (googlePlaceIdChanged) {
                existingMember.setEnrichmentStatus("PENDING");
                existingMember.setLastEnrichedAt(null);
            }

            existingMember.touch(); // Update timestamp
            IdeaListMember savedMember = ideaListRepository.saveIdeaListMember(existingMember);
            logger.debug("Updated idea: {} in list: {} group: {} by user: {}", ideaId, listId, groupId, requestingUserId);

            // Trigger async enrichment if googlePlaceId was changed
            if (googlePlaceIdChanged && savedMember.getGooglePlaceId() != null
                    && !savedMember.getGooglePlaceId().isBlank()
                    && placeEnrichmentService != null && placeEnrichmentService.isEnabled()) {
                placeEnrichmentService.enrichPlaceAsync(groupId, listId, ideaId, savedMember.getGooglePlaceId());
            }

            IdeaDTO dto = new IdeaDTO(savedMember);
            populateEnrichedData(dto, savedMember);
            return dto;
        }

        IdeaDTO dto = new IdeaDTO(existingMember);
        populateEnrichedData(dto, existingMember);
        return dto;
    }
    
    @Override
    public void deleteIdea(String groupId, String listId, String ideaId, String requestingUserId) {
        // Verify user is group member
        ensureUserIsGroupMember(groupId, requestingUserId);

        // Fetch idea (needed for existence check and S3 cleanup)
        IdeaListMember member = ideaListRepository.findIdeaListMemberById(groupId, listId, ideaId)
                .orElseThrow(() -> new ResourceNotFoundException("Idea not found: " + ideaId));

        // Clean up cached photo from S3 if present
        if (member.getCachedPhotoUrl() != null) {
            s3Service.deleteImageAsync(member.getCachedPhotoUrl());
        }

        // Delete idea
        ideaListRepository.deleteIdeaListMember(groupId, listId, ideaId);
        logger.debug("Deleted idea: {} from list: {} group: {} by user: {}", ideaId, listId, groupId, requestingUserId);
    }
    
    @Override
    public IdeaDTO addIdeaInterest(String groupId, String listId, String ideaId, String requestingUserId) {
        ensureUserIsGroupMember(groupId, requestingUserId);

        ideaListRepository.addIdeaInterest(groupId, listId, ideaId, requestingUserId);

        IdeaListMember member = ideaListRepository.findIdeaListMemberById(groupId, listId, ideaId)
                .orElseThrow(() -> new ResourceNotFoundException("Idea not found: " + ideaId));

        IdeaDTO dto = new IdeaDTO(member);
        populateEnrichedData(dto, member);
        return dto;
    }

    @Override
    public IdeaDTO removeIdeaInterest(String groupId, String listId, String ideaId, String requestingUserId) {
        ensureUserIsGroupMember(groupId, requestingUserId);

        ideaListRepository.removeIdeaInterest(groupId, listId, ideaId, requestingUserId);

        IdeaListMember member = ideaListRepository.findIdeaListMemberById(groupId, listId, ideaId)
                .orElseThrow(() -> new ResourceNotFoundException("Idea not found: " + ideaId));

        IdeaDTO dto = new IdeaDTO(member);
        populateEnrichedData(dto, member);
        return dto;
    }

    // Private helper methods

    private void ensureUserIsGroupMember(String groupId, String userId) {
        if (!groupRepository.isUserMemberOfGroup(groupId, userId)) {
            throw new UnauthorizedException("User is not a member of group: " + groupId);
        }
    }
    
    private void validateCreateIdeaListRequest(CreateIdeaListRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new ValidationException("Idea list name is required");
        }
    }
    
    private void validateCoordinates(Double latitude, Double longitude) {
        if ((latitude != null) != (longitude != null)) {
            throw new ValidationException("Both latitude and longitude must be provided together");
        }
        if (latitude != null) {
            if (latitude < -90 || latitude > 90) {
                throw new ValidationException("Latitude must be between -90 and 90");
            }
            if (longitude < -180 || longitude > 180) {
                throw new ValidationException("Longitude must be between -180 and 180");
            }
        }
    }

    private void validateCreateIdeaRequest(CreateIdeaRequest request) {
        // At least one field must be provided
        if ((request.getName() == null || request.getName().trim().isEmpty()) &&
            (request.getUrl() == null || request.getUrl().trim().isEmpty()) &&
            (request.getNote() == null || request.getNote().trim().isEmpty())) {
            throw new ValidationException("At least one field (name, url, or note) is required for an idea");
        }
    }
    
    private IdeaListDTO convertToDTO(IdeaList ideaList) {
        IdeaListDTO dto = new IdeaListDTO(ideaList);

        // Convert members and populate interest data
        List<IdeaDTO> ideaDTOs = ideaList.getMembers().stream()
                .map(member -> {
                    IdeaDTO ideaDTO = new IdeaDTO(member);
                    populateEnrichedData(ideaDTO, member);
                    return ideaDTO;
                })
                // Sort by interestCount desc, then addedTime desc
                .sorted(Comparator.comparingInt(IdeaDTO::getInterestCount).reversed()
                        .thenComparing(Comparator.comparing(IdeaDTO::getAddedTime).reversed()))
                .collect(Collectors.toList());

        dto.setIdeas(ideaDTOs);
        return dto;
    }

    private void populateEnrichedData(IdeaDTO dto, IdeaListMember member) {
        // Resolve creator display info
        resolveCreatorInfo(dto, member);
        // Resolve interest data
        List<InterestedUserDTO> interestedUsers = resolveInterestedUsers(member);
        dto.setInterestedUsers(interestedUsers);
        dto.setInterestCount(interestedUsers.size() + 1); // explicit + 1 (implicit creator)
    }

    private void resolveCreatorInfo(IdeaDTO dto, IdeaListMember member) {
        if (member.getAddedBy() != null) {
            try {
                userService.getUserSummary(UUID.fromString(member.getAddedBy()))
                        .ifPresent(summary -> {
                            dto.setAddedByName(summary.getDisplayName());
                            dto.setAddedByImagePath(summary.getMainImagePath());
                        });
            } catch (Exception e) {
                logger.warn("Failed to resolve creator info for userId: {}", member.getAddedBy(), e);
            }
        }
    }

    private List<InterestedUserDTO> resolveInterestedUsers(IdeaListMember member) {
        Set<String> userIds = member.getInterestedUserIds();
        if (userIds == null || userIds.isEmpty()) {
            return new ArrayList<>();
        }

        return userIds.stream()
                .map(userId -> {
                    try {
                        return userService.getUserSummary(UUID.fromString(userId))
                                .map(summary -> new InterestedUserDTO(
                                        userId,
                                        summary.getDisplayName(),
                                        summary.getMainImagePath()))
                                .orElse(null);
                    } catch (Exception e) {
                        logger.warn("Failed to resolve user summary for interest userId: {}", userId, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}