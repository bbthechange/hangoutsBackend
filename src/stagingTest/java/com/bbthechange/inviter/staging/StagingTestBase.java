package com.bbthechange.inviter.staging;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import java.util.*;

import static io.restassured.RestAssured.given;

public abstract class StagingTestBase {

    protected static String stagingUrl;
    protected static String testUserPhone;
    protected static String testUserPassword;
    protected static String testUserToken;
    protected static String testUserId;
    private static List<String> orphanedResources = new ArrayList<>();

    protected List<String> createdGroupIds = new ArrayList<>();
    protected List<String> createdHangoutIds = new ArrayList<>();

    @BeforeAll
    static void setupConfig() {
        stagingUrl = System.getProperty("staging.url");
        testUserPhone = System.getProperty("staging.test.phone");
        testUserPassword = System.getProperty("staging.test.password");

        RestAssured.baseURI = stagingUrl;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        testUserToken = loginAndGetToken(testUserPhone, testUserPassword);
    }

    @BeforeEach
    void setup() {
        createdGroupIds.clear();
        createdHangoutIds.clear();
    }

    @AfterEach
    void cleanup() {
        // Delete hangouts first (foreign key constraints)
        for (String hangoutId : createdHangoutIds) {
            retryCleanup(() -> deleteHangout(hangoutId), "hangout", hangoutId);
        }

        // Then delete groups
        for (String groupId : createdGroupIds) {
            retryCleanup(() -> deleteGroup(groupId), "group", groupId);
        }

        createdHangoutIds.clear();
        createdGroupIds.clear();
    }

    private void retryCleanup(Runnable cleanup, String type, String id) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                cleanup.run();
                return;
            } catch (Exception e) {
                if (attempt == 3) {
                    String resource = type + ":" + id;
                    orphanedResources.add(resource);
                    System.err.println("⚠ Failed to cleanup " + type + " " + id);
                    System.err.println("  Add to manual cleanup list: " + resource);
                } else {
                    try { Thread.sleep(500 * attempt); } catch (InterruptedException ie) {}
                }
            }
        }
    }

    @AfterAll
    static void reportOrphanedResources() {
        if (!orphanedResources.isEmpty()) {
            System.err.println("\n⚠ ORPHANED TEST RESOURCES:");
            orphanedResources.forEach(r -> System.err.println("  - " + r));
            System.err.println("  Run manual cleanup script to remove these.\n");
        }
    }

    protected static String loginAndGetToken(String phone, String password) {
        Response response = given()
            .contentType("application/json")
            .body(String.format("{\"phoneNumber\":\"%s\",\"password\":\"%s\"}", phone, password))
        .when()
            .post("/auth/login")
        .then()
            .statusCode(200)
        .extract()
            .response();

        testUserId = response.jsonPath().getString("user.id");
        return response.jsonPath().getString("accessToken");
    }

    protected String createTestGroup(String namePrefix) {
        String groupName = namePrefix + " " + UUID.randomUUID().toString().substring(0, 8);

        String groupId = given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType("application/json")
            .body(String.format("{\"groupName\":\"%s\",\"public\":true}", groupName))
        .when()
            .post("/groups")
        .then()
            .statusCode(201)
        .extract()
            .jsonPath()
            .getString("groupId");

        createdGroupIds.add(groupId);
        return groupId;
    }

    protected String createTestHangout(String groupId, String title) {
        return createTestHangoutWithExactTime(groupId, title);
    }

    protected String createTestHangoutWithExactTime(String groupId, String title) {
        String hangoutTitle = title + " " + UUID.randomUUID().toString().substring(0, 8);

        // Create hangout with exact time (tomorrow at 3pm-5pm) so it appears in group feed
        java.time.Instant tomorrow3pm = java.time.Instant.now()
            .plus(1, java.time.temporal.ChronoUnit.DAYS)
            .atZone(java.time.ZoneId.of("America/Los_Angeles"))
            .withHour(15)
            .withMinute(0)
            .withSecond(0)
            .toInstant();

        java.time.Instant tomorrow5pm = tomorrow3pm.plus(2, java.time.temporal.ChronoUnit.HOURS);

        String startTime = tomorrow3pm.toString();
        String endTime = tomorrow5pm.toString();

        String hangoutId = given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType("application/json")
            .body(String.format(
                "{\"title\":\"%s\",\"description\":\"Test hangout\",\"visibility\":\"PUBLIC\",\"associatedGroups\":[\"%s\"],\"timeInfo\":{\"startTime\":\"%s\",\"endTime\":\"%s\"}}",
                hangoutTitle, groupId, startTime, endTime))
        .when()
            .post("/hangouts")
        .then()
            .statusCode(201)
        .extract()
            .jsonPath()
            .getString("hangoutId");

        createdHangoutIds.add(hangoutId);
        return hangoutId;
    }

    protected String createTestHangoutWithFuzzyTime(String groupId, String title, String periodGranularity) {
        String hangoutTitle = title + " " + UUID.randomUUID().toString().substring(0, 8);

        // Create hangout with fuzzy time (tomorrow) so it appears in group feed
        // Use ISO-8601 format with timezone for periodStart
        String tomorrowDate = java.time.LocalDate.now()
            .plusDays(1)
            .atStartOfDay(java.time.ZoneId.of("America/Los_Angeles"))
            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        String hangoutId = given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType("application/json")
            .body(String.format(
                "{\"title\":\"%s\",\"description\":\"Test hangout\",\"visibility\":\"PUBLIC\",\"associatedGroups\":[\"%s\"],\"timeInfo\":{\"periodGranularity\":\"%s\",\"periodStart\":\"%s\"}}",
                hangoutTitle, groupId, periodGranularity, tomorrowDate))
        .when()
            .post("/hangouts")
        .then()
            .statusCode(201)
        .extract()
            .jsonPath()
            .getString("hangoutId");

        createdHangoutIds.add(hangoutId);
        return hangoutId;
    }

    protected void deleteGroup(String groupId) {
        given()
            .header("Authorization", "Bearer " + testUserToken)
        .when()
            .delete("/groups/" + groupId)
        .then()
            .statusCode(204);
    }

    protected void deleteHangout(String hangoutId) {
        given()
            .header("Authorization", "Bearer " + testUserToken)
        .when()
            .delete("/hangouts/" + hangoutId)
        .then()
            .statusCode(204);
    }
}
