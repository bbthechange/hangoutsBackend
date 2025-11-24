package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.CreateParticipationRequest;
import com.bbthechange.inviter.dto.ParticipationDTO;
import com.bbthechange.inviter.dto.UpdateParticipationRequest;
import com.bbthechange.inviter.exception.ParticipationNotFoundException;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.exception.UserNotFoundException;
import com.bbthechange.inviter.model.Participation;
import com.bbthechange.inviter.model.User;
import com.bbthechange.inviter.repository.ParticipationRepository;
import com.bbthechange.inviter.service.HangoutService;
import com.bbthechange.inviter.service.ParticipationService;
import com.bbthechange.inviter.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
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

    @Autowired
    public ParticipationServiceImpl(
            ParticipationRepository participationRepository,
            HangoutService hangoutService,
            UserService userService) {
        this.participationRepository = participationRepository;
        this.hangoutService = hangoutService;
        this.userService = userService;
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

        logger.info("Successfully deleted participation {} for hangout {}", participationId, hangoutId);
    }

    /**
     * Get user for denormalization with proper error handling.
     */
    private User getUserForDenormalization(String userId) {
        return userService.getUserById(UUID.fromString(userId))
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
    }
}
