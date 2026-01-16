package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.CreateHangoutRequest;
import com.bbthechange.inviter.dto.HangoutSummaryDTO;
import com.bbthechange.inviter.dto.UpdateHangoutRequest;
import com.bbthechange.inviter.dto.UserSummaryDTO;
import com.bbthechange.inviter.exception.ValidationException;
import com.bbthechange.inviter.model.EventVisibility;
import com.bbthechange.inviter.model.GroupMembership;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.testutil.HangoutPointerTestBuilder;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Tests for Host At Place feature functionality in HangoutServiceImpl.
 *
 * Coverage:
 * - enrichHostAtPlaceInfo: user lookup, null handling, user not found
 * - hangoutFromHangoutRequest: validation and field setting
 * - updateHangout: field changes, validation, pointer updates
 */
class HangoutServiceImplHostAtPlaceTest extends HangoutServiceTestBase {

    // ============================================================================
    // Tests for enrichHostAtPlaceInfo
    // ============================================================================

    @Nested
    class EnrichHostAtPlaceInfo {

        @Test
        void enrichHostAtPlaceInfo_WithValidUserId_SetsDisplayNameAndImagePath() {
            // Given: HangoutSummaryDTO with hostAtPlaceUserId set to valid user ID
            String groupId = UUID.randomUUID().toString();
            String hangoutId = UUID.randomUUID().toString();
            String hostUserId = UUID.randomUUID().toString();
            String requestingUserId = UUID.randomUUID().toString();

            HangoutPointer pointer = HangoutPointerTestBuilder.aPointer()
                .forGroup(groupId)
                .forHangout(hangoutId)
                .withTitle("Test Hangout")
                .build();
            // Manually set hostAtPlaceUserId since builder may not have this method
            pointer.setHostAtPlaceUserId(hostUserId);

            HangoutSummaryDTO dto = new HangoutSummaryDTO(pointer, requestingUserId);

            // Mock UserService returns user with displayName and imagePath
            UserSummaryDTO user = new UserSummaryDTO();
            user.setDisplayName("John");
            user.setMainImagePath("users/123/profile.jpg");
            when(userService.getUserSummary(UUID.fromString(hostUserId))).thenReturn(Optional.of(user));

            // When
            hangoutService.enrichHostAtPlaceInfo(dto);

            // Then
            assertThat(dto.getHostAtPlaceDisplayName()).isEqualTo("John");
            assertThat(dto.getHostAtPlaceImagePath()).isEqualTo("users/123/profile.jpg");
            verify(userService).getUserSummary(UUID.fromString(hostUserId));
        }

        @Test
        void enrichHostAtPlaceInfo_WithNullUserId_DoesNotCallUserService() {
            // Given: HangoutSummaryDTO with hostAtPlaceUserId = null
            String groupId = UUID.randomUUID().toString();
            String hangoutId = UUID.randomUUID().toString();
            String requestingUserId = UUID.randomUUID().toString();

            HangoutPointer pointer = HangoutPointerTestBuilder.aPointer()
                .forGroup(groupId)
                .forHangout(hangoutId)
                .withTitle("Test Hangout")
                .build();
            // hostAtPlaceUserId is null by default

            HangoutSummaryDTO dto = new HangoutSummaryDTO(pointer, requestingUserId);

            // When
            hangoutService.enrichHostAtPlaceInfo(dto);

            // Then
            verify(userService, never()).getUserSummary(any(UUID.class));
            assertThat(dto.getHostAtPlaceDisplayName()).isNull();
            assertThat(dto.getHostAtPlaceImagePath()).isNull();
        }

        @Test
        void enrichHostAtPlaceInfo_WithUserNotFound_LeavesFieldsNull() {
            // Given: HangoutSummaryDTO with hostAtPlaceUserId set, but UserService returns empty
            String groupId = UUID.randomUUID().toString();
            String hangoutId = UUID.randomUUID().toString();
            String hostUserId = UUID.randomUUID().toString();
            String requestingUserId = UUID.randomUUID().toString();

            HangoutPointer pointer = HangoutPointerTestBuilder.aPointer()
                .forGroup(groupId)
                .forHangout(hangoutId)
                .withTitle("Test Hangout")
                .build();
            pointer.setHostAtPlaceUserId(hostUserId);

            HangoutSummaryDTO dto = new HangoutSummaryDTO(pointer, requestingUserId);

            // Mock UserService returns empty Optional
            when(userService.getUserSummary(UUID.fromString(hostUserId))).thenReturn(Optional.empty());

            // When
            hangoutService.enrichHostAtPlaceInfo(dto);

            // Then
            assertThat(dto.getHostAtPlaceDisplayName()).isNull();
            assertThat(dto.getHostAtPlaceImagePath()).isNull();
            verify(userService).getUserSummary(UUID.fromString(hostUserId));
        }
    }

