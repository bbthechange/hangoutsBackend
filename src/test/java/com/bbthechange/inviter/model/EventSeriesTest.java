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

    // ============================================================================
    // WATCH PARTY FIELDS TESTS
    // ============================================================================

    @Test
    void defaultConstructor_ShouldInitializeDeletedEpisodeIds() {
        // When
        EventSeries series = new EventSeries();

        // Then
        assertThat(series.getDeletedEpisodeIds()).isNotNull().isEmpty();
    }

    @Test
    void parameterizedConstructor_ShouldInitializeDeletedEpisodeIds() {
        // Given
        String groupId = "550e8400-e29b-41d4-a716-446655440000";

        // When
        EventSeries series = new EventSeries("Title", "Description", groupId);

        // Then
        assertThat(series.getDeletedEpisodeIds()).isNotNull().isEmpty();
    }

    @Test
    void isWatchParty_WithWatchPartyType_ShouldReturnTrue() {
        // Given
        EventSeries series = new EventSeries();
        series.setEventSeriesType("WATCH_PARTY");

        // Then
        assertThat(series.isWatchParty()).isTrue();
    }

    @Test
    void isWatchParty_WithNullType_ShouldReturnFalse() {
        // Given
        EventSeries series = new EventSeries();
        series.setEventSeriesType(null);

        // Then
        assertThat(series.isWatchParty()).isFalse();
    }

    @Test
    void isWatchParty_WithOtherType_ShouldReturnFalse() {
        // Given
        EventSeries series = new EventSeries();
        series.setEventSeriesType("REGULAR");

        // Then
        assertThat(series.isWatchParty()).isFalse();
    }

    @Test
    void addDeletedEpisodeId_ShouldAddToSet() {
        // Given
        EventSeries series = new EventSeries();
        String episodeId = "episode-123";

        // When
        series.addDeletedEpisodeId(episodeId);

        // Then
        assertThat(series.getDeletedEpisodeIds()).contains(episodeId);
        assertThat(series.isEpisodeDeleted(episodeId)).isTrue();
    }

    @Test
    void addDeletedEpisodeId_WithNullSet_ShouldInitializeAndAdd() {
        // Given
        EventSeries series = new EventSeries();
        series.setDeletedEpisodeIds(null);

        // When
        series.addDeletedEpisodeId("episode-456");

        // Then
        assertThat(series.getDeletedEpisodeIds()).contains("episode-456");
    }

    @Test
    void isEpisodeDeleted_WithNonDeletedEpisode_ShouldReturnFalse() {
        // Given
        EventSeries series = new EventSeries();
        series.addDeletedEpisodeId("episode-123");

        // Then
        assertThat(series.isEpisodeDeleted("episode-456")).isFalse();
    }

    @Test
    void isEpisodeDeleted_WithNullSet_ShouldReturnFalse() {
        // Given
        EventSeries series = new EventSeries();
        series.setDeletedEpisodeIds(null);

        // Then
        assertThat(series.isEpisodeDeleted("episode-123")).isFalse();
    }

    @Test
    void removeDeletedEpisodeId_ShouldRemoveFromSet() {
        // Given
        EventSeries series = new EventSeries();
        series.addDeletedEpisodeId("episode-123");
        assertThat(series.isEpisodeDeleted("episode-123")).isTrue();

        // When
        series.removeDeletedEpisodeId("episode-123");

        // Then
        assertThat(series.isEpisodeDeleted("episode-123")).isFalse();
    }

    @Test
    void setWatchPartyFields_ShouldWorkCorrectly() {
        // Given
        EventSeries series = new EventSeries();

        // When
        series.setEventSeriesType("WATCH_PARTY");
        series.setSeasonId("TVMAZE#SHOW#123|SEASON#2");
        series.setDefaultHostId("host-user-id");
        series.setDefaultTime("19:30");
        series.setDayOverride(5); // Friday
        series.setTimezone("America/Los_Angeles");

        // Then
        assertThat(series.getEventSeriesType()).isEqualTo("WATCH_PARTY");
        assertThat(series.getSeasonId()).isEqualTo("TVMAZE#SHOW#123|SEASON#2");
        assertThat(series.getDefaultHostId()).isEqualTo("host-user-id");
        assertThat(series.getDefaultTime()).isEqualTo("19:30");
        assertThat(series.getDayOverride()).isEqualTo(5);
        assertThat(series.getTimezone()).isEqualTo("America/Los_Angeles");
        assertThat(series.isWatchParty()).isTrue();
    }

    @Test
    void setDeletedEpisodeIds_WithNull_ShouldInitializeEmptySet() {
        // Given
        EventSeries series = new EventSeries();

        // When
        series.setDeletedEpisodeIds(null);

        // Then
        assertThat(series.getDeletedEpisodeIds()).isNotNull().isEmpty();
    }
}