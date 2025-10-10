package com.bbthechange.inviter.hiking;

import com.bbthechange.inviter.dto.HikingTrail;
import com.bbthechange.inviter.model.Location;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OverpassApiClient.
 * Tests querying OpenStreetMap Overpass API and parsing responses.
 */
@ExtendWith(MockitoExtension.class)
class OverpassApiClientTest {

    @Mock
    private RestTemplate restTemplate;

    private ObjectMapper objectMapper;
    private OverpassApiClient overpassApiClient;

    // Test data
    private Location testLocation;
    private String validOsmResponse;
    private String osmResponseWithTwoTrails;
    private String osmResponseWithDifficulties;
    private String osmResponseWithoutName;
    private String osmResponseWithRelation;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        overpassApiClient = new OverpassApiClient(restTemplate, objectMapper);

        testLocation = new Location(47.6062, -122.3321); // Seattle

        // Valid OSM response with single trail
        validOsmResponse = """
            {
              "elements": [
                {
                  "type": "way",
                  "id": 123456,
                  "tags": {
                    "name": "Test Trail",
                    "route": "hiking",
                    "sac_scale": "hiking"
                  },
                  "geometry": [
                    {"lat": 47.6062, "lon": -122.3321},
                    {"lat": 47.6065, "lon": -122.3325}
                  ]
                }
              ]
            }
            """;

        // OSM response with two trails
        osmResponseWithTwoTrails = """
            {
              "elements": [
                {
                  "type": "way",
                  "id": 123456,
                  "tags": {
                    "name": "First Trail",
                    "route": "hiking",
                    "sac_scale": "hiking"
                  },
                  "geometry": [
                    {"lat": 47.6062, "lon": -122.3321},
                    {"lat": 47.6065, "lon": -122.3325}
                  ]
                },
                {
                  "type": "way",
                  "id": 789012,
                  "tags": {
                    "name": "Second Trail",
                    "route": "hiking",
                    "sac_scale": "mountain_hiking"
                  },
                  "geometry": [
                    {"lat": 47.6070, "lon": -122.3330},
                    {"lat": 47.6073, "lon": -122.3334}
                  ]
                }
              ]
            }
            """;

        // OSM response with various difficulty levels
        osmResponseWithDifficulties = """
            {
              "elements": [
                {
                  "type": "way",
                  "id": 100001,
                  "tags": {
                    "name": "Easy Trail",
                    "route": "hiking",
                    "sac_scale": "hiking"
                  },
                  "geometry": [
                    {"lat": 47.6062, "lon": -122.3321},
                    {"lat": 47.6063, "lon": -122.3322}
                  ]
                },
                {
                  "type": "way",
                  "id": 100002,
                  "tags": {
                    "name": "Moderate Trail",
                    "route": "hiking",
                    "sac_scale": "mountain_hiking"
                  },
                  "geometry": [
                    {"lat": 47.6062, "lon": -122.3321},
                    {"lat": 47.6063, "lon": -122.3322}
                  ]
                },
                {
                  "type": "way",
                  "id": 100003,
                  "tags": {
                    "name": "Hard Trail",
                    "route": "hiking",
                    "sac_scale": "demanding_mountain_hiking"
                  },
                  "geometry": [
                    {"lat": 47.6062, "lon": -122.3321},
                    {"lat": 47.6063, "lon": -122.3322}
                  ]
                },
                {
                  "type": "way",
                  "id": 100004,
                  "tags": {
                    "name": "Very Hard Trail",
                    "route": "hiking",
                    "sac_scale": "demanding_alpine_hiking"
                  },
                  "geometry": [
                    {"lat": 47.6062, "lon": -122.3321},
                    {"lat": 47.6063, "lon": -122.3322}
                  ]
                },
                {
                  "type": "way",
                  "id": 100005,
                  "tags": {
                    "name": "Unknown Difficulty Trail",
                    "route": "hiking",
                    "sac_scale": "some_unknown_scale"
                  },
                  "geometry": [
                    {"lat": 47.6062, "lon": -122.3321},
                    {"lat": 47.6063, "lon": -122.3322}
                  ]
                }
              ]
            }
            """;

