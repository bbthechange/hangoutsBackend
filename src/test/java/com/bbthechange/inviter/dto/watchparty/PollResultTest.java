package com.bbthechange.inviter.dto.watchparty;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PollResult DTO.
 *
 * Tests the static factory methods and setters/getters for this result object
 * used by TVMaze polling operations.
 */
@ExtendWith(MockitoExtension.class)
class PollResultTest {

    @Test
    void success_ShouldSetAllFieldsCorrectly() {
        // Given
        int totalTrackedShows = 10;
        int updatedShowsFound = 3;
        int messagesEmitted = 3;
        long durationMs = 1500L;

        // When
        PollResult result = PollResult.success(totalTrackedShows, updatedShowsFound, messagesEmitted, durationMs);

        // Then
        assertThat(result.getTotalTrackedShows()).isEqualTo(10);
        assertThat(result.getUpdatedShowsFound()).isEqualTo(3);
        assertThat(result.getMessagesEmitted()).isEqualTo(3);
        assertThat(result.getDurationMs()).isEqualTo(1500L);
    }

    @Test
    void noTrackedShows_ShouldSetZeroCountsWithDuration() {
        // Given
        long durationMs = 500L;

        // When
        PollResult result = PollResult.noTrackedShows(durationMs);

        // Then
        assertThat(result.getTotalTrackedShows()).isZero();
        assertThat(result.getUpdatedShowsFound()).isZero();
        assertThat(result.getMessagesEmitted()).isZero();
        assertThat(result.getDurationMs()).isEqualTo(500L);
    }

    @Test
    void defaultConstructor_ShouldInitializeWithDefaults() {
        // When
        PollResult result = new PollResult();

        // Then
        assertThat(result.getTotalTrackedShows()).isZero();
        assertThat(result.getUpdatedShowsFound()).isZero();
        assertThat(result.getMessagesEmitted()).isZero();
        assertThat(result.getDurationMs()).isZero();
    }

    @Test
    void setters_ShouldUpdateFields() {
        // Given
        PollResult result = new PollResult();

        // When
        result.setTotalTrackedShows(15);
        result.setUpdatedShowsFound(5);
        result.setMessagesEmitted(4);
        result.setDurationMs(2500L);

        // Then
        assertThat(result.getTotalTrackedShows()).isEqualTo(15);
        assertThat(result.getUpdatedShowsFound()).isEqualTo(5);
        assertThat(result.getMessagesEmitted()).isEqualTo(4);
        assertThat(result.getDurationMs()).isEqualTo(2500L);
    }

    @Test
    void toString_ShouldContainAllFields() {
        // Given
        PollResult result = PollResult.success(5, 2, 2, 1000L);

        // When
        String stringRepresentation = result.toString();

        // Then
        assertThat(stringRepresentation).contains("totalTrackedShows");
        assertThat(stringRepresentation).contains("5");
        assertThat(stringRepresentation).contains("updatedShowsFound");
        assertThat(stringRepresentation).contains("2");
        assertThat(stringRepresentation).contains("messagesEmitted");
        assertThat(stringRepresentation).contains("durationMs");
        assertThat(stringRepresentation).contains("1000");
    }
}
