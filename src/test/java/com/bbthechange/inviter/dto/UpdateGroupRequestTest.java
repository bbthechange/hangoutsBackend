package com.bbthechange.inviter.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for UpdateGroupRequest DTO.
 * Tests image path handling and update detection.
 */
class UpdateGroupRequestTest {

    @Test
    void hasUpdates_ReturnsTrueWhenMainImagePathProvided() {
        // Given
        UpdateGroupRequest request = new UpdateGroupRequest();
        request.setMainImagePath("/image.jpg");

        // When
        boolean result = request.hasUpdates();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void hasUpdates_ReturnsTrueWhenBackgroundImagePathProvided() {
        // Given
        UpdateGroupRequest request = new UpdateGroupRequest();
        request.setBackgroundImagePath("/bg.jpg");

        // When
        boolean result = request.hasUpdates();

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void hasUpdates_ReturnsFalseWhenNoFieldsProvided() {
        // Given
        UpdateGroupRequest request = new UpdateGroupRequest();
        // All fields are null

        // When
        boolean result = request.hasUpdates();

        // Then
        assertThat(result).isFalse();
    }
}
