package com.bbthechange.inviter.listener;

import com.bbthechange.inviter.dto.watchparty.sqs.*;
import com.bbthechange.inviter.service.WatchPartyBackgroundService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * SQS listener for episode action messages.
 * Processes NEW_EPISODE, UPDATE_TITLE, and REMOVE_EPISODE messages
 * by routing them to the appropriate background service methods.
 *
 * Uses maxConcurrentMessages=3 for parallel processing.
 */
@Component
@ConditionalOnProperty(name = "watchparty.sqs.enabled", havingValue = "true")
public class EpisodeActionListener {

    private static final Logger logger = LoggerFactory.getLogger(EpisodeActionListener.class);

    private final WatchPartyBackgroundService backgroundService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public EpisodeActionListener(
            WatchPartyBackgroundService backgroundService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.backgroundService = backgroundService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @SqsListener(value = "${watchparty.episode-actions-queue}", factory = "episodeActionListenerFactory")
    public void handleMessage(String messageBody) {
        String messageType = null;
        try {
            // Parse to get the type first
            JsonNode node = objectMapper.readTree(messageBody);
            JsonNode typeNode = node.get("type");

            if (typeNode == null || typeNode.isNull()) {
                logger.warn("Received message without type field: {}", messageBody);
                meterRegistry.counter("watchparty_episode_action_total", "type", "unknown", "status", "missing_type").increment();
                return;
            }

            messageType = typeNode.asText();
            logger.info("Processing episode action: type={}", messageType);

            switch (messageType) {
                case NewEpisodeMessage.TYPE -> {
                    NewEpisodeMessage newEpisodeMessage = objectMapper.readValue(messageBody, NewEpisodeMessage.class);
                    backgroundService.processNewEpisode(newEpisodeMessage);
                }
                case UpdateTitleMessage.TYPE -> {
                    UpdateTitleMessage updateTitleMessage = objectMapper.readValue(messageBody, UpdateTitleMessage.class);
                    backgroundService.processUpdateTitle(updateTitleMessage);
                }
                case RemoveEpisodeMessage.TYPE -> {
                    RemoveEpisodeMessage removeEpisodeMessage = objectMapper.readValue(messageBody, RemoveEpisodeMessage.class);
                    backgroundService.processRemoveEpisode(removeEpisodeMessage);
                }
                default -> {
                    logger.warn("Unknown message type: {}", messageType);
                    meterRegistry.counter("watchparty_episode_action_total", "type", messageType, "status", "unknown_type").increment();
                    return;
                }
            }

            meterRegistry.counter("watchparty_episode_action_total", "type", messageType, "status", "success").increment();

        } catch (Exception e) {
            String type = messageType != null ? messageType : "unknown";
            logger.error("Error processing episode action message (type={}): {}", type, messageBody, e);
            meterRegistry.counter("watchparty_episode_action_total", "type", type, "status", "error").increment();
            // Don't rethrow - acknowledge message to prevent infinite retry
        }
    }
}
