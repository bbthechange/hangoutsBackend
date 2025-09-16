package com.bbthechange.inviter.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit tests for SeriesPointer model class.
 * Tests parts management, serialization, and synchronization functionality.
 */
class SeriesPointerTest {

    @Test
    void getParts_WithTimestamps_ShouldReturnChronologicalOrder() {
        // Given
        SeriesPointer seriesPointer = new SeriesPointer();
        HangoutPointer part1 = createHangoutPointerWithTimestamp("part-1", 3000L);
        HangoutPointer part2 = createHangoutPointerWithTimestamp("part-2", 1000L);
        HangoutPointer part3 = createHangoutPointerWithTimestamp("part-3", 2000L);

        seriesPointer.setParts(Arrays.asList(part1, part2, part3));

        // When
        List<HangoutPointer> result = seriesPointer.getParts();

        // Then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getStartTimestamp()).isEqualTo(1000L);
        assertThat(result.get(1).getStartTimestamp()).isEqualTo(2000L);
        assertThat(result.get(2).getStartTimestamp()).isEqualTo(3000L);
    }

    @Test
    void addPart_ShouldAddToPartsList() {
        // Given
        SeriesPointer seriesPointer = new SeriesPointer();
        HangoutPointer part = createTestHangoutPointer("new-part");

        // When
        seriesPointer.addPart(part);

        // Then
        assertThat(seriesPointer.getParts()).hasSize(1);
        assertThat(seriesPointer.getParts().get(0)).isEqualTo(part);
        assertThat(seriesPointer.getPartsCount()).isEqualTo(1);
    }

    @Test
    void syncWithEventSeries_ShouldUpdateAllFields() {
        // Given
        SeriesPointer seriesPointer = new SeriesPointer();
        EventSeries eventSeries = createTestEventSeries();
        eventSeries.setSeriesTitle("Updated Title");
        eventSeries.setVersion(5L);

        // When
        seriesPointer.syncWithEventSeries(eventSeries);

        // Then
        assertThat(seriesPointer.getSeriesTitle()).isEqualTo("Updated Title");
        assertThat(seriesPointer.getVersion()).isEqualTo(5L);
    }

    @Test
    void constructor_WithParameters_ShouldSetCorrectValues() {
        // Given/When
        SeriesPointer seriesPointer = new SeriesPointer("12345678-1234-1234-1234-123456789012", "12345678-1234-1234-1234-123456789013", "Test Series");

        // Then
        assertThat(seriesPointer.getGroupId()).isEqualTo("12345678-1234-1234-1234-123456789012");
        assertThat(seriesPointer.getSeriesId()).isEqualTo("12345678-1234-1234-1234-123456789013");
        assertThat(seriesPointer.getSeriesTitle()).isEqualTo("Test Series");
        assertThat(seriesPointer.getItemType()).isEqualTo("SERIES_POINTER");
        assertThat(seriesPointer.getVersion()).isEqualTo(1L);
        assertThat(seriesPointer.getHangoutIds()).isNotNull().isEmpty();
        assertThat(seriesPointer.getParts()).isNotNull().isEmpty();
    }

    @Test
    void fromEventSeries_ShouldCopyAllFields() {
        // Given
        EventSeries eventSeries = createTestEventSeries();
        eventSeries.setSeriesTitle("Movie Night Series");
        eventSeries.setSeriesDescription("Weekly movie nights");
        eventSeries.setPrimaryEventId("primary-event-1");
        eventSeries.setStartTimestamp(1000L);
        eventSeries.setEndTimestamp(2000L);
        eventSeries.setHangoutIds(Arrays.asList("hangout-1", "hangout-2"));
        eventSeries.setVersion(3L);

        // When
        SeriesPointer result = SeriesPointer.fromEventSeries(eventSeries, "12345678-1234-1234-1234-123456789012");

        // Then
        assertThat(result.getGroupId()).isEqualTo("12345678-1234-1234-1234-123456789012");
        assertThat(result.getSeriesId()).isEqualTo(eventSeries.getSeriesId());
        assertThat(result.getSeriesTitle()).isEqualTo("Movie Night Series");
        assertThat(result.getSeriesDescription()).isEqualTo("Weekly movie nights");
        assertThat(result.getPrimaryEventId()).isEqualTo("primary-event-1");
        assertThat(result.getStartTimestamp()).isEqualTo(1000L);
        assertThat(result.getEndTimestamp()).isEqualTo(2000L);
        assertThat(result.getHangoutIds()).containsExactly("hangout-1", "hangout-2");
        assertThat(result.getVersion()).isEqualTo(3L);
    }

    @Test
    void getHangoutCount_ShouldReturnCorrectCount() {
        // Given
        SeriesPointer seriesPointer = new SeriesPointer();
        seriesPointer.setHangoutIds(Arrays.asList("hangout-1", "hangout-2", "hangout-3"));

        // When/Then
        assertThat(seriesPointer.getHangoutCount()).isEqualTo(3);
    }

    @Test
    void getHangoutCount_WithNullList_ShouldReturnZero() {
        // Given
        SeriesPointer seriesPointer = new SeriesPointer();
        seriesPointer.setHangoutIds(null);

        // When/Then
        assertThat(seriesPointer.getHangoutCount()).isEqualTo(0);
    }

    @Test
    void containsHangout_WithExistingHangout_ShouldReturnTrue() {
        // Given
        SeriesPointer seriesPointer = new SeriesPointer();
        seriesPointer.setHangoutIds(Arrays.asList("hangout-1", "hangout-2", "hangout-3"));

        // When/Then
        assertThat(seriesPointer.containsHangout("hangout-2")).isTrue();
    }

