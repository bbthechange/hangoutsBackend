package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.config.MomentumTuningProperties;
import com.bbthechange.inviter.model.BaseItem;
import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.model.InterestLevel;
import com.bbthechange.inviter.model.MomentumCategory;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.util.PaginatedResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <p>Used by {@link com.bbthechange.inviter.service.impl.ForwardFillSuggestionServiceImpl}
 * to decide how many empty weeks the feed needs to fill.
 */
@Component
public class WeekCoverageCalculator {

    private static final Logger logger = LoggerFactory.getLogger(WeekCoverageCalculator.class);

    /** DynamoDB page size when scanning future hangouts for coverage. */
    private static final int HANGOUT_PAGE_SIZE = 50;

    private final HangoutRepository hangoutRepository;
    private final MomentumTuningProperties tuning;

    public WeekCoverageCalculator(HangoutRepository hangoutRepository, MomentumTuningProperties tuning) {
        this.hangoutRepository = hangoutRepository;
        this.tuning = tuning;
    }

    /**
     * Return the set of week keys covered by at least one hangout in the next
     * {@code forwardWeeksToFill} ISO weeks. A week key is
     * {@code weekBasedYear * 100 + isoWeek}.
     */
    public Set<Integer> findCoveredWeeks(String groupId, long nowTimestamp) {
        int weeksToCheck = tuning.getForwardWeeksToFill();
        long lookAheadEnd = nowTimestamp + (weeksToCheck * 7L * 24 * 3600);

        Set<Integer> covered = new HashSet<>();
        PaginatedResult<BaseItem> page;
        String token = null;

        do {
            try {
                page = hangoutRepository.getFutureEventsPage(groupId, nowTimestamp, HANGOUT_PAGE_SIZE, token);
            } catch (Exception e) {
                logger.warn("Error querying hangouts for week coverage on group {}", groupId, e);
                break;
            }

            for (BaseItem item : page.getResults()) {
                if (!(item instanceof HangoutPointer hp)) {
                    continue;
                }
                if (hp.getStartTimestamp() == null) {
                    continue;
                }
                if (hp.getStartTimestamp() > lookAheadEnd) {
                    // Beyond our window — results are ordered by startTimestamp, so stop.
                    return covered;
                }
                if (!coversItsWeek(hp, nowTimestamp)) {
                    continue;
                }
                ZonedDateTime dt = Instant.ofEpochSecond(hp.getStartTimestamp()).atZone(ZoneOffset.UTC);
                covered.add(weekKey(
                        dt.get(IsoFields.WEEK_BASED_YEAR),
                        dt.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)));
            }

            token = page.getNextToken();
        } while (page.hasMore() && token != null);

        return covered;
    }

    /**
     * Count of empty weeks across the forward-fill horizon starting from "now".
     * Returns a number in [0, forwardWeeksToFill].
     */
    public int countEmptyWeeks(String groupId, long nowTimestamp) {
        Set<Integer> covered = findCoveredWeeks(groupId, nowTimestamp);
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
