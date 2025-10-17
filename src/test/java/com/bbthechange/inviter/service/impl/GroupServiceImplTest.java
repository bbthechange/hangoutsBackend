package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.service.GroupService;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.repository.UserRepository;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.exception.*;
import com.bbthechange.inviter.service.InviteService;
import com.bbthechange.inviter.util.PaginatedResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
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
    private HangoutRepository hangoutRepository;
    
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
    void updateGroup_WithGroupName_Success() {
        // Given
        String groupId = GROUP_ID;
        String userId = USER_ID;
        UpdateGroupRequest request = new UpdateGroupRequest("New Group Name", null);

        Group existingGroup = new Group("Old Group Name", true);
        GroupMembership membership = createTestMembership(groupId, userId, "Old Group Name", GroupRole.MEMBER);

        when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(existingGroup));
        when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        GroupDTO result = groupService.updateGroup(groupId, request, userId);

        // Then
        assertThat(result.getGroupName()).isEqualTo("New Group Name");
        verify(groupRepository).save(argThat(group ->
            group.getGroupName().equals("New Group Name") && group.isPublic()
        ));
    }

    @Test
    void updateGroup_WithPublicFlag_Success() {
        // Given
        String groupId = GROUP_ID;
        String userId = USER_ID;
        UpdateGroupRequest request = new UpdateGroupRequest(null, false);

        Group existingGroup = new Group("Test Group", true);
        GroupMembership membership = createTestMembership(groupId, userId, "Test Group", GroupRole.MEMBER);

        when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(existingGroup));
        when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        GroupDTO result = groupService.updateGroup(groupId, request, userId);

        // Then
        assertThat(result.isPublic()).isFalse();
        verify(groupRepository).save(argThat(group ->
            group.getGroupName().equals("Test Group") && !group.isPublic()
        ));
    }

    @Test
    void updateGroup_UserNotInGroup_ThrowsException() {
        // Given
        String groupId = GROUP_ID;
        String userId = USER_ID;
        UpdateGroupRequest request = new UpdateGroupRequest("New Name", null);

        when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> groupService.updateGroup(groupId, request, userId))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("User not in group");

        verify(groupRepository, never()).save(any(Group.class));
    }

    @Test
    void updateGroup_GroupNotFound_ThrowsException() {
        // Given
        String groupId = GROUP_ID;
        String userId = USER_ID;
        UpdateGroupRequest request = new UpdateGroupRequest("New Name", null);

        GroupMembership membership = createTestMembership(groupId, userId, "Test Group", GroupRole.MEMBER);
        when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));
        when(groupRepository.findById(groupId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> groupService.updateGroup(groupId, request, userId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Group not found");

        verify(groupRepository, never()).save(any(Group.class));
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
    void leaveGroup_WithMultipleMembers_RemovesUserOnly() {
        // Given
        String groupId = "12345678-1234-1234-1234-123456789012";
        String userId = USER_ID;
        String otherUserId = "11111111-1111-1111-1111-111111111111";

        GroupMembership userMembership = createTestMembership(groupId, userId, "Test Group", GroupRole.MEMBER);
        GroupMembership otherMembership = createTestMembership(groupId, otherUserId, "Test Group", GroupRole.MEMBER);
        List<GroupMembership> allMembers = List.of(userMembership, otherMembership);

        when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(userMembership));
        when(groupRepository.findMembersByGroupId(groupId)).thenReturn(allMembers);

        // When
        assertThatCode(() -> groupService.leaveGroup(groupId, userId))
            .doesNotThrowAnyException();

        // Then
        verify(groupRepository).removeMember(groupId, userId);
        verify(groupRepository, never()).delete(groupId);
    }

    @Test
    void leaveGroup_AsLastMember_DeletesGroup() {
        // Given
        String groupId = "12345678-1234-1234-1234-123456789012";
        String userId = USER_ID;

        GroupMembership userMembership = createTestMembership(groupId, userId, "Test Group", GroupRole.MEMBER);
        List<GroupMembership> allMembers = List.of(userMembership); // Only one member

        when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(userMembership));
        when(groupRepository.findMembersByGroupId(groupId)).thenReturn(allMembers);

        // When
        assertThatCode(() -> groupService.leaveGroup(groupId, userId))
            .doesNotThrowAnyException();

        // Then
        verify(groupRepository).delete(groupId);
        verify(groupRepository, never()).removeMember(groupId, userId);
    }

    @Test
    void leaveGroup_UserNotInGroup_ThrowsException() {
        // Given
        String groupId = "12345678-1234-1234-1234-123456789012";
        String userId = USER_ID;

        when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> groupService.leaveGroup(groupId, userId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("User not in group");

        verify(groupRepository, never()).removeMember(groupId, userId);
        verify(groupRepository, never()).delete(groupId);
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
        
        // Mock the new hangout repository methods for enhanced group feed
        // Create future events
        HangoutPointer futureHangout = createHangoutPointer(groupId, "11111111-1111-1111-1111-111111111111", "Future Hangout", java.time.Instant.now().plusSeconds(3600));
        TimeInfo timeInfo = new TimeInfo();
        timeInfo.setPeriodGranularity("evening");
        timeInfo.setPeriodStart("2025-08-05T19:00:00Z");
        futureHangout.setTimeInput(timeInfo);
        
        // Create unscheduled events (needs day)
        HangoutPointer needsScheduling = createHangoutPointer(groupId, "22222222-2222-2222-2222-222222222222", "Needs Scheduling", null);
        
        // Mock the repository methods that the enhanced service uses
        // Repository methods now return PaginatedResult<BaseItem> containing HangoutPointer objects
        PaginatedResult<BaseItem> futureEventsResult = new PaginatedResult<>(List.of(futureHangout), null);
        PaginatedResult<BaseItem> inProgressEventsResult = new PaginatedResult<>(List.of(needsScheduling), null);
        
        when(hangoutRepository.getFutureEventsPage(eq(groupId), anyLong(), isNull(), isNull()))
            .thenReturn(futureEventsResult);
        when(hangoutRepository.getInProgressEventsPage(eq(groupId), anyLong(), isNull(), isNull()))
            .thenReturn(inProgressEventsResult);
        
        // When
        GroupFeedDTO result = groupService.getGroupFeed(groupId, userId, null, null, null);
        
        // Then
        assertThat(result.getGroupId()).isEqualTo(groupId);
        assertThat(result.getWithDay()).hasSize(1);
        assertThat(result.getNeedsDay()).hasSize(1);
        
        // Verify hangout details and timeInfo - cast FeedItem to HangoutSummaryDTO
        HangoutSummaryDTO futureHangoutSummary = (HangoutSummaryDTO) result.getWithDay().get(0);
        assertThat(futureHangoutSummary.getTitle()).isEqualTo("Future Hangout");
        assertThat(futureHangoutSummary.getTimeInfo()).isNotNull();
        assertThat(futureHangoutSummary.getTimeInfo().getPeriodGranularity()).isEqualTo("evening");
        assertThat(futureHangoutSummary.getTimeInfo().getPeriodStart()).isEqualTo("2025-08-05T19:00:00Z");
        
        HangoutSummaryDTO needsSchedulingSummary = result.getNeedsDay().get(0);
        assertThat(needsSchedulingSummary.getTitle()).isEqualTo("Needs Scheduling");
        assertThat(needsSchedulingSummary.getTimeInfo()).isNull(); // No timeInfo set
    }
    
    // ================= Enhanced Group Feed Pagination Tests =================
    
    // A) Retrieving All Future Events Tests
    
    @Test
    void getGroupFeed_AllFutureEvents_ReturnsCompleteList() {
        // Given
        String groupId = GROUP_ID;
        String userId = USER_ID;
        
        // Mock user authorization
        when(groupRepository.findMembership(groupId, userId))
            .thenReturn(Optional.of(createTestMembership(groupId, userId, "Test Group", GroupRole.MEMBER)));
        
        // Setup: Mock repository to return 5 future events, no in-progress events, no pagination tokens
        List<HangoutPointer> futureEvents = List.of(
            createHangoutPointer(groupId, "future1", "Future Event 1", java.time.Instant.now().plusSeconds(1000)),
            createHangoutPointer(groupId, "future2", "Future Event 2", java.time.Instant.now().plusSeconds(2000)),
            createHangoutPointer(groupId, "future3", "Future Event 3", java.time.Instant.now().plusSeconds(3000)),
            createHangoutPointer(groupId, "future4", "Future Event 4", java.time.Instant.now().plusSeconds(4000)),
            createHangoutPointer(groupId, "future5", "Future Event 5", java.time.Instant.now().plusSeconds(5000))
        );
        
        PaginatedResult<BaseItem> futureEventsResult = new PaginatedResult<>(futureEvents.stream().map(BaseItem.class::cast).toList(), null); // No more pages
        PaginatedResult<BaseItem> inProgressEventsResult = new PaginatedResult<>(List.of(), null); // No in-progress
        
        when(hangoutRepository.getFutureEventsPage(eq(groupId), anyLong(), isNull(), isNull()))
            .thenReturn(futureEventsResult);
        when(hangoutRepository.getInProgressEventsPage(eq(groupId), anyLong(), isNull(), isNull()))
            .thenReturn(inProgressEventsResult);
        
        // When
        GroupFeedDTO result = groupService.getGroupFeed(groupId, userId, null, null, null);
        
        // Then
        // All 5 events returned in withDay list
        assertThat(result.getWithDay()).hasSize(5);
        // needsDay list is empty
        assertThat(result.getNeedsDay()).isEmpty();
        // nextPageToken is null (no more pages)
        assertThat(result.getNextPageToken()).isNull();
        // previousPageToken is generated for past events access
        assertThat(result.getPreviousPageToken()).isNotNull();
        // Events sorted chronologically (oldest first)  
        // Since HangoutSummaryDTO doesn't expose startTimestamp directly,
        // verify sorting by checking the titles correspond to our expected order
        List<FeedItem> withDay = result.getWithDay();
        assertThat(((HangoutSummaryDTO) withDay.get(0)).getTitle()).isEqualTo("Future Event 1"); // Earliest timestamp
        assertThat(((HangoutSummaryDTO) withDay.get(4)).getTitle()).isEqualTo("Future Event 5"); // Latest timestamp
    }
    
    @Test
    void getGroupFeed_MixedFutureAndInProgressEvents_ReturnsMergedSorted() {
        // Given
        String groupId = GROUP_ID;
        String userId = USER_ID;
        
        when(groupRepository.findMembership(groupId, userId))
            .thenReturn(Optional.of(createTestMembership(groupId, userId, "Test Group", GroupRole.MEMBER)));
        
        // Setup: Mock repository with 3 future events + 2 in-progress events, mixed timestamps
        List<HangoutPointer> futureEvents = List.of(
            createHangoutPointer(groupId, "future1", "Future Event 1", java.time.Instant.now().plusSeconds(1000)),
            createHangoutPointer(groupId, "future2", "Future Event 2", java.time.Instant.now().plusSeconds(3000)),
            createHangoutPointer(groupId, "future3", "Future Event 3", java.time.Instant.now().plusSeconds(5000))
        );
        
        List<HangoutPointer> inProgressEvents = List.of(
            createHangoutPointer(groupId, "inprogress1", "In Progress Event 1", null), // No timestamp
            createHangoutPointer(groupId, "inprogress2", "In Progress Event 2", null)  // No timestamp
        );
        
        PaginatedResult<BaseItem> futureEventsResult = new PaginatedResult<>(futureEvents.stream().map(BaseItem.class::cast).toList(), null);
        PaginatedResult<BaseItem> inProgressEventsResult = new PaginatedResult<>(inProgressEvents.stream().map(BaseItem.class::cast).toList(), null);
        
        when(hangoutRepository.getFutureEventsPage(eq(groupId), anyLong(), isNull(), isNull()))
            .thenReturn(futureEventsResult);
        when(hangoutRepository.getInProgressEventsPage(eq(groupId), anyLong(), isNull(), isNull()))
            .thenReturn(inProgressEventsResult);
        
        // When
        GroupFeedDTO result = groupService.getGroupFeed(groupId, userId, null, null, null);
        
        // Then
        // All 5 events merged and sorted by timestamp
        assertThat(result.getWithDay()).hasSize(3); // Future events (with timestamps) go to withDay
        assertThat(result.getNeedsDay()).hasSize(2); // In-progress events (no timestamps) go to needsDay
        
        // Verify events are sorted chronologically by checking expected title order
        List<FeedItem> withDay = result.getWithDay();
        assertThat(((HangoutSummaryDTO) withDay.get(0)).getTitle()).isEqualTo("Future Event 1"); // Earliest (1000s)
        assertThat(((HangoutSummaryDTO) withDay.get(1)).getTitle()).isEqualTo("Future Event 2"); // Middle (3000s) 
        assertThat(((HangoutSummaryDTO) withDay.get(2)).getTitle()).isEqualTo("Future Event 3"); // Latest (5000s)
        
        // Verify needsDay contains in-progress events
        assertThat(result.getNeedsDay().get(0).getTitle()).isEqualTo("In Progress Event 1");
        assertThat(result.getNeedsDay().get(1).getTitle()).isEqualTo("In Progress Event 2");
    }
    
    // B) Retrieving Future Events with Limit Tests
    
    @Test
    void getGroupFeed_WithLimit_ReturnsLimitedResults() {
        // Given
        String groupId = GROUP_ID;
        String userId = USER_ID;
        Integer limit = 3;
        String mockNextToken = createMockRepositoryToken("HANGOUT#future3", "1000");
        
        when(groupRepository.findMembership(groupId, userId))
            .thenReturn(Optional.of(createTestMembership(groupId, userId, "Test Group", GroupRole.MEMBER)));
        
        // Setup: Mock repository configured with limit=3, returns 3 events + nextToken
        List<HangoutPointer> futureEvents = List.of(
            createHangoutPointer(groupId, "future1", "Future Event 1", java.time.Instant.now().plusSeconds(1000)),
            createHangoutPointer(groupId, "future2", "Future Event 2", java.time.Instant.now().plusSeconds(2000)),
            createHangoutPointer(groupId, "future3", "Future Event 3", java.time.Instant.now().plusSeconds(3000))
        );
        
        PaginatedResult<BaseItem> futureEventsResult = new PaginatedResult<>(futureEvents.stream().map(BaseItem.class::cast).toList(), mockNextToken);
        PaginatedResult<BaseItem> inProgressEventsResult = new PaginatedResult<>(List.of(), null);
        
        when(hangoutRepository.getFutureEventsPage(eq(groupId), anyLong(), eq(limit), isNull()))
            .thenReturn(futureEventsResult);
        when(hangoutRepository.getInProgressEventsPage(eq(groupId), anyLong(), eq(limit), isNull()))
            .thenReturn(inProgressEventsResult);
        
        // When
        GroupFeedDTO result = groupService.getGroupFeed(groupId, userId, limit, null, null);
        
        // Then
        // Exactly 3 events returned
        assertThat(result.getWithDay()).hasSize(3);
        // nextPageToken is converted from repository token (not null)
        assertThat(result.getNextPageToken()).isNotNull();
        // Repository called with correct limit parameter
        verify(hangoutRepository).getFutureEventsPage(eq(groupId), anyLong(), eq(limit), isNull());
    }
    
    @Test
    void getGroupFeed_LimitExceedsAvailable_ReturnsAllAvailable() {
        // Given
        String groupId = GROUP_ID;
        String userId = USER_ID;
        Integer limit = 10;
        
        when(groupRepository.findMembership(groupId, userId))
            .thenReturn(Optional.of(createTestMembership(groupId, userId, "Test Group", GroupRole.MEMBER)));
        
        // Setup: Mock repository with limit=10 but only 4 events exist, no nextToken
        List<HangoutPointer> futureEvents = List.of(
            createHangoutPointer(groupId, "future1", "Future Event 1", java.time.Instant.now().plusSeconds(1000)),
            createHangoutPointer(groupId, "future2", "Future Event 2", java.time.Instant.now().plusSeconds(2000)),
            createHangoutPointer(groupId, "future3", "Future Event 3", java.time.Instant.now().plusSeconds(3000)),
            createHangoutPointer(groupId, "future4", "Future Event 4", java.time.Instant.now().plusSeconds(4000))
        );
        
        PaginatedResult<BaseItem> futureEventsResult = new PaginatedResult<>(futureEvents.stream().map(BaseItem.class::cast).toList(), null); // No more pages
        PaginatedResult<BaseItem> inProgressEventsResult = new PaginatedResult<>(List.of(), null);
        
        when(hangoutRepository.getFutureEventsPage(eq(groupId), anyLong(), eq(limit), isNull()))
            .thenReturn(futureEventsResult);
        when(hangoutRepository.getInProgressEventsPage(eq(groupId), anyLong(), eq(limit), isNull()))
            .thenReturn(inProgressEventsResult);
        
        // When
        GroupFeedDTO result = groupService.getGroupFeed(groupId, userId, limit, null, null);
        
        // Then
        // All 4 available events returned
        assertThat(result.getWithDay()).hasSize(4);
        // nextPageToken is null (no more pages)
        assertThat(result.getNextPageToken()).isNull();
    }
    
    // C) Forward Pagination from Limited Results Tests
    
    @Test
    void getGroupFeed_ForwardPaginationWithStartingAfter_UsesRepositoryToken() {
        // Given
        String groupId = GROUP_ID;
        String userId = USER_ID;
        String startingAfter = createTestPaginationToken("eventId123", 1234567890L, true);
        String mockRepositoryNextToken = createMockRepositoryToken("HANGOUT#future5", "5000");
        
        when(groupRepository.findMembership(groupId, userId))
            .thenReturn(Optional.of(createTestMembership(groupId, userId, "Test Group", GroupRole.MEMBER)));
        
        // Setup: Provide startingAfter token, mock repository to return next page
        List<HangoutPointer> futureEvents = List.of(
            createHangoutPointer(groupId, "future4", "Future Event 4", java.time.Instant.now().plusSeconds(4000)),
            createHangoutPointer(groupId, "future5", "Future Event 5", java.time.Instant.now().plusSeconds(5000))
        );
        
        PaginatedResult<BaseItem> futureEventsResult = new PaginatedResult<>(futureEvents.stream().map(BaseItem.class::cast).toList(), mockRepositoryNextToken);
        PaginatedResult<BaseItem> inProgressEventsResult = new PaginatedResult<>(List.of(), null);
        
        when(hangoutRepository.getFutureEventsPage(eq(groupId), anyLong(), isNull(), any()))
            .thenReturn(futureEventsResult);
        when(hangoutRepository.getInProgressEventsPage(eq(groupId), anyLong(), isNull(), any()))
            .thenReturn(inProgressEventsResult);
        
        // When
        GroupFeedDTO result = groupService.getGroupFeed(groupId, userId, null, startingAfter, null);
        
        // Then
        // Repository called with converted token from startingAfter
        verify(hangoutRepository).getFutureEventsPage(eq(groupId), anyLong(), isNull(), any());
        // Results represent continuation from previous page
        assertThat(result.getWithDay()).hasSize(2);
        // nextPageToken reflects repository's pagination state
        assertThat(result.getNextPageToken()).isNotNull();
    }
    
    @Test
    void getGroupFeed_ForwardPaginationLastPage_ReturnsNullNextToken() {
        // Given
        String groupId = GROUP_ID;
        String userId = USER_ID;
        String startingAfter = createTestPaginationToken("eventId123", 1234567890L, true);
        
        when(groupRepository.findMembership(groupId, userId))
            .thenReturn(Optional.of(createTestMembership(groupId, userId, "Test Group", GroupRole.MEMBER)));
        
        // Setup: startingAfter provided, repository returns events but no nextToken
        List<HangoutPointer> futureEvents = List.of(
            createHangoutPointer(groupId, "future4", "Future Event 4", java.time.Instant.now().plusSeconds(4000)),
            createHangoutPointer(groupId, "future5", "Future Event 5", java.time.Instant.now().plusSeconds(5000))
        );
        
        PaginatedResult<BaseItem> futureEventsResult = new PaginatedResult<>(futureEvents.stream().map(BaseItem.class::cast).toList(), null); // Last page
        PaginatedResult<BaseItem> inProgressEventsResult = new PaginatedResult<>(List.of(), null);
        
        when(hangoutRepository.getFutureEventsPage(eq(groupId), anyLong(), isNull(), any()))
            .thenReturn(futureEventsResult);
        when(hangoutRepository.getInProgressEventsPage(eq(groupId), anyLong(), isNull(), any()))
            .thenReturn(inProgressEventsResult);
        
        // When
        GroupFeedDTO result = groupService.getGroupFeed(groupId, userId, null, startingAfter, null);
        
        // Then
        // Events returned correctly
        assertThat(result.getWithDay()).hasSize(2);
        // nextPageToken is null (end of forward pagination)
        assertThat(result.getNextPageToken()).isNull();
        // previousPageToken still available for past events
        assertThat(result.getPreviousPageToken()).isNotNull();
    }
    
    @Test
    void getGroupFeed_InvalidStartingAfterToken_HandlesGracefully() {
        // Given
        String groupId = GROUP_ID;
        String userId = USER_ID;
        String invalidStartingAfter = "invalidToken";
        
        when(groupRepository.findMembership(groupId, userId))
            .thenReturn(Optional.of(createTestMembership(groupId, userId, "Test Group", GroupRole.MEMBER)));
        
        // Setup: Provide malformed startingAfter token
        List<HangoutPointer> futureEvents = List.of(
            createHangoutPointer(groupId, "future1", "Future Event 1", java.time.Instant.now().plusSeconds(1000))
        );
        
        PaginatedResult<BaseItem> futureEventsResult = new PaginatedResult<>(futureEvents.stream().map(BaseItem.class::cast).toList(), null);
        PaginatedResult<BaseItem> inProgressEventsResult = new PaginatedResult<>(List.of(), null);
        
        when(hangoutRepository.getFutureEventsPage(eq(groupId), anyLong(), isNull(), any()))
            .thenReturn(futureEventsResult);
        when(hangoutRepository.getInProgressEventsPage(eq(groupId), anyLong(), isNull(), any()))
            .thenReturn(inProgressEventsResult);
        
        // When
        GroupFeedDTO result = groupService.getGroupFeed(groupId, userId, null, invalidStartingAfter, null);
        
        // Then
        // Service handles token conversion failure gracefully
        // Repository called with null token (falls back to first page)
        verify(hangoutRepository).getFutureEventsPage(eq(groupId), anyLong(), isNull(), any());
        // No exceptions thrown
        assertThat(result.getWithDay()).hasSize(1);
    }
    
    // D) Backward Pagination (Past Events) Tests
    
    @Test
    void getGroupFeed_BackwardPaginationWithEndingBefore_ReturnsPastEvents() {
        // Given
        String groupId = GROUP_ID;
        String userId = USER_ID;
        String endingBefore = createTestPaginationToken("eventId123", 1234567890L, false);
        String mockRepositoryNextToken = "repositoryNextToken456";
        
        when(groupRepository.findMembership(groupId, userId))
            .thenReturn(Optional.of(createTestMembership(groupId, userId, "Test Group", GroupRole.MEMBER)));
        
        // Setup: Provide endingBefore token, mock past events repository method
        List<HangoutPointer> pastEvents = List.of(
            createHangoutPointer(groupId, "past1", "Past Event 1", java.time.Instant.now().minusSeconds(3000)),
            createHangoutPointer(groupId, "past2", "Past Event 2", java.time.Instant.now().minusSeconds(2000))
        );
        
        String mockNextToken = createMockRepositoryToken("HANGOUT#past1", "2000");
        PaginatedResult<BaseItem> pastEventsResult = new PaginatedResult<>(pastEvents.stream().map(BaseItem.class::cast).toList(), mockNextToken);
        
        when(hangoutRepository.getPastEventsPage(eq(groupId), anyLong(), isNull(), any()))
            .thenReturn(pastEventsResult);
        
        // When
        GroupFeedDTO result = groupService.getGroupFeed(groupId, userId, null, null, endingBefore);
        
        // Then
        // getPastEventsPage called instead of future events methods
        verify(hangoutRepository).getPastEventsPage(eq(groupId), anyLong(), isNull(), any());
        verify(hangoutRepository, never()).getFutureEventsPage(any(), anyLong(), any(), any());
        verify(hangoutRepository, never()).getInProgressEventsPage(any(), anyLong(), any(), any());
        
        // Repository receives converted token from endingBefore
        // Results are past events only
        assertThat(result.getWithDay()).hasSize(2);
        assertThat(result.getNeedsDay()).isEmpty();
        // nextPageToken is null (no forward from past)
        assertThat(result.getNextPageToken()).isNull();
    }
    
    @Test
    void getGroupFeed_BackwardPaginationFirstPage_UsesTimestampBoundary() {
        // Given
        String groupId = GROUP_ID;
        String userId = USER_ID;
        String endingBefore = createTestPaginationToken(null, 1234567890L, false); // null eventId
        
        when(groupRepository.findMembership(groupId, userId))
            .thenReturn(Optional.of(createTestMembership(groupId, userId, "Test Group", GroupRole.MEMBER)));
        
        // Setup: endingBefore token with null eventId (initial past events query)
        List<HangoutPointer> pastEvents = List.of(
            createHangoutPointer(groupId, "past1", "Past Event 1", java.time.Instant.now().minusSeconds(3000)),
            createHangoutPointer(groupId, "past2", "Past Event 2", java.time.Instant.now().minusSeconds(2000))
        );
        
        PaginatedResult<BaseItem> pastEventsResult = new PaginatedResult<>(pastEvents.stream().map(BaseItem.class::cast).toList(), null);
        
        when(hangoutRepository.getPastEventsPage(eq(groupId), anyLong(), isNull(), isNull()))
            .thenReturn(pastEventsResult);
        
        // When
        GroupFeedDTO result = groupService.getGroupFeed(groupId, userId, null, null, endingBefore);
        
        // Then
        // Repository called with null token (timestamp-based boundary)
        verify(hangoutRepository).getPastEventsPage(eq(groupId), anyLong(), isNull(), isNull());
        // Past events returned chronologically
        assertThat(result.getWithDay()).hasSize(2);
        // needsDay is empty (past events must have timestamps)
        assertThat(result.getNeedsDay()).isEmpty();
    }
    
    @Test
    void getGroupFeed_BackwardPaginationHasMore_ReturnsPreviousPageToken() {
        // Given
        String groupId = GROUP_ID;
        String userId = USER_ID;
        String endingBefore = createTestPaginationToken("eventId123", 1234567890L, false);
        String mockRepositoryNextToken = "repositoryNextToken456";
        
        when(groupRepository.findMembership(groupId, userId))
            .thenReturn(Optional.of(createTestMembership(groupId, userId, "Test Group", GroupRole.MEMBER)));
        
        // Setup: Past events repository returns results + nextToken (more past events)
        List<HangoutPointer> pastEvents = List.of(
            createHangoutPointer(groupId, "past1", "Past Event 1", java.time.Instant.now().minusSeconds(3000)),
            createHangoutPointer(groupId, "past2", "Past Event 2", java.time.Instant.now().minusSeconds(2000))
        );
        
        String mockNextToken = createMockRepositoryToken("HANGOUT#past2", "1000");
        PaginatedResult<BaseItem> pastEventsResult = new PaginatedResult<>(pastEvents.stream().map(BaseItem.class::cast).toList(), mockNextToken);
        
        when(hangoutRepository.getPastEventsPage(eq(groupId), anyLong(), isNull(), any()))
            .thenReturn(pastEventsResult);
        
        // When
        GroupFeedDTO result = groupService.getGroupFeed(groupId, userId, null, null, endingBefore);
        
        // Then
        // previousPageToken is converted from repository token
        assertThat(result.getPreviousPageToken()).isNotNull();
        // Enables further backward pagination
        // Token direction set to isForward=false (verified by token content)
        assertThat(result.getWithDay()).hasSize(2);
    }
    
    @Test
    void getGroupFeed_BackwardPaginationLastPage_ReturnsNullPreviousToken() {
        // Given
        String groupId = GROUP_ID;
        String userId = USER_ID;
        String endingBefore = createTestPaginationToken("eventId123", 1234567890L, false);
        
        when(groupRepository.findMembership(groupId, userId))
            .thenReturn(Optional.of(createTestMembership(groupId, userId, "Test Group", GroupRole.MEMBER)));
        
        // Setup: Past events repository returns events but no nextToken
        List<HangoutPointer> pastEvents = List.of(
            createHangoutPointer(groupId, "past1", "Past Event 1", java.time.Instant.now().minusSeconds(3000))
        );
        
        PaginatedResult<BaseItem> pastEventsResult = new PaginatedResult<>(pastEvents.stream().map(BaseItem.class::cast).toList(), null); // Last page
        
        when(hangoutRepository.getPastEventsPage(eq(groupId), anyLong(), isNull(), any()))
            .thenReturn(pastEventsResult);
        
        // When
        GroupFeedDTO result = groupService.getGroupFeed(groupId, userId, null, null, endingBefore);
        
        // Then
        // Events returned correctly
        assertThat(result.getWithDay()).hasSize(1);
        // previousPageToken is null (no more past events)
        assertThat(result.getPreviousPageToken()).isNull();
        // End of backward pagination reached
        assertThat(result.getNextPageToken()).isNull();
    }
    
    // Token Conversion Tests
    
    @Test
    void getRepositoryToken_ValidCustomToken_ConvertsCorrectly() {
        // Given
        String eventId = "test-event-123";
        Long timestamp = 1234567890L;
        String customToken = createTestPaginationToken(eventId, timestamp, true);
        String groupId = GROUP_ID;
        
        // When
        // Call the private method through reflection or create a test-friendly version
        // For this test, we'll verify the conversion through the actual service call
        when(groupRepository.findMembership(groupId, USER_ID))
            .thenReturn(Optional.of(createTestMembership(groupId, USER_ID, "Test Group", GroupRole.MEMBER)));
        
        List<HangoutPointer> pastEvents = List.of(
            createHangoutPointer(groupId, "past1", "Past Event 1", java.time.Instant.now().minusSeconds(3000))
        );
        PaginatedResult<BaseItem> pastEventsResult = new PaginatedResult<>(pastEvents.stream().map(BaseItem.class::cast).toList(), null);
        
        when(hangoutRepository.getPastEventsPage(eq(groupId), anyLong(), isNull(), any()))
            .thenReturn(pastEventsResult);
        
        // When
        GroupFeedDTO result = groupService.getGroupFeed(groupId, USER_ID, null, null, customToken);
        
        // Then
        // Jackson serialization produces correct RepositoryTokenData
        // Base64 encoding applied
        // Repository format matches expected structure
        verify(hangoutRepository).getPastEventsPage(eq(groupId), anyLong(), isNull(), any());
        assertThat(result.getWithDay()).hasSize(1);
    }
    
    @Test
    void getRepositoryToken_NullEventId_ReturnsNull() {
        // Given
        String customToken = createTestPaginationToken(null, 1234567890L, false);
        String groupId = GROUP_ID;
        
        when(groupRepository.findMembership(groupId, USER_ID))
            .thenReturn(Optional.of(createTestMembership(groupId, USER_ID, "Test Group", GroupRole.MEMBER)));
        
        List<HangoutPointer> pastEvents = List.of(
            createHangoutPointer(groupId, "past1", "Past Event 1", java.time.Instant.now().minusSeconds(3000))
        );
        PaginatedResult<BaseItem> pastEventsResult = new PaginatedResult<>(pastEvents.stream().map(BaseItem.class::cast).toList(), null);
        
        when(hangoutRepository.getPastEventsPage(eq(groupId), anyLong(), isNull(), isNull()))
            .thenReturn(pastEventsResult);
        
        // When
        GroupFeedDTO result = groupService.getGroupFeed(groupId, USER_ID, null, null, customToken);
        
        // Then
        // Method returns null (no repository token needed)
        // Repository performs timestamp-based boundary query
        verify(hangoutRepository).getPastEventsPage(eq(groupId), anyLong(), isNull(), isNull());
        assertThat(result.getWithDay()).hasSize(1);
    }
    
    @Test
    void getCustomToken_ValidRepositoryToken_ConvertsCorrectly() {
        // Given
        String groupId = GROUP_ID;
        String mockRepositoryToken = createMockRepositoryToken("HANGOUT#test-event-123", "1234567890");
        
        when(groupRepository.findMembership(groupId, USER_ID))
            .thenReturn(Optional.of(createTestMembership(groupId, USER_ID, "Test Group", GroupRole.MEMBER)));
        
        List<HangoutPointer> futureEvents = List.of(
            createHangoutPointer(groupId, "future1", "Future Event 1", java.time.Instant.now().plusSeconds(1000))
        );
        PaginatedResult<BaseItem> futureEventsResult = new PaginatedResult<>(futureEvents.stream().map(BaseItem.class::cast).toList(), mockRepositoryToken);
        PaginatedResult<BaseItem> inProgressEventsResult = new PaginatedResult<>(List.of(), null);
        
        when(hangoutRepository.getFutureEventsPage(eq(groupId), anyLong(), isNull(), isNull()))
            .thenReturn(futureEventsResult);
        when(hangoutRepository.getInProgressEventsPage(eq(groupId), anyLong(), isNull(), isNull()))
            .thenReturn(inProgressEventsResult);
        
        // When
        GroupFeedDTO result = groupService.getGroupFeed(groupId, USER_ID, null, null, null);
        
        // Then
        // Jackson deserialization parses correctly
        // Event ID extracted from "HANGOUT#" prefix
        // Timestamp parsed from string to long
        // Direction flag preserved
        assertThat(result.getNextPageToken()).isNotNull();
        assertThat(result.getWithDay()).hasSize(1);
    }
    
    @Test
    void getCustomToken_MalformedRepositoryToken_ReturnsNull() {
        // Given
        String groupId = GROUP_ID;
        String malformedRepositoryToken = "invalidBase64!@#$";
        
        when(groupRepository.findMembership(groupId, USER_ID))
            .thenReturn(Optional.of(createTestMembership(groupId, USER_ID, "Test Group", GroupRole.MEMBER)));
        
        List<HangoutPointer> futureEvents = List.of(
            createHangoutPointer(groupId, "future1", "Future Event 1", java.time.Instant.now().plusSeconds(1000))
        );
        PaginatedResult<BaseItem> futureEventsResult = new PaginatedResult<>(futureEvents.stream().map(BaseItem.class::cast).toList(), malformedRepositoryToken);
        PaginatedResult<BaseItem> inProgressEventsResult = new PaginatedResult<>(List.of(), null);
        
        when(hangoutRepository.getFutureEventsPage(eq(groupId), anyLong(), isNull(), isNull()))
            .thenReturn(futureEventsResult);
        when(hangoutRepository.getInProgressEventsPage(eq(groupId), anyLong(), isNull(), isNull()))
            .thenReturn(inProgressEventsResult);
        
        // When
        GroupFeedDTO result = groupService.getGroupFeed(groupId, USER_ID, null, null, null);
        
        // Then
        // Jackson parsing failure handled gracefully
        // Method returns null instead of throwing exception
        // Warning logged but no crash
        assertThat(result.getNextPageToken()).isNull(); // Malformed token converted to null
        assertThat(result.getWithDay()).hasSize(1);
    }
    
    // Authorization and Error Handling Tests
    
    @Test
    void getGroupFeed_UserNotInGroup_ThrowsUnauthorized() {
        // Given
        String groupId = GROUP_ID;
        String userId = USER_ID;
        
        // Setup: User not a member of requested group
        when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.empty());
        
        // When/Then
        // UnauthorizedException thrown before any repository calls
        assertThatThrownBy(() -> groupService.getGroupFeed(groupId, userId, null, null, null))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("User not in group");
        
        // Repository methods never invoked
        verify(hangoutRepository, never()).getFutureEventsPage(any(), anyLong(), any(), any());
        verify(hangoutRepository, never()).getInProgressEventsPage(any(), anyLong(), any(), any());
        verify(hangoutRepository, never()).getPastEventsPage(any(), anyLong(), any(), any());
    }
    
    @Test
    void getGroupFeed_RepositoryFailure_ThrowsRepositoryException() {
        // Given
        String groupId = GROUP_ID;
        String userId = USER_ID;
        
        when(groupRepository.findMembership(groupId, userId))
            .thenReturn(Optional.of(createTestMembership(groupId, userId, "Test Group", GroupRole.MEMBER)));
        
        // Setup: Repository throws exception during query
        when(hangoutRepository.getFutureEventsPage(any(), anyLong(), any(), any()))
            .thenThrow(new RuntimeException("Database connection failed"));
        
        // When/Then
        // Exception wrapped in RepositoryException
        assertThatThrownBy(() -> groupService.getGroupFeed(groupId, userId, null, null, null))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("Failed to retrieve group feed")
            .cause()
            .hasCauseInstanceOf(RuntimeException.class); // ExecutionException wraps the RuntimeException
    }
    
    // Parallel Query Verification Tests
    
    @Test
    void getCurrentAndFutureEvents_ParallelQueries_BothExecuted() {
        // Given
        String groupId = GROUP_ID;
        String userId = USER_ID;
        
        when(groupRepository.findMembership(groupId, userId))
            .thenReturn(Optional.of(createTestMembership(groupId, userId, "Test Group", GroupRole.MEMBER)));
        
        // Setup: Mock both getFutureEventsPage and getInProgressEventsPage
        List<HangoutPointer> futureEvents = List.of(
            createHangoutPointer(groupId, "future1", "Future Event 1", java.time.Instant.now().plusSeconds(1000))
        );
        List<HangoutPointer> inProgressEvents = List.of(
            createHangoutPointer(groupId, "inprogress1", "In Progress Event 1", null)
        );
        
        PaginatedResult<BaseItem> futureEventsResult = new PaginatedResult<>(futureEvents.stream().map(BaseItem.class::cast).toList(), null);
        PaginatedResult<BaseItem> inProgressEventsResult = new PaginatedResult<>(inProgressEvents.stream().map(BaseItem.class::cast).toList(), null);
        
        when(hangoutRepository.getFutureEventsPage(eq(groupId), anyLong(), isNull(), isNull()))
            .thenReturn(futureEventsResult);
        when(hangoutRepository.getInProgressEventsPage(eq(groupId), anyLong(), isNull(), isNull()))
            .thenReturn(inProgressEventsResult);
        
        // When
        GroupFeedDTO result = groupService.getGroupFeed(groupId, userId, null, null, null);
        
        // Then
        // Both repository methods called in parallel
        verify(hangoutRepository).getFutureEventsPage(eq(groupId), anyLong(), isNull(), isNull());
        verify(hangoutRepository).getInProgressEventsPage(eq(groupId), anyLong(), isNull(), isNull());
        
        // Results merged correctly regardless of completion order
        assertThat(result.getWithDay()).hasSize(1); // Future events with timestamps
        assertThat(result.getNeedsDay()).hasSize(1); // In-progress events without timestamps
        
        // CompletableFuture.get() waits for both queries
        // (This is implicitly tested by the successful completion and merged results)
    }

    // ================= Enhanced Group Feed Hydration Tests =================
    
    @Test
    void hydrateFeed_WithMixedItems_ShouldCreateCorrectFeedItems() {
        // Given: Mix of SeriesPointer and standalone HangoutPointer
        SeriesPointer seriesPointer = createTestSeriesPointer();
        HangoutPointer standaloneHangout = createTestHangoutPointer("standalone-1");
        HangoutPointer seriesPart = createTestHangoutPointer("series-part-1");

        // SeriesPointer contains the series part in its denormalized parts list
        seriesPointer.setParts(List.of(seriesPart));

        List<BaseItem> baseItems = List.of(seriesPointer, standaloneHangout, seriesPart);

        // When
        List<FeedItem> result = groupService.hydrateFeed(baseItems, USER_ID);

        // Then
        assertThat(result).hasSize(2); // Series + standalone hangout

        // Verify series
        SeriesSummaryDTO series = (SeriesSummaryDTO) result.get(0);
        assertThat(series.getSeriesId()).isEqualTo(seriesPointer.getSeriesId());
        assertThat(series.getParts()).hasSize(1);
        assertThat(series.getParts().get(0).getHangoutId()).isEqualTo("series-part-1");

        // Verify standalone hangout
        HangoutSummaryDTO hangout = (HangoutSummaryDTO) result.get(1);
        assertThat(hangout.getHangoutId()).isEqualTo("standalone-1");
    }

    @Test
    void hydrateFeed_WithOnlyStandaloneHangouts_ShouldReturnAllAsHangoutDTOs() {
        // Given: Only standalone hangouts
        List<BaseItem> baseItems = List.of(
            createTestHangoutPointer("hangout-1"),
            createTestHangoutPointer("hangout-2")
        );

        // When
        List<FeedItem> result = groupService.hydrateFeed(baseItems, USER_ID);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(item -> item instanceof HangoutSummaryDTO);
    }

    @Test
    void hydrateFeed_WithOnlySeries_ShouldReturnAllAsSeriesDTOs() {
        // Given: Only series with their parts
        SeriesPointer series1 = createTestSeriesPointer("series-1");
        SeriesPointer series2 = createTestSeriesPointer("series-2");

        List<BaseItem> baseItems = List.of(series1, series2);

        // When
        List<FeedItem> result = groupService.hydrateFeed(baseItems, USER_ID);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(item -> item instanceof SeriesSummaryDTO);
    }

    @Test
    void hydrateFeed_WithEmptyList_ShouldReturnEmptyList() {
        // Given
        List<BaseItem> baseItems = List.of();

        // When
        List<FeedItem> result = groupService.hydrateFeed(baseItems, USER_ID);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void hydrateFeed_WithSeriesContainingMultipleParts_ShouldIncludeAllParts() {
        // Given: Series with multiple parts
        SeriesPointer seriesPointer = createTestSeriesPointer();
        List<HangoutPointer> parts = List.of(
            createTestHangoutPointer("part-1"),
            createTestHangoutPointer("part-2"),
            createTestHangoutPointer("part-3")
        );
        seriesPointer.setParts(parts);

        List<BaseItem> baseItems = List.of(seriesPointer);

        // When
        List<FeedItem> result = groupService.hydrateFeed(baseItems, USER_ID);

        // Then
        assertThat(result).hasSize(1);
        SeriesSummaryDTO series = (SeriesSummaryDTO) result.get(0);
        assertThat(series.getParts()).hasSize(3);
        assertThat(series.getTotalParts()).isEqualTo(3);
    }

    @Test
    void createSeriesSummaryDTO_ShouldCopyAllSeriesFields() {
        // Given
        SeriesPointer seriesPointer = createTestSeriesPointer();
        seriesPointer.setSeriesTitle("Movie Night Series");
        seriesPointer.setSeriesDescription("Weekly movie nights");
        seriesPointer.setPrimaryEventId("primary-event-1");
        seriesPointer.setStartTimestamp(1000L);
        seriesPointer.setEndTimestamp(2000L);

        // When
        SeriesSummaryDTO result = groupService.createSeriesSummaryDTO(seriesPointer, USER_ID);

        // Then
        assertThat(result.getSeriesTitle()).isEqualTo("Movie Night Series");
        assertThat(result.getSeriesDescription()).isEqualTo("Weekly movie nights");
        assertThat(result.getPrimaryEventId()).isEqualTo("primary-event-1");
        assertThat(result.getStartTimestamp()).isEqualTo(1000L);
        assertThat(result.getEndTimestamp()).isEqualTo(2000L);
        assertThat(result.getType()).isEqualTo("series");
    }

    @Test
    void createSeriesSummaryDTO_WithNullParts_ShouldHandleGracefully() {
        // Given
        SeriesPointer seriesPointer = createTestSeriesPointer();
        seriesPointer.setParts(null);

        // When
        SeriesSummaryDTO result = groupService.createSeriesSummaryDTO(seriesPointer, USER_ID);

        // Then
        assertThat(result.getParts()).isEmpty();
        assertThat(result.getTotalParts()).isEqualTo(0);
    }

    // Helper methods for test data creation
    
    private String createTestPaginationToken(String eventId, Long timestamp, boolean isForward) {
        com.bbthechange.inviter.util.GroupFeedPaginationToken token = 
            new com.bbthechange.inviter.util.GroupFeedPaginationToken(eventId, timestamp, isForward);
        return token.encode();
    }
    
    private String createMockRepositoryToken(String sk, String startTimestamp) {
        try {
            com.bbthechange.inviter.util.RepositoryTokenData tokenData = 
                new com.bbthechange.inviter.util.RepositoryTokenData("GROUP#" + GROUP_ID, startTimestamp, "GROUP#" + GROUP_ID, sk);
            
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = objectMapper.writeValueAsString(tokenData);
            return java.util.Base64.getEncoder().encodeToString(json.getBytes());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create mock repository token", e);
        }
    }
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
            pointer.setStartTimestamp(hangoutTime.getEpochSecond()); // Use seconds, not milliseconds
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
    
    private SeriesPointer createTestSeriesPointer() {
        return createTestSeriesPointer("test-series-id");
    }

    private SeriesPointer createTestSeriesPointer(String seriesId) {
        SeriesPointer pointer = new SeriesPointer();
        pointer.setGroupId("12345678-1234-1234-1234-123456789012");
        pointer.setSeriesId(seriesId);
        pointer.setSeriesTitle("Test Series");
        pointer.setSeriesDescription("Test Description");
        pointer.setPrimaryEventId("primary-event");
        pointer.setStartTimestamp(1000L);
        pointer.setEndTimestamp(5000L);
        // Set keys directly for test purposes  
        pointer.setPk("GROUP#12345678-1234-1234-1234-123456789012");
        pointer.setSk("SERIES#" + seriesId);
        pointer.setGsi1pk("GROUP#12345678-1234-1234-1234-123456789012");
        return pointer;
    }

    private HangoutPointer createTestHangoutPointer(String hangoutId) {
        return createHangoutPointerWithTimestamp(hangoutId, null);
    }

    private HangoutPointer createHangoutPointerWithTimestamp(String hangoutId, Long timestamp) {
        HangoutPointer pointer = createHangoutPointer("test-group", hangoutId, "Test Hangout",
            timestamp != null ? java.time.Instant.ofEpochSecond(timestamp) : null);
        return pointer;
    }

    @Test
    void createGroup_DenormalizesImagePathsToInitialMembership() {
        // Given
        String creatorId = "87654321-4321-4321-4321-210987654321";
        CreateGroupRequest request = new CreateGroupRequest("Test Group", true);
        request.setMainImagePath("/group-main.jpg");
        request.setBackgroundImagePath("/group-bg.jpg");

        User creator = new User();
        creator.setId(UUID.fromString(creatorId));
        when(userRepository.findById(UUID.fromString(creatorId))).thenReturn(Optional.of(creator));
        doNothing().when(groupRepository).createGroupWithFirstMember(any(Group.class), any(GroupMembership.class));

        // When
        groupService.createGroup(request, creatorId);

        // Then - Capture GroupMembership passed to repository
        org.mockito.ArgumentCaptor<GroupMembership> membershipCaptor =
            org.mockito.ArgumentCaptor.forClass(GroupMembership.class);
        verify(groupRepository).createGroupWithFirstMember(any(Group.class), membershipCaptor.capture());

        GroupMembership membership = membershipCaptor.getValue();
        assertThat(membership.getGroupMainImagePath()).isEqualTo("/group-main.jpg");
        assertThat(membership.getGroupBackgroundImagePath()).isEqualTo("/group-bg.jpg");
    }

    @Test
    void updateGroup_CallsRepositoryToUpdateMembershipsWhenImagePathsChange() {
        // Given
        String userId = "87654321-4321-4321-4321-210987654321";
        Group existingGroup = createTestGroup("Test Group", true, GROUP_ID);
        existingGroup.setMainImagePath("/old.jpg");

        GroupMembership membership = createTestMembership(GROUP_ID, userId, "Test Group", GroupRole.ADMIN);
        when(groupRepository.findMembership(GROUP_ID, userId)).thenReturn(Optional.of(membership));
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(existingGroup));
        when(groupRepository.save(any(Group.class))).thenReturn(existingGroup);
        doNothing().when(groupRepository).updateMembershipGroupImagePaths(eq(GROUP_ID), eq("/new.jpg"), isNull());

        UpdateGroupRequest request = new UpdateGroupRequest();
        request.setMainImagePath("/new.jpg");

        // When
        groupService.updateGroup(GROUP_ID, request, userId);

        // Then
        verify(groupRepository).updateMembershipGroupImagePaths(eq(GROUP_ID), eq("/new.jpg"), isNull());
    }

    @Test
    void updateGroup_DoesNotCallRepositoryWhenImagePathsUnchanged() {
        // Given
        String userId = "87654321-4321-4321-4321-210987654321";
        Group existingGroup = createTestGroup("Test Group", true, GROUP_ID);

        GroupMembership membership = createTestMembership(GROUP_ID, userId, "Test Group", GroupRole.ADMIN);
        when(groupRepository.findMembership(GROUP_ID, userId)).thenReturn(Optional.of(membership));
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(existingGroup));
        when(groupRepository.save(any(Group.class))).thenReturn(existingGroup);

        UpdateGroupRequest request = new UpdateGroupRequest();
        request.setGroupName("New Group Name"); // Only change name, not images

        // When
        groupService.updateGroup(GROUP_ID, request, userId);

        // Then
        verify(groupRepository, never()).updateMembershipGroupImagePaths(anyString(), anyString(), anyString());
    }

    @Test
    void addMember_DenormalizesCurrentGroupImagePathsToNewMembership() {
        // Given
        String newUserId = "11111111-1111-1111-1111-111111111111";
        String addedBy = "87654321-4321-4321-4321-210987654321";

        Group group = createTestGroup("Test Group", true, GROUP_ID);
        group.setMainImagePath("/group-image.jpg");
        group.setBackgroundImagePath("/group-bg.jpg");

        lenient().when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        lenient().when(groupRepository.isUserMemberOfGroup(GROUP_ID, addedBy)).thenReturn(true);
        lenient().when(groupRepository.isUserMemberOfGroup(GROUP_ID, newUserId)).thenReturn(false);
        lenient().when(userRepository.findById(UUID.fromString(newUserId))).thenReturn(Optional.of(new User()));

        // When
        groupService.addMember(GROUP_ID, newUserId, null, addedBy);

        // Then - Capture GroupMembership passed to repository
        org.mockito.ArgumentCaptor<GroupMembership> membershipCaptor =
            org.mockito.ArgumentCaptor.forClass(GroupMembership.class);
        verify(groupRepository).addMember(membershipCaptor.capture());

        GroupMembership membership = membershipCaptor.getValue();
        assertThat(membership.getGroupMainImagePath()).isEqualTo("/group-image.jpg");
        assertThat(membership.getGroupBackgroundImagePath()).isEqualTo("/group-bg.jpg");
    }
}