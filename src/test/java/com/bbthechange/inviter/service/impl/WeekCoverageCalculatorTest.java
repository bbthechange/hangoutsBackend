package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.config.MomentumTuningProperties;
import com.bbthechange.inviter.model.BaseItem;
import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.model.InterestLevel;
import com.bbthechange.inviter.model.MomentumCategory;
import com.bbthechange.inviter.model.SeriesPointer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeekCoverageCalculatorTest {

    private MomentumTuningProperties tuning;
    private WeekCoverageCalculator calc;

    // 2026-01-07T12:00:00Z — Wednesday of ISO week 2 of 2026
    private static final long NOW = 1767787200L;

    @BeforeEach
    void setUp() {
        tuning = new MomentumTuningProperties(); // forwardWeeksToFill=8 default
        calc = new WeekCoverageCalculator(tuning);
    }

    private HangoutPointer pointerAt(long ts, MomentumCategory cat) {
        HangoutPointer hp = new HangoutPointer();
        hp.setHangoutId("h-" + ts);
        hp.setStartTimestamp(ts);
        hp.setMomentumCategory(cat);
        return hp;
    }

    private HangoutPointer buildingPointer(long startTs, long createdAtSeconds, int interestLevelSize) {
        HangoutPointer hp = pointerAt(startTs, MomentumCategory.BUILDING);
        hp.setCreatedAt(Instant.ofEpochSecond(createdAtSeconds));
        List<InterestLevel> levels = new ArrayList<>();
        for (int i = 0; i < interestLevelSize; i++) {
            InterestLevel il = new InterestLevel();
            il.setUserId("u" + i);
            il.setUserName("U" + i);
            il.setStatus("INTERESTED");
            il.setUpdatedAt(Instant.ofEpochSecond(createdAtSeconds));
            levels.add(il);
        }
        hp.setInterestLevels(levels);
        return hp;
    }

    @Test
    void noHangouts_allWeeksEmpty() {
        assertThat(calc.countEmptyWeeks(List.of(), NOW)).isEqualTo(tuning.getForwardWeeksToFill());
    }

    @Test
    void nullItems_allWeeksEmpty() {
        assertThat(calc.countEmptyWeeks(null, NOW)).isEqualTo(tuning.getForwardWeeksToFill());
    }

    @Test
    void oneConfirmedInCurrentWeek_sevenEmptyWeeks() {
        long currentWeek = NOW + 1000L;
        List<BaseItem> items = List.of(pointerAt(currentWeek, MomentumCategory.CONFIRMED));

        assertThat(calc.countEmptyWeeks(items, NOW)).isEqualTo(7);
    }

    @Test
    void datedBuildingCoversWeek_sameAsConfirmed() {
        long nextWeek = NOW + 7L * 86400;
        // A dated BUILDING without interest levels is stale+unsupported → does NOT cover.
        // Use the fresh variant here to exercise the "dated BUILDING covers" path.
        HangoutPointer freshDated = buildingPointer(nextWeek, NOW - 86400, 1);

        assertThat(calc.countEmptyWeeks(List.of(freshDated), NOW)).isEqualTo(7);
    }

    @Test
    void legacyNullMomentumCoversWeek() {
        long currentWeek = NOW + 1000L;
        List<BaseItem> items = List.of(pointerAt(currentWeek, null));

        assertThat(calc.countEmptyWeeks(items, NOW)).isEqualTo(7);
    }

    @Test
    void allEightWeeksCovered_returnsZero() {
        List<BaseItem> items = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            items.add(pointerAt(NOW + i * 7L * 86400 + 1000, MomentumCategory.CONFIRMED));
        }

        assertThat(calc.countEmptyWeeks(items, NOW)).isEqualTo(0);
    }

    @Test
    void hangoutsBeyondHorizon_ignored() {
        long beyond = NOW + 100L * 86400; // well outside 8-week window
        List<BaseItem> items = List.of(pointerAt(beyond, MomentumCategory.CONFIRMED));

        assertThat(calc.countEmptyWeeks(items, NOW)).isEqualTo(tuning.getForwardWeeksToFill());
    }

    @Test
    void nullStartTimestamp_skipped() {
        HangoutPointer hp = new HangoutPointer();
        hp.setHangoutId("floating");
        hp.setStartTimestamp(null);
        hp.setMomentumCategory(MomentumCategory.CONFIRMED);

        assertThat(calc.countEmptyWeeks(List.of(hp), NOW)).isEqualTo(tuning.getForwardWeeksToFill());
    }

    @Test
    void staleUnsupportedBuilding_doesNotCoverWeek() {
        long nextWeek = NOW + 7L * 86400;
        long staleCreatedAt = NOW - 10L * 86400; // 10 days ago
        HangoutPointer stale = buildingPointer(nextWeek, staleCreatedAt, 1); // creator only

        assertThat(calc.countEmptyWeeks(List.of(stale), NOW)).isEqualTo(tuning.getForwardWeeksToFill());
    }

    @Test
    void staleSupportedBuilding_coversWeek() {
        long nextWeek = NOW + 7L * 86400;
        long staleCreatedAt = NOW - 10L * 86400;
        HangoutPointer staleSupported = buildingPointer(nextWeek, staleCreatedAt, 3); // 3 interested

        assertThat(calc.countEmptyWeeks(List.of(staleSupported), NOW)).isEqualTo(7);
    }

    @Test
    void freshZeroSupportBuilding_coversWeek() {
        long nextWeek = NOW + 7L * 86400;
        long freshCreatedAt = NOW - 2L * 86400; // within 5-day fresh window
        HangoutPointer fresh = buildingPointer(nextWeek, freshCreatedAt, 1);

        assertThat(calc.countEmptyWeeks(List.of(fresh), NOW)).isEqualTo(7);
    }

    @Test
    void customHorizonRespected() {
        tuning.setForwardWeeksToFill(3);
        WeekCoverageCalculator custom = new WeekCoverageCalculator(tuning);

        assertThat(custom.countEmptyWeeks(List.of(), NOW)).isEqualTo(3);
    }

    @Test
    void inProgressEvent_coversCurrentWeek() {
        // An event with startTimestamp 1 hour ago, CONFIRMED (in progress).
        long oneHourAgo = NOW - 3600;
        HangoutPointer inProgress = pointerAt(oneHourAgo, MomentumCategory.CONFIRMED);

        // It's "this week" (started same ISO week as NOW), so covers the current week.
        assertThat(calc.countEmptyWeeks(List.of(inProgress), NOW)).isEqualTo(7);
    }

    @Test
    void seriesPointerSkipped_doesNotContributeToCoverage() {
        // SeriesPointer is a display aggregate; episodes are stored as individual
        // HangoutPointers that cover their own weeks. Including SeriesPointer here
        // would double-count.
        long currentWeek = NOW + 1000L;
        SeriesPointer sp = new SeriesPointer();
        sp.setSeriesId("series-1");
        sp.setStartTimestamp(currentWeek);
        List<BaseItem> items = List.of(sp);

        assertThat(calc.countEmptyWeeks(items, NOW)).isEqualTo(tuning.getForwardWeeksToFill());
    }

    @Test
    void unorderedItems_handledCorrectly() {
        // The old implementation relied on sorted-by-startTimestamp ordering to early-return
        // on items beyond the horizon. The new implementation iterates all items, so any
        // order works. Mix in an out-of-horizon item between two covering items.
        long beyond = NOW + 100L * 86400;
        long wk1 = NOW + 1000L;
        long wk2 = NOW + 7L * 86400;
        List<BaseItem> items = List.of(
                pointerAt(beyond, MomentumCategory.CONFIRMED),
                pointerAt(wk1, MomentumCategory.CONFIRMED),
                pointerAt(wk2, MomentumCategory.CONFIRMED)
        );

        assertThat(calc.countEmptyWeeks(items, NOW)).isEqualTo(6);
    }
}
