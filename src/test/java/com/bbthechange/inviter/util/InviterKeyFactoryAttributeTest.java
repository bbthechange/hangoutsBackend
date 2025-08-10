package com.bbthechange.inviter.util;

import com.bbthechange.inviter.exception.InvalidKeyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;

class InviterKeyFactoryAttributeTest {
    
    private final String validAttributeId = UUID.randomUUID().toString();
    
    @Test
    void getAttributeSk_WithValidUUID_ShouldCreateCorrectSortKey() {
        // When
        String sortKey = InviterKeyFactory.getAttributeSk(validAttributeId);
        
        // Then
        assertEquals("ATTRIBUTE#" + validAttributeId, sortKey);
    }
    
    @Test
    void getAttributeSk_WithValidUUIDUppercase_ShouldAcceptUppercase() {
        // Given
        String uppercaseUUID = validAttributeId.toUpperCase();
        
        // When
        String sortKey = InviterKeyFactory.getAttributeSk(uppercaseUUID);
        
        // Then
        assertEquals("ATTRIBUTE#" + uppercaseUUID, sortKey);
    }
    
    @Test
    void getAttributeSk_WithValidUUIDLowercase_ShouldAcceptLowercase() {
        // Given
        String lowercaseUUID = validAttributeId.toLowerCase();
        
        // When
        String sortKey = InviterKeyFactory.getAttributeSk(lowercaseUUID);
        
        // Then
        assertEquals("ATTRIBUTE#" + lowercaseUUID, sortKey);
    }
    
    @Test
    void getAttributeSk_WithNullId_ShouldThrowInvalidKeyException() {
        // When & Then
        InvalidKeyException exception = assertThrows(
            InvalidKeyException.class,
            () -> InviterKeyFactory.getAttributeSk(null)
        );
        assertTrue(exception.getMessage().contains("Attribute ID cannot be null or empty"));
    }
    
    @Test
    void getAttributeSk_WithEmptyId_ShouldThrowInvalidKeyException() {
        // When & Then
        InvalidKeyException exception = assertThrows(
            InvalidKeyException.class,
            () -> InviterKeyFactory.getAttributeSk("")
        );
        assertTrue(exception.getMessage().contains("Attribute ID cannot be null or empty"));
    }
    
    @Test
    void getAttributeSk_WithWhitespaceOnlyId_ShouldThrowInvalidKeyException() {
        // When & Then
        InvalidKeyException exception = assertThrows(
            InvalidKeyException.class,
            () -> InviterKeyFactory.getAttributeSk("   ")
        );
        assertTrue(exception.getMessage().contains("Attribute ID cannot be null or empty"));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {
        "not-a-uuid",
        "12345678-1234-1234-1234", // Too short
        "12345678-1234-1234-1234-12345678901", // Too long
        "12345678_1234_1234_1234_123456789012", // Wrong separators
        "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx", // Invalid characters
        "12345678-1234-1234-1234-12345678901z" // Invalid character at end
    })
    void getAttributeSk_WithInvalidUUIDFormats_ShouldThrowInvalidKeyException(String invalidUUID) {
        // When & Then
        InvalidKeyException exception = assertThrows(
            InvalidKeyException.class,
            () -> InviterKeyFactory.getAttributeSk(invalidUUID)
        );
        assertTrue(exception.getMessage().contains("Invalid Attribute ID format"));
    }
    
    @Test
    void isAttributeItem_WithValidAttributeSortKey_ShouldReturnTrue() {
        // Given
        String attributeSk = InviterKeyFactory.getAttributeSk(validAttributeId);
        
        // When & Then
        assertTrue(InviterKeyFactory.isAttributeItem(attributeSk));
    }
    
    @Test
    void isAttributeItem_WithMetadataSortKey_ShouldReturnFalse() {
        // Given
        String metadataSk = InviterKeyFactory.getMetadataSk();
        
        // When & Then
        assertFalse(InviterKeyFactory.isAttributeItem(metadataSk));
    }
    
    @Test
    void isAttributeItem_WithOtherPrefixSortKey_ShouldReturnFalse() {
        // Given
        String[] otherSortKeys = {
            InviterKeyFactory.getHangoutSk(validAttributeId),
            InviterKeyFactory.getPollSk(validAttributeId),
            InviterKeyFactory.getCarSk(validAttributeId),
            InviterKeyFactory.getInviteSk(validAttributeId),
            InviterKeyFactory.getAttendanceSk(validAttributeId)
        };
        
        // When & Then
        for (String sortKey : otherSortKeys) {
            assertFalse(InviterKeyFactory.isAttributeItem(sortKey), 
                "Should return false for sort key: " + sortKey);
        }
    }
    
    @Test
    void isAttributeItem_WithNullSortKey_ShouldReturnFalse() {
        // When & Then
        assertFalse(InviterKeyFactory.isAttributeItem(null));
    }
    
    @Test
    void isAttributeItem_WithEmptySortKey_ShouldReturnFalse() {
        // When & Then
        assertFalse(InviterKeyFactory.isAttributeItem(""));
    }
    
    @Test
    void isAttributeItem_WithPartialMatch_ShouldReturnFalse() {
        // Given - Sort key that contains ATTRIBUTE but doesn't start with it
        String partialMatch = "SOME_ATTRIBUTE#" + validAttributeId;
        
        // When & Then
        assertFalse(InviterKeyFactory.isAttributeItem(partialMatch));
    }
    
    @Test
    void attributePrefix_ShouldBeCorrectConstant() {
        // When & Then
        assertEquals("ATTRIBUTE", InviterKeyFactory.ATTRIBUTE_PREFIX);
    }
    
    @Test
    void getAttributeSk_WithMultipleValidUUIDs_ShouldCreateUniqueSortKeys() {
        // Given
        String uuid1 = UUID.randomUUID().toString();
        String uuid2 = UUID.randomUUID().toString();
        String uuid3 = UUID.randomUUID().toString();
        
        // When
        String sk1 = InviterKeyFactory.getAttributeSk(uuid1);
        String sk2 = InviterKeyFactory.getAttributeSk(uuid2);
        String sk3 = InviterKeyFactory.getAttributeSk(uuid3);
        
        // Then
        assertNotEquals(sk1, sk2);
        assertNotEquals(sk1, sk3);
        assertNotEquals(sk2, sk3);
        
        // All should start with ATTRIBUTE#
        assertTrue(sk1.startsWith("ATTRIBUTE#"));
        assertTrue(sk2.startsWith("ATTRIBUTE#"));
        assertTrue(sk3.startsWith("ATTRIBUTE#"));
    }
    
    @Test
    void attributeConstant_ShouldBeAvailableForModelUsage() {
        // When & Then
        // Verify the constant is accessible for model classes to use in setItemType()
        assertNotNull(InviterKeyFactory.ATTRIBUTE_PREFIX);
        assertEquals("ATTRIBUTE", InviterKeyFactory.ATTRIBUTE_PREFIX);
    }
    
    @Test
    void getAttributeSk_ShouldBeConsistentAcrossMultipleCalls() {
        // When
        String sk1 = InviterKeyFactory.getAttributeSk(validAttributeId);
        String sk2 = InviterKeyFactory.getAttributeSk(validAttributeId);
        
        // Then
        assertEquals(sk1, sk2); // Same input should produce same output
    }
}