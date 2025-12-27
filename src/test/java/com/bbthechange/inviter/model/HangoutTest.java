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
}
