package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.service.GroupService;
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
 * REST controller for group management operations.
 * Extends BaseController for consistent error handling and user extraction.
 */
@RestController
@RequestMapping("/groups")
@Validated
public class GroupController extends BaseController {
    
    private static final Logger logger = LoggerFactory.getLogger(GroupController.class);
    
    private final GroupService groupService;
    
    @Autowired
    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }
    
    @PostMapping
    public ResponseEntity<GroupDTO> createGroup(
            @Valid @RequestBody CreateGroupRequest request,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        logger.info("Creating group {} for user {}", request.getGroupName(), userId);
        
        GroupDTO group = groupService.createGroup(request, userId);
        logger.info("Successfully created group {} with ID {}", group.getGroupName(), group.getGroupId());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(group);
    }
    
    @GetMapping
    public ResponseEntity<List<GroupDTO>> getUserGroups(HttpServletRequest httpRequest) {
        String userId = extractUserId(httpRequest);
        
        // This uses the efficient GSI pattern - no N+1 queries!
        List<GroupDTO> groups = groupService.getUserGroups(userId);
        logger.debug("Retrieved {} groups for user {}", groups.size(), userId);
        
        return ResponseEntity.ok(groups);
    }
    
    @GetMapping("/{groupId}")
    public ResponseEntity<GroupDTO> getGroup(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid group ID format") String groupId,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        
        GroupDTO group = groupService.getGroup(groupId, userId);
        return ResponseEntity.ok(group);
    }
    
    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> deleteGroup(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid group ID format") String groupId,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        
        groupService.deleteGroup(groupId, userId);
        logger.info("Deleted group {} by user {}", groupId, userId);
        
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{groupId}/members")
    public ResponseEntity<Void> addMember(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid group ID format") String groupId,
            @RequestBody @Valid AddMemberRequest request,
            HttpServletRequest httpRequest) {
        
        String addedBy = extractUserId(httpRequest);
        
        groupService.addMember(groupId, request.getUserId(), request.getPhoneNumber(), addedBy);
        logger.info("Added member {} to group {} by {}", request.getUserId(), groupId, addedBy);
        
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/{groupId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid group ID format") String groupId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid user ID format") String userId,
            HttpServletRequest httpRequest) {
        
        String removedBy = extractUserId(httpRequest);
        
        groupService.removeMember(groupId, userId, removedBy);
        logger.info("Removed member {} from group {} by {}", userId, groupId, removedBy);
        
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/{groupId}/members")
    public ResponseEntity<List<GroupMemberDTO>> getGroupMembers(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid group ID format") String groupId,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        
        List<GroupMemberDTO> members = groupService.getGroupMembers(groupId, userId);
        logger.debug("Retrieved {} members for group {}", members.size(), groupId);
        
        return ResponseEntity.ok(members);
    }
    
    @GetMapping("/{groupId}/feed")
    public ResponseEntity<GroupFeedDTO> getGroupFeed(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid group ID format") String groupId,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        
        // Single query gets all hangout pointers - very efficient!
        GroupFeedDTO feed = groupService.getGroupFeed(groupId, userId);
        return ResponseEntity.ok(feed);
    }
}