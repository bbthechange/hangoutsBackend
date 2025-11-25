package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.UpdateHangoutRequest;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.model.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for ticket coordination fields (ticketLink, ticketsRequired, discountCode)
 * in HangoutServiceImpl.updateHangout().
 *
 * Coverage:
 * - Saving ticket fields to canonical hangout record
 * - Syncing ticket fields to group pointers
 * - No-op optimizations when values unchanged
 * - Authorization requirements
 * - Edge cases (empty strings, boolean transitions)
 */
class HangoutServiceImplUpdateTicketFieldsTest extends HangoutServiceTestBase {

    private static final String HANGOUT_ID = "12345678-1234-1234-1234-123456789012";
    private static final String USER_ID = "87654321-4321-4321-4321-210987654321";
    private static final String GROUP_1 = "11111111-1111-1111-1111-111111111111";
    private static final String GROUP_2 = "22222222-2222-2222-2222-222222222222";

    @Nested
    class SuccessfulTicketFieldUpdates {

        @Test
        void updateHangout_WithTicketLink_SavesTicketLinkToHangout() {
            // Given
            Hangout existingHangout = createTestHangout(HANGOUT_ID);
            existingHangout.setTicketLink(null);
            existingHangout.setAssociatedGroups(new ArrayList<>(List.of(GROUP_1)));

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setTicketLink("https://tickets.example.com");

            mockAuthorization(GROUP_1);
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            // When
            hangoutService.updateHangout(HANGOUT_ID, request, USER_ID);

            // Then
            ArgumentCaptor<Hangout> captor = ArgumentCaptor.forClass(Hangout.class);
            verify(hangoutRepository).createHangout(captor.capture());
            assertThat(captor.getValue().getTicketLink()).isEqualTo("https://tickets.example.com");
        }

        @Test
        void updateHangout_WithTicketsRequired_SavesTicketsRequiredToHangout() {
            // Given
            Hangout existingHangout = createTestHangout(HANGOUT_ID);
            existingHangout.setTicketsRequired(null);
            existingHangout.setAssociatedGroups(new ArrayList<>(List.of(GROUP_1)));

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setTicketsRequired(true);

            mockAuthorization(GROUP_1);
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            // When
            hangoutService.updateHangout(HANGOUT_ID, request, USER_ID);

            // Then
            ArgumentCaptor<Hangout> captor = ArgumentCaptor.forClass(Hangout.class);
            verify(hangoutRepository).createHangout(captor.capture());
            assertThat(captor.getValue().getTicketsRequired()).isTrue();
        }

        @Test
        void updateHangout_WithDiscountCode_SavesDiscountCodeToHangout() {
            // Given
            Hangout existingHangout = createTestHangout(HANGOUT_ID);
            existingHangout.setDiscountCode(null);
            existingHangout.setAssociatedGroups(new ArrayList<>(List.of(GROUP_1)));

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setDiscountCode("SAVE20");

            mockAuthorization(GROUP_1);
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            // When
            hangoutService.updateHangout(HANGOUT_ID, request, USER_ID);

            // Then
            ArgumentCaptor<Hangout> captor = ArgumentCaptor.forClass(Hangout.class);
            verify(hangoutRepository).createHangout(captor.capture());
            assertThat(captor.getValue().getDiscountCode()).isEqualTo("SAVE20");
        }

        @Test
        void updateHangout_WithAllTicketFields_SavesAllFields() {
            // Given
            Hangout existingHangout = createTestHangout(HANGOUT_ID);
            existingHangout.setTicketLink(null);
            existingHangout.setTicketsRequired(null);
            existingHangout.setDiscountCode(null);
            existingHangout.setAssociatedGroups(new ArrayList<>(List.of(GROUP_1)));

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setTicketLink("https://tickets.example.com/event123");
            request.setTicketsRequired(true);
            request.setDiscountCode("FRIENDS20");

            mockAuthorization(GROUP_1);
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            // When
            hangoutService.updateHangout(HANGOUT_ID, request, USER_ID);

            // Then
            ArgumentCaptor<Hangout> captor = ArgumentCaptor.forClass(Hangout.class);
            verify(hangoutRepository).createHangout(captor.capture());
            Hangout savedHangout = captor.getValue();

            assertThat(savedHangout.getTicketLink()).isEqualTo("https://tickets.example.com/event123");
            assertThat(savedHangout.getTicketsRequired()).isTrue();
            assertThat(savedHangout.getDiscountCode()).isEqualTo("FRIENDS20");
        }
    }

