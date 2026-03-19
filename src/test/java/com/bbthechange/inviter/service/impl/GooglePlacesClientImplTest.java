package com.bbthechange.inviter.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.bbthechange.inviter.service.PlaceDetailsResult;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GooglePlacesClientImpl.
 *
 * Note: @CircuitBreaker is AOP-based and not active in unit tests without a Spring context.
 * These tests verify: correct request format (New API), header usage, response parsing,
 * and counter increments. Error propagation is tested without the circuit breaker fallback.
 */
@ExtendWith(MockitoExtension.class)
class GooglePlacesClientImplTest {

    @Mock
    private RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MeterRegistry meterRegistry;
    private GooglePlacesClientImpl client;

    private static final String TEST_API_KEY = "test-api-key-123";
    private static final String TEST_PLACE_ID = "ChIJN1t_tDeuEmsRUsoyG83frY4";
    private static final String TEST_PHOTO_NAME = "places/ChIJN1t_tDeuEmsRUsoyG83frY4/photos/AXCi2Q_jXBzg";

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        client = new GooglePlacesClientImpl(restTemplate, objectMapper, TEST_API_KEY, meterRegistry);
    }

    // ===== findPlace =====

    @Test
    void findPlace_withValidNameAndCoords_returnsGooglePlaceId() throws Exception {
        String responseJson = """
            {"places": [{"id": "%s"}]}
            """.formatted(TEST_PLACE_ID);
        when(restTemplate.exchange(
            eq("https://places.googleapis.com/v1/places:searchText"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(ResponseEntity.ok(responseJson));

        Optional<String> result = client.findPlace("Sushi Nakazawa", 40.7295, -74.0028);

        assertThat(result).isPresent().contains(TEST_PLACE_ID);
    }

    @Test
    void findPlace_verifiesNewApiKeyHeader() {
        when(restTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)
        )).thenReturn(ResponseEntity.ok("{\"places\": []}"));

        client.findPlace("Cafe", 37.7749, -122.4194);

        ArgumentCaptor<HttpEntity<Object>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));

        HttpHeaders headers = entityCaptor.getValue().getHeaders();
        assertThat(headers.getFirst("X-Goog-Api-Key")).isEqualTo(TEST_API_KEY);
        assertThat(headers.getFirst("X-Goog-FieldMask")).isEqualTo("places.id");
    }

    @Test
    void findPlace_noResults_returnsEmpty() throws Exception {
        when(restTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)
        )).thenReturn(ResponseEntity.ok("{\"places\": []}"));

        Optional<String> result = client.findPlace("NonExistentPlace", 0.0, 0.0);

        assertThat(result).isEmpty();
    }

    @Test
    void findPlace_emptyPlacesArray_returnsEmpty() throws Exception {
        when(restTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)
        )).thenReturn(ResponseEntity.ok("{}"));

        Optional<String> result = client.findPlace("Cafe", 40.0, -74.0);

        assertThat(result).isEmpty();
    }

    @Test
    void findPlace_apiError_throwsRuntimeException() {
        when(restTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)
        )).thenThrow(new RestClientException("Connection refused"));

        assertThatThrownBy(() -> client.findPlace("Sushi Nakazawa", 40.7295, -74.0028))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Google Places findPlace failed");
    }

    @Test
    void findPlace_incrementsCounter() {
        when(restTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)
        )).thenReturn(ResponseEntity.ok("{\"places\": []}"));

        client.findPlace("Cafe", 40.0, -74.0);

        double count = meterRegistry.counter("place_enrichment_api_calls", "sku", "find_place").count();
        assertThat(count).isEqualTo(1.0);
    }

    // ===== getPlaceDetails =====

    @Test
    void getPlaceDetails_validPlaceId_returnsDetails() throws Exception {
        String responseJson = """
            {
              "displayName": {"text": "Sushi Nakazawa"},
              "rating": 4.6,
              "priceLevel": "PRICE_LEVEL_VERY_EXPENSIVE",
              "nationalPhoneNumber": "+1 212 924 0500",
              "websiteUri": "https://sushinakazawa.com",
              "googleMapsUri": "https://maps.google.com/?cid=123",
              "currentOpeningHours": {
                "weekdayDescriptions": [
                  "Monday: 5:00 – 10:00 PM",
                  "Tuesday: 5:00 – 10:00 PM",
                  "Wednesday: 5:00 – 10:00 PM",
                  "Thursday: 5:00 – 10:00 PM",
                  "Friday: 5:00 – 11:00 PM",
                  "Saturday: 5:00 – 11:00 PM",
                  "Sunday: Closed"
                ]
              },
              "photos": [{"name": "%s"}]
            }
            """.formatted(TEST_PHOTO_NAME);

        when(restTemplate.exchange(
            eq("https://places.googleapis.com/v1/places/" + TEST_PLACE_ID),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        )).thenReturn(ResponseEntity.ok(responseJson));

        Optional<PlaceDetailsResult> result = client.getPlaceDetails(TEST_PLACE_ID);

        assertThat(result).isPresent();
        PlaceDetailsResult details = result.get();
        assertThat(details.getRating()).isEqualTo(4.6);
        assertThat(details.getPriceLevel()).isEqualTo(4);
        assertThat(details.getPhoneNumber()).isEqualTo("+1 212 924 0500");
        assertThat(details.getWebsiteUrl()).isEqualTo("https://sushinakazawa.com");
        assertThat(details.getGoogleMapsUri()).isEqualTo("https://maps.google.com/?cid=123");
        assertThat(details.getCachedHoursJson()).contains("Monday");
        assertThat(details.getPhotoName()).isEqualTo(TEST_PHOTO_NAME);
    }

    @Test
    void getPlaceDetails_partialData_returnsPartialResult() throws Exception {
        String responseJson = """
            {
              "displayName": {"text": "Simple Cafe"},
              "rating": 3.9
            }
            """;

        when(restTemplate.exchange(
            anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)
        )).thenReturn(ResponseEntity.ok(responseJson));

        Optional<PlaceDetailsResult> result = client.getPlaceDetails(TEST_PLACE_ID);

        assertThat(result).isPresent();
        assertThat(result.get().getRating()).isEqualTo(3.9);
        assertThat(result.get().getPriceLevel()).isNull();
        assertThat(result.get().getPhoneNumber()).isNull();
        assertThat(result.get().getCachedHoursJson()).isNull();
        assertThat(result.get().getPhotoName()).isNull();
    }

    @Test
    void getPlaceDetails_verifiesFieldMaskHeader() {
        when(restTemplate.exchange(
            anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)
        )).thenReturn(ResponseEntity.ok("{}"));

        client.getPlaceDetails(TEST_PLACE_ID);

        ArgumentCaptor<HttpEntity<Void>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.GET), entityCaptor.capture(), eq(String.class));

        HttpHeaders headers = entityCaptor.getValue().getHeaders();
        assertThat(headers.getFirst("X-Goog-Api-Key")).isEqualTo(TEST_API_KEY);
        assertThat(headers.getFirst("X-Goog-FieldMask")).contains("rating", "priceLevel");
    }

    @Test
    void getPlaceDetails_apiError_throwsRuntimeException() {
        when(restTemplate.exchange(
            anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)
        )).thenThrow(new RestClientException("Timeout"));

        assertThatThrownBy(() -> client.getPlaceDetails(TEST_PLACE_ID))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Google Places getPlaceDetails failed");
    }

    @Test
    void getPlaceDetails_incrementsCounter() throws Exception {
        when(restTemplate.exchange(
            anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)
        )).thenReturn(ResponseEntity.ok("{\"rating\": 4.0}"));

        client.getPlaceDetails(TEST_PLACE_ID);

        double count = meterRegistry.counter("place_enrichment_api_calls", "sku", "place_details").count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    void getPlaceDetails_priceLevelMapping_inexpensive() throws Exception {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
            .thenReturn(ResponseEntity.ok("{\"priceLevel\": \"PRICE_LEVEL_INEXPENSIVE\"}"));

        Optional<PlaceDetailsResult> result = client.getPlaceDetails(TEST_PLACE_ID);
        assertThat(result.get().getPriceLevel()).isEqualTo(1);
    }

    @Test
    void getPlaceDetails_priceLevelMapping_moderate() throws Exception {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
            .thenReturn(ResponseEntity.ok("{\"priceLevel\": \"PRICE_LEVEL_MODERATE\"}"));

        Optional<PlaceDetailsResult> result = client.getPlaceDetails(TEST_PLACE_ID);
        assertThat(result.get().getPriceLevel()).isEqualTo(2);
    }

    @Test
    void getPlaceDetails_priceLevelMapping_expensive() throws Exception {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
            .thenReturn(ResponseEntity.ok("{\"priceLevel\": \"PRICE_LEVEL_EXPENSIVE\"}"));

        Optional<PlaceDetailsResult> result = client.getPlaceDetails(TEST_PLACE_ID);
        assertThat(result.get().getPriceLevel()).isEqualTo(3);
    }

    // ===== getPlacePhoto =====

    @Test
    void getPlacePhoto_validPhoto_returnsBytes() {
        byte[] photoBytes = "fake-jpeg-bytes".getBytes();
        when(restTemplate.exchange(
            contains(TEST_PHOTO_NAME + "/media"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(byte[].class)
        )).thenReturn(ResponseEntity.ok(photoBytes));

        Optional<byte[]> result = client.getPlacePhoto(TEST_PHOTO_NAME);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(photoBytes);
    }

    @Test
    void getPlacePhoto_noPhoto_returnsEmpty() {
        when(restTemplate.exchange(
            anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)
        )).thenReturn(ResponseEntity.ok(null));

        Optional<byte[]> result = client.getPlacePhoto(TEST_PHOTO_NAME);

        assertThat(result).isEmpty();
    }

    @Test
    void getPlacePhoto_emptyBytes_returnsEmpty() {
        when(restTemplate.exchange(
            anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)
        )).thenReturn(ResponseEntity.ok(new byte[0]));

        Optional<byte[]> result = client.getPlacePhoto(TEST_PHOTO_NAME);

        assertThat(result).isEmpty();
    }

    @Test
    void getPlacePhoto_apiError_throwsRuntimeException() {
        when(restTemplate.exchange(
            anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)
        )).thenThrow(new RestClientException("HTTP 429"));

        assertThatThrownBy(() -> client.getPlacePhoto(TEST_PHOTO_NAME))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Google Places getPlacePhoto failed");
    }

    @Test
    void getPlacePhoto_incrementsCounter() {
        when(restTemplate.exchange(
            anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)
        )).thenReturn(ResponseEntity.ok(new byte[]{1, 2, 3}));

        client.getPlacePhoto(TEST_PHOTO_NAME);

        double count = meterRegistry.counter("place_enrichment_api_calls", "sku", "place_photos").count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    void getPlacePhoto_urlContainsMaxDimensions() {
        when(restTemplate.exchange(
            anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)
        )).thenReturn(ResponseEntity.ok(new byte[]{1}));

        client.getPlacePhoto(TEST_PHOTO_NAME);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).exchange(urlCaptor.capture(), eq(HttpMethod.GET), any(), eq(byte[].class));
        assertThat(urlCaptor.getValue()).contains("maxHeightPx=400").contains("maxWidthPx=400");
    }
}
