package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.RefreshTokenPair;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.model.RefreshToken;
import com.bbthechange.inviter.repository.RefreshTokenRepository;
import com.bbthechange.inviter.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenRotationServiceTest {
    
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    
    @Mock
    private RefreshTokenHashingService hashingService;
    
    @Mock
    private JwtService jwtService;
    
    @Mock
    private UserRepository userRepository;
    
    private RefreshTokenRotationService rotationService;
    
    @BeforeEach
    void setUp() {
        rotationService = new RefreshTokenRotationService(
            refreshTokenRepository,
            hashingService,
            jwtService,
            userRepository
        );
    }
    
    @Test
    void refreshTokens_WebClient_ShouldRotateWithGracePeriod() {
        // Given
        String rawRefreshToken = "valid-refresh-token";
        String lookupHash = "lookup-hash";
        String userId = "user-123";
        String newRawToken = "new-refresh-token";
        String newAccessToken = "new-access-token";

        RefreshToken existingToken = createValidRefreshToken(userId);

        when(hashingService.generateLookupHash(rawRefreshToken)).thenReturn(lookupHash);
        when(refreshTokenRepository.findByTokenHash(lookupHash)).thenReturn(Optional.of(existingToken));
        when(hashingService.matches(rawRefreshToken, existingToken.getSecurityHash())).thenReturn(true);
        when(hashingService.generateRefreshToken()).thenReturn(newRawToken);
        when(jwtService.generateToken(userId)).thenReturn(newAccessToken);
        when(hashingService.generateLookupHash(newRawToken)).thenReturn("new-lookup-hash");
        when(hashingService.generateSecurityHash(newRawToken)).thenReturn("new-security-hash");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When - web client (isMobile = false)
        RefreshTokenPair result = rotationService.refreshTokens(rawRefreshToken, "127.0.0.1", "test-agent", false);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAccessToken()).isEqualTo(newAccessToken);
        assertThat(result.getRefreshToken()).isEqualTo(newRawToken);

        // Web rotation: saves new token AND marks old token as superseded (2 saves)
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
        // No delete - old token is marked superseded for grace period, not deleted
        verify(refreshTokenRepository, never()).deleteByTokenId(anyString(), anyString());
    }

    @Test
    void refreshTokens_MobileClient_ShouldNotRotateToken() {
        // Given
        String rawRefreshToken = "valid-refresh-token";
        String lookupHash = "lookup-hash";
        String userId = "user-123";
        String newAccessToken = "new-access-token";

        RefreshToken existingToken = createValidRefreshToken(userId);

        when(hashingService.generateLookupHash(rawRefreshToken)).thenReturn(lookupHash);
        when(refreshTokenRepository.findByTokenHash(lookupHash)).thenReturn(Optional.of(existingToken));
        when(hashingService.matches(rawRefreshToken, existingToken.getSecurityHash())).thenReturn(true);
        when(jwtService.generateToken(userId)).thenReturn(newAccessToken);

        // When - mobile client (isMobile = true)
        RefreshTokenPair result = rotationService.refreshTokens(rawRefreshToken, "127.0.0.1", "test-agent", true);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAccessToken()).isEqualTo(newAccessToken);
        assertThat(result.getRefreshToken()).isEqualTo(rawRefreshToken); // Same token returned

        // Mobile: no rotation, no saves, no deletes
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        verify(refreshTokenRepository, never()).deleteByTokenId(anyString(), anyString());
        verify(hashingService, never()).generateRefreshToken(); // No new token generated
    }
    
    @Test
    void refreshTokens_WithInvalidToken_ShouldThrowUnauthorizedException() {
        // Given
        String rawRefreshToken = "invalid-refresh-token";
        String lookupHash = "lookup-hash";
        
        when(hashingService.generateLookupHash(rawRefreshToken)).thenReturn(lookupHash);
        when(refreshTokenRepository.findByTokenHash(lookupHash)).thenReturn(Optional.empty());
        
        // When & Then
        assertThrows(UnauthorizedException.class, () -> 
            rotationService.refreshTokens(rawRefreshToken, "127.0.0.1", "test-agent")
        );
        
        verify(refreshTokenRepository, never()).save(any());
        verify(refreshTokenRepository, never()).deleteByTokenId(anyString(), anyString());
    }
    
    @Test
    void refreshTokens_WithIncorrectSecurityHash_ShouldThrowUnauthorizedException() {
        // Given
        String rawRefreshToken = "refresh-token";
        String lookupHash = "lookup-hash";
        String userId = "user-123";
        
        RefreshToken existingToken = createValidRefreshToken(userId);
        
        when(hashingService.generateLookupHash(rawRefreshToken)).thenReturn(lookupHash);
        when(refreshTokenRepository.findByTokenHash(lookupHash)).thenReturn(Optional.of(existingToken));
        when(hashingService.matches(rawRefreshToken, existingToken.getSecurityHash())).thenReturn(false);
        
        // When & Then
        assertThrows(UnauthorizedException.class, () -> 
            rotationService.refreshTokens(rawRefreshToken, "127.0.0.1", "test-agent")
        );
        
        verify(refreshTokenRepository, never()).save(any());
        verify(refreshTokenRepository, never()).deleteByTokenId(anyString(), anyString());
    }
    
    @Test
    void refreshTokens_WithExpiredToken_ShouldThrowUnauthorizedException() {
        // Given
        String rawRefreshToken = "expired-refresh-token";
        String lookupHash = "lookup-hash";
        String userId = "user-123";
        
        RefreshToken expiredToken = createExpiredRefreshToken(userId);
        
        when(hashingService.generateLookupHash(rawRefreshToken)).thenReturn(lookupHash);
        when(refreshTokenRepository.findByTokenHash(lookupHash)).thenReturn(Optional.of(expiredToken));
        when(hashingService.matches(rawRefreshToken, expiredToken.getSecurityHash())).thenReturn(true);
        
        // When & Then
        assertThrows(UnauthorizedException.class, () -> 
            rotationService.refreshTokens(rawRefreshToken, "127.0.0.1", "test-agent")
        );
        
        verify(refreshTokenRepository).deleteByTokenId(userId, expiredToken.getTokenId());
        verify(refreshTokenRepository, never()).save(any());
    }
    
    @Test
    void createRefreshToken_ShouldCreateAndSaveToken() {
        // Given
        String userId = "user-123";
        String rawToken = "raw-token";
        String deviceId = "device-123";
        String ipAddress = "127.0.0.1";
        String lookupHash = "lookup-hash";
        String securityHash = "security-hash";
        
        when(hashingService.generateLookupHash(rawToken)).thenReturn(lookupHash);
        when(hashingService.generateSecurityHash(rawToken)).thenReturn(securityHash);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        RefreshToken result = rotationService.createRefreshToken(userId, rawToken, deviceId, ipAddress);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getTokenHash()).isEqualTo(lookupHash);
        assertThat(result.getSecurityHash()).isEqualTo(securityHash);
        assertThat(result.getDeviceId()).isEqualTo(deviceId);
        assertThat(result.getIpAddress()).isEqualTo(ipAddress);
        
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }
    
    @Test
    void revokeRefreshToken_WithValidToken_ShouldDeleteToken() {
        // Given
        String rawToken = "refresh-token";
        String lookupHash = "lookup-hash";
        String userId = "user-123";
        RefreshToken token = createValidRefreshToken(userId);
        
        when(hashingService.generateLookupHash(rawToken)).thenReturn(lookupHash);
        when(refreshTokenRepository.findByTokenHash(lookupHash)).thenReturn(Optional.of(token));
        when(hashingService.matches(rawToken, token.getSecurityHash())).thenReturn(true);
        
        // When
        rotationService.revokeRefreshToken(rawToken);
        
        // Then
        verify(refreshTokenRepository).deleteByTokenId(userId, token.getTokenId());
    }
    
    @Test
    void revokeAllUserTokens_ShouldCallRepositoryMethod() {
        // Given
        String userId = "user-123";
        
        // When
        rotationService.revokeAllUserTokens(userId);
        
        // Then
        verify(refreshTokenRepository).deleteAllUserTokens(userId);
    }
    
    private RefreshToken createValidRefreshToken(String userId) {
        RefreshToken token = new RefreshToken();
        token.setTokenId("token-123");
        token.setUserId(userId);
        token.setTokenHash("token-hash");
        token.setSecurityHash("security-hash");
        token.setExpiryDate(Instant.now().plus(30, ChronoUnit.DAYS).getEpochSecond()); // Valid for 30 days
        token.setDeviceId("device-123");
        token.setIpAddress("127.0.0.1");
        return token;
    }
    
    private RefreshToken createExpiredRefreshToken(String userId) {
        RefreshToken token = new RefreshToken();
        token.setTokenId("token-123");
        token.setUserId(userId);
        token.setTokenHash("token-hash");
        token.setSecurityHash("security-hash");
        token.setExpiryDate(Instant.now().minus(1, ChronoUnit.DAYS).getEpochSecond()); // Expired yesterday
        token.setDeviceId("device-123");
        token.setIpAddress("127.0.0.1");
        return token;
    }
}