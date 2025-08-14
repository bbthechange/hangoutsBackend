package com.bbthechange.inviter.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    void generateSecurityHash_ShouldReturnBCryptHash() {
        // Given
        String rawToken = "test-token-123";
        
        // When
        String hash = hashingService.generateSecurityHash(rawToken);
        
        // Then
        assertThat(hash).isNotNull();
        assertThat(hash).startsWith("$2a$12$"); // BCrypt format with strength 12
    }
    
    @Test
    void generateSecurityHash_ShouldProduceDifferentHashesEachTime() {
        // Given
        String rawToken = "test-token-123";
        
        // When
        String hash1 = hashingService.generateSecurityHash(rawToken);
        String hash2 = hashingService.generateSecurityHash(rawToken);
        
        // Then
        assertThat(hash1).isNotEqualTo(hash2); // BCrypt includes salt
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
}