package com.bbthechange.inviter.hiking;

import com.bbthechange.inviter.dto.HikingTrail;
import com.bbthechange.inviter.model.Coordinate;
import com.bbthechange.inviter.model.Location;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for OpenStreetMap Overpass API.
 * Queries hiking trails from OpenStreetMap data.
 *
 * API Docs: https://wiki.openstreetmap.org/wiki/Overpass_API
 */
@Component
public class OverpassApiClient {

    private static final Logger logger = LoggerFactory.getLogger(OverpassApiClient.class);
    private static final String OVERPASS_API_URL = "https://overpass-api.de/api/interpreter";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public OverpassApiClient(@Qualifier("externalRestTemplate") RestTemplate restTemplate,
                            ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Search for hiking trails near a location.
     *
     * @param location Center point for search
     * @param radiusMeters Search radius in meters (default 5000m = 5km)
     * @return List of hiking trails found
     */
    public List<HikingTrail> searchTrailsNearLocation(Location location, Integer radiusMeters) {
        if (radiusMeters == null) {
            radiusMeters = 5000; // Default 5km radius
        }

        String query = buildOverpassQuery(location, radiusMeters);
        logger.info("Searching OSM trails near {},{} within {}m",
                   location.getLatitude(), location.getLongitude(), radiusMeters);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<String> request = new HttpEntity<>("data=" + query, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                OVERPASS_API_URL,
                request,
                String.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseOverpassResponse(response.getBody());
            } else {
                logger.error("Overpass API returned status: {}", response.getStatusCode());
                return new ArrayList<>();
            }

        } catch (Exception e) {
            logger.error("Error querying Overpass API: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Search for hiking trails by name.
     *
     * @param trailName Name to search for
     * @param location Optional center point to prioritize nearby results
     * @return List of matching trails
     */
    public List<HikingTrail> searchTrailsByName(String trailName, Location location) {
        // For name search, use wider radius if location provided
        Integer radius = location != null ? 50000 : null; // 50km if location given

        String query = buildNameSearchQuery(trailName, location, radius);
        logger.info("Searching OSM trails by name: '{}' near {}", trailName, location);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<String> request = new HttpEntity<>("data=" + query, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                OVERPASS_API_URL,
                request,
                String.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseOverpassResponse(response.getBody());
            } else {
                logger.error("Overpass API returned status: {}", response.getStatusCode());
                return new ArrayList<>();
            }

        } catch (Exception e) {
            logger.error("Error querying Overpass API by name: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Build Overpass QL query for location-based search.
     */
    private String buildOverpassQuery(Location location, Integer radiusMeters) {
        // Query both ways and relations tagged with route=hiking
        // Request geometry output to calculate distance
        return String.format(
            "[out:json];" +
            "(" +
            "  way[\"route\"=\"hiking\"](around:%d,%.6f,%.6f);" +
            "  relation[\"route\"=\"hiking\"](around:%d,%.6f,%.6f);" +
            ");" +
            "out geom;",
            radiusMeters, location.getLatitude(), location.getLongitude(),
            radiusMeters, location.getLatitude(), location.getLongitude()
        );
    }

    /**
     * Build Overpass QL query for name-based search.
     */
    private String buildNameSearchQuery(String trailName, Location location, Integer radiusMeters) {
        if (location != null && radiusMeters != null) {
            // Search by name within radius
            return String.format(
                "[out:json];" +
                "(" +
                "  way[\"route\"=\"hiking\"][\"name\"~\"%s\",i](around:%d,%.6f,%.6f);" +
                "  relation[\"route\"=\"hiking\"][\"name\"~\"%s\",i](around:%d,%.6f,%.6f);" +
                ");" +
                "out geom;",
                trailName, radiusMeters, location.getLatitude(), location.getLongitude(),
                trailName, radiusMeters, location.getLatitude(), location.getLongitude()
            );
        } else {
            // Global name search (limited to 100 results)
            return String.format(
                "[out:json][timeout:25];" +
                "(" +
                "  way[\"route\"=\"hiking\"][\"name\"~\"%s\",i];" +
                "  relation[\"route\"=\"hiking\"][\"name\"~\"%s\",i];" +
                ");" +
                "out geom 100;",
                trailName, trailName
            );
        }
    }

    /**
     * Parse Overpass API JSON response into HikingTrail objects.
     */
    private List<HikingTrail> parseOverpassResponse(String json) {
        List<HikingTrail> trails = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode elements = root.get("elements");

            if (elements == null || !elements.isArray()) {
                logger.warn("No elements found in Overpass response");
                return trails;
            }

            for (JsonNode element : elements) {
                try {
                    HikingTrail trail = parseTrailElement(element);
                    if (trail != null) {
                        trails.add(trail);
                    }
                } catch (Exception e) {
                    logger.error("Error parsing trail element: {}", e.getMessage());
                }
            }

            logger.info("Parsed {} trails from Overpass response", trails.size());

        } catch (Exception e) {
            logger.error("Error parsing Overpass JSON: {}", e.getMessage(), e);
        }

        return trails;
    }

    /**
     * Parse a single OSM element into a HikingTrail.
     */
    private HikingTrail parseTrailElement(JsonNode element) {
        String osmId = element.has("id") ? element.get("id").asText() : null;
        String osmType = element.has("type") ? element.get("type").asText() : "unknown";

        if (osmId == null) {
            return null;
        }

        JsonNode tags = element.get("tags");
        if (tags == null) {
            return null; // No tags means no trail metadata
        }

        // Extract trail name
        String name = tags.has("name") ? tags.get("name").asText() : "Unnamed Trail";

        HikingTrail trail = new HikingTrail(
            "osm-" + osmType + "-" + osmId,
            name,
            null // Location will be set from geometry
        );

        // Set source information
        trail.setSource("OSM");
        trail.setExternalId(osmType + "/" + osmId);
        trail.setExternalLink("https://www.openstreetmap.org/" + osmType + "/" + osmId);

        // Extract geometry and calculate distance
        List<Coordinate> geometry = extractGeometry(element);
        trail.setGeometry(geometry);

        if (!geometry.isEmpty()) {
            // Set location to first point
            Coordinate firstPoint = geometry.get(0);
            trail.setLocation(new Location(firstPoint.getLatitude(), firstPoint.getLongitude()));

            // Calculate distance from geometry
            double distanceKm = calculateDistance(geometry);
            trail.setDistanceKm(distanceKm);
        }

        // Extract OSM tags metadata
        Map<String, String> metadata = new HashMap<>();

        // Difficulty (sac_scale)
        if (tags.has("sac_scale")) {
            String sacScale = tags.get("sac_scale").asText();
            trail.setDifficulty(convertSacScaleToDifficulty(sacScale));
            metadata.put("sac_scale", sacScale);
        }

        // Trail type
        if (tags.has("trail_visibility")) {
            metadata.put("trail_visibility", tags.get("trail_visibility").asText());
        }

        // Surface
        if (tags.has("surface")) {
            metadata.put("surface", tags.get("surface").asText());
        }

        // Description
        if (tags.has("description")) {
            metadata.put("description", tags.get("description").asText());
        }

        // Region/operator
        if (tags.has("operator")) {
            trail.setRegion(tags.get("operator").asText());
            metadata.put("operator", tags.get("operator").asText());
        }

        trail.setMetadata(metadata);
        trail.setTrailType("hiking");
        trail.setLastUpdated(System.currentTimeMillis());

        // Assess data quality
        trail.setQuality(assessDataQuality(trail));

        return trail;
    }

    /**
     * Extract coordinates from OSM element geometry.
     */
    private List<Coordinate> extractGeometry(JsonNode element) {
        List<Coordinate> coordinates = new ArrayList<>();

        // For ways with "geometry" output
        if (element.has("geometry")) {
            JsonNode geometry = element.get("geometry");
            for (JsonNode node : geometry) {
                double lat = node.get("lat").asDouble();
                double lon = node.get("lon").asDouble();
                coordinates.add(new Coordinate(lat, lon));
            }
        }
        // For relations, extract from member ways
        else if (element.has("members")) {
            JsonNode members = element.get("members");
            for (JsonNode member : members) {
                if (member.has("geometry")) {
                    JsonNode geometry = member.get("geometry");
                    for (JsonNode node : geometry) {
                        double lat = node.get("lat").asDouble();
                        double lon = node.get("lon").asDouble();
                        coordinates.add(new Coordinate(lat, lon));
                    }
                }
            }
        }

        return coordinates;
    }

    /**
     * Calculate trail distance in kilometers using Haversine formula.
     */
    private double calculateDistance(List<Coordinate> coordinates) {
        if (coordinates.size() < 2) {
            return 0.0;
        }

        double totalDistance = 0.0;
        for (int i = 0; i < coordinates.size() - 1; i++) {
            totalDistance += haversineDistance(
                coordinates.get(i).getLatitude(),
                coordinates.get(i).getLongitude(),
                coordinates.get(i + 1).getLatitude(),
                coordinates.get(i + 1).getLongitude()
            );
        }

        return totalDistance;
    }

    /**
     * Haversine formula for distance between two coordinates.
     * Returns distance in kilometers.
     */
    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
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
     * Convert OSM sac_scale to simple difficulty rating.
     */
    private String convertSacScaleToDifficulty(String sacScale) {
        return switch (sacScale) {
            case "hiking" -> "Easy";
            case "mountain_hiking" -> "Moderate";
            case "demanding_mountain_hiking", "alpine_hiking" -> "Hard";
            case "demanding_alpine_hiking", "difficult_alpine_hiking" -> "Very Hard";
            default -> "Unknown";
        };
    }

    /**
     * Assess data quality based on field completeness.
     */
    private HikingTrail.DataQuality assessDataQuality(HikingTrail trail) {
        int score = 0;

        // Critical fields
        if (trail.getLocation() != null) score++;
        if (trail.getDistanceKm() != null && trail.getDistanceKm() > 0) score++;
        if (trail.getName() != null && !trail.getName().equals("Unnamed Trail")) score++;

        // Optional fields
        if (trail.getDifficulty() != null) score++;
        if (trail.getElevationGainMeters() != null) score++;
        if (trail.getGeometry() != null && trail.getGeometry().size() > 2) score++;

        if (score >= 5) return HikingTrail.DataQuality.HIGH;
        if (score >= 3) return HikingTrail.DataQuality.MEDIUM;
        return HikingTrail.DataQuality.LOW;
    }
}
