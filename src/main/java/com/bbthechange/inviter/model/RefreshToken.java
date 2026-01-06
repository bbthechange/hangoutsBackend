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
@DynamoDbBean
public class RefreshToken extends BaseItem {
    
    private String tokenId;           // UUID for this refresh token record
    private String userId;            // User this token belongs to
    private String tokenHash;         // SHA-256 hash for GSI lookup
    private String securityHash;      // BCrypt hash for validation
    private Long expiryDate;          // Unix timestamp (30 days from creation) - also serves as TTL
    private String deviceId;          // Optional: for device binding
    private String ipAddress;         // Optional: IP binding for extra security
    private Long supersededAt;        // Epoch seconds when this token was replaced (null = active, for grace period)
    
    public RefreshToken() {
        super();
        this.setItemType("REFRESH_TOKEN");
    }
    
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
        return expiryDate <= Instant.now().getEpochSecond();
    }

    /**
     * Check if this token has been superseded by a newer token
     */
    public boolean isSuperseded() {
        return supersededAt != null;
    }

    /**
     * Check if this superseded token is still within the grace period
     * @param gracePeriodSeconds the grace period in seconds (e.g., 300 for 5 minutes)
     * @return true if the token can still be used, false if grace period expired
     */
    public boolean isWithinGracePeriod(long gracePeriodSeconds) {
        if (supersededAt == null) {
            return true; // Not superseded, always valid (from grace perspective)
        }
        long elapsed = Instant.now().getEpochSecond() - supersededAt;
        return elapsed <= gracePeriodSeconds;
    }
}