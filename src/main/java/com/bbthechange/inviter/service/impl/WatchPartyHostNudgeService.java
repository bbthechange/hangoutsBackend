package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.model.EventSeries;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.repository.EventSeriesRepository;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.NotificationService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Handles WATCH_PARTY_HOST_NUDGE SQS messages: gate checks, idempotency claim,
 * recipient assembly, and notification dispatch.
 */
@Service
public class WatchPartyHostNudgeService {

    private static final Logger logger = LoggerFactory.getLogger(WatchPartyHostNudgeService.class);

    private static final String COUNTER = "watchparty_host_nudge_total";

    // Safety window: the schedule fires ~48h before air, but allow drift.
    private static final long MIN_MINUTES_BEFORE_AIR = 36L * 60;
    private static final long MAX_MINUTES_BEFORE_AIR = 60L * 60;

    /**
     * Cross-episode coalesce window. If the series fired a host nudge less than
     * this long ago, suppress the next episode's nudge — a 22-episode season
     * otherwise pushes the same recipient set 22 separate times.
     */
    static final long COALESCE_WINDOW_MILLIS = 24L * 60 * 60 * 1000;

    private final HangoutRepository hangoutRepository;
    private final EventSeriesRepository eventSeriesRepository;
    private final WatchPartyHostNudgeRecipientResolver recipientResolver;
    private final NotificationService notificationService;
    private final MeterRegistry meterRegistry;

    @Autowired
    public WatchPartyHostNudgeService(HangoutRepository hangoutRepository,
                                       EventSeriesRepository eventSeriesRepository,
                                       WatchPartyHostNudgeRecipientResolver recipientResolver,
                                       NotificationService notificationService,
                                       MeterRegistry meterRegistry) {
        this.hangoutRepository = hangoutRepository;
        this.eventSeriesRepository = eventSeriesRepository;
        this.recipientResolver = recipientResolver;
        this.notificationService = notificationService;
        this.meterRegistry = meterRegistry;
    }

    public void processHostNudge(String hangoutId) {
        Optional<Hangout> hangoutOpt = hangoutRepository.findHangoutById(hangoutId);
        if (hangoutOpt.isEmpty()) {
            logger.warn("Host nudge: hangout not found: {}", hangoutId);
            meterRegistry.counter(COUNTER, "status", "not_found").increment();
            return;
        }
        Hangout hangout = hangoutOpt.get();

        if (hangout.getHostNudgeSentAt() != null) {
            logger.info("Host nudge: already sent for hangout {} at {}",
                hangoutId, hangout.getHostNudgeSentAt());
            meterRegistry.counter(COUNTER, "status", "already_sent").increment();
            return;
        }

        if (hangout.getHostAtPlaceUserId() != null && !hangout.getHostAtPlaceUserId().isEmpty()) {
            logger.info("Host nudge: host already claimed for hangout {}", hangoutId);
            meterRegistry.counter(COUNTER, "status", "host_claimed").increment();
            return;
        }

        if (hangout.getSeriesId() == null || hangout.getSeriesId().isEmpty()) {
            logger.info("Host nudge: hangout {} is not in a series", hangoutId);
            meterRegistry.counter(COUNTER, "status", "not_in_series").increment();
            return;
        }

        Optional<EventSeries> seriesOpt = eventSeriesRepository.findById(hangout.getSeriesId());
        if (seriesOpt.isEmpty() || !seriesOpt.get().isWatchParty() || seriesOpt.get().isVirtualWatchParty()) {
            logger.info("Host nudge: series {} for hangout {} is not an in-person watch party",
                hangout.getSeriesId(), hangoutId);
            meterRegistry.counter(COUNTER, "status", "wrong_series_type").increment();
            return;
        }
        EventSeries series = seriesOpt.get();

        Long startTimestamp = hangout.getStartTimestamp();
        if (startTimestamp == null) {
            logger.info("Host nudge: hangout {} has no start time", hangoutId);
            meterRegistry.counter(COUNTER, "status", "outside_window").increment();
            return;
        }
        long nowSeconds = Instant.now().getEpochSecond();
        long minutesUntilStart = (startTimestamp - nowSeconds) / 60;
        if (minutesUntilStart < MIN_MINUTES_BEFORE_AIR || minutesUntilStart > MAX_MINUTES_BEFORE_AIR) {
            logger.warn("Host nudge: hangout {} outside window ({} min until air, expected {}-{})",
                hangoutId, minutesUntilStart, MIN_MINUTES_BEFORE_AIR, MAX_MINUTES_BEFORE_AIR);
            meterRegistry.counter(COUNTER, "status", "outside_window").increment();
            return;
        }

        long now = System.currentTimeMillis();

        // Soft cross-episode coalesce: read-check, send, persist. Two episodes whose
        // SQS messages fire concurrently can both pass this gate and both dispatch
        // before either persist lands — that's an accepted bounded over-send (at most
        // a couple of fan-outs during an SQS backlog drain) in exchange for not failing
        // the dispatch if the persist throws afterward. The 22-episodes-per-season
        // problem this fix actually targets is sequential (weekly), so a read-then-write
        // gate is sufficient there.
        Long lastSeriesFire = series.getLastHostNudgeFiredAt();
        if (lastSeriesFire != null && (now - lastSeriesFire) < COALESCE_WINDOW_MILLIS) {
            logger.info("Host nudge: series {} coalesced (last fired {} ms ago, window {} ms) for hangout {}",
                series.getSeriesId(), now - lastSeriesFire, COALESCE_WINDOW_MILLIS, hangoutId);
            meterRegistry.counter(COUNTER, "status", "series_coalesced").increment();
            return;
        }

        boolean claimed = hangoutRepository.setHostNudgeSentAtIfNull(hangoutId, now);
        if (!claimed) {
            logger.info("Host nudge: lost race for hangout {}", hangoutId);
            meterRegistry.counter(COUNTER, "status", "lost_race").increment();
            return;
        }

        try {
            Set<String> recipients = recipientResolver.resolve(series, hangout);
            String body = buildMessageBody(series, hangout);
            notificationService.notifyWatchPartyHostNeeded(recipients, series, hangout, body);

            // Persist the series-level coalesce timestamp AFTER a successful send.
            // Best-effort: if this write fails, the nudge still happened — we just lose
            // the coalesce gate for this cycle, which means at most one extra fan-out
            // before the next episode's claim lands. Better than failing the whole flow.
            try {
                eventSeriesRepository.updateLastHostNudgeFiredAt(series.getSeriesId(), now);
            } catch (RuntimeException persistEx) {
                logger.error("Host nudge: failed to persist lastHostNudgeFiredAt for series {} after successful send",
                    series.getSeriesId(), persistEx);
            }

            meterRegistry.counter(COUNTER, "status", "sent").increment();
            logger.info("Host nudge: sent for hangout {} to {} recipients", hangoutId, recipients.size());
        } catch (RuntimeException e) {
            // Compensating write: roll back the idempotency claim so the nudge isn't lost
            // permanently. ScheduledEventListener swallows exceptions to ack the SQS
            // message, so without this rollback a single dispatch failure would leave the
            // flag set forever with no notification delivered — silent permanent failure.
            logger.error("Host nudge dispatch failed for hangout {}, clearing claim", hangoutId, e);
            try {
                hangoutRepository.clearHostNudgeSentAt(hangoutId);
            } catch (Exception cleanupEx) {
                logger.error("Failed to clear host nudge claim after dispatch failure for hangout {}",
                    hangoutId, cleanupEx);
            }
            meterRegistry.counter(COUNTER, "status", "error").increment();
            throw e;
        }
    }

