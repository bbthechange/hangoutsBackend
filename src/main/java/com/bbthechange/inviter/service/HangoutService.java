package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.model.Event;
import com.bbthechange.inviter.model.Hangout;

import java.util.List;

/**
 * Service interface for hangout/event management with pointer update patterns.
 * Handles the complex coordination between canonical records and pointer records.
 */
public interface HangoutService {
    
    // New Hangout management methods
    /**
     * Create a new hangout and associate it with groups (creates pointer records).
     */
    Hangout createHangout(CreateHangoutRequest request, String requestingUserId);
    
    /**
     * Get hangout details by ID using item collection pattern.
     */
    HangoutDetailDTO getHangoutDetail(String hangoutId, String requestingUserId);
    
    /**
     * Update hangout metadata with pointer coordination.
     */
    void updateHangout(String hangoutId, UpdateHangoutRequest request, String requestingUserId);
    
    /**
     * Delete hangout and all associated data (canonical + pointer records).
     */
    void deleteHangout(String hangoutId, String requestingUserId);
    
    /**
     * Get all hangouts for a user (both direct invites and group memberships).
     * Returns chronologically sorted list using EntityTimeIndex GSI.
     */
    List<HangoutSummaryDTO> getHangoutsForUser(String userId);

    boolean canUserViewHangout(String userId, Hangout hangout);
    
    boolean canUserEditHangout(String userId, Hangout hangout);

    /**
     * Set user interest level for a hangout with atomic participant count updates.
     */
    void setUserInterest(String hangoutId, SetInterestRequest request, String requestingUserId);
    
    /**
     * Remove user interest level from a hangout with atomic participant count updates.
     */
    void removeUserInterest(String hangoutId, String requestingUserId);
    
    // Hangout Attribute management methods
    /**
     * Create a new attribute for a hangout.
     * User must have access to the hangout.
     */
    HangoutAttributeDTO createAttribute(String hangoutId, CreateAttributeRequest request, String requestingUserId);
    
    /**
     * Update an existing attribute by ID (supports renaming).
     * User must have access to the hangout.
     */
    HangoutAttributeDTO updateAttribute(String hangoutId, String attributeId, UpdateAttributeRequest request, String requestingUserId);
    
    /**
     * Delete an attribute by ID.
     * User must have access to the hangout. Idempotent operation.
     */
    void deleteAttribute(String hangoutId, String attributeId, String requestingUserId);
    
    /**
     * Verify user can access hangout (used by attribute operations).
     * Throws UnauthorizedException if user lacks access.
     */
    void verifyUserCanAccessHangout(String hangoutId, String userId);
    
    // Legacy event methods for backward compatibility
    
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
    
}