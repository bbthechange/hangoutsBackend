package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.util.InviterKeyFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for series-related operations in HangoutRepositoryImpl.
 *
 * Coverage:
 * - Series queries via SeriesIndex GSI
 * - Hangout retrieval by series ID
 * - Chronological ordering of series hangouts
 */
class HangoutRepositorySeriesTest extends HangoutRepositoryTestBase {

    @Test
    void findHangoutsBySeriesId_GivenValidId_ShouldQuerySeriesIndexAndReturnHangouts() {
        // GIVEN: A series ID and a list of hangouts we expect to get back
        String testSeriesId = UUID.randomUUID().toString();

        // Create mock response items representing hangouts in DynamoDB format
        String hangout1Id = UUID.randomUUID().toString();
        String hangout2Id = UUID.randomUUID().toString();
        List<Map<String, AttributeValue>> mockItems = Arrays.asList(
            createMockHangoutItem(hangout1Id, testSeriesId, 1000L),
            createMockHangoutItem(hangout2Id, testSeriesId, 2000L)
        );

        QueryResponse mockResponse = QueryResponse.builder()
            .items(mockItems)
            .build();

        // MOCKING: Configure mock to return our expected hangouts when SeriesIndex is queried
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // WHEN: We call the method we are testing
        List<Hangout> actualHangouts = repository.findHangoutsBySeriesId(testSeriesId);

        // THEN: We verify the results
        // 1. Confirm the SeriesIndex was used and not the main table
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());

        QueryRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.indexName()).isEqualTo("SeriesIndex");
        assertThat(request.keyConditionExpression()).isEqualTo("seriesId = :seriesId");
        assertThat(request.expressionAttributeValues().get(":seriesId").s()).isEqualTo(testSeriesId);
        assertThat(request.scanIndexForward()).isTrue(); // Chronological order

        // 2. Confirm the data returned is what we expected
        assertThat(actualHangouts).isNotNull();
        assertThat(actualHangouts).hasSize(2);
        assertThat(actualHangouts.get(0).getHangoutId()).isEqualTo(hangout1Id);
        assertThat(actualHangouts.get(1).getHangoutId()).isEqualTo(hangout2Id);
    }

    @Test
    void findHangoutsBySeriesId_GivenSeriesWithNoHangouts_ShouldReturnEmptyList() {
        // GIVEN: A series ID with no hangouts
        String testSeriesId = UUID.randomUUID().toString();

        QueryResponse mockResponse = QueryResponse.builder()
            .items(Collections.emptyList())
            .build();

        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // WHEN: We call the method
        List<Hangout> actualHangouts = repository.findHangoutsBySeriesId(testSeriesId);

        // THEN: We get an empty list and SeriesIndex was queried
        assertThat(actualHangouts).isNotNull();
        assertThat(actualHangouts).isEmpty();

        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());

        QueryRequest request = captor.getValue();
        assertThat(request.indexName()).isEqualTo("SeriesIndex");
        assertThat(request.expressionAttributeValues().get(":seriesId").s()).isEqualTo(testSeriesId);
    }

    @Test
    void findHangoutsBySeriesId_GivenDifferentSeriesId_ShouldQueryWithCorrectSeriesId() {
        // GIVEN: A different series ID
        String differentSeriesId = UUID.randomUUID().toString();

        QueryResponse mockResponse = QueryResponse.builder()
            .items(Collections.emptyList())
            .build();

        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // WHEN: We call the method with different series ID
        repository.findHangoutsBySeriesId(differentSeriesId);

        // THEN: The query should use the correct series ID parameter
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());

        QueryRequest request = captor.getValue();
        assertThat(request.expressionAttributeValues().get(":seriesId").s()).isEqualTo(differentSeriesId);
    }

    /**
     * Helper method to create mock hangout items for testing.
     */
    private Map<String, AttributeValue> createMockHangoutItem(String hangoutId, String seriesId, Long timestamp) {
        return Map.of(
            "pk", AttributeValue.builder().s(InviterKeyFactory.getEventPk(hangoutId)).build(),
            "sk", AttributeValue.builder().s(InviterKeyFactory.getMetadataSk()).build(),
            "itemType", AttributeValue.builder().s("HANGOUT").build(),
            "hangoutId", AttributeValue.builder().s(hangoutId).build(),
            "seriesId", AttributeValue.builder().s(seriesId).build(),
            "startTimestamp", AttributeValue.builder().n(timestamp.toString()).build(),
            "title", AttributeValue.builder().s("Test Hangout " + hangoutId).build(),
            "description", AttributeValue.builder().s("Test description").build()
        );
    }
}
