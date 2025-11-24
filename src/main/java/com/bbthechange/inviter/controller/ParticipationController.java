package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.CreateParticipationRequest;
import com.bbthechange.inviter.dto.ParticipationDTO;
import com.bbthechange.inviter.dto.UpdateParticipationRequest;
import com.bbthechange.inviter.service.ParticipationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for participation management operations.
 * Handles participation CRUD operations for hangout tickets and reservations.
 *
 * Authorization: All operations require user to be a member of any group associated with the hangout.
 */
@RestController
@RequestMapping("/hangouts/{hangoutId}/participations")
@Validated
public class ParticipationController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(ParticipationController.class);

    private final ParticipationService participationService;

    @Autowired
    public ParticipationController(ParticipationService participationService) {
        this.participationService = participationService;
    }

    /**
     * Create a new participation for a hangout.
     * POST /hangouts/{hangoutId}/participations
     *
     * @param hangoutId The hangout ID (must be valid UUID format)
     * @param request The participation creation request
     * @param httpRequest HTTP request for user extraction
     * @return 201 Created with the new participation DTO
     */
    @PostMapping
    public ResponseEntity<ParticipationDTO> createParticipation(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid hangout ID format") String hangoutId,
            @Valid @RequestBody CreateParticipationRequest request,
            HttpServletRequest httpRequest) {

        String userId = extractUserId(httpRequest);
        logger.info("Creating participation of type {} for hangout {} by user {}",
                request.getType(), hangoutId, userId);

        ParticipationDTO created = participationService.createParticipation(hangoutId, request, userId);

        logger.info("Successfully created participation {} for hangout {} by user {}",
                created.getParticipationId(), hangoutId, userId);

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Get all participations for a hangout.
     * GET /hangouts/{hangoutId}/participations
     *
     * @param hangoutId The hangout ID (must be valid UUID format)
     * @param httpRequest HTTP request for user extraction
     * @return 200 OK with list of participation DTOs
     */
    @GetMapping
    public ResponseEntity<List<ParticipationDTO>> getParticipations(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid hangout ID format") String hangoutId,
            HttpServletRequest httpRequest) {

        String userId = extractUserId(httpRequest);
        logger.debug("Retrieving participations for hangout {} (requested by user {})", hangoutId, userId);

        List<ParticipationDTO> participations = participationService.getParticipations(hangoutId, userId);

        logger.debug("Retrieved {} participations for hangout {}", participations.size(), hangoutId);

        return ResponseEntity.ok(participations);
    }

    /**
     * Get a specific participation by ID.
     * GET /hangouts/{hangoutId}/participations/{participationId}
     *
     * @param hangoutId The hangout ID (must be valid UUID format)
     * @param participationId The participation ID (must be valid UUID format)
     * @param httpRequest HTTP request for user extraction
     * @return 200 OK with the participation DTO
     */
    @GetMapping("/{participationId}")
    public ResponseEntity<ParticipationDTO> getParticipation(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid hangout ID format") String hangoutId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid participation ID format") String participationId,
            HttpServletRequest httpRequest) {

        String userId = extractUserId(httpRequest);
        logger.debug("Retrieving participation {} for hangout {} (requested by user {})",
                participationId, hangoutId, userId);

        ParticipationDTO participation = participationService.getParticipation(hangoutId, participationId, userId);

        return ResponseEntity.ok(participation);
    }

    /**
     * Update a participation by ID.
     * PUT /hangouts/{hangoutId}/participations/{participationId}
     *
     * @param hangoutId The hangout ID (must be valid UUID format)
     * @param participationId The participation ID (must be valid UUID format)
     * @param request The participation update request
     * @param httpRequest HTTP request for user extraction
     * @return 200 OK with the updated participation DTO
     */
    @PutMapping("/{participationId}")
    public ResponseEntity<ParticipationDTO> updateParticipation(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid hangout ID format") String hangoutId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid participation ID format") String participationId,
            @Valid @RequestBody UpdateParticipationRequest request,
            HttpServletRequest httpRequest) {

        String userId = extractUserId(httpRequest);
        logger.info("Updating participation {} for hangout {} by user {}",
                participationId, hangoutId, userId);

        ParticipationDTO updated = participationService.updateParticipation(hangoutId, participationId, request, userId);

        logger.info("Successfully updated participation {} for hangout {} by user {}",
                participationId, hangoutId, userId);

        return ResponseEntity.ok(updated);
    }

    /**
     * Delete a participation by ID.
     * DELETE /hangouts/{hangoutId}/participations/{participationId}
     *
     * Authorization: Any group member can delete participations.
     * Note: For CLAIMED_SPOT participations, use the dedicated unclaim endpoint instead.
     *
     * @param hangoutId The hangout ID (must be valid UUID format)
     * @param participationId The participation ID (must be valid UUID format)
     * @param httpRequest HTTP request for user extraction
     * @return 204 No Content
     */
    @DeleteMapping("/{participationId}")
    public ResponseEntity<Void> deleteParticipation(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid hangout ID format") String hangoutId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid participation ID format") String participationId,
            HttpServletRequest httpRequest) {

        String userId = extractUserId(httpRequest);
        logger.info("Deleting participation {} for hangout {} by user {}",
                participationId, hangoutId, userId);

        participationService.deleteParticipation(hangoutId, participationId, userId);

        logger.info("Successfully deleted participation {} for hangout {} by user {}",
                participationId, hangoutId, userId);

        return ResponseEntity.noContent().build();
    }
}
