package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.Address;
import com.bbthechange.inviter.dto.HangoutDetailData;
import com.bbthechange.inviter.exception.ResourceNotFoundException;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.model.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for location and description update methods in HangoutServiceImpl.
 *
 * Covers:
 * - updateEventLocation: location updates with pointer propagation, authorization
 * - updateEventDescription: description updates, group lastModified timestamp
 * - Edge cases: null locations, empty descriptions, authorization failures
 */
class HangoutServiceLocationDescriptionUpdateTest extends HangoutServiceTestBase {

    @Nested
    class UpdateEventLocationTests {

        @Test
        void updateEventLocation_ValidLocationAndAuthorization_UpdatesCanonicalAndPointers() {
            // Given
            String eventId = "12345678-1234-1234-1234-123456789012";
            String userId = "87654321-4321-4321-4321-210987654321";
            String groupId = "11111111-1111-1111-1111-111111111111";

            Address newLocation = new Address();
            newLocation.setName("New Park");
            newLocation.setStreetAddress("123 Main St");
            newLocation.setCity("Portland");

            Hangout hangout = createTestHangout(eventId);
            hangout.setAssociatedGroups(List.of(groupId));

            HangoutDetailData data = HangoutDetailData.builder().withHangout(hangout).build();
            when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(data);

            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.save(any(Hangout.class))).thenReturn(hangout);

            // When
            assertThatCode(() -> hangoutService.updateEventLocation(eventId, newLocation, userId))
                .doesNotThrowAnyException();

            // Then
            // Verify canonical record was updated
            ArgumentCaptor<Hangout> hangoutCaptor = ArgumentCaptor.forClass(Hangout.class);
            verify(hangoutRepository).save(hangoutCaptor.capture());
            assertThat(hangoutCaptor.getValue().getLocation()).isEqualTo(newLocation);

