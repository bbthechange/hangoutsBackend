package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.service.GroupService;
import com.bbthechange.inviter.service.GroupFeedService;
import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.model.Group;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.time.Instant;
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
    private final GroupFeedService groupFeedService;
    
    @Autowired
    public GroupController(GroupService groupService, GroupFeedService groupFeedService) {
        this.groupService = groupService;
        this.groupFeedService = groupFeedService;
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

    @PatchMapping("/{groupId}")
    public ResponseEntity<GroupDTO> updateGroup(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid group ID format") String groupId,
            @Valid @RequestBody UpdateGroupRequest request,
            HttpServletRequest httpRequest) {

        String userId = extractUserId(httpRequest);

        if (!request.hasUpdates()) {
            return ResponseEntity.badRequest().build();
        }

        GroupDTO updatedGroup = groupService.updateGroup(groupId, request, userId);
        logger.info("Updated group {} by user {}", groupId, userId);

        return ResponseEntity.ok(updatedGroup);
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

    @PostMapping("/{groupId}/leave")
    public ResponseEntity<Void> leaveGroup(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid group ID format") String groupId,
            HttpServletRequest httpRequest) {

        String userId = extractUserId(httpRequest);

        groupService.leaveGroup(groupId, userId);
        logger.info("User {} left group {}", userId, groupId);

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
            @RequestParam(required = false) @Min(1) Integer limit,
            @RequestParam(required = false) String startingAfter,
            @RequestParam(required = false) String endingBefore,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch,
            HttpServletRequest httpRequest) {

        String userId = extractUserId(httpRequest);

        // Step 1: Cheap ETag check - just fetch Group metadata (2 RCUs: group + membership)
        Group group = groupService.getGroupForEtagCheck(groupId, userId);
        String etag = calculateETag(groupId, group.getLastHangoutModified());

        // Step 2: Return 304 if ETag matches (saves 2-4 expensive GSI queries!)
        if (etag.equals(ifNoneMatch)) {
            logger.debug("Feed unchanged for group {}, returning 304", groupId);
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .cacheControl(CacheControl.noCache().mustRevalidate())
                    .build();
        }

        // Step 3: ETag doesn't match - do the expensive feed query
        GroupFeedDTO feed = groupService.getGroupFeed(groupId, userId, limit, startingAfter, endingBefore);
        logger.debug("Retrieved group feed for group {} with {} chronological events",
                    groupId, feed.getWithDay().size());

        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(CacheControl.noCache().mustRevalidate()) // Always revalidate, never stale
                .body(feed);
    }

    /**
     * Calculate ETag for group feed based on groupId and last modification timestamp.
     * ETag format: "{groupId}-{lastModifiedMillis}"
     *
     * @param groupId The group ID
     * @param lastModified The last hangout modification timestamp (can be null)
     * @return ETag string in quotes
     */
    private String calculateETag(String groupId, Instant lastModified) {
        return String.format("\"%s-%d\"",
            groupId,
            lastModified != null ? lastModified.toEpochMilli() : 0);
    }
    
    @GetMapping("/{groupId}/feed-items")
    public ResponseEntity<GroupFeedItemsResponse> getGroupFeedItems(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid group ID format") String groupId,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) Integer limit,
            @RequestParam(required = false) String startToken,
            HttpServletRequest httpRequest) {
        
        String userId = extractUserId(httpRequest);
        logger.info("Getting feed items for group {} with limit {} for user {}", groupId, limit, userId);
        
        GroupFeedItemsResponse response = groupFeedService.getFeedItems(groupId, limit, startToken, userId);
        logger.debug("Retrieved {} feed items for group {}", response.getItems().size(), groupId);
        
        return ResponseEntity.ok(response);
    }
}