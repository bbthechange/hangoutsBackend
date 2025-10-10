package com.bbthechange.inviter.model;

/**
 * Represents a coordinate point with optional elevation data.
 * Used to build trail geometry (LINESTRING).
 */
public class Coordinate {
    private Double latitude;
    private Double longitude;
    private Integer elevation;  // Meters above sea level (optional)

    // Constructors
    public Coordinate() {}

    public Coordinate(Double latitude, Double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Coordinate(Double latitude, Double longitude, Integer elevation) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.elevation = elevation;
    }

    // Getters and Setters
    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Integer getElevation() {
        return elevation;
    }

    public void setElevation(Integer elevation) {
        this.elevation = elevation;
    }

    @Override
    public String toString() {
        if (elevation != null) {
            return String.format("Coordinate{lat=%.6f, lng=%.6f, ele=%dm}", latitude, longitude, elevation);
        }
        return String.format("Coordinate{lat=%.6f, lng=%.6f}", latitude, longitude);
    }
}
