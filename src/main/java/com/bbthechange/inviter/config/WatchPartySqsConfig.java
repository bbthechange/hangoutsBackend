package com.bbthechange.inviter.config;

import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.time.Duration;

/**
 * Configuration for Watch Party SQS message listeners.
 * Creates two listener factories with different concurrency settings:
 * - tvMazeUpdateListenerFactory: maxConcurrentMessages=1 (rate limit protection for TVMaze API)
 * - episodeActionListenerFactory: maxConcurrentMessages=3 (parallel processing for episode actions)
 *
 * Only enabled when watchparty.sqs.enabled=true.
 */
@Configuration
@ConditionalOnProperty(name = "watchparty.sqs.enabled", havingValue = "true")
public class WatchPartySqsConfig {

    @Value("${aws.region}")
    private String region;

    /**
     * Listener factory for TVMaze update messages.
     * Uses maxConcurrentMessages=1 to respect TVMaze API rate limits.
     * Messages from this queue trigger fetches to TVMaze API.
     */
    @Bean
    public SqsMessageListenerContainerFactory<Object> tvMazeUpdateListenerFactory(
            SqsAsyncClient sqsAsyncClient) {
        return SqsMessageListenerContainerFactory.builder()
                .sqsAsyncClient(sqsAsyncClient)
                .configure(options -> options
                        .maxConcurrentMessages(1)
                        .maxMessagesPerPoll(1)
                        .pollTimeout(Duration.ofSeconds(20))
                        .acknowledgementShutdownTimeout(Duration.ofSeconds(30)))
                .build();
    }

    /**
     * Listener factory for episode action messages.
     * Uses maxConcurrentMessages=3 for parallel processing.
     * These messages don't call external APIs, only DynamoDB operations.
     */
    @Bean
    public SqsMessageListenerContainerFactory<Object> episodeActionListenerFactory(
            SqsAsyncClient sqsAsyncClient) {
        return SqsMessageListenerContainerFactory.builder()
                .sqsAsyncClient(sqsAsyncClient)
                .configure(options -> options
                        .maxConcurrentMessages(3)
                        .maxMessagesPerPoll(3)
                        .pollTimeout(Duration.ofSeconds(20))
                        .acknowledgementShutdownTimeout(Duration.ofSeconds(30)))
                .build();
    }
}