    @Test
    void containsHangout_WithNonExistingHangout_ShouldReturnFalse() {
        // Given
        SeriesPointer seriesPointer = new SeriesPointer();
        seriesPointer.setHangoutIds(Arrays.asList("hangout-1", "hangout-2", "hangout-3"));

        // When/Then
        assertThat(seriesPointer.containsHangout("hangout-999")).isFalse();
    }

    @Test
    void containsHangout_WithNullList_ShouldReturnFalse() {
        // Given
        SeriesPointer seriesPointer = new SeriesPointer();
        seriesPointer.setHangoutIds(null);

        // When/Then
        assertThat(seriesPointer.containsHangout("hangout-1")).isFalse();
    }

    @Test
    void setParts_WithNullList_ShouldCreateEmptyList() {
        // Given
        SeriesPointer seriesPointer = new SeriesPointer();

        // When
        seriesPointer.setParts(null);

        // Then
        assertThat(seriesPointer.getParts()).isNotNull().isEmpty();
        assertThat(seriesPointer.getPartsCount()).isEqualTo(0);
    }

    @Test
    void addPart_WithNullPartsList_ShouldInitializeList() {
        // Given
        SeriesPointer seriesPointer = new SeriesPointer();
        seriesPointer.setParts(null); // Force parts to be null
        HangoutPointer part = createTestHangoutPointer("new-part");

        // When
        seriesPointer.addPart(part);

        // Then
        assertThat(seriesPointer.getParts()).hasSize(1);
        assertThat(seriesPointer.getParts().get(0)).isEqualTo(part);
    }

    @Test
    void getParts_WithNullTimestamps_ShouldNotSort() {
        // Given
        SeriesPointer seriesPointer = new SeriesPointer();
        HangoutPointer part1 = createHangoutPointerWithTimestamp("part-1", null);
        HangoutPointer part2 = createHangoutPointerWithTimestamp("part-2", null);
        HangoutPointer part3 = createHangoutPointerWithTimestamp("part-3", null);

        seriesPointer.setParts(Arrays.asList(part1, part2, part3));

        // When
        List<HangoutPointer> result = seriesPointer.getParts();

        // Then
        assertThat(result).hasSize(3);
        // Order should remain unchanged since timestamps are null
        assertThat(result.get(0).getHangoutId()).isEqualTo("part-1");
        assertThat(result.get(1).getHangoutId()).isEqualTo("part-2");
        assertThat(result.get(2).getHangoutId()).isEqualTo("part-3");
    }

    @Test
    void syncWithEventSeries_WithNullHangoutIds_ShouldCreateEmptyList() {
        // Given
        SeriesPointer seriesPointer = new SeriesPointer();
        EventSeries eventSeries = createTestEventSeries();
        eventSeries.setHangoutIds(null);

        // When
        seriesPointer.syncWithEventSeries(eventSeries);

        // Then
        assertThat(seriesPointer.getHangoutIds()).isNotNull().isEmpty();
    }

    @Test
    void setParts_UpdatesPartsAndTouchesTimestamp() {
        // Given
        SeriesPointer seriesPointer = new SeriesPointer();
        java.time.Instant initialTimestamp = seriesPointer.getUpdatedAt();
        
        // Wait a bit to ensure timestamp difference
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            // Ignore
        }
        
        List<HangoutPointer> newParts = Arrays.asList(
            createTestHangoutPointer("part-1"),
            createTestHangoutPointer("part-2")
        );

        // When
        seriesPointer.setParts(newParts);

        // Then
        assertThat(seriesPointer.getParts()).hasSize(2);
        assertThat(seriesPointer.getParts()).containsExactlyElementsOf(newParts);
        assertThat(seriesPointer.getUpdatedAt()).isAfter(initialTimestamp);
    }

    @Test
    void getEndTimestamp_HasEndTimestampIndexAnnotation() {
        // Given/When - Use reflection to check annotation
        java.lang.reflect.Method method;
        try {
            method = SeriesPointer.class.getMethod("getEndTimestamp");
        } catch (NoSuchMethodException e) {
            fail("getEndTimestamp method not found");
            return;
        }

        // Then
        software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey annotation = 
            method.getAnnotation(software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey.class);
        
        assertThat(annotation).isNotNull();
        assertThat(annotation.indexNames()).containsExactly("EndTimestampIndex");
    }

    // Helper methods
    private HangoutPointer createTestHangoutPointer(String hangoutId) {
        return createHangoutPointerWithTimestamp(hangoutId, null);
    }

    private HangoutPointer createHangoutPointerWithTimestamp(String hangoutId, Long timestamp) {
        HangoutPointer pointer = new HangoutPointer();
        pointer.setHangoutId(hangoutId);
        pointer.setGroupId("12345678-1234-1234-1234-123456789012");
        pointer.setTitle("Test Hangout");
        if (timestamp != null) {
            pointer.setStartTimestamp(timestamp);
        }
        // Set keys directly for test purposes
        pointer.setPk("GROUP#12345678-1234-1234-1234-123456789012");
        pointer.setSk("HANGOUT#" + hangoutId);
        return pointer;
    }

    private EventSeries createTestEventSeries() {
        EventSeries series = new EventSeries();
        series.setSeriesId("12345678-1234-1234-1234-123456789014");
        series.setSeriesTitle("Test Series");
        series.setSeriesDescription("Test Description");
        series.setPrimaryEventId("primary-event");
        series.setStartTimestamp(1000L);
        series.setEndTimestamp(5000L);
        series.setVersion(1L);
        return series;
    }
}