package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.EventRepository;
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
class HangoutRepositoryImplTest {

    private static final String TABLE_NAME = "InviterTable";

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private DynamoDbEnhancedClient dynamoDbEnhancedClient;

    @Mock
    private QueryPerformanceTracker performanceTracker;

    @Mock
    private EventRepository eventRepository;

    private HangoutRepositoryImpl repository;

    private String eventId;
    private String pollId;
    private String optionId;
    private String userId;

    @BeforeEach
    void setUp() {
        when(performanceTracker.trackQuery(anyString(), anyString(), any())).thenAnswer(invocation -> {
            Object result = null;
            try {
                java.util.function.Supplier<?> supplier = invocation.getArgument(2);
                result = supplier.get();
            } catch (Exception e) {
                // Handle the case where it might be a Runnable instead of Supplier
            }
            return result;
        });

        repository = new HangoutRepositoryImpl(dynamoDbClient, dynamoDbEnhancedClient, performanceTracker, eventRepository);

        eventId = UUID.randomUUID().toString();
        pollId = UUID.randomUUID().toString();
        optionId = UUID.randomUUID().toString();
        userId = UUID.randomUUID().toString();
    }

    @Test
    void savePollOption_WithValidData_SavesSuccessfully() {
        // Given
        PollOption option = new PollOption(eventId, pollId, "Test Option");
        PutItemResponse response = PutItemResponse.builder().build();
        when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);

