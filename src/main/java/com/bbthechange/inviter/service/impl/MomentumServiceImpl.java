package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.MomentumDTO;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.AdaptiveNotificationService;
import com.bbthechange.inviter.service.MomentumService;
import com.bbthechange.inviter.service.NotificationService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Implementation of MomentumService.
 *
 * Scoring algorithm:
 *   - GOING RSVP: +3 points each
 *   - INTERESTED RSVP: +1 point each
 *   - Has startTimestamp (time added): +1
 *   - Has location: +1
 *   - Concrete action (ticketsRequired + ticketLink, or carpool riders): instant CONFIRMED
 *   - Recency multiplier: x1.5 if any InterestLevel updated in last 48h
 *   - Time proximity multiplier (if startTimestamp set):
 *       within 48h  -> x1.5
 *       within 7d   -> x1.2
 *       beyond 7d   -> x1.0
 *
 * Dynamic threshold:
 *   threshold = ceil(activeMembers * engagementMultiplier * 0.4)
 *   engagementMultiplier clamped [0.3, 1.0], default 0.6 for new groups
 *
 * Promotion rules:
 *   score >= threshold           -> GAINING_MOMENTUM
 *   score >= threshold*2 + date  -> CONFIRMED (auto)
 *   concrete action              -> CONFIRMED (instant)
 *   Never demote from CONFIRMED
 */
@Service
public class MomentumServiceImpl implements MomentumService {

    private static final Logger logger = LoggerFactory.getLogger(MomentumServiceImpl.class);

    // Recency window: 48 hours in seconds
    private static final long RECENCY_WINDOW_SECONDS = 48L * 3600;
    // Proximity windows in seconds
    private static final long PROXIMITY_48H_SECONDS = 48L * 3600;
    private static final long PROXIMITY_7D_SECONDS = 7L * 24 * 3600;

    // Default engagement multiplier for groups with no history
    private static final double DEFAULT_ENGAGEMENT_MULTIPLIER = 0.6;
    private static final double MIN_ENGAGEMENT_MULTIPLIER = 0.3;
    private static final double MAX_ENGAGEMENT_MULTIPLIER = 1.0;

    // Confirmed-by value for system/auto promotion
    private static final String SYSTEM_CONFIRMED_BY = "SYSTEM";

    private final HangoutRepository hangoutRepository;
    private final GroupRepository groupRepository;
    private final PointerUpdateService pointerUpdateService;
    private final AdaptiveNotificationService adaptiveNotificationService;
    private final NotificationService notificationService;

