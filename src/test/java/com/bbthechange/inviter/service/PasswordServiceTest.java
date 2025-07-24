package com.bbthechange.inviter.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PasswordService
 * 
 * Test Coverage:
 * - encryptPassword - Password encryption using BCrypt
 * - matches - Password verification against hash
 * - Various password scenarios and edge cases
 * - Security validation
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordService Tests")
class PasswordServiceTest {

    @InjectMocks
    private PasswordService passwordService;

    private String testPassword;
    private String encryptedPassword;

    @BeforeEach
    void setUp() {
        testPassword = "testPassword123!";
        encryptedPassword = passwordService.encryptPassword(testPassword);
    }

    @Nested
    @DisplayName("encryptPassword - Password Encryption Tests")
    class EncryptPasswordTests {

        @Test
        @DisplayName("Should encrypt password successfully")
        void encryptPassword_Success() {
            // Act
            String encrypted = passwordService.encryptPassword("password123");

            // Assert
            assertNotNull(encrypted);
            assertNotEquals("password123", encrypted);
            assertTrue(encrypted.startsWith("$2a$") || encrypted.startsWith("$2b$"), 
                "BCrypt hash should start with version identifier");
            assertTrue(encrypted.length() >= 60, "BCrypt hash should be at least 60 characters");
        }

        @Test
        @DisplayName("Should generate different hashes for same password")
        void encryptPassword_DifferentHashesForSamePassword() {
            // Arrange
            String password = "samePassword";

            // Act
            String hash1 = passwordService.encryptPassword(password);
            String hash2 = passwordService.encryptPassword(password);

            // Assert
            assertNotEquals(hash1, hash2, "BCrypt should generate different hashes for same password due to salt");
        }

        @Test
        @DisplayName("Should handle empty password")
        void encryptPassword_EmptyPassword() {
            // Act
            String encrypted = passwordService.encryptPassword("");

            // Assert
            assertNotNull(encrypted);
            assertNotEquals("", encrypted);
            assertTrue(encrypted.startsWith("$2a$") || encrypted.startsWith("$2b$"));
        }

