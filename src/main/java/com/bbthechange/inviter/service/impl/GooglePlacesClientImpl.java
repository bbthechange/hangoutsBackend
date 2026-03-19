package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.service.GooglePlacesClient;
import com.bbthechange.inviter.service.PlaceDetailsResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

/**
 * HTTP client for Google Places API (New).
 *
 * <p>Uses the new Places API at places.googleapis.com/v1 with X-Goog-Api-Key header
 * authentication and X-Goog-FieldMask for field selection. Wraps all calls with
 * a resilience4j circuit breaker to protect against Google API outages.</p>
 */
@Service
public class GooglePlacesClientImpl implements GooglePlacesClient {

    private static final Logger logger = LoggerFactory.getLogger(GooglePlacesClientImpl.class);

    private static final String FIND_PLACE_URL = "https://places.googleapis.com/v1/places:searchText";
    private static final String PLACE_DETAILS_URL = "https://places.googleapis.com/v1/places/";
    private static final String PLACE_PHOTO_URL = "https://places.googleapis.com/v1/";

    private static final String DETAILS_FIELD_MASK =
        "displayName,rating,priceLevel,currentOpeningHours.weekdayDescriptions," +
        "nationalPhoneNumber,websiteUri,googleMapsUri,photos";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final MeterRegistry meterRegistry;

    public GooglePlacesClientImpl(
            @Qualifier("externalRestTemplate") RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Qualifier("googlePlacesApiKey") String apiKey,
            MeterRegistry meterRegistry) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.meterRegistry = meterRegistry;
    }

    @Override
    @CircuitBreaker(name = "googlePlaces", fallbackMethod = "findPlaceFallback")
    public Optional<String> findPlace(String name, double latitude, double longitude) {
        meterRegistry.counter("place_enrichment_api_calls", "sku", "find_place").increment();
        try {
            Map<String, Object> requestBody = Map.of(
                "textQuery", name,
                "locationBias", Map.of(
                    "circle", Map.of(
                        "center", Map.of("latitude", latitude, "longitude", longitude),
                        "radius", 1000.0
                    )
                ),
                "maxResultCount", 1
            );

            HttpHeaders headers = buildHeaders("places.id");
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                FIND_PLACE_URL, HttpMethod.POST, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode places = root.path("places");
            if (places.isArray() && !places.isEmpty()) {
                String placeId = places.get(0).path("id").asText(null);
                return Optional.ofNullable(placeId);
            }
            return Optional.empty();

        } catch (Exception e) {
            logger.error("Error calling findPlace for name={}", name, e);
            throw new RuntimeException("Google Places findPlace failed", e);
        }
    }

    @Override
    @CircuitBreaker(name = "googlePlaces", fallbackMethod = "getPlaceDetailsFallback")
    public Optional<PlaceDetailsResult> getPlaceDetails(String googlePlaceId) {
        meterRegistry.counter("place_enrichment_api_calls", "sku", "place_details").increment();
        try {
            HttpHeaders headers = buildHeaders(DETAILS_FIELD_MASK);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                PLACE_DETAILS_URL + googlePlaceId, HttpMethod.GET, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            PlaceDetailsResult result = new PlaceDetailsResult();

            if (root.has("rating")) {
                result.setRating(root.get("rating").asDouble());
            }
            if (root.has("priceLevel")) {
                result.setPriceLevel(parsePriceLevel(root.get("priceLevel").asText()));
            }
            if (root.has("nationalPhoneNumber")) {
                result.setPhoneNumber(root.get("nationalPhoneNumber").asText());
            }
            if (root.has("websiteUri")) {
                result.setWebsiteUrl(root.get("websiteUri").asText());
            }
            if (root.has("googleMapsUri")) {
                result.setGoogleMapsUri(root.get("googleMapsUri").asText());
            }
            JsonNode hours = root.path("currentOpeningHours").path("weekdayDescriptions");
            if (hours.isArray() && !hours.isEmpty()) {
                result.setCachedHoursJson(objectMapper.writeValueAsString(hours));
            }
            JsonNode photos = root.path("photos");
            if (photos.isArray() && !photos.isEmpty()) {
                String photoName = photos.get(0).path("name").asText(null);
                result.setPhotoName(photoName);
            }

            return Optional.of(result);

        } catch (Exception e) {
            logger.error("Error calling getPlaceDetails for placeId={}", googlePlaceId, e);
            throw new RuntimeException("Google Places getPlaceDetails failed", e);
        }
    }

    @Override
    @CircuitBreaker(name = "googlePlaces", fallbackMethod = "getPlacePhotoFallback")
    public Optional<byte[]> getPlacePhoto(String photoName) {
        meterRegistry.counter("place_enrichment_api_calls", "sku", "place_photos").increment();
        try {
            String url = PLACE_PHOTO_URL + photoName + "/media?maxHeightPx=400&maxWidthPx=400";

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Goog-Api-Key", apiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<byte[]> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, byte[].class);

            byte[] bytes = response.getBody();
            if (bytes == null || bytes.length == 0) {
                return Optional.empty();
            }
            return Optional.of(bytes);

        } catch (Exception e) {
            logger.error("Error calling getPlacePhoto for photoName={}", photoName, e);
            throw new RuntimeException("Google Places getPlacePhoto failed", e);
        }
    }

    // ===== Circuit breaker fallbacks =====

    private Optional<String> findPlaceFallback(String name, double lat, double lng, Throwable t) {
        logger.warn("Circuit breaker open for findPlace (name={}): {}", name, t.getMessage());
        return Optional.empty();
    }

    private Optional<PlaceDetailsResult> getPlaceDetailsFallback(String googlePlaceId, Throwable t) {
        logger.warn("Circuit breaker open for getPlaceDetails (placeId={}): {}", googlePlaceId, t.getMessage());
        return Optional.empty();
    }

    private Optional<byte[]> getPlacePhotoFallback(String photoName, Throwable t) {
        logger.warn("Circuit breaker open for getPlacePhoto (photoName={}): {}", photoName, t.getMessage());
        return Optional.empty();
    }

    // ===== Helpers =====

    private HttpHeaders buildHeaders(String fieldMask) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Goog-Api-Key", apiKey);
        headers.set("X-Goog-FieldMask", fieldMask);
        return headers;
    }

    /**
     * Maps Google Places API (New) priceLevel enum string to integer 1-4.
     * PRICE_LEVEL_FREE=0, INEXPENSIVE=1, MODERATE=2, EXPENSIVE=3, VERY_EXPENSIVE=4
     */
    private Integer parsePriceLevel(String priceLevel) {
        return switch (priceLevel) {
            case "PRICE_LEVEL_FREE" -> 0;
            case "PRICE_LEVEL_INEXPENSIVE" -> 1;
            case "PRICE_LEVEL_MODERATE" -> 2;
            case "PRICE_LEVEL_EXPENSIVE" -> 3;
            case "PRICE_LEVEL_VERY_EXPENSIVE" -> 4;
            default -> null;
        };
    }
}
