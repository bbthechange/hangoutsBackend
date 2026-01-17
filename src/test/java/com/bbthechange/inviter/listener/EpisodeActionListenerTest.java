package com.bbthechange.inviter.listener;

import com.bbthechange.inviter.dto.watchparty.sqs.NewEpisodeMessage;
import com.bbthechange.inviter.dto.watchparty.sqs.RemoveEpisodeMessage;
import com.bbthechange.inviter.dto.watchparty.sqs.UpdateTitleMessage;
import com.bbthechange.inviter.service.WatchPartyBackgroundService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EpisodeActionListenerTest {

    @Mock
    private WatchPartyBackgroundService backgroundService;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    private EpisodeActionListener listener;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);
        listener = new EpisodeActionListener(backgroundService, objectMapper, meterRegistry);
    }

    @Test
    void handleMessage_WithNewEpisode_ProcessesNewEpisode() {
        // Given
        String messageBody = """
            {
                "type": "NEW_EPISODE",
                "seasonKey": "TVMAZE#SHOW#123|SEASON#1",
                "episode": {
                    "episodeId": 456,
                    "title": "Pilot",
                    "airTimestamp": 1705363200
                }
            }
            """;

        // When
        listener.handleMessage(messageBody);

        // Then
        verify(backgroundService).processNewEpisode(any(NewEpisodeMessage.class));
        verify(meterRegistry).counter("watchparty_episode_action_total", "type", "NEW_EPISODE", "status", "success");
    }

    @Test
    void handleMessage_WithUpdateTitle_ProcessesUpdateTitle() {
        // Given
        String messageBody = """
            {
                "type": "UPDATE_TITLE",
                "externalId": "456",
                "newTitle": "Updated Episode Title"
            }
            """;

        // When
        listener.handleMessage(messageBody);

        // Then
        verify(backgroundService).processUpdateTitle(any(UpdateTitleMessage.class));
        verify(meterRegistry).counter("watchparty_episode_action_total", "type", "UPDATE_TITLE", "status", "success");
    }

    @Test
    void handleMessage_WithRemoveEpisode_ProcessesRemoveEpisode() {
        // Given
        String messageBody = """
            {
                "type": "REMOVE_EPISODE",
                "externalId": "456"
            }
            """;

        // When
        listener.handleMessage(messageBody);

        // Then
        verify(backgroundService).processRemoveEpisode(any(RemoveEpisodeMessage.class));
        verify(meterRegistry).counter("watchparty_episode_action_total", "type", "REMOVE_EPISODE", "status", "success");
    }

    @Test
    void handleMessage_WithMissingType_LogsWarning() {
        // Given
        String messageBody = """
            {
                "externalId": "456"
            }
            """;

        // When
        listener.handleMessage(messageBody);

        // Then
        verify(backgroundService, never()).processNewEpisode(any());
        verify(backgroundService, never()).processUpdateTitle(any());
        verify(backgroundService, never()).processRemoveEpisode(any());
        verify(meterRegistry).counter("watchparty_episode_action_total", "type", "unknown", "status", "missing_type");
    }

    @Test
    void handleMessage_WithUnknownType_LogsWarning() {
        // Given
        String messageBody = """
            {
                "type": "UNKNOWN_TYPE",
                "data": "test"
            }
            """;

        // When
        listener.handleMessage(messageBody);

        // Then
        verify(backgroundService, never()).processNewEpisode(any());
        verify(backgroundService, never()).processUpdateTitle(any());
        verify(backgroundService, never()).processRemoveEpisode(any());
        verify(meterRegistry).counter("watchparty_episode_action_total", "type", "UNKNOWN_TYPE", "status", "unknown_type");
    }

    @Test
    void handleMessage_WithInvalidJson_DoesNotCrash() {
        // Given
        String messageBody = "not valid json";

        // When
        listener.handleMessage(messageBody);

        // Then
        verify(backgroundService, never()).processNewEpisode(any());
        verify(backgroundService, never()).processUpdateTitle(any());
        verify(backgroundService, never()).processRemoveEpisode(any());
        verify(meterRegistry).counter("watchparty_episode_action_total", "type", "unknown", "status", "error");
    }

    @Test
    void handleMessage_WhenBackgroundServiceThrows_DoesNotRethrow() {
        // Given
        String messageBody = """
            {
                "type": "NEW_EPISODE",
                "seasonKey": "TVMAZE#SHOW#123|SEASON#1",
                "episode": {
                    "episodeId": 456,
                    "title": "Pilot",
                    "airTimestamp": 1705363200
                }
            }
            """;

        doThrow(new RuntimeException("Processing failed"))
                .when(backgroundService).processNewEpisode(any());

        // When - should not throw
        listener.handleMessage(messageBody);

        // Then
        verify(backgroundService).processNewEpisode(any(NewEpisodeMessage.class));
        verify(meterRegistry).counter("watchparty_episode_action_total", "type", "NEW_EPISODE", "status", "error");
    }
}
