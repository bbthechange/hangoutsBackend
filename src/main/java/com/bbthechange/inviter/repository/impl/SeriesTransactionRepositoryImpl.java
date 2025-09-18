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
                
                // 4. Replace ALL existing SeriesPointers with updated versions that include the new hangout (PUT operations)
                for (SeriesPointer seriesPointerToUpdate : seriesPointersToUpdate) {
                    // Use PUT to completely replace the SeriesPointer with the updated parts field
                    TransactWriteItem putSeriesPointerItem = TransactWriteItem.builder()
                        .put(Put.builder()
                            .tableName(TABLE_NAME)
                            .item(seriesPointerSchema.itemToMap(seriesPointerToUpdate, true))
                            .build())
                        .build();
                    transactItems.add(putSeriesPointerItem);
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
    
    @Override
    public void unlinkHangoutFromSeries(
            EventSeries seriesToUpdate,
            Hangout hangoutToUpdate,
            List<HangoutPointer> pointersToUpdate,
            List<SeriesPointer> seriesPointersToUpdate) {
        
        performanceTracker.trackQuery("unlinkHangoutFromSeries", TABLE_NAME, () -> {
            try {
                List<TransactWriteItem> transactItems = new ArrayList<>();
                
                // 1. Update the EventSeries to remove the hangout ID and increment version
                TransactWriteItem updateSeriesItem = TransactWriteItem.builder()
                    .update(Update.builder()
                        .tableName(TABLE_NAME)
                        .key(Map.of(
                            "pk", AttributeValue.builder().s(seriesToUpdate.getPk()).build(),
                            "sk", AttributeValue.builder().s(seriesToUpdate.getSk()).build()
                        ))
                        .updateExpression("SET hangoutIds = :hangoutIds, updatedAt = :updated, version = :version")
                        .expressionAttributeValues(Map.of(
                            ":hangoutIds", convertStringListToAttributeValueList(seriesToUpdate.getHangoutIds()),
                            ":updated", AttributeValue.builder().n(String.valueOf(System.currentTimeMillis())).build(),
                            ":version", AttributeValue.builder().n(String.valueOf(seriesToUpdate.getVersion())).build()
                        ))
                        .build())
                    .build();
                transactItems.add(updateSeriesItem);
                
                // 2. Clear seriesId from the hangout
                TransactWriteItem updateHangoutItem = TransactWriteItem.builder()
                    .update(Update.builder()
                        .tableName(TABLE_NAME)
                        .key(Map.of(
                            "pk", AttributeValue.builder().s(hangoutToUpdate.getPk()).build(),
                            "sk", AttributeValue.builder().s(hangoutToUpdate.getSk()).build()
                        ))
                        .updateExpression("REMOVE seriesId SET updatedAt = :updated")
                        .expressionAttributeValues(Map.of(
                            ":updated", AttributeValue.builder().n(String.valueOf(System.currentTimeMillis())).build()
                        ))
                        .build())
                    .build();
                transactItems.add(updateHangoutItem);
                
                // 3. Clear seriesId from all HangoutPointers
                for (HangoutPointer pointerToUpdate : pointersToUpdate) {
                    TransactWriteItem updatePointerItem = TransactWriteItem.builder()
                        .update(Update.builder()
                            .tableName(TABLE_NAME)
                            .key(Map.of(
                                "pk", AttributeValue.builder().s(pointerToUpdate.getPk()).build(),
                                "sk", AttributeValue.builder().s(pointerToUpdate.getSk()).build()
                            ))
                            .updateExpression("REMOVE seriesId SET updatedAt = :updated")
                            .expressionAttributeValues(Map.of(
                                ":updated", AttributeValue.builder().n(String.valueOf(System.currentTimeMillis())).build()
                            ))
                            .build())
                        .build();
                    transactItems.add(updatePointerItem);
                }
                
                // 4. Update all SeriesPointers with the hangout removed
                for (SeriesPointer seriesPointerToUpdate : seriesPointersToUpdate) {
                    TransactWriteItem updateSeriesPointerItem = TransactWriteItem.builder()
                        .put(Put.builder()
                            .tableName(TABLE_NAME)
                            .item(seriesPointerSchema.itemToMap(seriesPointerToUpdate, true))
                            .build())
                        .build();
                    transactItems.add(updateSeriesPointerItem);
                }
                
                // Execute the transaction
                TransactWriteItemsRequest transactRequest = TransactWriteItemsRequest.builder()
                    .transactItems(transactItems)
                    .build();
                
                dynamoDbClient.transactWriteItems(transactRequest);
                
                logger.info("Successfully unlinked hangout {} from series {}", 
                    hangoutToUpdate.getHangoutId(), seriesToUpdate.getSeriesId());
                
            } catch (TransactionCanceledException e) {
                logger.error("Transaction cancelled while unlinking hangout from series: {}", e.cancellationReasons());
                throw new RepositoryException("Failed to unlink hangout from series atomically - transaction cancelled", e);
            } catch (DynamoDbException e) {
                logger.error("DynamoDB error while unlinking hangout from series", e);
                throw new RepositoryException("Failed to unlink hangout from series due to DynamoDB error", e);
            }
            
            return null;
        });
    }
    
    @Override
    public void deleteEntireSeries(
            EventSeries seriesToDelete,
            Hangout hangoutToUpdate,
            List<HangoutPointer> pointersToUpdate) {
        
        performanceTracker.trackQuery("deleteEntireSeries", TABLE_NAME, () -> {
            try {
                List<TransactWriteItem> transactItems = new ArrayList<>();
                
                // 1. Delete the EventSeries record
                TransactWriteItem deleteSeriesItem = TransactWriteItem.builder()
                    .delete(Delete.builder()
                        .tableName(TABLE_NAME)
                        .key(Map.of(
                            "pk", AttributeValue.builder().s(seriesToDelete.getPk()).build(),
                            "sk", AttributeValue.builder().s(seriesToDelete.getSk()).build()
                        ))
                        .build())
                    .build();
                transactItems.add(deleteSeriesItem);
                
                // 2. Clear seriesId from the hangout
                TransactWriteItem updateHangoutItem = TransactWriteItem.builder()
                    .update(Update.builder()
                        .tableName(TABLE_NAME)
                        .key(Map.of(
                            "pk", AttributeValue.builder().s(hangoutToUpdate.getPk()).build(),
                            "sk", AttributeValue.builder().s(hangoutToUpdate.getSk()).build()
                        ))
                        .updateExpression("REMOVE seriesId SET updatedAt = :updated")
                        .expressionAttributeValues(Map.of(
                            ":updated", AttributeValue.builder().n(String.valueOf(System.currentTimeMillis())).build()
                        ))
                        .build())
                    .build();
                transactItems.add(updateHangoutItem);
                
                // 3. Clear seriesId from all HangoutPointers
                for (HangoutPointer pointerToUpdate : pointersToUpdate) {
                    TransactWriteItem updatePointerItem = TransactWriteItem.builder()
                        .update(Update.builder()
                            .tableName(TABLE_NAME)
                            .key(Map.of(
                                "pk", AttributeValue.builder().s(pointerToUpdate.getPk()).build(),
                                "sk", AttributeValue.builder().s(pointerToUpdate.getSk()).build()
                            ))
                            .updateExpression("REMOVE seriesId SET updatedAt = :updated")
                            .expressionAttributeValues(Map.of(
                                ":updated", AttributeValue.builder().n(String.valueOf(System.currentTimeMillis())).build()
                            ))
                            .build())
                        .build();
                    transactItems.add(updatePointerItem);
                }
                
                // 4. Delete all SeriesPointers (we need to find and delete them)
                // Create delete operations for SeriesPointers based on the series groupId
                String groupPk = InviterKeyFactory.getGroupPk(seriesToDelete.getGroupId());
                String seriesSk = InviterKeyFactory.getSeriesSk(seriesToDelete.getSeriesId());
                
                TransactWriteItem deleteSeriesPointerItem = TransactWriteItem.builder()
                    .delete(Delete.builder()
                        .tableName(TABLE_NAME)
                        .key(Map.of(
                            "pk", AttributeValue.builder().s(groupPk).build(),
                            "sk", AttributeValue.builder().s(seriesSk).build()
                        ))
                        .build())
                    .build();
                transactItems.add(deleteSeriesPointerItem);
                
                // Execute the transaction
                TransactWriteItemsRequest transactRequest = TransactWriteItemsRequest.builder()
                    .transactItems(transactItems)
                    .build();
                
                dynamoDbClient.transactWriteItems(transactRequest);
                
                logger.info("Successfully deleted entire series {} and cleared hangout {}", 
                    seriesToDelete.getSeriesId(), hangoutToUpdate.getHangoutId());
                
            } catch (TransactionCanceledException e) {
                logger.error("Transaction cancelled while deleting entire series: {}", e.cancellationReasons());
                throw new RepositoryException("Failed to delete entire series atomically - transaction cancelled", e);
            } catch (DynamoDbException e) {
                logger.error("DynamoDB error while deleting entire series", e);
                throw new RepositoryException("Failed to delete entire series due to DynamoDB error", e);
            }
            
            return null;
        });
    }
    
    @Override
    public void removeHangoutFromSeries(
            EventSeries seriesToUpdate,
            Hangout hangoutToDelete,
            List<HangoutPointer> pointersToDelete,
            List<SeriesPointer> seriesPointersToUpdate) {
        
        performanceTracker.trackQuery("removeHangoutFromSeries", TABLE_NAME, () -> {
            try {
                List<TransactWriteItem> transactItems = new ArrayList<>();
                
                // 1. Update the EventSeries to remove the hangout ID and increment version
                TransactWriteItem updateSeriesItem = TransactWriteItem.builder()
                    .update(Update.builder()
                        .tableName(TABLE_NAME)
                        .key(Map.of(
                            "pk", AttributeValue.builder().s(seriesToUpdate.getPk()).build(),
                            "sk", AttributeValue.builder().s(seriesToUpdate.getSk()).build()
                        ))
                        .updateExpression("SET hangoutIds = :hangoutIds, updatedAt = :updated, version = :version")
                        .expressionAttributeValues(Map.of(
                            ":hangoutIds", convertStringListToAttributeValueList(seriesToUpdate.getHangoutIds()),
                            ":updated", AttributeValue.builder().n(String.valueOf(System.currentTimeMillis())).build(),
                            ":version", AttributeValue.builder().n(String.valueOf(seriesToUpdate.getVersion())).build()
                        ))
                        .build())
                    .build();
                transactItems.add(updateSeriesItem);
                
                // 2. Delete the hangout record
                TransactWriteItem deleteHangoutItem = TransactWriteItem.builder()
                    .delete(Delete.builder()
                        .tableName(TABLE_NAME)
                        .key(Map.of(
                            "pk", AttributeValue.builder().s(hangoutToDelete.getPk()).build(),
                            "sk", AttributeValue.builder().s(hangoutToDelete.getSk()).build()
                        ))
                        .build())
                    .build();
                transactItems.add(deleteHangoutItem);
                
                // 3. Delete all HangoutPointers
                for (HangoutPointer pointerToDelete : pointersToDelete) {
                    TransactWriteItem deletePointerItem = TransactWriteItem.builder()
                        .delete(Delete.builder()
                            .tableName(TABLE_NAME)
                            .key(Map.of(
                                "pk", AttributeValue.builder().s(pointerToDelete.getPk()).build(),
                                "sk", AttributeValue.builder().s(pointerToDelete.getSk()).build()
                            ))
                            .build())
                        .build();
                    transactItems.add(deletePointerItem);
                }
                
                // 4. Update all SeriesPointers with the hangout removed
                for (SeriesPointer seriesPointerToUpdate : seriesPointersToUpdate) {
                    TransactWriteItem updateSeriesPointerItem = TransactWriteItem.builder()
                        .put(Put.builder()
                            .tableName(TABLE_NAME)
                            .item(seriesPointerSchema.itemToMap(seriesPointerToUpdate, true))
                            .build())
                        .build();
                    transactItems.add(updateSeriesPointerItem);
                }
                
                // Execute the transaction
                TransactWriteItemsRequest transactRequest = TransactWriteItemsRequest.builder()
                    .transactItems(transactItems)
                    .build();
                
                dynamoDbClient.transactWriteItems(transactRequest);
                
                logger.info("Successfully removed hangout {} from series {}", 
                    hangoutToDelete.getHangoutId(), seriesToUpdate.getSeriesId());
                
            } catch (TransactionCanceledException e) {
                logger.error("Transaction cancelled while removing hangout from series: {}", e.cancellationReasons());
                throw new RepositoryException("Failed to remove hangout from series atomically - transaction cancelled", e);
            } catch (DynamoDbException e) {
                logger.error("DynamoDB error while removing hangout from series", e);
                throw new RepositoryException("Failed to remove hangout from series due to DynamoDB error", e);
            }
            
            return null;
        });
    }
    
    @Override
    public void deleteSeriesAndFinalHangout(
            EventSeries seriesToDelete,
            Hangout hangoutToDelete,
            List<HangoutPointer> pointersToDelete) {
        
        performanceTracker.trackQuery("deleteSeriesAndFinalHangout", TABLE_NAME, () -> {
            try {
                List<TransactWriteItem> transactItems = new ArrayList<>();
                
                // 1. Delete the EventSeries record
                TransactWriteItem deleteSeriesItem = TransactWriteItem.builder()
                    .delete(Delete.builder()
                        .tableName(TABLE_NAME)
                        .key(Map.of(
                            "pk", AttributeValue.builder().s(seriesToDelete.getPk()).build(),
                            "sk", AttributeValue.builder().s(seriesToDelete.getSk()).build()
                        ))
                        .build())
                    .build();
                transactItems.add(deleteSeriesItem);
                
                // 2. Delete the hangout record
                TransactWriteItem deleteHangoutItem = TransactWriteItem.builder()
                    .delete(Delete.builder()
                        .tableName(TABLE_NAME)
                        .key(Map.of(
                            "pk", AttributeValue.builder().s(hangoutToDelete.getPk()).build(),
                            "sk", AttributeValue.builder().s(hangoutToDelete.getSk()).build()
                        ))
                        .build())
                    .build();
                transactItems.add(deleteHangoutItem);
                
                // 3. Delete all HangoutPointers
                for (HangoutPointer pointerToDelete : pointersToDelete) {
                    TransactWriteItem deletePointerItem = TransactWriteItem.builder()
                        .delete(Delete.builder()
                            .tableName(TABLE_NAME)
                            .key(Map.of(
                                "pk", AttributeValue.builder().s(pointerToDelete.getPk()).build(),
                                "sk", AttributeValue.builder().s(pointerToDelete.getSk()).build()
                            ))
                            .build())
                        .build();
                    transactItems.add(deletePointerItem);
                }
                
                // 4. Delete all SeriesPointers
                String groupPk = InviterKeyFactory.getGroupPk(seriesToDelete.getGroupId());
                String seriesSk = InviterKeyFactory.getSeriesSk(seriesToDelete.getSeriesId());
                
                TransactWriteItem deleteSeriesPointerItem = TransactWriteItem.builder()
                    .delete(Delete.builder()
                        .tableName(TABLE_NAME)
                        .key(Map.of(
                            "pk", AttributeValue.builder().s(groupPk).build(),
                            "sk", AttributeValue.builder().s(seriesSk).build()
                        ))
                        .build())
                    .build();
                transactItems.add(deleteSeriesPointerItem);
                
                // Execute the transaction
                TransactWriteItemsRequest transactRequest = TransactWriteItemsRequest.builder()
                    .transactItems(transactItems)
                    .build();
                
                dynamoDbClient.transactWriteItems(transactRequest);
                
                logger.info("Successfully deleted series {} and final hangout {}", 
                    seriesToDelete.getSeriesId(), hangoutToDelete.getHangoutId());
                
            } catch (TransactionCanceledException e) {
                logger.error("Transaction cancelled while deleting series and final hangout: {}", e.cancellationReasons());
                throw new RepositoryException("Failed to delete series and final hangout atomically - transaction cancelled", e);
            } catch (DynamoDbException e) {
                logger.error("DynamoDB error while deleting series and final hangout", e);
                throw new RepositoryException("Failed to delete series and final hangout due to DynamoDB error", e);
            }
            
            return null;
        });
    }
    
    @Override
    public void updateSeriesAfterHangoutChange(
            EventSeries seriesToUpdate,
            List<SeriesPointer> seriesPointersToUpdate) {
        
        performanceTracker.trackQuery("updateSeriesAfterHangoutChange", TABLE_NAME, () -> {
            try {
                List<TransactWriteItem> transactItems = new ArrayList<>();
                
                // 1. Update the EventSeries record
                TransactWriteItem updateSeriesItem = TransactWriteItem.builder()
                    .put(Put.builder()
                        .tableName(TABLE_NAME)
                        .item(eventSeriesSchema.itemToMap(seriesToUpdate, true))
                        .build())
                    .build();
                transactItems.add(updateSeriesItem);
                
                // 2. Update all SeriesPointers
                for (SeriesPointer seriesPointerToUpdate : seriesPointersToUpdate) {
                    TransactWriteItem updateSeriesPointerItem = TransactWriteItem.builder()
                        .put(Put.builder()
                            .tableName(TABLE_NAME)
                            .item(seriesPointerSchema.itemToMap(seriesPointerToUpdate, true))
                            .build())
                        .build();
                    transactItems.add(updateSeriesPointerItem);
                }
                
                // Execute the transaction
                TransactWriteItemsRequest transactRequest = TransactWriteItemsRequest.builder()
                    .transactItems(transactItems)
                    .build();
                
                dynamoDbClient.transactWriteItems(transactRequest);
                
                logger.info("Successfully updated series {} after hangout modification", 
                    seriesToUpdate.getSeriesId());
                
            } catch (TransactionCanceledException e) {
                logger.error("Transaction cancelled while updating series: {}", e.cancellationReasons());
                throw new RepositoryException("Failed to update series atomically - transaction cancelled", e);
            } catch (DynamoDbException e) {
                logger.error("DynamoDB error while updating series", e);
                throw new RepositoryException("Failed to update series due to DynamoDB error", e);
            }
            
            return null;
        });
    }
    
    @Override
    public void deleteEntireSeriesWithAllHangouts(
            EventSeries seriesToDelete,
            List<Hangout> hangsoutsToDelete,
            List<HangoutPointer> pointersToDelete,
            List<SeriesPointer> seriesPointersToDelete) {
        
        performanceTracker.trackQuery("deleteEntireSeriesWithAllHangouts", TABLE_NAME, () -> {
            try {
                List<TransactWriteItem> transactItems = new ArrayList<>();
                
                // 1. Delete the EventSeries record
                TransactWriteItem deleteSeriesItem = TransactWriteItem.builder()
                    .delete(Delete.builder()
                        .tableName(TABLE_NAME)
                        .key(Map.of(
                            "pk", AttributeValue.builder().s(seriesToDelete.getPk()).build(),
                            "sk", AttributeValue.builder().s(seriesToDelete.getSk()).build()
                        ))
                        .build())
                    .build();
                transactItems.add(deleteSeriesItem);
                
                // 2. Delete all Hangout records in the series
                for (Hangout hangoutToDelete : hangsoutsToDelete) {
                    TransactWriteItem deleteHangoutItem = TransactWriteItem.builder()
                        .delete(Delete.builder()
                            .tableName(TABLE_NAME)
                            .key(Map.of(
                                "pk", AttributeValue.builder().s(hangoutToDelete.getPk()).build(),
                                "sk", AttributeValue.builder().s(hangoutToDelete.getSk()).build()
                            ))
                            .build())
                        .build();
                    transactItems.add(deleteHangoutItem);
                }
                
                // 3. Delete all HangoutPointer records
                for (HangoutPointer pointerToDelete : pointersToDelete) {
                    TransactWriteItem deletePointerItem = TransactWriteItem.builder()
                        .delete(Delete.builder()
                            .tableName(TABLE_NAME)
                            .key(Map.of(
                                "pk", AttributeValue.builder().s(pointerToDelete.getPk()).build(),
                                "sk", AttributeValue.builder().s(pointerToDelete.getSk()).build()
                            ))
                            .build())
                        .build();
                    transactItems.add(deletePointerItem);
                }
                
                // 4. Delete all SeriesPointer records
                for (SeriesPointer seriesPointerToDelete : seriesPointersToDelete) {
                    TransactWriteItem deleteSeriesPointerItem = TransactWriteItem.builder()
                        .delete(Delete.builder()
                            .tableName(TABLE_NAME)
                            .key(Map.of(
                                "pk", AttributeValue.builder().s(seriesPointerToDelete.getPk()).build(),
                                "sk", AttributeValue.builder().s(seriesPointerToDelete.getSk()).build()
                            ))
                            .build())
                        .build();
                    transactItems.add(deleteSeriesPointerItem);
                }
                
                // Execute the transaction
                TransactWriteItemsRequest transactRequest = TransactWriteItemsRequest.builder()
                    .transactItems(transactItems)
                    .build();
                
                dynamoDbClient.transactWriteItems(transactRequest);
                
                logger.info("Successfully deleted entire series {} with {} hangouts, {} hangout pointers, and {} series pointers", 
                    seriesToDelete.getSeriesId(), hangsoutsToDelete.size(), pointersToDelete.size(), seriesPointersToDelete.size());
                
            } catch (TransactionCanceledException e) {
                logger.error("Transaction cancelled while deleting entire series {}: {}", 
                    seriesToDelete.getSeriesId(), e.cancellationReasons());
                throw new RepositoryException("Failed to delete entire series atomically - transaction cancelled", e);
            } catch (DynamoDbException e) {
                logger.error("DynamoDB error while deleting entire series {}", seriesToDelete.getSeriesId(), e);
                throw new RepositoryException("Failed to delete entire series due to DynamoDB error", e);
            }
            
            return null;
        });
    }
    
    // Helper method to convert List<String> to AttributeValue list format
    private AttributeValue convertStringListToAttributeValueList(List<String> stringList) {
        if (stringList == null || stringList.isEmpty()) {
            return AttributeValue.builder().l(new ArrayList<AttributeValue>()).build();
        }
        
        List<AttributeValue> attributeValues = new ArrayList<>();
        for (String str : stringList) {
            attributeValues.add(AttributeValue.builder().s(str).build());
        }
        
        return AttributeValue.builder().l(attributeValues).build();
    }
}