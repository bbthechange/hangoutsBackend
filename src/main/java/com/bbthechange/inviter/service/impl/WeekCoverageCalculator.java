package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.config.MomentumTuningProperties;
import com.bbthechange.inviter.model.BaseItem;
import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.model.InterestLevel;
import com.bbthechange.inviter.model.MomentumCategory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared helper for computing ISO-week coverage over the forward-fill horizon.
 *
 * <p>Operates on an in-memory list of {@link BaseItem}s already fetched by the caller
 * (GroupServiceImpl merges future + in-progress + floating page results). This avoids
 * a duplicate DynamoDB query and naturally includes in-progress events in coverage.
 *
 * <p>A week is "covered" if it contains at least one <em>visible</em> hangout — one that
 * actually surfaces in the feed. Concretely:
 * <ul>
 *   <li>CONFIRMED or legacy null momentum → always covers the week.</li>
 *   <li>GAINING_MOMENTUM → covers the week.</li>
 *   <li>BUILDING: covers only if it would actually surface in the feed, i.e., fresh
 *       ({@code now - createdAt ≤ freshFloatAgeDays}) OR has {@code interestLevels.size() > 1}
 *       (stale-supported). A stale-unsupported BUILDING is held back by
 *       {@code FeedSortingService}, so it must not block forward-fill for its week.</li>
 * </ul>
 *
 * <p>Series pointers are intentionally ignored — their episodes appear as individual
 * {@link HangoutPointer} rows that already cover their own weeks.
 *
 * <p>Used by {@link com.bbthechange.inviter.service.impl.ForwardFillSuggestionServiceImpl}
 * to decide how many empty weeks the feed needs to fill.
 */
@Component
public class WeekCoverageCalculator {

    private final MomentumTuningProperties tuning;

    public WeekCoverageCalculator(MomentumTuningProperties tuning) {
        this.tuning = tuning;
    }

    /**
     * Count of empty weeks across the forward-fill horizon starting from "now".
     * Returns a number in [0, forwardWeeksToFill].
     *
     * @param items        items already fetched by the caller (future + in-progress + floating)
     * @param nowTimestamp Unix epoch seconds
     */
    public int countEmptyWeeks(List<BaseItem> items, long nowTimestamp) {
        Set<Integer> covered = findCoveredWeeks(items, nowTimestamp);
        int weeksToCheck = tuning.getForwardWeeksToFill();
        ZonedDateTime now = Instant.ofEpochSecond(nowTimestamp).atZone(ZoneOffset.UTC);

        int empty = 0;
        for (int offset = 0; offset < weeksToCheck; offset++) {
            ZonedDateTime weekStart = now.plusWeeks(offset);
            int key = weekKey(
                    weekStart.get(IsoFields.WEEK_BASED_YEAR),
                    weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            if (!covered.contains(key)) {
                empty++;
            }
        }
        return empty;
    }

    private Set<Integer> findCoveredWeeks(List<BaseItem> items, long nowTimestamp) {
        int weeksToCheck = tuning.getForwardWeeksToFill();
        long lookAheadEnd = nowTimestamp + (weeksToCheck * 7L * 24 * 3600);

        Set<Integer> covered = new HashSet<>();
        if (items == null) {
            return covered;
        }

        for (BaseItem item : items) {
            if (!(item instanceof HangoutPointer hp)) {
                continue;
            }
            if (hp.getStartTimestamp() == null) {
                continue;
            }
            if (hp.getStartTimestamp() > lookAheadEnd) {
                continue;
            }
            if (!coversItsWeek(hp, nowTimestamp)) {
                continue;
            }
            ZonedDateTime dt = Instant.ofEpochSecond(hp.getStartTimestamp()).atZone(ZoneOffset.UTC);
            covered.add(weekKey(
                    dt.get(IsoFields.WEEK_BASED_YEAR),
                    dt.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)));
        }

        return covered;
    }

    /**
     * Whether this timestamped pointer actually surfaces in the feed, and therefore
     * should count as "covering" its week. Mirrors {@code FeedSortingService}'s
     * fresh/stale/supported classification for BUILDING items.
     */
    private boolean coversItsWeek(HangoutPointer hp, long nowTimestamp) {
        MomentumCategory cat = hp.getMomentumCategory();
        if (cat == null || cat == MomentumCategory.CONFIRMED || cat == MomentumCategory.GAINING_MOMENTUM) {
            return true;
        }
        // BUILDING: fresh always surfaces; stale surfaces only if supported (>1 interest).
        boolean fresh = hp.getCreatedAt() != null
                && (nowTimestamp - hp.getCreatedAt().getEpochSecond()) <= tuning.getFreshFloatAgeSeconds();
        if (fresh) return true;
        List<InterestLevel> levels = hp.getInterestLevels();
        return levels != null && levels.size() > 1;
    }

    static int weekKey(int year, int isoWeek) {
        return year * 100 + isoWeek;
    }
}
