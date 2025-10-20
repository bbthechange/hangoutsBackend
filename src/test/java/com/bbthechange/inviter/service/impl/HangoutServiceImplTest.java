package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.service.FuzzyTimeService;
import com.bbthechange.inviter.service.NotificationService;
import com.bbthechange.inviter.service.UserService;
import com.bbthechange.inviter.service.EventSeriesService;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.exception.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.MockedStatic;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import org.junit.jupiter.api.Nested;

/**
 * Unit tests for HangoutServiceImpl using Mockito.
 * Tests the pointer update patterns and item collection logic.
 */
@ExtendWith(MockitoExtension.class)
class HangoutServiceImplTest {
    
    @Mock
    private HangoutRepository hangoutRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private FuzzyTimeService fuzzyTimeService;

    @Mock
    private UserService userService;

    @Mock
    private EventSeriesService eventSeriesService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private PointerUpdateService pointerUpdateService;

    @InjectMocks
    private HangoutServiceImpl hangoutService;
    
    @Test
    void updateEventTitle_Success() {
        // Given
        String eventId = "12345678-1234-1234-1234-123456789012";
        String newTitle = "Updated Event Title";
        String userId = "87654321-4321-4321-4321-210987654321";
        
        Hangout hangout = createTestHangout(eventId);
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222")));
        
        HangoutDetailData data = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
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
    void associateEventWithGroups_Success() {
        // Given
        String eventId = "12345678-1234-1234-1234-123456789012";
        List<String> groupIds = List.of("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222");
        String userId = "87654321-4321-4321-4321-210987654321";
        
        // Use hangout with timeInfo to test timeInfo propagation to pointer
        Hangout hangout = createTestHangoutWithTimeInput(eventId);
        hangout.setStartTimestamp(1754558100L); // Set timestamps that would come from fuzzy time service
        hangout.setEndTimestamp(1754566200L);
        // Set up hangout so user can edit it (they're admin in one of the groups they want to associate)
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111")));
        
        HangoutDetailData data = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(data);
        
        // Mock fuzzy time service conversion
        FuzzyTimeService.TimeConversionResult timeResult = new FuzzyTimeService.TimeConversionResult(1754558100L, 1754566200L);
        when(fuzzyTimeService.convert(any(TimeInfo.class))).thenReturn(timeResult);
        
        // Mock authorization - user is admin in first group and member of second
        GroupMembership adminMembership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group One");
        adminMembership.setRole(GroupRole.ADMIN);
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(
            Optional.of(adminMembership));
        when(groupRepository.findMembership("22222222-2222-2222-2222-222222222222", userId)).thenReturn(
            Optional.of(createTestMembership("22222222-2222-2222-2222-222222222222", userId, "Group Two")));
        
        when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(hangout);
        
        // When
        assertThatCode(() -> hangoutService.associateEventWithGroups(eventId, groupIds, userId))
            .doesNotThrowAnyException();
        
        // Then
        verify(hangoutRepository).createHangout(any(Hangout.class)); // Update canonical record
        verify(groupRepository, times(2)).saveHangoutPointer(any(HangoutPointer.class)); // Create pointers
        
        // Verify that pointers are created with timeInfo properly set
        verify(groupRepository, times(2)).saveHangoutPointer(argThat(pointer -> 
            pointer.getTimeInput() != null && 
            pointer.getStartTimestamp() != null &&
            pointer.getStartTimestamp().equals(1754558100L)
        ));
    }
    
    @Test
    void associateEventWithGroups_UserNotInGroup_ThrowsException() {
        // Given
        String eventId = "12345678-1234-1234-1234-123456789012";
        List<String> groupIds = List.of("11111111-1111-1111-1111-111111111111");
        String userId = "87654321-4321-4321-4321-210987654321";
        
        Hangout hangout = createTestHangout(eventId);
        // Set up hangout so user can edit it (they're admin in a different group)
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("22222222-2222-2222-2222-222222222222")));
        
        HangoutDetailData data = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(data);
        
        // User is admin in existing associated group (so they can edit hangout)
        GroupMembership adminMembership = createTestMembership("22222222-2222-2222-2222-222222222222", userId, "Existing Group");
        adminMembership.setRole(GroupRole.ADMIN);
        when(groupRepository.findMembership("22222222-2222-2222-2222-222222222222", userId)).thenReturn(Optional.of(adminMembership));
        
