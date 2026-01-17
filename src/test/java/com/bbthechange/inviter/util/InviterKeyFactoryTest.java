package com.bbthechange.inviter.util;

import com.bbthechange.inviter.exception.InvalidKeyException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for InviterKeyFactory.
 * Tests the TVMaze Season key patterns.
 */
class InviterKeyFactoryTest {

    // ============================================================================
    // TVMAZE SEASON KEY TESTS
    // ============================================================================

    @Test
    void getSeasonPk_WithValidShowId_ShouldReturnCorrectKey() {
        // Given
        Integer showId = 123;

        // When
        String pk = InviterKeyFactory.getSeasonPk(showId);

        // Then
        assertThat(pk).isEqualTo("TVMAZE#SHOW#123");
    }

    @Test
    void getSeasonPk_WithNullShowId_ShouldThrowException() {
        // When/Then
        assertThatThrownBy(() -> InviterKeyFactory.getSeasonPk(null))
            .isInstanceOf(InvalidKeyException.class)
            .hasMessageContaining("Show ID cannot be null");
    }

    @Test
    void getSeasonSk_WithValidSeasonNumber_ShouldReturnCorrectKey() {
        // Given
        Integer seasonNumber = 5;

        // When
        String sk = InviterKeyFactory.getSeasonSk(seasonNumber);

        // Then
        assertThat(sk).isEqualTo("SEASON#5");
    }

    @Test
    void getSeasonSk_WithNullSeasonNumber_ShouldThrowException() {
        // When/Then
        assertThatThrownBy(() -> InviterKeyFactory.getSeasonSk(null))
            .isInstanceOf(InvalidKeyException.class)
            .hasMessageContaining("Season number cannot be null");
    }

    @Test
    void getSeasonReference_WithValidParameters_ShouldReturnCorrectReference() {
        // Given
        Integer showId = 456;
        Integer seasonNumber = 3;

        // When
        String reference = InviterKeyFactory.getSeasonReference(showId, seasonNumber);

        // Then
        assertThat(reference).isEqualTo("TVMAZE#SHOW#456|SEASON#3");
    }

    @Test
    void getSeasonReference_WithNullShowId_ShouldThrowException() {
        // When/Then
        assertThatThrownBy(() -> InviterKeyFactory.getSeasonReference(null, 1))
            .isInstanceOf(InvalidKeyException.class)
            .hasMessageContaining("Show ID cannot be null");
    }

    @Test
    void getSeasonReference_WithNullSeasonNumber_ShouldThrowException() {
        // When/Then
        assertThatThrownBy(() -> InviterKeyFactory.getSeasonReference(123, null))
            .isInstanceOf(InvalidKeyException.class)
            .hasMessageContaining("Season number cannot be null");
    }

    @Test
    void isSeasonItem_WithValidSeasonSortKey_ShouldReturnTrue() {
        // Given
        String sortKey = "SEASON#5";

        // When
        boolean result = InviterKeyFactory.isSeasonItem(sortKey);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void isSeasonItem_WithInvalidSortKey_ShouldReturnFalse() {
        // Given
        String sortKey = "HANGOUT#123";

        // When
        boolean result = InviterKeyFactory.isSeasonItem(sortKey);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void isSeasonItem_WithNullSortKey_ShouldReturnFalse() {
        // When
        boolean result = InviterKeyFactory.isSeasonItem(null);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void isSeasonItem_WithPartialMatch_ShouldReturnFalse() {
        // Given - doesn't start with SEASON#
        String sortKey = "SOMESEASON#5";

        // When
        boolean result = InviterKeyFactory.isSeasonItem(sortKey);

        // Then
        assertThat(result).isFalse();
    }

    // ============================================================================
    // CONSTANTS TESTS
    // ============================================================================

    @Test
    void constants_ShouldHaveCorrectValues() {
        assertThat(InviterKeyFactory.TVMAZE_PREFIX).isEqualTo("TVMAZE");
        assertThat(InviterKeyFactory.SHOW_PREFIX).isEqualTo("SHOW");
        assertThat(InviterKeyFactory.SEASON_PREFIX).isEqualTo("SEASON");
    }
}
