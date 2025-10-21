package com.bbthechange.inviter.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import com.bbthechange.inviter.util.InstantAsLongAttributeConverter;

import java.time.Instant;

/**
 * Base class for all items stored in the InviterTable.
 * Provides common attributes for the single-table design pattern.
 */
@DynamoDbBean
public abstract class BaseItem {
    
    private String pk;          // Partition Key
    private String sk;          // Sort Key
    private String gsi1pk;      // GSI1 Partition Key
    private String gsi1sk;      // GSI1 Sort Key
    private String gsi2pk;      // GSI2 Partition Key (for CalendarTokenIndex)
    private String gsi2sk;      // GSI2 Sort Key (reserved for future use)
    private String itemType;    // Type discriminator for polymorphic deserialization
    private Instant createdAt;
    private Instant updatedAt;
    
    public BaseItem() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    
    @DynamoDbPartitionKey
    public String getPk() {
        return pk;
    }
    
    public void setPk(String pk) {
        this.pk = pk;
    }
    
    @DynamoDbSortKey
    public String getSk() {
        return sk;
    }
    
    public void setSk(String sk) {
        this.sk = sk;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "UserGroupIndex")
    public String getGsi1pk() {
        return gsi1pk;
    }
    
    public void setGsi1pk(String gsi1pk) {
        this.gsi1pk = gsi1pk;
    }
    
    @DynamoDbSecondarySortKey(indexNames = "UserGroupIndex")  
    public String getGsi1sk() {
        return gsi1sk;
    }
    
    public void setGsi1sk(String gsi1sk) {
        this.gsi1sk = gsi1sk;
    }

    public String getGsi2pk() {
        return gsi2pk;
    }

    public void setGsi2pk(String gsi2pk) {
        this.gsi2pk = gsi2pk;
    }

    public String getGsi2sk() {
        return gsi2sk;
    }

    public void setGsi2sk(String gsi2sk) {
        this.gsi2sk = gsi2sk;
    }

    @DynamoDbAttribute("itemType")
    public String getItemType() {
        return itemType;
    }
    
    public void setItemType(String itemType) {
        this.itemType = itemType;
    }
    
    @DynamoDbConvertedBy(InstantAsLongAttributeConverter.class)
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    @DynamoDbConvertedBy(InstantAsLongAttributeConverter.class)
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    /**
     * Update the updatedAt timestamp to current time.
     * Should be called before saving updates.
     */
    public void touch() {
        this.updatedAt = Instant.now();
    }
}