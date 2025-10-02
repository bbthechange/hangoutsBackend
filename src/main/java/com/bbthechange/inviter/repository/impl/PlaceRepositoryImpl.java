package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.exception.RepositoryException;
import com.bbthechange.inviter.model.Place;
import com.bbthechange.inviter.repository.PlaceRepository;
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
 * Implementation of PlaceRepository using DynamoDB direct client.
 * Follows the polymorphic repository pattern for single-table design.
 */
@Repository
public class PlaceRepositoryImpl implements PlaceRepository {

    private static final Logger logger = LoggerFactory.getLogger(PlaceRepositoryImpl.class);
    private static final String TABLE_NAME = "InviterTable";
    private static final String USER_GROUP_INDEX = "UserGroupIndex";

    private final DynamoDbClient dynamoDbClient;
    private final TableSchema<Place> placeSchema;
    private final QueryPerformanceTracker queryTracker;

    @Autowired
    public PlaceRepositoryImpl(DynamoDbClient dynamoDbClient, QueryPerformanceTracker queryTracker) {
        this.dynamoDbClient = dynamoDbClient;
        this.queryTracker = queryTracker;
        this.placeSchema = TableSchema.fromBean(Place.class);
    }

    @Override
    public List<Place> findPlacesByOwner(String ownerPk) {
        return queryTracker.trackQuery("Query", TABLE_NAME, () -> {
            try {
                QueryRequest request = QueryRequest.builder()
                    .tableName(TABLE_NAME)
                    .keyConditionExpression("pk = :pk AND begins_with(sk, :sk_prefix)")
                    .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.builder().s(ownerPk).build(),
                        ":sk_prefix", AttributeValue.builder().s(InviterKeyFactory.PLACE_PREFIX + "#").build()
                    ))
                    .build();

                QueryResponse response = dynamoDbClient.query(request);

                return response.items().stream()
                    .map(placeSchema::mapToItem)
                    .collect(Collectors.toList());

            } catch (DynamoDbException e) {
                logger.error("Failed to find places for owner {}", ownerPk, e);
                throw new RepositoryException("Failed to retrieve places", e);
            }
        });
    }

    @Override
    public Optional<Place> findPrimaryPlaceForUser(String userId) {
        return queryTracker.trackQuery("Query-GSI", TABLE_NAME, () -> {
            try {
                QueryRequest request = QueryRequest.builder()
                    .tableName(TABLE_NAME)
                    .indexName(USER_GROUP_INDEX)
                    .keyConditionExpression("gsi1pk = :gsi1pk AND gsi1sk = :gsi1sk")
                    .expressionAttributeValues(Map.of(
                        ":gsi1pk", AttributeValue.builder().s(InviterKeyFactory.getUserGsi1Pk(userId)).build(),
                        ":gsi1sk", AttributeValue.builder().s(InviterKeyFactory.PRIMARY_PLACE).build()
                    ))
                    .build();

                QueryResponse response = dynamoDbClient.query(request);

                if (response.items().isEmpty()) {
                    return Optional.empty();
                }

                return Optional.of(placeSchema.mapToItem(response.items().get(0)));

            } catch (DynamoDbException e) {
                logger.error("Failed to find primary place for user {}", userId, e);
                throw new RepositoryException("Failed to retrieve primary place", e);
            }
        });
    }

    @Override
    public Place save(Place place) {
        return queryTracker.trackQuery("PutItem", TABLE_NAME, () -> {
            try {
                place.touch();
                Map<String, AttributeValue> itemMap = placeSchema.itemToMap(place, true);

                PutItemRequest request = PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(itemMap)
                    .build();

                dynamoDbClient.putItem(request);
                logger.info("Saved place {} for owner {}", place.getPlaceId(), place.getPk());
                return place;

            } catch (DynamoDbException e) {
                logger.error("Failed to save place {}", place.getPlaceId(), e);
                throw new RepositoryException("Failed to save place", e);
            }
        });
    }

    @Override
    public Optional<Place> findByOwnerAndPlaceId(String ownerPk, String placeId) {
        return queryTracker.trackQuery("GetItem", TABLE_NAME, () -> {
            try {
                GetItemRequest request = GetItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of(
                        "pk", AttributeValue.builder().s(ownerPk).build(),
                        "sk", AttributeValue.builder().s(InviterKeyFactory.getPlaceSk(placeId)).build()
                    ))
                    .build();

                GetItemResponse response = dynamoDbClient.getItem(request);
                if (!response.hasItem()) {
                    return Optional.empty();
                }

                return Optional.of(placeSchema.mapToItem(response.item()));

            } catch (DynamoDbException e) {
                logger.error("Failed to find place {} for owner {}", placeId, ownerPk, e);
                throw new RepositoryException("Failed to retrieve place", e);
            }
        });
    }
}
