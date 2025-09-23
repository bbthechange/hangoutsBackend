package com.bbthechange.inviter.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AccountStatus enum
 * 
 * Test Coverage:
 * - Enum values and ordering
 * - String parsing functionality
 * - Error handling for invalid values
 */
@DisplayName("AccountStatus Enum Tests")
class AccountStatusTest {

    @Test
    @DisplayName("Should have correct values with expected ordinals")
    void accountStatus_HasCorrectValues_WithExpectedOrdinals() {
        // Assert enum values exist and have correct ordinals
        assertEquals(0, AccountStatus.UNVERIFIED.ordinal());
        assertEquals(1, AccountStatus.ACTIVE.ordinal());
        
        // Verify only these two values exist
        AccountStatus[] values = AccountStatus.values();
        assertEquals(2, values.length);
        assertEquals(AccountStatus.UNVERIFIED, values[0]);
        assertEquals(AccountStatus.ACTIVE, values[1]);
    }

    @Test
    @DisplayName("Should parse string values correctly with valueOf")
    void accountStatus_ValueOf_WorksForBothValues() {
        // Test string parsing for both valid values
        assertEquals(AccountStatus.UNVERIFIED, AccountStatus.valueOf("UNVERIFIED"));
        assertEquals(AccountStatus.ACTIVE, AccountStatus.valueOf("ACTIVE"));
        
        // Verify the parsed values have correct properties
        assertTrue(AccountStatus.valueOf("UNVERIFIED") == AccountStatus.UNVERIFIED);
        assertTrue(AccountStatus.valueOf("ACTIVE") == AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for invalid values")
    void accountStatus_ValueOf_ThrowsForInvalidValues() {
        // Test various invalid string values
        assertThrows(IllegalArgumentException.class, () -> AccountStatus.valueOf("INVALID"));
        assertThrows(IllegalArgumentException.class, () -> AccountStatus.valueOf("unverified"));
        assertThrows(IllegalArgumentException.class, () -> AccountStatus.valueOf("active"));
        assertThrows(IllegalArgumentException.class, () -> AccountStatus.valueOf(""));
        assertThrows(IllegalArgumentException.class, () -> AccountStatus.valueOf("PENDING"));
        assertThrows(IllegalArgumentException.class, () -> AccountStatus.valueOf("VERIFIED"));
    }

    @Test
    @DisplayName("Should throw NullPointerException for null value")
    void accountStatus_ValueOf_ThrowsForNullValue() {
        // Verify null handling
        assertThrows(NullPointerException.class, () -> AccountStatus.valueOf(null));
    }

    @Test
    @DisplayName("Should have correct string representation")
    void accountStatus_ToString_ReturnsCorrectValues() {
        // Verify toString() returns the enum name
        assertEquals("UNVERIFIED", AccountStatus.UNVERIFIED.toString());
        assertEquals("ACTIVE", AccountStatus.ACTIVE.toString());
        
        // Verify name() method works the same as toString() for enums
        assertEquals("UNVERIFIED", AccountStatus.UNVERIFIED.name());
        assertEquals("ACTIVE", AccountStatus.ACTIVE.name());
    }

    @Test
    @DisplayName("Should support equality and comparison operations")
    void accountStatus_EqualityAndComparison() {
        // Test equality
        assertEquals(AccountStatus.UNVERIFIED, AccountStatus.UNVERIFIED);
        assertEquals(AccountStatus.ACTIVE, AccountStatus.ACTIVE);
        assertNotEquals(AccountStatus.UNVERIFIED, AccountStatus.ACTIVE);
        assertNotEquals(AccountStatus.ACTIVE, AccountStatus.UNVERIFIED);
        
        // Test comparison based on ordinal values
        assertTrue(AccountStatus.UNVERIFIED.compareTo(AccountStatus.ACTIVE) < 0);
        assertTrue(AccountStatus.ACTIVE.compareTo(AccountStatus.UNVERIFIED) > 0);
        assertEquals(0, AccountStatus.UNVERIFIED.compareTo(AccountStatus.UNVERIFIED));
        assertEquals(0, AccountStatus.ACTIVE.compareTo(AccountStatus.ACTIVE));
    }

    @Test
    @DisplayName("Should support switch statement usage")
    void accountStatus_SwitchStatement_WorksCorrectly() {
        // Test enum works in switch statements
        String unverifiedResult = getStatusDescription(AccountStatus.UNVERIFIED);
        String activeResult = getStatusDescription(AccountStatus.ACTIVE);
        
        assertEquals("Account is not yet verified", unverifiedResult);
        assertEquals("Account is active and verified", activeResult);
    }

    // Helper method to test switch statement functionality
    private String getStatusDescription(AccountStatus status) {
        switch (status) {
            case UNVERIFIED:
                return "Account is not yet verified";
            case ACTIVE:
                return "Account is active and verified";
            default:
                return "Unknown status";
        }
    }
}