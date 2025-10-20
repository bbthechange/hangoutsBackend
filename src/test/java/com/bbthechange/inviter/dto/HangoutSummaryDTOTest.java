package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for HangoutSummaryDTO.
 * Tests the population and handling of interestLevels from HangoutPointer.
 */
class HangoutSummaryDTOTest {

    @Test
    void constructor_PopulatesInterestLevelsFromPointer() {
        // Given
        String groupId = UUID.randomUUID().toString();
        String hangoutId = UUID.randomUUID().toString();
        String requestingUserId = UUID.randomUUID().toString();

        HangoutPointer pointer = new HangoutPointer(groupId, hangoutId, "Test Hangout");

        // Create interest levels with all fields populated using valid UUIDs
        String userId1 = UUID.randomUUID().toString();
        String userId2 = UUID.randomUUID().toString();
        String userId3 = UUID.randomUUID().toString();

        InterestLevel interest1 = new InterestLevel(hangoutId, userId1, "User One", "GOING");
        interest1.setNotes("Excited to attend!");
        interest1.setMainImagePath("/images/user1.jpg");

        InterestLevel interest2 = new InterestLevel(hangoutId, userId2, "User Two", "INTERESTED");
        interest2.setNotes("Might come");
        interest2.setMainImagePath("/images/user2.jpg");

        InterestLevel interest3 = new InterestLevel(hangoutId, userId3, "User Three", "NOT_GOING");
        interest3.setNotes("Can't make it");
        interest3.setMainImagePath("/images/user3.jpg");

        pointer.setInterestLevels(Arrays.asList(interest1, interest2, interest3));

        // When
        HangoutSummaryDTO dto = new HangoutSummaryDTO(pointer, requestingUserId);

        // Then
        assertThat(dto.getInterestLevels()).isNotNull();
        assertThat(dto.getInterestLevels()).hasSize(3);

        // Verify first interest level fields are accessible
        InterestLevel first = dto.getInterestLevels().get(0);
        assertThat(first.getUserId()).isEqualTo(userId1);
        assertThat(first.getUserName()).isEqualTo("User One");
        assertThat(first.getStatus()).isEqualTo("GOING");
        assertThat(first.getNotes()).isEqualTo("Excited to attend!");
        assertThat(first.getMainImagePath()).isEqualTo("/images/user1.jpg");

        // Verify second interest level
        InterestLevel second = dto.getInterestLevels().get(1);
        assertThat(second.getUserId()).isEqualTo(userId2);
        assertThat(second.getUserName()).isEqualTo("User Two");
        assertThat(second.getStatus()).isEqualTo("INTERESTED");

        // Verify third interest level
        InterestLevel third = dto.getInterestLevels().get(2);
        assertThat(third.getUserId()).isEqualTo(userId3);
        assertThat(third.getStatus()).isEqualTo("NOT_GOING");
    }

    @Test
    void constructor_HandlesNullInterestLevelsGracefully() {
        // Given
        String groupId = UUID.randomUUID().toString();
        String hangoutId = UUID.randomUUID().toString();
        String requestingUserId = UUID.randomUUID().toString();

        HangoutPointer pointer = new HangoutPointer(groupId, hangoutId, "Test Hangout");
        pointer.setInterestLevels(null); // Explicitly set to null

        // When
        HangoutSummaryDTO dto = new HangoutSummaryDTO(pointer, requestingUserId);

        // Then - getInterestLevels should return empty list, not null
        assertThat(dto.getInterestLevels()).isNotNull();
        assertThat(dto.getInterestLevels()).isEmpty();
    }

    @Test
    void getInterestLevels_WhenNull_ReturnsEmptyList() {
        // Given
        String groupId = UUID.randomUUID().toString();
        String hangoutId = UUID.randomUUID().toString();
        String requestingUserId = UUID.randomUUID().toString();

        HangoutPointer pointer = new HangoutPointer(groupId, hangoutId, "Test Hangout");
        // Don't set any interest levels, leaving the field as initialized (empty list)

        // When
        HangoutSummaryDTO dto = new HangoutSummaryDTO(pointer, requestingUserId);

        // Then - getInterestLevels should never return null
        assertThat(dto.getInterestLevels()).isNotNull();
        assertThat(dto.getInterestLevels()).isEmpty();
    }
}
