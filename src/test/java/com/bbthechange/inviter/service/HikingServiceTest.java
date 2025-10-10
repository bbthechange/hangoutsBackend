package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.HikingTrail;
import com.bbthechange.inviter.dto.HikingTrail.DataQuality;
import com.bbthechange.inviter.hiking.OpenElevationClient;
import com.bbthechange.inviter.hiking.OverpassApiClient;
import com.bbthechange.inviter.model.Coordinate;
import com.bbthechange.inviter.model.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for HikingService
 *
 * Test Coverage:
 * - searchTrailsByName - Name-based trail search with sorting
 * - searchTrailsNearLocation - Location-based trail search
 * - suggestTrails - Trail recommendations with filtering
 * - getTrailById - NYI method verification
 * - Elevation enrichment - Optional elevation data
 * - Geometry handling - includeGeometry flag behavior
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HikingService Tests")
class HikingServiceTest {

    @Mock
    private OverpassApiClient overpassClient;

    @Mock
    private OpenElevationClient elevationClient;

    @InjectMocks
    private HikingService hikingService;

    private Location testLocation;
    private HikingTrail trailNear;
    private HikingTrail trailFar;
    private HikingTrail trailMid;

    @BeforeEach
    void setUp() {
        testLocation = new Location(47.6062, -122.3321); // Seattle

        // Create test trails with different distances from Seattle
        trailNear = createTrailWithLocation("trail-1", "Trail Near", 47.6070, -122.3325);
        trailNear.setQuality(DataQuality.HIGH);
        trailNear.setDifficulty("Easy");
        trailNear.setDistanceKm(3.0);
        trailNear.setGeometry(createGeometry(50)); // 50 coordinates

        trailMid = createTrailWithLocation("trail-2", "Trail Mid", 47.6080, -122.3340);
        trailMid.setQuality(DataQuality.MEDIUM);
        trailMid.setDifficulty("Moderate");
        trailMid.setDistanceKm(8.0);
        trailMid.setGeometry(createGeometry(50));

        trailFar = createTrailWithLocation("trail-3", "Trail Far", 47.6100, -122.3300);
        trailFar.setQuality(DataQuality.LOW);
        trailFar.setDifficulty("Hard");
        trailFar.setDistanceKm(12.0);
        trailFar.setGeometry(createGeometry(50));
    }

    @Nested
    @DisplayName("searchTrailsByName Tests")
    class SearchTrailsByNameTests {

        @Test
        @DisplayName("Should sort trails by proximity when location is provided")
        void searchTrailsByName_WithLocation_ReturnsSortedByProximity() {
            // Arrange: Mock returns trails in arbitrary order (Far, Near, Mid)
            List<HikingTrail> mockTrails = Arrays.asList(trailFar, trailNear, trailMid);
            when(overpassClient.searchTrailsByName("Wonderland", testLocation))
                .thenReturn(mockTrails);

            // Act
            List<HikingTrail> result = hikingService.searchTrailsByName(
                "Wonderland", testLocation, false, false);

            // Assert: Should be sorted by proximity (Near, Mid, Far)
            assertEquals(3, result.size());
            assertEquals("Trail Near", result.get(0).getName());
            assertEquals("Trail Mid", result.get(1).getName());
            assertEquals("Trail Far", result.get(2).getName());

            // Verify geometry was stripped (includeGeometry=false)
            assertNull(result.get(0).getGeometry());
            assertNull(result.get(1).getGeometry());
            assertNull(result.get(2).getGeometry());

            verify(overpassClient).searchTrailsByName("Wonderland", testLocation);
            verify(elevationClient, never()).enrichTrailWithElevation(any());
        }

        @Test
        @DisplayName("Should sort trails by quality when location is not provided")
        void searchTrailsByName_WithoutLocation_ReturnsSortedByQuality() {
            // Arrange: Mock returns trails with quality [MEDIUM, HIGH, LOW]
            List<HikingTrail> mockTrails = Arrays.asList(trailMid, trailNear, trailFar);
            when(overpassClient.searchTrailsByName("Trail", null))
                .thenReturn(mockTrails);

            // Act
            List<HikingTrail> result = hikingService.searchTrailsByName(
                "Trail", null, false, false);

            // Assert: Should be sorted by quality [HIGH, MEDIUM, LOW]
            assertEquals(3, result.size());
            assertEquals(DataQuality.HIGH, result.get(0).getQuality());
            assertEquals(DataQuality.MEDIUM, result.get(1).getQuality());
            assertEquals(DataQuality.LOW, result.get(2).getQuality());

            // Verify geometry was stripped
            assertNull(result.get(0).getGeometry());

            verify(overpassClient).searchTrailsByName("Trail", null);
        }

