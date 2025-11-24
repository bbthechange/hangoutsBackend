package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.CreateParticipationRequest;
import com.bbthechange.inviter.dto.HangoutDetailDTO;
import com.bbthechange.inviter.dto.ParticipationDTO;
import com.bbthechange.inviter.dto.ParticipationSummaryDTO;
import com.bbthechange.inviter.dto.ReservationOfferDTO;
import com.bbthechange.inviter.dto.UpdateParticipationRequest;
import com.bbthechange.inviter.dto.UserSummary;
import com.bbthechange.inviter.exception.ParticipationNotFoundException;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.exception.UserNotFoundException;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.model.Participation;
import com.bbthechange.inviter.model.ParticipationType;
import com.bbthechange.inviter.model.User;
import com.bbthechange.inviter.repository.ParticipationRepository;
import com.bbthechange.inviter.service.GroupTimestampService;
import com.bbthechange.inviter.service.HangoutService;
import com.bbthechange.inviter.service.ParticipationService;
import com.bbthechange.inviter.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of ParticipationService for participation management within hangouts.
 */
@Service
public class ParticipationServiceImpl implements ParticipationService {

    private static final Logger logger = LoggerFactory.getLogger(ParticipationServiceImpl.class);

    private final ParticipationRepository participationRepository;
    private final HangoutService hangoutService;
    private final UserService userService;
    private final PointerUpdateService pointerUpdateService;
    private final GroupTimestampService groupTimestampService;

    @Autowired
    public ParticipationServiceImpl(
            ParticipationRepository participationRepository,
            HangoutService hangoutService,
            UserService userService,
            PointerUpdateService pointerUpdateService,
            GroupTimestampService groupTimestampService) {
        this.participationRepository = participationRepository;
        this.hangoutService = hangoutService;
        this.userService = userService;
        this.pointerUpdateService = pointerUpdateService;
        this.groupTimestampService = groupTimestampService;
    }

    @Override
    public ParticipationDTO createParticipation(String hangoutId, CreateParticipationRequest request, String userId) {
        logger.info("User {} creating participation of type {} for hangout {}",
                    userId, request.getType(), hangoutId);

        // Verify user has access to hangout (throws UnauthorizedException if not)
        hangoutService.verifyUserCanAccessHangout(hangoutId, userId);

        // Get user info for denormalization
        User user = getUserForDenormalization(userId);

        // Create participation
        String participationId = UUID.randomUUID().toString();
        Participation participation = new Participation(hangoutId, participationId, userId, request.getType());
        participation.setSection(request.getSection());
        participation.setSeat(request.getSeat());
        participation.setReservationOfferId(request.getReservationOfferId());

        Participation saved = participationRepository.save(participation);

        // CRITICAL: Sync pointers after canonical update
        updatePointersWithParticipationData(hangoutId, userId);

        logger.info("Successfully created participation {} for hangout {}", participationId, hangoutId);

        return new ParticipationDTO(saved, user.getDisplayName(), user.getMainImagePath());
    }

    @Override
    public List<ParticipationDTO> getParticipations(String hangoutId, String userId) {
        logger.debug("Retrieving all participations for hangout {} (requested by user {})", hangoutId, userId);

        // Verify user has access to hangout (throws UnauthorizedException if not)
        hangoutService.verifyUserCanAccessHangout(hangoutId, userId);

        // Get all participations
        List<Participation> participations = participationRepository.findByHangoutId(hangoutId);

        // Get unique user IDs for batch lookup
        List<String> userIds = participations.stream()
                .map(Participation::getUserId)
                .distinct()
                .toList();

        // Batch fetch users (optimization for N+1 query prevention)
        // For now, fetch individually - can optimize with batch later if needed
        return participations.stream()
                .map(participation -> {
                    User user = getUserForDenormalization(participation.getUserId());
                    return new ParticipationDTO(
                            participation,
                            user.getDisplayName(),
                            user.getMainImagePath()
                    );
                })
                .collect(Collectors.toList());
    }

