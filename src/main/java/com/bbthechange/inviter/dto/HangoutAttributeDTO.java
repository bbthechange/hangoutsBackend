package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.HangoutAttribute;

/**
 * Data Transfer Object for HangoutAttribute responses.
 * Used in API responses to provide attribute data to clients.
 * 
 * Contains the attributeId for efficient frontend updates.
 */
public class HangoutAttributeDTO {
    
    private String attributeId;     // UUID for efficient updates
    private String attributeName;   // User-facing name
    private String stringValue;     // Attribute value (nullable)
    
    // Default constructor for JSON deserialization
    public HangoutAttributeDTO() {}
    
    /**
     * Constructor for creating DTO from entity.
     */
    public HangoutAttributeDTO(String attributeId, String attributeName, String stringValue) {
        this.attributeId = attributeId;
        this.attributeName = attributeName;
        this.stringValue = stringValue;
    }
    
    /**
     * Factory method to create DTO from HangoutAttribute entity.
     */
    public static HangoutAttributeDTO fromEntity(HangoutAttribute attribute) {
        if (attribute == null) {
            return null;
        }
        return new HangoutAttributeDTO(
            attribute.getAttributeId(),
            attribute.getAttributeName(),
            attribute.getStringValue()
        );
    }
    
    public String getAttributeId() {
        return attributeId;
    }
    
    public void setAttributeId(String attributeId) {
        this.attributeId = attributeId;
    }
    
    public String getAttributeName() {
        return attributeName;
    }
    
    public void setAttributeName(String attributeName) {
        this.attributeName = attributeName;
    }
    
    public String getStringValue() {
        return stringValue;
    }
    
    public void setStringValue(String stringValue) {
        this.stringValue = stringValue;
    }
    
    @Override
    public String toString() {
        return String.format("HangoutAttributeDTO{attributeId='%s', attributeName='%s', stringValue='%s'}", 
            attributeId, attributeName, stringValue);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        HangoutAttributeDTO that = (HangoutAttributeDTO) obj;
        return attributeId != null ? attributeId.equals(that.attributeId) : that.attributeId == null;
    }
    
    @Override
    public int hashCode() {
        return attributeId != null ? attributeId.hashCode() : 0;
    }
}