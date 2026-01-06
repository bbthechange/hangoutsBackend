package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.RefreshTokenPair;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.model.RefreshToken;
import com.bbthechange.inviter.model.User;
import com.bbthechange.inviter.repository.RefreshTokenRepository;
import com.bbthechange.inviter.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RefreshTokenRotationService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenRotationService.class);

    // Grace period for web clients to use superseded tokens (5 minutes default)
    @Value("${refresh.token.web.grace-period-seconds:300}")
    private long gracePeriodSeconds;

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenHashingService hashingService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    
    /**
     * Main refresh operation with dual-layer validation and conditional token rotation.
     * Mobile clients: No rotation (token stays valid for 30 days)
     * Web clients: Rotation with grace period (old token valid for 5 minutes after rotation)
     *
     * @param rawRefreshToken the raw refresh token from the client
     * @param ipAddress the client's IP address
     * @param userAgent the client's user agent
     * @param isMobile true for mobile clients (no rotation), false for web clients (rotation with grace)
     * @return new access token and (for web) new refresh token, or same refresh token for mobile
     */
    public RefreshTokenPair refreshTokens(String rawRefreshToken, String ipAddress, String userAgent, boolean isMobile) {
        String clientType = isMobile ? "mobile" : "web";

        try {
            // Stage 1: Fast lookup by SHA-256 hash (O(1) GSI query)
            String lookupHash = hashingService.generateLookupHash(rawRefreshToken);
            Optional<RefreshToken> existingTokenOpt = refreshTokenRepository.findByTokenHash(lookupHash);

            if (existingTokenOpt.isEmpty()) {
                log.warn("Token refresh failed: reason=not_found ip={}", ipAddress);
                throw new UnauthorizedException("Invalid refresh token");
            }

            RefreshToken existingToken = existingTokenOpt.get();

            // Stage 2: Secure validation against BCrypt/SHA-256 hash
            if (!hashingService.matches(rawRefreshToken, existingToken.getSecurityHash())) {
                log.warn("Token refresh failed: reason=hash_mismatch user={} ip={}",
                    existingToken.getUserId(), ipAddress);
                throw new UnauthorizedException("Invalid refresh token");
            }

            // Stage 3: Check expiration
            if (existingToken.isExpired()) {
                log.warn("Token refresh failed: reason=expired user={} ip={}",
                    existingToken.getUserId(), ipAddress);
                refreshTokenRepository.deleteByTokenId(existingToken.getUserId(), existingToken.getTokenId());
                throw new UnauthorizedException("Refresh token expired");
            }

            // Stage 4: Check if token was superseded (web only - grace period logic)
            if (existingToken.isSuperseded()) {
                if (!existingToken.isWithinGracePeriod(gracePeriodSeconds)) {
                    log.warn("Token refresh failed: reason=superseded_grace_expired user={} ip={}",
                        existingToken.getUserId(), ipAddress);
                    throw new UnauthorizedException("Refresh token expired (superseded)");
                }
                // Token is within grace period - allow refresh
                log.info("Token refresh using superseded token within grace period: user={} ip={}",
                    existingToken.getUserId(), ipAddress);
            }

            // Stage 5: Security checks
            if (isSuspiciousActivity(existingToken, ipAddress, userAgent)) {
                log.warn("Token refresh failed: reason=suspicious_activity user={} ip={}",
                    existingToken.getUserId(), ipAddress);
                refreshTokenRepository.deleteAllUserTokens(existingToken.getUserId());
                throw new UnauthorizedException("Suspicious activity detected - all sessions revoked");
            }

            // Stage 6: Calculate token age for audit logging
            long tokenAgeMinutes = ChronoUnit.MINUTES.between(existingToken.getCreatedAt(), Instant.now());

            // Stage 7: Generate new access token
            String newAccessToken = jwtService.generateToken(existingToken.getUserId());

            if (isMobile) {
                // MOBILE: No rotation - return same refresh token with new access token
                log.info("Token refresh: user={} clientType={} ip={} tokenAge={}min success=true",
                    existingToken.getUserId(), clientType, ipAddress, tokenAgeMinutes);
                return new RefreshTokenPair(newAccessToken, rawRefreshToken);
            } else {
                // WEB: Rotate with grace period
                String newRawRefreshToken = hashingService.generateRefreshToken();
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

                // Mark old token as superseded (not deleted - allows grace period)
                existingToken.setSupersededAt(Instant.now().getEpochSecond());
                refreshTokenRepository.save(existingToken);

                log.info("Token refresh: user={} clientType={} ip={} tokenAge={}min success=true rotated=true",
                    existingToken.getUserId(), clientType, ipAddress, tokenAgeMinutes);
                return new RefreshTokenPair(newAccessToken, newRawRefreshToken);
            }
        } catch (UnauthorizedException e) {
            // Already logged above
            throw e;
        } catch (Exception e) {
            log.error("Token refresh failed: reason=internal_error ip={} error={}", ipAddress, e.getMessage());
            throw new UnauthorizedException("Token refresh failed");
        }
    }

    /**
     * Main refresh operation - backward compatible overload.
     * Defaults to web client behavior (rotation with grace period).
     * @deprecated Use {@link #refreshTokens(String, String, String, boolean)} instead
     */
    @Deprecated
    public RefreshTokenPair refreshTokens(String rawRefreshToken, String ipAddress, String userAgent) {
        return refreshTokens(rawRefreshToken, ipAddress, userAgent, false);
    }
    
    /**
     * Quick lookup to get userId from a refresh token (for rate limiting).
     * Only does GSI lookup, no hash validation.
     *
     * @param rawRefreshToken the raw refresh token
     * @return userId if token exists, empty if not found
     */
    public Optional<String> getUserIdFromToken(String rawRefreshToken) {
        String lookupHash = hashingService.generateLookupHash(rawRefreshToken);
        return refreshTokenRepository.findByTokenHash(lookupHash)
            .map(RefreshToken::getUserId);
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