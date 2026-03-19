package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.EnrichmentResult;
import com.bbthechange.inviter.model.IdeaListMember;
import com.bbthechange.inviter.model.PlaceEnrichmentCacheEntry;
import com.bbthechange.inviter.repository.IdeaListRepository;
import com.bbthechange.inviter.repository.PlaceEnrichmentCacheRepository;
import com.bbthechange.inviter.service.GooglePlacesClient;
import com.bbthechange.inviter.service.PlaceDetailsResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the redesigned PlaceEnrichmentServiceImpl.
 *
 * Mocks: GooglePlacesClient, PlaceEnrichmentCacheRepository, IdeaListRepository, S3Client.
 * Verifies: cache-first lookup, pipeline execution, result copying to idea records,
 * read-path safety net logic.
 */
@ExtendWith(MockitoExtension.class)
class PlaceEnrichmentServiceImplTest {

    @Mock private GooglePlacesClient googlePlacesClient;
    @Mock private PlaceEnrichmentCacheRepository cacheRepository;
    @Mock private IdeaListRepository ideaListRepository;
    @Mock private S3Client s3Client;

    private PlaceEnrichmentServiceImpl service;

    private static final String TEST_GROUP = "11111111-1111-1111-1111-111111111111";
    private static final String TEST_LIST = "22222222-2222-2222-2222-222222222222";
    private static final String TEST_IDEA = "33333333-3333-3333-3333-333333333333";
    private static final String TEST_PLACE_ID = "ChIJN1t_tDeuEmsRUsoyG83frY4";
    private static final double TEST_LAT = 40.7295;
    private static final double TEST_LNG = -74.0028;
    private static final String TEST_NAME = "Sushi Nakazawa";
    private static final String TEST_APPLE_ID = "I63LYKU7G9BCPA";

    @BeforeEach
    void setUp() {
        service = new PlaceEnrichmentServiceImpl(
            googlePlacesClient, cacheRepository, ideaListRepository,
            s3Client, "test-api-key", "test-bucket");
    }

    // ===== isEnabled =====

    @Test
    void isEnabled_WithApiKey_ReturnsTrue() {
        assertThat(service.isEnabled()).isTrue();
    }

    @Test
    void isEnabled_WithBlankApiKey_ReturnsFalse() {
        PlaceEnrichmentServiceImpl disabled = new PlaceEnrichmentServiceImpl(
            googlePlacesClient, cacheRepository, ideaListRepository, s3Client, "  ", "bucket");
        assertThat(disabled.isEnabled()).isFalse();
    }

    @Test
    void isEnabled_WithNullApiKey_ReturnsFalse() {
        PlaceEnrichmentServiceImpl disabled = new PlaceEnrichmentServiceImpl(
            googlePlacesClient, cacheRepository, ideaListRepository, s3Client, null, "bucket");
        assertThat(disabled.isEnabled()).isFalse();
    }

    // ===== enrichPlaceSync =====

    @Nested
    class EnrichPlaceSyncTests {

        @Test
        void cacheHitByGooglePlaceId_ReturnsCached_NoApiCalls() {
            PlaceEnrichmentCacheEntry entry = enrichedEntry("sushi-nakazawa_40.7295_-74.0028", TEST_PLACE_ID);
            when(cacheRepository.findByGooglePlaceId(TEST_PLACE_ID)).thenReturn(Optional.of(entry));

            EnrichmentResult result = service.enrichPlaceSync(TEST_NAME, TEST_LAT, TEST_LNG,
                TEST_PLACE_ID, TEST_APPLE_ID);

            assertThat(result.getStatus()).isEqualTo(EnrichmentResult.Status.CACHED);
            assertThat(result.getData()).isNotNull();
            verifyNoInteractions(googlePlacesClient);
        }

        @Test
        void cacheHitByCacheKey_ReturnsCached_NoApiCalls() {
            PlaceEnrichmentCacheEntry entry = enrichedEntry("sushi-nakazawa_40.7295_-74.0028", null);
            when(cacheRepository.findByCacheKey(anyString())).thenReturn(Optional.of(entry));

            EnrichmentResult result = service.enrichPlaceSync(TEST_NAME, TEST_LAT, TEST_LNG, null, null);

            assertThat(result.getStatus()).isEqualTo(EnrichmentResult.Status.CACHED);
            verifyNoInteractions(googlePlacesClient);
        }

