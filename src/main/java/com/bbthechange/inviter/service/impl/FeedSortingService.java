package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.FeedItem;
import com.bbthechange.inviter.dto.HangoutSummaryDTO;
import com.bbthechange.inviter.dto.MomentumDTO;
import com.bbthechange.inviter.dto.SeriesSummaryDTO;
import com.bbthechange.inviter.model.InterestLevel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Service responsible for slot-based feed interleaving.
 *
 * <p>Buckets hangouts by time horizon (Imminent, Near-term, Mid-term, Distant, Dateless),
 * then within each horizon orders: CONFIRMED → GAINING_MOMENTUM → BUILDING.
 * Applies smart surfacing rules to cap zero-support BUILDING items and ensure
 * at least one item appears per week.
 *
 * <p>Series items are treated as CONFIRMED for sorting purposes.
 */
@Service
public class FeedSortingService {

    // Time horizon boundaries in seconds
    static final long IMMINENT_SECONDS  = TimeUnit.HOURS.toSeconds(48);
    static final long NEAR_TERM_SECONDS = TimeUnit.DAYS.toSeconds(7);
    static final long MID_TERM_SECONDS  = TimeUnit.DAYS.toSeconds(21); // 3 weeks
    // Distant = everything beyond MID_TERM

    // Smart surfacing constants
    static final int MAX_ZERO_SUPPORT_BUILDING = 2;
    static final long RECENT_SUPPORT_WINDOW_SECONDS = TimeUnit.HOURS.toSeconds(24);

    /**
     * Time horizon buckets, ordered for output.
     */
    enum TimeHorizon {
        IMMINENT,    // <= 48h
        NEAR_TERM,   // 3–7 days
        MID_TERM,    // 1–3 weeks
        DISTANT      // 3+ weeks
    }

    /**
     * Sort result containing the reordered lists.
     */
    public static class SortResult {
        public final List<FeedItem> withDay;
        public final List<HangoutSummaryDTO> needsDay;

        public SortResult(List<FeedItem> withDay, List<HangoutSummaryDTO> needsDay) {
            this.withDay  = withDay;
            this.needsDay = needsDay;
        }
    }

