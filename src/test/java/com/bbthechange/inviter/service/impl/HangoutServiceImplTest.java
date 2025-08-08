package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.service.FuzzyTimeService;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

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
    
    @InjectMocks
    private HangoutServiceImpl hangoutService;
    
    @Test
    void getEventDetail_Success() {
        // Given
        String eventId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";
        
        Event event = createTestEvent(eventId);
        event.setVisibility(EventVisibility.PUBLIC); // Public event - user can view
        
        EventDetailData data = new EventDetailData(
            event,
            List.of(createTestPoll()),
            List.of(createTestCar()),
            List.of(createTestVote()),
            List.of(createTestInterestLevel()),
            List.of()
        );
        
        when(hangoutRepository.getEventDetailData(eventId)).thenReturn(data);
        
        // When
        EventDetailDTO result = hangoutService.getEventDetail(eventId, userId);
        
        // Then
        assertThat(result.getEvent()).isEqualTo(event);
        assertThat(result.getPolls()).hasSize(1);
        assertThat(result.getCars()).hasSize(1);
        assertThat(result.getVotes()).hasSize(1);
        assertThat(result.getAttendance()).hasSize(1);
        
        verify(hangoutRepository).getEventDetailData(eventId);
    }
    
    @Test
    void getEventDetail_UnauthorizedUser_ThrowsException() {
        // Given
        String eventId = "12345678-1234-1234-1234-123456789012";
        String userId = "33333333-3333-3333-3333-333333333333";
        
        Event event = createTestEvent(eventId);
        event.setVisibility(EventVisibility.INVITE_ONLY); // Private event
        event.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111"))); // User not in this group
        
        EventDetailData data = new EventDetailData(event, List.of(), List.of(), List.of(), List.of(), List.of());
        when(hangoutRepository.getEventDetailData(eventId)).thenReturn(data);
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> hangoutService.getEventDetail(eventId, userId))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("Cannot view event");
    }
    
    @Test
    void updateEventTitle_Success() {
        // Given
        String eventId = "12345678-1234-1234-1234-123456789012";
        String newTitle = "Updated Event Title";
        String userId = "87654321-4321-4321-4321-210987654321";
        
        Event event = createTestEvent(eventId);
        event.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222")));
        
        EventDetailData data = new EventDetailData(event, List.of(), List.of(), List.of(), List.of(), List.of());
        when(hangoutRepository.getEventDetailData(eventId)).thenReturn(data);
        
        // Mock authorization - user is admin in group-1
        GroupMembership adminMembership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Test Group");
        adminMembership.setRole(GroupRole.ADMIN);
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(adminMembership));
        
        when(hangoutRepository.save(any(Event.class))).thenReturn(event);
        doNothing().when(groupRepository).updateHangoutPointer(anyString(), anyString(), any());
        
        // When
        assertThatCode(() -> hangoutService.updateEventTitle(eventId, newTitle, userId))
            .doesNotThrowAnyException();
        
        // Then - verify multi-step pointer update pattern
        verify(hangoutRepository).save(any(Event.class)); // Step 1: Update canonical record
        verify(groupRepository, times(2)).updateHangoutPointer(anyString(), eq(eventId), any()); // Step 2: Update pointers
    }
    
    @Test
    void associateEventWithGroups_Success() {
        // Given
        String eventId = "12345678-1234-1234-1234-123456789012";
        List<String> groupIds = List.of("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222");
        String userId = "87654321-4321-4321-4321-210987654321";
        
        Hangout hangout = createTestHangout(eventId);
        // Set up hangout so user can edit it (they're admin in one of the groups they want to associate)
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111")));
        
        HangoutDetailData data = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of());
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(data);
        
        // Mock authorization - user is admin in first group and member of second
        GroupMembership adminMembership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group One");
        adminMembership.setRole(GroupRole.ADMIN);
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(
            Optional.of(adminMembership));
        when(groupRepository.findMembership("22222222-2222-2222-2222-222222222222", userId)).thenReturn(
            Optional.of(createTestMembership("22222222-2222-2222-2222-222222222222", userId, "Group Two")));
        
        when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(hangout);
        doNothing().when(groupRepository).saveHangoutPointer(any(HangoutPointer.class));
        
        // When
        assertThatCode(() -> hangoutService.associateEventWithGroups(eventId, groupIds, userId))
            .doesNotThrowAnyException();
        
        // Then
        verify(hangoutRepository).createHangout(any(Hangout.class)); // Update canonical record
        verify(groupRepository, times(2)).saveHangoutPointer(any(HangoutPointer.class)); // Create pointers
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
        
        HangoutDetailData data = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of());
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
        
        HangoutDetailData data = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of());
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
    
    @Test
    void canUserViewEvent_PublicEvent_ReturnsTrue() {
        // Given
        String userId = "87654321-4321-4321-4321-210987654321";
        Event event = createTestEvent("12345678-1234-1234-1234-123456789012");
        event.setVisibility(EventVisibility.PUBLIC);
        
        // When
        boolean result = hangoutService.canUserViewEvent(userId, event);
        
        // Then
        assertThat(result).isTrue();
    }
    
    @Test
    void canUserViewEvent_InviteOnlyEventUserInGroup_ReturnsTrue() {
        // Given
        String userId = "87654321-4321-4321-4321-210987654321";
        Event event = createTestEvent("12345678-1234-1234-1234-123456789012");
        event.setVisibility(EventVisibility.INVITE_ONLY);
        event.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111")));
        
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(
            Optional.of(createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group One")));
        
        // When
        boolean result = hangoutService.canUserViewEvent(userId, event);
        
        // Then
        assertThat(result).isTrue();
    }
    
    @Test
    void canUserEditEvent_UserIsAdmin_ReturnsTrue() {
        // Given
        String userId = "87654321-4321-4321-4321-210987654321";
        Event event = createTestEvent("12345678-1234-1234-1234-123456789012");
        event.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111")));
        
        GroupMembership adminMembership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group One");
        adminMembership.setRole(GroupRole.ADMIN);
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(adminMembership));
        
        // When
        boolean result = hangoutService.canUserEditEvent(userId, event);
        
        // Then
        assertThat(result).isTrue();
    }
    
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
        vote.setUserName("John Doe");
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
        when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(savedHangout);
        doNothing().when(groupRepository).saveHangoutPointer(any(HangoutPointer.class));
        
        // When
        Hangout result = hangoutService.createHangout(request, userId);
        
        // Then
        assertThat(result).isNotNull();
        
        // Verify fuzzy time conversion was called
        verify(fuzzyTimeService).convert(timeInfo);
        
        // Verify hangout was created with correct timestamps
        verify(hangoutRepository).createHangout(argThat(hangout -> 
            hangout.getTimeInput().equals(timeInfo) &&
            hangout.getStartTimestamp().equals(1754557200L) &&
            hangout.getEndTimestamp().equals(1754571600L)
        ));
        
        // Verify pointer was created with GSI fields
        verify(groupRepository).saveHangoutPointer(argThat(pointer ->
            pointer.getGSI1PK().equals("GROUP#11111111-1111-1111-1111-111111111111") &&
            pointer.getGSI1SK().equals("T#1754557200")
        ));
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
        when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(savedHangout);
        doNothing().when(groupRepository).saveHangoutPointer(any(HangoutPointer.class));
        
        // When
        Hangout result = hangoutService.createHangout(request, userId);
        
        // Then
        assertThat(result).isNotNull();
        
        // Verify fuzzy time conversion was called
        verify(fuzzyTimeService).convert(timeInfo);
        
        // Verify hangout was created with correct timestamps
        verify(hangoutRepository).createHangout(argThat(hangout -> 
            hangout.getTimeInput().equals(timeInfo) &&
            hangout.getStartTimestamp().equals(1754558100L) &&
            hangout.getEndTimestamp().equals(1754566200L)
        ));
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
        when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(savedHangout);
        doNothing().when(groupRepository).saveHangoutPointer(any(HangoutPointer.class));
        
        // When
        Hangout result = hangoutService.createHangout(request, userId);
        
        // Then
        assertThat(result).isNotNull();
        
        // Verify fuzzy time service was not called for null timeInput
        verify(fuzzyTimeService, never()).convert(any());
        
        // Verify hangout was created with null timestamps
        verify(hangoutRepository).createHangout(argThat(hangout -> 
            hangout.getTimeInput() == null &&
            hangout.getStartTimestamp() == null &&
            hangout.getEndTimestamp() == null
        ));
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
        doNothing().when(groupRepository).updateHangoutPointer(anyString(), anyString(), any());
        
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
        
        // Verify pointer was updated with new GSI1SK
        verify(groupRepository).updateHangoutPointer(
            eq("11111111-1111-1111-1111-111111111111"), 
            eq(hangoutId), 
            argThat(updates -> updates.containsKey(":GSI1SK") && 
                updates.get(":GSI1SK").s().equals("T#1754603600"))
        );
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
        
        Hangout hangout = createTestHangout(eventId);
        hangout.setStartTimestamp(1754557200L); // Has timestamp from fuzzy time
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("22222222-2222-2222-2222-222222222222")));
        
        HangoutDetailData data = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of());
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(data);
        
        // Mock authorization
        GroupMembership adminMembership = createTestMembership("22222222-2222-2222-2222-222222222222", userId, "Existing Group");
        adminMembership.setRole(GroupRole.ADMIN);
        when(groupRepository.findMembership("22222222-2222-2222-2222-222222222222", userId)).thenReturn(Optional.of(adminMembership));
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(
            Optional.of(createTestMembership("11111111-1111-1111-1111-111111111111", userId, "New Group")));
        
        when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(hangout);
        doNothing().when(groupRepository).saveHangoutPointer(any(HangoutPointer.class));
        
        // When
        assertThatCode(() -> hangoutService.associateEventWithGroups(eventId, groupIds, userId))
            .doesNotThrowAnyException();
        
        // Then
        verify(groupRepository).saveHangoutPointer(argThat(pointer ->
            pointer.getGSI1PK().equals("GROUP#11111111-1111-1111-1111-111111111111") &&
            pointer.getGSI1SK().equals("T#1754557200")
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
        
        HangoutDetailData data = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of());
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
        pointer.setLocationName("Test Location");
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
}