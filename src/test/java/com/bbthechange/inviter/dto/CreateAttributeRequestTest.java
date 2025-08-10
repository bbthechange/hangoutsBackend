package com.bbthechange.inviter.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class CreateAttributeRequestTest {
    
    private final String validAttributeName = "dress_code";
    private final String validStringValue = "cocktail attire";
    
    @Test
    void constructor_WithValidInputs_ShouldCreateValidRequest() {
        // When
        CreateAttributeRequest request = new CreateAttributeRequest(validAttributeName, validStringValue);
        
        // Then
        assertEquals(validAttributeName, request.getAttributeName());
        assertEquals(validStringValue, request.getStringValue());
    }
    
    @Test
    void constructor_WithNullStringValue_ShouldAllowNull() {
        // When
        CreateAttributeRequest request = new CreateAttributeRequest(validAttributeName, null);
        
        // Then
        assertEquals(validAttributeName, request.getAttributeName());
        assertNull(request.getStringValue());
    }
    
    @Test
    void isValid_WithValidRequest_ShouldReturnTrue() {
        // Given
        CreateAttributeRequest request = new CreateAttributeRequest(validAttributeName, validStringValue);
        
        // When & Then
        assertTrue(request.isValid());
    }
    
    @Test
    void isValid_WithNullAttributeName_ShouldReturnTrue() {
        // Given - validation annotations should handle null check
        CreateAttributeRequest request = new CreateAttributeRequest(null, validStringValue);
        
        // When & Then
        // Note: Annotation validation happens at controller level, this method focuses on business rules
        assertTrue(request.isValid());
    }
    
    @Test
    void isValid_WithEmptyAttributeName_ShouldReturnFalse() {
        // Given
        CreateAttributeRequest request = new CreateAttributeRequest("", validStringValue);
        
        // When & Then
        assertFalse(request.isValid());
    }
    
    @Test
    void isValid_WithWhitespaceOnlyAttributeName_ShouldReturnFalse() {
        // Given
        CreateAttributeRequest request = new CreateAttributeRequest("   ", validStringValue);
        
        // When & Then
        assertFalse(request.isValid());
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"id", "type", "system", "internal", "pk", "sk", "gsi1pk", "gsi1sk", "system_reserved", "internal_field"})
    void isValid_WithReservedAttributeNames_ShouldReturnFalse(String reservedName) {
        // Given
        CreateAttributeRequest request = new CreateAttributeRequest(reservedName, validStringValue);
        
        // When & Then
        assertFalse(request.isValid());
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"ID", "TYPE", "SYSTEM", "INTERNAL", "PK", "SK"}) // Test uppercase variants
    void isValid_WithReservedAttributeNamesUppercase_ShouldReturnFalse(String reservedName) {
        // Given
        CreateAttributeRequest request = new CreateAttributeRequest(reservedName, validStringValue);
        
        // When & Then
        assertFalse(request.isValid());
    }
    
    @Test
    void isValid_WithValidNonReservedNames_ShouldReturnTrue() {
        // Given
        String[] validNames = {
            "dress_code",
            "bring_item", 
            "parking_info",
            "special_instructions",
            "contact_person",
            "备注", // Chinese characters
            "parking_🚗", // With emoji
            "dress-code", // With dash
            "item123" // With numbers
        };
        
        // When & Then
        for (String name : validNames) {
            CreateAttributeRequest request = new CreateAttributeRequest(name, validStringValue);
            assertTrue(request.isValid(), "Should be valid for name: " + name);
        }
    }
    
    @Test
    void getTrimmedAttributeName_WithWhitespace_ShouldReturnTrimmed() {
        // Given
        CreateAttributeRequest request = new CreateAttributeRequest("  dress_code  ", validStringValue);
        
        // When
        String trimmed = request.getTrimmedAttributeName();
        
        // Then
        assertEquals("dress_code", trimmed);
    }
    
    @Test
    void getTrimmedAttributeName_WithNullName_ShouldReturnNull() {
        // Given
        CreateAttributeRequest request = new CreateAttributeRequest(null, validStringValue);
        
        // When
        String trimmed = request.getTrimmedAttributeName();
        
        // Then
        assertNull(trimmed);
    }
    
    @Test
    void toString_ShouldContainAttributeNameButTruncateValue() {
        // Given
        String longValue = "a".repeat(100);
        CreateAttributeRequest request = new CreateAttributeRequest(validAttributeName, longValue);
        
        // When
        String result = request.toString();
        
        // Then
        assertTrue(result.contains(validAttributeName));
        assertTrue(result.contains("...")); // Should truncate long values
        assertFalse(result.contains(longValue)); // Full value should not be in toString
    }
    
    @Test
    void toString_WithNullStringValue_ShouldHandleGracefully() {
        // Given
        CreateAttributeRequest request = new CreateAttributeRequest(validAttributeName, null);
        
        // When
        String result = request.toString();
        
        // Then
        assertTrue(result.contains(validAttributeName));
        assertDoesNotThrow(() -> request.toString()); // Should not throw NPE
    }
    
    @Test
    void setters_ShouldUpdateFields() {
        // Given
        CreateAttributeRequest request = new CreateAttributeRequest();
        
        // When
        request.setAttributeName(validAttributeName);
        request.setStringValue(validStringValue);
        
        // Then
        assertEquals(validAttributeName, request.getAttributeName());
        assertEquals(validStringValue, request.getStringValue());
    }
    
    @Test
    void defaultConstructor_ShouldCreateEmptyRequest() {
        // When
        CreateAttributeRequest request = new CreateAttributeRequest();
        
        // Then
        assertNull(request.getAttributeName());
        assertNull(request.getStringValue());
    }
    
    @Test
    void isValid_WithUnicodeAttributeName_ShouldReturnTrue() {
        // Given - Test various Unicode scenarios
        String[] unicodeNames = {
            "停车信息", // Chinese
            "parking_🚗", // Emoji
            "café_info", // Accented characters  
            "информация", // Cyrillic
            "駐車場情報" // Japanese
        };
        
        // When & Then
        for (String name : unicodeNames) {
            CreateAttributeRequest request = new CreateAttributeRequest(name, validStringValue);
            assertTrue(request.isValid(), "Should be valid for Unicode name: " + name);
        }
    }
}