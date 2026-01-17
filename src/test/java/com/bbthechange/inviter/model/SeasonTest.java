package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InviterKeyFactory;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Season model.
 */
class SeasonTest {

    @Test
    void defaultConstructor_ShouldCreateSeasonWithDefaults() {
        // When
        Season season = new Season();

        // Then
        assertThat(season.getItemType()).isEqualTo("SEASON");
        assertThat(season.getEpisodes()).isEmpty();
        assertThat(season.getCreatedAt()).isNotNull();
        assertThat(season.getUpdatedAt()).isNotNull();
    }

    @Test
    void constructor_WithParameters_ShouldSetFieldsAndKeys() {
        // Given
        Integer showId = 123;
        Integer seasonNumber = 2;
        String showName = "Breaking Bad";

        // When
        Season season = new Season(showId, seasonNumber, showName);

        // Then
        assertThat(season.getShowId()).isEqualTo(showId);
        assertThat(season.getSeasonNumber()).isEqualTo(seasonNumber);
        assertThat(season.getShowName()).isEqualTo(showName);
        assertThat(season.getItemType()).isEqualTo("SEASON");
        assertThat(season.getEpisodes()).isEmpty();

        // Verify keys are set correctly
        assertThat(season.getPk()).isEqualTo("TVMAZE#SHOW#123");
        assertThat(season.getSk()).isEqualTo("SEASON#2");
        // ExternalIdIndex uses externalId=showId and externalSource="TVMAZE"
        assertThat(season.getExternalId()).isEqualTo("123");
        assertThat(season.getExternalSource()).isEqualTo("TVMAZE");
    }

    @Test
    void addEpisode_ShouldAddToEpisodesList() {
        // Given
        Season season = new Season(123, 1, "Test Show");
        Episode episode = new Episode(1, 1, "Pilot");

        // When
        season.addEpisode(episode);

        // Then
        assertThat(season.getEpisodes()).hasSize(1);
        assertThat(season.getEpisodes().get(0).getTitle()).isEqualTo("Pilot");
        assertThat(season.getEpisodeCount()).isEqualTo(1);
    }

    @Test
    void findEpisodeById_WithExistingEpisode_ShouldReturnEpisode() {
        // Given
        Season season = new Season(123, 1, "Test Show");
        Episode episode1 = new Episode(100, 1, "Episode 1");
        Episode episode2 = new Episode(101, 2, "Episode 2");
        season.setEpisodes(Arrays.asList(episode1, episode2));

        // When
        Optional<Episode> found = season.findEpisodeById(101);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Episode 2");
    }

    @Test
    void findEpisodeById_WithNonExistingEpisode_ShouldReturnEmpty() {
        // Given
        Season season = new Season(123, 1, "Test Show");
        Episode episode = new Episode(100, 1, "Episode 1");
        season.addEpisode(episode);

        // When
        Optional<Episode> found = season.findEpisodeById(999);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void findEpisodeById_WithNullEpisodes_ShouldReturnEmpty() {
        // Given
        Season season = new Season();
        season.setEpisodes(null);

        // When
        Optional<Episode> found = season.findEpisodeById(100);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void findEpisodeByNumber_WithExistingEpisode_ShouldReturnEpisode() {
        // Given
        Season season = new Season(123, 1, "Test Show");
        Episode episode = new Episode(100, 5, "Episode 5");
        season.addEpisode(episode);

        // When
        Optional<Episode> found = season.findEpisodeByNumber(5);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Episode 5");
    }

    @Test
    void getEpisodeCount_WithMultipleEpisodes_ShouldReturnCount() {
        // Given
        Season season = new Season(123, 1, "Test Show");
        season.addEpisode(new Episode(1, 1, "Ep 1"));
        season.addEpisode(new Episode(2, 2, "Ep 2"));
        season.addEpisode(new Episode(3, 3, "Ep 3"));

        // Then
        assertThat(season.getEpisodeCount()).isEqualTo(3);
    }

    @Test
    void getEpisodeCount_WithNullEpisodes_ShouldReturnZero() {
        // Given
        Season season = new Season();
        season.setEpisodes(null);

        // Then
        assertThat(season.getEpisodeCount()).isEqualTo(0);
    }

    @Test
    void touch_ShouldUpdateTimestamp() throws InterruptedException {
        // Given
        Season season = new Season(123, 1, "Test Show");
        java.time.Instant initialTimestamp = season.getUpdatedAt();

        // Small delay
        Thread.sleep(10);

        // When
        season.touch();

        // Then
        assertThat(season.getUpdatedAt()).isAfter(initialTimestamp);
    }

    @Test
    void isFresh_WithRecentTimestamp_ShouldReturnTrue() {
        // Given
        Season season = new Season(123, 1, "Test Show");
        long currentTime = System.currentTimeMillis();
        season.setLastCheckedTimestamp(currentTime - 1000); // 1 second ago

        // When
        boolean fresh = season.isFresh(60000); // 60 seconds max age

        // Then
        assertThat(fresh).isTrue();
    }

    @Test
    void isFresh_WithOldTimestamp_ShouldReturnFalse() {
        // Given
        Season season = new Season(123, 1, "Test Show");
        long currentTime = System.currentTimeMillis();
        season.setLastCheckedTimestamp(currentTime - 120000); // 2 minutes ago

        // When
        boolean fresh = season.isFresh(60000); // 60 seconds max age

        // Then
        assertThat(fresh).isFalse();
    }

    @Test
    void isFresh_WithNullTimestamp_ShouldReturnFalse() {
        // Given
        Season season = new Season(123, 1, "Test Show");
        season.setLastCheckedTimestamp(null);

        // When
        boolean fresh = season.isFresh(60000);

        // Then
        assertThat(fresh).isFalse();
    }

    @Test
    void getSeasonReference_ShouldReturnCorrectFormat() {
        // Given
        Season season = new Season(123, 2, "Test Show");

        // When
        String reference = season.getSeasonReference();

        // Then
        assertThat(reference).isEqualTo("TVMAZE#SHOW#123|SEASON#2");
    }

    @Test
    void setShowName_ShouldUpdateTimestamp() throws InterruptedException {
        // Given
        Season season = new Season(123, 1, "Test Show");
        java.time.Instant initialTimestamp = season.getUpdatedAt();

        Thread.sleep(10);

        // When
        season.setShowName("New Name");

        // Then
        assertThat(season.getShowName()).isEqualTo("New Name");
        assertThat(season.getUpdatedAt()).isAfter(initialTimestamp);
    }

    @Test
    void setOptionalFields_ShouldWorkCorrectly() {
        // Given
        Season season = new Season(123, 1, "Test Show");

        // When
        season.setSeasonImageUrl("https://example.com/season.jpg");
        season.setTvmazeSeasonId(456);
        season.setEndDate(1700000000L);

        // Then
        assertThat(season.getSeasonImageUrl()).isEqualTo("https://example.com/season.jpg");
        assertThat(season.getTvmazeSeasonId()).isEqualTo(456);
        assertThat(season.getEndDate()).isEqualTo(1700000000L);
    }
}