    /**
     * Body format (see UX doc):
     *   TBA episode:     "{Day}'s {ShowName} episode still needs a host!"
     *   Combined:        "{Day}'s double/triple {ShowName} episode still needs a host!"
     *   Default:         "{ShowName} — {EpisodeTitle} airs {Day} and still needs a host!"
     */
    String buildMessageBody(EventSeries series, Hangout hangout) {
        String showName = series.getSeriesTitle() != null ? series.getSeriesTitle() : "Show";
        String day = formatDayOfWeek(hangout.getStartTimestamp(), series.getTimezone());

        List<String> combined = hangout.getCombinedExternalIds();
        if (combined != null && combined.size() > 1) {
            String word = combined.size() == 2 ? "double" : "triple";
            return String.format("%s's %s %s episode still needs a host!", day, word, showName);
        }

        String episodeTitle = hangout.getTitle();
        if (episodeTitle == null || episodeTitle.isBlank() || isTbaTitle(episodeTitle)) {
            return String.format("%s's %s episode still needs a host!", day, showName);
        }

        return String.format("%s — %s airs %s and still needs a host!", showName, episodeTitle, day);
    }

    private boolean isTbaTitle(String title) {
        String trimmed = title.trim().toLowerCase(Locale.ROOT);
        return trimmed.equals("tba") || trimmed.equals("tbd")
            || trimmed.equals("tba.") || trimmed.equals("tbd.")
            || trimmed.startsWith("tba ") || trimmed.startsWith("tbd ");
    }

    private String formatDayOfWeek(Long startTimestamp, String timezone) {
        if (startTimestamp == null) {
            return "this week";
        }
        ZoneId zone;
        try {
            zone = (timezone != null && !timezone.isEmpty())
                ? ZoneId.of(timezone)
                : ZoneId.of("America/Los_Angeles");
        } catch (Exception e) {
            zone = ZoneId.of("America/Los_Angeles");
        }
        DayOfWeek dow = Instant.ofEpochSecond(startTimestamp).atZone(zone).getDayOfWeek();
        return dow.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }
}
