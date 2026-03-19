package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.EnrichmentData;
import com.bbthechange.inviter.dto.EnrichmentResult;
import com.bbthechange.inviter.model.IdeaListMember;
import com.bbthechange.inviter.model.PlaceEnrichmentCacheEntry;
import com.bbthechange.inviter.repository.IdeaListRepository;
import com.bbthechange.inviter.repository.PlaceEnrichmentCacheRepository;
import com.bbthechange.inviter.service.GooglePlacesClient;
import com.bbthechange.inviter.service.PlaceDetailsResult;
import com.bbthechange.inviter.service.PlaceEnrichmentService;
import com.bbthechange.inviter.util.CacheKeyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of PlaceEnrichmentService using Google Places API (New).
 *
 * <p>Provides synchronous enrichment for the /places/enrich endpoint and
 * async read-path safety net. All enrichment flows through the
 * PlaceEnrichmentCache DynamoDB table before writing to idea records.</p>
 */
@Service
public class PlaceEnrichmentServiceImpl implements PlaceEnrichmentService {

    private static final Logger logger = LoggerFactory.getLogger(PlaceEnrichmentServiceImpl.class);

    private static final int STALE_DAYS_THRESHOLD = 30;
    private static final int MAX_ENRICHMENTS_PER_READ = 5;
    private static final int MAX_FAILURE_COUNT = 3;
    private static final int SYNC_TIMEOUT_SECONDS = 8;
    private static final long TTL_DAYS = 90;

    private final GooglePlacesClient googlePlacesClient;
    private final PlaceEnrichmentCacheRepository cacheRepository;
    private final IdeaListRepository ideaListRepository;
    private final S3Client s3Client;
    private final String apiKey;
    private final String bucketName;

    // In-memory dedup: prevents concurrent requests for the same place hitting Google twice
    private final ConcurrentHashMap<String, CompletableFuture<EnrichmentResult>> inflightRequests =
        new ConcurrentHashMap<>();

    public PlaceEnrichmentServiceImpl(
            GooglePlacesClient googlePlacesClient,
            PlaceEnrichmentCacheRepository cacheRepository,
            IdeaListRepository ideaListRepository,
            S3Client s3Client,
            @Qualifier("googlePlacesApiKey") String apiKey,
            @Value("${aws.s3.bucket}") String bucketName) {
        this.googlePlacesClient = googlePlacesClient;
        this.cacheRepository = cacheRepository;
        this.ideaListRepository = ideaListRepository;
        this.s3Client = s3Client;
        this.apiKey = apiKey;
        this.bucketName = bucketName;
    }

