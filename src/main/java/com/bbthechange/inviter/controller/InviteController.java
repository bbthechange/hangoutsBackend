package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.AddInviteRequest;
import com.bbthechange.inviter.dto.EditInviteRequest;
import com.bbthechange.inviter.dto.InviteResponse;
import com.bbthechange.inviter.model.Invite;
import com.bbthechange.inviter.service.EventService;
import com.bbthechange.inviter.service.InviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/events/{eventId}/invites")
@Tag(name = "Invites", description = "Invite management for events")
@SecurityRequirement(name = "Bearer Authentication")
public class InviteController {
    
    @Autowired
    private InviteService inviteService;
    
    @Autowired
    private EventService eventService;
    
    @GetMapping
    @Operation(summary = "Get all invites for an event", 
               description = "Returns all invites for the specified event. User must be invited to the event.")
    public ResponseEntity<List<InviteResponse>> getInvitesForEvent(
            @Parameter(description = "Event ID") @PathVariable UUID eventId,
            HttpServletRequest request) {
        
        String userIdStr = (String) request.getAttribute("userId");
        if (userIdStr == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        
        UUID userId = UUID.fromString(userIdStr);
        
        // Validate that user is invited to this event OR is a host
        if (!inviteService.isUserInvitedToEvent(userId, eventId) && !eventService.isUserHostOfEvent(userId, eventId)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        
        List<InviteResponse> invites = inviteService.getInvitesForEvent(eventId);
        return new ResponseEntity<>(invites, HttpStatus.OK);
    }
    
    @PostMapping
    @Operation(summary = "Add an invite to an event",
               description = "Adds a new invite to the specified event by phone number. User must be a host of the event.")
    public ResponseEntity<Map<String, Object>> addInviteToEvent(
            @Parameter(description = "Event ID") @PathVariable UUID eventId,
            @RequestBody AddInviteRequest request,
            HttpServletRequest httpRequest) {
        
        String userIdStr = (String) httpRequest.getAttribute("userId");
        if (userIdStr == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        
        UUID userId = UUID.fromString(userIdStr);
        
        // Validate that user is a host of this event
        if (!eventService.isUserHostOfEvent(userId, eventId)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        
        try {
            Invite invite = inviteService.addInviteToEvent(eventId, request.getPhoneNumber());
            
            Map<String, Object> response = new HashMap<>();
            response.put("inviteId", invite.getId());
            response.put("message", "Invite added successfully");
            
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (IllegalStateException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return new ResponseEntity<>(error, HttpStatus.CONFLICT);
        }
    }
    
    @DeleteMapping("/{inviteId}")
    @Operation(summary = "Remove an invite from an event",
               description = "Removes the specified invite from the event. User must be a host of the event.")
    public ResponseEntity<Map<String, String>> removeInvite(
            @Parameter(description = "Event ID") @PathVariable UUID eventId,
            @Parameter(description = "Invite ID") @PathVariable UUID inviteId,
            HttpServletRequest request) {
        
        String userIdStr = (String) request.getAttribute("userId");
        if (userIdStr == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        
        UUID userId = UUID.fromString(userIdStr);
        
        // Validate that user is a host of this event
        if (!eventService.isUserHostOfEvent(userId, eventId)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        
        try {
            inviteService.removeInvite(inviteId);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Invite removed successfully");
            
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
        }
    }
    
    @PutMapping("/{inviteId}")
    @Operation(summary = "Update invite response", 
               description = "Updates the response for an invite. Users can only update their own invite response.")
    public ResponseEntity<Map<String, Object>> updateInviteResponse(
            @Parameter(description = "Event ID") @PathVariable UUID eventId,
            @Parameter(description = "Invite ID") @PathVariable UUID inviteId,
            @RequestBody EditInviteRequest request,
            HttpServletRequest httpRequest) {
        
        String userIdStr = (String) httpRequest.getAttribute("userId");
        if (userIdStr == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        
        UUID userId = UUID.fromString(userIdStr);
        
        // Note: We don't need to check if user is invited to the event here
        // because updateInviteResponse will verify the user owns the specific invite
        
        try {
            Invite updatedInvite = inviteService.updateInviteResponse(inviteId, userId, request.getResponse());
            
            Map<String, Object> response = new HashMap<>();
            response.put("inviteId", updatedInvite.getId());
            response.put("response", updatedInvite.getResponse());
            response.put("message", "Invite response updated successfully");
            
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
        } catch (IllegalStateException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
        }
    }
}