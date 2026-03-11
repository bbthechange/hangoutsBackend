package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.model.IdeaListMember;
import com.bbthechange.inviter.repository.IdeaListRepository;
import com.bbthechange.inviter.service.PlaceEnrichmentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of PlaceEnrichmentService using Google Places API.
 * Asynchronously enriches idea records with place details and caches photos to S3.
 */
@Service
@ConditionalOnProperty(name = "google.places.api-key")
public class PlaceEnrichmentServiceImpl implements PlaceEnrichmentService {

    private static final Logger logger = LoggerFactory.getLogger(PlaceEnrichmentServiceImpl.class);

    private static final String PLACES_DETAILS_URL = "https://maps.googleapis.com/maps/api/place/details/json";
    private static final String PLACES_PHOTO_URL = "https://maps.googleapis.com/maps/api/place/photo";
    private static final String S3_PHOTO_PREFIX = "places/photos/";
    private static final int STALE_DAYS_THRESHOLD = 30;
    private static final int MAX_STALE_RE_ENRICHMENTS_PER_CALL = 5;
    private static final int PHOTO_MAX_WIDTH = 800;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final IdeaListRepository ideaListRepository;
    private final S3Client s3Client;
    private final String apiKey;
    private final String bucketName;

    public PlaceEnrichmentServiceImpl(
            @Qualifier("externalRestTemplate") RestTemplate restTemplate,
            ObjectMapper objectMapper,
            IdeaListRepository ideaListRepository,
            S3Client s3Client,
            @Value("${google.places.api-key}") String apiKey,
            @Value("${aws.s3.bucket}") String bucketName) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.ideaListRepository = ideaListRepository;
        this.s3Client = s3Client;
        this.apiKey = apiKey;
        this.bucketName = bucketName;
    }

    @Override
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Async
    @Override
    public void enrichPlaceAsync(String groupId, String listId, String ideaId, String googlePlaceId) {
        doEnrichPlace(groupId, listId, ideaId, googlePlaceId);
    }

    @Async
    @Override
    public void triggerStaleReEnrichment(List<IdeaListMember> members, String groupId, String listId) {
        if (members == null || members.isEmpty()) {
            return;
        }

        Instant staleThreshold = Instant.now().minus(STALE_DAYS_THRESHOLD, ChronoUnit.DAYS);
        int reEnrichCount = 0;

        for (IdeaListMember member : members) {
            if (reEnrichCount >= MAX_STALE_RE_ENRICHMENTS_PER_CALL) {
                break;
            }

            // Only re-enrich items that were previously enriched and have a googlePlaceId
            if (member.getGooglePlaceId() != null && !member.getGooglePlaceId().isBlank()
                    && "ENRICHED".equals(member.getEnrichmentStatus())
                    && member.getLastEnrichedAt() != null
                    && member.getLastEnrichedAt().isBefore(staleThreshold)) {
                logger.info("Triggering re-enrichment for stale idea: {} (last enriched: {})",
                        member.getIdeaId(), member.getLastEnrichedAt());
                doEnrichPlace(groupId, listId, member.getIdeaId(), member.getGooglePlaceId());
                reEnrichCount++;
            }
        }

        if (reEnrichCount > 0) {
            logger.info("Triggered re-enrichment for {} stale ideas in list: {}", reEnrichCount, listId);
        }
    }

    /**
     * Internal enrichment logic. Called directly (not through Spring proxy) so @Async
     * is not applied here — callers are responsible for async dispatch.
     */
    private void doEnrichPlace(String groupId, String listId, String ideaId, String googlePlaceId) {
        try {
            logger.info("Starting enrichment for idea: {} with placeId: {}", ideaId, googlePlaceId);

            // Call Google Places Details API
            JsonNode placeDetails = fetchPlaceDetails(googlePlaceId);
            if (placeDetails == null || !placeDetails.has("result")) {
                logger.warn("No result from Google Places API for placeId: {}", googlePlaceId);
                updateEnrichmentStatus(groupId, listId, ideaId, "FAILED");
                return;
            }

            JsonNode result = placeDetails.get("result");
            Map<String, AttributeValue> enrichmentData = new HashMap<>();

            // Extract rating
            if (result.has("rating")) {
                enrichmentData.put("cachedRating", AttributeValue.builder()
                        .n(String.valueOf(result.get("rating").asDouble())).build());
            }

            // Extract price level
            if (result.has("price_level")) {
                enrichmentData.put("cachedPriceLevel", AttributeValue.builder()
                        .n(String.valueOf(result.get("price_level").asInt())).build());
            }

            // Extract phone number
            if (result.has("formatted_phone_number")) {
                enrichmentData.put("phoneNumber", AttributeValue.builder()
                        .s(result.get("formatted_phone_number").asText()).build());
            }

            // Extract website
            if (result.has("website")) {
                enrichmentData.put("websiteUrl", AttributeValue.builder()
                        .s(result.get("website").asText()).build());
            }

            // Extract address
            if (result.has("formatted_address")) {
                enrichmentData.put("address", AttributeValue.builder()
                        .s(result.get("formatted_address").asText()).build());
            }

            // Extract opening hours as JSON string
            if (result.has("opening_hours") && result.get("opening_hours").has("weekday_text")) {
                String hoursJson = objectMapper.writeValueAsString(result.get("opening_hours").get("weekday_text"));
                enrichmentData.put("cachedHoursJson", AttributeValue.builder()
                        .s(hoursJson).build());
            }

            // Extract and cache photo
            if (result.has("photos") && result.get("photos").isArray() && !result.get("photos").isEmpty()) {
                String photoReference = result.get("photos").get(0).get("photo_reference").asText();
                String s3Key = cachePhotoToS3(photoReference, ideaId);
                if (s3Key != null) {
                    enrichmentData.put("cachedPhotoUrl", AttributeValue.builder()
                            .s(s3Key).build());
                }
            }

            // Set enrichment metadata
            enrichmentData.put("lastEnrichedAt", AttributeValue.builder()
                    .n(String.valueOf(Instant.now().toEpochMilli())).build());
            enrichmentData.put("enrichmentStatus", AttributeValue.builder()
                    .s("ENRICHED").build());

            // Write all enrichment data to DynamoDB in one UpdateItem call
            ideaListRepository.updateIdeaEnrichmentData(groupId, listId, ideaId, enrichmentData);
            logger.info("Successfully enriched idea: {} with Google Places data", ideaId);

        } catch (Exception e) {
            logger.error("Failed to enrich idea: {} with placeId: {}", ideaId, googlePlaceId, e);
            try {
                updateEnrichmentStatus(groupId, listId, ideaId, "FAILED");
            } catch (Exception ex) {
                logger.error("Failed to update enrichment status to FAILED for idea: {}", ideaId, ex);
            }
        }
    }

    private JsonNode fetchPlaceDetails(String googlePlaceId) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(PLACES_DETAILS_URL)
                    .queryParam("place_id", googlePlaceId)
                    .queryParam("fields", "rating,price_level,formatted_phone_number,website,"
                            + "formatted_address,opening_hours,photos")
                    .queryParam("key", apiKey)
                    .toUriString();

            String response = restTemplate.getForObject(url, String.class);
            return objectMapper.readTree(response);
        } catch (Exception e) {
            logger.error("Error fetching place details for placeId: {}", googlePlaceId, e);
            return null;
        }
    }

    private String cachePhotoToS3(String photoReference, String ideaId) {
        try {
            String photoUrl = UriComponentsBuilder.fromHttpUrl(PLACES_PHOTO_URL)
                    .queryParam("maxwidth", PHOTO_MAX_WIDTH)
                    .queryParam("photo_reference", photoReference)
                    .queryParam("key", apiKey)
                    .toUriString();

            // Fetch photo bytes - RestTemplate follows redirects automatically
            byte[] photoBytes = restTemplate.getForObject(photoUrl, byte[].class);
            if (photoBytes == null || photoBytes.length == 0) {
                logger.warn("Empty photo response for photoReference: {}", photoReference);
                return null;
            }

            String s3Key = S3_PHOTO_PREFIX + ideaId + ".jpg";

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType("image/jpeg")
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(photoBytes));
            logger.debug("Cached photo to S3: {}", s3Key);
            return s3Key;

        } catch (Exception e) {
            logger.error("Error caching photo to S3 for ideaId: {}", ideaId, e);
            return null;
        }
    }

    private void updateEnrichmentStatus(String groupId, String listId, String ideaId, String status) {
        Map<String, AttributeValue> attrs = Map.of(
                "enrichmentStatus", AttributeValue.builder().s(status).build()
        );
        ideaListRepository.updateIdeaEnrichmentData(groupId, listId, ideaId, attrs);
    }
}
