package com.bbthechange.inviter.hiking;

import com.bbthechange.inviter.dto.HikingTrail;
import com.bbthechange.inviter.model.Coordinate;
import com.bbthechange.inviter.model.Location;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OpenElevationClient.
 * Tests elevation enrichment and calculation logic.
 */
@ExtendWith(MockitoExtension.class)
class OpenElevationClientTest {

    @Mock
    private RestTemplate restTemplate;

    private ObjectMapper objectMapper;
    private OpenElevationClient openElevationClient;

    // Test data
    private HikingTrail testTrail;
    private String validElevationResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        openElevationClient = new OpenElevationClient(restTemplate, objectMapper);

        // Create test trail with geometry
        testTrail = new HikingTrail("test-trail-1", "Test Trail", new Location(47.6062, -122.3321));
        List<Coordinate> geometry = new ArrayList<>();
        geometry.add(new Coordinate(47.6062, -122.3321)); // Start at ~1000m elevation
        geometry.add(new Coordinate(47.6065, -122.3325)); // +50m
        geometry.add(new Coordinate(47.6070, -122.3330)); // +50m
        geometry.add(new Coordinate(47.6068, -122.3335)); // -20m
        geometry.add(new Coordinate(47.6075, -122.3340)); // +120m
        testTrail.setGeometry(geometry);

