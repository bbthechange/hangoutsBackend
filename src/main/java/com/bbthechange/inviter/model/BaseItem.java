package com.bbthechange.inviter.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

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
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
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