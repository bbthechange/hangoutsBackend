package com.bbthechange.inviter.service;

import com.bbthechange.inviter.model.VerificationCode;
import com.bbthechange.inviter.repository.VerificationCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AwsSmsValidationService}.
 * <p>
 * Tests verify:
 * - Code generation (6-digit, random, unique)
 * - Secure hashing (SHA-256)
 * - Repository storage (expiration, phone number, failed attempts)
 * - SMS sending delegation
 * - Verification logic (expiration, rate limiting, hash comparison)
 */
@ExtendWith(MockitoExtension.class)
class AwsSmsValidationServiceTest {

    @Mock
    private VerificationCodeRepository verificationCodeRepository;

    @Mock
    private SmsNotificationService smsNotificationService;

    private AwsSmsValidationService service;

    @BeforeEach
    void setUp() {
        service = new AwsSmsValidationService(verificationCodeRepository, smsNotificationService);
    }

    // ========================================================================
    // Section 1: sendVerificationCode() Tests
    // ========================================================================

    @Test
    void sendVerificationCode_generatesExactly6DigitCode() {
        // Given
        String phoneNumber = "+19995550001";
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);

        // When
        service.sendVerificationCode(phoneNumber);

        // Then
        verify(smsNotificationService).sendVerificationCode(eq(phoneNumber), codeCaptor.capture());
        String capturedCode = codeCaptor.getValue();

        assertThat(capturedCode).hasSize(6);
        assertThat(capturedCode).matches("\\d{6}");

