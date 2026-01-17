package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.watchparty.sqs.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WatchPartySqsServiceImpl.
 *
 * Tests verify:
 * - Messages are sent to correct queue URLs
 * - Message types are properly constructed (NEW_EPISODE, UPDATE_TITLE, REMOVE_EPISODE)
 * - Message IDs are generated or preserved as appropriate
 * - Metrics are recorded for success, error, and serialization error scenarios
 */
@ExtendWith(MockitoExtension.class)
class WatchPartySqsServiceImplTest {

    private static final String TV_MAZE_UPDATES_QUEUE_URL = "https://sqs.us-west-2.amazonaws.com/test/tvmaze-updates";
    private static final String EPISODE_ACTIONS_QUEUE_URL = "https://sqs.us-west-2.amazonaws.com/test/episode-actions";

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    @Mock
    private ObjectMapper objectMapper;

    private SimpleMeterRegistry meterRegistry;
    private WatchPartySqsServiceImpl service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new WatchPartySqsServiceImpl(
                sqsAsyncClient,
                objectMapper,
                meterRegistry,
                TV_MAZE_UPDATES_QUEUE_URL,
                EPISODE_ACTIONS_QUEUE_URL
        );
    }

    @Nested
    class SendToTvMazeUpdatesQueueTests {

        @Test
        void sendToTvMazeUpdatesQueue_WithValidMessage_SendsToCorrectQueueUrl() throws Exception {
            // Given
            ShowUpdatedMessage message = new ShowUpdatedMessage(123);
            message.setMessageId("test-message-id");
            String serializedMessage = "{\"type\":\"SHOW_UPDATED\",\"showId\":123,\"messageId\":\"test-message-id\"}";
            when(objectMapper.writeValueAsString(message)).thenReturn(serializedMessage);
            when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                    .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().build()));

            // When
            service.sendToTvMazeUpdatesQueue(message);

            // Then
            ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
            verify(sqsAsyncClient).sendMessage(captor.capture());
            assertThat(captor.getValue().queueUrl()).isEqualTo(TV_MAZE_UPDATES_QUEUE_URL);
        }
    }

    @Nested
    class SendToEpisodeActionsQueueTests {

        @Test
        void sendToEpisodeActionsQueue_WithValidMessage_SendsToCorrectQueueUrl() throws Exception {
            // Given
            NewEpisodeMessage message = new NewEpisodeMessage("show-123-season-1", new EpisodeData(1001, "Episode Title", 1700000000L));
            message.setMessageId("test-message-id");
            String serializedMessage = "{\"type\":\"NEW_EPISODE\",\"seasonKey\":\"show-123-season-1\",\"messageId\":\"test-message-id\"}";
            when(objectMapper.writeValueAsString(message)).thenReturn(serializedMessage);
            when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                    .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().build()));

            // When
            service.sendToEpisodeActionsQueue(message);

            // Then
            ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
            verify(sqsAsyncClient).sendMessage(captor.capture());
            assertThat(captor.getValue().queueUrl()).isEqualTo(EPISODE_ACTIONS_QUEUE_URL);
        }
    }

    @Nested
    class SendNewEpisodeTests {

        @Test
        void sendNewEpisode_CreatesNewEpisodeMessageAndSends() throws Exception {
            // Given
            String seasonKey = "show-456-season-2";
            EpisodeData episode = new EpisodeData(2001, "The Big Reveal", 1705000000L);
            when(objectMapper.writeValueAsString(any(NewEpisodeMessage.class)))
                    .thenAnswer(invocation -> {
                        NewEpisodeMessage msg = invocation.getArgument(0);
                        return "{\"type\":\"NEW_EPISODE\",\"seasonKey\":\"" + msg.getSeasonKey() +
                               "\",\"messageId\":\"" + msg.getMessageId() + "\"}";
                    });
            when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                    .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().build()));

            // When
            service.sendNewEpisode(seasonKey, episode);

            // Then
            ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
            verify(sqsAsyncClient).sendMessage(captor.capture());
            String messageBody = captor.getValue().messageBody();
            assertThat(messageBody).contains("\"type\":\"NEW_EPISODE\"");
            assertThat(messageBody).contains("\"seasonKey\":\"show-456-season-2\"");
        }
    }

    @Nested
    class SendUpdateTitleTests {

        @Test
        void sendUpdateTitle_CreatesUpdateTitleMessageAndSends() throws Exception {
            // Given
            String externalId = "tvmaze-ep-789";
            String newTitle = "New Episode Title";
            when(objectMapper.writeValueAsString(any(UpdateTitleMessage.class)))
                    .thenAnswer(invocation -> {
                        UpdateTitleMessage msg = invocation.getArgument(0);
                        return "{\"type\":\"UPDATE_TITLE\",\"externalId\":\"" + msg.getExternalId() +
                               "\",\"newTitle\":\"" + msg.getNewTitle() +
                               "\",\"messageId\":\"" + msg.getMessageId() + "\"}";
                    });
            when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                    .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().build()));

            // When
            service.sendUpdateTitle(externalId, newTitle);

            // Then
            ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
            verify(sqsAsyncClient).sendMessage(captor.capture());
            String messageBody = captor.getValue().messageBody();
            assertThat(messageBody).contains("\"type\":\"UPDATE_TITLE\"");
            assertThat(messageBody).contains("\"externalId\":\"tvmaze-ep-789\"");
            assertThat(messageBody).contains("\"newTitle\":\"New Episode Title\"");
        }
    }

    @Nested
    class SendRemoveEpisodeTests {

        @Test
        void sendRemoveEpisode_CreatesRemoveEpisodeMessageAndSends() throws Exception {
            // Given
            String externalId = "tvmaze-ep-999";
            when(objectMapper.writeValueAsString(any(RemoveEpisodeMessage.class)))
                    .thenAnswer(invocation -> {
                        RemoveEpisodeMessage msg = invocation.getArgument(0);
                        return "{\"type\":\"REMOVE_EPISODE\",\"externalId\":\"" + msg.getExternalId() +
                               "\",\"messageId\":\"" + msg.getMessageId() + "\"}";
                    });
            when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                    .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().build()));

            // When
            service.sendRemoveEpisode(externalId);

            // Then
            ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
            verify(sqsAsyncClient).sendMessage(captor.capture());
            String messageBody = captor.getValue().messageBody();
            assertThat(messageBody).contains("\"type\":\"REMOVE_EPISODE\"");
            assertThat(messageBody).contains("\"externalId\":\"tvmaze-ep-999\"");
        }
    }

    @Nested
    class MessageIdHandlingTests {

        @Test
        void sendMessage_WithNullMessageId_GeneratesUUID() throws Exception {
            // Given
            ShowUpdatedMessage message = new ShowUpdatedMessage(100);
            // messageId is null by default
            assertThat(message.getMessageId()).isNull();

            when(objectMapper.writeValueAsString(any(WatchPartyMessage.class)))
                    .thenAnswer(invocation -> {
                        WatchPartyMessage msg = invocation.getArgument(0);
                        return "{\"type\":\"SHOW_UPDATED\",\"messageId\":\"" + msg.getMessageId() + "\"}";
                    });
            when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                    .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().build()));

            // When
            service.sendToTvMazeUpdatesQueue(message);

            // Then
            assertThat(message.getMessageId()).isNotNull();
            // UUID format check (36 chars with hyphens)
            assertThat(message.getMessageId()).matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");
        }

        @Test
        void sendMessage_WithExistingMessageId_PreservesIt() throws Exception {
            // Given
            String presetMessageId = "my-custom-message-id-12345";
            ShowUpdatedMessage message = new ShowUpdatedMessage(200);
            message.setMessageId(presetMessageId);

            when(objectMapper.writeValueAsString(any(WatchPartyMessage.class)))
                    .thenAnswer(invocation -> {
                        WatchPartyMessage msg = invocation.getArgument(0);
                        return "{\"type\":\"SHOW_UPDATED\",\"messageId\":\"" + msg.getMessageId() + "\"}";
                    });
            when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                    .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().build()));

            // When
            service.sendToTvMazeUpdatesQueue(message);

            // Then
            assertThat(message.getMessageId()).isEqualTo(presetMessageId);
            ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
            verify(sqsAsyncClient).sendMessage(captor.capture());
            assertThat(captor.getValue().messageBody()).contains("\"messageId\":\"my-custom-message-id-12345\"");
        }
    }

    @Nested
    class MetricsTests {

        @Test
        void sendMessage_OnSqsFailure_RecordsErrorMetric() throws Exception {
            // Given
            ShowUpdatedMessage message = new ShowUpdatedMessage(300);
            message.setMessageId("test-message-id");
            when(objectMapper.writeValueAsString(message))
                    .thenReturn("{\"type\":\"SHOW_UPDATED\",\"showId\":300,\"messageId\":\"test-message-id\"}");

            // Create a failed CompletableFuture that completes with an exception via whenComplete
            CompletableFuture<SendMessageResponse> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new RuntimeException("SQS connection failed"));
            when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class))).thenReturn(failedFuture);

            // When
            service.sendToTvMazeUpdatesQueue(message);

            // Then - need to wait for async completion
            // The whenComplete callback should have been invoked
            Counter errorCounter = meterRegistry.find("watchparty_sqs_send_total")
                    .tag("queue", "tvmaze-updates")
                    .tag("type", "SHOW_UPDATED")
                    .tag("status", "error")
                    .counter();
            assertThat(errorCounter).isNotNull();
            assertThat(errorCounter.count()).isEqualTo(1.0);
        }

        @Test
        void sendMessage_OnSuccess_RecordsSuccessMetric() throws Exception {
            // Given
            ShowUpdatedMessage message = new ShowUpdatedMessage(400);
            message.setMessageId("test-message-id");
            when(objectMapper.writeValueAsString(message))
                    .thenReturn("{\"type\":\"SHOW_UPDATED\",\"showId\":400,\"messageId\":\"test-message-id\"}");
            when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                    .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().messageId("sqs-msg-123").build()));

            // When
            service.sendToTvMazeUpdatesQueue(message);

            // Then
            Counter successCounter = meterRegistry.find("watchparty_sqs_send_total")
                    .tag("queue", "tvmaze-updates")
                    .tag("type", "SHOW_UPDATED")
                    .tag("status", "success")
                    .counter();
            assertThat(successCounter).isNotNull();
            assertThat(successCounter.count()).isEqualTo(1.0);
        }

        @Test
        void sendMessage_OnSerializationError_RecordsSerializationErrorMetric() throws Exception {
            // Given
            ShowUpdatedMessage message = new ShowUpdatedMessage(500);
            message.setMessageId("test-message-id");
            when(objectMapper.writeValueAsString(message))
                    .thenThrow(new JsonProcessingException("Serialization failed") {});

            // When
            service.sendToTvMazeUpdatesQueue(message);

            // Then
            Counter serializationErrorCounter = meterRegistry.find("watchparty_sqs_send_total")
                    .tag("queue", "tvmaze-updates")
                    .tag("type", "SHOW_UPDATED")
                    .tag("status", "serialization_error")
                    .counter();
            assertThat(serializationErrorCounter).isNotNull();
            assertThat(serializationErrorCounter.count()).isEqualTo(1.0);

            // Verify SQS client was never called
            verify(sqsAsyncClient, never()).sendMessage(any(SendMessageRequest.class));
        }
    }
}
