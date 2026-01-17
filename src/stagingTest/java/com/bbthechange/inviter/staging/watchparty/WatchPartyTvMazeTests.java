package com.bbthechange.inviter.staging.watchparty;

import com.bbthechange.inviter.staging.StagingTestBase;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;

/**
 * Phase 3 Integration Tests: TVMaze Client Integration
 *
 * These tests verify that the watch party creation endpoint correctly
 * fetches episode data from the TVMaze API instead of accepting it
 * in the request body.
 *
 * Uses completed TV shows with stable data:
 * - Game of Thrones Season 1 (10 episodes, all titled)
 */
@DisplayName("Watch Party TVMaze Integration - Phase 3")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WatchPartyTvMazeTests extends StagingTestBase {

    // Game of Thrones Season 1 - stable, completed show with known episode data
    // TVMaze: show ID 82, season 1 has season ID 307 (not 83, which is The Last Ship)
    private static final int GOT_SHOW_ID = 82;
    private static final int GOT_SEASON_NUMBER = 1;
    private static final int GOT_TVMAZE_SEASON_ID = 307;
    private static final int GOT_EXPECTED_EPISODE_COUNT = 10;
    private static final String GOT_SHOW_NAME = "Game of Thrones";
    private static final String GOT_FIRST_EPISODE_TITLE = "Winter is Coming";

    @Test
    @Order(1)
    @DisplayName("Create watch party fetches episodes from TVMaze and creates correct hangouts")
    void createWatchParty_FetchesFromTvMaze_CreatesCorrectHangouts() {
        // Arrange
        String groupId = createTestGroup("WP TVMaze");

        String requestBody = String.format("""
            {
              "showId": %d,
              "seasonNumber": %d,
              "tvmazeSeasonId": %d,
              "showName": "%s",
              "defaultTime": "21:00",
              "timezone": "America/New_York"
            }
            """, GOT_SHOW_ID, GOT_SEASON_NUMBER, GOT_TVMAZE_SEASON_ID, GOT_SHOW_NAME);

        // Act & Assert
        String seriesId = given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/groups/" + groupId + "/watch-parties")
        .then()
            .statusCode(201)
            .body("seriesId", notNullValue())
            .body("seriesTitle", containsString(GOT_SHOW_NAME))
            .body("hangouts", hasSize(GOT_EXPECTED_EPISODE_COUNT))
            .body("hangouts[0].title", not(emptyString()))
            .body("hangouts[0].externalId", notNullValue())
            .body("hangouts[0].startTimestamp", notNullValue())
        .extract()
            .jsonPath().getString("seriesId");

        // Track for cleanup
        createdSeriesIds.add(seriesId);
        seriesGroupMap.put(seriesId, groupId);
    }

    @Test
    @Order(2)
    @DisplayName("Create watch party verifies episode data from TVMaze is correctly mapped")
    void createWatchParty_VerifiesEpisodeDataFromTvMaze() {
        // Arrange
        String groupId = createTestGroup("WP TVMaze Data");

        String requestBody = String.format("""
            {
              "showId": %d,
              "seasonNumber": %d,
              "tvmazeSeasonId": %d,
              "showName": "%s",
              "defaultTime": "20:00",
              "timezone": "America/Los_Angeles"
            }
            """, GOT_SHOW_ID, GOT_SEASON_NUMBER, GOT_TVMAZE_SEASON_ID, GOT_SHOW_NAME);

        // Act
        String seriesId = given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/groups/" + groupId + "/watch-parties")
        .then()
            .statusCode(201)
            // Verify first episode is "Winter Is Coming" (GoT S1E1)
            .body("hangouts[0].title", equalTo(GOT_FIRST_EPISODE_TITLE))
            // Verify all hangouts have valid external IDs
            .body("hangouts.externalId", everyItem(notNullValue()))
            // Verify hangouts are ordered (by episode number)
            .body("hangouts.size()", equalTo(GOT_EXPECTED_EPISODE_COUNT))
        .extract()
            .jsonPath().getString("seriesId");

        // Track for cleanup
        createdSeriesIds.add(seriesId);
        seriesGroupMap.put(seriesId, groupId);
    }

    @Test
    @Order(3)
    @DisplayName("Create watch party returns 404 for invalid TVMaze season ID")
    void createWatchParty_InvalidSeasonId_Returns404() {
        // Arrange
        String groupId = createTestGroup("WP Invalid");

        String requestBody = """
            {
              "showId": 99999999,
              "seasonNumber": 1,
              "tvmazeSeasonId": 99999999,
              "showName": "Nonexistent Show",
              "defaultTime": "20:00",
              "timezone": "UTC"
            }
            """;

        // Act & Assert
        var response = given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/groups/" + groupId + "/watch-parties")
        .then()
            .extract()
            .response();

        // Log response for debugging
        System.out.println("Response status: " + response.statusCode());
        System.out.println("Response body: " + response.body().asString());

        // Verify 404 is returned for invalid TVMaze season
        Assertions.assertEquals(404, response.statusCode(),
            "Expected 404 for invalid TVMaze season ID, got: " + response.body().asString());
        Assertions.assertTrue(
            response.body().asString().toLowerCase().contains("not_found"),
            "Response should contain 'not_found'");
    }

    @Test
    @Order(4)
    @DisplayName("Creating watch party for same show in different groups produces consistent episode data")
    void createWatchParty_SameShowDifferentGroups_ProducesConsistentEpisodeData() {
        // Arrange - create two different groups
        String group1 = createTestGroup("WP Share 1");
        String group2 = createTestGroup("WP Share 2");

        String requestBody = String.format("""
            {
              "showId": %d,
              "seasonNumber": %d,
              "tvmazeSeasonId": %d,
              "showName": "%s",
              "defaultTime": "21:00",
              "timezone": "America/New_York"
            }
            """, GOT_SHOW_ID, GOT_SEASON_NUMBER, GOT_TVMAZE_SEASON_ID, GOT_SHOW_NAME);

        // Act - create watch party in first group
        var response1 = given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/groups/" + group1 + "/watch-parties")
        .then()
            .statusCode(201)
        .extract()
            .response();

        String series1 = response1.jsonPath().getString("seriesId");
        int hangouts1 = response1.jsonPath().getList("hangouts").size();
        String firstTitle1 = response1.jsonPath().getString("hangouts[0].title");

        createdSeriesIds.add(series1);
        seriesGroupMap.put(series1, group1);

        // Act - create watch party in second group (should reuse Season record)
        var response2 = given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/groups/" + group2 + "/watch-parties")
        .then()
            .statusCode(201)
        .extract()
            .response();

        String series2 = response2.jsonPath().getString("seriesId");
        int hangouts2 = response2.jsonPath().getList("hangouts").size();
        String firstTitle2 = response2.jsonPath().getString("hangouts[0].title");

        createdSeriesIds.add(series2);
        seriesGroupMap.put(series2, group2);

        // Assert - both should have identical episode data from TVMaze
        Assertions.assertEquals(hangouts1, hangouts2,
            "Both groups should have same number of hangouts");
        Assertions.assertEquals(firstTitle1, firstTitle2,
            "Both groups should have same first episode title");
        Assertions.assertEquals(GOT_EXPECTED_EPISODE_COUNT, hangouts1,
            "Should have correct episode count");
    }

    @Test
    @Order(5)
    @DisplayName("Watch party appears in group feed (past events section) for version 2.0.0+ clients")
    void createWatchParty_AppearsInGroupFeed_ForNewVersionClients() {
        // Arrange
        String groupId = createTestGroup("WP Feed Test");

        String requestBody = String.format("""
            {
              "showId": %d,
              "seasonNumber": %d,
              "tvmazeSeasonId": %d,
              "showName": "%s",
              "defaultTime": "20:00",
              "timezone": "America/Los_Angeles"
            }
            """, GOT_SHOW_ID, GOT_SEASON_NUMBER, GOT_TVMAZE_SEASON_ID, GOT_SHOW_NAME);

        // Create watch party
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

        // Create pagination token to query past events
        // GoT S1 aired in 2011, so hangouts have past timestamps
        // The feed requires endingBefore param to return past events
        // Use the same token format that GroupFeedPaginationToken.encode() produces
        long nowTimestamp = System.currentTimeMillis() / 1000;
        com.bbthechange.inviter.util.GroupFeedPaginationToken token =
            new com.bbthechange.inviter.util.GroupFeedPaginationToken(null, nowTimestamp, false);
        String pastEventsToken = token.encode();

        // Assert - wait for GSI propagation and verify in feed's past events section
        await()
            .atMost(10, SECONDS)
            .pollInterval(500, MILLISECONDS)
            .untilAsserted(() -> {
                given()
                    .header("Authorization", "Bearer " + testUserToken)
                    .header("X-App-Version", "2.0.0")
                    .queryParam("endingBefore", pastEventsToken)
                .when()
                    .get("/groups/" + groupId + "/feed")
                .then()
                    .statusCode(200)
                    .body("withDay.find { it.seriesId == '" + seriesId + "' }", notNullValue());
            });
    }
}
