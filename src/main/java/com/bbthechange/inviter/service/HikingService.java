package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.HikingTrail;
import com.bbthechange.inviter.hiking.OpenElevationClient;
import com.bbthechange.inviter.hiking.OverpassApiClient;
import com.bbthechange.inviter.model.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for hiking trail search and enrichment.
 * Orchestrates OpenStreetMap and Open-Elevation APIs.
 */
@Service
public class HikingService {

    private static final Logger logger = LoggerFactory.getLogger(HikingService.class);

    private final OverpassApiClient overpassClient;
    private final OpenElevationClient elevationClient;

    public HikingService(OverpassApiClient overpassClient,
                        OpenElevationClient elevationClient) {
        this.overpassClient = overpassClient;
        this.elevationClient = elevationClient;
    }

    /**
     * Search for hiking trails by name.
     *
     * @param trailName Name to search for
     * @param location Optional center point for prioritizing nearby results
     * @param includeElevation Whether to fetch elevation data (slower)
     * @param includeGeometry Whether to include full trail geometry (reduces response size if false)
     * @return List of matching trails, sorted by relevance
     */
    public List<HikingTrail> searchTrailsByName(String trailName, Location location, boolean includeElevation, boolean includeGeometry) {
        logger.info("Searching trails by name: '{}', location: {}, includeElevation: {}, includeGeometry: {}",
                   trailName, location, includeElevation, includeGeometry);

        // Search OpenStreetMap
        List<HikingTrail> trails = overpassClient.searchTrailsByName(trailName, location);

        logger.info("Found {} trails from OSM", trails.size());

        // Enrich with elevation data if requested
        if (includeElevation) {
            trails = enrichTrailsWithElevation(trails);
        }

        // Strip geometry if not requested to reduce response size
        if (!includeGeometry) {
            trails = stripGeometry(trails);
        }

        // Sort by data quality and distance from location
        if (location != null) {
            trails = sortTrailsByProximity(trails, location);
        } else {
            trails = sortTrailsByQuality(trails);
        }

        return trails;
    }

    /**
     * Search for hiking trails near a location.
     *
     * @param location Center point for search
     * @param radiusKm Search radius in kilometers
     * @param includeElevation Whether to fetch elevation data
     * @param includeGeometry Whether to include full trail geometry (reduces response size if false)
     * @return List of nearby trails, sorted by distance
     */
    public List<HikingTrail> searchTrailsNearLocation(Location location, Double radiusKm, boolean includeElevation, boolean includeGeometry) {
        if (radiusKm == null) {
            radiusKm = 5.0; // Default 5km
        }

        int radiusMeters = (int) (radiusKm * 1000);

        logger.info("Searching trails near {},{} within {}km, includeGeometry: {}",
                   location.getLatitude(), location.getLongitude(), radiusKm, includeGeometry);

        // Search OpenStreetMap
        List<HikingTrail> trails = overpassClient.searchTrailsNearLocation(location, radiusMeters);

        logger.info("Found {} trails from OSM", trails.size());

        // Enrich with elevation data if requested
        if (includeElevation) {
            trails = enrichTrailsWithElevation(trails);
        }

        // Strip geometry if not requested to reduce response size
        if (!includeGeometry) {
            trails = stripGeometry(trails);
        }

        // Sort by proximity
        trails = sortTrailsByProximity(trails, location);

        return trails;
    }

    /**
     * Get detailed information for a specific trail by ID.
     * Always includes elevation data for detail view.
     *
     * @param trailId Trail identifier (format: "osm-way-123456")
     * @return Trail with full details, or null if not found
     */
    public HikingTrail getTrailById(String trailId) {
        logger.info("Getting trail details for ID: {}", trailId);

        // For MVP, we'll need to search and filter
        // In production, this would query a cache/database
        // For now, return null and implement caching later
        logger.warn("getTrailById not yet implemented - requires caching layer");
        return null;
    }

