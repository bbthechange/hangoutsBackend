package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.watchparty.sqs.*;
import com.bbthechange.inviter.service.WatchPartySqsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of WatchPartySqsService for sending messages to SQS queues.
 * Only active when watchparty.sqs.enabled=true.
 */
@Service
@ConditionalOnProperty(name = "watchparty.sqs.enabled", havingValue = "true")
public class WatchPartySqsServiceImpl implements WatchPartySqsService {

    private static final Logger logger = LoggerFactory.getLogger(WatchPartySqsServiceImpl.class);

    private final SqsAsyncClient sqsAsyncClient;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final String tvMazeUpdatesQueueUrl;
    private final String episodeActionsQueueUrl;

    public WatchPartySqsServiceImpl(
            SqsAsyncClient sqsAsyncClient,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            @Value("${watchparty.tvmaze-updates-queue-url}") String tvMazeUpdatesQueueUrl,
            @Value("${watchparty.episode-actions-queue-url}") String episodeActionsQueueUrl) {
        this.sqsAsyncClient = sqsAsyncClient;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.tvMazeUpdatesQueueUrl = tvMazeUpdatesQueueUrl;
        this.episodeActionsQueueUrl = episodeActionsQueueUrl;
    }

    @Override
    public void sendToTvMazeUpdatesQueue(WatchPartyMessage message) {
        sendMessage(tvMazeUpdatesQueueUrl, message, "tvmaze-updates");
    }

    @Override
    public void sendToEpisodeActionsQueue(WatchPartyMessage message) {
        sendMessage(episodeActionsQueueUrl, message, "episode-actions");
    }

    @Override
    public void sendNewEpisode(String seasonKey, EpisodeData episode) {
        NewEpisodeMessage message = new NewEpisodeMessage(seasonKey, episode);
        message.setMessageId(UUID.randomUUID().toString());
        sendToEpisodeActionsQueue(message);
    }

    @Override
    public void sendUpdateTitle(String externalId, String newTitle) {
        UpdateTitleMessage message = new UpdateTitleMessage(externalId, newTitle);
        message.setMessageId(UUID.randomUUID().toString());
        sendToEpisodeActionsQueue(message);
    }

    @Override
    public void sendRemoveEpisode(String externalId) {
        RemoveEpisodeMessage message = new RemoveEpisodeMessage(externalId);
        message.setMessageId(UUID.randomUUID().toString());
        sendToEpisodeActionsQueue(message);
    }

    private void sendMessage(String queueUrl, WatchPartyMessage message, String queueName) {
        try {
            if (message.getMessageId() == null) {
                message.setMessageId(UUID.randomUUID().toString());
            }

            String messageBody = objectMapper.writeValueAsString(message);

            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .build();

            CompletableFuture<?> future = sqsAsyncClient.sendMessage(request);

            future.whenComplete((response, error) -> {
                if (error != null) {
                    logger.error("Failed to send message to {} queue: {}", queueName, message.getType(), error);
                    meterRegistry.counter("watchparty_sqs_send_total",
                            "queue", queueName,
                            "type", message.getType(),
                            "status", "error").increment();
                } else {
                    logger.info("Sent message to {} queue: type={}, messageId={}",
                            queueName, message.getType(), message.getMessageId());
                    meterRegistry.counter("watchparty_sqs_send_total",
                            "queue", queueName,
                            "type", message.getType(),
                            "status", "success").increment();
                }
            });

        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize message for {} queue: {}", queueName, message.getType(), e);
            meterRegistry.counter("watchparty_sqs_send_total",
                    "queue", queueName,
                    "type", message.getType(),
                    "status", "serialization_error").increment();
        }
    }
}
