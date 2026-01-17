package com.bbthechange.inviter.dto.watchparty;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CreateWatchPartyRequest.shouldFetchFromTvMaze() method.
 *
 * The method returns true if:
 * - tvmazeSeasonId is not null AND
 * - episodes is null or empty
 */
class CreateWatchPartyRequestTest {

    @Test
    void shouldFetchFromTvMaze_WithTvMazeSeasonIdAndNoEpisodes_ReturnsTrue() {
        // Given
        CreateWatchPartyRequest request = CreateWatchPartyRequest.builder()
                .tvmazeSeasonId(83)
                .episodes(null)
                .build();

        // When
        boolean result = request.shouldFetchFromTvMaze();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void shouldFetchFromTvMaze_WithTvMazeSeasonIdAndEmptyEpisodes_ReturnsTrue() {
        // Given
        CreateWatchPartyRequest request = CreateWatchPartyRequest.builder()
                .tvmazeSeasonId(83)
                .episodes(Collections.emptyList())
                .build();

        // When
        boolean result = request.shouldFetchFromTvMaze();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void shouldFetchFromTvMaze_WithTvMazeSeasonIdAndEpisodes_ReturnsFalse() {
        // Given
        CreateWatchPartyEpisodeRequest episode = CreateWatchPartyEpisodeRequest.builder()
                .episodeId(1001)
                .episodeNumber(1)
                .title("Pilot")
                .airTimestamp(1700000000L)
                .runtime(60)
                .build();

        CreateWatchPartyRequest request = CreateWatchPartyRequest.builder()
                .tvmazeSeasonId(83)
                .episodes(List.of(episode))
                .build();

        // When
        boolean result = request.shouldFetchFromTvMaze();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void shouldFetchFromTvMaze_WithNoTvMazeSeasonId_ReturnsFalse() {
        // Given
        CreateWatchPartyRequest request = CreateWatchPartyRequest.builder()
                .tvmazeSeasonId(null)
                .episodes(null)
                .build();

        // When
        boolean result = request.shouldFetchFromTvMaze();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void shouldFetchFromTvMaze_WithOnlyEpisodes_ReturnsFalse() {
        // Given
        CreateWatchPartyEpisodeRequest episode = CreateWatchPartyEpisodeRequest.builder()
                .episodeId(1001)
                .episodeNumber(1)
                .title("Pilot")
                .airTimestamp(1700000000L)
                .runtime(60)
                .build();

        CreateWatchPartyRequest request = CreateWatchPartyRequest.builder()
                .tvmazeSeasonId(null)
                .episodes(List.of(episode))
                .build();

        // When
        boolean result = request.shouldFetchFromTvMaze();

        // Then
        assertThat(result).isFalse();
    }
}
