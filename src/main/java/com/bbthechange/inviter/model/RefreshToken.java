package com.bbthechange.inviter.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@DynamoDbBean
public class RefreshToken extends BaseItem {
    
    private String tokenId;           // UUID for this refresh token record
    private String userId;            // User this token belongs to
    private String tokenHash;         // SHA-256 hash for GSI lookup
    private String securityHash;      // BCrypt hash for validation
    private Long expiryDate;          // Unix timestamp (30 days from creation) - also serves as TTL
    private String deviceId;          // Optional: for device binding
    private String ipAddress;         // Optional: IP binding for extra security
    
    public RefreshToken(String userId, String tokenHash, String securityHash, String deviceId, String ipAddress) {
        super();
        this.setItemType("REFRESH_TOKEN");
        this.tokenId = UUID.randomUUID().toString();
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.securityHash = securityHash;
        this.deviceId = deviceId;
        this.ipAddress = ipAddress;
        
        // 30 days from now
        this.expiryDate = Instant.now().plus(30, ChronoUnit.DAYS).getEpochSecond();
        
        // Set DynamoDB keys
        this.setPk("USER#" + userId);
        this.setSk("REFRESH_TOKEN#" + tokenId);
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "TokenHashIndex")
    public String getTokenHash() {
        return tokenHash;
    }
    
    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }
    
    /**
     * Check if the refresh token is expired
     */
    public boolean isExpired() {
        return expiryDate < Instant.now().getEpochSecond();
    }
}