        @Test
        void cacheMissFullPipeline_ReturnsEnriched() {
            when(cacheRepository.findByGooglePlaceId(TEST_PLACE_ID)).thenReturn(Optional.empty());
            when(cacheRepository.findByCacheKey(anyString())).thenReturn(Optional.empty());
            when(googlePlacesClient.getPlaceDetails(TEST_PLACE_ID))
                .thenReturn(Optional.of(fullDetailsResult()));
            when(googlePlacesClient.getPlacePhoto(anyString()))
                .thenReturn(Optional.of(new byte[]{1, 2, 3}));
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

            EnrichmentResult result = service.enrichPlaceSync(TEST_NAME, TEST_LAT, TEST_LNG,
                TEST_PLACE_ID, TEST_APPLE_ID);

            assertThat(result.getStatus()).isEqualTo(EnrichmentResult.Status.ENRICHED);
            assertThat(result.getData()).isNotNull();
            verify(cacheRepository).save(any(PlaceEnrichmentCacheEntry.class));
        }

        @Test
        void cacheMissAndroidPath_SkipsFindPlace() {
            // Android provides googlePlaceId → skip Find Place step
            when(cacheRepository.findByGooglePlaceId(TEST_PLACE_ID)).thenReturn(Optional.empty());
            when(cacheRepository.findByCacheKey(anyString())).thenReturn(Optional.empty());
            when(googlePlacesClient.getPlaceDetails(TEST_PLACE_ID))
                .thenReturn(Optional.of(fullDetailsResult()));
            when(googlePlacesClient.getPlacePhoto(anyString())).thenReturn(Optional.empty());

            service.enrichPlaceSync(TEST_NAME, TEST_LAT, TEST_LNG, TEST_PLACE_ID, null);

            verify(googlePlacesClient, never()).findPlace(anyString(), anyDouble(), anyDouble());
            verify(googlePlacesClient).getPlaceDetails(TEST_PLACE_ID);
        }

        @Test
        void cacheMissIosPath_CallsFindPlace() {
            // iOS: no googlePlaceId → call Find Place
            when(cacheRepository.findByCacheKey(anyString())).thenReturn(Optional.empty());
            when(googlePlacesClient.findPlace(TEST_NAME, TEST_LAT, TEST_LNG))
                .thenReturn(Optional.of(TEST_PLACE_ID));
            when(googlePlacesClient.getPlaceDetails(TEST_PLACE_ID))
                .thenReturn(Optional.of(fullDetailsResult()));
            when(googlePlacesClient.getPlacePhoto(anyString())).thenReturn(Optional.empty());

            EnrichmentResult result = service.enrichPlaceSync(TEST_NAME, TEST_LAT, TEST_LNG, null, null);

            assertThat(result.getStatus()).isEqualTo(EnrichmentResult.Status.ENRICHED);
            verify(googlePlacesClient).findPlace(TEST_NAME, TEST_LAT, TEST_LNG);
        }

        @Test
        void findPlaceReturnsEmpty_ReturnsFailed_WritesFailedToCache() {
            when(cacheRepository.findByCacheKey(anyString())).thenReturn(Optional.empty());
            when(googlePlacesClient.findPlace(anyString(), anyDouble(), anyDouble()))
                .thenReturn(Optional.empty());

            EnrichmentResult result = service.enrichPlaceSync(TEST_NAME, TEST_LAT, TEST_LNG, null, null);

            assertThat(result.getStatus()).isEqualTo(EnrichmentResult.Status.FAILED);
            assertThat(result.getData()).isNull();
            verify(cacheRepository).save(argThat(e -> "FAILED".equals(e.getStatus()) || "PERMANENTLY_FAILED".equals(e.getStatus())));
        }

        @Test
        void detailsReturnsEmpty_ReturnsFailed_WritesFailedToCache() {
            when(cacheRepository.findByGooglePlaceId(TEST_PLACE_ID)).thenReturn(Optional.empty());
            when(cacheRepository.findByCacheKey(anyString())).thenReturn(Optional.empty());
            when(googlePlacesClient.getPlaceDetails(TEST_PLACE_ID)).thenReturn(Optional.empty());

            EnrichmentResult result = service.enrichPlaceSync(TEST_NAME, TEST_LAT, TEST_LNG,
                TEST_PLACE_ID, null);

            assertThat(result.getStatus()).isEqualTo(EnrichmentResult.Status.FAILED);
            verify(cacheRepository).save(argThat(e -> "FAILED".equals(e.getStatus()) || "PERMANENTLY_FAILED".equals(e.getStatus())));
        }

        @Test
        void noPhoto_ReturnsEnriched_WithoutPhotoUrl() {
            when(cacheRepository.findByGooglePlaceId(TEST_PLACE_ID)).thenReturn(Optional.empty());
            when(cacheRepository.findByCacheKey(anyString())).thenReturn(Optional.empty());
            PlaceDetailsResult details = fullDetailsResult();
            details.setPhotoName(null);
            when(googlePlacesClient.getPlaceDetails(TEST_PLACE_ID)).thenReturn(Optional.of(details));

            EnrichmentResult result = service.enrichPlaceSync(TEST_NAME, TEST_LAT, TEST_LNG,
                TEST_PLACE_ID, null);

            assertThat(result.getStatus()).isEqualTo(EnrichmentResult.Status.ENRICHED);
            assertThat(result.getData().getCachedPhotoUrl()).isNull();
            verifyNoInteractions(s3Client);
        }