    @Override
    public ParticipationDTO getParticipation(String hangoutId, String participationId, String userId) {
        logger.debug("Retrieving participation {} for hangout {} (requested by user {})",
                    participationId, hangoutId, userId);

        // Verify user has access to hangout (throws UnauthorizedException if not)
        hangoutService.verifyUserCanAccessHangout(hangoutId, userId);

        // Get participation
        Participation participation = participationRepository.findById(hangoutId, participationId)
                .orElseThrow(() -> new ParticipationNotFoundException(
                        "Participation not found: " + participationId));

        // Get user info for denormalization
        User user = getUserForDenormalization(participation.getUserId());

        return new ParticipationDTO(participation, user.getDisplayName(), user.getMainImagePath());
    }

    @Override
    public ParticipationDTO updateParticipation(String hangoutId, String participationId,
                                                UpdateParticipationRequest request, String userId) {
        logger.info("User {} updating participation {} for hangout {}",
                    userId, participationId, hangoutId);

        // Verify request has updates
        if (!request.hasUpdates()) {
            throw new IllegalArgumentException("No updates provided");
        }

        // Verify user has access to hangout (throws UnauthorizedException if not)
        hangoutService.verifyUserCanAccessHangout(hangoutId, userId);

        // Get existing participation
        Participation participation = participationRepository.findById(hangoutId, participationId)
                .orElseThrow(() -> new ParticipationNotFoundException(
                        "Participation not found: " + participationId));

        // Authorization: participation owner OR any group member can edit
        // Since user has access to hangout, they're a group member, so they can edit
        boolean isOwner = participation.getUserId().equals(userId);
        if (!isOwner) {
            // Any group member can edit participations (following existing carpool pattern)
            logger.debug("User {} editing participation {} owned by user {}",
                        userId, participationId, participation.getUserId());
        }

        // Apply updates
        if (request.getType() != null) {
            participation.setType(request.getType());
        }
        if (request.getSection() != null) {
            participation.setSection(request.getSection());
        }
        if (request.getSeat() != null) {
            participation.setSeat(request.getSeat());
        }

        Participation updated = participationRepository.save(participation);

        // CRITICAL: Sync pointers after canonical update
        updatePointersWithParticipationData(hangoutId, userId);

        // Get user info for denormalization
        User user = getUserForDenormalization(participation.getUserId());

        logger.info("Successfully updated participation {} for hangout {}", participationId, hangoutId);

        return new ParticipationDTO(updated, user.getDisplayName(), user.getMainImagePath());
    }

    @Override
    public void deleteParticipation(String hangoutId, String participationId, String userId) {
        logger.info("User {} deleting participation {} for hangout {}",
                    userId, participationId, hangoutId);

        // Verify user has access to hangout (throws UnauthorizedException if not)
        // Since the participation owner must be a group member, if the requesting user
        // has access to the hangout, they can delete any participation
        hangoutService.verifyUserCanAccessHangout(hangoutId, userId);

        // Delete the participation directly
        // Note: This is idempotent - succeeds even if participation doesn't exist
        participationRepository.delete(hangoutId, participationId);

        // CRITICAL: Sync pointers after canonical update
        updatePointersWithParticipationData(hangoutId, userId);

        logger.info("Successfully deleted participation {} for hangout {}", participationId, hangoutId);
    }

    /**
     * Get user for denormalization with proper error handling.
     */
    private User getUserForDenormalization(String userId) {
        return userService.getUserById(UUID.fromString(userId))
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
    }

