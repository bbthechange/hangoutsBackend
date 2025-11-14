package com.bbthechange.inviter.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for User model
 *
 * Test Coverage:
 * - Constructor behavior for creationDate field
 * - Placeholder user creation (null password)
 * - Full user creation (with password)
 */
@DisplayName("User Model Tests")
class UserTest {

    @Test
    @DisplayName("Should set creationDate to null for placeholder users (password null)")
    void constructor_PlaceholderUser_CreationDateIsNull() {
        // Act - Create placeholder user (password is null)
        User placeholderUser = new User("+15551234567", null, null);

        // Assert
        assertNull(placeholderUser.getCreationDate(),
            "Placeholder users should have null creationDate until they register");
        assertEquals("+15551234567", placeholderUser.getPhoneNumber());
        assertNull(placeholderUser.getPassword());
        assertEquals(AccountStatus.UNVERIFIED, placeholderUser.getAccountStatus());
    }

    @Test
    @DisplayName("Should set creationDate for full users with password (3-arg constructor)")
    void constructor_FullUser_CreationDateIsSet() {
        // Act - Create full user with password
        User fullUser = new User("+15551234567", "testuser", "hashedpassword");

        // Assert
        assertNotNull(fullUser.getCreationDate(),
            "Full users with password should have creationDate set");
        assertNotNull(fullUser.getPassword());
        assertEquals(AccountStatus.UNVERIFIED, fullUser.getAccountStatus());
    }

    @Test
    @DisplayName("Should set creationDate for full users with password (4-arg constructor)")
    void constructor_FullUserWithDisplayName_CreationDateIsSet() {
        // Act - Create full user with password and display name
        User fullUser = new User("+15551234567", "testuser", "Test User", "hashedpassword");

        // Assert
        assertNotNull(fullUser.getCreationDate(),
            "Full users with password should have creationDate set");
        assertEquals("Test User", fullUser.getDisplayName());
        assertNotNull(fullUser.getPassword());
        assertEquals(AccountStatus.UNVERIFIED, fullUser.getAccountStatus());
    }

    @Test
    @DisplayName("Should set creationDate close to current time for full users")
    void constructor_FullUser_CreationDateIsRecent() {
        // Arrange
        Instant before = Instant.now();

        // Act
        User fullUser = new User("+15551234567", "testuser", "hashedpassword");

        // Arrange
        Instant after = Instant.now();

        // Assert
        assertNotNull(fullUser.getCreationDate());
        assertFalse(fullUser.getCreationDate().isBefore(before),
            "CreationDate should not be before user creation");
        assertFalse(fullUser.getCreationDate().isAfter(after),
            "CreationDate should not be after user creation");
    }

    @Test
    @DisplayName("Should set creationDate to null for placeholder user (4-arg constructor)")
    void constructor_PlaceholderUserWith4Args_CreationDateIsNull() {
        // Act - Create placeholder user with display name but no password
        User placeholderUser = new User("+15551234567", null, "Display Name", null);

        // Assert
        assertNull(placeholderUser.getCreationDate(),
            "Placeholder users should have null creationDate even with display name");
        assertEquals("Display Name", placeholderUser.getDisplayName());
        assertNull(placeholderUser.getPassword());
    }

    @Test
    @DisplayName("Should initialize other fields correctly for placeholder users")
    void constructor_PlaceholderUser_InitializesOtherFields() {
        // Act
        User placeholderUser = new User("+15551234567", null, null);

        // Assert
        assertNotNull(placeholderUser.getId(), "ID should be generated");
        assertEquals("+15551234567", placeholderUser.getPhoneNumber());
        assertNull(placeholderUser.getUsername());
        assertNull(placeholderUser.getDisplayName());
        assertNull(placeholderUser.getPassword());
        assertEquals(AccountStatus.UNVERIFIED, placeholderUser.getAccountStatus());
        assertFalse(placeholderUser.getIsTestAccount());
        assertNull(placeholderUser.getCreationDate());
    }

    @Test
    @DisplayName("Should initialize all fields correctly for full users")
    void constructor_FullUser_InitializesAllFields() {
        // Act
        User fullUser = new User("+15551234567", "testuser", "Test User", "hashedpassword");

        // Assert
        assertNotNull(fullUser.getId(), "ID should be generated");
        assertEquals("+15551234567", fullUser.getPhoneNumber());
        assertEquals("testuser", fullUser.getUsername());
        assertEquals("Test User", fullUser.getDisplayName());
        assertEquals("hashedpassword", fullUser.getPassword());
        assertEquals(AccountStatus.UNVERIFIED, fullUser.getAccountStatus());
        assertFalse(fullUser.getIsTestAccount());
        assertNotNull(fullUser.getCreationDate());
    }
}
