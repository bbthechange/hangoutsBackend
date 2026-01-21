package com.bbthechange.inviter.staging.watchparty;

import com.bbthechange.inviter.staging.StagingTestBase;
import org.junit.jupiter.api.*;
import java.time.*;
import java.time.Duration;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Phase 4 Integration Tests: Watch Party Settings Update
 *
 * Tests the PUT /groups/{groupId}/watch-parties/{seriesId} endpoint
 * which updates watch party settings with optional cascade to existing hangouts.
 *
 * Key behaviors tested:
 * - changeExistingUpcomingHangouts=true cascades time changes to future hangouts
 * - changeExistingUpcomingHangouts=false preserves existing hangout times
 * - Past hangouts are never modified regardless of cascade setting
 * - Timezone changes properly recalculate hangout timestamps
 */
@DisplayName("Watch Party Settings Update - Phase 4")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WatchPartyUpdateTests extends StagingTestBase {

    /**
     * Helper method to get the first hangout's start timestamp from a watch party series.
     */
    private long getFirstHangoutStartTime(String groupId, String seriesId) {
        return given()
            .header("Authorization", "Bearer " + testUserToken)
        .when()
            .get("/groups/" + groupId + "/watch-parties/" + seriesId)
        .then()
            .statusCode(200)
        .extract()
            .jsonPath().getLong("hangouts[0].startTimestamp");
    }

    /**
     * Creates a watch party with multiple future episodes for testing cascading updates.
     * Returns the series ID. Episodes are scheduled 1 and 2 weeks in the future.
     */
    private String createTestWatchPartyWithMultipleEpisodes(String groupId, String showName, String defaultTime) {
        long oneWeekOut = Instant.now().plusSeconds(86400 * 7).getEpochSecond();
        long twoWeeksOut = Instant.now().plusSeconds(86400 * 14).getEpochSecond();
        String uniqueShowName = showName + " " + UUID.randomUUID().toString().substring(0, 8);
        int showId = Math.abs(uniqueShowName.hashCode() % 90000) + 10000;

        String requestBody = """
            {
              "showId": %d,
              "seasonNumber": 1,
              "showName": "%s",
              "defaultTime": "%s",
              "timezone": "America/Los_Angeles",
              "episodes": [
                { "episodeId": 1, "title": "Episode 1", "airTimestamp": %d, "runtime": 60 },
                { "episodeId": 2, "title": "Episode 2", "airTimestamp": %d, "runtime": 60 }
              ]
            }
            """.formatted(showId, uniqueShowName, defaultTime, oneWeekOut, twoWeeksOut);

        String seriesId = given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/groups/" + groupId + "/watch-parties")
        .then()
            .statusCode(201)
        .extract()
            .jsonPath().getString("seriesId");

        createdSeriesIds.add(seriesId);
        seriesGroupMap.put(seriesId, groupId);
        return seriesId;
    }

    @Test
    @Order(1)
    @DisplayName("Update settings with cascade=true updates future hangout timestamps")
    void updateSettings_WithCascade_UpdatesFutureHangouts() {
        // Arrange - create watch party at 19:00
        String groupId = createTestGroup("WP Update Cascade");
        String seriesId = createTestWatchPartyWithMultipleEpisodes(groupId, "Cascade Test", "19:00");

        long originalStartTime = getFirstHangoutStartTime(groupId, seriesId);

        // Act - change time from 19:00 to 21:00 (2 hour shift) with cascade enabled
        given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType("application/json")
            .body("""
                {
                  "defaultTime": "21:00",
                  "changeExistingUpcomingHangouts": true
                }
                """)
        .when()
            .put("/groups/" + groupId + "/watch-parties/" + seriesId)
        .then()
            .statusCode(200);

        // Assert - hangout time should shift by 2 hours (7200 seconds)
        long newStartTime = getFirstHangoutStartTime(groupId, seriesId);
        long expectedStartTime = originalStartTime + 7200; // +2 hours

        // Use a small tolerance to account for any minor processing delays
        Assertions.assertTrue(
            Math.abs(newStartTime - expectedStartTime) <= 10,
            "Hangout start time should have shifted by exactly 2 hours. Expected: " + expectedStartTime + ", but was: " + newStartTime
        );
    }

    @Test
    @Order(2)
    @DisplayName("Update settings with cascade=false preserves existing hangout timestamps")
    void updateSettings_WithoutCascade_PreservesExistingHangouts() {
        // Arrange - create watch party at 19:00
        String groupId = createTestGroup("WP No Cascade");
        String seriesId = createTestWatchPartyWithMultipleEpisodes(groupId, "No Cascade Test", "19:00");

        long originalStartTime = getFirstHangoutStartTime(groupId, seriesId);

        // Act - change time from 19:00 to 21:00 but with cascade disabled
        given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType("application/json")
            .body("""
                {
                  "defaultTime": "21:00",
                  "changeExistingUpcomingHangouts": false
                }
                """)
        .when()
            .put("/groups/" + groupId + "/watch-parties/" + seriesId)
        .then()
            .statusCode(200);

        // Assert - hangout time should be unchanged
        long newStartTime = getFirstHangoutStartTime(groupId, seriesId);

        Assertions.assertEquals(
            originalStartTime, newStartTime,
            "Hangout timestamp should be unchanged when cascade=false"
        );

        // Also verify the series defaultTime was updated (series settings change, hangouts don't)
        given()
            .header("Authorization", "Bearer " + testUserToken)
        .when()
            .get("/groups/" + groupId + "/watch-parties/" + seriesId)
        .then()
            .statusCode(200)
            .body("defaultTime", equalTo("21:00"));
    }

    @Test
    @Order(3)
    @DisplayName("Update settings does not modify past hangouts even with cascade=true")
    void updateSettings_DoesNotModifyPastHangouts() {
        // Arrange - create watch party with one past and one future episode
        // Note: This test requires the ability to create past hangouts
        // If the API prevents this, we use a workaround by checking that only
        // future hangouts are modified when both exist

        String groupId = createTestGroup("WP Past Protected");

        // Create with a past episode (3 days ago) and future episode (1 week out)
        // Note: We use 3 days ago (not 1 day) to ensure the hangout's startTimestamp
        // is definitively in the past. The startTimestamp is calculated from the air date
        // plus defaultTime in the configured timezone, so 1 day ago can result in a
        // hangout that's still in the future depending on when the test runs.
        long pastTimestamp = Instant.now().minusSeconds(86400 * 3).getEpochSecond();
        long futureTimestamp = Instant.now().plusSeconds(86400 * 7).getEpochSecond();
        int showId = Math.abs("Past Test".hashCode() % 90000) + 10000;
        String uniqueShowName = "Past Test " + UUID.randomUUID().toString().substring(0, 8);

        String requestBody = """
            {
              "showId": %d,
              "seasonNumber": 1,
              "showName": "%s",
              "defaultTime": "19:00",
              "timezone": "America/Los_Angeles",
              "episodes": [
                { "episodeId": 1, "title": "Past Episode", "airTimestamp": %d, "runtime": 60 },
                { "episodeId": 2, "title": "Future Episode", "airTimestamp": %d, "runtime": 60 }
              ]
            }
            """.formatted(showId, uniqueShowName, pastTimestamp, futureTimestamp);

        String seriesId = given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/groups/" + groupId + "/watch-parties")
        .then()
            .statusCode(201)
        .extract()
            .jsonPath().getString("seriesId");

        createdSeriesIds.add(seriesId);
        seriesGroupMap.put(seriesId, groupId);

        // Get original timestamps for both hangouts
        var response = given()
            .header("Authorization", "Bearer " + testUserToken)
        .when()
            .get("/groups/" + groupId + "/watch-parties/" + seriesId)
        .then()
            .statusCode(200)
        .extract()
            .response();

        // Find the past hangout's timestamp (the one with earlier timestamp)
        long timestamp0 = response.jsonPath().getLong("hangouts[0].startTimestamp");
        long timestamp1 = response.jsonPath().getLong("hangouts[1].startTimestamp");

        long pastHangoutTimestamp = Math.min(timestamp0, timestamp1);
        long futureHangoutTimestamp = Math.max(timestamp0, timestamp1);
        int pastHangoutIndex = timestamp0 < timestamp1 ? 0 : 1;

        // Act - update with cascade=true
        given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType("application/json")
            .body("""
                {
                  "defaultTime": "21:00",
                  "changeExistingUpcomingHangouts": true
                }
                """)
        .when()
            .put("/groups/" + groupId + "/watch-parties/" + seriesId)
        .then()
            .statusCode(200);

        // Assert - past hangout should be unchanged
        var updatedResponse = given()
            .header("Authorization", "Bearer " + testUserToken)
        .when()
            .get("/groups/" + groupId + "/watch-parties/" + seriesId)
        .then()
            .statusCode(200)
        .extract()
            .response();

        long newPastTimestamp = updatedResponse.jsonPath().getLong("hangouts[" + pastHangoutIndex + "].startTimestamp");

        Assertions.assertEquals(
            pastHangoutTimestamp, newPastTimestamp,
            "Past hangout timestamp should never be modified"
        );
    }

    @Test
    @Order(4)
    @DisplayName("Update timezone with cascade=true recalculates hangout timestamps correctly")
    void updateSettings_TimezoneChange_RecalculatesTimestamps() {
        // Arrange - create watch party at 20:00 America/Los_Angeles (PST/PDT)
        String groupId = createTestGroup("WP Timezone Change");
        String seriesId = createTestWatchPartyWithMultipleEpisodes(groupId, "Timezone Test", "20:00");

        long originalStartTime = getFirstHangoutStartTime(groupId, seriesId);

        // Act - change timezone to America/New_York (EST/EDT) keeping same local time (20:00)
        // EST is UTC-5, PST is UTC-8, so same local time in EST is 3 hours earlier in UTC
        given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType("application/json")
            .body("""
                {
                  "timezone": "America/New_York",
                  "changeExistingUpcomingHangouts": true
                }
                """)
        .when()
            .put("/groups/" + groupId + "/watch-parties/" + seriesId)
        .then()
            .statusCode(200);

        // Assert - hangout should shift by the timezone difference
        // Calculate expected difference dynamically to handle DST edge cases
        LocalDate hangoutDate = LocalDate.now().plusDays(7);
        ZonedDateTime laTime = hangoutDate.atTime(20, 0).atZone(ZoneId.of("America/Los_Angeles"));
        ZonedDateTime nyTime = hangoutDate.atTime(20, 0).atZone(ZoneId.of("America/New_York"));
        long expectedDifferenceSeconds = Duration.between(nyTime.toInstant(), laTime.toInstant()).getSeconds();

        long newStartTime = getFirstHangoutStartTime(groupId, seriesId);
        long actualDifference = originalStartTime - newStartTime;

        // Allow 10 second tolerance for processing
        Assertions.assertTrue(
            Math.abs(actualDifference - expectedDifferenceSeconds) <= 10,
            "Hangout should shift by ~" + expectedDifferenceSeconds + "s when moving from LA to NY time, " +
            "but shifted by " + actualDifference + "s"
        );

        // Also verify the series timezone was updated
        given()
            .header("Authorization", "Bearer " + testUserToken)
        .when()
            .get("/groups/" + groupId + "/watch-parties/" + seriesId)
        .then()
            .statusCode(200)
            .body("timezone", equalTo("America/New_York"));
    }
}
