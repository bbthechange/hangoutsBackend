package com.bbthechange.inviter.listener;

import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * SQS listener for hangout reminder messages from EventBridge Scheduler.
 * Processes reminder requests and sends push notifications.
 *
 * Message format: {"hangoutId":"..."}
 *
 * Idempotency: Uses atomic DynamoDB update (setReminderSentAtIfNull) to ensure
 * only one reminder is sent even if message is delivered multiple times.
 */
@Component
@ConditionalOnProperty(name = "scheduler.enabled", havingValue = "true")
public class HangoutReminderListener {

    private static final Logger logger = LoggerFactory.getLogger(HangoutReminderListener.class);

    // Reminder window: 90-150 minutes before start (2 hours +/- 30 min tolerance)
    private static final long MIN_MINUTES_BEFORE_START = 90;
    private static final long MAX_MINUTES_BEFORE_START = 150;

    private final HangoutRepository hangoutRepository;
    private final NotificationService notificationService;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    public HangoutReminderListener(HangoutRepository hangoutRepository,
                                   NotificationService notificationService,
                                   MeterRegistry meterRegistry,
                                   ObjectMapper objectMapper) {
        this.hangoutRepository = hangoutRepository;
        this.notificationService = notificationService;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
    }

    @SqsListener(value = "${scheduler.queue-name:hangout-reminders}")
    public void handleReminder(String messageBody) {
        String hangoutId = null;
        try {
            // Parse hangoutId from message
            JsonNode node = objectMapper.readTree(messageBody);
            JsonNode hangoutIdNode = node.get("hangoutId");

            if (hangoutIdNode == null || hangoutIdNode.isNull()) {
                logger.warn("Received reminder with missing hangoutId field: {}", messageBody);
                meterRegistry.counter("hangout_reminder_total", "status", "missing_id").increment();
                return;
            }

            hangoutId = hangoutIdNode.asText();
            if (hangoutId.isBlank()) {
                logger.warn("Received reminder with blank hangoutId: {}", messageBody);
                meterRegistry.counter("hangout_reminder_total", "status", "missing_id").increment();
                return;
            }

            logger.info("Processing reminder for hangout: {}", hangoutId);
            processReminder(hangoutId);

        } catch (Exception e) {
            logger.error("Error processing reminder message: {}", messageBody, e);
            meterRegistry.counter("hangout_reminder_total", "status", "error").increment();
            // Don't rethrow - message will be acknowledged and deleted
            // Retrying won't help for parse errors or DB issues we've already logged
        }
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
