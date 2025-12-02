package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.service.HangoutService;
import com.bbthechange.inviter.service.FuzzyTimeService;
import com.bbthechange.inviter.service.UserService;
import com.bbthechange.inviter.service.EventSeriesService;
import com.bbthechange.inviter.service.NotificationService;
import com.bbthechange.inviter.service.S3Service;
import com.bbthechange.inviter.service.GroupTimestampService;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.exception.*;
import com.bbthechange.inviter.exception.RepositoryException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of HangoutService with pointer update patterns.
 * Handles coordination between canonical records and pointer records.
 */
@Service
public class HangoutServiceImpl implements HangoutService {

    private static final Logger logger = LoggerFactory.getLogger(HangoutServiceImpl.class);

    private final HangoutRepository hangoutRepository;
    private final GroupRepository groupRepository;
    private final FuzzyTimeService fuzzyTimeService;
    private final UserService userService;
    private final EventSeriesService eventSeriesService;
    private final NotificationService notificationService;
    private final PointerUpdateService pointerUpdateService;
    private final S3Service s3Service;
    private final GroupTimestampService groupTimestampService;

    @Autowired
    public HangoutServiceImpl(HangoutRepository hangoutRepository, GroupRepository groupRepository,
                              FuzzyTimeService fuzzyTimeService, UserService userService,
                              @Lazy EventSeriesService eventSeriesService,
                              NotificationService notificationService,
                              PointerUpdateService pointerUpdateService,
                              S3Service s3Service,
                              GroupTimestampService groupTimestampService) {
        this.hangoutRepository = hangoutRepository;
        this.groupRepository = groupRepository;
        this.fuzzyTimeService = fuzzyTimeService;
        this.userService = userService;
        this.eventSeriesService = eventSeriesService;
        this.notificationService = notificationService;
        this.pointerUpdateService = pointerUpdateService;
        this.s3Service = s3Service;
        this.groupTimestampService = groupTimestampService;
    }
    
    @Override
    public Hangout createHangout(CreateHangoutRequest request, String requestingUserId) {
        Hangout hangout = hangoutFromHangoutRequest(request, requestingUserId);

        // Prepare pointer records
        List<HangoutPointer> pointers = new ArrayList<>();
        if (request.getAssociatedGroups() != null) {
            for (String groupId : request.getAssociatedGroups()) {
                HangoutPointer pointer = new HangoutPointer(groupId, hangout.getHangoutId(), hangout.getTitle());
                pointer.setStatus("ACTIVE");
                pointer.setLocation(hangout.getLocation());
                pointer.setParticipantCount(0);

                // *** CRITICAL: Denormalize ALL time information ***
                pointer.setTimeInput(hangout.getTimeInput());           // For API response
                pointer.setGsi1pk("GROUP#" + groupId);                  // GSI primary key
                pointer.setStartTimestamp(hangout.getStartTimestamp()); // GSI sort key
                pointer.setEndTimestamp(hangout.getEndTimestamp());     // For completeness

                // Denormalize image path
                pointer.setMainImagePath(hangout.getMainImagePath());

                // Denormalize basic hangout fields
                pointer.setDescription(hangout.getDescription());
                pointer.setVisibility(hangout.getVisibility());
                pointer.setCarpoolEnabled(hangout.isCarpoolEnabled());

                // Denormalize ticket-related fields
                pointer.setTicketLink(hangout.getTicketLink());
                pointer.setTicketsRequired(hangout.getTicketsRequired());
                pointer.setDiscountCode(hangout.getDiscountCode());

                // NEW: Initialize empty collections (already done in constructor, but explicit here)
                // polls, pollOptions, votes, cars, carRiders, needsRide are initialized in HangoutPointer()

                // NEW: Add attributes created with the hangout (will be set after pointer is saved)
                // Note: attributes will be added after transaction completes

                pointers.add(pointer);
            }
        }

        // Prepare attribute records
        List<HangoutAttribute> attributes = new ArrayList<>();
        if (request.getAttributes() != null) {
            for (CreateAttributeRequest attrRequest : request.getAttributes()) {
                if (!attrRequest.isValid()) {
                    throw new ValidationException("Invalid attribute request: " + attrRequest);
                }
                // Simple duplicate name check within the request itself
                long nameCount = request.getAttributes().stream()
                    .filter(a -> a.getTrimmedAttributeName().equals(attrRequest.getTrimmedAttributeName()))
                    .count();
                if (nameCount > 1) {
                    throw new ValidationException("Duplicate attribute name in request: " + attrRequest.getTrimmedAttributeName());
                }
                attributes.add(new HangoutAttribute(hangout.getHangoutId(), attrRequest.getTrimmedAttributeName(), attrRequest.getStringValue()));
            }
        }

        // Denormalize attributes onto all pointers before saving
        for (HangoutPointer pointer : pointers) {
            pointer.setAttributes(new ArrayList<>(attributes)); // Create new list to avoid shared references
        }

        // Prepare poll records
        List<Poll> polls = new ArrayList<>();
        List<PollOption> pollOptions = new ArrayList<>();
        if (request.getPolls() != null) {
            for (CreatePollRequest pollRequest : request.getPolls()) {
                // Validate poll request
                if (pollRequest.getTitle() == null || pollRequest.getTitle().trim().isEmpty()) {
                    throw new ValidationException("Poll title is required");
                }
                if (pollRequest.getTitle().trim().length() > 200) {
                    throw new ValidationException("Poll title must be between 1 and 200 characters");
                }
                if (pollRequest.getDescription() != null && pollRequest.getDescription().length() > 1000) {
                    throw new ValidationException("Poll description cannot exceed 1000 characters");
                }

                // Create poll entity
                Poll poll = new Poll(
                    hangout.getHangoutId(),
                    pollRequest.getTitle(),
                    pollRequest.getDescription(),
                    pollRequest.isMultipleChoice()
                );
                polls.add(poll);

                // Create poll option entities
                if (pollRequest.getOptions() != null) {
                    for (String optionText : pollRequest.getOptions()) {
                        if (optionText != null && !optionText.trim().isEmpty()) {
                            pollOptions.add(new PollOption(hangout.getHangoutId(), poll.getPollId(), optionText.trim()));
                        }
                    }
                }
            }
        }

        // Denormalize polls onto pointers before saving
        for (HangoutPointer pointer : pointers) {
            pointer.setPolls(new ArrayList<>(polls)); // Create new list to avoid shared references
            pointer.setPollOptions(new ArrayList<>(pollOptions)); // Create new list to avoid shared references
            pointer.setVotes(new ArrayList<>()); // Initialize empty votes list
        }

        // Save everything in a single transaction
        hangout = hangoutRepository.createHangoutWithAttributes(hangout, pointers, attributes, polls, pollOptions);

        logger.info("Created hangout {} with {} attributes and {} polls by user {}",
            hangout.getHangoutId(), attributes.size(), polls.size(), requestingUserId);

        // Update Group.lastHangoutModified for all associated groups
        groupTimestampService.updateGroupTimestamps(hangout.getAssociatedGroups());

        // Send push notifications to group members
        String creatorName = getCreatorDisplayName(requestingUserId);
        notificationService.notifyNewHangout(hangout, requestingUserId, creatorName);

        return hangout;
    }

