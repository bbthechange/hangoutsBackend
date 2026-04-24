package com.bbthechange.inviter.util;

import com.bbthechange.inviter.dto.TimeInfo;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Generates the server-side {@code PollOption.text} for TIME poll options so that clients
 * which don't understand {@code attributeType == "TIME"} can still render the option in a
 * generic poll UI.
 *
 * <p>Rendering uses the timezone embedded in the {@code TimeInfo} ISO-8601 string (which is
 * the creator's timezone in practice, since the client stamps it). UTC is never used as a
 * fallback — the plan explicitly calls this out.
 */
public final class TimePollOptionTextGenerator {

    private static final DateTimeFormatter FUZZY_FMT =
        DateTimeFormatter.ofPattern("EEE M/d", Locale.US);
    private static final DateTimeFormatter EXACT_DATE_FMT =
        DateTimeFormatter.ofPattern("EEE M/d h:mm a", Locale.US);
    private static final DateTimeFormatter EXACT_TIME_FMT =
        DateTimeFormatter.ofPattern("h:mm a", Locale.US);

    private TimePollOptionTextGenerator() {}

    public static String generate(TimeInfo timeInput) {
        if (timeInput == null) {
            return "Time";
        }
        if (timeInput.getPeriodGranularity() != null && timeInput.getPeriodStart() != null) {
            return fuzzyText(timeInput.getPeriodGranularity(), timeInput.getPeriodStart());
        }
        if (timeInput.getStartTime() != null) {
            return exactText(timeInput.getStartTime(), timeInput.getEndTime());
        }
        return "Time";
    }

    private static String fuzzyText(String granularity, String periodStart) {
        ZonedDateTime zdt = ZonedDateTime.parse(periodStart);
        String day = zdt.format(FUZZY_FMT);
        String label = labelFor(granularity);
        return label.isEmpty() ? day : (day + " " + label);
    }

    private static String exactText(String startIso, String endIso) {
        ZonedDateTime start = ZonedDateTime.parse(startIso);
        if (endIso == null || endIso.isBlank()) {
            return start.format(EXACT_DATE_FMT);
        }
        ZonedDateTime end = ZonedDateTime.parse(endIso);
        boolean sameDay = start.toLocalDate().equals(end.toLocalDate())
            && start.getZone().equals(end.getZone());
        if (sameDay) {
            return start.format(EXACT_DATE_FMT) + "–" + end.format(EXACT_TIME_FMT);
        }
        return start.format(EXACT_DATE_FMT) + "–" + end.format(EXACT_DATE_FMT);
    }

    private static String labelFor(String granularity) {
        if (granularity == null) return "";
        switch (granularity.toLowerCase(Locale.US)) {
            case "morning": return "morning";
            case "afternoon": return "afternoon";
            case "evening": return "evening";
            case "night": return "night";
            case "day": return "all day";
            case "weekend": return "weekend";
            default: return granularity;
        }
    }
}
