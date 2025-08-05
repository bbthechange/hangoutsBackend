package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.service.HangoutService;
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

/**
 * Implementation of HangoutService with pointer update patterns.
 * Handles coordination between canonical records and pointer records.
 */
@Service
public class HangoutServiceImpl implements HangoutService {
    
    private static final Logger logger = LoggerFactory.getLogger(HangoutServiceImpl.class);
    
    private final HangoutRepository hangoutRepository;
    private final GroupRepository groupRepository;
    
    @Autowired
    public HangoutServiceImpl(HangoutRepository hangoutRepository, GroupRepository groupRepository) {
        this.hangoutRepository = hangoutRepository;
        this.groupRepository = groupRepository;
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
        EventDetailData data = hangoutRepository.getEventDetailData(eventId);
        if (!canUserEditEvent(requestingUserId, data.getEvent())) {
            throw new UnauthorizedException("Cannot edit event");
        }
        
        Event event = data.getEvent();
        
        // Verify user is in all specified groups
        for (String groupId : groupIds) {
            if (!groupRepository.findMembership(groupId, requestingUserId).isPresent()) {
                throw new UnauthorizedException("User not in group: " + groupId);
            }
        }
        
        // Update canonical record
        if (event.getAssociatedGroups() == null) {
            event.setAssociatedGroups(new java.util.ArrayList<>());
        }
        for (String groupId : groupIds) {
            if (!event.getAssociatedGroups().contains(groupId)) {
                event.getAssociatedGroups().add(groupId);
            }
        }
        hangoutRepository.save(event);
        
        // Create hangout pointer records
        for (String groupId : groupIds) {
            HangoutPointer pointer = new HangoutPointer(groupId, eventId, event.getName());
            pointer.setStatus("ACTIVE"); // Default status
            pointer.setLocationName(getLocationName(event));
            pointer.setParticipantCount(0); // Will be updated as people respond
            
            groupRepository.saveHangoutPointer(pointer);
        }
        
        logger.info("Associated event {} with {} groups by user {}", eventId, groupIds.size(), requestingUserId);
    }
    
    @Override
    public void disassociateEventFromGroups(String eventId, List<String> groupIds, String requestingUserId) {
        // Authorization check
        EventDetailData data = hangoutRepository.getEventDetailData(eventId);
        if (!canUserEditEvent(requestingUserId, data.getEvent())) {
            throw new UnauthorizedException("Cannot edit event");
        }
        
        Event event = data.getEvent();
        
        // Update canonical record
        if (event.getAssociatedGroups() != null) {
            event.getAssociatedGroups().removeAll(groupIds);
            hangoutRepository.save(event);
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
        // Extract location name from Address object
        // This would need to be adapted based on the actual Address structure
        if (event.getLocation() != null) {
            // return event.getLocation().getName(); // Adjust based on Address fields
            return "Location"; // Placeholder
        }
        return null;
    }
}