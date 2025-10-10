package com.bbthechange.inviter.model;

/**
 * Represents a geographic location with latitude and longitude.
 */
public class Location {
    private Double latitude;
    private Double longitude;

    // Constructors
    public Location() {}

    public Location(Double latitude, Double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
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

    @Override
    public String toString() {
        return String.format("Location{lat=%.6f, lng=%.6f}", latitude, longitude);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Location location = (Location) o;
        return latitude.equals(location.latitude) && longitude.equals(location.longitude);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(latitude, longitude);
    }
}
