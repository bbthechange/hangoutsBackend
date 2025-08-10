package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InviterKeyFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.UUID;

class HangoutAttributeTest {
    
    private String validHangoutId;
    private String validAttributeId;
    private String validAttributeName;
    private String validStringValue;
    
    @BeforeEach
    void setUp() {
        validHangoutId = UUID.randomUUID().toString();
        validAttributeId = UUID.randomUUID().toString();
        validAttributeName = "dress_code";
        validStringValue = "cocktail attire";
    }
    
    @Test
    void constructor_WithValidInputs_ShouldCreateValidAttribute() {
        // When
        HangoutAttribute attribute = new HangoutAttribute(validHangoutId, validAttributeName, validStringValue);
        
        // Then
        assertNotNull(attribute.getAttributeId());
        assertEquals(validHangoutId, attribute.getHangoutId());
        assertEquals(validAttributeName, attribute.getAttributeName());
        assertEquals(validStringValue, attribute.getStringValue());
        assertNotNull(attribute.getCreatedAt());
        assertNotNull(attribute.getUpdatedAt());
        assertEquals(InviterKeyFactory.ATTRIBUTE_PREFIX, attribute.getItemType());
        
        // Verify keys are set correctly
        assertEquals(InviterKeyFactory.getEventPk(validHangoutId), attribute.getPk());
        assertEquals(InviterKeyFactory.getAttributeSk(attribute.getAttributeId()), attribute.getSk());
    }
    
    @Test
    void constructor_WithSpecificAttributeId_ShouldUseProvidedId() {
        // When
        HangoutAttribute attribute = new HangoutAttribute(validHangoutId, validAttributeId, validAttributeName, validStringValue);
        
        // Then
        assertEquals(validAttributeId, attribute.getAttributeId());
        assertEquals(validHangoutId, attribute.getHangoutId());
        assertEquals(validAttributeName, attribute.getAttributeName());
        assertEquals(validStringValue, attribute.getStringValue());
        
        // Verify keys are set correctly with provided ID
        assertEquals(InviterKeyFactory.getEventPk(validHangoutId), attribute.getPk());
        assertEquals(InviterKeyFactory.getAttributeSk(validAttributeId), attribute.getSk());
    }
    
    @Test
    void constructor_WithNullStringValue_ShouldAllowNullValue() {
        // When
        HangoutAttribute attribute = new HangoutAttribute(validHangoutId, validAttributeName, null);
        
        // Then
        assertEquals(validHangoutId, attribute.getHangoutId());
        assertEquals(validAttributeName, attribute.getAttributeName());
        assertNull(attribute.getStringValue());
        assertTrue(attribute.isValid()); // Null value should be valid
    }
    
    @Test
    void constructor_WithEmptyStringValue_ShouldAllowEmptyValue() {
        // When
        HangoutAttribute attribute = new HangoutAttribute(validHangoutId, validAttributeName, "");
        
        // Then
        assertEquals(validHangoutId, attribute.getHangoutId());
        assertEquals(validAttributeName, attribute.getAttributeName());
        assertEquals("", attribute.getStringValue());
        assertTrue(attribute.isValid());
    }
    
    @Test
    void setAttributeName_WithValidName_ShouldUpdateNameAndTimestamp() throws InterruptedException {
        // Given
        HangoutAttribute attribute = new HangoutAttribute(validHangoutId, validAttributeName, validStringValue);
        Instant originalTimestamp = attribute.getUpdatedAt();
        Thread.sleep(1); // Ensure timestamp difference
        
        // When
        String newName = "parking_info";
        attribute.setAttributeName(newName);
        
        // Then
        assertEquals(newName, attribute.getAttributeName());
        assertTrue(attribute.getUpdatedAt().isAfter(originalTimestamp));
    }
    