    @Override
    public Hangout hangoutFromHangoutRequest(CreateHangoutRequest request, String requestingUserId) {
        // Convert timeInput to canonical timestamps
        FuzzyTimeService.TimeConversionResult timeResult = null;
        if (request.getTimeInfo() != null) {
            timeResult = fuzzyTimeService.convert(request.getTimeInfo());
        }

        // Create the hangout
        Hangout hangout = new Hangout(
            request.getTitle(),
            request.getDescription(),
            null, // startTime - will be populated from timeInput later
            null, // endTime - will be populated from timeInput later
            request.getLocation(),
            request.getVisibility(),
            request.getMainImagePath()
        );

        // Set the timeInput for fuzzy time support and canonical timestamps
        hangout.setTimeInput(request.getTimeInfo());
        if (timeResult != null) {
            hangout.setStartTimestamp(timeResult.startTimestamp);
            hangout.setEndTimestamp(timeResult.endTimestamp);
        }
        hangout.setCarpoolEnabled(request.isCarpoolEnabled());

        // Set ticket-related fields
        hangout.setTicketLink(request.getTicketLink());
        hangout.setTicketsRequired(request.getTicketsRequired());
        hangout.setDiscountCode(request.getDiscountCode());

        // Verify user is in all specified groups
        if (request.getAssociatedGroups() != null) {
            for (String groupId : request.getAssociatedGroups()) {
                if (!groupRepository.findMembership(groupId, requestingUserId).isPresent()) {
                    throw new UnauthorizedException("User not in group: " + groupId);
                }
            }
            hangout.setAssociatedGroups(request.getAssociatedGroups());
        }
        return hangout;
    }

