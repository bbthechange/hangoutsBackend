package com.bbthechange.inviter.integration;

import com.bbthechange.inviter.config.BaseIntegrationTest;
import com.bbthechange.inviter.model.Event;
import com.bbthechange.inviter.model.EventVisibility;
import com.bbthechange.inviter.model.Invite;
import com.bbthechange.inviter.model.Invite.InviteResponse;
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
 * Integration tests for InviteController endpoints
 * Tests complete HTTP request/response cycles with real DynamoDB via TestContainers
 */
@DisplayName("InviteController Integration Tests")
public class InviteControllerIntegrationTest extends BaseIntegrationTest {

    @Nested
    @DisplayName("GET /events/{eventId}/invites - List Invites Integration Tests")
    class ListInvitesIntegrationTests {

        @Test
        @DisplayName("Should list invites for event when user is host")
        void getInvites_Success_AsHost() throws Exception {
            // Arrange
            String hostToken = createDefaultUserAndGetToken();
            String inviteeToken = createSecondUserAndGetToken();
            
            User host = getUserByPhoneNumber("+1234567890");
            User invitee = getUserByPhoneNumber("+0987654321");
            
            Event savedEvent = createTestEventWithHost("Test Event", host);
            
            Invite invite = createTestInvite(savedEvent, invitee);
            inviteRepository.save(invite);

            // Act & Assert
            MvcResult result = mockMvc.perform(get("/events/" + savedEvent.getId() + "/invites")
                    .header("Authorization", authHeader(hostToken)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andReturn();

            // Verify response contains invite details
            String responseBody = result.getResponse().getContentAsString();
            List<Invite> invites = objectMapper.readValue(responseBody, new TypeReference<List<Invite>>() {});
            
            assertEquals(1, invites.size());
            Invite returnedInvite = invites.get(0);
            assertEquals(invitee.getId(), returnedInvite.getUserId());
            assertEquals(savedEvent.getId(), returnedInvite.getEventId());
            assertEquals(InviteResponse.NOT_RESPONDED, returnedInvite.getResponse());
        }

        @Test
        @DisplayName("Should reject listing invites when user is not authorized")
        void getInvites_Forbidden_NotAuthorized() throws Exception {
            // Arrange
            String hostToken = createDefaultUserAndGetToken();
            String unauthorizedToken = createSecondUserAndGetToken();
            
            User host = getUserByPhoneNumber("+1234567890");
            Event savedEvent = createTestEventWithHost("Private Event", host);

            // Act & Assert
            mockMvc.perform(get("/events/" + savedEvent.getId() + "/invites")
                    .header("Authorization", authHeader(unauthorizedToken)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("POST /events/{eventId}/invites - Add Invite Integration Tests")
    class AddInviteIntegrationTests {

        @Test
        @DisplayName("Should add invite successfully when user is host")
        void addInvite_Success_AsHost() throws Exception {
            // Arrange
            String hostToken = createDefaultUserAndGetToken();
            String inviteeToken = createSecondUserAndGetToken();
            
            User host = getUserByPhoneNumber("+1234567890");
            User invitee = getUserByPhoneNumber("+0987654321");
            
            Event savedEvent = createTestEventWithHost("Invitation Event", host);

            Map<String, String> inviteRequest = Map.of("phoneNumber", invitee.getPhoneNumber());

            // Act & Assert
            MvcResult result = mockMvc.perform(post("/events/" + savedEvent.getId() + "/invites")
                    .header("Authorization", authHeader(hostToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(inviteRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.inviteId").exists())
                    .andReturn();

            // Verify invite was persisted to DynamoDB
            String responseBody = result.getResponse().getContentAsString();
            Map<String, String> response = objectMapper.readValue(responseBody, new TypeReference<Map<String, String>>() {});
            String inviteId = response.get("inviteId");
            
            assertNotNull(inviteId);
            
            Invite savedInvite = inviteRepository.findById(UUID.fromString(inviteId)).orElse(null);
            assertNotNull(savedInvite);
            assertEquals(invitee.getId(), savedInvite.getUserId());
            assertEquals(savedEvent.getId(), savedInvite.getEventId());
            assertEquals(InviteResponse.NOT_RESPONDED, savedInvite.getResponse());
        }

        @Test
        @DisplayName("Should reject adding invite when user is not host")
        void addInvite_Forbidden_NotHost() throws Exception {
            // Arrange
            String hostToken = createDefaultUserAndGetToken();
            String nonHostToken = createSecondUserAndGetToken();
            
            User host = getUserByPhoneNumber("+1234567890");
            User nonHost = getUserByPhoneNumber("+0987654321");
            
            Event savedEvent = createTestEventWithHost("Host Only Event", host);

            Map<String, String> inviteRequest = Map.of("phoneNumber", "+1555000001");

            // Act & Assert
            mockMvc.perform(post("/events/" + savedEvent.getId() + "/invites")
                    .header("Authorization", authHeader(nonHostToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(inviteRequest)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should handle inviting non-existent user")
        void addInvite_NotFound_NonExistentUser() throws Exception {
            // Arrange
            String hostToken = createDefaultUserAndGetToken();
            User host = getUserByPhoneNumber("+1234567890");
            
            Event savedEvent = createTestEventWithHost("Test Event", host);

            Map<String, String> inviteRequest = Map.of("phoneNumber", "+9999999999");

            // Act & Assert
            mockMvc.perform(post("/events/" + savedEvent.getId() + "/invites")
                    .header("Authorization", authHeader(hostToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(inviteRequest)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PUT /events/{eventId}/invites/{inviteId} - Update Invite Response Integration Tests")
    class UpdateInviteResponseIntegrationTests {

        @Test
        @DisplayName("Should update invite response when user owns the invite")
        void updateInviteResponse_Success_AsInvitee() throws Exception {
            // Arrange
            String hostToken = createDefaultUserAndGetToken();
            String inviteeToken = createSecondUserAndGetToken();
            
            User host = getUserByPhoneNumber("+1234567890");
            User invitee = getUserByPhoneNumber("+0987654321");
            
            Event savedEvent = createTestEventWithHost("Response Event", host);
            
            Invite invite = createTestInvite(savedEvent, invitee);
            Invite savedInvite = inviteRepository.save(invite);

            Map<String, String> responseUpdate = Map.of("response", "GOING");

            // Act & Assert
            mockMvc.perform(put("/events/" + savedEvent.getId() + "/invites/" + savedInvite.getId())
                    .header("Authorization", authHeader(inviteeToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(responseUpdate)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.response").value("GOING"));

            // Verify invite response was updated in DynamoDB
            Invite updatedInvite = inviteRepository.findById(savedInvite.getId()).orElse(null);
            assertNotNull(updatedInvite);
            assertEquals(InviteResponse.GOING, updatedInvite.getResponse());
        }

        @Test
        @DisplayName("Should update invite response to DECLINED")
        void updateInviteResponse_Success_Declined() throws Exception {
            // Arrange
            String hostToken = createDefaultUserAndGetToken();
            String inviteeToken = createSecondUserAndGetToken();
            
            User host = getUserByPhoneNumber("+1234567890");
            User invitee = getUserByPhoneNumber("+0987654321");
            
            Event savedEvent = createTestEventWithHost("Decline Event", host);
            
            Invite invite = createTestInvite(savedEvent, invitee);
            Invite savedInvite = inviteRepository.save(invite);

            Map<String, String> responseUpdate = Map.of("response", "NOT_GOING");

            // Act & Assert
            mockMvc.perform(put("/events/" + savedEvent.getId() + "/invites/" + savedInvite.getId())
                    .header("Authorization", authHeader(inviteeToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(responseUpdate)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.response").value("NOT_GOING"));

            // Verify invite response was updated in DynamoDB
            Invite updatedInvite = inviteRepository.findById(savedInvite.getId()).orElse(null);
            assertNotNull(updatedInvite);
            assertEquals(InviteResponse.NOT_GOING, updatedInvite.getResponse());
        }

        @Test
        @DisplayName("Should reject response update when user doesn't own invite")
        void updateInviteResponse_Forbidden_NotOwner() throws Exception {
            // Arrange
            String hostToken = createDefaultUserAndGetToken();
            String inviteeToken = createSecondUserAndGetToken();
            String unauthorizedToken = createUserAndGetToken("+1555000001", "unauthorized", "Unauthorized User", "password");
            
            User host = getUserByPhoneNumber("+1234567890");
            User invitee = getUserByPhoneNumber("+0987654321");
            
            Event savedEvent = createTestEventWithHost("Protected Event", host);
            
            Invite invite = createTestInvite(savedEvent, invitee);
            Invite savedInvite = inviteRepository.save(invite);

            Map<String, String> responseUpdate = Map.of("response", "GOING");

            // Act & Assert
            mockMvc.perform(put("/events/" + savedEvent.getId() + "/invites/" + savedInvite.getId())
                    .header("Authorization", authHeader(unauthorizedToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(responseUpdate)))
                    .andExpect(status().isForbidden());

            // Verify invite response was not changed
            Invite unchangedInvite = inviteRepository.findById(savedInvite.getId()).orElse(null);
            assertNotNull(unchangedInvite);
            assertEquals(InviteResponse.NOT_RESPONDED, unchangedInvite.getResponse());
        }
    }

    @Nested
    @DisplayName("DELETE /events/{eventId}/invites/{inviteId} - Remove Invite Integration Tests")
    class RemoveInviteIntegrationTests {

        @Test
        @DisplayName("Should remove invite when user is host")
        void removeInvite_Success_AsHost() throws Exception {
            // Arrange
            String hostToken = createDefaultUserAndGetToken();
            String inviteeToken = createSecondUserAndGetToken();
            
            User host = getUserByPhoneNumber("+1234567890");
            User invitee = getUserByPhoneNumber("+0987654321");
            
            Event savedEvent = createTestEventWithHost("Removal Event", host);
            
            Invite invite = createTestInvite(savedEvent, invitee);
            Invite savedInvite = inviteRepository.save(invite);

            // Act & Assert
            mockMvc.perform(delete("/events/" + savedEvent.getId() + "/invites/" + savedInvite.getId())
                    .header("Authorization", authHeader(hostToken)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").exists());

            // Verify invite was removed from DynamoDB
            Invite removedInvite = inviteRepository.findById(savedInvite.getId()).orElse(null);
            assertNull(removedInvite);
        }

        @Test
        @DisplayName("Should reject invite removal when user is not host")
        void removeInvite_Forbidden_NotHost() throws Exception {
            // Arrange
            String hostToken = createDefaultUserAndGetToken();
            String nonHostToken = createSecondUserAndGetToken();
            
            User host = getUserByPhoneNumber("+1234567890");
            User nonHost = getUserByPhoneNumber("+0987654321");
            
            Event savedEvent = createTestEventWithHost("Protected Event", host);
            
            Invite invite = createTestInvite(savedEvent, nonHost);
            Invite savedInvite = inviteRepository.save(invite);

            // Act & Assert
            mockMvc.perform(delete("/events/" + savedEvent.getId() + "/invites/" + savedInvite.getId())
                    .header("Authorization", authHeader(nonHostToken)))
                    .andExpect(status().isForbidden());

            // Verify invite was not removed
            Invite stillExistingInvite = inviteRepository.findById(savedInvite.getId()).orElse(null);
            assertNotNull(stillExistingInvite);
        }
    }

    /**
     * Helper method to create test event with specified host
     */
    private Event createTestEvent(String title, User host) {
        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setName(title);
        event.setDescription("Test Description");
        event.setStartTime(LocalDateTime.now().plusDays(1));
        event.setEndTime(LocalDateTime.now().plusDays(1).plusHours(2));
        event.setVisibility(EventVisibility.INVITE_ONLY);
        return event;
    }
    
    /**
     * Helper method to create test event with host invite
     */
    private Event createTestEventWithHost(String title, User host) {
        Event event = createTestEvent(title, host);
        Event savedEvent = eventRepository.save(event);
        
        // Create host invite
        Invite hostInvite = new Invite(savedEvent.getId(), host.getId(), Invite.InviteType.HOST);
        inviteRepository.save(hostInvite);
        
        return savedEvent;
    }

    /**
     * Helper method to create test invite
     */
    private Invite createTestInvite(Event event, User invitee) {
        Invite invite = new Invite();
        invite.setId(UUID.randomUUID());
        invite.setEventId(event.getId());
        invite.setUserId(invitee.getId());
        invite.setResponse(InviteResponse.NOT_RESPONDED);
        return invite;
    }
}