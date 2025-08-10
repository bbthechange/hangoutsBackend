package com.bbthechange.inviter.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating hangout attributes via PUT /hangouts/{id}/attributes/{attributeId}.
 * 
 * Supports both renaming (attributeName) and value updates (stringValue).
 * Both fields are required to ensure explicit intent in PUT operations.
 * 
 * Validation rules:
 * - attributeName: Required, 1-100 characters, supports full Unicode
 * - stringValue: Required but can be empty string, 0-1000 characters
 */
public class UpdateAttributeRequest {
    
    @NotNull(message = "Attribute name is required")
    @Size(min = 1, max = 100, message = "Attribute name must be between 1 and 100 characters")
    private String attributeName;   // Supports renaming
    
    @NotNull(message = "String value is required (use empty string for null-like values)")
    @Size(max = 1000, message = "String value must not exceed 1000 characters")
    private String stringValue;     // Required in PUT semantics
    
    // Default constructor for JSON deserialization
    public UpdateAttributeRequest() {}
    
    /**
     * Constructor for creating request objects.
     */
    public UpdateAttributeRequest(String attributeName, String stringValue) {
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
    
    /**
     * Check if this update represents a name change.
     * Useful for service layer logic that may need to handle renames differently.
     */
    public boolean isNameChange(String currentName) {
        String newName = getTrimmedAttributeName();
        return newName != null && !newName.equals(currentName);
    }
    
    @Override
    public String toString() {
        return String.format("UpdateAttributeRequest{attributeName='%s', stringValue='%s'}", 
            attributeName, stringValue != null ? stringValue.substring(0, Math.min(stringValue.length(), 50)) + "..." : null);
    }
}