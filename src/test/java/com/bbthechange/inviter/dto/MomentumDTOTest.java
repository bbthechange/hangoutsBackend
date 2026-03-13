package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.MomentumCategory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for MomentumDTO static factory methods.
 *
 * Coverage:
 * - fromRawScore: score normalization, null category, zero threshold
 * - fromPointerFields: null score treated as zero, all fields mapped
 */
class MomentumDTOTest {

    // ============================================================================
    // fromRawScore Tests
    // ============================================================================

    @Test
    void fromRawScore_normalizesScoreToHundred() {
        // (10 * 100) / (2 * 2) = 250, min(100, 250) = 100
        MomentumDTO dto = MomentumDTO.fromRawScore(10, 2, MomentumCategory.BUILDING, null, null, null);

        assertThat(dto.getScore()).isEqualTo(100);
    }

    @Test
    void fromRawScore_midScore_normalizedCorrectly() {
        // (2 * 100) / (2 * 2) = 50
        MomentumDTO dto = MomentumDTO.fromRawScore(2, 2, MomentumCategory.GAINING_MOMENTUM, null, null, null);

        assertThat(dto.getScore()).isEqualTo(50);
    }

    @Test
    void fromRawScore_zeroThreshold_scoreIsZero() {
        // threshold <= 0, guarded → score = 0
        MomentumDTO dto = MomentumDTO.fromRawScore(5, 0, MomentumCategory.BUILDING, null, null, null);

        assertThat(dto.getScore()).isEqualTo(0);
    }

    @Test
    void fromRawScore_categoryName_isStringForm() {
        MomentumDTO dto = MomentumDTO.fromRawScore(0, 1, MomentumCategory.GAINING_MOMENTUM, null, null, null);

        assertThat(dto.getCategory()).isEqualTo("GAINING_MOMENTUM");
    }

    @Test
    void fromRawScore_nullCategory_categoryIsNull() {
        MomentumDTO dto = MomentumDTO.fromRawScore(0, 1, null, null, null, null);

        assertThat(dto.getCategory()).isNull();
    }

    @Test
    void fromRawScore_confirmedFields_areMapped() {
        MomentumDTO dto = MomentumDTO.fromRawScore(0, 1, MomentumCategory.CONFIRMED,
                1700000000000L, "user-1", "user-2");

        assertThat(dto.getConfirmedAt()).isEqualTo(1700000000000L);
        assertThat(dto.getConfirmedBy()).isEqualTo("user-1");
        assertThat(dto.getSuggestedBy()).isEqualTo("user-2");
    }

    // ============================================================================
    // fromPointerFields Tests
    // ============================================================================

    @Test
    void fromPointerFields_nullScore_treatedAsZero() {
        MomentumDTO dto = MomentumDTO.fromPointerFields(null, MomentumCategory.BUILDING, null, null, null);

        assertThat(dto.getScore()).isEqualTo(0);
    }

    @Test
    void fromPointerFields_allFieldsMapped() {
        MomentumDTO dto = MomentumDTO.fromPointerFields(5, MomentumCategory.CONFIRMED,
                1700000000000L, "user-1", "user-2");

        assertThat(dto.getScore()).isEqualTo(5);
        assertThat(dto.getCategory()).isEqualTo("CONFIRMED");
        assertThat(dto.getConfirmedAt()).isEqualTo(1700000000000L);
        assertThat(dto.getConfirmedBy()).isEqualTo("user-1");
        assertThat(dto.getSuggestedBy()).isEqualTo("user-2");
    }

    @Test
    void fromPointerFields_nullCategory_categoryIsNull() {
        MomentumDTO dto = MomentumDTO.fromPointerFields(5, null, null, null, null);

        assertThat(dto.getCategory()).isNull();
    }
}
