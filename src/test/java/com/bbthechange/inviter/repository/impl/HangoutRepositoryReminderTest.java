package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.exception.RepositoryException;
import com.bbthechange.inviter.util.InviterKeyFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for reminder-related methods in HangoutRepositoryImpl.
 *
 * Coverage:
 * - setReminderSentAtIfNull: conditional update for idempotency
 * - updateReminderScheduleName: storing EventBridge schedule name
 * - clearReminderSentAt: REMOVE expression for resetting reminder state
 */
class HangoutRepositoryReminderTest extends HangoutRepositoryTestBase {

    // ============================================================================
    // setReminderSentAtIfNull TESTS
    // ============================================================================

    @Test
    void testSetReminderSentAtIfNull_ReturnsTrue_WhenAttributeNotExists() {
        // Given
        UpdateItemResponse response = UpdateItemResponse.builder().build();
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenReturn(response);

        // When
        boolean result = repository.setReminderSentAtIfNull(eventId, 1700000000000L);

        // Then
        assertThat(result).isTrue();
        verify(dynamoDbClient).updateItem(any(UpdateItemRequest.class));
    }

    @Test
    void testSetReminderSentAtIfNull_ReturnsFalse_WhenAttributeAlreadyExists() {
        // Given
        ConditionalCheckFailedException exception = ConditionalCheckFailedException.builder()
            .message("The conditional request failed")
            .build();
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenThrow(exception);

        // When
        boolean result = repository.setReminderSentAtIfNull(eventId, 1700000000000L);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void testSetReminderSentAtIfNull_ThrowsRepositoryException_OnDynamoDbError() {
        // Given
        DynamoDbException exception = (DynamoDbException) DynamoDbException.builder()
            .message("Service unavailable")
            .build();
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenThrow(exception);

        // When/Then
        assertThatThrownBy(() -> repository.setReminderSentAtIfNull(eventId, 1700000000000L))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("Failed to set reminderSentAt")
            .hasCause(exception);
    }

    @Test
    void testSetReminderSentAtIfNull_UsesCorrectKey() {
        // Given
        UpdateItemResponse response = UpdateItemResponse.builder().build();
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenReturn(response);

        // When
        repository.setReminderSentAtIfNull(eventId, 1700000000000L);

        // Then
        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDbClient).updateItem(captor.capture());

        UpdateItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo(TABLE_NAME);
        assertThat(request.key().get("pk").s()).isEqualTo(InviterKeyFactory.getEventPk(eventId));
        assertThat(request.key().get("sk").s()).isEqualTo(InviterKeyFactory.getMetadataSk());

        // Verify condition expression for idempotency
        assertThat(request.conditionExpression()).isEqualTo("attribute_not_exists(reminderSentAt)");

        // Verify update expression sets reminderSentAt and updatedAt
        assertThat(request.updateExpression()).contains("SET reminderSentAt = :timestamp");
        assertThat(request.updateExpression()).contains("updatedAt = :now");

        // Verify the timestamp value
        assertThat(request.expressionAttributeValues().get(":timestamp").n()).isEqualTo("1700000000000");
    }

    // ============================================================================
    // updateReminderScheduleName TESTS
    // ============================================================================

    @Test
    void testUpdateReminderScheduleName_CallsDynamoDbWithCorrectExpression() {
        // Given
        UpdateItemResponse response = UpdateItemResponse.builder().build();
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenReturn(response);
        String scheduleName = "hangout-" + eventId;

        // When
        repository.updateReminderScheduleName(eventId, scheduleName);

        // Then
        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDbClient).updateItem(captor.capture());

        UpdateItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo(TABLE_NAME);
        assertThat(request.key().get("pk").s()).isEqualTo(InviterKeyFactory.getEventPk(eventId));
        assertThat(request.key().get("sk").s()).isEqualTo(InviterKeyFactory.getMetadataSk());

        // Verify update expression sets reminderScheduleName
        assertThat(request.updateExpression()).contains("SET reminderScheduleName = :name");
        assertThat(request.updateExpression()).contains("updatedAt = :now");

        // Verify the schedule name value
        assertThat(request.expressionAttributeValues().get(":name").s()).isEqualTo(scheduleName);
    }

    @Test
    void testUpdateReminderScheduleName_ThrowsRepositoryException_OnDynamoDbError() {
        // Given
        DynamoDbException exception = (DynamoDbException) DynamoDbException.builder()
            .message("Service unavailable")
            .build();
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenThrow(exception);

        // When/Then
        assertThatThrownBy(() -> repository.updateReminderScheduleName(eventId, "hangout-test"))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("Failed to update reminderScheduleName")
            .hasCause(exception);
    }

    // ============================================================================
    // clearReminderSentAt TESTS
    // ============================================================================

    @Test
    void testClearReminderSentAt_UsesRemoveExpression() {
        // Given
        UpdateItemResponse response = UpdateItemResponse.builder().build();
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenReturn(response);

        // When
        repository.clearReminderSentAt(eventId);

        // Then
        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDbClient).updateItem(captor.capture());

        UpdateItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo(TABLE_NAME);
        assertThat(request.key().get("pk").s()).isEqualTo(InviterKeyFactory.getEventPk(eventId));
        assertThat(request.key().get("sk").s()).isEqualTo(InviterKeyFactory.getMetadataSk());

        // Verify REMOVE expression is used (not SET to null)
        assertThat(request.updateExpression()).contains("REMOVE reminderSentAt");
        assertThat(request.updateExpression()).contains("SET updatedAt = :now");

        // Verify updatedAt timestamp is provided
        assertThat(request.expressionAttributeValues()).containsKey(":now");
    }

    @Test
    void testClearReminderSentAt_ThrowsRepositoryException_OnDynamoDbError() {
        // Given
        DynamoDbException exception = (DynamoDbException) DynamoDbException.builder()
            .message("Service unavailable")
            .build();
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenThrow(exception);

        // When/Then
        assertThatThrownBy(() -> repository.clearReminderSentAt(eventId))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("Failed to clear reminderSentAt")
            .hasCause(exception);
    }
}
