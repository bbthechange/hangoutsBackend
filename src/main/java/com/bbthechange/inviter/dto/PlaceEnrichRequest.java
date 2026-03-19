package com.bbthechange.inviter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for POST /places/enrich.
 * Synchronous place enrichment — client fires this when user selects an autocomplete result.
 */
@Data
@NoArgsConstructor
public class PlaceEnrichRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 200, message = "Name must be less than 200 characters")
    private String name;

    @NotNull(message = "Latitude is required")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    private Double longitude;

    /** Optional — Android provides this (saves 1 Google API call). */
    @Size(max = 200, message = "Google Place ID must be less than 200 characters")
    private String googlePlaceId;

    /** Optional — iOS 18+ only; stored for future use. */
    @Size(max = 200, message = "Apple Place ID must be less than 200 characters")
    private String applePlaceId;

    public String getName() {
        return name != null ? name.trim() : null;
    }

    public String getGooglePlaceId() {
        return googlePlaceId != null ? googlePlaceId.trim() : null;
    }

    public String getApplePlaceId() {
        return applePlaceId != null ? applePlaceId.trim() : null;
    }
}
