package com.bbthechange.inviter.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating new hangout attributes via POST /hangouts/{id}/attributes.
 * 
 * Validation rules:
 * - attributeName: Required, 1-100 characters, supports full Unicode
 * - stringValue: Optional, 0-1000 characters when present
 */
public class CreateAttributeRequest {
    
    @NotNull(message = "Attribute name is required")
    @Size(min = 1, max = 100, message = "Attribute name must be between 1 and 100 characters")
    private String attributeName;
    
    @Size(max = 1000, message = "String value must not exceed 1000 characters")
    private String stringValue;
    
    // Default constructor for JSON deserialization
    public CreateAttributeRequest() {}
    
    /**
     * Constructor for creating request objects.
     */
    public CreateAttributeRequest(String attributeName, String stringValue) {
        this.attributeName = attributeName;
        this.stringValue = stringValue;
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
    
    /**
     * Validate the request beyond basic annotations.
     * Called by service layer for additional business rule validation.
     */
    public boolean isValid() {
        // Basic null/length checks are handled by annotations
        // Additional business rules can be added here
        
        if (attributeName != null) {
            // Trim and check for effectively empty strings
            String trimmed = attributeName.trim();
            if (trimmed.isEmpty()) {
                return false;
            }
            
            // Check for reserved attribute names
            String lowercase = trimmed.toLowerCase();
            if (isReservedName(lowercase)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Check if attribute name is reserved and should not be allowed.
     */
    private boolean isReservedName(String name) {
        // Reserved words that could conflict with system fields or cause confusion
        return name.equals("id") || 
               name.equals("type") || 
               name.equals("system") || 
               name.equals("internal") ||
               name.equals("pk") ||
               name.equals("sk") ||
               name.startsWith("gsi") ||
               name.startsWith("system_") ||
               name.startsWith("internal_");
    }
    
    /**
     * Get the attribute name trimmed for consistency.
     */
    public String getTrimmedAttributeName() {
        return attributeName != null ? attributeName.trim() : null;
    }
    
    @Override
    public String toString() {
        return String.format("CreateAttributeRequest{attributeName='%s', stringValue='%s'}", 
            attributeName, stringValue != null ? stringValue.substring(0, Math.min(stringValue.length(), 50)) + "..." : null);
    }
}