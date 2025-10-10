package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.HikingTrail;
import com.bbthechange.inviter.dto.HikingTrail.DataQuality;
import com.bbthechange.inviter.model.Coordinate;
import com.bbthechange.inviter.model.Location;
import com.bbthechange.inviter.service.HikingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for HikingController
 *
 * Test Coverage:
 * - GET /hiking/trails/search - Name-based search
 * - GET /hiking/trails/nearby - Location-based search
 * - GET /hiking/trails/suggest - Trail suggestions
 * - GET /hiking/trails/{trailId} - Trail detail (NYI)
 * - GET /hiking/health - Health check
 * - Parameter validation and defaults
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HikingController Tests")
class HikingControllerTest {

    @Mock
    private HikingService hikingService;

    @InjectMocks
    private HikingController hikingController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private Location testLocation;
    private HikingTrail trail1;
    private HikingTrail trail2;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(hikingController)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();

        testLocation = new Location(47.6062, -122.3321);

        // Create test trails
        trail1 = new HikingTrail("trail-1", "Wonderland Trail", testLocation);
        trail1.setQuality(DataQuality.HIGH);
        trail1.setDifficulty("Moderate");
        trail1.setDistanceKm(150.0);

        trail2 = new HikingTrail("trail-2", "Pacific Crest Trail", new Location(47.5, -122.0));
        trail2.setQuality(DataQuality.HIGH);
        trail2.setDifficulty("Hard");
        trail2.setDistanceKm(500.0);
    }

    @Nested
    @DisplayName("GET /hiking/trails/search Tests")
    class SearchByNameTests {

        @Test
        @DisplayName("Should return 200 OK with trails when searching by name with location")
        void searchByName_ValidParameters_Returns200WithTrails() throws Exception {
            // Arrange
            List<HikingTrail> trails = Arrays.asList(trail1, trail2);
            when(hikingService.searchTrailsByName(
                eq("Wonderland"),
                argThat(loc -> loc != null && loc.getLatitude() == 47.6062 && loc.getLongitude() == -122.3321),
                eq(false),
                eq(false)
            )).thenReturn(trails);

            // Act & Assert
            mockMvc.perform(get("/hiking/trails/search")
                    .param("name", "Wonderland")
                    .param("latitude", "47.6062")
                    .param("longitude", "-122.3321")
                    .param("includeElevation", "false")
                    .param("includeGeometry", "false")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Wonderland Trail"))
                .andExpect(jsonPath("$[1].name").value("Pacific Crest Trail"));

            verify(hikingService).searchTrailsByName(eq("Wonderland"), any(Location.class), eq(false), eq(false));
        }

        @Test
        @DisplayName("Should pass null location when coordinates are not provided")
        void searchByName_WithoutLocation_PassesNullLocation() throws Exception {
            // Arrange
            List<HikingTrail> trails = Arrays.asList(trail1);
            when(hikingService.searchTrailsByName(eq("Trail"), isNull(), eq(false), eq(false)))
                .thenReturn(trails);

            // Act & Assert
            mockMvc.perform(get("/hiking/trails/search")
                    .param("name", "Trail")
                    .param("includeElevation", "false")
                    .param("includeGeometry", "false")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));

            verify(hikingService).searchTrailsByName(eq("Trail"), isNull(), eq(false), eq(false));
        }

        @Test
        @DisplayName("Should pass includeElevation=true flag to service")
        void searchByName_WithIncludeElevation_PassesTrueFlag() throws Exception {
            // Arrange
            List<HikingTrail> trails = Arrays.asList(trail1);
            when(hikingService.searchTrailsByName(eq("Trail"), isNull(), eq(true), eq(false)))
                .thenReturn(trails);

            // Act & Assert
            mockMvc.perform(get("/hiking/trails/search")
                    .param("name", "Trail")
                    .param("includeElevation", "true")
                    .param("includeGeometry", "false")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

            verify(hikingService).searchTrailsByName(eq("Trail"), isNull(), eq(true), eq(false));
        }

        @Test
        @DisplayName("Should pass includeGeometry=true flag to service")
        void searchByName_WithIncludeGeometry_PassesTrueFlag() throws Exception {
            // Arrange
            trail1.setGeometry(createGeometry(50));
            List<HikingTrail> trails = Arrays.asList(trail1);
            when(hikingService.searchTrailsByName(eq("Trail"), isNull(), eq(false), eq(true)))
                .thenReturn(trails);

            // Act & Assert
            mockMvc.perform(get("/hiking/trails/search")
                    .param("name", "Trail")
                    .param("includeElevation", "false")
                    .param("includeGeometry", "true")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].geometry").isArray());

            verify(hikingService).searchTrailsByName(eq("Trail"), isNull(), eq(false), eq(true));
        }
    }

    @Nested
    @DisplayName("GET /hiking/trails/nearby Tests")
    class SearchNearbyTests {

        @Test
        @DisplayName("Should return 200 OK with trails when searching nearby")
        void searchNearby_ValidCoordinates_Returns200WithTrails() throws Exception {
            // Arrange
            List<HikingTrail> trails = Arrays.asList(trail1, trail2);
            when(hikingService.searchTrailsNearLocation(
                argThat(loc -> loc.getLatitude() == 47.6062 && loc.getLongitude() == -122.3321),
                eq(10.0),
                eq(false),
                eq(false)
            )).thenReturn(trails);

            // Act & Assert
            mockMvc.perform(get("/hiking/trails/nearby")
                    .param("latitude", "47.6062")
                    .param("longitude", "-122.3321")
                    .param("radiusKm", "10")
                    .param("includeElevation", "false")
                    .param("includeGeometry", "false")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));

            verify(hikingService).searchTrailsNearLocation(any(Location.class), eq(10.0), eq(false), eq(false));
        }

        @Test
        @DisplayName("Should default to 5km radius when not specified")
        void searchNearby_DefaultRadius_Uses5km() throws Exception {
            // Arrange
            List<HikingTrail> trails = Arrays.asList(trail1);
            when(hikingService.searchTrailsNearLocation(any(Location.class), eq(5.0), eq(false), eq(false)))
                .thenReturn(trails);

            // Act & Assert
            mockMvc.perform(get("/hiking/trails/nearby")
                    .param("latitude", "47.6062")
                    .param("longitude", "-122.3321")
                    // radiusKm not provided - should default to 5.0
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

            verify(hikingService).searchTrailsNearLocation(any(Location.class), eq(5.0), eq(false), eq(false));
        }
    }

    @Nested
    @DisplayName("GET /hiking/trails/suggest Tests")
    class SuggestTrailsTests {

        @Test
        @DisplayName("Should return 200 OK with suggestions when all parameters provided")
        void suggestTrails_AllParameters_Returns200WithSuggestions() throws Exception {
            // Arrange
            List<HikingTrail> trails = Arrays.asList(trail1);
            when(hikingService.suggestTrails(
                argThat(loc -> loc.getLatitude() == 47.6062 && loc.getLongitude() == -122.3321),
                eq(15.0),
                eq("Moderate"),
                eq(10.0),
                eq(false)
            )).thenReturn(trails);

            // Act & Assert
            mockMvc.perform(get("/hiking/trails/suggest")
                    .param("latitude", "47.6062")
                    .param("longitude", "-122.3321")
                    .param("radiusKm", "15")
                    .param("difficulty", "Moderate")
                    .param("maxDistanceKm", "10")
                    .param("includeGeometry", "false")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Wonderland Trail"));

            verify(hikingService).suggestTrails(any(Location.class), eq(15.0), eq("Moderate"), eq(10.0), eq(false));
        }

        @Test
        @DisplayName("Should pass null for optional filters when omitted")
        void suggestTrails_OptionalFiltersOmitted_PassesNull() throws Exception {
            // Arrange
            List<HikingTrail> trails = Arrays.asList(trail1, trail2);
            when(hikingService.suggestTrails(
                any(Location.class),
                eq(10.0), // default radiusKm
                isNull(), // difficulty not provided
                isNull(), // maxDistanceKm not provided
                eq(false) // default includeGeometry
            )).thenReturn(trails);

            // Act & Assert
            mockMvc.perform(get("/hiking/trails/suggest")
                    .param("latitude", "47.6062")
                    .param("longitude", "-122.3321")
                    // radiusKm defaults to 10.0
                    // difficulty omitted
                    // maxDistanceKm omitted
                    // includeGeometry defaults to false
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

            verify(hikingService).suggestTrails(any(Location.class), eq(10.0), isNull(), isNull(), eq(false));
        }
    }

    @Nested
    @DisplayName("GET /hiking/trails/{trailId} Tests")
    class GetTrailByIdTests {

        @Test
        @DisplayName("Should return 200 OK when trail exists")
        void getTrailById_TrailExists_Returns200() throws Exception {
            // Arrange
            when(hikingService.getTrailById("osm-way-123456"))
                .thenReturn(trail1);

            // Act & Assert
            mockMvc.perform(get("/hiking/trails/{trailId}", "osm-way-123456")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("trail-1"))
                .andExpect(jsonPath("$.name").value("Wonderland Trail"));

            verify(hikingService).getTrailById("osm-way-123456");
        }

        @Test
        @DisplayName("Should return 404 NOT_FOUND when trail not found")
        void getTrailById_TrailNotFound_Returns404() throws Exception {
            // Arrange
            when(hikingService.getTrailById("osm-way-999999"))
                .thenReturn(null);

            // Act & Assert
            mockMvc.perform(get("/hiking/trails/{trailId}", "osm-way-999999")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

            verify(hikingService).getTrailById("osm-way-999999");
        }
    }

    @Nested
    @DisplayName("GET /hiking/health Tests")
    class HealthCheckTests {

        @Test
        @DisplayName("Should return 200 OK with operational message")
        void healthCheck_Always_Returns200() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/hiking/health")
                    .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json("\"Hiking service is operational\""));

            // Verify no service calls made (health check is independent)
            verifyNoInteractions(hikingService);
        }
    }

    // Helper methods

    private List<Coordinate> createGeometry(int size) {
        List<Coordinate> geometry = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            geometry.add(new Coordinate(47.6 + i * 0.0001, -122.3 + i * 0.0001));
        }
        return geometry;
    }
}