        @Test
        @DisplayName("Should enrich trails with elevation when requested")
        void searchTrailsByName_WithElevation_EnrichesTrails() {
            // Arrange
            List<HikingTrail> mockTrails = Arrays.asList(trailNear, trailMid);
            when(overpassClient.searchTrailsByName("Trail", testLocation))
                .thenReturn(mockTrails);
            when(elevationClient.enrichTrailWithElevation(any(HikingTrail.class)))
                .thenAnswer(invocation -> {
                    HikingTrail trail = invocation.getArgument(0);
                    trail.setElevationGainMeters(500);
                    return trail;
                });

            // Act
            List<HikingTrail> result = hikingService.searchTrailsByName(
                "Trail", testLocation, true, false);

            // Assert: OpenElevationClient should be called for each trail
            assertEquals(2, result.size());
            verify(elevationClient, times(2)).enrichTrailWithElevation(any(HikingTrail.class));
        }

        @Test
        @DisplayName("Should preserve geometry when includeGeometry=true")
        void searchTrailsByName_WithIncludeGeometry_PreservesGeometry() {
            // Arrange: Trails with 50+ coordinates each
            List<HikingTrail> mockTrails = Arrays.asList(trailNear, trailMid);
            when(overpassClient.searchTrailsByName("Trail", testLocation))
                .thenReturn(mockTrails);

            // Act
            List<HikingTrail> result = hikingService.searchTrailsByName(
                "Trail", testLocation, false, true);

            // Assert: Geometry should NOT be null
            assertEquals(2, result.size());
            assertNotNull(result.get(0).getGeometry());
            assertNotNull(result.get(1).getGeometry());
            assertEquals(50, result.get(0).getGeometry().size());
            assertEquals(50, result.get(1).getGeometry().size());
        }
    }

    @Nested
    @DisplayName("searchTrailsNearLocation Tests")
    class SearchTrailsNearLocationTests {

        @Test
        @DisplayName("Should convert radius from kilometers to meters")
        void searchTrailsNearLocation_ValidLocation_ConvertsRadiusToMeters() {
            // Arrange
            List<HikingTrail> mockTrails = Arrays.asList(trailNear);
            when(overpassClient.searchTrailsNearLocation(testLocation, 10000))
                .thenReturn(mockTrails);

            // Act: radiusKm=10.0 should convert to 10000 meters
            List<HikingTrail> result = hikingService.searchTrailsNearLocation(
                testLocation, 10.0, false, false);

            // Assert
            assertEquals(1, result.size());
            assertNull(result.get(0).getGeometry()); // Geometry stripped
            verify(overpassClient).searchTrailsNearLocation(testLocation, 10000);
        }

        @Test
        @DisplayName("Should default to 5km radius when not specified")
        void searchTrailsNearLocation_NullRadius_DefaultsTo5km() {
            // Arrange
            List<HikingTrail> mockTrails = Arrays.asList(trailNear);
            when(overpassClient.searchTrailsNearLocation(testLocation, 5000))
                .thenReturn(mockTrails);

            // Act: radiusKm=null should default to 5km (5000 meters)
            List<HikingTrail> result = hikingService.searchTrailsNearLocation(
                testLocation, null, false, false);

            // Assert
            assertEquals(1, result.size());
            verify(overpassClient).searchTrailsNearLocation(testLocation, 5000);
        }

        @Test
        @DisplayName("Should sort trails by proximity to search location")
        void searchTrailsNearLocation_SortsNearestFirst() {
            // Arrange: Mock returns trails in arbitrary order
            List<HikingTrail> mockTrails = Arrays.asList(trailFar, trailMid, trailNear);
            when(overpassClient.searchTrailsNearLocation(testLocation, 5000))
                .thenReturn(mockTrails);

            // Act
            List<HikingTrail> result = hikingService.searchTrailsNearLocation(
                testLocation, 5.0, false, false);

            // Assert: Nearest trail should be first
            assertEquals(3, result.size());
            assertEquals("Trail Near", result.get(0).getName());
            assertNull(result.get(0).getGeometry()); // Geometry stripped
        }
    }

    @Nested
    @DisplayName("suggestTrails Tests")
    class SuggestTrailsTests {

