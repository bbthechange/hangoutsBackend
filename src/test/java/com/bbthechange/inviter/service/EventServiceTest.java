package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.Address;
import com.bbthechange.inviter.model.Event;
import com.bbthechange.inviter.model.EventVisibility;
import com.bbthechange.inviter.model.Invite;
import com.bbthechange.inviter.model.User;
import com.bbthechange.inviter.repository.EventRepository;
import com.bbthechange.inviter.repository.InviteRepository;
import com.bbthechange.inviter.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EventService
 * 
 * Test Coverage:
 * - createEventWithInvites - Event creation with invite management
 * - getEventsForUser - Retrieve events for a specific user
 * - getEventForUser - Get specific event with access control
 * - isUserHostOfEvent - Host validation functionality
 * - updateEvent - Event modification functionality
 * - deleteEvent - Event deletion with cascade functionality
 * - findOrCreateUserByPhoneNumber - User lookup/creation for invites
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventService Tests")
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private InviteRepository inviteRepository;

    @InjectMocks
    private EventService eventService;
    
    private UUID testUserId;
    private UUID testEventId;
    private UUID otherUserId;
    private Event testEvent;
    private User testUser;
    private User otherUser;
    private Invite testInvite;
    private Address testAddress;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testEventId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        
        testAddress = new Address();
        testAddress.setStreetAddress("123 Test St");
        testAddress.setCity("Test City");
        testAddress.setState("TS");
        testAddress.setPostalCode("12345");
        
        testUser = new User("+1234567890", "testuser", "password");
        testUser.setId(testUserId);
        
        otherUser = new User("+0987654321", "otheruser", "password");
        otherUser.setId(otherUserId);
        
        testEvent = new Event(
            "Test Event",
            "Test Description",
            LocalDateTime.now().plusDays(1),
            LocalDateTime.now().plusDays(1).plusHours(2),
            testAddress,
            EventVisibility.INVITE_ONLY,
            "/images/test.jpg",
            Arrays.asList(testUserId)
        );
        testEvent.setId(testEventId);
        
        testInvite = new Invite(testEventId, testUserId);
    }

    @Nested
    @DisplayName("createEventWithInvites - Event Creation Tests")
    class CreateEventWithInvitesTests {

        @Test
        @DisplayName("Should create event and invites successfully")
        void createEventWithInvites_Success() {
            // Arrange
            List<String> phoneNumbers = Arrays.asList("+1234567890", "+0987654321");
            when(eventRepository.save(any(Event.class))).thenReturn(testEvent);
            when(userRepository.findByPhoneNumber("+1234567890")).thenReturn(Optional.of(testUser));
            when(userRepository.findByPhoneNumber("+0987654321")).thenReturn(Optional.of(otherUser));
            when(inviteRepository.save(any(Invite.class))).thenReturn(testInvite);

            // Act
            Event result = eventService.createEventWithInvites(
                "Test Event",
                "Test Description",
                testEvent.getStartTime(),
                testEvent.getEndTime(),
                testAddress,
                EventVisibility.INVITE_ONLY,
                "/images/test.jpg",
                Arrays.asList(testUserId),
                phoneNumbers
            );

            // Assert
            assertNotNull(result);
            assertEquals("Test Event", result.getName());
            verify(eventRepository).save(any(Event.class));
            verify(inviteRepository, times(2)).save(any(Invite.class));
            verify(userRepository).findByPhoneNumber("+1234567890");
            verify(userRepository).findByPhoneNumber("+0987654321");
        }

        @Test
        @DisplayName("Should create new users for unknown phone numbers")
        void createEventWithInvites_CreatesNewUsers() {
            // Arrange
            List<String> phoneNumbers = Arrays.asList("+1111111111");
            User newUser = new User("+1111111111", null, null);
            
            when(eventRepository.save(any(Event.class))).thenReturn(testEvent);
            when(userRepository.findByPhoneNumber("+1111111111")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenReturn(newUser);
            when(inviteRepository.save(any(Invite.class))).thenReturn(testInvite);

            // Act
            Event result = eventService.createEventWithInvites(
                "Test Event",
                "Test Description",
                testEvent.getStartTime(),
                testEvent.getEndTime(),
                testAddress,
                EventVisibility.INVITE_ONLY,
                "/images/test.jpg",
                Arrays.asList(testUserId),
                phoneNumbers
            );

            // Assert
            assertNotNull(result);
            verify(userRepository).findByPhoneNumber("+1111111111");
            verify(userRepository).save(argThat(user -> 
                "+1111111111".equals(user.getPhoneNumber()) && 
                user.getDisplayName() == null && 
                user.getPassword() == null
            ));
            verify(inviteRepository).save(any(Invite.class));
        }

        @Test
        @DisplayName("Should handle empty invite list")
        void createEventWithInvites_EmptyInviteList() {
            // Arrange
            when(eventRepository.save(any(Event.class))).thenReturn(testEvent);

            // Act
            Event result = eventService.createEventWithInvites(
                "Test Event",
                "Test Description",
                testEvent.getStartTime(),
                testEvent.getEndTime(),
                testAddress,
                EventVisibility.INVITE_ONLY,
                "/images/test.jpg",
                Arrays.asList(testUserId),
                new ArrayList<>()
            );

            // Assert
            assertNotNull(result);
            verify(eventRepository).save(any(Event.class));
            verify(inviteRepository, never()).save(any(Invite.class));
        }
    }

    @Nested
    @DisplayName("getEventsForUser - List User Events Tests")
    class GetEventsForUserTests {

        @Test
        @DisplayName("Should return events where user is invited")
        void getEventsForUser_InvitedEvents() {
            // Arrange
            List<Invite> userInvites = Arrays.asList(testInvite);
            List<Event> allEvents = Arrays.asList();
            
            when(inviteRepository.findByUserId(testUserId)).thenReturn(userInvites);
            when(eventRepository.findById(testEventId)).thenReturn(Optional.of(testEvent));
            when(eventRepository.findAll()).thenReturn(allEvents);

            // Act
            List<Event> result = eventService.getEventsForUser(testUserId);

            // Assert
            assertEquals(1, result.size());
            assertEquals(testEvent, result.get(0));
            verify(inviteRepository).findByUserId(testUserId);
            verify(eventRepository).findById(testEventId);
        }

        @Test
        @DisplayName("Should return events where user is host")
        void getEventsForUser_HostedEvents() {
            // Arrange
            Event hostedEvent = new Event("Hosted Event", "Description",
                LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(2).plusHours(1),
                testAddress, EventVisibility.PUBLIC, null, Arrays.asList(testUserId));
            
            when(inviteRepository.findByUserId(testUserId)).thenReturn(new ArrayList<>());
            when(eventRepository.findAll()).thenReturn(Arrays.asList(hostedEvent));

            // Act
            List<Event> result = eventService.getEventsForUser(testUserId);

            // Assert
            assertEquals(1, result.size());
            assertEquals(hostedEvent, result.get(0));
            verify(eventRepository).findAll();
        }

        @Test
        @DisplayName("Should not duplicate events where user is both invited and host")
        void getEventsForUser_NoDuplicates() {
            // Arrange
            testEvent.setHosts(Arrays.asList(testUserId)); // User is host
            List<Invite> userInvites = Arrays.asList(testInvite); // User is also invited
            
            when(inviteRepository.findByUserId(testUserId)).thenReturn(userInvites);
            when(eventRepository.findById(testEventId)).thenReturn(Optional.of(testEvent));
            when(eventRepository.findAll()).thenReturn(Arrays.asList(testEvent));

            // Act
            List<Event> result = eventService.getEventsForUser(testUserId);

            // Assert
            assertEquals(1, result.size()); // Should not duplicate
            assertEquals(testEvent, result.get(0));
        }

        @Test
        @DisplayName("Should return empty list when user has no events")
        void getEventsForUser_NoEvents() {
            // Arrange
            when(inviteRepository.findByUserId(testUserId)).thenReturn(new ArrayList<>());
            when(eventRepository.findAll()).thenReturn(new ArrayList<>());

            // Act
            List<Event> result = eventService.getEventsForUser(testUserId);

            // Assert
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("getEventForUser - Access Control Tests")
    class GetEventForUserTests {

        @Test
        @DisplayName("Should return event when user is host")
        void getEventForUser_UserIsHost() {
            // Arrange
            when(eventRepository.findById(testEventId)).thenReturn(Optional.of(testEvent));

            // Act
            Optional<Event> result = eventService.getEventForUser(testEventId, testUserId);

            // Assert
            assertTrue(result.isPresent());
            assertEquals(testEvent, result.get());
            verify(eventRepository).findById(testEventId);
        }

        @Test
        @DisplayName("Should return event when user is invited")
        void getEventForUser_UserIsInvited() {
            // Arrange
            Event eventWithOtherHost = new Event("Other Event", "Description",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2),
                testAddress, EventVisibility.INVITE_ONLY, null, Arrays.asList(otherUserId));
            eventWithOtherHost.setId(testEventId);
            
            when(eventRepository.findById(testEventId)).thenReturn(Optional.of(eventWithOtherHost));
            when(inviteRepository.findByUserId(testUserId)).thenReturn(Arrays.asList(testInvite));

            // Act
            Optional<Event> result = eventService.getEventForUser(testEventId, testUserId);

            // Assert
            assertTrue(result.isPresent());
            assertEquals(eventWithOtherHost, result.get());
            verify(inviteRepository).findByUserId(testUserId);
        }

        @Test
        @DisplayName("Should return empty when user has no access")
        void getEventForUser_NoAccess() {
            // Arrange
            Event restrictedEvent = new Event("Restricted Event", "Description",
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(2),
                testAddress, EventVisibility.INVITE_ONLY, null, Arrays.asList(otherUserId));
            restrictedEvent.setId(testEventId);
            
            when(eventRepository.findById(testEventId)).thenReturn(Optional.of(restrictedEvent));
            when(inviteRepository.findByUserId(testUserId)).thenReturn(new ArrayList<>());

            // Act
            Optional<Event> result = eventService.getEventForUser(testEventId, testUserId);

            // Assert
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return empty when event doesn't exist")
        void getEventForUser_EventNotFound() {
            // Arrange
            when(eventRepository.findById(testEventId)).thenReturn(Optional.empty());

            // Act
            Optional<Event> result = eventService.getEventForUser(testEventId, testUserId);

            // Assert
            assertTrue(result.isEmpty());
            verify(eventRepository).findById(testEventId);
        }
    }

    @Nested
    @DisplayName("isUserHostOfEvent - Host Validation Tests")
    class IsUserHostOfEventTests {

        @Test
        @DisplayName("Should return true when user is host")
        void isUserHostOfEvent_UserIsHost() {
            // Arrange
            when(eventRepository.findById(testEventId)).thenReturn(Optional.of(testEvent));

            // Act
            boolean result = eventService.isUserHostOfEvent(testUserId, testEventId);

            // Assert
            assertTrue(result);
        }

        @Test
        @DisplayName("Should return false when user is not host")
        void isUserHostOfEvent_UserIsNotHost() {
            // Arrange
            when(eventRepository.findById(testEventId)).thenReturn(Optional.of(testEvent));

            // Act
            boolean result = eventService.isUserHostOfEvent(otherUserId, testEventId);

            // Assert
            assertFalse(result);
        }

        @Test
        @DisplayName("Should return false when event doesn't exist")
        void isUserHostOfEvent_EventNotFound() {
            // Arrange
            when(eventRepository.findById(testEventId)).thenReturn(Optional.empty());

            // Act
            boolean result = eventService.isUserHostOfEvent(testUserId, testEventId);

            // Assert
            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("updateEvent - Event Modification Tests")
    class UpdateEventTests {

        @Test
        @DisplayName("Should update event successfully with all fields")
        void updateEvent_Success_AllFields() {
            // Arrange
            Event updatedEvent = new Event(testEvent.getName(), testEvent.getDescription(),
                testEvent.getStartTime(), testEvent.getEndTime(), testEvent.getLocation(),
                testEvent.getVisibility(), testEvent.getMainImagePath(), testEvent.getHosts());
            updatedEvent.setId(testEventId);
            
            when(eventRepository.findById(testEventId)).thenReturn(Optional.of(testEvent));
            when(eventRepository.save(any(Event.class))).thenReturn(updatedEvent);

            // Act
            Event result = eventService.updateEvent(
                testEventId,
                "Updated Name",
                "Updated Description",
                LocalDateTime.now().plusDays(2),
                LocalDateTime.now().plusDays(2).plusHours(3),
                testAddress,
                EventVisibility.PUBLIC,
                "/images/updated.jpg",
                Arrays.asList(testUserId, otherUserId)
            );

            // Assert
            assertNotNull(result);
            verify(eventRepository).findById(testEventId);
            verify(eventRepository).save(argThat(event ->
                "Updated Name".equals(event.getName()) &&
                "Updated Description".equals(event.getDescription()) &&
                EventVisibility.PUBLIC.equals(event.getVisibility()) &&
                "/images/updated.jpg".equals(event.getMainImagePath()) &&
                event.getHosts().contains(testUserId) &&
                event.getHosts().contains(otherUserId)
            ));
        }

        @Test
        @DisplayName("Should update only provided fields (partial update)")
        void updateEvent_PartialUpdate() {
            // Arrange
            when(eventRepository.findById(testEventId)).thenReturn(Optional.of(testEvent));
            when(eventRepository.save(any(Event.class))).thenReturn(testEvent);

            // Act - only update name and description
            Event result = eventService.updateEvent(
                testEventId,
                "Updated Name",
                "Updated Description",
                null, null, null, null, null, null
            );

            // Assert
            verify(eventRepository).save(argThat(event ->
                "Updated Name".equals(event.getName()) &&
                "Updated Description".equals(event.getDescription()) &&
                event.getStartTime().equals(testEvent.getStartTime()) && // Should remain unchanged
                event.getEndTime().equals(testEvent.getEndTime()) // Should remain unchanged
            ));
        }

        @Test
        @DisplayName("Should throw exception when event not found")
        void updateEvent_EventNotFound() {
            // Arrange
            when(eventRepository.findById(testEventId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                eventService.updateEvent(testEventId, "Updated", null, null, null, null, null, null, null)
            );
            verify(eventRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteEvent - Event Deletion Tests")
    class DeleteEventTests {

        @Test
        @DisplayName("Should delete event and all related invites successfully")
        void deleteEvent_Success() {
            // Arrange
            List<Invite> eventInvites = Arrays.asList(
                new Invite(testEventId, testUserId),
                new Invite(testEventId, otherUserId)
            );
            
            when(eventRepository.findById(testEventId)).thenReturn(Optional.of(testEvent));
            when(inviteRepository.findByEventId(testEventId)).thenReturn(eventInvites);
            doNothing().when(inviteRepository).delete(any(Invite.class));
            doNothing().when(eventRepository).deleteById(testEventId);

            // Act
            eventService.deleteEvent(testEventId);

            // Assert
            verify(eventRepository).findById(testEventId);
            verify(inviteRepository).findByEventId(testEventId);
            verify(inviteRepository, times(2)).delete(any(Invite.class));
            verify(eventRepository).deleteById(testEventId);
        }

        @Test
        @DisplayName("Should delete event even when no invites exist")
        void deleteEvent_NoInvites() {
            // Arrange
            when(eventRepository.findById(testEventId)).thenReturn(Optional.of(testEvent));
            when(inviteRepository.findByEventId(testEventId)).thenReturn(new ArrayList<>());
            doNothing().when(eventRepository).deleteById(testEventId);

            // Act
            eventService.deleteEvent(testEventId);

            // Assert
            verify(inviteRepository, never()).delete(any(Invite.class));
            verify(eventRepository).deleteById(testEventId);
        }

        @Test
        @DisplayName("Should throw exception when event not found")
        void deleteEvent_EventNotFound() {
            // Arrange
            when(eventRepository.findById(testEventId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () ->
                eventService.deleteEvent(testEventId)
            );
            verify(inviteRepository, never()).findByEventId(any());
            verify(eventRepository, never()).deleteById(any());
        }
    }
}