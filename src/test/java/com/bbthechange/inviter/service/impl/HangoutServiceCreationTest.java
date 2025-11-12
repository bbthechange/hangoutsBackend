package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.CreateAttributeRequest;
import com.bbthechange.inviter.dto.CreateHangoutRequest;
import com.bbthechange.inviter.dto.CreatePollRequest;
import com.bbthechange.inviter.dto.HangoutDetailData;
import com.bbthechange.inviter.dto.TimeInfo;
import com.bbthechange.inviter.exception.ValidationException;
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
 * Tests for hangout creation functionality in HangoutServiceImpl.
 *
 * Coverage:
 * - Creating hangouts with fuzzy time, exact time, and no time
 * - Creating hangouts with attributes and polls
 * - Creating hangouts with mainImagePath denormalization
 * - Poll validation (title, description, options)
 * - Group last modified timestamp updates on creation
 */
class HangoutServiceCreationTest extends HangoutServiceTestBase {

    @Test
    void createHangout_WithFuzzyTime_Success() {
        // Given
        String userId = "87654321-4321-4321-4321-210987654321";
        TimeInfo timeInfo = new TimeInfo();
        timeInfo.setPeriodGranularity("evening");
        timeInfo.setPeriodStart("2025-08-05T19:00:00Z");

        CreateHangoutRequest request = new CreateHangoutRequest();
        request.setTitle("Evening Hangout");
        request.setDescription("Test fuzzy time hangout");
        request.setTimeInfo(timeInfo);
        request.setVisibility(EventVisibility.INVITE_ONLY);
        request.setAssociatedGroups(List.of("11111111-1111-1111-1111-111111111111"));

        // Mock fuzzy time service
        FuzzyTimeService.TimeConversionResult timeResult = new FuzzyTimeService.TimeConversionResult(1754557200L, 1754571600L);
        when(fuzzyTimeService.convert(timeInfo)).thenReturn(timeResult);

        // Mock group membership validation
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Test Group");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership));

        // Mock repository operations
        Hangout savedHangout = createTestHangout("33333333-3333-3333-3333-333333333333");
        savedHangout.setStartTimestamp(1754557200L);
        savedHangout.setEndTimestamp(1754571600L);
        when(hangoutRepository.createHangoutWithAttributes(any(Hangout.class), anyList(), anyList(), anyList(), anyList())).thenReturn(savedHangout);

        // When
        Hangout result = hangoutService.createHangout(request, userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStartTimestamp()).isEqualTo(1754557200L);

        // Verify fuzzy time conversion was called
        verify(fuzzyTimeService).convert(timeInfo);

        // Verify the transactional repository method was called
        verify(hangoutRepository).createHangoutWithAttributes(any(Hangout.class), anyList(), anyList(), anyList(), anyList());
    }

    @Test
    void createHangout_WithExactTime_Success() {
        // Given
        String userId = "87654321-4321-4321-4321-210987654321";
        TimeInfo timeInfo = new TimeInfo();
        timeInfo.setStartTime("2025-08-05T19:15:00Z");
        timeInfo.setEndTime("2025-08-05T21:30:00Z");

        CreateHangoutRequest request = new CreateHangoutRequest();
        request.setTitle("Exact Time Hangout");
        request.setDescription("Test exact time hangout");
        request.setTimeInfo(timeInfo);
        request.setVisibility(EventVisibility.INVITE_ONLY);
        request.setAssociatedGroups(List.of("11111111-1111-1111-1111-111111111111"));

        // Mock fuzzy time service
        FuzzyTimeService.TimeConversionResult timeResult = new FuzzyTimeService.TimeConversionResult(1754558100L, 1754566200L);
        when(fuzzyTimeService.convert(timeInfo)).thenReturn(timeResult);

        // Mock group membership validation
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Test Group");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership));

        // Mock repository operations
        Hangout savedHangout = createTestHangout("33333333-3333-3333-3333-333333333333");
        savedHangout.setStartTimestamp(1754558100L);
        savedHangout.setEndTimestamp(1754566200L);
        when(hangoutRepository.createHangoutWithAttributes(any(Hangout.class), anyList(), anyList(), anyList(), anyList())).thenReturn(savedHangout);

        // When
        Hangout result = hangoutService.createHangout(request, userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStartTimestamp()).isEqualTo(1754558100L);

        // Verify fuzzy time conversion was called
        verify(fuzzyTimeService).convert(timeInfo);

        // Verify the transactional repository method was called
        verify(hangoutRepository).createHangoutWithAttributes(any(Hangout.class), anyList(), anyList(), anyList(), anyList());
    }

