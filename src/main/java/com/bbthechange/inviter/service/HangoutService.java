package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.model.Event;

/**
 * Service interface for hangout/event management with pointer update patterns.
 * Handles the complex coordination between canonical records and pointer records.
 */
public interface HangoutService {
    
    /**
     * Get complete event details using item collection pattern.
     * Single query gets ALL data - the power pattern!
     */
    EventDetailDTO getEventDetail(String eventId, String requestingUserId);
    
    /**
     * Update event title with pointer coordination.
     * Follows multi-step pointer update pattern from implementation plan.
     */
    void updateEventTitle(String eventId, String newTitle, String requestingUserId);
    
    /**
     * Update event description with pointer coordination.
     */
    void updateEventDescription(String eventId, String newDescription, String requestingUserId);
    
    /**
     * Update event location with pointer coordination.
     */
    void updateEventLocation(String eventId, String newLocationName, String requestingUserId);
    
    /**
     * Associate event with groups (creates hangout pointers).
     */
    void associateEventWithGroups(String eventId, java.util.List<String> groupIds, String requestingUserId);
    
    /**
     * Remove event association from groups (removes hangout pointers).
     */
    void disassociateEventFromGroups(String eventId, java.util.List<String> groupIds, String requestingUserId);
    
    /**
     * Check if user can view the event (authorization).
     */
    boolean canUserViewEvent(String userId, Event event);
    
    /**
     * Check if user can edit the event (authorization).
     */
    boolean canUserEditEvent(String userId, Event event);
}