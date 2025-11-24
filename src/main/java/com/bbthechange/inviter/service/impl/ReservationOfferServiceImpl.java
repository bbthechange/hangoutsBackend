package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.exception.*;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.ParticipationRepository;
import com.bbthechange.inviter.repository.ReservationOfferRepository;
import com.bbthechange.inviter.service.HangoutService;
import com.bbthechange.inviter.service.ReservationOfferService;
import com.bbthechange.inviter.service.UserService;
import com.bbthechange.inviter.util.InviterKeyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of ReservationOfferService for reservation offer management within hangouts.
 * Includes complex transaction logic for claim-spot, unclaim-spot, and complete operations.
 *
 * Authorization: ALL operations require user to be a member of any group associated with the hangout.
 */
@Service
public class ReservationOfferServiceImpl implements ReservationOfferService {

    private static final Logger logger = LoggerFactory.getLogger(ReservationOfferServiceImpl.class);
    private static final String TABLE_NAME = "InviterTable";
    private static final int MAX_RETRIES = 5;

    private final ReservationOfferRepository offerRepository;
    private final ParticipationRepository participationRepository;
    private final HangoutService hangoutService;
    private final UserService userService;
    private final DynamoDbClient dynamoDbClient;

    @Autowired
    public ReservationOfferServiceImpl(
            ReservationOfferRepository offerRepository,
            ParticipationRepository participationRepository,
            HangoutService hangoutService,
            UserService userService,
            DynamoDbClient dynamoDbClient) {
        this.offerRepository = offerRepository;
        this.participationRepository = participationRepository;
        this.hangoutService = hangoutService;
        this.userService = userService;
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    public ReservationOfferDTO createOffer(String hangoutId, CreateReservationOfferRequest request, String userId) {
        logger.info("User {} creating reservation offer of type {} for hangout {}",
                    userId, request.getType(), hangoutId);

        // Verify user has access to hangout (throws UnauthorizedException if not)
        hangoutService.verifyUserCanAccessHangout(hangoutId, userId);

        // Get user info for denormalization
        User user = getUserForDenormalization(userId);

        // Create offer
        String offerId = UUID.randomUUID().toString();
        ReservationOffer offer = new ReservationOffer(hangoutId, offerId, userId, request.getType());
        offer.setBuyDate(request.getBuyDate());
        offer.setSection(request.getSection());
        offer.setCapacity(request.getCapacity());

        // Set status (default to COLLECTING if not specified)
        if (request.getStatus() != null) {
            offer.setStatus(request.getStatus());
        }

        ReservationOffer saved = offerRepository.save(offer);

        logger.info("Successfully created reservation offer {} for hangout {}", offerId, hangoutId);

        return new ReservationOfferDTO(saved, user.getDisplayName(), user.getMainImagePath());
    }

    @Override
    public List<ReservationOfferDTO> getOffers(String hangoutId, String userId) {
        logger.debug("Retrieving all reservation offers for hangout {} (requested by user {})",
                     hangoutId, userId);

        // Verify user has access to hangout (throws UnauthorizedException if not)
        hangoutService.verifyUserCanAccessHangout(hangoutId, userId);

        // Get all offers
        List<ReservationOffer> offers = offerRepository.findByHangoutId(hangoutId);

        // Fetch users individually - can optimize with batch later if needed
        return offers.stream()
                .map(offer -> {
                    User user = getUserForDenormalization(offer.getUserId());
                    return new ReservationOfferDTO(
                            offer,
                            user.getDisplayName(),
                            user.getMainImagePath()
                    );
                })
                .collect(Collectors.toList());
    }

    @Override
    public ReservationOfferDTO getOffer(String hangoutId, String offerId, String userId) {
        logger.debug("Retrieving reservation offer {} for hangout {} (requested by user {})",
                    offerId, hangoutId, userId);

        // Verify user has access to hangout (throws UnauthorizedException if not)
        hangoutService.verifyUserCanAccessHangout(hangoutId, userId);

        // Get offer
        ReservationOffer offer = offerRepository.findById(hangoutId, offerId)
                .orElseThrow(() -> new ReservationOfferNotFoundException(
                        "Reservation offer not found: " + offerId));

        // Get user info for denormalization
        User user = getUserForDenormalization(offer.getUserId());

        return new ReservationOfferDTO(offer, user.getDisplayName(), user.getMainImagePath());
    }

    @Override
    public ReservationOfferDTO updateOffer(String hangoutId, String offerId,
                                          UpdateReservationOfferRequest request, String userId) {
        logger.info("User {} updating reservation offer {} for hangout {}",
                    userId, offerId, hangoutId);

        // Verify request has updates
        if (!request.hasUpdates()) {
            throw new IllegalArgumentException("No updates provided");
        }

        // Verify user has access to hangout (throws UnauthorizedException if not)
        hangoutService.verifyUserCanAccessHangout(hangoutId, userId);

        // Get existing offer
        ReservationOffer offer = offerRepository.findById(hangoutId, offerId)
                .orElseThrow(() -> new ReservationOfferNotFoundException(
                        "Reservation offer not found: " + offerId));

        // Authorization: any group member can edit
        boolean isCreator = offer.getUserId().equals(userId);
        if (!isCreator) {
            logger.debug("User {} editing reservation offer {} created by user {}",
                        userId, offerId, offer.getUserId());
        }

        // Apply updates
        if (request.getBuyDate() != null) {
            offer.setBuyDate(request.getBuyDate());
        }
        if (request.getSection() != null) {
            offer.setSection(request.getSection());
        }
        if (request.getCapacity() != null) {
            offer.setCapacity(request.getCapacity());
        }
        if (request.getStatus() != null) {
            offer.setStatus(request.getStatus());
        }

        ReservationOffer updated = offerRepository.save(offer);

        // Get user info for denormalization
        User user = getUserForDenormalization(offer.getUserId());

        logger.info("Successfully updated reservation offer {} for hangout {}", offerId, hangoutId);

        return new ReservationOfferDTO(updated, user.getDisplayName(), user.getMainImagePath());
    }

    @Override
    public void deleteOffer(String hangoutId, String offerId, String userId) {
        logger.info("User {} deleting reservation offer {} for hangout {}",
                    userId, offerId, hangoutId);

        // Verify user has access to hangout (throws UnauthorizedException if not)
        hangoutService.verifyUserCanAccessHangout(hangoutId, userId);

        // Delete the offer directly - any group member can delete
        // Note: This is idempotent - succeeds even if offer doesn't exist
        offerRepository.delete(hangoutId, offerId);

        logger.info("Successfully deleted reservation offer {} for hangout {}", offerId, hangoutId);
    }

    @Override
    public ReservationOfferDTO completeOffer(String hangoutId, String offerId,
                                            CompleteReservationOfferRequest request, String userId) {
        logger.info("User {} completing reservation offer {} for hangout {}",
                    userId, offerId, hangoutId);

        // Verify user has access to hangout (throws UnauthorizedException if not)
        // Authorization: any group member can complete offers
        hangoutService.verifyUserCanAccessHangout(hangoutId, userId);

        // Get current offer
        ReservationOffer offer = offerRepository.findById(hangoutId, offerId)
                .orElseThrow(() -> new ReservationOfferNotFoundException(
                        "Reservation offer not found: " + offerId));

        // Get participations to convert based on convertAll flag
        List<Participation> participationsToConvert;

        if (Boolean.TRUE.equals(request.getConvertAll())) {
            // Convert ALL TICKET_NEEDED participations linked to this offer
            participationsToConvert = participationRepository.findByOfferId(hangoutId, offerId)
                    .stream()
                    .filter(p -> p.getType() == ParticipationType.TICKET_NEEDED)
                    .collect(Collectors.toList());

            logger.info("Converting all {} TICKET_NEEDED participations for offer {}",
                       participationsToConvert.size(), offerId);
        } else {
            // Convert only specified participation IDs
            if (request.getParticipationIds() == null || request.getParticipationIds().isEmpty()) {
                throw new ValidationException("participationIds required when convertAll=false");
            }

            participationsToConvert = request.getParticipationIds().stream()
                    .map(id -> participationRepository.findById(hangoutId, id))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .filter(p -> p.getType() == ParticipationType.TICKET_NEEDED)
                    .collect(Collectors.toList());

            logger.info("Converting {} specified participations for offer {}",
                       participationsToConvert.size(), offerId);
        }

        // Update offer first
        offer.setStatus(OfferStatus.COMPLETED);
        offer.setCompletedDate(System.currentTimeMillis());
        offer.setTicketCount(request.getTicketCount());
        offer.setTotalPrice(request.getTotalPrice());
        offerRepository.save(offer);

        // Batch update participations using DynamoDB TransactWriteItems (90 per batch, limit is 100)
        int batchSize = 90;
        for (int i = 0; i < participationsToConvert.size(); i += batchSize) {
            List<Participation> batch = participationsToConvert.subList(
                    i, Math.min(i + batchSize, participationsToConvert.size())
            );

            // Build transaction items for this batch
            List<TransactWriteItem> transactionItems = new ArrayList<>();
            long now = System.currentTimeMillis();

            for (Participation participation : batch) {
                Map<String, AttributeValue> key = new HashMap<>();
                key.put("pk", AttributeValue.builder().s(participation.getPk()).build());
                key.put("sk", AttributeValue.builder().s(participation.getSk()).build());

                Map<String, AttributeValue> values = new HashMap<>();
                values.put(":purchased", AttributeValue.builder().s("TICKET_PURCHASED").build());
                values.put(":now", AttributeValue.builder().n(String.valueOf(now)).build());

                transactionItems.add(TransactWriteItem.builder()
                        .update(Update.builder()
                                .tableName(TABLE_NAME)
                                .key(key)
                                .updateExpression("SET #type = :purchased, updatedAt = :now")
                                .expressionAttributeNames(Map.of("#type", "type"))
                                .expressionAttributeValues(values)
                                .build())
                        .build());
            }

            // Execute batch transaction
            try {
                dynamoDbClient.transactWriteItems(TransactWriteItemsRequest.builder()
                        .transactItems(transactionItems)
                        .build());
                logger.debug("Completed batch transaction for {} participations", batch.size());
            } catch (TransactionCanceledException e) {
                logger.error("Transaction failed for participation batch: {}", e.getMessage());
                throw new RuntimeException("Failed to update participations in batch", e);
            }
        }

        // Get user info for denormalization
        User user = getUserForDenormalization(offer.getUserId());

        logger.info("Successfully completed reservation offer {} for hangout {} ({} participations converted)",
                   offerId, hangoutId, participationsToConvert.size());

        return new ReservationOfferDTO(offer, user.getDisplayName(), user.getMainImagePath());
    }

    @Override
    public ParticipationDTO claimSpot(String hangoutId, String offerId, String userId) {
        logger.info("User {} claiming spot in reservation offer {} for hangout {}",
                    userId, offerId, hangoutId);

        // Verify user has access to hangout (throws UnauthorizedException if not)
        hangoutService.verifyUserCanAccessHangout(hangoutId, userId);

        // Retry loop for version conflicts
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                // Get current offer (fresh on each retry)
                ReservationOffer offer = offerRepository.findById(hangoutId, offerId)
                        .orElseThrow(() -> new ReservationOfferNotFoundException(
                                "Reservation offer not found: " + offerId));

                // CRITICAL: Validate capacity is not null
                if (offer.getCapacity() == null) {
                    throw new IllegalOperationException(
                            "Cannot claim spot on offer with unlimited capacity. " +
                            "Create a participation directly instead."
                    );
                }

                // Check if capacity is already exceeded (pre-transaction check)
                if (offer.getClaimedSpots() >= offer.getCapacity()) {
                    throw new CapacityExceededException(
                            String.format("This reservation is full (%d/%d spots claimed)",
                                         offer.getClaimedSpots(), offer.getCapacity())
                    );
                }

                // Create participation that will be created
                String participationId = UUID.randomUUID().toString();
                Participation participation = new Participation(hangoutId, participationId, userId, ParticipationType.CLAIMED_SPOT);
                participation.setReservationOfferId(offerId);

                // Build transaction: conditional increment + create participation
                List<TransactWriteItem> items = new ArrayList<>();

                // 1. Update ReservationOffer: increment claimedSpots with capacity check
                Map<String, AttributeValue> offerKey = new HashMap<>();
                offerKey.put("pk", AttributeValue.builder().s(InviterKeyFactory.getEventPk(hangoutId)).build());
                offerKey.put("sk", AttributeValue.builder().s(InviterKeyFactory.getReservationOfferSk(offerId)).build());

                Map<String, AttributeValue> updateValues = new HashMap<>();
                updateValues.put(":one", AttributeValue.builder().n("1").build());
                updateValues.put(":capacity", AttributeValue.builder().n(String.valueOf(offer.getCapacity())).build());
                updateValues.put(":expectedVersion", AttributeValue.builder().n(String.valueOf(offer.getVersion())).build());
                updateValues.put(":now", AttributeValue.builder().n(String.valueOf(System.currentTimeMillis())).build());

                items.add(TransactWriteItem.builder()
                        .update(Update.builder()
                                .tableName(TABLE_NAME)
                                .key(offerKey)
                                .updateExpression("SET claimedSpots = claimedSpots + :one, #ver = #ver + :one, updatedAt = :now")
                                .conditionExpression("attribute_exists(pk) AND claimedSpots < :capacity AND #ver = :expectedVersion")
                                .expressionAttributeNames(Map.of("#ver", "version"))
                                .expressionAttributeValues(updateValues)
                                .build())
                        .build());

                // 2. Put Participation (CLAIMED_SPOT)
                Map<String, AttributeValue> participationItem = new HashMap<>();
                participationItem.put("pk", AttributeValue.builder().s(participation.getPk()).build());
                participationItem.put("sk", AttributeValue.builder().s(participation.getSk()).build());
                participationItem.put("itemType", AttributeValue.builder().s(InviterKeyFactory.PARTICIPATION_PREFIX).build());
                participationItem.put("participationId", AttributeValue.builder().s(participationId).build());
                participationItem.put("userId", AttributeValue.builder().s(userId).build());
                participationItem.put("type", AttributeValue.builder().s("CLAIMED_SPOT").build());
                participationItem.put("reservationOfferId", AttributeValue.builder().s(offerId).build());
                participationItem.put("createdAt", AttributeValue.builder().n(String.valueOf(System.currentTimeMillis())).build());
                participationItem.put("updatedAt", AttributeValue.builder().n(String.valueOf(System.currentTimeMillis())).build());

                items.add(TransactWriteItem.builder()
                        .put(Put.builder()
                                .tableName(TABLE_NAME)
                                .item(participationItem)
                                .build())
                        .build());

                // Execute transaction
                dynamoDbClient.transactWriteItems(TransactWriteItemsRequest.builder()
                        .transactItems(items)
                        .build());

                logger.info("Successfully claimed spot for user {} in offer {} (attempt {})",
                           userId, offerId, attempt);

                // Get user info for denormalization
                User user = getUserForDenormalization(userId);
                return new ParticipationDTO(participation, user.getDisplayName(), user.getMainImagePath());

            } catch (TransactionCanceledException e) {
                // Parse the cancellation reason
                String message = e.getMessage();
                logger.debug("Transaction attempt {} failed: {}", attempt, message);

                // Check if it's a capacity issue by refetching the offer
                ReservationOffer currentOffer = offerRepository.findById(hangoutId, offerId)
                        .orElseThrow(() -> new ReservationOfferNotFoundException(
                                "Reservation offer not found: " + offerId));

                if (currentOffer.getClaimedSpots() >= currentOffer.getCapacity()) {
                    // Capacity is actually exceeded
                    throw new CapacityExceededException(
                            String.format("This reservation is full (%d/%d spots claimed)",
                                         currentOffer.getClaimedSpots(), currentOffer.getCapacity())
                    );
                }

                // It's a version conflict - retry
                if (attempt < MAX_RETRIES) {
                    logger.debug("Retrying claim-spot due to version conflict (attempt {}/{})",
                                attempt, MAX_RETRIES);
                    continue;
                }

                // Max retries exceeded
                logger.warn("Max retries exceeded for claim-spot after {} attempts", MAX_RETRIES);
                throw new RuntimeException("Failed to claim spot after " + MAX_RETRIES + " attempts due to concurrent modifications");
            }
        }

