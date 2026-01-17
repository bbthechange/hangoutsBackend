package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.model.Season;
import com.bbthechange.inviter.repository.SeasonRepository;
import com.bbthechange.inviter.exception.RepositoryException;
import com.bbthechange.inviter.util.QueryPerformanceTracker;
import com.bbthechange.inviter.util.InviterKeyFactory;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of SeasonRepository for managing TV seasons for Watch Party features.
 * Uses the single-table design pattern with the InviterTable.
 */
@Repository
public class SeasonRepositoryImpl implements SeasonRepository {

    private static final Logger logger = LoggerFactory.getLogger(SeasonRepositoryImpl.class);

    private final DynamoDbClient dynamoDbClient;
    private final DynamoDbTable<Season> seasonTable;
    private static final String TABLE_NAME = "InviterTable";
    private final TableSchema<Season> seasonSchema;
    private final QueryPerformanceTracker performanceTracker;

    @Autowired
    public SeasonRepositoryImpl(
            DynamoDbClient dynamoDbClient,
            DynamoDbEnhancedClient dynamoDbEnhancedClient,
            QueryPerformanceTracker performanceTracker) {
        this.dynamoDbClient = dynamoDbClient;
        this.seasonTable = dynamoDbEnhancedClient.table(TABLE_NAME, TableSchema.fromBean(Season.class));
        this.seasonSchema = TableSchema.fromBean(Season.class);
        this.performanceTracker = performanceTracker;
    }

    @Override
    public Season save(Season season) {
        return performanceTracker.trackQuery("saveSeason", TABLE_NAME, () -> {
            try {
                season.touch();

                PutItemRequest request = PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(seasonSchema.itemToMap(season, true))
                    .build();

                dynamoDbClient.putItem(request);

                logger.debug("Successfully saved Season for show {} season {}",
                    season.getShowId(), season.getSeasonNumber());
                return season;

            } catch (DynamoDbException e) {
                logger.error("Failed to save Season for show {} season {}",
                    season.getShowId(), season.getSeasonNumber(), e);
                throw new RepositoryException("Failed to save Season", e);
            }
        });
    }

    @Override
    public Optional<Season> findByShowIdAndSeasonNumber(Integer showId, Integer seasonNumber) {
        return performanceTracker.trackQuery("findSeasonByShowIdAndSeasonNumber", TABLE_NAME, () -> {
            try {
                GetItemRequest request = GetItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of(
                        "pk", AttributeValue.builder().s(InviterKeyFactory.getSeasonPk(showId)).build(),
                        "sk", AttributeValue.builder().s(InviterKeyFactory.getSeasonSk(seasonNumber)).build()
                    ))
                    .build();

                GetItemResponse response = dynamoDbClient.getItem(request);
                if (!response.hasItem()) {
                    return Optional.empty();
                }

                Season season = seasonSchema.mapToItem(response.item());
                return Optional.of(season);

            } catch (DynamoDbException e) {
                logger.error("Failed to find Season for show {} season {}", showId, seasonNumber, e);
                throw new RepositoryException("Failed to retrieve Season", e);
            }
        });
    }

    @Override
    public List<Season> findByShowId(Integer showId) {
        return performanceTracker.trackQuery("findSeasonsByShowId", "ExternalIdIndex", () -> {
            try {
                // Use ExternalIdIndex with showId as externalId and "TVMAZE" as externalSource
                QueryRequest request = QueryRequest.builder()
                    .tableName(TABLE_NAME)
                    .indexName("ExternalIdIndex")
                    .keyConditionExpression("externalId = :showId AND externalSource = :source")
                    .filterExpression("itemType = :itemType")
                    .expressionAttributeValues(Map.of(
                        ":showId", AttributeValue.builder().s(showId.toString()).build(),
                        ":source", AttributeValue.builder().s("TVMAZE").build(),
                        ":itemType", AttributeValue.builder().s("SEASON").build()
                    ))
                    .build();

                QueryResponse response = dynamoDbClient.query(request);

                List<Season> seasons = response.items().stream()
                    .map(seasonSchema::mapToItem)
                    .sorted(Comparator.comparing(Season::getSeasonNumber))
                    .collect(Collectors.toList());

                logger.debug("Found {} seasons for show {}", seasons.size(), showId);
                return seasons;

            } catch (DynamoDbException e) {
                logger.error("Failed to query Seasons for show {}", showId, e);
                throw new RepositoryException("Failed to query Seasons by show ID", e);
            }
        });
    }

    @Override
    public void delete(Integer showId, Integer seasonNumber) {
        performanceTracker.trackQuery("deleteSeason", TABLE_NAME, () -> {
            try {
                DeleteItemRequest request = DeleteItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of(
                        "pk", AttributeValue.builder().s(InviterKeyFactory.getSeasonPk(showId)).build(),
                        "sk", AttributeValue.builder().s(InviterKeyFactory.getSeasonSk(seasonNumber)).build()
                    ))
                    .build();

                dynamoDbClient.deleteItem(request);

                logger.debug("Successfully deleted Season for show {} season {}", showId, seasonNumber);
                return null;

            } catch (DynamoDbException e) {
                logger.error("Failed to delete Season for show {} season {}", showId, seasonNumber, e);
                throw new RepositoryException("Failed to delete Season", e);
            }
        });
    }

    @Override
    public void updateLastCheckedTimestamp(Integer showId, Integer seasonNumber, Long timestamp) {
        if (showId == null || seasonNumber == null || timestamp == null) {
            throw new IllegalArgumentException("showId, seasonNumber, and timestamp cannot be null");
        }
        performanceTracker.trackQuery("updateSeasonLastCheckedTimestamp", TABLE_NAME, () -> {
            try {
                UpdateItemRequest request = UpdateItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of(
                        "pk", AttributeValue.builder().s(InviterKeyFactory.getSeasonPk(showId)).build(),
                        "sk", AttributeValue.builder().s(InviterKeyFactory.getSeasonSk(seasonNumber)).build()
                    ))
                    .updateExpression("SET lastCheckedTimestamp = :timestamp, updatedAt = :updatedAt")
                    .expressionAttributeValues(Map.of(
                        ":timestamp", AttributeValue.builder().n(timestamp.toString()).build(),
                        ":updatedAt", AttributeValue.builder().n(String.valueOf(System.currentTimeMillis())).build()
                    ))
                    .build();

                dynamoDbClient.updateItem(request);

                logger.debug("Updated lastCheckedTimestamp for show {} season {} to {}",
                    showId, seasonNumber, timestamp);
                return null;

            } catch (DynamoDbException e) {
                logger.error("Failed to update lastCheckedTimestamp for show {} season {}",
                    showId, seasonNumber, e);
                throw new RepositoryException("Failed to update Season lastCheckedTimestamp", e);
            }
        });
    }
}
