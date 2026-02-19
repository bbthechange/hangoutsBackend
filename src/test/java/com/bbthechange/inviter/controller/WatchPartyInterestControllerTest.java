package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.watchparty.SetSeriesInterestRequest;
import com.bbthechange.inviter.exception.ResourceNotFoundException;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.service.JwtService;
import com.bbthechange.inviter.service.WatchPartyService;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for WatchPartyInterestController
 *
 * Test Coverage:
 * - POST /watch-parties/{seriesId}/interest - Set series-level interest
 * - DELETE /watch-parties/{seriesId}/interest - Remove series-level interest
 * - Validation scenarios (seriesId format, request body)
 * - Error handling (ResourceNotFoundException, UnauthorizedException)
 */
@WebMvcTest(controllers = WatchPartyInterestController.class, excludeAutoConfiguration = {
    SecurityAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class,
    UserDetailsServiceAutoConfiguration.class
})
@TestPropertySource(locations = "classpath:application-test.properties")
@ActiveProfiles("test")
@DisplayName("WatchPartyInterestController Tests")
class WatchPartyInterestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WatchPartyService watchPartyService;

    @MockitoBean
    private JwtService jwtService;

    private String seriesId;
    private String userId;
    private String validJWT;

    @BeforeEach
    void setUp() {
        seriesId = "550e8400-e29b-41d4-a716-446655440000";
        userId = UUID.randomUUID().toString();
        validJWT = "valid.jwt.token";
    }

    @Nested
    @DisplayName("POST /watch-parties/{seriesId}/interest - Set Series Interest Tests")
    class SetSeriesInterestTests {

        @Test
        @DisplayName("Should return 200 and call service with valid request")
        void setSeriesInterest_WithValidRequest_Returns200AndCallsService() throws Exception {
            // Given
            SetSeriesInterestRequest request = new SetSeriesInterestRequest("GOING");
            doNothing().when(watchPartyService).setUserInterest(eq(seriesId), eq("GOING"), eq(userId));

            // When & Then
            mockMvc.perform(post("/watch-parties/{seriesId}/interest", seriesId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(watchPartyService).setUserInterest(eq(seriesId), eq("GOING"), eq(userId));
        }

        @Test
        @DisplayName("Should process GOING level correctly")
        void setSeriesInterest_WithGoingLevel_ProcessesCorrectly() throws Exception {
            // Given
            SetSeriesInterestRequest request = new SetSeriesInterestRequest("GOING");
            doNothing().when(watchPartyService).setUserInterest(eq(seriesId), eq("GOING"), eq(userId));

            // When & Then
            mockMvc.perform(post("/watch-parties/{seriesId}/interest", seriesId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(watchPartyService).setUserInterest(eq(seriesId), eq("GOING"), eq(userId));
        }

        @Test
        @DisplayName("Should process INTERESTED level correctly")
        void setSeriesInterest_WithInterestedLevel_ProcessesCorrectly() throws Exception {
            // Given
            SetSeriesInterestRequest request = new SetSeriesInterestRequest("INTERESTED");
            doNothing().when(watchPartyService).setUserInterest(eq(seriesId), eq("INTERESTED"), eq(userId));

            // When & Then
            mockMvc.perform(post("/watch-parties/{seriesId}/interest", seriesId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(watchPartyService).setUserInterest(eq(seriesId), eq("INTERESTED"), eq(userId));
        }

        @Test
        @DisplayName("Should process NOT_GOING level correctly")
        void setSeriesInterest_WithNotGoingLevel_ProcessesCorrectly() throws Exception {
            // Given
            SetSeriesInterestRequest request = new SetSeriesInterestRequest("NOT_GOING");
            doNothing().when(watchPartyService).setUserInterest(eq(seriesId), eq("NOT_GOING"), eq(userId));

            // When & Then
            mockMvc.perform(post("/watch-parties/{seriesId}/interest", seriesId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(watchPartyService).setUserInterest(eq(seriesId), eq("NOT_GOING"), eq(userId));
        }

        @Test
        @DisplayName("Should return 400 for invalid seriesId format")
        void setSeriesInterest_WithInvalidSeriesIdFormat_Returns400() throws Exception {
            // Given
            SetSeriesInterestRequest request = new SetSeriesInterestRequest("GOING");
            String invalidSeriesId = "invalid-id";

            // When & Then
            mockMvc.perform(post("/watch-parties/{seriesId}/interest", invalidSeriesId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(watchPartyService, never()).setUserInterest(any(), any(), any());
        }

        @Test
        @DisplayName("Should return 400 for missing level field")
        void setSeriesInterest_WithMissingLevel_Returns400() throws Exception {
            // Given - empty request body without level
            String requestJson = "{}";

            // When & Then
            mockMvc.perform(post("/watch-parties/{seriesId}/interest", seriesId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestJson))
                    .andExpect(status().isBadRequest());

            verify(watchPartyService, never()).setUserInterest(any(), any(), any());
        }

        @Test
        @DisplayName("Should return 404 when service throws ResourceNotFoundException")
        void setSeriesInterest_WhenServiceThrowsNotFound_Returns404() throws Exception {
            // Given
            SetSeriesInterestRequest request = new SetSeriesInterestRequest("GOING");
            doThrow(new ResourceNotFoundException("Watch party series not found: " + seriesId))
                    .when(watchPartyService).setUserInterest(eq(seriesId), eq("GOING"), eq(userId));

            // When & Then
            mockMvc.perform(post("/watch-parties/{seriesId}/interest", seriesId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());

            verify(watchPartyService).setUserInterest(eq(seriesId), eq("GOING"), eq(userId));
        }

        @Test
        @DisplayName("Should return 403 when service throws UnauthorizedException")
        void setSeriesInterest_WhenServiceThrowsUnauthorized_Returns403() throws Exception {
            // Given
            SetSeriesInterestRequest request = new SetSeriesInterestRequest("GOING");
            doThrow(new UnauthorizedException("User not authorized to access this watch party"))
                    .when(watchPartyService).setUserInterest(eq(seriesId), eq("GOING"), eq(userId));

            // When & Then
            mockMvc.perform(post("/watch-parties/{seriesId}/interest", seriesId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(watchPartyService).setUserInterest(eq(seriesId), eq("GOING"), eq(userId));
        }
    }

    @Nested
    @DisplayName("DELETE /watch-parties/{seriesId}/interest - Remove Series Interest Tests")
    class RemoveSeriesInterestTests {

        @Test
        @DisplayName("Should return 204 and call service with valid request")
        void removeSeriesInterest_WithValidRequest_Returns204AndCallsService() throws Exception {
            // Given
            doNothing().when(watchPartyService).removeUserInterest(eq(seriesId), eq(userId));

            // When & Then
            mockMvc.perform(delete("/watch-parties/{seriesId}/interest", seriesId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId))
                    .andExpect(status().isNoContent());

            verify(watchPartyService).removeUserInterest(eq(seriesId), eq(userId));
        }

        @Test
        @DisplayName("Should return 400 for invalid seriesId format")
        void removeSeriesInterest_WithInvalidSeriesIdFormat_Returns400() throws Exception {
            // Given
            String invalidSeriesId = "invalid-id";

            // When & Then
            mockMvc.perform(delete("/watch-parties/{seriesId}/interest", invalidSeriesId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId))
                    .andExpect(status().isBadRequest());

            verify(watchPartyService, never()).removeUserInterest(any(), any());
        }

        @Test
        @DisplayName("Should return 404 when service throws ResourceNotFoundException")
        void removeSeriesInterest_WhenServiceThrowsNotFound_Returns404() throws Exception {
            // Given
            doThrow(new ResourceNotFoundException("Watch party series not found: " + seriesId))
                    .when(watchPartyService).removeUserInterest(eq(seriesId), eq(userId));

            // When & Then
            mockMvc.perform(delete("/watch-parties/{seriesId}/interest", seriesId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId))
                    .andExpect(status().isNotFound());

            verify(watchPartyService).removeUserInterest(eq(seriesId), eq(userId));
        }

        @Test
        @DisplayName("Should return 403 when service throws UnauthorizedException")
        void removeSeriesInterest_WhenServiceThrowsUnauthorized_Returns403() throws Exception {
            // Given
            doThrow(new UnauthorizedException("User not authorized to access this watch party"))
                    .when(watchPartyService).removeUserInterest(eq(seriesId), eq(userId));

            // When & Then
            mockMvc.perform(delete("/watch-parties/{seriesId}/interest", seriesId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId))
                    .andExpect(status().isForbidden());

            verify(watchPartyService).removeUserInterest(eq(seriesId), eq(userId));
        }
    }
}
