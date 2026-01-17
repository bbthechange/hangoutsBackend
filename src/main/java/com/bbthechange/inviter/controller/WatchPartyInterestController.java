package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.watchparty.SetSeriesInterestRequest;
import com.bbthechange.inviter.service.WatchPartyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for watch party series interest operations.
 *
 * This is a separate controller because the /watch-parties/{seriesId}/interest endpoint
 * doesn't fit under the existing /groups/{groupId}/watch-parties pattern.
 */
@RestController
@RequestMapping("/watch-parties")
@Validated
public class WatchPartyInterestController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(WatchPartyInterestController.class);

    private final WatchPartyService watchPartyService;

    @Autowired
    public WatchPartyInterestController(WatchPartyService watchPartyService) {
        this.watchPartyService = watchPartyService;
    }

    /**
     * Set series-level interest for a watch party.
     *
     * User can express GOING, INTERESTED, or NOT_GOING interest in the entire series.
     * The interest level is stored on the SeriesPointer and visible to all group members.
     *
     * Authorization: User must be a member of a group that the watch party belongs to.
     *
     * @param seriesId The watch party series ID (UUID format)
     * @param request Request body with level: "GOING", "INTERESTED", or "NOT_GOING"
     * @param httpRequest HTTP request for user ID extraction
     * @return 200 OK on success
     */
    @PostMapping("/{seriesId}/interest")
    public ResponseEntity<Void> setSeriesInterest(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid series ID format") String seriesId,
            @Valid @RequestBody SetSeriesInterestRequest request,
            HttpServletRequest httpRequest) {

        String userId = extractUserId(httpRequest);
        logger.info("User {} setting interest {} on watch party series {}", userId, request.getLevel(), seriesId);

        watchPartyService.setUserInterest(seriesId, request.getLevel(), userId);

        return ResponseEntity.ok().build();
    }
}
