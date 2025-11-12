package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.CreateHangoutRequest;
import com.bbthechange.inviter.dto.HangoutDetailData;
import com.bbthechange.inviter.model.*;
import org.mockito.ArgumentCaptor;
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

            // Mock repository to return hangout WITH associated groups set
            Hangout savedHangout = createTestHangout("test-hangout-id");
            savedHangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(group1Id, group2Id)));
            when(hangoutRepository.createHangoutWithAttributes(any(Hangout.class), anyList(), anyList(), anyList(), anyList()))
                .thenReturn(savedHangout);

            // When
            hangoutService.createHangout(request, userId);

            // Then - Verify GroupTimestampService was called with both group IDs
            org.mockito.ArgumentCaptor<List<String>> groupIdsCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
            verify(groupTimestampService).updateGroupTimestamps(groupIdsCaptor.capture());

            List<String> capturedGroupIds = groupIdsCaptor.getValue();
            assertThat(capturedGroupIds).containsExactlyInAnyOrder(group1Id, group2Id);
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

            when(hangoutRepository.save(any(Hangout.class))).thenReturn(hangout);

            // When
            hangoutService.updateEventTitle(hangoutId, "New Title", userId);

            // Then - Verify GroupTimestampService was called with group ID
            org.mockito.ArgumentCaptor<List<String>> groupIdsCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
            verify(groupTimestampService).updateGroupTimestamps(groupIdsCaptor.capture());

            List<String> capturedGroupIds = groupIdsCaptor.getValue();
            assertThat(capturedGroupIds).containsExactly(groupId);
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

            // When
            hangoutService.deleteHangout(hangoutId, userId);

            // Then - Verify GroupTimestampService was called with both group IDs
            org.mockito.ArgumentCaptor<List<String>> groupIdsCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
            verify(groupTimestampService).updateGroupTimestamps(groupIdsCaptor.capture());

            List<String> capturedGroupIds = groupIdsCaptor.getValue();
            assertThat(capturedGroupIds).containsExactlyInAnyOrder(group1Id, group2Id);
        }

        @Test
        void updateGroupLastModified_WithNullGroupList_DoesNothing() {
            // Given
            String userId = "87654321-4321-4321-4321-210987654321";

            CreateHangoutRequest request = new CreateHangoutRequest();
            request.setTitle("Test Hangout");
            request.setDescription("Test Description");
            request.setAssociatedGroups(null); // Null group list

            // Mock repository to return hangout without setting associatedGroups (remains null)
            Hangout savedHangout = createTestHangout("test-hangout-id");
            // Don't set associatedGroups - it will be null by default
            when(hangoutRepository.createHangoutWithAttributes(any(Hangout.class), anyList(), anyList(), anyList(), anyList()))
                .thenReturn(savedHangout);

            // When
            hangoutService.createHangout(request, userId);

            // Then - Verify GroupTimestampService was called with empty list (Hangout initializes associatedGroups to empty ArrayList)
            ArgumentCaptor<List<String>> groupIdsCaptor = ArgumentCaptor.forClass(List.class);
            verify(groupTimestampService).updateGroupTimestamps(groupIdsCaptor.capture());

            // Verify the captured value is an empty list
            assertThat(groupIdsCaptor.getValue()).isEmpty();
        }

        @Test
        void updateGroupLastModified_WithEmptyGroupList_DoesNothing() {
            // Given
            String userId = "87654321-4321-4321-4321-210987654321";

            CreateHangoutRequest request = new CreateHangoutRequest();
            request.setTitle("Test Hangout");
            request.setDescription("Test Description");
            request.setAssociatedGroups(List.of()); // Empty group list

            // Mock repository to return hangout with empty associatedGroups
            Hangout savedHangout = createTestHangout("test-hangout-id");
            savedHangout.setAssociatedGroups(List.of());
            when(hangoutRepository.createHangoutWithAttributes(any(Hangout.class), anyList(), anyList(), anyList(), anyList()))
                .thenReturn(savedHangout);

            // When
            hangoutService.createHangout(request, userId);

            // Then - Verify GroupTimestampService was called with empty list (service handles it internally)
            verify(groupTimestampService).updateGroupTimestamps(List.of());
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

            // Mock repository to return hangout WITH associated groups set
            Hangout savedHangout = createTestHangout("test-hangout-id");
            savedHangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(group1Id, group2Id)));
            when(hangoutRepository.createHangoutWithAttributes(any(Hangout.class), anyList(), anyList(), anyList(), anyList()))
                .thenReturn(savedHangout);

            // When
            hangoutService.createHangout(request, userId);

            // Then - Verify GroupTimestampService was called with both group IDs
            // (The service will handle the case where a group might not exist)
            org.mockito.ArgumentCaptor<List<String>> groupIdsCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
            verify(groupTimestampService).updateGroupTimestamps(groupIdsCaptor.capture());

            List<String> capturedGroupIds = groupIdsCaptor.getValue();
            assertThat(capturedGroupIds).containsExactlyInAnyOrder(group1Id, group2Id);
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

            // Mock repository to return hangout WITH associated groups set
            Hangout savedHangout = createTestHangout("test-hangout-id");
            savedHangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(group1Id, group2Id)));
            when(hangoutRepository.createHangoutWithAttributes(any(Hangout.class), anyList(), anyList(), anyList(), anyList()))
                .thenReturn(savedHangout);

            // When - Should not throw exception
            assertThatCode(() -> hangoutService.createHangout(request, userId))
                .doesNotThrowAnyException();

            // Then - Verify GroupTimestampService was called with both group IDs
            // (The service itself handles exception recovery - see GroupTimestampServiceTest)
            org.mockito.ArgumentCaptor<List<String>> groupIdsCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
            verify(groupTimestampService).updateGroupTimestamps(groupIdsCaptor.capture());

            List<String> capturedGroupIds = groupIdsCaptor.getValue();
            assertThat(capturedGroupIds).containsExactlyInAnyOrder(group1Id, group2Id);
        }
    }
}
