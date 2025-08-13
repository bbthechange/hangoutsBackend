package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.service.GroupService;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.UserRepository;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.exception.*;
import com.bbthechange.inviter.service.InviteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GroupServiceImpl using Mockito.
 * Tests business logic without database dependencies.
 */
@ExtendWith(MockitoExtension.class)
class GroupServiceImplTest {

    private static final String GROUP_ID = "12345678-1234-1234-1234-123456789012";
    private static final String USER_ID = "87654321-4321-4321-4321-210987654321";
    private static final String PHONE_NUMBER = "+11234567890";

    @Mock
    private GroupRepository groupRepository;
    
    @Mock
    private UserRepository userRepository;

    @Mock
    private InviteService inviteService;
    
    @InjectMocks
    private GroupServiceImpl groupService;
    
    @Test
    void createGroup_Success() {
        // Given
        CreateGroupRequest request = new CreateGroupRequest("Test Group", true);
        String creatorId = "12345678-1234-1234-1234-123456789012";
        User creator = new User();
        creator.setId(UUID.fromString(creatorId));
        
        when(userRepository.findById(UUID.fromString(creatorId))).thenReturn(Optional.of(creator));
        doNothing().when(groupRepository).createGroupWithFirstMember(any(Group.class), any(GroupMembership.class));
        
        // When
        GroupDTO result = groupService.createGroup(request, creatorId);
        
        // Then
        assertThat(result.getGroupName()).isEqualTo("Test Group");
        assertThat(result.isPublic()).isTrue();
        assertThat(result.getUserRole()).isEqualTo(GroupRole.ADMIN);
        
        verify(groupRepository).createGroupWithFirstMember(any(Group.class), any(GroupMembership.class));
    }
    
    @Test
    void createGroup_CreatorNotFound_ThrowsException() {
        // Given
        CreateGroupRequest request = new CreateGroupRequest("Test Group", true);
        String creatorId = "12345678-1234-1234-1234-123456789999"; // Valid UUID format
        
        when(userRepository.findById(UUID.fromString(creatorId))).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> groupService.createGroup(request, creatorId))
            .isInstanceOf(UserNotFoundException.class)
            .hasMessageContaining("Creator not found");
            
