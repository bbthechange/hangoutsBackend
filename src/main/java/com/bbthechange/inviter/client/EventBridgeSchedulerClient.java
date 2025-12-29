package com.bbthechange.inviter.client;

import com.bbthechange.inviter.config.SchedulerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.scheduler.SchedulerClient;
import software.amazon.awssdk.services.scheduler.model.*;

/**
 * Client for AWS EventBridge Scheduler operations.
 * Encapsulates all AWS SDK interactions for schedule management.
 *
 * Handles:
 * - Building Target configuration (queue ARN, role ARN, DLQ, retry policy)
 * - Creating, updating, and deleting schedules
 * - Race condition handling with create/update fallbacks
 */
@Component
public class EventBridgeSchedulerClient {

    private static final Logger logger = LoggerFactory.getLogger(EventBridgeSchedulerClient.class);

    private final SchedulerClient schedulerClient; // May be null when disabled
    private final SchedulerConfig config;

    @Autowired
    public EventBridgeSchedulerClient(@Autowired(required = false) SchedulerClient schedulerClient,
                                       SchedulerConfig config) {
        this.schedulerClient = schedulerClient;
        this.config = config;
    }

    /**
     * Check if the scheduler is enabled and properly configured.
     */
    public boolean isEnabled() {
        return config.isSchedulerEnabled() && schedulerClient != null;
    }

    /**
     * Create or update a schedule based on whether it's expected to exist.
     * Handles race conditions and auto-deleted schedules gracefully.
     *
     * @param scheduleName The unique name for the schedule
     * @param scheduleExpression The schedule expression (e.g., "at(2025-01-01T10:00:00)")
     * @param inputJson The JSON payload to send to the target
     * @param expectedToExist True if we previously created this schedule (use update-first strategy)
     */
    public void createOrUpdateSchedule(String scheduleName, String scheduleExpression,
                                        String inputJson, boolean expectedToExist) {
        if (!isEnabled()) {
            logger.debug("Scheduler disabled, skipping createOrUpdateSchedule for: {}", scheduleName);
            return;
        }

        Target target = buildTarget(inputJson);

        if (expectedToExist) {
            // Schedule was created before - try update first, fall back to create if deleted
            updateWithCreateFallback(scheduleName, scheduleExpression, target);
        } else {
            // No schedule exists - try create first, fall back to update on race condition
            createWithUpdateFallback(scheduleName, scheduleExpression, target);
        }
    }

    /**
     * Delete a schedule. Idempotent - does not throw if schedule doesn't exist.
     *
     * @param scheduleName The name of the schedule to delete
     */
    public void deleteSchedule(String scheduleName) {
        if (!isEnabled()) {
            logger.debug("Scheduler disabled, skipping deleteSchedule for: {}", scheduleName);
            return;
        }

        try {
            DeleteScheduleRequest request = DeleteScheduleRequest.builder()
                    .name(scheduleName)
                    .groupName(config.getGroupName())
                    .build();

            schedulerClient.deleteSchedule(request);
            logger.info("Deleted schedule: {}", scheduleName);

        } catch (ResourceNotFoundException e) {
            // Schedule doesn't exist - that's fine (idempotent)
            logger.debug("Schedule {} not found for deletion (already deleted or never created)", scheduleName);
        }
    }

    /**
     * Try to update schedule first (expected to exist), fall back to create if not found.
     * Used when we have a stored reminderScheduleName.
     */
    private void updateWithCreateFallback(String scheduleName, String scheduleExpression, Target target) {
        try {
            doUpdateSchedule(scheduleName, scheduleExpression, target);
            logger.info("Updated schedule: {} with expression: {}", scheduleName, scheduleExpression);

        } catch (ResourceNotFoundException e) {
            // Schedule was deleted (e.g., after execution) - recreate it
            logger.info("Schedule {} not found, creating new one", scheduleName);
            try {
                doCreateSchedule(scheduleName, scheduleExpression, target);
                logger.info("Created schedule: {} with expression: {}", scheduleName, scheduleExpression);
            } catch (ConflictException ce) {
                // Race condition - another process created it, try update again
                logger.warn("Race condition creating schedule {}, retrying update", scheduleName);
                doUpdateSchedule(scheduleName, scheduleExpression, target);
            }
        }
    }

    /**
     * Try to create schedule first (not expected to exist), fall back to update on conflict.
     * Used when we don't have a stored reminderScheduleName.
     */
    private void createWithUpdateFallback(String scheduleName, String scheduleExpression, Target target) {
        try {
            doCreateSchedule(scheduleName, scheduleExpression, target);
            logger.info("Created schedule: {} with expression: {}", scheduleName, scheduleExpression);

        } catch (ConflictException e) {
            // Schedule already exists (race condition or stale data) - update it
            logger.info("Schedule {} already exists, updating instead", scheduleName);
            try {
                doUpdateSchedule(scheduleName, scheduleExpression, target);
                logger.info("Updated schedule: {} with expression: {}", scheduleName, scheduleExpression);
            } catch (ResourceNotFoundException re) {
                // Race condition - another process deleted it, try create again
                logger.warn("Race condition updating schedule {}, retrying create", scheduleName);
                doCreateSchedule(scheduleName, scheduleExpression, target);
            }
        }
    }

    private void doCreateSchedule(String scheduleName, String scheduleExpression, Target target) {
        CreateScheduleRequest request = CreateScheduleRequest.builder()
                .name(scheduleName)
                .groupName(config.getGroupName())
                .scheduleExpression(scheduleExpression)
                .flexibleTimeWindow(FlexibleTimeWindow.builder()
                        .mode(FlexibleTimeWindowMode.OFF)
                        .build())
                .target(target)
                .actionAfterCompletion(ActionAfterCompletion.DELETE)
                .build();

        schedulerClient.createSchedule(request);
    }

    private void doUpdateSchedule(String scheduleName, String scheduleExpression, Target target) {
        UpdateScheduleRequest request = UpdateScheduleRequest.builder()
                .name(scheduleName)
                .groupName(config.getGroupName())
                .scheduleExpression(scheduleExpression)
                .flexibleTimeWindow(FlexibleTimeWindow.builder()
                        .mode(FlexibleTimeWindowMode.OFF)
                        .build())
                .target(target)
                .actionAfterCompletion(ActionAfterCompletion.DELETE)
                .build();

        schedulerClient.updateSchedule(request);
    }

    private Target buildTarget(String inputJson) {
        return Target.builder()
                .arn(config.getQueueArn())
                .roleArn(config.getRoleArn())
                .input(inputJson)
                .deadLetterConfig(DeadLetterConfig.builder()
                        .arn(config.getDlqArn())
                        .build())
                .retryPolicy(RetryPolicy.builder()
                        .maximumRetryAttempts(2)
                        .maximumEventAgeInSeconds(3600) // 1 hour
                        .build())
                .build();
    }
}
