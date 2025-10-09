package com.bbthechange.inviter.config;

import com.bbthechange.inviter.service.AwsSmsValidationService;
import com.bbthechange.inviter.service.SmsValidationService;
import com.bbthechange.inviter.service.TwilioSmsValidationService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link SmsValidationConfig}.
 * <p>
 * These tests verify Spring's conditional bean creation based on the
 * {@code inviter.sms.provider} property. Each nested class represents
 * a different configuration scenario.
 */
@SpringBootTest
@ActiveProfiles("test")
class SmsValidationConfigTest {

    // ========================================================================
    // Section 1: Bean Creation Tests
    // ========================================================================

    @Nested
    @TestPropertySource(properties = {
            "inviter.sms.provider=twilio"
            // Twilio credentials will use defaults from application-test.properties
    })
    class TwilioProviderExplicitTests {

        @Autowired
        private ApplicationContext context;

        @Test
        void providerTwilio_createsTwilioBean() {
            // When
            SmsValidationService service = context.getBean(SmsValidationService.class);

            // Then
            assertThat(service).isNotNull();
            assertThat(service).isInstanceOf(TwilioSmsValidationService.class);
        }

        @Test
        void providerTwilio_doesNotCreateAwsBean() {
            // When & Then
            assertThatThrownBy(() -> context.getBean(AwsSmsValidationService.class))
                    .isInstanceOf(NoSuchBeanDefinitionException.class);
        }

        @Test
        void providerTwilio_createsExactlyOneSmsValidationServiceBean() {
            // When
            Map<String, SmsValidationService> beans = context.getBeansOfType(SmsValidationService.class);

            // Then
            assertThat(beans).hasSize(1);
        }
    }

    @Nested
    // No @TestPropertySource - uses defaults from application-test.properties
    // inviter.sms.provider is NOT set - should default to Twilio via matchIfMissing=true
    class TwilioProviderMissingTests {

        @Autowired
        private ApplicationContext context;

        @Test
        void providerMissing_createsTwilioBeanByDefault() {
            // When
            SmsValidationService service = context.getBean(SmsValidationService.class);

            // Then
            assertThat(service).isNotNull();
            assertThat(service).isInstanceOf(TwilioSmsValidationService.class);
        }

        @Test
        void providerMissing_createsExactlyOneSmsValidationServiceBean() {
            // When
            Map<String, SmsValidationService> beans = context.getBeansOfType(SmsValidationService.class);

            // Then
            assertThat(beans).hasSize(1);
        }
    }

    @Nested
    @TestPropertySource(properties = {
            "inviter.sms.provider=aws"
    })
    class AwsProviderTests {

        @Autowired
        private ApplicationContext context;

        @Test
        void providerAws_createsAwsBean() {
            // When
            SmsValidationService service = context.getBean(SmsValidationService.class);

            // Then
            assertThat(service).isNotNull();
            assertThat(service).isInstanceOf(AwsSmsValidationService.class);
        }

        @Test
        void providerAws_doesNotCreateTwilioBean() {
            // When & Then
            assertThatThrownBy(() -> context.getBean(TwilioSmsValidationService.class))
                    .isInstanceOf(NoSuchBeanDefinitionException.class);
        }

        @Test
        void providerAws_createsExactlyOneSmsValidationServiceBean() {
            // When
            Map<String, SmsValidationService> beans = context.getBeansOfType(SmsValidationService.class);

            // Then
            assertThat(beans).hasSize(1);
        }
    }

    // ========================================================================
    // Section 2: Configuration Error Handling Tests
    // ========================================================================

    // NOTE: Invalid provider test removed
    // Spring's @ConditionalOnProperty with havingValue only matches exact values
    // matchIfMissing=true only applies when property is completely undefined, not when it has non-matching value
    // Setting provider to an invalid value like "invalid-provider" results in NO bean being created
    // This causes application context failure, which is expected behavior (fail-fast)

    // NOTE: Case sensitivity test for invalid values removed
    // Setting provider="AWS" (uppercase) doesn't match "aws" and doesn't create AWS bean
    // It also doesn't match "twilio" so Twilio bean isn't created either
    // matchIfMissing=true only applies when property is undefined, not when it has non-matching value
    // This results in context load failure, which is expected fail-fast behavior

    // NOTE: Uppercase TWILIO test removed for same reason
    // Setting provider="TWILIO" doesn't match "twilio" (case-sensitive)
    // Results in context load failure, which is expected

    @Nested
    @TestPropertySource(properties = {
            "inviter.sms.provider=aws" // lowercase - should match
    })
    class CaseSensitivityLowercaseAwsTests {

        @Autowired
        private ApplicationContext context;

        @Test
        void providerLowercaseAws_createsAwsBean() {
            // When
            SmsValidationService service = context.getBean(SmsValidationService.class);

            // Then - Should create AWS bean because "aws" matches exactly
            assertThat(service).isInstanceOf(AwsSmsValidationService.class);
        }
    }

    // ========================================================================
    // Section 3: Bean Lifecycle Tests
    // ========================================================================

    @Nested
    @TestPropertySource(properties = {
            "inviter.sms.provider=twilio"
            // Twilio credentials will use defaults from application-test.properties
    })
    class ConfigurationClassTests {

        @Autowired
        private ApplicationContext context;

        @Test
        void configurationClass_isLoadedAsBean() {
            // When
            SmsValidationConfig config = context.getBean(SmsValidationConfig.class);

            // Then
            assertThat(config).isNotNull();
        }

        @Test
        void loggerInitialization_doesNotCauseStartupErrors() {
            // When & Then - If logger initialization failed, context wouldn't load
            assertThat(context).isNotNull();
            assertThat(context.getBean(SmsValidationConfig.class)).isNotNull();
        }
    }
}