    @Test
    void createHangout_WithoutTimeInput_Success() {
        // Given
        String userId = "87654321-4321-4321-4321-210987654321";

        CreateHangoutRequest request = new CreateHangoutRequest();
        request.setTitle("No Time Hangout");
        request.setDescription("Hangout without time");
        request.setTimeInfo(null);
        request.setVisibility(EventVisibility.INVITE_ONLY);
        request.setAssociatedGroups(List.of("11111111-1111-1111-1111-111111111111"));

        // Mock group membership validation
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Test Group");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership));

        // Mock repository operations
        Hangout savedHangout = createTestHangout("33333333-3333-3333-3333-333333333333");
        when(hangoutRepository.createHangoutWithAttributes(any(Hangout.class), anyList(), anyList(), anyList(), anyList())).thenReturn(savedHangout);

        // When
        Hangout result = hangoutService.createHangout(request, userId);

        // Then
        assertThat(result).isNotNull();

        // Verify fuzzy time service was not called
        verify(fuzzyTimeService, never()).convert(any());

        // Verify the transactional repository method was called
        verify(hangoutRepository).createHangoutWithAttributes(any(Hangout.class), anyList(), anyList(), anyList(), anyList());
    }

    @Test
    void createHangout_FuzzyTimeServiceThrowsException_PropagatesException() {
        // Given
        String userId = "87654321-4321-4321-4321-210987654321";
        TimeInfo invalidTimeInfo = new TimeInfo();
        invalidTimeInfo.setPeriodGranularity("invalid");
        invalidTimeInfo.setPeriodStart("2025-08-05T19:00:00Z");

        CreateHangoutRequest request = new CreateHangoutRequest();
        request.setTitle("Invalid Time Hangout");
        request.setTimeInfo(invalidTimeInfo);
        request.setVisibility(EventVisibility.INVITE_ONLY);

        // Mock fuzzy time service to throw exception
        when(fuzzyTimeService.convert(invalidTimeInfo))
            .thenThrow(new IllegalArgumentException("Unsupported periodGranularity: invalid"));

        // When/Then
        assertThatThrownBy(() -> hangoutService.createHangout(request, userId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported periodGranularity: invalid");

        // Verify no repository operations were attempted
        verify(hangoutRepository, never()).createHangout(any());
        verify(groupRepository, never()).saveHangoutPointer(any());
    }

    @Test
    void createHangout_WithAttributes_Success() {
        // Given
        String userId = "87654321-4321-4321-4321-210987654321";
        CreateHangoutRequest request = new CreateHangoutRequest();
        request.setTitle("Hangout With Attributes");
        request.setAssociatedGroups(List.of("11111111-1111-1111-1111-111111111111"));
        request.setAttributes(List.of(
            new CreateAttributeRequest("Vibe", "Chill"),
            new CreateAttributeRequest("Music", "Lo-fi")
        ));

        // Mock group membership validation
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Test Group");
        when(groupRepository.findMembership(anyString(), anyString())).thenReturn(Optional.of(membership));

        // Mock repository
        Hangout savedHangout = createTestHangout("44444444-4444-4444-4444-444444444444");
        when(hangoutRepository.createHangoutWithAttributes(any(Hangout.class), anyList(), anyList(), anyList(), anyList())).thenReturn(savedHangout);

        // When
        Hangout result = hangoutService.createHangout(request, userId);

        // Then
        assertThat(result).isNotNull();

        // Verify that the repository was called with the correct number of attributes
        verify(hangoutRepository).createHangoutWithAttributes(any(Hangout.class), anyList(), argThat(attributes ->
            attributes.size() == 2 &&
            attributes.stream().anyMatch(a -> a.getAttributeName().equals("Vibe") && a.getStringValue().equals("Chill")) &&
            attributes.stream().anyMatch(a -> a.getAttributeName().equals("Music") && a.getStringValue().equals("Lo-fi"))
        ), anyList(), anyList());
    }

    @Test
    void createHangout_WithSinglePollNoOptions_Success() {
        // Given
        String userId = "87654321-4321-4321-4321-210987654321";
        CreateHangoutRequest request = new CreateHangoutRequest();
        request.setTitle("Hangout With Poll");
        request.setAssociatedGroups(List.of("11111111-1111-1111-1111-111111111111"));
        request.setPolls(List.of(
            new CreatePollRequest("What time works best?", null, false, null)
        ));

        // Mock group membership validation
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Test Group");
        when(groupRepository.findMembership(anyString(), anyString())).thenReturn(Optional.of(membership));

        // Mock repository
        Hangout savedHangout = createTestHangout("44444444-4444-4444-4444-444444444444");
        when(hangoutRepository.createHangoutWithAttributes(any(Hangout.class), anyList(), anyList(), anyList(), anyList())).thenReturn(savedHangout);

        // When
        Hangout result = hangoutService.createHangout(request, userId);

        // Then
        assertThat(result).isNotNull();

        // Verify that the repository was called with one poll and no options
        verify(hangoutRepository).createHangoutWithAttributes(
            any(Hangout.class),
            anyList(),
            anyList(),
            argThat(polls ->
                polls.size() == 1 &&
                polls.get(0).getTitle().equals("What time works best?") &&
                polls.get(0).getDescription() == null &&
                !polls.get(0).isMultipleChoice()
            ),
            argThat(pollOptions -> pollOptions.isEmpty()) // No options
        );
    }

    @Test
    void createHangout_WithSinglePollWithOptions_Success() {
        // Given
        String userId = "87654321-4321-4321-4321-210987654321";
        CreateHangoutRequest request = new CreateHangoutRequest();
        request.setTitle("Dinner Plans");
        request.setAssociatedGroups(List.of("11111111-1111-1111-1111-111111111111"));
        request.setPolls(List.of(
            new CreatePollRequest("What should we eat?", "Vote for dinner option", false, List.of("Pizza", "Tacos", "Sushi"))
        ));

        // Mock group membership validation
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Test Group");
        when(groupRepository.findMembership(anyString(), anyString())).thenReturn(Optional.of(membership));

        // Mock repository
        Hangout savedHangout = createTestHangout("55555555-5555-5555-5555-555555555555");
        when(hangoutRepository.createHangoutWithAttributes(any(Hangout.class), anyList(), anyList(), anyList(), anyList())).thenReturn(savedHangout);

        // When
        Hangout result = hangoutService.createHangout(request, userId);

        // Then
        assertThat(result).isNotNull();

        // Verify that the repository was called with one poll and three options
        verify(hangoutRepository).createHangoutWithAttributes(
            any(Hangout.class),
            anyList(),
            anyList(),
            argThat(polls ->
                polls.size() == 1 &&
                polls.get(0).getTitle().equals("What should we eat?") &&
                polls.get(0).getDescription().equals("Vote for dinner option") &&
                !polls.get(0).isMultipleChoice()
            ),
            argThat(pollOptions ->
                pollOptions.size() == 3 &&
                pollOptions.stream().anyMatch(opt -> opt.getText().equals("Pizza")) &&
                pollOptions.stream().anyMatch(opt -> opt.getText().equals("Tacos")) &&
                pollOptions.stream().anyMatch(opt -> opt.getText().equals("Sushi"))
            )
        );
    }

    @Test
    void createHangout_WithMultiplePolls_Success() {
        // Given
        String userId = "87654321-4321-4321-4321-210987654321";
        CreateHangoutRequest request = new CreateHangoutRequest();
        request.setTitle("Group Meeting");
        request.setAssociatedGroups(List.of("11111111-1111-1111-1111-111111111111"));
        request.setPolls(List.of(
            new CreatePollRequest("What time?", null, false, List.of("Morning", "Afternoon", "Evening")),
            new CreatePollRequest("Bring food?", null, false, List.of("Yes", "No"))
        ));

        // Mock group membership validation
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Test Group");
        when(groupRepository.findMembership(anyString(), anyString())).thenReturn(Optional.of(membership));

        // Mock repository
        Hangout savedHangout = createTestHangout("66666666-6666-6666-6666-666666666666");
        when(hangoutRepository.createHangoutWithAttributes(any(Hangout.class), anyList(), anyList(), anyList(), anyList())).thenReturn(savedHangout);

        // When
        Hangout result = hangoutService.createHangout(request, userId);

        // Then
        assertThat(result).isNotNull();

        // Verify that the repository was called with two polls and five total options (3 + 2)
        verify(hangoutRepository).createHangoutWithAttributes(
            any(Hangout.class),
            anyList(),
            anyList(),
            argThat(polls ->
                polls.size() == 2 &&
                polls.stream().anyMatch(p -> p.getTitle().equals("What time?")) &&
                polls.stream().anyMatch(p -> p.getTitle().equals("Bring food?"))
            ),
            argThat(pollOptions -> pollOptions.size() == 5) // 3 options for poll 1, 2 for poll 2
        );
    }

    @Test
    void createHangout_WithPollMissingTitle_ThrowsValidationException() {
        // Given
        String userId = "87654321-4321-4321-4321-210987654321";
        CreateHangoutRequest request = new CreateHangoutRequest();
        request.setTitle("Hangout With Invalid Poll");
        request.setAssociatedGroups(List.of("11111111-1111-1111-1111-111111111111"));
        request.setPolls(List.of(
            new CreatePollRequest(null, "Description", false, null) // Missing title
        ));

        // Mock group membership validation
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Test Group");
        when(groupRepository.findMembership(anyString(), anyString())).thenReturn(Optional.of(membership));

        // When/Then
        assertThatThrownBy(() -> hangoutService.createHangout(request, userId))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Poll title is required");

        // Verify no database operations occurred
        verify(hangoutRepository, never()).createHangoutWithAttributes(any(), any(), any(), any(), any());
    }

    @Test
    void createHangout_WithPollTitleTooLong_ThrowsValidationException() {
        // Given
        String userId = "87654321-4321-4321-4321-210987654321";
        String longTitle = "a".repeat(201); // 201 characters, exceeds 200 limit

        CreateHangoutRequest request = new CreateHangoutRequest();
        request.setTitle("Hangout With Invalid Poll");
        request.setAssociatedGroups(List.of("11111111-1111-1111-1111-111111111111"));
        request.setPolls(List.of(
            new CreatePollRequest(longTitle, null, false, null)
        ));

        // Mock group membership validation
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Test Group");
        when(groupRepository.findMembership(anyString(), anyString())).thenReturn(Optional.of(membership));

        // When/Then
        assertThatThrownBy(() -> hangoutService.createHangout(request, userId))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Poll title must be between 1 and 200 characters");

        // Verify no database operations occurred
        verify(hangoutRepository, never()).createHangoutWithAttributes(any(), any(), any(), any(), any());
    }

    @Test
    void createHangout_WithPollDescriptionTooLong_ThrowsValidationException() {
        // Given
        String userId = "87654321-4321-4321-4321-210987654321";
        String longDescription = "a".repeat(1001); // 1001 characters, exceeds 1000 limit

        CreateHangoutRequest request = new CreateHangoutRequest();
        request.setTitle("Hangout With Invalid Poll");
        request.setAssociatedGroups(List.of("11111111-1111-1111-1111-111111111111"));
        request.setPolls(List.of(
            new CreatePollRequest("Valid Title", longDescription, false, null)
        ));

        // Mock group membership validation
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Test Group");
        when(groupRepository.findMembership(anyString(), anyString())).thenReturn(Optional.of(membership));

        // When/Then
        assertThatThrownBy(() -> hangoutService.createHangout(request, userId))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("Poll description cannot exceed 1000 characters");

        // Verify no database operations occurred
        verify(hangoutRepository, never()).createHangoutWithAttributes(any(), any(), any(), any(), any());
    }

    @Test
    void createHangout_WithEmptyPollOptions_FiltersOutEmpty() {
        // Given
        String userId = "87654321-4321-4321-4321-210987654321";
        CreateHangoutRequest request = new CreateHangoutRequest();
        request.setTitle("Hangout With Filtered Options");
        request.setAssociatedGroups(List.of("11111111-1111-1111-1111-111111111111"));
        request.setPolls(List.of(
            new CreatePollRequest("What to do?", null, false, java.util.Arrays.asList("Pizza", "", "  ", "Tacos", null))
        ));

        // Mock group membership validation
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Test Group");
        when(groupRepository.findMembership(anyString(), anyString())).thenReturn(Optional.of(membership));

        // Mock repository
        Hangout savedHangout = createTestHangout("77777777-7777-7777-7777-777777777777");
        when(hangoutRepository.createHangoutWithAttributes(any(Hangout.class), anyList(), anyList(), anyList(), anyList())).thenReturn(savedHangout);

        // When
        Hangout result = hangoutService.createHangout(request, userId);

        // Then
        assertThat(result).isNotNull();

        // Verify that only 2 valid options were created (Pizza and Tacos), empty/whitespace/null filtered out
        verify(hangoutRepository).createHangoutWithAttributes(
            any(Hangout.class),
            anyList(),
            anyList(),
            argThat(polls -> polls.size() == 1),
            argThat(pollOptions ->
                pollOptions.size() == 2 &&
                pollOptions.stream().anyMatch(opt -> opt.getText().equals("Pizza")) &&
                pollOptions.stream().anyMatch(opt -> opt.getText().equals("Tacos"))
            )
        );
    }

    @Test
    void createHangout_WithPollsAndAttributes_Success() {
        // Given
        String userId = "87654321-4321-4321-4321-210987654321";
        CreateHangoutRequest request = new CreateHangoutRequest();
        request.setTitle("Hangout With Both Polls And Attributes");
        request.setAssociatedGroups(List.of("11111111-1111-1111-1111-111111111111"));
        request.setAttributes(List.of(
            new CreateAttributeRequest("Vibe", "Chill"),
            new CreateAttributeRequest("Theme", "Casual")
        ));
        request.setPolls(List.of(
            new CreatePollRequest("Preferred time?", null, false, List.of("Morning", "Evening"))
        ));

        // Mock group membership validation
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Test Group");
        when(groupRepository.findMembership(anyString(), anyString())).thenReturn(Optional.of(membership));

        // Mock repository
        Hangout savedHangout = createTestHangout("88888888-8888-8888-8888-888888888888");
        when(hangoutRepository.createHangoutWithAttributes(any(Hangout.class), anyList(), anyList(), anyList(), anyList())).thenReturn(savedHangout);

        // When
        Hangout result = hangoutService.createHangout(request, userId);

        // Then
        assertThat(result).isNotNull();

        // Verify repository was called with both attributes and polls
        verify(hangoutRepository).createHangoutWithAttributes(
            any(Hangout.class),
            anyList(),
            argThat(attributes ->
                attributes.size() == 2 &&
                attributes.stream().anyMatch(a -> a.getAttributeName().equals("Vibe") && a.getStringValue().equals("Chill")) &&
                attributes.stream().anyMatch(a -> a.getAttributeName().equals("Theme") && a.getStringValue().equals("Casual"))
            ),
            argThat(polls ->
                polls.size() == 1 &&
                polls.get(0).getTitle().equals("Preferred time?")
            ),
            argThat(pollOptions ->
                pollOptions.size() == 2 &&
                pollOptions.stream().anyMatch(opt -> opt.getText().equals("Morning")) &&
                pollOptions.stream().anyMatch(opt -> opt.getText().equals("Evening"))
            )
        );
    }

    @Test
    void createHangout_DenormalizesMainImagePathToAllPointers() {
        // Given
        String userId = "87654321-4321-4321-4321-210987654321";
        CreateHangoutRequest request = new CreateHangoutRequest();
        request.setTitle("Test Hangout");
        request.setDescription("Test Description");
        request.setMainImagePath("/hangout-image.jpg");
        request.setAssociatedGroups(List.of("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222"));

        // Mock user is in both groups
        GroupMembership membership1 = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group 1");
        GroupMembership membership2 = createTestMembership("22222222-2222-2222-2222-222222222222", userId, "Group 2");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership1));
        when(groupRepository.findMembership("22222222-2222-2222-2222-222222222222", userId)).thenReturn(Optional.of(membership2));

        // Mock repository to return the hangout
        Hangout savedHangout = createTestHangout("test-hangout-id");
        savedHangout.setMainImagePath("/hangout-image.jpg");
        when(hangoutRepository.createHangoutWithAttributes(any(Hangout.class), anyList(), anyList(), anyList(), anyList()))
            .thenReturn(savedHangout);

        // When
        hangoutService.createHangout(request, userId);

        // Then - Capture the HangoutPointer list
        ArgumentCaptor<List<HangoutPointer>> pointerCaptor = ArgumentCaptor.forClass(List.class);
        verify(hangoutRepository).createHangoutWithAttributes(
            any(Hangout.class),
            pointerCaptor.capture(),
            anyList(),
            anyList(),
            anyList()
        );

        List<HangoutPointer> capturedPointers = pointerCaptor.getValue();
        assertThat(capturedPointers).hasSize(2);
        assertThat(capturedPointers.get(0).getMainImagePath()).isEqualTo("/hangout-image.jpg");
        assertThat(capturedPointers.get(1).getMainImagePath()).isEqualTo("/hangout-image.jpg");
    }

