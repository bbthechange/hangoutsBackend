package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.service.HangoutService;
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
    void updateEventTitle_UnauthorizedUser_ThrowsException() {
        // Given
        String eventId = "12345678-1234-1234-1234-123456789012";
        String newTitle = "Updated Title";
        String userId = "44444444-4444-4444-4444-444444444444";
        
        Event event = createTestEvent(eventId);
        event.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111")));
        
        EventDetailData data = new EventDetailData(event, List.of(), List.of(), List.of(), List.of(), List.of());
        when(hangoutRepository.getEventDetailData(eventId)).thenReturn(data);
        
        // Mock authorization - user is regular member, not admin
        GroupMembership memberMembership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Test Group");
        memberMembership.setRole(GroupRole.MEMBER);
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(memberMembership));
        
        // When/Then
        assertThatThrownBy(() -> hangoutService.updateEventTitle(eventId, newTitle, userId))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("Cannot edit event");
            
        verify(hangoutRepository, never()).save(any());
    }
    
    @Test
    void associateEventWithGroups_Success() {
        // Given
        String eventId = "12345678-1234-1234-1234-123456789012";
        List<String> groupIds = List.of("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222");
        String userId = "87654321-4321-4321-4321-210987654321";
        
        Event event = createTestEvent(eventId);
        // Set up event so user can edit it (they're admin in one of the groups they want to associate)
        event.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111")));
        
        EventDetailData data = new EventDetailData(event, List.of(), List.of(), List.of(), List.of(), List.of());
        when(hangoutRepository.getEventDetailData(eventId)).thenReturn(data);
        
        // Mock authorization - user is admin in first group and member of second
        GroupMembership adminMembership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group One");
        adminMembership.setRole(GroupRole.ADMIN);
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(
            Optional.of(adminMembership));
        when(groupRepository.findMembership("22222222-2222-2222-2222-222222222222", userId)).thenReturn(
            Optional.of(createTestMembership("22222222-2222-2222-2222-222222222222", userId, "Group Two")));
        
        when(hangoutRepository.save(any(Event.class))).thenReturn(event);
        doNothing().when(groupRepository).saveHangoutPointer(any(HangoutPointer.class));
        
        // When
        assertThatCode(() -> hangoutService.associateEventWithGroups(eventId, groupIds, userId))
            .doesNotThrowAnyException();
        
        // Then
        verify(hangoutRepository).save(any(Event.class)); // Update canonical record
        verify(groupRepository, times(2)).saveHangoutPointer(any(HangoutPointer.class)); // Create pointers
    }
    
    @Test
    void associateEventWithGroups_UserNotInGroup_ThrowsException() {
        // Given
        String eventId = "12345678-1234-1234-1234-123456789012";
        List<String> groupIds = List.of("11111111-1111-1111-1111-111111111111");
        String userId = "87654321-4321-4321-4321-210987654321";
        
        Event event = createTestEvent(eventId);
        // Set up event so user can edit it (they're admin in a different group)
        event.setAssociatedGroups(new java.util.ArrayList<>(List.of("22222222-2222-2222-2222-222222222222")));
        
        EventDetailData data = new EventDetailData(event, List.of(), List.of(), List.of(), List.of(), List.of());
        when(hangoutRepository.getEventDetailData(eventId)).thenReturn(data);
        
        // User is admin in existing associated group (so they can edit event)
        GroupMembership adminMembership = createTestMembership("22222222-2222-2222-2222-222222222222", userId, "Existing Group");
        adminMembership.setRole(GroupRole.ADMIN);
        when(groupRepository.findMembership("22222222-2222-2222-2222-222222222222", userId)).thenReturn(Optional.of(adminMembership));
        
        // But user is NOT in the group they're trying to associate with
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> hangoutService.associateEventWithGroups(eventId, groupIds, userId))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("User not in group");
            
        verify(hangoutRepository, never()).save(any());
        verify(groupRepository, never()).saveHangoutPointer(any());
    }
    
    @Test
    void disassociateEventFromGroups_Success() {
        // Given
        String eventId = "12345678-1234-1234-1234-123456789012";
        List<String> groupIds = List.of("11111111-1111-1111-1111-111111111111");
        String userId = "87654321-4321-4321-4321-210987654321";
        
        Event event = createTestEvent(eventId);
        event.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222")));
        
        EventDetailData data = new EventDetailData(event, List.of(), List.of(), List.of(), List.of(), List.of());
        when(hangoutRepository.getEventDetailData(eventId)).thenReturn(data);
        
        // Mock authorization - user is admin in group-1
        GroupMembership adminMembership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group One");
        adminMembership.setRole(GroupRole.ADMIN);
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(adminMembership));
        
        when(hangoutRepository.save(any(Event.class))).thenReturn(event);
        doNothing().when(groupRepository).deleteHangoutPointer(anyString(), anyString());
        
        // When
        assertThatCode(() -> hangoutService.disassociateEventFromGroups(eventId, groupIds, userId))
            .doesNotThrowAnyException();
        
        // Then
        verify(hangoutRepository).save(any(Event.class)); // Update canonical record
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
    
    @Test
    void canUserEditEvent_UserIsRegularMember_ReturnsFalse() {
        // Given
        String userId = "44444444-4444-4444-4444-444444444444";
        Event event = createTestEvent("12345678-1234-1234-1234-123456789012");
        event.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111")));
        
        GroupMembership memberMembership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group One");
        memberMembership.setRole(GroupRole.MEMBER);
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(memberMembership));
        
        // When
        boolean result = hangoutService.canUserEditEvent(userId, event);
        
        // Then
        assertThat(result).isFalse();
    }
    
    // Helper methods for test data creation
    private Event createTestEvent(String eventId) {
        Event event = new Event("Test Event", "Description", 
            java.time.LocalDateTime.now(), java.time.LocalDateTime.now().plusHours(2),
            null, EventVisibility.INVITE_ONLY, null);
        // Set the ID using reflection or a test-friendly constructor
        return event;
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
}