        // Should never reach here
        throw new RuntimeException("Unexpected error in claim-spot");
    }

    @Override
    public void unclaimSpot(String hangoutId, String offerId, String userId) {
        logger.info("User {} unclaiming spot in reservation offer {} for hangout {}",
                    userId, offerId, hangoutId);

        // Verify user has access to hangout (throws UnauthorizedException if not)
        hangoutService.verifyUserCanAccessHangout(hangoutId, userId);

        // Retry loop for version conflicts
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                // Get current offer (fresh on each retry)
                ReservationOffer offer = offerRepository.findById(hangoutId, offerId)
                        .orElseThrow(() -> new ReservationOfferNotFoundException(
                                "Reservation offer not found: " + offerId));

                // Find user's CLAIMED_SPOT participation for this offer
                Participation claimedSpot = participationRepository.findByOfferId(hangoutId, offerId)
                        .stream()
                        .filter(p -> p.getUserId().equals(userId) && p.getType() == ParticipationType.CLAIMED_SPOT)
                        .findFirst()
                        .orElseThrow(() -> new NotFoundException(
                                "You have not claimed a spot in this reservation"));

                // Build transaction: conditional decrement + delete participation
                List<TransactWriteItem> items = new ArrayList<>();

                // 1. Update ReservationOffer: decrement claimedSpots
                Map<String, AttributeValue> offerKey = new HashMap<>();
                offerKey.put("pk", AttributeValue.builder().s(InviterKeyFactory.getEventPk(hangoutId)).build());
                offerKey.put("sk", AttributeValue.builder().s(InviterKeyFactory.getReservationOfferSk(offerId)).build());

                Map<String, AttributeValue> updateValues = new HashMap<>();
                updateValues.put(":one", AttributeValue.builder().n("1").build());
                updateValues.put(":expectedVersion", AttributeValue.builder().n(String.valueOf(offer.getVersion())).build());
                updateValues.put(":now", AttributeValue.builder().n(String.valueOf(System.currentTimeMillis())).build());

                items.add(TransactWriteItem.builder()
                        .update(Update.builder()
                                .tableName(TABLE_NAME)
                                .key(offerKey)
                                .updateExpression("SET claimedSpots = claimedSpots - :one, #ver = #ver + :one, updatedAt = :now")
                                .conditionExpression("attribute_exists(pk) AND #ver = :expectedVersion")
                                .expressionAttributeNames(Map.of("#ver", "version"))
                                .expressionAttributeValues(updateValues)
                                .build())
                        .build());

                // 2. Delete Participation (CLAIMED_SPOT)
                Map<String, AttributeValue> participationKey = new HashMap<>();
                participationKey.put("pk", AttributeValue.builder().s(claimedSpot.getPk()).build());
                participationKey.put("sk", AttributeValue.builder().s(claimedSpot.getSk()).build());

                items.add(TransactWriteItem.builder()
                        .delete(Delete.builder()
                                .tableName(TABLE_NAME)
                                .key(participationKey)
                                .build())
                        .build());

                // Execute transaction
                dynamoDbClient.transactWriteItems(TransactWriteItemsRequest.builder()
                        .transactItems(items)
                        .build());

                logger.info("Successfully unclaimed spot for user {} in offer {} (attempt {})",
                           userId, offerId, attempt);
                return;

            } catch (TransactionCanceledException e) {
                // Version conflict - retry
                logger.debug("Transaction attempt {} failed: {}", attempt, e.getMessage());

                if (attempt < MAX_RETRIES) {
                    logger.debug("Retrying unclaim-spot due to version conflict (attempt {}/{})",
                                attempt, MAX_RETRIES);
                    continue;
                }

                // Max retries exceeded
                logger.warn("Max retries exceeded for unclaim-spot after {} attempts", MAX_RETRIES);
                throw new RuntimeException("Failed to unclaim spot after " + MAX_RETRIES + " attempts due to concurrent modifications");
            }
        }
    }

    /**
     * Get user for denormalization with proper error handling.
     */
    private User getUserForDenormalization(String userId) {
        return userService.getUserById(UUID.fromString(userId))
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
    }
}
