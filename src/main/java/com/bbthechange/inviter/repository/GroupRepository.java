package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.model.*;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository interface for group management operations in the InviterTable.
 * Provides atomic operations and efficient query patterns for groups and memberships.
 */
public interface GroupRepository {
    
    // Atomic operations
    /**
     * Atomically create a group with its first member using DynamoDB transactions.
     * Ensures both group metadata and creator membership are created together.
     */
    void createGroupWithFirstMember(Group group, GroupMembership membership);
    
    // Simple CRUD operations
    Group save(Group group);
    Optional<Group> findById(String groupId);
    void delete(String groupId);
    
    // Membership operations
    GroupMembership addMember(GroupMembership membership);
    void removeMember(String groupId, String userId);
    Optional<GroupMembership> findMembership(String groupId, String userId);
    List<GroupMembership> findMembersByGroupId(String groupId);
    
    /**
     * Find all groups a user belongs to using GSI query.
     * Returns denormalized data with group names - no additional lookups needed.
     */
    List<GroupMembership> findGroupsByUserId(String userId);
    
    // Hangout pointer operations (simple CRUD only - business logic in service layer)
    void saveHangoutPointer(HangoutPointer pointer);
    void updateHangoutPointer(String groupId, String hangoutId, Map<String, AttributeValue> updates);
    void deleteHangoutPointer(String groupId, String hangoutId);
    
    /**
     * Get all hangouts for a group for feed display.
     * Returns denormalized hangout data for efficient feed rendering.
     */
    List<HangoutPointer> findHangoutsByGroupId(String groupId);
}