package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.watchparty.PollResult;
import com.bbthechange.inviter.dto.watchparty.ReformatTitlesResult;
import com.bbthechange.inviter.dto.watchparty.sqs.*;
import com.bbthechange.inviter.exception.ResourceNotFoundException;
import com.bbthechange.inviter.service.TvMazePollingService;
import com.bbthechange.inviter.service.WatchPartyService;
import com.bbthechange.inviter.service.WatchPartySqsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Internal controller for Watch Party SQS testing.
 * Allows sending test messages to SQS queues in staging/testing environments.
 * Protected by InternalApiKeyFilter (X-Api-Key header).
 *
 * Only enabled when watchparty.sqs.enabled=true.
 */
@RestController
@RequestMapping("/internal/watch-party")
@ConditionalOnProperty(name = "watchparty.sqs.enabled", havingValue = "true")
public class InternalWatchPartyController {

    private static final Logger logger = LoggerFactory.getLogger(InternalWatchPartyController.class);

    private final WatchPartySqsService sqsService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final Optional<TvMazePollingService> pollingService;
    private final WatchPartyService watchPartyService;

    public InternalWatchPartyController(
            WatchPartySqsService sqsService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            Optional<TvMazePollingService> pollingService,
            WatchPartyService watchPartyService) {
        this.sqsService = sqsService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.pollingService = pollingService;
        this.watchPartyService = watchPartyService;
    }

    /**
     * Send a test message to an SQS queue.
     * Used for testing SQS message processing in staging environments.
     *
     * @param request The test message request containing queue type and message body
     * @return 200 OK with message ID if successful, 400 for invalid input
     */
    @PostMapping("/test-message")
    public ResponseEntity<Map<String, String>> sendTestMessage(@RequestBody TestMessageRequest request) {
        logger.info("Received test message request: queueType={}", request.getQueueType());

        if (request.getQueueType() == null || request.getQueueType().isBlank()) {
            logger.warn("Test message request missing queueType");
            return ResponseEntity.badRequest().body(Map.of("error", "queueType is required"));
        }

        if (request.getMessageBody() == null || request.getMessageBody().isBlank()) {
            logger.warn("Test message request missing messageBody");
            return ResponseEntity.badRequest().body(Map.of("error", "messageBody is required"));
        }

        try {
            WatchPartyMessage message = parseMessage(request.getMessageBody());
            if (message.getMessageId() == null) {
                message.setMessageId(UUID.randomUUID().toString());
            }

            String queueType = request.getQueueType().toLowerCase();
            switch (queueType) {
                case "tvmaze-updates" -> {
                    sqsService.sendToTvMazeUpdatesQueue(message);
                    meterRegistry.counter("watchparty_test_message_total", "queue", "tvmaze-updates", "status", "sent").increment();
                }
                case "episode-actions" -> {
                    sqsService.sendToEpisodeActionsQueue(message);
                    meterRegistry.counter("watchparty_test_message_total", "queue", "episode-actions", "status", "sent").increment();
                }
                default -> {
                    logger.warn("Unknown queue type: {}", queueType);
                    return ResponseEntity.badRequest().body(Map.of("error", "Unknown queueType: " + queueType));
                }
            }

            logger.info("Sent test message to {} queue: messageId={}", queueType, message.getMessageId());
            return ResponseEntity.ok(Map.of(
                    "status", "sent",
                    "messageId", message.getMessageId(),
                    "queue", queueType
            ));

        } catch (Exception e) {
            logger.error("Error sending test message: {}", e.getMessage(), e);
            meterRegistry.counter("watchparty_test_message_total", "queue", request.getQueueType(), "status", "error").increment();
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Trigger a show update by sending a SHOW_UPDATED message.
     * Convenience endpoint for testing show update flow.
     *
     * @param body Request body containing showId
     * @return 200 OK with message ID if successful
     */
    @PostMapping("/trigger-show-update")
    public ResponseEntity<Map<String, String>> triggerShowUpdate(@RequestBody Map<String, Integer> body) {
        Integer showId = body.get("showId");
        if (showId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "showId is required"));
        }

        logger.info("Triggering show update for showId: {}", showId);

        ShowUpdatedMessage message = new ShowUpdatedMessage(showId);
        message.setMessageId(UUID.randomUUID().toString());
        sqsService.sendToTvMazeUpdatesQueue(message);

        meterRegistry.counter("watchparty_test_message_total", "queue", "tvmaze-updates", "type", "SHOW_UPDATED").increment();

        return ResponseEntity.ok(Map.of(
                "status", "triggered",
                "messageId", message.getMessageId(),
                "showId", showId.toString()
        ));
    }

    /**
     * Trigger a poll for TVMaze updates.
     * Checks for updates to all tracked shows and emits SHOW_UPDATED messages.
     *
     * This endpoint is designed to be called by EventBridge Scheduler every 2 hours.
     *
     * @return 200 OK with poll statistics, or 503 if polling is not enabled
     */
    @PostMapping("/trigger-poll")
    public ResponseEntity<Map<String, Object>> triggerPoll() {
        logger.info("Received trigger-poll request");

        if (pollingService.isEmpty()) {
            logger.warn("Polling service not available - watchparty.polling.enabled=false");
            return ResponseEntity.status(503).body(Map.of(
                    "error", "Polling service not enabled",
                    "hint", "Set watchparty.polling.enabled=true to enable polling"
            ));
        }

        try {
            PollResult result = pollingService.get().pollForUpdates();

            meterRegistry.counter("watchparty_poll_trigger_total", "status", "success").increment();

            return ResponseEntity.ok(Map.of(
                    "status", "completed",
                    "totalTrackedShows", result.getTotalTrackedShows(),
                    "updatedShowsFound", result.getUpdatedShowsFound(),
                    "messagesEmitted", result.getMessagesEmitted(),
                    "durationMs", result.getDurationMs()
            ));

        } catch (Exception e) {
            logger.error("Trigger poll failed", e);
            meterRegistry.counter("watchparty_poll_trigger_total", "status", "error").increment();

            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "error", e.getMessage() != null ? e.getMessage() : "Unknown error"
            ));
        }
    }

    /**
     * Re-run the {@code WatchPartyTitleFormatter} against every future-dated generated-title
     * hangout in a series. Use after the curator has written a new {@code ShowFlavor} record
     * so existing hangouts pick up the curated short name without waiting for the next
     * TVMaze title update.
     *
     * <p>Idempotent. No notifications fired. Returns 404 when the series doesn't exist,
     * isn't a watch party, or has no {@code ShowFlavor} for its show.
     */
    @PostMapping("/{seriesId}/reformat-titles")
    public ResponseEntity<?> reformatTitles(@PathVariable String seriesId) {
        logger.info("Reformat titles requested for seriesId={}", seriesId);
        try {
            ReformatTitlesResult result = watchPartyService.reformatWatchPartyTitles(seriesId);
            meterRegistry.counter("watchparty_reformat_titles_total", "status", "success").increment();
            return ResponseEntity.ok(result);
        } catch (ResourceNotFoundException e) {
            logger.info("Reformat titles 404 for seriesId={}: {}", seriesId, e.getMessage());
            meterRegistry.counter("watchparty_reformat_titles_total", "status", "not_found").increment();
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    private WatchPartyMessage parseMessage(String messageBody) throws Exception {
        // Use Jackson polymorphic deserialization
        return objectMapper.readValue(messageBody, WatchPartyMessage.class);
    }
}
