package com.bbthechange.inviter.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

/**
 * Configuration for Ticketmaster Discovery API integration.
 *
 * Retrieves API key from AWS Systems Manager Parameter Store in production,
 * falls back to environment variable for local development.
 */
@Configuration
public class TicketmasterConfig {

    private static final Logger logger = LoggerFactory.getLogger(TicketmasterConfig.class);

    @Value("${ticketmaster.api-key-parameter-name:}")
    private String apiKeyParameterName;

    @Value("${ticketmaster.api.key:}")
    private String localApiKey;

    @Value("${aws.region:us-west-2}")
    private String awsRegion;

    /**
     * Provides Ticketmaster API key, loading from Parameter Store in production
     * or using local environment variable for development.
     */
    @Bean
    @Qualifier("ticketmasterApiKey")
    @ConditionalOnProperty(name = "ticketmaster.use-parameter-store", havingValue = "true", matchIfMissing = false)
    public String ticketmasterApiKey() {
        logger.info("Retrieving Ticketmaster API key from Parameter Store: {}", apiKeyParameterName);

        try (SsmClient ssmClient = SsmClient.builder()
                .region(Region.of(awsRegion))
                .build()) {

            GetParameterRequest parameterRequest = GetParameterRequest.builder()
                    .name(apiKeyParameterName)
                    .withDecryption(true)
                    .build();

            GetParameterResponse parameterResponse = ssmClient.getParameter(parameterRequest);
            String apiKey = parameterResponse.parameter().value();

            logger.info("Ticketmaster API key retrieved successfully from Parameter Store");
            return apiKey;

        } catch (Exception e) {
            logger.error("Failed to retrieve Ticketmaster API key from Parameter Store", e);
            throw new RuntimeException("Failed to initialize Ticketmaster API configuration", e);
        }
    }

    /**
     * Provides Ticketmaster API key from local configuration for development.
     * Active when Parameter Store is disabled (default for local development).
     */
    @Bean
    @Qualifier("ticketmasterApiKey")
    @ConditionalOnProperty(name = "ticketmaster.use-parameter-store", havingValue = "false", matchIfMissing = true)
    public String ticketmasterApiKeyLocal() {
        if (localApiKey == null || localApiKey.isBlank()) {
            logger.warn("Ticketmaster API key not configured. Event parsing from Ticketmaster URLs will fail.");
            return "";
        }
        logger.info("Using Ticketmaster API key from local configuration");
        return localApiKey;
    }
}
