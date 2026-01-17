package com.bbthechange.inviter.staging.watchparty;

import com.bbthechange.inviter.staging.StagingTestBase;
import org.junit.jupiter.api.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@DisplayName("Watch Party CRUD - Phase 2")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WatchPartyCrudTests extends StagingTestBase {

    @Test
    @Order(1)
    @DisplayName("Create watch party with valid data returns 201 with series and hangouts")
    void createWatchParty_ValidData_Returns201WithSeriesAndHangouts() {
        // Arrange
        String groupId = createTestGroup("WP CRUD");
        long futureTimestamp = Instant.now().plusSeconds(86400 * 7).getEpochSecond();
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);

        String requestBody = """
            {
              "showId": 99999,
              "seasonNumber": 1,
              "showName": "Test Show %s",
              "defaultTime": "20:00",
              "timezone": "America/Los_Angeles",
              "episodes": [
                { "episodeId": 1, "title": "Pilot", "airTimestamp": %d, "runtime": 60 },
                { "episodeId": 2, "title": "Episode 2", "airTimestamp": %d, "runtime": 60 }
              ]
            }
            """.formatted(uniqueId, futureTimestamp, futureTimestamp + 604800);

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
            .body("seriesTitle", containsString("Test Show"))
            .body("hangouts", hasSize(2))
            .body("hangouts[0].title", equalTo("Pilot"))
            .body("hangouts[0].externalId", equalTo("1"))
            .body("hangouts[1].title", equalTo("Episode 2"))
            .body("hangouts[1].externalId", equalTo("2"))
        .extract()
            .jsonPath().getString("seriesId");

        createdSeriesIds.add(seriesId);
        seriesGroupMap.put(seriesId, groupId);
    }

    @Test
    @Order(2)
    @DisplayName("Create watch party applies timezone correctly to hangout timestamps")
    void createWatchParty_AppliesTimezoneCorrectly() {
        // Arrange
        String groupId = createTestGroup("WP Timezone");

        // Use a specific future date to avoid DST edge cases
        LocalDate futureDate = LocalDate.now().plusDays(14);
        long airTimestamp = futureDate.atStartOfDay(ZoneOffset.UTC).toEpochSecond();

        String requestBody = """
            {
              "showId": 99998,
              "seasonNumber": 1,
              "showName": "Timezone Test %s",
              "defaultTime": "20:00",
              "timezone": "America/Los_Angeles",
              "episodes": [
                { "episodeId": 1, "title": "Test Episode", "airTimestamp": %d, "runtime": 60 }
              ]
            }
            """.formatted(UUID.randomUUID().toString().substring(0, 8), airTimestamp);

        // Act
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

        // Assert - verify timestamp is 20:00 LA time on the air date
        ZonedDateTime expected = futureDate.atTime(20, 0).atZone(ZoneId.of("America/Los_Angeles"));
        long expectedTimestamp = expected.toEpochSecond();

        long actualTimestamp = given()
            .header("Authorization", "Bearer " + testUserToken)
        .when()
            .get("/groups/" + groupId + "/watch-parties/" + seriesId)
        .then()
            .statusCode(200)
        .extract()
            .jsonPath().getLong("hangouts[0].startTimestamp");

        Assertions.assertEquals(expectedTimestamp, actualTimestamp,
            "Hangout should be at 20:00 LA time on the air date");
    }

    @Test
    @Order(3)
    @DisplayName("Create watch party with day override shifts hangout to correct day")
    void createWatchParty_DayOverride_ShiftsHangoutToCorrectDay() {
        // Arrange
        String groupId = createTestGroup("WP DayOverride");

        // Find next Monday
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        long mondayTimestamp = nextMonday.atTime(20, 0)
            .atZone(ZoneId.of("America/Los_Angeles")).toEpochSecond();

        String requestBody = """
            {
              "showId": 99997,
              "seasonNumber": 1,
              "showName": "Day Override Test %s",
              "defaultTime": "20:00",
              "timezone": "America/Los_Angeles",
              "dayOverride": 4,
              "episodes": [
                { "episodeId": 1, "title": "Test Episode", "airTimestamp": %d, "runtime": 60 }
              ]
            }
            """.formatted(UUID.randomUUID().toString().substring(0, 8), mondayTimestamp);

        // Act
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

        // Assert - hangout should be on Thursday (3 days after Monday)
        // dayOverride: 4 = Thursday (0=Sun, 1=Mon, 2=Tue, 3=Wed, 4=Thu)
        long expectedTimestamp = nextMonday.plusDays(3).atTime(20, 0)
            .atZone(ZoneId.of("America/Los_Angeles")).toEpochSecond();

        long actualTimestamp = given()
            .header("Authorization", "Bearer " + testUserToken)
        .when()
            .get("/groups/" + groupId + "/watch-parties/" + seriesId)
        .then()
            .statusCode(200)
        .extract()
            .jsonPath().getLong("hangouts[0].startTimestamp");

        Assertions.assertEquals(expectedTimestamp, actualTimestamp,
            "Hangout should be shifted to Thursday (dayOverride=4)");
    }

    @Test
    @Order(4)
    @DisplayName("Create watch party combines episodes within 20 hours into double episode")
    void createWatchParty_EpisodesWithin20Hours_CombinesIntoDoubleEpisode() {
        // Arrange
        String groupId = createTestGroup("WP Combined");
        long baseTime = Instant.now().plusSeconds(86400 * 7).getEpochSecond();

        // Two episodes 30 minutes apart (well within 20 hours)
        String requestBody = """
            {
              "showId": 99996,
              "seasonNumber": 1,
              "showName": "Combined Test %s",
              "defaultTime": "20:00",
              "timezone": "America/Los_Angeles",
              "episodes": [
                { "episodeId": 1, "title": "Part 1", "airTimestamp": %d, "runtime": 30 },
                { "episodeId": 2, "title": "Part 2", "airTimestamp": %d, "runtime": 30 }
              ]
            }
            """.formatted(UUID.randomUUID().toString().substring(0, 8), baseTime, baseTime + 1800);

        // Act & Assert
        String seriesId = given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/groups/" + groupId + "/watch-parties")
        .then()
            .statusCode(201)
            .body("hangouts", hasSize(1))
            .body("hangouts[0].title", equalTo("Double Episode: Part 1, Part 2"))
            .body("hangouts[0].combinedExternalIds", hasSize(2))
            .body("hangouts[0].combinedExternalIds", hasItems("1", "2"))
        .extract()
            .jsonPath().getString("seriesId");

        createdSeriesIds.add(seriesId);
        seriesGroupMap.put(seriesId, groupId);
    }

    @Test
    @Order(5)
    @DisplayName("Create watch party combines three episodes into triple episode")
    void createWatchParty_ThreeEpisodesWithin20Hours_CombinesIntoTripleEpisode() {
        // Arrange
        String groupId = createTestGroup("WP Triple");
        long baseTime = Instant.now().plusSeconds(86400 * 7).getEpochSecond();

        // Three episodes within 20 hours
        String requestBody = """
            {
              "showId": 99995,
              "seasonNumber": 1,
              "showName": "Triple Test %s",
              "defaultTime": "20:00",
              "timezone": "America/Los_Angeles",
              "episodes": [
                { "episodeId": 1, "title": "Part 1", "airTimestamp": %d, "runtime": 30 },
                { "episodeId": 2, "title": "Part 2", "airTimestamp": %d, "runtime": 30 },
                { "episodeId": 3, "title": "Part 3", "airTimestamp": %d, "runtime": 30 }
              ]
            }
            """.formatted(
                UUID.randomUUID().toString().substring(0, 8),
                baseTime, baseTime + 1800, baseTime + 3600);

        // Act & Assert
        String seriesId = given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/groups/" + groupId + "/watch-parties")
        .then()
            .statusCode(201)
            .body("hangouts", hasSize(1))
            .body("hangouts[0].title", equalTo("Triple Episode"))
            .body("hangouts[0].combinedExternalIds", hasSize(3))
        .extract()
            .jsonPath().getString("seriesId");

        createdSeriesIds.add(seriesId);
        seriesGroupMap.put(seriesId, groupId);
    }

    @Test
    @Order(6)
    @DisplayName("Create watch party rounds runtime up to 30 minute increments")
    void createWatchParty_RuntimeRounding_RoundsTo30MinIncrements() {
        // Arrange
        String groupId = createTestGroup("WP Runtime");
        long baseTime = Instant.now().plusSeconds(86400 * 7).getEpochSecond();

        // 38 minute runtime should round up to 60 minutes
        String requestBody = """
            {
              "showId": 99994,
              "seasonNumber": 1,
              "showName": "Runtime Test %s",
              "defaultTime": "20:00",
              "timezone": "UTC",
              "episodes": [
                { "episodeId": 1, "title": "Test Episode", "airTimestamp": %d, "runtime": 38 }
              ]
            }
            """.formatted(UUID.randomUUID().toString().substring(0, 8), baseTime);

        // Act
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

        // Assert - duration should be 60 minutes (3600 seconds)
        var response = given()
            .header("Authorization", "Bearer " + testUserToken)
        .when()
            .get("/groups/" + groupId + "/watch-parties/" + seriesId)
        .then()
            .statusCode(200)
        .extract()
            .response();

        long startTimestamp = response.jsonPath().getLong("hangouts[0].startTimestamp");
        long endTimestamp = response.jsonPath().getLong("hangouts[0].endTimestamp");

        Assertions.assertEquals(3600, endTimestamp - startTimestamp,
            "38 min runtime should round up to 60 min (3600 seconds)");
    }

    @Test
    @Order(7)
    @DisplayName("Get watch party returns full details including hangouts")
    void getWatchParty_ValidSeriesId_ReturnsFullDetails() {
        // Arrange
        String groupId = createTestGroup("WP Get");
        String seriesId = createTestWatchParty(groupId, "Get Test Show");

        // Act & Assert
        given()
            .header("Authorization", "Bearer " + testUserToken)
        .when()
            .get("/groups/" + groupId + "/watch-parties/" + seriesId)
        .then()
            .statusCode(200)
            .body("seriesId", equalTo(seriesId))
            .body("seriesTitle", containsString("Get Test Show"))
            .body("eventSeriesType", equalTo("WATCH_PARTY"))
            .body("defaultTime", notNullValue())
            .body("timezone", notNullValue())
            .body("hangouts", not(empty()));
    }

    @Test
    @Order(8)
    @DisplayName("Delete watch party removes series and all associated hangouts")
    void deleteWatchParty_ValidSeriesId_RemovesSeriesAndHangouts() {
        // Arrange
        String groupId = createTestGroup("WP Delete");
        String seriesId = createTestWatchParty(groupId, "Delete Test");

        // Remove from tracking since we're deleting manually
        createdSeriesIds.remove(seriesId);
        seriesGroupMap.remove(seriesId);

        // Act
        given()
            .header("Authorization", "Bearer " + testUserToken)
        .when()
            .delete("/groups/" + groupId + "/watch-parties/" + seriesId)
        .then()
            .statusCode(204);

        // Assert - series should be gone
        given()
            .header("Authorization", "Bearer " + testUserToken)
        .when()
            .get("/groups/" + groupId + "/watch-parties/" + seriesId)
        .then()
            .statusCode(404);
    }
}
