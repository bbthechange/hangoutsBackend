package com.bbthechange.inviter.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
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

@Configuration
public class FcmConfig {

    private static final Logger logger = LoggerFactory.getLogger(FcmConfig.class);

    @Value("${fcm.service-account-parameter-name:}")
    private String serviceAccountParameterName;

    @Value("${aws.region:us-west-2}")
    private String awsRegion;

    @Bean
    @ConditionalOnProperty(name = "fcm.enabled", havingValue = "true", matchIfMissing = false)
    public FirebaseApp firebaseApp() throws IOException {
        logger.info("Initializing Firebase from Parameter Store: {}", serviceAccountParameterName);

        // Check if Firebase is already initialized
        if (!FirebaseApp.getApps().isEmpty()) {
            logger.info("FirebaseApp already initialized, returning existing instance");
            return FirebaseApp.getInstance();
        }

        try (SsmClient ssmClient = SsmClient.builder()
                .region(Region.of(awsRegion))
                .build()) {

            GetParameterRequest parameterRequest = GetParameterRequest.builder()
                    .name(serviceAccountParameterName)
                    .withDecryption(true)
                    .build();

            GetParameterResponse parameterResponse = ssmClient.getParameter(parameterRequest);
            String serviceAccountJson = parameterResponse.parameter().value();

            InputStream serviceAccountStream = new ByteArrayInputStream(serviceAccountJson.getBytes());
            GoogleCredentials credentials = GoogleCredentials.fromStream(serviceAccountStream);

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build();

            FirebaseApp app = FirebaseApp.initializeApp(options);
            logger.info("Firebase initialized successfully");
            return app;

        } catch (Exception e) {
            logger.error("Failed to initialize Firebase from Parameter Store", e);
            throw new RuntimeException("Failed to initialize Firebase", e);
        }
    }
}
