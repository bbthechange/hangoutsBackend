package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InviterKeyFactory;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;

import java.util.UUID;

/**
 * HangoutAttribute entity for the InviterTable.
 * Represents a custom key-value attribute for a hangout/event.
 * 
 * Key Pattern: PK = EVENT#{hangoutId}, SK = ATTRIBUTE#{attributeId}
 * 
 * Design Notes:
 * - Uses UUID-based sort key for safety and efficient direct access
 * - Supports full Unicode in attributeName (emojis, special chars allowed)
 * - Extensible design for future type support (intValue, dateValue, etc.)
 */
@DynamoDbBean
public class HangoutAttribute extends BaseItem {
    
    private String attributeId;     // UUID - primary identifier used in sort key
    private String hangoutId;       // Foreign key reference to hangout
    private String attributeName;   // User-facing name (1-100 chars, Unicode supported)
    private String stringValue;     // Attribute value (0-1000 chars, nullable)
    
    // Future expansion fields (commented out for initial implementation):
    // private Integer intValue;
    // private LocalDateTime dateValue;
    // private Boolean boolValue;
    // private String attributeType; // Enum: STRING, INT, DATE, BOOLEAN
    
    // Default constructor for DynamoDB
    public HangoutAttribute() {
        super();
        setItemType(InviterKeyFactory.ATTRIBUTE_PREFIX);
    }
    
    /**
     * Create a new hangout attribute with generated UUID.
     * 
     * @param hangoutId The hangout this attribute belongs to
     * @param attributeName User-facing name for the attribute
     * @param stringValue The string value (can be null)
     */
    public HangoutAttribute(String hangoutId, String attributeName, String stringValue) {
        super();
        setItemType(InviterKeyFactory.ATTRIBUTE_PREFIX);
        this.attributeId = UUID.randomUUID().toString();
        this.hangoutId = hangoutId;
        this.attributeName = attributeName;
        this.stringValue = stringValue;
        
        // Set keys using InviterKeyFactory
        setPk(InviterKeyFactory.getEventPk(hangoutId));
        setSk(InviterKeyFactory.getAttributeSk(this.attributeId));
    }
    
    /**
     * Create hangout attribute with specific attributeId (for testing or data migration).
     */
    public HangoutAttribute(String hangoutId, String attributeId, String attributeName, String stringValue) {
        super();
        setItemType(InviterKeyFactory.ATTRIBUTE_PREFIX);
        this.attributeId = attributeId;
        this.hangoutId = hangoutId;
        this.attributeName = attributeName;
        this.stringValue = stringValue;
        
        // Set keys using InviterKeyFactory
        setPk(InviterKeyFactory.getEventPk(hangoutId));
        setSk(InviterKeyFactory.getAttributeSk(attributeId));
    }
    
    @DynamoDbAttribute("attributeId")
    public String getAttributeId() {
        return attributeId;
    }
    
    public void setAttributeId(String attributeId) {
        this.attributeId = attributeId;
    }
    
    @DynamoDbAttribute("hangoutId")
    public String getHangoutId() {
        return hangoutId;
    }
    
    public void setHangoutId(String hangoutId) {
        this.hangoutId = hangoutId;
    }
    
    @DynamoDbAttribute("attributeName")
    public String getAttributeName() {
        return attributeName;
    }
    
    public void setAttributeName(String attributeName) {
        this.attributeName = attributeName;
        touch(); // Update timestamp when attribute name changes
    }
    
    @DynamoDbAttribute("stringValue")
    public String getStringValue() {
        return stringValue;
    }
    
    public void setStringValue(String stringValue) {
        this.stringValue = stringValue;
        touch(); // Update timestamp when value changes
    }
    
    /**
     * Update both name and value in a single operation.
     * Useful for PUT operations that may change both fields.
     */
    public void updateAttribute(String attributeName, String stringValue) {
        this.attributeName = attributeName;
        this.stringValue = stringValue;
        touch();
    }
    
    /**
     * Validation method to check if attribute is valid for saving.
     * Called by service layer before persistence.
     */
    public boolean isValid() {
        // Attribute name validation
        if (attributeName == null || attributeName.trim().isEmpty()) {
            return false;
        }
        if (attributeName.length() > 100) {
            return false;
        }
        
        // String value validation (null is allowed)
        if (stringValue != null && stringValue.length() > 1000) {
            return false;
        }
        
        // Required IDs
        if (attributeId == null || hangoutId == null) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Get a display-friendly string representation.
     */
    @Override
    public String toString() {
        return String.format("HangoutAttribute{attributeId='%s', hangoutId='%s', attributeName='%s', stringValue='%s'}", 
            attributeId, hangoutId, attributeName, stringValue);
    }
    
    /**
     * Equality based on attributeId (unique identifier).
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        HangoutAttribute that = (HangoutAttribute) obj;
        return attributeId != null ? attributeId.equals(that.attributeId) : that.attributeId == null;
    }
    
    @Override
    public int hashCode() {
        return attributeId != null ? attributeId.hashCode() : 0;
    }
}