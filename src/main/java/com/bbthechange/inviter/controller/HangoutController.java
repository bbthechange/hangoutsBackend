package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.service.HangoutService;
import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.model.Hangout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * REST controller for hangout/event management with item collection patterns.
 * Provides both new /hangouts endpoints and legacy /events endpoints.
 * Extends BaseController for consistent error handling and user extraction.
 */
@RestController
@Validated
public class HangoutController extends BaseController {
    
    private static final Logger logger = LoggerFactory.getLogger(HangoutController.class);
    
    private final HangoutService hangoutService;
    
    @Autowired
    public HangoutController(HangoutService hangoutService) {
        this.hangoutService = hangoutService;
    }
    
    // NEW HANGOUT API ENDPOINTS
    
    @PostMapping("/hangouts")
    public ResponseEntity<Hangout> createHangout(
            @Valid @RequestBody CreateHangoutRequest request,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        
        Hangout hangout = hangoutService.createHangout(request, userId);
        logger.info("Created hangout {} by user {}", hangout.getHangoutId(), userId);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(hangout);
    }
    
    @GetMapping("/hangouts/{hangoutId}")
    public ResponseEntity<HangoutDetailDTO> getHangout(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid hangout ID format") String hangoutId,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        
        HangoutDetailDTO detail = hangoutService.getHangoutDetail(hangoutId, userId);
        
        logger.debug("Retrieved hangout detail for {} - {} polls, {} cars, {} attendance records", 
            hangoutId, 
            detail.getPolls().size(),
            detail.getCars().size(), 
            detail.getAttendance().size());
            
        return ResponseEntity.ok(detail);
    }
    
    @PatchMapping("/hangouts/{hangoutId}")
    public ResponseEntity<Void> updateHangout(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid hangout ID format") String hangoutId,
            @Valid @RequestBody UpdateHangoutRequest request,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        
        hangoutService.updateHangout(hangoutId, request, userId);
        logger.info("Updated hangout {} by user {}", hangoutId, userId);
        
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/hangouts/{hangoutId}")
    public ResponseEntity<Void> deleteHangout(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid hangout ID format") String hangoutId,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        
        hangoutService.deleteHangout(hangoutId, userId);
        logger.info("Deleted hangout {} by user {}", hangoutId, userId);
        
        return ResponseEntity.noContent().build();
    }
    
    // LEGACY EVENT API ENDPOINTS (for backward compatibility)
    @Deprecated
    @GetMapping("/events/{eventId}/detail")
    public ResponseEntity<EventDetailDTO> getEventDetail(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid event ID format") String eventId,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        
        // Single item collection query gets ALL data - the power pattern!
        EventDetailDTO detail = hangoutService.getEventDetail(eventId, userId);
        
        logger.debug("Retrieved event detail for {} - {} polls, {} cars, {} attendance records", 
            eventId, 
            detail.getPolls().size(),
            detail.getCars().size(), 
            detail.getAttendance().size());
            
        return ResponseEntity.ok(detail);
    }
    
    @PatchMapping("/{eventId}")
    public ResponseEntity<Void> updateEvent(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid event ID format") String eventId,
            @Valid @RequestBody UpdateEventRequest request,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        
        // This follows the multi-step pointer update pattern
        if (request.getName() != null) {
            hangoutService.updateEventTitle(eventId, request.getName(), userId);
            logger.info("Updated title for event {} by user {}", eventId, userId);
        }
        
        if (request.getDescription() != null) {
            hangoutService.updateEventDescription(eventId, request.getDescription(), userId);
            logger.info("Updated description for event {} by user {}", eventId, userId);
        }
        
        // Handle group associations
        if (request.getAssociatedGroups() != null) {
            hangoutService.associateEventWithGroups(eventId, request.getAssociatedGroups(), userId);
            logger.info("Updated group associations for event {} by user {}", eventId, userId);
        }
        
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{eventId}/groups")
    public ResponseEntity<Void> associateWithGroups(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid event ID format") String eventId,
            @Valid @RequestBody AssociateGroupsRequest request,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        
        hangoutService.associateEventWithGroups(eventId, request.getGroupIds(), userId);
        logger.info("Associated event {} with {} groups by user {}", eventId, request.getGroupIds().size(), userId);
        
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/{eventId}/groups")
    public ResponseEntity<Void> disassociateFromGroups(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid event ID format") String eventId,
            @Valid @RequestBody AssociateGroupsRequest request,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        
        hangoutService.disassociateEventFromGroups(eventId, request.getGroupIds(), userId);
        logger.info("Disassociated event {} from {} groups by user {}", eventId, request.getGroupIds().size(), userId);
        
        return ResponseEntity.ok().build();
    }
}