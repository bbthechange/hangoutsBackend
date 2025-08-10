package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.service.HangoutService;
import com.bbthechange.inviter.dto.*;
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
 * REST controller for hangout attribute management operations.
 * Handles individual attribute CRUD operations with efficient UUID-based access.
 * 
 * All operations inherit hangout permissions - users must have access to the hangout
 * to manage its attributes.
 */
@RestController
@RequestMapping("/hangouts/{hangoutId}/attributes")
@Validated
public class HangoutAttributeController extends BaseController {
    
    private static final Logger logger = LoggerFactory.getLogger(HangoutAttributeController.class);
    
    private final HangoutService hangoutService;
    
    @Autowired
    public HangoutAttributeController(HangoutService hangoutService) {
        this.hangoutService = hangoutService;
    }
    
    /**
     * Create a new attribute for a hangout.
     * POST /hangouts/{hangoutId}/attributes
     * 
     * @param hangoutId The hangout ID (must be valid UUID format)
     * @param request The attribute creation request
     * @param httpRequest HTTP request for user extraction
     * @return 201 Created with the new attribute DTO including attributeId
     */
    @PostMapping
    public ResponseEntity<HangoutAttributeDTO> createAttribute(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid hangout ID format") String hangoutId,
            @Valid @RequestBody CreateAttributeRequest request,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        logger.info("Creating attribute '{}' for hangout {} by user {}", 
            request.getAttributeName(), hangoutId, userId);
        
        HangoutAttributeDTO createdAttribute = hangoutService.createAttribute(hangoutId, request, userId);
        
        logger.info("Successfully created attribute {} for hangout {} by user {}", 
            createdAttribute.getAttributeId(), hangoutId, userId);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(createdAttribute);
    }
    
    /**
     * Update an existing attribute by ID.
     * PUT /hangouts/{hangoutId}/attributes/{attributeId}
     * 
     * Supports both value updates and renaming. Uses idempotent PUT semantics.
     * 
     * @param hangoutId The hangout ID (must be valid UUID format)
     * @param attributeId The attribute ID (must be valid UUID format)
     * @param request The attribute update request (both name and value required)
     * @param httpRequest HTTP request for user extraction
     * @return 200 OK with the updated attribute DTO
     */
    @PutMapping("/{attributeId}")
    public ResponseEntity<HangoutAttributeDTO> updateAttribute(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid hangout ID format") String hangoutId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid attribute ID format") String attributeId,
            @Valid @RequestBody UpdateAttributeRequest request,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        logger.info("Updating attribute {} for hangout {} by user {}", 
            attributeId, hangoutId, userId);
        
        HangoutAttributeDTO updatedAttribute = hangoutService.updateAttribute(hangoutId, attributeId, request, userId);
        
        logger.info("Successfully updated attribute {} for hangout {} by user {}", 
            attributeId, hangoutId, userId);
        
        return ResponseEntity.ok(updatedAttribute);
    }
    
    /**
     * Delete an attribute by ID.
     * DELETE /hangouts/{hangoutId}/attributes/{attributeId}
     * 
     * Idempotent operation - returns 200 OK even if attribute doesn't exist.
     * 
     * @param hangoutId The hangout ID (must be valid UUID format)
     * @param attributeId The attribute ID (must be valid UUID format)
     * @param httpRequest HTTP request for user extraction
     * @return 200 OK with success message
     */
    @DeleteMapping("/{attributeId}")
    public ResponseEntity<Object> deleteAttribute(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid hangout ID format") String hangoutId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid attribute ID format") String attributeId,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        logger.info("Deleting attribute {} for hangout {} by user {}", 
            attributeId, hangoutId, userId);
        
        hangoutService.deleteAttribute(hangoutId, attributeId, userId);
        
        logger.info("Successfully deleted attribute {} for hangout {} by user {}", 
            attributeId, hangoutId, userId);
        
        return ResponseEntity.ok().body(java.util.Map.of("message", "Attribute deleted successfully"));
    }
}