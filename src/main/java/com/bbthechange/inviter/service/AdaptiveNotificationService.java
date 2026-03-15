package com.bbthechange.inviter.service;

import com.bbthechange.inviter.model.GroupNotificationTracker;
import com.bbthechange.inviter.model.MomentumCategory;
import com.bbthechange.inviter.repository.GroupNotificationTrackerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.time.ZonedDateTime;
import java.util.Map;

/**
 * Adaptive per-group notification threshold service.
 *
 * <h3>Decision rules</h3>
 * <ul>
 *   <li><b>Concrete actions</b> (tickets purchased, reservation made) → ALWAYS notify</li>
 *   <li><b>Explicit confirmations</b> ("It's on!") → ALWAYS notify</li>
 *   <li><b>Momentum state changes</b> → check against adaptive weekly threshold</li>
 *   <li><b>General engagement signals</b> → check against adaptive weekly threshold</li>
 * </ul>
 *
 * <h3>Threshold</h3>
 * The target is ~1-2 notifications per week per group. The dynamic threshold
 * is derived from the 8-week rolling average:
 * <pre>
 *   weeklyBudget = max(2, ceil(rollingWeeklyAverage * 1.5))
 *   shouldSend   = notificationsSentThisWeek < weeklyBudget
 * </pre>
 * A brand-new group (no rolling history) defaults to a budget of 2.
 *
 * <h3>Notification types</h3>
 * <ul>
 *   <li>Gaining traction: "[Hangout] is gaining traction — N people are interested"</li>
 *   <li>Ticket action:    "[Name] bought tickets for [hangout]"</li>
 *   <li>Action nudge:     "[Hangout] is Friday — consider buying tickets"</li>
 *   <li>Empty week:       "Nothing planned next week — check out your group's ideas"</li>
 * </ul>
 */
