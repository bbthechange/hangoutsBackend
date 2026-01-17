package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.model.Season;
import com.bbthechange.inviter.util.QueryPerformanceTracker;
import com.bbthechange.inviter.util.InviterKeyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeasonRepositoryImplTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private DynamoDbEnhancedClient dynamoDbEnhancedClient;

    @Mock
    private QueryPerformanceTracker performanceTracker;

    private SeasonRepositoryImpl repository;

    private Integer showId;
    private Integer seasonNumber;

    @BeforeEach
    void setUp() {
        when(performanceTracker.trackQuery(anyString(), anyString(), any())).thenAnswer(invocation -> {
            Object result = null;
            try {
                java.util.function.Supplier<?> supplier = invocation.getArgument(2);
                result = supplier.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return result;
        });

        repository = new SeasonRepositoryImpl(dynamoDbClient, dynamoDbEnhancedClient, performanceTracker);

        showId = 123;
        seasonNumber = 2;
    }

    @Test
    void save_WithValidSeason_ShouldPutItemAndReturnSeason() {
        // Given
        Season season = new Season(showId, seasonNumber, "Breaking Bad");

        PutItemResponse response = PutItemResponse.builder().build();
        when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);

        // When
        Season result = repository.save(season);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getShowId()).isEqualTo(showId);
        assertThat(result.getSeasonNumber()).isEqualTo(seasonNumber);
        assertThat(result.getShowName()).isEqualTo("Breaking Bad");

        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(captor.capture());

        PutItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.item().get("pk").s()).isEqualTo("TVMAZE#SHOW#123");
        assertThat(request.item().get("sk").s()).isEqualTo("SEASON#2");
        assertThat(request.item().get("itemType").s()).isEqualTo("SEASON");
    }

    @Test
    void findByShowIdAndSeasonNumber_WithExistingSeason_ShouldReturnOptionalWithSeason() {
        // Given
        Map<String, AttributeValue> itemMap = createMockSeasonItem(showId, seasonNumber, "Breaking Bad");
        GetItemResponse response = GetItemResponse.builder()
                .item(itemMap)
                .build();
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

        // When
        Optional<Season> result = repository.findByShowIdAndSeasonNumber(showId, seasonNumber);

        // Then
        assertThat(result).isPresent();

        ArgumentCaptor<GetItemRequest> captor = ArgumentCaptor.forClass(GetItemRequest.class);
        verify(dynamoDbClient).getItem(captor.capture());

        GetItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.key().get("pk").s()).isEqualTo("TVMAZE#SHOW#123");
        assertThat(request.key().get("sk").s()).isEqualTo("SEASON#2");
    }

    @Test
    void delete_WithValidShowIdAndSeasonNumber_ShouldDeleteItem() {
        // Given
        DeleteItemResponse response = DeleteItemResponse.builder().build();
        when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class))).thenReturn(response);

        // When
        repository.delete(showId, seasonNumber);

        // Then
        ArgumentCaptor<DeleteItemRequest> captor = ArgumentCaptor.forClass(DeleteItemRequest.class);
        verify(dynamoDbClient).deleteItem(captor.capture());

        DeleteItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.key().get("pk").s()).isEqualTo("TVMAZE#SHOW#123");
        assertThat(request.key().get("sk").s()).isEqualTo("SEASON#2");
    }

    @Test
    void findByShowId_WithExistingSeasons_ShouldQueryShowIdIndexAndReturnSeasons() {
        // Given
        List<Map<String, AttributeValue>> mockItems = Arrays.asList(
            createMockSeasonItem(showId, 1, "Test Show"),
            createMockSeasonItem(showId, 2, "Test Show")
        );

        QueryResponse mockResponse = QueryResponse.builder()
            .items(mockItems)
            .build();

        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        List<Season> result = repository.findByShowId(showId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);

        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());

        QueryRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.indexName()).isEqualTo("ExternalIdIndex");
        assertThat(request.keyConditionExpression()).isEqualTo("externalId = :showId AND externalSource = :source");
        assertThat(request.filterExpression()).isEqualTo("itemType = :itemType");
        assertThat(request.expressionAttributeValues().get(":showId").s()).isEqualTo("123");
        assertThat(request.expressionAttributeValues().get(":source").s()).isEqualTo("TVMAZE");
        assertThat(request.expressionAttributeValues().get(":itemType").s()).isEqualTo("SEASON");
    }

    @Test
    void findByShowId_WithNoSeasons_ShouldReturnEmptyList() {
        // Given
        QueryResponse mockResponse = QueryResponse.builder()
            .items(Collections.emptyList())
            .build();

        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        List<Season> result = repository.findByShowId(showId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();

        verify(dynamoDbClient).query(any(QueryRequest.class));
    }

    @Test
    void updateLastCheckedTimestamp_WithValidParameters_ShouldUpdateItem() {
        // Given
        Long timestamp = System.currentTimeMillis();
        UpdateItemResponse response = UpdateItemResponse.builder().build();
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenReturn(response);

        // When
        repository.updateLastCheckedTimestamp(showId, seasonNumber, timestamp);

        // Then
        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDbClient).updateItem(captor.capture());

        UpdateItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.key().get("pk").s()).isEqualTo("TVMAZE#SHOW#123");
        assertThat(request.key().get("sk").s()).isEqualTo("SEASON#2");
        assertThat(request.updateExpression()).isEqualTo("SET lastCheckedTimestamp = :timestamp, updatedAt = :updatedAt");
        assertThat(request.expressionAttributeValues().get(":timestamp").n()).isEqualTo(timestamp.toString());
    }

    /**
     * Helper method to create mock Season items in DynamoDB attribute format.
     */
    private Map<String, AttributeValue> createMockSeasonItem(Integer showId, Integer seasonNumber, String showName) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", AttributeValue.builder().s(InviterKeyFactory.getSeasonPk(showId)).build());
        item.put("sk", AttributeValue.builder().s(InviterKeyFactory.getSeasonSk(seasonNumber)).build());
        item.put("itemType", AttributeValue.builder().s("SEASON").build());
        item.put("showId", AttributeValue.builder().n(showId.toString()).build());
        item.put("seasonNumber", AttributeValue.builder().n(seasonNumber.toString()).build());
        item.put("showName", AttributeValue.builder().s(showName).build());
        item.put("externalId", AttributeValue.builder().s(showId.toString()).build());
        item.put("externalSource", AttributeValue.builder().s("TVMAZE").build());
        return item;
    }
}
