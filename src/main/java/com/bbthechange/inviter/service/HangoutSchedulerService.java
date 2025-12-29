package com.bbthechange.inviter.service;

import com.bbthechange.inviter.client.EventBridgeSchedulerClient;
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
 * Service for scheduling hangout reminder notifications via AWS EventBridge Scheduler.
 *
 * Schedules are created 2 hours before hangout start time using one-time at() expressions.
 * Schedules auto-delete after invocation (ActionAfterCompletion.DELETE).
 *
 * This service handles business logic only. AWS SDK interactions are delegated to
 * EventBridgeSchedulerClient.
 */
@Service
public class HangoutSchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(HangoutSchedulerService.class);

    // Reminder is sent 2 hours before start
    private static final long REMINDER_OFFSET_SECONDS = 2 * 60 * 60; // 2 hours in seconds

    // Minimum time in the future for scheduling (avoid scheduling in the past)
    private static final long MIN_SCHEDULE_ADVANCE_SECONDS = 60; // 1 minute minimum

    private static final DateTimeFormatter SCHEDULE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC);

    private final EventBridgeSchedulerClient eventBridgeClient;
    private final HangoutRepository hangoutRepository;
    private final MeterRegistry meterRegistry;

    @Autowired
    public HangoutSchedulerService(EventBridgeSchedulerClient eventBridgeClient,
                                   HangoutRepository hangoutRepository,
                                   MeterRegistry meterRegistry) {
        this.eventBridgeClient = eventBridgeClient;
        this.hangoutRepository = hangoutRepository;
        this.meterRegistry = meterRegistry;

        if (eventBridgeClient.isEnabled()) {
            logger.info("HangoutSchedulerService initialized with scheduler ENABLED");
        } else {
            logger.info("HangoutSchedulerService initialized with scheduler DISABLED");
        }
    }

    /**
     * Schedule a reminder for the given hangout.
     * Creates or updates an EventBridge schedule to trigger 2 hours before start.
     *
     * @param hangout The hangout to schedule a reminder for
     */
    public void scheduleReminder(Hangout hangout) {
        if (!eventBridgeClient.isEnabled()) {
            logger.debug("Scheduler disabled, skipping reminder for hangout: {}", hangout.getHangoutId());
            return;
        }

        Long startTimestamp = hangout.getStartTimestamp();
        if (startTimestamp == null) {
            logger.debug("Hangout {} has no start time, skipping reminder scheduling", hangout.getHangoutId());
            return;
        }

        // Calculate reminder time (2 hours before start)
        long reminderTimestamp = startTimestamp - REMINDER_OFFSET_SECONDS;
        long nowSeconds = Instant.now().getEpochSecond();

        // Don't schedule if reminder time has already passed
        if (reminderTimestamp <= nowSeconds + MIN_SCHEDULE_ADVANCE_SECONDS) {
            logger.info("Reminder time already passed for hangout {}, not scheduling", hangout.getHangoutId());
            // Delete any existing schedule since it's no longer needed
            String existingScheduleName = hangout.getReminderScheduleName();
            if (existingScheduleName != null) {
                eventBridgeClient.deleteSchedule(existingScheduleName);
            }
            return;
        }

        // Determine schedule name - use existing if stored, otherwise generate new
        String existingScheduleName = hangout.getReminderScheduleName();
        String scheduleName = existingScheduleName != null
                ? existingScheduleName
                : generateScheduleName(hangout.getHangoutId());

        String scheduleExpression = buildScheduleExpression(reminderTimestamp);
        String inputJson = buildInputJson(hangout.getHangoutId());

        // Use stored name to determine if schedule is expected to exist
        boolean expectedToExist = (existingScheduleName != null);

        try {
            eventBridgeClient.createOrUpdateSchedule(scheduleName, scheduleExpression, inputJson, expectedToExist);

            // Store schedule name for future updates/deletion
            hangoutRepository.updateReminderScheduleName(hangout.getHangoutId(), scheduleName);

            meterRegistry.counter("hangout_schedule_created", "status", "success").increment();
            logger.info("Scheduled reminder for hangout {} at {}", hangout.getHangoutId(), scheduleExpression);

        } catch (Exception e) {
            logger.error("Failed to schedule reminder for hangout {}: {}",
                    hangout.getHangoutId(), e.getMessage(), e);
            meterRegistry.counter("hangout_schedule_created", "status", "error").increment();
        }
    }

    /**
     * Cancel the reminder schedule for the given hangout.
     * Called when a hangout is cancelled or deleted.
     *
     * @param hangout The hangout whose reminder should be cancelled
     */
    public void cancelReminder(Hangout hangout) {
        if (!eventBridgeClient.isEnabled()) {
            logger.debug("Scheduler disabled, skipping reminder cancellation for hangout: {}", hangout.getHangoutId());
            return;
        }

        // Use stored name if available, otherwise try the generated name
        String scheduleName = hangout.getReminderScheduleName();
        if (scheduleName == null || scheduleName.isEmpty()) {
            scheduleName = generateScheduleName(hangout.getHangoutId());
        }

        try {
            eventBridgeClient.deleteSchedule(scheduleName);
            meterRegistry.counter("hangout_schedule_deleted", "status", "success").increment();
            logger.info("Cancelled reminder for hangout {}", hangout.getHangoutId());

        } catch (Exception e) {
            logger.error("Failed to cancel reminder for hangout {}: {}",
                    hangout.getHangoutId(), e.getMessage(), e);
            meterRegistry.counter("hangout_schedule_deleted", "status", "error").increment();
        }
    }

    /**
     * Generate schedule name from hangout ID.
     * Format: hangout-{hangoutId}
     */
    private String generateScheduleName(String hangoutId) {
        return "hangout-" + hangoutId;
    }

    /**
     * Build the at() schedule expression for one-time execution.
     * Format: at(yyyy-MM-ddTHH:mm:ss)
     */
    private String buildScheduleExpression(long epochSeconds) {
        Instant instant = Instant.ofEpochSecond(epochSeconds);
        String formattedTime = SCHEDULE_TIME_FORMATTER.format(instant);
        return "at(" + formattedTime + ")";
    }

    /**
     * Build the JSON input payload for the schedule target.
     */
    private String buildInputJson(String hangoutId) {
        return String.format("{\"hangoutId\":\"%s\"}", hangoutId);
    }
}