        verify(groupRepository, never()).createGroupWithFirstMember(any(), any());
    }
    
    @Test
    void getUserGroups_UsesGSIEfficiently() {
        // Given
        String userId = USER_ID;
        List<GroupMembership> memberships = List.of(
            createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group One", GroupRole.ADMIN),
            createTestMembership("22222222-2222-2222-2222-222222222222", userId, "Group Two", GroupRole.MEMBER)
        );
        
        when(groupRepository.findGroupsByUserId(userId)).thenReturn(memberships);
        
        // When
        List<GroupDTO> result = groupService.getUserGroups(userId);
        
        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getGroupName()).isEqualTo("Group One"); // Denormalized data
        assertThat(result.get(0).getUserRole()).isEqualTo(GroupRole.ADMIN);
        assertThat(result.get(1).getGroupName()).isEqualTo("Group Two");
        assertThat(result.get(1).getUserRole()).isEqualTo(GroupRole.MEMBER);
        
        // Verify only ONE GSI query, no additional lookups (no N+1!)
        verify(groupRepository, times(1)).findGroupsByUserId(userId);
        verify(groupRepository, never()).findById(any());
    }
    
    @Test
    void getGroup_Success() {
        // Given
        String groupId = "12345678-1234-1234-1234-123456789012";
        String userId = USER_ID;
        
        Group group = createTestGroup("Test Group", false, groupId);
        GroupMembership membership = createTestMembership(groupId, userId, "Test Group", GroupRole.MEMBER);
        
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));
        
        // When
        GroupDTO result = groupService.getGroup(groupId, userId);
        
        // Then
        assertThat(result.getGroupName()).isEqualTo("Test Group");
        assertThat(result.getUserRole()).isEqualTo(GroupRole.MEMBER);
    }
    
    @Test
    void getGroup_UserNotInGroup_ThrowsUnauthorized() {
        // Given
        String groupId = "12345678-1234-1234-1234-123456789012";
        String userId = USER_ID;
        
        Group group = createTestGroup("Test Group", false, groupId);
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> groupService.getGroup(groupId, userId))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("User not in group");
    }
    
    @Test
    void addMember_ToPublicGroup_byUserId_Success() {
        // Given
        String addedBy = "11111111-1111-1111-1111-111111111111";
        
        Group group = createTestGroup("Public Group", true, GROUP_ID); // Public group
        User userToAdd = new User();
        userToAdd.setId(UUID.fromString(USER_ID));
        
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(userRepository.findById(UUID.fromString(USER_ID))).thenReturn(Optional.of(userToAdd));
        when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(false);
        when(groupRepository.addMember(any(GroupMembership.class))).thenReturn(createTestMembership(GROUP_ID, USER_ID, "Public Group", GroupRole.MEMBER));
        
        // When
        assertThatCode(() -> groupService.addMember(GROUP_ID, USER_ID, null, addedBy))
            .doesNotThrowAnyException();
        
        // Then
        // Make sure the correct user was added to the correct group
        verify(groupRepository).addMember(argThat(membership ->
                GROUP_ID.equals(membership.getGroupId()) && USER_ID.equals(membership.getUserId())));
    }

    @Test
    void addMember_ToPublicGroup_byPhoneNumber_Success() {
        // Given
        String addedBy = "11111111-1111-1111-1111-111111111111";

        Group group = createTestGroup("Public Group", true, GROUP_ID); // Public group
        User userToAdd = new User();
        userToAdd.setId(UUID.fromString(USER_ID));

        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(inviteService.findOrCreateUserByPhoneNumber(PHONE_NUMBER)).thenReturn(userToAdd);
        when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(false);
        when(groupRepository.addMember(any(GroupMembership.class))).thenReturn(createTestMembership(GROUP_ID, USER_ID, "Public Group", GroupRole.MEMBER));

        // When
        assertThatCode(() -> groupService.addMember(GROUP_ID, null, PHONE_NUMBER, addedBy))
                .doesNotThrowAnyException();

        // Then
        // Make sure the correct user was added to the correct group
        verify(groupRepository).addMember(argThat(membership -> 
            GROUP_ID.equals(membership.getGroupId()) && USER_ID.equals(membership.getUserId())));
    }

    @Test
    void addMember_ToPublicGroup_byPhoneNumberAndId_Failure() {
        // Given
        String addedBy = "11111111-1111-1111-1111-111111111111";

        Group group = createTestGroup("Public Group", true, GROUP_ID); // Public group
        User userToAdd = new User();
        userToAdd.setId(UUID.fromString(USER_ID));


        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

        // When/then
        assertThatThrownBy(() -> groupService.addMember(GROUP_ID, USER_ID, PHONE_NUMBER, addedBy))
                .isInstanceOf((IllegalArgumentException.class));
    }
    
    @Test
    void removeMember_UserRemovesThemself_Success() {
        // Given
        String groupId = "12345678-1234-1234-1234-123456789012";
        String userId = USER_ID;
        String removedBy = userId; // Same user removing themselves
        
        GroupMembership membership = createTestMembership(groupId, userId, "Test Group", GroupRole.MEMBER);
        when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));
        
        // When
        assertThatCode(() -> groupService.removeMember(groupId, userId, removedBy))
            .doesNotThrowAnyException();
        
        // Then
        verify(groupRepository).removeMember(groupId, userId);
    }
    
    @Test
    void removeMember_AdminRemovesOther_Success() {
        // Given
        String groupId = "12345678-1234-1234-1234-123456789012";
        String userId = USER_ID;
        String removedBy = "11111111-1111-1111-1111-111111111111";
        
        GroupMembership membershipToRemove = createTestMembership(groupId, userId, "Test Group", GroupRole.MEMBER);
        GroupMembership adminMembership = createTestMembership(groupId, removedBy, "Test Group", GroupRole.ADMIN);
        
        when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membershipToRemove));
        when(groupRepository.findMembership(groupId, removedBy)).thenReturn(Optional.of(adminMembership));
        
        // When
        assertThatCode(() -> groupService.removeMember(groupId, userId, removedBy))
            .doesNotThrowAnyException();
        
        // Then
        verify(groupRepository).removeMember(groupId, userId);
    }
    
    @Test
    void isUserInGroup_UserExists_ReturnsTrue() {
        // Given
        String userId = USER_ID;
        String groupId = "12345678-1234-1234-1234-123456789012";
        GroupMembership membership = createTestMembership(groupId, userId, "Test Group", GroupRole.MEMBER);
        
        when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));
        
        // When
        boolean result = groupService.isUserInGroup(userId, groupId);
        
        // Then
        assertThat(result).isTrue();
    }
    
    @Test
    void isUserInGroup_UserNotExists_ReturnsFalse() {
        // Given
        String userId = USER_ID;
        String groupId = "12345678-1234-1234-1234-123456789012";
        
        when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.empty());
        
        // When
        boolean result = groupService.isUserInGroup(userId, groupId);
        
        // Then
        assertThat(result).isFalse();
    }
    
    @Test
    void getGroupFeed_Success() {
        // Given
        String groupId = "12345678-1234-1234-1234-123456789012";
        String userId = USER_ID;
        
        when(groupRepository.findMembership(groupId, userId)).thenReturn(
            Optional.of(createTestMembership(groupId, userId, "Test Group", GroupRole.MEMBER)));
        
        // Create hangout pointers with timeInfo
        HangoutPointer futureHangout = createHangoutPointer(groupId, "11111111-1111-1111-1111-111111111111", "Future Hangout", java.time.Instant.now().plusSeconds(3600));
        TimeInfo timeInfo = new TimeInfo();
        timeInfo.setPeriodGranularity("evening");
        timeInfo.setPeriodStart("2025-08-05T19:00:00Z");
        futureHangout.setTimeInput(timeInfo);
        
        HangoutPointer needsScheduling = createHangoutPointer(groupId, "22222222-2222-2222-2222-222222222222", "Needs Scheduling", null);
        // No timeInfo for this one to test both scenarios
        
        List<HangoutPointer> hangouts = List.of(futureHangout, needsScheduling);
        when(groupRepository.findHangoutsByGroupId(groupId)).thenReturn(hangouts);
        
        // When
        GroupFeedDTO result = groupService.getGroupFeed(groupId, userId);
        
        // Then
        assertThat(result.getGroupId()).isEqualTo(groupId);
        assertThat(result.getWithDay()).hasSize(1);
        assertThat(result.getNeedsDay()).hasSize(1);
        
        // Verify hangout details and timeInfo
        HangoutSummaryDTO futureHangoutSummary = result.getWithDay().get(0);
        assertThat(futureHangoutSummary.getTitle()).isEqualTo("Future Hangout");
        assertThat(futureHangoutSummary.getTimeInfo()).isNotNull();
        assertThat(futureHangoutSummary.getTimeInfo().getPeriodGranularity()).isEqualTo("evening");
        assertThat(futureHangoutSummary.getTimeInfo().getPeriodStart()).isEqualTo("2025-08-05T19:00:00Z");
        
        HangoutSummaryDTO needsSchedulingSummary = result.getNeedsDay().get(0);
        assertThat(needsSchedulingSummary.getTitle()).isEqualTo("Needs Scheduling");
        assertThat(needsSchedulingSummary.getTimeInfo()).isNull(); // No timeInfo set
    }
    
    // Helper methods for test data creation
    private GroupMembership createTestMembership(String groupId, String userId, String groupName, String role) {
        // For tests, create membership without going through the constructor that validates UUIDs
        GroupMembership membership = new GroupMembership();
        membership.setGroupId(groupId);
        membership.setUserId(userId);
        membership.setGroupName(groupName);
        membership.setRole(role);
        // Set keys directly for test purposes
        membership.setPk("GROUP#" + groupId);
        membership.setSk("USER#" + userId);
        membership.setGsi1pk("USER#" + userId);
        membership.setGsi1sk("GROUP#" + groupId);
        return membership;
    }
    
    private HangoutPointer createHangoutPointer(String groupId, String hangoutId, String title, java.time.Instant hangoutTime) {
        // Create HangoutPointer without going through constructor that validates UUIDs
        HangoutPointer pointer = new HangoutPointer();
        pointer.setGroupId(groupId);
        pointer.setHangoutId(hangoutId);
        pointer.setTitle(title);
        if (hangoutTime != null) {
            pointer.setStartTimestamp(hangoutTime.toEpochMilli());
        }
        // Set keys directly for test purposes
        pointer.setPk("GROUP#" + groupId);
        pointer.setSk("HANGOUT#" + hangoutId);
        return pointer;
    }
    
    private Group createTestGroup(String groupName, boolean isPublic, String groupId) {
        // Create group without going through constructor that validates UUIDs
        Group group = new Group();
        group.setGroupId(groupId);
        group.setGroupName(groupName);
        group.setPublic(isPublic);
        // Set keys directly for test purposes
        group.setPk("GROUP#" + groupId);
        group.setSk("METADATA");
        return group;
    }
}