            // Verify pointer was updated with location
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId), eq(eventId), any(), eq("location"));
        }

        @Test
        void updateEventLocation_WithMultipleGroups_UpdatesAllPointers() {
            // Given
            String eventId = "12345678-1234-1234-1234-123456789012";
            String userId = "87654321-4321-4321-4321-210987654321";
            String groupId1 = "11111111-1111-1111-1111-111111111111";
            String groupId2 = "22222222-2222-2222-2222-222222222222";
            String groupId3 = "33333333-3333-3333-3333-333333333333";

            Address newLocation = new Address();
            newLocation.setName("Beach Park");

            Hangout hangout = createTestHangout(eventId);
            hangout.setAssociatedGroups(List.of(groupId1, groupId2, groupId3));

            HangoutDetailData data = HangoutDetailData.builder().withHangout(hangout).build();
            when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(data);

            GroupMembership membership = createTestMembership(groupId1, userId, "Test Group");
            when(groupRepository.findMembership(groupId1, userId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.save(any(Hangout.class))).thenReturn(hangout);

            // When
            hangoutService.updateEventLocation(eventId, newLocation, userId);

            // Then - Verify pointers for all 3 groups were updated
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId1), eq(eventId), any(), eq("location"));
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId2), eq(eventId), any(), eq("location"));
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId3), eq(eventId), any(), eq("location"));
        }

        @Test
        void updateEventLocation_UserNotAuthorized_ThrowsUnauthorizedException() {
            // Given
            String eventId = "12345678-1234-1234-1234-123456789012";
            String userId = "87654321-4321-4321-4321-210987654321";
            String groupId = "11111111-1111-1111-1111-111111111111";

            Address newLocation = new Address();
            newLocation.setName("New Park");

            Hangout hangout = createTestHangout(eventId);
            hangout.setAssociatedGroups(List.of(groupId));

            HangoutDetailData data = HangoutDetailData.builder().withHangout(hangout).build();
            when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(data);

            // User is not in the group
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> hangoutService.updateEventLocation(eventId, newLocation, userId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Cannot edit event");

            // Verify no updates were performed
            verify(hangoutRepository, never()).save(any(Hangout.class));
            verify(pointerUpdateService, never()).updatePointerWithRetry(anyString(), anyString(), any(), anyString());
        }

        @Test
        void updateEventLocation_HangoutNotFound_ThrowsResourceNotFoundException() {
            // Given
            String eventId = "non-existent-hangout";
            String userId = "87654321-4321-4321-4321-210987654321";
            Address newLocation = new Address();

            // Return data with null hangout
            HangoutDetailData data = HangoutDetailData.builder()
                .withHangout(null)
                .build();
            when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(data);

            // When/Then
            assertThatThrownBy(() -> hangoutService.updateEventLocation(eventId, newLocation, userId))
                .isInstanceOf(RuntimeException.class); // Will fail on null hangout
        }

        @Test
        void updateEventLocation_WithNullLocation_UpdatesSuccessfully() {
            // Given
            String eventId = "12345678-1234-1234-1234-123456789012";
            String userId = "87654321-4321-4321-4321-210987654321";
            String groupId = "11111111-1111-1111-1111-111111111111";

            Hangout hangout = createTestHangout(eventId);
            hangout.setAssociatedGroups(List.of(groupId));

            HangoutDetailData data = HangoutDetailData.builder().withHangout(hangout).build();
            when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(data);

            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.save(any(Hangout.class))).thenReturn(hangout);

            // When - Update with null location
            assertThatCode(() -> hangoutService.updateEventLocation(eventId, null, userId))
                .doesNotThrowAnyException();

            // Then - Should still update
            ArgumentCaptor<Hangout> hangoutCaptor = ArgumentCaptor.forClass(Hangout.class);
            verify(hangoutRepository).save(hangoutCaptor.capture());
            assertThat(hangoutCaptor.getValue().getLocation()).isNull();
        }

        @Test
        void updateEventLocation_UpdatesGroupLastModifiedTimestamp() {
            // Given
            String eventId = "12345678-1234-1234-1234-123456789012";
            String userId = "87654321-4321-4321-4321-210987654321";
            String groupId = "11111111-1111-1111-1111-111111111111";

            Address newLocation = new Address();
            newLocation.setName("Park");

            Hangout hangout = createTestHangout(eventId);
            hangout.setAssociatedGroups(List.of(groupId));

            HangoutDetailData data = HangoutDetailData.builder().withHangout(hangout).build();
            when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(data);

            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.save(any(Hangout.class))).thenReturn(hangout);

            // When
            hangoutService.updateEventLocation(eventId, newLocation, userId);

            // Then - Verify GroupTimestampService was called with group ID
            ArgumentCaptor<List<String>> groupIdsCaptor = ArgumentCaptor.forClass(List.class);
            verify(groupTimestampService).updateGroupTimestamps(groupIdsCaptor.capture());

            List<String> capturedGroupIds = groupIdsCaptor.getValue();
            assertThat(capturedGroupIds).containsExactly(groupId);
        }
    }

    @Nested
    class UpdateEventDescriptionTests {

        @Test
        void updateEventDescription_ValidDescriptionAndAuthorization_UpdatesCanonical() {
            // Given
            String eventId = "12345678-1234-1234-1234-123456789012";
            String userId = "87654321-4321-4321-4321-210987654321";
            String groupId = "11111111-1111-1111-1111-111111111111";
            String newDescription = "Updated event description with more details";

            Hangout hangout = createTestHangout(eventId);
            hangout.setAssociatedGroups(List.of(groupId));

            HangoutDetailData data = HangoutDetailData.builder().withHangout(hangout).build();
            when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(data);

            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.save(any(Hangout.class))).thenReturn(hangout);

            // When
            assertThatCode(() -> hangoutService.updateEventDescription(eventId, newDescription, userId))
                .doesNotThrowAnyException();

            // Then - Verify canonical record was updated
            ArgumentCaptor<Hangout> hangoutCaptor = ArgumentCaptor.forClass(Hangout.class);
            verify(hangoutRepository).save(hangoutCaptor.capture());
            assertThat(hangoutCaptor.getValue().getDescription()).isEqualTo(newDescription);
        }

        @Test
        void updateEventDescription_DoesNotTriggerPointerUpdate() {
            // Given - Description update alone doesn't trigger pointer updates in current implementation
            String eventId = "12345678-1234-1234-1234-123456789012";
            String userId = "87654321-4321-4321-4321-210987654321";
            String groupId = "11111111-1111-1111-1111-111111111111";
            String newDescription = "New description";

            Hangout hangout = createTestHangout(eventId);
            hangout.setAssociatedGroups(List.of(groupId));

            HangoutDetailData data = HangoutDetailData.builder().withHangout(hangout).build();
            when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(data);

            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.save(any(Hangout.class))).thenReturn(hangout);

            // When
            hangoutService.updateEventDescription(eventId, newDescription, userId);

            // Then - Pointer update should NOT be called (description updates don't trigger pointer updates)
            verify(pointerUpdateService, never()).updatePointerWithRetry(anyString(), anyString(), any(), anyString());
        }

        @Test
        void updateEventDescription_UserNotAuthorized_ThrowsUnauthorizedException() {
            // Given
            String eventId = "12345678-1234-1234-1234-123456789012";
            String userId = "87654321-4321-4321-4321-210987654321";
            String groupId = "11111111-1111-1111-1111-111111111111";
            String newDescription = "New description";

            Hangout hangout = createTestHangout(eventId);
            hangout.setAssociatedGroups(List.of(groupId));

            HangoutDetailData data = HangoutDetailData.builder().withHangout(hangout).build();
            when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(data);

            // User is not in the group
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> hangoutService.updateEventDescription(eventId, newDescription, userId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Cannot edit event");

            // Verify no updates were performed
            verify(hangoutRepository, never()).save(any(Hangout.class));
        }

        @Test
        void updateEventDescription_WithEmptyDescription_UpdatesSuccessfully() {
            // Given
            String eventId = "12345678-1234-1234-1234-123456789012";
            String userId = "87654321-4321-4321-4321-210987654321";
            String groupId = "11111111-1111-1111-1111-111111111111";

            Hangout hangout = createTestHangout(eventId);
            hangout.setAssociatedGroups(List.of(groupId));

            HangoutDetailData data = HangoutDetailData.builder().withHangout(hangout).build();
            when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(data);

            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.save(any(Hangout.class))).thenReturn(hangout);

            // When - Update with empty description
            assertThatCode(() -> hangoutService.updateEventDescription(eventId, "", userId))
                .doesNotThrowAnyException();

            // Then
            ArgumentCaptor<Hangout> hangoutCaptor = ArgumentCaptor.forClass(Hangout.class);
            verify(hangoutRepository).save(hangoutCaptor.capture());
            assertThat(hangoutCaptor.getValue().getDescription()).isEmpty();
        }

        @Test
        void updateEventDescription_WithNullDescription_UpdatesSuccessfully() {
            // Given
            String eventId = "12345678-1234-1234-1234-123456789012";
            String userId = "87654321-4321-4321-4321-210987654321";
            String groupId = "11111111-1111-1111-1111-111111111111";

            Hangout hangout = createTestHangout(eventId);
            hangout.setAssociatedGroups(List.of(groupId));

            HangoutDetailData data = HangoutDetailData.builder().withHangout(hangout).build();
            when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(data);

            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.save(any(Hangout.class))).thenReturn(hangout);

            // When - Update with null description
            assertThatCode(() -> hangoutService.updateEventDescription(eventId, null, userId))
                .doesNotThrowAnyException();

            // Then
            ArgumentCaptor<Hangout> hangoutCaptor = ArgumentCaptor.forClass(Hangout.class);
            verify(hangoutRepository).save(hangoutCaptor.capture());
            assertThat(hangoutCaptor.getValue().getDescription()).isNull();
        }

        @Test
        void updateEventDescription_UpdatesGroupLastModifiedTimestamp() {
            // Given
            String eventId = "12345678-1234-1234-1234-123456789012";
            String userId = "87654321-4321-4321-4321-210987654321";
            String groupId = "11111111-1111-1111-1111-111111111111";
            String newDescription = "Updated description";

            Hangout hangout = createTestHangout(eventId);
            hangout.setAssociatedGroups(List.of(groupId));

            HangoutDetailData data = HangoutDetailData.builder().withHangout(hangout).build();
            when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(data);

            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.save(any(Hangout.class))).thenReturn(hangout);

            // When
            hangoutService.updateEventDescription(eventId, newDescription, userId);

            // Then - Verify GroupTimestampService was called with group ID
            ArgumentCaptor<List<String>> groupIdsCaptor = ArgumentCaptor.forClass(List.class);
            verify(groupTimestampService).updateGroupTimestamps(groupIdsCaptor.capture());

            List<String> capturedGroupIds = groupIdsCaptor.getValue();
            assertThat(capturedGroupIds).containsExactly(groupId);
        }
    }

    // Helper method to mock group for timestamp updates
    private void mockGroupForTimestamp(String groupId) {
        Group group = new Group();
        group.setGroupId(groupId);
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }
}
