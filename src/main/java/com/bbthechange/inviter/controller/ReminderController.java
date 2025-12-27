package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.NotificationService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Optional;

/**
 * Internal controller for hangout reminder notifications.
 * Called by EventBridge Scheduler via API Destination.
 * Protected by InternalApiKeyFilter (X-Api-Key header).
 */
@RestController
@RequestMapping("/internal/reminders")
public class ReminderController {

    private static final Logger logger = LoggerFactory.getLogger(ReminderController.class);

    // Reminder window: 90-150 minutes before start (2 hours ± 30 min tolerance)
    private static final long MIN_MINUTES_BEFORE_START = 90;
    private static final long MAX_MINUTES_BEFORE_START = 150;

    private final HangoutRepository hangoutRepository;
    private final NotificationService notificationService;
    private final MeterRegistry meterRegistry;

    public ReminderController(HangoutRepository hangoutRepository,
                              NotificationService notificationService,
                              MeterRegistry meterRegistry) {
        this.hangoutRepository = hangoutRepository;
        this.notificationService = notificationService;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Send reminder notification for a hangout.
     * Called by EventBridge Scheduler 2 hours before hangout start.
     *
     * Idempotency: Uses atomic DynamoDB update to ensure only one reminder is sent.
     * Time validation: Skips if start time is outside expected window (stale/rescheduled).
     *
     * @param body Request body containing hangoutId (from EventBridge Scheduler input)
     * @return 200 OK in all cases (scheduler should not retry)
     */
    @PostMapping("/hangouts")
    public ResponseEntity<Void> sendHangoutReminder(@RequestBody java.util.Map<String, String> body) {
        String hangoutId = body.get("hangoutId");
        if (hangoutId == null || hangoutId.isBlank()) {
            logger.warn("Received reminder request with missing hangoutId");
            meterRegistry.counter("hangout_reminder_total", "status", "missing_id").increment();
            return ResponseEntity.badRequest().build();
        }
        logger.info("Received reminder request for hangout: {}", hangoutId);

        try {
            // 1. Fetch hangout
            Optional<Hangout> hangoutOpt = hangoutRepository.findHangoutById(hangoutId);
            if (hangoutOpt.isEmpty()) {
                logger.warn("Hangout not found for reminder: {}", hangoutId);
                meterRegistry.counter("hangout_reminder_total", "status", "not_found").increment();
                return ResponseEntity.ok().build();
            }

            Hangout hangout = hangoutOpt.get();

            // 2. Check if reminder already sent (idempotency)
            if (hangout.getReminderSentAt() != null) {
                logger.info("Reminder already sent for hangout: {} at {}", hangoutId, hangout.getReminderSentAt());
                meterRegistry.counter("hangout_reminder_total", "status", "already_sent").increment();
                return ResponseEntity.ok().build();
            }

            // 3. Verify start time is within expected window
            Long startTimestamp = hangout.getStartTimestamp();
            if (startTimestamp == null) {
                logger.info("Hangout has no start time, skipping reminder: {}", hangoutId);
                meterRegistry.counter("hangout_reminder_total", "status", "no_start_time").increment();
                return ResponseEntity.ok().build();
            }

            long nowSeconds = Instant.now().getEpochSecond();
            long minutesUntilStart = (startTimestamp - nowSeconds) / 60;

            if (minutesUntilStart < MIN_MINUTES_BEFORE_START || minutesUntilStart > MAX_MINUTES_BEFORE_START) {
                logger.warn("Hangout {} start time outside reminder window: {} minutes until start (expected {}-{})",
                        hangoutId, minutesUntilStart, MIN_MINUTES_BEFORE_START, MAX_MINUTES_BEFORE_START);
                meterRegistry.counter("hangout_reminder_total", "status", "outside_window").increment();
                return ResponseEntity.ok().build();
            }

            // 4. Atomic update to claim reminder (prevents race conditions)
            long now = System.currentTimeMillis();
            boolean claimed = hangoutRepository.setReminderSentAtIfNull(hangoutId, now);
            if (!claimed) {
                logger.info("Lost race to send reminder for hangout: {}", hangoutId);
                meterRegistry.counter("hangout_reminder_total", "status", "lost_race").increment();
                return ResponseEntity.ok().build();
            }

            // 5. Send reminder notifications
            logger.info("Sending reminder for hangout: {} (starts in {} minutes)", hangoutId, minutesUntilStart);
            notificationService.sendHangoutReminder(hangout);

            meterRegistry.counter("hangout_reminder_total", "status", "sent").increment();
            logger.info("Successfully sent reminder for hangout: {}", hangoutId);

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            logger.error("Error processing reminder for hangout {}: {}", hangoutId, e.getMessage(), e);
            meterRegistry.counter("hangout_reminder_total", "status", "error").increment();
            // Return 200 to prevent scheduler retries - we'll investigate via logs
            return ResponseEntity.ok().build();
        }
    }
}
