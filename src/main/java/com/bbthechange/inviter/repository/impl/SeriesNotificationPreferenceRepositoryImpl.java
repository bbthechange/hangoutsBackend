package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.exception.RepositoryException;
import com.bbthechange.inviter.model.SeriesNotificationPreference;
import com.bbthechange.inviter.repository.SeriesNotificationPreferenceRepository;
import com.bbthechange.inviter.util.InviterKeyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository
public class SeriesNotificationPreferenceRepositoryImpl implements SeriesNotificationPreferenceRepository {

    private static final Logger logger = LoggerFactory.getLogger(SeriesNotificationPreferenceRepositoryImpl.class);
    private static final String TABLE_NAME = "InviterTable";
    private static final int BATCH_GET_LIMIT = 100;

    private final DynamoDbClient dynamoDbClient;
    private final DynamoDbTable<SeriesNotificationPreference> preferenceTable;
    private final TableSchema<SeriesNotificationPreference> preferenceSchema;

    @Autowired
    public SeriesNotificationPreferenceRepositoryImpl(
            DynamoDbClient dynamoDbClient,
            DynamoDbEnhancedClient dynamoDbEnhancedClient) {
        this.dynamoDbClient = dynamoDbClient;
        this.preferenceSchema = TableSchema.fromBean(SeriesNotificationPreference.class);
        this.preferenceTable = dynamoDbEnhancedClient.table(TABLE_NAME, this.preferenceSchema);
    }

    @Override
    public Optional<SeriesNotificationPreference> find(String userId, String seriesId) {
        try {
            SeriesNotificationPreference pref = preferenceTable.getItem(builder -> builder.key(
                k -> k.partitionValue(InviterKeyFactory.getUserPk(userId))
                      .sortValue(InviterKeyFactory.getSeriesPrefSk(seriesId))));
            return Optional.ofNullable(pref);
        } catch (DynamoDbException e) {
            logger.error("Failed to find SeriesNotificationPreference for user={} series={}", userId, seriesId, e);
            throw new RepositoryException("Failed to find SeriesNotificationPreference", e);
        }
    }

    @Override
    public Set<String> findMutedUsersForSeries(String seriesId,
                                               String nudgeType,
                                               Collection<String> candidateUserIds) {
        if (candidateUserIds == null || candidateUserIds.isEmpty()) {
            return Collections.emptySet();
        }
        if (nudgeType == null || nudgeType.isBlank()) {
            return Collections.emptySet();
        }

        // Dedupe and preserve insertion order for stable behavior
        Set<String> uniqueIds = new LinkedHashSet<>(candidateUserIds);
        String sk = InviterKeyFactory.getSeriesPrefSk(seriesId);

        Set<String> mutedUsers = new HashSet<>();
        List<String> idList = new ArrayList<>(uniqueIds);

        for (int i = 0; i < idList.size(); i += BATCH_GET_LIMIT) {
            List<String> batch = idList.subList(i, Math.min(i + BATCH_GET_LIMIT, idList.size()));
            List<Map<String, AttributeValue>> keys = new ArrayList<>(batch.size());
            for (String userId : batch) {
                Map<String, AttributeValue> key = new HashMap<>();
                key.put("pk", AttributeValue.builder().s(InviterKeyFactory.getUserPk(userId)).build());
                key.put("sk", AttributeValue.builder().s(sk).build());
                keys.add(key);
            }

            BatchGetItemRequest request = BatchGetItemRequest.builder()
                .requestItems(Map.of(TABLE_NAME,
                    KeysAndAttributes.builder().keys(keys).build()))
                .build();

            try {
                BatchGetItemResponse response = dynamoDbClient.batchGetItem(request);
                List<Map<String, AttributeValue>> items = response.responses().get(TABLE_NAME);
                if (items == null) {
                    continue;
                }
                for (Map<String, AttributeValue> item : items) {
                    AttributeValue mapAttr = item.get("mutedNudgeTypes");
                    if (mapAttr == null || mapAttr.m() == null) {
                        continue;
                    }
                    AttributeValue flag = mapAttr.m().get(nudgeType);
                    if (flag != null && Boolean.TRUE.equals(flag.bool())) {
                        AttributeValue userIdAttr = item.get("userId");
                        if (userIdAttr != null && userIdAttr.s() != null) {
                            mutedUsers.add(userIdAttr.s());
                        }
                    }
                }
                if (response.hasUnprocessedKeys() && !response.unprocessedKeys().isEmpty()) {
                    logger.warn("BatchGetItem had unprocessed keys for series {} nudgeType {}", seriesId, nudgeType);
                }
            } catch (DynamoDbException e) {
                logger.error("Failed to batch get SeriesNotificationPreference for series {} nudgeType {}",
                    seriesId, nudgeType, e);
                throw new RepositoryException("Failed to batch get SeriesNotificationPreference", e);
            }
        }

        return mutedUsers;
    }

