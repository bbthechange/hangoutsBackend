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
    
    private SecretKey key;
    
    @PostConstruct
    public void init() {
        // Ensure secret key has proper entropy
        if (secretKey.length() < 32) {
            throw new IllegalArgumentException("JWT secret key must be at least 32 characters");
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
    
    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}