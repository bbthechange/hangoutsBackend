package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.service.HangoutService;
import com.bbthechange.inviter.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Extends BaseController for consistent error handling and user extraction.
 */
@RestController
@RequestMapping("/events")
@Validated
public class HangoutController extends BaseController {
    
    private static final Logger logger = LoggerFactory.getLogger(HangoutController.class);
    
    private final HangoutService hangoutService;
    
    @Autowired
    public HangoutController(HangoutService hangoutService) {
        this.hangoutService = hangoutService;
    }
    
    @GetMapping("/{eventId}/detail")
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