        int codeValue = Integer.parseInt(capturedCode);
        assertThat(codeValue).isBetween(100000, 999999);
    }

    @Test
    void sendVerificationCode_generatesDifferentCodesOnMultipleCalls() {
        // Given
        String phoneNumber = "+19995550001";
        Set<String> generatedCodes = new HashSet<>();
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);

        // When - Call 10 times
        for (int i = 0; i < 10; i++) {
            service.sendVerificationCode(phoneNumber);
        }

        // Then
        verify(smsNotificationService, times(10)).sendVerificationCode(eq(phoneNumber), codeCaptor.capture());
        generatedCodes.addAll(codeCaptor.getAllValues());

        // All 10 codes should be unique (extremely high probability)
        assertThat(generatedCodes).hasSize(10);
    }

    @Test
    void sendVerificationCode_savesVerificationCodeToRepository() {
        // Given
        String phoneNumber = "+19995550001";

        // When
        service.sendVerificationCode(phoneNumber);

        // Then
        verify(verificationCodeRepository).save(any(VerificationCode.class));
    }

    @Test
    void sendVerificationCode_storesSHA256HashedCodeNotPlaintext() {
        // Given
        String phoneNumber = "+19995550001";
        ArgumentCaptor<VerificationCode> vcCaptor = ArgumentCaptor.forClass(VerificationCode.class);
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);

        // When
        service.sendVerificationCode(phoneNumber);

        // Then
        verify(verificationCodeRepository).save(vcCaptor.capture());
        verify(smsNotificationService).sendVerificationCode(eq(phoneNumber), codeCaptor.capture());

        VerificationCode savedVc = vcCaptor.getValue();
        String rawCode = codeCaptor.getValue();

        // Hashed code should be 64 characters (SHA-256 hex)
        assertThat(savedVc.getHashedCode()).hasSize(64);
        assertThat(savedVc.getHashedCode()).matches("[a-f0-9]{64}");

        // Hashed code should NOT be the same as raw code
        assertThat(savedVc.getHashedCode()).isNotEqualTo(rawCode);
    }

    @Test
    void sendVerificationCode_setsExpirationTo15MinutesFromNow() {
        // Given
        String phoneNumber = "+19995550001";
        long testStartTime = Instant.now().getEpochSecond();
        ArgumentCaptor<VerificationCode> vcCaptor = ArgumentCaptor.forClass(VerificationCode.class);

        // When
        service.sendVerificationCode(phoneNumber);
        long testEndTime = Instant.now().getEpochSecond();

        // Then
        verify(verificationCodeRepository).save(vcCaptor.capture());
        VerificationCode savedVc = vcCaptor.getValue();

        long expectedMinExpiry = testStartTime + (15 * 60);
        long expectedMaxExpiry = testEndTime + (15 * 60);

        assertThat(savedVc.getExpiresAt())
                .isGreaterThanOrEqualTo(expectedMinExpiry)
                .isLessThanOrEqualTo(expectedMaxExpiry);
    }

    @Test
    void sendVerificationCode_setsPhoneNumberCorrectly() {
        // Given
        String phoneNumber = "+19995550001";
        ArgumentCaptor<VerificationCode> vcCaptor = ArgumentCaptor.forClass(VerificationCode.class);

        // When
        service.sendVerificationCode(phoneNumber);

        // Then
        verify(verificationCodeRepository).save(vcCaptor.capture());
        VerificationCode savedVc = vcCaptor.getValue();

        assertThat(savedVc.getPhoneNumber()).isEqualTo(phoneNumber);
    }

    @Test
    void sendVerificationCode_sendsRawCodeViaSmsNotHashedVersion() {
        // Given
        String phoneNumber = "+19995550001";
        ArgumentCaptor<VerificationCode> vcCaptor = ArgumentCaptor.forClass(VerificationCode.class);
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);

        // When
        service.sendVerificationCode(phoneNumber);

        // Then
        verify(verificationCodeRepository).save(vcCaptor.capture());
        verify(smsNotificationService).sendVerificationCode(eq(phoneNumber), codeCaptor.capture());

        VerificationCode savedVc = vcCaptor.getValue();
        String rawCode = codeCaptor.getValue();

        // SMS code should be 6 digits
        assertThat(rawCode).hasSize(6);
        assertThat(rawCode).matches("\\d{6}");

        // Saved hash should be 64 chars
        assertThat(savedVc.getHashedCode()).hasSize(64);

        // They should be different
        assertThat(savedVc.getHashedCode()).isNotEqualTo(rawCode);
    }

    @Test
    void sendVerificationCode_initializesFailedAttemptsToZero() {
        // Given
        String phoneNumber = "+19995550001";
        ArgumentCaptor<VerificationCode> vcCaptor = ArgumentCaptor.forClass(VerificationCode.class);

        // When
        service.sendVerificationCode(phoneNumber);

        // Then
        verify(verificationCodeRepository).save(vcCaptor.capture());
        VerificationCode savedVc = vcCaptor.getValue();

        assertThat(savedVc.getFailedAttempts()).isEqualTo(0);
    }

    @Test
    void sendVerificationCode_repositoryFailurePreventsSmsSending() {
        // Given
        String phoneNumber = "+19995550001";
        RuntimeException repositoryException = new RuntimeException("Database error");
        doThrow(repositoryException).when(verificationCodeRepository).save(any(VerificationCode.class));

        // When & Then
        assertThatThrownBy(() -> service.sendVerificationCode(phoneNumber))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Database error");

        // SMS service should NOT be called
        verify(smsNotificationService, never()).sendVerificationCode(anyString(), anyString());
    }

    @Test
    void sendVerificationCode_hashAlgorithmProducesConsistentResults() throws NoSuchAlgorithmException {
        // Given
        String phoneNumber = "+19995550001";
        String testCode = "123456";
        String expectedHash = hashCode(testCode);

        ArgumentCaptor<VerificationCode> vcCaptor = ArgumentCaptor.forClass(VerificationCode.class);
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);

        // We need to call multiple times and check if the same code produces the same hash
        // Since we can't control the random code generation, we'll verify by re-hashing captured codes

        // When
        service.sendVerificationCode(phoneNumber);

        // Then
        verify(verificationCodeRepository).save(vcCaptor.capture());
        verify(smsNotificationService).sendVerificationCode(eq(phoneNumber), codeCaptor.capture());

        VerificationCode savedVc = vcCaptor.getValue();
        String rawCode = codeCaptor.getValue();

        // Re-hash the captured raw code and verify it matches the saved hash
        String rehashed = hashCode(rawCode);
        assertThat(savedVc.getHashedCode()).isEqualTo(rehashed);
    }

    // ========================================================================
    // Section 2: verifyCode() Tests
    // ========================================================================

    @Test
    void verifyCode_validCode_returnsSuccess() throws NoSuchAlgorithmException {
        // Given
        String phoneNumber = "+19995550001";
        String submittedCode = "123456";
        String hashedCode = hashCode(submittedCode);
        long expiresAt = Instant.now().plusSeconds(300).getEpochSecond(); // 5 minutes from now

        VerificationCode verificationCode = new VerificationCode(phoneNumber, hashedCode, expiresAt);
        verificationCode.setFailedAttempts(0);

        when(verificationCodeRepository.findByPhoneNumber(phoneNumber))
                .thenReturn(Optional.of(verificationCode));

        // When
        VerificationResult result = service.verifyCode(phoneNumber, submittedCode);

        // Then
        assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.SUCCESS);
        assertThat(result.isSuccess()).isTrue();
        verify(verificationCodeRepository).deleteByPhoneNumber(phoneNumber);
        verify(verificationCodeRepository, never()).save(any(VerificationCode.class));
    }

    @Test
    void verifyCode_nonExistentPhoneNumber_returnsCodeExpired() {
        // Given
        String phoneNumber = "+19995550001";
        String submittedCode = "123456";

        when(verificationCodeRepository.findByPhoneNumber(phoneNumber))
                .thenReturn(Optional.empty());

        // When
        VerificationResult result = service.verifyCode(phoneNumber, submittedCode);

        // Then
        assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.CODE_EXPIRED);
        verify(verificationCodeRepository, never()).deleteByPhoneNumber(anyString());
        verify(verificationCodeRepository, never()).save(any(VerificationCode.class));
    }

    @Test
    void verifyCode_expiredCode_returnsCodeExpiredAndDeletesRecord() throws NoSuchAlgorithmException {
        // Given
        String phoneNumber = "+19995550001";
        String submittedCode = "123456";
        String hashedCode = hashCode(submittedCode);
        long expiresAt = Instant.now().minusSeconds(60).getEpochSecond(); // 1 minute ago

        VerificationCode verificationCode = new VerificationCode(phoneNumber, hashedCode, expiresAt);
        verificationCode.setFailedAttempts(0);

        when(verificationCodeRepository.findByPhoneNumber(phoneNumber))
                .thenReturn(Optional.of(verificationCode));

        // When
        VerificationResult result = service.verifyCode(phoneNumber, submittedCode);

        // Then
        assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.CODE_EXPIRED);
        verify(verificationCodeRepository).deleteByPhoneNumber(phoneNumber);
        verify(verificationCodeRepository, never()).save(any(VerificationCode.class));
    }

    @Test
    void verifyCode_incorrectCode_returnsInvalidCodeAndIncrementsAttempts() throws NoSuchAlgorithmException {
        // Given
        String phoneNumber = "+19995550001";
        String correctCode = "123456";
        String incorrectCode = "654321";
        String hashedCorrectCode = hashCode(correctCode);
        long expiresAt = Instant.now().plusSeconds(300).getEpochSecond();

        VerificationCode verificationCode = new VerificationCode(phoneNumber, hashedCorrectCode, expiresAt);
        verificationCode.setFailedAttempts(5);

        when(verificationCodeRepository.findByPhoneNumber(phoneNumber))
                .thenReturn(Optional.of(verificationCode));

        ArgumentCaptor<VerificationCode> vcCaptor = ArgumentCaptor.forClass(VerificationCode.class);

        // When
        VerificationResult result = service.verifyCode(phoneNumber, incorrectCode);

        // Then
        assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.INVALID_CODE);
        verify(verificationCodeRepository).save(vcCaptor.capture());
        verify(verificationCodeRepository, never()).deleteByPhoneNumber(anyString());

        VerificationCode savedVc = vcCaptor.getValue();
        assertThat(savedVc.getFailedAttempts()).isEqualTo(6);
    }

    @Test
    void verifyCode_exceedingMaxAttempts_deletesRecordAndReturnsCodeExpired() throws NoSuchAlgorithmException {
        // Given
        String phoneNumber = "+19995550001";
        String correctCode = "123456";
        String incorrectCode = "654321";
        String hashedCorrectCode = hashCode(correctCode);
        long expiresAt = Instant.now().plusSeconds(300).getEpochSecond();

        VerificationCode verificationCode = new VerificationCode(phoneNumber, hashedCorrectCode, expiresAt);
        verificationCode.setFailedAttempts(10); // At max limit

        when(verificationCodeRepository.findByPhoneNumber(phoneNumber))
                .thenReturn(Optional.of(verificationCode));

        // When
        VerificationResult result = service.verifyCode(phoneNumber, incorrectCode);

        // Then
        assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.CODE_EXPIRED);
        verify(verificationCodeRepository).deleteByPhoneNumber(phoneNumber);
        verify(verificationCodeRepository, never()).save(any(VerificationCode.class));
    }

    @Test
    void verifyCode_exactlyAtExpirationTime_isConsideredExpired() throws NoSuchAlgorithmException {
        // Given
        String phoneNumber = "+19995550001";
        String submittedCode = "123456";
        String hashedCode = hashCode(submittedCode);
        long expiresAt = Instant.now().minusSeconds(1).getEpochSecond(); // 1 second ago

        VerificationCode verificationCode = new VerificationCode(phoneNumber, hashedCode, expiresAt);
        verificationCode.setFailedAttempts(0);

        when(verificationCodeRepository.findByPhoneNumber(phoneNumber))
                .thenReturn(Optional.of(verificationCode));

        // When
        VerificationResult result = service.verifyCode(phoneNumber, submittedCode);

        // Then
        assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.CODE_EXPIRED);
        verify(verificationCodeRepository).deleteByPhoneNumber(phoneNumber);
    }

    @Test
    void verifyCode_bypassFlagAllowsAnyCode() throws NoSuchAlgorithmException {
        // Given
        String phoneNumber = "+19995550001";
        String correctCode = "123456";
        String wrongCode = "999999";
        String hashedCorrectCode = hashCode(correctCode);
        long expiresAt = Instant.now().plusSeconds(300).getEpochSecond();

        VerificationCode verificationCode = new VerificationCode(phoneNumber, hashedCorrectCode, expiresAt);
        verificationCode.setFailedAttempts(0);

        when(verificationCodeRepository.findByPhoneNumber(phoneNumber))
                .thenReturn(Optional.of(verificationCode));

        // Set bypass flag to true
        ReflectionTestUtils.setField(service, "bypassPhoneVerification", true);

        // When
        VerificationResult result = service.verifyCode(phoneNumber, wrongCode);

        // Then
        assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.SUCCESS);
        verify(verificationCodeRepository).deleteByPhoneNumber(phoneNumber);
    }

    @Test
    void verifyCode_successfulVerificationDeletesTheCodeRecord() throws NoSuchAlgorithmException {
        // Given
        String phoneNumber = "+19995550001";
        String submittedCode = "123456";
        String hashedCode = hashCode(submittedCode);
        long expiresAt = Instant.now().plusSeconds(300).getEpochSecond();

        VerificationCode verificationCode = new VerificationCode(phoneNumber, hashedCode, expiresAt);
        verificationCode.setFailedAttempts(0);

        when(verificationCodeRepository.findByPhoneNumber(phoneNumber))
                .thenReturn(Optional.of(verificationCode));

        // When
        service.verifyCode(phoneNumber, submittedCode);

        // Then
        verify(verificationCodeRepository, times(1)).deleteByPhoneNumber(phoneNumber);
    }

    @Test
    void verifyCode_failedVerificationBeforeMaxAttemptsDoesNotDelete() throws NoSuchAlgorithmException {
        // Given
        String phoneNumber = "+19995550001";
        String correctCode = "123456";
        String wrongCode = "999999";
        String hashedCorrectCode = hashCode(correctCode);
        long expiresAt = Instant.now().plusSeconds(300).getEpochSecond();

        VerificationCode verificationCode = new VerificationCode(phoneNumber, hashedCorrectCode, expiresAt);
        verificationCode.setFailedAttempts(5); // Less than max

        when(verificationCodeRepository.findByPhoneNumber(phoneNumber))
                .thenReturn(Optional.of(verificationCode));

        // When
        service.verifyCode(phoneNumber, wrongCode);

        // Then
        verify(verificationCodeRepository).save(any(VerificationCode.class));
        verify(verificationCodeRepository, never()).deleteByPhoneNumber(anyString());
    }

    @Test
    void verifyCode_multipleFailedAttemptsIncrementCorrectly() throws NoSuchAlgorithmException {
        // Test scenarios: 0 -> 1, 5 -> 6, 9 -> 10
        testFailedAttemptIncrement(0, 1);
        testFailedAttemptIncrement(5, 6);
        testFailedAttemptIncrement(9, 10);
    }

    @Test
    void verifyCode_correctCodeAfterPreviousFailuresStillSucceeds() throws NoSuchAlgorithmException {
        // Given
        String phoneNumber = "+19995550001";
        String submittedCode = "123456";
        String hashedCode = hashCode(submittedCode);
        long expiresAt = Instant.now().plusSeconds(300).getEpochSecond();

        VerificationCode verificationCode = new VerificationCode(phoneNumber, hashedCode, expiresAt);
        verificationCode.setFailedAttempts(8); // Had previous failures

        when(verificationCodeRepository.findByPhoneNumber(phoneNumber))
                .thenReturn(Optional.of(verificationCode));

        // When
        VerificationResult result = service.verifyCode(phoneNumber, submittedCode);

        // Then
        assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.SUCCESS);
        verify(verificationCodeRepository).deleteByPhoneNumber(phoneNumber);
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    /**
     * Helper method to compute SHA-256 hash for test assertions.
     */
    private String hashCode(String code) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(code.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Helper method to test failed attempt increments.
     */
    private void testFailedAttemptIncrement(int initialAttempts, int expectedAttempts) throws NoSuchAlgorithmException {
        // Given
        String phoneNumber = "+19995550001";
        String correctCode = "123456";
        String wrongCode = "999999";
        String hashedCorrectCode = hashCode(correctCode);
        long expiresAt = Instant.now().plusSeconds(300).getEpochSecond();

        VerificationCode verificationCode = new VerificationCode(phoneNumber, hashedCorrectCode, expiresAt);
        verificationCode.setFailedAttempts(initialAttempts);

        // Reset mocks for each test
        reset(verificationCodeRepository);
        when(verificationCodeRepository.findByPhoneNumber(phoneNumber))
                .thenReturn(Optional.of(verificationCode));

        ArgumentCaptor<VerificationCode> vcCaptor = ArgumentCaptor.forClass(VerificationCode.class);

        // When
        service.verifyCode(phoneNumber, wrongCode);

        // Then
        verify(verificationCodeRepository).save(vcCaptor.capture());
        VerificationCode savedVc = vcCaptor.getValue();
        assertThat(savedVc.getFailedAttempts()).isEqualTo(expectedAttempts);
    }
}
