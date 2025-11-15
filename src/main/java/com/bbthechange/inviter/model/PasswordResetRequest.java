package com.bbthechange.inviter.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

import java.time.Instant;

/**
 * DynamoDB entity representing a password reset request.
 *
 * <p>This table tracks password reset requests initiated by users. The primary key is userId,
 * ensuring only one active reset request per user at a time. GSIs on phoneNumber and email
 * allow lookups during the verification step.</p>
 *
 * <p>The TTL field enables automatic cleanup of stale reset requests after 1 day.</p>
 *
 * <p>Flow:
 * 1. Request reset → Create record with codeVerified=false
 * 2. Verify SMS code → Update codeVerified=true
 * 3. Reset password → Update tokenUsed=true
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class PasswordResetRequest {

    /**
     * User ID requesting the password reset (primary key).
     * Using userId as PK ensures only one active reset per user.
     */
    private String userId;

    /**
     * Phone number for SMS-based reset (GSI for lookup during verification).
     * Optional - null if using email-based reset.
     */
    private String phoneNumber;

    /**
     * Email address for email-based reset (GSI for lookup, future use).
     * Optional - null if using phone-based reset.
     */
    private String email;

    /**
     * Method used for this reset request (PHONE or EMAIL).
     */
    private ResetMethod method;

    /**
     * Whether the verification code (SMS or email) has been successfully verified.
     * false = code not yet verified, true = code verified and reset token issued.
     */
    private Boolean codeVerified;

    /**
     * Whether the reset token has been used to complete the password change.
     * false = token not yet used, true = password successfully reset.
     */
    private Boolean tokenUsed;

    /**
     * IP address that initiated the reset request (for security logging).
     */
    private String ipAddress;

    /**
     * DynamoDB TTL attribute (Unix timestamp).
     * Records are automatically deleted after this time (1 day from creation).
     */
    private Long ttl;

    @DynamoDbPartitionKey
    public String getUserId() {
        return userId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "PhoneNumberIndex")
    public String getPhoneNumber() {
        return phoneNumber;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "EmailIndex")
    public String getEmail() {
        return email;
    }

    @DynamoDbAttribute("ttl")
    public Long getTtl() {
        return ttl;
    }

    /**
     * Helper method to set TTL to 1 day from now (86400 seconds).
     * Call this when creating a new reset request.
     */
    public void setTtlOneDay() {
        this.ttl = Instant.now().plusSeconds(86400).getEpochSecond();
    }
}
