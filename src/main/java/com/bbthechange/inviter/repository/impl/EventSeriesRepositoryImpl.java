package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.model.EventSeries;
import com.bbthechange.inviter.repository.EventSeriesRepository;
import com.bbthechange.inviter.exception.RepositoryException;
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
 * Implementation of EventSeriesRepository for managing multi-part event series.
 * Uses the single-table design pattern with the InviterTable.
 */
@Repository
public class EventSeriesRepositoryImpl implements EventSeriesRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(EventSeriesRepositoryImpl.class);
    
    private final DynamoDbClient dynamoDbClient;
    private final DynamoDbTable<EventSeries> eventSeriesTable;
    private static final String TABLE_NAME = "InviterTable";
    private final TableSchema<EventSeries> eventSeriesSchema;
    private final QueryPerformanceTracker performanceTracker;
    
    @Autowired
    public EventSeriesRepositoryImpl(
            DynamoDbClient dynamoDbClient,
            DynamoDbEnhancedClient dynamoDbEnhancedClient,
            QueryPerformanceTracker performanceTracker) {
        this.dynamoDbClient = dynamoDbClient;
        this.eventSeriesTable = dynamoDbEnhancedClient.table(TABLE_NAME, TableSchema.fromBean(EventSeries.class));
        this.eventSeriesSchema = TableSchema.fromBean(EventSeries.class);
        this.performanceTracker = performanceTracker;
    }
    
    @Override
    public EventSeries save(EventSeries eventSeries) {
        return performanceTracker.trackQuery("saveEventSeries", TABLE_NAME, () -> {
            try {
                eventSeries.touch();
                
                PutItemRequest request = PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(eventSeriesSchema.itemToMap(eventSeries, true))
                    .build();
                
                dynamoDbClient.putItem(request);
                
                logger.debug("Successfully saved EventSeries {}", eventSeries.getSeriesId());
                return eventSeries;
                
            } catch (DynamoDbException e) {
                logger.error("Failed to save EventSeries {}", eventSeries.getSeriesId(), e);
                throw new RepositoryException("Failed to save EventSeries", e);
            }
        });
    }
    
    @Override
    public Optional<EventSeries> findById(String seriesId) {
        return performanceTracker.trackQuery("findEventSeriesById", TABLE_NAME, () -> {
            try {
                GetItemRequest request = GetItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of(
                        "pk", AttributeValue.builder().s(InviterKeyFactory.getSeriesPk(seriesId)).build(),
                        "sk", AttributeValue.builder().s(InviterKeyFactory.getMetadataSk()).build()
                    ))
                    .build();
                
                GetItemResponse response = dynamoDbClient.getItem(request);
                if (!response.hasItem()) {
                    return Optional.empty();
                }
                
                EventSeries eventSeries = eventSeriesSchema.mapToItem(response.item());
                return Optional.of(eventSeries);
                
            } catch (DynamoDbException e) {
                logger.error("Failed to find EventSeries {}", seriesId, e);
                throw new RepositoryException("Failed to retrieve EventSeries", e);
            }
        });
    }
    
    @Override
    public void deleteById(String seriesId) {
        performanceTracker.trackQuery("deleteEventSeries", TABLE_NAME, () -> {
            try {
                DeleteItemRequest request = DeleteItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of(
                        "pk", AttributeValue.builder().s(InviterKeyFactory.getSeriesPk(seriesId)).build(),
                        "sk", AttributeValue.builder().s(InviterKeyFactory.getMetadataSk()).build()
                    ))
                    .build();
                
                dynamoDbClient.deleteItem(request);
                
                logger.debug("Successfully deleted EventSeries {}", seriesId);
                return null;
                
            } catch (DynamoDbException e) {
                logger.error("Failed to delete EventSeries {}", seriesId, e);
                throw new RepositoryException("Failed to delete EventSeries", e);
            }
        });
    }
    
    @Override
    public void updateSeriesMetadata(String seriesId, Map<String, AttributeValue> updates) {
        performanceTracker.trackQuery("updateEventSeriesMetadata", TABLE_NAME, () -> {
            try {
                // This would need to be implemented using UpdateExpression
                // For now, throw UnsupportedOperationException as a placeholder
                throw new UnsupportedOperationException("Direct metadata updates not yet implemented for EventSeries");
            } catch (Exception e) {
                logger.error("Failed to update EventSeries metadata for {}", seriesId, e);
                throw new RepositoryException("Failed to update EventSeries metadata", e);
            }
        });
    }
    
    @Override
    public List<EventSeries> findByGroupId(String groupId) {
        return performanceTracker.trackQuery("findEventSeriesByGroupId", "EntityTimeIndex", () -> {
            try {
                String participantKey = InviterKeyFactory.getGroupPk(groupId);
                
                QueryRequest request = QueryRequest.builder()
                    .tableName(TABLE_NAME)
                    .indexName("EntityTimeIndex")
                    .keyConditionExpression("gsi1pk = :participantKey")
                    .filterExpression("itemType = :itemType")
                    .expressionAttributeValues(Map.of(
                        ":participantKey", AttributeValue.builder().s(participantKey).build(),
                        ":itemType", AttributeValue.builder().s("EVENT_SERIES").build()
                    ))
                    .scanIndexForward(true) // Sort by timestamp ascending
                    .build();
                
                QueryResponse response = dynamoDbClient.query(request);
                
                return response.items().stream()
                    .map(eventSeriesSchema::mapToItem)
                    .collect(Collectors.toList());
                    
            } catch (DynamoDbException e) {
                logger.error("Failed to query EventSeries for group {}", groupId, e);
                throw new RepositoryException("Failed to query EventSeries by group ID", e);
            }
        });
    }
    
    @Override
    public List<EventSeries> findUpcomingByGroupId(String groupId, long currentTimestamp) {
        return performanceTracker.trackQuery("findUpcomingEventSeriesByGroupId", "EntityTimeIndex", () -> {
            try {
                String participantKey = InviterKeyFactory.getGroupPk(groupId);
                
                QueryRequest request = QueryRequest.builder()
                    .tableName(TABLE_NAME)
                    .indexName("EntityTimeIndex")
                    .keyConditionExpression("gsi1pk = :participantKey AND startTimestamp > :currentTimestamp")
                    .filterExpression("itemType = :itemType")
                    .expressionAttributeValues(Map.of(
                        ":participantKey", AttributeValue.builder().s(participantKey).build(),
                        ":currentTimestamp", AttributeValue.builder().n(String.valueOf(currentTimestamp)).build(),
                        ":itemType", AttributeValue.builder().s("EVENT_SERIES").build()
                    ))
                    .scanIndexForward(true) // Sort by timestamp ascending (chronological order)
                    .build();
                
                QueryResponse response = dynamoDbClient.query(request);
                
                return response.items().stream()
                    .map(eventSeriesSchema::mapToItem)
                    .collect(Collectors.toList());
                    
            } catch (DynamoDbException e) {
                logger.error("Failed to query upcoming EventSeries for group {}", groupId, e);
                throw new RepositoryException("Failed to query upcoming EventSeries by group ID", e);
            }
        });
    }
}