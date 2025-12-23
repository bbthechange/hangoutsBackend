package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.Address;
import com.bbthechange.inviter.dto.HangoutDetailData;
import com.bbthechange.inviter.dto.TimeInfo;
import com.bbthechange.inviter.dto.UpdateHangoutRequest;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.service.FuzzyTimeService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

        HangoutDetailData data = HangoutDetailData.builder().withHangout(hangout).build();
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

        HangoutDetailData data = HangoutDetailData.builder().withHangout(hangout).build();
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

    // ================= Time/Location Change Notification Tests =================

    @Nested
    class TimeLocationChangeNotifications {

        private static final String HANGOUT_ID = "12345678-1234-1234-1234-123456789012";
        private static final String USER_ID = "87654321-4321-4321-4321-210987654321";
        private static final String GROUP_ID = "11111111-1111-1111-1111-111111111111";

        @Test
        void updateHangout_TimeChanged_TriggersNotificationWithTimeChangeType() {
            // Given: Existing hangout with TimeInfo, update request with different startTime
            Hangout existingHangout = createTestHangout(HANGOUT_ID);
            TimeInfo existingTimeInfo = new TimeInfo();
            existingTimeInfo.setPeriodGranularity("DAY");
            existingTimeInfo.setPeriodStart("2025-01-01T00:00:00Z");
            existingTimeInfo.setStartTime("2025-01-01T10:00:00Z");
            existingHangout.setTimeInput(existingTimeInfo);
            existingHangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(GROUP_ID)));

            TimeInfo newTimeInfo = new TimeInfo();
            newTimeInfo.setPeriodGranularity("DAY");
            newTimeInfo.setPeriodStart("2025-01-02T00:00:00Z"); // Different date
            newTimeInfo.setStartTime("2025-01-02T10:00:00Z");

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setTimeInfo(newTimeInfo);

            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            // Mock authorization
            GroupMembership membership = createTestMembership(GROUP_ID, USER_ID, "Test Group");
            when(groupRepository.findMembership(GROUP_ID, USER_ID)).thenReturn(Optional.of(membership));

            // Mock fuzzy time service
            FuzzyTimeService.TimeConversionResult timeResult = new FuzzyTimeService.TimeConversionResult(1754603600L, 1754618000L);
            when(fuzzyTimeService.convert(newTimeInfo)).thenReturn(timeResult);

            // Mock detail data with GOING users
            InterestLevel goingUser = new InterestLevel();
            goingUser.setUserId("user-going-1");
            goingUser.setStatus("GOING");
            HangoutDetailData detailData = HangoutDetailData.builder()
                .withHangout(existingHangout)
                .withAttendance(List.of(goingUser))
                .build();
            when(hangoutRepository.getHangoutDetailData(HANGOUT_ID)).thenReturn(detailData);

            // When
            hangoutService.updateHangout(HANGOUT_ID, request, USER_ID);

            // Then: notification sent with changeType="time"
            ArgumentCaptor<String> changeTypeCaptor = ArgumentCaptor.forClass(String.class);
            verify(notificationService).notifyHangoutUpdated(
                eq(HANGOUT_ID),
                anyString(),
                anyList(),
                changeTypeCaptor.capture(),
                eq(USER_ID),
                any(),
                any()
            );
            assertThat(changeTypeCaptor.getValue()).isEqualTo("time");
        }

        @Test
        void updateHangout_LocationChanged_TriggersNotificationWithLocationChangeType() {
            // Given: Existing hangout with Address, update request with different address
            Hangout existingHangout = createTestHangout(HANGOUT_ID);
            Address existingLocation = new Address();
            existingLocation.setName("Old Place");
            existingLocation.setStreetAddress("123 Old St");
            existingHangout.setLocation(existingLocation);
            existingHangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(GROUP_ID)));

            Address newLocation = new Address();
            newLocation.setName("New Place");
            newLocation.setStreetAddress("456 New St");

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setLocation(newLocation);

            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            GroupMembership membership = createTestMembership(GROUP_ID, USER_ID, "Test Group");
            when(groupRepository.findMembership(GROUP_ID, USER_ID)).thenReturn(Optional.of(membership));

            // Mock detail data with INTERESTED user
            InterestLevel interestedUser = new InterestLevel();
            interestedUser.setUserId("user-interested-1");
            interestedUser.setStatus("INTERESTED");
            HangoutDetailData detailData = HangoutDetailData.builder()
                .withHangout(existingHangout)
                .withAttendance(List.of(interestedUser))
                .build();
            when(hangoutRepository.getHangoutDetailData(HANGOUT_ID)).thenReturn(detailData);

            // When
            hangoutService.updateHangout(HANGOUT_ID, request, USER_ID);

            // Then: notification sent with changeType="location"
            ArgumentCaptor<String> changeTypeCaptor = ArgumentCaptor.forClass(String.class);
            verify(notificationService).notifyHangoutUpdated(
                eq(HANGOUT_ID),
                anyString(),
                anyList(),
                changeTypeCaptor.capture(),
                eq(USER_ID),
                any(),
                any()
            );
            assertThat(changeTypeCaptor.getValue()).isEqualTo("location");
        }

        @Test
        void updateHangout_TimeAndLocationChanged_TriggersNotificationWithCombinedChangeType() {
            // Given: Update request changes both timeInfo and location
            Hangout existingHangout = createTestHangout(HANGOUT_ID);
            TimeInfo existingTimeInfo = new TimeInfo();
            existingTimeInfo.setStartTime("2025-01-01T10:00:00Z");
            existingHangout.setTimeInput(existingTimeInfo);
            Address existingLocation = new Address();
            existingLocation.setName("Old Place");
            existingHangout.setLocation(existingLocation);
            existingHangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(GROUP_ID)));

            TimeInfo newTimeInfo = new TimeInfo();
            newTimeInfo.setStartTime("2025-01-02T10:00:00Z"); // Different

            Address newLocation = new Address();
            newLocation.setName("New Place"); // Different

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setTimeInfo(newTimeInfo);
            request.setLocation(newLocation);

            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            GroupMembership membership = createTestMembership(GROUP_ID, USER_ID, "Test Group");
            when(groupRepository.findMembership(GROUP_ID, USER_ID)).thenReturn(Optional.of(membership));

            FuzzyTimeService.TimeConversionResult timeResult = new FuzzyTimeService.TimeConversionResult(1754603600L, 1754618000L);
            when(fuzzyTimeService.convert(newTimeInfo)).thenReturn(timeResult);

            InterestLevel goingUser = new InterestLevel();
            goingUser.setUserId("user-going-1");
            goingUser.setStatus("GOING");
            HangoutDetailData detailData = HangoutDetailData.builder()
                .withHangout(existingHangout)
                .withAttendance(List.of(goingUser))
                .build();
            when(hangoutRepository.getHangoutDetailData(HANGOUT_ID)).thenReturn(detailData);

            // When
            hangoutService.updateHangout(HANGOUT_ID, request, USER_ID);

            // Then: notification sent with changeType="time_and_location"
            ArgumentCaptor<String> changeTypeCaptor = ArgumentCaptor.forClass(String.class);
            verify(notificationService).notifyHangoutUpdated(
                eq(HANGOUT_ID),
                anyString(),
                anyList(),
                changeTypeCaptor.capture(),
                eq(USER_ID),
                any(),
                any()
            );
            assertThat(changeTypeCaptor.getValue()).isEqualTo("time_and_location");
        }

        @Test
        void updateHangout_OnlyTitleChanged_NoNotification() {
            // Given: Update request only changes title
            Hangout existingHangout = createTestHangout(HANGOUT_ID);
            existingHangout.setTitle("Old Title");
            existingHangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(GROUP_ID)));

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setTitle("New Title");

            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            GroupMembership membership = createTestMembership(GROUP_ID, USER_ID, "Test Group");
            when(groupRepository.findMembership(GROUP_ID, USER_ID)).thenReturn(Optional.of(membership));

            // When
            hangoutService.updateHangout(HANGOUT_ID, request, USER_ID);

            // Then: notification NOT called
            verify(notificationService, never()).notifyHangoutUpdated(
                anyString(), anyString(), anyList(), anyString(), anyString(), any(), any()
            );
        }

        @Test
        void updateHangout_SameTimeInfo_NoNotification() {
            // Given: Update request has same TimeInfo values as existing hangout
            Hangout existingHangout = createTestHangout(HANGOUT_ID);
            TimeInfo existingTimeInfo = new TimeInfo();
            existingTimeInfo.setPeriodGranularity("DAY");
            existingTimeInfo.setPeriodStart("2025-01-01T00:00:00Z");
            existingTimeInfo.setStartTime("2025-01-01T10:00:00Z");
            existingTimeInfo.setEndTime("2025-01-01T12:00:00Z");
            existingHangout.setTimeInput(existingTimeInfo);
            existingHangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(GROUP_ID)));

            // Same values (start time fields match)
            TimeInfo sameTimeInfo = new TimeInfo();
            sameTimeInfo.setPeriodGranularity("DAY");
            sameTimeInfo.setPeriodStart("2025-01-01T00:00:00Z");
            sameTimeInfo.setStartTime("2025-01-01T10:00:00Z");
            sameTimeInfo.setEndTime("2025-01-01T12:00:00Z");

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setTimeInfo(sameTimeInfo);

            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            GroupMembership membership = createTestMembership(GROUP_ID, USER_ID, "Test Group");
            when(groupRepository.findMembership(GROUP_ID, USER_ID)).thenReturn(Optional.of(membership));

            FuzzyTimeService.TimeConversionResult timeResult = new FuzzyTimeService.TimeConversionResult(1754603600L, 1754618000L);
            when(fuzzyTimeService.convert(sameTimeInfo)).thenReturn(timeResult);

            // When
            hangoutService.updateHangout(HANGOUT_ID, request, USER_ID);

            // Then: notification NOT called
            verify(notificationService, never()).notifyHangoutUpdated(
                anyString(), anyString(), anyList(), anyString(), anyString(), any(), any()
            );
        }

        @Test
        void updateHangout_SameLocation_NoNotification() {
            // Given: Update request has same Address as existing hangout
            Hangout existingHangout = createTestHangout(HANGOUT_ID);
            Address existingLocation = new Address();
            existingLocation.setName("Same Place");
            existingLocation.setStreetAddress("123 Main St");
            existingHangout.setLocation(existingLocation);
            existingHangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(GROUP_ID)));

            // Same values
            Address sameLocation = new Address();
            sameLocation.setName("Same Place");
            sameLocation.setStreetAddress("123 Main St");

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setLocation(sameLocation);

            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            GroupMembership membership = createTestMembership(GROUP_ID, USER_ID, "Test Group");
            when(groupRepository.findMembership(GROUP_ID, USER_ID)).thenReturn(Optional.of(membership));

            // When
            hangoutService.updateHangout(HANGOUT_ID, request, USER_ID);

            // Then: notification NOT called
            verify(notificationService, never()).notifyHangoutUpdated(
                anyString(), anyString(), anyList(), anyString(), anyString(), any(), any()
            );
        }

        @Test
        void updateHangout_OnlyEndTimeChanged_NoNotification() {
            // Given: Existing TimeInfo with start and end time
            Hangout existingHangout = createTestHangout(HANGOUT_ID);
            TimeInfo existingTimeInfo = new TimeInfo();
            existingTimeInfo.setPeriodGranularity("DAY");
            existingTimeInfo.setPeriodStart("2025-01-01T00:00:00Z");
            existingTimeInfo.setStartTime("2025-01-01T10:00:00Z");
            existingTimeInfo.setEndTime("2025-01-01T12:00:00Z");
            existingHangout.setTimeInput(existingTimeInfo);
            existingHangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(GROUP_ID)));

            // Same start time fields, different end time
            TimeInfo newTimeInfo = new TimeInfo();
            newTimeInfo.setPeriodGranularity("DAY");
            newTimeInfo.setPeriodStart("2025-01-01T00:00:00Z");
            newTimeInfo.setStartTime("2025-01-01T10:00:00Z");
            newTimeInfo.setEndTime("2025-01-01T14:00:00Z"); // Different end time

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setTimeInfo(newTimeInfo);

            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            GroupMembership membership = createTestMembership(GROUP_ID, USER_ID, "Test Group");
            when(groupRepository.findMembership(GROUP_ID, USER_ID)).thenReturn(Optional.of(membership));

            FuzzyTimeService.TimeConversionResult timeResult = new FuzzyTimeService.TimeConversionResult(1754603600L, 1754628000L);
            when(fuzzyTimeService.convert(newTimeInfo)).thenReturn(timeResult);

            // When
            hangoutService.updateHangout(HANGOUT_ID, request, USER_ID);

            // Then: notification NOT called (end time changes don't trigger notifications)
            verify(notificationService, never()).notifyHangoutUpdated(
                anyString(), anyString(), anyList(), anyString(), anyString(), any(), any()
            );
        }

        @Test
        void updateHangout_NullToValueTimeInfo_TriggersNotification() {
            // Given: Existing hangout with null timeInput
            Hangout existingHangout = createTestHangout(HANGOUT_ID);
            existingHangout.setTimeInput(null);
            existingHangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(GROUP_ID)));

            // Request sets a TimeInfo
            TimeInfo newTimeInfo = new TimeInfo();
            newTimeInfo.setPeriodGranularity("DAY");
            newTimeInfo.setPeriodStart("2025-01-01T00:00:00Z");
            newTimeInfo.setStartTime("2025-01-01T10:00:00Z");

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setTimeInfo(newTimeInfo);

            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            GroupMembership membership = createTestMembership(GROUP_ID, USER_ID, "Test Group");
            when(groupRepository.findMembership(GROUP_ID, USER_ID)).thenReturn(Optional.of(membership));

            FuzzyTimeService.TimeConversionResult timeResult = new FuzzyTimeService.TimeConversionResult(1754603600L, 1754618000L);
            when(fuzzyTimeService.convert(newTimeInfo)).thenReturn(timeResult);

            InterestLevel goingUser = new InterestLevel();
            goingUser.setUserId("user-going-1");
            goingUser.setStatus("GOING");
            HangoutDetailData detailData = HangoutDetailData.builder()
                .withHangout(existingHangout)
                .withAttendance(List.of(goingUser))
                .build();
            when(hangoutRepository.getHangoutDetailData(HANGOUT_ID)).thenReturn(detailData);

            // When
            hangoutService.updateHangout(HANGOUT_ID, request, USER_ID);

            // Then: notification sent with changeType="time"
            verify(notificationService).notifyHangoutUpdated(
                eq(HANGOUT_ID), anyString(), anyList(), eq("time"), eq(USER_ID), any(), any()
            );
        }

        @Test
        void updateHangout_PeriodGranularityChanged_TriggersNotification() {
            // Given: Existing TimeInfo with periodGranularity="DAY"
            Hangout existingHangout = createTestHangout(HANGOUT_ID);
            TimeInfo existingTimeInfo = new TimeInfo();
            existingTimeInfo.setPeriodGranularity("DAY");
            existingTimeInfo.setPeriodStart("2025-01-01T00:00:00Z");
            existingTimeInfo.setStartTime("2025-01-01T10:00:00Z");
            existingHangout.setTimeInput(existingTimeInfo);
            existingHangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(GROUP_ID)));

            // New TimeInfo with periodGranularity="WEEK"
            TimeInfo newTimeInfo = new TimeInfo();
            newTimeInfo.setPeriodGranularity("WEEK"); // Different granularity
            newTimeInfo.setPeriodStart("2025-01-01T00:00:00Z");
            newTimeInfo.setStartTime("2025-01-01T10:00:00Z");

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setTimeInfo(newTimeInfo);

            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            GroupMembership membership = createTestMembership(GROUP_ID, USER_ID, "Test Group");
            when(groupRepository.findMembership(GROUP_ID, USER_ID)).thenReturn(Optional.of(membership));

            FuzzyTimeService.TimeConversionResult timeResult = new FuzzyTimeService.TimeConversionResult(1754603600L, 1754618000L);
            when(fuzzyTimeService.convert(newTimeInfo)).thenReturn(timeResult);

            InterestLevel goingUser = new InterestLevel();
            goingUser.setUserId("user-going-1");
            goingUser.setStatus("GOING");
            HangoutDetailData detailData = HangoutDetailData.builder()
                .withHangout(existingHangout)
                .withAttendance(List.of(goingUser))
                .build();
            when(hangoutRepository.getHangoutDetailData(HANGOUT_ID)).thenReturn(detailData);

            // When
            hangoutService.updateHangout(HANGOUT_ID, request, USER_ID);

            // Then: notification sent
            verify(notificationService).notifyHangoutUpdated(
                eq(HANGOUT_ID), anyString(), anyList(), eq("time"), eq(USER_ID), any(), any()
            );
        }

        @Test
        void updateHangout_NotificationFailure_DoesNotBreakUpdate() {
            // Given: Notification service throws exception
            Hangout existingHangout = createTestHangout(HANGOUT_ID);
            existingHangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(GROUP_ID)));

            TimeInfo newTimeInfo = new TimeInfo();
            newTimeInfo.setStartTime("2025-01-02T10:00:00Z");

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setTimeInfo(newTimeInfo);

            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            GroupMembership membership = createTestMembership(GROUP_ID, USER_ID, "Test Group");
            when(groupRepository.findMembership(GROUP_ID, USER_ID)).thenReturn(Optional.of(membership));

            FuzzyTimeService.TimeConversionResult timeResult = new FuzzyTimeService.TimeConversionResult(1754603600L, 1754618000L);
            when(fuzzyTimeService.convert(newTimeInfo)).thenReturn(timeResult);

            InterestLevel goingUser = new InterestLevel();
            goingUser.setUserId("user-going-1");
            goingUser.setStatus("GOING");
            HangoutDetailData detailData = HangoutDetailData.builder()
                .withHangout(existingHangout)
                .withAttendance(List.of(goingUser))
                .build();
            when(hangoutRepository.getHangoutDetailData(HANGOUT_ID)).thenReturn(detailData);

            // Mock notification service to throw exception
            doThrow(new RuntimeException("Notification failed"))
                .when(notificationService).notifyHangoutUpdated(
                    anyString(), anyString(), anyList(), anyString(), anyString(), any(), any()
                );

            // When/Then: no exception propagated from updateHangout
            assertThatCode(() -> hangoutService.updateHangout(HANGOUT_ID, request, USER_ID))
                .doesNotThrowAnyException();

            // And hangout was still saved
            verify(hangoutRepository).createHangout(any(Hangout.class));
        }

        @Test
        void updateHangout_FiltersInterestedUsers_OnlyGOINGAndINTERESTED() {
            // Given: Attendance with GOING, INTERESTED, and NOT_GOING users
            Hangout existingHangout = createTestHangout(HANGOUT_ID);
            existingHangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(GROUP_ID)));

            TimeInfo newTimeInfo = new TimeInfo();
            newTimeInfo.setStartTime("2025-01-02T10:00:00Z");

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setTimeInfo(newTimeInfo);

            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            GroupMembership membership = createTestMembership(GROUP_ID, USER_ID, "Test Group");
            when(groupRepository.findMembership(GROUP_ID, USER_ID)).thenReturn(Optional.of(membership));

            FuzzyTimeService.TimeConversionResult timeResult = new FuzzyTimeService.TimeConversionResult(1754603600L, 1754618000L);
            when(fuzzyTimeService.convert(newTimeInfo)).thenReturn(timeResult);

            // Create attendance with different statuses
            InterestLevel goingUser = new InterestLevel();
            goingUser.setUserId("user-going");
            goingUser.setStatus("GOING");

            InterestLevel interestedUser = new InterestLevel();
            interestedUser.setUserId("user-interested");
            interestedUser.setStatus("INTERESTED");

            InterestLevel notGoingUser = new InterestLevel();
            notGoingUser.setUserId("user-not-going");
            notGoingUser.setStatus("NOT_GOING");

            HangoutDetailData detailData = HangoutDetailData.builder()
                .withHangout(existingHangout)
                .withAttendance(List.of(goingUser, interestedUser, notGoingUser))
                .build();
            when(hangoutRepository.getHangoutDetailData(HANGOUT_ID)).thenReturn(detailData);

            // When
            hangoutService.updateHangout(HANGOUT_ID, request, USER_ID);

            // Then: capture and verify interestedUserIds only contains GOING and INTERESTED
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Set<String>> userIdsCaptor = ArgumentCaptor.forClass(Set.class);
            verify(notificationService).notifyHangoutUpdated(
                eq(HANGOUT_ID), anyString(), anyList(), anyString(), eq(USER_ID), userIdsCaptor.capture(), any()
            );

            Set<String> capturedUserIds = userIdsCaptor.getValue();
            assertThat(capturedUserIds).containsExactlyInAnyOrder("user-going", "user-interested");
            assertThat(capturedUserIds).doesNotContain("user-not-going");
        }
    }
}
