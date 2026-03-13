package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.model.GroupNotificationTracker;
import com.bbthechange.inviter.repository.GroupNotificationTrackerRepository;
import com.bbthechange.inviter.util.InviterKeyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Map;
import java.util.Optional;

/**
 * DynamoDB implementation for GroupNotificationTrackerRepository.
 *
 * Key pattern (group partition, single-table design):
 *   PK = GROUP#{groupId}
 *   SK = NOTIFICATION_TRACKER
 */
@Repository
public class GroupNotificationTrackerRepositoryImpl implements GroupNotificationTrackerRepository {

    private static final Logger logger = LoggerFactory.getLogger(GroupNotificationTrackerRepositoryImpl.class);
    private static final String TABLE_NAME = "InviterTable";
    static final String NOTIFICATION_TRACKER_SK = "NOTIFICATION_TRACKER";

    private final DynamoDbClient dynamoDbClient;
    private final TableSchema<GroupNotificationTracker> schema;

    @Autowired
    public GroupNotificationTrackerRepositoryImpl(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
        this.schema = TableSchema.fromBean(GroupNotificationTracker.class);
    }

    @Override
    public Optional<GroupNotificationTracker> findByGroupId(String groupId) {
        try {
            GetItemRequest request = GetItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of(
                            "pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build(),
                            "sk", AttributeValue.builder().s(NOTIFICATION_TRACKER_SK).build()
                    ))
                    .build();

            GetItemResponse response = dynamoDbClient.getItem(request);
            if (!response.hasItem() || response.item().isEmpty()) {
                return Optional.empty();
            }

            GroupNotificationTracker tracker = schema.mapToItem(response.item());
            return Optional.of(tracker);

        } catch (DynamoDbException e) {
            logger.error("Failed to load notification tracker for group {}: {}", groupId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void save(GroupNotificationTracker tracker) {
        try {
            tracker.touch();
            Map<String, AttributeValue> itemMap = schema.itemToMap(tracker, true);

            PutItemRequest request = PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(itemMap)
                    .build();

            dynamoDbClient.putItem(request);

        } catch (DynamoDbException e) {
            logger.error("Failed to save notification tracker for group {}: {}", tracker.getGroupId(), e.getMessage());
            // Don't rethrow — tracker failures must not break the notification or momentum flow
        }
    }
}