        @Test
        void s3KeyPattern_UsescacheKey() {
            when(cacheRepository.findByGooglePlaceId(TEST_PLACE_ID)).thenReturn(Optional.empty());
            when(cacheRepository.findByCacheKey(anyString())).thenReturn(Optional.empty());
            when(googlePlacesClient.getPlaceDetails(TEST_PLACE_ID)).thenReturn(Optional.of(fullDetailsResult()));
            when(googlePlacesClient.getPlacePhoto(anyString())).thenReturn(Optional.of(new byte[]{1, 2, 3}));
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

            service.enrichPlaceSync(TEST_NAME, TEST_LAT, TEST_LNG, TEST_PLACE_ID, null);

            ArgumentCaptor<PutObjectRequest> s3Captor = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(s3Client).putObject(s3Captor.capture(), any(RequestBody.class));
            assertThat(s3Captor.getValue().key()).startsWith("places/").endsWith("/photo.jpg");
            // Key should be based on cacheKey, not ideaId
            assertThat(s3Captor.getValue().key()).contains("sushi-nakazawa");
        }

        @Test
        void permanentlyFailedInCache_ResetsAndRetries() {
            PlaceEnrichmentCacheEntry pf = new PlaceEnrichmentCacheEntry();
            pf.setCacheKey("sushi-nakazawa_40.7295_-74.0028");
            pf.setStatus("PERMANENTLY_FAILED");
            pf.setFailureCount(3);
            // Cache lookup returns PERMANENTLY_FAILED (not ENRICHED, so pipeline runs)
            when(cacheRepository.findByGooglePlaceId(TEST_PLACE_ID)).thenReturn(Optional.of(pf));
            when(googlePlacesClient.getPlaceDetails(TEST_PLACE_ID)).thenReturn(Optional.of(fullDetailsResult()));
            lenient().when(googlePlacesClient.getPlacePhoto(anyString())).thenReturn(Optional.empty());

            EnrichmentResult result = service.enrichPlaceSync(TEST_NAME, TEST_LAT, TEST_LNG,
                TEST_PLACE_ID, null);

            // Should reset failureCount and attempt pipeline (user-initiated override)
            assertThat(result.getStatus()).isEqualTo(EnrichmentResult.Status.ENRICHED);
            verify(googlePlacesClient).getPlaceDetails(TEST_PLACE_ID);
            // Verify reset save (failureCount=0) + enriched save = 2 saves total
            verify(cacheRepository, times(2)).save(any(PlaceEnrichmentCacheEntry.class));
        }

        @Test
        void permanentlyFailedInCache_PipelineFailsAgain_ResetsFailureCount() {
            PlaceEnrichmentCacheEntry pf = new PlaceEnrichmentCacheEntry();
            pf.setCacheKey("sushi-nakazawa_40.7295_-74.0028");
            pf.setStatus("PERMANENTLY_FAILED");
            pf.setFailureCount(3);
            // Cache lookup returns PERMANENTLY_FAILED
            when(cacheRepository.findByGooglePlaceId(TEST_PLACE_ID)).thenReturn(Optional.of(pf));
            // Pipeline fails: Details returns empty
            when(googlePlacesClient.getPlaceDetails(TEST_PLACE_ID)).thenReturn(Optional.empty());
            // writeFailedCacheEntry now uses knownFailureCount=0 (passed in, no re-read needed)

            EnrichmentResult result = service.enrichPlaceSync(TEST_NAME, TEST_LAT, TEST_LNG,
                TEST_PLACE_ID, null);

            assertThat(result.getStatus()).isEqualTo(EnrichmentResult.Status.FAILED);
            // Verify saves: 1) reset (failureCount=0), 2) writeFailedCacheEntry (failureCount=1, not 4)
            ArgumentCaptor<PlaceEnrichmentCacheEntry> captor =
                ArgumentCaptor.forClass(PlaceEnrichmentCacheEntry.class);
            verify(cacheRepository, atLeast(2)).save(captor.capture());
            List<PlaceEnrichmentCacheEntry> saves = captor.getAllValues();
            // First save: reset to failureCount=0
            assertThat(saves.get(0).getFailureCount()).isEqualTo(0);
            // Second save: writeFailedCacheEntry increments from 0 → 1 (not from 3 → 4)
            assertThat(saves.get(1).getFailureCount()).isEqualTo(1);
        }

