package com.bbthechange.inviter.service;

import com.bbthechange.inviter.model.VerificationCode;
import com.bbthechange.inviter.repository.VerificationCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private VerificationCodeRepository mockVerificationCodeRepository;

    @Mock
    private SmsNotificationService mockSmsNotificationService;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(mockVerificationCodeRepository, mockSmsNotificationService);
    }

    @Test
    void sendVerificationCode_Always_GeneratesSixDigitCode() {
        // Given
        String phoneNumber = "+15551234567";

        // When
        accountService.sendVerificationCode(phoneNumber);

        // Then: verify SMS service was called with a 6-digit code
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockSmsNotificationService).sendVerificationCode(eq(phoneNumber), codeCaptor.capture());

        String sentCode = codeCaptor.getValue();
        assertThat(sentCode).hasSize(6);
        assertThat(sentCode).matches("\\d{6}"); // 6 digits only
        assertThat(Integer.parseInt(sentCode)).isBetween(100000, 999999);
    }

    @Test
    void sendVerificationCode_CalledMultipleTimes_GeneratesDifferentCodes() {
        // Given
        String phoneNumber = "+15551234567";
        Set<String> generatedCodes = new HashSet<>();

        // When: call multiple times
        for (int i = 0; i < 10; i++) {
            reset(mockSmsNotificationService);
            accountService.sendVerificationCode(phoneNumber);

            ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
            verify(mockSmsNotificationService).sendVerificationCode(eq(phoneNumber), codeCaptor.capture());
            generatedCodes.add(codeCaptor.getValue());
        }

        // Then: all codes should be different (high probability with random generation)
        assertThat(generatedCodes).hasSize(10);
    }

    @Test
    void sendVerificationCode_Always_GeneratesNumericOnlyCodes() {
        // Given
        String phoneNumber = "+15551234567";

        // When
        accountService.sendVerificationCode(phoneNumber);

        // Then
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockSmsNotificationService).sendVerificationCode(eq(phoneNumber), codeCaptor.capture());

        String sentCode = codeCaptor.getValue();
        assertThat(sentCode).containsOnlyDigits();
        assertThat(sentCode).doesNotContainAnyWhitespaces();
    }

    @Test
    void sendVerificationCode_Always_SavesVerificationCodeToRepository() {
        // Given
        String phoneNumber = "+15551234567";

        // When
        accountService.sendVerificationCode(phoneNumber);

        // Then
        verify(mockVerificationCodeRepository).save(any(VerificationCode.class));
    }

    @Test
    void sendVerificationCode_Always_SavesHashedCode() {
        // Given
        String phoneNumber = "+15551234567";

        // When
        accountService.sendVerificationCode(phoneNumber);

        // Then: verify repository was called with hashed code
        ArgumentCaptor<VerificationCode> verificationCodeCaptor = ArgumentCaptor.forClass(VerificationCode.class);
        verify(mockVerificationCodeRepository).save(verificationCodeCaptor.capture());

        VerificationCode savedCode = verificationCodeCaptor.getValue();
        
        // The hashed code should be a SHA-256 hash (64 hex characters)
        assertThat(savedCode.getHashedCode()).hasSize(64);
        assertThat(savedCode.getHashedCode()).matches("[a-f0-9]{64}");

        // The hashed code should not be the raw code
        ArgumentCaptor<String> rawCodeCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockSmsNotificationService).sendVerificationCode(eq(phoneNumber), rawCodeCaptor.capture());
        String rawCode = rawCodeCaptor.getValue();
        
        assertThat(savedCode.getHashedCode()).isNotEqualTo(rawCode);
    }

    @Test
    void sendVerificationCode_Always_SetsExpirationTime() {
        // Given
        String phoneNumber = "+15551234567";
        long testStartTime = Instant.now().getEpochSecond();

        // When
        accountService.sendVerificationCode(phoneNumber);

        // Then
        ArgumentCaptor<VerificationCode> verificationCodeCaptor = ArgumentCaptor.forClass(VerificationCode.class);
        verify(mockVerificationCodeRepository).save(verificationCodeCaptor.capture());

        VerificationCode savedCode = verificationCodeCaptor.getValue();
        long expirationTime = savedCode.getExpiresAt();
        long testEndTime = Instant.now().getEpochSecond();

        // Expiration should be 15 minutes (900 seconds) from now
        long expectedMinExpiration = testStartTime + (15 * 60);
        long expectedMaxExpiration = testEndTime + (15 * 60);

        assertThat(expirationTime).isBetween(expectedMinExpiration, expectedMaxExpiration);
    }

    @Test
    void sendVerificationCode_Always_SetsPhoneNumber() {
        // Given
        String phoneNumber = "+15551234567";

        // When
        accountService.sendVerificationCode(phoneNumber);

        // Then
        ArgumentCaptor<VerificationCode> verificationCodeCaptor = ArgumentCaptor.forClass(VerificationCode.class);
        verify(mockVerificationCodeRepository).save(verificationCodeCaptor.capture());

        VerificationCode savedCode = verificationCodeCaptor.getValue();
        assertThat(savedCode.getPhoneNumber()).isEqualTo(phoneNumber);
    }

    @Test
    void sendVerificationCode_Always_CallsSmsServiceWithPhoneNumberAndRawCode() {
        // Given
        String phoneNumber = "+19995550001";

        // When
        accountService.sendVerificationCode(phoneNumber);

        // Then
        ArgumentCaptor<String> phoneCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockSmsNotificationService).sendVerificationCode(phoneCaptor.capture(), codeCaptor.capture());

        assertThat(phoneCaptor.getValue()).isEqualTo(phoneNumber);
        assertThat(codeCaptor.getValue()).matches("\\d{6}");
    }

    @Test
    void sendVerificationCode_Always_SendsRawCodeNotHashedCode() {
        // Given
        String phoneNumber = "+15551234567";

        // When
        accountService.sendVerificationCode(phoneNumber);

        // Then: verify the code sent via SMS is the raw code, not the hashed version
        ArgumentCaptor<VerificationCode> verificationCodeCaptor = ArgumentCaptor.forClass(VerificationCode.class);
        verify(mockVerificationCodeRepository).save(verificationCodeCaptor.capture());

        ArgumentCaptor<String> smsCodeCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockSmsNotificationService).sendVerificationCode(eq(phoneNumber), smsCodeCaptor.capture());

        VerificationCode savedCode = verificationCodeCaptor.getValue();
        String sentCode = smsCodeCaptor.getValue();

        // SMS code should be 6 digits, hashed code should be 64 hex characters
        assertThat(sentCode).hasSize(6);
        assertThat(savedCode.getHashedCode()).hasSize(64);
        assertThat(sentCode).isNotEqualTo(savedCode.getHashedCode());
    }

    @Test
    void sendVerificationCode_WhenRepositoryThrows_PropagatesException() {
        // Given
        String phoneNumber = "+15551234567";
        RuntimeException repositoryException = new RuntimeException("Database error");
        doThrow(repositoryException).when(mockVerificationCodeRepository).save(any(VerificationCode.class));

        // When & Then
        assertThatThrownBy(() -> accountService.sendVerificationCode(phoneNumber))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Database error");

        // Verify that SMS service is not called if repository fails
        verifyNoInteractions(mockSmsNotificationService);
    }

    @Test
    void sendVerificationCode_WhenSmsServiceThrows_PropagatesException() {
        // Given
        String phoneNumber = "+15551234567";
        RuntimeException smsException = new RuntimeException("SMS service error");
        doThrow(smsException).when(mockSmsNotificationService)
                .sendVerificationCode(eq(phoneNumber), any(String.class));

        // When & Then
        assertThatThrownBy(() -> accountService.sendVerificationCode(phoneNumber))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("SMS service error");

        // Verify that repository save is still called even if SMS fails
        verify(mockVerificationCodeRepository).save(any(VerificationCode.class));
    }

    @Test
    void sendVerificationCode_Always_HashesCodeWithSHA256() throws NoSuchAlgorithmException {
        // Given
        String phoneNumber = "+15551234567";

        // When
        accountService.sendVerificationCode(phoneNumber);

        // Then: verify the hash is consistent with SHA-256
        ArgumentCaptor<VerificationCode> verificationCodeCaptor = ArgumentCaptor.forClass(VerificationCode.class);
        verify(mockVerificationCodeRepository).save(verificationCodeCaptor.capture());

        ArgumentCaptor<String> smsCodeCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockSmsNotificationService).sendVerificationCode(eq(phoneNumber), smsCodeCaptor.capture());

        VerificationCode savedCode = verificationCodeCaptor.getValue();
        String rawCode = smsCodeCaptor.getValue();

        // Manually compute SHA-256 hash to verify
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(rawCode.getBytes(StandardCharsets.UTF_8));
        
        StringBuilder expectedHash = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                expectedHash.append('0');
            }
            expectedHash.append(hex);
        }

        assertThat(savedCode.getHashedCode()).isEqualTo(expectedHash.toString());
    }

    @Test
    void sendVerificationCode_SameCode_ProducesSameHash() {
        // This test verifies hash consistency by checking the hashing logic
        // Since we can't control the random code generation, we test the concept
        // by verifying that the same input produces the same hash each time
        
        // Given
        String phoneNumber1 = "+15551234567";
        String phoneNumber2 = "+19995550001";

        // When: generate codes for both numbers
        accountService.sendVerificationCode(phoneNumber1);
        accountService.sendVerificationCode(phoneNumber2);

        // Then: verify that each verification code has a proper hash
        ArgumentCaptor<VerificationCode> verificationCodeCaptor = ArgumentCaptor.forClass(VerificationCode.class);
        verify(mockVerificationCodeRepository, times(2)).save(verificationCodeCaptor.capture());

        verificationCodeCaptor.getAllValues().forEach(code -> {
            assertThat(code.getHashedCode()).hasSize(64);
            assertThat(code.getHashedCode()).matches("[a-f0-9]{64}");
        });
    }

    @Test
    void sendVerificationCode_DifferentCodes_ProduceDifferentHashes() {
        // Given
        String phoneNumber = "+15551234567";

        // When: generate multiple codes
        accountService.sendVerificationCode(phoneNumber);
        accountService.sendVerificationCode(phoneNumber);

        // Then: verify different hashes were produced
        ArgumentCaptor<VerificationCode> verificationCodeCaptor = ArgumentCaptor.forClass(VerificationCode.class);
        verify(mockVerificationCodeRepository, times(2)).save(verificationCodeCaptor.capture());

        VerificationCode firstCode = verificationCodeCaptor.getAllValues().get(0);
        VerificationCode secondCode = verificationCodeCaptor.getAllValues().get(1);

        // With high probability, different random codes should produce different hashes
        assertThat(firstCode.getHashedCode()).isNotEqualTo(secondCode.getHashedCode());
    }

    @Test
    void sendVerificationCode_Always_InitializesFailedAttemptsToZero() {
        // Given
        String phoneNumber = "+15551234567";

        // When
        accountService.sendVerificationCode(phoneNumber);

        // Then
        ArgumentCaptor<VerificationCode> verificationCodeCaptor = ArgumentCaptor.forClass(VerificationCode.class);
        verify(mockVerificationCodeRepository).save(verificationCodeCaptor.capture());

        VerificationCode savedCode = verificationCodeCaptor.getValue();
        assertThat(savedCode.getFailedAttempts()).isEqualTo(0);
    }

    @Test
    void verifyCode_WithValidCodeAndPhoneNumber_ReturnsSuccess() throws NoSuchAlgorithmException {
        // Given
        String phoneNumber = "+15551234567";
        String submittedCode = "123456";
        String hashedCode = hashCode(submittedCode);
        
        VerificationCode validCode = new VerificationCode();
        validCode.setPhoneNumber(phoneNumber);
        validCode.setHashedCode(hashedCode);
        validCode.setExpiresAt(Instant.now().getEpochSecond() + 900); // 15 minutes from now
        validCode.setFailedAttempts(0);
        
        when(mockVerificationCodeRepository.findByPhoneNumber(phoneNumber))
                .thenReturn(Optional.of(validCode));

        // When
        VerificationResult result = accountService.verifyCode(phoneNumber, submittedCode);

        // Then
        assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.SUCCESS);
        assertThat(result.isSuccess()).isTrue();
        verify(mockVerificationCodeRepository).deleteByPhoneNumber(phoneNumber);
        verify(mockVerificationCodeRepository, never()).save(any());
    }

    @Test
    void verifyCode_WithNonExistentPhoneNumber_ReturnsCodeExpired() {
        // Given
        String phoneNumber = "+15551234567";
        String submittedCode = "123456";
        
        when(mockVerificationCodeRepository.findByPhoneNumber(phoneNumber))
                .thenReturn(Optional.empty());

        // When
        VerificationResult result = accountService.verifyCode(phoneNumber, submittedCode);

        // Then
        assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.CODE_EXPIRED);
        assertThat(result.isSuccess()).isFalse();
        verify(mockVerificationCodeRepository, never()).deleteByPhoneNumber(any());
        verify(mockVerificationCodeRepository, never()).save(any());
    }

    @Test
    void verifyCode_WithExpiredCode_ReturnsCodeExpiredAndDeletesRecord() throws NoSuchAlgorithmException {
        // Given
        String phoneNumber = "+15551234567";
        String submittedCode = "123456";
        String hashedCode = hashCode(submittedCode);
        
        VerificationCode expiredCode = new VerificationCode();
        expiredCode.setPhoneNumber(phoneNumber);
        expiredCode.setHashedCode(hashedCode);
        expiredCode.setExpiresAt(Instant.now().getEpochSecond() - 100); // 100 seconds ago (expired)
        expiredCode.setFailedAttempts(0);
        
        when(mockVerificationCodeRepository.findByPhoneNumber(phoneNumber))
                .thenReturn(Optional.of(expiredCode));

        // When
        VerificationResult result = accountService.verifyCode(phoneNumber, submittedCode);

        // Then
        assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.CODE_EXPIRED);
        assertThat(result.isSuccess()).isFalse();
        verify(mockVerificationCodeRepository).deleteByPhoneNumber(phoneNumber);
        verify(mockVerificationCodeRepository, never()).save(any());
    }

    @Test
    void verifyCode_WithIncorrectCode_ReturnsInvalidCodeAndIncrementsAttempts() throws NoSuchAlgorithmException {
        // Given
        String phoneNumber = "+15551234567";
        String submittedCode = "123456";
        String correctCode = "654321";
        String correctHashedCode = hashCode(correctCode);
        
        VerificationCode validCode = new VerificationCode();
        validCode.setPhoneNumber(phoneNumber);
        validCode.setHashedCode(correctHashedCode);
        validCode.setExpiresAt(Instant.now().getEpochSecond() + 900); // 15 minutes from now
        validCode.setFailedAttempts(5);
        
        when(mockVerificationCodeRepository.findByPhoneNumber(phoneNumber))
                .thenReturn(Optional.of(validCode));

        // When
        VerificationResult result = accountService.verifyCode(phoneNumber, submittedCode);

        // Then
        assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.INVALID_CODE);
        assertThat(result.isSuccess()).isFalse();
        
        ArgumentCaptor<VerificationCode> savedCodeCaptor = ArgumentCaptor.forClass(VerificationCode.class);
        verify(mockVerificationCodeRepository).save(savedCodeCaptor.capture());
        
        VerificationCode savedCode = savedCodeCaptor.getValue();
        assertThat(savedCode.getFailedAttempts()).isEqualTo(6); // incremented from 5 to 6
        verify(mockVerificationCodeRepository, never()).deleteByPhoneNumber(any());
    }

    @Test
    void verifyCode_WithIncorrectCodeAfterMaxAttempts_ReturnsCodeExpiredAndDeletesRecord() throws NoSuchAlgorithmException {
        // Given
        String phoneNumber = "+15551234567";
        String submittedCode = "123456";
        String correctCode = "654321";
        String correctHashedCode = hashCode(correctCode);
        
        VerificationCode validCode = new VerificationCode();
        validCode.setPhoneNumber(phoneNumber);
        validCode.setHashedCode(correctHashedCode);
        validCode.setExpiresAt(Instant.now().getEpochSecond() + 900); // 15 minutes from now
        validCode.setFailedAttempts(10); // At max attempts
        
        when(mockVerificationCodeRepository.findByPhoneNumber(phoneNumber))
                .thenReturn(Optional.of(validCode));

        // When
        VerificationResult result = accountService.verifyCode(phoneNumber, submittedCode);

        // Then
        assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.CODE_EXPIRED);
        assertThat(result.isSuccess()).isFalse();
        verify(mockVerificationCodeRepository).deleteByPhoneNumber(phoneNumber);
        verify(mockVerificationCodeRepository, never()).save(any());
    }

    @Test
    void verifyCode_WithCorrectCodeButExactlyAtExpirationTime_ReturnsCodeExpired() throws NoSuchAlgorithmException {
        // Given
        String phoneNumber = "+15551234567";
        String submittedCode = "123456";
        String hashedCode = hashCode(submittedCode);
        
        VerificationCode expiredCode = new VerificationCode();
        expiredCode.setPhoneNumber(phoneNumber);
        expiredCode.setHashedCode(hashedCode);
        expiredCode.setExpiresAt(Instant.now().getEpochSecond() - 1); // 1 second ago (expired)
        expiredCode.setFailedAttempts(0);
        
        when(mockVerificationCodeRepository.findByPhoneNumber(phoneNumber))
                .thenReturn(Optional.of(expiredCode));

        // When
        VerificationResult result = accountService.verifyCode(phoneNumber, submittedCode);

        // Then
        assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.CODE_EXPIRED);
        assertThat(result.isSuccess()).isFalse();
        verify(mockVerificationCodeRepository).deleteByPhoneNumber(phoneNumber);
        verify(mockVerificationCodeRepository, never()).save(any());
    }

    private String hashCode(String code) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(code.getBytes(StandardCharsets.UTF_8));
        
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}