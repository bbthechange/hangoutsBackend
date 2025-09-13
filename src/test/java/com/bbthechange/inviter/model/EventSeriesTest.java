package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InviterKeyFactory;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for EventSeries model.
 */
class EventSeriesTest {

    @Test
    void constructor_WithValidParameters_ShouldCreateEventSeriesWithCorrectKeys() {
        // Given
        String title = "Concert Night";
        String description = "A multi-part concert event";
        String groupId = "550e8400-e29b-41d4-a716-446655440000";

        // When
        EventSeries series = new EventSeries(title, description, groupId);

        // Then
        assertThat(series.getSeriesTitle()).isEqualTo(title);
        assertThat(series.getSeriesDescription()).isEqualTo(description);
        assertThat(series.getGroupId()).isEqualTo(groupId);
        assertThat(series.getSeriesId()).isNotNull();
        assertThat(series.getVersion()).isEqualTo(1L);
        assertThat(series.getHangoutIds()).isEmpty();
        assertThat(series.getItemType()).isEqualTo("EVENT_SERIES");
        
        // Verify keys are set correctly
        assertThat(series.getPk()).isEqualTo("SERIES#" + series.getSeriesId());
        assertThat(series.getSk()).isEqualTo("METADATA");
        assertThat(series.getGsi1pk()).isEqualTo("GROUP#" + groupId);
    }

    @Test
    void defaultConstructor_ShouldCreateEventSeriesWithDefaults() {
        // When
        EventSeries series = new EventSeries();

        // Then
        assertThat(series.getItemType()).isEqualTo("EVENT_SERIES");
        assertThat(series.getVersion()).isEqualTo(1L);
        assertThat(series.getHangoutIds()).isEmpty();
        assertThat(series.getCreatedAt()).isNotNull();
        assertThat(series.getUpdatedAt()).isNotNull();
    }

    @Test
    void addHangout_WithNewHangoutId_ShouldAddToList() {
        // Given
        EventSeries series = new EventSeries();
        String hangoutId = "123e4567-e89b-12d3-a456-426614174000";

        // When
        series.addHangout(hangoutId);

        // Then
        assertThat(series.getHangoutIds()).containsExactly(hangoutId);
        assertThat(series.containsHangout(hangoutId)).isTrue();
    }

    @Test
    void addHangout_WithDuplicateHangoutId_ShouldNotAddDuplicate() {
        // Given
        EventSeries series = new EventSeries();
        String hangoutId = "123e4567-e89b-12d3-a456-426614174000";
        series.addHangout(hangoutId);

        // When
        series.addHangout(hangoutId); // Add same ID again

        // Then
        assertThat(series.getHangoutIds()).hasSize(1);
        assertThat(series.getHangoutIds()).containsExactly(hangoutId);
    }

    @Test
    void removeHangout_WithExistingHangoutId_ShouldRemoveFromList() {
        // Given
        EventSeries series = new EventSeries();
        String hangoutId = "123e4567-e89b-12d3-a456-426614174000";
        series.addHangout(hangoutId);

        // When
        series.removeHangout(hangoutId);

        // Then
        assertThat(series.getHangoutIds()).isEmpty();
        assertThat(series.containsHangout(hangoutId)).isFalse();
    }

    @Test
    void removeHangout_WithNonExistentHangoutId_ShouldNotThrowException() {
        // Given
        EventSeries series = new EventSeries();
        String hangoutId = "123e4567-e89b-12d3-a456-426614174000";

        // When & Then (should not throw)
        assertThatCode(() -> series.removeHangout(hangoutId))
            .doesNotThrowAnyException();
    }

    @Test
    void getHangoutCount_WithMultipleHangouts_ShouldReturnCorrectCount() {
        // Given
        EventSeries series = new EventSeries();
        series.setHangoutIds(Arrays.asList("hangout1", "hangout2", "hangout3"));

        // When
        int count = series.getHangoutCount();

        // Then
        assertThat(count).isEqualTo(3);
    }

    @Test
    void getHangoutCount_WithEmptyList_ShouldReturnZero() {
        // Given
        EventSeries series = new EventSeries();

        // When
        int count = series.getHangoutCount();

        // Then
        assertThat(count).isEqualTo(0);
    }

    @Test
    void incrementVersion_ShouldIncrementVersionNumber() {
        // Given
        EventSeries series = new EventSeries();
        Long initialVersion = series.getVersion();

        // When
        series.incrementVersion();

        // Then
        assertThat(series.getVersion()).isEqualTo(initialVersion + 1);
    }

    @Test
    void setSeriesTitle_ShouldUpdateTitleAndTimestamp() {
        // Given
        EventSeries series = new EventSeries();
        String newTitle = "Updated Concert Night";
        
        // When
        series.setSeriesTitle(newTitle);

        // Then
        assertThat(series.getSeriesTitle()).isEqualTo(newTitle);
        assertThat(series.getUpdatedAt()).isNotNull();
    }

    @Test
    void setStartTimestamp_ShouldSetTimestampForGSI() {
        // Given
        EventSeries series = new EventSeries();
        Long timestamp = 1609459200L; // Example timestamp

        // When
        series.setStartTimestamp(timestamp);

        // Then
        assertThat(series.getStartTimestamp()).isEqualTo(timestamp);
    }
}