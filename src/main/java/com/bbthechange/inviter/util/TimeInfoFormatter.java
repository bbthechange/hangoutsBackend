package com.bbthechange.inviter.util;

import com.bbthechange.inviter.dto.TimeInfo;

import java.time.Instant;
import java.time.ZonedDateTime;

/**
 * Normalizes TimeInfo for API responses: emits only the relevant pair
 * (fuzzy or exact) and converts ISO-8601 timestamps to UTC (Z-suffixed).
 */
public final class TimeInfoFormatter {

    private TimeInfoFormatter() {}

    public static TimeInfo forResponse(TimeInfo timeInfo) {
        if (timeInfo == null) return null;

        TimeInfo out = new TimeInfo();
        if (timeInfo.getPeriodGranularity() != null) {
            out.setPeriodGranularity(timeInfo.getPeriodGranularity());
            out.setPeriodStart(toUtcIsoString(timeInfo.getPeriodStart()));
        } else if (timeInfo.getStartTime() != null) {
            out.setStartTime(toUtcIsoString(timeInfo.getStartTime()));
            if (timeInfo.getEndTime() != null) {
                out.setEndTime(toUtcIsoString(timeInfo.getEndTime()));
            }
        }
        return out;
    }

    private static String toUtcIsoString(String timeString) {
        if (timeString == null) return null;
        try {
            if (timeString.contains("T")) {
                return ZonedDateTime.parse(timeString).toInstant().toString();
            } else if (timeString.matches("\\d+")) {
                return Instant.ofEpochSecond(Long.parseLong(timeString)).toString();
            }
            return timeString;
        } catch (Exception e) {
            return timeString;
        }
    }
}
