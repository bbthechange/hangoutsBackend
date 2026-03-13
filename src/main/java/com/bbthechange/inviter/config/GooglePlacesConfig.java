package com.bbthechange.inviter.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

/**
 * Configuration for Google Places API integration.
 *
 * Retrieves API key from AWS Systems Manager Parameter Store in production,
 * falls back to environment variable for local development.
 */
@Configuration
public class GooglePlacesConfig {

    private static final Logger logger = LoggerFactory.getLogger(GooglePlacesConfig.class);

    @Value("${google.places.api-key-parameter-name:}")
    private String apiKeyParameterName;

    @Value("${google.places.api-key:}")
    private String localApiKey;

    @Value("${aws.region:us-west-2}")
    private String awsRegion;

    /**
     * Provides Google Places API key from Parameter Store in production.
     */
    @Bean("googlePlacesApiKey")
    @ConditionalOnProperty(name = "google.places.use-parameter-store", havingValue = "true", matchIfMissing = false)
    public String googlePlacesApiKey() {
        logger.info("Retrieving Google Places API key from Parameter Store: {}", apiKeyParameterName);

        try (SsmClient ssmClient = SsmClient.builder()
                .region(Region.of(awsRegion))
                .build()) {

            GetParameterRequest parameterRequest = GetParameterRequest.builder()
                    .name(apiKeyParameterName)
                    .withDecryption(true)
                    .build();

            GetParameterResponse parameterResponse = ssmClient.getParameter(parameterRequest);
            String apiKey = parameterResponse.parameter().value();

            logger.info("Google Places API key retrieved successfully from Parameter Store");
            return apiKey;

        } catch (Exception e) {
            logger.error("Failed to retrieve Google Places API key from Parameter Store. Place enrichment will be disabled.", e);
            return "";
        }
    }

    /**
     * Provides Google Places API key from local configuration for development.
     * Active when Parameter Store is disabled (default for local development).
     */
    @Bean("googlePlacesApiKey")
    @ConditionalOnProperty(name = "google.places.use-parameter-store", havingValue = "false", matchIfMissing = true)
    public String googlePlacesApiKeyLocal() {
        if (localApiKey == null || localApiKey.isBlank()) {
            logger.info("Google Places API key not configured. Place enrichment will be disabled.");
            return "";
        }
        logger.info("Using Google Places API key from local configuration");
        return localApiKey;
    }
}
