package com.bbthechange.inviter.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class UpdateAttributeRequestTest {
    
    private final String validAttributeName = "dress_code";
    private final String validStringValue = "cocktail attire";
    
    @Test
    void constructor_WithValidInputs_ShouldCreateValidRequest() {
        // When
        UpdateAttributeRequest request = new UpdateAttributeRequest(validAttributeName, validStringValue);
        
        // Then
        assertEquals(validAttributeName, request.getAttributeName());
        assertEquals(validStringValue, request.getStringValue());
    }
    
    @Test
    void constructor_WithEmptyStringValue_ShouldAllowEmptyString() {
        // When
        UpdateAttributeRequest request = new UpdateAttributeRequest(validAttributeName, "");
        
        // Then
        assertEquals(validAttributeName, request.getAttributeName());
        assertEquals("", request.getStringValue());
    }
    
    @Test
    void isValid_WithValidRequest_ShouldReturnTrue() {
        // Given
        UpdateAttributeRequest request = new UpdateAttributeRequest(validAttributeName, validStringValue);
        
        // When & Then
        assertTrue(request.isValid());
    }
    
    @Test
    void isValid_WithNullAttributeName_ShouldReturnTrue() {
        // Given - validation annotations should handle null check
        UpdateAttributeRequest request = new UpdateAttributeRequest(null, validStringValue);
        
        // When & Then
        // Note: Annotation validation happens at controller level, this method focuses on business rules
        assertTrue(request.isValid());
    }
    
    @Test
    void isValid_WithEmptyAttributeName_ShouldReturnFalse() {
        // Given
        UpdateAttributeRequest request = new UpdateAttributeRequest("", validStringValue);
        
        // When & Then
        assertFalse(request.isValid());
    }
    
    @Test
    void isValid_WithWhitespaceOnlyAttributeName_ShouldReturnFalse() {
        // Given
        UpdateAttributeRequest request = new UpdateAttributeRequest("   ", validStringValue);
        
        // When & Then
        assertFalse(request.isValid());
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"id", "type", "system", "internal", "pk", "sk", "gsi1pk", "gsi1sk", "system_reserved", "internal_field"})
    void isValid_WithReservedAttributeNames_ShouldReturnFalse(String reservedName) {
        // Given
        UpdateAttributeRequest request = new UpdateAttributeRequest(reservedName, validStringValue);
        
        // When & Then
        assertFalse(request.isValid());
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"ID", "TYPE", "SYSTEM", "INTERNAL", "PK", "SK"}) // Test uppercase variants
    void isValid_WithReservedAttributeNamesUppercase_ShouldReturnFalse(String reservedName) {
        // Given
        UpdateAttributeRequest request = new UpdateAttributeRequest(reservedName, validStringValue);
        
        // When & Then
        assertFalse(request.isValid());
    }
    
    @Test
    void getTrimmedAttributeName_WithWhitespace_ShouldReturnTrimmed() {
        // Given
        UpdateAttributeRequest request = new UpdateAttributeRequest("  dress_code  ", validStringValue);
        
        // When
        String trimmed = request.getTrimmedAttributeName();
        
        // Then
        assertEquals("dress_code", trimmed);
    }
    
    @Test
    void getTrimmedAttributeName_WithNullName_ShouldReturnNull() {
        // Given
        UpdateAttributeRequest request = new UpdateAttributeRequest(null, validStringValue);
        
        // When
        String trimmed = request.getTrimmedAttributeName();
        
        // Then
        assertNull(trimmed);
    }
    
    @Test
    void isNameChange_WithSameName_ShouldReturnFalse() {
        // Given
        UpdateAttributeRequest request = new UpdateAttributeRequest(validAttributeName, validStringValue);
        
        // When & Then
        assertFalse(request.isNameChange(validAttributeName));
    }
    
    @Test
    void isNameChange_WithDifferentName_ShouldReturnTrue() {
        // Given
        String newName = "parking_info";
        UpdateAttributeRequest request = new UpdateAttributeRequest(newName, validStringValue);
        
        // When & Then
        assertTrue(request.isNameChange(validAttributeName));
    }
    
    @Test
    void isNameChange_WithWhitespaceButSameCore_ShouldReturnFalse() {
        // Given
        UpdateAttributeRequest request = new UpdateAttributeRequest("  dress_code  ", validStringValue);
        
        // When & Then
        assertFalse(request.isNameChange(validAttributeName)); // Should trim before comparing
    }
    
    @Test
    void isNameChange_WithNullCurrentName_ShouldHandleGracefully() {
        // Given
        UpdateAttributeRequest request = new UpdateAttributeRequest(validAttributeName, validStringValue);
        
        // When & Then
        assertTrue(request.isNameChange(null)); // New name is not null, so it's a change
    }
    
    @Test
    void isNameChange_WithNullNewName_ShouldHandleGracefully() {
        // Given
        UpdateAttributeRequest request = new UpdateAttributeRequest(null, validStringValue);
        
        // When & Then
        assertFalse(request.isNameChange(validAttributeName)); // New name is null, so no change
    }
    
    @Test
    void isNameChange_WithBothNullNames_ShouldReturnFalse() {
        // Given
        UpdateAttributeRequest request = new UpdateAttributeRequest(null, validStringValue);
        
        // When & Then
        assertFalse(request.isNameChange(null)); // Both null, no change
    }
    
    @Test
    void toString_ShouldContainAttributeNameButTruncateValue() {
        // Given
        String longValue = "a".repeat(100);
        UpdateAttributeRequest request = new UpdateAttributeRequest(validAttributeName, longValue);
        
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
        UpdateAttributeRequest request = new UpdateAttributeRequest(validAttributeName, null);
        
        // When
        String result = request.toString();
        
        // Then
        assertTrue(result.contains(validAttributeName));
        assertDoesNotThrow(() -> request.toString()); // Should not throw NPE
    }
    
    @Test
    void setters_ShouldUpdateFields() {
        // Given
        UpdateAttributeRequest request = new UpdateAttributeRequest();
        
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
        UpdateAttributeRequest request = new UpdateAttributeRequest();
        
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
            UpdateAttributeRequest request = new UpdateAttributeRequest(name, validStringValue);
            assertTrue(request.isValid(), "Should be valid for Unicode name: " + name);
        }
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
            UpdateAttributeRequest request = new UpdateAttributeRequest(name, validStringValue);
            assertTrue(request.isValid(), "Should be valid for name: " + name);
        }
    }
}