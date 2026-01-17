package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.watchparty.CreateWatchPartyRequest;
import com.bbthechange.inviter.dto.watchparty.WatchPartyDetailResponse;
import com.bbthechange.inviter.dto.watchparty.WatchPartyResponse;
import com.bbthechange.inviter.service.WatchPartyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * REST controller for managing TV Watch Party series.
 * Watch parties are event series that track TV seasons and auto-create hangouts for episodes.
 */
@RestController
@RequestMapping("/groups/{groupId}/watch-parties")
public class WatchPartyController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(WatchPartyController.class);

    private final WatchPartyService watchPartyService;

    @Autowired
    public WatchPartyController(WatchPartyService watchPartyService) {
        this.watchPartyService = watchPartyService;
    }

    /**
     * Create a new watch party series for a group.
     *
     * Phase 2: Episodes are provided directly in the request.
     * Phase 3: Episodes will be fetched from TVMaze automatically.
     *
     * @param groupId The group to create the watch party in
     * @param request Watch party creation details including episodes
     * @return The created watch party with series ID and hangouts
     */
    @PostMapping
    public ResponseEntity<WatchPartyResponse> createWatchParty(
            @PathVariable String groupId,
            @Valid @RequestBody CreateWatchPartyRequest request,
            HttpServletRequest httpRequest) {

        String requestingUserId = extractUserId(httpRequest);
        logger.info("Creating watch party for show {} season {} in group {} by user {}",
                request.getShowId(), request.getSeasonNumber(), groupId, requestingUserId);

        WatchPartyResponse response = watchPartyService.createWatchParty(groupId, request, requestingUserId);

        logger.info("Successfully created watch party {} with {} hangouts",
                response.getSeriesId(), response.getHangouts().size());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get detailed information about a watch party series.
     *
     * @param groupId The group the watch party belongs to
     * @param seriesId The watch party series ID
     * @return Detailed watch party information including all hangouts
     */
    @GetMapping("/{seriesId}")
    public ResponseEntity<WatchPartyDetailResponse> getWatchParty(
            @PathVariable String groupId,
            @PathVariable String seriesId,
            HttpServletRequest httpRequest) {

        String requestingUserId = extractUserId(httpRequest);
        logger.info("Getting watch party {} in group {} by user {}", seriesId, groupId, requestingUserId);

        WatchPartyDetailResponse response = watchPartyService.getWatchParty(groupId, seriesId, requestingUserId);

        logger.info("Successfully retrieved watch party {} with {} hangouts",
                seriesId, response.getHangouts().size());

        return ResponseEntity.ok(response);
    }

    /**
     * Delete a watch party series and all its hangouts.
     *
     * Note: The Season record is NOT deleted (other groups may use it).
     *
     * @param groupId The group the watch party belongs to
     * @param seriesId The watch party series ID to delete
     * @return No content on success
     */
    @DeleteMapping("/{seriesId}")
    public ResponseEntity<Void> deleteWatchParty(
            @PathVariable String groupId,
            @PathVariable String seriesId,
            HttpServletRequest httpRequest) {

        String requestingUserId = extractUserId(httpRequest);
        logger.info("Deleting watch party {} in group {} by user {}", seriesId, groupId, requestingUserId);

        watchPartyService.deleteWatchParty(groupId, seriesId, requestingUserId);

        logger.info("Successfully deleted watch party {}", seriesId);

        return ResponseEntity.noContent().build();
    }
}
