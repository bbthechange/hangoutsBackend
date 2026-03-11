package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.model.IdeaListMember;
import com.bbthechange.inviter.repository.IdeaListRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for PlaceEnrichmentServiceImpl.
 *
 * These tests verify the enrichment service's observable behavior:
 * - What enrichment data gets persisted to the repository
 * - Which items get re-enriched based on staleness criteria
 * - Graceful degradation when external services fail
 *
 * Tests mock the external boundaries (Google API, S3, repository) and verify
 * the service's decisions and outcomes, not its internal HTTP call mechanics.
 */
@ExtendWith(MockitoExtension.class)
class PlaceEnrichmentServiceImplTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private IdeaListRepository ideaListRepository;

    @Mock
    private S3Client s3Client;

    private PlaceEnrichmentServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String testGroupId;
    private String testListId;
    private String testIdeaId;

    @BeforeEach
    void setUp() {
        testGroupId = UUID.randomUUID().toString();
        testListId = UUID.randomUUID().toString();
        testIdeaId = UUID.randomUUID().toString();
        service = new PlaceEnrichmentServiceImpl(
                restTemplate, objectMapper, ideaListRepository, s3Client,
                "test-api-key", "test-bucket");
    }

    // ===== isEnabled TESTS =====

    @Test
    void isEnabled_WithApiKey_ReturnsTrue() {
        assertThat(service.isEnabled()).isTrue();
    }

    @Test
    void isEnabled_WithBlankApiKey_ReturnsFalse() {
        PlaceEnrichmentServiceImpl disabledService = new PlaceEnrichmentServiceImpl(
                restTemplate, objectMapper, ideaListRepository, s3Client,
                "   ", "test-bucket");
        assertThat(disabledService.isEnabled()).isFalse();
    }

    @Test
    void isEnabled_WithNullApiKey_ReturnsFalse() {
        PlaceEnrichmentServiceImpl disabledService = new PlaceEnrichmentServiceImpl(
                restTemplate, objectMapper, ideaListRepository, s3Client,
                null, "test-bucket");
        assertThat(disabledService.isEnabled()).isFalse();
    }

    // ===== enrichPlaceAsync TESTS =====

    @Nested
    class EnrichPlaceAsyncTests {

        private void mockGoogleDetailsResponse(String jsonResponse) {
            when(restTemplate.getForObject(contains("place/details"), eq(String.class)))
                    .thenReturn(jsonResponse);
        }

        private void mockPhotoDownload(byte[] photoBytes) {
            when(restTemplate.getForObject(contains("place/photo"), eq(byte[].class)))
                    .thenReturn(photoBytes);
        }

        private void mockS3UploadSuccess() {
            when(s3Client.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                    .thenReturn(PutObjectResponse.builder().build());
        }

        private Map<String, AttributeValue> capturePersistedEnrichmentData() {
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, AttributeValue>> captor = ArgumentCaptor.forClass(Map.class);
            verify(ideaListRepository).updateIdeaEnrichmentData(
                    eq(testGroupId), eq(testListId), eq(testIdeaId), captor.capture());
            return captor.getValue();
        }

        @Test
        void enrichPlaceAsync_SuccessfulEnrichment_PersistsAllExtractedData() {
            // Given: Google API returns a full place details response
            mockGoogleDetailsResponse("""
                {
                    "result": {
                        "rating": 4.5,
                        "price_level": 2,
                        "formatted_phone_number": "(212) 555-1234",
                        "website": "http://joespizza.com",
                        "formatted_address": "123 Main St, New York, NY",
                        "opening_hours": {
                            "weekday_text": ["Monday: 9:00 AM - 10:00 PM"]
                        },
                        "photos": [
                            {"photo_reference": "photo-ref-123"}
                        ]
                    }
                }
                """);
            mockPhotoDownload(new byte[]{1, 2, 3});
            mockS3UploadSuccess();

            // When
            service.enrichPlaceAsync(testGroupId, testListId, testIdeaId, "ChIJtest123");

            // Then: all extracted place data is persisted with ENRICHED status
            Map<String, AttributeValue> data = capturePersistedEnrichmentData();
            assertThat(data.get("cachedRating").n()).isEqualTo("4.5");
            assertThat(data.get("cachedPriceLevel").n()).isEqualTo("2");
            assertThat(data.get("phoneNumber").s()).isEqualTo("(212) 555-1234");
            assertThat(data.get("websiteUrl").s()).isEqualTo("http://joespizza.com");
            assertThat(data.get("address").s()).isEqualTo("123 Main St, New York, NY");
            assertThat(data.get("cachedHoursJson").s()).contains("Monday: 9:00 AM - 10:00 PM");
            assertThat(data.get("cachedPhotoUrl").s()).startsWith("places/photos/").endsWith(".jpg");
            assertThat(data.get("enrichmentStatus").s()).isEqualTo("ENRICHED");
            assertThat(data.get("lastEnrichedAt")).isNotNull();
        }

        @Test
        void enrichPlaceAsync_GoogleApiReturnsNoResult_PersistsFailedStatus() {
            // Given: Google API returns response with no "result" field
            mockGoogleDetailsResponse("{\"status\": \"ZERO_RESULTS\"}");

            // When
            service.enrichPlaceAsync(testGroupId, testListId, testIdeaId, "invalid-place-id");

            // Then: only FAILED status is persisted (no enrichment data)
            Map<String, AttributeValue> data = capturePersistedEnrichmentData();
            assertThat(data.get("enrichmentStatus").s()).isEqualTo("FAILED");
        }

        @Test
        void enrichPlaceAsync_GoogleApiThrowsException_PersistsFailedStatus() {
            // Given: Google API call throws
            when(restTemplate.getForObject(contains("place/details"), eq(String.class)))
                    .thenThrow(new RestClientException("Connection refused"));

            // When
            service.enrichPlaceAsync(testGroupId, testListId, testIdeaId, "ChIJtest123");

            // Then: FAILED status persisted via graceful error handling
            Map<String, AttributeValue> data = capturePersistedEnrichmentData();
            assertThat(data.get("enrichmentStatus").s()).isEqualTo("FAILED");
        }

        @Test
        void enrichPlaceAsync_GoogleApiReturnsNull_PersistsFailedStatus() {
            // Given: RestTemplate returns null
            mockGoogleDetailsResponse(null);

            // When
            service.enrichPlaceAsync(testGroupId, testListId, testIdeaId, "ChIJtest123");

            // Then
            Map<String, AttributeValue> data = capturePersistedEnrichmentData();
            assertThat(data.get("enrichmentStatus").s()).isEqualTo("FAILED");
        }

        @Test
        void enrichPlaceAsync_ResponseWithNoPhotos_EnrichesOtherFieldsWithoutPhoto() {
            // Given: response has rating and phone but no photos
            mockGoogleDetailsResponse("""
                {
                    "result": {
                        "rating": 3.8,
                        "formatted_phone_number": "(555) 123-4567"
                    }
                }
                """);

            // When
            service.enrichPlaceAsync(testGroupId, testListId, testIdeaId, "ChIJtest123");

            // Then: available fields are persisted, no photo URL
            Map<String, AttributeValue> data = capturePersistedEnrichmentData();
            assertThat(data.get("cachedRating").n()).isEqualTo("3.8");
            assertThat(data.get("phoneNumber").s()).isEqualTo("(555) 123-4567");
            assertThat(data).doesNotContainKey("cachedPhotoUrl");
            assertThat(data.get("enrichmentStatus").s()).isEqualTo("ENRICHED");
        }

        @Test
        void enrichPlaceAsync_PhotoUploadFails_StillEnrichesOtherFields() {
            // Given: place details with a photo, but S3 upload fails
            mockGoogleDetailsResponse("""
                {
                    "result": {
                        "rating": 4.0,
                        "photos": [{"photo_reference": "photo-ref-fail"}]
                    }
                }
                """);
            mockPhotoDownload(new byte[]{1, 2, 3});
            when(s3Client.putObject(any(PutObjectRequest.class), any(software.amazon.awssdk.core.sync.RequestBody.class)))
                    .thenThrow(S3Exception.builder().message("Access denied").build());

            // When
            service.enrichPlaceAsync(testGroupId, testListId, testIdeaId, "ChIJtest123");

            // Then: rating is enriched, photo is gracefully skipped, status is still ENRICHED
            Map<String, AttributeValue> data = capturePersistedEnrichmentData();
            assertThat(data.get("cachedRating").n()).isEqualTo("4.0");
            assertThat(data).doesNotContainKey("cachedPhotoUrl");
            assertThat(data.get("enrichmentStatus").s()).isEqualTo("ENRICHED");
        }

        @Test
        void enrichPlaceAsync_EmptyPhotoBytes_SkipsPhotoGracefully() {
            // Given: photo download returns empty bytes
            mockGoogleDetailsResponse("""
                {
                    "result": {
                        "rating": 4.2,
                        "photos": [{"photo_reference": "photo-ref-empty"}]
                    }
                }
                """);
            mockPhotoDownload(new byte[0]);

            // When
            service.enrichPlaceAsync(testGroupId, testListId, testIdeaId, "ChIJtest123");

            // Then: no photo persisted, but enrichment succeeds
            Map<String, AttributeValue> data = capturePersistedEnrichmentData();
            assertThat(data).doesNotContainKey("cachedPhotoUrl");
            assertThat(data.get("enrichmentStatus").s()).isEqualTo("ENRICHED");
        }

        @Test
        void enrichPlaceAsync_SuccessfulPhoto_CachesToS3WithCorrectKey() {
            // Given: place has a photo that downloads successfully
            mockGoogleDetailsResponse("""
                {
                    "result": {
                        "photos": [{"photo_reference": "photo-ref-format"}]
                    }
                }
                """);
            mockPhotoDownload(new byte[]{1, 2, 3});
            mockS3UploadSuccess();

            // When
            service.enrichPlaceAsync(testGroupId, testListId, testIdeaId, "ChIJtest123");

            // Then: photo is cached to S3 under the expected path
            ArgumentCaptor<PutObjectRequest> s3Captor = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(s3Client).putObject(s3Captor.capture(), any(software.amazon.awssdk.core.sync.RequestBody.class));
            assertThat(s3Captor.getValue().key()).isEqualTo("places/photos/" + testIdeaId + ".jpg");
            assertThat(s3Captor.getValue().contentType()).isEqualTo("image/jpeg");
        }

        @Test
        void enrichPlaceAsync_MinimalResult_StillMarksEnriched() {
            // Given: result exists but has no useful fields at all
            mockGoogleDetailsResponse("{\"result\": {}}");

            // When
            service.enrichPlaceAsync(testGroupId, testListId, testIdeaId, "ChIJtest123");

            // Then: status is ENRICHED (the call succeeded, just no data available)
            Map<String, AttributeValue> data = capturePersistedEnrichmentData();
            assertThat(data.get("enrichmentStatus").s()).isEqualTo("ENRICHED");
            assertThat(data.get("lastEnrichedAt")).isNotNull();
        }
    }

    // ===== triggerStaleReEnrichment TESTS =====

    @Nested
    class StaleReEnrichmentTests {

        private IdeaListMember createMember(String ideaId, String googlePlaceId,
                                            String enrichmentStatus, Instant lastEnrichedAt) {
            IdeaListMember member = new IdeaListMember(testGroupId, testListId, "Test Place", null, null, "user-1");
            member.setIdeaId(ideaId);
            member.setGooglePlaceId(googlePlaceId);
            member.setEnrichmentStatus(enrichmentStatus);
            member.setLastEnrichedAt(lastEnrichedAt);
            return member;
        }

        private int countEnrichmentCallsToRepository() {
            // Each enrichment attempt results in a call to updateIdeaEnrichmentData.
            // This is the real observable side-effect of re-enrichment happening.
            return (int) mockingDetails(ideaListRepository).getInvocations().stream()
                    .filter(inv -> inv.getMethod().getName().equals("updateIdeaEnrichmentData"))
                    .count();
        }

        @Test
        void triggerStaleReEnrichment_StaleEnrichedItem_TriggersReEnrichment() {
            // Given: an item that was enriched 45 days ago
            IdeaListMember staleMember = createMember("stale-idea", "ChIJ-stale",
                    "ENRICHED", Instant.now().minus(45, ChronoUnit.DAYS));

            // Mock external calls so enrichPlaceAsync succeeds
            when(restTemplate.getForObject(contains("place/details"), eq(String.class)))
                    .thenReturn("{\"result\": {\"rating\": 4.0}}");

            // When
            service.triggerStaleReEnrichment(List.of(staleMember), testGroupId, testListId);

            // Then: the stale item's enrichment data was updated in the repository
            assertThat(countEnrichmentCallsToRepository()).isEqualTo(1);
        }

        @Test
        void triggerStaleReEnrichment_RecentlyEnrichedItem_NotReEnriched() {
            // Given: an item enriched 10 days ago (fresh)
            IdeaListMember recentMember = createMember("recent-idea", "ChIJ-recent",
                    "ENRICHED", Instant.now().minus(10, ChronoUnit.DAYS));

            // When
            service.triggerStaleReEnrichment(List.of(recentMember), testGroupId, testListId);

            // Then: no enrichment data updates happen
            assertThat(countEnrichmentCallsToRepository()).isEqualTo(0);
        }

        @Test
        void triggerStaleReEnrichment_FailedStatusItem_NotReEnriched() {
            // Given: an item with FAILED status, even though it's old
            IdeaListMember failedMember = createMember("failed-idea", "ChIJ-failed",
                    "FAILED", Instant.now().minus(60, ChronoUnit.DAYS));

            // When
            service.triggerStaleReEnrichment(List.of(failedMember), testGroupId, testListId);

            // Then: FAILED items are not retried automatically
            assertThat(countEnrichmentCallsToRepository()).isEqualTo(0);
        }

        @Test
        void triggerStaleReEnrichment_PendingStatusItem_NotReEnriched() {
            // Given: an item still in PENDING status
            IdeaListMember pendingMember = createMember("pending-idea", "ChIJ-pending",
                    "PENDING", null);

            // When
            service.triggerStaleReEnrichment(List.of(pendingMember), testGroupId, testListId);

            // Then: pending items are not re-enriched
            assertThat(countEnrichmentCallsToRepository()).isEqualTo(0);
        }

        @Test
        void triggerStaleReEnrichment_ItemWithoutGooglePlaceId_Skipped() {
            // Given: an item that has ENRICHED status but no googlePlaceId (e.g. custom place)
            IdeaListMember customMember = createMember("custom-idea", null,
                    "ENRICHED", Instant.now().minus(45, ChronoUnit.DAYS));

            // When
            service.triggerStaleReEnrichment(List.of(customMember), testGroupId, testListId);

            // Then: can't re-enrich without a Google Place ID
            assertThat(countEnrichmentCallsToRepository()).isEqualTo(0);
        }

        @Test
        void triggerStaleReEnrichment_MoreThanFiveStaleItems_OnlyEnrichesFive() {
            // Given: 7 stale items that all qualify for re-enrichment
            List<IdeaListMember> staleMembers = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                staleMembers.add(createMember("idea-" + i, "ChIJ-" + i,
                        "ENRICHED", Instant.now().minus(45, ChronoUnit.DAYS)));
            }

            when(restTemplate.getForObject(contains("place/details"), eq(String.class)))
                    .thenReturn("{\"result\": {\"rating\": 4.0}}");

            // When
            service.triggerStaleReEnrichment(staleMembers, testGroupId, testListId);

            // Then: at most 5 items are re-enriched per call (rate limiting)
            assertThat(countEnrichmentCallsToRepository()).isEqualTo(5);
        }

        @Test
        void triggerStaleReEnrichment_NullMembersList_DoesNotThrow() {
            assertThatCode(() -> service.triggerStaleReEnrichment(null, testGroupId, testListId))
                    .doesNotThrowAnyException();
        }

        @Test
        void triggerStaleReEnrichment_EmptyMembersList_DoesNotThrow() {
            assertThatCode(() -> service.triggerStaleReEnrichment(List.of(), testGroupId, testListId))
                    .doesNotThrowAnyException();
        }

        @Test
        void triggerStaleReEnrichment_MixedMembers_OnlyEnrichesEligible() {
            // Given: a mix of stale, recent, failed, and non-Google items
            List<IdeaListMember> mixedMembers = List.of(
                    createMember("stale1", "ChIJ-1", "ENRICHED", Instant.now().minus(45, ChronoUnit.DAYS)),
                    createMember("recent", "ChIJ-2", "ENRICHED", Instant.now().minus(5, ChronoUnit.DAYS)),
                    createMember("failed", "ChIJ-3", "FAILED", Instant.now().minus(60, ChronoUnit.DAYS)),
                    createMember("custom", null, "ENRICHED", Instant.now().minus(45, ChronoUnit.DAYS)),
                    createMember("stale2", "ChIJ-4", "ENRICHED", Instant.now().minus(31, ChronoUnit.DAYS))
            );

            when(restTemplate.getForObject(contains("place/details"), eq(String.class)))
                    .thenReturn("{\"result\": {\"rating\": 4.0}}");

            // When
            service.triggerStaleReEnrichment(mixedMembers, testGroupId, testListId);

            // Then: only the 2 stale+enriched+googlePlaceId items are re-enriched
            assertThat(countEnrichmentCallsToRepository()).isEqualTo(2);
        }
    }
}
