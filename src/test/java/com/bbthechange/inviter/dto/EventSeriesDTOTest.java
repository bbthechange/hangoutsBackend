package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.EventSeries;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for EventSeriesDTO.
 * Tests DTO creation and field mapping from EventSeries model.
 */
class EventSeriesDTOTest {

    @Test
    void constructor_FromEventSeries_CopiesAllFields() {
        // Given
        EventSeries eventSeries = createTestEventSeries();
        eventSeries.setSeriesId("12345678-1234-1234-1234-123456789012");
        eventSeries.setSeriesTitle("Movie Night Series");
        eventSeries.setSeriesDescription("Weekly movie nights with friends");
        eventSeries.setPrimaryEventId("primary-event-1");
        eventSeries.setGroupId("group-1");
        eventSeries.setStartTimestamp(1000L);
        eventSeries.setEndTimestamp(5000L);
        eventSeries.setHangoutIds(Arrays.asList("hangout-1", "hangout-2", "hangout-3"));
        eventSeries.setVersion(3L);
        
        Instant createdAt = Instant.now().minusSeconds(3600);
        Instant updatedAt = Instant.now();
        eventSeries.setCreatedAt(createdAt);
        eventSeries.setUpdatedAt(updatedAt);

        // When
        EventSeriesDTO dto = new EventSeriesDTO(eventSeries);

        // Then
        assertThat(dto.getSeriesId()).isEqualTo("12345678-1234-1234-1234-123456789012");
        assertThat(dto.getSeriesTitle()).isEqualTo("Movie Night Series");
        assertThat(dto.getSeriesDescription()).isEqualTo("Weekly movie nights with friends");
        assertThat(dto.getPrimaryEventId()).isEqualTo("primary-event-1");
        assertThat(dto.getGroupId()).isEqualTo("group-1");
        assertThat(dto.getStartTimestamp()).isEqualTo(1000L);
        assertThat(dto.getEndTimestamp()).isEqualTo(5000L);
        assertThat(dto.getHangoutIds()).containsExactly("hangout-1", "hangout-2", "hangout-3");
        assertThat(dto.getVersion()).isEqualTo(3L);
        assertThat(dto.getCreatedAt()).isEqualTo(createdAt);
        assertThat(dto.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void constructor_WithNullFields_HandlesGracefully() {
        // Given
        EventSeries eventSeries = new EventSeries();
        eventSeries.setSeriesId("12345678-1234-1234-1234-123456789012");
        eventSeries.setSeriesTitle("Test Series");
        // Leave description, primaryEventId, startTimestamp, endTimestamp as null/default
        eventSeries.setGroupId("group-1");
        eventSeries.setVersion(1L);
        // Explicitly set hangoutIds to null to test null handling
        eventSeries.setHangoutIds(null);
        // Leave createdAt and updatedAt as null

        // When
        EventSeriesDTO dto = new EventSeriesDTO(eventSeries);

        // Then
        assertThat(dto.getSeriesId()).isEqualTo("12345678-1234-1234-1234-123456789012");
        assertThat(dto.getSeriesTitle()).isEqualTo("Test Series");
        assertThat(dto.getSeriesDescription()).isNull();
        assertThat(dto.getPrimaryEventId()).isNull();
        assertThat(dto.getGroupId()).isEqualTo("group-1");
        assertThat(dto.getStartTimestamp()).isNull();
        assertThat(dto.getEndTimestamp()).isNull();
        assertThat(dto.getHangoutIds()).isNotNull().isEmpty();
        assertThat(dto.getVersion()).isEqualTo(1L);
        assertThat(dto.getCreatedAt()).isNotNull();
        assertThat(dto.getUpdatedAt()).isNotNull();
    }

    // Helper methods
    private EventSeries createTestEventSeries() {
        EventSeries series = new EventSeries();
        series.setSeriesId("test-series-id");
        series.setSeriesTitle("Test Series");
        series.setSeriesDescription("Test Description");
        series.setGroupId("test-group-id");
        series.setVersion(1L);
        return series;
    }
}