    // ============================================================================
    // Tests for validation in hangoutFromHangoutRequest
    // ============================================================================

    @Nested
    class HangoutFromHangoutRequest {

        @Test
        void hangoutFromHangoutRequest_WithValidHostAtPlaceUserId_SetsFieldOnHangout() {
            // Given: CreateHangoutRequest with hostAtPlaceUserId = valid UUID
            String hostUserId = UUID.randomUUID().toString();
            String requestingUserId = "87654321-4321-4321-4321-210987654321";

            CreateHangoutRequest request = new CreateHangoutRequest();
            request.setTitle("Test Hangout");
            request.setDescription("Description");
            request.setVisibility(EventVisibility.INVITE_ONLY);
            request.setHostAtPlaceUserId(hostUserId);

            // Mock UserService returns user for validation
            UserSummaryDTO user = new UserSummaryDTO();
            user.setDisplayName("Host User");
            when(userService.getUserSummary(UUID.fromString(hostUserId))).thenReturn(Optional.of(user));

            // When
            Hangout hangout = hangoutService.hangoutFromHangoutRequest(request, requestingUserId);

            // Then
            assertThat(hangout.getHostAtPlaceUserId()).isEqualTo(hostUserId);
            verify(userService).getUserSummary(UUID.fromString(hostUserId));
        }

        @Test
        void hangoutFromHangoutRequest_WithInvalidHostAtPlaceUserId_ThrowsValidationException() {
            // Given: CreateHangoutRequest with hostAtPlaceUserId that doesn't exist
            String nonExistentUserId = UUID.randomUUID().toString();
            String requestingUserId = "87654321-4321-4321-4321-210987654321";

            CreateHangoutRequest request = new CreateHangoutRequest();
            request.setTitle("Test Hangout");
            request.setDescription("Description");
            request.setVisibility(EventVisibility.INVITE_ONLY);
            request.setHostAtPlaceUserId(nonExistentUserId);

            // Mock UserService returns empty
            when(userService.getUserSummary(UUID.fromString(nonExistentUserId))).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> hangoutService.hangoutFromHangoutRequest(request, requestingUserId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid hostAtPlaceUserId");

            verify(userService).getUserSummary(UUID.fromString(nonExistentUserId));
        }

        @Test
        void hangoutFromHangoutRequest_WithNullHostAtPlaceUserId_DoesNotValidate() {
            // Given: CreateHangoutRequest with hostAtPlaceUserId = null
            String requestingUserId = "87654321-4321-4321-4321-210987654321";

            CreateHangoutRequest request = new CreateHangoutRequest();
            request.setTitle("Test Hangout");
            request.setDescription("Description");
            request.setVisibility(EventVisibility.INVITE_ONLY);
            request.setHostAtPlaceUserId(null);

            // When
            Hangout hangout = hangoutService.hangoutFromHangoutRequest(request, requestingUserId);

            // Then: No exception, UserService never called for hostAtPlace, field is null
            assertThat(hangout.getHostAtPlaceUserId()).isNull();
            verify(userService, never()).getUserSummary(any(UUID.class));
        }
    }

    // ============================================================================
    // Tests for update flow in updateHangout
    // ============================================================================

    @Nested
    class UpdateHangout {

        @Test
        void updateHangout_WithNewHostAtPlaceUserId_UpdatesHangoutAndPointers() {
            // Given: Existing hangout with hostAtPlaceUserId = null
            String hangoutId = "12345678-1234-1234-1234-123456789012";
            String userId = "87654321-4321-4321-4321-210987654321";
            String newHostUserId = UUID.randomUUID().toString();
            String groupId = "11111111-1111-1111-1111-111111111111";

            Hangout existingHangout = createTestHangout(hangoutId);
            existingHangout.setHostAtPlaceUserId(null);
            existingHangout.setAssociatedGroups(new ArrayList<>(List.of(groupId)));

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setHostAtPlaceUserId(newHostUserId);

            // Mock authorization
            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));

            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            // Mock UserService returns valid user for hostAtPlace validation
            UserSummaryDTO hostUser = new UserSummaryDTO();
            hostUser.setDisplayName("Host User");
            when(userService.getUserSummary(UUID.fromString(newHostUserId))).thenReturn(Optional.of(hostUser));

            // When
            hangoutService.updateHangout(hangoutId, request, userId);

