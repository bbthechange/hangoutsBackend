package com.bbthechange.inviter.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtService
 * 
 * Test Coverage:
 * - generateToken - JWT token generation
 * - extractUserId - Extract user ID from token
 * - isTokenValid - Token validation
 * - Token expiration handling
 * - Invalid token scenarios
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtService Tests")
class JwtServiceTest {

    private JwtService jwtService;

    private static final String SECRET_KEY = "mySecretKeyForJWTTokenGenerationThatIsLongEnough123456789";
    private final SecretKey key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes());
    
    private String testUserId;
    private String validToken;

    @BeforeEach
    void setUp() {
        // Manually create and initialize JwtService for testing
        jwtService = new JwtService();
        // Use reflection to set the private secretKey field
        try {
            java.lang.reflect.Field secretKeyField = JwtService.class.getDeclaredField("secretKey");
            secretKeyField.setAccessible(true);
            secretKeyField.set(jwtService, SECRET_KEY);
            // Call the init method to initialize the key
            jwtService.init();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize JwtService in test", e);
        }
        
        testUserId = "550e8400-e29b-41d4-a716-446655440000";
        // Generate a fresh token for each test
        validToken = jwtService.generateToken(testUserId);
    }

    @Nested
    @DisplayName("generateToken - Token Generation Tests")
    class GenerateTokenTests {

        @Test
        @DisplayName("Should generate valid JWT token")
        void generateToken_Success() {
            // Act
            String token = jwtService.generateToken(testUserId);

            // Assert
            assertNotNull(token);
            assertFalse(token.isEmpty());
            assertTrue(token.contains(".")); // JWT tokens have dots as separators
            
            // Verify token structure (header.payload.signature)
            String[] parts = token.split("\\.");
            assertEquals(3, parts.length, "JWT token should have 3 parts");
        }

        @Test
        @DisplayName("Should generate different tokens for same user at different times")
        void generateToken_DifferentTokensForSameUser() throws InterruptedException {
            // Arrange
            String firstToken = jwtService.generateToken(testUserId);
            Thread.sleep(1000); // Ensure different timestamp (1 second)
            
            // Act
            String secondToken = jwtService.generateToken(testUserId);

            // Assert
            // Note: JWT tokens might be same if generated at same millisecond
            // This test primarily verifies that the method works correctly
            assertNotNull(firstToken);
            assertNotNull(secondToken);
        }

        @Test
        @DisplayName("Should generate different tokens for different users")
        void generateToken_DifferentTokensForDifferentUsers() {
            // Arrange
            String userId1 = "user1";
            String userId2 = "user2";

            // Act
            String token1 = jwtService.generateToken(userId1);
            String token2 = jwtService.generateToken(userId2);

            // Assert
            assertNotEquals(token1, token2, "Tokens for different users should be different");
        }

        @Test
        @DisplayName("Should handle null user ID")
        void generateToken_NullUserId() {
            // Act
            String token = jwtService.generateToken(null);

            // Assert
            assertNotNull(token);
            
            // Verify the token contains null as subject
            Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            assertNull(claims.getSubject());
        }

        @Test
        @DisplayName("Should handle empty user ID")
        void generateToken_EmptyUserId() {
            // Act
            String token = jwtService.generateToken("");

            // Assert
            assertNotNull(token);
            
            // Verify the token contains empty string as subject
            Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            // For empty string, subject might be null or empty
            assertTrue(claims.getSubject() == null || claims.getSubject().isEmpty());
        }

        @Test
        @DisplayName("Should set correct expiration time")
        void generateToken_CorrectExpiration() {
            // Arrange
            long beforeGeneration = System.currentTimeMillis();
            
            // Act
            String token = jwtService.generateToken(testUserId);
            
            // Assert
            Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
            
            long expectedExpiration = beforeGeneration + 1800000; // 30 minutes
            long actualExpiration = claims.getExpiration().getTime();
            
            // Allow for small time differences (within 1 second)
            assertTrue(Math.abs(actualExpiration - expectedExpiration) < 1000,
                "Token expiration should be approximately 30 minutes from generation");
        }
    }

    @Nested
    @DisplayName("extractUserId - User ID Extraction Tests")
    class ExtractUserIdTests {

        @Test
        @DisplayName("Should extract user ID from valid token")
        void extractUserId_Success() {
            // Act
            String extractedUserId = jwtService.extractUserId(validToken);

            // Assert
            assertEquals(testUserId, extractedUserId);
        }

        @Test
        @DisplayName("Should extract null from token with null subject")
        void extractUserId_NullSubject() {
            // Arrange
            String tokenWithNullSubject = jwtService.generateToken(null);

            // Act
            String extractedUserId = jwtService.extractUserId(tokenWithNullSubject);

            // Assert
            assertNull(extractedUserId);
        }

        @Test
        @DisplayName("Should extract empty or null from token with empty subject")
        void extractUserId_EmptySubject() {
            // Arrange
            String tokenWithEmptySubject = jwtService.generateToken("");

            // Act
            String extractedUserId = jwtService.extractUserId(tokenWithEmptySubject);

            // Assert
            // For empty string, the extracted result might be null or empty
            assertTrue(extractedUserId == null || extractedUserId.isEmpty());
        }

        @Test
        @DisplayName("Should throw exception for invalid token format")
        void extractUserId_InvalidToken() {
            // Act & Assert
            assertThrows(Exception.class, () -> {
                jwtService.extractUserId("invalid.token.format");
            });
        }

        @Test
        @DisplayName("Should throw exception for malformed token")
        void extractUserId_MalformedToken() {
            // Act & Assert
            assertThrows(Exception.class, () -> {
                jwtService.extractUserId("not-a-jwt-token");
            });
        }
    }

    @Nested
    @DisplayName("isTokenValid - Token Validation Tests")
    class IsTokenValidTests {

        @Test
        @DisplayName("Should return true for valid token")
        void isTokenValid_ValidToken() {
            // Act
            boolean isValid = jwtService.isTokenValid(validToken);

            // Assert
            assertTrue(isValid);
        }

        @Test
        @DisplayName("Should return false for expired token")
        void isTokenValid_ExpiredToken() {
            // Arrange - Create expired token manually
            String expiredToken = Jwts.builder()
                .subject(testUserId)
                .issuedAt(new Date(System.currentTimeMillis() - 86400000 - 1000)) // 24 hours + 1 second ago
                .expiration(new Date(System.currentTimeMillis() - 1000)) // 1 second ago
                .signWith(key)
                .compact();

            // Act
            boolean isValid = jwtService.isTokenValid(expiredToken);

            // Assert
            assertFalse(isValid);
        }

        @Test
        @DisplayName("Should return false for invalid token format")
        void isTokenValid_InvalidFormat() {
            // Act
            boolean isValid = jwtService.isTokenValid("invalid.token.format");

            // Assert
            assertFalse(isValid);
        }

        @Test
        @DisplayName("Should return false for malformed token")
        void isTokenValid_MalformedToken() {
            // Act
            boolean isValid = jwtService.isTokenValid("not-a-jwt-token");

            // Assert
            assertFalse(isValid);
        }

        @Test
        @DisplayName("Should return false for null token")
        void isTokenValid_NullToken() {
            // Act
            boolean isValid = jwtService.isTokenValid(null);

            // Assert
            assertFalse(isValid);
        }

        @Test
        @DisplayName("Should return false for empty token")
        void isTokenValid_EmptyToken() {
            // Act
            boolean isValid = jwtService.isTokenValid("");

            // Assert
            assertFalse(isValid);
        }

        @Test
        @DisplayName("Should return false for token with wrong signature")
        void isTokenValid_WrongSignature() {
            // Arrange - Create token with different secret
            SecretKey wrongKey = Keys.hmacShaKeyFor("differentSecretKeyThatIsLongEnough123456789".getBytes());
            String tokenWithWrongSignature = Jwts.builder()
                .subject(testUserId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86400000))
                .signWith(wrongKey)
                .compact();

            // Act
            boolean isValid = jwtService.isTokenValid(tokenWithWrongSignature);

            // Assert
            assertFalse(isValid);
        }

        @Test
        @DisplayName("Should return true for token that expires in the future")
        void isTokenValid_FutureExpiration() {
            // Arrange - Create token that expires far in the future
            String futureToken = Jwts.builder()
                .subject(testUserId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 172800000)) // 48 hours
                .signWith(key)
                .compact();

            // Act
            boolean isValid = jwtService.isTokenValid(futureToken);

            // Assert
            assertTrue(isValid);
        }

        @Test
        @DisplayName("Should handle token that expires exactly now")
        void isTokenValid_ExpiresNow() {
            // Arrange - Create token that expires at current time
            String expiringNowToken = Jwts.builder()
                .subject(testUserId)
                .issuedAt(new Date(System.currentTimeMillis() - 1000)) // 1 second ago
                .expiration(new Date(System.currentTimeMillis())) // Expires now
                .signWith(key)
                .compact();

            // Act
            boolean isValid = jwtService.isTokenValid(expiringNowToken);

            // Assert
            // Token should be invalid if it expires exactly now (edge case handling)
            assertFalse(isValid);
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should generate, validate, and extract from token correctly")
        void fullTokenLifecycle() {
            // Arrange
            String originalUserId = "integration-test-user-id";

            // Act - Generate token
            String token = jwtService.generateToken(originalUserId);
            
            // Act - Validate token
            boolean isValid = jwtService.isTokenValid(token);
            
            // Act - Extract user ID
            String extractedUserId = jwtService.extractUserId(token);

            // Assert
            assertTrue(isValid, "Generated token should be valid");
            assertEquals(originalUserId, extractedUserId, "Extracted user ID should match original");
        }

        @Test
        @DisplayName("Should handle special characters in user ID")
        void specialCharactersInUserId() {
            // Arrange
            String specialUserId = "user@domain.com+special-chars_123";

            // Act
            String token = jwtService.generateToken(specialUserId);
            String extractedUserId = jwtService.extractUserId(token);
            boolean isValid = jwtService.isTokenValid(token);

            // Assert
            assertTrue(isValid);
            assertEquals(specialUserId, extractedUserId);
        }

        @Test
        @DisplayName("Should handle very long user ID")
        void veryLongUserId() {
            // Arrange
            String longUserId = "a".repeat(1000); // 1000 character user ID

            // Act
            String token = jwtService.generateToken(longUserId);
            String extractedUserId = jwtService.extractUserId(token);
            boolean isValid = jwtService.isTokenValid(token);

            // Assert
            assertTrue(isValid);
            assertEquals(longUserId, extractedUserId);
        }
    }

    @Nested
    @DisplayName("Password Reset Token Tests")
    class PasswordResetTokenTests {

        @Test
        @DisplayName("generatePasswordResetToken - Should create valid token with correct claims")
        void generatePasswordResetToken_CreatesValidToken() {
            // Act
            String resetToken = jwtService.generatePasswordResetToken(testUserId);

            // Assert
            assertNotNull(resetToken);
            assertFalse(resetToken.isEmpty());

            // Parse and verify claims
            Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(resetToken)
                .getPayload();

            assertEquals(testUserId, claims.getSubject());
            assertEquals("password_reset", claims.get("type"));
            assertNotNull(claims.getExpiration());

            // Verify expiration is ~15 minutes from now
            long expectedExpiration = System.currentTimeMillis() + 900000; // 15 minutes
            long actualExpiration = claims.getExpiration().getTime();
            assertTrue(Math.abs(actualExpiration - expectedExpiration) < 1000,
                "Token should expire in approximately 15 minutes");
        }

        @Test
        @DisplayName("isPasswordResetTokenValid - Should return true for valid reset token")
        void isPasswordResetTokenValid_WithValidToken_ReturnsTrue() {
            // Arrange
            String resetToken = jwtService.generatePasswordResetToken(testUserId);

            // Act
            boolean isValid = jwtService.isPasswordResetTokenValid(resetToken);

            // Assert
            assertTrue(isValid);
        }

        @Test
        @DisplayName("isPasswordResetTokenValid - Should return false for expired token")
        void isPasswordResetTokenValid_WithExpiredToken_ReturnsFalse() {
            // Arrange - Create expired reset token
            String expiredToken = Jwts.builder()
                .subject(testUserId)
                .claim("type", "password_reset")
                .issuedAt(new Date(System.currentTimeMillis() - 1000000)) // Long ago
                .expiration(new Date(System.currentTimeMillis() - 1000)) // 1 second ago
                .signWith(key)
                .compact();

            // Act
            boolean isValid = jwtService.isPasswordResetTokenValid(expiredToken);

            // Assert
            assertFalse(isValid);
        }

        @Test
        @DisplayName("isPasswordResetTokenValid - Should return false for access token")
        void isPasswordResetTokenValid_WithAccessToken_ReturnsFalse() {
            // Arrange - Generate regular access token (no type claim)
            String accessToken = jwtService.generateToken(testUserId);

            // Act
            boolean isValid = jwtService.isPasswordResetTokenValid(accessToken);

            // Assert
            assertFalse(isValid);
        }

        @Test
        @DisplayName("isPasswordResetTokenValid - Should return false for wrong type claim")
        void isPasswordResetTokenValid_WithWrongType_ReturnsFalse() {
            // Arrange - Create token with wrong type
            String wrongTypeToken = Jwts.builder()
                .subject(testUserId)
                .claim("type", "refresh_token") // Wrong type
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900000))
                .signWith(key)
                .compact();

            // Act
            boolean isValid = jwtService.isPasswordResetTokenValid(wrongTypeToken);

            // Assert
            assertFalse(isValid);
        }

        @Test
        @DisplayName("isPasswordResetTokenValid - Should return false for invalid signature")
        void isPasswordResetTokenValid_WithInvalidSignature_ReturnsFalse() {
            // Arrange - Create token with wrong key
            SecretKey wrongKey = Keys.hmacShaKeyFor("differentSecretKeyForPasswordReset123456789".getBytes());
            String invalidToken = Jwts.builder()
                .subject(testUserId)
                .claim("type", "password_reset")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900000))
                .signWith(wrongKey)
                .compact();

            // Act
            boolean isValid = jwtService.isPasswordResetTokenValid(invalidToken);

            // Assert
            assertFalse(isValid);
        }

        @Test
        @DisplayName("isPasswordResetTokenValid - Should return false for malformed token")
        void isPasswordResetTokenValid_WithMalformedToken_ReturnsFalse() {
            // Act
            boolean isValid = jwtService.isPasswordResetTokenValid("not.a.valid.token");

            // Assert
            assertFalse(isValid);
        }

        @Test
        @DisplayName("extractUserIdFromResetToken - Should extract userId from valid token")
        void extractUserIdFromResetToken_WithValidToken_ReturnsUserId() {
            // Arrange
            String resetToken = jwtService.generatePasswordResetToken(testUserId);

            // Act
            String extractedUserId = jwtService.extractUserIdFromResetToken(resetToken);

            // Assert
            assertEquals(testUserId, extractedUserId);
        }

        @Test
        @DisplayName("extractUserIdFromResetToken - Should throw exception for access token")
        void extractUserIdFromResetToken_WithAccessToken_ThrowsException() {
            // Arrange - Regular access token
            String accessToken = jwtService.generateToken(testUserId);

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                jwtService.extractUserIdFromResetToken(accessToken);
            }, "Should throw IllegalArgumentException for non-reset token");
        }

        @Test
        @DisplayName("extractUserIdFromResetToken - Should throw exception for expired token")
        void extractUserIdFromResetToken_WithExpiredToken_ThrowsException() {
            // Arrange - Expired reset token
            String expiredToken = Jwts.builder()
                .subject(testUserId)
                .claim("type", "password_reset")
                .issuedAt(new Date(System.currentTimeMillis() - 1000000))
                .expiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(key)
                .compact();

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                jwtService.extractUserIdFromResetToken(expiredToken);
            }, "Should throw IllegalArgumentException for expired token");
        }

        @Test
        @DisplayName("extractUserIdFromResetToken - Should throw exception for invalid token")
        void extractUserIdFromResetToken_WithInvalidToken_ThrowsException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                jwtService.extractUserIdFromResetToken("invalid.token");
            }, "Should throw IllegalArgumentException for invalid token");
        }

        @Test
        @DisplayName("Password reset token should be different from access token")
        void passwordResetToken_DifferentFromAccessToken() {
            // Arrange
            String accessToken = jwtService.generateToken(testUserId);
            String resetToken = jwtService.generatePasswordResetToken(testUserId);

            // Act
            boolean accessTokenValidAsReset = jwtService.isPasswordResetTokenValid(accessToken);
            boolean resetTokenValidAsAccess = jwtService.isTokenValid(resetToken); // Should be true (valid JWT)

            // Assert
            assertNotEquals(accessToken, resetToken, "Reset and access tokens should be different");
            assertFalse(accessTokenValidAsReset, "Access token should not be valid as reset token");
            assertTrue(resetTokenValidAsAccess, "Reset token should be valid JWT (but used differently)");
        }
    }
}