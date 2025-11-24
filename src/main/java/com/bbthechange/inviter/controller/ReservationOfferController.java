package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.service.ReservationOfferService;
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
 * REST controller for reservation offer management operations.
 * Handles offer CRUD operations and special transaction endpoints (claim, unclaim, complete).
 *
 * Authorization: All operations require user to be a member of any group associated with the hangout.
 */
@RestController
@RequestMapping("/hangouts/{hangoutId}/reservation-offers")
@Validated
public class ReservationOfferController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(ReservationOfferController.class);

    private final ReservationOfferService reservationOfferService;

    @Autowired
    public ReservationOfferController(ReservationOfferService reservationOfferService) {
        this.reservationOfferService = reservationOfferService;
    }

    /**
     * Create a new reservation offer for a hangout.
     * POST /hangouts/{hangoutId}/reservation-offers
     *
     * @param hangoutId The hangout ID (must be valid UUID format)
     * @param request The offer creation request
     * @param httpRequest HTTP request for user extraction
     * @return 201 Created with the new offer DTO
     */
    @PostMapping
    public ResponseEntity<ReservationOfferDTO> createOffer(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid hangout ID format") String hangoutId,
            @Valid @RequestBody CreateReservationOfferRequest request,
            HttpServletRequest httpRequest) {

        String userId = extractUserId(httpRequest);
        logger.info("Creating reservation offer of type {} for hangout {} by user {}",
                request.getType(), hangoutId, userId);

        ReservationOfferDTO created = reservationOfferService.createOffer(hangoutId, request, userId);

        logger.info("Successfully created reservation offer {} for hangout {} by user {}",
                created.getOfferId(), hangoutId, userId);

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Get all reservation offers for a hangout.
     * GET /hangouts/{hangoutId}/reservation-offers
     *
     * @param hangoutId The hangout ID (must be valid UUID format)
     * @param httpRequest HTTP request for user extraction
     * @return 200 OK with list of offer DTOs
     */
    @GetMapping
    public ResponseEntity<List<ReservationOfferDTO>> getOffers(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid hangout ID format") String hangoutId,
            HttpServletRequest httpRequest) {

        String userId = extractUserId(httpRequest);
        logger.debug("Retrieving reservation offers for hangout {} (requested by user {})",
                     hangoutId, userId);

        List<ReservationOfferDTO> offers = reservationOfferService.getOffers(hangoutId, userId);

        logger.debug("Retrieved {} reservation offers for hangout {}", offers.size(), hangoutId);

        return ResponseEntity.ok(offers);
    }

    /**
     * Get a specific reservation offer by ID.
     * GET /hangouts/{hangoutId}/reservation-offers/{offerId}
     *
     * @param hangoutId The hangout ID (must be valid UUID format)
     * @param offerId The offer ID (must be valid UUID format)
     * @param httpRequest HTTP request for user extraction
     * @return 200 OK with the offer DTO
     */
    @GetMapping("/{offerId}")
    public ResponseEntity<ReservationOfferDTO> getOffer(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid hangout ID format") String hangoutId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid offer ID format") String offerId,
            HttpServletRequest httpRequest) {

        String userId = extractUserId(httpRequest);
        logger.debug("Retrieving reservation offer {} for hangout {} (requested by user {})",
                offerId, hangoutId, userId);

        ReservationOfferDTO offer = reservationOfferService.getOffer(hangoutId, offerId, userId);

        return ResponseEntity.ok(offer);
    }

    /**
     * Update a reservation offer by ID.
     * PUT /hangouts/{hangoutId}/reservation-offers/{offerId}
     *
     * @param hangoutId The hangout ID (must be valid UUID format)
     * @param offerId The offer ID (must be valid UUID format)
     * @param request The offer update request
     * @param httpRequest HTTP request for user extraction
     * @return 200 OK with the updated offer DTO
     */
    @PutMapping("/{offerId}")
    public ResponseEntity<ReservationOfferDTO> updateOffer(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid hangout ID format") String hangoutId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid offer ID format") String offerId,
            @Valid @RequestBody UpdateReservationOfferRequest request,
            HttpServletRequest httpRequest) {

        String userId = extractUserId(httpRequest);
        logger.info("Updating reservation offer {} for hangout {} by user {}",
                offerId, hangoutId, userId);

        ReservationOfferDTO updated = reservationOfferService.updateOffer(hangoutId, offerId, request, userId);

        logger.info("Successfully updated reservation offer {} for hangout {} by user {}",
                offerId, hangoutId, userId);

        return ResponseEntity.ok(updated);
    }

    /**
     * Delete a reservation offer by ID.
     * DELETE /hangouts/{hangoutId}/reservation-offers/{offerId}
     *
     * Authorization: Any group member can delete offers.
     *
     * @param hangoutId The hangout ID (must be valid UUID format)
     * @param offerId The offer ID (must be valid UUID format)
     * @param httpRequest HTTP request for user extraction
     * @return 204 No Content
     */
    @DeleteMapping("/{offerId}")
    public ResponseEntity<Void> deleteOffer(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid hangout ID format") String hangoutId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid offer ID format") String offerId,
            HttpServletRequest httpRequest) {

        String userId = extractUserId(httpRequest);
        logger.info("Deleting reservation offer {} for hangout {} by user {}",
                offerId, hangoutId, userId);

        reservationOfferService.deleteOffer(hangoutId, offerId, userId);

        logger.info("Successfully deleted reservation offer {} for hangout {} by user {}",
                offerId, hangoutId, userId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Complete a reservation offer and convert participations.
     * POST /hangouts/{hangoutId}/reservation-offers/{offerId}/complete
     *
     * Marks the offer as completed and optionally converts TICKET_NEEDED participations
     * to TICKET_PURCHASED (either all or specified participations).
     *
     * Authorization: Any group member can complete offers.
     *
     * @param hangoutId The hangout ID (must be valid UUID format)
     * @param offerId The offer ID (must be valid UUID format)
     * @param request The completion request
     * @param httpRequest HTTP request for user extraction
     * @return 200 OK with the completed offer DTO
     */
    @PostMapping("/{offerId}/complete")
    public ResponseEntity<ReservationOfferDTO> completeOffer(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid hangout ID format") String hangoutId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid offer ID format") String offerId,
            @Valid @RequestBody CompleteReservationOfferRequest request,
            HttpServletRequest httpRequest) {

        String userId = extractUserId(httpRequest);
        logger.info("Completing reservation offer {} for hangout {} by user {}",
                offerId, hangoutId, userId);

        ReservationOfferDTO completed = reservationOfferService.completeOffer(hangoutId, offerId, request, userId);

        logger.info("Successfully completed reservation offer {} for hangout {} by user {}",
                offerId, hangoutId, userId);

        return ResponseEntity.ok(completed);
    }

    /**
     * Claim a spot in a reservation offer.
     * POST /hangouts/{hangoutId}/reservation-offers/{offerId}/claim-spot
     *
     * Atomically increments claimedSpots (with capacity check) and creates a CLAIMED_SPOT participation.
     *
     * Authorization: Any group member can claim spots.
     *
     * @param hangoutId The hangout ID (must be valid UUID format)
     * @param offerId The offer ID (must be valid UUID format)
     * @param httpRequest HTTP request for user extraction
     * @return 201 Created with the CLAIMED_SPOT participation DTO
     */
    @PostMapping("/{offerId}/claim-spot")
    public ResponseEntity<ParticipationDTO> claimSpot(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid hangout ID format") String hangoutId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid offer ID format") String offerId,
            HttpServletRequest httpRequest) {

        String userId = extractUserId(httpRequest);
        logger.info("User {} claiming spot in reservation offer {} for hangout {}",
                userId, offerId, hangoutId);

        ParticipationDTO participation = reservationOfferService.claimSpot(hangoutId, offerId, userId);

        logger.info("Successfully claimed spot for user {} in offer {} for hangout {}",
                userId, offerId, hangoutId);

        return ResponseEntity.status(HttpStatus.CREATED).body(participation);
    }

    /**
     * Unclaim a spot in a reservation offer.
     * POST /hangouts/{hangoutId}/reservation-offers/{offerId}/unclaim-spot
     *
     * Atomically decrements claimedSpots and deletes the user's CLAIMED_SPOT participation.
     *
     * Authorization: User must have claimed a spot.
     *
     * @param hangoutId The hangout ID (must be valid UUID format)
     * @param offerId The offer ID (must be valid UUID format)
     * @param httpRequest HTTP request for user extraction
     * @return 204 No Content
     */
    @PostMapping("/{offerId}/unclaim-spot")
    public ResponseEntity<Void> unclaimSpot(
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid hangout ID format") String hangoutId,
            @PathVariable @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid offer ID format") String offerId,
            HttpServletRequest httpRequest) {

        String userId = extractUserId(httpRequest);
        logger.info("User {} unclaiming spot in reservation offer {} for hangout {}",
                userId, offerId, hangoutId);

        reservationOfferService.unclaimSpot(hangoutId, offerId, userId);

        logger.info("Successfully unclaimed spot for user {} in offer {} for hangout {}",
                userId, offerId, hangoutId);

        return ResponseEntity.noContent().build();
    }
}
