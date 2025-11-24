package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.CreateParticipationRequest;
import com.bbthechange.inviter.dto.ParticipationDTO;
import com.bbthechange.inviter.dto.UpdateParticipationRequest;
import com.bbthechange.inviter.exception.ParticipationNotFoundException;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.model.Participation;
import com.bbthechange.inviter.model.ParticipationType;
import com.bbthechange.inviter.service.JwtService;
import com.bbthechange.inviter.service.ParticipationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for ParticipationController
 *
 * Test Coverage:
 * - POST /hangouts/{hangoutId}/participations - Create participation
 * - GET /hangouts/{hangoutId}/participations - List participations
 * - GET /hangouts/{hangoutId}/participations/{participationId} - Get specific participation
 * - PUT /hangouts/{hangoutId}/participations/{participationId} - Update participation
 * - DELETE /hangouts/{hangoutId}/participations/{participationId} - Delete participation
 * - Validation scenarios and error handling
 */
@WebMvcTest(controllers = ParticipationController.class, excludeAutoConfiguration = {
    SecurityAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class,
    UserDetailsServiceAutoConfiguration.class
})
@TestPropertySource(locations = "classpath:application-test.properties")
@ActiveProfiles("test")
@DisplayName("ParticipationController Tests")
class ParticipationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ParticipationService participationService;

    @MockitoBean
    private JwtService jwtService;

    private String hangoutId;
    private String participationId;
    private String userId;
    private String validJWT;
    private ParticipationDTO mockParticipationDTO;

    @BeforeEach
    void setUp() {
        hangoutId = UUID.randomUUID().toString();
        participationId = UUID.randomUUID().toString();
        userId = UUID.randomUUID().toString();
        validJWT = "valid.jwt.token";

        // Create a mock ParticipationDTO for test responses
        mockParticipationDTO = new ParticipationDTO(
                createMockParticipation(),
                "John Doe",
                "users/123/profile.jpg"
        );
    }

    private Participation createMockParticipation() {
        Participation participation = new Participation(hangoutId, participationId, userId, ParticipationType.TICKET_NEEDED);
        participation.setSection("A");
        participation.setSeat("12");
        participation.setCreatedAt(Instant.now());
        participation.setUpdatedAt(Instant.now());
        return participation;
    }

    @Nested
    @DisplayName("POST /hangouts/{hangoutId}/participations - Create Participation Tests")
    class CreateParticipationTests {

        @Test
        @DisplayName("Test 1: Valid request returns 201 with DTO")
        void createParticipation_ValidRequest_Returns201WithDTO() throws Exception {
            // Given
            CreateParticipationRequest request = new CreateParticipationRequest(ParticipationType.TICKET_NEEDED);
            request.setSection("A");

            when(participationService.createParticipation(eq(hangoutId), any(CreateParticipationRequest.class), eq(userId)))
                    .thenReturn(mockParticipationDTO);

            // When & Then
            mockMvc.perform(post("/hangouts/{hangoutId}/participations", hangoutId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.participationId").value(participationId))
                    .andExpect(jsonPath("$.type").value("TICKET_NEEDED"))
                    .andExpect(jsonPath("$.section").value("A"));

            verify(participationService).createParticipation(eq(hangoutId), any(CreateParticipationRequest.class), eq(userId));
        }

        @Test
        @DisplayName("Test 2: Missing required type returns 400")
        void createParticipation_MissingRequiredType_Returns400() throws Exception {
            // Given - Create request without type (violates @NotNull)
            String requestJson = "{\"section\": \"A\", \"seat\": \"12\"}";

            // When & Then
            mockMvc.perform(post("/hangouts/{hangoutId}/participations", hangoutId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
                    .andExpect(status().isBadRequest());

            verify(participationService, never()).createParticipation(any(), any(), any());
        }

        @Test
        @DisplayName("Test 3: Invalid hangout ID format returns 400")
        void createParticipation_InvalidHangoutIdFormat_Returns400() throws Exception {
            // Given
            CreateParticipationRequest request = new CreateParticipationRequest(ParticipationType.TICKET_NEEDED);
            String invalidHangoutId = "not-a-uuid";

            // When & Then
            mockMvc.perform(post("/hangouts/{hangoutId}/participations", invalidHangoutId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(participationService, never()).createParticipation(any(), any(), any());
        }

        @Test
        @DisplayName("Test 4: Section too long returns 400")
        void createParticipation_SectionTooLong_Returns400() throws Exception {
            // Given - Create request with section exceeding 200 characters
            CreateParticipationRequest request = new CreateParticipationRequest(ParticipationType.TICKET_NEEDED);
            request.setSection("A".repeat(201));

            // When & Then
            mockMvc.perform(post("/hangouts/{hangoutId}/participations", hangoutId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(participationService, never()).createParticipation(any(), any(), any());
        }

        @Test
        @DisplayName("Test 5: Unauthorized user returns 403")
        void createParticipation_UnauthorizedUser_Returns403() throws Exception {
            // Given
            CreateParticipationRequest request = new CreateParticipationRequest(ParticipationType.TICKET_NEEDED);

            when(participationService.createParticipation(eq(hangoutId), any(CreateParticipationRequest.class), eq(userId)))
                    .thenThrow(new UnauthorizedException("User not authorized"));

            // When & Then
            mockMvc.perform(post("/hangouts/{hangoutId}/participations", hangoutId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(participationService).createParticipation(eq(hangoutId), any(CreateParticipationRequest.class), eq(userId));
        }
    }

    @Nested
    @DisplayName("GET /hangouts/{hangoutId}/participations - List Participations Tests")
    class GetParticipationsTests {

        @Test
        @DisplayName("Test 6: Valid request returns 200 with list")
        void getParticipations_ValidRequest_Returns200WithList() throws Exception {
            // Given
            ParticipationDTO participation1 = mockParticipationDTO;
            ParticipationDTO participation2 = new ParticipationDTO(
                    createMockParticipation(),
                    "Jane Smith",
                    "users/456/profile.jpg"
            );
            ParticipationDTO participation3 = new ParticipationDTO(
                    createMockParticipation(),
                    "Bob Johnson",
                    "users/789/profile.jpg"
            );
            List<ParticipationDTO> participations = Arrays.asList(participation1, participation2, participation3);

            when(participationService.getParticipations(eq(hangoutId), eq(userId)))
                    .thenReturn(participations);

            // When & Then
            mockMvc.perform(get("/hangouts/{hangoutId}/participations", hangoutId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)));

            verify(participationService).getParticipations(eq(hangoutId), eq(userId));
        }

        @Test
        @DisplayName("Test 7: Empty list returns 200 with empty array")
        void getParticipations_EmptyList_Returns200WithEmptyArray() throws Exception {
            // Given
            when(participationService.getParticipations(eq(hangoutId), eq(userId)))
                    .thenReturn(Collections.emptyList());

            // When & Then
            mockMvc.perform(get("/hangouts/{hangoutId}/participations", hangoutId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));

            verify(participationService).getParticipations(eq(hangoutId), eq(userId));
        }
    }

    @Nested
    @DisplayName("GET /hangouts/{hangoutId}/participations/{participationId} - Get Participation Tests")
    class GetParticipationTests {

        @Test
        @DisplayName("Test 8: Valid request returns 200 with DTO")
        void getParticipation_ValidRequest_Returns200WithDTO() throws Exception {
            // Given
            when(participationService.getParticipation(eq(hangoutId), eq(participationId), eq(userId)))
                    .thenReturn(mockParticipationDTO);

            // When & Then
            mockMvc.perform(get("/hangouts/{hangoutId}/participations/{participationId}", hangoutId, participationId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.participationId").value(participationId))
                    .andExpect(jsonPath("$.userId").value(userId))
                    .andExpect(jsonPath("$.displayName").value("John Doe"));

            verify(participationService).getParticipation(eq(hangoutId), eq(participationId), eq(userId));
        }

        @Test
        @DisplayName("Test 9: Not found returns 404")
        void getParticipation_NotFound_Returns404() throws Exception {
            // Given
            String nonexistentId = UUID.randomUUID().toString();

            when(participationService.getParticipation(eq(hangoutId), eq(nonexistentId), eq(userId)))
                    .thenThrow(new ParticipationNotFoundException("Participation not found"));

            // When & Then
            mockMvc.perform(get("/hangouts/{hangoutId}/participations/{participationId}", hangoutId, nonexistentId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());

            verify(participationService).getParticipation(eq(hangoutId), eq(nonexistentId), eq(userId));
        }
    }

    @Nested
    @DisplayName("PUT /hangouts/{hangoutId}/participations/{participationId} - Update Participation Tests")
    class UpdateParticipationTests {

        @Test
        @DisplayName("Test 10: Valid update returns 200 with updated DTO")
        void updateParticipation_ValidUpdate_Returns200WithUpdatedDTO() throws Exception {
            // Given
            UpdateParticipationRequest request = new UpdateParticipationRequest();
            request.setType(ParticipationType.TICKET_PURCHASED);
            request.setSection("B");

            ParticipationDTO updatedDTO = new ParticipationDTO(
                    createMockParticipation(),
                    "John Doe",
                    "users/123/profile.jpg"
            );
            updatedDTO.setType(ParticipationType.TICKET_PURCHASED);
            updatedDTO.setSection("B");

            when(participationService.updateParticipation(eq(hangoutId), eq(participationId), any(UpdateParticipationRequest.class), eq(userId)))
                    .thenReturn(updatedDTO);

            // When & Then
            mockMvc.perform(put("/hangouts/{hangoutId}/participations/{participationId}", hangoutId, participationId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.type").value("TICKET_PURCHASED"))
                    .andExpect(jsonPath("$.section").value("B"));

            verify(participationService).updateParticipation(eq(hangoutId), eq(participationId), any(UpdateParticipationRequest.class), eq(userId));
        }

        @Test
        @DisplayName("Test 11: Seat too long returns 400")
        void updateParticipation_SeatTooLong_Returns400() throws Exception {
            // Given - Create request with seat exceeding 50 characters
            UpdateParticipationRequest request = new UpdateParticipationRequest();
            request.setSeat("X".repeat(51));

            // When & Then
            mockMvc.perform(put("/hangouts/{hangoutId}/participations/{participationId}", hangoutId, participationId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(participationService, never()).updateParticipation(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Test 12: Invalid participation ID format returns 400")
        void updateParticipation_InvalidParticipationIdFormat_Returns400() throws Exception {
            // Given
            UpdateParticipationRequest request = new UpdateParticipationRequest();
            request.setType(ParticipationType.TICKET_PURCHASED);
            String invalidParticipationId = "not-a-uuid";

            // When & Then
            mockMvc.perform(put("/hangouts/{hangoutId}/participations/{participationId}", hangoutId, invalidParticipationId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(participationService, never()).updateParticipation(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("DELETE /hangouts/{hangoutId}/participations/{participationId} - Delete Participation Tests")
    class DeleteParticipationTests {

        @Test
        @DisplayName("Test 13: Valid request returns 204")
        void deleteParticipation_ValidRequest_Returns204() throws Exception {
            // Given
            doNothing().when(participationService).deleteParticipation(eq(hangoutId), eq(participationId), eq(userId));

            // When & Then
            mockMvc.perform(delete("/hangouts/{hangoutId}/participations/{participationId}", hangoutId, participationId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNoContent());

            verify(participationService).deleteParticipation(eq(hangoutId), eq(participationId), eq(userId));
        }

        @Test
        @DisplayName("Test 14: Unauthorized user returns 403")
        void deleteParticipation_UnauthorizedUser_Returns403() throws Exception {
            // Given
            doThrow(new UnauthorizedException("User not authorized"))
                    .when(participationService).deleteParticipation(eq(hangoutId), eq(participationId), eq(userId));

            // When & Then
            mockMvc.perform(delete("/hangouts/{hangoutId}/participations/{participationId}", hangoutId, participationId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());

            verify(participationService).deleteParticipation(eq(hangoutId), eq(participationId), eq(userId));
        }
    }
}
