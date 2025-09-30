package com.bbthechange.inviter.config;

import com.eatthepath.pushy.apns.ApnsClient;
import com.eatthepath.pushy.apns.ApnsClientBuilder;
import com.eatthepath.pushy.apns.auth.ApnsSigningKey;
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Configuration
public class ApnsConfig {

    private static final Logger logger = LoggerFactory.getLogger(ApnsConfig.class);

    @Value("${apns.key-parameter-name:}")
    private String keyParameterName;

    @Value("${apns.key-id:}")
    private String keyId;

    @Value("${apns.team-id:}")
    private String teamId;

    @Value("${apns.production:false}")
    private boolean production;

    @Value("${aws.region:us-west-2}")
    private String awsRegion;

    @Bean
    @ConditionalOnProperty(name = "apns.enabled", havingValue = "true", matchIfMissing = false)
    public ApnsClient apnsClient() throws IOException, InvalidKeyException, NoSuchAlgorithmException {
        logger.info("Initializing APNs client from Parameter Store: {}", keyParameterName);

        // Retrieve APNS key from AWS Systems Manager Parameter Store
        try (SsmClient ssmClient = SsmClient.builder()
                .region(Region.of(awsRegion))
                .build()) {

            GetParameterRequest parameterRequest = GetParameterRequest.builder()
                    .name(keyParameterName)
                    .withDecryption(true)
                    .build();

            GetParameterResponse parameterResponse = ssmClient.getParameter(parameterRequest);
            String keyContent = parameterResponse.parameter().value();

            // Convert key content to InputStream for ApnsSigningKey
            InputStream keyInputStream = new ByteArrayInputStream(keyContent.getBytes());
            ApnsSigningKey signingKey = ApnsSigningKey.loadFromInputStream(keyInputStream, teamId, keyId);

            ApnsClientBuilder builder = new ApnsClientBuilder()
                    .setApnsServer(production ?
                        ApnsClientBuilder.PRODUCTION_APNS_HOST :
                        ApnsClientBuilder.DEVELOPMENT_APNS_HOST)
                    .setSigningKey(signingKey);

            logger.info("APNs client initialized successfully (production: {})", production);
            return builder.build();
        } catch (Exception e) {
            logger.error("Failed to initialize APNs client from Parameter Store", e);
            throw new RuntimeException("Failed to initialize APNs client", e);
        }
    }
}