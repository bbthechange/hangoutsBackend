package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.config.MomentumTuningProperties;
import com.bbthechange.inviter.model.BaseItem;
import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.model.InterestLevel;
import com.bbthechange.inviter.model.MomentumCategory;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.util.PaginatedResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeekCoverageCalculatorTest {

    @Mock
    private HangoutRepository hangoutRepository;

    private MomentumTuningProperties tuning;
    private WeekCoverageCalculator calc;

    private static final String GROUP_ID = "group-abc";
    // 2026-01-07T12:00:00Z — Wednesday of ISO week 2 of 2026
    private static final long NOW = 1767787200L;

    @BeforeEach
    void setUp() {
        tuning = new MomentumTuningProperties(); // forwardWeeksToFill=8 default
        calc = new WeekCoverageCalculator(hangoutRepository, tuning);
    }

    private PaginatedResult<BaseItem> page(List<BaseItem> items) {
        return new PaginatedResult<>(items, null);
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
        when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), anyInt(), any()))
                .thenReturn(page(List.of()));

        assertThat(calc.countEmptyWeeks(GROUP_ID, NOW)).isEqualTo(tuning.getForwardWeeksToFill());
    }

    @Test
    void oneConfirmedInCurrentWeek_sevenEmptyWeeks() {
        long currentWeek = NOW + 1000L;
        when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), anyInt(), any()))
                .thenReturn(page(List.of(pointerAt(currentWeek, MomentumCategory.CONFIRMED))));

        // 8 - 1 covered = 7 empty
        assertThat(calc.countEmptyWeeks(GROUP_ID, NOW)).isEqualTo(7);
    }

    @Test
    void datedBuildingCoversWeek_sameAsConfirmed() {
        long nextWeek = NOW + 7L * 86400;
        when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), anyInt(), any()))
                .thenReturn(page(List.of(pointerAt(nextWeek, MomentumCategory.BUILDING))));

        // A dated BUILDING float is a proposal for that week → counts as covered.
        assertThat(calc.countEmptyWeeks(GROUP_ID, NOW)).isEqualTo(7);
    }

    @Test
    void legacyNullMomentumCoversWeek() {
        long currentWeek = NOW + 1000L;
        when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), anyInt(), any()))
                .thenReturn(page(List.of(pointerAt(currentWeek, null))));

        assertThat(calc.countEmptyWeeks(GROUP_ID, NOW)).isEqualTo(7);
    }

    @Test
    void allEightWeeksCovered_returnsZero() {
        List<BaseItem> items = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            items.add(pointerAt(NOW + i * 7L * 86400 + 1000, MomentumCategory.CONFIRMED));
        }
        when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), anyInt(), any()))
                .thenReturn(page(items));

        assertThat(calc.countEmptyWeeks(GROUP_ID, NOW)).isEqualTo(0);
    }

    @Test
    void hangoutsBeyondHorizon_ignored() {
        long beyond = NOW + 100L * 86400; // well outside 8-week window
        when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), anyInt(), any()))
                .thenReturn(page(List.of(pointerAt(beyond, MomentumCategory.CONFIRMED))));

        assertThat(calc.countEmptyWeeks(GROUP_ID, NOW)).isEqualTo(tuning.getForwardWeeksToFill());
    }

    @Test
    void nullStartTimestamp_skipped() {
        HangoutPointer hp = new HangoutPointer();
        hp.setHangoutId("floating");
        hp.setStartTimestamp(null);
        hp.setMomentumCategory(MomentumCategory.CONFIRMED);
        when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), anyInt(), any()))
                .thenReturn(page(List.of(hp)));

        assertThat(calc.countEmptyWeeks(GROUP_ID, NOW)).isEqualTo(tuning.getForwardWeeksToFill());
    }

    @Test
    void repositoryThrows_returnsMaxEmptyWeeks() {
        when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), anyInt(), any()))
                .thenThrow(new RuntimeException("DynamoDB unavailable"));

        // Degraded: unable to determine coverage → assume all empty so filler runs.
        assertThat(calc.countEmptyWeeks(GROUP_ID, NOW)).isEqualTo(tuning.getForwardWeeksToFill());
    }

    @Test
    void staleUnsupportedBuilding_doesNotCoverWeek() {
        // A stale, zero-support BUILDING is held back by FeedSortingService. It would be
        // contradictory for it to satisfy its week and block forward-fill from placing
        // anything there.
        long nextWeek = NOW + 7L * 86400;
        long staleCreatedAt = NOW - 10L * 86400; // 10 days ago
        HangoutPointer stale = buildingPointer(nextWeek, staleCreatedAt, 1); // creator only

        when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), anyInt(), any()))
                .thenReturn(page(List.of(stale)));

        // Stale-unsupported does NOT cover → 8 empty weeks.
        assertThat(calc.countEmptyWeeks(GROUP_ID, NOW)).isEqualTo(tuning.getForwardWeeksToFill());
    }

    @Test
    void staleSupportedBuilding_coversWeek() {
        long nextWeek = NOW + 7L * 86400;
        long staleCreatedAt = NOW - 10L * 86400;
        HangoutPointer staleSupported = buildingPointer(nextWeek, staleCreatedAt, 3); // 3 interested

        when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), anyInt(), any()))
                .thenReturn(page(List.of(staleSupported)));

        // Stale-supported surfaces in feed → covers → 7 empty weeks.
        assertThat(calc.countEmptyWeeks(GROUP_ID, NOW)).isEqualTo(7);
    }

    @Test
    void freshZeroSupportBuilding_coversWeek() {
        long nextWeek = NOW + 7L * 86400;
        long freshCreatedAt = NOW - 2L * 86400; // within 5-day fresh window
        HangoutPointer fresh = buildingPointer(nextWeek, freshCreatedAt, 1);

        when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), anyInt(), any()))
                .thenReturn(page(List.of(fresh)));

        // Fresh always surfaces → covers.
        assertThat(calc.countEmptyWeeks(GROUP_ID, NOW)).isEqualTo(7);
    }

    @Test
    void customHorizonRespected() {
        tuning.setForwardWeeksToFill(3);
        WeekCoverageCalculator custom = new WeekCoverageCalculator(hangoutRepository, tuning);
        when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), anyInt(), any()))
                .thenReturn(page(List.of()));

        assertThat(custom.countEmptyWeeks(GROUP_ID, NOW)).isEqualTo(3);
    }
}
