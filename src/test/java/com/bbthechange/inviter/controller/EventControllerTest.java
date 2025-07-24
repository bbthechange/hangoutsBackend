package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.Address;
import com.bbthechange.inviter.dto.CreateEventRequest;
import com.bbthechange.inviter.dto.UpdateEventRequest;
import com.bbthechange.inviter.model.Event;
import com.bbthechange.inviter.model.EventVisibility;
import com.bbthechange.inviter.repository.UserRepository;
import com.bbthechange.inviter.service.EventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for EventController
 * 
 * Test Coverage:
 * - POST /events/new - Event creation functionality
 * - GET /events - List user events functionality  
 * - GET /events/{id} - Get specific event functionality
 * - PUT /events/{id} - Update event functionality
 * - DELETE /events/{id} - Delete event functionality
 * - Authentication and authorization scenarios
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventController Tests")
class EventControllerTest {

    @Mock
    private EventService eventService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private EventController eventController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    
    private UUID testUserId;
    private UUID testEventId;
    private Event testEvent;
    private CreateEventRequest createEventRequest;
    private UpdateEventRequest updateEventRequest;
    private Address testAddress;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(eventController).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        testUserId = UUID.randomUUID();
        testEventId = UUID.randomUUID();
        
        testAddress = new Address();
        testAddress.setStreetAddress("123 Test St");
        testAddress.setCity("Test City");
        testAddress.setState("TS");
        testAddress.setPostalCode("12345");
        
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
        
        createEventRequest = new CreateEventRequest();
        createEventRequest.setName("Test Event");
        createEventRequest.setDescription("Test Description");
        createEventRequest.setStartTime(LocalDateTime.now().plusDays(1));
        createEventRequest.setEndTime(LocalDateTime.now().plusDays(1).plusHours(2));
        createEventRequest.setLocation(testAddress);
        createEventRequest.setVisibility(EventVisibility.INVITE_ONLY);
        createEventRequest.setMainImagePath("/images/test.jpg");
        createEventRequest.setHostUserIds(Arrays.asList(testUserId));
        createEventRequest.setInvitePhoneNumbers(Arrays.asList("+1234567890"));
        