        @Test
        void s3UploadFails_StillReturnsEnrichedWithoutPhotoUrl() {
            when(cacheRepository.findByGooglePlaceId(TEST_PLACE_ID)).thenReturn(Optional.empty());
            when(cacheRepository.findByCacheKey(anyString())).thenReturn(Optional.empty());
            when(googlePlacesClient.getPlaceDetails(TEST_PLACE_ID)).thenReturn(Optional.of(fullDetailsResult()));
            when(googlePlacesClient.getPlacePhoto(anyString())).thenReturn(Optional.of(new byte[]{1, 2, 3}));
            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(new RuntimeException("S3 upload failed"));

            EnrichmentResult result = service.enrichPlaceSync(TEST_NAME, TEST_LAT, TEST_LNG,
                TEST_PLACE_ID, null);

            assertThat(result.getStatus()).isEqualTo(EnrichmentResult.Status.ENRICHED);
            assertThat(result.getData().getCachedPhotoUrl()).isNull();
            assertThat(result.getData().getCachedRating()).isEqualTo(4.6);
        }

        @Test
        void cacheHitForFAILEDStatus_RunsPipeline() {
            // FAILED cache entry should NOT be treated as cache hit — pipeline should run
            PlaceEnrichmentCacheEntry failedEntry = new PlaceEnrichmentCacheEntry();
            failedEntry.setCacheKey("sushi-nakazawa_40.7295_-74.0028");
            failedEntry.setStatus("FAILED");
            failedEntry.setFailureCount(1);
            when(cacheRepository.findByGooglePlaceId(TEST_PLACE_ID)).thenReturn(Optional.of(failedEntry));
            when(googlePlacesClient.getPlaceDetails(TEST_PLACE_ID)).thenReturn(Optional.of(fullDetailsResult()));
            lenient().when(googlePlacesClient.getPlacePhoto(anyString())).thenReturn(Optional.empty());

            EnrichmentResult result = service.enrichPlaceSync(TEST_NAME, TEST_LAT, TEST_LNG,
                TEST_PLACE_ID, null);

            assertThat(result.getStatus()).isEqualTo(EnrichmentResult.Status.ENRICHED);
            verify(googlePlacesClient).getPlaceDetails(TEST_PLACE_ID);
        }

        @Test
        void buildEnrichedCacheEntry_ApplePlaceIdStored() {
            when(cacheRepository.findByGooglePlaceId(TEST_PLACE_ID)).thenReturn(Optional.empty());
            when(cacheRepository.findByCacheKey(anyString())).thenReturn(Optional.empty());
            when(googlePlacesClient.getPlaceDetails(TEST_PLACE_ID)).thenReturn(Optional.of(fullDetailsResult()));
            when(googlePlacesClient.getPlacePhoto(anyString())).thenReturn(Optional.empty());

            service.enrichPlaceSync(TEST_NAME, TEST_LAT, TEST_LNG, TEST_PLACE_ID, TEST_APPLE_ID);

            ArgumentCaptor<PlaceEnrichmentCacheEntry> captor =
                ArgumentCaptor.forClass(PlaceEnrichmentCacheEntry.class);
            verify(cacheRepository).save(captor.capture());
            assertThat(captor.getValue().getApplePlaceId()).isEqualTo(TEST_APPLE_ID);
            assertThat(captor.getValue().getGooglePlaceId()).isEqualTo(TEST_PLACE_ID);
        }

        @Test
        void buildEnrichedCacheEntry_TtlSetTo90Days() {
            when(cacheRepository.findByGooglePlaceId(TEST_PLACE_ID)).thenReturn(Optional.empty());
            when(cacheRepository.findByCacheKey(anyString())).thenReturn(Optional.empty());
            when(googlePlacesClient.getPlaceDetails(TEST_PLACE_ID)).thenReturn(Optional.of(fullDetailsResult()));
            when(googlePlacesClient.getPlacePhoto(anyString())).thenReturn(Optional.empty());

            service.enrichPlaceSync(TEST_NAME, TEST_LAT, TEST_LNG, TEST_PLACE_ID, null);

            ArgumentCaptor<PlaceEnrichmentCacheEntry> captor =
                ArgumentCaptor.forClass(PlaceEnrichmentCacheEntry.class);
            verify(cacheRepository).save(captor.capture());
            long ttl = captor.getValue().getTtl();
            long expectedMinTtl = Instant.now().plusSeconds(89 * 24 * 3600).getEpochSecond();
            long expectedMaxTtl = Instant.now().plusSeconds(91 * 24 * 3600).getEpochSecond();
            assertThat(ttl).isBetween(expectedMinTtl, expectedMaxTtl);
        }

