package com.bbthechange.inviter.staging.watchparty;

import com.bbthechange.inviter.staging.StagingTestBase;
import org.junit.jupiter.api.*;
import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.Matchers.*;

import java.util.List;
import java.util.Map;

/**
 * Phase 5 Integration Tests: Watch Party Interest & Version Filtering
 *
 * Tests the ability for users to express interest in watch party series
 * and the version-based filtering of watch parties in the group feed.
 *
 * Prerequisites:
 * - Phase 2 (Basic CRUD) must be implemented
 * - Phase 3 (TVMaze) can be stubbed or implemented
 * - SeriesPointer.interestLevels field must exist
 * - Group feed must filter by eventSeriesType
 */
@DisplayName("Tier 3: Watch Party Interest & Filtering - Phase 5")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WatchPartyInterestTests extends StagingTestBase {

    @Test
    @Order(1)
    @DisplayName("Set interest to GOING stores interest level on series")
    void setInterest_Going_StoresInterestLevel() {
        // Arrange - create watch party
        String groupId = createTestGroup("WP Interest GOING");
        String seriesId = createTestWatchParty(groupId, "Interest Test Show");

        // Act - set interest to GOING
        given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType("application/json")
            .body("{\"level\": \"GOING\"}")
        .when()
            .post("/watch-parties/" + seriesId + "/interest")
        .then()
            .statusCode(200);

        // Assert - verify interest is stored
        given()
            .header("Authorization", "Bearer " + testUserToken)
        .when()
            .get("/groups/" + groupId + "/watch-parties/" + seriesId)
        .then()
            .statusCode(200)
            .body("interestLevels", notNullValue())
            .body("interestLevels.find { it.userId == '" + testUserId + "' }.level",
                equalTo("GOING"));
    }

    @Test
    @Order(2)
    @DisplayName("Set interest to INTERESTED stores interest level")
    void setInterest_Interested_StoresInterestLevel() {
        // Arrange
        String groupId = createTestGroup("WP Interest INTERESTED");
        String seriesId = createTestWatchParty(groupId, "Interest Test Show 2");

        // Act - set interest to INTERESTED
        given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType("application/json")
            .body("{\"level\": \"INTERESTED\"}")
        .when()
            .post("/watch-parties/" + seriesId + "/interest")
        .then()
            .statusCode(200);

        // Assert
        given()
            .header("Authorization", "Bearer " + testUserToken)
        .when()
            .get("/groups/" + groupId + "/watch-parties/" + seriesId)
        .then()
            .statusCode(200)
            .body("interestLevels.find { it.userId == '" + testUserId + "' }.level",
                equalTo("INTERESTED"));
    }

    @Test
    @Order(3)
    @DisplayName("Change interest level from GOING to NOT_GOING updates correctly")
    void setInterest_ChangeLevel_UpdatesInterestLevel() {
        // Arrange - create watch party and set initial interest
        String groupId = createTestGroup("WP Interest Change");
        String seriesId = createTestWatchParty(groupId, "Change Interest Show");

        // Set initial interest to GOING
        given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType("application/json")
            .body("{\"level\": \"GOING\"}")
        .when()
            .post("/watch-parties/" + seriesId + "/interest")
        .then()
            .statusCode(200);

        // Act - change to NOT_GOING
        given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType("application/json")
            .body("{\"level\": \"NOT_GOING\"}")
        .when()
            .post("/watch-parties/" + seriesId + "/interest")
        .then()
            .statusCode(200);

        // Assert - verify interest is updated (not duplicated)
        io.restassured.response.Response response = given()
            .header("Authorization", "Bearer " + testUserToken)
        .when()
            .get("/groups/" + groupId + "/watch-parties/" + seriesId)
        .then()
            .statusCode(200)
        .extract()
            .response();

        // Verify single entry with updated level
        List<Map<String, Object>> interestLevels =
            response.jsonPath().getList("interestLevels");

        long userEntryCount = interestLevels.stream()
            .filter(entry -> testUserId.equals(entry.get("userId")))
            .count();

        Assertions.assertEquals(1, userEntryCount,
            "User should have exactly one interest entry, not duplicates");

        // Verify the level is NOT_GOING
        String currentLevel = response.jsonPath()
            .getString("interestLevels.find { it.userId == '" + testUserId + "' }.level");
        Assertions.assertEquals("NOT_GOING", currentLevel,
            "Interest level should be updated to NOT_GOING");
    }

    @Test
    @Order(4)
    @DisplayName("Old app version (< 2.0.0) does not see watch party in group feed")
    void groupFeed_OldAppVersion_FiltersOutWatchParties() {
        // Arrange - create watch party
        String groupId = createTestGroup("WP Version Filter Old");
        String seriesId = createTestWatchParty(groupId, "Version Filter Show");

        // Wait for GSI propagation before testing feed
        await()
            .atMost(5, SECONDS)
            .pollInterval(500, MILLISECONDS)
            .untilAsserted(() -> {
                // Act - request feed with old app version
                io.restassured.response.Response response = given()
                    .header("Authorization", "Bearer " + testUserToken)
                    .header("X-App-Version", "1.9.0")
                .when()
                    .get("/groups/" + groupId + "/feed")
                .then()
                    .statusCode(200)
                .extract()
                    .response();

                // Assert - watch party should NOT be in feed
                String responseBody = response.getBody().asString();

                // Look for the seriesId in the response - it should NOT be there
                Assertions.assertFalse(
                    responseBody.contains(seriesId),
                    "Old app version should NOT see watch party in feed. " +
                    "SeriesId '" + seriesId + "' should not appear in response"
                );
            });
    }

    @Test
    @Order(5)
    @DisplayName("New app version (>= 2.0.0) sees watch party in group feed")
    void groupFeed_NewAppVersion_IncludesWatchParties() {
        // Arrange - create watch party
        String groupId = createTestGroup("WP Version Filter New");
        String seriesId = createTestWatchParty(groupId, "Version Include Show");

        // Wait for GSI propagation before testing feed
        await()
            .atMost(5, SECONDS)
            .pollInterval(500, MILLISECONDS)
            .untilAsserted(() -> {
                // Act - request feed with new app version
                io.restassured.response.Response response = given()
                    .header("Authorization", "Bearer " + testUserToken)
                    .header("X-App-Version", "2.0.0")
                .when()
                    .get("/groups/" + groupId + "/feed")
                .then()
                    .statusCode(200)
                .extract()
                    .response();

                // Assert - watch party SHOULD be in feed
                String responseBody = response.getBody().asString();

                Assertions.assertTrue(
                    responseBody.contains(seriesId),
                    "New app version SHOULD see watch party in feed. " +
                    "SeriesId '" + seriesId + "' should appear in response.\n" +
                    "Response: " + responseBody
                );
            });
    }

    @Test
    @Order(6)
    @DisplayName("New app version (2.1.0) sees watch party in group feed")
    void groupFeed_NewerAppVersion_IncludesWatchParties() {
        // Arrange - create watch party
        String groupId = createTestGroup("WP Version Filter Newer");
        String seriesId = createTestWatchParty(groupId, "Version Newer Show");

        // Wait for GSI propagation
        await()
            .atMost(5, SECONDS)
            .pollInterval(500, MILLISECONDS)
            .untilAsserted(() -> {
                // Act - request feed with newer app version (2.1.0)
                io.restassured.response.Response response = given()
                    .header("Authorization", "Bearer " + testUserToken)
                    .header("X-App-Version", "2.1.0")
                .when()
                    .get("/groups/" + groupId + "/feed")
                .then()
                    .statusCode(200)
                .extract()
                    .response();

                // Assert - watch party SHOULD be in feed
                String responseBody = response.getBody().asString();

                Assertions.assertTrue(
                    responseBody.contains(seriesId),
                    "App version 2.1.0 SHOULD see watch party in feed"
                );
            });
    }

    @Test
    @Order(7)
    @DisplayName("Feed without version header treats as new version (sees watch parties)")
    void groupFeed_NoVersionHeader_IncludesWatchParties() {
        // Arrange - create watch party
        String groupId = createTestGroup("WP No Version Header");
        String seriesId = createTestWatchParty(groupId, "No Version Show");

        // Wait for GSI propagation
        await()
            .atMost(5, SECONDS)
            .pollInterval(500, MILLISECONDS)
            .untilAsserted(() -> {
                // Act - request feed WITHOUT version header (default behavior)
                io.restassured.response.Response response = given()
                    .header("Authorization", "Bearer " + testUserToken)
                    // No X-App-Version header
                .when()
                    .get("/groups/" + groupId + "/feed")
                .then()
                    .statusCode(200)
                .extract()
                    .response();

                // Assert - watch party SHOULD be in feed (web clients, default behavior)
                String responseBody = response.getBody().asString();

                Assertions.assertTrue(
                    responseBody.contains(seriesId),
                    "Request without version header SHOULD see watch party (web client behavior)"
                );
            });
    }
}
