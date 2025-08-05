package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.exception.*;
import com.bbthechange.inviter.util.QueryPerformanceTracker;
import com.bbthechange.inviter.util.InviterKeyFactory;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of GroupRepository using DynamoDB Enhanced Client.
 * Provides atomic operations and efficient query patterns with performance tracking.
 */
// @Repository - Disabled in favor of PolymorphicGroupRepositoryImpl
@Deprecated
public class GroupRepositoryImpl implements GroupRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(GroupRepositoryImpl.class);
    
    private final DynamoDbTable<BaseItem> inviterTable;
    private final DynamoDbClient dynamoDbClient;
    private final QueryPerformanceTracker performanceTracker;
    
    @Autowired
    public GroupRepositoryImpl(
            DynamoDbEnhancedClient dynamoDbEnhancedClient, 
            DynamoDbClient dynamoDbClient,
            QueryPerformanceTracker performanceTracker) {
        this.inviterTable = dynamoDbEnhancedClient.table("InviterTable", TableSchema.fromBean(BaseItem.class));
        this.dynamoDbClient = dynamoDbClient;
        this.performanceTracker = performanceTracker;
    }
    
    @Override
    public void createGroupWithFirstMember(Group group, GroupMembership membership) {
        try {
            // Atomic creation using TransactWriteItems
            TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                .transactItems(
                    // 1. Create group metadata
                    TransactWriteItem.builder()
                        .put(Put.builder()
                            .tableName("InviterTable")
                            .item(convertToAttributeValueMap(group))
                            .conditionExpression("attribute_not_exists(pk)")
                            .build())
                        .build(),
                    // 2. Add creator as first member  
                    TransactWriteItem.builder()
                        .put(Put.builder()
                            .tableName("InviterTable")
                            .item(convertToAttributeValueMap(membership))
                            .build())
                        .build()
                )
                .build();
                
            dynamoDbClient.transactWriteItems(request);
            logger.info("Created group {} with first member {}", group.getGroupId(), membership.getUserId());
            
        } catch (TransactionCanceledException e) {
            logger.error("Transaction failed for group creation: {}", e.getMessage());
            throw new TransactionFailedException("Failed to create group atomically", e);
        } catch (DynamoDbException e) {
            logger.error("DynamoDB error in group creation", e);
            throw new RepositoryException("Database operation failed", e);
        }
    }
    
    @Override
    public Group save(Group group) {
        return performanceTracker.trackQuery("saveGroup", "InviterTable", () -> {
            try {
                group.touch();
                inviterTable.putItem(group);
                return group;
            } catch (DynamoDbException e) {
                logger.error("Failed to save group {}", group.getGroupId(), e);
                throw new RepositoryException("Failed to save group", e);
            }
        });
    }
    
    @Override
    public Optional<Group> findById(String groupId) {
        return performanceTracker.trackQuery("findGroupById", "InviterTable", () -> {
            try {
                Key key = Key.builder()
                    .partitionValue(InviterKeyFactory.getGroupPk(groupId))
                    .sortValue(InviterKeyFactory.getMetadataSk())
                    .build();
                
                BaseItem item = inviterTable.getItem(key);
                if (item != null && InviterKeyFactory.isMetadata(item.getSk())) {
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
    public void delete(String groupId) {
        performanceTracker.trackQuery("deleteGroup", "InviterTable", () -> {
            try {
                Key key = Key.builder()
                    .partitionValue(InviterKeyFactory.getGroupPk(groupId))
                    .sortValue(InviterKeyFactory.getMetadataSk())
                    .build();
                
                inviterTable.deleteItem(key);
                logger.info("Deleted group {}", groupId);
                return null;
                
            } catch (DynamoDbException e) {
                logger.error("Failed to delete group {}", groupId, e);
                throw new RepositoryException("Failed to delete group", e);
            }
        });
    }
    
    @Override
    public GroupMembership addMember(GroupMembership membership) {
        return performanceTracker.trackQuery("addGroupMember", "InviterTable", () -> {
            try {
                inviterTable.putItem(membership);
                logger.info("Added member {} to group {}", membership.getUserId(), membership.getGroupId());
                return membership;
                
            } catch (DynamoDbException e) {
                logger.error("Failed to add member {} to group {}", membership.getUserId(), membership.getGroupId(), e);
                throw new RepositoryException("Failed to add group member", e);
            }
        });
    }
    
    @Override
    public void removeMember(String groupId, String userId) {
        performanceTracker.trackQuery("removeGroupMember", "InviterTable", () -> {
            try {
                Key key = Key.builder()
                    .partitionValue(InviterKeyFactory.getGroupPk(groupId))
                    .sortValue(InviterKeyFactory.getUserSk(userId))
                    .build();
                
                inviterTable.deleteItem(key);
                logger.info("Removed member {} from group {}", userId, groupId);
                return null;
                
            } catch (DynamoDbException e) {
                logger.error("Failed to remove member {} from group {}", userId, groupId, e);
                throw new RepositoryException("Failed to remove group member", e);
            }
        });
    }
    
    @Override
    public Optional<GroupMembership> findMembership(String groupId, String userId) {
        return performanceTracker.trackQuery("findGroupMembership", "InviterTable", () -> {
            try {
                Key key = Key.builder()
                    .partitionValue(InviterKeyFactory.getGroupPk(groupId))
                    .sortValue(InviterKeyFactory.getUserSk(userId))
                    .build();
                
                BaseItem item = inviterTable.getItem(key);
                if (item != null && InviterKeyFactory.isGroupMembership(item.getSk())) {
                    return Optional.of((GroupMembership) item);
                }
                return Optional.empty();
                
            } catch (DynamoDbException e) {
                logger.error("Failed to find membership for user {} in group {}", userId, groupId, e);
                throw new RepositoryException("Failed to retrieve group membership", e);
            }
        });
    }
    
    @Override
    public List<GroupMembership> findMembersByGroupId(String groupId) {
        return performanceTracker.trackQuery("findGroupMembers", "InviterTable", () -> {
            try {
                QueryConditional queryConditional = QueryConditional
                    .sortBeginsWith(Key.builder()
                        .partitionValue(InviterKeyFactory.getGroupPk(groupId))
                        .sortValue(InviterKeyFactory.USER_PREFIX)
                        .build());
                
                return inviterTable.query(QueryEnhancedRequest.builder()
                        .queryConditional(queryConditional)
                        .scanIndexForward(true)
                        .build())
                    .stream()
                    .flatMap(page -> page.items().stream())
                    .filter(item -> InviterKeyFactory.isGroupMembership(item.getSk()))
                    .map(item -> (GroupMembership) item)
                    .collect(Collectors.toList());
                    
            } catch (DynamoDbException e) {
                logger.error("Failed to find members for group {}", groupId, e);
                throw new RepositoryException("Failed to retrieve group members", e);
            }
        });
    }
    
    @Override
    public List<GroupMembership> findGroupsByUserId(String userId) {
        return performanceTracker.trackQuery("findGroupsByUserId", "InviterTable", () -> {
            try {
                // Single GSI query - returns denormalized groupName, no additional queries needed!
                QueryConditional queryConditional = QueryConditional
                    .keyEqualTo(Key.builder()
                        .partitionValue(InviterKeyFactory.getUserGsi1Pk(userId))
                        .build());
                
                return inviterTable.index("UserGroupIndex")
                    .query(QueryEnhancedRequest.builder()
                        .queryConditional(queryConditional)
                        .scanIndexForward(true)
                        .build())
                    .stream()
                    .flatMap(page -> page.items().stream())
                    .filter(item -> InviterKeyFactory.isGroupMembership(item.getSk()))
                    .map(item -> (GroupMembership) item)
                    .collect(Collectors.toList());
                    
            } catch (DynamoDbException e) {
                logger.error("Failed to find groups for user {}", userId, e);
                throw new RepositoryException("Failed to retrieve user groups", e);
            }
        });
    }
    
    @Override
    public void saveHangoutPointer(HangoutPointer pointer) {
        performanceTracker.trackQuery("saveHangoutPointer", "InviterTable", () -> {
            try {
                inviterTable.putItem(pointer);
                logger.debug("Saved hangout pointer {} for group {}", pointer.getHangoutId(), pointer.getGroupId());
                return null;
                
            } catch (DynamoDbException e) {
                logger.error("Failed to save hangout pointer {} for group {}", pointer.getHangoutId(), pointer.getGroupId(), e);
                throw new RepositoryException("Failed to save hangout pointer", e);
            }
        });
    }
    
    @Override
    public void updateHangoutPointer(String groupId, String hangoutId, Map<String, AttributeValue> updates) {
        performanceTracker.trackQuery("updateHangoutPointer", "InviterTable", () -> {
            try {
                // Build update expression from the updates map
                StringBuilder updateExpression = new StringBuilder("set ");
                StringBuilder nameBuilder = new StringBuilder();
                Map<String, String> expressionAttributeNames = new HashMap<>();
                
                int index = 0;
                for (String attributeName : updates.keySet()) {
                    if (index > 0) updateExpression.append(", ");
                    
                    String nameAlias = "#attr" + index;
                    String valueAlias = ":val" + index;
                    
                    updateExpression.append(nameAlias).append(" = ").append(valueAlias);
                    expressionAttributeNames.put(nameAlias, attributeName);
                    index++;
                }
                
                UpdateItemRequest request = UpdateItemRequest.builder()
                    .tableName("InviterTable") 
                    .key(Map.of(
                        "pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build(),
                        "sk", AttributeValue.builder().s(InviterKeyFactory.getHangoutSk(hangoutId)).build()
                    ))
                    .updateExpression(updateExpression.toString())
                    .expressionAttributeNames(expressionAttributeNames)
                    .expressionAttributeValues(updates)
                    .build();
                
                dynamoDbClient.updateItem(request);
                logger.debug("Updated hangout pointer {} for group {}", hangoutId, groupId);
                return null;
                
            } catch (DynamoDbException e) {
                logger.error("Failed to update hangout pointer {} for group {}", hangoutId, groupId, e);
                throw new RepositoryException("Failed to update hangout pointer", e);
            }
        });
    }
    
    @Override
    public void deleteHangoutPointer(String groupId, String hangoutId) {
        performanceTracker.trackQuery("deleteHangoutPointer", "InviterTable", () -> {
            try {
                Key key = Key.builder()
                    .partitionValue(InviterKeyFactory.getGroupPk(groupId))
                    .sortValue(InviterKeyFactory.getHangoutSk(hangoutId))
                    .build();
                
                inviterTable.deleteItem(key);
                logger.debug("Deleted hangout pointer {} for group {}", hangoutId, groupId);
                return null;
                
            } catch (DynamoDbException e) {
                logger.error("Failed to delete hangout pointer {} for group {}", hangoutId, groupId, e);
                throw new RepositoryException("Failed to delete hangout pointer", e);
            }
        });
    }
    
    @Override
    public List<HangoutPointer> findHangoutsByGroupId(String groupId) {
        return performanceTracker.trackQuery("findHangoutsByGroupId", "InviterTable", () -> {
            try {
                QueryConditional queryConditional = QueryConditional
                    .sortBeginsWith(Key.builder()
                        .partitionValue(InviterKeyFactory.getGroupPk(groupId))
                        .sortValue(InviterKeyFactory.HANGOUT_PREFIX)
                        .build());
                
                return inviterTable.query(QueryEnhancedRequest.builder()
                        .queryConditional(queryConditional)
                        .scanIndexForward(true)
                        .build())
                    .stream()
                    .flatMap(page -> page.items().stream())
                    .filter(item -> InviterKeyFactory.isHangoutPointer(item.getSk()))
                    .map(item -> (HangoutPointer) item)
                    .collect(Collectors.toList());
                    
            } catch (DynamoDbException e) {
                logger.error("Failed to find hangouts for group {}", groupId, e);
                throw new RepositoryException("Failed to retrieve group hangouts", e);
            }
        });
    }
    
    // Helper method for DynamoDB attribute conversion
    @SuppressWarnings("unchecked")
    private Map<String, AttributeValue> convertToAttributeValueMap(BaseItem item) {
        @SuppressWarnings("rawtypes")
        TableSchema schema = TableSchema.fromBean(item.getClass());
        return schema.itemToMap((Object) item, true);
    }
}