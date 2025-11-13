package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.exception.RepositoryException;
import com.bbthechange.inviter.model.InviteCode;
import com.bbthechange.inviter.repository.InviteCodeRepository;
import com.bbthechange.inviter.util.InviterKeyFactory;
import com.bbthechange.inviter.util.QueryPerformanceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementation of InviteCodeRepository using DynamoDB Enhanced Client.
 */
@Repository
public class InviteCodeRepositoryImpl implements InviteCodeRepository {

    private static final Logger logger = LoggerFactory.getLogger(InviteCodeRepositoryImpl.class);
    private static final String TABLE_NAME = "InviterTable";

    private final DynamoDbClient dynamoDbClient;
    private final TableSchema<InviteCode> inviteCodeSchema;
    private final QueryPerformanceTracker queryTracker;

    @Autowired
    public InviteCodeRepositoryImpl(DynamoDbClient dynamoDbClient, QueryPerformanceTracker queryTracker) {
        this.dynamoDbClient = dynamoDbClient;
        this.queryTracker = queryTracker;
        this.inviteCodeSchema = TableSchema.fromBean(InviteCode.class);
    }

    @Override
    public void save(InviteCode inviteCode) {
        queryTracker.trackQuery("PutItem", TABLE_NAME, () -> {
            try {
                Map<String, AttributeValue> itemMap = inviteCodeSchema.itemToMap(inviteCode, true);

                PutItemRequest request = PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(itemMap)
                    .build();

                dynamoDbClient.putItem(request);
                logger.debug("Saved invite code: {}", inviteCode.getInviteCodeId());
                return null;

            } catch (DynamoDbException e) {
                logger.error("Failed to save invite code {}", inviteCode.getInviteCodeId(), e);
                throw new RepositoryException("Failed to save invite code", e);
            }
        });
    }

    @Override
    public Optional<InviteCode> findByCode(String code) {
        return queryTracker.trackQuery("Query", TABLE_NAME, () -> {
            try {
                QueryRequest request = QueryRequest.builder()
                    .tableName(TABLE_NAME)
                    .indexName("InviteCodeIndex")
                    .keyConditionExpression("gsi3pk = :gsi3pk AND gsi3sk = :gsi3sk")
                    .expressionAttributeValues(Map.of(
                        ":gsi3pk", AttributeValue.builder().s(InviterKeyFactory.getCodeLookupGsi3pk(code)).build(),
                        ":gsi3sk", AttributeValue.builder().s(InviterKeyFactory.getMetadataSk()).build()
                    ))
                    .build();

                QueryResponse response = dynamoDbClient.query(request);

                if (response.items().isEmpty()) {
                    return Optional.empty();
                }

                InviteCode inviteCode = inviteCodeSchema.mapToItem(response.items().get(0));
                return Optional.of(inviteCode);

            } catch (DynamoDbException e) {
                logger.error("Failed to find invite code by code string: {}", code, e);
                throw new RepositoryException("Failed to find invite code by code", e);
            }
        });
    }

    @Override
    public Optional<InviteCode> findById(String inviteCodeId) {
        return queryTracker.trackQuery("GetItem", TABLE_NAME, () -> {
            try {
                GetItemRequest request = GetItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of(
                        "pk", AttributeValue.builder().s(InviterKeyFactory.getInviteCodePk(inviteCodeId)).build(),
                        "sk", AttributeValue.builder().s(InviterKeyFactory.getMetadataSk()).build()
                    ))
                    .build();

                GetItemResponse response = dynamoDbClient.getItem(request);

                if (!response.hasItem() || response.item().isEmpty()) {
                    return Optional.empty();
                }

                InviteCode inviteCode = inviteCodeSchema.mapToItem(response.item());
                return Optional.of(inviteCode);

            } catch (DynamoDbException e) {
                logger.error("Failed to find invite code by ID: {}", inviteCodeId, e);
                throw new RepositoryException("Failed to find invite code by ID", e);
            }
        });
    }

    @Override
    public List<InviteCode> findAllByGroupId(String groupId) {
        return queryTracker.trackQuery("Query", TABLE_NAME, () -> {
            try {
                QueryRequest request = QueryRequest.builder()
                    .tableName(TABLE_NAME)
                    .indexName("UserGroupIndex")
                    .keyConditionExpression("gsi1pk = :gsi1pk AND begins_with(gsi1sk, :gsi1sk_prefix)")
                    .expressionAttributeValues(Map.of(
                        ":gsi1pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build(),
                        ":gsi1sk_prefix", AttributeValue.builder().s(InviterKeyFactory.CREATED_PREFIX + "#").build()
                    ))
                    .build();

                QueryResponse response = dynamoDbClient.query(request);

                List<InviteCode> inviteCodes = new ArrayList<>();
                for (Map<String, AttributeValue> item : response.items()) {
                    InviteCode inviteCode = inviteCodeSchema.mapToItem(item);
                    inviteCodes.add(inviteCode);
                }

                logger.debug("Found {} invite codes for group {}", inviteCodes.size(), groupId);
                return inviteCodes;

            } catch (DynamoDbException e) {
                logger.error("Failed to find invite codes for group: {}", groupId, e);
                throw new RepositoryException("Failed to find invite codes for group", e);
            }
        });
    }

    @Override
    public boolean codeExists(String code) {
        return findByCode(code).isPresent();
    }

    @Override
    public Optional<InviteCode> findActiveCodeForGroup(String groupId) {
        return queryTracker.trackQuery("Query", TABLE_NAME, () -> {
            try {
                // Query all codes for this group, we'll filter in memory
                List<InviteCode> allCodes = findAllByGroupId(groupId);

                // Find first active (usable) code
                return allCodes.stream()
                    .filter(InviteCode::isUsable)
                    .findFirst();

            } catch (DynamoDbException e) {
                logger.error("Failed to find active invite code for group: {}", groupId, e);
                throw new RepositoryException("Failed to find active invite code for group", e);
            }
        });
    }

    @Override
    public void delete(String inviteCodeId) {
        queryTracker.trackQuery("DeleteItem", TABLE_NAME, () -> {
            try {
                DeleteItemRequest request = DeleteItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of(
                        "pk", AttributeValue.builder().s(InviterKeyFactory.getInviteCodePk(inviteCodeId)).build(),
                        "sk", AttributeValue.builder().s(InviterKeyFactory.getMetadataSk()).build()
                    ))
                    .build();

                dynamoDbClient.deleteItem(request);
                logger.info("Deleted invite code: {}", inviteCodeId);
                return null;

            } catch (DynamoDbException e) {
                logger.error("Failed to delete invite code: {}", inviteCodeId, e);
                throw new RepositoryException("Failed to delete invite code", e);
            }
        });
    }
}
