package com.bbthechange.inviter.service;

import java.util.Optional;

public interface GooglePlacesClient {

    /**
     * Find a Google Place by name + coordinates.
     * Uses Google Places API (New): searchText endpoint.
     * @return googlePlaceId if found, empty if no match or on error
     */
    Optional<String> findPlace(String name, double latitude, double longitude);

    /**
     * Get place details by googlePlaceId.
     * Uses Google Places API (New): Place Details.
     * @return structured details or empty if failed
     */
    Optional<PlaceDetailsResult> getPlaceDetails(String googlePlaceId);

    /**
     * Get first photo bytes for a place.
     * Uses Google Places API (New): Place Photos.
     * @param photoName resource name e.g. "places/xxx/photos/yyy"
     * @return photo bytes or empty if no photo available
     */
    Optional<byte[]> getPlacePhoto(String photoName);
}