@Service
public class AdaptiveNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(AdaptiveNotificationService.class);

    /** Weeks kept in the rolling average (8-week window). */
    private static final int ROLLING_WINDOW_WEEKS = 8;

    /** Default weekly notification budget for new groups with no history. */
    private static final int DEFAULT_WEEKLY_BUDGET = 2;

    // Signal type constants
    public static final String SIGNAL_BUILDING_TO_GAINING = "BUILDING_TO_GAINING";
    public static final String SIGNAL_GAINING_TO_CONFIRMED = "GAINING_TO_CONFIRMED";
    public static final String SIGNAL_CONFIRMED = "CONFIRMED";
    public static final String SIGNAL_CONCRETE_ACTION = "CONCRETE_ACTION";
    public static final String SIGNAL_GENERAL = "GENERAL";

    private final GroupNotificationTrackerRepository trackerRepository;

    @Autowired
    public AdaptiveNotificationService(GroupNotificationTrackerRepository trackerRepository) {
        this.trackerRepository = trackerRepository;
    }

    // ============================================================================
    // PUBLIC API
    // ============================================================================

    /**
     * Decide whether a momentum state change should trigger a notification.
     *
     * <p>Concrete actions and explicit confirmations always return {@code true}.
     * All other signals are subject to the weekly budget.
     *
     * @param groupId      the group the hangout belongs to
     * @param signalType   one of the SIGNAL_* constants in this class
     * @param previousCategory  the momentum category before the change (may be null for new)
     * @param newCategory        the momentum category after the change
     * @return {@code true} if a notification should be sent
     */
    public boolean shouldSendNotification(String groupId,
                                          String signalType,
                                          MomentumCategory previousCategory,
                                          MomentumCategory newCategory) {
        if (groupId == null) {
            return false;
        }

        // Concrete actions and explicit confirmations always notify
        if (SIGNAL_CONCRETE_ACTION.equals(signalType) || SIGNAL_CONFIRMED.equals(signalType)) {
            recordSignalOnTracker(groupId, signalType, true);
            return true;
        }

        // State unchanged — no notification
        if (previousCategory == newCategory) {
            recordSignalOnTracker(groupId, signalType, false);
            return false;
        }

        // Check weekly budget — single load, decide, record, save
        try {
            GroupNotificationTracker tracker = loadOrCreate(groupId);
            rolloverIfNeeded(tracker);

            int budget = computeWeeklyBudget(tracker);
            boolean approved = tracker.getNotificationsSentThisWeek() < budget;

            // Record signal and save in one shot (no second DB read)
            Map<String, Integer> counts = tracker.getSignalCounts();
            counts.merge(signalType, 1, Integer::sum);
            tracker.setSignalCounts(counts);
            if (approved) {
                tracker.setNotificationsSentThisWeek(tracker.getNotificationsSentThisWeek() + 1);
            }
            trackerRepository.save(tracker);

            return approved;
        } catch (Exception e) {
            logger.warn("Failed to check notification budget for group {}", groupId, e);
            return false;
        }
    }

    // ============================================================================
    // PRIVATE HELPERS
    // ============================================================================

    /**
     * Load or create a tracker for a group.
     * The returned tracker is not yet persisted — callers must call {@code trackerRepository.save()} explicitly.
     */
    GroupNotificationTracker loadOrCreate(String groupId) {
        return trackerRepository.findByGroupId(groupId).orElseGet(() -> {
            GroupNotificationTracker fresh = new GroupNotificationTracker();
            fresh.setGroupId(groupId);
            fresh.setPk("GROUP#" + groupId);
            fresh.setSk("NOTIFICATION_TRACKER");
            fresh.setWeekKey(currentWeekKey());
            fresh.setNotificationsSentThisWeek(0);
            fresh.setRollingWeeklyAverage(0.0);
            return fresh;
        });
    }

    /**
     * If the tracker's weekKey is stale (different from the current ISO week),
     * archive the current week's count into the rolling average and reset counters.
     */
    void rolloverIfNeeded(GroupNotificationTracker tracker) {
        String current = currentWeekKey();
        if (current.equals(tracker.getWeekKey())) {
            return;
        }

        // Update rolling 8-week average
        double oldAvg = tracker.getRollingWeeklyAverage();
        int weekCount = tracker.getNotificationsSentThisWeek();

        // Exponential moving average with window = ROLLING_WINDOW_WEEKS
        double alpha = 1.0 / ROLLING_WINDOW_WEEKS;
        double newAvg;
        if (oldAvg == 0.0) {
            newAvg = weekCount;
        } else {
            newAvg = alpha * weekCount + (1 - alpha) * oldAvg;
        }

        tracker.setRollingWeeklyAverage(newAvg);
        tracker.setNotificationsSentThisWeek(0);
        tracker.setSignalCounts(new java.util.HashMap<>());
        tracker.setWeekKey(current);

        logger.debug("Rolled over tracker for group {}: weekCount={}, newAvg={}",
                tracker.getGroupId(), weekCount, newAvg);
    }

    /**
     * Compute the weekly notification budget from the rolling average.
     * Targets ~1-2 notifications per week.
     */
    int computeWeeklyBudget(GroupNotificationTracker tracker) {
        double avg = tracker.getRollingWeeklyAverage();
        if (avg <= 0.0) {
            return DEFAULT_WEEKLY_BUDGET;
        }
        // Budget = 1.5× rolling average, minimum 2
        return Math.max(DEFAULT_WEEKLY_BUDGET, (int) Math.ceil(avg * 1.5));
    }

    /**
     * Record a signal on a tracker and persist it.
     * Used for always-notify paths (concrete actions, explicit confirmations)
     * where the budget is not checked.
     * Best-effort — failures are logged but not propagated.
     */
    private void recordSignalOnTracker(String groupId, String signalType, boolean notified) {
        try {
            GroupNotificationTracker tracker = loadOrCreate(groupId);
            rolloverIfNeeded(tracker);

            Map<String, Integer> counts = tracker.getSignalCounts();
            counts.merge(signalType, 1, Integer::sum);
            tracker.setSignalCounts(counts);

            if (notified) {
                tracker.setNotificationsSentThisWeek(tracker.getNotificationsSentThisWeek() + 1);
            }

            trackerRepository.save(tracker);
        } catch (Exception e) {
            logger.warn("Failed to record signal for group {}: {}", groupId, e.getMessage());
        }
    }

    /**
     * Returns the current ISO week key in the format "YYYY-WNN", e.g. "2026-W10".
     */
    String currentWeekKey() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        int year = now.get(IsoFields.WEEK_BASED_YEAR);
        int week = now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return String.format("%d-W%02d", year, week);
    }
}
