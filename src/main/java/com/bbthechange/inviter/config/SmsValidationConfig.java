package com.bbthechange.inviter.config;

import com.bbthechange.inviter.repository.VerificationCodeRepository;
import com.bbthechange.inviter.service.AwsSmsValidationService;
import com.bbthechange.inviter.service.SmsNotificationService;
import com.bbthechange.inviter.service.SmsValidationService;
import com.bbthechange.inviter.service.TwilioSmsValidationService;
import io.micrometer.core.instrument.MeterRegistry;
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
 * Configuration for SMS validation service provider selection.
 * <p>
 * Selects between AWS SNS and Twilio Verify implementations based on the
 * {@code inviter.sms.provider} property. Defaults to Twilio for better international support.
 * <p>
 * Supported providers:
 * - "aws": Uses AWS SNS with local code generation and DynamoDB storage
 * - "twilio": Uses Twilio Verify API for code management
 * <p>
 * For Twilio in production, the auth token is retrieved from AWS Parameter Store.
 */
@Configuration
public class SmsValidationConfig {

    private static final Logger logger = LoggerFactory.getLogger(SmsValidationConfig.class);

    @Value("${inviter.sms.provider:twilio}")
    private String smsProvider;

    @Value("${aws.region:us-west-2}")
    private String awsRegion;

    /**
     * Creates AWS SNS-based SMS validation service.
     * <p>
     * Active when {@code inviter.sms.provider=aws}.
     */
    @Bean
    @ConditionalOnProperty(name = "inviter.sms.provider", havingValue = "aws")
    public SmsValidationService awsSmsValidationService(
            VerificationCodeRepository verificationCodeRepository,
            SmsNotificationService smsNotificationService) {
        logger.info("Configuring AWS SNS SMS validation service");
        return new AwsSmsValidationService(verificationCodeRepository, smsNotificationService);
    }

    /**
     * Creates Twilio Verify API-based SMS validation service.
     * <p>
     * Active when {@code inviter.sms.provider=twilio} or when the property is not set.
     * <p>
     * Configuration:
     * - For staging/prod: All credentials retrieved from AWS Parameter Store
     * - For dev/test: Credentials provided directly via properties or environment variables
     */
    @Bean
    @ConditionalOnProperty(name = "inviter.sms.provider", havingValue = "twilio", matchIfMissing = true)
    public SmsValidationService twilioSmsValidationService(
            @Value("${twilio.account-sid-parameter-name:}") String accountSidParameterName,
            @Value("${twilio.account-sid:}") String accountSidDirect,
            @Value("${twilio.auth-token-parameter-name:}") String authTokenParameterName,
            @Value("${twilio.auth-token:}") String authTokenDirect,
            @Value("${twilio.verify-service-sid-parameter-name:}") String serviceSidParameterName,
            @Value("${twilio.verify-service-sid:}") String serviceSidDirect,
            MeterRegistry meterRegistry) {

        logger.info("Configuring Twilio Verify SMS validation service");

        // Retrieve Account SID
        String accountSid;
        if (accountSidParameterName != null && !accountSidParameterName.isEmpty()) {
            logger.info("Retrieving Twilio account SID from Parameter Store: {}", accountSidParameterName);
            accountSid = retrieveFromParameterStore(accountSidParameterName);
        } else {
            logger.info("Using Twilio account SID from configuration (dev/test mode)");
            accountSid = accountSidDirect;
        }

        // Retrieve Auth Token
        String authToken;
        if (authTokenParameterName != null && !authTokenParameterName.isEmpty()) {
            logger.info("Retrieving Twilio auth token from Parameter Store: {}", authTokenParameterName);
            authToken = retrieveFromParameterStore(authTokenParameterName);
        } else {
            logger.info("Using Twilio auth token from configuration (dev/test mode)");
            authToken = authTokenDirect;
        }

        // Retrieve Verify Service SID
        String verifyServiceSid;
        if (serviceSidParameterName != null && !serviceSidParameterName.isEmpty()) {
            logger.info("Retrieving Twilio verify service SID from Parameter Store: {}", serviceSidParameterName);
            verifyServiceSid = retrieveFromParameterStore(serviceSidParameterName);
        } else {
            logger.info("Using Twilio verify service SID from configuration (dev/test mode)");
            verifyServiceSid = serviceSidDirect;
        }

        return new TwilioSmsValidationService(accountSid, authToken, verifyServiceSid, meterRegistry);
    }

    /**
     * Retrieves a parameter value from AWS Systems Manager Parameter Store.
     */
    private String retrieveFromParameterStore(String parameterName) {
        try (SsmClient ssmClient = SsmClient.builder()
                .region(Region.of(awsRegion))
                .build()) {

            GetParameterRequest parameterRequest = GetParameterRequest.builder()
                    .name(parameterName)
                    .withDecryption(true)
                    .build();

            GetParameterResponse parameterResponse = ssmClient.getParameter(parameterRequest);
            String value = parameterResponse.parameter().value();

            logger.info("Successfully retrieved parameter from Parameter Store: {}", parameterName);
            return value;

        } catch (Exception e) {
            logger.error("Failed to retrieve parameter from Parameter Store: {}", parameterName, e);
            throw new RuntimeException("Failed to retrieve Twilio auth token from Parameter Store", e);
        }
    }
}
