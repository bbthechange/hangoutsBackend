package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.config.MomentumTuningProperties;
import com.bbthechange.inviter.dto.FeedItem;
import com.bbthechange.inviter.dto.HangoutSummaryDTO;
import com.bbthechange.inviter.dto.MomentumDTO;
import com.bbthechange.inviter.dto.SeriesSummaryDTO;
import com.bbthechange.inviter.model.InterestLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FeedSortingService} with fresh/stale float handling.
 *
 * Coverage:
 *   - Time horizon bucketing (Imminent, Near-term, Mid-term, Distant)
 *   - Per-horizon sort order CONFIRMED → GAINING_MOMENTUM → BUILDING, chronological within
 *   - Fresh floats (createdAt within freshFloatAgeDays): always surface, uncapped
 *   - Stale-supported floats: normal busy-week + surge rules
 *   - Stale-unsupported floats: held back via SortResult.heldStaleFloats
 *   - Series treated as CONFIRMED
 *   - Legacy null momentum treated as CONFIRMED
 *   - surfaceReason is populated on every emitted hangout
 */
class FeedSortingServiceTest {

    private FeedSortingService service;
    private MomentumTuningProperties tuning;
    private long nowSeconds;

    @BeforeEach
    void setUp() {
        tuning = new MomentumTuningProperties(); // defaults: freshAge=5d, etc.
        service = new FeedSortingService(tuning);
        nowSeconds = Instant.now().getEpochSecond();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Creates a fresh hangout by default (createdAt = now). */
    private HangoutSummaryDTO buildHangout(String id, String category, Long startTimestamp) {
        return buildHangout(id, category, startTimestamp, List.of(), nowSeconds);
    }

    private HangoutSummaryDTO buildHangout(String id, String category, Long startTimestamp,
                                           List<InterestLevel> interestLevels) {
        return buildHangout(id, category, startTimestamp, interestLevels, nowSeconds);
    }

    private HangoutSummaryDTO buildHangout(String id, String category, Long startTimestamp,
                                           List<InterestLevel> interestLevels,
                                           long createdAtSeconds) {
        HangoutSummaryDTO dto = HangoutSummaryDTO.builder()
                .withHangoutId(id)
                .withTitle("Hangout " + id)
                .withStartTimestamp(startTimestamp)
                .withInterestLevels(interestLevels)
                .build();
        dto.setCreatedAt(createdAtSeconds);
        if (category != null) {
            MomentumDTO m = new MomentumDTO();
            m.setCategory(category);
            m.setScore(5);
            dto.setMomentum(m);
        }
        return dto;
    }

    /** A stale hangout (older than the fresh threshold). */
    private HangoutSummaryDTO buildStaleHangout(String id, String category, Long startTimestamp,
                                                List<InterestLevel> levels) {
        long createdAt = nowSeconds - TimeUnit.DAYS.toSeconds(tuning.getFreshFloatAgeDays() + 1);
        return buildHangout(id, category, startTimestamp, levels, createdAt);
    }

    private SeriesSummaryDTO buildSeries(String id, Long startTimestamp) {
        SeriesSummaryDTO s = new SeriesSummaryDTO();
        s.setSeriesId(id);
        s.setSeriesTitle("Series " + id);
        s.setStartTimestamp(startTimestamp);
        return s;
    }

    private InterestLevel buildInterestLevel(String userId, long updatedAtSeconds) {
        InterestLevel il = new InterestLevel();
        il.setUserId(userId);
        il.setUserName("User");
        il.setStatus("GOING");
        il.setUpdatedAt(Instant.ofEpochSecond(updatedAtSeconds));
        return il;
    }

    private long inSeconds(long secondsFromNow) { return nowSeconds + secondsFromNow; }

    // =========================================================================
    // Time horizon bucketing + per-horizon ordering
    // =========================================================================

    @Nested
    class HorizonSorting {

        @Test
        void imminentConfirmedBeforeImminentBuilding() {
            long imminent = inSeconds(TimeUnit.HOURS.toSeconds(10));
            HangoutSummaryDTO ib = buildHangout("ib", "BUILDING",  imminent);
            HangoutSummaryDTO ic = buildHangout("ic", "CONFIRMED", imminent);

            FeedSortingService.SortResult r = service.sortFeed(
                    new ArrayList<>(List.of(ib, ic)), new ArrayList<>(), nowSeconds);

            List<String> ids = ids(r.withDay);
            assertThat(ids.indexOf("ic")).isLessThan(ids.indexOf("ib"));
        }

        @Test
        void withinHorizon_confirmedBeforeGainingBeforeBuilding() {
            long ts = inSeconds(TimeUnit.DAYS.toSeconds(5));
            HangoutSummaryDTO b = buildHangout("b", "BUILDING", ts);     // fresh default
            HangoutSummaryDTO c = buildHangout("c", "CONFIRMED", ts);
            HangoutSummaryDTO g = buildHangout("g", "GAINING_MOMENTUM", ts);

            FeedSortingService.SortResult r = service.sortFeed(
                    new ArrayList<>(List.of(b, g, c)), new ArrayList<>(), nowSeconds);

            List<String> ids = ids(r.withDay);
            assertThat(ids.indexOf("c")).isLessThan(ids.indexOf("g"));
            assertThat(ids.indexOf("g")).isLessThan(ids.indexOf("b"));
        }

        @Test
        void confirmedItemsChronologicalWithinHorizon() {
            long earlier = inSeconds(TimeUnit.DAYS.toSeconds(4));
            long later   = inSeconds(TimeUnit.DAYS.toSeconds(6));
            HangoutSummaryDTO c1 = buildHangout("c1", "CONFIRMED", later);
            HangoutSummaryDTO c2 = buildHangout("c2", "CONFIRMED", earlier);

            FeedSortingService.SortResult r = service.sortFeed(
                    new ArrayList<>(List.of(c1, c2)), new ArrayList<>(), nowSeconds);
            assertThat(ids(r.withDay)).containsExactly("c2", "c1");
        }

        @Test
        void distantItemsAfterImminent() {
            long imminent = inSeconds(TimeUnit.HOURS.toSeconds(5));
            long distant  = inSeconds(TimeUnit.DAYS.toSeconds(30));
            HangoutSummaryDTO imm = buildHangout("imm", "CONFIRMED", imminent);
            HangoutSummaryDTO dis = buildHangout("dis", "CONFIRMED", distant);

            FeedSortingService.SortResult r = service.sortFeed(
                    new ArrayList<>(List.of(dis, imm)), new ArrayList<>(), nowSeconds);
            assertThat(ids(r.withDay)).containsExactly("imm", "dis");
        }

        @Test
        void nullTimestampInWithDay_goesToDistant() {
            long imm = inSeconds(TimeUnit.HOURS.toSeconds(5));
            HangoutSummaryDTO nullTs   = buildHangout("null-ts", "CONFIRMED", null);
            HangoutSummaryDTO imminent = buildHangout("imminent", "CONFIRMED", imm);

            FeedSortingService.SortResult r = service.sortFeed(
                    new ArrayList<>(List.of(nullTs, imminent)), new ArrayList<>(), nowSeconds);
            assertThat(ids(r.withDay)).containsExactly("imminent", "null-ts");
        }
    }

    // =========================================================================
    // Series items
    // =========================================================================

    @Nested
    class SeriesAsConfirmed {

        @Test
        void seriesSortsBeforeBuildingSameHorizon() {
            long ts = inSeconds(TimeUnit.HOURS.toSeconds(10));
            SeriesSummaryDTO s = buildSeries("s1", ts);
            HangoutSummaryDTO b = buildHangout("b",  "BUILDING", ts);

            FeedSortingService.SortResult r = service.sortFeed(
                    new ArrayList<>(List.of(b, s)), new ArrayList<>(), nowSeconds);
            assertThat(r.withDay.get(0)).isSameAs(s);
            assertThat(r.withDay.get(1)).isSameAs(b);
        }

        @Test
        void getMomentumCategory_series() {
            assertThat(service.getMomentumCategory(buildSeries("s1", nowSeconds + 1000)))
                    .isEqualTo("CONFIRMED");
        }

        @Test
        void getMomentumCategory_hangoutNullMomentum() {
            assertThat(service.getMomentumCategory(buildHangout("h", null, nowSeconds + 1000)))
                    .isNull();
        }
    }

    // =========================================================================
    // Fresh floats always surface (Bug 1 fix)
    // =========================================================================

    @Nested
    class FreshFloats {

        @Test
        void twoFreshFloatsInNeedsDay_bothSurface() {
            // This is the user-reported bug: creating a second float hid the first.
            HangoutSummaryDTO f1 = buildHangout("f1", "BUILDING", null);
            HangoutSummaryDTO f2 = buildHangout("f2", "BUILDING", null);

            FeedSortingService.SortResult r = service.sortFeed(
                    new ArrayList<>(), new ArrayList<>(List.of(f1, f2)), nowSeconds);

            assertThat(r.needsDay).hasSize(2);
            assertThat(r.heldStaleFloats).isEmpty();
            r.needsDay.forEach(h ->
                    assertThat(h.getSurfaceReason()).isEqualTo(FeedSortingService.REASON_FRESH_FLOAT));
        }

        @Test
        void freshFloat_surfacesEvenInBusyWeek() {
            long ts = inSeconds(TimeUnit.DAYS.toSeconds(5));
            HangoutSummaryDTO confirmed = buildHangout("c", "CONFIRMED", ts);
            HangoutSummaryDTO freshBuilding = buildHangout("b", "BUILDING", ts); // fresh default

            FeedSortingService.SortResult r = service.sortFeed(
                    new ArrayList<>(List.of(confirmed, freshBuilding)),
                    new ArrayList<>(), nowSeconds);

            assertThat(ids(r.withDay)).containsExactly("c", "b");
            HangoutSummaryDTO bOut = (HangoutSummaryDTO) r.withDay.get(1);
            assertThat(bOut.getSurfaceReason()).isEqualTo(FeedSortingService.REASON_FRESH_FLOAT);
        }

        @Test
        void freshFloat_zeroSupport_stillSurfacesUncapped() {
            // Two fresh zero-support floats in needsDay — both surface, cap doesn't apply.
            HangoutSummaryDTO f1 = buildHangout("f1", "BUILDING", null, List.of());
            HangoutSummaryDTO f2 = buildHangout("f2", "BUILDING", null, List.of());
            HangoutSummaryDTO f3 = buildHangout("f3", "BUILDING", null, List.of());

            FeedSortingService.SortResult r = service.sortFeed(
                    new ArrayList<>(), new ArrayList<>(List.of(f1, f2, f3)), nowSeconds);

            assertThat(r.needsDay).hasSize(3);
        }
    }

    // =========================================================================
    // Stale unsupported floats are held back
    // =========================================================================

    @Nested
    class StaleUnsupportedHeld {

        @Test
        void staleUnsupported_heldNotSurfaced() {
            HangoutSummaryDTO stale = buildStaleHangout("s", "BUILDING", null, List.of());

            FeedSortingService.SortResult r = service.sortFeed(
                    new ArrayList<>(), new ArrayList<>(List.of(stale)), nowSeconds);

            assertThat(r.needsDay).isEmpty();
            assertThat(r.heldStaleFloats).hasSize(1);
            assertThat(r.heldStaleFloats.get(0).getHangoutId()).isEqualTo("s");
        }

        @Test
        void staleUnsupported_withTimestamp_heldBack() {
            long ts = inSeconds(TimeUnit.DAYS.toSeconds(5));
            HangoutSummaryDTO stale = buildStaleHangout("s", "BUILDING", ts, List.of());

            FeedSortingService.SortResult r = service.sortFeed(
                    new ArrayList<>(List.of(stale)), new ArrayList<>(), nowSeconds);

            assertThat(r.withDay).isEmpty();
            assertThat(r.heldStaleFloats).hasSize(1);
        }

        @Test
        void mixedFreshAndStaleUnsupported_freshSurfacesStaleHeld() {
            HangoutSummaryDTO fresh = buildHangout("fresh", "BUILDING", null);
            HangoutSummaryDTO stale = buildStaleHangout("stale", "BUILDING", null, List.of());

            FeedSortingService.SortResult r = service.sortFeed(
                    new ArrayList<>(), new ArrayList<>(List.of(fresh, stale)), nowSeconds);

            assertThat(r.needsDay).hasSize(1);
            assertThat(r.needsDay.get(0).getHangoutId()).isEqualTo("fresh");
            assertThat(r.heldStaleFloats).hasSize(1);
            assertThat(r.heldStaleFloats.get(0).getHangoutId()).isEqualTo("stale");
        }

        @Test
        void nullCreatedAt_treatedAsStale() {
            HangoutSummaryDTO nullCreated = buildHangout("n", "BUILDING", null, List.of(), 0L);
            nullCreated.setCreatedAt(null);

            FeedSortingService.SortResult r = service.sortFeed(
                    new ArrayList<>(), new ArrayList<>(List.of(nullCreated)), nowSeconds);

            assertThat(r.needsDay).isEmpty();
            assertThat(r.heldStaleFloats).hasSize(1);
        }
    }

    // =========================================================================
    // Stale-supported floats: busy-week + surge behavior (old rules)
    // =========================================================================

    @Nested
    class StaleSupportedFloats {

        @Test
        void staleSupported_busyWeek_suppressedWithoutSurge() {
            long ts = inSeconds(TimeUnit.DAYS.toSeconds(5));
            HangoutSummaryDTO c = buildHangout("c", "CONFIRMED", ts);
            List<InterestLevel> oldSupport = List.of(
                    buildInterestLevel("u1", nowSeconds - TimeUnit.DAYS.toSeconds(3)),
                    buildInterestLevel("u2", nowSeconds - TimeUnit.DAYS.toSeconds(3)));
            HangoutSummaryDTO b = buildStaleHangout("b", "BUILDING", ts, oldSupport);

            FeedSortingService.SortResult r = service.sortFeed(
                    new ArrayList<>(List.of(c, b)), new ArrayList<>(), nowSeconds);

            assertThat(ids(r.withDay)).containsExactly("c");
            assertThat(r.heldStaleFloats).isEmpty(); // stale-supported is not held
        }

        @Test
        void staleSupported_busyWeek_surging_surfaces() {
            long ts = inSeconds(TimeUnit.DAYS.toSeconds(5));
            HangoutSummaryDTO c = buildHangout("c", "CONFIRMED", ts);
            List<InterestLevel> surging = List.of(
                    buildInterestLevel("u1", nowSeconds - TimeUnit.HOURS.toSeconds(2)),
                    buildInterestLevel("u2", nowSeconds - TimeUnit.HOURS.toSeconds(5)));
            HangoutSummaryDTO b = buildStaleHangout("b", "BUILDING", ts, surging);

            FeedSortingService.SortResult r = service.sortFeed(
                    new ArrayList<>(List.of(c, b)), new ArrayList<>(), nowSeconds);

            assertThat(ids(r.withDay)).contains("b");
            HangoutSummaryDTO bOut = (HangoutSummaryDTO) r.withDay.stream()
                    .filter(i -> "b".equals(((HangoutSummaryDTO) i).getHangoutId())).findFirst().get();
            assertThat(bOut.getSurfaceReason()).isEqualTo(FeedSortingService.REASON_SUPPORTED_FLOAT);
        }

        @Test
        void staleSupported_emptyWeek_surfaces() {
            long ts = inSeconds(TimeUnit.DAYS.toSeconds(5));
            List<InterestLevel> support = List.of(
                    buildInterestLevel("u1", nowSeconds - TimeUnit.DAYS.toSeconds(3)),
                    buildInterestLevel("u2", nowSeconds - TimeUnit.DAYS.toSeconds(3)));
            HangoutSummaryDTO b = buildStaleHangout("b", "BUILDING", ts, support);

            FeedSortingService.SortResult r = service.sortFeed(
                    new ArrayList<>(List.of(b)), new ArrayList<>(), nowSeconds);

            assertThat(ids(r.withDay)).containsExactly("b");
        }

        @Test
        void imminentStaleSupported_surfacesEvenWithConfirmed() {
            // Imminent horizon is suppression-exempt.
            long ts = inSeconds(TimeUnit.HOURS.toSeconds(10));
            HangoutSummaryDTO c = buildHangout("c", "CONFIRMED", ts);
            List<InterestLevel> support = List.of(
                    buildInterestLevel("u1", nowSeconds - TimeUnit.DAYS.toSeconds(3)),
                    buildInterestLevel("u2", nowSeconds - TimeUnit.DAYS.toSeconds(3)));
            HangoutSummaryDTO b = buildStaleHangout("b", "BUILDING", ts, support);

            FeedSortingService.SortResult r = service.sortFeed(
                    new ArrayList<>(List.of(c, b)), new ArrayList<>(), nowSeconds);

            assertThat(ids(r.withDay)).containsExactlyInAnyOrder("c", "b");
        }

        @Test
        void distantStaleSupported_surfacesEvenWithConfirmed() {
            long ts = inSeconds(TimeUnit.DAYS.toSeconds(30));
            HangoutSummaryDTO c = buildHangout("c", "CONFIRMED", ts);
            List<InterestLevel> support = List.of(
                    buildInterestLevel("u1", nowSeconds - TimeUnit.DAYS.toSeconds(3)),
                    buildInterestLevel("u2", nowSeconds - TimeUnit.DAYS.toSeconds(3)));
            HangoutSummaryDTO b = buildStaleHangout("b", "BUILDING", ts, support);

            FeedSortingService.SortResult r = service.sortFeed(
                    new ArrayList<>(List.of(c, b)), new ArrayList<>(), nowSeconds);

            assertThat(ids(r.withDay)).containsExactlyInAnyOrder("c", "b");
        }
    }

    // =========================================================================
    // Surface-reason labeling
    // =========================================================================

    @Nested
    class SurfaceReasons {

        @Test
        void confirmedItemHasConfirmedReason() {
            HangoutSummaryDTO c = buildHangout("c", "CONFIRMED", inSeconds(TimeUnit.DAYS.toSeconds(3)));
            FeedSortingService.SortResult r = service.sortFeed(
                    new ArrayList<>(List.of(c)), new ArrayList<>(), nowSeconds);
            assertThat(((HangoutSummaryDTO) r.withDay.get(0)).getSurfaceReason())
                    .isEqualTo(FeedSortingService.REASON_CONFIRMED);
        }

        @Test
        void gainingItemHasGainingReason() {
            HangoutSummaryDTO g = buildHangout("g", "GAINING_MOMENTUM", inSeconds(TimeUnit.DAYS.toSeconds(3)));
            FeedSortingService.SortResult r = service.sortFeed(
                    new ArrayList<>(List.of(g)), new ArrayList<>(), nowSeconds);
            assertThat(((HangoutSummaryDTO) r.withDay.get(0)).getSurfaceReason())
                    .isEqualTo(FeedSortingService.REASON_GAINING);
        }

        @Test
        void legacyNullMomentumTreatedAsConfirmed() {
            HangoutSummaryDTO legacy = buildHangout("l", null, inSeconds(TimeUnit.DAYS.toSeconds(3)));
            FeedSortingService.SortResult r = service.sortFeed(
                    new ArrayList<>(List.of(legacy)), new ArrayList<>(), nowSeconds);
            assertThat(((HangoutSummaryDTO) r.withDay.get(0)).getSurfaceReason())
                    .isEqualTo(FeedSortingService.REASON_CONFIRMED);
        }
    }

    // =========================================================================
    // needsDay ordering
    // =========================================================================

    @Nested
    class NeedsDay {

        @Test
        void confirmedBeforeGainingBeforeBuilding() {
            HangoutSummaryDTO b = buildHangout("b", "BUILDING",         null);
            HangoutSummaryDTO c = buildHangout("c", "CONFIRMED",        null);
            HangoutSummaryDTO g = buildHangout("g", "GAINING_MOMENTUM", null);

            FeedSortingService.SortResult r = service.sortFeed(
                    new ArrayList<>(), new ArrayList<>(List.of(b, g, c)), nowSeconds);

            List<String> ids = r.needsDay.stream().map(HangoutSummaryDTO::getHangoutId).toList();
            assertThat(ids.indexOf("c")).isLessThan(ids.indexOf("g"));
            assertThat(ids.indexOf("g")).isLessThan(ids.indexOf("b"));
        }

        @Test
        void legacyNullMomentumTreatedAsConfirmed_inNeedsDay() {
            HangoutSummaryDTO legacy   = buildHangout("legacy", null, null);
            HangoutSummaryDTO building = buildHangout("b", "BUILDING", null);

            FeedSortingService.SortResult r = service.sortFeed(
                    new ArrayList<>(), new ArrayList<>(List.of(building, legacy)), nowSeconds);

            List<String> ids = r.needsDay.stream().map(HangoutSummaryDTO::getHangoutId).toList();
            assertThat(ids.indexOf("legacy")).isLessThan(ids.indexOf("b"));
        }
    }

    // =========================================================================
    // Introspection helpers
    // =========================================================================

    @Nested
    class Introspection {
        @Test
        void isZeroSupport_nullLevels() {
            HangoutSummaryDTO h = buildHangout("h", "BUILDING", null);
            h.setInterestLevels(null);
            assertThat(service.isZeroSupport(h)).isTrue();
        }

        @Test
        void isZeroSupport_singleLevel() {
            HangoutSummaryDTO h = buildHangout("h", "BUILDING", null,
                    List.of(buildInterestLevel("u1", nowSeconds)));
            assertThat(service.isZeroSupport(h)).isTrue();
        }

        @Test
        void isZeroSupport_twoLevels() {
            HangoutSummaryDTO h = buildHangout("h", "BUILDING", null, List.of(
                    buildInterestLevel("u1", nowSeconds),
                    buildInterestLevel("u2", nowSeconds)));
            assertThat(service.isZeroSupport(h)).isFalse();
        }

        @Test
        void isFresh_createdRecently_true() {
            HangoutSummaryDTO h = buildHangout("h", "BUILDING", null, List.of(),
                    nowSeconds - TimeUnit.DAYS.toSeconds(1));
            assertThat(service.isFresh(h, nowSeconds)).isTrue();
        }

        @Test
        void isFresh_createdLongAgo_false() {
            HangoutSummaryDTO h = buildHangout("h", "BUILDING", null, List.of(),
                    nowSeconds - TimeUnit.DAYS.toSeconds(10));
            assertThat(service.isFresh(h, nowSeconds)).isFalse();
        }

        @Test
        void isFresh_nullCreatedAt_false() {
            HangoutSummaryDTO h = buildHangout("h", "BUILDING", null);
            h.setCreatedAt(null);
            assertThat(service.isFresh(h, nowSeconds)).isFalse();
        }

        @Test
        void hasRecentSupportSurge_twoSignals24h_true() {
            HangoutSummaryDTO h = buildHangout("h", "BUILDING", null, List.of(
                    buildInterestLevel("u1", nowSeconds - TimeUnit.HOURS.toSeconds(3)),
                    buildInterestLevel("u2", nowSeconds - TimeUnit.HOURS.toSeconds(5))));
            assertThat(service.hasRecentSupportSurge(h, nowSeconds)).isTrue();
        }

        @Test
        void hasRecentSupportSurge_singleRecent_false() {
            HangoutSummaryDTO h = buildHangout("h", "BUILDING", null, List.of(
                    buildInterestLevel("u1", nowSeconds - TimeUnit.HOURS.toSeconds(3)),
                    buildInterestLevel("u2", nowSeconds - TimeUnit.DAYS.toSeconds(5))));
            assertThat(service.hasRecentSupportSurge(h, nowSeconds)).isFalse();
        }
    }

    // =========================================================================
    // Empty input
    // =========================================================================

    @Test
    void emptyLists_returnsEmptyResult() {
        FeedSortingService.SortResult r = service.sortFeed(
                new ArrayList<>(), new ArrayList<>(), nowSeconds);
        assertThat(r.withDay).isEmpty();
        assertThat(r.needsDay).isEmpty();
        assertThat(r.heldStaleFloats).isEmpty();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static List<String> ids(List<FeedItem> items) {
        return items.stream()
                .map(i -> i instanceof HangoutSummaryDTO h ? h.getHangoutId()
                        : ((SeriesSummaryDTO) i).getSeriesId())
                .toList();
    }
}
