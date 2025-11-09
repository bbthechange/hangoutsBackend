package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.CreateHangoutRequest;
import com.bbthechange.inviter.dto.HangoutDetailData;
import com.bbthechange.inviter.model.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for group lastModified timestamp updates.
 *
 * Covers:
 * - Updating group timestamps when hangouts are created
 * - Updating group timestamps when hangouts are modified
 * - Updating group timestamps when hangouts are deleted
 * - Edge cases: null/empty group lists, missing groups, save failures
 */
class HangoutServiceGroupLastModifiedTest extends HangoutServiceTestBase {

    @Nested
    class UpdateGroupLastModified {

        @Test
        void createHangout_WithAssociatedGroups_UpdatesGroupLastModified() {
            // Given
            String userId = "87654321-4321-4321-4321-210987654321";
            String group1Id = "11111111-1111-1111-1111-111111111111";
            String group2Id = "22222222-2222-2222-2222-222222222222";

            CreateHangoutRequest request = new CreateHangoutRequest();
            request.setTitle("Test Hangout");
            request.setDescription("Test Description");
            request.setAssociatedGroups(List.of(group1Id, group2Id));

            // Mock group memberships
            GroupMembership membership1 = createTestMembership(group1Id, userId, "Group 1");
            GroupMembership membership2 = createTestMembership(group2Id, userId, "Group 2");
            when(groupRepository.findMembership(group1Id, userId)).thenReturn(Optional.of(membership1));
            when(groupRepository.findMembership(group2Id, userId)).thenReturn(Optional.of(membership2));

            // Mock groups for timestamp updates
            Group group1 = new Group();
            group1.setGroupId(group1Id);
            group1.setGroupName("Group 1");

            Group group2 = new Group();
            group2.setGroupId(group2Id);
            group2.setGroupName("Group 2");

            when(groupRepository.findById(group1Id)).thenReturn(Optional.of(group1));
            when(groupRepository.findById(group2Id)).thenReturn(Optional.of(group2));
            when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Mock repository to return hangout WITH associated groups set
            Hangout savedHangout = createTestHangout("test-hangout-id");
            savedHangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(group1Id, group2Id)));
            when(hangoutRepository.createHangoutWithAttributes(any(Hangout.class), anyList(), anyList(), anyList(), anyList()))
                .thenReturn(savedHangout);

            java.time.Instant beforeCall = java.time.Instant.now().minusSeconds(1);

            // When
            hangoutService.createHangout(request, userId);

            java.time.Instant afterCall = java.time.Instant.now().plusSeconds(1);

            // Then - Verify findById was called for both groups
            verify(groupRepository).findById(group1Id);
            verify(groupRepository).findById(group2Id);

            // Verify save was called for both groups with updated timestamps
            org.mockito.ArgumentCaptor<Group> groupCaptor = org.mockito.ArgumentCaptor.forClass(Group.class);
            verify(groupRepository, times(2)).save(groupCaptor.capture());

            List<Group> savedGroups = groupCaptor.getAllValues();
            assertThat(savedGroups).hasSize(2);

