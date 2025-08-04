package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.service.GroupService;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.UserRepository;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.exception.*;
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
    
    @Mock
    private GroupRepository groupRepository;
    
    @Mock
    private UserRepository userRepository;
    
    @InjectMocks
    private GroupServiceImpl groupService;
    
    @Test
    void createGroup_Success() {
        // Given
        CreateGroupRequest request = new CreateGroupRequest("Test Group", true);
        String creatorId = "creator-123";
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
        String creatorId = "non-existent-user";
        
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
        String userId = "user-123";
        List<GroupMembership> memberships = List.of(
            createTestMembership("group-1", userId, "Group One", GroupRole.ADMIN),
            createTestMembership("group-2", userId, "Group Two", GroupRole.MEMBER)
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
        String groupId = "group-123";
        String userId = "user-456";
        
        Group group = new Group("Test Group", false);
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
        String groupId = "group-123";
        String userId = "user-456";
        
        Group group = new Group("Test Group", false);
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> groupService.getGroup(groupId, userId))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("User not in group");
    }
    
    @Test
    void addMember_ToPublicGroup_Success() {
        // Given
        String groupId = "group-123";
        String userId = "new-user";
        String addedBy = "admin-user";
        
        Group group = new Group("Public Group", true); // Public group
        User userToAdd = new User();
        userToAdd.setId(UUID.fromString(userId));
        
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(userRepository.findById(UUID.fromString(userId))).thenReturn(Optional.of(userToAdd));
        when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.empty());
        when(groupRepository.addMember(any(GroupMembership.class))).thenReturn(new GroupMembership(groupId, userId, "Public Group"));
        
        // When
        assertThatCode(() -> groupService.addMember(groupId, userId, addedBy))
            .doesNotThrowAnyException();
        
        // Then
        verify(groupRepository).addMember(any(GroupMembership.class));
    }
    
    @Test
    void addMember_ToPrivateGroup_RequiresAdmin() {
        // Given
        String groupId = "group-123";
        String userId = "new-user";
        String addedBy = "regular-user";
        
        Group group = new Group("Private Group", false); // Private group
        GroupMembership adderMembership = createTestMembership(groupId, addedBy, "Private Group", GroupRole.MEMBER);
        
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupRepository.findMembership(groupId, addedBy)).thenReturn(Optional.of(adderMembership));
        
        // When/Then
        assertThatThrownBy(() -> groupService.addMember(groupId, userId, addedBy))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("Only admins can add members to private groups");
            
        verify(groupRepository, never()).addMember(any());
    }
    
    @Test
    void removeMember_UserRemovesThemself_Success() {
        // Given
        String groupId = "group-123";
        String userId = "user-456";
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
        String groupId = "group-123";
        String userId = "user-to-remove";
        String removedBy = "admin-user";
        
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
        String userId = "user-123";
        String groupId = "group-456";
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
        String userId = "user-123";
        String groupId = "group-456";
        
        when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.empty());
        
        // When
        boolean result = groupService.isUserInGroup(userId, groupId);
        
        // Then
        assertThat(result).isFalse();
    }
    
    @Test
    void getGroupFeed_Success() {
        // Given
        String groupId = "group-123";
        String userId = "user-456";
        
        when(groupRepository.findMembership(groupId, userId)).thenReturn(
            Optional.of(createTestMembership(groupId, userId, "Test Group", GroupRole.MEMBER)));
        
        List<HangoutPointer> hangouts = List.of(
            createHangoutPointer(groupId, "hangout-1", "Future Hangout", java.time.Instant.now().plusSeconds(3600)),
            createHangoutPointer(groupId, "hangout-2", "Needs Scheduling", null)
        );
        when(groupRepository.findHangoutsByGroupId(groupId)).thenReturn(hangouts);
        
        // When
        GroupFeedDTO result = groupService.getGroupFeed(groupId, userId);
        
        // Then
        assertThat(result.getGroupId()).isEqualTo(groupId);
        assertThat(result.getWithDay()).hasSize(1);
        assertThat(result.getNeedsDay()).hasSize(1);
        assertThat(result.getWithDay().get(0).getTitle()).isEqualTo("Future Hangout");
        assertThat(result.getNeedsDay().get(0).getTitle()).isEqualTo("Needs Scheduling");
    }
    
    // Helper methods for test data creation
    private GroupMembership createTestMembership(String groupId, String userId, String groupName, String role) {
        GroupMembership membership = new GroupMembership(groupId, userId, groupName);
        membership.setRole(role);
        return membership;
    }
    
    private HangoutPointer createHangoutPointer(String groupId, String hangoutId, String title, java.time.Instant hangoutTime) {
        HangoutPointer pointer = new HangoutPointer(groupId, hangoutId, title);
        pointer.setHangoutTime(hangoutTime);
        return pointer;
    }
}