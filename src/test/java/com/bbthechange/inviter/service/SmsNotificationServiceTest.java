package com.bbthechange.inviter.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sns.model.SnsException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmsNotificationServiceTest {

    @Mock
    private SnsClient mockSnsClient;

    private SmsNotificationService smsNotificationService;

    @Test
    void sendVerificationCode_WhenPhoneNumberInAllowlist_LogsCodeAndDoesNotCallSNS() {
        // Given: service with allowlisted phone number
        smsNotificationService = new SmsNotificationService(mockSnsClient, "+15551234567,+19995550001");
        String allowlistedPhone = "+15551234567";
        String code = "123456";

        // When
        smsNotificationService.sendVerificationCode(allowlistedPhone, code);

        // Then: SNS should not be called
        verifyNoInteractions(mockSnsClient);
    }

    @Test
    void sendVerificationCode_WhenPhoneNumberNotInAllowlist_CallsSNSWithCorrectParameters() {
        // Given: service with empty allowlist
        smsNotificationService = new SmsNotificationService(mockSnsClient, "");
        String phoneNumber = "+15551234567";
        String code = "123456";
        PublishResponse mockResponse = PublishResponse.builder()
                .messageId("msg-123")
                .build();
        when(mockSnsClient.publish(any(PublishRequest.class))).thenReturn(mockResponse);

        // When
        smsNotificationService.sendVerificationCode(phoneNumber, code);

        // Then: verify SNS was called with correct parameters
        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(mockSnsClient).publish(requestCaptor.capture());

        PublishRequest actualRequest = requestCaptor.getValue();
        assertThat(actualRequest.phoneNumber()).isEqualTo(phoneNumber);
        assertThat(actualRequest.message()).contains(code);
        assertThat(actualRequest.message()).contains("Your Inviter code is");
        assertThat(actualRequest.message()).contains("15 minutes");
        assertThat(actualRequest.messageAttributes()).containsKey("AWS.SNS.SMS.SMSType");
    }

    @Test
    void sendVerificationCode_WhenAllowlistIsEmpty_CallsSNSForAllNumbers() {
        // Given: service with empty allowlist
        smsNotificationService = new SmsNotificationService(mockSnsClient, "");
        String phoneNumber = "+19995550001";
        String code = "654321";
        PublishResponse mockResponse = PublishResponse.builder()
                .messageId("msg-456")
                .build();
        when(mockSnsClient.publish(any(PublishRequest.class))).thenReturn(mockResponse);

        // When
        smsNotificationService.sendVerificationCode(phoneNumber, code);

        // Then: verify SNS was called
        verify(mockSnsClient).publish(any(PublishRequest.class));
    }

    @Test
    void sendVerificationCode_WhenCallingRealSNS_SendsCorrectMessageFormat() {
        // Given
        smsNotificationService = new SmsNotificationService(mockSnsClient, "");
        String phoneNumber = "+15551234567";
        String code = "789012";
        PublishResponse mockResponse = PublishResponse.builder()
                .messageId("msg-789")
                .build();
        when(mockSnsClient.publish(any(PublishRequest.class))).thenReturn(mockResponse);

        // When
        smsNotificationService.sendVerificationCode(phoneNumber, code);

        // Then
        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(mockSnsClient).publish(requestCaptor.capture());

        PublishRequest actualRequest = requestCaptor.getValue();
        String expectedMessage = "Your Inviter code is 789012. Do not share it with anyone. This code expires in 15 minutes.";
        assertThat(actualRequest.message()).isEqualTo(expectedMessage);
    }

    @Test
    void sendVerificationCode_WhenCallingRealSNS_SetsTransactionalMessageType() {
        // Given
        smsNotificationService = new SmsNotificationService(mockSnsClient, "");
        String phoneNumber = "+15551234567";
        String code = "345678";
        PublishResponse mockResponse = PublishResponse.builder()
                .messageId("msg-345")
                .build();
        when(mockSnsClient.publish(any(PublishRequest.class))).thenReturn(mockResponse);

        // When
        smsNotificationService.sendVerificationCode(phoneNumber, code);

        // Then
        ArgumentCaptor<PublishRequest> requestCaptor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(mockSnsClient).publish(requestCaptor.capture());

        PublishRequest actualRequest = requestCaptor.getValue();
        MessageAttributeValue smsType = actualRequest.messageAttributes().get("AWS.SNS.SMS.SMSType");
        assertThat(smsType.stringValue()).isEqualTo("Transactional");
        assertThat(smsType.dataType()).isEqualTo("String");
    }

    @Test
    void sendVerificationCode_WhenSNSThrowsException_ThrowsRuntimeException() {
        // Given
        smsNotificationService = new SmsNotificationService(mockSnsClient, "");
        String phoneNumber = "+15551234567";
        String code = "456789";
        when(mockSnsClient.publish(any(PublishRequest.class)))
                .thenThrow(new RuntimeException("Network error"));

        // When & Then
        assertThatThrownBy(() -> smsNotificationService.sendVerificationCode(phoneNumber, code))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to send verification SMS")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void sendVerificationCode_WhenSNSThrowsSnsException_ThrowsRuntimeExceptionWithCause() {
        // Given
        smsNotificationService = new SmsNotificationService(mockSnsClient, "");
        String phoneNumber = "+15551234567";
        String code = "567890";
        
        AwsErrorDetails errorDetails = AwsErrorDetails.builder()
                .errorMessage("Invalid phone number")
                .errorCode("InvalidParameter")
                .build();
        SnsException snsException = (SnsException) SnsException.builder()
                .awsErrorDetails(errorDetails)
                .build();
        
        when(mockSnsClient.publish(any(PublishRequest.class))).thenThrow(snsException);

        // When & Then
        assertThatThrownBy(() -> smsNotificationService.sendVerificationCode(phoneNumber, code))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to send verification SMS")
                .hasCauseInstanceOf(SnsException.class);
    }

    @Test
    void sendVerificationCode_WhenPhoneNumberInAllowlistWithSpaces_LogsCodeAndDoesNotCallSNS() {
        // Given: allowlist contains number with spaces
        smsNotificationService = new SmsNotificationService(mockSnsClient, " +15551234567 , +19995550001 ");
        String phoneNumber = "+15551234567";
        String code = "111111";

        // When
        smsNotificationService.sendVerificationCode(phoneNumber, code);

        // Then: SNS should not be called (number matches after trimming)
        verifyNoInteractions(mockSnsClient);
    }

    @Test
    void constructor_WithMultipleAllowlistNumbers_ParsesCorrectly() {
        // Given: multiple numbers in allowlist
        String allowlistString = "+15551234567,+19995550001,+18885554321";
        
        // When
        smsNotificationService = new SmsNotificationService(mockSnsClient, allowlistString);
        
        // Then: verify each number is recognized as allowlisted
        // Test first number
        smsNotificationService.sendVerificationCode("+15551234567", "123456");
        verifyNoInteractions(mockSnsClient);
        
        // Test second number
        reset(mockSnsClient);
        smsNotificationService.sendVerificationCode("+19995550001", "234567");
        verifyNoInteractions(mockSnsClient);
        
        // Test third number
        reset(mockSnsClient);
        smsNotificationService.sendVerificationCode("+18885554321", "345678");
        verifyNoInteractions(mockSnsClient);
    }

    @Test
    void constructor_WithEmptyAllowlist_EnablesProductionMode() {
        // Given: empty allowlist string
        smsNotificationService = new SmsNotificationService(mockSnsClient, "");
        String phoneNumber = "+15551234567";
        String code = "999999";
        PublishResponse mockResponse = PublishResponse.builder()
                .messageId("msg-999")
                .build();
        when(mockSnsClient.publish(any(PublishRequest.class))).thenReturn(mockResponse);

        // When
        smsNotificationService.sendVerificationCode(phoneNumber, code);

        // Then: SNS should be called (production mode)
        verify(mockSnsClient).publish(any(PublishRequest.class));
    }
}