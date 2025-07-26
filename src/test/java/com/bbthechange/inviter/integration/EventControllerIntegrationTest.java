package com.bbthechange.inviter.integration;

import com.bbthechange.inviter.config.BaseIntegrationTest;
import com.bbthechange.inviter.model.Event;
import com.bbthechange.inviter.model.EventVisibility;
import com.bbthechange.inviter.model.User;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for EventController endpoints
 * Tests complete HTTP request/response cycles with real DynamoDB via TestContainers
 */
@DisplayName("EventController Integration Tests")
public class EventControllerIntegrationTest extends BaseIntegrationTest {

    @Nested
    @DisplayName("POST /events/new - Event Creation Integration Tests")
    class EventCreationIntegrationTests {

        @Test
        @DisplayName("Should create new event successfully with valid data")
        void createEvent_Success_ValidData() throws Exception {
            // Arrange
            String token = createDefaultUserAndGetToken();
            
            Event newEvent = new Event();
            newEvent.setName("Integration Test Event");
            newEvent.setDescription("Test event description");
            newEvent.setStartTime(LocalDateTime.now().plusDays(1));
            newEvent.setEndTime(LocalDateTime.now().plusDays(1).plusHours(2));
            newEvent.setVisibility(EventVisibility.INVITE_ONLY);

            // Act & Assert
            MvcResult result = mockMvc.perform(post("/events/new")
                    .header("Authorization", authHeader(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(newEvent)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").exists())
                    .andReturn();

            // Verify event was persisted to DynamoDB
            String responseBody = result.getResponse().getContentAsString();
            Map<String, String> response = objectMapper.readValue(responseBody, new TypeReference<Map<String, String>>() {});
            String eventId = response.get("id");
            
            assertNotNull(eventId);
            
            // Verify event can be retrieved
            Event savedEvent = eventRepository.findById(UUID.fromString(eventId)).orElse(null);
            assertNotNull(savedEvent);
            assertEquals("Integration Test Event", savedEvent.getName());
            assertEquals("Test event description", savedEvent.getDescription());
            assertEquals(EventVisibility.INVITE_ONLY, savedEvent.getVisibility());
            assertNotNull(savedEvent.getHosts());
            assertFalse(savedEvent.getHosts().isEmpty());
        }

        @Test
        @DisplayName("Should reject event creation without authentication")
        void createEvent_Unauthorized_NoAuth() throws Exception {
            // Arrange
            Event newEvent = new Event();
            newEvent.setName("Unauthorized Event");

            // Act & Assert
            mockMvc.perform(post("/events/new")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(newEvent)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should reject event creation with invalid JWT token")
        void createEvent_Unauthorized_InvalidToken() throws Exception {
            // Arrange
            Event newEvent = new Event();
            newEvent.setName("Invalid Token Event");

            // Act & Assert
            mockMvc.perform(post("/events/new")
                    .header("Authorization", "Bearer invalid-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(newEvent)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /events - Event Listing Integration Tests")
    class EventListingIntegrationTests {

        @Test
        @DisplayName("Should return empty list when user has no events")
        void getEvents_Success_EmptyList() throws Exception {
            // Arrange
            String token = createDefaultUserAndGetToken();

            // Act & Assert
            mockMvc.perform(get("/events")
                    .header("Authorization", authHeader(token)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("Should return user's events when they exist")
        void getEvents_Success_WithEvents() throws Exception {
            // Arrange
            String token = createDefaultUserAndGetToken();
            
            // Create multiple events
            User user = getUserByPhoneNumber("+1234567890");
            Event event1 = createTestEvent("Event 1", "Description 1");
            Event event2 = createTestEvent("Event 2", "Description 2");
            
            event1.setHosts(List.of(user.getId()));
            event2.setHosts(List.of(user.getId()));
            
            eventRepository.save(event1);
            eventRepository.save(event2);

            // Act & Assert
            MvcResult result = mockMvc.perform(get("/events")
                    .header("Authorization", authHeader(token)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andReturn();

            // Verify response contains both events
            String responseBody = result.getResponse().getContentAsString();
            List<Event> events = objectMapper.readValue(responseBody, new TypeReference<List<Event>>() {});
            
            assertEquals(2, events.size());
            assertTrue(events.stream().anyMatch(e -> "Event 1".equals(e.getName())));
            assertTrue(events.stream().anyMatch(e -> "Event 2".equals(e.getName())));
        }

        @Test
        @DisplayName("Should reject event listing without authentication")
        void getEvents_Unauthorized_NoAuth() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/events"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /events/{id} - Event Retrieval Integration Tests")
    class EventRetrievalIntegrationTests {

        @Test
        @DisplayName("Should retrieve event by ID when user is authorized")
        void getEvent_Success_Authorized() throws Exception {
            // Arrange
            String token = createDefaultUserAndGetToken();
            User user = getUserByPhoneNumber("+1234567890");
            
            Event event = createTestEvent("Retrievable Event", "Test Description");
            event.setHosts(List.of(user.getId()));
            Event savedEvent = eventRepository.save(event);

            // Act & Assert
            mockMvc.perform(get("/events/" + savedEvent.getId())
                    .header("Authorization", authHeader(token)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(savedEvent.getId().toString()))
                    .andExpect(jsonPath("$.name").value("Retrievable Event"))
                    .andExpect(jsonPath("$.description").value("Test Description"));
        }

        @Test
        @DisplayName("Should return 404 for non-existent event")
        void getEvent_NotFound_NonExistent() throws Exception {
            // Arrange
            String token = createDefaultUserAndGetToken();
            UUID nonExistentId = UUID.randomUUID();

            // Act & Assert
            mockMvc.perform(get("/events/" + nonExistentId)
                    .header("Authorization", authHeader(token)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should reject retrieval without authentication")
        void getEvent_Unauthorized_NoAuth() throws Exception {
            // Arrange
            UUID eventId = UUID.randomUUID();

            // Act & Assert
            mockMvc.perform(get("/events/" + eventId))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("PUT /events/{id} - Event Update Integration Tests")
    class EventUpdateIntegrationTests {

        @Test
        @DisplayName("Should update event when user is host")
        void updateEvent_Success_AsHost() throws Exception {
            // Arrange
            String token = createDefaultUserAndGetToken();
            User user = getUserByPhoneNumber("+1234567890");
            
            Event originalEvent = createTestEvent("Original Title", "Original Description");
            originalEvent.setHosts(List.of(user.getId()));
            Event savedEvent = eventRepository.save(originalEvent);

            Event updatedEvent = new Event();
            updatedEvent.setName("Updated Title");
            updatedEvent.setDescription("Updated Description");
            updatedEvent.setStartTime(LocalDateTime.now().plusDays(2));
            updatedEvent.setEndTime(LocalDateTime.now().plusDays(2).plusHours(2));
            updatedEvent.setVisibility(EventVisibility.PUBLIC);

            // Act & Assert
            mockMvc.perform(put("/events/" + savedEvent.getId())
                    .header("Authorization", authHeader(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updatedEvent)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.name").value("Updated Title"))
                    .andExpect(jsonPath("$.description").value("Updated Description"));

            // Verify event was updated in DynamoDB
            Event retrievedEvent = eventRepository.findById(savedEvent.getId()).orElse(null);
            assertNotNull(retrievedEvent);
            assertEquals("Updated Title", retrievedEvent.getName());
            assertEquals("Updated Description", retrievedEvent.getDescription());
        }

        @Test
        @DisplayName("Should reject update when user is not host")
        void updateEvent_Forbidden_NotHost() throws Exception {
            // Arrange
            String hostToken = createDefaultUserAndGetToken();
            String nonHostToken = createSecondUserAndGetToken();
            
            User hostUser = getUserByPhoneNumber("+1234567890");
            
            Event event = createTestEvent("Host Event", "Host Description");
            event.setHosts(List.of(hostUser.getId()));
            Event savedEvent = eventRepository.save(event);

            Event updatedEvent = new Event();
            updatedEvent.setName("Hacked Title");

            // Act & Assert
            mockMvc.perform(put("/events/" + savedEvent.getId())
                    .header("Authorization", authHeader(nonHostToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updatedEvent)))
                    .andExpect(status().isForbidden());

            // Verify event was not modified
            Event retrievedEvent = eventRepository.findById(savedEvent.getId()).orElse(null);
            assertNotNull(retrievedEvent);
            assertEquals("Host Event", retrievedEvent.getName()); // Should remain unchanged
        }
    }

    /**
     * Helper method to create test event with default values
     */
    private Event createTestEvent(String name, String description) {
        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setName(name);
        event.setDescription(description);
        event.setStartTime(LocalDateTime.now().plusDays(1));
        event.setEndTime(LocalDateTime.now().plusDays(1).plusHours(2));
        event.setVisibility(EventVisibility.INVITE_ONLY);
        return event;
    }
}