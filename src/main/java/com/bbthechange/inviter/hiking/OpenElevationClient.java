package com.bbthechange.inviter.hiking;

import com.bbthechange.inviter.dto.HikingTrail;
import com.bbthechange.inviter.model.Coordinate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Client for Open-Elevation API.
 * Fetches elevation data for coordinates and calculates elevation gain/loss.
 *
 * API Docs: https://open-elevation.com/
 */
@Component
public class OpenElevationClient {

    private static final Logger logger = LoggerFactory.getLogger(OpenElevationClient.class);
    private static final String OPEN_ELEVATION_API_URL = "https://api.open-elevation.com/api/v1/lookup";
    private static final int MAX_LOCATIONS_PER_REQUEST = 100; // API limit

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public OpenElevationClient(@Qualifier("externalRestTemplate") RestTemplate restTemplate,
                              ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Enrich trail with elevation data.
     * Calculates elevation gain and loss from trail geometry.
     *
     * @param trail Trail with geometry coordinates
     * @return Trail with elevation gain/loss populated
     */
    public HikingTrail enrichTrailWithElevation(HikingTrail trail) {
        if (trail.getGeometry() == null || trail.getGeometry().isEmpty()) {
            logger.warn("Trail {} has no geometry, cannot calculate elevation", trail.getId());
            return trail;
        }

        try {
            // Sample points for elevation (don't query every single point)
            List<Coordinate> sampledPoints = sampleCoordinates(trail.getGeometry(), 20);

            // Fetch elevation data
            List<Coordinate> pointsWithElevation = fetchElevationData(sampledPoints);

            if (pointsWithElevation.isEmpty()) {
                logger.warn("No elevation data returned for trail {}", trail.getId());
                return trail;
            }

            // Check if any coordinates actually have elevation data
            boolean hasElevationData = pointsWithElevation.stream()
                .anyMatch(coord -> coord.getElevation() != null);

            if (!hasElevationData) {
                logger.warn("No elevation data available for trail {}", trail.getId());
                return trail;
            }

            // Calculate elevation gain and loss
            ElevationStats stats = calculateElevationStats(pointsWithElevation);

            trail.setElevationGainMeters(stats.totalGain);
            trail.setElevationLossMeters(stats.totalLoss);

            // Update geometry with elevation data
            trail.getGeometry().forEach(coord -> {
                // Find closest sampled point and use its elevation
                Coordinate closest = findClosestPoint(coord, pointsWithElevation);
                if (closest != null && closest.getElevation() != null) {
                    coord.setElevation(closest.getElevation());
                }
            });

            logger.info("Enriched trail {} with elevation: +{}m, -{}m",
                       trail.getId(), stats.totalGain, stats.totalLoss);

        } catch (Exception e) {
            logger.error("Error enriching trail {} with elevation: {}",
                        trail.getId(), e.getMessage(), e);
        }

        return trail;
    }

    /**
     * Fetch elevation data for a list of coordinates.
     */
    private List<Coordinate> fetchElevationData(List<Coordinate> coordinates) {
        List<Coordinate> enrichedCoordinates = new ArrayList<>();

        // Process in batches due to API limits
        for (int i = 0; i < coordinates.size(); i += MAX_LOCATIONS_PER_REQUEST) {
            int end = Math.min(i + MAX_LOCATIONS_PER_REQUEST, coordinates.size());
            List<Coordinate> batch = coordinates.subList(i, end);

            List<Coordinate> batchResults = fetchElevationBatch(batch);
            enrichedCoordinates.addAll(batchResults);

            // Rate limiting: small delay between batches
            if (end < coordinates.size()) {
                try {
                    Thread.sleep(100); // 100ms delay
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        return enrichedCoordinates;
    }

    /**
     * Fetch elevation data for a batch of coordinates.
     */
    private List<Coordinate> fetchElevationBatch(List<Coordinate> coordinates) {
        try {
            // Build locations query string
            StringBuilder locations = new StringBuilder();
            for (int i = 0; i < coordinates.size(); i++) {
                Coordinate coord = coordinates.get(i);
                if (i > 0) locations.append("|");
                locations.append(String.format("%.6f,%.6f",
                    coord.getLatitude(), coord.getLongitude()));
            }

            String url = OPEN_ELEVATION_API_URL + "?locations=" + locations.toString();

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return parseElevationResponse(response.getBody(), coordinates);
            } else {
                logger.error("Open-Elevation API returned status: {}", response.getStatusCode());
            }

        } catch (Exception e) {
            logger.error("Error fetching elevation data: {}", e.getMessage());
        }

        return coordinates; // Return original without elevation if fetch fails
    }

    /**
     * Parse Open-Elevation API response and update coordinates with elevation.
     */
    private List<Coordinate> parseElevationResponse(String json, List<Coordinate> originalCoordinates) {
        List<Coordinate> enriched = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode results = root.get("results");

            if (results == null || !results.isArray()) {
                logger.warn("No results in elevation response");
                return originalCoordinates;
            }

            for (int i = 0; i < results.size() && i < originalCoordinates.size(); i++) {
                JsonNode result = results.get(i);
                Coordinate original = originalCoordinates.get(i);

                if (result.has("elevation")) {
                    int elevation = result.get("elevation").asInt();
                    Coordinate enrichedCoord = new Coordinate(
                        original.getLatitude(),
                        original.getLongitude(),
                        elevation
                    );
                    enriched.add(enrichedCoord);
                } else {
                    enriched.add(original);
                }
            }

        } catch (Exception e) {
            logger.error("Error parsing elevation response: {}", e.getMessage());
            return originalCoordinates;
        }

        return enriched;
    }

    /**
     * Sample coordinates to reduce API calls.
     * Takes evenly spaced points from the trail.
     */
    private List<Coordinate> sampleCoordinates(List<Coordinate> coordinates, int maxSamples) {
        if (coordinates.size() <= maxSamples) {
            return new ArrayList<>(coordinates);
        }

        List<Coordinate> sampled = new ArrayList<>();
        double step = (double) coordinates.size() / maxSamples;

        for (int i = 0; i < maxSamples; i++) {
            int index = (int) Math.floor(i * step);
            sampled.add(coordinates.get(index));
        }

        // Always include the last point
        if (!sampled.contains(coordinates.get(coordinates.size() - 1))) {
            sampled.add(coordinates.get(coordinates.size() - 1));
        }

        return sampled;
    }

    /**
     * Calculate total elevation gain and loss from coordinates with elevation.
     */
    private ElevationStats calculateElevationStats(List<Coordinate> coordinates) {
        int totalGain = 0;
        int totalLoss = 0;

        for (int i = 0; i < coordinates.size() - 1; i++) {
            Coordinate current = coordinates.get(i);
            Coordinate next = coordinates.get(i + 1);

            if (current.getElevation() != null && next.getElevation() != null) {
                int diff = next.getElevation() - current.getElevation();
                if (diff > 0) {
                    totalGain += diff;
                } else {
                    totalLoss += Math.abs(diff);
                }
            }
        }

        return new ElevationStats(totalGain, totalLoss);
    }

    /**
     * Find the closest coordinate in a list to a given coordinate.
     */
    private Coordinate findClosestPoint(Coordinate target, List<Coordinate> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }

        Coordinate closest = candidates.get(0);
        double minDistance = distance(target, closest);

        for (Coordinate candidate : candidates) {
            double dist = distance(target, candidate);
            if (dist < minDistance) {
                minDistance = dist;
                closest = candidate;
            }
        }

        return closest;
    }

    /**
     * Simple distance calculation between two coordinates.
     */
    private double distance(Coordinate c1, Coordinate c2) {
        double latDiff = c1.getLatitude() - c2.getLatitude();
        double lonDiff = c1.getLongitude() - c2.getLongitude();
        return Math.sqrt(latDiff * latDiff + lonDiff * lonDiff);
    }

    /**
     * Inner class to hold elevation statistics.
     */
    private static class ElevationStats {
        final int totalGain;
        final int totalLoss;

        ElevationStats(int totalGain, int totalLoss) {
            this.totalGain = totalGain;
            this.totalLoss = totalLoss;
        }
    }
}
