package com.bbthechange.inviter.util;

import com.bbthechange.inviter.dto.HangoutDetailData;
import com.bbthechange.inviter.model.InterestLevel;
import com.bbthechange.inviter.model.SeriesPointer;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reusable primitives for filtering users by InterestLevel.status across the
 * hangout-attendance list (HangoutDetailData.attendance) and the series-level
 * interest list (SeriesPointer.interestLevels).
 *
 * Used by nudge recipient resolvers (host-nudge, future location-nudge, etc.).
 * Each method returns an empty Set when given null input or an empty list —
 * never null.
 */
public final class InterestLevelQueries {

    private InterestLevelQueries() {}

    public static Set<String> goingOrInterestedOnHangout(HangoutDetailData detailData) {
        if (detailData == null) {
            return Collections.emptySet();
        }
        return filterByStatus(detailData.getAttendance(), "GOING", "INTERESTED");
    }

    public static Set<String> notGoingOnHangout(HangoutDetailData detailData) {
        if (detailData == null) {
            return Collections.emptySet();
        }
        return filterByStatus(detailData.getAttendance(), "NOT_GOING");
    }

    public static Set<String> goingOnHangout(HangoutDetailData detailData) {
        if (detailData == null) {
            return Collections.emptySet();
        }
        return filterByStatus(detailData.getAttendance(), "GOING");
    }

    public static Set<String> goingOrInterestedOnSeries(SeriesPointer pointer) {
        if (pointer == null) {
            return Collections.emptySet();
        }
        return filterByStatus(pointer.getInterestLevels(), "GOING", "INTERESTED");
    }

    public static Set<String> notGoingOnSeries(SeriesPointer pointer) {
        if (pointer == null) {
            return Collections.emptySet();
        }
        return filterByStatus(pointer.getInterestLevels(), "NOT_GOING");
    }

    private static Set<String> filterByStatus(List<InterestLevel> levels, String... statuses) {
        if (levels == null || levels.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> statusSet = Set.of(statuses);
        return levels.stream()
            .filter(il -> il != null && il.getStatus() != null && statusSet.contains(il.getStatus()))
            .map(InterestLevel::getUserId)
            .filter(id -> id != null && !id.isEmpty())
            .collect(Collectors.toSet());
    }
}
