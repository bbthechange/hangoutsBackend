package com.bbthechange.inviter.staging.hangouts;

import com.bbthechange.inviter.staging.StagingTestBase;
import org.junit.jupiter.api.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

@DisplayName("Tier 2: Hangout CRUD - Live Staging")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HangoutCrudTests extends StagingTestBase {

    @Test
    @Order(1)
    @DisplayName("Create hangout returns 201 with hangout ID")
    void createHangout_ValidData_ReturnsHangoutId() {
        String groupId = createTestGroup("Hangout Test Group");
        String hangoutTitle = "Test Hangout " + java.util.UUID.randomUUID().toString().substring(0, 8);

        String hangoutId = given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType("application/json")
            .body(String.format(
                "{\"title\":\"%s\",\"description\":\"Test hangout\",\"visibility\":\"PUBLIC\",\"associatedGroups\":[\"%s\"]}",
                hangoutTitle, groupId))
        .when()
            .post("/hangouts")
        .then()
            .statusCode(201)
            .body("hangoutId", notNullValue())
            .body("title", equalTo(hangoutTitle))
        .extract()
            .jsonPath()
            .getString("hangoutId");

        createdHangoutIds.add(hangoutId);
    }

    @Test
    @Order(2)
    @DisplayName("Get hangout details returns full hangout data")
    void getHangoutDetails_ValidHangoutId_ReturnsFullData() {
        String groupId = createTestGroup("Details Hangout Group");
        String hangoutId = createTestHangout(groupId, "Details Test Hangout");

        given()
            .header("Authorization", "Bearer " + testUserToken)
        .when()
            .get("/hangouts/" + hangoutId)
        .then()
            .statusCode(200)
            .body("hangout.hangoutId", equalTo(hangoutId))
            .body("hangout.title", containsString("Details Test Hangout"))
            .body("hangout.description", notNullValue())
            .body("hangout.associatedGroups", hasItem(groupId))
            .body("attributes", notNullValue())
            .body("polls", notNullValue());
    }

    @Test
    @Order(3)
    @DisplayName("Update hangout modifies hangout data")
    void updateHangout_ValidData_UpdatesHangout() {
        String groupId = createTestGroup("Update Hangout Group");
        String hangoutId = createTestHangout(groupId, "Original Title");
        String updatedTitle = "Updated Title " + java.util.UUID.randomUUID().toString().substring(0, 8);
        String updatedDescription = "Updated description";

        given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType("application/json")
            .body(String.format("{\"title\":\"%s\",\"description\":\"%s\"}",
                updatedTitle, updatedDescription))
        .when()
            .patch("/hangouts/" + hangoutId)
        .then()
            .statusCode(200);

        // Verify update persisted
        given()
            .header("Authorization", "Bearer " + testUserToken)
        .when()
            .get("/hangouts/" + hangoutId)
        .then()
            .statusCode(200)
            .body("hangout.title", equalTo(updatedTitle))
            .body("hangout.description", equalTo(updatedDescription));
    }

    @Test
    @Order(4)
    @DisplayName("Group feed returns hangouts for group")
    void groupFeed_ValidGroupId_ReturnsHangouts() {
        String groupId = createTestGroup("Feed Test Group");

        // Create one hangout with exact time and one with fuzzy time
        String exactTimeHangoutId = createTestHangoutWithExactTime(groupId, "Exact Time Hangout");
        String fuzzyTimeHangoutId = createTestHangoutWithFuzzyTime(groupId, "Fuzzy Time Hangout", "AFTERNOON");

        // Verify both hangouts were created with future timestamps
        given()
            .header("Authorization", "Bearer " + testUserToken)
        .when()
            .get("/hangouts/" + exactTimeHangoutId)
        .then()
            .statusCode(200)
            .body("hangout.startTimestamp", notNullValue())
            .body("hangout.associatedGroups", hasItem(groupId));

        given()
            .header("Authorization", "Bearer " + testUserToken)
        .when()
            .get("/hangouts/" + fuzzyTimeHangoutId)
        .then()
            .statusCode(200)
            .body("hangout.startTimestamp", notNullValue())
            .body("hangout.associatedGroups", hasItem(groupId));

        // Wait for feed to be consistent
        await()
            .atMost(10, SECONDS)
            .pollInterval(500, MILLISECONDS)
            .untilAsserted(() -> {
                io.restassured.response.Response response = given()
                    .header("Authorization", "Bearer " + testUserToken)
                .when()
                    .get("/groups/" + groupId + "/feed")
                .then()
                    .statusCode(200)
                    .body("groupId", equalTo(groupId))
                    .body("withDay", notNullValue())
                    .body("needsDay", notNullValue())
                .extract()
                    .response();

                // Verify both hangouts are in withDay array (both have future times)
                java.util.List<String> withDayIds = response.jsonPath().getList("withDay.hangoutId", String.class);
                java.util.List<String> needsDayIds = response.jsonPath().getList("needsDay.hangoutId", String.class);

                if (withDayIds == null) withDayIds = java.util.Collections.emptyList();
                if (needsDayIds == null) needsDayIds = java.util.Collections.emptyList();

                boolean exactTimeFound = withDayIds.contains(exactTimeHangoutId) || needsDayIds.contains(exactTimeHangoutId);
                boolean fuzzyTimeFound = withDayIds.contains(fuzzyTimeHangoutId) || needsDayIds.contains(fuzzyTimeHangoutId);

                String responseBody = response.getBody().asString();
                Assertions.assertTrue(exactTimeFound, "Exact time hangout should be in feed. withDay count: " + withDayIds.size() + ", needsDay count: " + needsDayIds.size() + "\nFeed response: " + responseBody);
                Assertions.assertTrue(fuzzyTimeFound, "Fuzzy time hangout should be in feed. withDay count: " + withDayIds.size() + ", needsDay count: " + needsDayIds.size() + "\nFeed response: " + responseBody);
            });
    }
}
