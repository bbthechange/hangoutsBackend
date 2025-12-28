package com.bbthechange.inviter.config;

import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.time.Duration;

/**
 * Configuration for SQS message listeners.
 * Only enabled when scheduler.enabled=true.
 */
@Configuration
@ConditionalOnProperty(name = "scheduler.enabled", havingValue = "true")
public class SqsConfig {

    @Value("${aws.region}")
    private String region;

    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        return SqsAsyncClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public SqsMessageListenerContainerFactory<Object> defaultSqsListenerContainerFactory(
            SqsAsyncClient sqsAsyncClient) {
        return SqsMessageListenerContainerFactory.builder()
                .sqsAsyncClient(sqsAsyncClient)
                .configure(options -> options
                        .maxConcurrentMessages(10)
                        .maxMessagesPerPoll(10)
                        .pollTimeout(Duration.ofSeconds(20))
                        .acknowledgementShutdownTimeout(Duration.ofSeconds(30)))
                .build();
    }
}
