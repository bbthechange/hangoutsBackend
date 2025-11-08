package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.exception.RepositoryException;
import com.bbthechange.inviter.util.InviterKeyFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for hangout creation methods in HangoutRepositoryImpl.
 *
 * Covers:
 * - createHangout() - simple hangout creation
 * - createHangoutWithAttributes() - atomic creation with pointers, attributes, polls, and options
 * - saveHangoutAndPointersAtomically() - atomic save of hangout with pointers
 * - Error handling and transaction validation
 *
 * Total tests: 10
 */
class HangoutRepositoryCreationTest extends HangoutRepositoryTestBase {

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
        Hangout result = repository.createHangoutWithAttributes(hangout, pointers, attributes, Collections.emptyList(), Collections.emptyList());

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
        assertThatThrownBy(() -> repository.createHangoutWithAttributes(hangout, pointers, attributes, Collections.emptyList(), Collections.emptyList()))
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
        Hangout result = repository.createHangoutWithAttributes(hangout, emptyPointers, attributes, Collections.emptyList(), Collections.emptyList());

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
        Hangout result = repository.createHangoutWithAttributes(hangout, pointers, emptyAttributes, Collections.emptyList(), Collections.emptyList());

        // Then
        assertThat(result).isSameAs(hangout);

        ArgumentCaptor<TransactWriteItemsRequest> captor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());

        TransactWriteItemsRequest request = captor.getValue();
        assertThat(request.transactItems()).hasSize(2); // 1 hangout + 1 pointer (no attributes)
    }

    @Test
    void createHangoutWithAttributes_IncludingPolls_ExecutesAtomicTransaction() {
        // Given
        Hangout hangout = createValidHangout();
        String groupId = UUID.randomUUID().toString();

        List<HangoutPointer> pointers = Arrays.asList(
            createValidHangoutPointer(hangout.getHangoutId(), groupId)
        );

        // Create one poll with no options
        Poll poll = new Poll(hangout.getHangoutId(), "What time?", null, false);
        List<Poll> polls = Arrays.asList(poll);
        List<PollOption> pollOptions = Collections.emptyList();

        TransactWriteItemsResponse response = TransactWriteItemsResponse.builder().build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class))).thenReturn(response);

        // When
        Hangout result = repository.createHangoutWithAttributes(hangout, pointers, Collections.emptyList(), polls, pollOptions);

        // Then
        assertThat(result).isSameAs(hangout);

        ArgumentCaptor<TransactWriteItemsRequest> captor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());

        TransactWriteItemsRequest request = captor.getValue();
        assertThat(request.transactItems()).hasSize(3); // 1 hangout + 1 pointer + 1 poll

        // Verify poll item is included
        boolean hasPollItem = request.transactItems().stream()
            .anyMatch(item -> item.put().item().get("itemType").s().equals("POLL"));
        assertThat(hasPollItem).isTrue();
    }

    @Test
    void createHangoutWithAttributes_WithPollsAndOptions_CorrectItemCount() {
        // Given
        Hangout hangout = createValidHangout();
        String groupId = UUID.randomUUID().toString();
        String attributeId = UUID.randomUUID().toString();

        List<HangoutPointer> pointers = Arrays.asList(
            createValidHangoutPointer(hangout.getHangoutId(), groupId)
        );
        List<HangoutAttribute> attributes = Arrays.asList(
            createValidHangoutAttribute(hangout.getHangoutId(), attributeId, "Test Value")
        );

        // Create poll with 3 options
        Poll poll = new Poll(hangout.getHangoutId(), "Food choice?", "Pick one", false);
        List<Poll> polls = Arrays.asList(poll);
        List<PollOption> pollOptions = Arrays.asList(
            new PollOption(hangout.getHangoutId(), poll.getPollId(), "Pizza"),
            new PollOption(hangout.getHangoutId(), poll.getPollId(), "Tacos"),
            new PollOption(hangout.getHangoutId(), poll.getPollId(), "Sushi")
        );

        TransactWriteItemsResponse response = TransactWriteItemsResponse.builder().build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class))).thenReturn(response);

        // When
        Hangout result = repository.createHangoutWithAttributes(hangout, pointers, attributes, polls, pollOptions);

        // Then
        assertThat(result).isSameAs(hangout);

        ArgumentCaptor<TransactWriteItemsRequest> captor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());

        TransactWriteItemsRequest request = captor.getValue();
        // 1 hangout + 1 pointer + 1 attribute + 1 poll + 3 poll options = 7 items
        assertThat(request.transactItems()).hasSize(7);

        // Verify poll and poll option items are included
        long pollCount = request.transactItems().stream()
            .filter(item -> item.put().item().get("itemType").s().equals("POLL"))
            .count();
        long pollOptionCount = request.transactItems().stream()
            .filter(item -> item.put().item().get("itemType").s().equals("POLL_OPTION"))
            .count();

        assertThat(pollCount).isEqualTo(1);
        assertThat(pollOptionCount).isEqualTo(3);
    }

    @Test
    void createHangoutWithAttributes_EmptyPolls_HandlesCorrectly() {
        // Given
        Hangout hangout = createValidHangout();
        String groupId = UUID.randomUUID().toString();

        List<HangoutPointer> pointers = Arrays.asList(
            createValidHangoutPointer(hangout.getHangoutId(), groupId)
        );
        List<Poll> emptyPolls = new ArrayList<>();
        List<PollOption> emptyPollOptions = new ArrayList<>();

        TransactWriteItemsResponse response = TransactWriteItemsResponse.builder().build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class))).thenReturn(response);

        // When
        Hangout result = repository.createHangoutWithAttributes(hangout, pointers, Collections.emptyList(), emptyPolls, emptyPollOptions);

        // Then
        assertThat(result).isSameAs(hangout);

        ArgumentCaptor<TransactWriteItemsRequest> captor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());

        TransactWriteItemsRequest request = captor.getValue();
        assertThat(request.transactItems()).hasSize(2); // 1 hangout + 1 pointer (no polls)

        // Verify no poll items are included
        boolean hasPollItem = request.transactItems().stream()
            .anyMatch(item -> item.put().item().get("itemType").s().equals("POLL"));
        assertThat(hasPollItem).isFalse();
    }

    // ============================================================================
    // HELPER METHODS
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
}