        @Test
        void writeFailedCacheEntry_AtCount2_StatusIsFAILED_NotPermanentlyFailed() {
            // Existing entry has failureCount=2, next failure should be 3 → PERMANENTLY_FAILED
            // But at count=1 → should stay FAILED (boundary: count < 3)
            PlaceEnrichmentCacheEntry existingEntry = new PlaceEnrichmentCacheEntry();
            existingEntry.setFailureCount(1);
            when(cacheRepository.findByCacheKey(anyString())).thenReturn(Optional.of(existingEntry));
            when(googlePlacesClient.findPlace(anyString(), anyDouble(), anyDouble()))
                .thenReturn(Optional.empty());

            service.enrichPlaceSync(TEST_NAME, TEST_LAT, TEST_LNG, null, null);

            ArgumentCaptor<PlaceEnrichmentCacheEntry> captor =
                ArgumentCaptor.forClass(PlaceEnrichmentCacheEntry.class);
            verify(cacheRepository).save(captor.capture());
            // failureCount was 1, incremented to 2 → still FAILED (not PERMANENTLY_FAILED)
            assertThat(captor.getValue().getFailureCount()).isEqualTo(2);
            assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
        }

        @Test
        void cacheEntryData_MappedCorrectly() {
            when(cacheRepository.findByGooglePlaceId(TEST_PLACE_ID)).thenReturn(Optional.empty());
            when(cacheRepository.findByCacheKey(anyString())).thenReturn(Optional.empty());
            PlaceDetailsResult details = fullDetailsResult();
            when(googlePlacesClient.getPlaceDetails(TEST_PLACE_ID)).thenReturn(Optional.of(details));
            when(googlePlacesClient.getPlacePhoto(anyString())).thenReturn(Optional.empty());

            EnrichmentResult result = service.enrichPlaceSync(TEST_NAME, TEST_LAT, TEST_LNG,
                TEST_PLACE_ID, null);

            assertThat(result.getData().getCachedRating()).isEqualTo(4.6);
            assertThat(result.getData().getCachedPriceLevel()).isEqualTo(4);
            assertThat(result.getData().getPhoneNumber()).isEqualTo("+1 212 524 0500");
            assertThat(result.getData().getWebsiteUrl()).isEqualTo("https://sushinakazawa.com");
            assertThat(result.getData().getCachedHoursJson()).contains("Monday");
        }
    }

    // ===== lookupCache =====

    @Nested
    class LookupCacheTests {

        @Test
        void byGooglePlaceId_Found_ReturnsEntry() {
            PlaceEnrichmentCacheEntry entry = enrichedEntry("key", TEST_PLACE_ID);
            when(cacheRepository.findByGooglePlaceId(TEST_PLACE_ID)).thenReturn(Optional.of(entry));

            Optional<PlaceEnrichmentCacheEntry> result = service.lookupCache(
                TEST_NAME, TEST_LAT, TEST_LNG, TEST_PLACE_ID);

            assertThat(result).isPresent();
            verify(cacheRepository, never()).findByCacheKey(anyString());
        }

        @Test
        void googlePlaceIdMiss_FallsThroughToCacheKey() {
            when(cacheRepository.findByGooglePlaceId(TEST_PLACE_ID)).thenReturn(Optional.empty());
            PlaceEnrichmentCacheEntry entry = enrichedEntry("sushi-nakazawa_40.7295_-74.0028", null);
            when(cacheRepository.findByCacheKey(anyString())).thenReturn(Optional.of(entry));

            Optional<PlaceEnrichmentCacheEntry> result = service.lookupCache(
                TEST_NAME, TEST_LAT, TEST_LNG, TEST_PLACE_ID);

            assertThat(result).isPresent();
        }

        @Test
        void neitherFound_ReturnsEmpty() {
            when(cacheRepository.findByGooglePlaceId(TEST_PLACE_ID)).thenReturn(Optional.empty());
            when(cacheRepository.findByCacheKey(anyString())).thenReturn(Optional.empty());

            Optional<PlaceEnrichmentCacheEntry> result = service.lookupCache(
                TEST_NAME, TEST_LAT, TEST_LNG, TEST_PLACE_ID);

            assertThat(result).isEmpty();
        }

        @Test
        void nullGooglePlaceId_SearchesByCacheKey() {
            when(cacheRepository.findByCacheKey(anyString())).thenReturn(Optional.empty());

            service.lookupCache(TEST_NAME, TEST_LAT, TEST_LNG, null);

            verify(cacheRepository, never()).findByGooglePlaceId(any());
            verify(cacheRepository).findByCacheKey(anyString());
        }

        @Test
        void nullNameAndCoords_ReturnsEmpty() {
            Optional<PlaceEnrichmentCacheEntry> result = service.lookupCache(null, null, null, null);
            assertThat(result).isEmpty();
            verifyNoInteractions(cacheRepository);
        }
    }

