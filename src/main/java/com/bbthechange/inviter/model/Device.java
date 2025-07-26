package com.bbthechange.inviter.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class Device {
    private String token;
    private UUID userId;
    private Platform platform;
    private boolean isActive;
    private Instant updatedAt;
    
    public Device(String token, UUID userId, Platform platform) {
        this.token = token;
        this.userId = userId;
        this.platform = platform;
        this.isActive = true;
        this.updatedAt = Instant.now();
    }
    
    @DynamoDbPartitionKey
    public String getToken() {
        return token;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "UserIndex")
    public UUID getUserId() {
        return userId;
    }
    
    public enum Platform {
        IOS, ANDROID
    }
}