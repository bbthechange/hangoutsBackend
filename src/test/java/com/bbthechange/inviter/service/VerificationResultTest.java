package com.bbthechange.inviter.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationResultTest {

    @Test
    void success_CreatesSuccessResult_WithCorrectStatusAndMessage() {
        // When
        VerificationResult result = VerificationResult.success();
        
        // Then
        assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.SUCCESS);
        assertThat(result.getMessage()).isEqualTo("Verification successful");
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void codeExpired_CreatesExpiredResult_WithCorrectStatusAndMessage() {
        // When
        VerificationResult result = VerificationResult.codeExpired();
        
        // Then
        assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.CODE_EXPIRED);
        assertThat(result.getMessage()).isEqualTo("The verification code has expired. Please request a new one.");
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void invalidCode_CreatesInvalidResult_WithCorrectStatusAndMessage() {
        // When
        VerificationResult result = VerificationResult.invalidCode();
        
        // Then
        assertThat(result.getStatus()).isEqualTo(VerificationResult.Status.INVALID_CODE);
        assertThat(result.getMessage()).isEqualTo("The verification code is incorrect.");
        assertThat(result.isSuccess()).isFalse();
    }
}