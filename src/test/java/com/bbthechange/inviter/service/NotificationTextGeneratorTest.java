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
        String result = textGenerator.getHangoutUpdatedBody(hangoutTitle, "time", null);

        // Then
        assertThat(result).isEqualTo("Time changed for 'Movie Night'");
    }

    @Test
    void getHangoutUpdatedBody_LocationChange_WithLocationName_ReturnsMessageWithLocation() {
        // Given
        String hangoutTitle = "Movie Night";
        String locationName = "Central Park";

        // When
        String result = textGenerator.getHangoutUpdatedBody(hangoutTitle, "location", locationName);

        // Then
        assertThat(result).isEqualTo("Location changed for 'Movie Night', now at Central Park");
    }

    @Test
    void getHangoutUpdatedBody_LocationChange_WithoutLocationName_ReturnsBasicMessage() {
        // Given
        String hangoutTitle = "Movie Night";

        // When
        String result = textGenerator.getHangoutUpdatedBody(hangoutTitle, "location", null);

        // Then
        assertThat(result).isEqualTo("Location changed for 'Movie Night'");
    }

    @Test
    void getHangoutUpdatedBody_TimeAndLocationChange_WithLocationName_ReturnsCombinedMessage() {
        // Given
        String hangoutTitle = "Movie Night";
        String locationName = "Central Park";

        // When
        String result = textGenerator.getHangoutUpdatedBody(hangoutTitle, "time_and_location", locationName);

        // Then
        assertThat(result).isEqualTo("Time and location changed for 'Movie Night', now at Central Park");
    }

    @Test
    void getHangoutUpdatedBody_TimeAndLocationChange_WithoutLocationName_ReturnsCombinedMessage() {
        // Given
        String hangoutTitle = "Movie Night";

        // When
        String result = textGenerator.getHangoutUpdatedBody(hangoutTitle, "time_and_location", null);

        // Then
        assertThat(result).isEqualTo("Time and location changed for 'Movie Night'");
    }

    @Test
    void getHangoutUpdatedBody_UnknownChangeType_ReturnsGenericMessage() {
        // Given
        String hangoutTitle = "Movie Night";

        // When
        String result = textGenerator.getHangoutUpdatedBody(hangoutTitle, "unknown", null);

        // Then
        assertThat(result).isEqualTo("'Movie Night' was updated");
    }

    // ========== Hangout Reminder Tests ==========

    @Test
    void getHangoutReminderBody_ReturnsCorrectFormat() {
        // Given
        String hangoutTitle = "Pizza Night";

        // When
        String result = textGenerator.getHangoutReminderBody(hangoutTitle);

        // Then
        assertThat(result).isEqualTo("Pizza Night starts in 2 hours");
    }

    @Test
    void getHangoutReminderBody_HandlesSpecialCharacters() {
        // Given: Title with special characters
        String hangoutTitle = "John's Birthday @ the Park!";

        // When
        String result = textGenerator.getHangoutReminderBody(hangoutTitle);

        // Then: Special characters preserved
        assertThat(result).isEqualTo("John's Birthday @ the Park! starts in 2 hours");
    }

    @Test
    void getHangoutReminderBody_HandlesEmojis() {
        // Given: Title with emojis
        String hangoutTitle = "Team BBQ 🍖🔥";

        // When
        String result = textGenerator.getHangoutReminderBody(hangoutTitle);

        // Then: Emojis preserved
        assertThat(result).isEqualTo("Team BBQ 🍖🔥 starts in 2 hours");
    }

    @Test
    void getHangoutReminderBody_HandlesLongTitle() {
        // Given: Long title
        String hangoutTitle = "Annual Company Holiday Party and Team Building Event 2024";

        // When
        String result = textGenerator.getHangoutReminderBody(hangoutTitle);

        // Then: Full title used
        assertThat(result).isEqualTo("Annual Company Holiday Party and Team Building Event 2024 starts in 2 hours");
    }

    @Test
    void hangoutReminderTitle_IsConstant() {
        // Verify the title constant
        assertThat(NotificationTextGenerator.HANGOUT_REMINDER_TITLE).isEqualTo("Starting Soon!");
    }

    // ========== Watch Party Title Tests ==========

    @Test
    void getWatchPartyTitle_WithCancelledMessage_ReturnsEpisodeRemovedTitle() {
        // Given
        String message = "Episode 5 has been cancelled";

        // When
        String result = textGenerator.getWatchPartyTitle(message);

        // Then
        assertThat(result).isEqualTo(NotificationTextGenerator.WATCH_PARTY_EPISODE_REMOVED_TITLE);
    }

    @Test
    void getWatchPartyTitle_WithRemovedMessage_ReturnsEpisodeRemovedTitle() {
        // Given
        String message = "Episode 3 was removed from the watch party";

        // When
        String result = textGenerator.getWatchPartyTitle(message);

        // Then
        assertThat(result).isEqualTo(NotificationTextGenerator.WATCH_PARTY_EPISODE_REMOVED_TITLE);
    }

    @Test
    void getWatchPartyTitle_WithRenamedMessage_ReturnsTitleUpdatedTitle() {
        // Given
        String message = "Episode renamed to 'The Final Chapter'";

        // When
        String result = textGenerator.getWatchPartyTitle(message);

        // Then
        assertThat(result).isEqualTo(NotificationTextGenerator.WATCH_PARTY_TITLE_UPDATED_TITLE);
    }

    @Test
    void getWatchPartyTitle_WithNeedsHostMessage_ReturnsNeedsHostTitle() {
        // Given
        String message = "The watch party needs a host for next week";

        // When
        String result = textGenerator.getWatchPartyTitle(message);

        // Then
        assertThat(result).isEqualTo(NotificationTextGenerator.WATCH_PARTY_NEEDS_HOST_TITLE);
    }

    @Test
    void getWatchPartyTitle_WithNewEpisodeMessage_ReturnsNewEpisodeTitle() {
        // Given
        String message = "New episode available: Season 2 Episode 1";

        // When
        String result = textGenerator.getWatchPartyTitle(message);

        // Then
        assertThat(result).isEqualTo(NotificationTextGenerator.WATCH_PARTY_NEW_EPISODE_TITLE);
    }

    @Test
    void getWatchPartyTitle_WithNullMessage_ReturnsNewEpisodeTitle() {
        // Given
        String message = null;

        // When
        String result = textGenerator.getWatchPartyTitle(message);

        // Then
        assertThat(result).isEqualTo(NotificationTextGenerator.WATCH_PARTY_NEW_EPISODE_TITLE);
    }

    // ========== Watch Party Body Tests ==========

    @Test
    void getWatchPartyBody_WithValidMessage_ReturnsMessage() {
        // Given
        String message = "Episode 5: The Great Adventure is now available";

        // When
        String result = textGenerator.getWatchPartyBody(message);

        // Then
        assertThat(result).isEqualTo(message);
    }

    @Test
    void getWatchPartyBody_WithNullMessage_ReturnsDefaultBody() {
        // Given
        String message = null;

        // When
        String result = textGenerator.getWatchPartyBody(message);

        // Then
        assertThat(result).isEqualTo("Check the app for details");
    }
}
