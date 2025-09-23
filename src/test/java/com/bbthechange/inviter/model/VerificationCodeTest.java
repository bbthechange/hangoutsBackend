package com.bbthechange.inviter.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationCodeTest {

    @Test
    void isExpired_WhenCurrentTimeBeforeExpiry_ReturnsFalse() {
        // Given: verification code expires 5 minutes from now
        long futureTimestamp = Instant.now().getEpochSecond() + 300;
        VerificationCode verificationCode = new VerificationCode("+15551234567", "hashedCode", futureTimestamp);

        // When
        boolean result = verificationCode.isExpired();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void isExpired_WhenCurrentTimeAfterExpiry_ReturnsTrue() {
        // Given: verification code expired 5 minutes ago
        long pastTimestamp = Instant.now().getEpochSecond() - 300;
        VerificationCode verificationCode = new VerificationCode("+15551234567", "hashedCode", pastTimestamp);

        // When
        boolean result = verificationCode.isExpired();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void isExpired_WhenCurrentTimeEqualsExpiry_ReturnsTrue() {
        // Given: verification code expires exactly now (accounting for test execution time)
        long currentTimestamp = Instant.now().getEpochSecond() - 1; // 1 second ago to account for execution time
        VerificationCode verificationCode = new VerificationCode("+15551234567", "hashedCode", currentTimestamp);

        // When
        boolean result = verificationCode.isExpired();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void incrementFailedAttempts_WhenFailedAttemptsIsZero_IncrementsToOne() {
        // Given
        VerificationCode verificationCode = new VerificationCode("+15551234567", "hashedCode", Instant.now().getEpochSecond() + 300);
        assertThat(verificationCode.getFailedAttempts()).isEqualTo(0);

        // When
        verificationCode.incrementFailedAttempts();

        // Then
        assertThat(verificationCode.getFailedAttempts()).isEqualTo(1);
    }

    @Test
    void incrementFailedAttempts_WhenFailedAttemptsIsNull_IncrementsToOne() {
        // Given
        VerificationCode verificationCode = new VerificationCode();
        verificationCode.setFailedAttempts(null);

        // When
        verificationCode.incrementFailedAttempts();

        // Then
        assertThat(verificationCode.getFailedAttempts()).isEqualTo(1);
    }

    @Test
    void incrementFailedAttempts_WhenCalledMultipleTimes_IncrementsCorrectly() {
        // Given
        VerificationCode verificationCode = new VerificationCode("+15551234567", "hashedCode", Instant.now().getEpochSecond() + 300);

        // When
        verificationCode.incrementFailedAttempts();
        verificationCode.incrementFailedAttempts();
        verificationCode.incrementFailedAttempts();

        // Then
        assertThat(verificationCode.getFailedAttempts()).isEqualTo(3);
    }

    @Test
    void constructor_WithNoArgs_InitializesFailedAttemptsToZero() {
        // When
        VerificationCode verificationCode = new VerificationCode();

        // Then
        assertThat(verificationCode.getFailedAttempts()).isEqualTo(0);
    }

    @Test
    void constructor_WithArgs_InitializesFailedAttemptsToZero() {
        // When
        VerificationCode verificationCode = new VerificationCode("+15551234567", "hashedCode", Instant.now().getEpochSecond() + 300);

        // Then
        assertThat(verificationCode.getFailedAttempts()).isEqualTo(0);
        assertThat(verificationCode.getPhoneNumber()).isEqualTo("+15551234567");
        assertThat(verificationCode.getHashedCode()).isEqualTo("hashedCode");
    }
}