        updateEventRequest = new UpdateEventRequest();
        updateEventRequest.setName("Updated Event");
        updateEventRequest.setDescription("Updated Description");
    }

    @Nested
    @DisplayName("POST /events/new - Create Event Tests")
    class CreateEventTests {

        @Test
        @DisplayName("Should create event successfully with all fields")
        void createEvent_Success_AllFields() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(eventService.createEventWithInvites(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(testEvent);

            // Act
            ResponseEntity<Map<String, UUID>> response = eventController.createEvent(createEventRequest, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(testEventId, response.getBody().get("id"));
            
            verify(eventService).createEventWithInvites(
                eq("Test Event"),
                eq("Test Description"),
                eq(createEventRequest.getStartTime()),
                eq(createEventRequest.getEndTime()),
                eq(testAddress),
                eq(EventVisibility.INVITE_ONLY),
                eq("/images/test.jpg"),
                eq(Arrays.asList(testUserId)),
                eq(Arrays.asList("+1234567890"))
            );
        }

        @Test
        @DisplayName("Should create event with default visibility when not specified")
        void createEvent_Success_DefaultVisibility() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            createEventRequest.setVisibility(null);
            when(eventService.createEventWithInvites(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(testEvent);

            // Act
            ResponseEntity<Map<String, UUID>> response = eventController.createEvent(createEventRequest, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            verify(eventService).createEventWithInvites(
                any(), any(), any(), any(), any(),
                eq(EventVisibility.INVITE_ONLY), // Should default to INVITE_ONLY
                any(), any(), any()
            );
        }

        @Test
        @DisplayName("Should create event with user as host when no hosts specified")
        void createEvent_Success_DefaultHost() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            createEventRequest.setHostUserIds(null);
            when(eventService.createEventWithInvites(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(testEvent);

            // Act
            ResponseEntity<Map<String, UUID>> response = eventController.createEvent(createEventRequest, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            verify(eventService).createEventWithInvites(
                any(), any(), any(), any(), any(), any(), any(),
                eq(Arrays.asList(testUserId)), // Should default to creating user
                any()
            );
        }

        @Test
        @DisplayName("Should return UNAUTHORIZED when userId is null")
        void createEvent_Unauthorized_NullUserId() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(null);

            // Act
            ResponseEntity<Map<String, UUID>> response = eventController.createEvent(createEventRequest, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            verifyNoInteractions(eventService);
        }
    }

    @Nested
    @DisplayName("GET /events - List Events Tests")
    class ListEventsTests {

        @Test
        @DisplayName("Should return user events successfully")
        void getAllEvents_Success() {
            // Arrange
            List<Event> userEvents = Arrays.asList(testEvent);
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(eventService.getEventsForUser(testUserId)).thenReturn(userEvents);

            // Act
            ResponseEntity<List<Event>> response = eventController.getAllEvents(httpServletRequest);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(userEvents, response.getBody());
            verify(eventService).getEventsForUser(testUserId);
        }

        @Test
        @DisplayName("Should return empty list when user has no events")
        void getAllEvents_Success_EmptyList() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(eventService.getEventsForUser(testUserId)).thenReturn(new ArrayList<>());

            // Act
            ResponseEntity<List<Event>> response = eventController.getAllEvents(httpServletRequest);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertTrue(response.getBody().isEmpty());
        }

        @Test
        @DisplayName("Should return UNAUTHORIZED when userId is null")
        void getAllEvents_Unauthorized() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(null);

            // Act
            ResponseEntity<List<Event>> response = eventController.getAllEvents(httpServletRequest);

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            verifyNoInteractions(eventService);
        }
    }

    @Nested
    @DisplayName("GET /events/{id} - Get Event Tests")
    class GetEventTests {

        @Test
        @DisplayName("Should return event successfully when user has access")
        void getEvent_Success() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(eventService.getEventForUser(testEventId, testUserId)).thenReturn(Optional.of(testEvent));

            // Act
            ResponseEntity<Event> response = eventController.getEvent(testEventId, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(testEvent, response.getBody());
            verify(eventService).getEventForUser(testEventId, testUserId);
        }

        @Test
        @DisplayName("Should return NOT_FOUND when event doesn't exist or user has no access")
        void getEvent_NotFound() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(eventService.getEventForUser(testEventId, testUserId)).thenReturn(Optional.empty());

            // Act
            ResponseEntity<Event> response = eventController.getEvent(testEventId, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            verify(eventService).getEventForUser(testEventId, testUserId);
        }

        @Test
        @DisplayName("Should return UNAUTHORIZED when userId is null")
        void getEvent_Unauthorized() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(null);

            // Act
            ResponseEntity<Event> response = eventController.getEvent(testEventId, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            verifyNoInteractions(eventService);
        }
    }

    @Nested
    @DisplayName("PUT /events/{id} - Update Event Tests")
    class UpdateEventTests {

        @Test
        @DisplayName("Should update event successfully when user is host")
        void updateEvent_Success() {
            // Arrange
            Event updatedEvent = new Event(testEvent.getName(), testEvent.getDescription(),
                testEvent.getStartTime(), testEvent.getEndTime(), testEvent.getLocation(),
                testEvent.getVisibility(), testEvent.getMainImagePath(), testEvent.getHosts());
            updatedEvent.setId(testEventId);
            updatedEvent.setName("Updated Event");
            
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(eventService.isUserHostOfEvent(testUserId, testEventId)).thenReturn(true);
            when(eventService.updateEvent(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(updatedEvent);

            // Act
            ResponseEntity<Event> response = eventController.updateEvent(testEventId, updateEventRequest, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(updatedEvent, response.getBody());
            verify(eventService).isUserHostOfEvent(testUserId, testEventId);
            verify(eventService).updateEvent(
                eq(testEventId),
                eq("Updated Event"),
                eq("Updated Description"),
                eq(updateEventRequest.getStartTime()),
                eq(updateEventRequest.getEndTime()),
                eq(updateEventRequest.getLocation()),
                eq(updateEventRequest.getVisibility()),
                eq(updateEventRequest.getMainImagePath()),
                eq(updateEventRequest.getHostUserIds())
            );
        }

        @Test
        @DisplayName("Should return FORBIDDEN when user is not host")
        void updateEvent_Forbidden_NotHost() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(eventService.isUserHostOfEvent(testUserId, testEventId)).thenReturn(false);

            // Act
            ResponseEntity<Event> response = eventController.updateEvent(testEventId, updateEventRequest, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            verify(eventService).isUserHostOfEvent(testUserId, testEventId);
            verify(eventService, never()).updateEvent(any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should return NOT_FOUND when event doesn't exist")
        void updateEvent_NotFound() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(eventService.isUserHostOfEvent(testUserId, testEventId)).thenReturn(true);
            when(eventService.updateEvent(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Event not found"));

            // Act
            ResponseEntity<Event> response = eventController.updateEvent(testEventId, updateEventRequest, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        }

        @Test
        @DisplayName("Should return UNAUTHORIZED when userId is null")
        void updateEvent_Unauthorized() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(null);

            // Act
            ResponseEntity<Event> response = eventController.updateEvent(testEventId, updateEventRequest, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            verifyNoInteractions(eventService);
        }
    }

    @Nested
    @DisplayName("DELETE /events/{id} - Delete Event Tests")
    class DeleteEventTests {

        @Test
        @DisplayName("Should delete event successfully when user is host")
        void deleteEvent_Success() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(eventService.isUserHostOfEvent(testUserId, testEventId)).thenReturn(true);
            doNothing().when(eventService).deleteEvent(testEventId);

            // Act
            ResponseEntity<Map<String, String>> response = eventController.deleteEvent(testEventId, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Event deleted successfully", response.getBody().get("message"));
            verify(eventService).isUserHostOfEvent(testUserId, testEventId);
            verify(eventService).deleteEvent(testEventId);
        }

        @Test
        @DisplayName("Should return FORBIDDEN when user is not host")
        void deleteEvent_Forbidden_NotHost() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(eventService.isUserHostOfEvent(testUserId, testEventId)).thenReturn(false);

            // Act
            ResponseEntity<Map<String, String>> response = eventController.deleteEvent(testEventId, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            verify(eventService).isUserHostOfEvent(testUserId, testEventId);
            verify(eventService, never()).deleteEvent(any());
        }

        @Test
        @DisplayName("Should return NOT_FOUND when event doesn't exist")
        void deleteEvent_NotFound() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(eventService.isUserHostOfEvent(testUserId, testEventId)).thenReturn(true);
            doThrow(new IllegalArgumentException("Event not found")).when(eventService).deleteEvent(testEventId);

            // Act
            ResponseEntity<Map<String, String>> response = eventController.deleteEvent(testEventId, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            verify(eventService).deleteEvent(testEventId);
        }

        @Test
        @DisplayName("Should return UNAUTHORIZED when userId is null")
        void deleteEvent_Unauthorized() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(null);

            // Act
            ResponseEntity<Map<String, String>> response = eventController.deleteEvent(testEventId, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            verifyNoInteractions(eventService);
        }
    }
}