package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.EnrichmentResult;
import com.bbthechange.inviter.dto.PlaceEnrichRequest;
import com.bbthechange.inviter.dto.PlaceEnrichResponse;
import com.bbthechange.inviter.service.PlaceEnrichmentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for synchronous place enrichment.
 * POST /places/enrich — blocks until enrichment completes (or fails/times out).
 * Requires JWT authentication.
 */
@RestController
@RequestMapping("/places")
public class PlaceEnrichmentController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(PlaceEnrichmentController.class);

    private final PlaceEnrichmentService placeEnrichmentService;

    @Autowired
    public PlaceEnrichmentController(PlaceEnrichmentService placeEnrichmentService) {
        this.placeEnrichmentService = placeEnrichmentService;
    }

    /**
     * Synchronously enrich a place. Cache lookup first; falls back to full Google API pipeline.
     * POST /places/enrich
     *
     * @return 200 with status CACHED | ENRICHED | FAILED and optional enrichment data
     */
    @PostMapping("/enrich")
    public ResponseEntity<PlaceEnrichResponse> enrichPlace(
            @Valid @RequestBody PlaceEnrichRequest request,
            HttpServletRequest httpRequest) {

        String userId = extractUserId(httpRequest);
        logger.debug("Place enrichment requested by user: {} for place: {}", userId, request.getName());

        EnrichmentResult result = placeEnrichmentService.enrichPlaceSync(
                request.getName(),
                request.getLatitude(),
                request.getLongitude(),
                request.getGooglePlaceId(),
                request.getApplePlaceId()
        );

        return ResponseEntity.ok(PlaceEnrichResponse.from(result));
    }
}