        // Valid elevation response with gain and loss
        validElevationResponse = """
            {
              "results": [
                {"latitude": 47.6062, "longitude": -122.3321, "elevation": 1000},
                {"latitude": 47.6065, "longitude": -122.3325, "elevation": 1050},
                {"latitude": 47.6070, "longitude": -122.3330, "elevation": 1100},
                {"latitude": 47.6068, "longitude": -122.3335, "elevation": 1080},
                {"latitude": 47.6075, "longitude": -122.3340, "elevation": 1200}
              ]
            }
            """;
    }

    // Test 1: Valid trail gets elevation data added
    @Test
    void enrichTrailWithElevation_ValidTrail_AddsElevationData() {
        // Given
        ResponseEntity<String> mockResponse = new ResponseEntity<>(
            validElevationResponse, HttpStatus.OK);
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenReturn(mockResponse);

        // When
        HikingTrail enrichedTrail = openElevationClient.enrichTrailWithElevation(testTrail);

        // Then
        assertThat(enrichedTrail).isNotNull();
        assertThat(enrichedTrail.getElevationGainMeters()).isNotNull();
        assertThat(enrichedTrail.getElevationLossMeters()).isNotNull();

        // Expected gain: (1050-1000) + (1100-1050) + (1200-1080) = 50 + 50 + 120 = 220m
        // Expected loss: (1100-1080) = 20m
        assertThat(enrichedTrail.getElevationGainMeters()).isEqualTo(220);
        assertThat(enrichedTrail.getElevationLossMeters()).isEqualTo(20);

        // Verify coordinates have elevation values
        assertThat(enrichedTrail.getGeometry()).isNotEmpty();
        for (Coordinate coord : enrichedTrail.getGeometry()) {
            // All coordinates should have elevation assigned (closest match)
            assertThat(coord.getElevation()).isNotNull();
        }

        // Verify RestTemplate was called with correct URL format
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate, atLeastOnce()).getForEntity(urlCaptor.capture(), eq(String.class));

        String capturedUrl = urlCaptor.getValue();
        assertThat(capturedUrl).contains("api.open-elevation.com");
        assertThat(capturedUrl).contains("locations=");
    }

    // Test 2: Empty geometry returns unchanged trail
    @Test
    void enrichTrailWithElevation_EmptyGeometry_ReturnsUnchanged() {
        // Given - Trail with null geometry
        HikingTrail trailWithoutGeometry = new HikingTrail(
            "no-geom-trail", "No Geometry Trail", new Location(47.6062, -122.3321));
        trailWithoutGeometry.setGeometry(null);

        // When
        HikingTrail result = openElevationClient.enrichTrailWithElevation(trailWithoutGeometry);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getElevationGainMeters()).isNull();
        assertThat(result.getElevationLossMeters()).isNull();

        // Verify no API calls were made
        verifyNoInteractions(restTemplate);

        // Test with empty list
        HikingTrail trailWithEmptyGeometry = new HikingTrail(
            "empty-geom-trail", "Empty Geometry Trail", new Location(47.6062, -122.3321));
        trailWithEmptyGeometry.setGeometry(new ArrayList<>());

        result = openElevationClient.enrichTrailWithElevation(trailWithEmptyGeometry);

        assertThat(result).isNotNull();
        assertThat(result.getElevationGainMeters()).isNull();
        verifyNoInteractions(restTemplate);
    }

    // Test 3: Large trail is sampled down to max samples
    @Test
    void sampleCoordinates_LargeTrail_ReducesToMaxSamples() {
        // Given - Trail with 100 coordinates (should be sampled to 20)
        HikingTrail largeTrail = new HikingTrail(
            "large-trail", "Large Trail", new Location(47.6062, -122.3321));
        List<Coordinate> largeGeometry = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            largeGeometry.add(new Coordinate(47.6062 + i * 0.001, -122.3321 + i * 0.001));
        }
        largeTrail.setGeometry(largeGeometry);

        String sampledElevationResponse = """
            {
              "results": [
                {"latitude": 47.6062, "longitude": -122.3321, "elevation": 1000},
                {"latitude": 47.6067, "longitude": -122.3326, "elevation": 1010},
                {"latitude": 47.6072, "longitude": -122.3331, "elevation": 1020},
                {"latitude": 47.6077, "longitude": -122.3336, "elevation": 1030},
                {"latitude": 47.6082, "longitude": -122.3341, "elevation": 1040},
                {"latitude": 47.6087, "longitude": -122.3346, "elevation": 1050},
                {"latitude": 47.6092, "longitude": -122.3351, "elevation": 1060},
                {"latitude": 47.6097, "longitude": -122.3356, "elevation": 1070},
                {"latitude": 47.6102, "longitude": -122.3361, "elevation": 1080},
                {"latitude": 47.6107, "longitude": -122.3366, "elevation": 1090},
                {"latitude": 47.6112, "longitude": -122.3371, "elevation": 1100},
                {"latitude": 47.6117, "longitude": -122.3376, "elevation": 1110},
                {"latitude": 47.6122, "longitude": -122.3381, "elevation": 1120},
                {"latitude": 47.6127, "longitude": -122.3386, "elevation": 1130},
                {"latitude": 47.6132, "longitude": -122.3391, "elevation": 1140},
                {"latitude": 47.6137, "longitude": -122.3396, "elevation": 1150},
                {"latitude": 47.6142, "longitude": -122.3401, "elevation": 1160},
                {"latitude": 47.6147, "longitude": -122.3406, "elevation": 1170},
                {"latitude": 47.6152, "longitude": -122.3411, "elevation": 1180},
                {"latitude": 47.6157, "longitude": -122.3416, "elevation": 1190}
              ]
            }
            """;

        ResponseEntity<String> mockResponse = new ResponseEntity<>(
            sampledElevationResponse, HttpStatus.OK);
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenReturn(mockResponse);

        // When
        HikingTrail enriched = openElevationClient.enrichTrailWithElevation(largeTrail);

        // Then
        assertThat(enriched.getElevationGainMeters()).isNotNull();

        // Verify only one API call was made (sampling reduced to <= 20 points)
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate, times(1)).getForEntity(urlCaptor.capture(), eq(String.class));

        // Count pipe-separated coordinates in URL (should be ~20, not 100)
        String url = urlCaptor.getValue();
        int locationCount = url.split("\\|").length;
        assertThat(locationCount).isLessThanOrEqualTo(21); // 20 sampled + possibly last point
    }

    // Test 4: Large coordinate list processes in batches
    @Test
    void fetchElevationBatch_LargeList_ProcessesInBatches() {
        // Given - Trail with 30 coordinates (fits in one batch of 100)
        HikingTrail trail30 = new HikingTrail(
            "trail-30", "30 Point Trail", new Location(47.6062, -122.3321));
        List<Coordinate> geom30 = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            geom30.add(new Coordinate(47.6062 + i * 0.001, -122.3321 + i * 0.001));
        }
        trail30.setGeometry(geom30);

        // Create response with 30 elevation points
        StringBuilder response30 = new StringBuilder("{\"results\": [");
        for (int i = 0; i < 30; i++) {
            if (i > 0) response30.append(",");
            response30.append(String.format(
                "{\"latitude\": %.4f, \"longitude\": %.4f, \"elevation\": %d}",
                47.6062 + i * 0.001, -122.3321 + i * 0.001, 1000 + i * 10));
        }
        response30.append("]}");

        ResponseEntity<String> mock30Response = new ResponseEntity<>(
            response30.toString(), HttpStatus.OK);
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenReturn(mock30Response);

        // When
        openElevationClient.enrichTrailWithElevation(trail30);

        // Then - Should make only 1 API call (30 < 100 batch limit)
        verify(restTemplate, times(1)).getForEntity(anyString(), eq(String.class));

        // Reset for 150 coordinate test
        reset(restTemplate);

        // Given - Trail with 150 coordinates (needs 2 batches)
        // But will be sampled to 20 first, so still 1 batch
        HikingTrail trail150 = new HikingTrail(
            "trail-150", "150 Point Trail", new Location(47.6062, -122.3321));
        List<Coordinate> geom150 = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            geom150.add(new Coordinate(47.6062 + i * 0.0001, -122.3321 + i * 0.0001));
        }
        trail150.setGeometry(geom150);

        // Create response with 20 sampled points
        StringBuilder response20 = new StringBuilder("{\"results\": [");
        for (int i = 0; i < 20; i++) {
            if (i > 0) response20.append(",");
            response20.append(String.format(
                "{\"latitude\": %.4f, \"longitude\": %.4f, \"elevation\": %d}",
                47.6062 + i * 0.0075, -122.3321 + i * 0.0075, 1000 + i * 50));
        }
        response20.append("]}");

        ResponseEntity<String> mock20Response = new ResponseEntity<>(
            response20.toString(), HttpStatus.OK);
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenReturn(mock20Response);

        // When
        openElevationClient.enrichTrailWithElevation(trail150);

        // Then - Should make 1 API call (sampled to 20 points)
        verify(restTemplate, times(1)).getForEntity(anyString(), eq(String.class));
    }

    // Test 5: Elevation gain/loss calculation
    @Test
    void calculateElevationStats_MixedGainLoss_CorrectTotals() {
        // Given - Coordinates with known elevation changes
        // [100m, 150m, 120m, 180m, 160m]
        // Gain: (150-100) + (180-120) = 50 + 60 = 110m
        // Loss: (150-120) + (180-160) = 30 + 20 = 50m
        HikingTrail trailWithStats = new HikingTrail(
            "stats-trail", "Stats Trail", new Location(47.6062, -122.3321));
        List<Coordinate> statsGeometry = new ArrayList<>();
        statsGeometry.add(new Coordinate(47.6062, -122.3321));
        statsGeometry.add(new Coordinate(47.6065, -122.3325));
        statsGeometry.add(new Coordinate(47.6070, -122.3330));
        statsGeometry.add(new Coordinate(47.6075, -122.3335));
        statsGeometry.add(new Coordinate(47.6080, -122.3340));
        trailWithStats.setGeometry(statsGeometry);

        String statsResponse = """
            {
              "results": [
                {"latitude": 47.6062, "longitude": -122.3321, "elevation": 100},
                {"latitude": 47.6065, "longitude": -122.3325, "elevation": 150},
                {"latitude": 47.6070, "longitude": -122.3330, "elevation": 120},
                {"latitude": 47.6075, "longitude": -122.3335, "elevation": 180},
                {"latitude": 47.6080, "longitude": -122.3340, "elevation": 160}
              ]
            }
            """;

        ResponseEntity<String> mockResponse = new ResponseEntity<>(
            statsResponse, HttpStatus.OK);
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenReturn(mockResponse);

        // When
        HikingTrail enriched = openElevationClient.enrichTrailWithElevation(trailWithStats);

        // Then
        assertThat(enriched.getElevationGainMeters()).isEqualTo(110);
        assertThat(enriched.getElevationLossMeters()).isEqualTo(50);
    }

    // Test 6: API failure returns original trail unchanged
    @Test
    void enrichTrailWithElevation_ApiFailure_ReturnsOriginalTrail() {
        // Given
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenThrow(new RestClientException("API is down"));

        // When
        HikingTrail result = openElevationClient.enrichTrailWithElevation(testTrail);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getElevationGainMeters()).isNull();
        assertThat(result.getElevationLossMeters()).isNull();
        // Original trail returned unchanged
        assertThat(result.getId()).isEqualTo(testTrail.getId());
    }

    // Test 7: Elevation response parsing updates coordinates
    @Test
    void parseElevationResponse_ValidJson_UpdatesCoordinates() {
        // Given - This test is indirect via enrichment
        ResponseEntity<String> mockResponse = new ResponseEntity<>(
            validElevationResponse, HttpStatus.OK);
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenReturn(mockResponse);

        // When
        HikingTrail enriched = openElevationClient.enrichTrailWithElevation(testTrail);

        // Then - Verify coordinates have elevation populated
        assertThat(enriched.getGeometry()).isNotEmpty();

        boolean allHaveElevation = enriched.getGeometry().stream()
            .allMatch(coord -> coord.getElevation() != null);
        assertThat(allHaveElevation).isTrue();

        // Verify elevation values are reasonable (between 100 and 2000m)
        for (Coordinate coord : enriched.getGeometry()) {
            assertThat(coord.getElevation()).isBetween(100, 2000);
        }
    }

    // Test 8: Partial elevation data is handled gracefully
    @Test
    void enrichTrailWithElevation_PartialElevationData_HandlesGracefully() {
        // Given - Response missing some elevation values
        String partialElevationResponse = """
            {
              "results": [
                {"latitude": 47.6062, "longitude": -122.3321, "elevation": 1000},
                {"latitude": 47.6065, "longitude": -122.3325, "elevation": 1050},
                {"latitude": 47.6070, "longitude": -122.3330}
              ]
            }
            """;

        ResponseEntity<String> mockResponse = new ResponseEntity<>(
            partialElevationResponse, HttpStatus.OK);
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
            .thenReturn(mockResponse);

        // Create trail with only 3 coordinates to match response
        HikingTrail partialTrail = new HikingTrail(
            "partial-trail", "Partial Trail", new Location(47.6062, -122.3321));
        List<Coordinate> partialGeometry = new ArrayList<>();
        partialGeometry.add(new Coordinate(47.6062, -122.3321));
        partialGeometry.add(new Coordinate(47.6065, -122.3325));
        partialGeometry.add(new Coordinate(47.6070, -122.3330));
        partialTrail.setGeometry(partialGeometry);

        // When
        HikingTrail enriched = openElevationClient.enrichTrailWithElevation(partialTrail);

        // Then - Should calculate gain/loss from available data only
        assertThat(enriched.getElevationGainMeters()).isNotNull();
        // Gain from first two points: 1050 - 1000 = 50m
        assertThat(enriched.getElevationGainMeters()).isEqualTo(50);
        assertThat(enriched.getElevationLossMeters()).isEqualTo(0);

        // Verify no exceptions thrown and trail is returned
        assertThat(enriched.getId()).isEqualTo(partialTrail.getId());
    }
}
