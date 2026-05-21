package com.bbthechange.inviter.util;

import com.bbthechange.inviter.dto.HangoutDetailData;
import com.bbthechange.inviter.model.InterestLevel;
import com.bbthechange.inviter.model.SeriesPointer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InterestLevelQueriesTest {

    private InterestLevel level(String userId, String status) {
        InterestLevel il = new InterestLevel();
        il.setUserId(userId);
        il.setStatus(status);
        return il;
    }

    private HangoutDetailData detail(List<InterestLevel> attendance) {
        return HangoutDetailData.builder().withAttendance(attendance).build();
    }

    private SeriesPointer pointer(List<InterestLevel> interestLevels) {
        SeriesPointer p = new SeriesPointer();
        p.setInterestLevels(interestLevels);
        return p;
    }

    // ---- goingOrInterestedOnHangout ----

    @Test
    void goingOrInterestedOnHangout_nullDetailData_returnsEmpty() {
        assertThat(InterestLevelQueries.goingOrInterestedOnHangout(null)).isEmpty();
    }

    @Test
    void goingOrInterestedOnHangout_emptyAttendance_returnsEmpty() {
        assertThat(InterestLevelQueries.goingOrInterestedOnHangout(detail(List.of()))).isEmpty();
    }

    @Test
    void goingOrInterestedOnHangout_mixedStatuses_returnsOnlyMatching() {
        List<InterestLevel> attendance = List.of(
            level("u1", "GOING"),
            level("u2", "INTERESTED"),
            level("u3", "NOT_GOING"),
            level("u4", null)
        );
        assertThat(InterestLevelQueries.goingOrInterestedOnHangout(detail(attendance)))
            .containsExactlyInAnyOrder("u1", "u2");
    }

    // ---- notGoingOnHangout ----

    @Test
    void notGoingOnHangout_nullDetailData_returnsEmpty() {
        assertThat(InterestLevelQueries.notGoingOnHangout(null)).isEmpty();
    }

    @Test
    void notGoingOnHangout_emptyAttendance_returnsEmpty() {
        assertThat(InterestLevelQueries.notGoingOnHangout(detail(List.of()))).isEmpty();
    }

    @Test
    void notGoingOnHangout_mixedStatuses_returnsOnlyNotGoing() {
        List<InterestLevel> attendance = List.of(
            level("u1", "GOING"),
            level("u2", "NOT_GOING"),
            level("u3", "INTERESTED"),
            level("u4", "NOT_GOING")
        );
        assertThat(InterestLevelQueries.notGoingOnHangout(detail(attendance)))
            .containsExactlyInAnyOrder("u2", "u4");
    }

    // ---- goingOnHangout ----

    @Test
    void goingOnHangout_nullDetailData_returnsEmpty() {
        assertThat(InterestLevelQueries.goingOnHangout(null)).isEmpty();
    }

    @Test
    void goingOnHangout_emptyAttendance_returnsEmpty() {
        assertThat(InterestLevelQueries.goingOnHangout(detail(List.of()))).isEmpty();
    }

    @Test
    void goingOnHangout_mixedStatuses_returnsOnlyGoing() {
        List<InterestLevel> attendance = List.of(
            level("u1", "GOING"),
            level("u2", "INTERESTED"),
            level("u3", "GOING"),
            level("u4", "NOT_GOING")
        );
        assertThat(InterestLevelQueries.goingOnHangout(detail(attendance)))
            .containsExactlyInAnyOrder("u1", "u3");
    }

    // ---- goingOrInterestedOnSeries ----

    @Test
    void goingOrInterestedOnSeries_nullPointer_returnsEmpty() {
        assertThat(InterestLevelQueries.goingOrInterestedOnSeries(null)).isEmpty();
    }

    @Test
    void goingOrInterestedOnSeries_emptyInterestLevels_returnsEmpty() {
        assertThat(InterestLevelQueries.goingOrInterestedOnSeries(pointer(new ArrayList<>()))).isEmpty();
    }

    @Test
    void goingOrInterestedOnSeries_mixedStatuses_returnsOnlyMatching() {
        List<InterestLevel> levels = List.of(
            level("u1", "GOING"),
            level("u2", "INTERESTED"),
            level("u3", "NOT_GOING")
        );
        assertThat(InterestLevelQueries.goingOrInterestedOnSeries(pointer(new ArrayList<>(levels))))
            .containsExactlyInAnyOrder("u1", "u2");
    }

    // ---- notGoingOnSeries ----

    @Test
    void notGoingOnSeries_nullPointer_returnsEmpty() {
        assertThat(InterestLevelQueries.notGoingOnSeries(null)).isEmpty();
    }

    @Test
    void notGoingOnSeries_emptyInterestLevels_returnsEmpty() {
        assertThat(InterestLevelQueries.notGoingOnSeries(pointer(new ArrayList<>()))).isEmpty();
    }

    @Test
    void notGoingOnSeries_mixedStatuses_returnsOnlyNotGoing() {
        List<InterestLevel> levels = List.of(
            level("u1", "GOING"),
            level("u2", "NOT_GOING"),
            level("u3", "INTERESTED"),
            level("u4", "NOT_GOING")
        );
        assertThat(InterestLevelQueries.notGoingOnSeries(pointer(new ArrayList<>(levels))))
            .containsExactlyInAnyOrder("u2", "u4");
    }

    @Test
    void filtersOutNullUserIds() {
        List<InterestLevel> attendance = List.of(
            level("u1", "GOING"),
            level(null, "GOING")
        );
        Set<String> result = InterestLevelQueries.goingOrInterestedOnHangout(detail(attendance));
        assertThat(result).containsExactly("u1");
    }
}
