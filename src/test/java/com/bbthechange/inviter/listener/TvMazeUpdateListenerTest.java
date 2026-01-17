package com.bbthechange.inviter.listener;

import com.bbthechange.inviter.client.TvMazeClient;
import com.bbthechange.inviter.dto.tvmaze.TvMazeEpisodeResponse;
import com.bbthechange.inviter.model.Episode;
import com.bbthechange.inviter.model.Season;
import com.bbthechange.inviter.repository.SeasonRepository;
import com.bbthechange.inviter.service.WatchPartySqsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TvMazeUpdateListenerTest {

    @Mock
    private SeasonRepository seasonRepository;

    @Mock
    private TvMazeClient tvMazeClient;

    @Mock
    private WatchPartySqsService sqsService;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    private TvMazeUpdateListener listener;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);
        listener = new TvMazeUpdateListener(seasonRepository, tvMazeClient, sqsService, objectMapper, meterRegistry);
    }

    @Test
    void handleMessage_WithValidShowId_ProcessesSeasons() {
        // Given
        String messageBody = "{\"type\":\"SHOW_UPDATED\",\"showId\":123}";

        Season season = new Season(123, 1, "Test Show");
        season.setTvmazeSeasonId(456);

        when(seasonRepository.findByShowId(123)).thenReturn(List.of(season));
        when(tvMazeClient.getEpisodes(456)).thenReturn(List.of());

        // When
        listener.handleMessage(messageBody);

        // Then
        verify(seasonRepository).findByShowId(123);
        verify(meterRegistry).counter("watchparty_tvmaze_update_total", "status", "success");
    }

    @Test
    void handleMessage_WithNullShowId_LogsWarning() {
        // Given
        String messageBody = "{\"type\":\"SHOW_UPDATED\",\"showId\":null}";

        // When
        listener.handleMessage(messageBody);

        // Then
        verify(seasonRepository, never()).findByShowId(any());
        verify(meterRegistry).counter("watchparty_tvmaze_update_total", "status", "invalid_show_id");
    }

    @Test
    void handleMessage_WithNoSeasons_SkipsProcessing() {
        // Given
        String messageBody = "{\"type\":\"SHOW_UPDATED\",\"showId\":123}";
        when(seasonRepository.findByShowId(123)).thenReturn(List.of());

        // When
        listener.handleMessage(messageBody);

        // Then
        verify(seasonRepository).findByShowId(123);
        verify(tvMazeClient, never()).getEpisodes(any());
        verify(meterRegistry).counter("watchparty_tvmaze_update_total", "status", "no_seasons");
    }

    @Test
    void handleMessage_WithNewEpisode_EmitsNewEpisodeMessage() {
        // Given
        String messageBody = "{\"type\":\"SHOW_UPDATED\",\"showId\":123}";

        Season season = new Season(123, 1, "Test Show");
        season.setTvmazeSeasonId(456);
        // Season has no episodes

        TvMazeEpisodeResponse newEpisode = new TvMazeEpisodeResponse();
        newEpisode.setId(789);
        newEpisode.setNumber(1);
        newEpisode.setName("Pilot");
        newEpisode.setAirstamp("2024-01-15T21:00:00-05:00");
        newEpisode.setRuntime(60);

        when(seasonRepository.findByShowId(123)).thenReturn(List.of(season));
        when(tvMazeClient.getEpisodes(456)).thenReturn(List.of(newEpisode));

        // When
        listener.handleMessage(messageBody);

        // Then
        verify(sqsService).sendNewEpisode(anyString(), any());
        verify(seasonRepository).updateLastCheckedTimestamp(eq(123), eq(1), anyLong());
    }

    @Test
    void handleMessage_WithTitleChange_EmitsUpdateTitleMessage() {
        // Given
        String messageBody = "{\"type\":\"SHOW_UPDATED\",\"showId\":123}";

        Season season = new Season(123, 1, "Test Show");
        season.setTvmazeSeasonId(456);

        // Add existing episode with old title
        Episode existingEpisode = new Episode(789, 1, "Old Title");
        season.addEpisode(existingEpisode);

        // TVMaze returns same episode with new title
        TvMazeEpisodeResponse updatedEpisode = new TvMazeEpisodeResponse();
        updatedEpisode.setId(789);
        updatedEpisode.setNumber(1);
        updatedEpisode.setName("New Title");
        updatedEpisode.setAirstamp("2024-01-15T21:00:00-05:00");

        when(seasonRepository.findByShowId(123)).thenReturn(List.of(season));
        when(tvMazeClient.getEpisodes(456)).thenReturn(List.of(updatedEpisode));

        // When
        listener.handleMessage(messageBody);

        // Then
        verify(sqsService).sendUpdateTitle("789", "New Title");
        verify(sqsService, never()).sendNewEpisode(anyString(), any());
    }

    @Test
    void handleMessage_WithRemovedEpisode_EmitsRemoveEpisodeMessage() {
        // Given
        String messageBody = "{\"type\":\"SHOW_UPDATED\",\"showId\":123}";

        Season season = new Season(123, 1, "Test Show");
        season.setTvmazeSeasonId(456);

        // Season has an episode
        Episode existingEpisode = new Episode(789, 1, "Episode 1");
        season.addEpisode(existingEpisode);

        // TVMaze returns no episodes (episode was removed)
        when(seasonRepository.findByShowId(123)).thenReturn(List.of(season));
        when(tvMazeClient.getEpisodes(456)).thenReturn(List.of());

        // When
        listener.handleMessage(messageBody);

        // Then
        verify(sqsService).sendRemoveEpisode("789");
    }

    @Test
    void handleMessage_WithInvalidJson_DoesNotCrash() {
        // Given
        String messageBody = "not valid json";

        // When
        listener.handleMessage(messageBody);

        // Then
        verify(seasonRepository, never()).findByShowId(any());
        verify(meterRegistry).counter("watchparty_tvmaze_update_total", "status", "error");
    }

    @Test
    void handleMessage_WithNoChanges_DoesNotEmitMessages() {
        // Given
        String messageBody = "{\"type\":\"SHOW_UPDATED\",\"showId\":123}";

        Season season = new Season(123, 1, "Test Show");
        season.setTvmazeSeasonId(456);

        // Season has an episode
        Episode existingEpisode = new Episode(789, 1, "Episode 1");
        season.addEpisode(existingEpisode);

        // TVMaze returns same episode with same title
        TvMazeEpisodeResponse sameEpisode = new TvMazeEpisodeResponse();
        sameEpisode.setId(789);
        sameEpisode.setNumber(1);
        sameEpisode.setName("Episode 1"); // Same title
        sameEpisode.setAirstamp("2024-01-15T21:00:00-05:00");

        when(seasonRepository.findByShowId(123)).thenReturn(List.of(season));
        when(tvMazeClient.getEpisodes(456)).thenReturn(List.of(sameEpisode));

        // When
        listener.handleMessage(messageBody);

        // Then
        verify(sqsService, never()).sendNewEpisode(anyString(), any());
        verify(sqsService, never()).sendUpdateTitle(anyString(), anyString());
        verify(sqsService, never()).sendRemoveEpisode(anyString());
    }
}
