package com.bbthechange.inviter.listener;

import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.NotificationService;
import com.bbthechange.inviter.service.TimePollService;
import com.bbthechange.inviter.service.TimeSuggestionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * SQS listener for scheduled event messages from EventBridge Scheduler.
 * Routes messages by type:
 * - No type / unknown type: hangout reminder (backward compatible)
 * - TIME_SUGGESTION_ADOPTION: time suggestion auto-adoption check
 * - IDEA_ADD_BATCH: batched idea addition notification
 *
 * Idempotency: Reminders use atomic DynamoDB update (setReminderSentAtIfNull).
 * Adoption uses adoptForHangout() which checks suggestion status.
 * Idea batches use TTL-based batch records.
 */
@Component
@ConditionalOnProperty(name = "scheduler.enabled", havingValue = "true")
public class ScheduledEventListener {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledEventListener.class);

    private static final String TYPE_TIME_SUGGESTION_ADOPTION = "TIME_SUGGESTION_ADOPTION";
    private static final String TYPE_POLL_ADOPTION = "POLL_ADOPTION";
    private static final String TYPE_IDEA_ADD_BATCH = "IDEA_ADD_BATCH";

    // Reminder window: 90-150 minutes before start (2 hours +/- 30 min tolerance)
    private static final long MIN_MINUTES_BEFORE_START = 90;
    private static final long MAX_MINUTES_BEFORE_START = 150;

    private final HangoutRepository hangoutRepository;
    private final NotificationService notificationService;
    private final TimeSuggestionService timeSuggestionService;
    private final TimePollService timePollService;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final int shortWindowHours;
    private final int longWindowHours;

    // Injected via setter to avoid circular dependency — batch handling is optional
    private IdeaAddBatchHandler ideaAddBatchHandler;

    public ScheduledEventListener(HangoutRepository hangoutRepository,
                                   NotificationService notificationService,
                                   TimeSuggestionService timeSuggestionService,
                                   TimePollService timePollService,
                                   MeterRegistry meterRegistry,
                                   ObjectMapper objectMapper,
                                   @Value("${time-suggestion.auto-adoption.short-window-hours:24}") int shortWindowHours,
                                   @Value("${time-suggestion.auto-adoption.long-window-hours:48}") int longWindowHours) {
        this.hangoutRepository = hangoutRepository;
        this.notificationService = notificationService;
        this.timeSuggestionService = timeSuggestionService;
        this.timePollService = timePollService;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
        this.shortWindowHours = shortWindowHours;
        this.longWindowHours = longWindowHours;
    }

    /**
     * Set the handler for IDEA_ADD_BATCH messages.
     * Called by IdeaNotificationBatchService to register itself.
     */
    public void setIdeaAddBatchHandler(IdeaAddBatchHandler handler) {
        this.ideaAddBatchHandler = handler;
    }

    @SqsListener(value = "${scheduler.queue-name:hangout-reminders}")
    public void handleMessage(String messageBody) {
        try {
            JsonNode node = objectMapper.readTree(messageBody);
            String type = node.has("type") ? node.get("type").asText() : null;

            if (TYPE_TIME_SUGGESTION_ADOPTION.equals(type)) {
                handleTimeSuggestionAdoption(node, messageBody);
            } else if (TYPE_POLL_ADOPTION.equals(type)) {
                handlePollAdoption(node, messageBody);
            } else if (TYPE_IDEA_ADD_BATCH.equals(type)) {
                handleIdeaAddBatch(node, messageBody);
            } else {
                handleReminder(node, messageBody);
            }
        } catch (Exception e) {
            logger.error("Error processing scheduled event message: {}", messageBody, e);
            // Don't rethrow — acknowledge and delete the message
        }
    }

    private void handleTimeSuggestionAdoption(JsonNode node, String messageBody) {
        JsonNode hangoutIdNode = node.get("hangoutId");
        if (hangoutIdNode == null || hangoutIdNode.isNull() || hangoutIdNode.asText().isBlank()) {
            logger.warn("Received adoption message with missing hangoutId: {}", messageBody);
            meterRegistry.counter("time_suggestion_adoption_total", "status", "missing_id").increment();
            return;
        }

        String hangoutId = hangoutIdNode.asText();
        logger.info("Processing time suggestion adoption for hangout: {}", hangoutId);

        try {
            timeSuggestionService.adoptForHangout(hangoutId, shortWindowHours, longWindowHours);
            meterRegistry.counter("time_suggestion_adoption_total", "status", "processed").increment();
        } catch (Exception e) {
            logger.error("Error during time suggestion adoption for hangout {}: {}", hangoutId, e.getMessage(), e);
            meterRegistry.counter("time_suggestion_adoption_total", "status", "error").increment();
        }
    }

    private void handlePollAdoption(JsonNode node, String messageBody) {
        JsonNode hangoutIdNode = node.get("hangoutId");
        JsonNode pollIdNode = node.get("pollId");
        if (hangoutIdNode == null || hangoutIdNode.isNull() || hangoutIdNode.asText().isBlank()
                || pollIdNode == null || pollIdNode.isNull() || pollIdNode.asText().isBlank()) {
            logger.warn("Received POLL_ADOPTION with missing id(s): {}", messageBody);
            meterRegistry.counter("time_poll_adoption_total", "status", "missing_id").increment();
            return;
        }

        String hangoutId = hangoutIdNode.asText();
        String pollId = pollIdNode.asText();
        logger.info("Processing POLL_ADOPTION for hangout={} poll={}", hangoutId, pollId);

        try {
            timePollService.evaluateAndAdopt(hangoutId, pollId);
            meterRegistry.counter("time_poll_adoption_total", "status", "processed").increment();
        } catch (Exception e) {
            logger.error("Error during POLL_ADOPTION for hangout {} poll {}: {}",
                    hangoutId, pollId, e.getMessage(), e);
            meterRegistry.counter("time_poll_adoption_total", "status", "error").increment();
        }
    }

    private void handleIdeaAddBatch(JsonNode node, String messageBody) {
        if (ideaAddBatchHandler == null) {
            logger.warn("Received IDEA_ADD_BATCH message but no handler registered: {}", messageBody);
            meterRegistry.counter("idea_batch_notification_total", "status", "no_handler").increment();
            return;
        }

        String groupId = getRequiredField(node, "groupId", messageBody, "idea_batch_notification_total");
        String listId = getRequiredField(node, "listId", messageBody, "idea_batch_notification_total");
        String adderId = getRequiredField(node, "adderId", messageBody, "idea_batch_notification_total");
        if (groupId == null || listId == null || adderId == null) {
            return;
        }

        logger.info("Processing idea add batch: group={}, list={}, adder={}", groupId, listId, adderId);
        ideaAddBatchHandler.handleIdeaAddBatch(groupId, listId, adderId);
    }

    private String getRequiredField(JsonNode node, String fieldName, String messageBody, String metricName) {
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode == null || fieldNode.isNull() || fieldNode.asText().isBlank()) {
            logger.warn("Received message with missing {}: {}", fieldName, messageBody);
            meterRegistry.counter(metricName, "status", "missing_field").increment();
            return null;
        }
        return fieldNode.asText();
    }

    private void handleReminder(JsonNode node, String messageBody) {
        JsonNode hangoutIdNode = node.get("hangoutId");

        if (hangoutIdNode == null || hangoutIdNode.isNull()) {
            logger.warn("Received reminder with missing hangoutId field: {}", messageBody);
            meterRegistry.counter("hangout_reminder_total", "status", "missing_id").increment();
            return;
        }

        String hangoutId = hangoutIdNode.asText();
        if (hangoutId.isBlank()) {
            logger.warn("Received reminder with blank hangoutId: {}", messageBody);
            meterRegistry.counter("hangout_reminder_total", "status", "missing_id").increment();
            return;
        }

        logger.info("Processing reminder for hangout: {}", hangoutId);
        processReminder(hangoutId);
    }

    private void processReminder(String hangoutId) {
        // 1. Fetch hangout
        Optional<Hangout> hangoutOpt = hangoutRepository.findHangoutById(hangoutId);
        if (hangoutOpt.isEmpty()) {
            logger.warn("Hangout not found for reminder: {}", hangoutId);
            meterRegistry.counter("hangout_reminder_total", "status", "not_found").increment();
            return;
        }

        Hangout hangout = hangoutOpt.get();

        // 2. Check if reminder already sent (idempotency)
        if (hangout.getReminderSentAt() != null) {
            logger.info("Reminder already sent for hangout: {} at {}", hangoutId, hangout.getReminderSentAt());
            meterRegistry.counter("hangout_reminder_total", "status", "already_sent").increment();
            return;
        }

        // 3. Verify start time is within expected window
        Long startTimestamp = hangout.getStartTimestamp();
        if (startTimestamp == null) {
            logger.info("Hangout has no start time, skipping reminder: {}", hangoutId);
            meterRegistry.counter("hangout_reminder_total", "status", "no_start_time").increment();
            return;
        }

        long nowSeconds = Instant.now().getEpochSecond();
        long minutesUntilStart = (startTimestamp - nowSeconds) / 60;

        if (minutesUntilStart < MIN_MINUTES_BEFORE_START || minutesUntilStart > MAX_MINUTES_BEFORE_START) {
            logger.warn("Hangout {} start time outside reminder window: {} minutes until start (expected {}-{})",
                    hangoutId, minutesUntilStart, MIN_MINUTES_BEFORE_START, MAX_MINUTES_BEFORE_START);
            meterRegistry.counter("hangout_reminder_total", "status", "outside_window").increment();
            return;
        }

        // 4. Atomic update to claim reminder (prevents race conditions/duplicates)
        long now = System.currentTimeMillis();
        boolean claimed = hangoutRepository.setReminderSentAtIfNull(hangoutId, now);
        if (!claimed) {
            logger.info("Lost race to send reminder for hangout: {}", hangoutId);
            meterRegistry.counter("hangout_reminder_total", "status", "lost_race").increment();
            return;
        }

        // 5. Send reminder notifications
        logger.info("Sending reminder for hangout: {} (starts in {} minutes)", hangoutId, minutesUntilStart);
        notificationService.sendHangoutReminder(hangout);

        meterRegistry.counter("hangout_reminder_total", "status", "sent").increment();
        logger.info("Successfully sent reminder for hangout: {}", hangoutId);
    }

    /**
     * Callback interface for handling IDEA_ADD_BATCH messages.
     * Implemented by IdeaNotificationBatchService to avoid tight coupling.
     */
    public interface IdeaAddBatchHandler {
        void handleIdeaAddBatch(String groupId, String listId, String adderId);
    }
}
