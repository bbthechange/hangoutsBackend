package com.bbthechange.inviter.service;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class RefreshTokenHashingService {
    
    @Value("${refresh.token.salt:default-salt-change-in-production}")
    private String salt;
    
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    /**
     * Generate deterministic SHA-256 hash for fast GSI lookups
     */
    public String generateLookupHash(String rawToken) {
        return DigestUtils.sha256Hex(rawToken + salt);
    }
    
    /**
     * Generate BCrypt hash for secure validation
     */
    public String generateSecurityHash(String rawToken) {
        return passwordEncoder.encode(rawToken);
    }
    
    /**
     * Validate raw token against BCrypt hash
     */
    public boolean matches(String rawToken, String bcryptHash) {
        return passwordEncoder.matches(rawToken, bcryptHash);
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