    // ===== enrichPlaceAsync =====

    @Nested
    class EnrichPlaceAsyncTests {

        @Test
        void successfulPipeline_CopiesDataToIdeaRecord() {
            when(cacheRepository.findByGooglePlaceId(TEST_PLACE_ID)).thenReturn(Optional.empty());
            when(cacheRepository.findByCacheKey(anyString())).thenReturn(Optional.empty());
            when(googlePlacesClient.getPlaceDetails(TEST_PLACE_ID))
                .thenReturn(Optional.of(fullDetailsResult()));
            when(googlePlacesClient.getPlacePhoto(anyString())).thenReturn(Optional.empty());

            service.enrichPlaceAsync(TEST_GROUP, TEST_LIST, TEST_IDEA,
                TEST_NAME, TEST_LAT, TEST_LNG, TEST_PLACE_ID, null);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, AttributeValue>> captor = ArgumentCaptor.forClass(Map.class);
            verify(ideaListRepository).updateIdeaEnrichmentData(
                eq(TEST_GROUP), eq(TEST_LIST), eq(TEST_IDEA), captor.capture());
            Map<String, AttributeValue> attrs = captor.getValue();
            assertThat(attrs.get("enrichmentStatus").s()).isEqualTo("ENRICHED");
            assertThat(attrs.get("cachedRating").n()).isEqualTo("4.6");
        }

        @Test
        void cachedStatus_StillCopiesDataToIdea() {
            // enrichPlaceAsync should copy data even when enrichPlaceSync returns CACHED
            PlaceEnrichmentCacheEntry entry = enrichedEntry("sushi-nakazawa_40.7295_-74.0028", TEST_PLACE_ID);
            when(cacheRepository.findByGooglePlaceId(TEST_PLACE_ID)).thenReturn(Optional.of(entry));

            service.enrichPlaceAsync(TEST_GROUP, TEST_LIST, TEST_IDEA,
                TEST_NAME, TEST_LAT, TEST_LNG, TEST_PLACE_ID, null);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, AttributeValue>> captor = ArgumentCaptor.forClass(Map.class);
            verify(ideaListRepository).updateIdeaEnrichmentData(
                eq(TEST_GROUP), eq(TEST_LIST), eq(TEST_IDEA), captor.capture());
            Map<String, AttributeValue> attrs = captor.getValue();
            assertThat(attrs.get("enrichmentStatus").s()).isEqualTo("ENRICHED");
            assertThat(attrs.get("cachedRating").n()).isEqualTo("4.6");
        }

        @Test
        void pipelineFails_SetsIdeaStatusToFailed() {
            when(cacheRepository.findByCacheKey(anyString())).thenReturn(Optional.empty());
            when(googlePlacesClient.findPlace(anyString(), anyDouble(), anyDouble()))
                .thenReturn(Optional.empty());

            service.enrichPlaceAsync(TEST_GROUP, TEST_LIST, TEST_IDEA,
                TEST_NAME, TEST_LAT, TEST_LNG, null, null);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, AttributeValue>> captor = ArgumentCaptor.forClass(Map.class);
            verify(ideaListRepository).updateIdeaEnrichmentData(
                eq(TEST_GROUP), eq(TEST_LIST), eq(TEST_IDEA), captor.capture());
            assertThat(captor.getValue().get("enrichmentStatus").s()).isEqualTo("FAILED");
        }

        @Test
        void nullName_SkipsWithoutError() {
            assertThatCode(() -> service.enrichPlaceAsync(
                TEST_GROUP, TEST_LIST, TEST_IDEA, null, TEST_LAT, TEST_LNG, TEST_PLACE_ID, null))
                .doesNotThrowAnyException();
            verifyNoInteractions(googlePlacesClient, cacheRepository);
        }

        @Test
        void nullCoords_SkipsWithoutError() {
            assertThatCode(() -> service.enrichPlaceAsync(
                TEST_GROUP, TEST_LIST, TEST_IDEA, TEST_NAME, null, null, null, null))
                .doesNotThrowAnyException();
        }
    }

    // ===== triggerReadPathEnrichment =====

    @Nested
    class TriggerReadPathEnrichmentTests {

