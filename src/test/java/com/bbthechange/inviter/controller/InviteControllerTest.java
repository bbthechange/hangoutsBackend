package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.AddInviteRequest;
import com.bbthechange.inviter.dto.EditInviteRequest;
import com.bbthechange.inviter.dto.InviteResponse;
import com.bbthechange.inviter.model.Invite;
import com.bbthechange.inviter.service.EventService;
import com.bbthechange.inviter.service.InviteService;
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
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InviteController
 * 
 * Test Coverage:
 * - GET /events/{eventId}/invites - List event invites
 * - POST /events/{eventId}/invites - Add invite to event
 * - DELETE /events/{eventId}/invites/{inviteId} - Remove invite
 * - PUT /events/{eventId}/invites/{inviteId} - Update invite response
 * - Authorization and access control scenarios
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InviteController Tests")
class InviteControllerTest {

    @Mock
    private InviteService inviteService;

    @Mock
    private EventService eventService;

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private InviteController inviteController;

    private UUID testUserId;
    private UUID testEventId;
    private UUID testInviteId;
    private UUID otherUserId;
    private Invite testInvite;
    private AddInviteRequest addInviteRequest;
    private EditInviteRequest editInviteRequest;
    private InviteResponse inviteResponse;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testEventId = UUID.randomUUID();
        testInviteId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();

        testInvite = new Invite(testEventId, testUserId);
        testInvite.setId(testInviteId);
        testInvite.setResponse(Invite.InviteResponse.NOT_RESPONDED);

        addInviteRequest = new AddInviteRequest();
        addInviteRequest.setPhoneNumber("+1234567890");

        editInviteRequest = new EditInviteRequest();
        editInviteRequest.setResponse(Invite.InviteResponse.GOING);

