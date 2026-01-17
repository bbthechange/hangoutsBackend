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

    /**
     * Update denormalized group name in all membership records for a group.
     * Called when group name changes to maintain data consistency.
     */
    void updateMembershipGroupNames(String groupId, String newGroupName);

    /**
     * Update denormalized group image paths in all membership records for a group.
     * Called when group images change to maintain data consistency.
     */
    void updateMembershipGroupImagePaths(String groupId, String mainImagePath, String backgroundImagePath);

    /**
     * Update denormalized user image path in all membership records for a user.
     * Called when user mainImagePath changes to maintain data consistency.
     */
    void updateMembershipUserImagePath(String userId, String mainImagePath);

    /**
     * Returns group membership if a user is a member of the given group
     */
    Optional<GroupMembership> findMembership(String groupId, String userId);
    List<GroupMembership> findMembersByGroupId(String groupId);

    Boolean isUserMemberOfGroup(String groupId, String userId);

    /**
     * Find all groups a user belongs to using GSI query.
     * Returns denormalized data with group names - no additional lookups needed.
     */
    List<GroupMembership> findGroupsByUserId(String userId);

    /**
     * Find group membership by calendar subscription token.
     * Uses CalendarTokenIndex GSI for efficient token-based lookup.
     *
     * @param token Calendar subscription token (UUID)
     * @return Optional containing GroupMembership if token is valid, empty otherwise
     */
    Optional<GroupMembership> findMembershipByToken(String token);
    
    // Hangout pointer operations (simple CRUD only - business logic in service layer)
    void saveHangoutPointer(HangoutPointer pointer);
    Optional<HangoutPointer> findHangoutPointer(String groupId, String hangoutId);
    void updateHangoutPointer(String groupId, String hangoutId, Map<String, AttributeValue> updates);
    void deleteHangoutPointer(String groupId, String hangoutId);
    
    // Series pointer operations (simple CRUD only - business logic in service layer)
    void saveSeriesPointer(SeriesPointer pointer);
    Optional<SeriesPointer> findSeriesPointer(String groupId, String seriesId);
    void deleteSeriesPointer(String groupId, String seriesId);
    
    /**
     * Get all hangouts for a group for feed display.
     * Returns denormalized hangout data for efficient feed rendering.
     */
    List<HangoutPointer> findHangoutsByGroupId(String groupId);
    
    /**
     * Atomically update participant count for a hangout pointer.
     * Uses DynamoDB atomic ADD operation to safely increment/decrement.
     */
    void atomicallyUpdateParticipantCount(String groupId, String hangoutId, int delta);
}