        @Test
        void nullStatus_LegacyData_TriggersEnrichment() {
            IdeaListMember member = member("idea-1", TEST_PLACE_ID, null, null, TEST_LAT, TEST_LNG);
            when(cacheRepository.findByGooglePlaceId(TEST_PLACE_ID)).thenReturn(Optional.empty());
            when(cacheRepository.findByCacheKey(anyString())).thenReturn(Optional.empty());
            when(googlePlacesClient.getPlaceDetails(TEST_PLACE_ID)).thenReturn(Optional.of(fullDetailsResult()));
            when(googlePlacesClient.getPlacePhoto(anyString())).thenReturn(Optional.empty());

            service.triggerReadPathEnrichment(List.of(member), TEST_GROUP, TEST_LIST);

            verify(ideaListRepository).updateIdeaEnrichmentData(any(), any(), eq("idea-1"), any());
        }

        @Test
        void pendingStatus_TriggersEnrichment() {
            IdeaListMember member = member("idea-2", TEST_PLACE_ID, "PENDING", null, TEST_LAT, TEST_LNG);
            when(cacheRepository.findByGooglePlaceId(TEST_PLACE_ID)).thenReturn(Optional.empty());
            when(cacheRepository.findByCacheKey(anyString())).thenReturn(Optional.empty());
            when(googlePlacesClient.getPlaceDetails(TEST_PLACE_ID)).thenReturn(Optional.of(fullDetailsResult()));
            when(googlePlacesClient.getPlacePhoto(anyString())).thenReturn(Optional.empty());

            service.triggerReadPathEnrichment(List.of(member), TEST_GROUP, TEST_LIST);

            verify(ideaListRepository).updateIdeaEnrichmentData(any(), any(), eq("idea-2"), any());
        }

        @Test
        void failedWithLowCount_TriggersEnrichment() {
            IdeaListMember member = member("idea-3", TEST_PLACE_ID, "FAILED", null, TEST_LAT, TEST_LNG);
            PlaceEnrichmentCacheEntry failedEntry = new PlaceEnrichmentCacheEntry();
            failedEntry.setFailureCount(1);
            failedEntry.setStatus("FAILED");
            // needsEnrichment + lookupCacheInternal both use findByGooglePlaceId (returns FAILED → pipeline runs)
            when(cacheRepository.findByGooglePlaceId(TEST_PLACE_ID)).thenReturn(Optional.of(failedEntry));
            when(googlePlacesClient.getPlaceDetails(TEST_PLACE_ID)).thenReturn(Optional.of(fullDetailsResult()));
            lenient().when(googlePlacesClient.getPlacePhoto(anyString())).thenReturn(Optional.empty());

            service.triggerReadPathEnrichment(List.of(member), TEST_GROUP, TEST_LIST);

            verify(googlePlacesClient).getPlaceDetails(TEST_PLACE_ID);
        }

        @Test
        void failedWithMaxCount_Skipped() {
            IdeaListMember member = member("idea-4", TEST_PLACE_ID, "FAILED", null, TEST_LAT, TEST_LNG);
            PlaceEnrichmentCacheEntry maxFailed = new PlaceEnrichmentCacheEntry();
            maxFailed.setFailureCount(3);
            maxFailed.setStatus("PERMANENTLY_FAILED");
            when(cacheRepository.findByGooglePlaceId(TEST_PLACE_ID)).thenReturn(Optional.of(maxFailed));

            service.triggerReadPathEnrichment(List.of(member), TEST_GROUP, TEST_LIST);

            verifyNoInteractions(googlePlacesClient);
        }

        @Test
        void permanentlyFailed_Skipped() {
            IdeaListMember member = member("idea-5", TEST_PLACE_ID, "PERMANENTLY_FAILED", null, TEST_LAT, TEST_LNG);

            service.triggerReadPathEnrichment(List.of(member), TEST_GROUP, TEST_LIST);

            verifyNoInteractions(googlePlacesClient, cacheRepository);
        }

        @Test
        void notApplicable_Skipped() {
            IdeaListMember member = member("idea-6", null, "NOT_APPLICABLE", null, TEST_LAT, TEST_LNG);

            service.triggerReadPathEnrichment(List.of(member), TEST_GROUP, TEST_LIST);

            verifyNoInteractions(googlePlacesClient, cacheRepository);
        }

        @Test
        void enrichedAndStale_TriggersEnrichment() {
            Instant stale = Instant.now().minus(45, ChronoUnit.DAYS);
            IdeaListMember member = member("idea-7", TEST_PLACE_ID, "ENRICHED", stale, TEST_LAT, TEST_LNG);
            when(cacheRepository.findByGooglePlaceId(TEST_PLACE_ID)).thenReturn(Optional.empty());
            when(cacheRepository.findByCacheKey(anyString())).thenReturn(Optional.empty());
            when(googlePlacesClient.getPlaceDetails(TEST_PLACE_ID)).thenReturn(Optional.of(fullDetailsResult()));
            when(googlePlacesClient.getPlacePhoto(anyString())).thenReturn(Optional.empty());

            service.triggerReadPathEnrichment(List.of(member), TEST_GROUP, TEST_LIST);

            verify(ideaListRepository).updateIdeaEnrichmentData(any(), any(), eq("idea-7"), any());
        }