    @Override
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public EnrichmentResult enrichPlaceSync(String name, double latitude, double longitude,
                                            String googlePlaceId, String applePlaceId) {
        String cacheKey = CacheKeyUtils.normalize(name, latitude, longitude);

        // Cache lookup
        Optional<PlaceEnrichmentCacheEntry> cached = lookupCacheInternal(cacheKey, googlePlaceId);
        if (cached.isPresent() && "ENRICHED".equals(cached.get().getStatus())) {
            return EnrichmentResult.cached(EnrichmentData.fromCacheEntry(cached.get()));
        }
        // PERMANENTLY_FAILED: user-initiated call resets failureCount and retries
        int knownFailureCount = 0;
        if (cached.isPresent() && "PERMANENTLY_FAILED".equals(cached.get().getStatus())) {
            PlaceEnrichmentCacheEntry reset = cached.get();
            reset.setFailureCount(0);
            reset.setStatus("FAILED");  // downgrade so writeFailedCacheEntry increments from 0
            cacheRepository.save(reset);
            logger.info("Reset PERMANENTLY_FAILED cache entry for key={}", cacheKey);
        } else if (cached.isPresent() && cached.get().getFailureCount() != null) {
            knownFailureCount = cached.get().getFailureCount();
        }

        // Dedup: atomically join or create inflight request for this cacheKey
        final String resolvedGooglePlaceId = googlePlaceId;
        final String resolvedApplePlaceId = applePlaceId;
        boolean[] isOwner = {false};
        CompletableFuture<EnrichmentResult> future = inflightRequests.computeIfAbsent(cacheKey, k -> {
            isOwner[0] = true;
            return new CompletableFuture<>();
        });

        if (!isOwner[0]) {
            // Another thread is running the pipeline — wait for its result
            try {
                return future.get(SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.warn("Timed out or interrupted waiting for inflight enrichment for key={}", cacheKey);
                return EnrichmentResult.failed();
            }
        }

        // We own the future — run the pipeline
        final int failureCount = knownFailureCount;
        try {
            EnrichmentResult result = runPipeline(cacheKey, name, latitude, longitude,
                resolvedGooglePlaceId, resolvedApplePlaceId, failureCount);
            future.complete(result);
            return result;
        } catch (Exception e) {
            logger.error("Pipeline failed for cacheKey={}", cacheKey, e);
            future.complete(EnrichmentResult.failed());
            return EnrichmentResult.failed();
        } finally {
            inflightRequests.remove(cacheKey);
        }
    }

    @Override
    public Optional<PlaceEnrichmentCacheEntry> lookupCache(String name, Double latitude, Double longitude,
                                                           String googlePlaceId) {
        if (googlePlaceId != null && !googlePlaceId.isBlank()) {
            Optional<PlaceEnrichmentCacheEntry> byGoogleId = cacheRepository.findByGooglePlaceId(googlePlaceId);
            if (byGoogleId.isPresent()) return byGoogleId;
        }
        if (name != null && latitude != null && longitude != null) {
            String cacheKey = CacheKeyUtils.normalize(name, latitude, longitude);
            return cacheRepository.findByCacheKey(cacheKey);
        }
        return Optional.empty();
    }

    @Async
    @Override
    public void enrichPlaceAsync(String groupId, String listId, String ideaId,
                                 String name, Double latitude, Double longitude,
                                 String googlePlaceId, String applePlaceId) {
        try {
            if (name == null || latitude == null || longitude == null) {
                logger.warn("enrichPlaceAsync skipped for idea {} — missing name or coords", ideaId);
                return;
            }
            EnrichmentResult result = enrichPlaceSync(name, latitude, longitude, googlePlaceId, applePlaceId);
            if (result.getStatus() != EnrichmentResult.Status.FAILED) {
                copyEnrichmentToIdea(groupId, listId, ideaId, result.getData());
            } else {
                updateIdeaEnrichmentStatus(groupId, listId, ideaId, "FAILED");
            }
        } catch (Exception e) {
            logger.error("Async enrichment failed for idea {}", ideaId, e);
            updateIdeaEnrichmentStatus(groupId, listId, ideaId, "FAILED");
        }
    }

    @Async
    @Override
    public void triggerReadPathEnrichment(List<IdeaListMember> members, String groupId, String listId) {
        if (members == null || members.isEmpty()) return;

        Instant staleThreshold = Instant.now().minus(STALE_DAYS_THRESHOLD, ChronoUnit.DAYS);
        int count = 0;

        for (IdeaListMember member : members) {
            if (count >= MAX_ENRICHMENTS_PER_READ) break;
            if (!needsEnrichment(member, staleThreshold)) continue;
            if (member.getName() == null) continue;
            boolean hasCoords = member.getLatitude() != null && member.getLongitude() != null;
            boolean hasGooglePlaceId = member.getGooglePlaceId() != null && !member.getGooglePlaceId().isBlank();
            if (!hasCoords && !hasGooglePlaceId) continue;

            enrichPlaceAsync(groupId, listId, member.getIdeaId(),
                member.getName(), member.getLatitude(), member.getLongitude(),
                member.getGooglePlaceId(), member.getApplePlaceId());
            count++;
        }
    }

    // ===== Private helpers =====

    private EnrichmentResult runPipeline(String cacheKey, String name, double lat, double lng,
                                         String googlePlaceId, String applePlaceId,
                                         int knownFailureCount) {
        // Step 1: Find Google Place (skip if googlePlaceId already provided)
        String resolvedPlaceId = googlePlaceId;
        if (resolvedPlaceId == null || resolvedPlaceId.isBlank()) {
            Optional<String> found = googlePlacesClient.findPlace(name, lat, lng);
            if (found.isEmpty()) {
                logger.warn("Find Place returned no result for name={}", name);
                writeFailedCacheEntry(cacheKey, null, applePlaceId, knownFailureCount);
                return EnrichmentResult.failed();
            }
            resolvedPlaceId = found.get();
        }

        // Step 2: Get Place Details
        Optional<PlaceDetailsResult> details = googlePlacesClient.getPlaceDetails(resolvedPlaceId);
        if (details.isEmpty()) {
            logger.warn("Place Details returned no result for placeId={}", resolvedPlaceId);
            writeFailedCacheEntry(cacheKey, resolvedPlaceId, applePlaceId, knownFailureCount);
            return EnrichmentResult.failed();
        }

        // Step 3: Get Place Photo (optional — no photo is valid)
        PlaceDetailsResult d = details.get();
        String photoUrl = null;
        if (d.getPhotoName() != null) {
            Optional<byte[]> photoBytes = googlePlacesClient.getPlacePhoto(d.getPhotoName());
            if (photoBytes.isPresent() && photoBytes.get().length > 0) {
                String s3Key = "places/" + cacheKey + "/photo.jpg";
                if (uploadToS3(s3Key, photoBytes.get())) {
                    photoUrl = s3Key;
                }
            }
        }

        // Step 4: Write enriched cache entry
        PlaceEnrichmentCacheEntry entry = buildEnrichedCacheEntry(
            cacheKey, resolvedPlaceId, applePlaceId, d, photoUrl);
        cacheRepository.save(entry);

        return EnrichmentResult.enriched(EnrichmentData.fromCacheEntry(entry));
    }

    private Optional<PlaceEnrichmentCacheEntry> lookupCacheInternal(String cacheKey, String googlePlaceId) {
        if (googlePlaceId != null && !googlePlaceId.isBlank()) {
            Optional<PlaceEnrichmentCacheEntry> byGoogleId = cacheRepository.findByGooglePlaceId(googlePlaceId);
            if (byGoogleId.isPresent()) return byGoogleId;
        }
        return cacheRepository.findByCacheKey(cacheKey);
    }

    private boolean needsEnrichment(IdeaListMember member, Instant staleThreshold) {
        String status = member.getEnrichmentStatus();
        if (status == null) return true;
        if ("PENDING".equals(status)) return true;
        if ("NOT_APPLICABLE".equals(status)) return false;
        if ("PERMANENTLY_FAILED".equals(status)) return false;
        if ("FAILED".equals(status)) {
            Optional<PlaceEnrichmentCacheEntry> cached = lookupCache(
                member.getName(), member.getLatitude(), member.getLongitude(), member.getGooglePlaceId());
            if (cached.isPresent() && cached.get().getFailureCount() != null
                    && cached.get().getFailureCount() >= MAX_FAILURE_COUNT) {
                return false;
            }
            return true;
        }
        if ("ENRICHED".equals(status) && member.getLastEnrichedAt() != null) {
            return member.getLastEnrichedAt().isBefore(staleThreshold);
        }
        return false;
    }

    private void copyEnrichmentToIdea(String groupId, String listId, String ideaId, EnrichmentData data) {
        Map<String, AttributeValue> attrs = new HashMap<>();
        if (data.getCachedPhotoUrl() != null) {
            attrs.put("cachedPhotoUrl", AttributeValue.builder().s(data.getCachedPhotoUrl()).build());
        }
        if (data.getCachedRating() != null) {
            attrs.put("cachedRating", AttributeValue.builder().n(String.valueOf(data.getCachedRating())).build());
        }
        if (data.getCachedPriceLevel() != null) {
            attrs.put("cachedPriceLevel", AttributeValue.builder().n(String.valueOf(data.getCachedPriceLevel())).build());
        }
        if (data.getCachedHoursJson() != null) {
            attrs.put("cachedHoursJson", AttributeValue.builder().s(data.getCachedHoursJson()).build());
        }
        if (data.getPhoneNumber() != null) {
            attrs.put("phoneNumber", AttributeValue.builder().s(data.getPhoneNumber()).build());
        }
        if (data.getWebsiteUrl() != null) {
            attrs.put("websiteUrl", AttributeValue.builder().s(data.getWebsiteUrl()).build());
        }
        if (data.getGooglePlaceId() != null) {
            attrs.put("googlePlaceId", AttributeValue.builder().s(data.getGooglePlaceId()).build());
        }
        attrs.put("enrichmentStatus", AttributeValue.builder().s("ENRICHED").build());
        attrs.put("lastEnrichedAt", AttributeValue.builder()
            .n(String.valueOf(Instant.now().toEpochMilli())).build());
        ideaListRepository.updateIdeaEnrichmentData(groupId, listId, ideaId, attrs);
    }

    private void updateIdeaEnrichmentStatus(String groupId, String listId, String ideaId, String status) {
        Map<String, AttributeValue> attrs = Map.of(
            "enrichmentStatus", AttributeValue.builder().s(status).build()
        );
        ideaListRepository.updateIdeaEnrichmentData(groupId, listId, ideaId, attrs);
    }

    private PlaceEnrichmentCacheEntry buildEnrichedCacheEntry(String cacheKey, String googlePlaceId,
                                                               String applePlaceId,
                                                               PlaceDetailsResult d, String photoUrl) {
        PlaceEnrichmentCacheEntry entry = new PlaceEnrichmentCacheEntry();
        entry.setCacheKey(cacheKey);
        entry.setGooglePlaceId(googlePlaceId);
        entry.setApplePlaceId(applePlaceId);
        entry.setStatus("ENRICHED");
        entry.setFailureCount(0);
        entry.setCachedPhotoUrl(photoUrl);
        entry.setCachedRating(d.getRating());
        entry.setCachedPriceLevel(d.getPriceLevel());
        entry.setCachedHoursJson(d.getCachedHoursJson());
        entry.setPhoneNumber(d.getPhoneNumber());
        entry.setWebsiteUrl(d.getWebsiteUrl());
        String now = Instant.now().toString();
        entry.setLastEnrichedAt(now);
        entry.setCreatedAt(now);
        entry.setTtl(Instant.now().plusSeconds(TTL_DAYS * 24 * 3600).getEpochSecond());
        return entry;
    }

    private void writeFailedCacheEntry(String cacheKey, String googlePlaceId, String applePlaceId,
                                        int knownFailureCount) {
        try {
            // Use the known failure count (avoids DynamoDB eventual consistency stale read)
            int newFailureCount = knownFailureCount + 1;
            String status = newFailureCount >= MAX_FAILURE_COUNT ? "PERMANENTLY_FAILED" : "FAILED";

            PlaceEnrichmentCacheEntry entry = new PlaceEnrichmentCacheEntry();
            entry.setCacheKey(cacheKey);
            entry.setGooglePlaceId(googlePlaceId);
            entry.setApplePlaceId(applePlaceId);
            entry.setStatus(status);
            entry.setFailureCount(newFailureCount);
            String now = Instant.now().toString();
            entry.setCreatedAt(now);
            entry.setLastEnrichedAt(now);
            entry.setTtl(Instant.now().plusSeconds(TTL_DAYS * 24 * 3600).getEpochSecond());
            cacheRepository.save(entry);
        } catch (Exception e) {
            logger.error("Failed to write failed cache entry for cacheKey={}", cacheKey, e);
        }
    }

    private boolean uploadToS3(String s3Key, byte[] bytes) {
        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType("image/jpeg")
                .build();
            s3Client.putObject(putRequest, RequestBody.fromBytes(bytes));
            logger.debug("Uploaded photo to S3: {}", s3Key);
            return true;
        } catch (Exception e) {
            logger.error("Failed to upload photo to S3 key={}", s3Key, e);
            return false;
        }
    }
}