        // But user is NOT in the group they're trying to associate with
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> hangoutService.associateEventWithGroups(eventId, groupIds, userId))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("User not in group");
            
        verify(hangoutRepository, never()).createHangout(any());
        verify(groupRepository, never()).saveHangoutPointer(any());
    }
    
    @Test
    void disassociateEventFromGroups_Success() {
        // Given
        String eventId = "12345678-1234-1234-1234-123456789012";
        List<String> groupIds = List.of("11111111-1111-1111-1111-111111111111");
        String userId = "87654321-4321-4321-4321-210987654321";
        
        Hangout hangout = createTestHangout(eventId);
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222")));
        
        HangoutDetailData data = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(data);
        
        // Mock authorization - user is admin in group-1
        GroupMembership adminMembership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group One");
        adminMembership.setRole(GroupRole.ADMIN);
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(adminMembership));
        
        when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(hangout);
        doNothing().when(groupRepository).deleteHangoutPointer(anyString(), anyString());
        
        // When
        assertThatCode(() -> hangoutService.disassociateEventFromGroups(eventId, groupIds, userId))
            .doesNotThrowAnyException();
        
        // Then
        verify(hangoutRepository).createHangout(any(Hangout.class)); // Update canonical record
        verify(groupRepository).deleteHangoutPointer("11111111-1111-1111-1111-111111111111", eventId); // Remove pointer
    }
    
    // Removed deprecated Event authorization test methods - replaced with Hangout versions
    
    // Helper methods for test data creation
    private Event createTestEvent(String eventId) {
        Event event = new Event("Test Event", "Description", 
            java.time.LocalDateTime.now(), java.time.LocalDateTime.now().plusHours(2),
            null, EventVisibility.INVITE_ONLY, null);
        // Set the ID using reflection or a test-friendly constructor
        return event;
    }
    
    private Hangout createTestHangout(String hangoutId) {
        Hangout hangout = new Hangout("Test Hangout", "Description", 
            java.time.LocalDateTime.now(), java.time.LocalDateTime.now().plusHours(2),
            null, EventVisibility.INVITE_ONLY, null);
        hangout.setHangoutId(hangoutId);
        return hangout;
    }
    
    private GroupMembership createTestMembership(String groupId, String userId, String groupName) {
        // For tests, create membership without going through the constructor that validates UUIDs
        GroupMembership membership = new GroupMembership();
        membership.setGroupId(groupId);
        membership.setUserId(userId);
        membership.setGroupName(groupName);
        membership.setRole(GroupRole.MEMBER);
        // Set keys directly for test purposes
        membership.setPk("GROUP#" + groupId);
        membership.setSk("USER#" + userId);
        membership.setGsi1pk("USER#" + userId);
        membership.setGsi1sk("GROUP#" + groupId);
        return membership;
    }
    
    private Poll createTestPoll() {
        // Create Poll without constructor validation for tests
        Poll poll = new Poll();
        poll.setEventId("12345678-1234-1234-1234-123456789012");
        poll.setPollId("11111111-1111-1111-1111-111111111111");
        poll.setTitle("Test Poll");
        poll.setDescription("Description");
        poll.setMultipleChoice(false);
        poll.setPk("EVENT#12345678-1234-1234-1234-123456789012");
        poll.setSk("POLL#11111111-1111-1111-1111-111111111111");
        return poll;
    }
    
    private Car createTestCar() {
        // Create Car without constructor validation for tests
        Car car = new Car();
        car.setEventId("12345678-1234-1234-1234-123456789012");
        car.setDriverId("87654321-4321-4321-4321-210987654321");
        car.setDriverName("John Doe");
        car.setTotalCapacity(4);
        car.setAvailableSeats(4);
        car.setPk("EVENT#12345678-1234-1234-1234-123456789012");
        car.setSk("CAR#87654321-4321-4321-4321-210987654321");
        return car;
    }
    
    private Vote createTestVote() {
        // Create Vote without constructor validation for tests
        Vote vote = new Vote();
        vote.setEventId("12345678-1234-1234-1234-123456789012");
        vote.setPollId("11111111-1111-1111-1111-111111111111");
        vote.setOptionId("22222222-2222-2222-2222-222222222222");
        vote.setUserId("87654321-4321-4321-4321-210987654321");
        vote.setVoteType("YES");
        vote.setPk("EVENT#12345678-1234-1234-1234-123456789012");
        vote.setSk("POLL#11111111-1111-1111-1111-111111111111#VOTE#87654321-4321-4321-4321-210987654321#OPTION#22222222-2222-2222-2222-222222222222");
        return vote;
    }
    
    private InterestLevel createTestInterestLevel() {
        // Create InterestLevel without constructor validation for tests
        InterestLevel interest = new InterestLevel();
        interest.setEventId("12345678-1234-1234-1234-123456789012");
        interest.setUserId("87654321-4321-4321-4321-210987654321");
        interest.setUserName("John Doe");
        interest.setStatus("GOING");
        interest.setPk("EVENT#12345678-1234-1234-1234-123456789012");
        interest.setSk("ATTENDANCE#87654321-4321-4321-4321-210987654321");
        return interest;
    }
    
    // ===== Integration Tests for Fuzzy Time Functionality =====
    
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
        when(hangoutRepository.createHangoutWithAttributes(any(Hangout.class), anyList(), anyList())).thenReturn(savedHangout);

        // When
        Hangout result = hangoutService.createHangout(request, userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStartTimestamp()).isEqualTo(1754557200L);

        // Verify fuzzy time conversion was called
        verify(fuzzyTimeService).convert(timeInfo);

        // Verify the transactional repository method was called
        verify(hangoutRepository).createHangoutWithAttributes(any(Hangout.class), anyList(), anyList());
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
        when(hangoutRepository.createHangoutWithAttributes(any(Hangout.class), anyList(), anyList())).thenReturn(savedHangout);

        // When
        Hangout result = hangoutService.createHangout(request, userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStartTimestamp()).isEqualTo(1754558100L);

        // Verify fuzzy time conversion was called
        verify(fuzzyTimeService).convert(timeInfo);

        // Verify the transactional repository method was called
        verify(hangoutRepository).createHangoutWithAttributes(any(Hangout.class), anyList(), anyList());
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
        when(hangoutRepository.createHangoutWithAttributes(any(Hangout.class), anyList(), anyList())).thenReturn(savedHangout);

        // When
        Hangout result = hangoutService.createHangout(request, userId);

        // Then
        assertThat(result).isNotNull();

        // Verify fuzzy time service was not called
        verify(fuzzyTimeService, never()).convert(any());

        // Verify the transactional repository method was called
        verify(hangoutRepository).createHangoutWithAttributes(any(Hangout.class), anyList(), anyList());
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
    void associateEventWithGroups_WithTimestamps_CreatesPointersWithGSIFields() {
        // Given
        String eventId = "12345678-1234-1234-1234-123456789012";
        List<String> groupIds = List.of("11111111-1111-1111-1111-111111111111");
        String userId = "87654321-4321-4321-4321-210987654321";
        
        // Use hangout with timeInput so GSI fields get set properly
        Hangout hangout = createTestHangoutWithTimeInput(eventId);
        hangout.setStartTimestamp(1754557200L); // Set timestamp that would come from fuzzy service
        hangout.setEndTimestamp(1754571600L);
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("22222222-2222-2222-2222-222222222222")));
        
        HangoutDetailData data = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(data);
        
        // Mock fuzzy time service conversion
        FuzzyTimeService.TimeConversionResult timeResult = new FuzzyTimeService.TimeConversionResult(1754557200L, 1754571600L);
        when(fuzzyTimeService.convert(any(TimeInfo.class))).thenReturn(timeResult);
        
        // Mock authorization
        GroupMembership adminMembership = createTestMembership("22222222-2222-2222-2222-222222222222", userId, "Existing Group");
        adminMembership.setRole(GroupRole.ADMIN);
        when(groupRepository.findMembership("22222222-2222-2222-2222-222222222222", userId)).thenReturn(Optional.of(adminMembership));
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(
            Optional.of(createTestMembership("11111111-1111-1111-1111-111111111111", userId, "New Group")));
        
        when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(hangout);
        
        // When
        assertThatCode(() -> hangoutService.associateEventWithGroups(eventId, groupIds, userId))
            .doesNotThrowAnyException();
        
        // Then
        verify(groupRepository).saveHangoutPointer(argThat(pointer ->
            pointer.getGsi1pk().equals("GROUP#11111111-1111-1111-1111-111111111111") &&
            pointer.getStartTimestamp().equals(1754557200L) &&
            pointer.getTimeInput() != null // Verify timeInput was set
        ));
    }
    
    private java.util.HashMap<String, String> createTimeInputMap(String key1, String value1, String key2, String value2) {
        java.util.HashMap<String, String> timeInput = new java.util.HashMap<>();
        timeInput.put(key1, value1);
        timeInput.put(key2, value2);
        return timeInput;
    }
    
    @Test 
    void getHangoutsForUser_WithGroupAndUserPointers_Success() {
        // Given
        String userId = "87654321-4321-4321-4321-210987654321";
        
        // Mock user's groups
        List<GroupMembership> userGroups = List.of(
            createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group One"),
            createTestMembership("22222222-2222-2222-2222-222222222222", userId, "Group Two")
        );
        when(groupRepository.findGroupsByUserId(userId)).thenReturn(userGroups);
        
        // Create mock hangout pointers from both groups and user
        HangoutPointer groupPointer1 = createTestHangoutPointer("11111111-1111-1111-1111-111111111111", "hangout-1");
        HangoutPointer groupPointer2 = createTestHangoutPointer("22222222-2222-2222-2222-222222222222", "hangout-2"); 
        HangoutPointer userPointer = createTestHangoutPointer(userId, "hangout-3");
        
        // Mock GSI queries
        when(hangoutRepository.findUpcomingHangoutsForParticipant("USER#" + userId, "T#"))
            .thenReturn(List.of(userPointer));
        when(hangoutRepository.findUpcomingHangoutsForParticipant("GROUP#11111111-1111-1111-1111-111111111111", "T#"))
            .thenReturn(List.of(groupPointer1));
        when(hangoutRepository.findUpcomingHangoutsForParticipant("GROUP#22222222-2222-2222-2222-222222222222", "T#"))
            .thenReturn(List.of(groupPointer2));
            
        // No need to mock findHangoutById - convertToSummaryDTO now uses denormalized pointer data
        
        // When
        List<HangoutSummaryDTO> result = hangoutService.getHangoutsForUser(userId);
        
        // Then
        assertThat(result).hasSize(3);
        assertThat(result.stream().map(HangoutSummaryDTO::getHangoutId))
            .containsExactlyInAnyOrder("hangout-1", "hangout-2", "hangout-3");
            
        // Verify GSI queries were made for user and both groups
        verify(hangoutRepository).findUpcomingHangoutsForParticipant("USER#" + userId, "T#");
        verify(hangoutRepository).findUpcomingHangoutsForParticipant("GROUP#11111111-1111-1111-1111-111111111111", "T#");
        verify(hangoutRepository).findUpcomingHangoutsForParticipant("GROUP#22222222-2222-2222-2222-222222222222", "T#");
    }
    
    @Test
    void getHangoutsForUser_NoGroups_OnlyUserPointers() {
        // Given
        String userId = "87654321-4321-4321-4321-210987654321";
        
        // Mock user has no groups
        when(groupRepository.findGroupsByUserId(userId)).thenReturn(List.of());
        
        // Create mock user hangout pointer
        HangoutPointer userPointer = createTestHangoutPointer(userId, "hangout-1");
        when(hangoutRepository.findUpcomingHangoutsForParticipant("USER#" + userId, "T#"))
            .thenReturn(List.of(userPointer));
            
        
        // When
        List<HangoutSummaryDTO> result = hangoutService.getHangoutsForUser(userId);
        
        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getHangoutId()).isEqualTo("hangout-1");
        
        // Verify only user query was made
        verify(hangoutRepository).findUpcomingHangoutsForParticipant("USER#" + userId, "T#");
        verify(hangoutRepository, never()).findUpcomingHangoutsForParticipant(startsWith("GROUP#"), anyString());
    }
    
    @Test
    void getHangoutsForUser_GSIQueryFails_ContinuesWithOtherQueries() {
        // Given
        String userId = "87654321-4321-4321-4321-210987654321";
        
        // Mock user's groups
        List<GroupMembership> userGroups = List.of(
            createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group One")
        );
        when(groupRepository.findGroupsByUserId(userId)).thenReturn(userGroups);
        
        // Mock one GSI query fails, other succeeds
        when(hangoutRepository.findUpcomingHangoutsForParticipant("USER#" + userId, "T#"))
            .thenThrow(new RuntimeException("GSI query failed"));
        when(hangoutRepository.findUpcomingHangoutsForParticipant("GROUP#11111111-1111-1111-1111-111111111111", "T#"))
            .thenReturn(List.of(createTestHangoutPointer("11111111-1111-1111-1111-111111111111", "hangout-1")));
            
        
        // When
        List<HangoutSummaryDTO> result = hangoutService.getHangoutsForUser(userId);
        
        // Then - should still return results from successful queries
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getHangoutId()).isEqualTo("hangout-1");
    }
    
    @Test
    void formatTimeInfoForResponse_WithUnixTimestamps_ConvertsToISO() {
        // This tests the private method through the public getHangoutDetail method
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";
        
        Hangout hangout = createTestHangout(hangoutId);
        hangout.setVisibility(EventVisibility.PUBLIC);
        
        // Set timeInput with Unix timestamps for fuzzy time (only periodGranularity and periodStart)
        TimeInfo timeInput = new TimeInfo();
        timeInput.setPeriodGranularity("evening");
        timeInput.setPeriodStart("1754557200"); // Unix timestamp for 2025-08-05T19:00:00Z  
        hangout.setTimeInput(timeInput);
        
        HangoutDetailData data = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
        
        // When
        HangoutDetailDTO result = hangoutService.getHangoutDetail(hangoutId, userId);
        
        // Then - fuzzy time only returns periodGranularity and periodStart
        TimeInfo timeInfo = result.getHangout().getTimeInput();
        assertThat(timeInfo.getPeriodStart()).isEqualTo("2025-08-07T09:00:00Z");
        assertThat(timeInfo.getPeriodGranularity()).isEqualTo("evening");
        assertThat(timeInfo.getStartTime()).isNull(); // Not returned for fuzzy time
    }
    
    // Helper methods for new tests
    private HangoutPointer createTestHangoutPointer(String entityId, String hangoutId) {
        // Create pointer manually for tests to support both group and user entities
        HangoutPointer pointer = new HangoutPointer();
        pointer.setGroupId(entityId); // For tests, this can be either group or user ID
        pointer.setHangoutId(hangoutId);
        pointer.setTitle("Test Hangout " + hangoutId);
        pointer.setStatus("ACTIVE");
        Address testLocation = new Address();
        testLocation.setName("Test Location");
        pointer.setLocation(testLocation);
        pointer.setParticipantCount(5);
        
        // Set keys manually for test
        if (entityId.contains("1111") || entityId.contains("2222")) {
            // This is a group ID
            pointer.setPk("GROUP#" + entityId);
        } else {
            // This is a user ID
            pointer.setPk("USER#" + entityId);
        }
        pointer.setSk("HANGOUT#" + hangoutId);
        return pointer;
    }
    
    private Hangout createTestHangoutWithTimeInput(String hangoutId) {
        Hangout hangout = createTestHangout(hangoutId);
        TimeInfo timeInfo = new TimeInfo();
        timeInfo.setStartTime("1754558100"); // Unix timestamp
        timeInfo.setEndTime("1754566200");
        hangout.setTimeInput(timeInfo);
        return hangout;
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
        when(hangoutRepository.createHangoutWithAttributes(any(Hangout.class), anyList(), anyList())).thenReturn(savedHangout);

        // When
        Hangout result = hangoutService.createHangout(request, userId);

        // Then
        assertThat(result).isNotNull();

        // Verify that the repository was called with the correct number of attributes
        verify(hangoutRepository).createHangoutWithAttributes(any(Hangout.class), anyList(), argThat(attributes -> 
            attributes.size() == 2 &&
            attributes.stream().anyMatch(a -> a.getAttributeName().equals("Vibe") && a.getStringValue().equals("Chill")) &&
            attributes.stream().anyMatch(a -> a.getAttributeName().equals("Music") && a.getStringValue().equals("Lo-fi"))
        ));
    }
    
    @Test
    void hangoutSummaryDTO_Constructor_SetsTimeInfoFromPointer() {
        // Given
        String groupId = "11111111-1111-1111-1111-111111111111";
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String title = "Test Hangout";
        
        TimeInfo timeInfo = new TimeInfo();
        timeInfo.setPeriodGranularity("evening");
        timeInfo.setPeriodStart("2025-08-05T19:00:00Z");
        
        HangoutPointer pointer = new HangoutPointer(groupId, hangoutId, title);
        pointer.setStatus("ACTIVE");
        Address testLocation = new Address();
        testLocation.setName("Test Location");
        pointer.setLocation(testLocation);
        pointer.setParticipantCount(5);
        pointer.setTimeInput(timeInfo);

        // When
        String testUserId = "user-123";
        HangoutSummaryDTO summary = new HangoutSummaryDTO(pointer, testUserId);

        // Then
        assertThat(summary.getHangoutId()).isEqualTo(hangoutId);
        assertThat(summary.getTitle()).isEqualTo(title);
        assertThat(summary.getStatus()).isEqualTo("ACTIVE");
        assertThat(summary.getLocation().getName()).isEqualTo("Test Location");
        assertThat(summary.getParticipantCount()).isEqualTo(5);
        
        // Verify timeInfo is properly set from pointer's timeInput
        assertThat(summary.getTimeInfo()).isNotNull();
        assertThat(summary.getTimeInfo().getPeriodGranularity()).isEqualTo("evening");
        assertThat(summary.getTimeInfo().getPeriodStart()).isEqualTo("2025-08-05T19:00:00Z");
    }

    @Test
    void setUserInterest_NewInterest_Success() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";
        SetInterestRequest request = new SetInterestRequest("GOING", "Excited to attend!");

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setVisibility(EventVisibility.PUBLIC);
        hangout.setAssociatedGroups(List.of("11111111-1111-1111-1111-111111111111"));

        HangoutDetailData data = new HangoutDetailData(
            hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);

        User user = createTestUser(userId);
        when(userService.getUserById(UUID.fromString(userId))).thenReturn(Optional.of(user));

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

        HangoutDetailData data = new HangoutDetailData(
            hangout, List.of(), List.of(), List.of(), List.of(), List.of(existingInterest), List.of(), List.of()
        );
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);

        User user = createTestUser(userId);
        when(userService.getUserById(UUID.fromString(userId))).thenReturn(Optional.of(user));

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

        HangoutDetailData data = new HangoutDetailData(
            hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
        when(userService.getUserById(UUID.fromString(userId))).thenReturn(Optional.empty());

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

        HangoutDetailData data = new HangoutDetailData(
            null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
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

        HangoutDetailData data = new HangoutDetailData(
            hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
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

        HangoutDetailData data = new HangoutDetailData(
            hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);

        User user = createTestUser(userId);
        user.setMainImagePath(userImagePath); // User has a profile image
        when(userService.getUserById(UUID.fromString(userId))).thenReturn(Optional.of(user));

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

        HangoutDetailData data = new HangoutDetailData(
            hangout, List.of(), List.of(), List.of(), List.of(), List.of(existingInterest), List.of(), List.of()
        );
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
        HangoutDetailData data = new HangoutDetailData(
            hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
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

        HangoutDetailData data = new HangoutDetailData(
            null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
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

        HangoutDetailData data = new HangoutDetailData(
            hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> hangoutService.removeUserInterest(hangoutId, userId))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("Cannot remove interest for this hangout");

        verify(hangoutRepository, never()).deleteInterestLevel(anyString(), anyString());
        verify(groupRepository, never()).atomicallyUpdateParticipantCount(anyString(), anyString(), anyInt());
    }

    // Helper method for User creation
    private User createTestUser(String userId) {
        User user = new User();
        user.setDisplayName("John Doe");
        user.setUsername("johndoe");
        return user;
    }

    // ============================================================================
    // NEEDS RIDE INTEGRATION TESTS
    // ============================================================================

    @Test
    void getHangoutDetail_WithNeedsRideData_IncludesNeedsRideInDTO() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String requesterUserId = UUID.randomUUID().toString();
        
        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setTitle("Test Hangout");
        hangout.setVisibility(EventVisibility.PUBLIC);
        
        NeedsRide needsRide1 = new NeedsRide(hangoutId, userId, "Need a ride from downtown");
        NeedsRide needsRide2 = new NeedsRide(hangoutId, requesterUserId, "Need a ride from airport");
        List<NeedsRide> needsRideList = List.of(needsRide1, needsRide2);
        
        HangoutDetailData data = new HangoutDetailData(
            hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), needsRideList
        );
        
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
        when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());

        // When
        HangoutDetailDTO result = hangoutService.getHangoutDetail(hangoutId, requesterUserId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getHangout()).isEqualTo(hangout);
        assertThat(result.getNeedsRide()).hasSize(2);
        assertThat(result.getNeedsRide().get(0).getUserId()).isEqualTo(userId);
        assertThat(result.getNeedsRide().get(0).getNotes()).isEqualTo("Need a ride from downtown");
        assertThat(result.getNeedsRide().get(1).getUserId()).isEqualTo(requesterUserId);
        assertThat(result.getNeedsRide().get(1).getNotes()).isEqualTo("Need a ride from airport");
        
        verify(hangoutRepository).getHangoutDetailData(hangoutId);
        verify(hangoutRepository).findAttributesByHangoutId(hangoutId);
    }

    @Test
    void getHangoutDetail_WithEmptyNeedsRideData_ReturnsEmptyNeedsRideList() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String requesterUserId = UUID.randomUUID().toString();
        
        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setTitle("Test Hangout");
        hangout.setVisibility(EventVisibility.PUBLIC);
        
        HangoutDetailData data = new HangoutDetailData(
            hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
        
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
        when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());

        // When
        HangoutDetailDTO result = hangoutService.getHangoutDetail(hangoutId, requesterUserId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getHangout()).isEqualTo(hangout);
        assertThat(result.getNeedsRide()).isEmpty();
        
        verify(hangoutRepository).getHangoutDetailData(hangoutId);
    }

    @Test
    void getHangoutDetail_WithNullNotesInNeedsRide_HandlesNullGracefully() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String requesterUserId = UUID.randomUUID().toString();
        
        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setTitle("Test Hangout");
        hangout.setVisibility(EventVisibility.PUBLIC);
        
        NeedsRide needsRideWithNull = new NeedsRide(hangoutId, userId, null);
        List<NeedsRide> needsRideList = List.of(needsRideWithNull);
        
        HangoutDetailData data = new HangoutDetailData(
            hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), needsRideList
        );
        
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
        when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());

        // When
        HangoutDetailDTO result = hangoutService.getHangoutDetail(hangoutId, requesterUserId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNeedsRide()).hasSize(1);
        assertThat(result.getNeedsRide().get(0).getUserId()).isEqualTo(userId);
        assertThat(result.getNeedsRide().get(0).getNotes()).isNull();
        
        verify(hangoutRepository).getHangoutDetailData(hangoutId);
    }

    @Test
    void getHangoutDetail_WithMixedNeedsRideData_TransformsAllCorrectly() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String user1Id = UUID.randomUUID().toString();
        String user2Id = UUID.randomUUID().toString();
        String user3Id = UUID.randomUUID().toString();
        String requesterUserId = UUID.randomUUID().toString();
        
        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setTitle("Test Hangout");
        hangout.setVisibility(EventVisibility.PUBLIC);
        
        NeedsRide needsRide1 = new NeedsRide(hangoutId, user1Id, "Need a ride from downtown");
        NeedsRide needsRide2 = new NeedsRide(hangoutId, user2Id, null);
        NeedsRide needsRide3 = new NeedsRide(hangoutId, user3Id, "");
        List<NeedsRide> needsRideList = List.of(needsRide1, needsRide2, needsRide3);
        
        HangoutDetailData data = new HangoutDetailData(
            hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), needsRideList
        );
        
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
        when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());

        // When
        HangoutDetailDTO result = hangoutService.getHangoutDetail(hangoutId, requesterUserId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNeedsRide()).hasSize(3);
        
        // Verify each needs ride DTO is correctly transformed
        assertThat(result.getNeedsRide().get(0).getUserId()).isEqualTo(user1Id);
        assertThat(result.getNeedsRide().get(0).getNotes()).isEqualTo("Need a ride from downtown");
        
        assertThat(result.getNeedsRide().get(1).getUserId()).isEqualTo(user2Id);
        assertThat(result.getNeedsRide().get(1).getNotes()).isNull();
        
        assertThat(result.getNeedsRide().get(2).getUserId()).isEqualTo(user3Id);
        assertThat(result.getNeedsRide().get(2).getNotes()).isEqualTo("");
        
        verify(hangoutRepository).getHangoutDetailData(hangoutId);
    }

    @Test
    void getHangoutDetail_WithNonExistentHangout_ThrowsException() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String requesterUserId = UUID.randomUUID().toString();
        
        // Return data with null hangout (simulating not found)
        HangoutDetailData data = new HangoutDetailData(
            null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
        
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);

        // When/Then - Service should throw exception for null hangout (no data exposure)
        assertThatThrownBy(() -> hangoutService.getHangoutDetail(hangoutId, requesterUserId))
                .isInstanceOf(RuntimeException.class); // Could be NPE or other runtime exception
        
        verify(hangoutRepository).getHangoutDetailData(hangoutId);
    }

    @Test
    void getHangoutDetail_WithComplexData_IncludesNeedsRideAlongsideOtherData() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String requesterUserId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        
        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setTitle("Complex Hangout");
        hangout.setVisibility(EventVisibility.PUBLIC);
        
        // Set up various data types
        List<Car> cars = List.of(new Car(hangoutId, requesterUserId, "Test Driver", 4));
        List<CarRider> carRiders = List.of(new CarRider(hangoutId, requesterUserId, userId, "Test Rider"));
        List<NeedsRide> needsRideList = List.of(new NeedsRide(hangoutId, userId, "Still need a ride"));
        List<InterestLevel> attendance = List.of();
        List<Vote> votes = List.of();
        
        HangoutDetailData data = new HangoutDetailData(
            hangout, List.of(), List.of(), cars, votes, attendance, carRiders, needsRideList
        );
        
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
        when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());

        // When
        HangoutDetailDTO result = hangoutService.getHangoutDetail(hangoutId, requesterUserId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getHangout()).isEqualTo(hangout);
        
        // Verify all data types are included
        assertThat(result.getCars()).hasSize(1);
        assertThat(result.getCarRiders()).hasSize(1);
        assertThat(result.getNeedsRide()).hasSize(1);
        assertThat(result.getAttendance()).isEmpty();
        assertThat(result.getVotes()).isEmpty();
        
        // Verify specific needsRide data
        assertThat(result.getNeedsRide().get(0).getUserId()).isEqualTo(userId);
        assertThat(result.getNeedsRide().get(0).getNotes()).isEqualTo("Still need a ride");
        
        verify(hangoutRepository).getHangoutDetailData(hangoutId);
    }

    // ============================================================================
    // SERIES INTEGRATION TESTS - Test Plan 3
    // ============================================================================

    // Modified updateHangout() Tests

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

    // Modified deleteHangout() Tests

    @Test
    void deleteHangout_WithHangoutInSeries_UsesSeriesDeletion() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";
        String seriesId = "series-123";
        
        Hangout hangout = createTestHangout(hangoutId);
        hangout.setSeriesId(seriesId);
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111")));
        
        // Mock authorization
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Test Group");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership));
        
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        doNothing().when(eventSeriesService).removeHangoutFromSeries(hangoutId);
        
        // When
        hangoutService.deleteHangout(hangoutId, userId);
        
        // Then
        // Verify series removal is called
        verify(eventSeriesService).removeHangoutFromSeries(hangoutId);
        
        // Verify standard deletion logic is NOT executed
        verify(groupRepository, never()).deleteHangoutPointer(anyString(), anyString());
        verify(hangoutRepository, never()).deleteHangout(anyString());
    }

    @Test
    void deleteHangout_WithStandaloneHangout_UsesStandardDeletion() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";
        
        Hangout hangout = createTestHangout(hangoutId);
        hangout.setSeriesId(null); // Standalone hangout
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111")));
        
        // Mock authorization
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Test Group");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership));
        
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        doNothing().when(groupRepository).deleteHangoutPointer(anyString(), anyString());
        doNothing().when(hangoutRepository).deleteHangout(hangoutId);
        
        // When
        hangoutService.deleteHangout(hangoutId, userId);
        
        // Then
        // Verify standard deletion logic executes (pointers deleted, then hangout)
        verify(groupRepository).deleteHangoutPointer("11111111-1111-1111-1111-111111111111", hangoutId);
        verify(hangoutRepository).deleteHangout(hangoutId);
        
        // Verify series removal is NOT called
        verify(eventSeriesService, never()).removeHangoutFromSeries(anyString());
    }

    @Test
    void deleteHangout_WithSeriesRemovalFailure_ThrowsRepositoryException() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";
        String seriesId = "series-123";
        
        Hangout hangout = createTestHangout(hangoutId);
        hangout.setSeriesId(seriesId);
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111")));
        
        // Mock authorization
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Test Group");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership));
        
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        
        // Mock series service to throw exception
        RuntimeException originalException = new RuntimeException("Series removal failed");
        doThrow(originalException).when(eventSeriesService).removeHangoutFromSeries(hangoutId);
        
        // When & Then
        assertThatThrownBy(() -> hangoutService.deleteHangout(hangoutId, userId))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("Failed to remove hangout from series during deletion")
            .hasCause(originalException);
        
        // Verify series removal was attempted
        verify(eventSeriesService).removeHangoutFromSeries(hangoutId);
        
        // Verify no partial deletion occurs
        verify(groupRepository, never()).deleteHangoutPointer(anyString(), anyString());
        verify(hangoutRepository, never()).deleteHangout(anyString());
    }

    @Test
    void deleteHangout_MaintainsAuthorizationChecks() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";
        String seriesId = "series-123";

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setSeriesId(seriesId);
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111")));

        // Mock authorization failure - user is not in group
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.empty());

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));

        // When & Then
        assertThatThrownBy(() -> hangoutService.deleteHangout(hangoutId, userId))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("Cannot delete hangout");

        // Verify no deletion operations are attempted
        verify(eventSeriesService, never()).removeHangoutFromSeries(anyString());
        verify(groupRepository, never()).deleteHangoutPointer(anyString(), anyString());
        verify(hangoutRepository, never()).deleteHangout(anyString());
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
        when(hangoutRepository.createHangoutWithAttributes(any(Hangout.class), anyList(), anyList()))
            .thenReturn(savedHangout);

        // When
        hangoutService.createHangout(request, userId);

        // Then - Capture the HangoutPointer list
        org.mockito.ArgumentCaptor<List<HangoutPointer>> pointerCaptor =
            org.mockito.ArgumentCaptor.forClass(List.class);
        verify(hangoutRepository).createHangoutWithAttributes(
            any(Hangout.class),
            pointerCaptor.capture(),
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
        when(hangoutRepository.createHangoutWithAttributes(any(Hangout.class), anyList(), anyList()))
            .thenReturn(savedHangout);

        // When
        hangoutService.createHangout(request, userId);

        // Then - Capture the HangoutPointer list
        org.mockito.ArgumentCaptor<List<HangoutPointer>> pointerCaptor =
            org.mockito.ArgumentCaptor.forClass(List.class);
        verify(hangoutRepository).createHangoutWithAttributes(
            any(Hangout.class),
            pointerCaptor.capture(),
            anyList()
        );

        List<HangoutPointer> capturedPointers = pointerCaptor.getValue();
        assertThat(capturedPointers).hasSize(1);
        assertThat(capturedPointers.get(0).getMainImagePath()).isNull();
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

    // ============================================================================
    // TEST PLAN PHASE 3: POINTER UPDATE TESTS - Group 1
    // ============================================================================

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

    // ============================================================================
    // TEST PLAN PHASE 3: POINTER UPDATE TESTS - Group 2
    // ============================================================================

    @Test
    void createAttribute_WithValidAttribute_ShouldUpdateAllPointers() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";

        CreateAttributeRequest request = new CreateAttributeRequest();
        request.setAttributeName("dress_code");
        request.setStringValue("casual");

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222")));


        // Mock authorization - user in first group
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group 1");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership));

        // Mock attribute creation
        HangoutAttribute newAttribute = new HangoutAttribute();
        newAttribute.setHangoutId(hangoutId);
        newAttribute.setAttributeName("dress_code");
        newAttribute.setStringValue("casual");
        when(hangoutRepository.saveAttribute(any(HangoutAttribute.class))).thenReturn(newAttribute);

        // Mock current attributes (after save)
        List<HangoutAttribute> updatedAttributes = List.of(newAttribute);
        when(hangoutRepository.findAttributesByHangoutId(hangoutId))
            .thenReturn(List.of())  // First call: check for duplicates - empty
            .thenReturn(updatedAttributes);  // Second call: get updated list

        // When
        hangoutService.createAttribute(hangoutId, request, userId);

        // Then
        // Verify canonical attribute was saved
        verify(hangoutRepository).saveAttribute(any(HangoutAttribute.class));

        // Verify both pointers were updated with new attributes list via PointerUpdateService
        verify(pointerUpdateService).updatePointerWithRetry(eq("11111111-1111-1111-1111-111111111111"), eq(hangoutId), any(), eq("attributes"));
        verify(pointerUpdateService).updatePointerWithRetry(eq("22222222-2222-2222-2222-222222222222"), eq(hangoutId), any(), eq("attributes"));
    }

    @Test
    void createAttribute_WhenPointerNotFound_ShouldContinueWithOtherPointers() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";

        CreateAttributeRequest request = new CreateAttributeRequest();
        request.setAttributeName("music");
        request.setStringValue("jazz");

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222")));


        // Mock authorization
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group 1");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership));

        // Mock attribute creation
        HangoutAttribute newAttribute = new HangoutAttribute();
        newAttribute.setHangoutId(hangoutId);
        newAttribute.setAttributeName("music");
        newAttribute.setStringValue("jazz");
        when(hangoutRepository.saveAttribute(any(HangoutAttribute.class))).thenReturn(newAttribute);
        when(hangoutRepository.findAttributesByHangoutId(hangoutId))
            .thenReturn(List.of())  // First call: check for duplicates - empty
            .thenReturn(List.of(newAttribute));  // Second call: get updated list

        // When
        assertThatCode(() -> hangoutService.createAttribute(hangoutId, request, userId))
            .doesNotThrowAnyException();

        // Then
        // Verify canonical attribute was saved
        verify(hangoutRepository).saveAttribute(any(HangoutAttribute.class));

        // Verify both groups were attempted to be updated via PointerUpdateService
        verify(pointerUpdateService).updatePointerWithRetry(eq("11111111-1111-1111-1111-111111111111"), eq(hangoutId), any(), eq("attributes"));
        verify(pointerUpdateService).updatePointerWithRetry(eq("22222222-2222-2222-2222-222222222222"), eq(hangoutId), any(), eq("attributes"));
    }

    @Test
    void createAttribute_WithNoAssociatedGroups_ShouldNotUpdatePointers() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";

        CreateAttributeRequest request = new CreateAttributeRequest();
        request.setAttributeName("theme");
        request.setStringValue("retro");

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setAssociatedGroups(new java.util.ArrayList<>()); // No groups
        hangout.setVisibility(EventVisibility.PUBLIC); // Make it public so auth passes


        // Mock authorization
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));

        // Mock attribute creation
        HangoutAttribute newAttribute = new HangoutAttribute();
        when(hangoutRepository.saveAttribute(any(HangoutAttribute.class))).thenReturn(newAttribute);
        when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());

        // When
        hangoutService.createAttribute(hangoutId, request, userId);

        // Then
        // Verify canonical attribute was saved
        verify(hangoutRepository).saveAttribute(any(HangoutAttribute.class));

        // Verify no pointer operations attempted
        verify(pointerUpdateService, never()).updatePointerWithRetry(anyString(), anyString(), any(), anyString());
    }

    // ============================================================================
    // TEST PLAN PHASE 3: POINTER UPDATE TESTS - Group 3
    // ============================================================================

    @Test
    void updateAttribute_WithNameAndValueChange_ShouldUpdateAllPointers() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String attributeId = "attr-123";
        String userId = "87654321-4321-4321-4321-210987654321";

        UpdateAttributeRequest request = new UpdateAttributeRequest();
        request.setAttributeName("dress_code");
        request.setStringValue("formal");

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111")));


        // Mock authorization
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group 1");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership));

        // Mock existing attribute
        HangoutAttribute existingAttribute = new HangoutAttribute();
        existingAttribute.setHangoutId(hangoutId);
        existingAttribute.setAttributeId(attributeId);
        existingAttribute.setAttributeName("dress_code");
        existingAttribute.setStringValue("casual");
        when(hangoutRepository.findAttributeById(hangoutId, attributeId)).thenReturn(Optional.of(existingAttribute));

        // Mock attribute update
        HangoutAttribute updatedAttribute = new HangoutAttribute();
        updatedAttribute.setHangoutId(hangoutId);
        updatedAttribute.setAttributeId(attributeId);
        updatedAttribute.setAttributeName("dress_code");
        updatedAttribute.setStringValue("formal");
        when(hangoutRepository.saveAttribute(any(HangoutAttribute.class))).thenReturn(updatedAttribute);

        // Mock attributes list after update
        List<HangoutAttribute> updatedAttributes = List.of(updatedAttribute);
        when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(updatedAttributes);

        // When
        hangoutService.updateAttribute(hangoutId, attributeId, request, userId);

        // Then
        // Verify canonical attribute was saved
        verify(hangoutRepository).saveAttribute(argThat(attr ->
            attr.getStringValue().equals("formal")
        ));

        // Verify pointer was updated with attributes
        verify(pointerUpdateService).updatePointerWithRetry(eq("11111111-1111-1111-1111-111111111111"), eq(hangoutId), any(), eq("attributes"));
    }

    @Test
    void updateAttribute_WhenRepositoryFails_ShouldLogAndContinue() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String attributeId = "attr-123";
        String userId = "87654321-4321-4321-4321-210987654321";

        UpdateAttributeRequest request = new UpdateAttributeRequest();
        request.setAttributeName("theme");
        request.setStringValue("updated");

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222")));


        // Mock authorization
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group 1");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership));

        // Mock existing attribute
        HangoutAttribute existingAttribute = new HangoutAttribute();
        existingAttribute.setHangoutId(hangoutId);
        existingAttribute.setAttributeId(attributeId);
        existingAttribute.setAttributeName("theme");
        existingAttribute.setStringValue("old");
        when(hangoutRepository.findAttributeById(hangoutId, attributeId)).thenReturn(Optional.of(existingAttribute));

        // Mock attribute update
        HangoutAttribute updatedAttribute = new HangoutAttribute();
        when(hangoutRepository.saveAttribute(any(HangoutAttribute.class))).thenReturn(updatedAttribute);
        when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of(updatedAttribute));

        // Mock pointer retrieval
        HangoutPointer pointer1 = createTestHangoutPointer("11111111-1111-1111-1111-111111111111", hangoutId);
        HangoutPointer pointer2 = createTestHangoutPointer("22222222-2222-2222-2222-222222222222", hangoutId);

        // When
        assertThatCode(() -> hangoutService.updateAttribute(hangoutId, attributeId, request, userId))
            .doesNotThrowAnyException();

        // Then
        // Verify canonical attribute was saved
        verify(hangoutRepository).saveAttribute(any(HangoutAttribute.class));

        // Verify both pointers were updated via PointerUpdateService
        verify(pointerUpdateService).updatePointerWithRetry(eq("11111111-1111-1111-1111-111111111111"), eq(hangoutId), any(), eq("attributes"));
        verify(pointerUpdateService).updatePointerWithRetry(eq("22222222-2222-2222-2222-222222222222"), eq(hangoutId), any(), eq("attributes"));
    }

    // ============================================================================
    // TEST PLAN PHASE 3: POINTER UPDATE TESTS - Group 4
    // ============================================================================

    @Test
    void deleteAttribute_WithValidId_ShouldRemoveFromAllPointers() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String attributeId = "attr-123";
        String userId = "87654321-4321-4321-4321-210987654321";

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(
            "11111111-1111-1111-1111-111111111111",
            "22222222-2222-2222-2222-222222222222",
            "33333333-3333-3333-3333-333333333333"
        )));


        // Mock authorization
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group 1");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership));

        // Mock attribute deletion
        doNothing().when(hangoutRepository).deleteAttribute(hangoutId, attributeId);

        // Mock attributes list after deletion (one attribute remaining)
        HangoutAttribute remainingAttribute = new HangoutAttribute();
        remainingAttribute.setHangoutId(hangoutId);
        remainingAttribute.setAttributeName("other_attribute");
        when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of(remainingAttribute));

        // When
        hangoutService.deleteAttribute(hangoutId, attributeId, userId);

        // Then
        // Verify canonical attribute was deleted
        verify(hangoutRepository).deleteAttribute(hangoutId, attributeId);

        // Verify all 3 pointers were updated via PointerUpdateService
        verify(pointerUpdateService).updatePointerWithRetry(eq("11111111-1111-1111-1111-111111111111"), eq(hangoutId), any(), eq("attributes"));
        verify(pointerUpdateService).updatePointerWithRetry(eq("22222222-2222-2222-2222-222222222222"), eq(hangoutId), any(), eq("attributes"));
        verify(pointerUpdateService).updatePointerWithRetry(eq("33333333-3333-3333-3333-333333333333"), eq(hangoutId), any(), eq("attributes"));
    }

    @Test
    void deleteAttribute_WithAllAttributesRemoved_ShouldSetEmptyList() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String attributeId = "attr-123";
        String userId = "87654321-4321-4321-4321-210987654321";

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111")));


        // Mock authorization
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group 1");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership));

        // Mock attribute deletion
        doNothing().when(hangoutRepository).deleteAttribute(hangoutId, attributeId);

        // Mock empty attributes list after deletion (last attribute was deleted)
        when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());

        // When
        hangoutService.deleteAttribute(hangoutId, attributeId, userId);

        // Then
        // Verify canonical attribute was deleted
        verify(hangoutRepository).deleteAttribute(hangoutId, attributeId);

        // Verify pointer was updated via PointerUpdateService
        verify(pointerUpdateService).updatePointerWithRetry(eq("11111111-1111-1111-1111-111111111111"), eq(hangoutId), any(), eq("attributes"));
    }

    // ============================================================================
    // INTEREST LEVEL DENORMALIZATION TESTS
    // ============================================================================

    @Test
    void associateEventWithGroups_WithExistingInterestLevels_DenormalizesToPointer() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String groupId = "11111111-1111-1111-1111-111111111111";
        String userId = "87654321-4321-4321-4321-210987654321";

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setVisibility(EventVisibility.PUBLIC);
        // Set up hangout so user can edit it (they're admin in a group it's already associated with)
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(groupId)));

        // Create interest levels that should be denormalized to the pointer
        String userId1 = UUID.randomUUID().toString();
        String userId2 = UUID.randomUUID().toString();

        InterestLevel interest1 = new InterestLevel(hangoutId, userId1, "User One", "GOING");
        InterestLevel interest2 = new InterestLevel(hangoutId, userId2, "User Two", "INTERESTED");

        HangoutDetailData data = new HangoutDetailData(
            hangout, List.of(), List.of(), List.of(), List.of(), List.of(interest1, interest2), List.of(), List.of()
        );
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);

        GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
        membership.setRole(GroupRole.ADMIN);
        when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));

        // When
        hangoutService.associateEventWithGroups(hangoutId, List.of(groupId), userId);

        // Then
        verify(groupRepository).saveHangoutPointer(argThat((HangoutPointer pointer) ->
            pointer.getInterestLevels() != null &&
            pointer.getInterestLevels().size() == 2 &&
            pointer.getInterestLevels().get(0).getUserId().equals(userId1) &&
            pointer.getInterestLevels().get(1).getUserId().equals(userId2)
        ));
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
        HangoutDetailData initialData = new HangoutDetailData(
            hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
        HangoutDetailData updatedData = new HangoutDetailData(
            hangout, List.of(), List.of(), List.of(), List.of(), List.of(newInterest), List.of(), List.of()
        );

        when(hangoutRepository.getHangoutDetailData(hangoutId))
            .thenReturn(initialData)  // First call for authorization/initial check
            .thenReturn(updatedData); // Second call after save to get updated interest levels

        User user = createTestUser(userId);
        when(userService.getUserById(UUID.fromString(userId))).thenReturn(Optional.of(user));

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

        HangoutDetailData data = new HangoutDetailData(
            hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);

        User user = createTestUser(userId);
        when(userService.getUserById(UUID.fromString(userId))).thenReturn(Optional.of(user));

        InterestLevel mockInterest = createTestInterestLevel();
        when(hangoutRepository.saveInterestLevel(any(InterestLevel.class))).thenReturn(mockInterest);

        // When
        hangoutService.setUserInterest(hangoutId, request, userId);

        // Then - verify interest level was saved but pointers were not updated
        verify(hangoutRepository).saveInterestLevel(any(InterestLevel.class));
        verify(pointerUpdateService, never()).updatePointerWithRetry(anyString(), anyString(), any(), anyString());
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
        HangoutDetailData initialData = new HangoutDetailData(
            hangout, List.of(), List.of(), List.of(), List.of(), List.of(existingInterest, otherInterest), List.of(), List.of()
        );
        HangoutDetailData updatedData = new HangoutDetailData(
            hangout, List.of(), List.of(), List.of(), List.of(), List.of(otherInterest), List.of(), List.of()
        );

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

        HangoutDetailData data = new HangoutDetailData(
            hangout, List.of(), List.of(), List.of(), List.of(), List.of(existingInterest), List.of(), List.of()
        );
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);

        doNothing().when(hangoutRepository).deleteInterestLevel(hangoutId, userId);

        // When
        hangoutService.removeUserInterest(hangoutId, userId);

        // Then - verify interest level was deleted but pointers were not updated
        verify(hangoutRepository).deleteInterestLevel(hangoutId, userId);
        verify(pointerUpdateService, never()).updatePointerWithRetry(anyString(), anyString(), any(), anyString());
    }

    @Test
    void resyncHangoutPointers_IncludesInterestLevelsInUpdate() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String groupId = "11111111-1111-1111-1111-111111111111";

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setAssociatedGroups(List.of(groupId));

        String userId1 = UUID.randomUUID().toString();
        String userId2 = UUID.randomUUID().toString();

        InterestLevel interest1 = new InterestLevel(hangoutId, userId1, "User One", "GOING");
        InterestLevel interest2 = new InterestLevel(hangoutId, userId2, "User Two", "INTERESTED");

        HangoutDetailData data = new HangoutDetailData(
            hangout, List.of(), List.of(), List.of(), List.of(), List.of(interest1, interest2), List.of(), List.of()
        );
        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
        when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());

        // When
        hangoutService.resyncHangoutPointers(hangoutId);

        // Then - verify pointerUpdateService was called with a lambda that sets interest levels
        verify(pointerUpdateService).updatePointerWithRetry(eq(groupId), eq(hangoutId), any(), eq("complete resync"));
    }

    // ============================================================================
    // PHASE 4: OPTIMISTIC LOCKING RETRY TESTS
    // ============================================================================

    @Nested
    class OptimisticLockingRetryTests {

        // Test Group 1: Success Cases

        @Test
        void updatePointersWithAttributes_WithNoConflict_ShouldSucceedOnFirstAttempt() {
            // Given
            String hangoutId = "12345678-1234-1234-1234-123456789012";
            String groupId = "11111111-1111-1111-1111-111111111111";
            String userId = "87654321-4321-4321-4321-210987654321";

            Hangout hangout = createTestHangout(hangoutId);
            hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(groupId)));

            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
            HangoutPointer pointer = createTestHangoutPointer(groupId, hangoutId);
            pointer.setVersion(1L);

            HangoutAttribute attribute = new HangoutAttribute();
            attribute.setHangoutId(hangoutId);
            attribute.setAttributeId("attr-1");
            attribute.setAttributeName("Location");
            attribute.setStringValue("Park");

            CreateAttributeRequest request = new CreateAttributeRequest();
            request.setAttributeName("Location");
            request.setStringValue("Park");

            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());
            when(hangoutRepository.saveAttribute(any(HangoutAttribute.class))).thenReturn(attribute);

            // When
            hangoutService.createAttribute(hangoutId, request, userId);

            // Then - verify that PointerUpdateService is called
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId), eq(hangoutId), any(), eq("attributes"));

            // Note: Retry behavior is now tested in PointerUpdateServiceTest, not here.
        }

        @Test
        void updatePointersWithAttributes_WithConflictThenSuccess_ShouldRetryAndSucceed() {
            // Given
            String hangoutId = "12345678-1234-1234-1234-123456789012";
            String groupId = "11111111-1111-1111-1111-111111111111";
            String userId = "87654321-4321-4321-4321-210987654321";

            Hangout hangout = createTestHangout(hangoutId);
            hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(groupId)));

            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
            HangoutPointer pointer1 = createTestHangoutPointer(groupId, hangoutId);
            pointer1.setVersion(1L);
            HangoutPointer pointer2 = createTestHangoutPointer(groupId, hangoutId);
            pointer2.setVersion(2L);

            HangoutAttribute attribute = new HangoutAttribute();
            attribute.setHangoutId(hangoutId);
            attribute.setAttributeId("attr-1");
            attribute.setAttributeName("Location");
            attribute.setStringValue("Park");

            CreateAttributeRequest request = new CreateAttributeRequest();
            request.setAttributeName("Location");
            request.setStringValue("Park");

            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());
            when(hangoutRepository.saveAttribute(any(HangoutAttribute.class))).thenReturn(attribute);

            // When
            hangoutService.createAttribute(hangoutId, request, userId);

            // Then - verify that PointerUpdateService is called
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId), eq(hangoutId), any(), eq("attributes"));

            // Note: Retry behavior with conflicts is now tested in PointerUpdateServiceTest.
        }

        // Test Group 2: Retry Exhaustion

        @Test
        void updatePointersWithAttributes_WithPersistentConflict_ShouldGiveUpAfterMaxRetries() {
            // Given
            String hangoutId = "12345678-1234-1234-1234-123456789012";
            String groupId = "11111111-1111-1111-1111-111111111111";
            String userId = "87654321-4321-4321-4321-210987654321";

            Hangout hangout = createTestHangout(hangoutId);
            hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(groupId)));

            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
            HangoutPointer pointer = createTestHangoutPointer(groupId, hangoutId);
            pointer.setVersion(1L);

            HangoutAttribute attribute = new HangoutAttribute();
            attribute.setHangoutId(hangoutId);
            attribute.setAttributeId("attr-1");
            attribute.setAttributeName("Location");
            attribute.setStringValue("Park");

            CreateAttributeRequest request = new CreateAttributeRequest();
            request.setAttributeName("Location");
            request.setStringValue("Park");

            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());
            when(hangoutRepository.saveAttribute(any(HangoutAttribute.class))).thenReturn(attribute);

            // When
            hangoutService.createAttribute(hangoutId, request, userId);

            // Then - verify that PointerUpdateService is called
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId), eq(hangoutId), any(), eq("attributes"));

            // Note: Max retry behavior is now tested in PointerUpdateServiceTest.
        }

        @Test
        void updatePointersWithAttributes_WithMultipleGroups_OnlyRetriesFailedPointer() {
            // Given
            String hangoutId = "12345678-1234-1234-1234-123456789012";
            String groupId1 = "11111111-1111-1111-1111-111111111111";
            String groupId2 = "22222222-2222-2222-2222-222222222222";
            String groupId3 = "33333333-3333-3333-3333-333333333333";
            String userId = "87654321-4321-4321-4321-210987654321";

            Hangout hangout = createTestHangout(hangoutId);
            hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(groupId1, groupId2, groupId3)));

            GroupMembership membership = createTestMembership(groupId1, userId, "Test Group");
            HangoutPointer pointer1 = createTestHangoutPointer(groupId1, hangoutId);
            HangoutPointer pointer2 = createTestHangoutPointer(groupId2, hangoutId);
            HangoutPointer pointer3 = createTestHangoutPointer(groupId3, hangoutId);

            HangoutAttribute attribute = new HangoutAttribute();
            attribute.setHangoutId(hangoutId);
            attribute.setAttributeId("attr-1");
            attribute.setAttributeName("Location");
            attribute.setStringValue("Park");

            CreateAttributeRequest request = new CreateAttributeRequest();
            request.setAttributeName("Location");
            request.setStringValue("Park");

            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
            when(groupRepository.findMembership(groupId1, userId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());
            when(hangoutRepository.saveAttribute(any(HangoutAttribute.class))).thenReturn(attribute);

            // When
            hangoutService.createAttribute(hangoutId, request, userId);

            // Then - verify that PointerUpdateService is called for all groups
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId1), eq(hangoutId), any(), eq("attributes"));
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId2), eq(hangoutId), any(), eq("attributes"));
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId3), eq(hangoutId), any(), eq("attributes"));

            // Note: Individual group retry behavior is now tested in PointerUpdateServiceTest.
        }

        // Test Group 3: Error Handling

        @Test
        void updatePointersWithAttributes_WithNonVersionException_ShouldGiveUpImmediately() {
            // Given
            String hangoutId = "12345678-1234-1234-1234-123456789012";
            String groupId = "11111111-1111-1111-1111-111111111111";
            String userId = "87654321-4321-4321-4321-210987654321";

            Hangout hangout = createTestHangout(hangoutId);
            hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(groupId)));

            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
            HangoutPointer pointer = createTestHangoutPointer(groupId, hangoutId);

            HangoutAttribute attribute = new HangoutAttribute();
            attribute.setHangoutId(hangoutId);
            attribute.setAttributeId("attr-1");
            attribute.setAttributeName("Location");
            attribute.setStringValue("Park");

            CreateAttributeRequest request = new CreateAttributeRequest();
            request.setAttributeName("Location");
            request.setStringValue("Park");

            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());
            when(hangoutRepository.saveAttribute(any(HangoutAttribute.class))).thenReturn(attribute);

            // When
            hangoutService.createAttribute(hangoutId, request, userId);

            // Then
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId), eq(hangoutId), any(), eq("attributes"));

            // Note: Retry behavior for non-version exceptions is now tested in PointerUpdateServiceTest.
        }

        @Test
        void updatePointersWithAttributes_WithInterruptedException_ShouldStopRetrying() {
            // Given
            String hangoutId = "12345678-1234-1234-1234-123456789012";
            String groupId = "11111111-1111-1111-1111-111111111111";
            String userId = "87654321-4321-4321-4321-210987654321";

            Hangout hangout = createTestHangout(hangoutId);
            hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(groupId)));

            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
            HangoutPointer pointer = createTestHangoutPointer(groupId, hangoutId);

            HangoutAttribute attribute = new HangoutAttribute();
            attribute.setHangoutId(hangoutId);
            attribute.setAttributeId("attr-1");
            attribute.setAttributeName("Location");
            attribute.setStringValue("Park");

            CreateAttributeRequest request = new CreateAttributeRequest();
            request.setAttributeName("Location");
            request.setStringValue("Park");

            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());
            when(hangoutRepository.saveAttribute(any(HangoutAttribute.class))).thenReturn(attribute);

            // When
            hangoutService.createAttribute(hangoutId, request, userId);

            // Then
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId), eq(hangoutId), any(), eq("attributes"));

            // Note: InterruptedException and retry behavior is now tested in PointerUpdateServiceTest.
        }

        @Test
        void updatePointersWithAttributes_WithPointerNotFound_ShouldSkipGracefully() {
            // Given
            String hangoutId = "12345678-1234-1234-1234-123456789012";
            String groupId = "11111111-1111-1111-1111-111111111111";
            String userId = "87654321-4321-4321-4321-210987654321";

            Hangout hangout = createTestHangout(hangoutId);
            hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(groupId)));

            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");

            HangoutAttribute attribute = new HangoutAttribute();
            attribute.setHangoutId(hangoutId);
            attribute.setAttributeId("attr-1");
            attribute.setAttributeName("Location");
            attribute.setStringValue("Park");

            CreateAttributeRequest request = new CreateAttributeRequest();
            request.setAttributeName("Location");
            request.setStringValue("Park");

            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());
            when(hangoutRepository.saveAttribute(any(HangoutAttribute.class))).thenReturn(attribute);

            // When
            hangoutService.createAttribute(hangoutId, request, userId);

            // Then
            verify(pointerUpdateService).updatePointerWithRetry(eq(groupId), eq(hangoutId), any(), eq("attributes"));
        }
    }
}
