package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.service.HangoutService;
import com.bbthechange.inviter.service.FuzzyTimeService;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
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
    
    @Autowired
    public HangoutServiceImpl(HangoutRepository hangoutRepository, GroupRepository groupRepository,
                              FuzzyTimeService fuzzyTimeService) {
        this.hangoutRepository = hangoutRepository;
        this.groupRepository = groupRepository;
        this.fuzzyTimeService = fuzzyTimeService;
    }
    
    @Override
    public Hangout createHangout(CreateHangoutRequest request, String requestingUserId) {
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
        
        // Save canonical hangout record
        hangout = hangoutRepository.createHangout(hangout);
        
        // Create hangout pointer records for each associated group
        if (request.getAssociatedGroups() != null) {
            for (String groupId : request.getAssociatedGroups()) {
                HangoutPointer pointer = new HangoutPointer(groupId, hangout.getHangoutId(), hangout.getTitle());
                pointer.setStatus("ACTIVE");
                pointer.setLocationName(getLocationName(hangout.getLocation()));
                pointer.setParticipantCount(0);
                
                // *** CRITICAL: Denormalize ALL time information ***
                pointer.setTimeInput(hangout.getTimeInput());           // For API response
                pointer.setStartTimestamp(hangout.getStartTimestamp()); // For GSI sorting  
                pointer.setEndTimestamp(hangout.getEndTimestamp());     // For completeness
                
                // Set GSI fields for EntityTimeIndex
                pointer.setGSI1PK("GROUP#" + groupId);
                if (hangout.getStartTimestamp() != null) {
                    pointer.setGSI1SK("T#" + hangout.getStartTimestamp());
                }
                
                groupRepository.saveHangoutPointer(pointer);
            }
        }
        
        logger.info("Created hangout {} by user {}", hangout.getHangoutId(), requestingUserId);
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
        
        // Transform to DTO with formatted timeInfo
        TimeInfo timeInfo = formatTimeInfoForResponse(hangout.getTimeInput());
        hangout.setTimeInput(timeInfo);
        return new HangoutDetailDTO(
                hangout,
            hangoutDetail.getPolls(),
            hangoutDetail.getCars(),
            hangoutDetail.getVotes(),
            hangoutDetail.getAttendance(),
            hangoutDetail.getCarRiders()
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
        Map<String, String> pointerUpdates = new HashMap<>();
        
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
            
            // Time change requires pointer updates for GSI1SK
            if (hangout.getAssociatedGroups() != null) {
                needsPointerUpdate = true;
                pointerUpdates.put("GSI1SK", "T#" + timeResult.startTimestamp);
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
        
        // Delete pointer records first
        if (hangout.getAssociatedGroups() != null) {
            for (String groupId : hangout.getAssociatedGroups()) {
                groupRepository.deleteHangoutPointer(groupId, hangoutId);
            }
        }
        
        // Delete canonical record and all associated data (polls, cars, etc.)
        hangoutRepository.deleteHangout(hangoutId);
        
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
    public EventDetailDTO getEventDetail(String eventId, String requestingUserId) {
        // Single item collection query gets EVERYTHING (the power pattern!)
        EventDetailData data = hangoutRepository.getEventDetailData(eventId);
        
        // Authorization check
        if (!canUserViewEvent(requestingUserId, data.getEvent())) {
            throw new UnauthorizedException("Cannot view event");
        }
        
        // All data fetched in one query - just transform to DTO
        return new EventDetailDTO(
            data.getEvent(),
            data.getPolls(),
            data.getCars(),
            data.getVotes(),
            data.getAttendance()
        );
    }
    
    @Override
    public void updateEventTitle(String eventId, String newTitle, String requestingUserId) {
        // Authorization check first
        EventDetailData data = hangoutRepository.getEventDetailData(eventId);
        if (!canUserEditEvent(requestingUserId, data.getEvent())) {
            throw new UnauthorizedException("Cannot edit event");
        }
        
        // Multi-step process from implementation plan:
        
        // Step 1: Update canonical record first
        Event event = data.getEvent();
        event.setName(newTitle);
        hangoutRepository.save(event);
        
        // Step 2: Get associated groups list from canonical record
        List<String> associatedGroups = event.getAssociatedGroups();
        
        // Step 3: Update each pointer record
        if (associatedGroups != null && !associatedGroups.isEmpty()) {
            updatePointerRecords(eventId, associatedGroups, Map.of("title", newTitle));
        }
        
        logger.info("Updated title for event {} to '{}' by user {}", eventId, newTitle, requestingUserId);
    }
    
    @Override
    public void updateEventDescription(String eventId, String newDescription, String requestingUserId) {
        // Authorization check first
        EventDetailData data = hangoutRepository.getEventDetailData(eventId);
        if (!canUserEditEvent(requestingUserId, data.getEvent())) {
            throw new UnauthorizedException("Cannot edit event");
        }
        
        // Step 1: Update canonical record
        Event event = data.getEvent();
        event.setDescription(newDescription);
        hangoutRepository.save(event);
        
        logger.info("Updated description for event {} by user {}", eventId, requestingUserId);
    }
    
    @Override
    public void updateEventLocation(String eventId, String newLocationName, String requestingUserId) {
        // Authorization check first
        EventDetailData data = hangoutRepository.getEventDetailData(eventId);
        if (!canUserEditEvent(requestingUserId, data.getEvent())) {
            throw new UnauthorizedException("Cannot edit event");
        }
        
        // Step 1: Update canonical record
        Event event = data.getEvent();
        // Note: Assuming location has a name field - may need to adjust based on Address structure
        if (event.getLocation() != null) {
            // This would need to be adapted based on the actual Address structure
            // event.getLocation().setName(newLocationName);
        }
        hangoutRepository.save(event);
        
        // Step 2: Update pointer records with location
        List<String> associatedGroups = event.getAssociatedGroups();
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
            pointer.setGSI1PK("GROUP#" + groupId);
            if (hangout.getTimeInput() != null) {
                pointer.setTimeInput(hangout.getTimeInput());
                FuzzyTimeService.TimeConversionResult timeResult = fuzzyTimeService.convert(hangout.getTimeInput());
                pointer.setStartTimestamp(timeResult.startTimestamp);
                pointer.setGSI1SK("T#" + timeResult.startTimestamp);
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
    
    @Override
    public boolean canUserViewEvent(String userId, Event event) {
        // TODO: Implement proper authorization logic
        // For now, basic check based on event visibility and group membership
        
        if (event.getVisibility() == EventVisibility.PUBLIC) {
            return true;
        }
        
        // For invite-only events, check if user is in any associated groups
        if (event.getAssociatedGroups() != null) {
            for (String groupId : event.getAssociatedGroups()) {
                if (groupRepository.findMembership(groupId, userId).isPresent()) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    @Override
    public boolean canUserEditEvent(String userId, Event event) {
        // TODO: Implement proper edit authorization logic
        // For now, check if user is admin in any associated groups
        
        if (event.getAssociatedGroups() != null) {
            for (String groupId : event.getAssociatedGroups()) {
                GroupMembership membership = groupRepository.findMembership(groupId, userId).orElse(null);
                if (membership != null) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private void updatePointerRecords(String eventId, List<String> groupIds, Map<String, String> updates) {
        // Convert string updates to AttributeValue updates
        Map<String, AttributeValue> updateValues = new HashMap<>();
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            updateValues.put(":" + entry.getKey(), AttributeValue.builder().s(entry.getValue()).build());
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
    
    private boolean canUserViewHangout(String userId, Hangout hangout) {
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
    
    private boolean canUserEditHangout(String userId, Hangout hangout) {
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
}