    @Test
    void setStringValue_WithValidValue_ShouldUpdateValueAndTimestamp() throws InterruptedException {
        // Given
        HangoutAttribute attribute = new HangoutAttribute(validHangoutId, validAttributeName, validStringValue);
        Instant originalTimestamp = attribute.getUpdatedAt();
        Thread.sleep(1); // Ensure timestamp difference
        
        // When
        String newValue = "business casual";
        attribute.setStringValue(newValue);
        
        // Then
        assertEquals(newValue, attribute.getStringValue());
        assertTrue(attribute.getUpdatedAt().isAfter(originalTimestamp));
    }
    
    @Test
    void updateAttribute_WithBothNameAndValue_ShouldUpdateBothFields() throws InterruptedException {
        // Given
        HangoutAttribute attribute = new HangoutAttribute(validHangoutId, validAttributeName, validStringValue);
        Instant originalTimestamp = attribute.getUpdatedAt();
        Thread.sleep(1); // Ensure timestamp difference
        
        // When
        String newName = "parking_info";
        String newValue = "Free parking in lot B";
        attribute.updateAttribute(newName, newValue);
        
        // Then
        assertEquals(newName, attribute.getAttributeName());
        assertEquals(newValue, attribute.getStringValue());
        assertTrue(attribute.getUpdatedAt().isAfter(originalTimestamp));
    }
    
    @Test
    void isValid_WithValidAttribute_ShouldReturnTrue() {
        // Given
        HangoutAttribute attribute = new HangoutAttribute(validHangoutId, validAttributeName, validStringValue);
        
        // When & Then
        assertTrue(attribute.isValid());
    }
    
    @Test
    void isValid_WithNullAttributeName_ShouldReturnFalse() {
        // Given
        HangoutAttribute attribute = new HangoutAttribute(validHangoutId, validAttributeName, validStringValue);
        attribute.setAttributeName(null);
        
        // When & Then
        assertFalse(attribute.isValid());
    }
    
    @Test
    void isValid_WithEmptyAttributeName_ShouldReturnFalse() {
        // Given
        HangoutAttribute attribute = new HangoutAttribute(validHangoutId, validAttributeName, validStringValue);
        attribute.setAttributeName("");
        
        // When & Then
        assertFalse(attribute.isValid());
    }
    
    @Test
    void isValid_WithWhitespaceOnlyAttributeName_ShouldReturnFalse() {
        // Given
        HangoutAttribute attribute = new HangoutAttribute(validHangoutId, validAttributeName, validStringValue);
        attribute.setAttributeName("   ");
        
        // When & Then
        assertFalse(attribute.isValid());
    }
    
    @Test
    void isValid_WithAttributeNameTooLong_ShouldReturnFalse() {
        // Given
        String longName = "a".repeat(101); // 101 characters
        HangoutAttribute attribute = new HangoutAttribute(validHangoutId, validAttributeName, validStringValue);
        attribute.setAttributeName(longName);
        
        // When & Then
        assertFalse(attribute.isValid());
    }
    
    @Test
    void isValid_WithAttributeNameExactlyMaxLength_ShouldReturnTrue() {
        // Given
        String maxLengthName = "a".repeat(100); // Exactly 100 characters
        HangoutAttribute attribute = new HangoutAttribute(validHangoutId, validAttributeName, validStringValue);
        attribute.setAttributeName(maxLengthName);
        
        // When & Then
        assertTrue(attribute.isValid());
    }
    
    @Test
    void isValid_WithStringValueTooLong_ShouldReturnFalse() {
        // Given
        String longValue = "a".repeat(1001); // 1001 characters
        HangoutAttribute attribute = new HangoutAttribute(validHangoutId, validAttributeName, validStringValue);
        attribute.setStringValue(longValue);
        
        // When & Then
        assertFalse(attribute.isValid());
    }
    
    @Test
    void isValid_WithStringValueExactlyMaxLength_ShouldReturnTrue() {
        // Given
        String maxLengthValue = "a".repeat(1000); // Exactly 1000 characters
        HangoutAttribute attribute = new HangoutAttribute(validHangoutId, validAttributeName, validStringValue);
        attribute.setStringValue(maxLengthValue);
        
        // When & Then
        assertTrue(attribute.isValid());
    }
    
