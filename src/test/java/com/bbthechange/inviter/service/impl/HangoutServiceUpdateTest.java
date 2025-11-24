package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.HangoutDetailData;
import com.bbthechange.inviter.dto.TimeInfo;
import com.bbthechange.inviter.dto.UpdateHangoutRequest;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.service.FuzzyTimeService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for hangout update functionality in HangoutServiceImpl.
 *
 * Coverage:
 * - Updating hangout basic fields (title, description, visibility, carpoolEnabled)
 * - Updating hangout time information
 * - Series integration (update triggers, failure handling)
 * - Pointer propagation on field changes
 * - Group last modified timestamp updates on modification
 */
class HangoutServiceUpdateTest extends HangoutServiceTestBase {

    @Test
    void updateEventTitle_Success() {
        // Given
        String eventId = "12345678-1234-1234-1234-123456789012";
        String newTitle = "Updated Event Title";
        String userId = "87654321-4321-4321-4321-210987654321";

        Hangout hangout = createTestHangout(eventId);
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222")));

        HangoutDetailData data = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(data);

        // Mock authorization - user is admin in group-1
        GroupMembership adminMembership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Test Group");
        adminMembership.setRole(GroupRole.ADMIN);
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(adminMembership));

        when(hangoutRepository.save(any(Hangout.class))).thenReturn(hangout);

        // When
        assertThatCode(() -> hangoutService.updateEventTitle(eventId, newTitle, userId))
            .doesNotThrowAnyException();

