package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.util.InviterKeyFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for hangout pointer operations in HangoutRepositoryImpl.
 *
 * Coverage:
 * - Batch pointer retrieval
 * - Handling of empty/null groups
 * - Partial results handling
 * - Unprocessed keys handling
 */
class HangoutRepositoryPointersTest extends HangoutRepositoryTestBase {

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
     * Helper method to create mock hangout pointer items for testing.
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
}
