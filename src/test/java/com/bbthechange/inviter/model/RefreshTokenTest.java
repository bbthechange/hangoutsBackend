package com.bbthechange.inviter.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenTest {
    
    @Test
    void constructor_ShouldSetAllFields() {
        // Given
        String userId = "user-123";
        String tokenHash = "token-hash";
        String securityHash = "security-hash";
        String deviceId = "device-123";
        String ipAddress = "192.168.1.1";
        
        // When
        RefreshToken token = new RefreshToken(userId, tokenHash, securityHash, deviceId, ipAddress);
        
        // Then
        assertThat(token.getUserId()).isEqualTo(userId);
        assertThat(token.getTokenHash()).isEqualTo(tokenHash);
        assertThat(token.getSecurityHash()).isEqualTo(securityHash);
        assertThat(token.getDeviceId()).isEqualTo(deviceId);
        assertThat(token.getIpAddress()).isEqualTo(ipAddress);
        assertThat(token.getTokenId()).isNotNull();
        assertThat(token.getItemType()).isEqualTo("REFRESH_TOKEN");
        assertThat(token.getPk()).isEqualTo("USER#" + userId);
        assertThat(token.getSk()).startsWith("REFRESH_TOKEN#");
        assertThat(token.getExpiryDate()).isGreaterThan(Instant.now().getEpochSecond());
    }
    
    @Test
    void constructor_ShouldSetExpiryDate30DaysFromNow() {
        // Given
        String userId = "user-123";
        Instant beforeCreation = Instant.now();
        
        // When
        RefreshToken token = new RefreshToken(userId, "hash", "secHash", "device", "ip");
        
        // Then
        Instant afterCreation = Instant.now();
        Instant expectedMinExpiry = beforeCreation.plus(30, ChronoUnit.DAYS);
        Instant expectedMaxExpiry = afterCreation.plus(30, ChronoUnit.DAYS);
        
        Instant actualExpiry = Instant.ofEpochSecond(token.getExpiryDate());
        assertThat(actualExpiry).isBetween(expectedMinExpiry, expectedMaxExpiry);
    }
    
    @Test
    void isExpired_WithFutureExpiryDate_ShouldReturnFalse() {
        // Given
        RefreshToken token = new RefreshToken();
        token.setExpiryDate(Instant.now().plus(1, ChronoUnit.HOURS).getEpochSecond());
        
        // When
        boolean isExpired = token.isExpired();
        
        // Then
        assertThat(isExpired).isFalse();
    }
    
    @Test
    void isExpired_WithPastExpiryDate_ShouldReturnTrue() {
        // Given
        RefreshToken token = new RefreshToken();
        token.setExpiryDate(Instant.now().minus(1, ChronoUnit.HOURS).getEpochSecond());
        
        // When
        boolean isExpired = token.isExpired();
        
        // Then
        assertThat(isExpired).isTrue();
    }
    
    @Test
    void isExpired_WithCurrentTimestamp_ShouldReturnTrue() {
        // Given
        RefreshToken token = new RefreshToken();
        token.setExpiryDate(Instant.now().getEpochSecond());
        
        // When
        boolean isExpired = token.isExpired();
        
        // Then
        assertThat(isExpired).isTrue(); // Current time should be considered expired
    }
    
    @Test
    void defaultConstructor_ShouldSetItemType() {
        // When
        RefreshToken token = new RefreshToken();
        
        // Then
        assertThat(token.getItemType()).isEqualTo("REFRESH_TOKEN");
    }
    
    @Test
    void getTokenHash_ShouldReturnTokenHashValue() {
        // Given
        RefreshToken token = new RefreshToken();
        String expectedHash = "test-hash-value";
        token.setTokenHash(expectedHash);
        
        // When
        String actualHash = token.getTokenHash();
        
        // Then
        assertThat(actualHash).isEqualTo(expectedHash);
    }
    
    @Test
    void setTokenHash_ShouldUpdateTokenHashValue() {
        // Given
        RefreshToken token = new RefreshToken();
        String newHash = "new-hash-value";
        
        // When
        token.setTokenHash(newHash);
        
        // Then
        assertThat(token.getTokenHash()).isEqualTo(newHash);
    }
}