    @Test
    void createHangout_HandlesNullMainImagePath() {
        // Given
        String userId = "87654321-4321-4321-4321-210987654321";
        CreateHangoutRequest request = new CreateHangoutRequest();
        request.setTitle("Test Hangout");
        request.setDescription("Test Description");
        request.setMainImagePath(null);
        request.setAssociatedGroups(List.of("11111111-1111-1111-1111-111111111111"));

        // Mock user is in group
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group 1");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership));

        // Mock repository to return the hangout
        Hangout savedHangout = createTestHangout("test-hangout-id");
        when(hangoutRepository.createHangoutWithAttributes(any(Hangout.class), anyList(), anyList(), anyList(), anyList()))
            .thenReturn(savedHangout);

        // When
        hangoutService.createHangout(request, userId);

        // Then - Capture the HangoutPointer list
        ArgumentCaptor<List<HangoutPointer>> pointerCaptor = ArgumentCaptor.forClass(List.class);
        verify(hangoutRepository).createHangoutWithAttributes(
            any(Hangout.class),
            pointerCaptor.capture(),
            anyList(),
            anyList(),
            anyList()
        );

        List<HangoutPointer> capturedPointers = pointerCaptor.getValue();
        assertThat(capturedPointers).hasSize(1);
        assertThat(capturedPointers.get(0).getMainImagePath()).isNull();
    }

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
        ArgumentCaptor<List<String>> groupIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(groupTimestampService).updateGroupTimestamps(groupIdsCaptor.capture());

        List<String> capturedGroupIds = groupIdsCaptor.getValue();
        assertThat(capturedGroupIds).containsExactlyInAnyOrder(group1Id, group2Id);
    }
}