            // Verify timestamps are approximately "now" (within test execution window)
            for (Group savedGroup : savedGroups) {
                assertThat(savedGroup.getLastHangoutModified()).isNotNull();
                assertThat(savedGroup.getLastHangoutModified()).isBetween(beforeCall, afterCall);
            }
        }

        @Test
        void updateHangout_UpdatesGroupLastModified() {
            // Given
            String hangoutId = "12345678-1234-1234-1234-123456789012";
            String userId = "87654321-4321-4321-4321-210987654321";
            String groupId = "11111111-1111-1111-1111-111111111111";

            Hangout hangout = createTestHangout(hangoutId);
            hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(groupId)));

            HangoutDetailData data = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
            when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);

            // Mock authorization
            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
            membership.setRole(GroupRole.ADMIN);
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));

            // Mock group for timestamp update
            Group group = new Group();
            group.setGroupId(groupId);
            group.setGroupName("Test Group");
            when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
            when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));

            when(hangoutRepository.save(any(Hangout.class))).thenReturn(hangout);

            java.time.Instant beforeCall = java.time.Instant.now().minusSeconds(1);

            // When
            hangoutService.updateEventTitle(hangoutId, "New Title", userId);

            java.time.Instant afterCall = java.time.Instant.now().plusSeconds(1);

            // Then - Verify group timestamp was updated
            verify(groupRepository).findById(groupId);

            org.mockito.ArgumentCaptor<Group> groupCaptor = org.mockito.ArgumentCaptor.forClass(Group.class);
            verify(groupRepository).save(groupCaptor.capture());

            Group savedGroup = groupCaptor.getValue();
            assertThat(savedGroup.getLastHangoutModified()).isNotNull();
            assertThat(savedGroup.getLastHangoutModified()).isBetween(beforeCall, afterCall);
        }

        @Test
        void deleteHangout_UpdatesGroupLastModified() {
            // Given
            String hangoutId = "12345678-1234-1234-1234-123456789012";
            String userId = "87654321-4321-4321-4321-210987654321";
            String group1Id = "11111111-1111-1111-1111-111111111111";
            String group2Id = "22222222-2222-2222-2222-222222222222";

            Hangout hangout = createTestHangout(hangoutId);
            hangout.setSeriesId(null); // Standalone hangout
            hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(group1Id, group2Id)));

            // Mock authorization - user must be in at least one associated group as admin
            GroupMembership membership1 = createTestMembership(group1Id, userId, "Group 1");
            membership1.setRole(GroupRole.ADMIN);
            when(groupRepository.findMembership(group1Id, userId)).thenReturn(Optional.of(membership1));

            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
            doNothing().when(groupRepository).deleteHangoutPointer(anyString(), anyString());
            doNothing().when(hangoutRepository).deleteHangout(hangoutId);

            // Mock groups for timestamp updates
            Group group1 = new Group();
            group1.setGroupId(group1Id);
            group1.setGroupName("Group 1");

            Group group2 = new Group();
            group2.setGroupId(group2Id);
            group2.setGroupName("Group 2");

            when(groupRepository.findById(group1Id)).thenReturn(Optional.of(group1));
            when(groupRepository.findById(group2Id)).thenReturn(Optional.of(group2));
            when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));

            java.time.Instant beforeCall = java.time.Instant.now().minusSeconds(1);

            // When
            hangoutService.deleteHangout(hangoutId, userId);

            java.time.Instant afterCall = java.time.Instant.now().plusSeconds(1);

            // Then - Verify timestamps were updated for both groups even though hangout was deleted
            verify(groupRepository).findById(group1Id);
            verify(groupRepository).findById(group2Id);

            org.mockito.ArgumentCaptor<Group> groupCaptor = org.mockito.ArgumentCaptor.forClass(Group.class);
            verify(groupRepository, times(2)).save(groupCaptor.capture());

            List<Group> savedGroups = groupCaptor.getAllValues();
            assertThat(savedGroups).hasSize(2);

            for (Group savedGroup : savedGroups) {
                assertThat(savedGroup.getLastHangoutModified()).isNotNull();
                assertThat(savedGroup.getLastHangoutModified()).isBetween(beforeCall, afterCall);
            }
        }

        @Test
        void updateGroupLastModified_WithNullGroupList_DoesNothing() {
            // Given
            String userId = "87654321-4321-4321-4321-210987654321";

            CreateHangoutRequest request = new CreateHangoutRequest();
            request.setTitle("Test Hangout");
            request.setDescription("Test Description");
            request.setAssociatedGroups(null); // Null group list

            // Mock repository to return hangout
            Hangout savedHangout = createTestHangout("test-hangout-id");
            when(hangoutRepository.createHangoutWithAttributes(any(Hangout.class), anyList(), anyList(), anyList(), anyList()))
                .thenReturn(savedHangout);

            // When
            hangoutService.createHangout(request, userId);

            // Then - Verify no group operations were performed
            verify(groupRepository, never()).findById(anyString());
            verify(groupRepository, never()).save(any(Group.class));
        }

        @Test
        void updateGroupLastModified_WithEmptyGroupList_DoesNothing() {
            // Given
            String userId = "87654321-4321-4321-4321-210987654321";

            CreateHangoutRequest request = new CreateHangoutRequest();
            request.setTitle("Test Hangout");
            request.setDescription("Test Description");
            request.setAssociatedGroups(List.of()); // Empty group list

            // Mock repository to return hangout
            Hangout savedHangout = createTestHangout("test-hangout-id");
            when(hangoutRepository.createHangoutWithAttributes(any(Hangout.class), anyList(), anyList(), anyList(), anyList()))
                .thenReturn(savedHangout);

            // When
            hangoutService.createHangout(request, userId);

            // Then - Verify no group operations were performed
            verify(groupRepository, never()).findById(anyString());
            verify(groupRepository, never()).save(any(Group.class));
        }

        @Test
        void updateGroupLastModified_WhenGroupNotFound_ContinuesWithOtherGroups() {
            // Given
            String userId = "87654321-4321-4321-4321-210987654321";
            String group1Id = "11111111-1111-1111-1111-111111111111";
            String group2Id = "22222222-2222-2222-2222-222222222222";

            CreateHangoutRequest request = new CreateHangoutRequest();
            request.setTitle("Test Hangout");
            request.setDescription("Test Description");
            request.setAssociatedGroups(List.of(group1Id, group2Id));

            // Mock group memberships
            GroupMembership membership1 = createTestMembership(group1Id, userId, "Group 1");
            GroupMembership membership2 = createTestMembership(group2Id, userId, "Group 2");
            when(groupRepository.findMembership(group1Id, userId)).thenReturn(Optional.of(membership1));
            when(groupRepository.findMembership(group2Id, userId)).thenReturn(Optional.of(membership2));

            // Mock group1 not found, group2 exists
            when(groupRepository.findById(group1Id)).thenReturn(Optional.empty());

            Group group2 = new Group();
            group2.setGroupId(group2Id);
            group2.setGroupName("Group 2");
            when(groupRepository.findById(group2Id)).thenReturn(Optional.of(group2));
            when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Mock repository to return hangout WITH associated groups set
            Hangout savedHangout = createTestHangout("test-hangout-id");
            savedHangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(group1Id, group2Id)));
            when(hangoutRepository.createHangoutWithAttributes(any(Hangout.class), anyList(), anyList(), anyList(), anyList()))
                .thenReturn(savedHangout);

            // When
            hangoutService.createHangout(request, userId);

            // Then - Verify both groups were queried
            verify(groupRepository).findById(group1Id);
            verify(groupRepository).findById(group2Id);

            // Verify only group2 was saved (group1 not found)
            org.mockito.ArgumentCaptor<Group> groupCaptor = org.mockito.ArgumentCaptor.forClass(Group.class);
            verify(groupRepository, times(1)).save(groupCaptor.capture());

            Group savedGroup = groupCaptor.getValue();
            assertThat(savedGroup.getGroupId()).isEqualTo(group2Id);
            assertThat(savedGroup.getLastHangoutModified()).isNotNull();
        }

        @Test
        void updateGroupLastModified_WhenSaveThrowsException_ContinuesWithOtherGroups() {
            // Given
            String userId = "87654321-4321-4321-4321-210987654321";
            String group1Id = "11111111-1111-1111-1111-111111111111";
            String group2Id = "22222222-2222-2222-2222-222222222222";

            CreateHangoutRequest request = new CreateHangoutRequest();
            request.setTitle("Test Hangout");
            request.setDescription("Test Description");
            request.setAssociatedGroups(List.of(group1Id, group2Id));

            // Mock group memberships
            GroupMembership membership1 = createTestMembership(group1Id, userId, "Group 1");
            GroupMembership membership2 = createTestMembership(group2Id, userId, "Group 2");
            when(groupRepository.findMembership(group1Id, userId)).thenReturn(Optional.of(membership1));
            when(groupRepository.findMembership(group2Id, userId)).thenReturn(Optional.of(membership2));

            // Mock both groups exist
            Group group1 = new Group();
            group1.setGroupId(group1Id);
            group1.setGroupName("Group 1");

            Group group2 = new Group();
            group2.setGroupId(group2Id);
            group2.setGroupName("Group 2");

            when(groupRepository.findById(group1Id)).thenReturn(Optional.of(group1));
            when(groupRepository.findById(group2Id)).thenReturn(Optional.of(group2));

            // Mock save to throw exception for group1, succeed for group2
            when(groupRepository.save(argThat(g -> g != null && g.getGroupId().equals(group1Id))))
                .thenThrow(new RuntimeException("Database error"));
            when(groupRepository.save(argThat(g -> g != null && g.getGroupId().equals(group2Id))))
                .thenAnswer(invocation -> invocation.getArgument(0));

            // Mock repository to return hangout WITH associated groups set
            Hangout savedHangout = createTestHangout("test-hangout-id");
            savedHangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(group1Id, group2Id)));
            when(hangoutRepository.createHangoutWithAttributes(any(Hangout.class), anyList(), anyList(), anyList(), anyList()))
                .thenReturn(savedHangout);

            // When - Should not throw exception
            assertThatCode(() -> hangoutService.createHangout(request, userId))
                .doesNotThrowAnyException();

            // Then - Verify both groups were attempted to be saved
            verify(groupRepository).findById(group1Id);
            verify(groupRepository).findById(group2Id);
            verify(groupRepository, times(2)).save(any(Group.class));
        }
    }
}
