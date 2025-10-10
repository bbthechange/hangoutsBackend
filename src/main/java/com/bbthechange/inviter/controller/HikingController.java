package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.HikingTrail;
import com.bbthechange.inviter.model.Location;
import com.bbthechange.inviter.service.HikingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API controller for hiking trail search.
 */
@RestController
@RequestMapping("/hiking")
@Tag(name = "Hiking", description = "Hiking trail search and information")
public class HikingController {

    private final HikingService hikingService;

    public HikingController(HikingService hikingService) {
        this.hikingService = hikingService;
    }

    /**
     * Search for hiking trails by name.
     *
     * @param name Trail name to search for
     * @param latitude Optional latitude for prioritizing nearby results
     * @param longitude Optional longitude for prioritizing nearby results
     * @param includeElevation Whether to include elevation data (slower, default false)
     * @param includeGeometry Whether to include full trail geometry (default false to reduce response size)
     * @return List of matching trails
     */
    @GetMapping("/trails/search")
    @Operation(summary = "Search trails by name",
              description = "Search for hiking trails by name. Optionally provide location to prioritize nearby results.")
    public ResponseEntity<List<HikingTrail>> searchTrailsByName(
            @Parameter(description = "Trail name to search for", required = true)
            @RequestParam String name,

            @Parameter(description = "Latitude for prioritizing nearby results")
            @RequestParam(required = false) Double latitude,

            @Parameter(description = "Longitude for prioritizing nearby results")
            @RequestParam(required = false) Double longitude,

            @Parameter(description = "Include elevation data (slower)")
            @RequestParam(required = false, defaultValue = "false") boolean includeElevation,

            @Parameter(description = "Include full trail geometry coordinates (default false)")
            @RequestParam(required = false, defaultValue = "false") boolean includeGeometry
    ) {
        Location location = null;
        if (latitude != null && longitude != null) {
            location = new Location(latitude, longitude);
        }

        List<HikingTrail> trails = hikingService.searchTrailsByName(name, location, includeElevation, includeGeometry);

        return ResponseEntity.ok(trails);
    }

    /**
     * Search for hiking trails near a location.
     *
     * @param latitude Center latitude
     * @param longitude Center longitude
     * @param radiusKm Search radius in kilometers (default 5km)
     * @param includeElevation Whether to include elevation data
     * @param includeGeometry Whether to include full trail geometry (default false to reduce response size)
     * @return List of nearby trails
     */
    @GetMapping("/trails/nearby")
    @Operation(summary = "Search trails by location",
              description = "Find hiking trails near a specific location within a given radius.")
    public ResponseEntity<List<HikingTrail>> searchTrailsNearby(
            @Parameter(description = "Center point latitude", required = true)
            @RequestParam Double latitude,

            @Parameter(description = "Center point longitude", required = true)
            @RequestParam Double longitude,

            @Parameter(description = "Search radius in kilometers (default 5km)")
            @RequestParam(required = false, defaultValue = "5.0") Double radiusKm,

            @Parameter(description = "Include elevation data (slower)")
            @RequestParam(required = false, defaultValue = "false") boolean includeElevation,

            @Parameter(description = "Include full trail geometry coordinates (default false)")
            @RequestParam(required = false, defaultValue = "false") boolean includeGeometry
    ) {
        Location location = new Location(latitude, longitude);

        List<HikingTrail> trails = hikingService.searchTrailsNearLocation(location, radiusKm, includeElevation, includeGeometry);

        return ResponseEntity.ok(trails);
    }

    /**
     * Get trail suggestions based on location and preferences.
     *
     * @param latitude User's latitude
     * @param longitude User's longitude
     * @param radiusKm Search radius in kilometers (default 10km)
     * @param difficulty Optional difficulty filter ("Easy", "Moderate", "Hard")
     * @param maxDistanceKm Optional max trail length in kilometers
     * @param includeGeometry Whether to include full trail geometry (default false to reduce response size)
     * @return List of suggested trails
     */
    @GetMapping("/trails/suggest")
    @Operation(summary = "Get trail suggestions",
              description = "Get personalized trail suggestions based on location and preferences. Returns high-quality trails sorted by relevance.")
    public ResponseEntity<List<HikingTrail>> suggestTrails(
            @Parameter(description = "User's latitude", required = true)
            @RequestParam Double latitude,

            @Parameter(description = "User's longitude", required = true)
            @RequestParam Double longitude,

            @Parameter(description = "Search radius in kilometers (default 10km)")
            @RequestParam(required = false, defaultValue = "10.0") Double radiusKm,

            @Parameter(description = "Difficulty filter: Easy, Moderate, Hard, Very Hard")
            @RequestParam(required = false) String difficulty,

            @Parameter(description = "Maximum trail length in kilometers")
            @RequestParam(required = false) Double maxDistanceKm,

            @Parameter(description = "Include full trail geometry coordinates (default false)")
            @RequestParam(required = false, defaultValue = "false") boolean includeGeometry
    ) {
        Location location = new Location(latitude, longitude);

        List<HikingTrail> trails = hikingService.suggestTrails(location, radiusKm, difficulty, maxDistanceKm, includeGeometry);

        return ResponseEntity.ok(trails);
    }

    /**
     * Get detailed information for a specific trail.
     *
     * @param trailId Trail identifier (e.g., "osm-way-123456")
     * @return Trail details with elevation data
     */
    @GetMapping("/trails/{trailId}")
    @Operation(summary = "Get trail details",
              description = "Get detailed information for a specific trail, including elevation profile.")
    public ResponseEntity<HikingTrail> getTrailById(
            @Parameter(description = "Trail identifier", required = true)
            @PathVariable String trailId
    ) {
        HikingTrail trail = hikingService.getTrailById(trailId);

        if (trail == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(trail);
    }

    /**
     * Health check endpoint for hiking service.
     */
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check if hiking service is operational")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Hiking service is operational");
    }
}