    /**
     * Update all pointer records with current participation/offer data.
     * Call this after ANY participation/offer operation.
     */
    private void updatePointersWithParticipationData(String hangoutId, String userId) {
        // 1. Get all hangout data in one call (includes participations and offers with denormalized user info)
        HangoutDetailDTO detail;
        try {
            detail = hangoutService.getHangoutDetail(hangoutId, userId);
            if (detail == null || detail.getHangout() == null) {
                logger.warn("Failed to get hangout detail for pointer update: hangoutId={}", hangoutId);
                return;
            }
        } catch (Exception e) {
            logger.warn("Failed to get hangout detail for pointer update: {}", e.getMessage());
            return;
        }

        Hangout hangout = detail.getHangout();
        List<String> associatedGroups = hangout.getAssociatedGroups();

        if (associatedGroups == null || associatedGroups.isEmpty()) {
            logger.debug("No associated groups for hangout {}, skipping pointer update", hangoutId);
            return;
        }

        // 2. Get canonical data from the detail response (already includes denormalized user info)
        List<ParticipationDTO> participations = detail.getParticipations();
        List<ReservationOfferDTO> offers = detail.getReservationOffers();

        // 3. Build denormalized ParticipationSummaryDTO
        ParticipationSummaryDTO participationSummary = buildParticipationSummary(participations, offers);

        // 4. Update ALL HangoutPointers (one per associated group)
        for (String groupId : associatedGroups) {
            pointerUpdateService.updatePointerWithRetry(groupId, hangoutId, pointer -> {
                // Update participation summary
                pointer.setParticipationSummary(participationSummary);

                // Update ticket fields from canonical hangout
                pointer.setTicketLink(hangout.getTicketLink());
                pointer.setTicketsRequired(hangout.getTicketsRequired());
                pointer.setDiscountCode(hangout.getDiscountCode());

                // Version auto-incremented by @DynamoDbVersionAttribute
            }, "participation/offer data");
        }

        // 5. Update group timestamps for ETag invalidation
        groupTimestampService.updateGroupTimestamps(associatedGroups);
    }

    /**
     * Build ParticipationSummaryDTO from DTOs with denormalized user info.
     */
    private ParticipationSummaryDTO buildParticipationSummary(
        List<ParticipationDTO> participations,
        List<ReservationOfferDTO> offers
    ) {
        ParticipationSummaryDTO summary = new ParticipationSummaryDTO();

        // Group participations by type
        Map<ParticipationType, List<ParticipationDTO>> byType = participations.stream()
            .collect(Collectors.groupingBy(ParticipationDTO::getType));

        // Build user lists for each type (deduplicated by userId)
        summary.setUsersNeedingTickets(
            buildUserList(byType.get(ParticipationType.TICKET_NEEDED))
        );
        summary.setUsersWithTickets(
            buildUserList(byType.get(ParticipationType.TICKET_PURCHASED))
        );
        summary.setUsersWithClaimedSpots(
            buildUserList(byType.get(ParticipationType.CLAIMED_SPOT))
        );

        // Count extras (no user list needed - just count)
        long extraCount = participations.stream()
            .filter(p -> p.getType() == ParticipationType.TICKET_EXTRA)
            .count();
        summary.setExtraTicketCount((int) extraCount);

        // Include ALL reservation offers (already denormalized with user info)
        summary.setReservationOffers(offers);

        return summary;
    }

    /**
     * Build user list from participation DTOs, deduplicated by userId.
     * User info is already denormalized in the DTOs.
     */
    private List<UserSummary> buildUserList(List<ParticipationDTO> participations) {
        if (participations == null || participations.isEmpty()) {
            return new ArrayList<>();
        }

        // Use a map to deduplicate by userId (user with 2 tickets appears once)
        return participations.stream()
            .collect(Collectors.toMap(
                ParticipationDTO::getUserId,  // Key: userId
                p -> new UserSummary(          // Value: UserSummary
                    p.getUserId(),
                    p.getDisplayName(),
                    p.getMainImagePath()
                ),
                (existing, replacement) -> existing  // Keep first if duplicate
            ))
            .values()
            .stream()
            .collect(Collectors.toList());
    }
}
