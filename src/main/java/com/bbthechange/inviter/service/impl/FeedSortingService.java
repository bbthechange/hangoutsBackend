package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.config.MomentumTuningProperties;
import com.bbthechange.inviter.dto.FeedItem;
import com.bbthechange.inviter.dto.HangoutSummaryDTO;
import com.bbthechange.inviter.dto.MomentumDTO;
import com.bbthechange.inviter.dto.SeriesSummaryDTO;
import com.bbthechange.inviter.model.InterestLevel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Service responsible for slot-based feed interleaving with fresh/stale float handling.
 *
 * <p>Buckets timestamped hangouts by time horizon (Imminent, Near-term, Mid-term, Distant),
 * then within each horizon orders: CONFIRMED → GAINING_MOMENTUM → BUILDING.
 *
 * <p>BUILDING items are classified at read time as:
 * <ul>
 *   <li><b>FRESH_FLOAT</b> — created within {@code freshFloatAgeDays}. Always surfaces,
 *       bypasses busy-week suppression and any cap.</li>
 *   <li><b>SUPPORTED_FLOAT</b> — stale with {@code >1} interest level. Subject to
 *       busy-week suppression (non-exempt horizons) and the recent-support surge exception.</li>
 *   <li>Stale and {@code ≤1} interest — held back as a "fade candidate". Returned to the
 *       caller via {@link SortResult#heldStaleFloats} so the forward-fill service can
 *       surface them into empty weeks.</li>
 * </ul>
 *
 * <p>Series items are treated as CONFIRMED for sorting purposes.
 */
@Service
public class FeedSortingService {

    /** Surface reason labels set on {@link HangoutSummaryDTO#getSurfaceReason()}. */
    public static final String REASON_CONFIRMED        = "CONFIRMED";
    public static final String REASON_GAINING          = "GAINING";
    public static final String REASON_FRESH_FLOAT      = "FRESH_FLOAT";
    public static final String REASON_SUPPORTED_FLOAT  = "SUPPORTED_FLOAT";
    public static final String REASON_STALE_FILLER     = "STALE_FILLER";

    private final MomentumTuningProperties tuning;

    public FeedSortingService(MomentumTuningProperties tuning) {
        this.tuning = tuning;
    }

    /** Time horizon buckets, ordered for output. */
    enum TimeHorizon { IMMINENT, NEAR_TERM, MID_TERM, DISTANT }

    /** Sort result containing the reordered lists and held-back fade candidates. */
    public static class SortResult {
        public final List<FeedItem> withDay;
        public final List<HangoutSummaryDTO> needsDay;
        /**
         * Stale + unsupported BUILDING floats held back from the main feed.
         * The forward-fill service decides which (if any) to resurface.
         */
        public final List<HangoutSummaryDTO> heldStaleFloats;

        public SortResult(List<FeedItem> withDay,
                          List<HangoutSummaryDTO> needsDay,
                          List<HangoutSummaryDTO> heldStaleFloats) {
            this.withDay = withDay;
            this.needsDay = needsDay;
            this.heldStaleFloats = heldStaleFloats;
        }
    }

    /**
     * Re-sort the feed using slot-based interleaving.
     *
     * @param withDay    Items that have a timestamp (may include HangoutSummaryDTO or SeriesSummaryDTO)
     * @param needsDay   Hangouts without a timestamp
     * @param nowSeconds Current time as Unix epoch seconds
     */
    public SortResult sortFeed(List<FeedItem> withDay,
                               List<HangoutSummaryDTO> needsDay,
                               long nowSeconds) {

        // Buckets for timestamped items
        List<FeedItem> imminent = new ArrayList<>();
        List<FeedItem> nearTerm = new ArrayList<>();
        List<FeedItem> midTerm  = new ArrayList<>();
        List<FeedItem> distant  = new ArrayList<>();

        long imminentLimit = tuning.getImminentHorizonSeconds();
        long nearLimit     = tuning.getNearTermHorizonSeconds();
        long midLimit      = tuning.getMidTermHorizonSeconds();

        for (FeedItem item : withDay) {
            Long ts = getStartTimestamp(item);
            if (ts == null) {
                distant.add(item);
                continue;
            }
            long delta = ts - nowSeconds;
            if (delta <= imminentLimit)      imminent.add(item);
            else if (delta <= nearLimit)     nearTerm.add(item);
            else if (delta <= midLimit)      midTerm.add(item);
            else                             distant.add(item);
        }

        List<HangoutSummaryDTO> heldStaleFloats = new ArrayList<>();

        List<FeedItem> sortedWithDay = new ArrayList<>();
        // Imminent and Distant are suppression-exempt (stale-supported items always surface there).
        sortedWithDay.addAll(sortHorizon(imminent, nowSeconds, true,  heldStaleFloats));
        sortedWithDay.addAll(sortHorizon(nearTerm, nowSeconds, false, heldStaleFloats));
        sortedWithDay.addAll(sortHorizon(midTerm,  nowSeconds, false, heldStaleFloats));
        sortedWithDay.addAll(sortHorizon(distant,  nowSeconds, true,  heldStaleFloats));

        List<HangoutSummaryDTO> sortedNeedsDay = sortNeedsDay(needsDay, nowSeconds, heldStaleFloats);

        return new SortResult(sortedWithDay, sortedNeedsDay, heldStaleFloats);
    }

    // -------------------------------------------------------------------------
    // Per-horizon sort
    // -------------------------------------------------------------------------

    private List<FeedItem> sortHorizon(List<FeedItem> items,
                                       long nowSeconds,
                                       boolean suppressionExempt,
                                       List<HangoutSummaryDTO> heldStaleFloats) {
        if (items.isEmpty()) return List.of();

        List<FeedItem> confirmed = new ArrayList<>();
        List<FeedItem> gaining   = new ArrayList<>();
        List<FeedItem> building  = new ArrayList<>();

        for (FeedItem item : items) {
            String category = getMomentumCategory(item);
            if ("CONFIRMED".equals(category) || category == null || item instanceof SeriesSummaryDTO) {
                markReason(item, REASON_CONFIRMED);
                confirmed.add(item);
            } else if ("GAINING_MOMENTUM".equals(category)) {
                markReason(item, REASON_GAINING);
                gaining.add(item);
            } else {
                building.add(item); // surfaceReason set later during fresh/stale split
            }
        }

        Comparator<FeedItem> chrono = Comparator.comparingLong(i -> {
            Long ts = getStartTimestamp(i);
            return ts != null ? ts : Long.MAX_VALUE;
        });
        confirmed.sort(chrono);
        gaining.sort(chrono);
        building.sort(chrono);

        boolean weekHasConfirmed = !suppressionExempt && !confirmed.isEmpty();
        List<FeedItem> surfacedBuilding = classifyAndFilterBuilding(
                building, weekHasConfirmed, nowSeconds, heldStaleFloats);

        List<FeedItem> result = new ArrayList<>();
        result.addAll(confirmed);
        result.addAll(gaining);
        result.addAll(surfacedBuilding);
        return result;
    }

    private List<HangoutSummaryDTO> sortNeedsDay(List<HangoutSummaryDTO> needsDay,
                                                 long nowSeconds,
                                                 List<HangoutSummaryDTO> heldStaleFloats) {
        if (needsDay.isEmpty()) return List.of();

        List<HangoutSummaryDTO> confirmed = new ArrayList<>();
        List<HangoutSummaryDTO> gaining   = new ArrayList<>();
        List<HangoutSummaryDTO> building  = new ArrayList<>();

        for (HangoutSummaryDTO item : needsDay) {
            String category = getMomentumCategory(item);
            if ("CONFIRMED".equals(category) || category == null) {
                item.setSurfaceReason(REASON_CONFIRMED);
                confirmed.add(item);
            } else if ("GAINING_MOMENTUM".equals(category)) {
                item.setSurfaceReason(REASON_GAINING);
                gaining.add(item);
            } else {
                building.add(item);
            }
        }

        // needsDay has no time horizon — busy-week suppression never applies.
        List<FeedItem> surfacedBuilding = classifyAndFilterBuilding(
                new ArrayList<>(building), /*weekHasConfirmed*/ false, nowSeconds, heldStaleFloats);

        List<HangoutSummaryDTO> result = new ArrayList<>();
        result.addAll(confirmed);
        result.addAll(gaining);
        for (FeedItem f : surfacedBuilding) {
            if (f instanceof HangoutSummaryDTO h) result.add(h);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Fresh / stale classification + filtering for BUILDING items
    // -------------------------------------------------------------------------

    /**
     * Split BUILDING items into fresh (always surface), stale-supported (normal rules),
     * and stale-unsupported (held back for forward-fill). Returns the items to surface
     * in this horizon; held items are appended to {@code heldStaleFloats}.
     *
     * <p>Stale-supported items (by construction have {@code interestLevels.size() > 1})
     * are subject to busy-week suppression with the recent-support surge exception.
     * No zero-support cap applies — the fresh/stale split obsoleted that cap because
     * stale-unsupported items are already held back, and fresh items always surface.
     */
    List<FeedItem> classifyAndFilterBuilding(List<FeedItem> building,
                                             boolean weekHasConfirmed,
                                             long nowSeconds,
                                             List<HangoutSummaryDTO> heldStaleFloats) {
        if (building.isEmpty()) return List.of();

        List<FeedItem> fresh           = new ArrayList<>();
        List<FeedItem> staleSupported  = new ArrayList<>();

        for (FeedItem item : building) {
            if (!(item instanceof HangoutSummaryDTO h)) {
                // Defensive: a non-hangout BUILDING is unexpected. Treat as fresh to avoid dropping.
                fresh.add(item);
                continue;
            }
            if (isFresh(h, nowSeconds)) {
                h.setSurfaceReason(REASON_FRESH_FLOAT);
                fresh.add(h);
            } else if (isZeroSupport(h)) {
                // stale-unsupported → held back as a fade candidate
                heldStaleFloats.add(h);
            } else {
                h.setSurfaceReason(REASON_SUPPORTED_FLOAT);
                staleSupported.add(h);
            }
        }

        // Fresh floats always surface.
        List<FeedItem> result = new ArrayList<>(fresh);

        if (!staleSupported.isEmpty()) {
            if (weekHasConfirmed) {
                // Busy week: only surging items pass.
                for (FeedItem item : staleSupported) {
                    if (hasRecentSupportSurge(item, nowSeconds)) result.add(item);
                }
            } else {
                // Empty / suppression-exempt horizon: all stale-supported surface.
                result.addAll(staleSupported);
            }
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Introspection helpers
    // -------------------------------------------------------------------------

    String getMomentumCategory(FeedItem item) {
        if (item instanceof SeriesSummaryDTO) return "CONFIRMED";
        if (item instanceof HangoutSummaryDTO h) {
            MomentumDTO m = h.getMomentum();
            return m != null ? m.getCategory() : null;
        }
        return null;
    }

    Long getStartTimestamp(FeedItem item) {
        if (item instanceof HangoutSummaryDTO h) return h.getStartTimestamp();
        if (item instanceof SeriesSummaryDTO s)  return s.getStartTimestamp();
        return null;
    }

    /**
     * A hangout is "fresh" if created within {@code freshFloatAgeDays}. Legacy hangouts
     * with a null createdAt are treated as stale (conservative — they've been around a while).
     */
    boolean isFresh(HangoutSummaryDTO h, long nowSeconds) {
        Long createdAt = h.getCreatedAt();
        if (createdAt == null) return false;
        return (nowSeconds - createdAt) <= tuning.getFreshFloatAgeSeconds();
    }

    /** Zero support = interestLevels empty or contains only the creator. */
    boolean isZeroSupport(FeedItem item) {
        if (item instanceof HangoutSummaryDTO h) {
            List<InterestLevel> levels = h.getInterestLevels();
            return levels == null || levels.size() <= 1;
        }
        return false;
    }

    /** ≥N interest-level updates within the recent-support window. */
    boolean hasRecentSupportSurge(FeedItem item, long nowSeconds) {
        if (!(item instanceof HangoutSummaryDTO h)) return false;
        List<InterestLevel> levels = h.getInterestLevels();
        if (levels == null || levels.isEmpty()) return false;
        long cutoff = nowSeconds - tuning.getRecentSupportWindowSeconds();
        long recent = levels.stream()
                .filter(il -> il.getUpdatedAt() != null
                        && il.getUpdatedAt().toEpochMilli() / 1000 >= cutoff)
                .count();
        return recent >= tuning.getRecentSupportMinSignals();
    }

    private void markReason(FeedItem item, String reason) {
        if (item instanceof HangoutSummaryDTO h) {
            h.setSurfaceReason(reason);
        }
        // SeriesSummaryDTO has no surfaceReason field — intentionally skipped.
    }
}
