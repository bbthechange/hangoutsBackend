package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.Coordinate;
import com.bbthechange.inviter.model.Location;

import java.util.List;
import java.util.Map;

/**
 * Represents a hiking trail with complete metadata.
 * Data may be sourced from OpenStreetMap, USGS, or state APIs.
 */
public class HikingTrail {
    private String id;
    private String name;
    private Location location;  // Starting point (lat, lng)
    private Double distanceKm;
    private Integer elevationGainMeters;
    private Integer elevationLossMeters;
    private String difficulty;  // OSM sac_scale or computed
    private String trailType;   // hiking, running, mountain_biking
    private String region;      // State or park name
    private String source;      // "OSM", "USGS", "WA_STATE"
    private String externalId;  // Source-specific identifier
    private String externalLink; // Link to external trail info
    private List<Coordinate> geometry; // Trail path (LINESTRING)
    private Map<String, String> metadata; // Source-specific extras
    private DataQuality quality; // Completeness score
    private Long lastUpdated;

    // Constructors
    public HikingTrail() {}

    public HikingTrail(String id, String name, Location location) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.lastUpdated = System.currentTimeMillis();
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public Double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(Double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public Integer getElevationGainMeters() {
        return elevationGainMeters;
    }

    public void setElevationGainMeters(Integer elevationGainMeters) {
        this.elevationGainMeters = elevationGainMeters;
    }

    public Integer getElevationLossMeters() {
        return elevationLossMeters;
    }

    public void setElevationLossMeters(Integer elevationLossMeters) {
        this.elevationLossMeters = elevationLossMeters;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getTrailType() {
        return trailType;
    }

    public void setTrailType(String trailType) {
        this.trailType = trailType;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getExternalLink() {
        return externalLink;
    }

    public void setExternalLink(String externalLink) {
        this.externalLink = externalLink;
    }

    public List<Coordinate> getGeometry() {
        return geometry;
    }

    public void setGeometry(List<Coordinate> geometry) {
        this.geometry = geometry;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public DataQuality getQuality() {
        return quality;
    }

    public void setQuality(DataQuality quality) {
        this.quality = quality;
    }

    public Long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    /**
     * Data quality enum based on field completeness
     */
    public enum DataQuality {
        HIGH,    // All critical fields present and validated
        MEDIUM,  // Some optional fields missing (e.g., elevation)
        LOW      // Critical fields missing (e.g., location or distance)
    }
}
