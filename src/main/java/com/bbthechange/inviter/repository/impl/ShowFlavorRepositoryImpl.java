package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.exception.RepositoryException;
import com.bbthechange.inviter.model.ShowFlavor;
import com.bbthechange.inviter.repository.ShowFlavorRepository;
import com.bbthechange.inviter.util.InviterKeyFactory;
import com.bbthechange.inviter.util.QueryPerformanceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

import java.util.Map;
import java.util.Optional;

@Repository
public class ShowFlavorRepositoryImpl implements ShowFlavorRepository {

    private static final Logger logger = LoggerFactory.getLogger(ShowFlavorRepositoryImpl.class);
    private static final String TABLE_NAME = "InviterTable";

    private final DynamoDbClient dynamoDbClient;
    private final TableSchema<ShowFlavor> schema;
    private final QueryPerformanceTracker performanceTracker;

    @Autowired
    public ShowFlavorRepositoryImpl(DynamoDbClient dynamoDbClient,
                                    DynamoDbEnhancedClient dynamoDbEnhancedClient,
                                    QueryPerformanceTracker performanceTracker) {
        this.dynamoDbClient = dynamoDbClient;
        this.schema = TableSchema.fromBean(ShowFlavor.class);
        this.performanceTracker = performanceTracker;
    }

    @Override
    public Optional<ShowFlavor> findByShowId(Integer showId) {
        if (showId == null) {
            return Optional.empty();
        }
        return performanceTracker.trackQuery("findShowFlavorByShowId", TABLE_NAME, () -> {
            try {
                GetItemRequest request = GetItemRequest.builder()
                        .tableName(TABLE_NAME)
                        .key(Map.of(
                                "pk", AttributeValue.builder().s(InviterKeyFactory.getSeasonPk(showId)).build(),
                                "sk", AttributeValue.builder().s(InviterKeyFactory.getFlavorSk()).build()
                        ))
                        .build();

                GetItemResponse response = dynamoDbClient.getItem(request);
                if (!response.hasItem()) {
                    return Optional.empty();
                }
                return Optional.of(schema.mapToItem(response.item()));
            } catch (DynamoDbException e) {
                logger.error("Failed to load ShowFlavor for showId {}", showId, e);
                throw new RepositoryException("Failed to load ShowFlavor", e);
            }
        });
    }
}