    @Test
    void isValid_WithNullStringValue_ShouldReturnTrue() {
        // Given
        HangoutAttribute attribute = new HangoutAttribute(validHangoutId, validAttributeName, validStringValue);
        attribute.setStringValue(null);
        
        // When & Then
        assertTrue(attribute.isValid());
    }
    
    @Test
    void isValid_WithNullAttributeId_ShouldReturnFalse() {
        // Given
        HangoutAttribute attribute = new HangoutAttribute(validHangoutId, validAttributeName, validStringValue);
        attribute.setAttributeId(null);
        
        // When & Then
        assertFalse(attribute.isValid());
    }
    
    @Test
    void isValid_WithNullHangoutId_ShouldReturnFalse() {
        // Given
        HangoutAttribute attribute = new HangoutAttribute(validHangoutId, validAttributeName, validStringValue);
        attribute.setHangoutId(null);
        
        // When & Then
        assertFalse(attribute.isValid());
    }
    
    @Test
    void isValid_WithUnicodeAttributeName_ShouldReturnTrue() {
        // Given - Test emoji and special characters
        String unicodeName = "parking_info_🚗_测试";
        HangoutAttribute attribute = new HangoutAttribute(validHangoutId, unicodeName, validStringValue);
        
        // When & Then
        assertTrue(attribute.isValid());
        assertEquals(unicodeName, attribute.getAttributeName());
    }
    
    @Test
    void equals_WithSameAttributeId_ShouldReturnTrue() {
        // Given
        HangoutAttribute attribute1 = new HangoutAttribute(validHangoutId, validAttributeId, validAttributeName, validStringValue);
        HangoutAttribute attribute2 = new HangoutAttribute(validHangoutId, validAttributeId, "different_name", "different_value");
        
        // When & Then
        assertEquals(attribute1, attribute2);
        assertEquals(attribute1.hashCode(), attribute2.hashCode());
    }
    
    @Test
    void equals_WithDifferentAttributeId_ShouldReturnFalse() {
        // Given
        HangoutAttribute attribute1 = new HangoutAttribute(validHangoutId, validAttributeName, validStringValue);
        HangoutAttribute attribute2 = new HangoutAttribute(validHangoutId, validAttributeName, validStringValue);
        
        // When & Then (different UUIDs generated)
        assertNotEquals(attribute1, attribute2);
    }
    
    @Test
    void equals_WithNullAttributeId_ShouldHandleGracefully() {
        // Given
        HangoutAttribute attribute1 = new HangoutAttribute(validHangoutId, validAttributeName, validStringValue);
        attribute1.setAttributeId(null);
        HangoutAttribute attribute2 = new HangoutAttribute(validHangoutId, validAttributeName, validStringValue);
        attribute2.setAttributeId(null);
        
        // When & Then
        assertEquals(attribute1, attribute2);
    }
    
    @Test
    void toString_ShouldContainKeyInformation() {
        // Given
        HangoutAttribute attribute = new HangoutAttribute(validHangoutId, validAttributeId, validAttributeName, validStringValue);
        
        // When
        String result = attribute.toString();
        
        // Then
        assertAll(
            () -> assertTrue(result.contains(validAttributeId)),
            () -> assertTrue(result.contains(validHangoutId)),
            () -> assertTrue(result.contains(validAttributeName)),
            () -> assertTrue(result.contains(validStringValue))
        );
    }
    
    @Test
    void defaultConstructor_ShouldSetItemTypeAndTimestamps() {
        // When
        HangoutAttribute attribute = new HangoutAttribute();
        
        // Then
        assertEquals(InviterKeyFactory.ATTRIBUTE_PREFIX, attribute.getItemType());
        assertNotNull(attribute.getCreatedAt());
        assertNotNull(attribute.getUpdatedAt());
    }
}