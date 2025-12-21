package com.bbthechange.inviter.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class NotificationTextGeneratorTest {

    @InjectMocks
    private NotificationTextGenerator textGenerator;

    private static final String TEST_GROUP_NAME = "Friends Group";
    private static final String TEST_ADDER_NAME = "John";

    @Test
    void getGroupMemberAddedBody_WithValidAdderName_IncludesAdderInMessage() {
        // Given
        String adderName = TEST_ADDER_NAME;
        String groupName = TEST_GROUP_NAME;

        // When
        String result = textGenerator.getGroupMemberAddedBody(adderName, groupName);

        // Then
        assertThat(result).isEqualTo("John added you to the group Friends Group");
    }

    @Test
    void getGroupMemberAddedBody_WithNullAdderName_UsesFallbackMessage() {
        // Given
        String adderName = null;
        String groupName = TEST_GROUP_NAME;

        // When
        String result = textGenerator.getGroupMemberAddedBody(adderName, groupName);

        // Then
        assertThat(result).isEqualTo("You were added to the group Friends Group");
    }

    @Test
    void getGroupMemberAddedBody_WithEmptyAdderName_UsesFallbackMessage() {
        // Given
        String groupName = TEST_GROUP_NAME;

        // When - empty string
        String resultEmpty = textGenerator.getGroupMemberAddedBody("", groupName);
        // When - whitespace only
        String resultWhitespace = textGenerator.getGroupMemberAddedBody("   ", groupName);

        // Then
        assertThat(resultEmpty).isEqualTo("You were added to the group Friends Group");
        assertThat(resultWhitespace).isEqualTo("You were added to the group Friends Group");
    }

    @Test
    void getGroupMemberAddedBody_WithUnknownAdderName_UsesFallbackMessage() {
        // Given - "Unknown" is the fallback value from user lookup
        String adderName = "Unknown";
        String groupName = TEST_GROUP_NAME;

        // When
        String result = textGenerator.getGroupMemberAddedBody(adderName, groupName);

        // Then
        assertThat(result).isEqualTo("You were added to the group Friends Group");
    }

    // ========== Hangout Updated Tests ==========

    @Test
    void getHangoutUpdatedBody_TimeChange_ReturnsTimeChangedMessage() {
        // Given
        String hangoutTitle = "Movie Night";

        // When
        String result = textGenerator.getHangoutUpdatedBody(hangoutTitle, "time");

        // Then
        assertThat(result).isEqualTo("Time changed for 'Movie Night'");
    }

    @Test
    void getHangoutUpdatedBody_LocationChange_ReturnsLocationChangedMessage() {
        // Given
        String hangoutTitle = "Movie Night";

        // When
        String result = textGenerator.getHangoutUpdatedBody(hangoutTitle, "location");

        // Then
        assertThat(result).isEqualTo("Location changed for 'Movie Night'");
    }

    @Test
    void getHangoutUpdatedBody_TimeAndLocationChange_ReturnsCombinedMessage() {
        // Given
        String hangoutTitle = "Movie Night";

        // When
        String result = textGenerator.getHangoutUpdatedBody(hangoutTitle, "time_and_location");

        // Then
        assertThat(result).isEqualTo("Time and location changed for 'Movie Night'");
    }

    @Test
    void getHangoutUpdatedBody_UnknownChangeType_ReturnsGenericMessage() {
        // Given
        String hangoutTitle = "Movie Night";

        // When
        String result = textGenerator.getHangoutUpdatedBody(hangoutTitle, "unknown");

        // Then
        assertThat(result).isEqualTo("'Movie Night' was updated");
    }
}
