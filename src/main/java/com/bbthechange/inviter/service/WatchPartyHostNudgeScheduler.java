package com.bbthechange.inviter.service;

import com.bbthechange.inviter.client.EventBridgeSchedulerClient;
import com.bbthechange.inviter.model.EventSeries;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.repository.HangoutRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Schedules / cancels EventBridge schedules for the watch-party host nudge.
 *
 * Mirrors HangoutSchedulerService: fire time is 48h before the hangout's
 * startTimestamp; schedule name is "hostnudge-{hangoutId}"; payload is the
 * SQS dispatch type the listener routes on.
 */
@Service
public class WatchPartyHostNudgeScheduler {

    private static final Logger logger = LoggerFactory.getLogger(WatchPartyHostNudgeScheduler.class);

    private static final long NUDGE_OFFSET_SECONDS = 48L * 60 * 60;
    private static final long MIN_SCHEDULE_ADVANCE_SECONDS = 60;

    private static final String SCHEDULE_PREFIX = "hostnudge-";
    private static final String COUNTER_CREATED = "watchparty_host_nudge_schedule_created";
    private static final String COUNTER_DELETED = "watchparty_host_nudge_schedule_deleted";

    private static final DateTimeFormatter SCHEDULE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC);

    private final EventBridgeSchedulerClient eventBridgeClient;
    private final HangoutRepository hangoutRepository;
    private final MeterRegistry meterRegistry;

    @Autowired
    public WatchPartyHostNudgeScheduler(EventBridgeSchedulerClient eventBridgeClient,
                                         HangoutRepository hangoutRepository,
                                         MeterRegistry meterRegistry) {
        this.eventBridgeClient = eventBridgeClient;
        this.hangoutRepository = hangoutRepository;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Idempotently create-or-update the EventBridge schedule for this hangout.
     * Silent skip when the scheduler is disabled. Counter-tracked no-op for
     * virtual series, already-claimed hosts, already-sent nudges, and past
     * fire times.
     */
    public void scheduleHostNudge(Hangout hangout, EventSeries series) {
        if (!eventBridgeClient.isEnabled()) {
            logger.debug("Scheduler disabled, skipping host nudge for hangout: {}",
                hangout != null ? hangout.getHangoutId() : "<null>");
            return;
        }

        if (series != null && series.isVirtualWatchParty()) {
            logger.info("Skipping host nudge for hangout {}: series is virtual", hangout.getHangoutId());
            meterRegistry.counter(COUNTER_CREATED, "status", "skipped_virtual").increment();
            return;
        }

        if (hangout.getHostAtPlaceUserId() != null && !hangout.getHostAtPlaceUserId().isEmpty()) {
            logger.info("Skipping host nudge for hangout {}: host already claimed", hangout.getHangoutId());
            meterRegistry.counter(COUNTER_CREATED, "status", "skipped_has_host").increment();
            return;
        }

        if (hangout.getHostNudgeSentAt() != null) {
            logger.info("Skipping host nudge for hangout {}: nudge already sent at {}",
                hangout.getHangoutId(), hangout.getHostNudgeSentAt());
            meterRegistry.counter(COUNTER_CREATED, "status", "skipped_past").increment();
            return;
        }

        Long startTimestamp = hangout.getStartTimestamp();
        if (startTimestamp == null) {
            logger.info("Skipping host nudge for hangout {}: no start time", hangout.getHangoutId());
            meterRegistry.counter(COUNTER_CREATED, "status", "skipped_past").increment();
            return;
        }

        long fireTimestamp = startTimestamp - NUDGE_OFFSET_SECONDS;
        long nowSeconds = Instant.now().getEpochSecond();
        if (fireTimestamp <= nowSeconds + MIN_SCHEDULE_ADVANCE_SECONDS) {
            logger.info("Skipping host nudge for hangout {}: fire time already passed", hangout.getHangoutId());
            meterRegistry.counter(COUNTER_CREATED, "status", "skipped_past").increment();
            String existing = hangout.getHostNudgeScheduleName();
            if (existing != null && !existing.isEmpty()) {
                try {
                    eventBridgeClient.deleteSchedule(existing);
                } catch (Exception e) {
                    logger.warn("Failed to clean up stale host-nudge schedule {}: {}", existing, e.getMessage());
                }
            }
            return;
        }

        String existing = hangout.getHostNudgeScheduleName();
        String scheduleName = (existing != null && !existing.isEmpty())
            ? existing
            : generateScheduleName(hangout.getHangoutId());
        boolean expectedToExist = (existing != null && !existing.isEmpty());

        String scheduleExpression = buildScheduleExpression(fireTimestamp);
        String inputJson = buildInputJson(hangout.getHangoutId());

        try {
            eventBridgeClient.createOrUpdateSchedule(scheduleName, scheduleExpression, inputJson, expectedToExist);
            hangoutRepository.updateHostNudgeScheduleName(hangout.getHangoutId(), scheduleName);
            meterRegistry.counter(COUNTER_CREATED, "status", "success").increment();
            logger.info("Scheduled host nudge for hangout {} at {}", hangout.getHangoutId(), scheduleExpression);
        } catch (Exception e) {
            logger.error("Failed to schedule host nudge for hangout {}: {}",
                hangout.getHangoutId(), e.getMessage(), e);
            meterRegistry.counter(COUNTER_CREATED, "status", "error").increment();
        }
    }

    /**
     * Delete the EventBridge schedule. Safe to call when no schedule exists —
     * the client treats ResourceNotFound as success.
     */
    public void cancelHostNudge(Hangout hangout) {
        if (!eventBridgeClient.isEnabled()) {
            logger.debug("Scheduler disabled, skipping host-nudge cancel for hangout: {}",
                hangout != null ? hangout.getHangoutId() : "<null>");
            return;
        }

        String scheduleName = hangout.getHostNudgeScheduleName();
        if (scheduleName == null || scheduleName.isEmpty()) {
            scheduleName = generateScheduleName(hangout.getHangoutId());
        }

        try {
            eventBridgeClient.deleteSchedule(scheduleName);
            meterRegistry.counter(COUNTER_DELETED, "status", "success").increment();
            logger.info("Cancelled host nudge for hangout {}", hangout.getHangoutId());
        } catch (Exception e) {
            logger.error("Failed to cancel host nudge for hangout {}: {}",
                hangout.getHangoutId(), e.getMessage(), e);
            meterRegistry.counter(COUNTER_DELETED, "status", "error").increment();
        }
    }

    private String generateScheduleName(String hangoutId) {
        return SCHEDULE_PREFIX + hangoutId;
    }

    private String buildScheduleExpression(long epochSeconds) {
        Instant instant = Instant.ofEpochSecond(epochSeconds);
        return "at(" + SCHEDULE_TIME_FORMATTER.format(instant) + ")";
    }

    private String buildInputJson(String hangoutId) {
        return String.format("{\"type\":\"WATCH_PARTY_HOST_NUDGE\",\"hangoutId\":\"%s\"}", hangoutId);
    }
}