    /**
     * Re-sort the feed using slot-based interleaving.
     *
     * @param withDay     Items that have a timestamp (FeedItem — may include HangoutSummaryDTO or SeriesSummaryDTO)
     * @param needsDay    Hangouts without a timestamp
     * @param nowSeconds  Current time as Unix epoch seconds
     * @return            Reordered SortResult
     */
    public SortResult sortFeed(List<FeedItem> withDay, List<HangoutSummaryDTO> needsDay, long nowSeconds) {

        // -----------------------------------------------------------------------
        // 1. Bucket withDay items by time horizon
        // -----------------------------------------------------------------------
        List<FeedItem> imminent  = new ArrayList<>();
        List<FeedItem> nearTerm  = new ArrayList<>();
        List<FeedItem> midTerm   = new ArrayList<>();
        List<FeedItem> distant   = new ArrayList<>();

        for (FeedItem item : withDay) {
            Long ts = getStartTimestamp(item);
            if (ts == null) {
                // Should not happen in withDay but guard anyway
                distant.add(item);
                continue;
            }
            long secondsFromNow = ts - nowSeconds;
            if (secondsFromNow <= IMMINENT_SECONDS) {
                imminent.add(item);
            } else if (secondsFromNow <= NEAR_TERM_SECONDS) {
                nearTerm.add(item);
            } else if (secondsFromNow <= MID_TERM_SECONDS) {
                midTerm.add(item);
            } else {
                distant.add(item);
            }
        }

        // -----------------------------------------------------------------------
        // 2. Apply per-horizon sorting and smart surfacing, track zero-support cap
        // -----------------------------------------------------------------------
        ZeroSupportCounter zeroSupportCounter = new ZeroSupportCounter();

        List<FeedItem> sortedWithDay = new ArrayList<>();
        sortedWithDay.addAll(sortHorizon(imminent,  nowSeconds, zeroSupportCounter, true));
        sortedWithDay.addAll(sortHorizon(nearTerm,  nowSeconds, zeroSupportCounter, false));
        sortedWithDay.addAll(sortHorizon(midTerm,   nowSeconds, zeroSupportCounter, false));
        sortedWithDay.addAll(sortHorizon(distant,   nowSeconds, zeroSupportCounter, true));  // Distant: no suppression (like imminent)

        // -----------------------------------------------------------------------
        // 3. Sort needsDay (no timestamp): CONFIRMED > GAINING_MOMENTUM > BUILDING
        //    Apply zero-support cap (shared counter continues from withDay).
        // -----------------------------------------------------------------------
        List<HangoutSummaryDTO> sortedNeedsDay = sortNeedsDay(needsDay, nowSeconds, zeroSupportCounter);

        return new SortResult(sortedWithDay, sortedNeedsDay);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Sort a single time-horizon bucket.
     * Within the bucket: CONFIRMED/Series first, then GAINING_MOMENTUM, then BUILDING.
     * Each momentum group is ordered chronologically.
     * Applies smart surfacing rules for BUILDING items.
     *
     * @param items              Items in this horizon bucket
     * @param nowSeconds         Current time (epoch seconds)
     * @param counter            Shared zero-support counter across the whole feed
     * @param suppressionExempt  True if this is the Imminent bucket (affects 24h signal boost)
     * @return Sorted list with smart surfacing applied
     */
    private List<FeedItem> sortHorizon(List<FeedItem> items, long nowSeconds,
                                        ZeroSupportCounter counter, boolean suppressionExempt) {
        if (items.isEmpty()) {
            return List.of();
        }

        List<FeedItem> confirmed      = new ArrayList<>();
        List<FeedItem> gainingMomentum = new ArrayList<>();
        List<FeedItem> building        = new ArrayList<>();

        for (FeedItem item : items) {
            String category = getMomentumCategory(item);
            if ("CONFIRMED".equals(category) || category == null || item instanceof SeriesSummaryDTO) {
                // null/legacy hangouts are treated as CONFIRMED (backward compat)
                confirmed.add(item);
            } else if ("GAINING_MOMENTUM".equals(category)) {
                gainingMomentum.add(item);
            } else {
                // BUILDING
                building.add(item);
            }
        }

        // Chronological within each group
        Comparator<FeedItem> chronoOrder = Comparator.comparingLong(i -> {
            Long ts = getStartTimestamp(i);
            return ts != null ? ts : Long.MAX_VALUE;
        });

        confirmed.sort(chronoOrder);
        gainingMomentum.sort(chronoOrder);
        building.sort(chronoOrder);

        // Smart surfacing for BUILDING items
        // Imminent items (< 48h away) always show — suppression only applies to longer horizons
        boolean weekHasConfirmedItems = !suppressionExempt && !confirmed.isEmpty();
        List<FeedItem> filteredBuilding = applySmartSurfacing(
                building, weekHasConfirmedItems, nowSeconds, counter);

        List<FeedItem> result = new ArrayList<>();
        result.addAll(confirmed);
        result.addAll(gainingMomentum);
        result.addAll(filteredBuilding);
        return result;
    }

    /**
     * Sort needsDay items by momentum category, applying the shared zero-support cap.
     */
    private List<HangoutSummaryDTO> sortNeedsDay(List<HangoutSummaryDTO> needsDay,
                                                   long nowSeconds,
                                                   ZeroSupportCounter counter) {
        if (needsDay.isEmpty()) {
            return List.of();
        }

        List<HangoutSummaryDTO> confirmed       = new ArrayList<>();
        List<HangoutSummaryDTO> gainingMomentum = new ArrayList<>();
        List<HangoutSummaryDTO> building         = new ArrayList<>();

        for (HangoutSummaryDTO item : needsDay) {
            String category = getMomentumCategory(item);
            if ("CONFIRMED".equals(category) || category == null) {
                // null/legacy hangouts are treated as CONFIRMED (backward compat)
                confirmed.add(item);
            } else if ("GAINING_MOMENTUM".equals(category)) {
                gainingMomentum.add(item);
            } else {
                // BUILDING
                building.add(item);
            }
        }

        // needsDay items have no time horizon — "busy week" suppression doesn't apply
        List<FeedItem> filteredBuildingFeed = applySmartSurfacing(
                new ArrayList<>(building), false, nowSeconds, counter);
        List<HangoutSummaryDTO> filteredBuilding = filteredBuildingFeed.stream()
                .filter(i -> i instanceof HangoutSummaryDTO)
                .map(i -> (HangoutSummaryDTO) i)
                .toList();

        List<HangoutSummaryDTO> result = new ArrayList<>();
        result.addAll(confirmed);
        result.addAll(gainingMomentum);
        result.addAll(filteredBuilding);
        return result;
    }

    /**
     * Apply smart surfacing rules to a list of BUILDING items within a horizon/week:
     *
     * <ul>
     *   <li>If the week is busy (has confirmed events) → only show BUILDING items that have
     *       recent support (2+ signals in last 24h)</li>
     *   <li>If the week is empty → auto-surface the best candidate (most recent support)</li>
     *   <li>Never show more than {@code MAX_ZERO_SUPPORT_BUILDING} zero-support items across the entire feed</li>
     * </ul>
     *
     * @param building           BUILDING candidates (pre-sorted by desired order)
     * @param weekHasConfirmed   Whether this week/horizon already has confirmed items
     * @param nowSeconds         Current epoch seconds
     * @param counter            Shared mutable counter for zero-support items
     * @return Filtered list
     */
    List<FeedItem> applySmartSurfacing(List<FeedItem> building, boolean weekHasConfirmed,
                                        long nowSeconds, ZeroSupportCounter counter) {
        if (building.isEmpty()) {
            return List.of();
        }

        // Items with 2+ signals in last 24h always surface, regardless of week busyness
        List<FeedItem> recentlySurging = new ArrayList<>();
        List<FeedItem> normal = new ArrayList<>();

        for (FeedItem item : building) {
            if (hasRecentSupportSurge(item, nowSeconds)) {
                recentlySurging.add(item);
            } else {
                normal.add(item);
            }
        }

        List<FeedItem> result = new ArrayList<>(recentlySurging);

        if (weekHasConfirmed) {
            // Busy week: only recently-surging items pass through — no more BUILDING shown
            // (recently-surging are already added above)
        } else {
            // Empty week: auto-surface best candidate if no surging items were found
            if (result.isEmpty() && !normal.isEmpty()) {
                // Sort normal by most recent support signal, then add the best one
                List<FeedItem> sorted = new ArrayList<>(normal);
                sorted.sort(Comparator.comparingLong(
                        (FeedItem i) -> getLastSupportTimestamp(i)).reversed());
                result.add(sorted.get(0));
            }
            // Remaining normal items are NOT added — spec says "auto-surface best candidate" only
        }

        // Apply global zero-support cap
        List<FeedItem> capped = new ArrayList<>();
        for (FeedItem item : result) {
            if (isZeroSupport(item)) {
                if (counter.count < MAX_ZERO_SUPPORT_BUILDING) {
                    counter.count++;
                    capped.add(item);
                }
                // Else: skip — already at cap
            } else {
                capped.add(item);
            }
        }

        return capped;
    }

    // -------------------------------------------------------------------------
    // Introspection helpers
    // -------------------------------------------------------------------------

    /**
     * Get the momentum category string from a FeedItem.
     * Series items are treated as "CONFIRMED".
     */
    String getMomentumCategory(FeedItem item) {
        if (item instanceof SeriesSummaryDTO) {
            return "CONFIRMED";
        }
        if (item instanceof HangoutSummaryDTO h) {
            MomentumDTO momentum = h.getMomentum();
            return momentum != null ? momentum.getCategory() : null;
        }
        return null;
    }

    /**
     * Get the startTimestamp from a FeedItem.
     */
    Long getStartTimestamp(FeedItem item) {
        if (item instanceof HangoutSummaryDTO h) {
            return h.getStartTimestamp();
        }
        if (item instanceof SeriesSummaryDTO s) {
            return s.getStartTimestamp();
        }
        return null;
    }

    /**
     * A hangout has zero support if its interestLevels list is empty or contains
     * only one entry (assumed to be the creator/suggester).
     */
    boolean isZeroSupport(FeedItem item) {
        if (item instanceof HangoutSummaryDTO h) {
            List<com.bbthechange.inviter.model.InterestLevel> levels = h.getInterestLevels();
            return levels == null || levels.size() <= 1;
        }
        // Series items and non-hangout items are never considered zero-support
        return false;
    }

    /**
     * Returns true if the item has 2+ interest-level signals updated within the last 24 hours.
     */
    boolean hasRecentSupportSurge(FeedItem item, long nowSeconds) {
        if (!(item instanceof HangoutSummaryDTO h)) {
            return false;
        }
        List<InterestLevel> levels = h.getInterestLevels();
        if (levels == null || levels.isEmpty()) {
            return false;
        }
        long cutoffSeconds = nowSeconds - RECENT_SUPPORT_WINDOW_SECONDS;
        long recentCount = levels.stream()
                .filter(il -> il.getUpdatedAt() != null &&
                              il.getUpdatedAt().toEpochMilli() / 1000 >= cutoffSeconds)
                .count();
        return recentCount >= 2;
    }

    /**
     * Returns the most recent interest-level update timestamp (epoch seconds),
     * or 0 if no signals exist.
     */
    long getLastSupportTimestamp(FeedItem item) {
        if (!(item instanceof HangoutSummaryDTO h)) {
            return 0L;
        }
        List<InterestLevel> levels = h.getInterestLevels();
        if (levels == null || levels.isEmpty()) {
            return 0L;
        }
        return levels.stream()
                .filter(il -> il.getUpdatedAt() != null)
                .mapToLong(il -> il.getUpdatedAt().toEpochMilli() / 1000)
                .max()
                .orElse(0L);
    }

    // -------------------------------------------------------------------------
    // Inner classes
    // -------------------------------------------------------------------------

    /**
     * Mutable counter shared across the entire feed pass to track how many
     * zero-support BUILDING items have been allowed through.
     */
    static class ZeroSupportCounter {
        int count = 0;
    }
}
