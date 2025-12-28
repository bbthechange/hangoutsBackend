package com.bbthechange.inviter.service;

import com.bbthechange.inviter.config.SchedulerConfig;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.repository.HangoutRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.scheduler.SchedulerClient;
import software.amazon.awssdk.services.scheduler.model.*;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Service for scheduling hangout reminder notifications via AWS EventBridge Scheduler.
 *
 * Schedules are created 2 hours before hangout start time using one-time at() expressions.
 * Schedules auto-delete after invocation (ActionAfterCompletion.DELETE).
 *
 * When scheduler.enabled=false, all methods are no-ops.
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

    private final SchedulerClient schedulerClient; // May be null when scheduler is disabled
    private final SchedulerConfig schedulerConfig;
    private final HangoutRepository hangoutRepository;
    private final MeterRegistry meterRegistry;

    @Autowired
    public HangoutSchedulerService(@Autowired(required = false) SchedulerClient schedulerClient,
                                   SchedulerConfig schedulerConfig,
                                   HangoutRepository hangoutRepository,
                                   MeterRegistry meterRegistry) {
        this.schedulerClient = schedulerClient;
        this.schedulerConfig = schedulerConfig;
        this.hangoutRepository = hangoutRepository;
        this.meterRegistry = meterRegistry;

        if (!schedulerConfig.isSchedulerEnabled()) {
            logger.info("HangoutSchedulerService initialized with scheduler DISABLED");
        } else if (schedulerClient == null) {
            logger.warn("Scheduler enabled but SchedulerClient is null - check configuration");
        } else {
            logger.info("HangoutSchedulerService initialized with scheduler ENABLED");
        }
    }

    /**
     * Schedule a reminder for the given hangout.
     * Creates or updates an EventBridge schedule to trigger 2 hours before start.
     *
     * @param hangout The hangout to schedule a reminder for
     */
    public void scheduleReminder(Hangout hangout) {
        if (!schedulerConfig.isSchedulerEnabled() || schedulerClient == null) {
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
            return;
        }

        String scheduleName = getScheduleName(hangout.getHangoutId());
        String scheduleExpression = buildScheduleExpression(reminderTimestamp);

        // Build the target with SQS queue
        // The hangoutId is passed as JSON in the input field, which becomes the SQS message body.
        String input = String.format("{\"hangoutId\":\"%s\"}", hangout.getHangoutId());

        Target target = Target.builder()
                .arn(schedulerConfig.getQueueArn())
                .roleArn(schedulerConfig.getRoleArn())
                .input(input)
                .deadLetterConfig(DeadLetterConfig.builder()
                        .arn(schedulerConfig.getDlqArn())
                        .build())
                .retryPolicy(RetryPolicy.builder()
                        .maximumRetryAttempts(2)
                        .maximumEventAgeInSeconds(3600) // 1 hour
                        .build())
                .build();

        try {
            // Check if schedule already exists
            boolean exists = scheduleExists(scheduleName);

            if (exists) {
                // Update existing schedule
                UpdateScheduleRequest updateRequest = UpdateScheduleRequest.builder()
                        .name(scheduleName)
                        .groupName(schedulerConfig.getGroupName())
                        .scheduleExpression(scheduleExpression)
                        .flexibleTimeWindow(FlexibleTimeWindow.builder()
                                .mode(FlexibleTimeWindowMode.OFF)
                                .build())
                        .target(target)
                        .actionAfterCompletion(ActionAfterCompletion.DELETE)
                        .build();

                schedulerClient.updateSchedule(updateRequest);
                logger.info("Updated reminder schedule {} for hangout {} at {}",
                        scheduleName, hangout.getHangoutId(), scheduleExpression);
            } else {
                // Create new schedule
                CreateScheduleRequest createRequest = CreateScheduleRequest.builder()
                        .name(scheduleName)
                        .groupName(schedulerConfig.getGroupName())
                        .scheduleExpression(scheduleExpression)
                        .flexibleTimeWindow(FlexibleTimeWindow.builder()
                                .mode(FlexibleTimeWindowMode.OFF)
                                .build())
                        .target(target)
                        .actionAfterCompletion(ActionAfterCompletion.DELETE)
                        .build();

                schedulerClient.createSchedule(createRequest);
                logger.info("Created reminder schedule {} for hangout {} at {}",
                        scheduleName, hangout.getHangoutId(), scheduleExpression);
            }

            // Store schedule name in hangout for future updates/deletion
            hangoutRepository.updateReminderScheduleName(hangout.getHangoutId(), scheduleName);

            meterRegistry.counter("hangout_schedule_created", "status", "success").increment();

        } catch (ConflictException e) {
            // Schedule already exists (race condition), try update
            logger.warn("Schedule {} already exists, attempting update", scheduleName);
            try {
                schedulerClient.updateSchedule(UpdateScheduleRequest.builder()
                        .name(scheduleName)
                        .groupName(schedulerConfig.getGroupName())
                        .scheduleExpression(scheduleExpression)
                        .flexibleTimeWindow(FlexibleTimeWindow.builder()
                                .mode(FlexibleTimeWindowMode.OFF)
                                .build())
                        .target(target)
                        .actionAfterCompletion(ActionAfterCompletion.DELETE)
                        .build());

                hangoutRepository.updateReminderScheduleName(hangout.getHangoutId(), scheduleName);
                meterRegistry.counter("hangout_schedule_created", "status", "conflict_resolved").increment();
            } catch (Exception updateEx) {
                logger.error("Failed to update schedule after conflict: {}", updateEx.getMessage(), updateEx);
                meterRegistry.counter("hangout_schedule_created", "status", "error").increment();
            }
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
        if (!schedulerConfig.isSchedulerEnabled() || schedulerClient == null) {
            logger.debug("Scheduler disabled, skipping reminder cancellation for hangout: {}", hangout.getHangoutId());
            return;
        }

        String scheduleName = hangout.getReminderScheduleName();
        if (scheduleName == null || scheduleName.isEmpty()) {
            // No schedule exists, check if one might exist with default name
            scheduleName = getScheduleName(hangout.getHangoutId());
        }

        try {
            DeleteScheduleRequest deleteRequest = DeleteScheduleRequest.builder()
                    .name(scheduleName)
                    .groupName(schedulerConfig.getGroupName())
                    .build();

            schedulerClient.deleteSchedule(deleteRequest);
            logger.info("Deleted reminder schedule {} for hangout {}", scheduleName, hangout.getHangoutId());
            meterRegistry.counter("hangout_schedule_deleted", "status", "success").increment();

        } catch (ResourceNotFoundException e) {
            // Schedule doesn't exist, that's fine (idempotent)
            logger.debug("Schedule {} not found for deletion (already deleted or never created)", scheduleName);
        } catch (Exception e) {
            logger.error("Failed to delete reminder schedule {} for hangout {}: {}",
                    scheduleName, hangout.getHangoutId(), e.getMessage(), e);
            meterRegistry.counter("hangout_schedule_deleted", "status", "error").increment();
        }
    }

    /**
     * Check if a schedule already exists.
     */
    private boolean scheduleExists(String scheduleName) {
        try {
            GetScheduleRequest request = GetScheduleRequest.builder()
                    .name(scheduleName)
                    .groupName(schedulerConfig.getGroupName())
                    .build();
            schedulerClient.getSchedule(request);
            return true;
        } catch (ResourceNotFoundException e) {
            return false;
        }
    }

    /**
     * Generate schedule name from hangout ID.
     * Format: hangout-{hangoutId}
     */
    private String getScheduleName(String hangoutId) {
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
}
