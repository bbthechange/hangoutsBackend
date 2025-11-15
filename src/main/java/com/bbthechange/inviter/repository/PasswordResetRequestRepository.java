package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.model.PasswordResetRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.Optional;

/**
 * Repository for PasswordResetRequest DynamoDB operations.
 *
 * <p>Provides methods to manage password reset requests with lookups by:
 * - userId (PK) - direct lookup
 * - phoneNumber (GSI) - for SMS-based reset verification
 * - email (GSI) - for future email-based reset verification
 * </p>
 */
@Repository
public class PasswordResetRequestRepository {

    private final DynamoDbTable<PasswordResetRequest> resetRequestTable;
    private final DynamoDbIndex<PasswordResetRequest> phoneNumberIndex;
    private final DynamoDbIndex<PasswordResetRequest> emailIndex;

    @Autowired
    public PasswordResetRequestRepository(DynamoDbEnhancedClient dynamoDbEnhancedClient) {
        this.resetRequestTable = dynamoDbEnhancedClient.table(
            "PasswordResetRequest",
            TableSchema.fromBean(PasswordResetRequest.class)
        );
        this.phoneNumberIndex = resetRequestTable.index("PhoneNumberIndex");
        this.emailIndex = resetRequestTable.index("EmailIndex");
    }

    /**
     * Save or update a password reset request.
     * Since userId is the PK, this will overwrite any existing request for the same user.
     */
    public PasswordResetRequest save(PasswordResetRequest request) {
        resetRequestTable.putItem(request);
        return request;
    }

    /**
     * Find a reset request by user ID (primary key lookup).
     * This is the fastest and cheapest query.
     */
    public Optional<PasswordResetRequest> findById(String userId) {
        PasswordResetRequest request = resetRequestTable.getItem(
            Key.builder().partitionValue(userId).build()
        );
        return Optional.ofNullable(request);
    }

    /**
     * Find a reset request by phone number (GSI query).
     * Used during SMS code verification step.
     */
    public Optional<PasswordResetRequest> findByPhoneNumber(String phoneNumber) {
        return phoneNumberIndex.query(QueryConditional.keyEqualTo(
                Key.builder().partitionValue(phoneNumber).build()))
            .stream()
            .flatMap(page -> page.items().stream())
            .findFirst();
    }

    /**
     * Find a reset request by email (GSI query, future use).
     * Will be used for email-based password reset verification.
     */
    public Optional<PasswordResetRequest> findByEmail(String email) {
        return emailIndex.query(QueryConditional.keyEqualTo(
                Key.builder().partitionValue(email).build()))
            .stream()
            .flatMap(page -> page.items().stream())
            .findFirst();
    }

    /**
     * Delete a password reset request.
     */
    public void delete(PasswordResetRequest request) {
        resetRequestTable.deleteItem(request);
    }

    /**
     * Delete a password reset request by user ID.
     */
    public void deleteById(String userId) {
        resetRequestTable.deleteItem(
            Key.builder().partitionValue(userId).build()
        );
    }
}
