package com.bbthechange.inviter.staging.infrastructure;

import com.bbthechange.inviter.staging.StagingTestBase;
import org.junit.jupiter.api.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

@DisplayName("Tier 4: Infrastructure Features - Live Staging")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InfrastructureTests extends StagingTestBase {

    @Test
    @Order(1)
    @DisplayName("Group feed ETag returns 304 when not modified")
    void groupFeedETag_NotModified_Returns304() {
        String groupId = createTestGroup("ETag Test Group");
        createTestHangout(groupId, "ETag Test Hangout");

        // Wait for feed to be consistent
        await()
            .atMost(2, SECONDS)
            .pollInterval(100, MILLISECONDS)
            .untilAsserted(() -> {
                given()
                    .header("Authorization", "Bearer " + testUserToken)
                .when()
                    .get("/groups/" + groupId + "/feed")
                .then()
                    .statusCode(200)
                    .header("ETag", notNullValue());
            });

        // First request to get ETag
        String etag = given()
            .header("Authorization", "Bearer " + testUserToken)
        .when()
            .get("/groups/" + groupId + "/feed")
        .then()
            .statusCode(200)
            .header("ETag", notNullValue())
        .extract()
            .header("ETag");

        // Second request with If-None-Match should return 304
        given()
            .header("Authorization", "Bearer " + testUserToken)
            .header("If-None-Match", etag)
        .when()
            .get("/groups/" + groupId + "/feed")
        .then()
            .statusCode(304);
    }

    @Test
    @Order(2)
    @DisplayName("Calendar feed returns iCalendar data")
    void calendarFeed_ValidToken_ReturnsICS() {
        String groupId = createTestGroup("Calendar Test Group");

        // Get calendar subscription response
        io.restassured.response.Response subscriptionResponse = given()
            .header("Authorization", "Bearer " + testUserToken)
        .when()
            .post("/calendar/subscriptions/" + groupId)
        .then()
            .statusCode(201)
            .body("subscriptionUrl", notNullValue())
            .body("webcalUrl", notNullValue())
        .extract()
            .response();

        String subscriptionUrl = subscriptionResponse.jsonPath().getString("subscriptionUrl");

        // Extract token from URL (last segment after final '/')
        String calendarToken = subscriptionUrl.substring(subscriptionUrl.lastIndexOf('/') + 1);

        // Wait for CalendarTokenIndex GSI to be consistent
        try { Thread.sleep(1000); } catch (InterruptedException e) {}

        // Fetch calendar feed
        given()
        .when()
            .get("/calendar/feed/" + groupId + "/" + calendarToken)
        .then()
            .statusCode(200)
            .contentType(containsString("text/calendar"))
            .body(containsString("BEGIN:VCALENDAR"))
            .body(containsString("END:VCALENDAR"));
    }
}
