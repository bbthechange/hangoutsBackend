package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.CreateHangoutRequest;
import com.bbthechange.inviter.dto.HangoutDetailData;
import com.bbthechange.inviter.dto.UserSummaryDTO;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.exception.ValidationException;
import com.bbthechange.inviter.model.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Edge case and utility method tests for HangoutServiceImpl.
 *
 * Covers:
 * - resyncHangoutPointers edge cases (null groups, missing hangout, empty data)
 * - hangoutFromHangoutRequest with unauthorized groups
 * - getCreatorDisplayName with various user states
 * - Null/empty associated groups handling across operations
 * - Repository exception handling
 */
class HangoutServiceEdgeCasesTest extends HangoutServiceTestBase {

    @Nested
    class ResyncHangoutPointersEdgeCases {

        @Test
        void resyncHangoutPointers_WithNonExistentHangout_LogsWarningAndReturns() {
            // Given
            String hangoutId = "non-existent-hangout";
            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.empty());

            // When
            assertThatCode(() -> hangoutService.resyncHangoutPointers(hangoutId))
                .doesNotThrowAnyException();

            // Then - Should not attempt to update any pointers
            verify(pointerUpdateService, never()).updatePointerWithRetry(anyString(), anyString(), any(), anyString());
        }

        @Test
        void resyncHangoutPointers_WithNullAssociatedGroups_LogsWarningAndReturns() {
            // Given
            String hangoutId = "hangout-1";
            Hangout hangout = createTestHangout(hangoutId);
            hangout.setAssociatedGroups(null);

            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));

            // When
            assertThatCode(() -> hangoutService.resyncHangoutPointers(hangoutId))
                .doesNotThrowAnyException();

            // Then
            verify(pointerUpdateService, never()).updatePointerWithRetry(anyString(), anyString(), any(), anyString());
        }

        @Test
        void resyncHangoutPointers_WithEmptyAssociatedGroups_LogsWarningAndReturns() {
            // Given
            String hangoutId = "hangout-1";
            Hangout hangout = createTestHangout(hangoutId);
            hangout.setAssociatedGroups(List.of());

            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));

            // When
            assertThatCode(() -> hangoutService.resyncHangoutPointers(hangoutId))
                .doesNotThrowAnyException();

            // Then
            verify(pointerUpdateService, never()).updatePointerWithRetry(anyString(), anyString(), any(), anyString());
        }

        @Test
        void resyncHangoutPointers_LambdaUpdatesAllPointerFields() {
            // Given
            String hangoutId = "hangout-1";
            String groupId = "11111111-1111-1111-1111-111111111111";

            // Create hangout with all basic fields populated
            Hangout hangout = createTestHangout(hangoutId);
            hangout.setAssociatedGroups(List.of(groupId));
            hangout.setTitle("Updated Title");
            hangout.setDescription("Updated Description");
            hangout.setVisibility(EventVisibility.PUBLIC);
            hangout.setMainImagePath("/updated-image.jpg");
            hangout.setCarpoolEnabled(true);
            hangout.setSeriesId("series-456");
            hangout.setStartTimestamp(1754557200L);
            hangout.setEndTimestamp(1754571600L);

            com.bbthechange.inviter.dto.TimeInfo timeInfo = new com.bbthechange.inviter.dto.TimeInfo();
            timeInfo.setPeriodGranularity("morning");
            timeInfo.setPeriodStart("2025-08-06T08:00:00Z");
            hangout.setTimeInput(timeInfo);

            // Use test helpers that properly set keys
            Poll poll = createTestPoll();
            Car car = createTestCar();
            Vote vote = createTestVote();
            InterestLevel interest = createTestInterestLevel();

            // Create attribute manually with proper setup
            HangoutAttribute attribute = new HangoutAttribute();
            attribute.setHangoutId(hangoutId);
            attribute.setAttributeId("attr-123");
            attribute.setAttributeName("Vibe");
            attribute.setStringValue("Chill");
            attribute.setPk("HANGOUT#" + hangoutId);
            attribute.setSk("ATTR#attr-123");

            HangoutDetailData detailData = HangoutDetailData.builder()
                .withHangout(hangout)
                .withPolls(List.of(poll))
                .withCars(List.of(car))
                .withVotes(List.of(vote))
                .withAttendance(List.of(interest))
                .build();

            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
            when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(detailData);
            when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of(attribute));

            // Capture the lambda that updates the pointer
            org.mockito.ArgumentCaptor<java.util.function.Consumer<HangoutPointer>> lambdaCaptor =
                org.mockito.ArgumentCaptor.forClass(java.util.function.Consumer.class);

            // When
            hangoutService.resyncHangoutPointers(hangoutId);

            // Then - Capture and execute the lambda
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId), eq(hangoutId), lambdaCaptor.capture(), eq("complete resync"));

            // Create a test pointer with old values using the test base helper
            HangoutPointer testPointer = createTestHangoutPointer(groupId, hangoutId);
            testPointer.setTitle("Old Title");
            testPointer.setDescription("Old Description");
            testPointer.setVisibility(EventVisibility.INVITE_ONLY);
            testPointer.setMainImagePath("/old-image.jpg");
            testPointer.setCarpoolEnabled(false);

            // Execute the lambda - this is what we're testing!
            lambdaCaptor.getValue().accept(testPointer);

            // Verify basic fields were updated from the hangout
            assertThat(testPointer.getTitle()).isEqualTo("Updated Title");
            assertThat(testPointer.getDescription()).isEqualTo("Updated Description");
            assertThat(testPointer.getVisibility()).isEqualTo(EventVisibility.PUBLIC);
            assertThat(testPointer.getMainImagePath()).isEqualTo("/updated-image.jpg");
            assertThat(testPointer.isCarpoolEnabled()).isTrue();

            // Verify time fields were updated
            assertThat(testPointer.getTimeInput()).isEqualTo(timeInfo);
            assertThat(testPointer.getStartTimestamp()).isEqualTo(1754557200L);
            assertThat(testPointer.getEndTimestamp()).isEqualTo(1754571600L);
            assertThat(testPointer.getSeriesId()).isEqualTo("series-456");

            // Verify collections were copied from detail data
            assertThat(testPointer.getPolls()).hasSize(1);
            assertThat(testPointer.getPolls().get(0).getPollId()).isEqualTo("11111111-1111-1111-1111-111111111111");
            assertThat(testPointer.getCars()).hasSize(1);
            assertThat(testPointer.getVotes()).hasSize(1);
            assertThat(testPointer.getInterestLevels()).hasSize(1);
            assertThat(testPointer.getAttributes()).hasSize(1);
            assertThat(testPointer.getAttributes().get(0).getAttributeName()).isEqualTo("Vibe");
        }

        @Test
        void resyncHangoutPointers_WithMultipleGroups_SyncsAll() {
            // Given
            String hangoutId = "hangout-1";
            String groupId1 = "11111111-1111-1111-1111-111111111111";
            String groupId2 = "22222222-2222-2222-2222-222222222222";
            String groupId3 = "33333333-3333-3333-3333-333333333333";

            Hangout hangout = createTestHangout(hangoutId);
            hangout.setAssociatedGroups(List.of(groupId1, groupId2, groupId3));

            HangoutDetailData detailData = HangoutDetailData.builder().withHangout(hangout).build();

            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
            when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(detailData);
            when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());

            // When
            hangoutService.resyncHangoutPointers(hangoutId);

            // Then - Verify all 3 groups were updated
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId1), eq(hangoutId), any(), eq("complete resync"));
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId2), eq(hangoutId), any(), eq("complete resync"));
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId3), eq(hangoutId), any(), eq("complete resync"));
        }
    }

    @Nested
    class HangoutFromHangoutRequestTests {

        @Test
        void hangoutFromHangoutRequest_UserNotInGroup_ThrowsUnauthorizedException() {
            // Given
            String userId = "87654321-4321-4321-4321-210987654321";
            String groupId = "11111111-1111-1111-1111-111111111111";

            CreateHangoutRequest request = new CreateHangoutRequest();
            request.setTitle("Test Hangout");
            request.setVisibility(EventVisibility.INVITE_ONLY);
            request.setAssociatedGroups(List.of(groupId));

            // User is not in the group
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> hangoutService.hangoutFromHangoutRequest(request, userId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("User not in group");

            // Verify membership check was performed
            verify(groupRepository).findMembership(groupId, userId);
        }

        @Test
        void hangoutFromHangoutRequest_UserInSomeGroupsNotOthers_ThrowsUnauthorizedException() {
            // Given
            String userId = "87654321-4321-4321-4321-210987654321";
            String groupId1 = "11111111-1111-1111-1111-111111111111";
            String groupId2 = "22222222-2222-2222-2222-222222222222";

            CreateHangoutRequest request = new CreateHangoutRequest();
            request.setTitle("Test Hangout");
            request.setVisibility(EventVisibility.INVITE_ONLY);
            request.setAssociatedGroups(List.of(groupId1, groupId2));

            // User is in group1 but not group2
            GroupMembership membership1 = createTestMembership(groupId1, userId, "Group 1");
            when(groupRepository.findMembership(groupId1, userId)).thenReturn(Optional.of(membership1));
            when(groupRepository.findMembership(groupId2, userId)).thenReturn(Optional.empty());

            // When/Then - Should fail because user is not in ALL groups
            assertThatThrownBy(() -> hangoutService.hangoutFromHangoutRequest(request, userId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("User not in group: " + groupId2);
        }

        @Test
        void hangoutFromHangoutRequest_WithNullAssociatedGroups_CreatesHangout() {
            // Given
            String userId = "87654321-4321-4321-4321-210987654321";

            CreateHangoutRequest request = new CreateHangoutRequest();
            request.setTitle("Test Hangout");
            request.setVisibility(EventVisibility.PUBLIC);
            request.setAssociatedGroups(null);

            // When
            Hangout result = hangoutService.hangoutFromHangoutRequest(request, userId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo("Test Hangout");
            // The method doesn't modify null groups, so it stays null
            // But we need to check what the actual implementation does

            // Verify no membership checks were performed
            verify(groupRepository, never()).findMembership(anyString(), anyString());
        }

        @Test
        void hangoutFromHangoutRequest_WithEmptyAssociatedGroups_CreatesHangout() {
            // Given
            String userId = "87654321-4321-4321-4321-210987654321";

            CreateHangoutRequest request = new CreateHangoutRequest();
            request.setTitle("Test Hangout");
            request.setVisibility(EventVisibility.PUBLIC);
            request.setAssociatedGroups(List.of());

            // When
            Hangout result = hangoutService.hangoutFromHangoutRequest(request, userId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo("Test Hangout");
            assertThat(result.getAssociatedGroups()).isEmpty();

            // Verify no membership checks were performed
            verify(groupRepository, never()).findMembership(anyString(), anyString());
        }
    }

    @Nested
    class GetCreatorDisplayNameTests {
        // Testing the private method through createHangout which calls it

        @Test
        void createHangout_WithValidUser_UsesDisplayName() {
            // Given
            String userId = "87654321-4321-4321-4321-210987654321";
            String groupId = "11111111-1111-1111-1111-111111111111";

            CreateHangoutRequest request = new CreateHangoutRequest();
            request.setTitle("Test Hangout");
            request.setVisibility(EventVisibility.INVITE_ONLY);
            request.setAssociatedGroups(List.of(groupId));

            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));

            UserSummaryDTO user = createTestUser(userId);
            user.setDisplayName("John Doe");
            when(userService.getUserSummary(UUID.fromString(userId))).thenReturn(Optional.of(user));

            Hangout savedHangout = createTestHangout("hangout-1");
            when(hangoutRepository.createHangoutWithAttributes(any(), any(), any(), any(), any())).thenReturn(savedHangout);

            // When
            hangoutService.createHangout(request, userId);

            // Then - Notification service should be called with display name
            verify(notificationService).notifyNewHangout(any(Hangout.class), eq(userId), eq("John Doe"));
        }

        @Test
        void createHangout_WithNonExistentUser_UsesFallbackName() {
            // Given
            String userId = "87654321-4321-4321-4321-210987654321";
            String groupId = "11111111-1111-1111-1111-111111111111";

            CreateHangoutRequest request = new CreateHangoutRequest();
            request.setTitle("Test Hangout");
            request.setVisibility(EventVisibility.INVITE_ONLY);
            request.setAssociatedGroups(List.of(groupId));

            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));

            // User not found
            when(userService.getUserSummary(UUID.fromString(userId))).thenReturn(Optional.empty());

            Hangout savedHangout = createTestHangout("hangout-1");
            when(hangoutRepository.createHangoutWithAttributes(any(), any(), any(), any(), any())).thenReturn(savedHangout);

            // When
            hangoutService.createHangout(request, userId);

            // Then - Should use "Unknown" as fallback
            verify(notificationService).notifyNewHangout(any(Hangout.class), eq(userId), eq("Unknown"));
        }

        @Test
        void createHangout_WithUserServiceException_UsesFallbackName() {
            // Given
            String userId = "87654321-4321-4321-4321-210987654321";
            String groupId = "11111111-1111-1111-1111-111111111111";

            CreateHangoutRequest request = new CreateHangoutRequest();
            request.setTitle("Test Hangout");
            request.setVisibility(EventVisibility.INVITE_ONLY);
            request.setAssociatedGroups(List.of(groupId));

            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));

            // UserService throws exception
            when(userService.getUserSummary(UUID.fromString(userId))).thenThrow(new RuntimeException("Service unavailable"));

            Hangout savedHangout = createTestHangout("hangout-1");
            when(hangoutRepository.createHangoutWithAttributes(any(), any(), any(), any(), any())).thenReturn(savedHangout);

            // When
            hangoutService.createHangout(request, userId);

            // Then - Should use "Unknown" as fallback and not propagate exception
            verify(notificationService).notifyNewHangout(any(Hangout.class), eq(userId), eq("Unknown"));
        }
    }

    @Nested
    class NullAndEmptyGroupsHandlingTests {

        @Test
        void updateHangout_WithNullAssociatedGroups_FailsAuthorization() {
            // Given
            String hangoutId = "hangout-1";
            String userId = "user-1";

            Hangout hangout = createTestHangout(hangoutId);
            hangout.setAssociatedGroups(null); // No groups means no one can edit
            hangout.setVisibility(EventVisibility.PUBLIC);

            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));

            com.bbthechange.inviter.dto.UpdateHangoutRequest request = new com.bbthechange.inviter.dto.UpdateHangoutRequest();
            request.setTitle("New Title");

            // When/Then - Should fail authorization since no groups to check membership
            assertThatThrownBy(() -> hangoutService.updateHangout(hangoutId, request, userId))
                .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        void deleteHangout_WithNullAssociatedGroups_FailsAuthorization() {
            // Given
            String hangoutId = "hangout-1";
            String userId = "user-1";

            Hangout hangout = createTestHangout(hangoutId);
            hangout.setAssociatedGroups(null); // No groups means no one can edit
            hangout.setVisibility(EventVisibility.PUBLIC);

            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));

            // When/Then - Should fail authorization since no groups to check membership
            assertThatThrownBy(() -> hangoutService.deleteHangout(hangoutId, userId))
                .isInstanceOf(UnauthorizedException.class);
        }
    }
}