        @Test
        @DisplayName("Should filter trails by difficulty (case-insensitive)")
        void suggestTrails_FiltersByDifficulty_ReturnsMatchingOnly() {
            // Arrange: Create trails with different difficulties
            HikingTrail easyTrail = createTrailWithLocation("easy", "Easy Trail", 47.6065, -122.3320);
            easyTrail.setDifficulty("Easy");
            easyTrail.setQuality(DataQuality.HIGH);
            easyTrail.setGeometry(createGeometry(50));

            HikingTrail moderateTrail1 = createTrailWithLocation("mod1", "Moderate 1", 47.6070, -122.3325);
            moderateTrail1.setDifficulty("Moderate");
            moderateTrail1.setQuality(DataQuality.HIGH);
            moderateTrail1.setGeometry(createGeometry(50));

            HikingTrail moderateTrail2 = createTrailWithLocation("mod2", "Moderate 2", 47.6075, -122.3330);
            moderateTrail2.setDifficulty("Moderate");
            moderateTrail2.setQuality(DataQuality.MEDIUM);
            moderateTrail2.setGeometry(createGeometry(50));

            HikingTrail hardTrail = createTrailWithLocation("hard", "Hard Trail", 47.6080, -122.3335);
            hardTrail.setDifficulty("Hard");
            hardTrail.setQuality(DataQuality.MEDIUM);
            hardTrail.setGeometry(createGeometry(50));

            List<HikingTrail> mockTrails = Arrays.asList(easyTrail, moderateTrail1, hardTrail, moderateTrail2);
            when(overpassClient.searchTrailsNearLocation(any(Location.class), anyInt()))
                .thenReturn(mockTrails);

            // Act: Filter by difficulty="Moderate"
            List<HikingTrail> result = hikingService.suggestTrails(
                testLocation, 10.0, "Moderate", null, false);

            // Assert: Only Moderate trails returned, geometry stripped
            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(t -> "Moderate".equals(t.getDifficulty())));
            assertNull(result.get(0).getGeometry());
        }

        @Test
        @DisplayName("Should filter trails by max distance")
        void suggestTrails_FiltersByMaxDistance_RemovesLongerTrails() {
            // Arrange: Trails with distances [3.0, 8.0, 12.0, 5.0]
            trailNear.setDistanceKm(3.0);
            trailMid.setDistanceKm(8.0);
            trailFar.setDistanceKm(12.0);

            HikingTrail trail4 = createTrailWithLocation("trail-4", "Trail 4", 47.6075, -122.3330);
            trail4.setDistanceKm(5.0);
            trail4.setQuality(DataQuality.MEDIUM);
            trail4.setGeometry(createGeometry(50));

            List<HikingTrail> mockTrails = Arrays.asList(trailNear, trailMid, trailFar, trail4);
            when(overpassClient.searchTrailsNearLocation(any(Location.class), anyInt()))
                .thenReturn(mockTrails);

            // Act: Filter by maxDistanceKm=10.0
            List<HikingTrail> result = hikingService.suggestTrails(
                testLocation, 10.0, null, 10.0, false);

            // Assert: Should return trails with distance <= 10km: [3.0, 8.0, 5.0]
            assertEquals(3, result.size());
            assertTrue(result.stream().allMatch(t -> t.getDistanceKm() != null && t.getDistanceKm() <= 10.0));
            assertFalse(result.contains(trailFar)); // 12km trail excluded
            assertNull(result.get(0).getGeometry());
        }

        @Test
        @DisplayName("Should limit results to top 20 trails")
        void suggestTrails_NoFilters_LimitsTo20Results() {
            // Arrange: Create 30 trails
            List<HikingTrail> mockTrails = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                HikingTrail trail = createTrailWithLocation("trail-" + i, "Trail " + i, 47.61 + i * 0.001, -122.33);
                trail.setQuality(i < 10 ? DataQuality.HIGH : i < 20 ? DataQuality.MEDIUM : DataQuality.LOW);
                trail.setGeometry(createGeometry(50));
                mockTrails.add(trail);
            }
            when(overpassClient.searchTrailsNearLocation(any(Location.class), anyInt()))
                .thenReturn(mockTrails);

            // Act
            List<HikingTrail> result = hikingService.suggestTrails(
                testLocation, 10.0, null, null, false);