    /**
     * Suggest hiking trails based on location and preferences.
     * Returns popular/high-quality trails in the area.
     *
     * @param location User's location
     * @param radiusKm Search radius
     * @param difficulty Optional difficulty filter ("Easy", "Moderate", "Hard")
     * @param maxDistanceKm Optional max trail length filter
     * @param includeGeometry Whether to include full trail geometry (reduces response size if false)
     * @return List of suggested trails
     */
    public List<HikingTrail> suggestTrails(Location location, Double radiusKm,
                                          String difficulty, Double maxDistanceKm, boolean includeGeometry) {
        logger.info("Suggesting trails near {},{} within {}km, difficulty: {}, maxDistance: {}km, includeGeometry: {}",
                   location.getLatitude(), location.getLongitude(), radiusKm, difficulty, maxDistanceKm, includeGeometry);

        // Search for trails nearby (without elevation for performance, without geometry initially)
        List<HikingTrail> trails = searchTrailsNearLocation(location, radiusKm, false, true);

        // Filter by difficulty if specified
        if (difficulty != null && !difficulty.isEmpty()) {
            trails = trails.stream()
                .filter(trail -> difficulty.equalsIgnoreCase(trail.getDifficulty()))
                .collect(Collectors.toList());
        }

        // Filter by max distance if specified
        if (maxDistanceKm != null) {
            trails = trails.stream()
                .filter(trail -> trail.getDistanceKm() != null && trail.getDistanceKm() <= maxDistanceKm)
                .collect(Collectors.toList());
        }

        // Sort by quality and return top results
        trails = sortTrailsByQuality(trails);

        // Limit to top 20 suggestions
        List<HikingTrail> suggestions = trails.stream()
            .limit(20)
            .collect(Collectors.toList());

        // Strip geometry if not requested
        if (!includeGeometry) {
            suggestions = stripGeometry(suggestions);
        }

        return suggestions;
    }

    /**
     * Enrich trails with elevation data.
     * Processes trails in parallel for performance.
     */
    private List<HikingTrail> enrichTrailsWithElevation(List<HikingTrail> trails) {
        logger.info("Enriching {} trails with elevation data", trails.size());

        List<HikingTrail> enriched = new ArrayList<>();

        for (HikingTrail trail : trails) {
            try {
                HikingTrail enrichedTrail = elevationClient.enrichTrailWithElevation(trail);
                enriched.add(enrichedTrail);
            } catch (Exception e) {
                logger.error("Error enriching trail {}: {}", trail.getId(), e.getMessage());
                // Add original trail without elevation
                enriched.add(trail);
            }
        }

        return enriched;
    }

    /**
     * Sort trails by proximity to a location.
     */
    private List<HikingTrail> sortTrailsByProximity(List<HikingTrail> trails, Location location) {
        return trails.stream()
            .sorted(Comparator.comparingDouble(trail -> {
                if (trail.getLocation() == null) {
                    return Double.MAX_VALUE;
                }
                return calculateDistance(
                    location.getLatitude(), location.getLongitude(),
                    trail.getLocation().getLatitude(), trail.getLocation().getLongitude()
                );
            }))
            .collect(Collectors.toList());
    }

    /**
     * Sort trails by data quality (high quality first).
     */
    private List<HikingTrail> sortTrailsByQuality(List<HikingTrail> trails) {
        return trails.stream()
            .sorted(Comparator.comparing(HikingTrail::getQuality,
                Comparator.nullsLast(Comparator.naturalOrder())))
            .collect(Collectors.toList());
    }

    /**
     * Calculate distance between two points using Haversine formula.
     * Returns distance in kilometers.
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int EARTH_RADIUS_KM = 6371;

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }

    /**
     * Strip geometry from trails to reduce response size.
     * Keeps only the location (starting point) but removes full trail path.
     *
     * @param trails List of trails with geometry
     * @return List of trails without geometry
     */
    private List<HikingTrail> stripGeometry(List<HikingTrail> trails) {
        trails.forEach(trail -> trail.setGeometry(null));
        return trails;
    }
}
