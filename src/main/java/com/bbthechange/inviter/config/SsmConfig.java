package com.bbthechange.inviter.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;

/**
 * Configuration for AWS Systems Manager (SSM) Parameter Store client.
 * Provides a singleton SsmClient for efficient parameter retrieval.
 * Only enabled when internal.api-key-enabled=true.
 */
@Configuration
public class SsmConfig {

    private static final Logger logger = LoggerFactory.getLogger(SsmConfig.class);

    @Value("${aws.region:us-west-2}")
    private String awsRegion;

    @Bean
    @ConditionalOnProperty(name = "internal.api-key-enabled", havingValue = "true", matchIfMissing = false)
    public SsmClient ssmClient() {
        logger.info("Initializing SSM client for internal API key retrieval");
        return SsmClient.builder()
                .region(Region.of(awsRegion))
                .build();
    }
}
