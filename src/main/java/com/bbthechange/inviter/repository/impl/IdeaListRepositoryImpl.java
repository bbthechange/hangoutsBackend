package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.exception.RepositoryException;
import com.bbthechange.inviter.model.IdeaList;
import com.bbthechange.inviter.model.IdeaListMember;
import com.bbthechange.inviter.repository.IdeaListRepository;
import com.bbthechange.inviter.util.InviterKeyFactory;
import com.bbthechange.inviter.util.QueryPerformanceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of IdeaListRepository using DynamoDB Enhanced Client.
 * Handles polymorphic deserialization for the single-table design pattern.
 */
@Repository
public class IdeaListRepositoryImpl implements IdeaListRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(IdeaListRepositoryImpl.class);
    private static final String TABLE_NAME = "InviterTable";
    
    private final DynamoDbClient dynamoDbClient;
    private final TableSchema<IdeaList> ideaListSchema;
    private final TableSchema<IdeaListMember> ideaMemberSchema;
    private final QueryPerformanceTracker queryTracker;
    
    @Autowired
    public IdeaListRepositoryImpl(DynamoDbClient dynamoDbClient, QueryPerformanceTracker queryTracker) {
        this.dynamoDbClient = dynamoDbClient;
        this.queryTracker = queryTracker;
        this.ideaListSchema = TableSchema.fromBean(IdeaList.class);
        this.ideaMemberSchema = TableSchema.fromBean(IdeaListMember.class);
    }
    
    @Override
    public IdeaList saveIdeaList(IdeaList ideaList) {
        try {
            Map<String, AttributeValue> item = ideaListSchema.itemToMap(ideaList, false);
            
            PutItemRequest request = PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(item)
                    .build();
                    
            dynamoDbClient.putItem(request);
            logger.debug("Saved idea list: {}", ideaList.getListId());
            return ideaList;
            
        } catch (Exception e) {
            logger.error("Error saving idea list: {}", ideaList.getListId(), e);
            throw new RepositoryException("Failed to save idea list", e);
        }
    }
    
    @Override
    public Optional<IdeaList> findIdeaListById(String groupId, String listId) {
        try {
            GetItemRequest request = GetItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of(
                            "pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build(),
                            "sk", AttributeValue.builder().s(InviterKeyFactory.getIdeaListSk(listId)).build()
                    ))
                    .build();
                    
            GetItemResponse response = dynamoDbClient.getItem(request);
            
            if (response.hasItem()) {
                IdeaList ideaList = ideaListSchema.mapToItem(response.item());
                return Optional.of(ideaList);
            }
            
            return Optional.empty();
            
        } catch (Exception e) {
            logger.error("Error finding idea list: {} in group: {}", listId, groupId, e);
            throw new RepositoryException("Failed to find idea list", e);
        }
    }
    
    @Override
    public void deleteIdeaList(String groupId, String listId) {
        try {
            DeleteItemRequest request = DeleteItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of(
                            "pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build(),
                            "sk", AttributeValue.builder().s(InviterKeyFactory.getIdeaListSk(listId)).build()
                    ))
                    .build();
                    
            dynamoDbClient.deleteItem(request);
            logger.debug("Deleted idea list: {} from group: {}", listId, groupId);
            
        } catch (Exception e) {
            logger.error("Error deleting idea list: {} from group: {}", listId, groupId, e);
            throw new RepositoryException("Failed to delete idea list", e);
        }
    }
    
    @Override
    public IdeaListMember saveIdeaListMember(IdeaListMember member) {
        try {
            Map<String, AttributeValue> item = ideaMemberSchema.itemToMap(member, false);
            
            PutItemRequest request = PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(item)
                    .build();
                    
            dynamoDbClient.putItem(request);
            logger.debug("Saved idea list member: {} to list: {}", member.getIdeaId(), member.getListId());
            return member;
            
        } catch (Exception e) {
            logger.error("Error saving idea list member: {}", member.getIdeaId(), e);
            throw new RepositoryException("Failed to save idea list member", e);
        }
    }
    
    @Override
    public Optional<IdeaListMember> findIdeaListMemberById(String groupId, String listId, String ideaId) {
        try {
            GetItemRequest request = GetItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of(
                            "pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build(),
                            "sk", AttributeValue.builder().s(InviterKeyFactory.getIdeaListMemberSk(listId, ideaId)).build()
                    ))
                    .build();
                    
            GetItemResponse response = dynamoDbClient.getItem(request);
            
            if (response.hasItem()) {
                IdeaListMember member = ideaMemberSchema.mapToItem(response.item());
                return Optional.of(member);
            }
            
            return Optional.empty();
            
        } catch (Exception e) {
            logger.error("Error finding idea list member: {} in list: {} group: {}", ideaId, listId, groupId, e);
            throw new RepositoryException("Failed to find idea list member", e);
        }
    }
    
    @Override
    public void deleteIdeaListMember(String groupId, String listId, String ideaId) {
        try {
            DeleteItemRequest request = DeleteItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of(
                            "pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build(),
                            "sk", AttributeValue.builder().s(InviterKeyFactory.getIdeaListMemberSk(listId, ideaId)).build()
                    ))
                    .build();
                    
            dynamoDbClient.deleteItem(request);
            logger.debug("Deleted idea list member: {} from list: {} group: {}", ideaId, listId, groupId);
            
        } catch (Exception e) {
            logger.error("Error deleting idea list member: {} from list: {} group: {}", ideaId, listId, groupId, e);
            throw new RepositoryException("Failed to delete idea list member", e);
        }
    }
    
    @Override
    public List<IdeaList> findAllIdeaListsWithMembersByGroupId(String groupId) {
        return queryTracker.trackQuery("findAllIdeaListsWithMembersByGroupId", TABLE_NAME, () -> {
            try {
                // Query for all idea list items in the group
                QueryRequest request = QueryRequest.builder()
                        .tableName(TABLE_NAME)
                        .keyConditionExpression("pk = :pk AND begins_with(sk, :skPrefix)")
                        .expressionAttributeValues(Map.of(
                                ":pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build(),
                                ":skPrefix", AttributeValue.builder().s(InviterKeyFactory.getIdeaListQueryPrefix()).build()
                        ))
                        .build();
                        
                QueryResponse response = dynamoDbClient.query(request);
                
                // Separate idea lists from members
                Map<String, IdeaList> ideaListsMap = new HashMap<>();
                Map<String, List<IdeaListMember>> membersMap = new HashMap<>();
                
                for (Map<String, AttributeValue> item : response.items()) {
                    String sortKey = item.get("sk").s();
                    
                    if (InviterKeyFactory.isIdeaList(sortKey)) {
                        IdeaList ideaList = ideaListSchema.mapToItem(item);
                        ideaListsMap.put(ideaList.getListId(), ideaList);
                        membersMap.put(ideaList.getListId(), new ArrayList<>());
                    } else if (InviterKeyFactory.isIdeaListMember(sortKey)) {
                        IdeaListMember member = ideaMemberSchema.mapToItem(item);
                        membersMap.computeIfAbsent(member.getListId(), k -> new ArrayList<>()).add(member);
                    }
                }
                
                // Create result list with populated members
                List<IdeaList> result = ideaListsMap.values().stream()
                        .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt())) // Most recent first
                        .collect(Collectors.toList());
                
                logger.debug("Found {} idea lists with members for group: {}", result.size(), groupId);
                return result;
                
            } catch (Exception e) {
                logger.error("Error finding idea lists for group: {}", groupId, e);
                throw new RepositoryException("Failed to find idea lists for group", e);
            }
        });
    }
    
    @Override
    public Optional<IdeaList> findIdeaListWithMembersById(String groupId, String listId) {
        return queryTracker.trackQuery("findIdeaListWithMembersById", TABLE_NAME, () -> {
            try {
                // Query for idea list and its members
                QueryRequest request = QueryRequest.builder()
                        .tableName(TABLE_NAME)
                        .keyConditionExpression("pk = :pk AND begins_with(sk, :skPrefix)")
                        .expressionAttributeValues(Map.of(
                                ":pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build(),
                                ":skPrefix", AttributeValue.builder().s(InviterKeyFactory.getIdeaListPrefix(listId)).build()
                        ))
                        .build();
                        
                QueryResponse response = dynamoDbClient.query(request);
                
                if (response.items().isEmpty()) {
                    return Optional.empty();
                }
                
                IdeaList ideaList = null;
                List<IdeaListMember> members = new ArrayList<>();
                
                for (Map<String, AttributeValue> item : response.items()) {
                    String sortKey = item.get("sk").s();
                    
                    if (InviterKeyFactory.isIdeaList(sortKey)) {
                        ideaList = ideaListSchema.mapToItem(item);
                    } else if (InviterKeyFactory.isIdeaListMember(sortKey)) {
                        members.add(ideaMemberSchema.mapToItem(item));
                    }
                }
                
                if (ideaList != null) {
                    logger.debug("Found idea list: {} with {} members", listId, members.size());
                    return Optional.of(ideaList);
                }
                
                return Optional.empty();
                
            } catch (Exception e) {
                logger.error("Error finding idea list with members: {} in group: {}", listId, groupId, e);
                throw new RepositoryException("Failed to find idea list with members", e);
            }
        });
    }
    
    @Override
    public void deleteIdeaListWithAllMembers(String groupId, String listId) {
        queryTracker.trackQuery("deleteIdeaListWithAllMembers", TABLE_NAME, () -> {
            try {
                // First query to get all items to delete
                QueryRequest queryRequest = QueryRequest.builder()
                        .tableName(TABLE_NAME)
                        .keyConditionExpression("pk = :pk AND begins_with(sk, :skPrefix)")
                        .expressionAttributeValues(Map.of(
                                ":pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build(),
                                ":skPrefix", AttributeValue.builder().s(InviterKeyFactory.getIdeaListPrefix(listId)).build()
                        ))
                        .build();
                        
                QueryResponse queryResponse = dynamoDbClient.query(queryRequest);
                
                if (queryResponse.items().isEmpty()) {
                    logger.warn("No items found to delete for idea list: {} in group: {}", listId, groupId);
                    return null;
                }
                
                // Batch delete all items
                List<WriteRequest> deleteRequests = queryResponse.items().stream()
                        .map(item -> WriteRequest.builder()
                                .deleteRequest(DeleteRequest.builder()
                                        .key(Map.of(
                                                "pk", item.get("pk"),
                                                "sk", item.get("sk")
                                        ))
                                        .build())
                                .build())
                        .collect(Collectors.toList());
                
                BatchWriteItemRequest batchRequest = BatchWriteItemRequest.builder()
                        .requestItems(Map.of(TABLE_NAME, deleteRequests))
                        .build();
                        
                dynamoDbClient.batchWriteItem(batchRequest);
                logger.debug("Deleted idea list: {} with {} items from group: {}", listId, deleteRequests.size(), groupId);
                return null;
                
            } catch (Exception e) {
                logger.error("Error deleting idea list with members: {} from group: {}", listId, groupId, e);
                throw new RepositoryException("Failed to delete idea list with all members", e);
            }
        });
    }
    
    @Override
    public boolean ideaListExists(String groupId, String listId) {
        try {
            Optional<IdeaList> ideaList = findIdeaListById(groupId, listId);
            return ideaList.isPresent();
        } catch (Exception e) {
            logger.error("Error checking if idea list exists: {} in group: {}", listId, groupId, e);
            return false;
        }
    }
    
    @Override
    public List<IdeaListMember> findMembersByListId(String groupId, String listId) {
        return queryTracker.trackQuery("findMembersByListId", TABLE_NAME, () -> {
            try {
                QueryRequest request = QueryRequest.builder()
                        .tableName(TABLE_NAME)
                        .keyConditionExpression("pk = :pk AND begins_with(sk, :skPrefix)")
                        .filterExpression("contains(sk, :ideaPrefix)")
                        .expressionAttributeValues(Map.of(
                                ":pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build(),
                                ":skPrefix", AttributeValue.builder().s(InviterKeyFactory.getIdeaListPrefix(listId)).build(),
                                ":ideaPrefix", AttributeValue.builder().s("#" + InviterKeyFactory.IDEA_PREFIX + "#").build()
                        ))
                        .build();
                        
                QueryResponse response = dynamoDbClient.query(request);
                
                List<IdeaListMember> members = response.items().stream()
                        .map(item -> ideaMemberSchema.mapToItem(item))
                        .sorted((a, b) -> b.getAddedTime().compareTo(a.getAddedTime())) // Most recent first
                        .collect(Collectors.toList());
                
                logger.debug("Found {} members for idea list: {} in group: {}", members.size(), listId, groupId);
                return members;
                
            } catch (Exception e) {
                logger.error("Error finding members for idea list: {} in group: {}", listId, groupId, e);
                throw new RepositoryException("Failed to find members for idea list", e);
            }
        });
    }
}