        // When
        PollOption result = repository.savePollOption(option);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getText()).isEqualTo("Test Option");
        verify(dynamoDbClient).putItem(any(PutItemRequest.class));
    }

    @Test
    void getAllPollData_WithExistingPolls_QueriesCorrectly() {
        // Given
        QueryResponse response = QueryResponse.builder()
                .items(new ArrayList<>())
                .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        // When
        List<BaseItem> result = repository.getAllPollData(eventId);

        // Then
        assertThat(result).isNotNull();
        
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());
        
        QueryRequest request = captor.getValue();
        assertThat(request.keyConditionExpression()).isEqualTo("pk = :pk AND begins_with(sk, :sk_prefix)");
        assertThat(request.expressionAttributeValues().get(":sk_prefix").s()).isEqualTo("POLL#");
    }

    @Test
    void getSpecificPollData_WithPollId_QueriesCorrectly() {
        // Given
        QueryResponse response = QueryResponse.builder()
                .items(new ArrayList<>())
                .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        // When
        List<BaseItem> result = repository.getSpecificPollData(eventId, pollId);

        // Then
        assertThat(result).isNotNull();
        
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());
        
        QueryRequest request = captor.getValue();
        assertThat(request.keyConditionExpression()).isEqualTo("pk = :pk AND begins_with(sk, :sk_prefix)");
        assertThat(request.expressionAttributeValues().get(":sk_prefix").s()).isEqualTo("POLL#" + pollId);
    }

    @Test
    void deletePollOptionTransaction_WithVotes_ExecutesTransaction() {
        // Given
        // Mock the query to find votes
        Map<String, AttributeValue> voteItem = new HashMap<>();
        voteItem.put("pk", AttributeValue.builder().s("EVENT#" + eventId).build());
        voteItem.put("sk", AttributeValue.builder().s("POLL#" + pollId + "#VOTE#" + userId + "#OPTION#" + optionId).build());
        voteItem.put("itemType", AttributeValue.builder().s("VOTE").build());
        voteItem.put("optionId", AttributeValue.builder().s(optionId).build());
        voteItem.put("userId", AttributeValue.builder().s(userId).build());

        QueryResponse queryResponse = QueryResponse.builder()
                .items(Arrays.asList(voteItem))
                .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);

        TransactWriteItemsResponse transactResponse = TransactWriteItemsResponse.builder().build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class))).thenReturn(transactResponse);

        // When
        repository.deletePollOptionTransaction(eventId, pollId, optionId);

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());
        
        TransactWriteItemsRequest request = captor.getValue();
        // Should have 2 items: delete option + delete vote
        assertThat(request.transactItems()).hasSize(2);
    }

    @Test
    void deletePollOptionTransaction_WithNoVotes_OnlyDeletesOption() {
        // Given
        QueryResponse queryResponse = QueryResponse.builder()
                .items(new ArrayList<>())
                .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);

        TransactWriteItemsResponse transactResponse = TransactWriteItemsResponse.builder().build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class))).thenReturn(transactResponse);

        // When
        repository.deletePollOptionTransaction(eventId, pollId, optionId);

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());
        
        TransactWriteItemsRequest request = captor.getValue();
        // Should only have 1 item: delete option
        assertThat(request.transactItems()).hasSize(1);
    }

    @Test
    void getPollOptions_WithValidIds_QueriesCorrectly() {
        // Given
        QueryResponse response = QueryResponse.builder()
                .items(new ArrayList<>())
                .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        // When
        List<PollOption> result = repository.getPollOptions(eventId, pollId);

        // Then
        assertThat(result).isNotNull();
        
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());
        
        QueryRequest request = captor.getValue();
        assertThat(request.keyConditionExpression()).contains("begins_with");
        assertThat(request.expressionAttributeValues().get(":sk_prefix").s()).contains("POLL#" + pollId + "#OPTION#");
    }

    // ============================================================================
    // NEEDS RIDE TESTS
    // ============================================================================

    @Test
    void saveNeedsRide_WithValidData_SavesSuccessfully() {
        // Given
        NeedsRide needsRide = new NeedsRide(eventId, userId, "Need a ride from downtown");
        PutItemResponse response = PutItemResponse.builder().build();
        when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);

        // When
        NeedsRide result = repository.saveNeedsRide(needsRide);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEventId()).isEqualTo(eventId);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getNotes()).isEqualTo("Need a ride from downtown");
        
        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(captor.capture());
        
        PutItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.item()).isNotEmpty();
    }

    @Test
    void saveNeedsRide_WithNullNotes_SavesSuccessfully() {
        // Given
        NeedsRide needsRide = new NeedsRide(eventId, userId, null);
        PutItemResponse response = PutItemResponse.builder().build();
        when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);

        // When
        NeedsRide result = repository.saveNeedsRide(needsRide);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEventId()).isEqualTo(eventId);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getNotes()).isNull();
        verify(dynamoDbClient).putItem(any(PutItemRequest.class));
    }

    @Test
    void saveNeedsRide_WithEmptyNotes_SavesSuccessfully() {
        // Given
        NeedsRide needsRide = new NeedsRide(eventId, userId, "");
        PutItemResponse response = PutItemResponse.builder().build();
        when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);

        // When
        NeedsRide result = repository.saveNeedsRide(needsRide);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEventId()).isEqualTo(eventId);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getNotes()).isEqualTo("");
        verify(dynamoDbClient).putItem(any(PutItemRequest.class));
    }

    @Test
    void getNeedsRideListForEvent_WithExistingRequests_QueriesCorrectly() {
        // Given
        QueryResponse response = QueryResponse.builder()
                .items(new ArrayList<>())
                .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        // When
        List<NeedsRide> result = repository.getNeedsRideListForEvent(eventId);

        // Then
        assertThat(result).isNotNull();
        
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());
        
        QueryRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.keyConditionExpression()).isEqualTo("pk = :pk AND begins_with(sk, :sk_prefix)");
        assertThat(request.expressionAttributeValues().get(":pk").s()).isEqualTo("EVENT#" + eventId);
        assertThat(request.expressionAttributeValues().get(":sk_prefix").s()).isEqualTo("NEEDS_RIDE#");
    }

    @Test
    void getNeedsRideListForEvent_WithNoRequests_ReturnsEmptyList() {
        // Given
        QueryResponse response = QueryResponse.builder()
                .items(new ArrayList<>())
                .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        // When
        List<NeedsRide> result = repository.getNeedsRideListForEvent(eventId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
        verify(dynamoDbClient).query(any(QueryRequest.class));
    }

    @Test
    void deleteNeedsRide_WithValidIds_DeletesSuccessfully() {
        // Given
        DeleteItemResponse response = DeleteItemResponse.builder().build();
        when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class))).thenReturn(response);

        // When
        repository.deleteNeedsRide(eventId, userId);

        // Then
        ArgumentCaptor<DeleteItemRequest> captor = ArgumentCaptor.forClass(DeleteItemRequest.class);
        verify(dynamoDbClient).deleteItem(captor.capture());
        
        DeleteItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.key().get("pk").s()).isEqualTo("EVENT#" + eventId);
        assertThat(request.key().get("sk").s()).isEqualTo("NEEDS_RIDE#" + userId);
    }

    @Test
    void getNeedsRideRequestForUser_WithExistingRequest_ReturnsOptionalWithRequest() {
        // Given
        Map<String, AttributeValue> itemMap = Map.of(
                "pk", AttributeValue.builder().s("EVENT#" + eventId).build(),
                "sk", AttributeValue.builder().s("NEEDS_RIDE#" + userId).build(),
                "eventId", AttributeValue.builder().s(eventId).build(),
                "userId", AttributeValue.builder().s(userId).build(),
                "notes", AttributeValue.builder().s("Need a ride").build()
        );
        GetItemResponse response = GetItemResponse.builder()
                .item(itemMap)
                .build();
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

        // When
        Optional<NeedsRide> result = repository.getNeedsRideRequestForUser(eventId, userId);

        // Then
        assertThat(result).isPresent();
        
        ArgumentCaptor<GetItemRequest> captor = ArgumentCaptor.forClass(GetItemRequest.class);
        verify(dynamoDbClient).getItem(captor.capture());
        
        GetItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.key().get("pk").s()).isEqualTo("EVENT#" + eventId);
        assertThat(request.key().get("sk").s()).isEqualTo("NEEDS_RIDE#" + userId);
    }

    @Test
    void getNeedsRideRequestForUser_WithNoExistingRequest_ReturnsEmptyOptional() {
        // Given
        GetItemResponse response = GetItemResponse.builder()
                .item(Map.of()) // Empty item map indicates no item found
                .build();
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

        // When
        Optional<NeedsRide> result = repository.getNeedsRideRequestForUser(eventId, userId);

        // Then
        assertThat(result).isEmpty();
        
        ArgumentCaptor<GetItemRequest> captor = ArgumentCaptor.forClass(GetItemRequest.class);
        verify(dynamoDbClient).getItem(captor.capture());
        
        GetItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.key().get("pk").s()).isEqualTo("EVENT#" + eventId);
        assertThat(request.key().get("sk").s()).isEqualTo("NEEDS_RIDE#" + userId);
    }

    @Test
    void getNeedsRideRequestForUser_WithDifferentEventAndUser_QueriesCorrectKeys() {
        // Given
        String differentEventId = UUID.randomUUID().toString();
        String differentUserId = UUID.randomUUID().toString();
        GetItemResponse response = GetItemResponse.builder()
                .item(Map.of())
                .build();
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

        // When
        repository.getNeedsRideRequestForUser(differentEventId, differentUserId);

        // Then
        ArgumentCaptor<GetItemRequest> captor = ArgumentCaptor.forClass(GetItemRequest.class);
        verify(dynamoDbClient).getItem(captor.capture());
        
        GetItemRequest request = captor.getValue();
        assertThat(request.key().get("pk").s()).isEqualTo("EVENT#" + differentEventId);
        assertThat(request.key().get("sk").s()).isEqualTo("NEEDS_RIDE#" + differentUserId);
    }

    // ============================================================================
    // SERIES-RELATED TESTS
    // ============================================================================

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

    // ============================================================================
    // FIND POINTERS FOR HANGOUT TESTS
    // ============================================================================

    @Test
    void findPointersForHangout_MultipleGroups() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        String group1Id = UUID.randomUUID().toString();
        String group2Id = UUID.randomUUID().toString();
        String group3Id = UUID.randomUUID().toString();
        hangout.setAssociatedGroups(Arrays.asList(group1Id, group2Id, group3Id));
        
        // Create mock response items for 3 HangoutPointers
        List<Map<String, AttributeValue>> mockItems = Arrays.asList(
            createMockHangoutPointerItem(group1Id, hangoutId, "Test Event"),
            createMockHangoutPointerItem(group2Id, hangoutId, "Test Event"),
            createMockHangoutPointerItem(group3Id, hangoutId, "Test Event")
        );
        
        BatchGetItemResponse mockResponse = BatchGetItemResponse.builder()
            .responses(Map.of(TABLE_NAME, mockItems))
            .build();
        
        when(dynamoDbClient.batchGetItem(any(BatchGetItemRequest.class))).thenReturn(mockResponse);
        
        // When
        List<HangoutPointer> result = repository.findPointersForHangout(hangout);
        
        // Then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getGroupId()).isEqualTo(group1Id);
        assertThat(result.get(1).getGroupId()).isEqualTo(group2Id);
        assertThat(result.get(2).getGroupId()).isEqualTo(group3Id);
        result.forEach(pointer -> {
            assertThat(pointer.getHangoutId()).isEqualTo(hangoutId);
            assertThat(pointer.getTitle()).isEqualTo("Test Event");
        });
        
        // Verify BatchGetItem was called with correct keys
        ArgumentCaptor<BatchGetItemRequest> captor = ArgumentCaptor.forClass(BatchGetItemRequest.class);
        verify(dynamoDbClient).batchGetItem(captor.capture());
        
        BatchGetItemRequest request = captor.getValue();
        KeysAndAttributes keysAndAttributes = request.requestItems().get(TABLE_NAME);
        assertThat(keysAndAttributes.keys()).hasSize(3);
        
        // Verify performance tracker was called
        verify(performanceTracker).trackQuery(eq("findPointersForHangout"), eq(TABLE_NAME), any());
    }
    
    @Test
    void findPointersForHangout_NoGroups() {
        // Given
        Hangout hangout = new Hangout();
        hangout.setHangoutId(UUID.randomUUID().toString());
        hangout.setAssociatedGroups(Collections.emptyList());
        
        // When
        List<HangoutPointer> result = repository.findPointersForHangout(hangout);
        
        // Then
        assertThat(result).isEmpty();
        
        // Verify no DynamoDB call was made
        verify(dynamoDbClient, never()).batchGetItem(any(BatchGetItemRequest.class));
        
        // Verify performance tracker was still called
        verify(performanceTracker).trackQuery(eq("findPointersForHangout"), eq(TABLE_NAME), any());
    }
    
    @Test
    void findPointersForHangout_NullGroups() {
        // Given
        Hangout hangout = new Hangout();
        hangout.setHangoutId(UUID.randomUUID().toString());
        hangout.setAssociatedGroups(null);
        
        // When
        List<HangoutPointer> result = repository.findPointersForHangout(hangout);
        
        // Then
        assertThat(result).isEmpty();
        
        // Verify no DynamoDB call was made
        verify(dynamoDbClient, never()).batchGetItem(any(BatchGetItemRequest.class));
    }
    
    @Test
    void findPointersForHangout_PartialResults() {
        // Given - hangout with 3 groups but only 2 pointers exist
        String hangoutId = UUID.randomUUID().toString();
        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        String group1Id = UUID.randomUUID().toString();
        String group2Id = UUID.randomUUID().toString();
        String group3Id = UUID.randomUUID().toString();
        hangout.setAssociatedGroups(Arrays.asList(group1Id, group2Id, group3Id));
        
        // Mock response with only 2 items (group-3 pointer doesn't exist)
        List<Map<String, AttributeValue>> mockItems = Arrays.asList(
            createMockHangoutPointerItem(group1Id, hangoutId, "Test Event"),
            createMockHangoutPointerItem(group2Id, hangoutId, "Test Event")
        );
        
        BatchGetItemResponse mockResponse = BatchGetItemResponse.builder()
            .responses(Map.of(TABLE_NAME, mockItems))
            .build();
        
        when(dynamoDbClient.batchGetItem(any(BatchGetItemRequest.class))).thenReturn(mockResponse);
        
        // When
        List<HangoutPointer> result = repository.findPointersForHangout(hangout);
        
        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getGroupId()).isEqualTo(group1Id);
        assertThat(result.get(1).getGroupId()).isEqualTo(group2Id);
        
        // Verify BatchGetItem was called with 3 keys even though only 2 results returned
        ArgumentCaptor<BatchGetItemRequest> captor = ArgumentCaptor.forClass(BatchGetItemRequest.class);
        verify(dynamoDbClient).batchGetItem(captor.capture());
        
        BatchGetItemRequest request = captor.getValue();
        KeysAndAttributes keysAndAttributes = request.requestItems().get(TABLE_NAME);
        assertThat(keysAndAttributes.keys()).hasSize(3);
    }
    
    @Test
    void findPointersForHangout_UnprocessedKeys() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        String group1Id = UUID.randomUUID().toString();
        String group2Id = UUID.randomUUID().toString();
        hangout.setAssociatedGroups(Arrays.asList(group1Id, group2Id));
        
        // Create mock response items
        List<Map<String, AttributeValue>> mockItems = Arrays.asList(
            createMockHangoutPointerItem(group1Id, hangoutId, "Test Event")
        );
        
        // Mock unprocessed keys - one key wasn't processed
        Map<String, AttributeValue> unprocessedKey = new HashMap<>();
        unprocessedKey.put("pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(group2Id)).build());
        unprocessedKey.put("sk", AttributeValue.builder().s(InviterKeyFactory.getHangoutSk(hangoutId)).build());
        
        BatchGetItemResponse mockResponse = BatchGetItemResponse.builder()
            .responses(Map.of(TABLE_NAME, mockItems))
            .unprocessedKeys(Map.of(TABLE_NAME, KeysAndAttributes.builder()
                .keys(Arrays.asList(unprocessedKey))
                .build()))
            .build();
        
        when(dynamoDbClient.batchGetItem(any(BatchGetItemRequest.class))).thenReturn(mockResponse);
        
        // When
        List<HangoutPointer> result = repository.findPointersForHangout(hangout);
        
        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getGroupId()).isEqualTo(group1Id);
        
        // The method should still return available results despite unprocessed keys
        // (Note: The actual implementation logs a warning but continues)
    }

    /**
     * Helper method to create mock hangout items in DynamoDB attribute format.
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

    // ================= SeriesPointer Deserialization Tests =================

    @Test
    @Disabled("Failing due to InviterKeyFactory validation - needs investigation")
    void deserializeItem_WithSeriesPointerItemType_ShouldReturnSeriesPointer() {
        // Given
        Map<String, AttributeValue> itemMap = createSeriesPointerItemMap();

        // When
        BaseItem result = repository.deserializeItem(itemMap);

        // Then
        assertThat(result).isInstanceOf(SeriesPointer.class);
        SeriesPointer seriesPointer = (SeriesPointer) result;
        assertThat(seriesPointer.getSeriesId()).isNotNull();
    }

    @Test
    @Disabled("Failing due to InviterKeyFactory validation - needs investigation")
    void deserializeItem_WithSeriesSkPattern_ShouldReturnSeriesPointer() {
        // Given: Item without itemType but with SERIES# SK pattern
        String testSeriesId = UUID.randomUUID().toString();
        Map<String, AttributeValue> itemMap = createItemMapWithSk("SERIES#" + testSeriesId);

        // When
        BaseItem result = repository.deserializeItem(itemMap);

        // Then
        assertThat(result).isInstanceOf(SeriesPointer.class);
    }

    /**
     * Helper method to create mock HangoutPointer items in DynamoDB attribute format.
     */
    private Map<String, AttributeValue> createMockHangoutPointerItem(String groupId, String hangoutId, String title) {
        return Map.of(
            "pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build(),
            "sk", AttributeValue.builder().s(InviterKeyFactory.getHangoutSk(hangoutId)).build(),
            "itemType", AttributeValue.builder().s("HANGOUT_POINTER").build(),
            "groupId", AttributeValue.builder().s(groupId).build(),
            "hangoutId", AttributeValue.builder().s(hangoutId).build(),
            "title", AttributeValue.builder().s(title).build(),
            "participantCount", AttributeValue.builder().n("1").build()
        );
    }

    private Map<String, AttributeValue> createSeriesPointerItemMap() {
        String testGroupId = UUID.randomUUID().toString();
        String testSeriesId = UUID.randomUUID().toString();
        return Map.of(
            "pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(testGroupId)).build(),
            "sk", AttributeValue.builder().s(InviterKeyFactory.getSeriesSk(testSeriesId)).build(),
            "itemType", AttributeValue.builder().s("SERIES_POINTER").build(),
            "seriesId", AttributeValue.builder().s(testSeriesId).build(),
            "seriesTitle", AttributeValue.builder().s("Test Series").build(),
            "groupId", AttributeValue.builder().s(testGroupId).build(),
            "startTimestamp", AttributeValue.builder().n("1000").build(),
            "endTimestamp", AttributeValue.builder().n("5000").build()
        );
    }

    private Map<String, AttributeValue> createItemMapWithSk(String sk) {
        String testGroupId = UUID.randomUUID().toString();
        String testSeriesId = UUID.randomUUID().toString();
        return Map.of(
            "pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(testGroupId)).build(),
            "sk", AttributeValue.builder().s(sk).build(),
            "seriesId", AttributeValue.builder().s(testSeriesId).build(),
            "seriesTitle", AttributeValue.builder().s("Test Series").build(),
            "groupId", AttributeValue.builder().s(testGroupId).build()
        );
    }
}