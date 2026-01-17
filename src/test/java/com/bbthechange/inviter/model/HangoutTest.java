package com.bbthechange.inviter.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for Hangout model, focusing on reminder-related fields.
 *
 * Coverage:
 * - reminderScheduleName getter/setter
 * - reminderSentAt getter/setter
 * - Default null values for new hangouts
 * - touch() behavior on setters
 */
class HangoutTest {

    @Test
    void testReminderScheduleName_SetAndGet() {
        // Given
        Hangout hangout = new Hangout();
        String expectedScheduleName = "hangout-abc123";

        // When
        hangout.setReminderScheduleName(expectedScheduleName);

        // Then
        assertThat(hangout.getReminderScheduleName()).isEqualTo("hangout-abc123");
    }

    @Test
    void testReminderSentAt_SetAndGet() {
        // Given
        Hangout hangout = new Hangout();
        Long expectedTimestamp = 1700000000000L;

        // When
        hangout.setReminderSentAt(expectedTimestamp);

        // Then
        assertThat(hangout.getReminderSentAt()).isEqualTo(1700000000000L);
    }

    @Test
    void testReminderScheduleName_NullByDefault() {
        // Given
        Hangout hangout = new Hangout();

        // Then
        assertThat(hangout.getReminderScheduleName()).isNull();
    }

    @Test
    void testReminderSentAt_NullByDefault() {
        // Given
        Hangout hangout = new Hangout();

        // Then
        assertThat(hangout.getReminderSentAt()).isNull();
    }

    @Test
    void testSetReminderScheduleName_UpdatesTimestamp() throws InterruptedException {
        // Given
        Hangout hangout = new Hangout();
        Instant initialUpdatedAt = hangout.getUpdatedAt();

        // Small delay to ensure timestamp difference
        Thread.sleep(5);

        // When
        hangout.setReminderScheduleName("hangout-xyz789");

        // Then
        assertThat(hangout.getUpdatedAt()).isAfter(initialUpdatedAt);
    }

    @Test
    void testSetReminderSentAt_UpdatesTimestamp() throws InterruptedException {
        // Given
        Hangout hangout = new Hangout();
        Instant initialUpdatedAt = hangout.getUpdatedAt();

        // Small delay to ensure timestamp difference
        Thread.sleep(5);

        // When
        hangout.setReminderSentAt(System.currentTimeMillis());

        // Then
        assertThat(hangout.getUpdatedAt()).isAfter(initialUpdatedAt);
    }

    // ============================================================================
    // WATCH PARTY FIELDS TESTS
    // ============================================================================

    @Test
    void defaultConstructor_ShouldInitializeCombinedExternalIds() {
        // When
        Hangout hangout = new Hangout();

        // Then
        assertThat(hangout.getCombinedExternalIds()).isNotNull().isEmpty();
    }

    @Test
    void testTitleNotificationSent_SetAndGet() {
        // Given
        Hangout hangout = new Hangout();

        // When
        hangout.setTitleNotificationSent(true);

        // Then
        assertThat(hangout.getTitleNotificationSent()).isTrue();
    }

    @Test
    void testTitleNotificationSent_NullByDefault() {
        // Given
        Hangout hangout = new Hangout();

        // Then
        assertThat(hangout.getTitleNotificationSent()).isNull();
    }

    @Test
    void hasCombinedEpisodes_WithEmptyList_ShouldReturnFalse() {
        // Given
        Hangout hangout = new Hangout();

        // Then
        assertThat(hangout.hasCombinedEpisodes()).isFalse();
    }

    @Test
    void hasCombinedEpisodes_WithNullList_ShouldReturnFalse() {
        // Given
        Hangout hangout = new Hangout();
        hangout.setCombinedExternalIds(null);

        // Then
        assertThat(hangout.hasCombinedEpisodes()).isFalse();
    }

    @Test
    void hasCombinedEpisodes_WithItems_ShouldReturnTrue() {
        // Given
        Hangout hangout = new Hangout();
        hangout.addCombinedExternalId("episode-1");

        // Then
        assertThat(hangout.hasCombinedEpisodes()).isTrue();
    }

    @Test
    void addCombinedExternalId_ShouldAddToList() {
        // Given
        Hangout hangout = new Hangout();
        String externalId = "tvmaze-episode-123";

        // When
        hangout.addCombinedExternalId(externalId);

        // Then
        assertThat(hangout.getCombinedExternalIds()).contains(externalId);
        assertThat(hangout.getCombinedEpisodesCount()).isEqualTo(1);
    }

    @Test
    void addCombinedExternalId_WithDuplicate_ShouldNotAddDuplicate() {
        // Given
        Hangout hangout = new Hangout();
        String externalId = "tvmaze-episode-123";
        hangout.addCombinedExternalId(externalId);

        // When
        hangout.addCombinedExternalId(externalId);

        // Then
        assertThat(hangout.getCombinedExternalIds()).hasSize(1);
    }

    @Test
    void addCombinedExternalId_WithNullList_ShouldInitializeAndAdd() {
        // Given
        Hangout hangout = new Hangout();
        hangout.setCombinedExternalIds(null);

        // When
        hangout.addCombinedExternalId("episode-1");

        // Then
        assertThat(hangout.getCombinedExternalIds()).contains("episode-1");
    }

    @Test
    void removeCombinedExternalId_ShouldRemoveFromList() {
        // Given
        Hangout hangout = new Hangout();
        hangout.addCombinedExternalId("episode-1");
        hangout.addCombinedExternalId("episode-2");

        // When
        hangout.removeCombinedExternalId("episode-1");

        // Then
        assertThat(hangout.getCombinedExternalIds()).containsExactly("episode-2");
    }

    @Test
    void getCombinedEpisodesCount_WithMultipleEpisodes_ShouldReturnCount() {
        // Given
        Hangout hangout = new Hangout();
        hangout.addCombinedExternalId("episode-1");
        hangout.addCombinedExternalId("episode-2");
        hangout.addCombinedExternalId("episode-3");

        // Then
        assertThat(hangout.getCombinedEpisodesCount()).isEqualTo(3);
    }

    @Test
    void getCombinedEpisodesCount_WithNullList_ShouldReturnZero() {
        // Given
        Hangout hangout = new Hangout();
        hangout.setCombinedExternalIds(null);

        // Then
        assertThat(hangout.getCombinedEpisodesCount()).isEqualTo(0);
    }

    @Test
    void setCombinedExternalIds_WithNull_ShouldInitializeEmptyList() {
        // Given
        Hangout hangout = new Hangout();

        // When
        hangout.setCombinedExternalIds(null);

        // Then
        assertThat(hangout.getCombinedExternalIds()).isNotNull().isEmpty();
    }

    @Test
    void setTitleNotificationSent_ShouldUpdateTimestamp() throws InterruptedException {
        // Given
        Hangout hangout = new Hangout();
        Instant initialUpdatedAt = hangout.getUpdatedAt();

        Thread.sleep(5);

        // When
        hangout.setTitleNotificationSent(true);

        // Then
        assertThat(hangout.getUpdatedAt()).isAfter(initialUpdatedAt);
    }
}
