package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.*;
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
     * Get the group feed showing all hangouts organized by status.
     * Single query gets all hangout pointers - very efficient!
     */
    GroupFeedDTO getGroupFeed(String groupId, String requestingUserId);
}