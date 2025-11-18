package com.bbthechange.inviter.service;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class RefreshTokenHashingService {

    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenHashingService.class);

    @Value("${refresh.token.salt:default-salt-change-in-production}")
    private String salt;

    // Temporary: For backwards compatibility with existing BCrypt tokens
    // TODO: Remove after 2025-12-17 when all BCrypt tokens expired (30 days from deployment)
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    /**
     * Generate deterministic SHA-256 hash for fast GSI lookups
     */
    public String generateLookupHash(String rawToken) {
        return DigestUtils.sha256Hex(rawToken + salt);
    }
    
    /**
     * Generate SHA-256 hash for secure validation
     */
    public String generateSecurityHash(String rawToken) {
        // All new tokens use SHA-256 (fast, cryptographically secure for high-entropy tokens)
        return DigestUtils.sha256Hex(rawToken + salt);
    }
    
    /**
     * Validate raw token against stored hash
     * DUAL-MODE: Supports both BCrypt (legacy) and SHA-256 (new) hashes
     */
    public boolean matches(String rawToken, String securityHash) {
        if (securityHash == null) {
            return false;
        }

        // Auto-detect hash type by format
        if (isBCryptHash(securityHash)) {
            // Legacy path: BCrypt validation (~334ms)
            logger.debug("Validating legacy BCrypt token");
            return passwordEncoder.matches(rawToken, securityHash);
        } else {
            // New path: SHA-256 validation (~1ms)
            logger.debug("Validating SHA-256 token");
            String computedHash = DigestUtils.sha256Hex(rawToken + salt);

            // Use constant-time comparison to prevent timing attacks
            byte[] computedBytes = computedHash.getBytes(StandardCharsets.UTF_8);
            byte[] storedBytes = securityHash.getBytes(StandardCharsets.UTF_8);

            return MessageDigest.isEqual(computedBytes, storedBytes);
        }
    }

    /**
     * Detect BCrypt hash format
     * BCrypt hashes always start with $2a$, $2b$, $2y$
     */
    private boolean isBCryptHash(String hash) {
        return hash.startsWith("$2");
    }
    
    /**
     * Generate cryptographically secure 256-bit refresh token
     */
    public String generateRefreshToken() {
        byte[] bytes = new byte[32]; // 256 bits
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}