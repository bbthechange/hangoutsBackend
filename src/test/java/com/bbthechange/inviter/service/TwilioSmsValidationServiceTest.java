package com.bbthechange.inviter.service;

import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import com.twilio.rest.verify.v2.service.VerificationCreator;
import com.twilio.rest.verify.v2.service.VerificationCheckCreator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TwilioSmsValidationServiceTest {

    @Mock
    private VerificationCreator mockVerificationCreator;

    @Mock
    private Verification mockVerification;

    @Mock
    private VerificationCheckCreator mockCheckCreator;

    @Mock
    private VerificationCheck mockCheck;

    private TwilioSmsValidationService service;

    private static final String TEST_ACCOUNT_SID = "test-account-sid";
    private static final String TEST_AUTH_TOKEN = "test-auth-token";
    private static final String TEST_VERIFY_SERVICE_SID = "test-verify-service-sid";

    @BeforeEach
    void setUp() {
        // Note: We cannot easily mock Twilio.init() in setUp() because it's a static method
        // that gets called in the constructor. Instead, we initialize the service in each test
        // within a MockedStatic<Twilio> block, or we accept that Twilio.init() will be called
        // with test credentials (which is harmless as long as we don't make actual API calls).
        service = new TwilioSmsValidationService(
                TEST_ACCOUNT_SID,
                TEST_AUTH_TOKEN,
                TEST_VERIFY_SERVICE_SID
        );
    }

    // ========================================================================
    // Section 1: sendVerificationCode() Tests
    // ========================================================================

    @Test
    void sendVerificationCode_SuccessfulVerificationStart_CallsTwilioApiCorrectly() {
        // Given
        String phoneNumber = "+19995550001";

        try (MockedStatic<Verification> mockedVerificationStatic = mockStatic(Verification.class)) {
            mockedVerificationStatic.when(() -> Verification.creator(TEST_VERIFY_SERVICE_SID, phoneNumber, "sms"))
                    .thenReturn(mockVerificationCreator);
            when(mockVerificationCreator.create()).thenReturn(mockVerification);
            when(mockVerification.getSid()).thenReturn("VE123456");
            when(mockVerification.getStatus()).thenReturn("pending");

            // When
            service.sendVerificationCode(phoneNumber);

            // Then
            mockedVerificationStatic.verify(() -> Verification.creator(TEST_VERIFY_SERVICE_SID, phoneNumber, "sms"));
            verify(mockVerificationCreator).create();
        }
    }

    @Test
    void sendVerificationCode_TwilioApiException_ThrowsRuntimeException() {
        // Given
        String phoneNumber = "+19995550001";
        ApiException apiException = mock(ApiException.class);
        when(apiException.getMessage()).thenReturn("Invalid phone number");
        when(apiException.getCode()).thenReturn(21211);

        try (MockedStatic<Verification> mockedVerificationStatic = mockStatic(Verification.class)) {
            mockedVerificationStatic.when(() -> Verification.creator(anyString(), anyString(), anyString()))
                    .thenReturn(mockVerificationCreator);
            when(mockVerificationCreator.create()).thenThrow(apiException);

            // When & Then
            assertThatThrownBy(() -> service.sendVerificationCode(phoneNumber))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to send verification code via Twilio")
                    .hasCause(apiException);
        }
    }

    @Test
    void sendVerificationCode_NetworkException_ThrowsRuntimeException() {
        // Given
        String phoneNumber = "+19995550001";
        RuntimeException networkException = new RuntimeException("Network error");

        try (MockedStatic<Verification> mockedVerificationStatic = mockStatic(Verification.class)) {
            mockedVerificationStatic.when(() -> Verification.creator(anyString(), anyString(), anyString()))
                    .thenReturn(mockVerificationCreator);
            when(mockVerificationCreator.create()).thenThrow(networkException);

            // When & Then
            assertThatThrownBy(() -> service.sendVerificationCode(phoneNumber))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to send verification code via Twilio");
        }
    }

    @Test
    void sendVerificationCode_UsesSmsChannel() {
        // Given
        String phoneNumber = "+19995550001";

        try (MockedStatic<Verification> mockedVerificationStatic = mockStatic(Verification.class)) {
            mockedVerificationStatic.when(() -> Verification.creator(anyString(), anyString(), eq("sms")))
                    .thenReturn(mockVerificationCreator);
            when(mockVerificationCreator.create()).thenReturn(mockVerification);
            when(mockVerification.getSid()).thenReturn("VE123456");
            when(mockVerification.getStatus()).thenReturn("pending");

            // When
            service.sendVerificationCode(phoneNumber);

            // Then
            mockedVerificationStatic.verify(() -> Verification.creator(anyString(), anyString(), eq("sms")));
        }
    }

    @Test
    void sendVerificationCode_UsesConfiguredVerifyServiceSid() {
        // Given
        String phoneNumber = "+19995550001";

        try (MockedStatic<Verification> mockedVerificationStatic = mockStatic(Verification.class)) {
            mockedVerificationStatic.when(() -> Verification.creator(eq(TEST_VERIFY_SERVICE_SID), anyString(), anyString()))
                    .thenReturn(mockVerificationCreator);
            when(mockVerificationCreator.create()).thenReturn(mockVerification);
            when(mockVerification.getSid()).thenReturn("VE123456");
            when(mockVerification.getStatus()).thenReturn("pending");

            // When
            service.sendVerificationCode(phoneNumber);

            // Then
            mockedVerificationStatic.verify(() -> Verification.creator(eq(TEST_VERIFY_SERVICE_SID), anyString(), anyString()));
        }
    }

    @Test
    void sendVerificationCode_HandlesPhoneNumberCorrectly() {
        // Given
        String[] phoneNumbers = {"+19995550001", "+4412345678", "+81123456789"};

        for (String phoneNumber : phoneNumbers) {
            try (MockedStatic<Verification> mockedVerificationStatic = mockStatic(Verification.class)) {
                mockedVerificationStatic.when(() -> Verification.creator(anyString(), eq(phoneNumber), anyString()))
                        .thenReturn(mockVerificationCreator);
                when(mockVerificationCreator.create()).thenReturn(mockVerification);
                when(mockVerification.getSid()).thenReturn("VE123456");
                when(mockVerification.getStatus()).thenReturn("pending");

                // When
                service.sendVerificationCode(phoneNumber);

                // Then
                mockedVerificationStatic.verify(() -> Verification.creator(anyString(), eq(phoneNumber), anyString()));
            }
        }
    }

    // ========================================================================
    // Section 2: verifyCode() Tests
    // ========================================================================

    @Test
    void verifyCode_ApprovedStatus_ReturnsSuccess() {
        // Given
        String phoneNumber = "+19995550001";
        String code = "123456";

        try (MockedStatic<VerificationCheck> mockedCheckStatic = mockStatic(VerificationCheck.class)) {
            mockedCheckStatic.when(() -> VerificationCheck.creator(TEST_VERIFY_SERVICE_SID))
                    .thenReturn(mockCheckCreator);
            when(mockCheckCreator.setTo(phoneNumber)).thenReturn(mockCheckCreator);
            when(mockCheckCreator.setCode(code)).thenReturn(mockCheckCreator);
            when(mockCheckCreator.create()).thenReturn(mockCheck);
            when(mockCheck.getStatus()).thenReturn("approved");

            // When
            VerificationResult result = service.verifyCode(phoneNumber, code);

            // Then
            assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.SUCCESS);
            assertThat(result.isSuccess()).isTrue();
        }
    }

    @Test
    void verifyCode_PendingStatus_ReturnsInvalidCode() {
        // Given
        String phoneNumber = "+19995550001";
        String code = "999999";

        try (MockedStatic<VerificationCheck> mockedCheckStatic = mockStatic(VerificationCheck.class)) {
            mockedCheckStatic.when(() -> VerificationCheck.creator(TEST_VERIFY_SERVICE_SID))
                    .thenReturn(mockCheckCreator);
            when(mockCheckCreator.setTo(phoneNumber)).thenReturn(mockCheckCreator);
            when(mockCheckCreator.setCode(code)).thenReturn(mockCheckCreator);
            when(mockCheckCreator.create()).thenReturn(mockCheck);
            when(mockCheck.getStatus()).thenReturn("pending");

            // When
            VerificationResult result = service.verifyCode(phoneNumber, code);

            // Then
            assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.INVALID_CODE);
        }
    }

    @Test
    void verifyCode_CanceledStatus_ReturnsInvalidCode() {
        // Given
        String phoneNumber = "+19995550001";
        String code = "123456";

        try (MockedStatic<VerificationCheck> mockedCheckStatic = mockStatic(VerificationCheck.class)) {
            mockedCheckStatic.when(() -> VerificationCheck.creator(TEST_VERIFY_SERVICE_SID))
                    .thenReturn(mockCheckCreator);
            when(mockCheckCreator.setTo(phoneNumber)).thenReturn(mockCheckCreator);
            when(mockCheckCreator.setCode(code)).thenReturn(mockCheckCreator);
            when(mockCheckCreator.create()).thenReturn(mockCheck);
            when(mockCheck.getStatus()).thenReturn("canceled");

            // When
            VerificationResult result = service.verifyCode(phoneNumber, code);

            // Then
            assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.INVALID_CODE);
        }
    }

    @Test
    void verifyCode_ApiExceptionWith404_ReturnsCodeExpired() {
        // Given
        String phoneNumber = "+19995550001";
        String code = "123456";
        ApiException apiException = mock(ApiException.class);
        when(apiException.getStatusCode()).thenReturn(404);
        when(apiException.getMessage()).thenReturn("Not found");

        try (MockedStatic<VerificationCheck> mockedCheckStatic = mockStatic(VerificationCheck.class)) {
            mockedCheckStatic.when(() -> VerificationCheck.creator(TEST_VERIFY_SERVICE_SID))
                    .thenReturn(mockCheckCreator);
            when(mockCheckCreator.setTo(phoneNumber)).thenReturn(mockCheckCreator);
            when(mockCheckCreator.setCode(code)).thenReturn(mockCheckCreator);
            when(mockCheckCreator.create()).thenThrow(apiException);

            // When
            VerificationResult result = service.verifyCode(phoneNumber, code);

            // Then
            assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.CODE_EXPIRED);
        }
    }

    @Test
    void verifyCode_ApiExceptionWith429_ReturnsCodeExpired() {
        // Given
        String phoneNumber = "+19995550001";
        String code = "123456";
        ApiException apiException = mock(ApiException.class);
        when(apiException.getStatusCode()).thenReturn(429);
        when(apiException.getMessage()).thenReturn("Too many requests");

        try (MockedStatic<VerificationCheck> mockedCheckStatic = mockStatic(VerificationCheck.class)) {
            mockedCheckStatic.when(() -> VerificationCheck.creator(TEST_VERIFY_SERVICE_SID))
                    .thenReturn(mockCheckCreator);
            when(mockCheckCreator.setTo(phoneNumber)).thenReturn(mockCheckCreator);
            when(mockCheckCreator.setCode(code)).thenReturn(mockCheckCreator);
            when(mockCheckCreator.create()).thenThrow(apiException);

            // When
            VerificationResult result = service.verifyCode(phoneNumber, code);

            // Then
            assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.CODE_EXPIRED);
        }
    }

    @Test
    void verifyCode_ApiExceptionWith400_ReturnsInvalidCode() {
        // Given
        String phoneNumber = "+19995550001";
        String code = "123456";
        ApiException apiException = mock(ApiException.class);
        when(apiException.getStatusCode()).thenReturn(400);
        when(apiException.getMessage()).thenReturn("Bad request");

        try (MockedStatic<VerificationCheck> mockedCheckStatic = mockStatic(VerificationCheck.class)) {
            mockedCheckStatic.when(() -> VerificationCheck.creator(TEST_VERIFY_SERVICE_SID))
                    .thenReturn(mockCheckCreator);
            when(mockCheckCreator.setTo(phoneNumber)).thenReturn(mockCheckCreator);
            when(mockCheckCreator.setCode(code)).thenReturn(mockCheckCreator);
            when(mockCheckCreator.create()).thenThrow(apiException);

            // When
            VerificationResult result = service.verifyCode(phoneNumber, code);

            // Then
            assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.INVALID_CODE);
        }
    }

    @Test
    void verifyCode_ApiExceptionWith500_ReturnsInvalidCode() {
        // Given
        String phoneNumber = "+19995550001";
        String code = "123456";
        ApiException apiException = mock(ApiException.class);
        when(apiException.getStatusCode()).thenReturn(500);
        when(apiException.getMessage()).thenReturn("Server error");

        try (MockedStatic<VerificationCheck> mockedCheckStatic = mockStatic(VerificationCheck.class)) {
            mockedCheckStatic.when(() -> VerificationCheck.creator(TEST_VERIFY_SERVICE_SID))
                    .thenReturn(mockCheckCreator);
            when(mockCheckCreator.setTo(phoneNumber)).thenReturn(mockCheckCreator);
            when(mockCheckCreator.setCode(code)).thenReturn(mockCheckCreator);
            when(mockCheckCreator.create()).thenThrow(apiException);

            // When
            VerificationResult result = service.verifyCode(phoneNumber, code);

            // Then
            assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.INVALID_CODE);
        }
    }

    @Test
    void verifyCode_ApiExceptionWith503_ReturnsInvalidCode() {
        // Given
        String phoneNumber = "+19995550001";
        String code = "123456";
        ApiException apiException = mock(ApiException.class);
        when(apiException.getStatusCode()).thenReturn(503);
        when(apiException.getMessage()).thenReturn("Service unavailable");

        try (MockedStatic<VerificationCheck> mockedCheckStatic = mockStatic(VerificationCheck.class)) {
            mockedCheckStatic.when(() -> VerificationCheck.creator(TEST_VERIFY_SERVICE_SID))
                    .thenReturn(mockCheckCreator);
            when(mockCheckCreator.setTo(phoneNumber)).thenReturn(mockCheckCreator);
            when(mockCheckCreator.setCode(code)).thenReturn(mockCheckCreator);
            when(mockCheckCreator.create()).thenThrow(apiException);

            // When
            VerificationResult result = service.verifyCode(phoneNumber, code);

            // Then
            assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.INVALID_CODE);
        }
    }

    @Test
    void verifyCode_ApiExceptionWithNullStatusCode_ReturnsInvalidCode() {
        // Given
        String phoneNumber = "+19995550001";
        String code = "123456";
        ApiException apiException = mock(ApiException.class);
        when(apiException.getStatusCode()).thenReturn(null);
        when(apiException.getMessage()).thenReturn("Unknown error");

        try (MockedStatic<VerificationCheck> mockedCheckStatic = mockStatic(VerificationCheck.class)) {
            mockedCheckStatic.when(() -> VerificationCheck.creator(TEST_VERIFY_SERVICE_SID))
                    .thenReturn(mockCheckCreator);
            when(mockCheckCreator.setTo(phoneNumber)).thenReturn(mockCheckCreator);
            when(mockCheckCreator.setCode(code)).thenReturn(mockCheckCreator);
            when(mockCheckCreator.create()).thenThrow(apiException);

            // When
            VerificationResult result = service.verifyCode(phoneNumber, code);

            // Then
            assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.INVALID_CODE);
        }
    }

    @Test
    void verifyCode_GenericException_ReturnsInvalidCode() {
        // Given
        String phoneNumber = "+19995550001";
        String code = "123456";
        RuntimeException genericException = new RuntimeException("Unexpected error");

        try (MockedStatic<VerificationCheck> mockedCheckStatic = mockStatic(VerificationCheck.class)) {
            mockedCheckStatic.when(() -> VerificationCheck.creator(TEST_VERIFY_SERVICE_SID))
                    .thenReturn(mockCheckCreator);
            when(mockCheckCreator.setTo(phoneNumber)).thenReturn(mockCheckCreator);
            when(mockCheckCreator.setCode(code)).thenReturn(mockCheckCreator);
            when(mockCheckCreator.create()).thenThrow(genericException);

            // When
            VerificationResult result = service.verifyCode(phoneNumber, code);

            // Then
            assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.INVALID_CODE);
        }
    }

    @Test
    void verifyCode_PassesPhoneNumberCorrectly() {
        // Given
        String phoneNumber = "+19995550001";
        String code = "123456";

        try (MockedStatic<VerificationCheck> mockedCheckStatic = mockStatic(VerificationCheck.class)) {
            mockedCheckStatic.when(() -> VerificationCheck.creator(TEST_VERIFY_SERVICE_SID))
                    .thenReturn(mockCheckCreator);
            when(mockCheckCreator.setTo(phoneNumber)).thenReturn(mockCheckCreator);
            when(mockCheckCreator.setCode(code)).thenReturn(mockCheckCreator);
            when(mockCheckCreator.create()).thenReturn(mockCheck);
            when(mockCheck.getStatus()).thenReturn("approved");

            // When
            service.verifyCode(phoneNumber, code);

            // Then
            verify(mockCheckCreator).setTo(eq(phoneNumber));
        }
    }

    @Test
    void verifyCode_PassesCodeCorrectly() {
        // Given
        String phoneNumber = "+19995550001";
        String[] codes = {"123456", "000000", "999999"};

        for (String code : codes) {
            try (MockedStatic<VerificationCheck> mockedCheckStatic = mockStatic(VerificationCheck.class)) {
                mockedCheckStatic.when(() -> VerificationCheck.creator(TEST_VERIFY_SERVICE_SID))
                        .thenReturn(mockCheckCreator);
                when(mockCheckCreator.setTo(phoneNumber)).thenReturn(mockCheckCreator);
                when(mockCheckCreator.setCode(code)).thenReturn(mockCheckCreator);
                when(mockCheckCreator.create()).thenReturn(mockCheck);
                when(mockCheck.getStatus()).thenReturn("approved");

                // When
                service.verifyCode(phoneNumber, code);

                // Then
                verify(mockCheckCreator).setCode(eq(code));
            }
        }
    }
}
