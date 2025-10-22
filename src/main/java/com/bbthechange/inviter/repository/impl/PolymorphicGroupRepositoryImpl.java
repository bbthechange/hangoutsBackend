package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.exception.RepositoryException;
import com.bbthechange.inviter.model.BaseItem;
import com.bbthechange.inviter.model.Group;
import com.bbthechange.inviter.model.GroupMembership;
import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.model.SeriesPointer;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.util.InviterKeyFactory;
import com.bbthechange.inviter.util.QueryPerformanceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Polymorphic implementation of GroupRepository that handles type-based deserialization
 * for the single-table design pattern using type discriminators.
 */
@Repository
@Primary
public class PolymorphicGroupRepositoryImpl implements GroupRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(PolymorphicGroupRepositoryImpl.class);
    private static final String TABLE_NAME = "InviterTable";
    
    private final DynamoDbClient dynamoDbClient;
    private final TableSchema<Group> groupSchema;
    private final TableSchema<GroupMembership> membershipSchema;
    private final TableSchema<HangoutPointer> hangoutSchema;
    private final TableSchema<SeriesPointer> seriesSchema;
    private final QueryPerformanceTracker queryTracker;
    
    @Autowired
    public PolymorphicGroupRepositoryImpl(DynamoDbClient dynamoDbClient, QueryPerformanceTracker queryTracker) {
        this.dynamoDbClient = dynamoDbClient;
        this.queryTracker = queryTracker;
        this.groupSchema = TableSchema.fromBean(Group.class);
        this.membershipSchema = TableSchema.fromBean(GroupMembership.class);
        this.hangoutSchema = TableSchema.fromBean(HangoutPointer.class);
        this.seriesSchema = TableSchema.fromBean(SeriesPointer.class);
    }
    
    /**
     * Deserialize a DynamoDB item based on its itemType discriminator.
     */
    private BaseItem deserializeItem(Map<String, AttributeValue> itemMap) {
        AttributeValue typeAttr = itemMap.get("itemType");
        if (typeAttr == null || typeAttr.s() == null) {
            // Fallback to SK pattern matching for backward compatibility
            AttributeValue skAttr = itemMap.get("sk");
            if (skAttr != null) {
                String sk = skAttr.s();
                if (InviterKeyFactory.isMetadata(sk)) {
                    return groupSchema.mapToItem(itemMap);
                } else if (InviterKeyFactory.isGroupMembership(sk)) {
                    return membershipSchema.mapToItem(itemMap);
                } else if (InviterKeyFactory.isHangoutPointer(sk)) {
                    return hangoutSchema.mapToItem(itemMap);
                }
            }
            throw new IllegalStateException("Missing itemType discriminator and unable to determine type from SK");
        }
        
        String itemType = typeAttr.s();
        switch (itemType) {
            case "GROUP":
                return groupSchema.mapToItem(itemMap);
            case "GROUP_MEMBERSHIP":
                return membershipSchema.mapToItem(itemMap);
            case "HANGOUT_POINTER":
                return hangoutSchema.mapToItem(itemMap);
            default:
                logger.warn("Unknown item type encountered: {}. Skipping deserialization.", itemType);
                return null;
        }
    }
    
    @Override
    public Optional<Group> findById(String groupId) {
        return queryTracker.trackQuery("GetItem", TABLE_NAME, () -> {
            try {
                GetItemRequest request = GetItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of(
                        "pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build(),
                        "sk", AttributeValue.builder().s(InviterKeyFactory.getMetadataSk()).build()
                    ))
                    .build();
                
                GetItemResponse response = dynamoDbClient.getItem(request);
                if (!response.hasItem()) {
                    return Optional.empty();
                }
                
                BaseItem item = deserializeItem(response.item());
                if (item instanceof Group) {
                    return Optional.of((Group) item);
                }
                return Optional.empty();
                
            } catch (DynamoDbException e) {
                logger.error("Failed to find group {}", groupId, e);
                throw new RepositoryException("Failed to retrieve group", e);
            }
        });
    }
    
    @Override
    public Group save(Group group) {
        return queryTracker.trackQuery("PutItem", TABLE_NAME, () -> {
            try {
                group.touch();
                Map<String, AttributeValue> itemMap = groupSchema.itemToMap(group, true);
                
                PutItemRequest request = PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(itemMap)
                    .build();
                
                dynamoDbClient.putItem(request);
                return group;
                
            } catch (DynamoDbException e) {
                logger.error("Failed to save group {}", group.getGroupId(), e);
                throw new RepositoryException("Failed to save group", e);
            }
        });
    }
    
    @Override
    public void delete(String groupId) {
        queryTracker.trackQuery("Query+BatchDelete", TABLE_NAME, () -> {
            try {
                // Step 1: Query all records for this group
                QueryRequest queryRequest = QueryRequest.builder()
                    .tableName(TABLE_NAME)
                    .keyConditionExpression("pk = :pk")
                    .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build()
                    ))
                    .build();
                
                QueryResponse queryResponse = dynamoDbClient.query(queryRequest);
                List<Map<String, AttributeValue>> items = queryResponse.items();
                
                if (items.isEmpty()) {
                    logger.info("No records found for group {}", groupId);
                    return null;
                }
                
                logger.info("Found {} records to delete for group {}", items.size(), groupId);
                
                // Step 2: Convert to delete requests
                List<WriteRequest> deleteRequests = items.stream()
                    .map(item -> WriteRequest.builder()
                        .deleteRequest(DeleteRequest.builder()
                            .key(Map.of(
                                "pk", item.get("pk"),
                                "sk", item.get("sk")
                            ))
                            .build())
                        .build())
                    .collect(Collectors.toList());
                
                // Step 3: Batch delete in chunks of 25 (DynamoDB limit)
                for (int i = 0; i < deleteRequests.size(); i += 25) {
                    int endIndex = Math.min(i + 25, deleteRequests.size());
                    List<WriteRequest> batch = deleteRequests.subList(i, endIndex);
                    
                    BatchWriteItemRequest batchRequest = BatchWriteItemRequest.builder()
                        .requestItems(Map.of(TABLE_NAME, batch))
                        .build();
                    
                    BatchWriteItemResponse batchResponse = dynamoDbClient.batchWriteItem(batchRequest);
                    
                    // Handle unprocessed items (rare but possible)
                    if (batchResponse.hasUnprocessedItems() && !batchResponse.unprocessedItems().isEmpty()) {
                        logger.warn("Some items were not processed in batch delete for group {}", groupId);
                        // Could implement retry logic here if needed
                    }
                }
                
                logger.info("Successfully deleted all {} records for group {}", items.size(), groupId);
                
            } catch (DynamoDbException e) {
                logger.error("Failed to delete group {} completely", groupId, e);
                throw new RepositoryException("Failed to delete group completely", e);
            }
            return null;
        });
    }
    
    @Override
    public List<GroupMembership> findMembersByGroupId(String groupId) {
        return queryTracker.trackQuery("Query", TABLE_NAME, () -> {
            try {
                QueryRequest request = QueryRequest.builder()
                    .tableName(TABLE_NAME)
                    .keyConditionExpression("pk = :pk AND begins_with(sk, :sk)")
                    .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build(),
                        ":sk", AttributeValue.builder().s(InviterKeyFactory.USER_PREFIX).build()
                    ))
                    .build();
                
                QueryResponse response = dynamoDbClient.query(request);
                
                return response.items().stream()
                    .map(this::deserializeItem)
                    .filter(item -> item instanceof GroupMembership)
                    .map(item -> (GroupMembership) item)
                    .collect(Collectors.toList());
                    
            } catch (DynamoDbException e) {
                logger.error("Failed to find members for group {}", groupId, e);
                throw new RepositoryException("Failed to retrieve group members", e);
            }
        });
    }
    
    @Override
    public GroupMembership addMember(GroupMembership membership) {
        return queryTracker.trackQuery("PutItem", TABLE_NAME, () -> {
            try {
                membership.touch();
                Map<String, AttributeValue> itemMap = membershipSchema.itemToMap(membership, true);
                
                PutItemRequest request = PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(itemMap)
                    .build();
                
                dynamoDbClient.putItem(request);
                return membership;
                
            } catch (DynamoDbException e) {
                logger.error("Failed to add member {} to group {}", 
                    membership.getUserId(), membership.getGroupId(), e);
                throw new RepositoryException("Failed to add group member", e);
            }
        });
    }
    
    @Override
    public void removeMember(String groupId, String userId) {
        queryTracker.trackQuery("DeleteItem", TABLE_NAME, () -> {
            try {
                DeleteItemRequest request = DeleteItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of(
                        "pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build(),
                        "sk", AttributeValue.builder().s(InviterKeyFactory.getUserSk(userId)).build()
                    ))
                    .build();
                
                dynamoDbClient.deleteItem(request);
                
            } catch (DynamoDbException e) {
                logger.error("Failed to remove member {} from group {}", userId, groupId, e);
                throw new RepositoryException("Failed to remove group member", e);
            }
            return null;
        });
    }
    
    @Override
    public Optional<GroupMembership> findMembership(String groupId, String userId) {
        return queryTracker.trackQuery("GetItem", TABLE_NAME, () -> {
            try {
                GetItemRequest request = GetItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of(
                        "pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build(),
                        "sk", AttributeValue.builder().s(InviterKeyFactory.getUserSk(userId)).build()
                    ))
                    .build();
                
                GetItemResponse response = dynamoDbClient.getItem(request);
                if (!response.hasItem()) {
                    return Optional.empty();
                }
                
                BaseItem item = deserializeItem(response.item());
                if (item instanceof GroupMembership) {
                    return Optional.of((GroupMembership) item);
                }
                return Optional.empty();
                
            } catch (DynamoDbException e) {
                logger.error("Failed to find membership for user {} in group {}", userId, groupId, e);
                throw new RepositoryException("Failed to retrieve membership", e);
            }
        });
    }

    @Override
    public Boolean isUserMemberOfGroup(String groupId, String userId) {
        return queryTracker.trackQuery("GetItem", TABLE_NAME, () -> {
            try {
                QueryRequest request = QueryRequest.builder()
                        .tableName(TABLE_NAME)
                        .keyConditionExpression("pk = :pk AND sk = :sk")
                        .expressionAttributeValues(Map.of(
                                ":pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build(),
                                ":sk", AttributeValue.builder().s(InviterKeyFactory.getUserSk(userId)).build()
                        ))
                        .select(Select.COUNT)
                        .build();

                QueryResponse response = dynamoDbClient.query(request);
                return response.count() > 0;

            } catch (DynamoDbException e) {
                logger.error("Failed to find membership for user {} in group {}", userId, groupId, e);
                throw new RepositoryException("Failed to retrieve membership", e);
            }
        });
    }

    @Override
    public List<GroupMembership> findGroupsByUserId(String userId) {
        return queryTracker.trackQuery("Query", TABLE_NAME, () -> {
            try {
                QueryRequest request = QueryRequest.builder()
                    .tableName(TABLE_NAME)
                    .indexName("UserGroupIndex")
                    .keyConditionExpression("gsi1pk = :gsi1pk")
                    .expressionAttributeValues(Map.of(
                        ":gsi1pk", AttributeValue.builder().s(InviterKeyFactory.getUserGsi1Pk(userId)).build()
                    ))
                    .build();

                QueryResponse response = dynamoDbClient.query(request);

                return response.items().stream()
                    .map(this::deserializeItem)
                    .filter(item -> item instanceof GroupMembership)
                    .map(item -> (GroupMembership) item)
                    .collect(Collectors.toList());

            } catch (DynamoDbException e) {
                logger.error("Failed to find groups for user {}", userId, e);
                throw new RepositoryException("Failed to retrieve user groups", e);
            }
        });
    }

    @Override
    public Optional<GroupMembership> findMembershipByToken(String token) {
        return queryTracker.trackQuery("Query", TABLE_NAME, () -> {
            try {
                QueryRequest request = QueryRequest.builder()
                    .tableName(TABLE_NAME)
                    .indexName("CalendarTokenIndex")
                    .keyConditionExpression("gsi2pk = :gsi2pk")
                    .expressionAttributeValues(Map.of(
                        ":gsi2pk", AttributeValue.builder().s("TOKEN#" + token).build()
                    ))
                    .limit(1)
                    .build();

                QueryResponse response = dynamoDbClient.query(request);

                return response.items().stream()
                    .map(this::deserializeItem)
                    .filter(item -> item instanceof GroupMembership)
                    .map(item -> (GroupMembership) item)
                    .findFirst();

            } catch (DynamoDbException e) {
                logger.error("Failed to find membership by token", e);
                throw new RepositoryException("Failed to retrieve membership by token", e);
            }
        });
    }
    
    @Override
    public List<HangoutPointer> findHangoutsByGroupId(String groupId) {
        return queryTracker.trackQuery("Query", TABLE_NAME, () -> {
            try {
                QueryRequest request = QueryRequest.builder()
                    .tableName(TABLE_NAME)
                    .keyConditionExpression("pk = :pk AND begins_with(sk, :sk)")
                    .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build(),
                        ":sk", AttributeValue.builder().s(InviterKeyFactory.HANGOUT_PREFIX).build()
                    ))
                    .build();
                
                QueryResponse response = dynamoDbClient.query(request);
                
                return response.items().stream()
                    .map(this::deserializeItem)
                    .filter(item -> item instanceof HangoutPointer)
                    .map(item -> (HangoutPointer) item)
                    .collect(Collectors.toList());
                    
            } catch (DynamoDbException e) {
                logger.error("Failed to find hangouts for group {}", groupId, e);
                throw new RepositoryException("Failed to retrieve group hangouts", e);
            }
        });
    }
    
    @Override
    public void saveHangoutPointer(HangoutPointer pointer) {
        queryTracker.trackQuery("PutItem", TABLE_NAME, () -> {
            try {
                pointer.touch();
                Map<String, AttributeValue> itemMap = hangoutSchema.itemToMap(pointer, true);
                
                PutItemRequest request = PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(itemMap)
                    .build();
                
                dynamoDbClient.putItem(request);
                
            } catch (DynamoDbException e) {
                logger.error("Failed to save hangout pointer {} for group {}", 
                    pointer.getHangoutId(), pointer.getGroupId(), e);
                throw new RepositoryException("Failed to save hangout pointer", e);
            }
            return null;
        });
    }

    @Override
    public Optional<HangoutPointer> findHangoutPointer(String groupId, String hangoutId) {
        return queryTracker.trackQuery("GetItem", TABLE_NAME, () -> {
            try {
                GetItemRequest request = GetItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of(
                        "pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build(),
                        "sk", AttributeValue.builder().s(InviterKeyFactory.getHangoutSk(hangoutId)).build()
                    ))
                    .build();

                GetItemResponse response = dynamoDbClient.getItem(request);
                if (!response.hasItem()) {
                    return Optional.empty();
                }

                BaseItem item = deserializeItem(response.item());
                if (item instanceof HangoutPointer) {
                    return Optional.of((HangoutPointer) item);
                }
                return Optional.empty();

            } catch (DynamoDbException e) {
                logger.error("Failed to find hangout pointer {} for group {}", hangoutId, groupId, e);
                throw new RepositoryException("Failed to retrieve hangout pointer", e);
            }
        });
    }

    @Override
    public void saveSeriesPointer(SeriesPointer pointer) {
        queryTracker.trackQuery("PutItem", TABLE_NAME, () -> {
            try {
                pointer.touch();
                Map<String, AttributeValue> itemMap = seriesSchema.itemToMap(pointer, true);
                
                PutItemRequest request = PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(itemMap)
                    .build();
                
                dynamoDbClient.putItem(request);
                
            } catch (DynamoDbException e) {
                logger.error("Failed to save series pointer {} for group {}", 
                    pointer.getSeriesId(), pointer.getGroupId(), e);
                throw new RepositoryException("Failed to save series pointer", e);
            }
            return null;
        });
    }
    
    @Override
    public void deleteHangoutPointer(String groupId, String hangoutId) {
        queryTracker.trackQuery("DeleteItem", TABLE_NAME, () -> {
            try {
                DeleteItemRequest request = DeleteItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of(
                        "pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build(),
                        "sk", AttributeValue.builder().s(InviterKeyFactory.getHangoutSk(hangoutId)).build()
                    ))
                    .build();
                
                dynamoDbClient.deleteItem(request);
                
            } catch (DynamoDbException e) {
                logger.error("Failed to delete hangout pointer {} from group {}", hangoutId, groupId, e);
                throw new RepositoryException("Failed to delete hangout pointer", e);
            }
            return null;
        });
    }
    
    @Override
    public void createGroupWithFirstMember(Group group, GroupMembership membership) {
        queryTracker.trackQuery("TransactWriteItems", TABLE_NAME, () -> {
            try {
                // Prepare the group item
                Map<String, AttributeValue> groupItem = groupSchema.itemToMap(group, true);
                
                // Prepare the membership item
                Map<String, AttributeValue> membershipItem = membershipSchema.itemToMap(membership, true);
                
                // Create the transaction
                TransactWriteItemsRequest transactRequest = TransactWriteItemsRequest.builder()
                    .transactItems(
                        TransactWriteItem.builder()
                            .put(Put.builder()
                                .tableName(TABLE_NAME)
                                .item(groupItem)
                                .build())
                            .build(),
                        TransactWriteItem.builder()
                            .put(Put.builder()
                                .tableName(TABLE_NAME)
                                .item(membershipItem)
                                .build())
                            .build()
                    )
                    .build();
                
                dynamoDbClient.transactWriteItems(transactRequest);
                
            } catch (DynamoDbException e) {
                logger.error("Failed to create group {} with first member {}", 
                    group.getGroupId(), membership.getUserId(), e);
                throw new RepositoryException("Failed to create group with first member", e);
            }
            return null;
        });
    }
    
    @Override
    public void updateHangoutPointer(String groupId, String hangoutId, Map<String, AttributeValue> updates) {
        queryTracker.trackQuery("UpdateItem", TABLE_NAME, () -> {
            try {
                // Build update expression from the provided updates
                StringBuilder updateExpression = new StringBuilder("SET ");
                Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
                
                int index = 0;
                for (Map.Entry<String, AttributeValue> entry : updates.entrySet()) {
                    if (index > 0) {
                        updateExpression.append(", ");
                    }
                    String placeholder = ":val" + index;
                    updateExpression.append(entry.getKey()).append(" = ").append(placeholder);
                    expressionAttributeValues.put(placeholder, entry.getValue());
                    index++;
                }
                
                // Add updated timestamp
                updateExpression.append(", updatedAt = :updatedAt");
                expressionAttributeValues.put(":updatedAt", 
                    AttributeValue.builder().s(Instant.now().toString()).build());
                
                UpdateItemRequest request = UpdateItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of(
                        "pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build(),
                        "sk", AttributeValue.builder().s(InviterKeyFactory.getHangoutSk(hangoutId)).build()
                    ))
                    .updateExpression(updateExpression.toString())
                    .expressionAttributeValues(expressionAttributeValues)
                    .build();
                
                dynamoDbClient.updateItem(request);
                
            } catch (DynamoDbException e) {
                logger.error("Failed to update hangout pointer {} in group {}", hangoutId, groupId, e);
                throw new RepositoryException("Failed to update hangout pointer", e);
            }
            return null;
        });
    }
    
    @Override
    public void atomicallyUpdateParticipantCount(String groupId, String hangoutId, int delta) {
        if (delta == 0) {
            return; // No change needed
        }

        queryTracker.trackQuery("UpdateItem", TABLE_NAME, () -> {
            try {
                // Build the key for the HangoutPointer
                Map<String, AttributeValue> key = Map.of(
                    "pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build(),
                    "sk", AttributeValue.builder().s(InviterKeyFactory.getHangoutSk(hangoutId)).build()
                );

                // Build atomic counter update expression
                UpdateItemRequest request = UpdateItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(key)
                    .updateExpression("SET participantCount = if_not_exists(participantCount, :zero) + :delta, updatedAt = :timestamp")
                    .expressionAttributeValues(Map.of(
                        ":delta", AttributeValue.builder().n(String.valueOf(delta)).build(),
                        ":zero", AttributeValue.builder().n("0").build(),
                        ":timestamp", AttributeValue.builder().s(Instant.now().toString()).build()
                    ))
                    .build();

                dynamoDbClient.updateItem(request);
                logger.debug("Atomically updated participantCount by {} for hangout {} in group {}",
                            delta, hangoutId, groupId);

            } catch (DynamoDbException e) {
                logger.error("Failed to atomically update participant count for hangout {} in group {}",
                            hangoutId, groupId, e);
                throw new RepositoryException("Failed to update participant count", e);
            }

            return null;
        });
    }

    @Override
    public void updateMembershipGroupNames(String groupId, String newGroupName) {
        queryTracker.trackQuery("UpdateMembershipGroupNames", TABLE_NAME, () -> {
            try {
                // First, get all membership records for this group
                List<GroupMembership> memberships = findMembersByGroupId(groupId);

                if (memberships.isEmpty()) {
                    logger.debug("No memberships to update for group {}", groupId);
                    return null;
                }

                // DynamoDB TransactWriteItems supports up to 100 items per transaction
                // For larger groups, we need to batch the updates
                int batchSize = 25; // Conservative batch size to stay well under limits
                String timestamp = Instant.now().toString();

                for (int i = 0; i < memberships.size(); i += batchSize) {
                    List<GroupMembership> batch = memberships.subList(i,
                        Math.min(i + batchSize, memberships.size()));

                    // Build transaction items for this batch
                    List<TransactWriteItem> transactItems = batch.stream()
                        .map(membership -> TransactWriteItem.builder()
                            .update(Update.builder()
                                .tableName(TABLE_NAME)
                                .key(Map.of(
                                    "pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build(),
                                    "sk", AttributeValue.builder().s(InviterKeyFactory.getUserSk(membership.getUserId())).build()
                                ))
                                .updateExpression("SET groupName = :newName, updatedAt = :timestamp")
                                .expressionAttributeValues(Map.of(
                                    ":newName", AttributeValue.builder().s(newGroupName).build(),
                                    ":timestamp", AttributeValue.builder().s(timestamp).build()
                                ))
                                .build())
                            .build())
                        .collect(Collectors.toList());

                    // Execute the batch transaction
                    TransactWriteItemsRequest transactRequest = TransactWriteItemsRequest.builder()
                        .transactItems(transactItems)
                        .build();

                    dynamoDbClient.transactWriteItems(transactRequest);
                    logger.debug("Updated batch of {} memberships for group {}", batch.size(), groupId);
                }

                logger.info("Updated {} membership records for group {} with new name {} in {} batch(es)",
                          memberships.size(), groupId, newGroupName, (memberships.size() + batchSize - 1) / batchSize);

            } catch (DynamoDbException e) {
                logger.error("Failed to update membership group names for group {}", groupId, e);
                throw new RepositoryException("Failed to update membership group names", e);
            }
            return null;
        });
    }

    @Override
    public void updateMembershipGroupImagePaths(String groupId, String mainImagePath, String backgroundImagePath) {
        queryTracker.trackQuery("UpdateMembershipGroupImagePaths", TABLE_NAME, () -> {
            try {
                // First, get all membership records for this group
                List<GroupMembership> memberships = findMembersByGroupId(groupId);
                if (memberships.isEmpty()) {
                    logger.debug("No memberships to update for group {}", groupId);
                    return null;
                }

                // DynamoDB TransactWriteItems supports up to 100 items per transaction
                // For larger groups, we need to batch the updates
                int batchSize = 25; // Conservative batch size to stay well under limits
                String timestamp = Instant.now().toString();

                for (int i = 0; i < memberships.size(); i += batchSize) {
                    List<GroupMembership> batch = memberships.subList(i,
                        Math.min(i + batchSize, memberships.size()));

                    // Build transaction items for this batch
                    List<TransactWriteItem> transactItems = batch.stream()
                        .map(membership -> TransactWriteItem.builder()
                            .update(Update.builder()
                                .tableName(TABLE_NAME)
                                .key(Map.of(
                                    "pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build(),
                                    "sk", AttributeValue.builder().s(InviterKeyFactory.getUserSk(membership.getUserId())).build()
                                ))
                                .updateExpression("SET groupMainImagePath = :mainImagePath, groupBackgroundImagePath = :backgroundImagePath, updatedAt = :timestamp")
                                .expressionAttributeValues(Map.of(
                                    ":mainImagePath", AttributeValue.builder().s(mainImagePath != null ? mainImagePath : "").build(),
                                    ":backgroundImagePath", AttributeValue.builder().s(backgroundImagePath != null ? backgroundImagePath : "").build(),
                                    ":timestamp", AttributeValue.builder().s(timestamp).build()
                                ))
                                .build())
                            .build())
                        .collect(Collectors.toList());

                    // Execute the batch transaction
                    TransactWriteItemsRequest transactRequest = TransactWriteItemsRequest.builder()
                        .transactItems(transactItems)
                        .build();

                    dynamoDbClient.transactWriteItems(transactRequest);
                    logger.debug("Updated batch of {} memberships for group {}", batch.size(), groupId);
                }

                logger.info("Updated {} membership records for group {} with new image paths in {} batch(es)",
                          memberships.size(), groupId, (memberships.size() + batchSize - 1) / batchSize);

            } catch (DynamoDbException e) {
                logger.error("Failed to update membership group image paths for group {}", groupId, e);
                throw new RepositoryException("Failed to update membership group image paths", e);
            }
            return null;
        });
    }

    @Override
    public void updateMembershipUserImagePath(String userId, String mainImagePath) {
        queryTracker.trackQuery("UpdateMembershipUserImagePath", TABLE_NAME, () -> {
            try {
                // First, get all membership records for this user
                List<GroupMembership> memberships = findGroupsByUserId(userId);
                if (memberships.isEmpty()) {
                    logger.debug("No memberships to update for user {}", userId);
                    return null;
                }

                // DynamoDB TransactWriteItems supports up to 100 items per transaction
                // For larger member sets, we need to batch the updates
                int batchSize = 25; // Conservative batch size to stay well under limits
                String timestamp = Instant.now().toString();

                for (int i = 0; i < memberships.size(); i += batchSize) {
                    List<GroupMembership> batch = memberships.subList(i,
                        Math.min(i + batchSize, memberships.size()));

                    // Build transaction items for this batch
                    List<TransactWriteItem> transactItems = batch.stream()
                        .map(membership -> TransactWriteItem.builder()
                            .update(Update.builder()
                                .tableName(TABLE_NAME)
                                .key(Map.of(
                                    "pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(membership.getGroupId())).build(),
                                    "sk", AttributeValue.builder().s(InviterKeyFactory.getUserSk(userId)).build()
                                ))
                                .updateExpression("SET userMainImagePath = :mainImagePath, updatedAt = :timestamp")
                                .expressionAttributeValues(Map.of(
                                    ":mainImagePath", AttributeValue.builder().s(mainImagePath != null ? mainImagePath : "").build(),
                                    ":timestamp", AttributeValue.builder().s(timestamp).build()
                                ))
                                .build())
                            .build())
                        .collect(Collectors.toList());

                    // Execute the batch transaction
                    TransactWriteItemsRequest transactRequest = TransactWriteItemsRequest.builder()
                        .transactItems(transactItems)
                        .build();

                    dynamoDbClient.transactWriteItems(transactRequest);
                    logger.debug("Updated batch of {} memberships for user {}", batch.size(), userId);
                }

                logger.info("Updated {} membership records for user {} with new image path in {} batch(es)",
                          memberships.size(), userId, (memberships.size() + batchSize - 1) / batchSize);

            } catch (DynamoDbException e) {
                logger.error("Failed to update membership user image path for user {}", userId, e);
                throw new RepositoryException("Failed to update membership user image path", e);
            }
            return null;
        });
    }
}