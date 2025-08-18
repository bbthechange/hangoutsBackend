package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.service.IdeaListService;
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
import java.util.List;

/**
 * REST controller for idea list management operations within groups.
 * All endpoints require JWT authentication and group membership.
 */
@RestController
@RequestMapping("/groups/{groupId}/idea-lists")
@Validated
public class IdeaListController extends BaseController {
    
    private static final Logger logger = LoggerFactory.getLogger(IdeaListController.class);
    
    private final IdeaListService ideaListService;
    
    @Autowired
    public IdeaListController(IdeaListService ideaListService) {
        this.ideaListService = ideaListService;
    }
    
    /**
     * Get all idea lists for a group with their ideas.
     * GET /groups/{groupId}/idea-lists
     */
    @GetMapping
    public ResponseEntity<List<IdeaListDTO>> getIdeaListsForGroup(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid group ID format") String groupId,
            HttpServletRequest httpRequest) {
        
        String requestingUserId = extractUserId(httpRequest);
        logger.debug("Getting idea lists for group: {} by user: {}", groupId, requestingUserId);
        
        List<IdeaListDTO> ideaLists = ideaListService.getIdeaListsForGroup(groupId, requestingUserId);
        return ResponseEntity.ok(ideaLists);
    }
    
    /**
     * Get a single idea list with all its ideas.
     * GET /groups/{groupId}/idea-lists/{listId}
     */
    @GetMapping("/{listId}")
    public ResponseEntity<IdeaListDTO> getIdeaList(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid group ID format") String groupId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid list ID format") String listId,
            HttpServletRequest httpRequest) {
        
        String requestingUserId = extractUserId(httpRequest);
        logger.debug("Getting idea list: {} in group: {} by user: {}", listId, groupId, requestingUserId);
        
        IdeaListDTO ideaList = ideaListService.getIdeaList(groupId, listId, requestingUserId);
        return ResponseEntity.ok(ideaList);
    }
    
    /**
     * Create a new idea list (empty initially).
     * POST /groups/{groupId}/idea-lists
     */
    @PostMapping
    public ResponseEntity<IdeaListDTO> createIdeaList(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid group ID format") String groupId,
            @Valid @RequestBody CreateIdeaListRequest request,
            HttpServletRequest httpRequest) {
        
        String requestingUserId = extractUserId(httpRequest);
        logger.debug("Creating idea list in group: {} by user: {}", groupId, requestingUserId);
        
        IdeaListDTO createdList = ideaListService.createIdeaList(groupId, request, requestingUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdList);
    }
    
    /**
     * Update an idea list (name, category, note).
     * PUT /groups/{groupId}/idea-lists/{listId}
     */
    @PutMapping("/{listId}")
    public ResponseEntity<IdeaListDTO> updateIdeaList(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid group ID format") String groupId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid list ID format") String listId,
            @Valid @RequestBody UpdateIdeaListRequest request,
            HttpServletRequest httpRequest) {
        
        String requestingUserId = extractUserId(httpRequest);
        logger.debug("Updating idea list: {} in group: {} by user: {}", listId, groupId, requestingUserId);
        
        IdeaListDTO updatedList = ideaListService.updateIdeaList(groupId, listId, request, requestingUserId);
        return ResponseEntity.ok(updatedList);
    }
    
    /**
     * Delete an idea list and all its ideas.
     * DELETE /groups/{groupId}/idea-lists/{listId}
     */
    @DeleteMapping("/{listId}")
    public ResponseEntity<Void> deleteIdeaList(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid group ID format") String groupId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid list ID format") String listId,
            HttpServletRequest httpRequest) {
        
        String requestingUserId = extractUserId(httpRequest);
        logger.debug("Deleting idea list: {} from group: {} by user: {}", listId, groupId, requestingUserId);
        
        ideaListService.deleteIdeaList(groupId, listId, requestingUserId);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Add a new idea to an idea list.
     * POST /groups/{groupId}/idea-lists/{listId}/ideas
     */
    @PostMapping("/{listId}/ideas")
    public ResponseEntity<IdeaDTO> addIdeaToList(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid group ID format") String groupId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid list ID format") String listId,
            @Valid @RequestBody CreateIdeaRequest request,
            HttpServletRequest httpRequest) {
        
        String requestingUserId = extractUserId(httpRequest);
        logger.debug("Adding idea to list: {} in group: {} by user: {}", listId, groupId, requestingUserId);
        
        IdeaDTO createdIdea = ideaListService.addIdeaToList(groupId, listId, request, requestingUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdIdea);
    }
    
    /**
     * Update an idea (partial update with PATCH semantics).
     * PATCH /groups/{groupId}/idea-lists/{listId}/ideas/{ideaId}
     */
    @PatchMapping("/{listId}/ideas/{ideaId}")
    public ResponseEntity<IdeaDTO> updateIdea(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid group ID format") String groupId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid list ID format") String listId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid idea ID format") String ideaId,
            @Valid @RequestBody UpdateIdeaRequest request,
            HttpServletRequest httpRequest) {
        
        String requestingUserId = extractUserId(httpRequest);
        logger.debug("Updating idea: {} in list: {} group: {} by user: {}", ideaId, listId, groupId, requestingUserId);
        
        IdeaDTO updatedIdea = ideaListService.updateIdea(groupId, listId, ideaId, request, requestingUserId);
        return ResponseEntity.ok(updatedIdea);
    }
    
    /**
     * Delete an idea from a list.
     * DELETE /groups/{groupId}/idea-lists/{listId}/ideas/{ideaId}
     */
    @DeleteMapping("/{listId}/ideas/{ideaId}")
    public ResponseEntity<Void> deleteIdea(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid group ID format") String groupId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid list ID format") String listId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid idea ID format") String ideaId,
            HttpServletRequest httpRequest) {
        
        String requestingUserId = extractUserId(httpRequest);
        logger.debug("Deleting idea: {} from list: {} group: {} by user: {}", ideaId, listId, groupId, requestingUserId);
        
        ideaListService.deleteIdea(groupId, listId, ideaId, requestingUserId);
        return ResponseEntity.noContent().build();
    }
}