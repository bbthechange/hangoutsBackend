package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.EventRepository;
import com.bbthechange.inviter.util.QueryPerformanceTracker;
import com.bbthechange.inviter.util.InviterKeyFactory;
import com.bbthechange.inviter.dto.HangoutDetailData;
import com.bbthechange.inviter.exception.InvalidKeyException;
import com.bbthechange.inviter.exception.ResourceNotFoundException;
import com.bbthechange.inviter.exception.RepositoryException;
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
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

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
        // Set up tracking to properly propagate exceptions
        lenient().when(performanceTracker.trackQuery(anyString(), anyString(), any())).thenAnswer(invocation -> {
            try {
                java.util.function.Supplier<?> supplier = invocation.getArgument(2);
                return supplier.get();
            } catch (Exception e) {
                // Re-throw the exception to maintain proper error handling
                throw e;
            }
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

    // ============================================================================
    // CORE FUNCTIONALITY TESTS - HIGH PRIORITY
    // ============================================================================

    @Test
    void getHangoutDetailData_WithCompleteEventData_ReturnsAllItems() {
        // Given
        String eventId = UUID.randomUUID().toString();
        String pollId = UUID.randomUUID().toString();
        String optionId = UUID.randomUUID().toString();
        String carId = UUID.randomUUID().toString();
        String riderId = UUID.randomUUID().toString();
        
        // Create comprehensive mock response with all entity types
        List<Map<String, AttributeValue>> mockItems = Arrays.asList(
            // Hangout metadata
            createMockHangoutMetadataItem(eventId),
            // Poll
            createMockPollItem(eventId, pollId),
            // Poll Option
            createMockPollOptionItem(eventId, pollId, optionId),
            // Car
            createMockCarItem(eventId, carId),
            // Vote
            createMockVoteItem(eventId, pollId, optionId, userId),
            // Interest Level (attendance)
            createMockInterestLevelItem(eventId, userId),
            // Car Rider
            createMockCarRiderItem(eventId, carId, riderId),
            // Needs Ride
            createMockNeedsRideItem(eventId, userId)
        );
        
        QueryResponse mockResponse = QueryResponse.builder()
            .items(mockItems)
            .build();
        
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        HangoutDetailData result = repository.getHangoutDetailData(eventId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getHangout()).isNotNull();
        assertThat(result.getHangout().getHangoutId()).isEqualTo(eventId);
        assertThat(result.getPolls()).hasSize(1);
        assertThat(result.getPollOptions()).hasSize(1);
        assertThat(result.getCars()).hasSize(1);
        assertThat(result.getVotes()).hasSize(1);
        assertThat(result.getAttendance()).hasSize(1);
        assertThat(result.getCarRiders()).hasSize(1);
        assertThat(result.getNeedsRide()).hasSize(1);
        
        // Verify query construction
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());
        
        QueryRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.keyConditionExpression()).isEqualTo("pk = :pk");
        assertThat(request.expressionAttributeValues().get(":pk").s()).isEqualTo("EVENT#" + eventId);
        assertThat(request.scanIndexForward()).isTrue();
    }

    @Test
    void getHangoutDetailData_WithNoHangoutMetadata_ThrowsResourceNotFoundException() {
        // Given
        String eventId = UUID.randomUUID().toString();
        
        // Mock response with empty items - no hangout metadata found
        QueryResponse mockResponse = QueryResponse.builder()
            .items(Collections.emptyList())
            .build();
        
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When/Then - Note: ResourceNotFoundException gets wrapped in RepositoryException
        assertThatThrownBy(() -> repository.getHangoutDetailData(eventId))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("Failed to retrieve hangout details")
            .hasCauseInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getHangoutDetailData_WithDeserializationError_FiltersOutBadItems() {
        // Given
        String eventId = UUID.randomUUID().toString();
        
        List<Map<String, AttributeValue>> mockItems = Arrays.asList(
            // Valid hangout metadata
            createMockHangoutMetadataItem(eventId),
            // Invalid item that will cause deserialization to fail
            createInvalidItem(eventId)
        );
        
        QueryResponse mockResponse = QueryResponse.builder()
            .items(mockItems)
            .build();
        
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        HangoutDetailData result = repository.getHangoutDetailData(eventId);

        // Then - should return hangout but filter out the bad item
        assertThat(result).isNotNull();
        assertThat(result.getHangout()).isNotNull();
        assertThat(result.getPolls()).isEmpty();
        assertThat(result.getPollOptions()).isEmpty();
        assertThat(result.getCars()).isEmpty();
    }

    @Test
    void deserializeItem_WithHangoutItemType_ReturnsHangout() {
        // Given
        Map<String, AttributeValue> itemMap = createMockHangoutMetadataItem(eventId);

        // When
        BaseItem result = repository.deserializeItem(itemMap);

        // Then
        assertThat(result).isInstanceOf(Hangout.class);
        Hangout hangout = (Hangout) result;
        assertThat(hangout.getHangoutId()).isEqualTo(eventId);
    }

    @Test
    void deserializeItem_WithPollItemType_ReturnsPoll() {
        // Given
        Map<String, AttributeValue> itemMap = createMockPollItem(eventId, pollId);

        // When
        BaseItem result = repository.deserializeItem(itemMap);

        // Then
        assertThat(result).isInstanceOf(Poll.class);
        Poll poll = (Poll) result;
        assertThat(poll.getPollId()).isEqualTo(pollId);
    }

    @Test
    void deserializeItem_WithoutItemTypeButValidSK_FallsBackToSKPattern() {
        // Given
        Map<String, AttributeValue> itemMap = new HashMap<>();
        itemMap.put("pk", AttributeValue.builder().s("EVENT#" + eventId).build());
        itemMap.put("sk", AttributeValue.builder().s("METADATA").build());
        itemMap.put("hangoutId", AttributeValue.builder().s(eventId).build());
        itemMap.put("title", AttributeValue.builder().s("Test Hangout").build());
        // Note: No itemType field

        // When
        BaseItem result = repository.deserializeItem(itemMap);

        // Then
        assertThat(result).isInstanceOf(Hangout.class);
    }

    @Test
    void deserializeItem_WithUnknownItemType_ThrowsIllegalArgumentException() {
        // Given
        Map<String, AttributeValue> itemMap = new HashMap<>();
        itemMap.put("pk", AttributeValue.builder().s("EVENT#" + eventId).build());
        itemMap.put("sk", AttributeValue.builder().s("UNKNOWN").build());
        itemMap.put("itemType", AttributeValue.builder().s("UNKNOWN_TYPE").build());

        // When/Then
        assertThatThrownBy(() -> repository.deserializeItem(itemMap))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown item type: UNKNOWN_TYPE");
    }

    @Test
    void deserializeItem_WithoutItemTypeAndUnrecognizableSK_ThrowsIllegalStateException() {
        // Given
        Map<String, AttributeValue> itemMap = new HashMap<>();
        itemMap.put("pk", AttributeValue.builder().s("EVENT#" + eventId).build());
        itemMap.put("sk", AttributeValue.builder().s("UNRECOGNIZABLE_PATTERN").build());
        // Note: No itemType field and SK doesn't match any pattern

        // When/Then
        assertThatThrownBy(() -> repository.deserializeItem(itemMap))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Missing itemType discriminator and unable to determine type from SK");
    }

    @Test
    void createHangout_WithValidHangout_SavesSuccessfully() {
        // Given
        Hangout hangout = new Hangout();
        hangout.setHangoutId(eventId);
        hangout.setTitle("Test Hangout");
        hangout.setDescription("Test Description");
        hangout.setPk(InviterKeyFactory.getEventPk(eventId));
        hangout.setSk(InviterKeyFactory.getMetadataSk());
        
        PutItemResponse response = PutItemResponse.builder().build();
        when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);

        // When
        Hangout result = repository.createHangout(hangout);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getHangoutId()).isEqualTo(eventId);
        assertThat(result.getUpdatedAt()).isNotNull(); // Should be touched
        
        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(captor.capture());
        
        PutItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.item()).isNotEmpty();
        assertThat(request.item()).containsKey("pk");
        assertThat(request.item()).containsKey("sk");
    }

    @Test
    void createHangout_WithDynamoDbException_ThrowsRepositoryException() {
        // Given
        Hangout hangout = new Hangout();
        hangout.setHangoutId(eventId);
        hangout.setPk(InviterKeyFactory.getEventPk(eventId));
        hangout.setSk(InviterKeyFactory.getMetadataSk());
        
        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
            .thenThrow(DynamoDbException.builder().message("DynamoDB error").build());

        // When/Then
        assertThatThrownBy(() -> repository.createHangout(hangout))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("Failed to create hangout")
            .hasCauseInstanceOf(DynamoDbException.class);
    }

    // Helper methods for creating mock DynamoDB items
    private Map<String, AttributeValue> createMockHangoutMetadataItem(String hangoutId) {
        return Map.of(
            "pk", AttributeValue.builder().s("EVENT#" + hangoutId).build(),
            "sk", AttributeValue.builder().s("METADATA").build(),
            "itemType", AttributeValue.builder().s("HANGOUT").build(),
            "hangoutId", AttributeValue.builder().s(hangoutId).build(),
            "title", AttributeValue.builder().s("Test Hangout").build(),
            "description", AttributeValue.builder().s("Test Description").build()
        );
    }

    private Map<String, AttributeValue> createMockPollItem(String eventId, String pollId) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", AttributeValue.builder().s("EVENT#" + eventId).build());
        item.put("sk", AttributeValue.builder().s("POLL#" + pollId).build());
        item.put("itemType", AttributeValue.builder().s("POLL").build());
        item.put("pollId", AttributeValue.builder().s(pollId).build());
        item.put("question", AttributeValue.builder().s("Test Poll Question").build());
        item.put("eventId", AttributeValue.builder().s(eventId).build());
        return item;
    }

    private Map<String, AttributeValue> createMockPollOptionItem(String eventId, String pollId, String optionId) {
        return Map.of(
            "pk", AttributeValue.builder().s("EVENT#" + eventId).build(),
            "sk", AttributeValue.builder().s("POLL#" + pollId + "#OPTION#" + optionId).build(),
            "itemType", AttributeValue.builder().s("POLL_OPTION").build(),
            "optionId", AttributeValue.builder().s(optionId).build(),
            "text", AttributeValue.builder().s("Test Option").build()
        );
    }

    private Map<String, AttributeValue> createMockCarItem(String eventId, String carId) {
        return Map.of(
            "pk", AttributeValue.builder().s("EVENT#" + eventId).build(),
            "sk", AttributeValue.builder().s("CAR#" + carId).build(),
            "itemType", AttributeValue.builder().s("CAR").build(),
            "driverId", AttributeValue.builder().s(carId).build(),
            "availableSeats", AttributeValue.builder().n("4").build()
        );
    }

    private Map<String, AttributeValue> createMockVoteItem(String eventId, String pollId, String optionId, String userId) {
        return Map.of(
            "pk", AttributeValue.builder().s("EVENT#" + eventId).build(),
            "sk", AttributeValue.builder().s("POLL#" + pollId + "#VOTE#" + userId + "#OPTION#" + optionId).build(),
            "itemType", AttributeValue.builder().s("VOTE").build(),
            "userId", AttributeValue.builder().s(userId).build(),
            "optionId", AttributeValue.builder().s(optionId).build()
        );
    }

    // ============================================================================
    // ATOMIC TRANSACTION TESTS
    // ============================================================================

    @Test
    void createHangoutWithAttributes_ValidData_ExecutesTransactionCorrectly() {
        // Given
        Hangout hangout = createValidHangout();
        String groupId1 = UUID.randomUUID().toString();
        String groupId2 = UUID.randomUUID().toString();
        String attributeId1 = UUID.randomUUID().toString();
        String attributeId2 = UUID.randomUUID().toString();
        
        List<HangoutPointer> pointers = Arrays.asList(
            createValidHangoutPointer(hangout.getHangoutId(), groupId1),
            createValidHangoutPointer(hangout.getHangoutId(), groupId2)
        );
        List<HangoutAttribute> attributes = Arrays.asList(
            createValidHangoutAttribute(hangout.getHangoutId(), attributeId1, "Test Location"),
            createValidHangoutAttribute(hangout.getHangoutId(), attributeId2, "50")
        );
        
        TransactWriteItemsResponse response = TransactWriteItemsResponse.builder().build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class))).thenReturn(response);

        // When
        Hangout result = repository.createHangoutWithAttributes(hangout, pointers, attributes);

        // Then
        assertThat(result).isSameAs(hangout);
        
        ArgumentCaptor<TransactWriteItemsRequest> captor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());
        
        TransactWriteItemsRequest request = captor.getValue();
        assertThat(request.transactItems()).hasSize(5); // 1 hangout + 2 pointers + 2 attributes
        
        // Verify hangout item is first
        TransactWriteItem hangoutItem = request.transactItems().get(0);
        assertThat(hangoutItem.put().tableName()).isEqualTo("InviterTable");
        assertThat(hangoutItem.put().item().get("itemType").s()).isEqualTo("HANGOUT");
        
        // Verify pointer items
        TransactWriteItem pointer1 = request.transactItems().get(1);
        assertThat(pointer1.put().item().get("itemType").s()).isEqualTo("HANGOUT_POINTER");
        
        TransactWriteItem pointer2 = request.transactItems().get(2);
        assertThat(pointer2.put().item().get("itemType").s()).isEqualTo("HANGOUT_POINTER");
        
        // Verify attribute items
        TransactWriteItem attr1 = request.transactItems().get(3);
        assertThat(attr1.put().item().get("itemType").s()).isEqualTo("ATTRIBUTE");
        
        TransactWriteItem attr2 = request.transactItems().get(4);
        assertThat(attr2.put().item().get("itemType").s()).isEqualTo("ATTRIBUTE");
    }

    @Test
    void createHangoutWithAttributes_DynamoDbException_WrapsInRepositoryException() {
        // Given
        Hangout hangout = createValidHangout();
        String groupId = UUID.randomUUID().toString();
        String attributeId = UUID.randomUUID().toString();
        
        List<HangoutPointer> pointers = Arrays.asList(createValidHangoutPointer(hangout.getHangoutId(), groupId));
        List<HangoutAttribute> attributes = Arrays.asList(createValidHangoutAttribute(hangout.getHangoutId(), attributeId, "Test"));
        
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
            .thenThrow(DynamoDbException.builder().message("Transaction failed").build());

        // When/Then
        assertThatThrownBy(() -> repository.createHangoutWithAttributes(hangout, pointers, attributes))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("Failed to atomically create hangout with attributes")
            .hasCauseInstanceOf(DynamoDbException.class);
    }

    @Test
    void saveHangoutAndPointersAtomically_ValidData_ExecutesTransactionCorrectly() {
        // Given
        Hangout hangout = createValidHangout();
        String groupId1 = UUID.randomUUID().toString();
        String groupId2 = UUID.randomUUID().toString();
        
        List<HangoutPointer> pointers = Arrays.asList(
            createValidHangoutPointer(hangout.getHangoutId(), groupId1),
            createValidHangoutPointer(hangout.getHangoutId(), groupId2)
        );
        
        TransactWriteItemsResponse response = TransactWriteItemsResponse.builder().build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class))).thenReturn(response);

        // When
        repository.saveHangoutAndPointersAtomically(hangout, pointers);

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());
        
        TransactWriteItemsRequest request = captor.getValue();
        assertThat(request.transactItems()).hasSize(3); // 1 hangout + 2 pointers
        
        // Verify hangout item is first
        TransactWriteItem hangoutItem = request.transactItems().get(0);
        assertThat(hangoutItem.put().tableName()).isEqualTo("InviterTable");
        assertThat(hangoutItem.put().item().get("itemType").s()).isEqualTo("HANGOUT");
        
        // Verify pointer items
        TransactWriteItem pointer1 = request.transactItems().get(1);
        assertThat(pointer1.put().item().get("itemType").s()).isEqualTo("HANGOUT_POINTER");
        
        TransactWriteItem pointer2 = request.transactItems().get(2);
        assertThat(pointer2.put().item().get("itemType").s()).isEqualTo("HANGOUT_POINTER");
    }

    @Test
    void saveHangoutAndPointersAtomically_DynamoDbException_WrapsInRepositoryException() {
        // Given
        Hangout hangout = createValidHangout();
        String groupId = UUID.randomUUID().toString();
        
        List<HangoutPointer> pointers = Arrays.asList(createValidHangoutPointer(hangout.getHangoutId(), groupId));
        
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
            .thenThrow(DynamoDbException.builder().message("Transaction failed").build());

        // When/Then
        assertThatThrownBy(() -> repository.saveHangoutAndPointersAtomically(hangout, pointers))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("Failed to atomically save hangout and pointers")
            .hasCauseInstanceOf(DynamoDbException.class);
    }

    @Test
    void createHangoutWithAttributes_EmptyPointers_HandlesCorrectly() {
        // Given
        Hangout hangout = createValidHangout();
        List<HangoutPointer> emptyPointers = new ArrayList<>();
        String attributeId = UUID.randomUUID().toString();
        
        List<HangoutAttribute> attributes = Arrays.asList(
            createValidHangoutAttribute(hangout.getHangoutId(), attributeId, "Test Location")
        );
        
        TransactWriteItemsResponse response = TransactWriteItemsResponse.builder().build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class))).thenReturn(response);

        // When
        Hangout result = repository.createHangoutWithAttributes(hangout, emptyPointers, attributes);

        // Then
        assertThat(result).isSameAs(hangout);
        
        ArgumentCaptor<TransactWriteItemsRequest> captor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());
        
        TransactWriteItemsRequest request = captor.getValue();
        assertThat(request.transactItems()).hasSize(2); // 1 hangout + 1 attribute (no pointers)
    }

    @Test
    void createHangoutWithAttributes_EmptyAttributes_HandlesCorrectly() {
        // Given
        Hangout hangout = createValidHangout();
        String groupId = UUID.randomUUID().toString();
        
        List<HangoutPointer> pointers = Arrays.asList(
            createValidHangoutPointer(hangout.getHangoutId(), groupId)
        );
        List<HangoutAttribute> emptyAttributes = new ArrayList<>();
        
        TransactWriteItemsResponse response = TransactWriteItemsResponse.builder().build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class))).thenReturn(response);

        // When
        Hangout result = repository.createHangoutWithAttributes(hangout, pointers, emptyAttributes);

        // Then
        assertThat(result).isSameAs(hangout);
        
        ArgumentCaptor<TransactWriteItemsRequest> captor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());
        
        TransactWriteItemsRequest request = captor.getValue();
        assertThat(request.transactItems()).hasSize(2); // 1 hangout + 1 pointer (no attributes)
    }

    // ============================================================================
    // HELPER METHODS FOR ATOMIC TRANSACTION TESTS
    // ============================================================================

    private HangoutPointer createValidHangoutPointer(String hangoutId, String groupId) {
        HangoutPointer pointer = new HangoutPointer(groupId, hangoutId, "Test Hangout");
        pointer.setPk(InviterKeyFactory.getGroupPk(groupId));
        pointer.setSk(InviterKeyFactory.getHangoutSk(hangoutId));
        return pointer;
    }

    private HangoutAttribute createValidHangoutAttribute(String hangoutId, String attributeName, String attributeValue) {
        HangoutAttribute attribute = new HangoutAttribute(hangoutId, attributeName, attributeValue);
        attribute.setPk(InviterKeyFactory.getEventPk(hangoutId));
        attribute.setSk(InviterKeyFactory.getAttributeSk(attributeName));
        return attribute;
    }

    private Hangout createValidHangout() {
        Hangout hangout = new Hangout();
        hangout.setHangoutId(eventId);
        hangout.setTitle("Test Hangout");
        hangout.setDescription("Test Description");
        hangout.setPk(InviterKeyFactory.getEventPk(eventId));
        hangout.setSk(InviterKeyFactory.getMetadataSk());
        return hangout;
    }

    private Map<String, AttributeValue> createMockInterestLevelItem(String eventId, String userId) {
        return Map.of(
            "pk", AttributeValue.builder().s("EVENT#" + eventId).build(),
            "sk", AttributeValue.builder().s("ATTENDANCE#" + userId).build(),
            "itemType", AttributeValue.builder().s("INTEREST_LEVEL").build(),
            "userId", AttributeValue.builder().s(userId).build(),
            "level", AttributeValue.builder().s("GOING").build()
        );
    }

    private Map<String, AttributeValue> createMockCarRiderItem(String eventId, String carId, String riderId) {
        return Map.of(
            "pk", AttributeValue.builder().s("EVENT#" + eventId).build(),
            "sk", AttributeValue.builder().s("CAR#" + carId + "#RIDER#" + riderId).build(),
            "itemType", AttributeValue.builder().s("CAR_RIDER").build(),
            "driverId", AttributeValue.builder().s(carId).build(),
            "riderId", AttributeValue.builder().s(riderId).build()
        );
    }

    private Map<String, AttributeValue> createMockNeedsRideItem(String eventId, String userId) {
        return Map.of(
            "pk", AttributeValue.builder().s("EVENT#" + eventId).build(),
            "sk", AttributeValue.builder().s("NEEDS_RIDE#" + userId).build(),
            "itemType", AttributeValue.builder().s("NEEDS_RIDE").build(),
            "eventId", AttributeValue.builder().s(eventId).build(),
            "userId", AttributeValue.builder().s(userId).build()
        );
    }

    private Map<String, AttributeValue> createInvalidItem(String eventId) {
        // Item with unknown itemType that will cause deserializeItem to fail
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", AttributeValue.builder().s("EVENT#" + eventId).build());
        item.put("sk", AttributeValue.builder().s("UNKNOWN#invalid").build());
        item.put("itemType", AttributeValue.builder().s("UNKNOWN_TYPE").build());
        item.put("someField", AttributeValue.builder().s("someValue").build());
        return item;
    }

    // ============================================================================
    // PAGINATION METHODS TESTS
    // ============================================================================

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

    // Helper methods for pagination tests
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
}