package com.bbthechange.inviter.service;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class RefreshTokenHashingServiceTest {
    
    private RefreshTokenHashingService hashingService;
    
    @BeforeEach
    void setUp() {
        hashingService = new RefreshTokenHashingService();
        ReflectionTestUtils.setField(hashingService, "salt", "test-salt-123");
    }
    
    @Test
    void generateRefreshToken_ShouldReturnBase64UrlEncodedString() {
        // When
        String token = hashingService.generateRefreshToken();
        
        // Then
        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
        assertThat(token).doesNotContain("+", "/", "="); // URL-safe Base64
        assertThat(token.length()).isGreaterThan(40); // 32 bytes -> ~43 chars in Base64
    }
    
    @Test
    void generateRefreshToken_ShouldGenerateUniqueTokens() {
        // When
        String token1 = hashingService.generateRefreshToken();
        String token2 = hashingService.generateRefreshToken();
        
        // Then
        assertThat(token1).isNotEqualTo(token2);
    }
    
    @Test
    void generateLookupHash_ShouldReturnConsistentHash() {
        // Given
        String rawToken = "test-token-123";
        
        // When
        String hash1 = hashingService.generateLookupHash(rawToken);
        String hash2 = hashingService.generateLookupHash(rawToken);
        
        // Then
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64); // SHA-256 produces 64 hex characters
    }
    
    @Test
    void generateLookupHash_ShouldProduceDifferentHashesForDifferentTokens() {
        // Given
        String token1 = "token-1";
        String token2 = "token-2";
        
        // When
        String hash1 = hashingService.generateLookupHash(token1);
        String hash2 = hashingService.generateLookupHash(token2);
        
        // Then
        assertThat(hash1).isNotEqualTo(hash2);
    }
    
    @Test
    void generateSecurityHash_ShouldReturnSHA256Hash() {
        // Given
        String rawToken = "test-token-123";

        // When
        String hash = hashingService.generateSecurityHash(rawToken);

        // Then
        assertThat(hash).isNotNull();
        assertThat(hash).hasSize(64); // SHA-256 produces 64 hex characters
        assertThat(hash).matches("[a-f0-9]{64}"); // Valid hex SHA-256
        assertThat(hash).doesNotStartWith("$2"); // NOT BCrypt format
    }

    @Test
    void generateSecurityHash_ShouldProduceConsistentHashesForSameToken() {
        // Given
        String rawToken = "test-token-123";

        // When
        String hash1 = hashingService.generateSecurityHash(rawToken);
        String hash2 = hashingService.generateSecurityHash(rawToken);

        // Then
        assertThat(hash1).isEqualTo(hash2); // SHA-256 is deterministic
    }
    
    @Test
    void matches_ShouldReturnTrueForCorrectToken() {
        // Given
        String rawToken = "test-token-123";
        String hash = hashingService.generateSecurityHash(rawToken);
        
        // When
        boolean matches = hashingService.matches(rawToken, hash);
        
        // Then
        assertThat(matches).isTrue();
    }
    
    @Test
    void matches_ShouldReturnFalseForIncorrectToken() {
        // Given
        String rawToken = "test-token-123";
        String wrongToken = "wrong-token";
        String hash = hashingService.generateSecurityHash(rawToken);

        // When
        boolean matches = hashingService.matches(wrongToken, hash);

        // Then
        assertThat(matches).isFalse();
    }

    // ========== Dual-Mode Validation Tests ==========

    @Test
    void matches_WithBCryptHash_ShouldReturnTrue() {
        // Simulate legacy BCrypt token from production
        String rawToken = "legacy-bcrypt-token-123";
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);
        String bcryptHash = bcrypt.encode(rawToken);

        // When
        boolean result = hashingService.matches(rawToken, bcryptHash);

        // Then
        assertThat(result).isTrue();
        assertThat(bcryptHash).startsWith("$2"); // Verify it's actually BCrypt
    }

    @Test
    void matches_WithBCryptHash_ShouldReturnFalseForWrongToken() {
        // Simulate legacy BCrypt token validation failure
        String rawToken = "legacy-bcrypt-token-123";
        String wrongToken = "wrong-token";
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);
        String bcryptHash = bcrypt.encode(rawToken);

        // When
        boolean result = hashingService.matches(wrongToken, bcryptHash);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void matches_WithSHA256Hash_ShouldReturnTrue() {
        // New SHA-256 token validation
        String rawToken = "new-sha256-token-456";
        String sha256Hash = hashingService.generateSecurityHash(rawToken);

        // When
        boolean result = hashingService.matches(rawToken, sha256Hash);

        // Then
        assertThat(result).isTrue();
        assertThat(sha256Hash).hasSize(64); // Verify it's SHA-256
        assertThat(sha256Hash).doesNotStartWith("$2"); // Verify it's NOT BCrypt
    }

    @Test
    void matches_WithSHA256Hash_ShouldReturnFalseForWrongToken() {
        // New SHA-256 token validation failure
        String rawToken = "new-sha256-token-456";
        String wrongToken = "wrong-token";
        String sha256Hash = DigestUtils.sha256Hex(rawToken + "test-salt-123");

        // When
        boolean result = hashingService.matches(wrongToken, sha256Hash);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void matches_WithNullHash_ShouldReturnFalse() {
        // Given
        String rawToken = "test-token";

        // When
        boolean result = hashingService.matches(rawToken, null);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void matches_WithInvalidHashFormat_ShouldHandleGracefully() {
        // Test with hash that's neither BCrypt nor valid SHA-256
        String rawToken = "test-token";
        String invalidHash = "not-a-valid-hash";

        // When/Then - should not throw exception, just return false
        boolean result = hashingService.matches(rawToken, invalidHash);
        assertThat(result).isFalse();
    }

    @Test
    void dualMode_BothHashTypesShouldWorkInSameSession() {
        // Verify both BCrypt and SHA-256 validation work in same service instance
        String bcryptToken = "bcrypt-token";
        String sha256Token = "sha256-token";

        // Create BCrypt hash (simulating old token)
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);
        String bcryptHash = bcrypt.encode(bcryptToken);

        // Create SHA-256 hash (simulating new token)
        String sha256Hash = hashingService.generateSecurityHash(sha256Token);

        // When/Then - both should validate correctly
        assertThat(hashingService.matches(bcryptToken, bcryptHash)).isTrue();
        assertThat(hashingService.matches(sha256Token, sha256Hash)).isTrue();

        // Cross-validation should fail
        assertThat(hashingService.matches(bcryptToken, sha256Hash)).isFalse();
        assertThat(hashingService.matches(sha256Token, bcryptHash)).isFalse();
    }
}