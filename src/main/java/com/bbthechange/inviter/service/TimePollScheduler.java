package com.bbthechange.inviter.service;

import com.bbthechange.inviter.client.EventBridgeSchedulerClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * EventBridge scheduler for TIME-poll auto-adoption. Creates two per-poll schedules by
 * convention:
 *
 * <ul>
 *   <li>{@code poll-adopt-{pollId}-24h} fixed at {@code createdAt + 24h}
 *   <li>{@code poll-adopt-{pollId}-48h} starts at {@code createdAt + 48h} and slides forward
 *       on option-add (see TIME_POLL_MIGRATION_PLAN.md §Scheduling model).
 * </ul>
 *
 * Both schedules emit a {@code POLL_ADOPTION} message that {@link
 * com.bbthechange.inviter.listener.ScheduledEventListener} routes to
 * {@link TimePollService#evaluateAndAdopt(String, String)}.
 */
@Service
public class TimePollScheduler {

    private static final Logger logger = LoggerFactory.getLogger(TimePollScheduler.class);

    static final long SHORT_WINDOW_MS = 24L * 60 * 60 * 1000;
    static final long LONG_WINDOW_MS = 48L * 60 * 60 * 1000;

    private static final long MIN_SCHEDULE_ADVANCE_SECONDS = 60;

    private static final DateTimeFormatter SCHEDULE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC);

    private final EventBridgeSchedulerClient eventBridgeClient;
    private final ObjectMapper objectMapper;
    private final int shortWindowHours;
    private final int longWindowHours;

    @Autowired
    public TimePollScheduler(EventBridgeSchedulerClient eventBridgeClient,
                             ObjectMapper objectMapper,
                             @Value("${time-suggestion.auto-adoption.short-window-hours:24}")
                             int shortWindowHours,
                             @Value("${time-suggestion.auto-adoption.long-window-hours:48}")
                             int longWindowHours) {
        this.eventBridgeClient = eventBridgeClient;
        this.objectMapper = objectMapper;
        this.shortWindowHours = shortWindowHours;
        this.longWindowHours = longWindowHours;
    }

    /**
     * Schedule both the fixed 24h and initial 48h adoption checks for a new TIME poll.
     * Returns the epoch-ms target of the 48h schedule so callers can persist it on the
     * poll as {@code scheduledFinalAdoptionAt}; returns {@code null} when scheduling is
     * disabled or the fire time is already in the past.
     */
    public Long scheduleInitial(String hangoutId, String pollId, Instant createdAt) {
        if (!eventBridgeClient.isEnabled()) {
            logger.debug("Scheduler disabled — skipping schedule for poll {}", pollId);
            return null;
        }
        String payload = buildPayload(hangoutId, pollId);

        long shortFireEpochSec = createdAt.getEpochSecond() + shortWindowHours * 3600L;
        scheduleAt(shortName(pollId), shortFireEpochSec, payload, "24h", pollId);

        long longFireEpochSec = createdAt.getEpochSecond() + longWindowHours * 3600L;
        boolean created = scheduleAt(longName(pollId), longFireEpochSec, payload, "48h", pollId);
        return created ? longFireEpochSec * 1000L : null;
    }

    /**
     * Slide the 48h schedule forward to {@code fireAtEpochMs}. Returns the new
     * {@code scheduledFinalAdoptionAt} if re-scheduled successfully, null otherwise.
     */
    public Long reschedule48h(String hangoutId, String pollId, long fireAtEpochMs) {
        if (!eventBridgeClient.isEnabled()) {
            return null;
        }
        String payload = buildPayload(hangoutId, pollId);
        long fireEpochSec = fireAtEpochMs / 1000L;
        boolean ok = scheduleAt(longName(pollId), fireEpochSec, payload, "48h", pollId);
        return ok ? fireEpochSec * 1000L : null;
    }

    /**
     * Cancel both adoption schedules for a poll. Idempotent — safe on unknown poll ids.
     */
    public void cancelBoth(String pollId) {
        if (!eventBridgeClient.isEnabled()) {
            return;
        }
        try {
            eventBridgeClient.deleteSchedule(shortName(pollId));
            eventBridgeClient.deleteSchedule(longName(pollId));
            logger.info("Cancelled both adoption schedules for poll {}", pollId);
        } catch (Exception e) {
            logger.warn("Failed to cancel adoption schedules for poll {}: {}", pollId, e.getMessage());
        }
    }

    private boolean scheduleAt(String name, long fireEpochSec, String payload,
                               String label, String pollId) {
        long nowSec = Instant.now().getEpochSecond();
        if (fireEpochSec <= nowSec + MIN_SCHEDULE_ADVANCE_SECONDS) {
            logger.info("{} adoption window already passed for poll {} — skipping schedule",
                label, pollId);
            return false;
        }
        String expr = "at(" + SCHEDULE_TIME_FORMATTER.format(Instant.ofEpochSecond(fireEpochSec)) + ")";
        try {
            eventBridgeClient.createOrUpdateSchedule(name, expr, payload, false);
            logger.info("Scheduled {} adoption check for poll {} at {}", label, pollId, expr);
            return true;
        } catch (Exception e) {
            logger.error("Failed to schedule {} adoption check for poll {}: {}",
                label, pollId, e.getMessage(), e);
            return false;
        }
    }

    private String buildPayload(String hangoutId, String pollId) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "POLL_ADOPTION");
        node.put("hangoutId", hangoutId);
        node.put("pollId", pollId);
        return node.toString();
    }

    private String shortName(String pollId) {
        return "poll-adopt-" + pollId + "-24h";
    }

    private String longName(String pollId) {
        return "poll-adopt-" + pollId + "-48h";
    }
}
