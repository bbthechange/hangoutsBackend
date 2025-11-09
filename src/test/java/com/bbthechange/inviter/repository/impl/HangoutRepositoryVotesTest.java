package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.model.Vote;
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
 * Tests for vote operations in HangoutRepositoryImpl.
 *
 * Votes represent user selections on poll options.
 * Each vote is uniquely identified by eventId + pollId + userId + optionId.
 *
 * Covers:
 * - saveVote(Vote) - create/update user's vote on a poll option
 * - deleteVote(eventId, pollId, userId, optionId) - remove user's vote
 *
 * Total tests: 10
 */
class HangoutRepositoryVotesTest extends HangoutRepositoryTestBase {

    @Nested
    class SaveVote {

        @Test
        void saveVote_WithValidData_SavesSuccessfully() {
            // Given
            Vote vote = createValidVote("YES");

            PutItemResponse response = PutItemResponse.builder().build();
            when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);

            // When
            Vote result = repository.saveVote(vote);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getEventId()).isEqualTo(eventId);
            assertThat(result.getPollId()).isEqualTo(pollId);
            assertThat(result.getOptionId()).isEqualTo(optionId);
            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getVoteType()).isEqualTo("YES");
            assertThat(result.getUpdatedAt()).isNotNull(); // Should be touched

            ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
            verify(dynamoDbClient).putItem(captor.capture());

            PutItemRequest request = captor.getValue();
            assertThat(request.tableName()).isEqualTo("InviterTable");
            assertThat(request.item()).containsKey("pk");
            assertThat(request.item()).containsKey("sk");
            assertThat(request.item().get("pk").s()).isEqualTo(InviterKeyFactory.getEventPk(eventId));
            assertThat(request.item().get("sk").s()).isEqualTo(InviterKeyFactory.getVoteSk(pollId, userId, optionId));
        }

        @Test
        void saveVote_WithYesVoteType_SavesCorrectly() {
            // Given
            Vote vote = createValidVote("YES");

            PutItemResponse response = PutItemResponse.builder().build();
            when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);

            // When
            Vote result = repository.saveVote(vote);

            // Then
            assertThat(result.getVoteType()).isEqualTo("YES");
            verify(dynamoDbClient).putItem(any(PutItemRequest.class));
        }

        @Test
        void saveVote_WithNoVoteType_SavesCorrectly() {
            // Given
            Vote vote = createValidVote("NO");

            PutItemResponse response = PutItemResponse.builder().build();
            when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);

            // When
            Vote result = repository.saveVote(vote);

            // Then
            assertThat(result.getVoteType()).isEqualTo("NO");
            verify(dynamoDbClient).putItem(any(PutItemRequest.class));
        }

        @Test
        void saveVote_WithMaybeVoteType_SavesCorrectly() {
            // Given
            Vote vote = createValidVote("MAYBE");

            PutItemResponse response = PutItemResponse.builder().build();
            when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);

            // When
            Vote result = repository.saveVote(vote);

            // Then
            assertThat(result.getVoteType()).isEqualTo("MAYBE");
            verify(dynamoDbClient).putItem(any(PutItemRequest.class));
        }

        @Test
        void saveVote_WithCustomVoteType_SavesCorrectly() {
            // Given - custom vote type for specialized polls
            Vote vote = createValidVote("STRONGLY_AGREE");

            PutItemResponse response = PutItemResponse.builder().build();
            when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);

            // When
            Vote result = repository.saveVote(vote);

            // Then
            assertThat(result.getVoteType()).isEqualTo("STRONGLY_AGREE");
            verify(dynamoDbClient).putItem(any(PutItemRequest.class));
        }

        @Test
        void saveVote_UpdatesTimestamp_TouchesRecord() {
            // Given
            Vote vote = createValidVote("YES");
            Instant beforeSave = Instant.now();

            PutItemResponse response = PutItemResponse.builder().build();
            when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);

            // When
            Vote result = repository.saveVote(vote);

            // Then
            assertThat(result.getUpdatedAt()).isNotNull();
            assertThat(result.getUpdatedAt()).isAfterOrEqualTo(beforeSave);
        }

        @Test
        void saveVote_ChangingVote_OverwritesPrevious() {
            // Given - user changing their vote from YES to NO
            Vote initialVote = createValidVote("YES");
            Vote changedVote = createValidVote("NO");

            PutItemResponse response = PutItemResponse.builder().build();
            when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);

            // When - save initial vote then change it
            repository.saveVote(initialVote);
            Vote result = repository.saveVote(changedVote);

            // Then - vote should reflect the change
            assertThat(result.getVoteType()).isEqualTo("NO");
            verify(dynamoDbClient, times(2)).putItem(any(PutItemRequest.class));
        }

        @Test
        void saveVote_WithDynamoDbException_ThrowsRepositoryException() {
            // Given
            Vote vote = createValidVote("YES");

            when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenThrow(DynamoDbException.builder().message("DynamoDB error").build());

            // When/Then
            assertThatThrownBy(() -> repository.saveVote(vote))
                .isInstanceOf(RepositoryException.class)
                .hasMessageContaining("Failed to save vote")
                .hasCauseInstanceOf(DynamoDbException.class);
        }
    }

    @Nested
    class DeleteVote {

        @Test
        void deleteVote_WithValidIds_DeletesSuccessfully() {
            // Given
            DeleteItemResponse response = DeleteItemResponse.builder().build();
            when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class))).thenReturn(response);

            // When
            repository.deleteVote(eventId, pollId, userId, optionId);

            // Then
            ArgumentCaptor<DeleteItemRequest> captor = ArgumentCaptor.forClass(DeleteItemRequest.class);
            verify(dynamoDbClient).deleteItem(captor.capture());

            DeleteItemRequest request = captor.getValue();
            assertThat(request.tableName()).isEqualTo("InviterTable");
            assertThat(request.key()).containsKey("pk");
            assertThat(request.key()).containsKey("sk");
            assertThat(request.key().get("pk").s()).isEqualTo(InviterKeyFactory.getEventPk(eventId));
            assertThat(request.key().get("sk").s()).isEqualTo(InviterKeyFactory.getVoteSk(pollId, userId, optionId));
        }

        @Test
        void deleteVote_WithDynamoDbException_ThrowsRepositoryException() {
            // Given
            when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class)))
                .thenThrow(DynamoDbException.builder().message("DynamoDB error").build());

            // When/Then
            assertThatThrownBy(() -> repository.deleteVote(eventId, pollId, userId, optionId))
                .isInstanceOf(RepositoryException.class)
                .hasMessageContaining("Failed to delete vote")
                .hasCauseInstanceOf(DynamoDbException.class);
        }
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    private Vote createValidVote(String voteType) {
        Vote vote = new Vote(eventId, pollId, optionId, userId, voteType);
        vote.setPk(InviterKeyFactory.getEventPk(eventId));
        vote.setSk(InviterKeyFactory.getVoteSk(pollId, userId, optionId));
        return vote;
    }
}