            // Then
            verify(hangoutRepository).createHangout(argThat(h ->
                newHostUserId.equals(h.getHostAtPlaceUserId())
            ));
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId), eq(hangoutId), any(), eq("basic fields"));
        }

        @Test
        void updateHangout_WithChangedHostAtPlaceUserId_UpdatesHangoutAndPointers() {
            // Given: Existing hangout with hostAtPlaceUserId = "user1"
            String hangoutId = "12345678-1234-1234-1234-123456789012";
            String userId = "87654321-4321-4321-4321-210987654321";
            String oldHostUserId = UUID.randomUUID().toString();
            String newHostUserId = UUID.randomUUID().toString();
            String groupId = "11111111-1111-1111-1111-111111111111";

            Hangout existingHangout = createTestHangout(hangoutId);
            existingHangout.setHostAtPlaceUserId(oldHostUserId);
            existingHangout.setAssociatedGroups(new ArrayList<>(List.of(groupId)));

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setHostAtPlaceUserId(newHostUserId);

            // Mock authorization
            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));

            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            // Mock UserService returns valid user for new hostAtPlace
            UserSummaryDTO hostUser = new UserSummaryDTO();
            hostUser.setDisplayName("New Host User");
            when(userService.getUserSummary(UUID.fromString(newHostUserId))).thenReturn(Optional.of(hostUser));

            // When
            hangoutService.updateHangout(hangoutId, request, userId);

            // Then: Hangout saved with "user2"
            verify(hangoutRepository).createHangout(argThat(h ->
                newHostUserId.equals(h.getHostAtPlaceUserId())
            ));
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId), eq(hangoutId), any(), eq("basic fields"));
        }

        @Test
        void updateHangout_WithClearingHostAtPlaceUserId_SetsToNull() {
            // Given: Existing hangout with hostAtPlaceUserId = "user1"
            String hangoutId = "12345678-1234-1234-1234-123456789012";
            String userId = "87654321-4321-4321-4321-210987654321";
            String existingHostUserId = UUID.randomUUID().toString();
            String groupId = "11111111-1111-1111-1111-111111111111";

            Hangout existingHangout = createTestHangout(hangoutId);
            existingHangout.setHostAtPlaceUserId(existingHostUserId);
            existingHangout.setAssociatedGroups(new ArrayList<>(List.of(groupId)));

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setHostAtPlaceUserId(null); // Clearing the field

            // Mock authorization
            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));

            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            // When
            hangoutService.updateHangout(hangoutId, request, userId);

            // Then: Hangout saved with hostAtPlaceUserId = null
            verify(hangoutRepository).createHangout(argThat(h ->
                h.getHostAtPlaceUserId() == null
            ));
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId), eq(hangoutId), any(), eq("basic fields"));
        }

        @Test
        void updateHangout_WithInvalidHostAtPlaceUserId_ThrowsValidationException() {
            // Given: UpdateHangoutRequest with hostAtPlaceUserId that doesn't exist
            String hangoutId = "12345678-1234-1234-1234-123456789012";
            String userId = "87654321-4321-4321-4321-210987654321";
            String invalidHostUserId = UUID.randomUUID().toString();
            String groupId = "11111111-1111-1111-1111-111111111111";

            Hangout existingHangout = createTestHangout(hangoutId);
            existingHangout.setAssociatedGroups(new ArrayList<>(List.of(groupId)));

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setHostAtPlaceUserId(invalidHostUserId);

            // Mock authorization
            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));

            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));

            // Mock UserService returns empty (user doesn't exist)
            when(userService.getUserSummary(UUID.fromString(invalidHostUserId))).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> hangoutService.updateHangout(hangoutId, request, userId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid hostAtPlaceUserId");

            // Verify hangout NOT saved
            verify(hangoutRepository, never()).createHangout(any(Hangout.class));
        }

        @Test
        void updateHangout_WithSameHostAtPlaceUserId_DoesNotTriggerPointerUpdate() {
            // Given: Existing hangout with hostAtPlaceUserId = "user1", request with same value
            String hangoutId = "12345678-1234-1234-1234-123456789012";
            String userId = "87654321-4321-4321-4321-210987654321";
            String sameHostUserId = UUID.randomUUID().toString();
            String groupId = "11111111-1111-1111-1111-111111111111";

            Hangout existingHangout = createTestHangout(hangoutId);
            existingHangout.setHostAtPlaceUserId(sameHostUserId);
            existingHangout.setAssociatedGroups(new ArrayList<>(List.of(groupId)));

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setHostAtPlaceUserId(sameHostUserId); // Same value as existing

            // Mock authorization
            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));

            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            // Mock UserService returns valid user (needed for validation)
            UserSummaryDTO hostUser = new UserSummaryDTO();
            hostUser.setDisplayName("Host User");
            when(userService.getUserSummary(UUID.fromString(sameHostUserId))).thenReturn(Optional.of(hostUser));

            // When
            hangoutService.updateHangout(hangoutId, request, userId);

            // Then: pointerUpdateService NOT called (no change detected)
            verify(pointerUpdateService, never()).updatePointerWithRetry(anyString(), anyString(), any(), anyString());
        }
    }
}
