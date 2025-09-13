package com.bbthechange.inviter.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for series-related functionality in InviterKeyFactory.
 */
class InviterKeyFactorySeriesTest {

    @Test
    void getSeriesPk_WithValidSeriesId_ShouldReturnCorrectPartitionKey() {
        // Given
        String seriesId = "550e8400-e29b-41d4-a716-446655440000";

        // When
        String result = InviterKeyFactory.getSeriesPk(seriesId);

        // Then
        assertThat(result).isEqualTo("SERIES#" + seriesId);
    }

    @Test
    void getSeriesPk_WithDifferentSeriesId_ShouldReturnCorrectPartitionKey() {
        // Given
        String seriesId = "123e4567-e89b-12d3-a456-426614174000";

        // When
        String result = InviterKeyFactory.getSeriesPk(seriesId);

        // Then
        assertThat(result).isEqualTo("SERIES#" + seriesId);
    }

    @Test
    void getSeriesPk_WithNullSeriesId_ShouldThrowException() {
        // When & Then
        assertThatThrownBy(() -> InviterKeyFactory.getSeriesPk(null))
            .isInstanceOf(Exception.class);
    }

    @Test
    void getSeriesPk_WithEmptySeriesId_ShouldThrowException() {
        // When & Then
        assertThatThrownBy(() -> InviterKeyFactory.getSeriesPk(""))
            .isInstanceOf(Exception.class);
    }

    @Test
    void getSeriesPk_WithInvalidUuidFormat_ShouldThrowException() {
        // When & Then
        assertThatThrownBy(() -> InviterKeyFactory.getSeriesPk("not-a-uuid"))
            .isInstanceOf(Exception.class);
    }

    @Test
    void seriesPrefix_ShouldHaveCorrectValue() {
        // Then
        assertThat(InviterKeyFactory.SERIES_PREFIX).isEqualTo("SERIES");
    }

    @Test
    void isSeriesItem_WithMetadataSortKey_ShouldReturnTrue() {
        // Given
        String sortKey = "METADATA";

        // When
        boolean result = InviterKeyFactory.isSeriesItem(sortKey);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void isSeriesItem_WithNonMetadataSortKey_ShouldReturnFalse() {
        // Given
        String sortKey = "HANGOUT#12345";

        // When
        boolean result = InviterKeyFactory.isSeriesItem(sortKey);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void isSeriesItem_WithNullSortKey_ShouldReturnFalse() {
        // When
        boolean result = InviterKeyFactory.isSeriesItem(null);

        // Then
        assertThat(result).isFalse();
    }
}