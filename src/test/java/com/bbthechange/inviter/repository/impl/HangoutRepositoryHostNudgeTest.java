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
 * Tests for watch-party host-nudge fields on HangoutRepositoryImpl.
 *
 * Mirrors HangoutRepositoryReminderTest for the host-nudge surface added in
 * Phase 1 of the watch-party host-nudge feature.
 */
class HangoutRepositoryHostNudgeTest extends HangoutRepositoryTestBase {

    // ============================================================================
    // setHostNudgeSentAtIfNull
    // ============================================================================

    @Test
    void setHostNudgeSentAtIfNull_ReturnsTrue_WhenAttributeNotExists() {
        UpdateItemResponse response = UpdateItemResponse.builder().build();
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenReturn(response);

        boolean result = repository.setHostNudgeSentAtIfNull(eventId, 1700000000000L);

        assertThat(result).isTrue();
        verify(dynamoDbClient).updateItem(any(UpdateItemRequest.class));
    }

    @Test
    void setHostNudgeSentAtIfNull_ReturnsFalse_WhenAttributeAlreadyExists() {
        ConditionalCheckFailedException exception = ConditionalCheckFailedException.builder()
            .message("The conditional request failed")
            .build();
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenThrow(exception);

        boolean result = repository.setHostNudgeSentAtIfNull(eventId, 1700000000000L);

        assertThat(result).isFalse();
    }

    @Test
    void setHostNudgeSentAtIfNull_ThrowsRepositoryException_OnDynamoDbError() {
        DynamoDbException exception = (DynamoDbException) DynamoDbException.builder()
            .message("Service unavailable")
            .build();
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenThrow(exception);

        assertThatThrownBy(() -> repository.setHostNudgeSentAtIfNull(eventId, 1700000000000L))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("Failed to set hostNudgeSentAt")
            .hasCause(exception);
    }

    @Test
    void setHostNudgeSentAtIfNull_UsesCorrectKeyAndCondition() {
        UpdateItemResponse response = UpdateItemResponse.builder().build();
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenReturn(response);

        repository.setHostNudgeSentAtIfNull(eventId, 1700000000000L);

        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDbClient).updateItem(captor.capture());
        UpdateItemRequest request = captor.getValue();

        assertThat(request.tableName()).isEqualTo(TABLE_NAME);
        assertThat(request.key().get("pk").s()).isEqualTo(InviterKeyFactory.getEventPk(eventId));
        assertThat(request.key().get("sk").s()).isEqualTo(InviterKeyFactory.getMetadataSk());
        assertThat(request.conditionExpression()).isEqualTo("attribute_not_exists(hostNudgeSentAt)");
        assertThat(request.updateExpression()).contains("SET hostNudgeSentAt = :timestamp");
        assertThat(request.updateExpression()).contains("updatedAt = :now");
        assertThat(request.expressionAttributeValues().get(":timestamp").n()).isEqualTo("1700000000000");
    }

    // ============================================================================
    // updateHostNudgeScheduleName
    // ============================================================================

    @Test
    void updateHostNudgeScheduleName_CallsDynamoDbWithCorrectExpression() {
        UpdateItemResponse response = UpdateItemResponse.builder().build();
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenReturn(response);
        String scheduleName = "hostnudge-" + eventId;

        repository.updateHostNudgeScheduleName(eventId, scheduleName);

        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDbClient).updateItem(captor.capture());
        UpdateItemRequest request = captor.getValue();

        assertThat(request.tableName()).isEqualTo(TABLE_NAME);
        assertThat(request.key().get("pk").s()).isEqualTo(InviterKeyFactory.getEventPk(eventId));
        assertThat(request.key().get("sk").s()).isEqualTo(InviterKeyFactory.getMetadataSk());
        assertThat(request.updateExpression()).contains("SET hostNudgeScheduleName = :name");
        assertThat(request.updateExpression()).contains("updatedAt = :now");
        assertThat(request.expressionAttributeValues().get(":name").s()).isEqualTo(scheduleName);
    }

    @Test
    void updateHostNudgeScheduleName_ThrowsRepositoryException_OnDynamoDbError() {
        DynamoDbException exception = (DynamoDbException) DynamoDbException.builder()
            .message("Service unavailable")
            .build();
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenThrow(exception);

        assertThatThrownBy(() -> repository.updateHostNudgeScheduleName(eventId, "hostnudge-x"))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("Failed to update hostNudgeScheduleName")
            .hasCause(exception);
    }

    // ============================================================================
    // updateLastHostNotificationAt
    // ============================================================================

    @Test
    void updateLastHostNotificationAt_CallsDynamoDbWithCorrectExpression() {
        UpdateItemResponse response = UpdateItemResponse.builder().build();
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenReturn(response);

        repository.updateLastHostNotificationAt(eventId, 1700000000000L);

        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDbClient).updateItem(captor.capture());
        UpdateItemRequest request = captor.getValue();

        assertThat(request.tableName()).isEqualTo(TABLE_NAME);
        assertThat(request.key().get("pk").s()).isEqualTo(InviterKeyFactory.getEventPk(eventId));
        assertThat(request.key().get("sk").s()).isEqualTo(InviterKeyFactory.getMetadataSk());
        assertThat(request.updateExpression()).contains("SET lastHostNotificationAt = :timestamp");
        assertThat(request.updateExpression()).contains("updatedAt = :now");
        assertThat(request.expressionAttributeValues().get(":timestamp").n()).isEqualTo("1700000000000");
    }

    @Test
    void updateLastHostNotificationAt_ThrowsRepositoryException_OnDynamoDbError() {
        DynamoDbException exception = (DynamoDbException) DynamoDbException.builder()
            .message("Service unavailable")
            .build();
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenThrow(exception);

        assertThatThrownBy(() -> repository.updateLastHostNotificationAt(eventId, 1700000000000L))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("Failed to update lastHostNotificationAt")
            .hasCause(exception);
    }
}
