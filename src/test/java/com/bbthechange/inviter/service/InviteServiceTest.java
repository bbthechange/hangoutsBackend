package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.InviteResponse;
import com.bbthechange.inviter.model.Event;
import com.bbthechange.inviter.model.Invite;
import com.bbthechange.inviter.model.User;
import com.bbthechange.inviter.repository.EventRepository;
import com.bbthechange.inviter.repository.InviteRepository;
import com.bbthechange.inviter.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InviteServiceTest {

    @Mock
    private InviteRepository inviteRepository;
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private EventRepository eventRepository;
    
    @Mock
    private PushNotificationService pushNotificationService;
    
    @InjectMocks
    private InviteService inviteService;
    
    private UUID userId;
    private UUID eventId;
    private UUID inviteId;
    private User testUser;
    private Event testEvent;
    private Invite testInvite;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        inviteId = UUID.randomUUID();
        
        testUser = new User();
        testUser.setId(userId);
        testUser.setPhoneNumber("1234567890");
        testUser.setUsername("testuser");
        testUser.setDisplayName("Test User");
        
        testEvent = new Event();
        testEvent.setId(eventId);
        testEvent.setName("Test Event");
        
        testInvite = new Invite(eventId, userId, Invite.InviteType.GUEST);
        testInvite.setId(inviteId);
    }

    @Test
    void testIsUserInvitedToEvent_UserIsInvited_ReturnsTrue() {
        // Arrange
        List<Invite> userInvites = Arrays.asList(testInvite);
        when(inviteRepository.findByUserId(userId)).thenReturn(userInvites);
        
        // Act
        boolean result = inviteService.isUserInvitedToEvent(userId, eventId);
        
        // Assert
        assertTrue(result);
        verify(inviteRepository).findByUserId(userId);
    }

    @Test
    void testIsUserInvitedToEvent_UserNotInvited_ReturnsFalse() {
        // Arrange
        UUID differentEventId = UUID.randomUUID();
        List<Invite> userInvites = Arrays.asList(testInvite);
        when(inviteRepository.findByUserId(userId)).thenReturn(userInvites);
        
        // Act
        boolean result = inviteService.isUserInvitedToEvent(userId, differentEventId);
        
        // Assert
        assertFalse(result);
        verify(inviteRepository).findByUserId(userId);
    }

    @Test
    void testGetInvitesForEvent_ReturnsInviteResponses() {
        // Arrange
        List<Invite> invites = Arrays.asList(testInvite);
        when(inviteRepository.findByEventId(eventId)).thenReturn(invites);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        
        // Act
        List<InviteResponse> result = inviteService.getInvitesForEvent(eventId);
        
        // Assert
        assertEquals(1, result.size());
        InviteResponse response = result.get(0);
        assertEquals(inviteId, response.getId());
        assertEquals(eventId, response.getEventId());
        assertEquals(userId, response.getUserId());
        assertEquals("1234567890", response.getUserPhoneNumber());
        assertEquals("testuser", response.getUsername());
        assertEquals("Test User", response.getDisplayName());
        assertEquals(Invite.InviteType.GUEST, response.getType());
        
        verify(inviteRepository).findByEventId(eventId);
        verify(userRepository).findById(userId);
    }

    @Test
    void testAddInviteToEvent_WithPhoneNumberOnly_CreatesGuestInvite() {
        // Arrange
        String phoneNumber = "1234567890";
        when(userRepository.findByPhoneNumber(phoneNumber)).thenReturn(Optional.of(testUser));
        when(inviteRepository.findByEventId(eventId)).thenReturn(Collections.emptyList());
        when(inviteRepository.save(any(Invite.class))).thenReturn(testInvite);
        
        // Act
        Invite result = inviteService.addInviteToEvent(eventId, phoneNumber);
        
        // Assert
        assertEquals(testInvite, result);
        verify(userRepository).findByPhoneNumber(phoneNumber);
        verify(inviteRepository).findByEventId(eventId);
        verify(inviteRepository).save(any(Invite.class));
    }

    @Test
    void testAddInviteToEvent_WithNewUser_CreatesUserAndInvite() {
        // Arrange
        String phoneNumber = "9876543210";
        User newUser = new User(phoneNumber, null, null);
        newUser.setId(UUID.randomUUID());
        
        when(userRepository.findByPhoneNumber(phoneNumber)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(newUser);
        when(inviteRepository.findByEventId(eventId)).thenReturn(Collections.emptyList());
        when(inviteRepository.save(any(Invite.class))).thenReturn(testInvite);
        
        // Act
        Invite result = inviteService.addInviteToEvent(eventId, phoneNumber);
        
        // Assert
        assertEquals(testInvite, result);
        verify(userRepository).findByPhoneNumber(phoneNumber);
        verify(userRepository).save(any(User.class));
        verify(inviteRepository).save(any(Invite.class));
    }

    @Test
    void testAddInviteToEvent_UserAlreadyInvited_ThrowsException() {
        // Arrange
        String phoneNumber = "1234567890";
        List<Invite> existingInvites = Arrays.asList(testInvite);
        
        when(userRepository.findByPhoneNumber(phoneNumber)).thenReturn(Optional.of(testUser));
        when(inviteRepository.findByEventId(eventId)).thenReturn(existingInvites);
        
        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> inviteService.addInviteToEvent(eventId, phoneNumber));
        
        assertEquals("User is already invited to this event", exception.getMessage());
        verify(userRepository).findByPhoneNumber(phoneNumber);
        verify(inviteRepository).findByEventId(eventId);
        verify(inviteRepository, never()).save(any(Invite.class));
    }

    @Test
    void testAddInviteToEvent_WithHostType_CreatesHostInvite() {
        // Arrange
        String phoneNumber = "1234567890";
        when(userRepository.findByPhoneNumber(phoneNumber)).thenReturn(Optional.of(testUser));
        when(inviteRepository.findByEventId(eventId)).thenReturn(Collections.emptyList());
        when(inviteRepository.save(any(Invite.class))).thenReturn(testInvite);
        
        // Act
        Invite result = inviteService.addInviteToEvent(eventId, phoneNumber, Invite.InviteType.HOST);
        
        // Assert
        assertEquals(testInvite, result);
        verify(inviteRepository).save(any(Invite.class));
    }


    @Test
    void testRemoveInvite_InviteExists_RemovesInvite() {
        // Arrange
        when(inviteRepository.findById(inviteId)).thenReturn(Optional.of(testInvite));
        
        // Act
        inviteService.removeInvite(inviteId);
        
        // Assert
        verify(inviteRepository).findById(inviteId);
        verify(inviteRepository).delete(testInvite);
    }

    @Test
    void testRemoveInvite_InviteNotFound_ThrowsException() {
        // Arrange
        when(inviteRepository.findById(inviteId)).thenReturn(Optional.empty());
        
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> inviteService.removeInvite(inviteId));
        
        assertEquals("Invite not found", exception.getMessage());
        verify(inviteRepository).findById(inviteId);
        verify(inviteRepository, never()).delete(any());
    }

    @Test
    void testRemoveInvite_LastHostWithoutLegacyHosts_ThrowsException() {
        // Arrange
        Invite hostInvite = new Invite(eventId, userId, Invite.InviteType.HOST);
        hostInvite.setId(inviteId);
        
        when(inviteRepository.findById(inviteId)).thenReturn(Optional.of(hostInvite));
        when(inviteRepository.findByEventId(eventId)).thenReturn(Arrays.asList(hostInvite));
        
        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> inviteService.removeInvite(inviteId));
        
        assertEquals("Cannot remove the last host from an event", exception.getMessage());
        verify(inviteRepository, never()).delete(any());
    }

    @Test
    void testRemoveInvite_HostWithMultipleHosts_AllowsRemoval() {
        // Arrange
        Invite hostInvite1 = new Invite(eventId, userId, Invite.InviteType.HOST);
        hostInvite1.setId(inviteId);
        Invite hostInvite2 = new Invite(eventId, UUID.randomUUID(), Invite.InviteType.HOST);
        
        when(inviteRepository.findById(inviteId)).thenReturn(Optional.of(hostInvite1));
        when(inviteRepository.findByEventId(eventId)).thenReturn(Arrays.asList(hostInvite1, hostInvite2));
        
        // Act
        inviteService.removeInvite(inviteId);
        
        // Assert
        verify(inviteRepository).delete(hostInvite1);
    }

    @Test
    void testUpdateInviteResponse_ValidRequest_UpdatesResponse() {
        // Arrange
        Invite.InviteResponse response = Invite.InviteResponse.GOING;
        when(inviteRepository.findById(inviteId)).thenReturn(Optional.of(testInvite));
        when(inviteRepository.save(testInvite)).thenReturn(testInvite);
        
        // Act
        Invite result = inviteService.updateInviteResponse(inviteId, userId, response);
        
        // Assert
        assertEquals(testInvite, result);
        assertEquals(response, testInvite.getResponse());
        verify(inviteRepository).findById(inviteId);
        verify(inviteRepository).save(testInvite);
    }

    @Test
    void testUpdateInviteResponse_InviteNotFound_ThrowsException() {
        // Arrange
        when(inviteRepository.findById(inviteId)).thenReturn(Optional.empty());
        
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> inviteService.updateInviteResponse(inviteId, userId, Invite.InviteResponse.GOING));
        
        assertEquals("Invite not found", exception.getMessage());
        verify(inviteRepository).findById(inviteId);
        verify(inviteRepository, never()).save(any());
    }

    @Test
    void testUpdateInviteResponse_WrongUser_ThrowsException() {
        // Arrange
        UUID differentUserId = UUID.randomUUID();
        when(inviteRepository.findById(inviteId)).thenReturn(Optional.of(testInvite));
        
        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> inviteService.updateInviteResponse(inviteId, differentUserId, Invite.InviteResponse.GOING));
        
        assertEquals("You can only edit your own invite response", exception.getMessage());
        verify(inviteRepository).findById(inviteId);
        verify(inviteRepository, never()).save(any());
    }

    @Test
    void testGetInvitesForEvent_UserNotFound_UsesFallbackUser() {
        // Arrange
        List<Invite> invites = Arrays.asList(testInvite);
        when(inviteRepository.findByEventId(eventId)).thenReturn(invites);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        
        // Act
        List<InviteResponse> result = inviteService.getInvitesForEvent(eventId);
        
        // Assert
        assertEquals(1, result.size());
        InviteResponse response = result.get(0);
        assertNull(response.getUserPhoneNumber());
        assertNull(response.getUsername());
        assertNull(response.getDisplayName());
        
        verify(inviteRepository).findByEventId(eventId);
        verify(userRepository).findById(userId);
    }
}