    /**
     * Caffeine cache for group engagement data.
     * Key: groupId, Value: GroupEngagementData record
     * TTL: 1 hour — engagement data doesn't need to be real-time precise.
     */
    private final Cache<String, GroupEngagementData> engagementCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofHours(1))
            .build();

    @Autowired
    public MomentumServiceImpl(HangoutRepository hangoutRepository,
                                GroupRepository groupRepository,
                                PointerUpdateService pointerUpdateService,
                                AdaptiveNotificationService adaptiveNotificationService,
                                NotificationService notificationService) {
        this.hangoutRepository = hangoutRepository;
        this.groupRepository = groupRepository;
        this.pointerUpdateService = pointerUpdateService;
        this.adaptiveNotificationService = adaptiveNotificationService;
        this.notificationService = notificationService;
    }

    // ============================================================================
    // PUBLIC INTERFACE METHODS
    // ============================================================================

    @Override
    public void initializeMomentum(Hangout hangout, boolean confirmed, String creatorUserId) {
        long now = System.currentTimeMillis();

        if (confirmed) {
            // "Lock it in" — start confirmed
            hangout.setMomentumCategory(MomentumCategory.CONFIRMED);
            hangout.setMomentumScore(0);
            hangout.setConfirmedAt(now);
            hangout.setConfirmedBy(creatorUserId);
            hangout.setSuggestedBy(null);
        } else {
            // "Float it" — start building
            hangout.setMomentumCategory(MomentumCategory.BUILDING);
            hangout.setMomentumScore(0);
            hangout.setConfirmedAt(null);
            hangout.setConfirmedBy(null);
            hangout.setSuggestedBy(creatorUserId);
        }

        logger.debug("Initialized momentum for hangout {} to {} (confirmed={}, creator={})",
                hangout.getHangoutId(), hangout.getMomentumCategory(), confirmed, creatorUserId);
    }

    @Override
    public void recomputeMomentum(String hangoutId) {
        // Step 1: Load canonical hangout
        Hangout hangout = hangoutRepository.findHangoutById(hangoutId).orElse(null);
        if (hangout == null) {
            logger.warn("recomputeMomentum called for non-existent hangout: {}", hangoutId);
            return;
        }

        // Never demote from CONFIRMED
        if (MomentumCategory.CONFIRMED.equals(hangout.getMomentumCategory())) {
            logger.debug("Hangout {} is already CONFIRMED, skipping recompute", hangoutId);
            return;
        }

        // Step 2: Capture the previous category before any changes
        MomentumCategory previousCategory = hangout.getMomentumCategory();

        // Step 3: Load all hangout detail data in a single query (attendance + car riders)
        com.bbthechange.inviter.dto.HangoutDetailData detailData = loadDetailData(hangoutId);
        List<InterestLevel> interestLevels = detailData.getAttendance();

        // Step 4: Check for concrete action (instant CONFIRMED)
        if (hasConcreteAction(hangout, detailData)) {
            applyConfirmation(hangout, SYSTEM_CONFIRMED_BY);
            saveAndUpdatePointers(hangout);
            maybeNotifyMomentumChange(hangout, previousCategory, MomentumCategory.CONFIRMED,
                    AdaptiveNotificationService.SIGNAL_CONCRETE_ACTION, interestLevels);
            return;
        }

        // Step 5: Compute raw score from RSVPs + bonuses
        int rawScore = computeRawScore(hangout, interestLevels);

        // Step 6: Apply multipliers (compound)
        long nowSeconds = System.currentTimeMillis() / 1000;
        boolean hasRecentActivity = hasRecentActivity(interestLevels, nowSeconds);

        double multiplier = 1.0;
        if (hasRecentActivity) {
            multiplier *= 1.5;
        }
        if (hangout.getStartTimestamp() != null) {
            long secondsUntilEvent = hangout.getStartTimestamp() - nowSeconds;
            if (secondsUntilEvent > 0 && secondsUntilEvent <= PROXIMITY_48H_SECONDS) {
                multiplier *= 1.5;
            } else if (secondsUntilEvent > 0 && secondsUntilEvent <= PROXIMITY_7D_SECONDS) {
                multiplier *= 1.2;
            }
        }
        int finalScore = (int) Math.round(rawScore * multiplier);

        // Step 7: Compute dynamic threshold
        String primaryGroupId = getPrimaryGroupId(hangout);
        int threshold = computeThreshold(primaryGroupId);

        // Step 8: Determine new category
        MomentumCategory newCategory = determineCategory(hangout, finalScore, threshold);

        // Step 9: Apply changes
        hangout.setMomentumScore(finalScore);
        if (newCategory == MomentumCategory.CONFIRMED
                && !MomentumCategory.CONFIRMED.equals(hangout.getMomentumCategory())) {
            applyConfirmation(hangout, SYSTEM_CONFIRMED_BY);
        } else {
            hangout.setMomentumCategory(newCategory);
        }

        // Step 10: Save and propagate to pointers
        saveAndUpdatePointers(hangout);

        // Step 11: Fire adaptive notification if category changed
        if (newCategory != previousCategory) {
            String signal = newCategory == MomentumCategory.CONFIRMED
                    ? AdaptiveNotificationService.SIGNAL_GAINING_TO_CONFIRMED
                    : AdaptiveNotificationService.SIGNAL_BUILDING_TO_GAINING;
            maybeNotifyMomentumChange(hangout, previousCategory, newCategory, signal, interestLevels);
        }

        logger.debug("Recomputed momentum for hangout {}: score={}, category={}, threshold={}",
                hangoutId, finalScore, hangout.getMomentumCategory(), threshold);
    }

    @Override
    public void confirmHangout(String hangoutId, String confirmedByUserId) {
        Hangout hangout = hangoutRepository.findHangoutById(hangoutId).orElse(null);
        if (hangout == null) {
            logger.warn("confirmHangout called for non-existent hangout: {}", hangoutId);
            return;
        }

        if (MomentumCategory.CONFIRMED.equals(hangout.getMomentumCategory())) {
            logger.debug("Hangout {} is already CONFIRMED", hangoutId);
            return;
        }

        MomentumCategory previousCategory = hangout.getMomentumCategory();
        applyConfirmation(hangout, confirmedByUserId);
        saveAndUpdatePointers(hangout);

        // Manual confirmation always notifies (explicit "It's on!" signal)
        maybeNotifyMomentumChange(hangout, previousCategory, MomentumCategory.CONFIRMED,
                AdaptiveNotificationService.SIGNAL_CONFIRMED, List.of());

        logger.info("Hangout {} manually confirmed by user {}", hangoutId, confirmedByUserId);
    }

    @Override
    public MomentumDTO buildMomentumDTO(Hangout hangout, String groupId) {
        int threshold = computeThreshold(groupId);
        int rawScore = hangout.getMomentumScore() != null ? hangout.getMomentumScore() : 0;
        return MomentumDTO.fromRawScore(
                rawScore,
                threshold,
                hangout.getMomentumCategory(),
                hangout.getConfirmedAt(),
                hangout.getConfirmedBy(),
                hangout.getSuggestedBy()
        );
    }

    @Override
    public MomentumDTO buildMomentumDTOFromPointer(HangoutPointer pointer) {
        return MomentumDTO.fromPointerFields(
                pointer.getMomentumScore(),
                pointer.getMomentumCategory(),
                pointer.getConfirmedAt(),
                pointer.getConfirmedBy(),
                pointer.getSuggestedBy()
        );
    }

    // ============================================================================
    // PRIVATE SCORING HELPERS
    // ============================================================================

    /**
     * Load full hangout detail data in a single query (attendance, cars, riders, etc.)
     */
    private com.bbthechange.inviter.dto.HangoutDetailData loadDetailData(String hangoutId) {
        try {
            return hangoutRepository.getHangoutDetailData(hangoutId);
        } catch (Exception e) {
            logger.warn("Failed to load detail data for hangout {}: {}", hangoutId, e.getMessage());
            return com.bbthechange.inviter.dto.HangoutDetailData.builder().build();
        }
    }

    /**
     * A concrete action exists if: ticketsRequired=true with a ticketLink set,
     * OR carpool has at least one rider.
     * Either triggers instant CONFIRMED.
     */
    private boolean hasConcreteAction(Hangout hangout,
                                       com.bbthechange.inviter.dto.HangoutDetailData detailData) {
        boolean hasTickets = Boolean.TRUE.equals(hangout.getTicketsRequired())
                && hangout.getTicketLink() != null
                && !hangout.getTicketLink().isBlank();
        if (hasTickets) {
            return true;
        }

        // Carpool with at least one rider is a concrete action
        return detailData.getCarRiders() != null && !detailData.getCarRiders().isEmpty();
    }

    /**
     * Compute raw score from RSVPs and attribute bonuses.
     */
    private int computeRawScore(Hangout hangout, List<InterestLevel> interestLevels) {
        int score = 0;

        for (InterestLevel il : interestLevels) {
            if ("GOING".equalsIgnoreCase(il.getStatus())) {
                score += 3;
            } else if ("INTERESTED".equalsIgnoreCase(il.getStatus())) {
                score += 1;
            }
        }

        if (hangout.getStartTimestamp() != null) {
            score += 1;
        }
        if (hangout.getLocation() != null) {
            score += 1;
        }

        return score;
    }

    /**
     * Returns true if any InterestLevel record was updated within the last 48 hours.
     */
    private boolean hasRecentActivity(List<InterestLevel> interestLevels, long nowSeconds) {
        Instant cutoff = Instant.ofEpochSecond(nowSeconds - RECENCY_WINDOW_SECONDS);
        return interestLevels.stream()
                .anyMatch(il -> il.getUpdatedAt() != null && il.getUpdatedAt().isAfter(cutoff));
    }

    /**
     * Determine the new momentum category based on score vs threshold.
     * Never demotes from CONFIRMED.
     */
    private MomentumCategory determineCategory(Hangout hangout, int score, int threshold) {
        // Already confirmed — never demote
        if (MomentumCategory.CONFIRMED.equals(hangout.getMomentumCategory())) {
            return MomentumCategory.CONFIRMED;
        }

        // Auto-confirm: score >= threshold*2 AND has a date
        if (score >= threshold * 2 && hangout.getStartTimestamp() != null) {
            return MomentumCategory.CONFIRMED;
        }

        // Gaining momentum: score >= threshold
        if (score >= threshold) {
            return MomentumCategory.GAINING_MOMENTUM;
        }

        // Still building
        return MomentumCategory.BUILDING;
    }

    /**
     * Apply confirmation fields to a hangout.
     */
    private void applyConfirmation(Hangout hangout, String confirmedBy) {
        hangout.setMomentumCategory(MomentumCategory.CONFIRMED);
        hangout.setConfirmedAt(System.currentTimeMillis());
        hangout.setConfirmedBy(confirmedBy);
    }

    /**
     * Save the updated canonical hangout and propagate momentum fields to all pointers.
     */
    private void saveAndUpdatePointers(Hangout hangout) {
        hangoutRepository.save(hangout);

        // Update all associated group pointers
        List<String> groups = hangout.getAssociatedGroups();
        if (groups == null || groups.isEmpty()) {
            return;
        }

        final MomentumCategory category = hangout.getMomentumCategory();
        final Integer score = hangout.getMomentumScore();
        final Long confirmedAt = hangout.getConfirmedAt();
        final String confirmedBy = hangout.getConfirmedBy();
        final String suggestedBy = hangout.getSuggestedBy();
        final String hangoutId = hangout.getHangoutId();

        for (String groupId : groups) {
            pointerUpdateService.updatePointerWithRetry(groupId, hangoutId, pointer -> {
                pointer.setMomentumCategory(category);
                pointer.setMomentumScore(score);
                pointer.setConfirmedAt(confirmedAt);
                pointer.setConfirmedBy(confirmedBy);
                pointer.setSuggestedBy(suggestedBy);
            }, "momentum");
        }
    }

    // ============================================================================
    // ADAPTIVE NOTIFICATION INTEGRATION
    // ============================================================================

    /**
     * Ask AdaptiveNotificationService whether to send a momentum change notification,
     * and if approved, send it via NotificationService.
     *
     * <p>This is fire-and-forget: failures are logged but never propagated to the caller.
     */
    private void maybeNotifyMomentumChange(Hangout hangout,
                                            MomentumCategory previousCategory,
                                            MomentumCategory newCategory,
                                            String signalType,
                                            List<InterestLevel> interestLevels) {
        try {
            String primaryGroupId = getPrimaryGroupId(hangout);
            if (primaryGroupId == null) {
                return;
            }

            boolean approved = adaptiveNotificationService.shouldSendNotification(
                    primaryGroupId, signalType, previousCategory, newCategory);

            if (!approved) {
                logger.debug("Adaptive notification suppressed for hangout {} (group {}, signal {})",
                        hangout.getHangoutId(), primaryGroupId, signalType);
                return;
            }

            // Build message and notify group members
            String message = buildMomentumNotificationMessage(hangout, newCategory, signalType, interestLevels);
            Set<String> groupIds = new java.util.HashSet<>(
                    hangout.getAssociatedGroups() != null ? hangout.getAssociatedGroups() : List.of());

            notificationService.notifyMomentumChange(
                    hangout.getHangoutId(), hangout.getTitle(), primaryGroupId, groupIds, message, signalType);

            logger.info("Momentum notification sent for hangout {} (group {}, signal {}, category {}→{})",
                    hangout.getHangoutId(), primaryGroupId, signalType, previousCategory, newCategory);

        } catch (Exception e) {
            // Notifications must never break momentum computation
            logger.error("Failed to send momentum notification for hangout {}: {}",
                    hangout.getHangoutId(), e.getMessage());
        }
    }

    /**
     * Build the notification message body for a momentum change.
     */
    private String buildMomentumNotificationMessage(Hangout hangout,
                                                     MomentumCategory newCategory,
                                                     String signalType,
                                                     List<InterestLevel> interestLevels) {
        String title = hangout.getTitle() != null ? hangout.getTitle() : "A hangout";

        if (AdaptiveNotificationService.SIGNAL_CONCRETE_ACTION.equals(signalType)) {
            return AdaptiveNotificationService.ticketPurchasedMessage(null, title);
        }

        if (AdaptiveNotificationService.SIGNAL_CONFIRMED.equals(signalType)
                || newCategory == MomentumCategory.CONFIRMED) {
            return "'" + title + "' is confirmed — it's happening!";
        }

        // GAINING_MOMENTUM: count interested people
        int interestedCount = (int) interestLevels.stream()
                .filter(il -> "GOING".equalsIgnoreCase(il.getStatus())
                        || "INTERESTED".equalsIgnoreCase(il.getStatus()))
                .count();
        return AdaptiveNotificationService.gainingTractionMessage(title, interestedCount);
    }

    // ============================================================================
    // DYNAMIC THRESHOLD COMPUTATION (with Caffeine caching)
    // ============================================================================

    /**
     * Get the primary group ID for a hangout.
     * Uses the first associated group if the hangout belongs to multiple groups.
     */
    private String getPrimaryGroupId(Hangout hangout) {
        List<String> groups = hangout.getAssociatedGroups();
        if (groups != null && !groups.isEmpty()) {
            return groups.get(0);
        }
        return null;
    }

    /**
     * Compute dynamic threshold for a group.
     * threshold = ceil(activeMembers * engagementMultiplier * 0.4)
     * Minimum threshold of 1 to avoid trivial promotion.
     */
    int computeThreshold(String groupId) {
        if (groupId == null) {
            // No group context — use a sensible default
            return 1;
        }
        GroupEngagementData engagement = computeGroupEngagement(groupId);
        double raw = engagement.activeMembers() * engagement.engagementMultiplier() * 0.4;
        int threshold = (int) Math.ceil(raw);
        return Math.max(1, threshold);
    }

    /**
     * Compute group engagement data with Caffeine caching (1-hour TTL).
     * Returns activeMembers count and rolling engagement multiplier.
     *
     * activeMembers = count of members active (with any InterestLevel) in last 8 weeks.
     * engagementMultiplier = rolling 8-week confirmation rate, clamped [0.3, 1.0].
     * Default engagementMultiplier = 0.6 for new groups with no history.
     */
    GroupEngagementData computeGroupEngagement(String groupId) {
        return engagementCache.get(groupId, this::loadGroupEngagementFromDb);
    }

    private GroupEngagementData loadGroupEngagementFromDb(String groupId) {
        try {
            List<GroupMembership> members = groupRepository.findMembersByGroupId(groupId);
            int totalMembers = members.size();

            if (totalMembers == 0) {
                return new GroupEngagementData(0, DEFAULT_ENGAGEMENT_MULTIPLIER);
            }

            // Active members = total members in the group.
            // A more sophisticated implementation would check which members have had
            // InterestLevel activity in the last 8 weeks. For now, use total membership
            // count as a proxy for active members, which is conservative and avoids
            // expensive cross-partition queries at this stage.
            // TODO: Refine to count members with InterestLevel records in last 8 weeks
            //       when group-level activity tracking is available.
            int activeMembers = totalMembers;

            // Use default engagement multiplier since we don't yet have historical
            // confirmation rate data stored per group. This will be refined in a future
            // iteration when group confirmation history is tracked.
            double engagementMultiplier = DEFAULT_ENGAGEMENT_MULTIPLIER;
            engagementMultiplier = Math.max(MIN_ENGAGEMENT_MULTIPLIER,
                    Math.min(MAX_ENGAGEMENT_MULTIPLIER, engagementMultiplier));

            return new GroupEngagementData(activeMembers, engagementMultiplier);

        } catch (Exception e) {
            logger.warn("Failed to load group engagement data for group {}: {}", groupId, e.getMessage());
            return new GroupEngagementData(5, DEFAULT_ENGAGEMENT_MULTIPLIER); // Fallback
        }
    }

    // ============================================================================
    // INTERNAL DATA RECORD
    // ============================================================================

    /**
     * Lightweight record holding computed group engagement metrics for caching.
     */
    record GroupEngagementData(int activeMembers, double engagementMultiplier) {}
}