        @Test
        void enrichedAndFresh_Skipped() {
            Instant fresh = Instant.now().minus(5, ChronoUnit.DAYS);
            IdeaListMember member = member("idea-8", TEST_PLACE_ID, "ENRICHED", fresh, TEST_LAT, TEST_LNG);

            service.triggerReadPathEnrichment(List.of(member), TEST_GROUP, TEST_LIST);

            verifyNoInteractions(googlePlacesClient);
        }

        @Test
        void max5CapEnforced() {
            List<IdeaListMember> members = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                members.add(member("idea-" + i, TEST_PLACE_ID + i, null, null, TEST_LAT, TEST_LNG));
            }
            when(cacheRepository.findByGooglePlaceId(anyString())).thenReturn(Optional.empty());
            when(cacheRepository.findByCacheKey(anyString())).thenReturn(Optional.empty());
            when(googlePlacesClient.getPlaceDetails(anyString())).thenReturn(Optional.of(fullDetailsResult()));
            when(googlePlacesClient.getPlacePhoto(anyString())).thenReturn(Optional.empty());

            service.triggerReadPathEnrichment(members, TEST_GROUP, TEST_LIST);

            verify(ideaListRepository, times(5)).updateIdeaEnrichmentData(any(), any(), any(), any());
        }

        @Test
        void memberWithoutNameOrCoords_Skipped() {
            IdeaListMember member = member("idea-skip", null, null, null, null, null);
            member.setName(null);

            service.triggerReadPathEnrichment(List.of(member), TEST_GROUP, TEST_LIST);

            verifyNoInteractions(googlePlacesClient);
        }

        @Test
        void memberWithoutCoordsButHasGooglePlaceId_PassedToEnrichAsync() {
            // Has googlePlaceId but no lat/lng — triggerReadPathEnrichment allows it through
            // (hasGooglePlaceId=true), but enrichPlaceAsync skips because coords are null.
            // Verifies no crash and graceful handling.
            IdeaListMember member = member("idea-gid", TEST_PLACE_ID, null, null, null, null);

            assertThatCode(() ->
                service.triggerReadPathEnrichment(List.of(member), TEST_GROUP, TEST_LIST))
                .doesNotThrowAnyException();
            // No interactions with Google API since enrichPlaceAsync skips null coords
            verifyNoInteractions(googlePlacesClient);
        }

        @Test
        void nullMembersList_DoesNotThrow() {
            assertThatCode(() ->
                service.triggerReadPathEnrichment(null, TEST_GROUP, TEST_LIST))
                .doesNotThrowAnyException();
        }

        @Test
        void emptyMembersList_DoesNotThrow() {
            assertThatCode(() ->
                service.triggerReadPathEnrichment(List.of(), TEST_GROUP, TEST_LIST))
                .doesNotThrowAnyException();
        }
    }

    // ===== helpers =====

    private PlaceEnrichmentCacheEntry enrichedEntry(String cacheKey, String googlePlaceId) {
        PlaceEnrichmentCacheEntry e = new PlaceEnrichmentCacheEntry();
        e.setCacheKey(cacheKey);
        e.setGooglePlaceId(googlePlaceId);
        e.setStatus("ENRICHED");
        e.setCachedRating(4.6);
        e.setCachedPriceLevel(4);
        e.setPhoneNumber("+1 212 524 0500");
        e.setWebsiteUrl("https://sushinakazawa.com");
        e.setCachedHoursJson("[\"Monday: 5:00 – 10:00 PM\"]");
        return e;
    }

    private PlaceDetailsResult fullDetailsResult() {
        PlaceDetailsResult d = new PlaceDetailsResult();
        d.setRating(4.6);
        d.setPriceLevel(4);
        d.setPhoneNumber("+1 212 524 0500");
        d.setWebsiteUrl("https://sushinakazawa.com");
        d.setCachedHoursJson("[\"Monday: 5:00 – 10:00 PM\"]");
        d.setPhotoName("places/" + TEST_PLACE_ID + "/photos/abc123");
        return d;
    }

    private IdeaListMember member(String ideaId, String googlePlaceId, String status,
                                   Instant lastEnrichedAt, Double lat, Double lng) {
        IdeaListMember m = new IdeaListMember(TEST_GROUP, TEST_LIST, TEST_NAME, null, null, "user-1");
        m.setIdeaId(ideaId);
        m.setGooglePlaceId(googlePlaceId);
        m.setEnrichmentStatus(status);
        m.setLastEnrichedAt(lastEnrichedAt);
        m.setLatitude(lat);
        m.setLongitude(lng);
        return m;
    }
}