        // Then - verify multi-step pointer update pattern
        verify(hangoutRepository).save(any(Hangout.class)); // Step 1: Update canonical record
        verify(pointerUpdateService, times(2)).updatePointerWithRetry(anyString(), eq(eventId), any(), eq("title")); // Step 2: Update pointers
    }

    @Test
    void updateHangout_WithTimeInputChange_Success() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";

        TimeInfo newTimeInfo = new TimeInfo();
        newTimeInfo.setPeriodGranularity("morning");
        newTimeInfo.setPeriodStart("2025-08-06T08:00:00Z");

        UpdateHangoutRequest request = new UpdateHangoutRequest();
        request.setTimeInfo(newTimeInfo);

        // Mock existing hangout
        Hangout existingHangout = createTestHangout(hangoutId);
        existingHangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111")));
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));

        // Mock authorization
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Test Group");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership));

        // Mock fuzzy time service
        FuzzyTimeService.TimeConversionResult timeResult = new FuzzyTimeService.TimeConversionResult(1754603600L, 1754618000L);
        when(fuzzyTimeService.convert(newTimeInfo)).thenReturn(timeResult);

        // Mock repository operations
        when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

        // When
        assertThatCode(() -> hangoutService.updateHangout(hangoutId, request, userId))
            .doesNotThrowAnyException();

        // Then
        // Verify fuzzy time conversion was called
        verify(fuzzyTimeService).convert(newTimeInfo);

        // Verify hangout was updated with new timestamps
        verify(hangoutRepository).createHangout(argThat(hangout ->
            hangout.getTimeInput().equals(newTimeInfo) &&
            hangout.getStartTimestamp().equals(1754603600L) &&
            hangout.getEndTimestamp().equals(1754618000L)
        ));

        // Verify pointer was updated with basic fields (including time fields)
        verify(pointerUpdateService).updatePointerWithRetry(eq("11111111-1111-1111-1111-111111111111"), eq(hangoutId), any(), eq("basic fields"));
    }

    @Test
    void updateHangout_WithHangoutInSeries_TriggersSeriesUpdate() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";
        String seriesId = "series-123";

        UpdateHangoutRequest request = new UpdateHangoutRequest();
        request.setTitle("Updated Series Hangout");

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setSeriesId(seriesId);
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111")));

        // Mock authorization
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Test Group");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership));

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(hangout);
        doNothing().when(eventSeriesService).updateSeriesAfterHangoutModification(hangoutId);

        // When
        hangoutService.updateHangout(hangoutId, request, userId);

        // Then
        // Verify standard hangout update logic executes
        verify(hangoutRepository).createHangout(argThat(h -> h.getTitle().equals("Updated Series Hangout")));
        verify(pointerUpdateService).updatePointerWithRetry(eq("11111111-1111-1111-1111-111111111111"), eq(hangoutId), any(), eq("basic fields"));

        // Verify series update is triggered
        verify(eventSeriesService).updateSeriesAfterHangoutModification(hangoutId);
    }

    @Test
    void updateHangout_WithStandaloneHangout_SkipsSeriesUpdate() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";

        UpdateHangoutRequest request = new UpdateHangoutRequest();
        request.setTitle("Updated Standalone Hangout");

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setSeriesId(null); // Standalone hangout
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111")));

        // Mock authorization
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Test Group");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership));

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(hangout);

        // When
        hangoutService.updateHangout(hangoutId, request, userId);

        // Then
        // Verify standard hangout update logic executes
        verify(hangoutRepository).createHangout(argThat(h -> h.getTitle().equals("Updated Standalone Hangout")));
        verify(pointerUpdateService).updatePointerWithRetry(eq("11111111-1111-1111-1111-111111111111"), eq(hangoutId), any(), eq("basic fields"));

        // Verify series update is NOT called
        verify(eventSeriesService, never()).updateSeriesAfterHangoutModification(anyString());
    }

    @Test
    void updateHangout_WithSeriesUpdateFailure_ContinuesHangoutUpdate() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";
        String seriesId = "series-123";

        UpdateHangoutRequest request = new UpdateHangoutRequest();
        request.setTitle("Updated Hangout");

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setSeriesId(seriesId);
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111")));

        // Mock authorization
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Test Group");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership));

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(hangout);

        // Mock series service to throw exception
        doThrow(new RuntimeException("Series update failed")).when(eventSeriesService).updateSeriesAfterHangoutModification(hangoutId);

        // When
        assertThatCode(() -> hangoutService.updateHangout(hangoutId, request, userId))
            .doesNotThrowAnyException();

        // Then
        // Verify hangout update completes successfully
        verify(hangoutRepository).createHangout(any(Hangout.class));
        verify(pointerUpdateService).updatePointerWithRetry(anyString(), anyString(), any(), eq("basic fields"));

        // Verify series update was attempted
        verify(eventSeriesService).updateSeriesAfterHangoutModification(hangoutId);
    }

    @Test
    void updateHangout_WithTimeInfoChange_UpdatesSeriesTimestamps() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";
        String seriesId = "series-123";

        TimeInfo newTimeInfo = new TimeInfo();
        newTimeInfo.setPeriodGranularity("morning");
        newTimeInfo.setPeriodStart("2025-08-06T08:00:00Z");

        UpdateHangoutRequest request = new UpdateHangoutRequest();
        request.setTimeInfo(newTimeInfo);

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setSeriesId(seriesId);
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111")));

        // Mock authorization
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Test Group");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership));

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(hangout);
        doNothing().when(eventSeriesService).updateSeriesAfterHangoutModification(hangoutId);

        // Mock fuzzy time service
        FuzzyTimeService.TimeConversionResult timeResult = new FuzzyTimeService.TimeConversionResult(1754603600L, 1754618000L);
        when(fuzzyTimeService.convert(newTimeInfo)).thenReturn(timeResult);

        // When
        hangoutService.updateHangout(hangoutId, request, userId);

        // Then
        // Verify hangout timestamps are updated
        verify(hangoutRepository).createHangout(argThat(h ->
            h.getTimeInput().equals(newTimeInfo) &&
            h.getStartTimestamp().equals(1754603600L) &&
            h.getEndTimestamp().equals(1754618000L)
        ));

        // Verify pointer records are updated with basic fields (including time fields)
        verify(pointerUpdateService).updatePointerWithRetry(eq("11111111-1111-1111-1111-111111111111"), eq(hangoutId), any(), eq("basic fields"));

        // Verify series update is triggered
        verify(eventSeriesService).updateSeriesAfterHangoutModification(hangoutId);
    }

    @Test
    void updateHangout_PropagatesToPointersWhenMainImagePathChanges() {
        // Given
        String hangoutId = "hangout-1";
        String userId = "87654321-4321-4321-4321-210987654321";

        Hangout existingHangout = createTestHangout(hangoutId);
        existingHangout.setMainImagePath("/old-image.jpg");
        existingHangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("group-1")));

        UpdateHangoutRequest request = new UpdateHangoutRequest();
        request.setMainImagePath("/new-image.jpg");

        // Mock authorization - user is in group
        GroupMembership membership = createTestMembership("group-1", userId, "Group 1");
        when(groupRepository.findMembership("group-1", userId)).thenReturn(Optional.of(membership));

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));
        when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

        // When
        hangoutService.updateHangout(hangoutId, request, userId);

        // Then - Verify pointer was updated with basic fields (including mainImagePath)
        verify(pointerUpdateService).updatePointerWithRetry(eq("group-1"), eq(hangoutId), any(), eq("basic fields"));
    }

    @Test
    void updateHangout_WithDescriptionChange_ShouldUpdatePointers() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";
        String newDescription = "Updated description for the hangout";

        Hangout existingHangout = createTestHangout(hangoutId);
        existingHangout.setDescription("Old description");
        existingHangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222")));

        UpdateHangoutRequest request = new UpdateHangoutRequest();
        request.setDescription(newDescription);

        // Mock authorization - user is member of first group
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group 1");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership));

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));
        when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

        // When
        hangoutService.updateHangout(hangoutId, request, userId);

        // Then
        // Verify canonical hangout was updated
        verify(hangoutRepository).createHangout(argThat(h -> h.getDescription().equals(newDescription)));

        // Verify pointers were updated for both groups with basic fields
        verify(pointerUpdateService, times(2)).updatePointerWithRetry(anyString(), eq(hangoutId), any(), eq("basic fields"));
    }

    @Test
    void updateHangout_WithVisibilityChange_ShouldUpdatePointers() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";

        Hangout existingHangout = createTestHangout(hangoutId);
        existingHangout.setVisibility(EventVisibility.INVITE_ONLY);
        existingHangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111")));

        UpdateHangoutRequest request = new UpdateHangoutRequest();
        request.setVisibility(EventVisibility.PUBLIC);

        // Mock authorization
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group 1");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership));

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));
        when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

        // When
        hangoutService.updateHangout(hangoutId, request, userId);

        // Then
        // Verify canonical hangout visibility was changed
        verify(hangoutRepository).createHangout(argThat(h -> h.getVisibility() == EventVisibility.PUBLIC));

        // Verify pointer was updated with basic fields (including visibility)
        verify(pointerUpdateService).updatePointerWithRetry(eq("11111111-1111-1111-1111-111111111111"), eq(hangoutId), any(), eq("basic fields"));
    }

    @Test
    void updateHangout_WithCarpoolEnabledChange_ShouldUpdatePointers() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";

        Hangout existingHangout = createTestHangout(hangoutId);
        existingHangout.setCarpoolEnabled(false);
        existingHangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(
            "11111111-1111-1111-1111-111111111111",
            "22222222-2222-2222-2222-222222222222",
            "33333333-3333-3333-3333-333333333333"
        )));

        UpdateHangoutRequest request = new UpdateHangoutRequest();
        request.setCarpoolEnabled(true);

        // Mock authorization
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group 1");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership));

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));
        when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

        // When
        hangoutService.updateHangout(hangoutId, request, userId);

        // Then
        // Verify canonical hangout carpoolEnabled was changed
        verify(hangoutRepository).createHangout(argThat(h -> h.isCarpoolEnabled()));

        // Verify pointers were updated for all 3 groups with basic fields
        verify(pointerUpdateService, times(3)).updatePointerWithRetry(anyString(), eq(hangoutId), any(), eq("basic fields"));
    }

    @Test
    void updateHangout_WithNoFieldChanges_ShouldNotUpdatePointers() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";

        Hangout existingHangout = createTestHangout(hangoutId);
        existingHangout.setDescription("Same description");
        existingHangout.setVisibility(EventVisibility.INVITE_ONLY);
        existingHangout.setCarpoolEnabled(false);
        existingHangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111")));

        UpdateHangoutRequest request = new UpdateHangoutRequest();
        // Request has no changes to description, visibility, or carpoolEnabled

        // Mock authorization
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group 1");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership));

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));
        when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

        // When
        hangoutService.updateHangout(hangoutId, request, userId);

        // Then
        // Verify canonical hangout was still saved
        verify(hangoutRepository).createHangout(any(Hangout.class));

        // Verify pointers were NOT updated (no relevant field changes)
        verify(pointerUpdateService, never()).updatePointerWithRetry(anyString(), anyString(), any(), anyString());
    }

    @Test
    void updateHangout_UpdatesGroupLastModified() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";
        String groupId = "11111111-1111-1111-1111-111111111111";

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(groupId)));

        HangoutDetailData data = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);

        // Mock authorization
        GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
        membership.setRole(GroupRole.ADMIN);
        when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));

        when(hangoutRepository.save(any(Hangout.class))).thenReturn(hangout);

        // When
        hangoutService.updateEventTitle(hangoutId, "New Title", userId);

        // Then - Verify GroupTimestampService was called with group ID
        ArgumentCaptor<List<String>> groupIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(groupTimestampService).updateGroupTimestamps(groupIdsCaptor.capture());

        List<String> capturedGroupIds = groupIdsCaptor.getValue();
        assertThat(capturedGroupIds).containsExactly(groupId);
    }
}