    @Nested
    class PointerSynchronization {

        @Test
        void updateHangout_WithTicketLink_SyncsToAllGroupPointers() {
            // Given
            Hangout existingHangout = createTestHangout(HANGOUT_ID);
            existingHangout.setTicketLink(null);
            existingHangout.setAssociatedGroups(new ArrayList<>(List.of(GROUP_1, GROUP_2)));

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setTicketLink("https://tickets.example.com");

            mockAuthorization(GROUP_1);
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            // When
            hangoutService.updateHangout(HANGOUT_ID, request, USER_ID);

            // Then - Verify pointer update is called for each group
            verify(pointerUpdateService, times(2)).updatePointerWithRetry(
                anyString(), eq(HANGOUT_ID), any(), eq("basic fields"));
        }

        @Test
        void updateHangout_WithTicketsRequired_SyncsToPointers() {
            // Given
            Hangout existingHangout = createTestHangout(HANGOUT_ID);
            existingHangout.setTicketsRequired(null);
            existingHangout.setAssociatedGroups(new ArrayList<>(List.of(GROUP_1)));

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setTicketsRequired(true);

            mockAuthorization(GROUP_1);
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            // When
            hangoutService.updateHangout(HANGOUT_ID, request, USER_ID);

            // Then
            verify(pointerUpdateService).updatePointerWithRetry(
                eq(GROUP_1), eq(HANGOUT_ID), any(), eq("basic fields"));
        }

        @Test
        void updateHangout_WithDiscountCode_SyncsToPointers() {
            // Given
            Hangout existingHangout = createTestHangout(HANGOUT_ID);
            existingHangout.setDiscountCode(null);
            existingHangout.setAssociatedGroups(new ArrayList<>(List.of(GROUP_1)));

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setDiscountCode("SAVE20");

            mockAuthorization(GROUP_1);
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            // When
            hangoutService.updateHangout(HANGOUT_ID, request, USER_ID);

            // Then
            verify(pointerUpdateService).updatePointerWithRetry(
                eq(GROUP_1), eq(HANGOUT_ID), any(), eq("basic fields"));
        }
    }

    @Nested
    class NoOpOptimization {

        @Test
        void updateHangout_WithSameTicketLink_DoesNotTriggerPointerUpdate() {
            // Given
            Hangout existingHangout = createTestHangout(HANGOUT_ID);
            existingHangout.setTicketLink("https://existing.com");
            existingHangout.setAssociatedGroups(new ArrayList<>(List.of(GROUP_1)));

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setTicketLink("https://existing.com"); // Same value

            mockAuthorization(GROUP_1);
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            // When
            hangoutService.updateHangout(HANGOUT_ID, request, USER_ID);

            // Then - No pointer update because no fields changed
            verify(pointerUpdateService, never()).updatePointerWithRetry(
                anyString(), anyString(), any(), anyString());
        }

        @Test
        void updateHangout_WithNullTicketFields_DoesNotUpdateExistingValues() {
            // Given
            Hangout existingHangout = createTestHangout(HANGOUT_ID);
            existingHangout.setTicketLink("https://existing.com");
            existingHangout.setTicketsRequired(true);
            existingHangout.setDiscountCode("EXISTING20");
            existingHangout.setAssociatedGroups(new ArrayList<>(List.of(GROUP_1)));

            // Request with null ticket fields - should not change existing values
            UpdateHangoutRequest request = new UpdateHangoutRequest();
            // All ticket fields are null by default
            request.setTitle("Updated Title"); // Only update title

            mockAuthorization(GROUP_1);
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            // When
            hangoutService.updateHangout(HANGOUT_ID, request, USER_ID);

            // Then - Existing ticket values should remain unchanged
            ArgumentCaptor<Hangout> captor = ArgumentCaptor.forClass(Hangout.class);
            verify(hangoutRepository).createHangout(captor.capture());
            Hangout savedHangout = captor.getValue();

            assertThat(savedHangout.getTicketLink()).isEqualTo("https://existing.com");
            assertThat(savedHangout.getTicketsRequired()).isTrue();
            assertThat(savedHangout.getDiscountCode()).isEqualTo("EXISTING20");
        }
    }

    @Nested
    class Authorization {

