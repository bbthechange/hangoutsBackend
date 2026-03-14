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
 * Schedules EventBridge one-shot events for time suggestion auto-adoption.
 *
 * When a time suggestion is created, two schedules are created:
 * 1. At createdAt + shortWindowHours (24h default) — for suggestions with supporters
 * 2. At createdAt + longWindowHours (48h default) — for zero-vote suggestions
 *
 * Both fire and call adoptForHangout(), which is idempotent. If the first event
 * adopts the suggestion, the second is a no-op. Both auto-delete after execution.
 */
@Service
public class TimeSuggestionSchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(TimeSuggestionSchedulerService.class);

    private static final long MIN_SCHEDULE_ADVANCE_SECONDS = 60;

    private static final DateTimeFormatter SCHEDULE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC);

    private final EventBridgeSchedulerClient eventBridgeClient;
    private final ObjectMapper objectMapper;
    private final int shortWindowHours;
    private final int longWindowHours;

    @Autowired
    public TimeSuggestionSchedulerService(
            EventBridgeSchedulerClient eventBridgeClient,
            ObjectMapper objectMapper,
            @Value("${time-suggestion.auto-adoption.short-window-hours:24}") int shortWindowHours,
            @Value("${time-suggestion.auto-adoption.long-window-hours:48}") int longWindowHours) {
        this.eventBridgeClient = eventBridgeClient;
        this.objectMapper = objectMapper;
        this.shortWindowHours = shortWindowHours;
        this.longWindowHours = longWindowHours;
    }

    /**
     * Schedule two adoption check events for a newly created time suggestion.
     *
     * @param hangoutId    The hangout the suggestion belongs to
     * @param suggestionId The suggestion ID (used for schedule naming)
     * @param createdAt    When the suggestion was created
     */
    public void scheduleAdoptionChecks(String hangoutId, String suggestionId, Instant createdAt) {
        if (!eventBridgeClient.isEnabled()) {
            logger.debug("Scheduler disabled, skipping adoption scheduling for suggestion: {}", suggestionId);
            return;
        }

        String inputJson = buildInputJson(hangoutId, suggestionId);

        // Schedule at short window (24h) — handles suggestions with supporters
        scheduleAt(hangoutId, suggestionId, "short", createdAt, shortWindowHours, inputJson);

        // Schedule at long window (48h) — handles zero-vote suggestions
        scheduleAt(hangoutId, suggestionId, "long", createdAt, longWindowHours, inputJson);
    }

    /**
     * Cancel both adoption schedules for a suggestion.
     * Idempotent — safe to call even if schedules don't exist.
     *
     * @param suggestionId The suggestion whose schedules should be cancelled
     */
    public void cancelAdoptionChecks(String suggestionId) {
        if (!eventBridgeClient.isEnabled()) {
            return;
        }

        try {
            eventBridgeClient.deleteSchedule(generateScheduleName(suggestionId, "short"));
            eventBridgeClient.deleteSchedule(generateScheduleName(suggestionId, "long"));
            logger.info("Cancelled adoption schedules for suggestion {}", suggestionId);
        } catch (Exception e) {
            logger.warn("Failed to cancel adoption schedules for suggestion {}: {}", suggestionId, e.getMessage());
        }
    }

    private void scheduleAt(String hangoutId, String suggestionId, String windowLabel,
                            Instant createdAt, int windowHours, String inputJson) {
        long fireAtEpochSeconds = createdAt.getEpochSecond() + (windowHours * 3600L);
        long nowSeconds = Instant.now().getEpochSecond();

        if (fireAtEpochSeconds <= nowSeconds + MIN_SCHEDULE_ADVANCE_SECONDS) {
            logger.info("Adoption {} window already passed for suggestion {} — skipping schedule",
                    windowLabel, suggestionId);
            return;
        }

        String scheduleName = generateScheduleName(suggestionId, windowLabel);
        String scheduleExpression = buildScheduleExpression(fireAtEpochSeconds);

        try {
            eventBridgeClient.createOrUpdateSchedule(scheduleName, scheduleExpression, inputJson, false);
            logger.info("Scheduled {} adoption check for suggestion {} (hangout {}) at {}",
                    windowLabel, suggestionId, hangoutId, scheduleExpression);
        } catch (Exception e) {
            logger.error("Failed to schedule {} adoption check for suggestion {}: {}",
                    windowLabel, suggestionId, e.getMessage(), e);
        }
    }

    private String generateScheduleName(String suggestionId, String windowLabel) {
        return "time-suggestion-" + suggestionId + "-" + windowLabel;
    }

    private String buildScheduleExpression(long epochSeconds) {
        Instant instant = Instant.ofEpochSecond(epochSeconds);
        String formattedTime = SCHEDULE_TIME_FORMATTER.format(instant);
        return "at(" + formattedTime + ")";
    }

    private String buildInputJson(String hangoutId, String suggestionId) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "TIME_SUGGESTION_ADOPTION");
        node.put("hangoutId", hangoutId);
        node.put("suggestionId", suggestionId);
        return node.toString();
    }
}