    @Override
    public HangoutDetailDTO getHangoutDetail(String hangoutId, String requestingUserId) {
        // Single item collection query gets EVERYTHING (the power pattern!)
        HangoutDetailData hangoutDetail = hangoutRepository.getHangoutDetailData(hangoutId);
        // Authorization check
        Hangout hangout = hangoutDetail.getHangout();
        if (!canUserViewHangout(requestingUserId, hangout)) {
            throw new UnauthorizedException("Cannot view hangout");
        }
        
        // Load attributes for this hangout
        List<HangoutAttribute> attributes = hangoutRepository.findAttributesByHangoutId(hangoutId);
        List<HangoutAttributeDTO> attributeDTOs = attributes.stream()
            .map(HangoutAttributeDTO::fromEntity)
            .collect(Collectors.toList());
        
        // Transform poll data with options and vote counts directly from the single query
        List<PollWithOptionsDTO> pollsWithOptions = transformPollData(hangoutDetail, requestingUserId);
        
        // Transform to DTO with formatted timeInfo
        TimeInfo timeInfo = formatTimeInfoForResponse(hangout.getTimeInput());
        hangout.setTimeInput(timeInfo);
        // Transform needs ride data to DTOs
        List<NeedsRideDTO> needsRideDTOs = hangoutDetail.getNeedsRide().stream()
            .map(NeedsRideDTO::new)
            .collect(Collectors.toList());

        // Convert participations and offers to DTOs with denormalized user info
        List<ParticipationDTO> participationDTOs = hangoutDetail.getParticipations().stream()
            .map(p -> {
                UserSummaryDTO user = userService.getUserSummary(UUID.fromString(p.getUserId()))
                    .orElse(null);
                if (user == null) {
                    logger.warn("User not found for participation: {}", p.getUserId());
                    return null;
                }
                return new ParticipationDTO(p, user.getDisplayName(), user.getMainImagePath());
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        List<ReservationOfferDTO> offerDTOs = hangoutDetail.getReservationOffers().stream()
            .map(o -> {
                UserSummaryDTO user = userService.getUserSummary(UUID.fromString(o.getUserId()))
                    .orElse(null);
                if (user == null) {
                    logger.warn("User not found for offer: {}", o.getUserId());
                    return null;
                }
                return new ReservationOfferDTO(o, user.getDisplayName(), user.getMainImagePath());
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        return HangoutDetailDTO.builder()
                .withHangout(hangout)
                .withAttributes(attributeDTOs)
                .withPolls(pollsWithOptions)
                .withCars(hangoutDetail.getCars())
                .withVotes(hangoutDetail.getVotes())
                .withAttendance(hangoutDetail.getAttendance())
                .withCarRiders(hangoutDetail.getCarRiders())
                .withNeedsRide(needsRideDTOs)
                .withParticipations(participationDTOs)
                .withReservationOffers(offerDTOs)
                .build();
    }
    
    @Override
    public void updateHangout(String hangoutId, UpdateHangoutRequest request, String requestingUserId) {
        // Get and authorize
        Hangout hangout = hangoutRepository.findHangoutById(hangoutId)
            .orElseThrow(() -> new ResourceNotFoundException("Hangout not found: " + hangoutId));

        if (!canUserEditHangout(requestingUserId, hangout)) {
            throw new UnauthorizedException("Cannot edit hangout");
        }

        // Track if any pointer-denormalized fields changed
        boolean needsPointerUpdate = false;
        String oldMainImagePath = null;

        // Update canonical record fields
        if (request.getTitle() != null && !request.getTitle().equals(hangout.getTitle())) {
            hangout.setTitle(request.getTitle());
            needsPointerUpdate = true;
        }

        if (request.getDescription() != null) {
            hangout.setDescription(request.getDescription());
            needsPointerUpdate = true;
        }

        if (request.getTimeInfo() != null) {
            hangout.setTimeInput(request.getTimeInfo());

            // Convert timeInput to canonical timestamps
            FuzzyTimeService.TimeConversionResult timeResult = fuzzyTimeService.convert(request.getTimeInfo());
            hangout.setStartTimestamp(timeResult.startTimestamp);
            hangout.setEndTimestamp(timeResult.endTimestamp);
            needsPointerUpdate = true;
        }

        if (request.getLocation() != null) {
            hangout.setLocation(request.getLocation());
            needsPointerUpdate = true;
        }

        if (request.getVisibility() != null) {
            hangout.setVisibility(request.getVisibility());
            needsPointerUpdate = true;
        }

        if (request.getMainImagePath() != null && !request.getMainImagePath().equals(hangout.getMainImagePath())) {
            oldMainImagePath = hangout.getMainImagePath(); // Store old path for cleanup
            hangout.setMainImagePath(request.getMainImagePath());
            needsPointerUpdate = true;
        }

        if (request.isCarpoolEnabled() != hangout.isCarpoolEnabled()) {
            hangout.setCarpoolEnabled(request.isCarpoolEnabled());
            needsPointerUpdate = true;
        }

        // Ticket coordination fields
        if (request.getTicketLink() != null && !request.getTicketLink().equals(hangout.getTicketLink())) {
            hangout.setTicketLink(request.getTicketLink());
            needsPointerUpdate = true;
        }

        if (request.getTicketsRequired() != null && !request.getTicketsRequired().equals(hangout.getTicketsRequired())) {
            hangout.setTicketsRequired(request.getTicketsRequired());
            needsPointerUpdate = true;
        }

        if (request.getDiscountCode() != null && !request.getDiscountCode().equals(hangout.getDiscountCode())) {
            hangout.setDiscountCode(request.getDiscountCode());
            needsPointerUpdate = true;
        }

        // Save canonical record
        hangoutRepository.createHangout(hangout); // Using createHangout as it's a putItem

        // Update pointer records using read-modify-write pattern with optimistic locking
        if (needsPointerUpdate && hangout.getAssociatedGroups() != null) {
            updatePointersWithBasicFields(hangout);
        }

        // Update Group.lastHangoutModified for all associated groups
        groupTimestampService.updateGroupTimestamps(hangout.getAssociatedGroups());

        // If this hangout is part of a series, update the series records
        if (hangout.getSeriesId() != null) {
            try {
                eventSeriesService.updateSeriesAfterHangoutModification(hangoutId);
                logger.info("Updated series {} after hangout {} modification", hangout.getSeriesId(), hangoutId);
            } catch (Exception e) {
                logger.warn("Failed to update series after hangout modification: {}", e.getMessage());
                // Continue execution - the hangout update itself succeeded
            }
        }

        // Delete old image from S3 asynchronously if it was changed
        if (oldMainImagePath != null) {
            s3Service.deleteImageAsync(oldMainImagePath);
            logger.info("Initiated async deletion of old hangout image: {}", oldMainImagePath);
        }

        logger.info("Updated hangout {} by user {}", hangoutId, requestingUserId);
    }
    
    @Override
    public void deleteHangout(String hangoutId, String requestingUserId) {
        // Get and authorize
        Hangout hangout = hangoutRepository.findHangoutById(hangoutId)
            .orElseThrow(() -> new ResourceNotFoundException("Hangout not found: " + hangoutId));
        
        if (!canUserEditHangout(requestingUserId, hangout)) {
            throw new UnauthorizedException("Cannot delete hangout");
        }
        
        // If this hangout is part of a series, handle series cleanup first
        if (hangout.getSeriesId() != null) {
            try {
                eventSeriesService.removeHangoutFromSeries(hangoutId);
                logger.info("Removed hangout {} from series {} during deletion", hangoutId, hangout.getSeriesId());
            } catch (Exception e) {
                logger.error("Failed to remove hangout from series during deletion: {}", e.getMessage());
                throw new RepositoryException("Failed to remove hangout from series during deletion", e);
            }
        } else {
            // Standard deletion process for non-series hangouts

            // Delete pointer records first
            if (hangout.getAssociatedGroups() != null) {
                for (String groupId : hangout.getAssociatedGroups()) {
                    groupRepository.deleteHangoutPointer(groupId, hangoutId);
                }
            }

            // Delete canonical record and all associated data (polls, cars, etc.)
            hangoutRepository.deleteHangout(hangoutId);
        }

        // Update Group.lastHangoutModified for all associated groups
        // (Must happen for both series and non-series hangouts)
        groupTimestampService.updateGroupTimestamps(hangout.getAssociatedGroups());

        logger.info("Deleted hangout {} by user {}", hangoutId, requestingUserId);
    }

    @Override
    public List<HangoutSummaryDTO> getHangoutsForUser(String userId) {
        logger.info("Fetching hangouts for user {}", userId);

        // Step 1: Get user's group IDs
        List<GroupMembership> userGroups = groupRepository.findGroupsByUserId(userId);
        List<String> groupIds = userGroups.stream()
            .map(GroupMembership::getGroupId)
            .collect(Collectors.toList());

        // Step 2: Construct GSI query partition keys
        List<String> partitionKeys = new ArrayList<>();
        partitionKeys.add("USER#" + userId); // Direct user invites
        for (String groupId : groupIds) {
            partitionKeys.add("GROUP#" + groupId); // Group hangouts
        }

        // Step 3: Execute parallel GSI queries
        List<BaseItem> allHangoutPointers = new ArrayList<>();
        for (String partitionKey : partitionKeys) {
            try {
                List<BaseItem> pointers = hangoutRepository.findUpcomingHangoutsForParticipant(partitionKey, "T#");
                allHangoutPointers.addAll(pointers);
            } catch (Exception e) {
                logger.warn("Failed to query hangouts for partition key {}: {}", partitionKey, e.getMessage());
            }
        }

        // Step 4: Filter for HangoutPointer items and convert to DTOs
        List<HangoutSummaryDTO> hangoutSummaries = allHangoutPointers.stream()
            .filter(item -> item instanceof HangoutPointer)
            .map(item -> (HangoutPointer) item)
            .map(pointer -> convertToSummaryDTO(pointer, userId))
            .sorted((a, b) -> {
                // Sort by hangoutId as a proxy for chronological order
                // In practice, GSI already returns items sorted by timestamp
                return a.getHangoutId().compareTo(b.getHangoutId());
            })
            .collect(Collectors.toList());

        logger.info("Found {} hangouts for user {}", hangoutSummaries.size(), userId);
        return hangoutSummaries;
    }
    
    @Override
    public void updateEventTitle(String eventId, String newTitle, String requestingUserId) {
        // Authorization check first
        HangoutDetailData data = hangoutRepository.getHangoutDetailData(eventId);
        if (!canUserEditHangout(requestingUserId, data.getHangout())) {
            throw new UnauthorizedException("Cannot edit event");
        }

        // Step 1: Update canonical record first
        Hangout hangout = data.getHangout();
        hangout.setTitle(newTitle);
        hangoutRepository.save(hangout);

        // Step 2: Update each pointer record using read-modify-write pattern with optimistic locking
        List<String> associatedGroups = hangout.getAssociatedGroups();
        if (associatedGroups != null && !associatedGroups.isEmpty()) {
            for (String groupId : associatedGroups) {
                pointerUpdateService.updatePointerWithRetry(groupId, eventId, pointer -> {
                    pointer.setTitle(newTitle);
                }, "title");
            }
        }

        // Update Group.lastHangoutModified for all associated groups
        groupTimestampService.updateGroupTimestamps(associatedGroups);

        logger.info("Updated title for event {} to '{}' by user {}", eventId, newTitle, requestingUserId);
    }
    
    @Override
    public void updateEventDescription(String eventId, String newDescription, String requestingUserId) {
        // Authorization check first
        HangoutDetailData data = hangoutRepository.getHangoutDetailData(eventId);
        if (!canUserEditHangout(requestingUserId, data.getHangout())) {
            throw new UnauthorizedException("Cannot edit event");
        }
        
        // Step 1: Update canonical record
        Hangout hangout = data.getHangout();
        hangout.setDescription(newDescription);
        hangoutRepository.save(hangout);

        // Update Group.lastHangoutModified for all associated groups
        groupTimestampService.updateGroupTimestamps(hangout.getAssociatedGroups());

        logger.info("Updated description for event {} by user {}", eventId, requestingUserId);
    }
    
    @Override
    public void updateEventLocation(String eventId, Address newLocation, String requestingUserId) {
        // Authorization check first
        HangoutDetailData data = hangoutRepository.getHangoutDetailData(eventId);
        if (!canUserEditHangout(requestingUserId, data.getHangout())) {
            throw new UnauthorizedException("Cannot edit event");
        }

        // Step 1: Update canonical record
        Hangout hangout = data.getHangout();
        hangout.setLocation(newLocation);
        hangoutRepository.save(hangout);

        // Step 2: Update each pointer record using read-modify-write pattern with optimistic locking
        List<String> associatedGroups = hangout.getAssociatedGroups();
        if (associatedGroups != null && !associatedGroups.isEmpty()) {
            for (String groupId : associatedGroups) {
                pointerUpdateService.updatePointerWithRetry(groupId, eventId, pointer -> {
                    pointer.setLocation(newLocation);
                }, "location");
            }
        }

        // Update Group.lastHangoutModified for all associated groups
        groupTimestampService.updateGroupTimestamps(associatedGroups);

        logger.info("Updated location for event {} by user {}", eventId, requestingUserId);
    }
    
    @Override
    public void associateEventWithGroups(String eventId, List<String> groupIds, String requestingUserId) {
        // Authorization check
        HangoutDetailData hangoutDetail = hangoutRepository.getHangoutDetailData(eventId);
        if (!canUserEditHangout(requestingUserId, hangoutDetail.getHangout())) {
            throw new UnauthorizedException("Cannot edit event");
        }

        Hangout hangout = hangoutDetail.getHangout();

        // Verify user is in all specified groups
        for (String groupId : groupIds) {
            if (!groupRepository.findMembership(groupId, requestingUserId).isPresent()) {
                throw new UnauthorizedException("User not in group: " + groupId);
            }
        }
        
        // Update canonical record
        if (hangout.getAssociatedGroups() == null) {
            hangout.setAssociatedGroups(new java.util.ArrayList<>());
        }
        for (String groupId : groupIds) {
            if (!hangout.getAssociatedGroups().contains(groupId)) {
                hangout.getAssociatedGroups().add(groupId);
            }
        }
        hangoutRepository.createHangout(hangout);
        
        // Create hangout pointer records
        for (String groupId : groupIds) {
            HangoutPointer pointer = new HangoutPointer(groupId, eventId, hangout.getTitle());
            pointer.setStatus("ACTIVE"); // Default status
            pointer.setLocation(hangout.getLocation());
            pointer.setParticipantCount(0); // Will be updated as people respond

            // Set GSI fields for EntityTimeIndex
            pointer.setGsi1pk("GROUP#" + groupId);
            if (hangout.getTimeInput() != null) {
                pointer.setTimeInput(hangout.getTimeInput());
                FuzzyTimeService.TimeConversionResult timeResult = fuzzyTimeService.convert(hangout.getTimeInput());
                pointer.setStartTimestamp(timeResult.startTimestamp);
                pointer.setEndTimestamp(timeResult.endTimestamp);
            }

            // Denormalize basic hangout fields (Phase 2 - denormalization plan)
            pointer.setDescription(hangout.getDescription());
            pointer.setVisibility(hangout.getVisibility());
            pointer.setCarpoolEnabled(hangout.isCarpoolEnabled());
            pointer.setMainImagePath(hangout.getMainImagePath());

            // Denormalize existing polls, votes, attributes, and interest levels
            // Get all existing data from hangout detail
            HangoutDetailData detailData = hangoutRepository.getHangoutDetailData(eventId);
            pointer.setPolls(detailData.getPolls());
            pointer.setPollOptions(detailData.getPollOptions());
            pointer.setVotes(detailData.getVotes());
            pointer.setCars(detailData.getCars());
            pointer.setCarRiders(detailData.getCarRiders());
            pointer.setNeedsRide(detailData.getNeedsRide());
            pointer.setAttributes(hangoutRepository.findAttributesByHangoutId(eventId));
            pointer.setInterestLevels(detailData.getAttendance());

            groupRepository.saveHangoutPointer(pointer);
        }

        // Update Group.lastHangoutModified for newly associated groups
        groupTimestampService.updateGroupTimestamps(groupIds);

        logger.info("Associated event {} with {} groups by user {}", eventId, groupIds.size(), requestingUserId);
    }
    
    @Override
    public void disassociateEventFromGroups(String eventId, List<String> groupIds, String requestingUserId) {
        // Authorization check
        HangoutDetailData data = hangoutRepository.getHangoutDetailData(eventId);
        if (!canUserEditHangout(requestingUserId, data.getHangout())) {
            throw new UnauthorizedException("Cannot edit event");
        }
        
        Hangout event = data.getHangout();
        
        // Update canonical record
        if (event.getAssociatedGroups() != null) {
            event.getAssociatedGroups().removeAll(groupIds);
            hangoutRepository.createHangout(event);
        }
        
        // Remove hangout pointer records
        for (String groupId : groupIds) {
            groupRepository.deleteHangoutPointer(groupId, eventId);
        }

        // Update Group.lastHangoutModified for disassociated groups
        groupTimestampService.updateGroupTimestamps(groupIds);

        logger.info("Disassociated event {} from {} groups by user {}", eventId, groupIds.size(), requestingUserId);
    }

    /**
     * Update all pointer records with basic hangout fields using read-modify-write pattern.
     * This method should be called after updating hangout basic fields (title, description, etc.).
     *
     * Uses optimistic locking with retry to handle concurrent pointer updates.
     *
     * @param hangout The canonical hangout record with updated fields
     */
    private void updatePointersWithBasicFields(Hangout hangout) {
        List<String> associatedGroups = hangout.getAssociatedGroups();

        if (associatedGroups == null || associatedGroups.isEmpty()) {
            logger.debug("No associated groups for hangout {}, skipping pointer update", hangout.getHangoutId());
            return;
        }

        // Update each group's pointer with optimistic locking retry
        for (String groupId : associatedGroups) {
            pointerUpdateService.updatePointerWithRetry(groupId, hangout.getHangoutId(), pointer -> {
                // Update all basic fields from canonical hangout
                pointer.setTitle(hangout.getTitle());
                pointer.setDescription(hangout.getDescription());
                pointer.setLocation(hangout.getLocation());
                pointer.setVisibility(hangout.getVisibility());
                pointer.setMainImagePath(hangout.getMainImagePath());
                pointer.setCarpoolEnabled(hangout.isCarpoolEnabled());

                // Update time fields
                pointer.setTimeInput(hangout.getTimeInput());
                pointer.setStartTimestamp(hangout.getStartTimestamp());
                pointer.setEndTimestamp(hangout.getEndTimestamp());

                // Update ticket coordination fields
                pointer.setTicketLink(hangout.getTicketLink());
                pointer.setTicketsRequired(hangout.getTicketsRequired());
                pointer.setDiscountCode(hangout.getDiscountCode());
            }, "basic fields");
        }
    }

    /**
     * Update all pointer records with the current attribute list from the canonical hangout.
     * This method should be called after any attribute create/update/delete operation.
     *
     * Uses optimistic locking with retry to handle concurrent pointer updates.
     */
    private void updatePointersWithAttributes(String hangoutId) {
        // Get hangout to find associated groups
        Optional<Hangout> hangoutOpt = hangoutRepository.findHangoutById(hangoutId);
        if (hangoutOpt.isEmpty()) {
            logger.warn("Cannot update pointers for non-existent hangout: {}", hangoutId);
            return;
        }

        Hangout hangout = hangoutOpt.get();
        List<String> associatedGroups = hangout.getAssociatedGroups();

        if (associatedGroups == null || associatedGroups.isEmpty()) {
            logger.debug("No associated groups for hangout {}, skipping pointer update", hangoutId);
            return;
        }

        // Get current attribute list from canonical record
        List<HangoutAttribute> attributes = hangoutRepository.findAttributesByHangoutId(hangoutId);

        // Update each group's pointer with optimistic locking retry
        for (String groupId : associatedGroups) {
            pointerUpdateService.updatePointerWithRetry(groupId, hangoutId, pointer -> {
                pointer.setAttributes(new ArrayList<>(attributes));
            }, "attributes");
        }

        // Update group timestamps for ETag invalidation
        groupTimestampService.updateGroupTimestamps(associatedGroups);
    }

    @Override
    public boolean canUserViewHangout(String userId, Hangout hangout) {
        // Check if hangout is public
        if (hangout.getVisibility() == EventVisibility.PUBLIC) {
            return true;
        }
        
        // For invite-only hangouts, check if user is in any associated groups
        if (hangout.getAssociatedGroups() != null) {
            for (String groupId : hangout.getAssociatedGroups()) {
                if (groupRepository.findMembership(groupId, userId).isPresent()) {
                    return true;
                }
            }
        }
        
        return false;
    }

    @Override
    public boolean canUserEditHangout(String userId, Hangout hangout) {
        // Check if user is a member of any associated groups
        // For now, any group member can edit - can be refined to admin-only later
        if (hangout.getAssociatedGroups() != null) {
            for (String groupId : hangout.getAssociatedGroups()) {
                GroupMembership membership = groupRepository.findMembership(groupId, userId).orElse(null);
                if (membership != null) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private HangoutSummaryDTO convertToSummaryDTO(HangoutPointer pointer, String requestingUserId) {
        HangoutSummaryDTO summary = new HangoutSummaryDTO(pointer, requestingUserId);

        // *** NO DATABASE CALL - Use denormalized timeInput ***
        if (pointer.getTimeInput() != null) {
            TimeInfo timeInfo = formatTimeInfoForResponse(pointer.getTimeInput());
            summary.setTimeInfo(timeInfo);
        }

        return summary;
    }

    private TimeInfo formatTimeInfoForResponse(TimeInfo timeInfo) {
        if (timeInfo == null) return null;

        TimeInfo formattedTimeInfo = new TimeInfo();

        // For fuzzy time: only return periodGranularity and periodStart in UTC
        if (timeInfo.getPeriodGranularity() != null) {
            formattedTimeInfo.setPeriodGranularity(timeInfo.getPeriodGranularity());
            if (timeInfo.getPeriodStart() != null) {
                formattedTimeInfo.setPeriodStart(convertToUtcIsoString(timeInfo.getPeriodStart()));
            }
        }
        // For exact time: only return startTime and endTime in UTC
        else if (timeInfo.getStartTime() != null) {
            formattedTimeInfo.setStartTime(convertToUtcIsoString(timeInfo.getStartTime()));
            if (timeInfo.getEndTime() != null) {
                formattedTimeInfo.setEndTime(convertToUtcIsoString(timeInfo.getEndTime()));
            }
        }
        // Ensure other fields are null if not set, as per API contract
        formattedTimeInfo.setPeriodGranularity(formattedTimeInfo.getPeriodGranularity()); // Re-set to ensure null if not set above
        formattedTimeInfo.setPeriodStart(formattedTimeInfo.getPeriodStart());
        formattedTimeInfo.setStartTime(formattedTimeInfo.getStartTime());
        formattedTimeInfo.setEndTime(formattedTimeInfo.getEndTime());

        return formattedTimeInfo;
    }
    
    private String convertToUtcIsoString(String timeString) {
        if (timeString == null) {
            return null;
        }
        
        try {
            // Check if it's already in ISO format (contains 'T' and timezone info)
            if (timeString.contains("T")) {
                // Parse and convert to UTC
                java.time.ZonedDateTime zonedDateTime = java.time.ZonedDateTime.parse(timeString);
                return zonedDateTime.toInstant().toString(); // Converts to UTC ISO format ending with 'Z'
            } else if (timeString.matches("\\d+")) {
                // It's a Unix timestamp, convert to UTC ISO
                long timestamp = Long.parseLong(timeString);
                java.time.Instant instant = java.time.Instant.ofEpochSecond(timestamp);
                return instant.toString();
            } else {
                // Return as-is if format is unknown
                return timeString;
            }
        } catch (Exception e) {
            // Return as-is if parsing fails
            return timeString;
        }
    }

    @Override
    public void setUserInterest(String hangoutId, SetInterestRequest request, String requestingUserId) {
        // Get hangout and authorize
        HangoutDetailData data = hangoutRepository.getHangoutDetailData(hangoutId);
        if (data.getHangout() == null) {
            throw new ResourceNotFoundException("Hangout not found: " + hangoutId);
        }
        if (!canUserViewHangout(requestingUserId, data.getHangout())) {
            throw new UnauthorizedException("Cannot set interest for this hangout");
        }

        // Get existing interest level to determine count change
        String oldStatus = null;
        List<InterestLevel> currentAttendance = data.getAttendance();
        for (InterestLevel interest : currentAttendance) {
            if (requestingUserId.equals(interest.getUserId())) {
                oldStatus = interest.getStatus();
                break;
            }
        }

        // Get user for denormalization
        Optional<UserSummaryDTO> userOpt = userService.getUserSummary(UUID.fromString(requestingUserId));
        if (userOpt.isEmpty()) {
            throw new ResourceNotFoundException("User not found");
        }
        UserSummaryDTO user = userOpt.get();
        String displayName = user.getDisplayName();

        // Create/update InterestLevel
        InterestLevel interestLevel = new InterestLevel(hangoutId, requestingUserId, displayName, request.getStatus());
        interestLevel.setNotes(request.getNotes());
        interestLevel.setMainImagePath(user.getMainImagePath()); // Denormalize user's profile image
        hangoutRepository.saveInterestLevel(interestLevel);

        // Update participant counts using atomic counters
        List<String> associatedGroups = data.getHangout().getAssociatedGroups();
        if (associatedGroups != null && !associatedGroups.isEmpty()) {
            updateParticipantCounts(hangoutId, oldStatus, request.getStatus(), associatedGroups);

            // Denormalize updated interest levels to all group pointers
            List<InterestLevel> updatedInterestLevels = hangoutRepository.getHangoutDetailData(hangoutId).getAttendance();
            for (String groupId : associatedGroups) {
                pointerUpdateService.updatePointerWithRetry(groupId, hangoutId, pointer -> {
                    pointer.setInterestLevels(new ArrayList<>(updatedInterestLevels));
                }, "interest levels");
            }

            // Update group timestamps for ETag invalidation
            groupTimestampService.updateGroupTimestamps(associatedGroups);
        }

        logger.info("Set interest {} for user {} on hangout {}", request.getStatus(), requestingUserId, hangoutId);
    }

    @Override
    public void removeUserInterest(String hangoutId, String requestingUserId) {
        // Authorization check
        HangoutDetailData data = hangoutRepository.getHangoutDetailData(hangoutId);
        if (data.getHangout() == null) {
            throw new ResourceNotFoundException("Hangout not found: " + hangoutId);
        }
        if (!canUserViewHangout(requestingUserId, data.getHangout())) {
            throw new UnauthorizedException("Cannot remove interest for this hangout");
        }

        // Get existing status for count calculation
        String oldStatus = null;
        List<InterestLevel> currentAttendance = data.getAttendance();
        for (InterestLevel interest : currentAttendance) {
            if (requestingUserId.equals(interest.getUserId())) {
                oldStatus = interest.getStatus();
                break;
            }
        }

        // Delete interest level
        hangoutRepository.deleteInterestLevel(hangoutId, requestingUserId);

        // Update participant counts (removal = status changes from X to null)
        List<String> associatedGroups = data.getHangout().getAssociatedGroups();
        if (associatedGroups != null && !associatedGroups.isEmpty()) {
            updateParticipantCounts(hangoutId, oldStatus, null, associatedGroups);

            // Denormalize updated interest levels to all group pointers
            List<InterestLevel> updatedInterestLevels = hangoutRepository.getHangoutDetailData(hangoutId).getAttendance();
            for (String groupId : associatedGroups) {
                pointerUpdateService.updatePointerWithRetry(groupId, hangoutId, pointer -> {
                    pointer.setInterestLevels(new ArrayList<>(updatedInterestLevels));
                }, "interest levels");
            }

            // Update group timestamps for ETag invalidation
            groupTimestampService.updateGroupTimestamps(associatedGroups);
        }

        logger.info("Removed interest for user {} on hangout {}", requestingUserId, hangoutId);
    }

    private void updateParticipantCounts(String hangoutId, String oldStatus, String newStatus,
                                       List<String> associatedGroups) {
        List<String> interested = Arrays.asList("GOING","INTERESTED");
        boolean wasGoing = interested.contains(oldStatus);
        boolean nowGoing = interested.contains(newStatus);

        if (wasGoing == nowGoing) {
            return; // No count change needed
        }

        int delta = nowGoing ? 1 : -1; // +1 if now going, -1 if no longer going

        // Use the atomic counter method
        for (String groupId : associatedGroups) {
            groupRepository.atomicallyUpdateParticipantCount(groupId, hangoutId, delta);
            logger.debug("Updated participantCount by {} for hangout {} in group {}",
                        delta, hangoutId, groupId);
        }
    }
    
    // ============================================================================
    // HANGOUT ATTRIBUTE MANAGEMENT METHODS
    // ============================================================================
    
    @Override
    public HangoutAttributeDTO createAttribute(String hangoutId, CreateAttributeRequest request, String requestingUserId) {
        logger.info("Creating attribute '{}' for hangout {} by user {}", 
            request.getAttributeName(), hangoutId, requestingUserId);
        
        // Authorization check - user must have access to the hangout
        verifyUserCanAccessHangout(hangoutId, requestingUserId);
        
        // Validate request
        if (!request.isValid()) {
            throw new ValidationException("Invalid attribute request: " + request);
        }
        
        // Check for duplicate attribute names (business rule - optional but helpful UX)
        List<HangoutAttribute> existingAttributes = hangoutRepository.findAttributesByHangoutId(hangoutId);
        String trimmedName = request.getTrimmedAttributeName();
        
        boolean nameExists = existingAttributes.stream()
            .anyMatch(attr -> trimmedName.equals(attr.getAttributeName()));
        
        if (nameExists) {
            throw new ValidationException("Attribute with name '" + trimmedName + "' already exists");
        }
        
        // Create new attribute
        HangoutAttribute attribute = new HangoutAttribute(hangoutId, trimmedName, request.getStringValue());
        
        // Save to repository
        HangoutAttribute savedAttribute = hangoutRepository.saveAttribute(attribute);

        // Update pointer records with new attribute list
        updatePointersWithAttributes(hangoutId);

        logger.info("Successfully created attribute {} for hangout {}",
            savedAttribute.getAttributeId(), hangoutId);

        return HangoutAttributeDTO.fromEntity(savedAttribute);
    }
    
    @Override
    public HangoutAttributeDTO updateAttribute(String hangoutId, String attributeId, 
                                             UpdateAttributeRequest request, String requestingUserId) {
        logger.info("Updating attribute {} for hangout {} by user {}", 
            attributeId, hangoutId, requestingUserId);
        
        // Authorization check - user must have access to the hangout
        verifyUserCanAccessHangout(hangoutId, requestingUserId);
        
        // Validate request
        if (!request.isValid()) {
            throw new ValidationException("Invalid attribute update request: " + request);
        }
        
        // Find existing attribute
        HangoutAttribute existingAttribute = hangoutRepository.findAttributeById(hangoutId, attributeId)
            .orElseThrow(() -> new ResourceNotFoundException("Attribute not found: " + attributeId));
        
        // Check for duplicate names if renaming
        String trimmedNewName = request.getTrimmedAttributeName();
        if (request.isNameChange(existingAttribute.getAttributeName())) {
            List<HangoutAttribute> allAttributes = hangoutRepository.findAttributesByHangoutId(hangoutId);
            
            boolean nameConflict = allAttributes.stream()
                .filter(attr -> !attr.getAttributeId().equals(attributeId)) // Exclude current attribute
                .anyMatch(attr -> trimmedNewName.equals(attr.getAttributeName()));
            
            if (nameConflict) {
                throw new ValidationException("Attribute with name '" + trimmedNewName + "' already exists");
            }
        }
        
        // Update attribute fields
        existingAttribute.updateAttribute(trimmedNewName, request.getStringValue());
        
        // Save updated attribute
        HangoutAttribute savedAttribute = hangoutRepository.saveAttribute(existingAttribute);

        // Update pointer records with updated attribute list
        updatePointersWithAttributes(hangoutId);

        logger.info("Successfully updated attribute {} for hangout {}", attributeId, hangoutId);

        return HangoutAttributeDTO.fromEntity(savedAttribute);
    }
    
    @Override
    public void deleteAttribute(String hangoutId, String attributeId, String requestingUserId) {
        logger.info("Deleting attribute {} for hangout {} by user {}", 
            attributeId, hangoutId, requestingUserId);
        
        // Authorization check - user must have access to the hangout
        verifyUserCanAccessHangout(hangoutId, requestingUserId);
        
        // Delete attribute (idempotent operation)
        hangoutRepository.deleteAttribute(hangoutId, attributeId);

        // Update pointer records with updated attribute list (attribute now removed)
        updatePointersWithAttributes(hangoutId);

        logger.info("Successfully deleted attribute {} for hangout {}", attributeId, hangoutId);
    }
    
    @Override
    public void verifyUserCanAccessHangout(String hangoutId, String userId) {
        // Get hangout and check authorization using existing logic
        Hangout hangout = hangoutRepository.findHangoutById(hangoutId)
            .orElseThrow(() -> new ResourceNotFoundException("Hangout not found: " + hangoutId));
        
        if (!canUserViewHangout(userId, hangout)) {
            throw new UnauthorizedException("User does not have access to this hangout");
        }
    }
    
    /**
     * Transform raw poll data from single table query into PollWithOptionsDTO.
     * This method performs the same runtime vote counting as PollService but uses data
     * already retrieved from the single table query.
     */
    private List<PollWithOptionsDTO> transformPollData(HangoutDetailData hangoutDetail, String requestingUserId) {
        return com.bbthechange.inviter.util.HangoutDataTransformer.transformPollData(
                hangoutDetail.getPolls(),
                hangoutDetail.getPollOptions(),
                hangoutDetail.getVotes(),
                requestingUserId
        );
    }

    /**
     * Get display name for creator user with fallback to "Unknown".
     */
    private String getCreatorDisplayName(String userId) {
        try {
            Optional<UserSummaryDTO> userOpt = userService.getUserSummary(UUID.fromString(userId));
            if (userOpt.isPresent()) {
                UserSummaryDTO user = userOpt.get();
                return user.getDisplayName();
            }
        } catch (Exception e) {
            logger.warn("Failed to get display name for user {}: {}", userId, e.getMessage());
        }
        return "Unknown";
    }

    @Override
    public void resyncHangoutPointers(String hangoutId) {
        logger.info("Manually resyncing all pointer data for hangout {}", hangoutId);

        // Get hangout to find associated groups
        Optional<Hangout> hangoutOpt = hangoutRepository.findHangoutById(hangoutId);
        if (hangoutOpt.isEmpty()) {
            logger.warn("Cannot resync pointers for non-existent hangout: {}", hangoutId);
            return;
        }

        Hangout hangout = hangoutOpt.get();
        List<String> associatedGroups = hangout.getAssociatedGroups();

        if (associatedGroups == null || associatedGroups.isEmpty()) {
            logger.warn("No associated groups for hangout {}, skipping pointer resync", hangoutId);
            return;
        }

        // Get all denormalized data from canonical records
        HangoutDetailData detailData = hangoutRepository.getHangoutDetailData(hangoutId);
        List<HangoutAttribute> attributes = hangoutRepository.findAttributesByHangoutId(hangoutId);

        // Update each group's pointer with ALL denormalized data
        for (String groupId : associatedGroups) {
            pointerUpdateService.updatePointerWithRetry(groupId, hangoutId, pointer -> {
                // Update basic fields
                pointer.setTitle(hangout.getTitle());
                pointer.setDescription(hangout.getDescription());
                pointer.setLocation(hangout.getLocation());
                pointer.setVisibility(hangout.getVisibility());
                pointer.setMainImagePath(hangout.getMainImagePath());
                pointer.setCarpoolEnabled(hangout.isCarpoolEnabled());

                // Update time fields
                pointer.setTimeInput(hangout.getTimeInput());
                pointer.setStartTimestamp(hangout.getStartTimestamp());
                pointer.setEndTimestamp(hangout.getEndTimestamp());
                pointer.setSeriesId(hangout.getSeriesId());

                // Update poll data
                pointer.setPolls(new ArrayList<>(detailData.getPolls()));
                pointer.setPollOptions(new ArrayList<>(detailData.getPollOptions()));
                pointer.setVotes(new ArrayList<>(detailData.getVotes()));

                // Update carpool data
                pointer.setCars(new ArrayList<>(detailData.getCars()));
                pointer.setCarRiders(new ArrayList<>(detailData.getCarRiders()));
                pointer.setNeedsRide(new ArrayList<>(detailData.getNeedsRide()));

                // Update attributes
                pointer.setAttributes(new ArrayList<>(attributes));

                // Update interest levels
                pointer.setInterestLevels(new ArrayList<>(detailData.getAttendance()));
            }, "complete resync");
        }

        logger.info("Successfully resynced {} pointer(s) for hangout {}", associatedGroups.size(), hangoutId);
    }

}