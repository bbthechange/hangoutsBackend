package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.model.EventSeries;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.model.SeriesPointer;
import com.bbthechange.inviter.repository.SeriesTransactionRepository;
import com.bbthechange.inviter.exception.RepositoryException;
import com.bbthechange.inviter.util.QueryPerformanceTracker;
import com.bbthechange.inviter.util.InviterKeyFactory;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implementation of SeriesTransactionRepository for atomic multi-item operations
 * when creating and managing event series.
 */
@Repository
public class SeriesTransactionRepositoryImpl implements SeriesTransactionRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(SeriesTransactionRepositoryImpl.class);
    
    private final DynamoDbClient dynamoDbClient;
    private final QueryPerformanceTracker performanceTracker;
    
    private static final String TABLE_NAME = "InviterTable";
    
    // Table schemas for mapping objects to DynamoDB items
    private final TableSchema<EventSeries> eventSeriesSchema;
    private final TableSchema<Hangout> hangoutSchema;
    private final TableSchema<HangoutPointer> hangoutPointerSchema;
    private final TableSchema<SeriesPointer> seriesPointerSchema;
    
    @Autowired
    public SeriesTransactionRepositoryImpl(
            DynamoDbClient dynamoDbClient,
            QueryPerformanceTracker performanceTracker) {
        this.dynamoDbClient = dynamoDbClient;
        this.performanceTracker = performanceTracker;
        
        // Initialize table schemas
        this.eventSeriesSchema = TableSchema.fromBean(EventSeries.class);
        this.hangoutSchema = TableSchema.fromBean(Hangout.class);
        this.hangoutPointerSchema = TableSchema.fromBean(HangoutPointer.class);
        this.seriesPointerSchema = TableSchema.fromBean(SeriesPointer.class);
    }
    
    @Override
    public void createSeriesWithNewPart(
            EventSeries seriesToCreate,
            Hangout hangoutToUpdate,
            List<HangoutPointer> pointersToUpdate,
            Hangout newHangoutToCreate,
            List<HangoutPointer> newPointersToCreate,
            List<SeriesPointer> seriesPointersToCreate) {
        
        performanceTracker.trackQuery("createSeriesWithNewPart", TABLE_NAME, () -> {
            try {
                List<TransactWriteItem> transactItems = new ArrayList<>();
                
                // 1. Create the new EventSeries record (PUT operation)
                TransactWriteItem createSeriesItem = TransactWriteItem.builder()
                    .put(Put.builder()
                        .tableName(TABLE_NAME)
                        .item(eventSeriesSchema.itemToMap(seriesToCreate, true))
                        .build())
                    .build();
                transactItems.add(createSeriesItem);
                
                // 2. Update the existing Hangout's seriesId (UPDATE operation)
                TransactWriteItem updateHangoutItem = TransactWriteItem.builder()
                    .update(Update.builder()
                        .tableName(TABLE_NAME)
                        .key(Map.of(
                            "pk", AttributeValue.builder().s(InviterKeyFactory.getEventPk(hangoutToUpdate.getHangoutId())).build(),
                            "sk", AttributeValue.builder().s(InviterKeyFactory.getMetadataSk()).build()
                        ))
                        .updateExpression("SET seriesId = :sid, updatedAt = :updated, version = version + :inc")
                        .expressionAttributeValues(Map.of(
                            ":sid", AttributeValue.builder().s(seriesToCreate.getSeriesId()).build(),
                            ":updated", AttributeValue.builder().n(String.valueOf(System.currentTimeMillis())).build(),
                            ":inc", AttributeValue.builder().n("1").build()
                        ))
                        .build())
                    .build();
                transactItems.add(updateHangoutItem);
                
                // 3. Update ALL existing HangoutPointers' seriesId (UPDATE operations)
                for (HangoutPointer pointerToUpdate : pointersToUpdate) {
                    TransactWriteItem updatePointerItem = TransactWriteItem.builder()
                        .update(Update.builder()
                            .tableName(TABLE_NAME)
                            .key(Map.of(
                                "pk", AttributeValue.builder().s(pointerToUpdate.getPk()).build(),
                                "sk", AttributeValue.builder().s(pointerToUpdate.getSk()).build()
                            ))
                            .updateExpression("SET seriesId = :sid, updatedAt = :updated")
                            .expressionAttributeValues(Map.of(
                                ":sid", AttributeValue.builder().s(seriesToCreate.getSeriesId()).build(),
                                ":updated", AttributeValue.builder().n(String.valueOf(System.currentTimeMillis())).build()
                            ))
                            .build())
                        .build();
                    transactItems.add(updatePointerItem);
                }
                
                // 4. Create the new Hangout part (PUT operation)
                TransactWriteItem createNewHangoutItem = TransactWriteItem.builder()
                    .put(Put.builder()
                        .tableName(TABLE_NAME)
                        .item(hangoutSchema.itemToMap(newHangoutToCreate, true))
                        .build())
                    .build();
                transactItems.add(createNewHangoutItem);
                
                // 5. Create ALL new HangoutPointers (PUT operations)
                for (HangoutPointer newPointerToCreate : newPointersToCreate) {
                    TransactWriteItem createNewPointerItem = TransactWriteItem.builder()
                        .put(Put.builder()
                            .tableName(TABLE_NAME)
                            .item(hangoutPointerSchema.itemToMap(newPointerToCreate, true))
                            .build())
                        .build();
                    transactItems.add(createNewPointerItem);
                }
                
                // 6. Create ALL new SeriesPointers (PUT operations)
                for (SeriesPointer seriesPointerToCreate : seriesPointersToCreate) {
                    TransactWriteItem createSeriesPointerItem = TransactWriteItem.builder()
                        .put(Put.builder()
                            .tableName(TABLE_NAME)
                            .item(seriesPointerSchema.itemToMap(seriesPointerToCreate, true))
                            .build())
                        .build();
                    transactItems.add(createSeriesPointerItem);
                }
                
                // Execute the transaction
                TransactWriteItemsRequest transactRequest = TransactWriteItemsRequest.builder()
                    .transactItems(transactItems)
                    .build();
                
                dynamoDbClient.transactWriteItems(transactRequest);
                
                logger.info("Successfully created series {} with new part {}", 
                    seriesToCreate.getSeriesId(), newHangoutToCreate.getHangoutId());
                
            } catch (TransactionCanceledException e) {
                logger.error("Transaction cancelled while creating series {}: {}", 
                    seriesToCreate.getSeriesId(), e.cancellationReasons());
                throw new RepositoryException("Failed to create series atomically - transaction cancelled", e);
            } catch (DynamoDbException e) {
                logger.error("DynamoDB error while creating series {}", seriesToCreate.getSeriesId(), e);
                throw new RepositoryException("Failed to create series due to DynamoDB error", e);
            }
            
            return null;
        });
    }
    
    @Override
    public void addPartToExistingSeries(
            String seriesId,
            Hangout newHangoutToCreate,
            List<HangoutPointer> newPointersToCreate,
            List<SeriesPointer> seriesPointersToUpdate) {
        
        performanceTracker.trackQuery("addPartToExistingSeries", TABLE_NAME, () -> {
            try {
                List<TransactWriteItem> transactItems = new ArrayList<>();
                
                // 1. Update the EventSeries to add the new hangout ID (UPDATE operation)
                TransactWriteItem updateSeriesItem = TransactWriteItem.builder()
                    .update(Update.builder()
                        .tableName(TABLE_NAME)
                        .key(Map.of(
                            "pk", AttributeValue.builder().s(InviterKeyFactory.getSeriesPk(seriesId)).build(),
                            "sk", AttributeValue.builder().s(InviterKeyFactory.getMetadataSk()).build()
                        ))
                        .updateExpression("SET hangoutIds = list_append(hangoutIds, :newId), updatedAt = :updated, version = version + :inc")
                        .expressionAttributeValues(Map.of(
                            ":newId", AttributeValue.builder().l(
                                AttributeValue.builder().s(newHangoutToCreate.getHangoutId()).build()
                            ).build(),
                            ":updated", AttributeValue.builder().n(String.valueOf(System.currentTimeMillis())).build(),
                            ":inc", AttributeValue.builder().n("1").build()
                        ))
                        .build())
                    .build();
                transactItems.add(updateSeriesItem);
                
                // 2. Create the new Hangout part (PUT operation)
                TransactWriteItem createNewHangoutItem = TransactWriteItem.builder()
                    .put(Put.builder()
                        .tableName(TABLE_NAME)
                        .item(hangoutSchema.itemToMap(newHangoutToCreate, true))
                        .build())
                    .build();
                transactItems.add(createNewHangoutItem);
                
                // 3. Create ALL new HangoutPointers (PUT operations)
                for (HangoutPointer newPointerToCreate : newPointersToCreate) {
                    TransactWriteItem createNewPointerItem = TransactWriteItem.builder()
                        .put(Put.builder()
                            .tableName(TABLE_NAME)
                            .item(hangoutPointerSchema.itemToMap(newPointerToCreate, true))
                            .build())
                        .build();
                    transactItems.add(createNewPointerItem);
                }
                
                // 4. Update ALL existing SeriesPointers to include the new hangout (UPDATE operations)
                for (SeriesPointer seriesPointerToUpdate : seriesPointersToUpdate) {
                    TransactWriteItem updateSeriesPointerItem = TransactWriteItem.builder()
                        .update(Update.builder()
                            .tableName(TABLE_NAME)
                            .key(Map.of(
                                "pk", AttributeValue.builder().s(seriesPointerToUpdate.getPk()).build(),
                                "sk", AttributeValue.builder().s(seriesPointerToUpdate.getSk()).build()
                            ))
                            .updateExpression("SET hangoutIds = list_append(hangoutIds, :newId), updatedAt = :updated, version = version + :inc")
                            .expressionAttributeValues(Map.of(
                                ":newId", AttributeValue.builder().l(
                                    AttributeValue.builder().s(newHangoutToCreate.getHangoutId()).build()
                                ).build(),
                                ":updated", AttributeValue.builder().n(String.valueOf(System.currentTimeMillis())).build(),
                                ":inc", AttributeValue.builder().n("1").build()
                            ))
                            .build())
                        .build();
                    transactItems.add(updateSeriesPointerItem);
                }
                
                // Execute the transaction
                TransactWriteItemsRequest transactRequest = TransactWriteItemsRequest.builder()
                    .transactItems(transactItems)
                    .build();
                
                dynamoDbClient.transactWriteItems(transactRequest);
                
                logger.info("Successfully added new part {} to series {}", 
                    newHangoutToCreate.getHangoutId(), seriesId);
                
            } catch (TransactionCanceledException e) {
                logger.error("Transaction cancelled while adding part to series {}: {}", 
                    seriesId, e.cancellationReasons());
                throw new RepositoryException("Failed to add part to series atomically - transaction cancelled", e);
            } catch (DynamoDbException e) {
                logger.error("DynamoDB error while adding part to series {}", seriesId, e);
                throw new RepositoryException("Failed to add part to series due to DynamoDB error", e);
            }
            
            return null;
        });
    }
}