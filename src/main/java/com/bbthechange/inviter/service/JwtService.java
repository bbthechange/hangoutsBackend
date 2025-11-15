package com.bbthechange.inviter.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {
    
    /**
 * The secret key for signing JWTs. This value is injected from the 'jwt.secret' property.
 *
 * This property is crucial for cryptographic operations. For local development and CI/CD build processes,
 * a default, insecure value is provided here to allow the application context to initialize successfully.
 * This ensures the application can run locally and pass builds without requiring environment-specific setup.
 *
 * In deployed environments (staging and production), this default value is consistently overridden
 * by a strong, secure secret provided via an Elastic Beanstalk environment variable
 */
@Value("${jwt.secret:default_secret_for_local_development_only_12345}")
    private String secretKey; // Environment variable, not hardcoded
    
    private static final long ACCESS_TOKEN_EXPIRATION = 1800000; // 30 minutes
    private static final long PASSWORD_RESET_TOKEN_EXPIRATION = 900000; // 15 minutes

    private SecretKey key;
    
    @PostConstruct
    public void init() {
        // Ensure secret key has proper entropy
        if (secretKey.length() < 32) {
            throw new IllegalArgumentException("JWT secret key must be at least 32 characters (was " + secretKey.length() + ")");
        }
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }
    
    public String generateToken(String userId) {
        return Jwts.builder()
                .subject(userId)  // Use userId as subject
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRATION))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
    
    public String extractUserId(String token) {
        return extractClaims(token).getSubject();
    }
    
    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractClaims(token);
            return !claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
    
    public int getAccessTokenExpirationSeconds() {
        return (int) (ACCESS_TOKEN_EXPIRATION / 1000);
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Generate a password reset token (15 minute expiration).
     * These tokens include a "type" claim to distinguish them from access tokens.
     *
     * @param userId The user ID to encode in the token
     * @return JWT reset token with 15 minute expiration
     */
    public String generatePasswordResetToken(String userId) {
        return Jwts.builder()
                .subject(userId)
                .claim("type", "password_reset")  // Distinguish from access tokens
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + PASSWORD_RESET_TOKEN_EXPIRATION))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Validate a password reset token.
     * Checks:
     * - Valid signature
     * - Not expired
     * - Has correct "type" claim
     *
     * @param token The JWT token to validate
     * @return true if token is valid, false otherwise
     */
    public boolean isPasswordResetTokenValid(String token) {
        try {
            Claims claims = extractClaims(token);
            return "password_reset".equals(claims.get("type"))
                && !claims.getExpiration().before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Extract user ID from a password reset token.
     * Validates the token before extraction to prevent token confusion attacks.
     *
     * @param token The JWT reset token
     * @return The user ID from the token subject
     * @throws IllegalArgumentException if token is not a valid password reset token
     */
    public String extractUserIdFromResetToken(String token) {
        if (!isPasswordResetTokenValid(token)) {
            throw new IllegalArgumentException("Not a valid password reset token");
        }
        return extractClaims(token).getSubject();
    }
}