        inviteResponse = new InviteResponse(testInviteId, testEventId, testUserId, "+1234567890", 
                                          "testuser", "Test User", Invite.InviteType.GUEST, 
                                          Invite.InviteResponse.NOT_RESPONDED, false);
    }

    @Nested
    @DisplayName("GET /events/{eventId}/invites - List Invites Tests")
    class GetInvitesTests {

        @Test
        @DisplayName("Should return invites when user is invited to event")
        void getInvitesForEvent_Success_UserInvited() {
            // Arrange
            List<InviteResponse> invites = Arrays.asList(inviteResponse);
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(inviteService.isUserInvitedToEvent(testUserId, testEventId)).thenReturn(true);
            when(inviteService.getInvitesForEvent(testEventId)).thenReturn(invites);

            // Act
            ResponseEntity<List<InviteResponse>> response = inviteController.getInvitesForEvent(testEventId, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(invites, response.getBody());
            verify(inviteService).isUserInvitedToEvent(testUserId, testEventId);
            verify(inviteService).getInvitesForEvent(testEventId);
        }

        @Test
        @DisplayName("Should return invites when user is host of event")
        void getInvitesForEvent_Success_UserIsHost() {
            // Arrange
            List<InviteResponse> invites = Arrays.asList(inviteResponse);
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(inviteService.isUserInvitedToEvent(testUserId, testEventId)).thenReturn(false);
            when(eventService.isUserHostOfEvent(testUserId, testEventId)).thenReturn(true);
            when(inviteService.getInvitesForEvent(testEventId)).thenReturn(invites);

            // Act
            ResponseEntity<List<InviteResponse>> response = inviteController.getInvitesForEvent(testEventId, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(invites, response.getBody());
            verify(eventService).isUserHostOfEvent(testUserId, testEventId);
            verify(inviteService).getInvitesForEvent(testEventId);
        }

        @Test
        @DisplayName("Should return empty list when no invites exist")
        void getInvitesForEvent_Success_EmptyList() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(inviteService.isUserInvitedToEvent(testUserId, testEventId)).thenReturn(true);
            when(inviteService.getInvitesForEvent(testEventId)).thenReturn(new ArrayList<>());

            // Act
            ResponseEntity<List<InviteResponse>> response = inviteController.getInvitesForEvent(testEventId, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertTrue(response.getBody().isEmpty());
        }

        @Test
        @DisplayName("Should return FORBIDDEN when user has no access to event")
        void getInvitesForEvent_Forbidden_NoAccess() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(inviteService.isUserInvitedToEvent(testUserId, testEventId)).thenReturn(false);
            when(eventService.isUserHostOfEvent(testUserId, testEventId)).thenReturn(false);

            // Act
            ResponseEntity<List<InviteResponse>> response = inviteController.getInvitesForEvent(testEventId, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            verify(inviteService, never()).getInvitesForEvent(any());
        }

        @Test
        @DisplayName("Should return UNAUTHORIZED when userId is null")
        void getInvitesForEvent_Unauthorized() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(null);

            // Act
            ResponseEntity<List<InviteResponse>> response = inviteController.getInvitesForEvent(testEventId, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            verifyNoInteractions(inviteService, eventService);
        }
    }

    @Nested
    @DisplayName("POST /events/{eventId}/invites - Add Invite Tests")
    class AddInviteTests {

        @Test
        @DisplayName("Should add invite successfully when user is host")
        void addInviteToEvent_Success() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(eventService.isUserHostOfEvent(testUserId, testEventId)).thenReturn(true);
            when(inviteService.addInviteToEvent(testEventId, "+1234567890")).thenReturn(testInvite);

            // Act
            ResponseEntity<Map<String, Object>> response = inviteController.addInviteToEvent(testEventId, addInviteRequest, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(testInviteId, response.getBody().get("inviteId"));
            assertEquals("Invite added successfully", response.getBody().get("message"));
            
            verify(eventService).isUserHostOfEvent(testUserId, testEventId);
            verify(inviteService).addInviteToEvent(testEventId, "+1234567890");
        }

        @Test
        @DisplayName("Should return CONFLICT when invite already exists")
        void addInviteToEvent_Conflict_InviteExists() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(eventService.isUserHostOfEvent(testUserId, testEventId)).thenReturn(true);
            when(inviteService.addInviteToEvent(testEventId, "+1234567890"))
                .thenThrow(new IllegalStateException("User is already invited to this event"));

            // Act
            ResponseEntity<Map<String, Object>> response = inviteController.addInviteToEvent(testEventId, addInviteRequest, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("User is already invited to this event", response.getBody().get("error"));
        }

        @Test
        @DisplayName("Should return FORBIDDEN when user is not host")
        void addInviteToEvent_Forbidden_NotHost() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(eventService.isUserHostOfEvent(testUserId, testEventId)).thenReturn(false);

            // Act
            ResponseEntity<Map<String, Object>> response = inviteController.addInviteToEvent(testEventId, addInviteRequest, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            verify(eventService).isUserHostOfEvent(testUserId, testEventId);
            verify(inviteService, never()).addInviteToEvent(any(), any());
        }

        @Test
        @DisplayName("Should return UNAUTHORIZED when userId is null")
        void addInviteToEvent_Unauthorized() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(null);

            // Act
            ResponseEntity<Map<String, Object>> response = inviteController.addInviteToEvent(testEventId, addInviteRequest, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            verifyNoInteractions(eventService, inviteService);
        }
    }

    @Nested
    @DisplayName("DELETE /events/{eventId}/invites/{inviteId} - Remove Invite Tests")
    class RemoveInviteTests {

        @Test
        @DisplayName("Should remove invite successfully when user is host")
        void removeInvite_Success() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(eventService.isUserHostOfEvent(testUserId, testEventId)).thenReturn(true);
            doNothing().when(inviteService).removeInvite(testInviteId);

            // Act
            ResponseEntity<Map<String, String>> response = inviteController.removeInvite(testEventId, testInviteId, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Invite removed successfully", response.getBody().get("message"));
            
            verify(eventService).isUserHostOfEvent(testUserId, testEventId);
            verify(inviteService).removeInvite(testInviteId);
        }

        @Test
        @DisplayName("Should return NOT_FOUND when invite doesn't exist")
        void removeInvite_NotFound() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(eventService.isUserHostOfEvent(testUserId, testEventId)).thenReturn(true);
            doThrow(new IllegalArgumentException("Invite not found")).when(inviteService).removeInvite(testInviteId);

            // Act
            ResponseEntity<Map<String, String>> response = inviteController.removeInvite(testEventId, testInviteId, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Invite not found", response.getBody().get("error"));
            
            verify(inviteService).removeInvite(testInviteId);
        }

        @Test
        @DisplayName("Should return FORBIDDEN when user is not host")
        void removeInvite_Forbidden_NotHost() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(eventService.isUserHostOfEvent(testUserId, testEventId)).thenReturn(false);

            // Act
            ResponseEntity<Map<String, String>> response = inviteController.removeInvite(testEventId, testInviteId, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            verify(eventService).isUserHostOfEvent(testUserId, testEventId);
            verify(inviteService, never()).removeInvite(any());
        }

        @Test
        @DisplayName("Should return UNAUTHORIZED when userId is null")
        void removeInvite_Unauthorized() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(null);

            // Act
            ResponseEntity<Map<String, String>> response = inviteController.removeInvite(testEventId, testInviteId, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            verifyNoInteractions(eventService, inviteService);
        }
    }

    @Nested
    @DisplayName("PUT /events/{eventId}/invites/{inviteId} - Update Response Tests")
    class UpdateInviteResponseTests {

        @Test
        @DisplayName("Should update invite response successfully")
        void updateInviteResponse_Success() {
            // Arrange
            Invite updatedInvite = new Invite(testEventId, testUserId);
            updatedInvite.setId(testInviteId);
            updatedInvite.setResponse(Invite.InviteResponse.GOING);
            
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(inviteService.updateInviteResponse(testInviteId, testUserId, Invite.InviteResponse.GOING))
                .thenReturn(updatedInvite);

            // Act
            ResponseEntity<Map<String, Object>> response = inviteController.updateInviteResponse(testEventId, testInviteId, editInviteRequest, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(testInviteId, response.getBody().get("inviteId"));
            assertEquals(Invite.InviteResponse.GOING, response.getBody().get("response"));
            assertEquals("Invite response updated successfully", response.getBody().get("message"));
            
            verify(inviteService).updateInviteResponse(testInviteId, testUserId, Invite.InviteResponse.GOING);
        }

        @Test
        @DisplayName("Should return NOT_FOUND when invite doesn't exist")
        void updateInviteResponse_NotFound() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(inviteService.updateInviteResponse(testInviteId, testUserId, Invite.InviteResponse.GOING))
                .thenThrow(new IllegalArgumentException("Invite not found"));

            // Act
            ResponseEntity<Map<String, Object>> response = inviteController.updateInviteResponse(testEventId, testInviteId, editInviteRequest, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Invite not found", response.getBody().get("error"));
        }

        @Test
        @DisplayName("Should return FORBIDDEN when user doesn't own the invite")
        void updateInviteResponse_Forbidden_NotOwner() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(inviteService.updateInviteResponse(testInviteId, testUserId, Invite.InviteResponse.GOING))
                .thenThrow(new IllegalStateException("User can only update their own invite response"));

            // Act
            ResponseEntity<Map<String, Object>> response = inviteController.updateInviteResponse(testEventId, testInviteId, editInviteRequest, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("User can only update their own invite response", response.getBody().get("error"));
        }

        @Test
        @DisplayName("Should return UNAUTHORIZED when userId is null")
        void updateInviteResponse_Unauthorized() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(null);

            // Act
            ResponseEntity<Map<String, Object>> response = inviteController.updateInviteResponse(testEventId, testInviteId, editInviteRequest, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            verifyNoInteractions(inviteService);
        }

        @Test
        @DisplayName("Should handle different response types")
        void updateInviteResponse_DifferentResponseTypes() {
            // Arrange
            editInviteRequest.setResponse(Invite.InviteResponse.NOT_GOING);
            Invite updatedInvite = new Invite(testEventId, testUserId);
            updatedInvite.setId(testInviteId);
            updatedInvite.setResponse(Invite.InviteResponse.NOT_GOING);
            
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(inviteService.updateInviteResponse(testInviteId, testUserId, Invite.InviteResponse.NOT_GOING))
                .thenReturn(updatedInvite);

            // Act
            ResponseEntity<Map<String, Object>> response = inviteController.updateInviteResponse(testEventId, testInviteId, editInviteRequest, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(Invite.InviteResponse.NOT_GOING, response.getBody().get("response"));
            verify(inviteService).updateInviteResponse(testInviteId, testUserId, Invite.InviteResponse.NOT_GOING);
        }
    }
}