            // Assert: Should return exactly 20 trails, highest quality first
            assertEquals(20, result.size());
            assertNull(result.get(0).getGeometry());
        }

        @Test
        @DisplayName("Should apply both difficulty and distance filters together")
        void suggestTrails_CombinedFilters_AppliesBoth() {
            // Arrange: Create trails with various difficulties and distances
            HikingTrail trail1 = createTrailWithLocation("1", "Easy 5km", 47.6065, -122.3320);
            trail1.setDifficulty("Easy");
            trail1.setDistanceKm(5.0);
            trail1.setQuality(DataQuality.HIGH);
            trail1.setGeometry(createGeometry(50));

            HikingTrail trail2 = createTrailWithLocation("2", "Moderate 8km", 47.6070, -122.3325);
            trail2.setDifficulty("Moderate");
            trail2.setDistanceKm(8.0);
            trail2.setQuality(DataQuality.HIGH);
            trail2.setGeometry(createGeometry(50));

            HikingTrail trail3 = createTrailWithLocation("3", "Moderate 12km", 47.6075, -122.3330);
            trail3.setDifficulty("Moderate");
            trail3.setDistanceKm(12.0);
            trail3.setQuality(DataQuality.MEDIUM);
            trail3.setGeometry(createGeometry(50));

            HikingTrail trail4 = createTrailWithLocation("4", "Hard 6km", 47.6080, -122.3335);
            trail4.setDifficulty("Hard");
            trail4.setDistanceKm(6.0);
            trail4.setQuality(DataQuality.MEDIUM);
            trail4.setGeometry(createGeometry(50));

            List<HikingTrail> mockTrails = Arrays.asList(trail1, trail2, trail3, trail4);
            when(overpassClient.searchTrailsNearLocation(any(Location.class), anyInt()))
                .thenReturn(mockTrails);

            // Act: Filter by difficulty="Moderate" AND maxDistanceKm=10.0
            List<HikingTrail> result = hikingService.suggestTrails(
                testLocation, 15.0, "Moderate", 10.0, false);

            // Assert: Only trail2 (Moderate 8km) should match both filters
            assertEquals(1, result.size());
            assertEquals("Moderate 8km", result.get(0).getName());
            assertEquals("Moderate", result.get(0).getDifficulty());
            assertEquals(8.0, result.get(0).getDistanceKm());
            assertNull(result.get(0).getGeometry());
        }
    }

    @Nested
    @DisplayName("getTrailById Tests")
    class GetTrailByIdTests {

        @Test
        @DisplayName("Should return null when not implemented")
        void getTrailById_NotImplemented_ReturnsNull() {
            // Act
            HikingTrail result = hikingService.getTrailById("osm-way-123456");

            // Assert: Should return null (not yet implemented)
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Elevation Enrichment Tests")
    class ElevationEnrichmentTests {

        @Test
        @DisplayName("Should handle client exceptions gracefully")
        void enrichTrailsWithElevation_HandlesClientExceptions() {
            // Arrange: 3 trails, elevation client throws exception on trail #2
            List<HikingTrail> mockTrails = Arrays.asList(trailNear, trailMid, trailFar);
            when(overpassClient.searchTrailsByName("Trail", testLocation))
                .thenReturn(mockTrails);

            when(elevationClient.enrichTrailWithElevation(trailNear))
                .thenAnswer(invocation -> {
                    HikingTrail trail = invocation.getArgument(0);
                    trail.setElevationGainMeters(300);
                    return trail;
                });

            when(elevationClient.enrichTrailWithElevation(trailMid))
                .thenThrow(new RuntimeException("Elevation API error"));

            when(elevationClient.enrichTrailWithElevation(trailFar))
                .thenAnswer(invocation -> {
                    HikingTrail trail = invocation.getArgument(0);
                    trail.setElevationGainMeters(500);
                    return trail;
                });

            // Act
            List<HikingTrail> result = hikingService.searchTrailsByName(
                "Trail", testLocation, true, false);

            // Assert: All 3 trails returned, #2 has no elevation, #1 and #3 enriched
            assertEquals(3, result.size());
            assertEquals(300, result.get(0).getElevationGainMeters()); // trailNear enriched
            assertNull(result.get(1).getElevationGainMeters()); // trailMid failed, no elevation
            assertEquals(500, result.get(2).getElevationGainMeters()); // trailFar enriched

            verify(elevationClient, times(3)).enrichTrailWithElevation(any(HikingTrail.class));
        }
    }

    // Helper methods

    private HikingTrail createTrailWithLocation(String id, String name, double lat, double lng) {
        Location location = new Location(lat, lng);
        HikingTrail trail = new HikingTrail(id, name, location);
        return trail;
    }

    private List<Coordinate> createGeometry(int size) {
        List<Coordinate> geometry = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            geometry.add(new Coordinate(47.6 + i * 0.0001, -122.3 + i * 0.0001));
        }
        return geometry;
    }
}
