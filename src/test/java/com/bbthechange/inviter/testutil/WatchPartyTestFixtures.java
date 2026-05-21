package com.bbthechange.inviter.testutil;

import com.bbthechange.inviter.model.EventSeries;
import com.bbthechange.inviter.model.EventVisibility;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.util.InviterKeyFactory;

import java.time.Instant;
import java.util.UUID;

/**
 * Shared builders for watch-party Hangout / EventSeries instances with the
 * Phase 1 nudge fields (watchPartyModel, hostNudgeScheduleName,
 * hostNudgeSentAt, lastHostNotificationAt) populated.
 *
 * Downstream phases use these to avoid divergent test-data construction.
 */
public final class WatchPartyTestFixtures {

    private WatchPartyTestFixtures() {}

    /** EventSeries for an in-person watch party. */
    public static EventSeries inPersonSeries() {
        return inPersonSeries(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }

    public static EventSeries inPersonSeries(String seriesId, String groupId) {
        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);
        series.setSeriesTitle("Test Show");
        series.setGroupId(groupId);
        series.setEventSeriesType("WATCH_PARTY");
        series.setWatchPartyModel("IN_PERSON");
        series.setDefaultTime("20:00");
        series.setTimezone("America/Los_Angeles");
        series.setPk(InviterKeyFactory.getSeriesPk(seriesId));
        series.setSk(InviterKeyFactory.getMetadataSk());
        series.setGsi1pk(InviterKeyFactory.getGroupPk(groupId));
        return series;
    }

    /** EventSeries for a virtual watch party. */
    public static EventSeries virtualSeries() {
        return virtualSeries(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }

    public static EventSeries virtualSeries(String seriesId, String groupId) {
        EventSeries series = inPersonSeries(seriesId, groupId);
        series.setWatchPartyModel("VIRTUAL");
        return series;
    }

    /** A Hangout in an in-person watch-party series with the host-nudge fields populated. */
    public static Hangout inPersonHangout() {
        return inPersonHangout(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }

    public static Hangout inPersonHangout(String hangoutId, String seriesId) {
        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setTitle("Episode 1");
        hangout.setSeriesId(seriesId);
        hangout.setVisibility(EventVisibility.INVITE_ONLY);
        // 48h+ in the future, well outside the reminder window.
        long startSeconds = Instant.now().getEpochSecond() + 60L * 60 * 60;
        hangout.setStartTimestamp(startSeconds);
        hangout.setEndTimestamp(startSeconds + 60 * 60);
        hangout.setHostNudgeScheduleName("hostnudge-" + hangoutId);
        hangout.setHostNudgeSentAt(null);
        hangout.setLastHostNotificationAt(null);
        hangout.setPk(InviterKeyFactory.getEventPk(hangoutId));
        hangout.setSk(InviterKeyFactory.getMetadataSk());
        return hangout;
    }

    /** A Hangout in a virtual watch-party series (host nudge is suppressed for virtual). */
    public static Hangout virtualHangout() {
        return virtualHangout(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }

    public static Hangout virtualHangout(String hangoutId, String seriesId) {
        Hangout hangout = inPersonHangout(hangoutId, seriesId);
        // Virtual hangouts never schedule a host nudge — leave the field null
        // to reflect that.
        hangout.setHostNudgeScheduleName(null);
        return hangout;
    }
}
