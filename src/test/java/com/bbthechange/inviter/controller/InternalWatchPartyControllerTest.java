package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.watchparty.PollResult;
import com.bbthechange.inviter.dto.watchparty.sqs.ShowUpdatedMessage;
import com.bbthechange.inviter.dto.watchparty.sqs.TestMessageRequest;
import com.bbthechange.inviter.dto.watchparty.sqs.WatchPartyMessage;
import com.bbthechange.inviter.service.TvMazePollingService;
import com.bbthechange.inviter.service.WatchPartySqsService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for InternalWatchPartyController.triggerPoll() endpoint.
 *
 * These tests verify the behavior of the trigger-poll endpoint under various conditions:
 * - Polling service not available (Optional.empty)
 * - Successful poll with results
 * - Poll returning no tracked shows
 * - Poll throwing exceptions
 * - Metric recording behavior
 */
@ExtendWith(MockitoExtension.class)
class InternalWatchPartyControllerTest {

    @Mock
    private WatchPartySqsService sqsService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private TvMazePollingService pollingService;

    private SimpleMeterRegistry meterRegistry;

    @Nested
    class TriggerPollTests {

        private InternalWatchPartyController controllerWithPolling;
        private InternalWatchPartyController controllerWithoutPolling;

        @BeforeEach
        void setUp() {
            meterRegistry = new SimpleMeterRegistry();
            controllerWithPolling = new InternalWatchPartyController(
                    sqsService, objectMapper, meterRegistry, Optional.of(pollingService));
            controllerWithoutPolling = new InternalWatchPartyController(
                    sqsService, objectMapper, meterRegistry, Optional.empty());
        }

        @Test
        void triggerPoll_WhenPollingServiceNotAvailable_Returns503() {
            // When
            ResponseEntity<Map<String, Object>> response = controllerWithoutPolling.triggerPoll();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).containsKey("error");
            assertThat(response.getBody()).containsKey("hint");
            assertThat(response.getBody().get("error")).isEqualTo("Polling service not enabled");
        }