    @Override
    public void setMuted(String userId, String seriesId, String nudgeType, boolean muted) {
        if (nudgeType == null || nudgeType.isBlank()) {
            throw new IllegalArgumentException("nudgeType is required");
        }
        String pk = InviterKeyFactory.getUserPk(userId);
        String sk = InviterKeyFactory.getSeriesPrefSk(seriesId);
        Map<String, AttributeValue> key = Map.of(
            "pk", AttributeValue.builder().s(pk).build(),
            "sk", AttributeValue.builder().s(sk).build());
        String now = Instant.now().toString();

        if (muted) {
            setMutedTrue(userId, seriesId, nudgeType, key, now, /*createFirst=*/true);
            return;
        }

        // Unmute: remove the key only if the row exists, then drop the row when the map empties.
        UpdateItemRequest removeRequest = UpdateItemRequest.builder()
            .tableName(TABLE_NAME)
            .key(key)
            .updateExpression("REMOVE mutedNudgeTypes.#k SET updatedAt = :now")
            .conditionExpression("attribute_exists(pk)")
            .expressionAttributeNames(Map.of("#k", nudgeType))
            .expressionAttributeValues(Map.of(
                ":now", AttributeValue.builder().s(now).build()))
            .returnValues(ReturnValue.ALL_NEW)
            .build();

        UpdateItemResponse response;
        try {
            response = dynamoDbClient.updateItem(removeRequest);
        } catch (ConditionalCheckFailedException e) {
            // Row didn't exist — nothing to unmute.
            return;
        } catch (DynamoDbException e) {
            logger.error("Failed to remove mute key for user={} series={} nudgeType={}",
                userId, seriesId, nudgeType, e);
            throw new RepositoryException("Failed to clear mute", e);
        }

        Map<String, AttributeValue> attrs = response.attributes();
        if (attrs == null || attrs.isEmpty()) {
            return;
        }
        AttributeValue mapAttr = attrs.get("mutedNudgeTypes");
        boolean empty = mapAttr == null || mapAttr.m() == null || mapAttr.m().isEmpty();
        if (!empty) {
            return;
        }

        // Map is empty — delete the row, but guard against a concurrent re-population.
        DeleteItemRequest deleteRequest = DeleteItemRequest.builder()
            .tableName(TABLE_NAME)
            .key(key)
            .conditionExpression(
                "attribute_not_exists(mutedNudgeTypes) OR size(mutedNudgeTypes) = :zero")
            .expressionAttributeValues(Map.of(
                ":zero", AttributeValue.builder().n("0").build()))
            .build();
        try {
            dynamoDbClient.deleteItem(deleteRequest);
        } catch (ConditionalCheckFailedException e) {
            // Another writer added a mute back in — leave the row alone.
        } catch (DynamoDbException e) {
            logger.error("Failed to delete empty preference row for user={} series={}",
                userId, seriesId, e);
            throw new RepositoryException("Failed to delete empty preference row", e);
        }
    }

    /**
     * Two-path mute write that survives concurrent delete/setMuted races.
     *
     * Either path uses a condition that guarantees the row's housekeeping
     * columns (userId, seriesId, itemType, createdAt) are populated:
     * - create path: guarded by attribute_not_exists(pk) and writes the full row.
     * - in-place path: guarded by attribute_exists(pk); if the row has been
     *   deleted between attempts, falls back to create. Bounded retries
     *   prevent unbounded ping-pong with concurrent deletes.
     */
    private void setMutedTrue(String userId, String seriesId, String nudgeType,
                              Map<String, AttributeValue> key, String now, boolean createFirst) {
        int attempts = 0;
        boolean tryCreate = createFirst;
        while (attempts < 4) {
            attempts++;
            try {
                if (tryCreate) {
                    UpdateItemRequest createRequest = UpdateItemRequest.builder()
                        .tableName(TABLE_NAME)
                        .key(key)
                        .updateExpression(
                            "SET mutedNudgeTypes = :map, userId = :userId, seriesId = :seriesId, "
                                + "itemType = :type, createdAt = :now, updatedAt = :now")
                        .conditionExpression("attribute_not_exists(pk)")
                        .expressionAttributeValues(Map.of(
                            ":map", AttributeValue.builder()
                                .m(Map.of(nudgeType, AttributeValue.builder().bool(true).build()))
                                .build(),
                            ":userId", AttributeValue.builder().s(userId).build(),
                            ":seriesId", AttributeValue.builder().s(seriesId).build(),
                            ":type", AttributeValue.builder().s("SERIES_NOTIFICATION_PREFERENCE").build(),
                            ":now", AttributeValue.builder().s(now).build()))
                        .build();
                    dynamoDbClient.updateItem(createRequest);
                } else {
                    UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                        .tableName(TABLE_NAME)
                        .key(key)
                        .updateExpression("SET mutedNudgeTypes.#k = :true, updatedAt = :now")
                        .conditionExpression("attribute_exists(pk)")
                        .expressionAttributeNames(Map.of("#k", nudgeType))
                        .expressionAttributeValues(Map.of(
                            ":true", AttributeValue.builder().bool(true).build(),
                            ":now", AttributeValue.builder().s(now).build()))
                        .build();
                    dynamoDbClient.updateItem(updateRequest);
                }
                return;
            } catch (ConditionalCheckFailedException e) {
                // Flip path and retry: create→update means row exists; update→create means row was deleted.
                tryCreate = !tryCreate;
            } catch (DynamoDbException e) {
                logger.error("Failed to set mute for user={} series={} nudgeType={}",
                    userId, seriesId, nudgeType, e);
                throw new RepositoryException("Failed to set mute", e);
            }
        }
        throw new RepositoryException(
            "Failed to set mute after retries due to concurrent contention: user=" + userId
                + " series=" + seriesId + " nudgeType=" + nudgeType, null);
    }

    @Override
    public void delete(String userId, String seriesId) {
        DeleteItemRequest request = DeleteItemRequest.builder()
            .tableName(TABLE_NAME)
            .key(Map.of(
                "pk", AttributeValue.builder().s(InviterKeyFactory.getUserPk(userId)).build(),
                "sk", AttributeValue.builder().s(InviterKeyFactory.getSeriesPrefSk(seriesId)).build()))
            .build();
        try {
            dynamoDbClient.deleteItem(request);
        } catch (DynamoDbException e) {
            logger.error("Failed to delete SeriesNotificationPreference for user={} series={}",
                userId, seriesId, e);
            throw new RepositoryException("Failed to delete SeriesNotificationPreference", e);
        }
    }
}
