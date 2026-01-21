package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.watchparty.*;
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

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for WatchPartyController.
 *
 * Test Coverage:
 * - POST /groups/{groupId}/watch-parties - Create watch party
 * - GET /groups/{groupId}/watch-parties/{seriesId} - Get watch party details
 * - PUT /groups/{groupId}/watch-parties/{seriesId} - Update watch party
 * - DELETE /groups/{groupId}/watch-parties/{seriesId} - Delete watch party
 * - Various error scenarios (404, 403, 400)
 */
@WebMvcTest(controllers = WatchPartyController.class, excludeAutoConfiguration = {
    SecurityAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class,
    UserDetailsServiceAutoConfiguration.class
})
@TestPropertySource(locations = "classpath:application-test.properties")
@ActiveProfiles("test")
@DisplayName("WatchPartyController Tests")
class WatchPartyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WatchPartyService watchPartyService;

    @MockitoBean
    private JwtService jwtService;

    private String groupId;
    private String seriesId;
    private String userId;
    private String validJWT;

    @BeforeEach
    void setUp() {
        groupId = UUID.randomUUID().toString();
        seriesId = UUID.randomUUID().toString();
        userId = UUID.randomUUID().toString();
        validJWT = "valid.jwt.token";
    }

    @Nested
    @DisplayName("POST /groups/{groupId}/watch-parties - Create Watch Party Tests")
    class CreateWatchPartyTests {

        @Test
        @DisplayName("Should create watch party successfully and return 201")
        void createWatchParty_WithValidRequest_Returns201AndCallsService() throws Exception {
            // Given
            CreateWatchPartyRequest request = CreateWatchPartyRequest.builder()
                    .showId(12345)
                    .seasonNumber(1)
                    .showName("Test Show")
                    .defaultTime("20:00")
                    .timezone("America/Los_Angeles")
                    .episodes(List.of(
                            CreateWatchPartyEpisodeRequest.builder()
                                    .episodeId(98765)
                                    .episodeNumber(1)
                                    .title("Pilot")
                                    .airTimestamp(1705363200L)
                                    .runtime(60)
                                    .build()
                    ))
                    .build();

            WatchPartyHangoutSummary hangoutSummary = WatchPartyHangoutSummary.builder()
                    .hangoutId(UUID.randomUUID().toString())
                    .title("Episode 1: Pilot")
                    .startTimestamp(1705363200L)
                    .endTimestamp(1705366800L)
                    .externalId("12345")
                    .build();

            WatchPartyResponse response = WatchPartyResponse.builder()
                    .seriesId(seriesId)
                    .seriesTitle("Test Show Season 1")
                    .hangouts(List.of(hangoutSummary))
                    .build();

            when(watchPartyService.createWatchParty(eq(groupId), any(CreateWatchPartyRequest.class), eq(userId)))
                    .thenReturn(response);

            // When & Then
            mockMvc.perform(post("/groups/{groupId}/watch-parties", groupId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.seriesId").value(seriesId))
                    .andExpect(jsonPath("$.seriesTitle").value("Test Show Season 1"))
                    .andExpect(jsonPath("$.hangouts").isArray())
                    .andExpect(jsonPath("$.hangouts[0].title").value("Episode 1: Pilot"));

            verify(watchPartyService).createWatchParty(eq(groupId), any(CreateWatchPartyRequest.class), eq(userId));
        }

        @Test
        @DisplayName("Should return 400 when required fields are missing")
        void createWatchParty_WithInvalidRequest_Returns400() throws Exception {
            // Given - Request missing required fields (showId, seasonNumber, defaultTime, timezone)
            CreateWatchPartyRequest invalidRequest = CreateWatchPartyRequest.builder()
                    .showName("Test Show")
                    // Missing showId, seasonNumber, defaultTime, timezone
                    .build();

            // When & Then
            mockMvc.perform(post("/groups/{groupId}/watch-parties", groupId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(watchPartyService);
        }
    }

    @Nested
    @DisplayName("GET /groups/{groupId}/watch-parties/{seriesId} - Get Watch Party Tests")
    class GetWatchPartyTests {

        @Test
        @DisplayName("Should return watch party details successfully")
        void getWatchParty_WithValidSeriesId_Returns200AndCallsService() throws Exception {
            // Given
            WatchPartyHangoutSummary hangoutSummary = WatchPartyHangoutSummary.builder()
                    .hangoutId(UUID.randomUUID().toString())
                    .title("Episode 1: Pilot")
                    .startTimestamp(1705363200L)
                    .endTimestamp(1705366800L)
                    .build();

            WatchPartyDetailResponse response = WatchPartyDetailResponse.builder()
                    .seriesId(seriesId)
                    .seriesTitle("Test Show Season 1")
                    .groupId(groupId)
                    .eventSeriesType("WATCH_PARTY")
                    .showId(12345)
                    .seasonNumber(1)
                    .defaultTime("20:00")
                    .timezone("America/Los_Angeles")
                    .hangouts(List.of(hangoutSummary))
                    .interestLevels(List.of())
                    .build();

            when(watchPartyService.getWatchParty(eq(groupId), eq(seriesId), eq(userId)))
                    .thenReturn(response);

            // When & Then
            mockMvc.perform(get("/groups/{groupId}/watch-parties/{seriesId}", groupId, seriesId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.seriesId").value(seriesId))
                    .andExpect(jsonPath("$.seriesTitle").value("Test Show Season 1"))
                    .andExpect(jsonPath("$.groupId").value(groupId))
                    .andExpect(jsonPath("$.eventSeriesType").value("WATCH_PARTY"))
                    .andExpect(jsonPath("$.showId").value(12345))
                    .andExpect(jsonPath("$.seasonNumber").value(1))
                    .andExpect(jsonPath("$.defaultTime").value("20:00"))
                    .andExpect(jsonPath("$.timezone").value("America/Los_Angeles"))
                    .andExpect(jsonPath("$.hangouts").isArray());

            verify(watchPartyService).getWatchParty(eq(groupId), eq(seriesId), eq(userId));
        }

        @Test
        @DisplayName("Should return 404 when watch party not found")
        void getWatchParty_WhenServiceThrowsNotFound_Returns404() throws Exception {
            // Given
            when(watchPartyService.getWatchParty(eq(groupId), eq(seriesId), eq(userId)))
                    .thenThrow(new ResourceNotFoundException("Watch party not found: " + seriesId));

            // When & Then
            mockMvc.perform(get("/groups/{groupId}/watch-parties/{seriesId}", groupId, seriesId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("NOT_FOUND"));

            verify(watchPartyService).getWatchParty(eq(groupId), eq(seriesId), eq(userId));
        }

        @Test
        @DisplayName("Should return 403 when user is not authorized")
        void getWatchParty_WhenServiceThrowsUnauthorized_Returns403() throws Exception {
            // Given
            when(watchPartyService.getWatchParty(eq(groupId), eq(seriesId), eq(userId)))
                    .thenThrow(new UnauthorizedException("User is not a member of this group"));

            // When & Then
            mockMvc.perform(get("/groups/{groupId}/watch-parties/{seriesId}", groupId, seriesId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

            verify(watchPartyService).getWatchParty(eq(groupId), eq(seriesId), eq(userId));
        }
    }

    @Nested
    @DisplayName("PUT /groups/{groupId}/watch-parties/{seriesId} - Update Watch Party Tests")
    class UpdateWatchPartyTests {

        @Test
        @DisplayName("Should update watch party successfully and return 200")
        void updateWatchParty_WithValidRequest_Returns200AndCallsService() throws Exception {
            // Given
            UpdateWatchPartyRequest request = UpdateWatchPartyRequest.builder()
                    .defaultTime("21:00")
                    .timezone("America/New_York")
                    .dayOverride(5) // Friday
                    .changeExistingUpcomingHangouts(true)
                    .build();

            WatchPartyDetailResponse response = WatchPartyDetailResponse.builder()
                    .seriesId(seriesId)
                    .seriesTitle("Test Show Season 1")
                    .groupId(groupId)
                    .eventSeriesType("WATCH_PARTY")
                    .showId(12345)
                    .seasonNumber(1)
                    .defaultTime("21:00")
                    .timezone("America/New_York")
                    .dayOverride(5)
                    .hangouts(List.of())
                    .interestLevels(List.of())
                    .build();

            when(watchPartyService.updateWatchParty(eq(groupId), eq(seriesId), any(UpdateWatchPartyRequest.class), eq(userId)))
                    .thenReturn(response);

            // When & Then
            mockMvc.perform(put("/groups/{groupId}/watch-parties/{seriesId}", groupId, seriesId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.seriesId").value(seriesId))
                    .andExpect(jsonPath("$.defaultTime").value("21:00"))
                    .andExpect(jsonPath("$.timezone").value("America/New_York"))
                    .andExpect(jsonPath("$.dayOverride").value(5));

            verify(watchPartyService).updateWatchParty(eq(groupId), eq(seriesId), any(UpdateWatchPartyRequest.class), eq(userId));
        }

        @Test
        @DisplayName("Should return 400 when update request has invalid data")
        void updateWatchParty_WithInvalidRequest_Returns400() throws Exception {
            // Given - Invalid dayOverride (must be 0-6)
            UpdateWatchPartyRequest invalidRequest = UpdateWatchPartyRequest.builder()
                    .dayOverride(10) // Invalid: must be 0-6
                    .build();

            // When & Then
            mockMvc.perform(put("/groups/{groupId}/watch-parties/{seriesId}", groupId, seriesId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(watchPartyService);
        }

        @Test
        @DisplayName("Should return 404 when watch party not found for update")
        void updateWatchParty_WhenServiceThrowsNotFound_Returns404() throws Exception {
            // Given
            UpdateWatchPartyRequest request = UpdateWatchPartyRequest.builder()
                    .defaultTime("21:00")
                    .build();

            when(watchPartyService.updateWatchParty(eq(groupId), eq(seriesId), any(UpdateWatchPartyRequest.class), eq(userId)))
                    .thenThrow(new ResourceNotFoundException("Watch party not found: " + seriesId));

            // When & Then
            mockMvc.perform(put("/groups/{groupId}/watch-parties/{seriesId}", groupId, seriesId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("NOT_FOUND"));

            verify(watchPartyService).updateWatchParty(eq(groupId), eq(seriesId), any(UpdateWatchPartyRequest.class), eq(userId));
        }
    }

    @Nested
    @DisplayName("DELETE /groups/{groupId}/watch-parties/{seriesId} - Delete Watch Party Tests")
    class DeleteWatchPartyTests {

        @Test
        @DisplayName("Should delete watch party successfully and return 204")
        void deleteWatchParty_WithValidSeriesId_Returns204AndCallsService() throws Exception {
            // Given
            doNothing().when(watchPartyService).deleteWatchParty(eq(groupId), eq(seriesId), eq(userId));

            // When & Then
            mockMvc.perform(delete("/groups/{groupId}/watch-parties/{seriesId}", groupId, seriesId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNoContent());

            verify(watchPartyService).deleteWatchParty(eq(groupId), eq(seriesId), eq(userId));
        }

        @Test
        @DisplayName("Should return 404 when watch party not found for deletion")
        void deleteWatchParty_WhenServiceThrowsNotFound_Returns404() throws Exception {
            // Given
            doThrow(new ResourceNotFoundException("Watch party not found: " + seriesId))
                    .when(watchPartyService).deleteWatchParty(eq(groupId), eq(seriesId), eq(userId));

            // When & Then
            mockMvc.perform(delete("/groups/{groupId}/watch-parties/{seriesId}", groupId, seriesId)
                    .header("Authorization", "Bearer " + validJWT)
                    .requestAttr("userId", userId)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("NOT_FOUND"));

            verify(watchPartyService).deleteWatchParty(eq(groupId), eq(seriesId), eq(userId));
        }
    }
}
