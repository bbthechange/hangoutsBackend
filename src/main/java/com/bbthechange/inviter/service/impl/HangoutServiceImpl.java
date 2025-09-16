package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.service.HangoutService;
import com.bbthechange.inviter.service.FuzzyTimeService;
import com.bbthechange.inviter.service.UserService;
import com.bbthechange.inviter.service.EventSeriesService;
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

    @Autowired
    public HangoutServiceImpl(HangoutRepository hangoutRepository, GroupRepository groupRepository,
                              FuzzyTimeService fuzzyTimeService, UserService userService,
                              @Lazy EventSeriesService eventSeriesService) {
        this.hangoutRepository = hangoutRepository;
        this.groupRepository = groupRepository;
        this.fuzzyTimeService = fuzzyTimeService;
        this.userService = userService;
        this.eventSeriesService = eventSeriesService;
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
                pointer.setLocationName(getLocationName(hangout.getLocation()));
                pointer.setParticipantCount(0);
                
                // *** CRITICAL: Denormalize ALL time information ***
                pointer.setTimeInput(hangout.getTimeInput());           // For API response
                pointer.setGsi1pk("GROUP#" + groupId);                  // GSI primary key
                pointer.setStartTimestamp(hangout.getStartTimestamp()); // GSI sort key
                pointer.setEndTimestamp(hangout.getEndTimestamp());     // For completeness

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
        
        // Save everything in a single transaction
        hangout = hangoutRepository.createHangoutWithAttributes(hangout, pointers, attributes);
        
        logger.info("Created hangout {} with {} attributes by user {}", hangout.getHangoutId(), attributes.size(), requestingUserId);
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

        return new HangoutDetailDTO(
                hangout,
            attributeDTOs, // Now includes actual attributes from single query
            pollsWithOptions,
            hangoutDetail.getCars(),
            hangoutDetail.getVotes(),
            hangoutDetail.getAttendance(),
            hangoutDetail.getCarRiders(),
            needsRideDTOs
        );
    }
    
    @Override
    public void updateHangout(String hangoutId, UpdateHangoutRequest request, String requestingUserId) {
        // Get and authorize
        Hangout hangout = hangoutRepository.findHangoutById(hangoutId)
            .orElseThrow(() -> new ResourceNotFoundException("Hangout not found: " + hangoutId));
        
        if (!canUserEditHangout(requestingUserId, hangout)) {
            throw new UnauthorizedException("Cannot edit hangout");
        }
        
        // Update canonical record
        boolean needsPointerUpdate = false;
        Map<String, Object> pointerUpdates = new HashMap<>();
        
        if (request.getTitle() != null && !request.getTitle().equals(hangout.getTitle())) {
            hangout.setTitle(request.getTitle());
            pointerUpdates.put("title", request.getTitle());
            needsPointerUpdate = true;
        }
        
        if (request.getDescription() != null) {
            hangout.setDescription(request.getDescription());
        }
        
        if (request.getTimeInfo() != null) {
            hangout.setTimeInput(request.getTimeInfo());
            
            // Convert timeInput to canonical timestamps
            FuzzyTimeService.TimeConversionResult timeResult = fuzzyTimeService.convert(request.getTimeInfo());
            hangout.setStartTimestamp(timeResult.startTimestamp);
            hangout.setEndTimestamp(timeResult.endTimestamp);
            // TODO participantCount? Not used yet, not sure we should use it
            if (hangout.getAssociatedGroups() != null) {
                needsPointerUpdate = true;
                pointerUpdates.put("timeInput", request.getTimeInfo());
                pointerUpdates.put("startTimestamp", timeResult.startTimestamp);
                pointerUpdates.put("endTimestamp", timeResult.endTimestamp);
            }
        }

        if (request.getLocation() != null) {
            hangout.setLocation(request.getLocation());
            String locationName = getLocationName(request.getLocation());
            if (locationName != null) {
                pointerUpdates.put("locationName", locationName);
                needsPointerUpdate = true;
            }
        }
        
        if (request.getVisibility() != null) {
            hangout.setVisibility(request.getVisibility());
        }
        
        if (request.getMainImagePath() != null) {
            hangout.setMainImagePath(request.getMainImagePath());
        }
        
        hangout.setCarpoolEnabled(request.isCarpoolEnabled());
        
        // Save canonical record
        hangoutRepository.createHangout(hangout); // Using createHangout as it's a putItem
        
        // Update pointer records if needed
        if (needsPointerUpdate && hangout.getAssociatedGroups() != null) {
            updatePointerRecords(hangoutId, hangout.getAssociatedGroups(), pointerUpdates);
        }
        
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
            .map(this::convertToSummaryDTO)
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
        
        // Multi-step process from implementation plan:
        
        // Step 1: Update canonical record first
        Hangout hangout = data.getHangout();
        hangout.setTitle(newTitle);
        hangoutRepository.save(hangout);
        
        // Step 2: Get associated groups list from canonical record
        List<String> associatedGroups = hangout.getAssociatedGroups();
        
        // Step 3: Update each pointer record
        if (associatedGroups != null && !associatedGroups.isEmpty()) {
            updatePointerRecords(eventId, associatedGroups, Map.of("title", newTitle));
        }
        
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
        
        logger.info("Updated description for event {} by user {}", eventId, requestingUserId);
    }
    
    @Override
    public void updateEventLocation(String eventId, String newLocationName, String requestingUserId) {
        // Authorization check first
        HangoutDetailData data = hangoutRepository.getHangoutDetailData(eventId);
        if (!canUserEditHangout(requestingUserId, data.getHangout())) {
            throw new UnauthorizedException("Cannot edit event");
        }
        
        // Step 1: Update canonical record
        Hangout hangout = data.getHangout();
        // TODO: Properly implement Address update logic based on Address structure
        if (hangout.getLocation() != null) {
            // This would need to be adapted based on the actual Address structure
            // hangout.getLocation().setName(newLocationName);
        }
        hangoutRepository.save(hangout);
        
        // Step 2: Update pointer records with location
        List<String> associatedGroups = hangout.getAssociatedGroups();
        if (associatedGroups != null && !associatedGroups.isEmpty()) {
            updatePointerRecords(eventId, associatedGroups, Map.of("locationName", newLocationName));
        }
        
        logger.info("Updated location for event {} to '{}' by user {}", eventId, newLocationName, requestingUserId);
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
            pointer.setLocationName(getLocationName(hangout.getLocation()));
            // TODO this looks wrong
            pointer.setParticipantCount(0); // Will be updated as people respond

            // Set GSI fields for EntityTimeIndex
            pointer.setGsi1pk("GROUP#" + groupId);
            if (hangout.getTimeInput() != null) {
                pointer.setTimeInput(hangout.getTimeInput());
                FuzzyTimeService.TimeConversionResult timeResult = fuzzyTimeService.convert(hangout.getTimeInput());
                pointer.setStartTimestamp(timeResult.startTimestamp);
                pointer.setEndTimestamp(timeResult.endTimestamp);
            }

            groupRepository.saveHangoutPointer(pointer);
        }
        
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
        
        logger.info("Disassociated event {} from {} groups by user {}", eventId, groupIds.size(), requestingUserId);
    }
    

    private void updatePointerRecords(String eventId, List<String> groupIds, Map<String, Object> updates) {
        // Convert string updates to AttributeValue updates
        Map<String, AttributeValue> updateValues = new HashMap<>();
        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            AttributeValue attributeValue;

            // Type checking to build the correct AttributeValue
            if (value instanceof String) {
                attributeValue = AttributeValue.builder().s((String) value).build();
            } else if (value instanceof Number) {
                attributeValue = AttributeValue.builder().n(value.toString()).build();
            } else if (value instanceof TimeInfo) {
                // Handle TimeInfo objects by storing as a DynamoDB Map (M type)
                TimeInfo timeInfo = (TimeInfo) value;
                Map<String, AttributeValue> timeInfoMap = new HashMap<>();
                
                if (timeInfo.getStartTime() != null) {
                    timeInfoMap.put("startTime", AttributeValue.builder().s(timeInfo.getStartTime()).build());
                }
                if (timeInfo.getEndTime() != null) {
                    timeInfoMap.put("endTime", AttributeValue.builder().s(timeInfo.getEndTime()).build());
                }
                if (timeInfo.getPeriodGranularity() != null) {
                    timeInfoMap.put("periodGranularity", AttributeValue.builder().s(timeInfo.getPeriodGranularity()).build());
                }
                if (timeInfo.getPeriodStart() != null) {
                    timeInfoMap.put("periodStart", AttributeValue.builder().s(timeInfo.getPeriodStart()).build());
                }
                
                attributeValue = AttributeValue.builder().m(timeInfoMap).build();
            } else {
                // Add more types here if needed (e.g., Boolean)
                logger.warn("Unsupported type in pointer update for key {}: {}", key, value == null? null : value.getClass().getName());
                continue; // Skip unsupported types
            }
            updateValues.put(key, attributeValue);
        }
        
        // Update each group's hangout pointer
        for (String groupId : groupIds) {
            try {
                groupRepository.updateHangoutPointer(groupId, eventId, updateValues);
            } catch (Exception e) {
                logger.warn("Failed to update hangout pointer for group {} and event {}: {}", 
                    groupId, eventId, e.getMessage());
            }
        }
    }
    
    private String getLocationName(Event event) {
        return getLocationName(event.getLocation());
    }
    
    private String getLocationName(Address location) {
        if (location == null) {
            return null;
        }
        
        // Create a readable location name from Address components
        StringBuilder locationName = new StringBuilder();
        
        if (location.getStreetAddress() != null && !location.getStreetAddress().trim().isEmpty()) {
            locationName.append(location.getStreetAddress());
        }
        
        if (location.getCity() != null && !location.getCity().trim().isEmpty()) {
            if (locationName.length() > 0) {
                locationName.append(", ");
            }
            locationName.append(location.getCity());
        }
        
        if (location.getState() != null && !location.getState().trim().isEmpty()) {
            if (locationName.length() > 0) {
                locationName.append(", ");
            }
            locationName.append(location.getState());
        }
        
        return locationName.length() > 0 ? locationName.toString() : null;
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
    
    private HangoutSummaryDTO convertToSummaryDTO(HangoutPointer pointer) {
        HangoutSummaryDTO summary = new HangoutSummaryDTO(pointer);
        
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
        Optional<User> userOpt = userService.getUserById(UUID.fromString(requestingUserId));
        if (userOpt.isEmpty()) {
            throw new ResourceNotFoundException("User not found");
        }
        User user = userOpt.get();
        String displayName = user.getDisplayName() != null ? user.getDisplayName() : user.getUsername();

        // Create/update InterestLevel
        InterestLevel interestLevel = new InterestLevel(hangoutId, requestingUserId, displayName, request.getStatus());
        interestLevel.setNotes(request.getNotes());
        hangoutRepository.saveInterestLevel(interestLevel);

        // Update participant counts using atomic counters
        List<String> associatedGroups = data.getHangout().getAssociatedGroups();
        if (associatedGroups != null && !associatedGroups.isEmpty()) {
            updateParticipantCounts(hangoutId, oldStatus, request.getStatus(), associatedGroups);
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
        List<Poll> polls = hangoutDetail.getPolls();
        List<PollOption> allOptions = hangoutDetail.getPollOptions();
        List<Vote> allVotes = hangoutDetail.getVotes();
        
        // Group options and votes by poll ID
        Map<String, List<PollOption>> optionsByPoll = allOptions.stream()
            .collect(Collectors.groupingBy(PollOption::getPollId));
            
        Map<String, List<Vote>> votesByPoll = allVotes.stream()
            .collect(Collectors.groupingBy(Vote::getPollId));
        
        // Build hierarchical DTOs with runtime vote counting
        return polls.stream()
            .map(poll -> {
                List<PollOption> options = optionsByPoll.getOrDefault(poll.getPollId(), List.of());
                List<Vote> pollVotes = votesByPoll.getOrDefault(poll.getPollId(), List.of());
                
                // Calculate vote counts by option at runtime
                Map<String, Long> voteCountsByOption = pollVotes.stream()
                    .collect(Collectors.groupingBy(Vote::getOptionId, Collectors.counting()));
                
                List<PollOptionDTO> optionDTOs = options.stream()
                    .map(option -> {
                        // Runtime calculation - no denormalized count field needed
                        int voteCount = voteCountsByOption.getOrDefault(option.getOptionId(), 0L).intValue();
                        
                        boolean userVoted = pollVotes.stream()
                            .anyMatch(vote -> vote.getOptionId().equals(option.getOptionId()) 
                                           && vote.getUserId().equals(requestingUserId));
                        
                        return new PollOptionDTO(option.getOptionId(), option.getText(), 
                                               voteCount, userVoted);
                    })
                    .collect(Collectors.toList());
                
                // Total votes = sum of all votes for this poll
                int totalVotes = pollVotes.size();
                
                return new PollWithOptionsDTO(poll, optionDTOs, totalVotes);
            })
            .collect(Collectors.toList());
    }
}