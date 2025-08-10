package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.HangoutAttribute;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;

class HangoutAttributeDTOTest {
    
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
    void constructor_WithValidInputs_ShouldCreateValidDTO() {
        // When
        HangoutAttributeDTO dto = new HangoutAttributeDTO(validAttributeId, validAttributeName, validStringValue);
        
        // Then
        assertEquals(validAttributeId, dto.getAttributeId());
        assertEquals(validAttributeName, dto.getAttributeName());
        assertEquals(validStringValue, dto.getStringValue());
    }
    
    @Test
    void constructor_WithNullStringValue_ShouldAllowNull() {
        // When
        HangoutAttributeDTO dto = new HangoutAttributeDTO(validAttributeId, validAttributeName, null);
        
        // Then
        assertEquals(validAttributeId, dto.getAttributeId());
        assertEquals(validAttributeName, dto.getAttributeName());
        assertNull(dto.getStringValue());
    }
    
    @Test
    void fromEntity_WithValidAttribute_ShouldCreateCorrectDTO() {
        // Given
        HangoutAttribute attribute = new HangoutAttribute(validHangoutId, validAttributeId, validAttributeName, validStringValue);
        
        // When
        HangoutAttributeDTO dto = HangoutAttributeDTO.fromEntity(attribute);
        
        // Then
        assertNotNull(dto);
        assertEquals(attribute.getAttributeId(), dto.getAttributeId());
        assertEquals(attribute.getAttributeName(), dto.getAttributeName());
        assertEquals(attribute.getStringValue(), dto.getStringValue());
    }
    
    @Test
    void fromEntity_WithNullAttribute_ShouldReturnNull() {
        // When
        HangoutAttributeDTO dto = HangoutAttributeDTO.fromEntity(null);
        
        // Then
        assertNull(dto);
    }
    
    @Test
    void fromEntity_WithNullStringValueInAttribute_ShouldPreserveNull() {
        // Given
        HangoutAttribute attribute = new HangoutAttribute(validHangoutId, validAttributeId, validAttributeName, null);
        
        // When
        HangoutAttributeDTO dto = HangoutAttributeDTO.fromEntity(attribute);
        
        // Then
        assertNotNull(dto);
        assertEquals(attribute.getAttributeId(), dto.getAttributeId());
        assertEquals(attribute.getAttributeName(), dto.getAttributeName());
        assertNull(dto.getStringValue());
    }
    
    @Test
    void fromEntity_WithUnicodeAttributeName_ShouldPreserveUnicode() {
        // Given
        String unicodeName = "parking_info_🚗_测试";
        HangoutAttribute attribute = new HangoutAttribute(validHangoutId, validAttributeId, unicodeName, validStringValue);
        
        // When
        HangoutAttributeDTO dto = HangoutAttributeDTO.fromEntity(attribute);
        
        // Then
        assertNotNull(dto);
        assertEquals(unicodeName, dto.getAttributeName());
        assertEquals(attribute.getAttributeId(), dto.getAttributeId());
        assertEquals(attribute.getStringValue(), dto.getStringValue());
    }
    
    @Test
    void equals_WithSameAttributeId_ShouldReturnTrue() {
        // Given
        HangoutAttributeDTO dto1 = new HangoutAttributeDTO(validAttributeId, validAttributeName, validStringValue);
        HangoutAttributeDTO dto2 = new HangoutAttributeDTO(validAttributeId, "different_name", "different_value");
        
        // When & Then
        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }
    
    @Test
    void equals_WithDifferentAttributeId_ShouldReturnFalse() {
        // Given
        String differentAttributeId = UUID.randomUUID().toString();
        HangoutAttributeDTO dto1 = new HangoutAttributeDTO(validAttributeId, validAttributeName, validStringValue);
        HangoutAttributeDTO dto2 = new HangoutAttributeDTO(differentAttributeId, validAttributeName, validStringValue);
        
        // When & Then
        assertNotEquals(dto1, dto2);
    }
    
    @Test
    void equals_WithNullAttributeId_ShouldHandleGracefully() {
        // Given
        HangoutAttributeDTO dto1 = new HangoutAttributeDTO(null, validAttributeName, validStringValue);
        HangoutAttributeDTO dto2 = new HangoutAttributeDTO(null, validAttributeName, validStringValue);
        
        // When & Then
        assertEquals(dto1, dto2);
    }
    
    @Test
    void toString_ShouldContainKeyInformation() {
        // Given
        HangoutAttributeDTO dto = new HangoutAttributeDTO(validAttributeId, validAttributeName, validStringValue);
        
        // When
        String result = dto.toString();
        
        // Then
        assertAll(
            () -> assertTrue(result.contains(validAttributeId)),
            () -> assertTrue(result.contains(validAttributeName)),
            () -> assertTrue(result.contains(validStringValue))
        );
    }
    
    @Test
    void setters_ShouldUpdateFields() {
        // Given
        HangoutAttributeDTO dto = new HangoutAttributeDTO();
        
        // When
        dto.setAttributeId(validAttributeId);
        dto.setAttributeName(validAttributeName);
        dto.setStringValue(validStringValue);
        
        // Then
        assertEquals(validAttributeId, dto.getAttributeId());
        assertEquals(validAttributeName, dto.getAttributeName());
        assertEquals(validStringValue, dto.getStringValue());
    }
    
    @Test
    void defaultConstructor_ShouldCreateEmptyDTO() {
        // When
        HangoutAttributeDTO dto = new HangoutAttributeDTO();
        
        // Then
        assertNull(dto.getAttributeId());
        assertNull(dto.getAttributeName());
        assertNull(dto.getStringValue());
    }
}