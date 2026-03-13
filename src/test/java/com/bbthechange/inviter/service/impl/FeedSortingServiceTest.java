package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.FeedItem;
import com.bbthechange.inviter.dto.HangoutSummaryDTO;
import com.bbthechange.inviter.dto.MomentumDTO;
import com.bbthechange.inviter.dto.SeriesSummaryDTO;
import com.bbthechange.inviter.model.InterestLevel;
import com.bbthechange.inviter.model.MomentumCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for FeedSortingService slot-based interleaving.
 *
 * Coverage:
 * - Time horizon bucketing (Imminent, Near-term, Mid-term, Distant)
 * - Per-horizon sort order: CONFIRMED → GAINING_MOMENTUM → BUILDING
 * - Series items treated as CONFIRMED
 * - Zero-support cap (max 2 across entire feed)
 * - Busy week suppression of BUILDING items (confirmed events present)
 * - Empty week auto-surfacing (no confirmed events)
 * - Recent support surge (2+ signals in 24h) always surfaces
 * - needsDay items ordered by momentum category
 */
class FeedSortingServiceTest {

    private FeedSortingService service;
    private long nowSeconds;

    @BeforeEach
    void setUp() {
        service = new FeedSortingService();
        nowSeconds = Instant.now().getEpochSecond();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private HangoutSummaryDTO buildHangout(String id, String category, Long startTimestamp) {
        return buildHangout(id, category, startTimestamp, List.of());
    }

    private HangoutSummaryDTO buildHangout(String id, String category, Long startTimestamp,
                                            List<InterestLevel> interestLevels) {
        HangoutSummaryDTO dto = HangoutSummaryDTO.builder()
                .withHangoutId(id)
                .withTitle("Hangout " + id)
                .withStartTimestamp(startTimestamp)
                .withInterestLevels(interestLevels)
                .build();

        if (category != null) {
            MomentumDTO momentum = new MomentumDTO();
            momentum.setCategory(category);
            momentum.setScore(5);
            dto.setMomentum(momentum);
        }
        return dto;
    }

    private SeriesSummaryDTO buildSeries(String id, Long startTimestamp) {
        SeriesSummaryDTO dto = new SeriesSummaryDTO();
        dto.setSeriesId(id);
        dto.setSeriesTitle("Series " + id);
        dto.setStartTimestamp(startTimestamp);
        return dto;
    }

    private InterestLevel buildInterestLevel(String userId, long updatedAtSeconds) {
        // Use default constructor to avoid UUID validation in the full constructor
        InterestLevel il = new InterestLevel();
        il.setUserId(userId);
        il.setUserName("User");
        il.setStatus("GOING");
        il.setUpdatedAt(Instant.ofEpochSecond(updatedAtSeconds));
        return il;
    }

    private long inSeconds(long secondsFromNow) {
        return nowSeconds + secondsFromNow;
    }

    // -------------------------------------------------------------------------
    // 1. Time horizon bucketing and per-horizon sort order
    // -------------------------------------------------------------------------

    @Nested
    class TimeHorizonSorting {

        @Test
        void sortFeed_imminentItemsFirst_confirmedBeforeBuilding() {
            // Imminent: < 48h
            long imminent = inSeconds(TimeUnit.HOURS.toSeconds(10));
            // Near-term: 3-7 days
            long nearTerm = inSeconds(TimeUnit.DAYS.toSeconds(5));

            HangoutSummaryDTO imminentBuilding  = buildHangout("ib", "BUILDING",  imminent);
            HangoutSummaryDTO imminentConfirmed = buildHangout("ic", "CONFIRMED", imminent);
            HangoutSummaryDTO nearTermConfirmed = buildHangout("nc", "CONFIRMED", nearTerm);

            List<FeedItem> withDay = new ArrayList<>(List.of(imminentBuilding, imminentConfirmed, nearTermConfirmed));
            List<HangoutSummaryDTO> needsDay = new ArrayList<>();

            FeedSortingService.SortResult result = service.sortFeed(withDay, needsDay, nowSeconds);

            // Imminent CONFIRMED should come before Imminent BUILDING
            // Then Near-term CONFIRMED
            List<String> ids = result.withDay.stream()
                    .map(i -> ((HangoutSummaryDTO) i).getHangoutId())
                    .toList();
            assertThat(ids.indexOf("ic")).isLessThan(ids.indexOf("ib"));
            assertThat(ids.indexOf("ic")).isLessThan(ids.indexOf("nc"));
        }

        @Test
        void sortFeed_withinHorizon_confirmedBeforeGainingBeforeBuilding() {
            long ts = inSeconds(TimeUnit.DAYS.toSeconds(5)); // near-term

            // BUILDING item has a recent surge (2+ in 24h) so it passes busy-week suppression
            List<InterestLevel> recentSupport = List.of(
                    buildInterestLevel("u1", nowSeconds - TimeUnit.HOURS.toSeconds(2)),
                    buildInterestLevel("u2", nowSeconds - TimeUnit.HOURS.toSeconds(3))
            );
            HangoutSummaryDTO building       = buildHangout("b", "BUILDING",         ts, recentSupport);
            HangoutSummaryDTO confirmed      = buildHangout("c", "CONFIRMED",        ts);
            HangoutSummaryDTO gaining        = buildHangout("g", "GAINING_MOMENTUM", ts);

            List<FeedItem> withDay = new ArrayList<>(List.of(building, gaining, confirmed));
            FeedSortingService.SortResult result = service.sortFeed(withDay, new ArrayList<>(), nowSeconds);

            List<String> ids = result.withDay.stream()
                    .map(i -> ((HangoutSummaryDTO) i).getHangoutId())
                    .toList();

            assertThat(ids.indexOf("c")).isLessThan(ids.indexOf("g"));
            assertThat(ids.indexOf("g")).isLessThan(ids.indexOf("b"));
        }

        @Test
        void sortFeed_distantItemsLast() {
            long imminent = inSeconds(TimeUnit.HOURS.toSeconds(5));
            long distant  = inSeconds(TimeUnit.DAYS.toSeconds(30));

            HangoutSummaryDTO imminentItem = buildHangout("imm", "CONFIRMED", imminent);
            HangoutSummaryDTO distantItem  = buildHangout("dis", "CONFIRMED", distant);

            List<FeedItem> withDay = new ArrayList<>(List.of(distantItem, imminentItem));
            FeedSortingService.SortResult result = service.sortFeed(withDay, new ArrayList<>(), nowSeconds);

            List<String> ids = result.withDay.stream()
                    .map(i -> ((HangoutSummaryDTO) i).getHangoutId())
                    .toList();
            assertThat(ids.indexOf("imm")).isLessThan(ids.indexOf("dis"));
        }
    }

    // -------------------------------------------------------------------------
    // 1b. Additional time horizon tests
    // -------------------------------------------------------------------------

    @Nested
    class TimeHorizonBoundaries {

        @Test
        void sortFeed_itemAtNearTermBoundary_bucketsCorrectly() {
            // An item exactly at nowSeconds + NEAR_TERM_SECONDS lands in NEAR_TERM, not imminent or mid-term
            long atBoundary = inSeconds(FeedSortingService.NEAR_TERM_SECONDS);
            long beyondBoundary = inSeconds(FeedSortingService.NEAR_TERM_SECONDS + 1);

            HangoutSummaryDTO nearTermItem = buildHangout("nt", "CONFIRMED", atBoundary);
            HangoutSummaryDTO midTermItem  = buildHangout("mt", "CONFIRMED", beyondBoundary);

            List<FeedItem> withDay = new ArrayList<>(List.of(midTermItem, nearTermItem));
            FeedSortingService.SortResult result = service.sortFeed(withDay, new ArrayList<>(), nowSeconds);

            List<String> ids = result.withDay.stream()
                    .map(i -> ((HangoutSummaryDTO) i).getHangoutId())
                    .toList();
            // Near-term item should come before mid-term item
            assertThat(ids.indexOf("nt")).isLessThan(ids.indexOf("mt"));
        }

        @Test
        void sortFeed_nullTimestamp_inWithDayList_goesToDistant() {
            // An item in withDay with null timestamp (guarded case) goes to distant bucket
            long imminentTs = inSeconds(TimeUnit.HOURS.toSeconds(5));

            HangoutSummaryDTO nullTsItem   = buildHangout("null-ts", "CONFIRMED", null);
            HangoutSummaryDTO imminentItem = buildHangout("imminent", "CONFIRMED", imminentTs);

            List<FeedItem> withDay = new ArrayList<>(List.of(nullTsItem, imminentItem));
            FeedSortingService.SortResult result = service.sortFeed(withDay, new ArrayList<>(), nowSeconds);

            List<String> ids = result.withDay.stream()
                    .map(i -> ((HangoutSummaryDTO) i).getHangoutId())
                    .toList();
            // Imminent item (has a real timestamp) should come before the null-timestamp item
            assertThat(ids.indexOf("imminent")).isLessThan(ids.indexOf("null-ts"));
        }
    }

    // -------------------------------------------------------------------------
    // 2. Series items treated as CONFIRMED
    // -------------------------------------------------------------------------

    @Nested
    class SeriesAreConfirmed {

        @Test
        void sortFeed_seriesItemSortsBeforeBuilding_inSameHorizon() {
            // Use imminent horizon so busy-week suppression does not apply
            long ts = inSeconds(TimeUnit.HOURS.toSeconds(10)); // imminent < 48h

            SeriesSummaryDTO series    = buildSeries("s1", ts);
            HangoutSummaryDTO building = buildHangout("b",  "BUILDING", ts);

            List<FeedItem> withDay = new ArrayList<>(List.of(building, series));
            FeedSortingService.SortResult result = service.sortFeed(withDay, new ArrayList<>(), nowSeconds);

            List<Object> items = result.withDay.stream().<Object>map(i -> i).toList();
            assertThat(items.get(0)).isSameAs(series);
            assertThat(items.get(1)).isSameAs(building);
        }

        @Test
        void getMomentumCategory_seriesReturnsConfirmed() {
            SeriesSummaryDTO series = buildSeries("s1", nowSeconds + 1000);
            assertThat(service.getMomentumCategory(series)).isEqualTo("CONFIRMED");
        }

        @Test
        void getMomentumCategory_hangoutWithNullMomentum_returnsNull() {
            HangoutSummaryDTO h = buildHangout("h", null, nowSeconds + 1000);
            assertThat(service.getMomentumCategory(h)).isNull();
        }
    }

    // -------------------------------------------------------------------------
    // 3. Zero-support cap
    // -------------------------------------------------------------------------

    @Nested
    class ZeroSupportCap {

        @Test
        void sortFeed_capsTwoZeroSupportItemsAcrossEntireFeed() {
            // Three BUILDING zero-support items in different horizons
            long ts1 = inSeconds(TimeUnit.HOURS.toSeconds(10));  // imminent
            long ts2 = inSeconds(TimeUnit.DAYS.toSeconds(5));    // near-term
            long ts3 = inSeconds(TimeUnit.DAYS.toSeconds(15));   // mid-term

            HangoutSummaryDTO zs1 = buildHangout("z1", "BUILDING", ts1, List.of());
            HangoutSummaryDTO zs2 = buildHangout("z2", "BUILDING", ts2, List.of());
            HangoutSummaryDTO zs3 = buildHangout("z3", "BUILDING", ts3, List.of());

            List<FeedItem> withDay = new ArrayList<>(List.of(zs1, zs2, zs3));
            FeedSortingService.SortResult result = service.sortFeed(withDay, new ArrayList<>(), nowSeconds);

            // At most 2 zero-support items should appear
            long zeroSupportCount = result.withDay.stream()
                    .filter(i -> i instanceof HangoutSummaryDTO h && service.isZeroSupport(h))
                    .count();
            assertThat(zeroSupportCount).isLessThanOrEqualTo(2);
        }

        @Test
        void sortFeed_supportedBuildingItemsNotCappedByZeroSupportRule() {
            // Three BUILDING items with support (2+ interest levels)
            long ts = inSeconds(TimeUnit.DAYS.toSeconds(5));
            List<InterestLevel> supported = List.of(
                    buildInterestLevel("u1", nowSeconds - 1000),
                    buildInterestLevel("u2", nowSeconds - 1000)
            );
            HangoutSummaryDTO h1 = buildHangout("s1", "BUILDING", ts, supported);
            HangoutSummaryDTO h2 = buildHangout("s2", "BUILDING", ts, supported);
            HangoutSummaryDTO h3 = buildHangout("s3", "BUILDING", ts, supported);

            List<FeedItem> withDay = new ArrayList<>(List.of(h1, h2, h3));
            FeedSortingService.SortResult result = service.sortFeed(withDay, new ArrayList<>(), nowSeconds);

            // All 3 should be present since they have support
            assertThat(result.withDay).hasSize(3);
        }

        @Test
        void sortFeed_zeroSupportCap_sharedAcrossHorizons() {
            // One zero-support item in near-term, two in mid-term — only 2 total should pass
            long nearTs  = inSeconds(TimeUnit.DAYS.toSeconds(5));
            long midTs1  = inSeconds(TimeUnit.DAYS.toSeconds(12));
            long midTs2  = inSeconds(TimeUnit.DAYS.toSeconds(14));

            HangoutSummaryDTO nearZs  = buildHangout("near",  "BUILDING", nearTs,  List.of());
            HangoutSummaryDTO midZs1  = buildHangout("mid1",  "BUILDING", midTs1,  List.of());
            HangoutSummaryDTO midZs2  = buildHangout("mid2",  "BUILDING", midTs2,  List.of());

            List<FeedItem> withDay = new ArrayList<>(List.of(nearZs, midZs1, midZs2));
            FeedSortingService.SortResult result = service.sortFeed(withDay, new ArrayList<>(), nowSeconds);

            long zeroSupportCount = result.withDay.stream()
                    .filter(i -> i instanceof HangoutSummaryDTO h && service.isZeroSupport(h))
                    .count();
            assertThat(zeroSupportCount).isEqualTo(2);
            assertThat(result.withDay).hasSize(2);
        }

        @Test
        void isZeroSupport_nullInterestLevels_returnsTrue() {
            // HangoutSummaryDTO with null interestLevels field (not just empty list)
            HangoutSummaryDTO h = buildHangout("h", "BUILDING", null);
            // buildHangout without interestLevels arg doesn't set them — confirm null is handled
            h.setInterestLevels(null);
            assertThat(service.isZeroSupport(h)).isTrue();
        }

        @Test
        void isZeroSupport_emptyInterestLevels_returnsTrue() {
            HangoutSummaryDTO h = buildHangout("h", "BUILDING", null, List.of());
            assertThat(service.isZeroSupport(h)).isTrue();
        }

        @Test
        void isZeroSupport_singleInterestLevel_returnsTrue() {
            List<InterestLevel> oneLevel = List.of(buildInterestLevel("u1", nowSeconds));
            HangoutSummaryDTO h = buildHangout("h", "BUILDING", null, oneLevel);
            assertThat(service.isZeroSupport(h)).isTrue();
        }

        @Test
        void isZeroSupport_twoInterestLevels_returnsFalse() {
            List<InterestLevel> twoLevels = List.of(
                    buildInterestLevel("u1", nowSeconds),
                    buildInterestLevel("u2", nowSeconds)
            );
            HangoutSummaryDTO h = buildHangout("h", "BUILDING", null, twoLevels);
            assertThat(service.isZeroSupport(h)).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // 4. Busy week suppression
    // -------------------------------------------------------------------------

    @Nested
    class BusyWeekSuppression {

        @Test
        void sortFeed_busyWeek_suppressesBuildingItemsWithoutRecentSurge() {
            long ts = inSeconds(TimeUnit.DAYS.toSeconds(5)); // same near-term horizon

            HangoutSummaryDTO confirmed = buildHangout("c", "CONFIRMED", ts);
            // Old BUILDING item — no recent support surge
            List<InterestLevel> oldSupport = List.of(
                    buildInterestLevel("u1", nowSeconds - TimeUnit.DAYS.toSeconds(3)),
                    buildInterestLevel("u2", nowSeconds - TimeUnit.DAYS.toSeconds(3))
            );
            HangoutSummaryDTO building = buildHangout("b", "BUILDING", ts, oldSupport);

            List<FeedItem> withDay = new ArrayList<>(List.of(confirmed, building));
            FeedSortingService.SortResult result = service.sortFeed(withDay, new ArrayList<>(), nowSeconds);

            // Busy week: only confirmed should appear (building suppressed)
            assertThat(result.withDay).hasSize(1);
            assertThat(((HangoutSummaryDTO) result.withDay.get(0)).getHangoutId()).isEqualTo("c");
        }

        @Test
        void sortFeed_imminentHorizon_buildingItemAlwaysSurfaces_notSuppressed() {
            // Imminent horizon (< 48h) is exempt from busy-week suppression
            long ts = inSeconds(TimeUnit.HOURS.toSeconds(10)); // imminent

            HangoutSummaryDTO confirmed = buildHangout("c", "CONFIRMED", ts);
            // BUILDING item with no recent surge — would be suppressed in near-term, but not imminent
            HangoutSummaryDTO building = buildHangout("b", "BUILDING", ts, List.of());

            List<FeedItem> withDay = new ArrayList<>(List.of(confirmed, building));
            FeedSortingService.SortResult result = service.sortFeed(withDay, new ArrayList<>(), nowSeconds);

            // Both items appear — imminent horizon bypasses busy-week suppression
            List<String> ids = result.withDay.stream()
                    .map(i -> ((HangoutSummaryDTO) i).getHangoutId())
                    .toList();
            assertThat(ids).containsExactlyInAnyOrder("c", "b");
        }

        @Test
        void sortFeed_busyWeek_keepsRecentlySurgingBuildingItem() {
            long ts = inSeconds(TimeUnit.DAYS.toSeconds(5));

            HangoutSummaryDTO confirmed = buildHangout("c", "CONFIRMED", ts);
            // Recently surging: 2 signals in last 24h
            List<InterestLevel> recentSupport = List.of(
                    buildInterestLevel("u1", nowSeconds - TimeUnit.HOURS.toSeconds(2)),
                    buildInterestLevel("u2", nowSeconds - TimeUnit.HOURS.toSeconds(2))
            );
            HangoutSummaryDTO building = buildHangout("b", "BUILDING", ts, recentSupport);

            List<FeedItem> withDay = new ArrayList<>(List.of(confirmed, building));
            FeedSortingService.SortResult result = service.sortFeed(withDay, new ArrayList<>(), nowSeconds);

            // Both should appear: confirmed + recently-surging building
            assertThat(result.withDay).hasSize(2);
        }
    }

    // -------------------------------------------------------------------------
    // 5. Empty week auto-surfacing
    // -------------------------------------------------------------------------

    @Nested
    class EmptyWeekAutoSurfacing {

        @Test
        void sortFeed_emptyWeek_autosurfacesBestCandidate() {
            long ts = inSeconds(TimeUnit.DAYS.toSeconds(5));

            // Two BUILDING items — different last-support timestamps
            List<InterestLevel> olderSupport = List.of(
                    buildInterestLevel("u1", nowSeconds - TimeUnit.DAYS.toSeconds(2)),
                    buildInterestLevel("u2", nowSeconds - TimeUnit.DAYS.toSeconds(2))
            );
            List<InterestLevel> recentSupport = List.of(
                    buildInterestLevel("u3", nowSeconds - TimeUnit.HOURS.toSeconds(5)),
                    buildInterestLevel("u4", nowSeconds - TimeUnit.HOURS.toSeconds(5))
            );

            HangoutSummaryDTO olderItem  = buildHangout("old", "BUILDING", ts, olderSupport);
            HangoutSummaryDTO recentItem = buildHangout("new", "BUILDING", ts, recentSupport);

            List<FeedItem> withDay = new ArrayList<>(List.of(olderItem, recentItem));
            FeedSortingService.SortResult result = service.sortFeed(withDay, new ArrayList<>(), nowSeconds);

            // Both items should be present (empty week, no suppression)
            assertThat(result.withDay).hasSize(2);
        }

        @Test
        void sortFeed_emptyWeekWithSingleCandidate_includesIt() {
            long ts = inSeconds(TimeUnit.DAYS.toSeconds(5));
            HangoutSummaryDTO building = buildHangout("b", "BUILDING", ts);

            List<FeedItem> withDay = new ArrayList<>(List.of(building));
            FeedSortingService.SortResult result = service.sortFeed(withDay, new ArrayList<>(), nowSeconds);

            assertThat(result.withDay).hasSize(1);
        }
    }

    // -------------------------------------------------------------------------
    // 6. Recent support surge (2+ signals in 24h) — always shows
    // -------------------------------------------------------------------------

    @Nested
    class RecentSupportSurge {

        @Test
        void hasRecentSupportSurge_twoSignalsInLast24h_returnsTrue() {
            List<InterestLevel> levels = List.of(
                    buildInterestLevel("u1", nowSeconds - TimeUnit.HOURS.toSeconds(3)),
                    buildInterestLevel("u2", nowSeconds - TimeUnit.HOURS.toSeconds(5))
            );
            HangoutSummaryDTO h = buildHangout("h", "BUILDING", null, levels);
            assertThat(service.hasRecentSupportSurge(h, nowSeconds)).isTrue();
        }

        @Test
        void hasRecentSupportSurge_onlyOneSignalInLast24h_returnsFalse() {
            List<InterestLevel> levels = List.of(
                    buildInterestLevel("u1", nowSeconds - TimeUnit.HOURS.toSeconds(3)),
                    buildInterestLevel("u2", nowSeconds - TimeUnit.DAYS.toSeconds(5)) // old
            );
            HangoutSummaryDTO h = buildHangout("h", "BUILDING", null, levels);
            assertThat(service.hasRecentSupportSurge(h, nowSeconds)).isFalse();
        }

        @Test
        void hasRecentSupportSurge_noInterestLevels_returnsFalse() {
            HangoutSummaryDTO h = buildHangout("h", "BUILDING", null, List.of());
            assertThat(service.hasRecentSupportSurge(h, nowSeconds)).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // 7. needsDay items ordered by momentum category
    // -------------------------------------------------------------------------

    @Nested
    class NeedsDaySorting {

        @Test
        void sortFeed_needsDay_confirmedBeforeGainingBeforeBuilding() {
            HangoutSummaryDTO building  = buildHangout("b", "BUILDING",         null);
            HangoutSummaryDTO confirmed = buildHangout("c", "CONFIRMED",        null);
            HangoutSummaryDTO gaining   = buildHangout("g", "GAINING_MOMENTUM", null);

            List<HangoutSummaryDTO> needsDay = new ArrayList<>(List.of(building, gaining, confirmed));
            FeedSortingService.SortResult result = service.sortFeed(new ArrayList<>(), needsDay, nowSeconds);

            List<String> ids = result.needsDay.stream().map(HangoutSummaryDTO::getHangoutId).toList();
            assertThat(ids.indexOf("c")).isLessThan(ids.indexOf("g"));
            assertThat(ids.indexOf("g")).isLessThan(ids.indexOf("b"));
        }

        @Test
        void sortFeed_needsDay_zeroSupportCapApplied() {
            HangoutSummaryDTO zs1 = buildHangout("z1", "BUILDING", null, List.of());
            HangoutSummaryDTO zs2 = buildHangout("z2", "BUILDING", null, List.of());
            HangoutSummaryDTO zs3 = buildHangout("z3", "BUILDING", null, List.of());

            List<HangoutSummaryDTO> needsDay = new ArrayList<>(List.of(zs1, zs2, zs3));
            FeedSortingService.SortResult result = service.sortFeed(new ArrayList<>(), needsDay, nowSeconds);

            assertThat(result.needsDay).hasSize(2);
        }

        @Test
        void sortFeed_needsDay_zeroSupportCountSharedWithWithDay() {
            // 2 zero-support items in withDay already hit the cap
            long ts = inSeconds(TimeUnit.DAYS.toSeconds(5));
            HangoutSummaryDTO withDayZs1 = buildHangout("wd1", "BUILDING", ts, List.of());
            HangoutSummaryDTO withDayZs2 = buildHangout("wd2", "BUILDING", ts, List.of());

            // 1 zero-support item in needsDay — should be excluded since cap already hit
            HangoutSummaryDTO needsDayZs = buildHangout("nd1", "BUILDING", null, List.of());

            FeedSortingService.SortResult result = service.sortFeed(
                    new ArrayList<>(List.of(withDayZs1, withDayZs2)),
                    new ArrayList<>(List.of(needsDayZs)),
                    nowSeconds);

            // withDay got 2 zero-support items (cap reached), needsDay should get 0
            long withDayZsCount = result.withDay.stream()
                    .filter(i -> service.isZeroSupport((FeedItem) i))
                    .count();
            assertThat(withDayZsCount).isEqualTo(2);
            assertThat(result.needsDay).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // 8. Empty feed
    // -------------------------------------------------------------------------

    @Test
    void sortFeed_emptyLists_returnsEmptyResult() {
        FeedSortingService.SortResult result = service.sortFeed(
                new ArrayList<>(), new ArrayList<>(), nowSeconds);

        assertThat(result.withDay).isEmpty();
        assertThat(result.needsDay).isEmpty();
    }

    // -------------------------------------------------------------------------
    // 9. Chronological within same horizon+category
    // -------------------------------------------------------------------------

    @Test
    void sortFeed_confirmedItems_chronologicalWithinHorizon() {
        long earlier = inSeconds(TimeUnit.DAYS.toSeconds(4));
        long later   = inSeconds(TimeUnit.DAYS.toSeconds(6));

        HangoutSummaryDTO c1 = buildHangout("c1", "CONFIRMED", later);
        HangoutSummaryDTO c2 = buildHangout("c2", "CONFIRMED", earlier);

        List<FeedItem> withDay = new ArrayList<>(List.of(c1, c2));
        FeedSortingService.SortResult result = service.sortFeed(withDay, new ArrayList<>(), nowSeconds);

        List<String> ids = result.withDay.stream()
                .map(i -> ((HangoutSummaryDTO) i).getHangoutId())
                .toList();
        assertThat(ids.indexOf("c2")).isLessThan(ids.indexOf("c1"));
    }
}
