package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.exception.RepositoryException;
import com.bbthechange.inviter.util.InviterKeyFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for basic CRUD operations in HangoutRepositoryImpl.
 *
 * Covers:
 * - save(Hangout) - update existing hangout
 * - findHangoutById(String) - retrieve single hangout
 * - deleteHangout(String) - delete entire hangout collection
 *
 * Total tests: 11
 */
class HangoutRepositoryBasicCrudTest extends HangoutRepositoryTestBase {

    @Nested
    class SaveHangout {

        @Test
        void save_WithValidHangout_SavesSuccessfully() {
            // Given
            Hangout hangout = createValidHangout();

            PutItemResponse response = PutItemResponse.builder().build();
            when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);

            // When
            Hangout result = repository.save(hangout);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getHangoutId()).isEqualTo(eventId);
            assertThat(result.getUpdatedAt()).isNotNull(); // Should be touched

            ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
            verify(dynamoDbClient).putItem(captor.capture());

            PutItemRequest request = captor.getValue();
            assertThat(request.tableName()).isEqualTo("InviterTable");
            assertThat(request.item()).containsKey("pk");
            assertThat(request.item()).containsKey("sk");
        }

        @Test
        void save_UpdatesTimestamp_TouchesHangout() {
            // Given
            Hangout hangout = createValidHangout();
            Instant beforeSave = Instant.now();

            PutItemResponse response = PutItemResponse.builder().build();
            when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);

            // When
            Hangout result = repository.save(hangout);

            // Then
            assertThat(result.getUpdatedAt()).isNotNull();
            assertThat(result.getUpdatedAt()).isAfterOrEqualTo(beforeSave);
        }

        @Test
        void save_WithDynamoDbException_ThrowsRepositoryException() {
            // Given
            Hangout hangout = createValidHangout();

            when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenThrow(DynamoDbException.builder().message("DynamoDB error").build());

            // When/Then
            assertThatThrownBy(() -> repository.save(hangout))
                .isInstanceOf(RepositoryException.class)
                .hasMessageContaining("Failed to save hangout")
                .hasCauseInstanceOf(DynamoDbException.class);
        }

        @Test
        void save_WithUpdatedTitle_PersistsChanges() {
            // Given
            Hangout hangout = createValidHangout();
            hangout.setTitle("Updated Title");

            PutItemResponse response = PutItemResponse.builder().build();
            when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);

            // When
            Hangout result = repository.save(hangout);

            // Then
            assertThat(result.getTitle()).isEqualTo("Updated Title");
            verify(dynamoDbClient).putItem(any(PutItemRequest.class));
        }
    }

    @Nested
    class FindHangoutById {

        @Test
        void findHangoutById_WithExistingHangout_ReturnsOptionalWithHangout() {
            // Given
            Hangout hangout = createValidHangout();

            Map<String, AttributeValue> itemMap = new HashMap<>();
            itemMap.put("pk", AttributeValue.builder().s(InviterKeyFactory.getEventPk(eventId)).build());
            itemMap.put("sk", AttributeValue.builder().s(InviterKeyFactory.getMetadataSk()).build());
            itemMap.put("hangoutId", AttributeValue.builder().s(eventId).build());
            itemMap.put("title", AttributeValue.builder().s("Test Hangout").build());
            itemMap.put("itemType", AttributeValue.builder().s("HANGOUT").build());

            GetItemResponse response = GetItemResponse.builder()
                .item(itemMap)
                .build();

            when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

            // When
            Optional<Hangout> result = repository.findHangoutById(eventId);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getHangoutId()).isEqualTo(eventId);

            ArgumentCaptor<GetItemRequest> captor = ArgumentCaptor.forClass(GetItemRequest.class);
            verify(dynamoDbClient).getItem(captor.capture());

            GetItemRequest request = captor.getValue();
            assertThat(request.tableName()).isEqualTo("InviterTable");
            assertThat(request.key()).containsKey("pk");
            assertThat(request.key()).containsKey("sk");
            assertThat(request.key().get("pk").s()).isEqualTo(InviterKeyFactory.getEventPk(eventId));
            assertThat(request.key().get("sk").s()).isEqualTo(InviterKeyFactory.getMetadataSk());
        }

        @Test
        void findHangoutById_WithNonExistentHangout_ReturnsEmptyOptional() {
            // Given
            GetItemResponse response = GetItemResponse.builder().build(); // No item
            when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

            // When
            Optional<Hangout> result = repository.findHangoutById(eventId);

            // Then
            assertThat(result).isEmpty();
            verify(dynamoDbClient).getItem(any(GetItemRequest.class));
        }

        @Test
        void findHangoutById_WithDynamoDbException_ThrowsRepositoryException() {
            // Given
            when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenThrow(DynamoDbException.builder().message("DynamoDB error").build());

            // When/Then
            assertThatThrownBy(() -> repository.findHangoutById(eventId))
                .isInstanceOf(RepositoryException.class)
                .hasMessageContaining("Failed to retrieve hangout")
                .hasCauseInstanceOf(DynamoDbException.class);
        }
    }

    @Nested
    class DeleteHangout {

        @Test
        void deleteHangout_WithExistingItems_QueriesAndDeletesAll() {
            // Given
            // Mock query response with multiple items in the hangout collection
            Map<String, AttributeValue> hangoutItem = createItemMap("METADATA");
            Map<String, AttributeValue> pollItem = createItemMap("POLL#poll1");
            Map<String, AttributeValue> carItem = createItemMap("CAR#driver1");

            QueryResponse queryResponse = QueryResponse.builder()
                .items(Arrays.asList(hangoutItem, pollItem, carItem))
                .build();

            when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);

            DeleteItemResponse deleteResponse = DeleteItemResponse.builder().build();
            when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class))).thenReturn(deleteResponse);

            // When
            repository.deleteHangout(eventId);

            // Then
            ArgumentCaptor<QueryRequest> queryCaptor = ArgumentCaptor.forClass(QueryRequest.class);
            verify(dynamoDbClient).query(queryCaptor.capture());

            QueryRequest queryRequest = queryCaptor.getValue();
            assertThat(queryRequest.tableName()).isEqualTo("InviterTable");
            assertThat(queryRequest.keyConditionExpression()).isEqualTo("pk = :pk");
            assertThat(queryRequest.expressionAttributeValues()).containsKey(":pk");

            // Verify all 3 items were deleted
            ArgumentCaptor<DeleteItemRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteItemRequest.class);
            verify(dynamoDbClient, times(3)).deleteItem(deleteCaptor.capture());

            List<DeleteItemRequest> deleteRequests = deleteCaptor.getAllValues();
            assertThat(deleteRequests).hasSize(3);
            assertThat(deleteRequests).allMatch(req -> req.tableName().equals("InviterTable"));
        }

        @Test
        void deleteHangout_WithEmptyCollection_HandlesGracefully() {
            // Given
            QueryResponse queryResponse = QueryResponse.builder()
                .items(Collections.emptyList())
                .build();

            when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);

            // When
            repository.deleteHangout(eventId);

            // Then
            verify(dynamoDbClient).query(any(QueryRequest.class));
            verify(dynamoDbClient, never()).deleteItem(any(DeleteItemRequest.class));
        }

        @Test
        void deleteHangout_WithQueryException_ThrowsRepositoryException() {
            // Given
            when(dynamoDbClient.query(any(QueryRequest.class)))
                .thenThrow(DynamoDbException.builder().message("Query failed").build());

            // When/Then
            assertThatThrownBy(() -> repository.deleteHangout(eventId))
                .isInstanceOf(RepositoryException.class)
                .hasMessageContaining("Failed to delete hangout")
                .hasCauseInstanceOf(DynamoDbException.class);
        }

        @Test
        void deleteHangout_WithDeleteException_ThrowsRepositoryException() {
            // Given
            Map<String, AttributeValue> hangoutItem = createItemMap("METADATA");
            QueryResponse queryResponse = QueryResponse.builder()
                .items(Collections.singletonList(hangoutItem))
                .build();

            when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);
            when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class)))
                .thenThrow(DynamoDbException.builder().message("Delete failed").build());

            // When/Then
            assertThatThrownBy(() -> repository.deleteHangout(eventId))
                .isInstanceOf(RepositoryException.class)
                .hasMessageContaining("Failed to delete hangout")
                .hasCauseInstanceOf(DynamoDbException.class);
        }
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    private Hangout createValidHangout() {
        Hangout hangout = new Hangout();
        hangout.setHangoutId(eventId);
        hangout.setTitle("Test Hangout");
        hangout.setDescription("Test Description");
        hangout.setPk(InviterKeyFactory.getEventPk(eventId));
        hangout.setSk(InviterKeyFactory.getMetadataSk());
        return hangout;
    }

    private Map<String, AttributeValue> createItemMap(String sk) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", AttributeValue.builder().s(InviterKeyFactory.getEventPk(eventId)).build());
        item.put("sk", AttributeValue.builder().s(sk).build());
        return item;
    }
}