        @Test
        void triggerPoll_WhenPollSucceeds_Returns200WithStatistics() {
            // Given
            PollResult pollResult = PollResult.success(10, 3, 3, 1500L);
            when(pollingService.pollForUpdates()).thenReturn(pollResult);

            // When
            ResponseEntity<Map<String, Object>> response = controllerWithPolling.triggerPoll();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("status")).isEqualTo("completed");
            assertThat(response.getBody().get("totalTrackedShows")).isEqualTo(10);
            assertThat(response.getBody().get("updatedShowsFound")).isEqualTo(3);
            assertThat(response.getBody().get("messagesEmitted")).isEqualTo(3);
            assertThat(response.getBody().get("durationMs")).isEqualTo(1500L);
        }

        @Test
        void triggerPoll_WhenPollThrowsException_Returns500WithError() {
            // Given
            when(pollingService.pollForUpdates()).thenThrow(new RuntimeException("Database error"));

            // When
            ResponseEntity<Map<String, Object>> response = controllerWithPolling.triggerPoll();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("status")).isEqualTo("error");
            assertThat(response.getBody().get("error")).isEqualTo("Database error");
        }

        @Test
        void triggerPoll_WhenPollReturnsNoTrackedShows_Returns200WithZeroCounts() {
            // Given
            PollResult pollResult = PollResult.noTrackedShows(100L);
            when(pollingService.pollForUpdates()).thenReturn(pollResult);

            // When
            ResponseEntity<Map<String, Object>> response = controllerWithPolling.triggerPoll();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("status")).isEqualTo("completed");
            assertThat(response.getBody().get("totalTrackedShows")).isEqualTo(0);
            assertThat(response.getBody().get("updatedShowsFound")).isEqualTo(0);
            assertThat(response.getBody().get("messagesEmitted")).isEqualTo(0);
        }

        @Test
        void triggerPoll_Success_IncrementsSuccessMetric() {
            // Given
            PollResult pollResult = PollResult.success(5, 1, 1, 500L);
            when(pollingService.pollForUpdates()).thenReturn(pollResult);

            // When
            controllerWithPolling.triggerPoll();

            // Then
            Counter successCounter = meterRegistry.find("watchparty_poll_trigger_total")
                    .tag("status", "success")
                    .counter();
            assertThat(successCounter).isNotNull();
            assertThat(successCounter.count()).isEqualTo(1.0);
        }

        @Test
        void triggerPoll_Error_IncrementsErrorMetric() {
            // Given
            when(pollingService.pollForUpdates()).thenThrow(new RuntimeException("Test error"));

            // When
            controllerWithPolling.triggerPoll();

            // Then
            Counter errorCounter = meterRegistry.find("watchparty_poll_trigger_total")
                    .tag("status", "error")
                    .counter();
            assertThat(errorCounter).isNotNull();
            assertThat(errorCounter.count()).isEqualTo(1.0);
        }
    }

    @Nested
    class SendTestMessageTests {

        private InternalWatchPartyController controller;

        @BeforeEach
        void setUp() {
            meterRegistry = new SimpleMeterRegistry();
            controller = new InternalWatchPartyController(
                    sqsService, objectMapper, meterRegistry, Optional.of(pollingService));
        }

        @Test
        void sendTestMessage_WithMissingQueueType_Returns400() {
            // Given - queueType is null
            TestMessageRequest request = new TestMessageRequest(null, "{\"type\":\"SHOW_UPDATED\",\"showId\":123}");

            // When
            ResponseEntity<Map<String, String>> response = controller.sendTestMessage(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("error")).contains("queueType");
        }

        @Test
        void sendTestMessage_WithMissingMessageBody_Returns400() {
            // Given - messageBody is null
            TestMessageRequest request = new TestMessageRequest("tvmaze-updates", null);

            // When
            ResponseEntity<Map<String, String>> response = controller.sendTestMessage(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("error")).contains("messageBody");
        }

        @Test
        void sendTestMessage_WithInvalidQueueType_Returns400() throws Exception {
            // Given
            String messageBody = "{\"type\":\"SHOW_UPDATED\",\"showId\":123}";
            TestMessageRequest request = new TestMessageRequest("invalid-queue", messageBody);
            // The controller parses the message before validating queue type
            ShowUpdatedMessage parsedMessage = new ShowUpdatedMessage(123);
            when(objectMapper.readValue(eq(messageBody), eq(WatchPartyMessage.class))).thenReturn(parsedMessage);

            // When
            ResponseEntity<Map<String, String>> response = controller.sendTestMessage(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("error")).contains("invalid-queue");
        }

        @Test
        void sendTestMessage_WithValidTvMazeUpdatesQueue_Returns200AndSendsMessage() throws Exception {
            // Given
            String messageBody = "{\"type\":\"SHOW_UPDATED\",\"showId\":123}";
            TestMessageRequest request = new TestMessageRequest("tvmaze-updates", messageBody);
            ShowUpdatedMessage expectedMessage = new ShowUpdatedMessage(123);
            when(objectMapper.readValue(eq(messageBody), eq(WatchPartyMessage.class))).thenReturn(expectedMessage);

            // When
            ResponseEntity<Map<String, String>> response = controller.sendTestMessage(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("status")).isEqualTo("sent");
            assertThat(response.getBody().get("queue")).isEqualTo("tvmaze-updates");
            verify(sqsService).sendToTvMazeUpdatesQueue(any(WatchPartyMessage.class));
        }

        @Test
        void sendTestMessage_WithValidEpisodeActionsQueue_Returns200AndSendsMessage() throws Exception {
            // Given
            String messageBody = "{\"type\":\"NEW_EPISODE\",\"seasonKey\":\"show-1-season-1\"}";
            TestMessageRequest request = new TestMessageRequest("episode-actions", messageBody);
            // Use a real message instance instead of mock to avoid NPE on getMessageId()
            ShowUpdatedMessage expectedMessage = new ShowUpdatedMessage(100);
            expectedMessage.setMessageId("test-message-id-123");
            when(objectMapper.readValue(eq(messageBody), eq(WatchPartyMessage.class))).thenReturn(expectedMessage);

            // When
            ResponseEntity<Map<String, String>> response = controller.sendTestMessage(request);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("status")).isEqualTo("sent");
            assertThat(response.getBody().get("queue")).isEqualTo("episode-actions");
            verify(sqsService).sendToEpisodeActionsQueue(any(WatchPartyMessage.class));
        }
    }

    @Nested
    class TriggerShowUpdateTests {

        private InternalWatchPartyController controller;

        @BeforeEach
        void setUp() {
            meterRegistry = new SimpleMeterRegistry();
            controller = new InternalWatchPartyController(
                    sqsService, objectMapper, meterRegistry, Optional.of(pollingService));
        }

        @Test
        void triggerShowUpdate_WithMissingShowId_Returns400() {
            // Given - empty body
            Map<String, Integer> body = Map.of();

            // When
            ResponseEntity<Map<String, String>> response = controller.triggerShowUpdate(body);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("error")).contains("showId");
        }

        @Test
        void triggerShowUpdate_WithValidShowId_Returns200AndSendsMessage() {
            // Given
            Map<String, Integer> body = Map.of("showId", 123);

            // When
            ResponseEntity<Map<String, String>> response = controller.triggerShowUpdate(body);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("status")).isEqualTo("triggered");
            assertThat(response.getBody().get("showId")).isEqualTo("123");

            ArgumentCaptor<WatchPartyMessage> captor = ArgumentCaptor.forClass(WatchPartyMessage.class);
            verify(sqsService).sendToTvMazeUpdatesQueue(captor.capture());
            assertThat(captor.getValue()).isInstanceOf(ShowUpdatedMessage.class);
            ShowUpdatedMessage sentMessage = (ShowUpdatedMessage) captor.getValue();
            assertThat(sentMessage.getShowId()).isEqualTo(123);
        }

        @Test
        void triggerShowUpdate_Success_IncrementsMetric() {
            // Given
            Map<String, Integer> body = Map.of("showId", 456);

            // When
            controller.triggerShowUpdate(body);

            // Then
            Counter counter = meterRegistry.find("watchparty_test_message_total")
                    .tag("queue", "tvmaze-updates")
                    .tag("type", "SHOW_UPDATED")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }
    }
}
