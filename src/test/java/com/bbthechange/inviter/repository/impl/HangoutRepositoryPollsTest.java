package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.model.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for poll-related operations in HangoutRepositoryImpl.
 *
 * Coverage:
 * - Poll option CRUD operations
 * - Poll data queries (all polls, specific poll)
 * - Poll option deletion with cascade vote cleanup
 */
class HangoutRepositoryPollsTest extends HangoutRepositoryTestBase {

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
}