        @Test
        void updateHangout_WithTicketFields_RequiresGroupMembership() {
            // Given
            Hangout existingHangout = createTestHangout(HANGOUT_ID);
            existingHangout.setAssociatedGroups(new ArrayList<>(List.of(GROUP_1)));

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setTicketLink("https://tickets.example.com");

            // User is NOT a member of the group
            when(groupRepository.findMembership(GROUP_1, USER_ID)).thenReturn(Optional.empty());
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(existingHangout));

            // When/Then
            assertThatThrownBy(() -> hangoutService.updateHangout(HANGOUT_ID, request, USER_ID))
                .isInstanceOf(UnauthorizedException.class);
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void updateHangout_WithEmptyStringTicketLink_TreatedAsUpdate() {
            // Given
            Hangout existingHangout = createTestHangout(HANGOUT_ID);
            existingHangout.setTicketLink("https://existing.com");
            existingHangout.setAssociatedGroups(new ArrayList<>(List.of(GROUP_1)));

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setTicketLink(""); // Empty string - user wants to clear the link

            mockAuthorization(GROUP_1);
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            // When
            hangoutService.updateHangout(HANGOUT_ID, request, USER_ID);

            // Then
            ArgumentCaptor<Hangout> captor = ArgumentCaptor.forClass(Hangout.class);
            verify(hangoutRepository).createHangout(captor.capture());
            assertThat(captor.getValue().getTicketLink()).isEqualTo("");
        }

        @Test
        void updateHangout_TicketsRequiredFalseToTrue_TriggersUpdate() {
            // Given
            Hangout existingHangout = createTestHangout(HANGOUT_ID);
            existingHangout.setTicketsRequired(false);
            existingHangout.setAssociatedGroups(new ArrayList<>(List.of(GROUP_1)));

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setTicketsRequired(true);

            mockAuthorization(GROUP_1);
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            // When
            hangoutService.updateHangout(HANGOUT_ID, request, USER_ID);

            // Then - Pointer update should be triggered
            verify(pointerUpdateService).updatePointerWithRetry(
                eq(GROUP_1), eq(HANGOUT_ID), any(), eq("basic fields"));

            // Verify the value was actually changed
            ArgumentCaptor<Hangout> captor = ArgumentCaptor.forClass(Hangout.class);
            verify(hangoutRepository).createHangout(captor.capture());
            assertThat(captor.getValue().getTicketsRequired()).isTrue();
        }

        @Test
        void updateHangout_TicketsRequiredTrueToFalse_TriggersUpdate() {
            // Given
            Hangout existingHangout = createTestHangout(HANGOUT_ID);
            existingHangout.setTicketsRequired(true);
            existingHangout.setAssociatedGroups(new ArrayList<>(List.of(GROUP_1)));

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setTicketsRequired(false);

            mockAuthorization(GROUP_1);
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            // When
            hangoutService.updateHangout(HANGOUT_ID, request, USER_ID);

            // Then
            verify(pointerUpdateService).updatePointerWithRetry(
                eq(GROUP_1), eq(HANGOUT_ID), any(), eq("basic fields"));

            ArgumentCaptor<Hangout> captor = ArgumentCaptor.forClass(Hangout.class);
            verify(hangoutRepository).createHangout(captor.capture());
            assertThat(captor.getValue().getTicketsRequired()).isFalse();
        }

        @Test
        void updateHangout_WithSameTicketsRequiredValue_DoesNotTriggerPointerUpdate() {
            // Given
            Hangout existingHangout = createTestHangout(HANGOUT_ID);
            existingHangout.setTicketsRequired(true);
            existingHangout.setAssociatedGroups(new ArrayList<>(List.of(GROUP_1)));

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setTicketsRequired(true); // Same value

            mockAuthorization(GROUP_1);
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            // When
            hangoutService.updateHangout(HANGOUT_ID, request, USER_ID);

            // Then - No pointer update because no actual change
            verify(pointerUpdateService, never()).updatePointerWithRetry(
                anyString(), anyString(), any(), anyString());
        }
    }

    /**
     * Helper method to mock user authorization for a group.
     */
    private void mockAuthorization(String groupId) {
        GroupMembership membership = createTestMembership(groupId, USER_ID, "Test Group");
        when(groupRepository.findMembership(groupId, USER_ID)).thenReturn(Optional.of(membership));
    }
}
