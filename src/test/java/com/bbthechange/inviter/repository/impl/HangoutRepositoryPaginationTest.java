package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.exception.InvalidKeyException;
import com.bbthechange.inviter.exception.RepositoryException;
import com.bbthechange.inviter.model.BaseItem;
import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.model.SeriesPointer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for pagination methods in HangoutRepositoryImpl.
 *
 * Covers:
 * - getInProgressEventsPage() - Events currently happening (8 tests)
 * - findUpcomingHangoutsPage() - Future events with HangoutPointer filtering (6 tests)
 * - getPastEventsPage() - Past events (4 tests)
 * - getFutureEventsPage() - Future events without filtering (3 tests)
 * - findUpcomingHangoutsForParticipant() - Non-paginated upcoming events (7 tests)
 * - Common pagination behaviors (3 tests)
 *
 * Total tests: 31
 */
class HangoutRepositoryPaginationTest extends HangoutRepositoryTestBase {

    // ========== getInProgressEventsPage Tests ==========

    @Test
    void getInProgressEventsPage_ValidParameters_UsesEndTimestampIndexGSI() {
        // Given
        String groupId = UUID.randomUUID().toString();
        long nowTimestamp = 1640995200L; // Current time
        int limit = 10;
        String startToken = null;

        QueryResponse mockResponse = QueryResponse.builder()
            .items(Collections.emptyList())
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        repository.getInProgressEventsPage(groupId, nowTimestamp, limit, startToken);

        // Then
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());

        QueryRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.indexName()).isEqualTo("EndTimestampIndex");

        // Verify performance tracker was called
        verify(performanceTracker).trackQuery(eq("getInProgressEventsPage"), eq("EndTimestampIndex"), any());
    }

    @Test
    void getInProgressEventsPage_ValidParameters_CorrectKeyCondition() {
        // Given
        String groupId = UUID.randomUUID().toString();
        long nowTimestamp = 1640995200L;
        String expectedParticipantKey = "GROUP#" + groupId;

        QueryResponse mockResponse = QueryResponse.builder()
            .items(Collections.emptyList())
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        repository.getInProgressEventsPage(groupId, nowTimestamp, 10, null);

        // Then
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());

        QueryRequest request = captor.getValue();
        assertThat(request.keyConditionExpression())
            .isEqualTo("gsi1pk = :participantKey AND endTimestamp > :nowTimestamp");
        assertThat(request.expressionAttributeValues())
            .containsEntry(":participantKey", AttributeValue.builder().s(expectedParticipantKey).build())
            .containsEntry(":nowTimestamp", AttributeValue.builder().n(String.valueOf(nowTimestamp)).build());
    }

    @Test
    void getInProgressEventsPage_ValidParameters_CorrectFilterExpression() {
        // Given
        String groupId = UUID.randomUUID().toString();
        long nowTimestamp = 1640995200L;

        QueryResponse mockResponse = QueryResponse.builder()
            .items(Collections.emptyList())
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        repository.getInProgressEventsPage(groupId, nowTimestamp, 10, null);

        // Then
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());

        QueryRequest request = captor.getValue();
        assertThat(request.filterExpression()).isEqualTo("startTimestamp <= :nowTimestamp");
    }

    @Test
    void getInProgressEventsPage_ValidParameters_SortsAscendingByEndTimestamp() {
        // Given
        String groupId = UUID.randomUUID().toString();
        long nowTimestamp = 1640995200L;

        QueryResponse mockResponse = QueryResponse.builder()
            .items(Collections.emptyList())
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        repository.getInProgressEventsPage(groupId, nowTimestamp, 10, null);

        // Then
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());

        QueryRequest request = captor.getValue();
        assertThat(request.scanIndexForward()).isTrue();
    }

    @Test
    void getInProgressEventsPage_WithLimit_AppliesLimitToQuery() {
        // Given
        String groupId = UUID.randomUUID().toString();
        long nowTimestamp = 1640995200L;
        int limit = 5;

        QueryResponse mockResponse = QueryResponse.builder()
            .items(Collections.emptyList())
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        repository.getInProgressEventsPage(groupId, nowTimestamp, limit, null);

        // Then
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());

        QueryRequest request = captor.getValue();
        assertThat(request.limit()).isEqualTo(limit);
    }

    @Test
    void getInProgressEventsPage_WithValidToken_IncludesExclusiveStartKey() {
        // Given
        String groupId = UUID.randomUUID().toString();
        long nowTimestamp = 1640995200L;
        String validToken = createValidEndTimestampToken(groupId, "eventId", 1640995300L);

        QueryResponse mockResponse = QueryResponse.builder()
            .items(Collections.emptyList())
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        repository.getInProgressEventsPage(groupId, nowTimestamp, 10, validToken);

        // Then
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());

        QueryRequest request = captor.getValue();
        assertThat(request.exclusiveStartKey()).isNotNull();
        assertThat(request.exclusiveStartKey()).containsKey("gsi1pk");
        assertThat(request.exclusiveStartKey()).containsKey("endTimestamp");
        assertThat(request.exclusiveStartKey()).containsKey("pk");
        assertThat(request.exclusiveStartKey()).containsKey("sk");
    }

    @Test
    void getInProgressEventsPage_WithInvalidToken_ThrowsIllegalArgumentException() {
        // Given
        String groupId = UUID.randomUUID().toString();
        long nowTimestamp = 1640995200L;
        // Create an invalid Base64 token (not valid Base64)
        String invalidToken = "not_valid_base64!@#$%";

        // When/Then - Exception should occur during token parsing, before DynamoDB call
        assertThatThrownBy(() -> repository.getInProgressEventsPage(groupId, nowTimestamp, 10, invalidToken))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid pagination token");

        // Verify DynamoDB was never called due to token parsing failure
        verify(dynamoDbClient, never()).query(any(QueryRequest.class));
    }

    @Test
    void getInProgressEventsPage_WithResults_ReturnsCorrectBaseItems() {
        // Given
        String groupId = UUID.randomUUID().toString();
        long nowTimestamp = 1640995200L;
        String hangoutId1 = UUID.randomUUID().toString();
        String seriesId1 = UUID.randomUUID().toString();

        List<Map<String, AttributeValue>> mockItems = Arrays.asList(
            createMockHangoutPointerItemForPagination(groupId, hangoutId1, "Event 1", nowTimestamp - 1000, nowTimestamp + 2000),
            createMockSeriesPointerItemForPagination(groupId, seriesId1, "Series 1", nowTimestamp - 500, nowTimestamp + 1500)
        );

        QueryResponse mockResponse = QueryResponse.builder()
            .items(mockItems)
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        var result = repository.getInProgressEventsPage(groupId, nowTimestamp, 10, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getResults()).hasSize(2);
        assertThat(result.getResults().get(0)).isInstanceOf(HangoutPointer.class);
        assertThat(result.getResults().get(1)).isInstanceOf(SeriesPointer.class);
    }

    @Test
    void getInProgressEventsPage_WithLastEvaluatedKey_GeneratesNextToken() {
        // Given
        String groupId = UUID.randomUUID().toString();
        long nowTimestamp = 1640995200L;

        Map<String, AttributeValue> lastEvaluatedKey = Map.of(
            "gsi1pk", AttributeValue.builder().s("GROUP#" + groupId).build(),
            "endTimestamp", AttributeValue.builder().n("1640995300").build(),
            "pk", AttributeValue.builder().s("GROUP#" + groupId).build(),
            "sk", AttributeValue.builder().s("HANGOUT#eventId").build()
        );

        QueryResponse mockResponse = QueryResponse.builder()
            .items(Collections.emptyList())
            .lastEvaluatedKey(lastEvaluatedKey)
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        var result = repository.getInProgressEventsPage(groupId, nowTimestamp, 10, null);

        // Then
        assertThat(result.getNextToken()).isNotNull();
        assertThat(result.hasMore()).isTrue();
    }

    // ========== findUpcomingHangoutsPage Tests ==========

    @Test
    void findUpcomingHangoutsPage_ValidParameters_UsesEntityTimeIndexGSI() {
        // Given
        String participantKey = "GROUP#" + UUID.randomUUID().toString();
        String timePrefix = "T#";
        int limit = 10;

        QueryResponse mockResponse = QueryResponse.builder()
            .items(Collections.emptyList())
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        repository.findUpcomingHangoutsPage(participantKey, timePrefix, limit, null);

        // Then
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());

        QueryRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.indexName()).isEqualTo("EntityTimeIndex");

        // Verify performance tracker was called
        verify(performanceTracker).trackQuery(eq("findUpcomingHangoutsPage"), eq("EntityTimeIndex"), any());
    }

    @Test
    void findUpcomingHangoutsPage_ValidParameters_QueriesFutureEventsOnly() {
        // Given
        String participantKey = "GROUP#groupId";
        String timePrefix = "T#";
        long currentTime = System.currentTimeMillis() / 1000;

        QueryResponse mockResponse = QueryResponse.builder()
            .items(Collections.emptyList())
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        repository.findUpcomingHangoutsPage(participantKey, timePrefix, 10, null);

        // Then
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());

        QueryRequest request = captor.getValue();
        assertThat(request.keyConditionExpression())
            .isEqualTo("gsi1pk = :participantKey AND startTimestamp > :timestampPrefix");
        assertThat(request.expressionAttributeValues())
            .containsEntry(":participantKey", AttributeValue.builder().s(participantKey).build());

        // Verify it uses current timestamp, not the timePrefix parameter
        String timestampValue = request.expressionAttributeValues().get(":timestampPrefix").n();
        long queryTimestamp = Long.parseLong(timestampValue);
        assertThat(queryTimestamp).isCloseTo(currentTime, within(5L)); // Allow 5 second tolerance
    }

    @Test
    void findUpcomingHangoutsPage_WithMixedResults_FiltersToHangoutPointersOnly() {
        // Given
        String participantKey = "GROUP#groupId";
        String hangoutId = UUID.randomUUID().toString();
        String seriesId = UUID.randomUUID().toString();
        String groupId = "groupId";

        List<Map<String, AttributeValue>> mockItems = Arrays.asList(
            createMockHangoutPointerItemForPagination(groupId, hangoutId, "Hangout 1", 1640995300L, 1640995400L),
            createMockSeriesPointerItemForPagination(groupId, seriesId, "Series 1", 1640995350L, 1640995450L)
        );

        QueryResponse mockResponse = QueryResponse.builder()
            .items(mockItems)
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        var result = repository.findUpcomingHangoutsPage(participantKey, "T#", 10, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getResults()).hasSize(1); // Only HangoutPointer should remain
        assertThat(result.getResults().get(0)).isInstanceOf(HangoutPointer.class);
        HangoutPointer pointer = (HangoutPointer) result.getResults().get(0);
        assertThat(pointer.getHangoutId()).isEqualTo(hangoutId);
    }

    @Test
    void findUpcomingHangoutsPage_WithNullToken_StartsPaginationFromBeginning() {
        // Given
        String participantKey = "GROUP#groupId";

        QueryResponse mockResponse = QueryResponse.builder()
            .items(Collections.emptyList())
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        repository.findUpcomingHangoutsPage(participantKey, "T#", 10, null);

        // Then
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());

        QueryRequest request = captor.getValue();
        // AWS SDK returns empty map when no exclusiveStartKey is set
        assertThat(request.exclusiveStartKey()).isEmpty();
    }

    @Test
    void findUpcomingHangoutsPage_WithEmptyToken_StartsPaginationFromBeginning() {
        // Given
        String participantKey = "GROUP#groupId";

        QueryResponse mockResponse = QueryResponse.builder()
            .items(Collections.emptyList())
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        repository.findUpcomingHangoutsPage(participantKey, "T#", 10, "   ");

        // Then
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());

        QueryRequest request = captor.getValue();
        // AWS SDK returns empty map when no exclusiveStartKey is set
        assertThat(request.exclusiveStartKey()).isEmpty();
    }

    @Test
    void findUpcomingHangoutsPage_WithValidToken_ParsesAndUsesStartKey() {
        // Given
        String participantKey = "GROUP#groupId";
        String validToken = createValidStartToken("groupId", "eventId", 1640995300L);

        QueryResponse mockResponse = QueryResponse.builder()
            .items(Collections.emptyList())
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        repository.findUpcomingHangoutsPage(participantKey, "T#", 10, validToken);

        // Then
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());

        QueryRequest request = captor.getValue();
        assertThat(request.exclusiveStartKey()).isNotNull();
        assertThat(request.exclusiveStartKey()).containsKey("gsi1pk");
        assertThat(request.exclusiveStartKey()).containsKey("startTimestamp");
        assertThat(request.exclusiveStartKey()).containsKey("pk");
        assertThat(request.exclusiveStartKey()).containsKey("sk");
    }

    // ========== getPastEventsPage Tests ==========

    @Test
    void getPastEventsPage_ValidParameters_UsesEntityTimeIndexGSI() {
        // Given
        String groupId = UUID.randomUUID().toString();
        long nowTimestamp = 1640995200L;

        QueryResponse mockResponse = QueryResponse.builder()
            .items(Collections.emptyList())
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        repository.getPastEventsPage(groupId, nowTimestamp, 10, null);

        // Then
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());

        QueryRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.indexName()).isEqualTo("EntityTimeIndex");

        // Verify performance tracker was called
        verify(performanceTracker).trackQuery(eq("getPastEventsPage"), eq("EntityTimeIndex"), any());
    }

    @Test
    void getPastEventsPage_ValidParameters_SortsDescendingByStartTimestamp() {
        // Given
        String groupId = UUID.randomUUID().toString();
        long nowTimestamp = 1640995200L;

        QueryResponse mockResponse = QueryResponse.builder()
            .items(Collections.emptyList())
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        repository.getPastEventsPage(groupId, nowTimestamp, 10, null);

        // Then
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());

        QueryRequest request = captor.getValue();
        assertThat(request.scanIndexForward()).isFalse(); // Reverse chronological order
    }

    @Test
    void getPastEventsPage_ValidParameters_QueriesPastEventsOnly() {
        // Given
        String groupId = UUID.randomUUID().toString();
        long nowTimestamp = 1640995200L;
        String expectedParticipantKey = "GROUP#" + groupId;

        QueryResponse mockResponse = QueryResponse.builder()
            .items(Collections.emptyList())
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        repository.getPastEventsPage(groupId, nowTimestamp, 10, null);

        // Then
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());

        QueryRequest request = captor.getValue();
        assertThat(request.keyConditionExpression())
            .isEqualTo("gsi1pk = :participantKey AND startTimestamp < :nowTimestamp");
        assertThat(request.expressionAttributeValues())
            .containsEntry(":participantKey", AttributeValue.builder().s(expectedParticipantKey).build())
            .containsEntry(":nowTimestamp", AttributeValue.builder().n(String.valueOf(nowTimestamp)).build());
    }

    @Test
    void getPastEventsPage_WithResults_ReturnsBaseItemsNotFiltered() {
        // Given
        String groupId = UUID.randomUUID().toString();
        long nowTimestamp = 1640995200L;
        String hangoutId = UUID.randomUUID().toString();
        String seriesId = UUID.randomUUID().toString();

        List<Map<String, AttributeValue>> mockItems = Arrays.asList(
            createMockHangoutPointerItemForPagination(groupId, hangoutId, "Past Event", nowTimestamp - 2000, nowTimestamp - 1000),
            createMockSeriesPointerItemForPagination(groupId, seriesId, "Past Series", nowTimestamp - 1500, nowTimestamp - 500)
        );

        QueryResponse mockResponse = QueryResponse.builder()
            .items(mockItems)
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        var result = repository.getPastEventsPage(groupId, nowTimestamp, 10, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getResults()).hasSize(2); // Both types should be included
        assertThat(result.getResults().get(0)).isInstanceOf(HangoutPointer.class);
        assertThat(result.getResults().get(1)).isInstanceOf(SeriesPointer.class);
    }

    // ========== getFutureEventsPage Tests ==========

    @Test
    void getFutureEventsPage_ValidParameters_UsesEntityTimeIndexGSI() {
        // Given
        String groupId = UUID.randomUUID().toString();
        long nowTimestamp = 1640995200L;

        QueryResponse mockResponse = QueryResponse.builder()
            .items(Collections.emptyList())
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        repository.getFutureEventsPage(groupId, nowTimestamp, 10, null);

        // Then
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());

        QueryRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.indexName()).isEqualTo("EntityTimeIndex");

        // Verify performance tracker was called
        verify(performanceTracker).trackQuery(eq("getFutureEventsPage"), eq("EntityTimeIndex"), any());
    }

    @Test
    void getFutureEventsPage_ValidParameters_UsesSameQueryLogicAsUpcoming() {
        // Given
        String groupId = UUID.randomUUID().toString();
        long nowTimestamp = 1640995200L;
        String expectedParticipantKey = "GROUP#" + groupId;

        QueryResponse mockResponse = QueryResponse.builder()
            .items(Collections.emptyList())
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        repository.getFutureEventsPage(groupId, nowTimestamp, 10, null);

        // Then
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());

        QueryRequest request = captor.getValue();
        assertThat(request.keyConditionExpression())
            .isEqualTo("gsi1pk = :participantKey AND startTimestamp > :nowTimestamp");
        assertThat(request.scanIndexForward()).isTrue(); // Chronological order
        assertThat(request.expressionAttributeValues())
            .containsEntry(":participantKey", AttributeValue.builder().s(expectedParticipantKey).build())
            .containsEntry(":nowTimestamp", AttributeValue.builder().n(String.valueOf(nowTimestamp)).build());
    }

    @Test
    void getFutureEventsPage_WithResults_ReturnsAllBaseItemTypes() {
        // Given
        String groupId = UUID.randomUUID().toString();
        long nowTimestamp = 1640995200L;
        String hangoutId = UUID.randomUUID().toString();
        String seriesId = UUID.randomUUID().toString();

        List<Map<String, AttributeValue>> mockItems = Arrays.asList(
            createMockHangoutPointerItemForPagination(groupId, hangoutId, "Future Event", nowTimestamp + 1000, nowTimestamp + 2000),
            createMockSeriesPointerItemForPagination(groupId, seriesId, "Future Series", nowTimestamp + 1500, nowTimestamp + 2500)
        );

        QueryResponse mockResponse = QueryResponse.builder()
            .items(mockItems)
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        var result = repository.getFutureEventsPage(groupId, nowTimestamp, 10, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getResults()).hasSize(2); // No type filtering
        assertThat(result.getResults().get(0)).isInstanceOf(HangoutPointer.class);
        assertThat(result.getResults().get(1)).isInstanceOf(SeriesPointer.class);
    }

    // ========== Cross-Method Common Tests ==========

    @Test
    void allPaginationMethods_WhenDynamoDbException_ThrowsRepositoryException() {
        // Given
        String groupId = UUID.randomUUID().toString();
        long nowTimestamp = 1640995200L;
        String participantKey = "GROUP#" + groupId;

        when(dynamoDbClient.query(any(QueryRequest.class)))
            .thenThrow(DynamoDbException.builder().message("DynamoDB error").build());

        // When/Then - Test all four methods
        assertThatThrownBy(() -> repository.getInProgressEventsPage(groupId, nowTimestamp, 10, null))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("Failed to query in-progress events")
            .hasCauseInstanceOf(DynamoDbException.class);

        assertThatThrownBy(() -> repository.findUpcomingHangoutsPage(participantKey, "T#", 10, null))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("Failed to query paginated upcoming hangouts from EntityTimeIndex GSI")
            .hasCauseInstanceOf(DynamoDbException.class);

        assertThatThrownBy(() -> repository.getPastEventsPage(groupId, nowTimestamp, 10, null))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("Failed to query past events")
            .hasCauseInstanceOf(DynamoDbException.class);

        assertThatThrownBy(() -> repository.getFutureEventsPage(groupId, nowTimestamp, 10, null))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("Failed to query future events")
            .hasCauseInstanceOf(DynamoDbException.class);
    }

    @Test
    void allPaginationMethods_WithGroupId_UsesCorrectParticipantKeyFormat() {
        // Given
        String groupId = UUID.randomUUID().toString();
        long nowTimestamp = 1640995200L;
        String expectedParticipantKey = "GROUP#" + groupId;

        QueryResponse mockResponse = QueryResponse.builder()
            .items(Collections.emptyList())
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When - Call all methods that use groupId directly
        repository.getInProgressEventsPage(groupId, nowTimestamp, 10, null);
        repository.getPastEventsPage(groupId, nowTimestamp, 10, null);
        repository.getFutureEventsPage(groupId, nowTimestamp, 10, null);

        // Then
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient, times(3)).query(captor.capture());

        List<QueryRequest> requests = captor.getAllValues();
        for (QueryRequest request : requests) {
            assertThat(request.expressionAttributeValues())
                .containsEntry(":participantKey", AttributeValue.builder().s(expectedParticipantKey).build());
        }
    }

    @Test
    void allPaginationMethods_ExecuteWithinPerformanceTracker() {
        // Given
        String groupId = UUID.randomUUID().toString();
        long nowTimestamp = 1640995200L;
        String participantKey = "GROUP#" + groupId;

        QueryResponse mockResponse = QueryResponse.builder()
            .items(Collections.emptyList())
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        repository.getInProgressEventsPage(groupId, nowTimestamp, 10, null);
        repository.findUpcomingHangoutsPage(participantKey, "T#", 10, null);
        repository.getPastEventsPage(groupId, nowTimestamp, 10, null);
        repository.getFutureEventsPage(groupId, nowTimestamp, 10, null);

        // Then
        verify(performanceTracker).trackQuery(eq("getInProgressEventsPage"), eq("EndTimestampIndex"), any());
        verify(performanceTracker).trackQuery(eq("findUpcomingHangoutsPage"), eq("EntityTimeIndex"), any());
        verify(performanceTracker).trackQuery(eq("getPastEventsPage"), eq("EntityTimeIndex"), any());
        verify(performanceTracker).trackQuery(eq("getFutureEventsPage"), eq("EntityTimeIndex"), any());
    }

    @Test
    void allPaginationMethods_WithNullOrEmptyGroupId_ThrowsInvalidKeyException() {
        // Given
        long nowTimestamp = 1640995200L;

        // When/Then - Test that null/empty groupId validation works properly
        assertThatThrownBy(() -> repository.getInProgressEventsPage(null, nowTimestamp, 10, null))
            .isInstanceOf(InvalidKeyException.class)
            .hasMessage("Group ID cannot be null or empty");

        assertThatThrownBy(() -> repository.getPastEventsPage("", nowTimestamp, 10, null))
            .isInstanceOf(InvalidKeyException.class)
            .hasMessage("Group ID cannot be null or empty");

        assertThatThrownBy(() -> repository.getFutureEventsPage(null, nowTimestamp, 10, null))
            .isInstanceOf(InvalidKeyException.class)
            .hasMessage("Group ID cannot be null or empty");

        // Verify DynamoDB was never called due to validation failure
        verify(dynamoDbClient, never()).query(any(QueryRequest.class));
    }

    // ========== findUpcomingHangoutsForParticipant Tests ==========

    @Test
    void findUpcomingHangoutsForParticipant_ValidParameters_UsesEntityTimeIndexGSI() {
        // Given
        String participantKey = "GROUP#test-group-id";
        String timePrefix = "T#";

        QueryResponse mockResponse = QueryResponse.builder()
            .items(Collections.emptyList())
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        repository.findUpcomingHangoutsForParticipant(participantKey, timePrefix);

        // Then
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());

        QueryRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.indexName()).isEqualTo("EntityTimeIndex");

        // Verify performance tracker was called
        verify(performanceTracker).trackQuery(eq("findUpcomingHangoutsForParticipant"), eq("EntityTimeIndex"), any());
    }

    @Test
    void findUpcomingHangoutsForParticipant_ValidParameters_QueriesFutureEventsOnly() {
        // Given
        String participantKey = "GROUP#test-group-id";
        String timePrefix = "T#";

        QueryResponse mockResponse = QueryResponse.builder()
            .items(Collections.emptyList())
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        repository.findUpcomingHangoutsForParticipant(participantKey, timePrefix);

        // Then
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());

        QueryRequest request = captor.getValue();

        // Verify key condition filters future events only
        assertThat(request.keyConditionExpression()).isEqualTo("gsi1pk = :participantKey AND startTimestamp > :timestampPrefix");

        // Verify expression attribute values contain participant key and current timestamp
        assertThat(request.expressionAttributeValues()).containsKey(":participantKey");
        assertThat(request.expressionAttributeValues()).containsKey(":timestampPrefix");
        assertThat(request.expressionAttributeValues().get(":participantKey").s()).isEqualTo(participantKey);

        // Verify the timestamp is a current/recent timestamp (within last minute)
        String timestampValue = request.expressionAttributeValues().get(":timestampPrefix").n();
        long queryTimestamp = Long.parseLong(timestampValue);
        long currentTime = System.currentTimeMillis() / 1000;
        assertThat(queryTimestamp).isBetween(currentTime - 60, currentTime + 1); // Allow 1 minute variance
    }

    @Test
    void findUpcomingHangoutsForParticipant_ValidParameters_SortsAscendingByStartTimestamp() {
        // Given
        String participantKey = "GROUP#test-group-id";
        String timePrefix = "T#";

        QueryResponse mockResponse = QueryResponse.builder()
            .items(Collections.emptyList())
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        repository.findUpcomingHangoutsForParticipant(participantKey, timePrefix);

        // Then
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());

        QueryRequest request = captor.getValue();

        // Verify ascending sort order (chronological)
        assertThat(request.scanIndexForward()).isTrue();
    }

    @Test
    void findUpcomingHangoutsForParticipant_WithMixedResults_ReturnsAllBaseItemTypes() {
        // Given
        String participantKey = "GROUP#test-group-id";
        String timePrefix = "T#";
        String hangoutId = UUID.randomUUID().toString();
        String seriesId = UUID.randomUUID().toString();

        List<Map<String, AttributeValue>> mockItems = Arrays.asList(
            createMockHangoutPointerItemForPagination("test-group-id", hangoutId, "Test Hangout", 1640995200L, 1640999200L),
            createMockSeriesPointerItemForPagination("test-group-id", seriesId, "Test Series", 1640995300L, 1640999300L)
        );

        QueryResponse mockResponse = QueryResponse.builder()
            .items(mockItems)
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        List<BaseItem> result = repository.findUpcomingHangoutsForParticipant(participantKey, timePrefix);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isInstanceOf(HangoutPointer.class);
        assertThat(result.get(1)).isInstanceOf(SeriesPointer.class);

        // Verify both types are returned without filtering
        HangoutPointer hangoutPointer = (HangoutPointer) result.get(0);
        SeriesPointer seriesPointer = (SeriesPointer) result.get(1);

        assertThat(hangoutPointer.getHangoutId()).isEqualTo(hangoutId);
        assertThat(seriesPointer.getSeriesId()).isEqualTo(seriesId);
    }

    @Test
    void findUpcomingHangoutsForParticipant_DynamoDbException_ThrowsRepositoryException() {
        // Given
        String participantKey = "GROUP#test-group-id";
        String timePrefix = "T#";

        when(dynamoDbClient.query(any(QueryRequest.class)))
            .thenThrow(DynamoDbException.builder().message("DynamoDB error").build());

        // When/Then
        assertThatThrownBy(() -> repository.findUpcomingHangoutsForParticipant(participantKey, timePrefix))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("Failed to query upcoming hangouts from EntityTimeIndex GSI")
            .hasCauseInstanceOf(DynamoDbException.class);
    }

    @Test
    void findUpcomingHangoutsForParticipant_ValidParameters_TracksPerformance() {
        // Given
        String participantKey = "GROUP#test-group-id";
        String timePrefix = "T#";

        QueryResponse mockResponse = QueryResponse.builder()
            .items(Collections.emptyList())
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        repository.findUpcomingHangoutsForParticipant(participantKey, timePrefix);

        // Then
        verify(performanceTracker).trackQuery(
            eq("findUpcomingHangoutsForParticipant"),
            eq("EntityTimeIndex"),
            any()
        );
    }

    @Test
    void findUpcomingHangoutsForParticipant_WithResults_CallsDeserializeItem() {
        // Given
        String participantKey = "GROUP#test-group-id";
        String timePrefix = "T#";
        String hangoutId = UUID.randomUUID().toString();

        List<Map<String, AttributeValue>> mockItems = Arrays.asList(
            createMockHangoutPointerItemForPagination("test-group-id", hangoutId, "Test Hangout", 1640995200L, 1640999200L)
        );

        QueryResponse mockResponse = QueryResponse.builder()
            .items(mockItems)
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        List<BaseItem> result = repository.findUpcomingHangoutsForParticipant(participantKey, timePrefix);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isInstanceOf(HangoutPointer.class);

        // Verify deserializeItem was called for each result
        HangoutPointer hangoutPointer = (HangoutPointer) result.get(0);
        assertThat(hangoutPointer.getHangoutId()).isEqualTo(hangoutId);
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    private Map<String, AttributeValue> createMockHangoutPointerItemForPagination(
            String groupId, String hangoutId, String title, long startTimestamp, long endTimestamp) {
        return Map.of(
            "pk", AttributeValue.builder().s("GROUP#" + groupId).build(),
            "sk", AttributeValue.builder().s("HANGOUT#" + hangoutId).build(),
            "itemType", AttributeValue.builder().s("HANGOUT_POINTER").build(),
            "gsi1pk", AttributeValue.builder().s("GROUP#" + groupId).build(),
            "startTimestamp", AttributeValue.builder().n(String.valueOf(startTimestamp)).build(),
            "endTimestamp", AttributeValue.builder().n(String.valueOf(endTimestamp)).build(),
            "groupId", AttributeValue.builder().s(groupId).build(),
            "hangoutId", AttributeValue.builder().s(hangoutId).build(),
            "title", AttributeValue.builder().s(title).build(),
            "participantCount", AttributeValue.builder().n("1").build()
        );
    }

    private Map<String, AttributeValue> createMockSeriesPointerItemForPagination(
            String groupId, String seriesId, String title, long startTimestamp, long endTimestamp) {
        Map<String, AttributeValue> result = new HashMap<>();
        result.put("pk", AttributeValue.builder().s("GROUP#" + groupId).build());
        result.put("sk", AttributeValue.builder().s("SERIES#" + seriesId).build());
        result.put("itemType", AttributeValue.builder().s("SERIES_POINTER").build());
        result.put("gsi1pk", AttributeValue.builder().s("GROUP#" + groupId).build());
        result.put("startTimestamp", AttributeValue.builder().n(String.valueOf(startTimestamp)).build());
        result.put("endTimestamp", AttributeValue.builder().n(String.valueOf(endTimestamp)).build());
        result.put("groupId", AttributeValue.builder().s(groupId).build());
        result.put("seriesId", AttributeValue.builder().s(seriesId).build());
        result.put("seriesTitle", AttributeValue.builder().s(title).build());
        // Note: parts field is optional for test mocking
        return result;
    }

    private String createValidStartToken(String groupId, String eventId, long startTimestamp) {
        String json = String.format(
            "{\"gsi1pk\":\"GROUP#%s\",\"startTimestamp\":\"%d\",\"pk\":\"GROUP#%s\",\"sk\":\"HANGOUT#%s\"}",
            groupId, startTimestamp, groupId, eventId
        );
        return Base64.getEncoder().encodeToString(json.getBytes());
    }

    private String createValidEndTimestampToken(String groupId, String eventId, long endTimestamp) {
        String json = String.format(
            "{\"gsi1pk\":\"GROUP#%s\",\"endTimestamp\":\"%d\",\"pk\":\"GROUP#%s\",\"sk\":\"HANGOUT#%s\"}",
            groupId, endTimestamp, groupId, eventId
        );
        return Base64.getEncoder().encodeToString(json.getBytes());
    }
}
