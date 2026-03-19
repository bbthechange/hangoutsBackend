package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.EnrichmentData;
import com.bbthechange.inviter.dto.EnrichmentResult;
import com.bbthechange.inviter.dto.PlaceEnrichRequest;
import com.bbthechange.inviter.dto.PlaceEnrichResponse;
import com.bbthechange.inviter.service.PlaceEnrichmentService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Direct unit tests for PlaceEnrichmentController — verifies response shape matches spec.
 */
@ExtendWith(MockitoExtension.class)
class PlaceEnrichmentControllerUnitTest {

    @Mock
    private PlaceEnrichmentService placeEnrichmentService;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private PlaceEnrichmentController controller;

    private String testUserId;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID().toString();
        when(httpRequest.getAttribute("userId")).thenReturn(testUserId);
    }

    private PlaceEnrichRequest validRequest() {
        PlaceEnrichRequest req = new PlaceEnrichRequest();
        req.setName("Sushi Nakazawa");
        req.setLatitude(40.7295);
        req.setLongitude(-74.0028);
        return req;
    }

    private EnrichmentData sampleData() {
        EnrichmentData data = new EnrichmentData();
        data.setCachedPhotoUrl("places/sushi-nakazawa_40.7295_-74.0028/photo.jpg");
        data.setCachedRating(4.6);
        data.setCachedPriceLevel(4);
        data.setPhoneNumber("+12125240500");
        data.setWebsiteUrl("https://sushinakazawa.com");
        data.setGooglePlaceId("ChIJN1t_tDeuEmsRUsoyG83frY4");
        return data;
    }

    @Test
    void enrichPlace_cachedResult_responseShapeMatchesSpec() {
        when(placeEnrichmentService.enrichPlaceSync(any(), anyDouble(), anyDouble(), any(), any()))
                .thenReturn(EnrichmentResult.cached(sampleData()));

        ResponseEntity<PlaceEnrichResponse> response = controller.enrichPlace(validRequest(), httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PlaceEnrichResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo("CACHED");
        assertThat(body.getData()).isNotNull();
        assertThat(body.getData().getCachedRating()).isEqualTo(4.6);
        assertThat(body.getData().getCachedPriceLevel()).isEqualTo(4);
        assertThat(body.getData().getGooglePlaceId()).isEqualTo("ChIJN1t_tDeuEmsRUsoyG83frY4");
    }

    @Test
    void enrichPlace_enrichedResult_responseShapeMatchesSpec() {
        when(placeEnrichmentService.enrichPlaceSync(any(), anyDouble(), anyDouble(), any(), any()))
                .thenReturn(EnrichmentResult.enriched(sampleData()));

        ResponseEntity<PlaceEnrichResponse> response = controller.enrichPlace(validRequest(), httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PlaceEnrichResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo("ENRICHED");
        assertThat(body.getData()).isNotNull();
    }

    @Test
    void enrichPlace_failedResult_dataIsNull() {
        when(placeEnrichmentService.enrichPlaceSync(any(), anyDouble(), anyDouble(), any(), any()))
                .thenReturn(EnrichmentResult.failed());

        ResponseEntity<PlaceEnrichResponse> response = controller.enrichPlace(validRequest(), httpRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PlaceEnrichResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo("FAILED");
        assertThat(body.getData()).isNull();
    }

    @Test
    void enrichPlace_delegatesAllParamsToService() {
        PlaceEnrichRequest request = validRequest();
        request.setGooglePlaceId("ChIJN1t_tDeuEmsRUsoyG83frY4");
        request.setApplePlaceId("I63LYKU7G9BCPA");
        when(placeEnrichmentService.enrichPlaceSync(any(), anyDouble(), anyDouble(), any(), any()))
                .thenReturn(EnrichmentResult.enriched(sampleData()));

        controller.enrichPlace(request, httpRequest);

        verify(placeEnrichmentService).enrichPlaceSync(
                eq("Sushi Nakazawa"),
                eq(40.7295),
                eq(-74.0028),
                eq("ChIJN1t_tDeuEmsRUsoyG83frY4"),
                eq("I63LYKU7G9BCPA")
        );
    }
}
