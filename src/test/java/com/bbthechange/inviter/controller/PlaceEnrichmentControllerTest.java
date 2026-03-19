package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.EnrichmentData;
import com.bbthechange.inviter.dto.EnrichmentResult;
import com.bbthechange.inviter.dto.PlaceEnrichRequest;
import com.bbthechange.inviter.service.PlaceEnrichmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc tests for PlaceEnrichmentController — HTTP contract, validation, and service integration.
 */
@ExtendWith(MockitoExtension.class)
class PlaceEnrichmentControllerTest {

    @Mock
    private PlaceEnrichmentService placeEnrichmentService;

    @InjectMocks
    private PlaceEnrichmentController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private String testUserId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
        testUserId = UUID.randomUUID().toString();
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

    private PlaceEnrichRequest validRequest() {
        PlaceEnrichRequest req = new PlaceEnrichRequest();
        req.setName("Sushi Nakazawa");
        req.setLatitude(40.7295);
        req.setLongitude(-74.0028);
        return req;
    }

    // ===== SUCCESS CASES =====

    @Nested
    class SuccessTests {

        @Test
        void enrichPlace_cacheHit_returns200WithCachedStatus() throws Exception {
            when(placeEnrichmentService.enrichPlaceSync(any(), anyDouble(), anyDouble(), any(), any()))
                    .thenReturn(EnrichmentResult.cached(sampleData()));

            mockMvc.perform(post("/places/enrich")
                            .contentType(MediaType.APPLICATION_JSON)
                            .requestAttr("userId", testUserId)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CACHED"))
                    .andExpect(jsonPath("$.data.cachedRating").value(4.6))
                    .andExpect(jsonPath("$.data.googlePlaceId").value("ChIJN1t_tDeuEmsRUsoyG83frY4"));
        }

        @Test
        void enrichPlace_cacheMiss_returns200WithEnrichedStatus() throws Exception {
            when(placeEnrichmentService.enrichPlaceSync(any(), anyDouble(), anyDouble(), any(), any()))
                    .thenReturn(EnrichmentResult.enriched(sampleData()));

            mockMvc.perform(post("/places/enrich")
                            .contentType(MediaType.APPLICATION_JSON)
                            .requestAttr("userId", testUserId)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ENRICHED"))
                    .andExpect(jsonPath("$.data").exists());
        }

        @Test
        void enrichPlace_failure_returns200WithFailedStatus() throws Exception {
            when(placeEnrichmentService.enrichPlaceSync(any(), anyDouble(), anyDouble(), any(), any()))
                    .thenReturn(EnrichmentResult.failed());

            mockMvc.perform(post("/places/enrich")
                            .contentType(MediaType.APPLICATION_JSON)
                            .requestAttr("userId", testUserId)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FAILED"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }

        @Test
        void enrichPlace_withGooglePlaceId_passedToService() throws Exception {
            PlaceEnrichRequest request = validRequest();
            request.setGooglePlaceId("ChIJN1t_tDeuEmsRUsoyG83frY4");
            when(placeEnrichmentService.enrichPlaceSync(any(), anyDouble(), anyDouble(), eq("ChIJN1t_tDeuEmsRUsoyG83frY4"), any()))
                    .thenReturn(EnrichmentResult.cached(sampleData()));

            mockMvc.perform(post("/places/enrich")
                            .contentType(MediaType.APPLICATION_JSON)
                            .requestAttr("userId", testUserId)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            verify(placeEnrichmentService).enrichPlaceSync(
                    eq("Sushi Nakazawa"), eq(40.7295), eq(-74.0028),
                    eq("ChIJN1t_tDeuEmsRUsoyG83frY4"), isNull());
        }

        @Test
        void enrichPlace_withoutGooglePlaceId_passedAsNull() throws Exception {
            when(placeEnrichmentService.enrichPlaceSync(any(), anyDouble(), anyDouble(), isNull(), isNull()))
                    .thenReturn(EnrichmentResult.enriched(sampleData()));

            mockMvc.perform(post("/places/enrich")
                            .contentType(MediaType.APPLICATION_JSON)
                            .requestAttr("userId", testUserId)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isOk());

            verify(placeEnrichmentService).enrichPlaceSync(
                    eq("Sushi Nakazawa"), eq(40.7295), eq(-74.0028), isNull(), isNull());
        }
    }

    // ===== VALIDATION ERRORS =====

    @Nested
    class ValidationTests {

        @Test
        void enrichPlace_missingName_returns400() throws Exception {
            PlaceEnrichRequest request = new PlaceEnrichRequest();
            request.setLatitude(40.7295);
            request.setLongitude(-74.0028);

            mockMvc.perform(post("/places/enrich")
                            .contentType(MediaType.APPLICATION_JSON)
                            .requestAttr("userId", testUserId)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(placeEnrichmentService);
        }

        @Test
        void enrichPlace_missingLatitude_returns400() throws Exception {
            PlaceEnrichRequest request = new PlaceEnrichRequest();
            request.setName("Sushi Nakazawa");
            request.setLongitude(-74.0028);

            mockMvc.perform(post("/places/enrich")
                            .contentType(MediaType.APPLICATION_JSON)
                            .requestAttr("userId", testUserId)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(placeEnrichmentService);
        }

        @Test
        void enrichPlace_missingLongitude_returns400() throws Exception {
            PlaceEnrichRequest request = new PlaceEnrichRequest();
            request.setName("Sushi Nakazawa");
            request.setLatitude(40.7295);

            mockMvc.perform(post("/places/enrich")
                            .contentType(MediaType.APPLICATION_JSON)
                            .requestAttr("userId", testUserId)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(placeEnrichmentService);
        }
    }

    // ===== AUTHENTICATION =====

    @Test
    void enrichPlace_noAuth_returnsForbidden() throws Exception {
        // Without userId attribute, extractUserId throws UnauthorizedException → 403
        mockMvc.perform(post("/places/enrich")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

        verifyNoInteractions(placeEnrichmentService);
    }
}
