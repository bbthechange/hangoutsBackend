package com.bbthechange.inviter.listener;

import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.NotificationService;
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
 *
 * Idempotency: Reminders use atomic DynamoDB update (setReminderSentAtIfNull).
 * Adoption uses adoptForHangout() which checks suggestion status.
 */
@Component
@ConditionalOnProperty(name = "scheduler.enabled", havingValue = "true")
public class HangoutReminderListener {

    private static final Logger logger = LoggerFactory.getLogger(HangoutReminderListener.class);

    private static final String TYPE_TIME_SUGGESTION_ADOPTION = "TIME_SUGGESTION_ADOPTION";

    // Reminder window: 90-150 minutes before start (2 hours +/- 30 min tolerance)
    private static final long MIN_MINUTES_BEFORE_START = 90;
    private static final long MAX_MINUTES_BEFORE_START = 150;

    private final HangoutRepository hangoutRepository;
    private final NotificationService notificationService;
    private final TimeSuggestionService timeSuggestionService;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final int shortWindowHours;
    private final int longWindowHours;

    public HangoutReminderListener(HangoutRepository hangoutRepository,
                                   NotificationService notificationService,
                                   TimeSuggestionService timeSuggestionService,
                                   MeterRegistry meterRegistry,
                                   ObjectMapper objectMapper,
                                   @Value("${time-suggestion.auto-adoption.short-window-hours:24}") int shortWindowHours,
                                   @Value("${time-suggestion.auto-adoption.long-window-hours:48}") int longWindowHours) {
        this.hangoutRepository = hangoutRepository;
        this.notificationService = notificationService;
        this.timeSuggestionService = timeSuggestionService;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
        this.shortWindowHours = shortWindowHours;
        this.longWindowHours = longWindowHours;
    }

    @SqsListener(value = "${scheduler.queue-name:hangout-reminders}")
    public void handleMessage(String messageBody) {
        try {
            JsonNode node = objectMapper.readTree(messageBody);
            String type = node.has("type") ? node.get("type").asText() : null;

            if (TYPE_TIME_SUGGESTION_ADOPTION.equals(type)) {
                handleTimeSuggestionAdoption(node, messageBody);
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
}
