package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.RefreshTokenPair;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.model.RefreshToken;
import com.bbthechange.inviter.model.User;
import com.bbthechange.inviter.repository.RefreshTokenRepository;
import com.bbthechange.inviter.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RefreshTokenRotationService {
    
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenHashingService hashingService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    
    /**
     * Main refresh operation with dual-layer validation and token rotation
     */
    public RefreshTokenPair refreshTokens(String rawRefreshToken, String ipAddress, String userAgent) {
        
        // Stage 1: Fast lookup by SHA-256 hash (O(1) GSI query)
        String lookupHash = hashingService.generateLookupHash(rawRefreshToken);
        Optional<RefreshToken> existingTokenOpt = refreshTokenRepository.findByTokenHash(lookupHash);
        
        if (existingTokenOpt.isEmpty()) {
            throw new UnauthorizedException("Invalid refresh token");
        }
        
        RefreshToken existingToken = existingTokenOpt.get();
        
        // Stage 2: Secure validation against BCrypt hash
        if (!hashingService.matches(rawRefreshToken, existingToken.getSecurityHash())) {
            throw new UnauthorizedException("Invalid refresh token");
        }
        
        // Stage 3: Check expiration
        if (existingToken.isExpired()) {
            refreshTokenRepository.deleteByTokenId(existingToken.getUserId(), existingToken.getTokenId());
            throw new UnauthorizedException("Refresh token expired");
        }
        
        // Stage 4: Security checks
        if (isSuspiciousActivity(existingToken, ipAddress, userAgent)) {
            // Revoke ALL user tokens on suspicious activity
            refreshTokenRepository.deleteAllUserTokens(existingToken.getUserId());
            throw new UnauthorizedException("Suspicious activity detected - all sessions revoked");
        }
        
        // Stage 5: Generate new token pair
        String newRawRefreshToken = hashingService.generateRefreshToken();
        String newAccessToken = jwtService.generateToken(existingToken.getUserId()); // Use existing method signature
        
        // Stage 6: Create new refresh token record
        String newLookupHash = hashingService.generateLookupHash(newRawRefreshToken);
        String newSecurityHash = hashingService.generateSecurityHash(newRawRefreshToken);
        
        RefreshToken newTokenRecord = new RefreshToken(
            existingToken.getUserId(), 
            newLookupHash,
            newSecurityHash,
            existingToken.getDeviceId(),
            ipAddress
        );
        refreshTokenRepository.save(newTokenRecord);
        
        // Stage 7: Delete old refresh token (token rotation)
        refreshTokenRepository.deleteByTokenId(existingToken.getUserId(), existingToken.getTokenId());
        
        return new RefreshTokenPair(newAccessToken, newRawRefreshToken);
    }
    
    /**
     * Create initial refresh token for login
     */
    public RefreshToken createRefreshToken(String userId, String rawRefreshToken, String deviceId, String ipAddress) {
        String lookupHash = hashingService.generateLookupHash(rawRefreshToken);
        String securityHash = hashingService.generateSecurityHash(rawRefreshToken);
        
        RefreshToken refreshToken = new RefreshToken(userId, lookupHash, securityHash, deviceId, ipAddress);
        return refreshTokenRepository.save(refreshToken);
    }
    
    /**
     * Revoke specific refresh token (for logout)
     */
    public void revokeRefreshToken(String rawRefreshToken) {
        String lookupHash = hashingService.generateLookupHash(rawRefreshToken);
        Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(lookupHash);
        
        if (tokenOpt.isPresent() && hashingService.matches(rawRefreshToken, tokenOpt.get().getSecurityHash())) {
            RefreshToken token = tokenOpt.get();
            refreshTokenRepository.deleteByTokenId(token.getUserId(), token.getTokenId());
        }
    }
    
    /**
     * Revoke all user refresh tokens (for logout-all or security response)
     */
    public void revokeAllUserTokens(String userId) {
        refreshTokenRepository.deleteAllUserTokens(userId);
    }
    
    /**
     * Simple suspicious activity detection
     * In production, this would include more sophisticated checks
     */
    private boolean isSuspiciousActivity(RefreshToken token, String currentIp, String currentUserAgent) {
        // For now, just check for rapid token rotation (basic rate limiting)
        // In production, you might check:
        // - IP address changes
        // - Unusual user agent patterns
        // - High frequency refresh attempts
        // - Geographic location changes
        
        return false; // Simplified for initial implementation
    }
}