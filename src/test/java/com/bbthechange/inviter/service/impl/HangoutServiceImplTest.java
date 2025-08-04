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
        String eventId = "event-123";
        String userId = "user-456";
        
        Event event = createTestEvent(eventId);
        event.setVisibility(EventVisibility.PUBLIC); // Public event - user can view
        
        EventDetailData data = new EventDetailData(
            event,
            List.of(createTestPoll()),
            List.of(createTestCar()),
            List.of(createTestVote()),
            List.of(createTestInterestLevel())
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
        String eventId = "event-123";
        String userId = "unauthorized-user";
        
        Event event = createTestEvent(eventId);
        event.setVisibility(EventVisibility.INVITE_ONLY); // Private event
        event.setAssociatedGroups(List.of("group-1")); // User not in this group
        
        EventDetailData data = new EventDetailData(event, List.of(), List.of(), List.of(), List.of());
        when(hangoutRepository.getEventDetailData(eventId)).thenReturn(data);
        when(groupRepository.findMembership("group-1", userId)).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> hangoutService.getEventDetail(eventId, userId))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("Cannot view event");
    }
    
    @Test
    void updateEventTitle_Success() {
        // Given
        String eventId = "event-123";
        String newTitle = "Updated Event Title";
        String userId = "admin-user";
        
        Event event = createTestEvent(eventId);
        event.setAssociatedGroups(List.of("group-1", "group-2"));
        
        EventDetailData data = new EventDetailData(event, List.of(), List.of(), List.of(), List.of());
        when(hangoutRepository.getEventDetailData(eventId)).thenReturn(data);
        
        // Mock authorization - user is admin in group-1
        GroupMembership adminMembership = new GroupMembership("group-1", userId, "Test Group");
        adminMembership.setRole(GroupRole.ADMIN);
        when(groupRepository.findMembership("group-1", userId)).thenReturn(Optional.of(adminMembership));
        
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
        String eventId = "event-123";
        String newTitle = "Updated Title";
        String userId = "regular-user";
        
        Event event = createTestEvent(eventId);
        event.setAssociatedGroups(List.of("group-1"));
        
        EventDetailData data = new EventDetailData(event, List.of(), List.of(), List.of(), List.of());
        when(hangoutRepository.getEventDetailData(eventId)).thenReturn(data);
        
        // Mock authorization - user is regular member, not admin
        GroupMembership memberMembership = new GroupMembership("group-1", userId, "Test Group");
        memberMembership.setRole(GroupRole.MEMBER);
        when(groupRepository.findMembership("group-1", userId)).thenReturn(Optional.of(memberMembership));
        
        // When/Then
        assertThatThrownBy(() -> hangoutService.updateEventTitle(eventId, newTitle, userId))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("Cannot edit event");
            
        verify(hangoutRepository, never()).save(any());
    }
    
    @Test
    void associateEventWithGroups_Success() {
        // Given
        String eventId = "event-123";
        List<String> groupIds = List.of("group-1", "group-2");
        String userId = "admin-user";
        
        Event event = createTestEvent(eventId);
        event.setAssociatedGroups(List.of()); // Initially no groups
        
        EventDetailData data = new EventDetailData(event, List.of(), List.of(), List.of(), List.of());
        when(hangoutRepository.getEventDetailData(eventId)).thenReturn(data);
        
        // Mock authorization - user is member of both groups
        when(groupRepository.findMembership("group-1", userId)).thenReturn(
            Optional.of(new GroupMembership("group-1", userId, "Group One")));
        when(groupRepository.findMembership("group-2", userId)).thenReturn(
            Optional.of(new GroupMembership("group-2", userId, "Group Two")));
        
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
        String eventId = "event-123";
        List<String> groupIds = List.of("group-1");
        String userId = "non-member";
        
        Event event = createTestEvent(eventId);
        EventDetailData data = new EventDetailData(event, List.of(), List.of(), List.of(), List.of());
        when(hangoutRepository.getEventDetailData(eventId)).thenReturn(data);
        
        // User is not in the group
        when(groupRepository.findMembership("group-1", userId)).thenReturn(Optional.empty());
        
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
        String eventId = "event-123";
        List<String> groupIds = List.of("group-1");
        String userId = "admin-user";
        
        Event event = createTestEvent(eventId);
        event.setAssociatedGroups(List.of("group-1", "group-2")); // Has groups initially
        
        EventDetailData data = new EventDetailData(event, List.of(), List.of(), List.of(), List.of());
        when(hangoutRepository.getEventDetailData(eventId)).thenReturn(data);
        
        // Mock authorization - user is admin in group-1
        GroupMembership adminMembership = new GroupMembership("group-1", userId, "Group One");
        adminMembership.setRole(GroupRole.ADMIN);
        when(groupRepository.findMembership("group-1", userId)).thenReturn(Optional.of(adminMembership));
        
        when(hangoutRepository.save(any(Event.class))).thenReturn(event);
        doNothing().when(groupRepository).deleteHangoutPointer(anyString(), anyString());
        
        // When
        assertThatCode(() -> hangoutService.disassociateEventFromGroups(eventId, groupIds, userId))
            .doesNotThrowAnyException();
        
        // Then
        verify(hangoutRepository).save(any(Event.class)); // Update canonical record
        verify(groupRepository).deleteHangoutPointer("group-1", eventId); // Remove pointer
    }
    
    @Test
    void canUserViewEvent_PublicEvent_ReturnsTrue() {
        // Given
        String userId = "any-user";
        Event event = createTestEvent("event-123");
        event.setVisibility(EventVisibility.PUBLIC);
        
        // When
        boolean result = hangoutService.canUserViewEvent(userId, event);
        
        // Then
        assertThat(result).isTrue();
    }
    
    @Test
    void canUserViewEvent_InviteOnlyEventUserInGroup_ReturnsTrue() {
        // Given
        String userId = "member-user";
        Event event = createTestEvent("event-123");
        event.setVisibility(EventVisibility.INVITE_ONLY);
        event.setAssociatedGroups(List.of("group-1"));
        
        when(groupRepository.findMembership("group-1", userId)).thenReturn(
            Optional.of(new GroupMembership("group-1", userId, "Group One")));
        
        // When
        boolean result = hangoutService.canUserViewEvent(userId, event);
        
        // Then
        assertThat(result).isTrue();
    }
    
    @Test
    void canUserEditEvent_UserIsAdmin_ReturnsTrue() {
        // Given
        String userId = "admin-user";
        Event event = createTestEvent("event-123");
        event.setAssociatedGroups(List.of("group-1"));
        
        GroupMembership adminMembership = new GroupMembership("group-1", userId, "Group One");
        adminMembership.setRole(GroupRole.ADMIN);
        when(groupRepository.findMembership("group-1", userId)).thenReturn(Optional.of(adminMembership));
        
        // When
        boolean result = hangoutService.canUserEditEvent(userId, event);
        
        // Then
        assertThat(result).isTrue();
    }
    
    @Test
    void canUserEditEvent_UserIsRegularMember_ReturnsFalse() {
        // Given
        String userId = "regular-user";
        Event event = createTestEvent("event-123");
        event.setAssociatedGroups(List.of("group-1"));
        
        GroupMembership memberMembership = new GroupMembership("group-1", userId, "Group One");
        memberMembership.setRole(GroupRole.MEMBER);
        when(groupRepository.findMembership("group-1", userId)).thenReturn(Optional.of(memberMembership));
        
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
    
    private Poll createTestPoll() {
        return new Poll("event-123", "Test Poll", "Description", false);
    }
    
    private Car createTestCar() {
        return new Car("event-123", "driver-123", "John Doe", 4);
    }
    
    private Vote createTestVote() {
        return new Vote("event-123", "poll-123", "option-1", "user-123", "John Doe", "YES");
    }
    
    private InterestLevel createTestInterestLevel() {
        return new InterestLevel("event-123", "user-123", "John Doe", "GOING");
    }
}