package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.model.Group;
import java.util.List;

/**
 * Service interface for group management operations.
 * Provides business logic for groups, memberships, and group feeds.
 */
public interface GroupService {
    
    /**
     * Create a new group with the creator as the first admin member.
     * Uses atomic transaction to ensure both group and membership are created together.
     */
    GroupDTO createGroup(CreateGroupRequest request, String creatorId);
    
    /**
     * Get group details for a specific user.
     * Includes the user's role in the group.
     */
    GroupDTO getGroup(String groupId, String requestingUserId);

    /**
     * Update group attributes (member only).
     * Only provided fields in the request will be updated.
     */
    GroupDTO updateGroup(String groupId, UpdateGroupRequest request, String requestingUserId);

    /**
     * Delete a group (admin only).
     * Also removes all memberships and hangout pointers.
     */
    void deleteGroup(String groupId, String requestingUserId);
    
    /**
     * Add a member to a group (admin only for private groups).
     */
    void addMember(String groupId, String userId, String phoneNumber, String addedBy);
    
    /**
     * Remove a member from a group (admin only, or user can remove themselves).
     */
    void removeMember(String groupId, String userId, String removedBy);

    /**
     * Leave a group (user removes themselves).
     * Simplified version of removeMember for self-removal.
     */
    void leaveGroup(String groupId, String userId);

    /**
     * Get all members of a group (members only).
     */
    List<GroupMemberDTO> getGroupMembers(String groupId, String requestingUserId);
    
    /**
     * Get all groups a user belongs to.
     * Uses efficient GSI pattern - no N+1 queries!
     */
    List<GroupDTO> getUserGroups(String userId);
    
    /**
     * Check if a user is a member of a group.
     */
    boolean isUserInGroup(String userId, String groupId);
    
    /**
     * Get the group feed showing current/future hangouts chronologically with pagination support.
     * Uses parallel queries for optimal performance - future events + in-progress events.
     *
     * @param groupId The group ID
     * @param requestingUserId The user requesting the feed (for authorization)
     * @param limit Maximum number of events to return (null for no limit)
     * @param startingAfter Pagination token for forward pagination (get more future events)
     * @param endingBefore Pagination token for backward pagination (get past events)
     * @return GroupFeedDTO with chronological events and pagination tokens
     */
    GroupFeedDTO getGroupFeed(String groupId, String requestingUserId, Integer limit,
                             String startingAfter, String endingBefore);

    /**
     * Get group metadata for ETag validation.
     * Verifies user has access and returns group with lastHangoutModified timestamp.
     * Lightweight check (2 RCUs: group metadata + membership check).
     *
     * @param groupId The group ID
     * @param requestingUserId The user requesting the check (for authorization)
     * @return Group metadata with lastHangoutModified timestamp
     * @throws ForbiddenException if user is not a member of the group
     * @throws NotFoundException if group does not exist
     */
    Group getGroupForEtagCheck(String groupId, String requestingUserId);
}