        // OSM response without name tag
        osmResponseWithoutName = """
            {
              "elements": [
                {
                  "type": "way",
                  "id": 999999,
                  "tags": {
                    "route": "hiking"
                  },
                  "geometry": [
                    {"lat": 47.6062, "lon": -122.3321},
                    {"lat": 47.6065, "lon": -122.3325}
                  ]
                }
              ]
            }
            """;

        // OSM response with relation type (multi-way trail)
        osmResponseWithRelation = """
            {
              "elements": [
                {
                  "type": "relation",
                  "id": 555555,
                  "tags": {
                    "name": "Long Multi-Way Trail",
                    "route": "hiking",
                    "sac_scale": "mountain_hiking"
                  },
                  "members": [
                    {
                      "type": "way",
                      "ref": 111,
                      "geometry": [
                        {"lat": 47.6062, "lon": -122.3321},
                        {"lat": 47.6065, "lon": -122.3325}
                      ]
                    },
                    {
                      "type": "way",
                      "ref": 222,
                      "geometry": [
                        {"lat": 47.6065, "lon": -122.3325},
                        {"lat": 47.6070, "lon": -122.3330}
                      ]
                    }
                  ]
                }
              ]
            }
            """;
    }

    // Test 1: Valid location query returns parsed trails
    @Test
    void searchTrailsNearLocation_ValidLocation_ReturnsTrails() {
        // Given
        ResponseEntity<String> mockResponse = new ResponseEntity<>(
            osmResponseWithTwoTrails, HttpStatus.OK);
        when(restTemplate.postForEntity(
            anyString(), any(HttpEntity.class), eq(String.class)))
            .thenReturn(mockResponse);

        // When
        List<HikingTrail> trails = overpassApiClient.searchTrailsNearLocation(testLocation, 5000);

        // Then
        assertThat(trails).hasSize(2);

        // Verify first trail
        HikingTrail firstTrail = trails.get(0);
        assertThat(firstTrail.getId()).isEqualTo("osm-way-123456");
        assertThat(firstTrail.getName()).isEqualTo("First Trail");
        assertThat(firstTrail.getSource()).isEqualTo("OSM");
        assertThat(firstTrail.getExternalId()).isEqualTo("way/123456");
        assertThat(firstTrail.getExternalLink()).isEqualTo("https://www.openstreetmap.org/way/123456");
        assertThat(firstTrail.getLocation()).isNotNull();
        assertThat(firstTrail.getLocation().getLatitude()).isEqualTo(47.6062);
        assertThat(firstTrail.getLocation().getLongitude()).isEqualTo(-122.3321);
        assertThat(firstTrail.getDistanceKm()).isGreaterThan(0.0);

        // Verify second trail
        HikingTrail secondTrail = trails.get(1);
        assertThat(secondTrail.getId()).isEqualTo("osm-way-789012");
        assertThat(secondTrail.getName()).isEqualTo("Second Trail");

        // Verify RestTemplate was called with correct parameters
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(
            anyString(), entityCaptor.capture(), eq(String.class));

        HttpEntity<String> capturedEntity = entityCaptor.getValue();
        String requestBody = capturedEntity.getBody();
        assertThat(requestBody).contains("data=");
        assertThat(requestBody).contains("47.606200");
        assertThat(requestBody).contains("-122.332100");
        assertThat(requestBody).contains("5000");
    }

    // Test 2: API failure returns empty list
    @Test
    void searchTrailsNearLocation_ApiFailure_ReturnsEmptyList() {
        // Given
        when(restTemplate.postForEntity(
            anyString(), any(HttpEntity.class), eq(String.class)))
            .thenThrow(new RestClientException("API is down"));

        // When
        List<HikingTrail> trails = overpassApiClient.searchTrailsNearLocation(testLocation, 5000);

        // Then
        assertThat(trails).isNotNull();
        assertThat(trails).isEmpty();
    }

    // Test 3: Non-200 response returns empty list
    @Test
    void searchTrailsNearLocation_Non200Response_ReturnsEmptyList() {
        // Given
        ResponseEntity<String> mockResponse = new ResponseEntity<>(
            "Not Found", HttpStatus.NOT_FOUND);
        when(restTemplate.postForEntity(
            anyString(), any(HttpEntity.class), eq(String.class)))
            .thenReturn(mockResponse);

        // When
        List<HikingTrail> trails = overpassApiClient.searchTrailsNearLocation(testLocation, 5000);

        // Then
        assertThat(trails).isNotNull();
        assertThat(trails).isEmpty();
    }

    // Test 4: Name search with location returns matching trails
    @Test
    void searchTrailsByName_WithLocation_ReturnsMatchingTrails() {
        // Given
        String searchName = "Wonderland";
        String osmResponseWithWonderland = """
            {
              "elements": [
                {
                  "type": "way",
                  "id": 987654,
                  "tags": {
                    "name": "Mount Rainier Wonderland Trail",
                    "route": "hiking",
                    "sac_scale": "mountain_hiking"
                  },
                  "geometry": [
                    {"lat": 46.8523, "lon": -121.7603},
                    {"lat": 46.8530, "lon": -121.7610}
                  ]
                }
              ]
            }
            """;

        ResponseEntity<String> mockResponse = new ResponseEntity<>(
            osmResponseWithWonderland, HttpStatus.OK);
        when(restTemplate.postForEntity(
            anyString(), any(HttpEntity.class), eq(String.class)))
            .thenReturn(mockResponse);

        // When
        List<HikingTrail> trails = overpassApiClient.searchTrailsByName(searchName, testLocation);

        // Then
        assertThat(trails).hasSize(1);
        HikingTrail trail = trails.get(0);
        assertThat(trail.getName()).contains("Wonderland");
        assertThat(trail.getSource()).isEqualTo("OSM");

        // Verify query includes location (around clause)
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(
            anyString(), entityCaptor.capture(), eq(String.class));

        HttpEntity<String> capturedEntity = entityCaptor.getValue();
        String requestBody = capturedEntity.getBody();
        assertThat(requestBody).contains("Wonderland");
        assertThat(requestBody).contains("around");
        assertThat(requestBody).contains("50000"); // 50km radius
    }

    // Test 5: Name search without location uses global search
    @Test
    void searchTrailsByName_WithoutLocation_UsesGlobalSearch() {
        // Given
        String searchName = "Pacific";
        String osmResponseWithPCT = """
            {
              "elements": [
                {
                  "type": "relation",
                  "id": 1225378,
                  "tags": {
                    "name": "Pacific Crest Trail",
                    "route": "hiking"
                  },
                  "members": [
                    {
                      "type": "way",
                      "ref": 111,
                      "geometry": [
                        {"lat": 49.0, "lon": -120.5},
                        {"lat": 49.1, "lon": -120.6}
                      ]
                    }
                  ]
                }
              ]
            }
            """;

        ResponseEntity<String> mockResponse = new ResponseEntity<>(
            osmResponseWithPCT, HttpStatus.OK);
        when(restTemplate.postForEntity(
            anyString(), any(HttpEntity.class), eq(String.class)))
            .thenReturn(mockResponse);

        // When
        List<HikingTrail> trails = overpassApiClient.searchTrailsByName(searchName, null);

        // Then
        assertThat(trails).hasSize(1);
        HikingTrail trail = trails.get(0);
        assertThat(trail.getName()).contains("Pacific");

        // Verify query does NOT include around clause
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(
            anyString(), entityCaptor.capture(), eq(String.class));

        HttpEntity<String> capturedEntity = entityCaptor.getValue();
        String requestBody = capturedEntity.getBody();
        assertThat(requestBody).contains("Pacific");
        assertThat(requestBody).doesNotContain("around");
        assertThat(requestBody).contains("100"); // Global limit
    }

    // Test 6: Distance calculation from geometry
    @Test
    void parseTrailElement_ValidOsmWay_CalculatesDistance() {
        // Given - Response with known coordinates forming approximately 10km trail
        // Using coordinates that are roughly 5km apart, with 3 points = ~10km total
        String osmResponseWithKnownDistance = """
            {
              "elements": [
                {
                  "type": "way",
                  "id": 999000,
                  "tags": {
                    "name": "Distance Test Trail",
                    "route": "hiking"
                  },
                  "geometry": [
                    {"lat": 47.6062, "lon": -122.3321},
                    {"lat": 47.6512, "lon": -122.3321},
                    {"lat": 47.6962, "lon": -122.3321}
                  ]
                }
              ]
            }
            """;

        ResponseEntity<String> mockResponse = new ResponseEntity<>(
            osmResponseWithKnownDistance, HttpStatus.OK);
        when(restTemplate.postForEntity(
            anyString(), any(HttpEntity.class), eq(String.class)))
            .thenReturn(mockResponse);

        // When
        List<HikingTrail> trails = overpassApiClient.searchTrailsNearLocation(testLocation, 5000);

        // Then
        assertThat(trails).hasSize(1);
        HikingTrail trail = trails.get(0);
        assertThat(trail.getDistanceKm()).isNotNull();
        assertThat(trail.getDistanceKm()).isGreaterThan(0.0);
        assertThat(trail.getDistanceKm()).isPositive();
        // The distance should be around 10km (0.045 degrees * 2 segments ≈ 10km at this latitude)
        assertThat(trail.getDistanceKm()).isBetween(8.0, 12.0);
    }

    // Test 7: sac_scale to difficulty conversion
    @Test
    void parseSacScale_VariousDifficulties_CorrectMapping() {
        // Given
        ResponseEntity<String> mockResponse = new ResponseEntity<>(
            osmResponseWithDifficulties, HttpStatus.OK);
        when(restTemplate.postForEntity(
            anyString(), any(HttpEntity.class), eq(String.class)))
            .thenReturn(mockResponse);

        // When
        List<HikingTrail> trails = overpassApiClient.searchTrailsNearLocation(testLocation, 5000);

        // Then
        assertThat(trails).hasSize(5);

        // Verify difficulty mappings
        HikingTrail easyTrail = trails.stream()
            .filter(t -> t.getName().equals("Easy Trail"))
            .findFirst().orElseThrow();
        assertThat(easyTrail.getDifficulty()).isEqualTo("Easy");

        HikingTrail moderateTrail = trails.stream()
            .filter(t -> t.getName().equals("Moderate Trail"))
            .findFirst().orElseThrow();
        assertThat(moderateTrail.getDifficulty()).isEqualTo("Moderate");

        HikingTrail hardTrail = trails.stream()
            .filter(t -> t.getName().equals("Hard Trail"))
            .findFirst().orElseThrow();
        assertThat(hardTrail.getDifficulty()).isEqualTo("Hard");

        HikingTrail veryHardTrail = trails.stream()
            .filter(t -> t.getName().equals("Very Hard Trail"))
            .findFirst().orElseThrow();
        assertThat(veryHardTrail.getDifficulty()).isEqualTo("Very Hard");

        HikingTrail unknownTrail = trails.stream()
            .filter(t -> t.getName().equals("Unknown Difficulty Trail"))
            .findFirst().orElseThrow();
        assertThat(unknownTrail.getDifficulty()).isEqualTo("Unknown");
    }

    // Test 8: Data quality assessment
    @Test
    void assessDataQuality_VariousCompleteness_CorrectQualityScores() {
        // Given - Response with trails of varying completeness
        String osmResponseWithQualityLevels = """
            {
              "elements": [
                {
                  "type": "way",
                  "id": 200001,
                  "tags": {
                    "name": "High Quality Trail",
                    "route": "hiking",
                    "sac_scale": "mountain_hiking",
                    "surface": "ground",
                    "operator": "National Park Service"
                  },
                  "geometry": [
                    {"lat": 47.6062, "lon": -122.3321},
                    {"lat": 47.6065, "lon": -122.3325},
                    {"lat": 47.6070, "lon": -122.3330}
                  ]
                },
                {
                  "type": "way",
                  "id": 200002,
                  "tags": {
                    "name": "Medium Quality Trail",
                    "route": "hiking"
                  },
                  "geometry": [
                    {"lat": 47.6062, "lon": -122.3321},
                    {"lat": 47.6065, "lon": -122.3325}
                  ]
                },
                {
                  "type": "way",
                  "id": 200003,
                  "tags": {
                    "name": "Low Quality Trail",
                    "route": "hiking"
                  },
                  "geometry": [
                    {"lat": 47.6062, "lon": -122.3321}
                  ]
                }
              ]
            }
            """;

        ResponseEntity<String> mockResponse = new ResponseEntity<>(
            osmResponseWithQualityLevels, HttpStatus.OK);
        when(restTemplate.postForEntity(
            anyString(), any(HttpEntity.class), eq(String.class)))
            .thenReturn(mockResponse);

        // When
        List<HikingTrail> trails = overpassApiClient.searchTrailsNearLocation(testLocation, 5000);

        // Then
        assertThat(trails).hasSize(3);

        // High quality: has name, location, distance, geometry (3+ points), difficulty
        HikingTrail highQuality = trails.stream()
            .filter(t -> t.getName().equals("High Quality Trail"))
            .findFirst().orElseThrow();
        assertThat(highQuality.getQuality()).isEqualTo(HikingTrail.DataQuality.HIGH);

        // Medium quality: has name, location, distance, geometry (2 points)
        HikingTrail mediumQuality = trails.stream()
            .filter(t -> t.getName().equals("Medium Quality Trail"))
            .findFirst().orElseThrow();
        assertThat(mediumQuality.getQuality()).isEqualTo(HikingTrail.DataQuality.MEDIUM);

        // Low quality: has name, location, but minimal geometry (1 point = no distance)
        HikingTrail lowQuality = trails.stream()
            .filter(t -> t.getName().equals("Low Quality Trail"))
            .findFirst().orElseThrow();
        assertThat(lowQuality.getQuality()).isEqualTo(HikingTrail.DataQuality.LOW);
    }

    // Test 9: Trail without name uses "Unnamed Trail"
    @Test
    void parseTrailElement_MissingName_UsesUnnamedTrail() {
        // Given
        ResponseEntity<String> mockResponse = new ResponseEntity<>(
            osmResponseWithoutName, HttpStatus.OK);
        when(restTemplate.postForEntity(
            anyString(), any(HttpEntity.class), eq(String.class)))
            .thenReturn(mockResponse);

        // When
        List<HikingTrail> trails = overpassApiClient.searchTrailsNearLocation(testLocation, 5000);

        // Then
        assertThat(trails).hasSize(1);
        HikingTrail trail = trails.get(0);
        assertThat(trail.getName()).isEqualTo("Unnamed Trail");
        assertThat(trail.getId()).isEqualTo("osm-way-999999");
        assertThat(trail.getSource()).isEqualTo("OSM");
        assertThat(trail.getLocation()).isNotNull();
    }

    // Test 10: Relation type extracts geometry from members
    @Test
    void parseTrailElement_RelationType_ExtractsGeometryFromMembers() {
        // Given
        ResponseEntity<String> mockResponse = new ResponseEntity<>(
            osmResponseWithRelation, HttpStatus.OK);
        when(restTemplate.postForEntity(
            anyString(), any(HttpEntity.class), eq(String.class)))
            .thenReturn(mockResponse);

        // When
        List<HikingTrail> trails = overpassApiClient.searchTrailsNearLocation(testLocation, 5000);

        // Then
        assertThat(trails).hasSize(1);
        HikingTrail trail = trails.get(0);
        assertThat(trail.getId()).isEqualTo("osm-relation-555555");
        assertThat(trail.getName()).isEqualTo("Long Multi-Way Trail");
        assertThat(trail.getExternalId()).isEqualTo("relation/555555");
        assertThat(trail.getExternalLink()).isEqualTo("https://www.openstreetmap.org/relation/555555");

        // Verify geometry is extracted from all members
        assertThat(trail.getGeometry()).isNotNull();
        assertThat(trail.getGeometry()).hasSize(4); // 2 points from first way + 2 from second

        // Verify distance is calculated from all member geometries
        assertThat(trail.getDistanceKm()).isGreaterThan(0.0);

        // Verify difficulty is parsed
        assertThat(trail.getDifficulty()).isEqualTo("Moderate");
    }
}