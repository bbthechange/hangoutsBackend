package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.model.InterestLevel;
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
 * Tests for interest level operations in HangoutRepositoryImpl.
 *
 * Interest levels (attendance) represent user responses to events:
 * - "GOING" - user is attending
 * - "INTERESTED" - user might attend
 * - "NOT_GOING" - user declined
 *
 * Covers:
 * - saveInterestLevel(InterestLevel) - create/update user's interest level
 * - deleteInterestLevel(eventId, userId) - remove user's interest level
 *
 * Total tests: 9
 */
class HangoutRepositoryInterestLevelTest extends HangoutRepositoryTestBase {

    @Nested
    class SaveInterestLevel {

        @Test
        void saveInterestLevel_WithValidData_SavesSuccessfully() {
            // Given
            InterestLevel interestLevel = createValidInterestLevel("GOING");

            PutItemResponse response = PutItemResponse.builder().build();
            when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);

            // When
            InterestLevel result = repository.saveInterestLevel(interestLevel);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getEventId()).isEqualTo(eventId);
            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getStatus()).isEqualTo("GOING");
            assertThat(result.getUpdatedAt()).isNotNull(); // Should be touched

            ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
            verify(dynamoDbClient).putItem(captor.capture());

            PutItemRequest request = captor.getValue();
            assertThat(request.tableName()).isEqualTo("InviterTable");
            assertThat(request.item()).containsKey("pk");
            assertThat(request.item()).containsKey("sk");
            assertThat(request.item().get("pk").s()).isEqualTo(InviterKeyFactory.getEventPk(eventId));
            assertThat(request.item().get("sk").s()).isEqualTo(InviterKeyFactory.getAttendanceSk(userId));
        }

        @Test
        void saveInterestLevel_WithInterestedStatus_SavesCorrectly() {
            // Given
            InterestLevel interestLevel = createValidInterestLevel("INTERESTED");

            PutItemResponse response = PutItemResponse.builder().build();
            when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);

            // When
            InterestLevel result = repository.saveInterestLevel(interestLevel);

            // Then
            assertThat(result.getStatus()).isEqualTo("INTERESTED");
            verify(dynamoDbClient).putItem(any(PutItemRequest.class));
        }

        @Test
        void saveInterestLevel_WithNotGoingStatus_SavesCorrectly() {
            // Given
            InterestLevel interestLevel = createValidInterestLevel("NOT_GOING");

            PutItemResponse response = PutItemResponse.builder().build();
            when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);

            // When
            InterestLevel result = repository.saveInterestLevel(interestLevel);

            // Then
            assertThat(result.getStatus()).isEqualTo("NOT_GOING");
            verify(dynamoDbClient).putItem(any(PutItemRequest.class));
        }

        @Test
        void saveInterestLevel_WithNotes_PersistsNotes() {
            // Given
            InterestLevel interestLevel = createValidInterestLevel("GOING");
            interestLevel.setNotes("Running late, will be there by 7pm");

            PutItemResponse response = PutItemResponse.builder().build();
            when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);

            // When
            InterestLevel result = repository.saveInterestLevel(interestLevel);

            // Then
            assertThat(result.getNotes()).isEqualTo("Running late, will be there by 7pm");
            verify(dynamoDbClient).putItem(any(PutItemRequest.class));
        }

        @Test
        void saveInterestLevel_UpdatesTimestamp_TouchesRecord() {
            // Given
            InterestLevel interestLevel = createValidInterestLevel("GOING");
            Instant beforeSave = Instant.now();

            PutItemResponse response = PutItemResponse.builder().build();
            when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);

            // When
            InterestLevel result = repository.saveInterestLevel(interestLevel);

            // Then
            assertThat(result.getUpdatedAt()).isNotNull();
            assertThat(result.getUpdatedAt()).isAfterOrEqualTo(beforeSave);
        }

        @Test
        void saveInterestLevel_WithDenormalizedUserData_PersistsAllFields() {
            // Given
            InterestLevel interestLevel = createValidInterestLevel("GOING");
            interestLevel.setUserName("John Doe");
            interestLevel.setMainImagePath("users/profile123.jpg");

            PutItemResponse response = PutItemResponse.builder().build();
            when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);

            // When
            InterestLevel result = repository.saveInterestLevel(interestLevel);

            // Then
            assertThat(result.getUserName()).isEqualTo("John Doe");
            assertThat(result.getMainImagePath()).isEqualTo("users/profile123.jpg");
            verify(dynamoDbClient).putItem(any(PutItemRequest.class));
        }

        @Test
        void saveInterestLevel_WithDynamoDbException_ThrowsRepositoryException() {
            // Given
            InterestLevel interestLevel = createValidInterestLevel("GOING");

            when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenThrow(DynamoDbException.builder().message("DynamoDB error").build());

            // When/Then
            assertThatThrownBy(() -> repository.saveInterestLevel(interestLevel))
                .isInstanceOf(RepositoryException.class)
                .hasMessageContaining("Failed to save interest level")
                .hasCauseInstanceOf(DynamoDbException.class);
        }
    }

    @Nested
    class DeleteInterestLevel {

        @Test
        void deleteInterestLevel_WithValidIds_DeletesSuccessfully() {
            // Given
            DeleteItemResponse response = DeleteItemResponse.builder().build();
            when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class))).thenReturn(response);

            // When
            repository.deleteInterestLevel(eventId, userId);

            // Then
            ArgumentCaptor<DeleteItemRequest> captor = ArgumentCaptor.forClass(DeleteItemRequest.class);
            verify(dynamoDbClient).deleteItem(captor.capture());

            DeleteItemRequest request = captor.getValue();
            assertThat(request.tableName()).isEqualTo("InviterTable");
            assertThat(request.key()).containsKey("pk");
            assertThat(request.key()).containsKey("sk");
            assertThat(request.key().get("pk").s()).isEqualTo(InviterKeyFactory.getEventPk(eventId));
            assertThat(request.key().get("sk").s()).isEqualTo(InviterKeyFactory.getAttendanceSk(userId));
        }

        @Test
        void deleteInterestLevel_WithDynamoDbException_ThrowsRepositoryException() {
            // Given
            when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class)))
                .thenThrow(DynamoDbException.builder().message("DynamoDB error").build());

            // When/Then
            assertThatThrownBy(() -> repository.deleteInterestLevel(eventId, userId))
                .isInstanceOf(RepositoryException.class)
                .hasMessageContaining("Failed to delete interest level")
                .hasCauseInstanceOf(DynamoDbException.class);
        }
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    private InterestLevel createValidInterestLevel(String status) {
        InterestLevel interestLevel = new InterestLevel(eventId, userId, "Test User", status);
        interestLevel.setPk(InviterKeyFactory.getEventPk(eventId));
        interestLevel.setSk(InviterKeyFactory.getAttendanceSk(userId));
        return interestLevel;
    }
}
