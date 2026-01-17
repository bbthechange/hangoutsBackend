package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.client.TvMazeClient;
import com.bbthechange.inviter.dto.watchparty.PollResult;
import com.bbthechange.inviter.dto.watchparty.sqs.ShowUpdatedMessage;
import com.bbthechange.inviter.dto.watchparty.sqs.WatchPartyMessage;
import com.bbthechange.inviter.exception.TvMazeException;
import com.bbthechange.inviter.repository.SeasonRepository;
import com.bbthechange.inviter.service.WatchPartySqsService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TvMazePollingServiceImplTest {

    @Mock
    private TvMazeClient tvMazeClient;

    @Mock
    private SeasonRepository seasonRepository;

    @Mock
    private WatchPartySqsService sqsService;

    private MeterRegistry meterRegistry;
    private TvMazePollingServiceImpl pollingService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        // Use short cache TTL (0) for testing to avoid cache issues
        pollingService = new TvMazePollingServiceImpl(
                tvMazeClient,
                seasonRepository,
                sqsService,
                meterRegistry,
                "day",
                0L  // No caching for tests
        );
    }

    @Nested
    @DisplayName("pollForUpdates")
    class PollForUpdatesTests {

        @Test
        @DisplayName("should emit messages for tracked shows with updates")
        void pollForUpdates_WithTrackedShowsUpdated_EmitsMessages() {
            // Given
            Set<Integer> trackedShows = Set.of(100, 200, 300);
            Map<Integer, Long> tvMazeUpdates = Map.of(
                    100, 1700000000L,  // Tracked show updated
                    200, 1700000001L,  // Tracked show updated
                    999, 1700000002L   // Not tracked
            );

            when(seasonRepository.findAllDistinctShowIds()).thenReturn(trackedShows);
            when(tvMazeClient.getShowUpdates("day")).thenReturn(tvMazeUpdates);

            // When
            PollResult result = pollingService.pollForUpdates();

            // Then
            assertThat(result.getTotalTrackedShows()).isEqualTo(3);
            assertThat(result.getUpdatedShowsFound()).isEqualTo(2);
            assertThat(result.getMessagesEmitted()).isEqualTo(2);
            assertThat(result.getDurationMs()).isGreaterThanOrEqualTo(0);

            // Verify SQS messages were sent
            ArgumentCaptor<WatchPartyMessage> messageCaptor = ArgumentCaptor.forClass(WatchPartyMessage.class);
            verify(sqsService, times(2)).sendToTvMazeUpdatesQueue(messageCaptor.capture());

            assertThat(messageCaptor.getAllValues())
                    .extracting(m -> ((ShowUpdatedMessage) m).getShowId())
                    .containsExactlyInAnyOrder(100, 200);
        }

        @Test
        @DisplayName("should skip poll when no tracked shows")
        void pollForUpdates_NoTrackedShows_SkipsPoll() {
            // Given
            when(seasonRepository.findAllDistinctShowIds()).thenReturn(Collections.emptySet());

            // When
            PollResult result = pollingService.pollForUpdates();

            // Then
            assertThat(result.getTotalTrackedShows()).isEqualTo(0);
            assertThat(result.getUpdatedShowsFound()).isEqualTo(0);
            assertThat(result.getMessagesEmitted()).isEqualTo(0);

            // Verify TVMaze API was NOT called
            verifyNoInteractions(tvMazeClient);
            verifyNoInteractions(sqsService);
        }

        @Test
        @DisplayName("should emit no messages when no intersection")
        void pollForUpdates_NoIntersection_EmitsNoMessages() {
            // Given
            Set<Integer> trackedShows = Set.of(100, 200, 300);
            Map<Integer, Long> tvMazeUpdates = Map.of(
                    500, 1700000000L,
                    600, 1700000001L
            );

            when(seasonRepository.findAllDistinctShowIds()).thenReturn(trackedShows);
            when(tvMazeClient.getShowUpdates("day")).thenReturn(tvMazeUpdates);

            // When
            PollResult result = pollingService.pollForUpdates();

            // Then
            assertThat(result.getTotalTrackedShows()).isEqualTo(3);
            assertThat(result.getUpdatedShowsFound()).isEqualTo(0);
            assertThat(result.getMessagesEmitted()).isEqualTo(0);

            verifyNoInteractions(sqsService);
        }

        @Test
        @DisplayName("should propagate TvMazeException")
        void pollForUpdates_TvMazeError_PropagatesException() {
            // Given
            Set<Integer> trackedShows = Set.of(100, 200, 300);
            when(seasonRepository.findAllDistinctShowIds()).thenReturn(trackedShows);
            when(tvMazeClient.getShowUpdates("day")).thenThrow(TvMazeException.serviceUnavailable("API error"));

            // When/Then
            assertThatThrownBy(() -> pollingService.pollForUpdates())
                    .isInstanceOf(TvMazeException.class)
                    .hasMessageContaining("API error");
        }

        @Test
        @DisplayName("should continue emitting messages even if one fails")
        void pollForUpdates_PartialSqsFailure_ContinuesWithOthers() {
            // Given
            Set<Integer> trackedShows = Set.of(100, 200, 300);
            Map<Integer, Long> tvMazeUpdates = Map.of(
                    100, 1700000000L,
                    200, 1700000001L,
                    300, 1700000002L
            );

            when(seasonRepository.findAllDistinctShowIds()).thenReturn(trackedShows);
            when(tvMazeClient.getShowUpdates("day")).thenReturn(tvMazeUpdates);

            // First call succeeds, second fails, third succeeds
            doNothing()
                    .doThrow(new RuntimeException("SQS error"))
                    .doNothing()
                    .when(sqsService).sendToTvMazeUpdatesQueue(any());

            // When
            PollResult result = pollingService.pollForUpdates();

            // Then - 2 successful, 1 failed
            assertThat(result.getUpdatedShowsFound()).isEqualTo(3);
            assertThat(result.getMessagesEmitted()).isEqualTo(2);

            verify(sqsService, times(3)).sendToTvMazeUpdatesQueue(any());
        }

        @Test
        @DisplayName("should handle all tracked shows updated")
        void pollForUpdates_AllTrackedShowsUpdated_EmitsAllMessages() {
            // Given
            Set<Integer> trackedShows = Set.of(100, 200);
            Map<Integer, Long> tvMazeUpdates = Map.of(
                    100, 1700000000L,
                    200, 1700000001L
            );

            when(seasonRepository.findAllDistinctShowIds()).thenReturn(trackedShows);
            when(tvMazeClient.getShowUpdates("day")).thenReturn(tvMazeUpdates);

            // When
            PollResult result = pollingService.pollForUpdates();

            // Then
            assertThat(result.getTotalTrackedShows()).isEqualTo(2);
            assertThat(result.getUpdatedShowsFound()).isEqualTo(2);
            assertThat(result.getMessagesEmitted()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("caching")
    class CachingTests {

        @Test
        @DisplayName("should use cached tracked shows within TTL")
        void pollForUpdates_CachesTrackedShows_ReducesScans() {
            // Given - Use service with 1 hour cache TTL
            pollingService = new TvMazePollingServiceImpl(
                    tvMazeClient,
                    seasonRepository,
                    sqsService,
                    meterRegistry,
                    "day",
                    3600000L  // 1 hour cache
            );

            Set<Integer> trackedShows = Set.of(100, 200);
            Map<Integer, Long> tvMazeUpdates = Map.of(100, 1700000000L);

            when(seasonRepository.findAllDistinctShowIds()).thenReturn(trackedShows);
            when(tvMazeClient.getShowUpdates("day")).thenReturn(tvMazeUpdates);

            // When - Poll twice
            pollingService.pollForUpdates();
            pollingService.pollForUpdates();

            // Then - Repository should only be called once due to caching
            verify(seasonRepository, times(1)).findAllDistinctShowIds();
            verify(tvMazeClient, times(2)).getShowUpdates("day");
        }

        @Test
        @DisplayName("should invalidate cache when requested")
        void invalidateCache_ShouldRefreshOnNextPoll() {
            // Given - Use service with 1 hour cache TTL
            pollingService = new TvMazePollingServiceImpl(
                    tvMazeClient,
                    seasonRepository,
                    sqsService,
                    meterRegistry,
                    "day",
                    3600000L  // 1 hour cache
            );

            Set<Integer> trackedShows = Set.of(100);
            Map<Integer, Long> tvMazeUpdates = Map.of(100, 1700000000L);

            when(seasonRepository.findAllDistinctShowIds()).thenReturn(trackedShows);
            when(tvMazeClient.getShowUpdates("day")).thenReturn(tvMazeUpdates);

            // When - Poll, invalidate, poll again
            pollingService.pollForUpdates();
            pollingService.invalidateCache();
            pollingService.pollForUpdates();

            // Then - Repository should be called twice (once per poll)
            verify(seasonRepository, times(2)).findAllDistinctShowIds();
        }
    }

    @Nested
    @DisplayName("metrics")
    class MetricsTests {

        @Test
        @DisplayName("should record success metrics")
        void pollForUpdates_Success_RecordsMetrics() {
            // Given
            Set<Integer> trackedShows = Set.of(100);
            Map<Integer, Long> tvMazeUpdates = Map.of(100, 1700000000L);

            when(seasonRepository.findAllDistinctShowIds()).thenReturn(trackedShows);
            when(tvMazeClient.getShowUpdates("day")).thenReturn(tvMazeUpdates);

            // When
            pollingService.pollForUpdates();

            // Then
            assertThat(meterRegistry.counter("watchparty_poll_total", "status", "success").count())
                    .isEqualTo(1.0);
            assertThat(meterRegistry.timer("watchparty_poll_duration", "status", "success").count())
                    .isEqualTo(1L);
        }

        @Test
        @DisplayName("should record error metrics on failure")
        void pollForUpdates_Error_RecordsMetrics() {
            // Given
            when(seasonRepository.findAllDistinctShowIds()).thenReturn(Set.of(100));
            when(tvMazeClient.getShowUpdates("day")).thenThrow(TvMazeException.serviceUnavailable("API error"));

            // When/Then
            assertThatThrownBy(() -> pollingService.pollForUpdates());

            assertThat(meterRegistry.counter("watchparty_poll_total", "status", "error").count())
                    .isEqualTo(1.0);
        }

        @Test
        @DisplayName("should record skipped metrics when no tracked shows")
        void pollForUpdates_Skipped_RecordsMetrics() {
            // Given
            when(seasonRepository.findAllDistinctShowIds()).thenReturn(Collections.emptySet());

            // When
            pollingService.pollForUpdates();

            // Then
            assertThat(meterRegistry.counter("watchparty_poll_total", "status", "skipped").count())
                    .isEqualTo(1.0);
        }
    }
}
