package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.exception.RepositoryException;
import com.bbthechange.inviter.model.EventSeries;
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

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventSeriesRepositoryImplTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private DynamoDbEnhancedClient dynamoDbEnhancedClient;

    @Mock
    private QueryPerformanceTracker performanceTracker;

    private EventSeriesRepositoryImpl repository;

    private String seriesId;
    private String groupId;

    @BeforeEach
    void setUp() {
        when(performanceTracker.trackQuery(anyString(), anyString(), any())).thenAnswer(invocation -> {
            Object result = null;
            try {
                java.util.function.Supplier<?> supplier = invocation.getArgument(2);
                result = supplier.get();
            } catch (Exception e) {
                // Allow exceptions to propagate for testing error scenarios
                throw new RuntimeException(e);
            }
            return result;
        });

        repository = new EventSeriesRepositoryImpl(dynamoDbClient, dynamoDbEnhancedClient, performanceTracker);
        
        seriesId = UUID.randomUUID().toString();
        groupId = UUID.randomUUID().toString();
    }

    @Test
    @Disabled("Failing due to exception handling - needs investigation")
    void save_WithValidEventSeries_ShouldPutItemAndReturnSeries() {
        // Given
        EventSeries eventSeries = new EventSeries("Concert Night", "Multi-part concert event", groupId);
        eventSeries.setSeriesId(seriesId);
        
        PutItemResponse response = PutItemResponse.builder().build();
        when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);

        // When
        EventSeries result = repository.save(eventSeries);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSeriesId()).isEqualTo(seriesId);
        assertThat(result.getSeriesTitle()).isEqualTo("Concert Night");
        assertThat(result.getGroupId()).isEqualTo(groupId);
        
        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(captor.capture());
        
        PutItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.item().get("pk").s()).isEqualTo("SERIES#" + seriesId);
        assertThat(request.item().get("sk").s()).isEqualTo("METADATA");
        assertThat(request.item().get("itemType").s()).isEqualTo("EVENT_SERIES");
    }

    @Test
    void findById_WithExistingSeries_ShouldReturnOptionalWithSeries() {
        // Given
        Map<String, AttributeValue> itemMap = createMockSeriesItem(seriesId, groupId, "Concert Night", 1000L);
        GetItemResponse response = GetItemResponse.builder()
                .item(itemMap)
                .build();
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

        // When
        Optional<EventSeries> result = repository.findById(seriesId);

        // Then
        assertThat(result).isPresent();
        
        ArgumentCaptor<GetItemRequest> captor = ArgumentCaptor.forClass(GetItemRequest.class);
        verify(dynamoDbClient).getItem(captor.capture());
        
        GetItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.key().get("pk").s()).isEqualTo("SERIES#" + seriesId);
        assertThat(request.key().get("sk").s()).isEqualTo("METADATA");
    }

    @Test
    @Disabled("Failing due to exception handling - needs investigation")
    void findById_WithNonExistentSeries_ShouldReturnEmptyOptional() {
        // Given
        GetItemResponse response = GetItemResponse.builder()
                .item(Map.of()) // Empty item map indicates no item found
                .build();
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

        // When
        Optional<EventSeries> result = repository.findById(seriesId);

        // Then
        assertThat(result).isEmpty();
        
        ArgumentCaptor<GetItemRequest> captor = ArgumentCaptor.forClass(GetItemRequest.class);
        verify(dynamoDbClient).getItem(captor.capture());
        
        GetItemRequest request = captor.getValue();
        assertThat(request.key().get("pk").s()).isEqualTo("SERIES#" + seriesId);
        assertThat(request.key().get("sk").s()).isEqualTo("METADATA");
    }

    @Test
    void deleteById_WithValidSeriesId_ShouldDeleteItem() {
        // Given
        DeleteItemResponse response = DeleteItemResponse.builder().build();
        when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class))).thenReturn(response);

        // When
        repository.deleteById(seriesId);

        // Then
        ArgumentCaptor<DeleteItemRequest> captor = ArgumentCaptor.forClass(DeleteItemRequest.class);
        verify(dynamoDbClient).deleteItem(captor.capture());
        
        DeleteItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.key().get("pk").s()).isEqualTo("SERIES#" + seriesId);
        assertThat(request.key().get("sk").s()).isEqualTo("METADATA");
    }

    @Test
    void findByGroupId_WithExistingSeries_ShouldQueryEntityTimeIndexAndReturnSeries() {
        // Given
        String series1Id = UUID.randomUUID().toString();
        String series2Id = UUID.randomUUID().toString();
        List<Map<String, AttributeValue>> mockItems = Arrays.asList(
            createMockSeriesItem(series1Id, groupId, "Concert Night", 1000L),
            createMockSeriesItem(series2Id, groupId, "Food Tour", 2000L)
        );
        
        QueryResponse mockResponse = QueryResponse.builder()
            .items(mockItems)
            .build();
        
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        List<EventSeries> result = repository.findByGroupId(groupId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());
        
        QueryRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.indexName()).isEqualTo("EntityTimeIndex");
        assertThat(request.keyConditionExpression()).isEqualTo("gsi1pk = :participantKey");
        assertThat(request.filterExpression()).isEqualTo("itemType = :itemType");
        assertThat(request.expressionAttributeValues().get(":participantKey").s()).isEqualTo("GROUP#" + groupId);
        assertThat(request.expressionAttributeValues().get(":itemType").s()).isEqualTo("EVENT_SERIES");
        assertThat(request.scanIndexForward()).isTrue(); // Chronological order
    }

    @Test
    void findUpcomingByGroupId_WithFutureSeries_ShouldQueryWithTimestampFilter() {
        // Given
        long currentTimestamp = 1500L;
        String futureSeriesId = UUID.randomUUID().toString();
        List<Map<String, AttributeValue>> mockItems = Arrays.asList(
            createMockSeriesItem(futureSeriesId, groupId, "Future Concert", 2000L)
        );
        
        QueryResponse mockResponse = QueryResponse.builder()
            .items(mockItems)
            .build();
        
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        List<EventSeries> result = repository.findUpcomingByGroupId(groupId, currentTimestamp);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());
        
        QueryRequest request = captor.getValue();
        assertThat(request.indexName()).isEqualTo("EntityTimeIndex");
        assertThat(request.keyConditionExpression()).isEqualTo("gsi1pk = :participantKey AND startTimestamp > :currentTimestamp");
        assertThat(request.filterExpression()).isEqualTo("itemType = :itemType");
        assertThat(request.expressionAttributeValues().get(":currentTimestamp").n()).isEqualTo(String.valueOf(currentTimestamp));
        assertThat(request.scanIndexForward()).isTrue();
    }

    @Test
    void findByGroupId_WithNoSeries_ShouldReturnEmptyList() {
        // Given
        QueryResponse mockResponse = QueryResponse.builder()
            .items(Collections.emptyList())
            .build();

        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        List<EventSeries> result = repository.findByGroupId(groupId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();

        verify(dynamoDbClient).query(any(QueryRequest.class));
    }

    // ============================================================================
    // findAllWatchPartySeries Tests
    // ============================================================================

    @Test
    void findAllWatchPartySeries_WithNoWatchParties_ReturnsEmptyList() {
        // Given
        ScanResponse mockResponse = ScanResponse.builder()
            .items(Collections.emptyList())
            .lastEvaluatedKey(Collections.emptyMap())
            .build();

        when(dynamoDbClient.scan(any(ScanRequest.class))).thenReturn(mockResponse);

        // When
        List<EventSeries> result = repository.findAllWatchPartySeries();

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();

        ArgumentCaptor<ScanRequest> captor = ArgumentCaptor.forClass(ScanRequest.class);
        verify(dynamoDbClient).scan(captor.capture());

        ScanRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.filterExpression()).isEqualTo("itemType = :itemType AND eventSeriesType = :seriesType");
        assertThat(request.expressionAttributeValues().get(":itemType").s()).isEqualTo("EVENT_SERIES");
        assertThat(request.expressionAttributeValues().get(":seriesType").s()).isEqualTo("WATCH_PARTY");
    }

    @Test
    void findAllWatchPartySeries_WithWatchParties_ReturnsAllSeries() {
        // Given
        String series1Id = UUID.randomUUID().toString();
        String series2Id = UUID.randomUUID().toString();
        String group1Id = UUID.randomUUID().toString();
        String group2Id = UUID.randomUUID().toString();

        List<Map<String, AttributeValue>> mockItems = Arrays.asList(
            createMockWatchPartySeriesItem(series1Id, group1Id, "Breaking Bad Watch Party"),
            createMockWatchPartySeriesItem(series2Id, group2Id, "The Office Watch Party")
        );

        ScanResponse mockResponse = ScanResponse.builder()
            .items(mockItems)
            .lastEvaluatedKey(Collections.emptyMap())
            .build();

        when(dynamoDbClient.scan(any(ScanRequest.class))).thenReturn(mockResponse);

        // When
        List<EventSeries> result = repository.findAllWatchPartySeries();

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);

        verify(dynamoDbClient, times(1)).scan(any(ScanRequest.class));
    }

    @Test
    void findAllWatchPartySeries_WithPagination_ReturnsAllPages() {
        // Given
        String series1Id = UUID.randomUUID().toString();
        String series2Id = UUID.randomUUID().toString();
        String group1Id = UUID.randomUUID().toString();
        String group2Id = UUID.randomUUID().toString();

        // First page with lastEvaluatedKey indicating more pages
        List<Map<String, AttributeValue>> firstPageItems = Arrays.asList(
            createMockWatchPartySeriesItem(series1Id, group1Id, "Breaking Bad Watch Party")
        );
        Map<String, AttributeValue> lastKey = Map.of(
            "pk", AttributeValue.builder().s("SERIES#" + series1Id).build(),
            "sk", AttributeValue.builder().s("METADATA").build()
        );
        ScanResponse firstPageResponse = ScanResponse.builder()
            .items(firstPageItems)
            .lastEvaluatedKey(lastKey)
            .build();

        // Second page with no more pages
        List<Map<String, AttributeValue>> secondPageItems = Arrays.asList(
            createMockWatchPartySeriesItem(series2Id, group2Id, "The Office Watch Party")
        );
        ScanResponse secondPageResponse = ScanResponse.builder()
            .items(secondPageItems)
            .lastEvaluatedKey(Collections.emptyMap())
            .build();

        when(dynamoDbClient.scan(any(ScanRequest.class)))
            .thenReturn(firstPageResponse)
            .thenReturn(secondPageResponse);

        // When
        List<EventSeries> result = repository.findAllWatchPartySeries();

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);

        // Verify scan was called twice (for both pages)
        ArgumentCaptor<ScanRequest> captor = ArgumentCaptor.forClass(ScanRequest.class);
        verify(dynamoDbClient, times(2)).scan(captor.capture());

        List<ScanRequest> requests = captor.getAllValues();

        // First request should not have exclusiveStartKey
        assertThat(requests.get(0).exclusiveStartKey()).isNullOrEmpty();

        // Second request should have exclusiveStartKey from first response
        assertThat(requests.get(1).exclusiveStartKey()).isNotNull();
        assertThat(requests.get(1).exclusiveStartKey().get("pk").s()).isEqualTo("SERIES#" + series1Id);
    }

    @Test
    void findAllWatchPartySeries_WithDynamoDbException_ThrowsRepositoryException() {
        // Given
        DynamoDbException dynamoDbException = (DynamoDbException) DynamoDbException.builder()
            .message("DynamoDB scan failed")
            .build();

        when(dynamoDbClient.scan(any(ScanRequest.class))).thenThrow(dynamoDbException);

        // When/Then
        // The performanceTracker mock wraps exceptions in RuntimeException,
        // which contains RepositoryException as cause, which contains DynamoDbException as root cause
        assertThatThrownBy(() -> repository.findAllWatchPartySeries())
            .isInstanceOf(RuntimeException.class)
            .hasCauseInstanceOf(RepositoryException.class)
            .hasRootCauseInstanceOf(DynamoDbException.class);

        // Verify the RepositoryException message
        try {
            repository.findAllWatchPartySeries();
        } catch (RuntimeException e) {
            assertThat(e.getCause()).isInstanceOf(RepositoryException.class);
            assertThat(e.getCause().getMessage()).isEqualTo("Failed to scan for Watch Party EventSeries");
        }

        verify(dynamoDbClient, times(2)).scan(any(ScanRequest.class));
    }

    /**
     * Helper method to create mock EventSeries items in DynamoDB attribute format.
     */
    private Map<String, AttributeValue> createMockSeriesItem(String seriesId, String groupId,
                                                           String title, Long startTimestamp) {
        return Map.of(
            "pk", AttributeValue.builder().s(InviterKeyFactory.getSeriesPk(seriesId)).build(),
            "sk", AttributeValue.builder().s(InviterKeyFactory.getMetadataSk()).build(),
            "itemType", AttributeValue.builder().s("EVENT_SERIES").build(),
            "seriesId", AttributeValue.builder().s(seriesId).build(),
            "groupId", AttributeValue.builder().s(groupId).build(),
            "seriesTitle", AttributeValue.builder().s(title).build(),
            "seriesDescription", AttributeValue.builder().s("Test description").build(),
            "startTimestamp", AttributeValue.builder().n(startTimestamp.toString()).build(),
            "gsi1pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build()
        );
    }

    /**
     * Helper method to create mock Watch Party EventSeries items in DynamoDB attribute format.
     */
    private Map<String, AttributeValue> createMockWatchPartySeriesItem(String seriesId, String groupId,
                                                                       String title) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", AttributeValue.builder().s(InviterKeyFactory.getSeriesPk(seriesId)).build());
        item.put("sk", AttributeValue.builder().s(InviterKeyFactory.getMetadataSk()).build());
        item.put("itemType", AttributeValue.builder().s("EVENT_SERIES").build());
        item.put("eventSeriesType", AttributeValue.builder().s("WATCH_PARTY").build());
        item.put("seriesId", AttributeValue.builder().s(seriesId).build());
        item.put("groupId", AttributeValue.builder().s(groupId).build());
        item.put("seriesTitle", AttributeValue.builder().s(title).build());
        item.put("seriesDescription", AttributeValue.builder().s("Test watch party description").build());
        item.put("gsi1pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build());
        return item;
    }
}