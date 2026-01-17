package com.bbthechange.inviter.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Episode model.
 */
class EpisodeTest {

    @Test
    void defaultConstructor_ShouldCreateEmptyEpisode() {
        // When
        Episode episode = new Episode();

        // Then
        assertThat(episode.getEpisodeId()).isNull();
        assertThat(episode.getEpisodeNumber()).isNull();
        assertThat(episode.getTitle()).isNull();
        assertThat(episode.getAirTimestamp()).isNull();
        assertThat(episode.getImageUrl()).isNull();
        assertThat(episode.getRuntime()).isNull();
        assertThat(episode.getType()).isNull();
    }

    @Test
    void constructor_WithParameters_ShouldSetFieldsCorrectly() {
        // Given
        Integer episodeId = 12345;
        Integer episodeNumber = 1;
        String title = "Pilot";

        // When
        Episode episode = new Episode(episodeId, episodeNumber, title);

        // Then
        assertThat(episode.getEpisodeId()).isEqualTo(episodeId);
        assertThat(episode.getEpisodeNumber()).isEqualTo(episodeNumber);
        assertThat(episode.getTitle()).isEqualTo(title);
        assertThat(episode.getType()).isEqualTo("regular"); // Default type
    }

    @Test
    void settersAndGetters_ShouldWorkCorrectly() {
        // Given
        Episode episode = new Episode();

        // When
        episode.setEpisodeId(100);
        episode.setEpisodeNumber(5);
        episode.setTitle("The Episode");
        episode.setAirTimestamp(1609459200L);
        episode.setImageUrl("https://example.com/image.jpg");
        episode.setRuntime(45);
        episode.setType("significant_special");

        // Then
        assertThat(episode.getEpisodeId()).isEqualTo(100);
        assertThat(episode.getEpisodeNumber()).isEqualTo(5);
        assertThat(episode.getTitle()).isEqualTo("The Episode");
        assertThat(episode.getAirTimestamp()).isEqualTo(1609459200L);
        assertThat(episode.getImageUrl()).isEqualTo("https://example.com/image.jpg");
        assertThat(episode.getRuntime()).isEqualTo(45);
        assertThat(episode.getType()).isEqualTo("significant_special");
    }

    @Test
    void isRegular_WithRegularType_ShouldReturnTrue() {
        // Given
        Episode episode = new Episode(1, 1, "Test");

        // Then
        assertThat(episode.isRegular()).isTrue();
        assertThat(episode.isSignificantSpecial()).isFalse();
    }

    @Test
    void isRegular_WithSpecialType_ShouldReturnFalse() {
        // Given
        Episode episode = new Episode();
        episode.setType("significant_special");

        // Then
        assertThat(episode.isRegular()).isFalse();
        assertThat(episode.isSignificantSpecial()).isTrue();
    }

    @Test
    void isRegular_WithNullType_ShouldReturnFalse() {
        // Given
        Episode episode = new Episode();
        episode.setType(null);

        // Then
        assertThat(episode.isRegular()).isFalse();
        assertThat(episode.isSignificantSpecial()).isFalse();
    }
}