        @Test
        @DisplayName("Should handle null password")
        void encryptPassword_NullPassword() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                passwordService.encryptPassword(null);
            });
        }

        @Test
        @DisplayName("Should handle very long password")
        void encryptPassword_VeryLongPassword() {
            // Arrange
            String longPassword = "a".repeat(1000);

            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                passwordService.encryptPassword(longPassword);
            });
        }

        @Test
        @DisplayName("Should handle password with special characters")
        void encryptPassword_SpecialCharacters() {
            // Arrange
            String specialPassword = "p@$$w0rd!#$%^&*()_+-=[]{}|;':\",./<>?`~";

            // Act
            String encrypted = passwordService.encryptPassword(specialPassword);

            // Assert
            assertNotNull(encrypted);
            assertNotEquals(specialPassword, encrypted);
            assertTrue(encrypted.startsWith("$2a$") || encrypted.startsWith("$2b$"));
        }

        @Test
        @DisplayName("Should handle password with unicode characters")
        void encryptPassword_UnicodeCharacters() {
            // Arrange
            String unicodePassword = "пароль密码パスワード🔐";

            // Act
            String encrypted = passwordService.encryptPassword(unicodePassword);

            // Assert
            assertNotNull(encrypted);
            assertNotEquals(unicodePassword, encrypted);
            assertTrue(encrypted.startsWith("$2a$") || encrypted.startsWith("$2b$"));
        }
    }

    @Nested
    @DisplayName("matches - Password Verification Tests")
    class MatchesTests {

        @Test
        @DisplayName("Should return true for correct password")
        void matches_CorrectPassword() {
            // Act
            boolean matches = passwordService.matches(testPassword, encryptedPassword);

            // Assert
            assertTrue(matches);
        }

        @Test
        @DisplayName("Should return false for incorrect password")
        void matches_IncorrectPassword() {
            // Act
            boolean matches = passwordService.matches("wrongPassword", encryptedPassword);

            // Assert
            assertFalse(matches);
        }

        @Test
        @DisplayName("Should throw exception for null plain password")
        void matches_NullPlainPassword() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                passwordService.matches(null, encryptedPassword);
            });
        }

        @Test
        @DisplayName("Should return false for null hashed password")
        void matches_NullHashedPassword() {
            // Act
            boolean matches = passwordService.matches(testPassword, null);

            // Assert
            assertFalse(matches);
        }

        @Test
        @DisplayName("Should throw exception for both null passwords")
        void matches_BothNullPasswords() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> {
                passwordService.matches(null, null);
            });
        }

        @Test
        @DisplayName("Should return false for empty plain password with valid hash")
        void matches_EmptyPlainPassword() {
            // Act
            boolean matches = passwordService.matches("", encryptedPassword);

            // Assert
            assertFalse(matches);
        }

        @Test
        @DisplayName("Should return true for empty password against its hash")
        void matches_EmptyPasswordAgainstItsHash() {
            // Arrange
            String emptyPasswordHash = passwordService.encryptPassword("");

            // Act
            boolean matches = passwordService.matches("", emptyPasswordHash);

            // Assert
            assertTrue(matches);
        }

        @Test
        @DisplayName("Should return false for invalid hash format")
        void matches_InvalidHashFormat() {
            // Act
            boolean matches = passwordService.matches(testPassword, "invalid-hash-format");

            // Assert
            assertFalse(matches);
        }

        @Test
        @DisplayName("Should return false for plain text as hash")
        void matches_PlainTextAsHash() {
            // Act
            boolean matches = passwordService.matches(testPassword, testPassword);

            // Assert
            assertFalse(matches);
        }

        @Test
        @DisplayName("Should handle case sensitivity")
        void matches_CaseSensitive() {
            // Arrange
            String upperCasePassword = testPassword.toUpperCase();

            // Act
            boolean matches = passwordService.matches(upperCasePassword, encryptedPassword);

            // Assert
            assertFalse(matches, "Password matching should be case sensitive");
        }

        @Test
        @DisplayName("Should handle special characters correctly")
        void matches_SpecialCharacters() {
            // Arrange
            String specialPassword = "test@#$%^&*()_+password";
            String specialPasswordHash = passwordService.encryptPassword(specialPassword);

            // Act
            boolean matchesCorrect = passwordService.matches(specialPassword, specialPasswordHash);
            boolean matchesIncorrect = passwordService.matches("test@#$%^&*()_+wrong", specialPasswordHash);

            // Assert
            assertTrue(matchesCorrect);
            assertFalse(matchesIncorrect);
        }

        @Test
        @DisplayName("Should handle unicode characters correctly")
        void matches_UnicodeCharacters() {
            // Arrange
            String unicodePassword = "пароль密码パスワード🔐";
            String unicodePasswordHash = passwordService.encryptPassword(unicodePassword);

            // Act
            boolean matchesCorrect = passwordService.matches(unicodePassword, unicodePasswordHash);
            boolean matchesIncorrect = passwordService.matches("wrong密码", unicodePasswordHash);

            // Assert
            assertTrue(matchesCorrect);
            assertFalse(matchesIncorrect);
        }

        @Test
        @DisplayName("Should handle reasonable length passwords")
        void matches_ReasonableLengthPassword() {
            // Arrange - Use a reasonable length password (72 chars is BCrypt limit)
            String reasonablePassword = "a".repeat(50);
            String reasonablePasswordHash = passwordService.encryptPassword(reasonablePassword);

            // Act
            boolean matchesCorrect = passwordService.matches(reasonablePassword, reasonablePasswordHash);
            boolean matchesIncorrect = passwordService.matches("a".repeat(49), reasonablePasswordHash);

            // Assert
            assertTrue(matchesCorrect);
            assertFalse(matchesIncorrect);
        }

        @Test
        @DisplayName("Should handle whitespace in passwords")
        void matches_WhitespaceInPasswords() {
            // Arrange
            String passwordWithSpaces = " password with spaces ";
            String spacesPasswordHash = passwordService.encryptPassword(passwordWithSpaces);

            // Act
            boolean matchesExact = passwordService.matches(passwordWithSpaces, spacesPasswordHash);
            boolean matchesTrimmed = passwordService.matches("password with spaces", spacesPasswordHash);

            // Assert
            assertTrue(matchesExact);
            assertFalse(matchesTrimmed, "Whitespace should be preserved in password matching");
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should encrypt and verify multiple passwords correctly")
        void encryptAndVerifyMultiplePasswords() {
            // Arrange
            String[] passwords = {
                "password1",
                "password2",
                "differentPassword",
                "p@$$w0rd!",
                "",
                "very_long_password_with_many_characters_123456789"
            };

            // Act & Assert
            for (String password : passwords) {
                String hash = passwordService.encryptPassword(password);
                assertTrue(passwordService.matches(password, hash), 
                    "Password '" + password + "' should match its hash");
                
                // Test that other passwords don't match
                for (String otherPassword : passwords) {
                    if (!password.equals(otherPassword)) {
                        assertFalse(passwordService.matches(otherPassword, hash),
                            "Password '" + otherPassword + "' should not match hash of '" + password + "'");
                    }
                }
            }
        }

        @Test
        @DisplayName("Should maintain security properties across multiple operations")
        void securityPropertiesConsistency() {
            // Arrange
            String password = "securityTestPassword123!";

            // Act - Generate multiple hashes
            String hash1 = passwordService.encryptPassword(password);
            String hash2 = passwordService.encryptPassword(password);
            String hash3 = passwordService.encryptPassword(password);

            // Assert - All hashes should be different but all should match the original password
            assertNotEquals(hash1, hash2);
            assertNotEquals(hash2, hash3);
            assertNotEquals(hash1, hash3);

            assertTrue(passwordService.matches(password, hash1));
            assertTrue(passwordService.matches(password, hash2));
            assertTrue(passwordService.matches(password, hash3));
        }

        @Test
        @DisplayName("Should handle concurrent encryption operations")
        void concurrentEncryptionOperations() {
            // Arrange
            String password = "concurrentTestPassword";
            int numberOfThreads = 10;
            String[] hashes = new String[numberOfThreads];
            Thread[] threads = new Thread[numberOfThreads];

            // Act - Create multiple threads that encrypt the same password
            for (int i = 0; i < numberOfThreads; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    hashes[index] = passwordService.encryptPassword(password);
                });
                threads[i].start();
            }

            // Wait for all threads to complete
            for (Thread thread : threads) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    fail("Thread was interrupted");
                }
            }

            // Assert - All hashes should be valid and different
            for (int i = 0; i < numberOfThreads; i++) {
                assertNotNull(hashes[i], "Hash " + i + " should not be null");
                assertTrue(passwordService.matches(password, hashes[i]), 
                    "Hash " + i + " should match the original password");
                
                // Ensure all hashes are different
                for (int j = i + 1; j < numberOfThreads; j++) {
                    assertNotEquals(hashes[i], hashes[j], 
                        "Hash " + i + " should be different from hash " + j);
                }
            }
        }
    }
}