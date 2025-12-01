package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.HangoutDetailData;
import com.bbthechange.inviter.dto.SetInterestRequest;
import com.bbthechange.inviter.dto.UserSummaryDTO;
import com.bbthechange.inviter.exception.ResourceNotFoundException;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for user interest level management in HangoutServiceImpl.
 *
 * Coverage:
 * - Setting new and existing interest levels
 * - Removing interest levels
 * - User image denormalization to interest levels
 * - Authorization checks for interest operations
 * - Participant count updates
 * - Pointer updates for interest level changes
 */
class HangoutServiceInterestLevelTest extends HangoutServiceTestBase {

    @Test
    void setUserInterest_NewInterest_Success() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";
        SetInterestRequest request = new SetInterestRequest("GOING", "Excited to attend!");

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setVisibility(EventVisibility.PUBLIC);
        hangout.setAssociatedGroups(List.of("11111111-1111-1111-1111-111111111111"));

        HangoutDetailData data = HangoutDetailData.builder().withHangout(hangout).build();
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);

        UserSummaryDTO user = createTestUser(userId);
        when(userService.getUserSummary(UUID.fromString(userId))).thenReturn(Optional.of(user));

        InterestLevel mockInterest = createTestInterestLevel();
        when(hangoutRepository.saveInterestLevel(any(InterestLevel.class))).thenReturn(mockInterest);
        doNothing().when(groupRepository).atomicallyUpdateParticipantCount(anyString(), anyString(), anyInt());

        // When
        hangoutService.setUserInterest(hangoutId, request, userId);

        // Then
        verify(hangoutRepository).saveInterestLevel(argThat(interest ->
            interest.getEventId().equals(hangoutId) &&
            interest.getUserId().equals(userId) &&
            interest.getStatus().equals("GOING") &&
            interest.getNotes().equals("Excited to attend!") &&
            interest.getMainImagePath() == null // Test user has no image
        ));
        verify(groupRepository).atomicallyUpdateParticipantCount("11111111-1111-1111-1111-111111111111", hangoutId, 1);
    }

    @Test
    void setUserInterest_UpdateExistingInterest_Success() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";
        SetInterestRequest request = new SetInterestRequest("NOT_GOING", "Can't make it");

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setVisibility(EventVisibility.PUBLIC);
        hangout.setAssociatedGroups(List.of("11111111-1111-1111-1111-111111111111"));

        // Existing interest level - user was previously GOING
        InterestLevel existingInterest = createTestInterestLevel();
        existingInterest.setUserId(userId);
        existingInterest.setStatus("GOING");

        HangoutDetailData data = HangoutDetailData.builder()
            .withHangout(hangout)
            .withAttendance(List.of(existingInterest))
            .build();
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);

        UserSummaryDTO user = createTestUser(userId);
        when(userService.getUserSummary(UUID.fromString(userId))).thenReturn(Optional.of(user));

        InterestLevel mockInterest2 = createTestInterestLevel();
        when(hangoutRepository.saveInterestLevel(any(InterestLevel.class))).thenReturn(mockInterest2);
        doNothing().when(groupRepository).atomicallyUpdateParticipantCount(anyString(), anyString(), anyInt());

        // When
        hangoutService.setUserInterest(hangoutId, request, userId);

        // Then
        verify(hangoutRepository).saveInterestLevel(argThat(interest ->
            interest.getStatus().equals("NOT_GOING") &&
            interest.getNotes().equals("Can't make it")
        ));
        // User changed from GOING to NOT_GOING, so count should decrease by 1
        verify(groupRepository).atomicallyUpdateParticipantCount("11111111-1111-1111-1111-111111111111", hangoutId, -1);
    }

    @Test
    void setUserInterest_UserNotFound_ThrowsException() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";
        SetInterestRequest request = new SetInterestRequest("GOING", null);

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setVisibility(EventVisibility.PUBLIC);

        HangoutDetailData data = HangoutDetailData.builder().withHangout(hangout).build();
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
        when(userService.getUserSummary(UUID.fromString(userId))).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> hangoutService.setUserInterest(hangoutId, request, userId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("User not found");

        verify(hangoutRepository, never()).saveInterestLevel(any());
        verify(groupRepository, never()).atomicallyUpdateParticipantCount(anyString(), anyString(), anyInt());
    }

    @Test
    void setUserInterest_EventNotFound_ThrowsException() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";
        SetInterestRequest request = new SetInterestRequest("GOING", null);

        HangoutDetailData data = HangoutDetailData.builder().withHangout(null).build();
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);

        // When/Then
        assertThatThrownBy(() -> hangoutService.setUserInterest(hangoutId, request, userId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Hangout not found");

        verify(userService, never()).getUserById(any());
        verify(hangoutRepository, never()).saveInterestLevel(any());
        verify(groupRepository, never()).atomicallyUpdateParticipantCount(anyString(), anyString(), anyInt());
    }

    @Test
    void setUserInterest_UnauthorizedUser_ThrowsException() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";
        SetInterestRequest request = new SetInterestRequest("GOING", null);

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setVisibility(EventVisibility.INVITE_ONLY);
        hangout.setAssociatedGroups(List.of("11111111-1111-1111-1111-111111111111"));

        HangoutDetailData data = HangoutDetailData.builder().withHangout(hangout).build();
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> hangoutService.setUserInterest(hangoutId, request, userId))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("Cannot set interest for this hangout");

        verify(userService, never()).getUserById(any());
        verify(hangoutRepository, never()).saveInterestLevel(any());
        verify(groupRepository, never()).atomicallyUpdateParticipantCount(anyString(), anyString(), anyInt());
    }

    @Test
    void setUserInterest_WithUserImage_DenormalizesMainImagePath() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";
        String userImagePath = "images/users/87654321-4321-4321-4321-210987654321/profile.jpg";
        SetInterestRequest request = new SetInterestRequest("INTERESTED", null);

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setVisibility(EventVisibility.PUBLIC);
        hangout.setAssociatedGroups(List.of("11111111-1111-1111-1111-111111111111"));

        HangoutDetailData data = HangoutDetailData.builder().withHangout(hangout).build();
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);

        UserSummaryDTO user = createTestUser(userId);
        user.setMainImagePath(userImagePath); // User has a profile image
        when(userService.getUserSummary(UUID.fromString(userId))).thenReturn(Optional.of(user));

        InterestLevel mockInterest = createTestInterestLevel();
        when(hangoutRepository.saveInterestLevel(any(InterestLevel.class))).thenReturn(mockInterest);
        doNothing().when(groupRepository).atomicallyUpdateParticipantCount(anyString(), anyString(), anyInt());

        // When
        hangoutService.setUserInterest(hangoutId, request, userId);

        // Then - Verify mainImagePath is denormalized from user profile
        verify(hangoutRepository).saveInterestLevel(argThat(interest ->
            interest.getEventId().equals(hangoutId) &&
            interest.getUserId().equals(userId) &&
            interest.getStatus().equals("INTERESTED") &&
            interest.getMainImagePath().equals(userImagePath) // Image path should be denormalized
        ));
        verify(groupRepository).atomicallyUpdateParticipantCount("11111111-1111-1111-1111-111111111111", hangoutId, 1);
    }

    @Test
    void setUserInterest_UpdatesAllGroupPointers() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";
        String groupId1 = "11111111-1111-1111-1111-111111111111";
        String groupId2 = "22222222-2222-2222-2222-222222222222";
        SetInterestRequest request = new SetInterestRequest("GOING", "Excited!");

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setVisibility(EventVisibility.PUBLIC);
        hangout.setAssociatedGroups(List.of(groupId1, groupId2));

        InterestLevel newInterest = createTestInterestLevel();
        newInterest.setUserId(userId);
        newInterest.setStatus("GOING");
        newInterest.setNotes("Excited!");

        // Mock getHangoutDetailData to return updated attendance after save
        HangoutDetailData initialData = HangoutDetailData.builder().withHangout(hangout).build();
        HangoutDetailData updatedData = HangoutDetailData.builder()
            .withHangout(hangout)
            .withAttendance(List.of(newInterest))
            .build();

        when(hangoutRepository.getHangoutDetailData(hangoutId))
            .thenReturn(initialData)  // First call for authorization/initial check
            .thenReturn(updatedData); // Second call after save to get updated interest levels

        UserSummaryDTO user = createTestUser(userId);
        when(userService.getUserSummary(UUID.fromString(userId))).thenReturn(Optional.of(user));

        when(hangoutRepository.saveInterestLevel(any(InterestLevel.class))).thenReturn(newInterest);
        doNothing().when(groupRepository).atomicallyUpdateParticipantCount(anyString(), anyString(), anyInt());

        // When
        hangoutService.setUserInterest(hangoutId, request, userId);

        // Then - verify pointerUpdateService was called for both groups
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId1), eq(hangoutId), any(), eq("interest levels"));
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId2), eq(hangoutId), any(), eq("interest levels"));
    }

    @Test
    void setUserInterest_WithNoAssociatedGroups_DoesNotUpdatePointers() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";
        SetInterestRequest request = new SetInterestRequest("GOING", "Excited!");

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setVisibility(EventVisibility.PUBLIC);
        hangout.setAssociatedGroups(null); // No associated groups

        HangoutDetailData data = HangoutDetailData.builder().withHangout(hangout).build();
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);

        UserSummaryDTO user = createTestUser(userId);
        when(userService.getUserSummary(UUID.fromString(userId))).thenReturn(Optional.of(user));

        InterestLevel mockInterest = createTestInterestLevel();
        when(hangoutRepository.saveInterestLevel(any(InterestLevel.class))).thenReturn(mockInterest);

        // When
        hangoutService.setUserInterest(hangoutId, request, userId);

        // Then - verify interest level was saved but pointers were not updated
        verify(hangoutRepository).saveInterestLevel(any(InterestLevel.class));
        verify(pointerUpdateService, never()).updatePointerWithRetry(anyString(), anyString(), any(), anyString());
    }

    @Test
    void removeUserInterest_ExistingInterest_Success() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setVisibility(EventVisibility.PUBLIC);
        hangout.setAssociatedGroups(List.of("11111111-1111-1111-1111-111111111111"));

        InterestLevel existingInterest = createTestInterestLevel();
        existingInterest.setUserId(userId);
        existingInterest.setStatus("GOING");

        HangoutDetailData data = HangoutDetailData.builder()
            .withHangout(hangout)
            .withAttendance(List.of(existingInterest))
            .build();
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);

        doNothing().when(hangoutRepository).deleteInterestLevel(hangoutId, userId);
        doNothing().when(groupRepository).atomicallyUpdateParticipantCount(anyString(), anyString(), anyInt());

        // When
        hangoutService.removeUserInterest(hangoutId, userId);

        // Then
        verify(hangoutRepository).deleteInterestLevel(hangoutId, userId);
        // User was GOING, so removing should decrease count by 1
        verify(groupRepository).atomicallyUpdateParticipantCount("11111111-1111-1111-1111-111111111111", hangoutId, -1);
    }

    @Test
    void removeUserInterest_NoExistingInterest_NoCountChange() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setVisibility(EventVisibility.PUBLIC);
        hangout.setAssociatedGroups(List.of("11111111-1111-1111-1111-111111111111"));

        // No existing interest level for this user
        HangoutDetailData data = HangoutDetailData.builder().withHangout(hangout).build();
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);

        doNothing().when(hangoutRepository).deleteInterestLevel(hangoutId, userId);

        // When
        hangoutService.removeUserInterest(hangoutId, userId);

        // Then
        verify(hangoutRepository).deleteInterestLevel(hangoutId, userId);
        // No existing interest, so no count change should occur
        verify(groupRepository, never()).atomicallyUpdateParticipantCount(anyString(), anyString(), anyInt());
    }

    @Test
    void removeUserInterest_EventNotFound_ThrowsException() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";

        HangoutDetailData data = HangoutDetailData.builder().withHangout(null).build();
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);

        // When/Then
        assertThatThrownBy(() -> hangoutService.removeUserInterest(hangoutId, userId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Hangout not found");

        verify(hangoutRepository, never()).deleteInterestLevel(anyString(), anyString());
        verify(groupRepository, never()).atomicallyUpdateParticipantCount(anyString(), anyString(), anyInt());
    }

    @Test
    void removeUserInterest_UnauthorizedUser_ThrowsException() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setVisibility(EventVisibility.INVITE_ONLY);
        hangout.setAssociatedGroups(List.of("11111111-1111-1111-1111-111111111111"));

        HangoutDetailData data = HangoutDetailData.builder().withHangout(hangout).build();
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> hangoutService.removeUserInterest(hangoutId, userId))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("Cannot remove interest for this hangout");

        verify(hangoutRepository, never()).deleteInterestLevel(anyString(), anyString());
        verify(groupRepository, never()).atomicallyUpdateParticipantCount(anyString(), anyString(), anyInt());
    }

    @Test
    void removeUserInterest_UpdatesAllGroupPointers() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";
        String groupId1 = "11111111-1111-1111-1111-111111111111";
        String groupId2 = "22222222-2222-2222-2222-222222222222";

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setVisibility(EventVisibility.PUBLIC);
        hangout.setAssociatedGroups(List.of(groupId1, groupId2));

        InterestLevel existingInterest = createTestInterestLevel();
        existingInterest.setUserId(userId);
        existingInterest.setStatus("GOING");

        String otherUserId = UUID.randomUUID().toString();
        InterestLevel otherInterest = new InterestLevel(hangoutId, otherUserId, "Other User", "INTERESTED");

        // Mock getHangoutDetailData to return attendance before and after deletion
        HangoutDetailData initialData = HangoutDetailData.builder()
            .withHangout(hangout)
            .withAttendance(List.of(existingInterest, otherInterest))
            .build();
        HangoutDetailData updatedData = HangoutDetailData.builder()
            .withHangout(hangout)
            .withAttendance(List.of(otherInterest))
            .build();

        when(hangoutRepository.getHangoutDetailData(hangoutId))
            .thenReturn(initialData)  // First call for authorization/initial check
            .thenReturn(updatedData); // Second call after deletion to get updated interest levels

        doNothing().when(hangoutRepository).deleteInterestLevel(hangoutId, userId);
        doNothing().when(groupRepository).atomicallyUpdateParticipantCount(anyString(), anyString(), anyInt());

        // When
        hangoutService.removeUserInterest(hangoutId, userId);

        // Then - verify pointerUpdateService was called for both groups
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId1), eq(hangoutId), any(), eq("interest levels"));
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId2), eq(hangoutId), any(), eq("interest levels"));
    }

    @Test
    void removeUserInterest_WithNoAssociatedGroups_DoesNotUpdatePointers() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setVisibility(EventVisibility.PUBLIC);
        hangout.setAssociatedGroups(null); // No associated groups

        InterestLevel existingInterest = createTestInterestLevel();
        existingInterest.setUserId(userId);
        existingInterest.setStatus("GOING");

        HangoutDetailData data = HangoutDetailData.builder()
            .withHangout(hangout)
            .withAttendance(List.of(existingInterest))
            .build();
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);

        doNothing().when(hangoutRepository).deleteInterestLevel(hangoutId, userId);

        // When
        hangoutService.removeUserInterest(hangoutId, userId);

        // Then - verify interest level was deleted but pointers were not updated
        verify(hangoutRepository).deleteInterestLevel(hangoutId, userId);
        verify(pointerUpdateService, never()).updatePointerWithRetry(anyString(), anyString(), any(), anyString());
    }
}
