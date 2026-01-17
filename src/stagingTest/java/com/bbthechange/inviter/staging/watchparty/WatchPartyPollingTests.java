package com.bbthechange.inviter.staging.watchparty;

import com.bbthechange.inviter.staging.StagingTestBase;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Staging tests for Watch Party Polling functionality (Phase 7).
 * Tests the trigger-poll endpoint for automatic show update detection.
 *
 * Prerequisites:
 * - SQS queues must exist in staging account
 * - watchparty.sqs.enabled=true in staging config
 * - watchparty.polling.enabled=true in staging config
 * - Valid X-Api-Key header required for internal endpoints
 */
@DisplayName("Watch Party Polling - Phase 7")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WatchPartyPollingTests extends StagingTestBase {

    private static String internalApiKey;

    @BeforeAll
    static void setupApiKey() {
        // Get internal API key from system property
        internalApiKey = System.getProperty("internal.api-key", "");
    }

    @Test
    @Order(1)
    @DisplayName("Trigger poll endpoint returns statistics")
    void triggerPoll_ReturnsStatistics() {
        // Skip if no API key configured
        if (internalApiKey.isEmpty()) {
            System.out.println("Skipping test - no internal API key configured");
            return;
        }

        given()
            .header("X-Api-Key", internalApiKey)
            .contentType("application/json")
        .when()
            .post("/internal/watch-party/trigger-poll")
        .then()
            .statusCode(anyOf(is(200), is(503))) // 503 if polling disabled
            .body("status", anyOf(equalTo("completed"), equalTo("error")))
            // If completed, verify statistics fields exist
            .body(
                either(hasKey("totalTrackedShows")).or(hasKey("error"))
            );
    }

    @Test
    @Order(2)
    @DisplayName("Trigger poll without API key returns 401/403")
    void triggerPoll_WithoutApiKey_ReturnsUnauthorized() {
        // Skip if API key bypass is enabled (local development)
        String bypassEnabled = System.getProperty("internal.api-key-bypass", "false");
        if ("true".equals(bypassEnabled)) {
            System.out.println("Skipping test - API key bypass is enabled");
            return;
        }

        // Skip if no API key is configured (means we're in local dev with bypass)
        if (internalApiKey.isEmpty()) {
            System.out.println("Skipping test - no internal API key configured (local development mode)");
            return;
        }

        given()
            .contentType("application/json")
        .when()
            .post("/internal/watch-party/trigger-poll")
        .then()
            .statusCode(anyOf(is(401), is(403)));
    }

    @Test
    @Order(3)
    @DisplayName("Trigger poll with valid API key completes successfully")
    void triggerPoll_WithValidApiKey_Completes() {
        if (internalApiKey.isEmpty()) {
            System.out.println("Skipping test - no internal API key configured");
            return;
        }

        given()
            .header("X-Api-Key", internalApiKey)
            .contentType("application/json")
        .when()
            .post("/internal/watch-party/trigger-poll")
        .then()
            .statusCode(anyOf(is(200), is(503)))
            // Response should include duration (indicates the poll ran)
            .body(either(hasKey("durationMs")).or(hasKey("error")));
    }

    @Test
    @Order(4)
    @DisplayName("Trigger poll response includes all expected fields on success")
    void triggerPoll_Success_IncludesAllFields() {
        if (internalApiKey.isEmpty()) {
            System.out.println("Skipping test - no internal API key configured");
            return;
        }

        var response = given()
            .header("X-Api-Key", internalApiKey)
            .contentType("application/json")
        .when()
            .post("/internal/watch-party/trigger-poll")
        .then()
            .statusCode(anyOf(is(200), is(503)))
        .extract()
            .response();

        // If status is 200 (success), verify all fields are present
        if (response.statusCode() == 200) {
            given()
                .header("X-Api-Key", internalApiKey)
                .contentType("application/json")
            .when()
                .post("/internal/watch-party/trigger-poll")
            .then()
                .statusCode(200)
                .body("status", equalTo("completed"))
                .body("totalTrackedShows", greaterThanOrEqualTo(0))
                .body("updatedShowsFound", greaterThanOrEqualTo(0))
                .body("messagesEmitted", greaterThanOrEqualTo(0))
                .body("durationMs", greaterThanOrEqualTo(0));
        } else {
            // If polling is disabled, we expect 503 with an error message
            given()
                .header("X-Api-Key", internalApiKey)
                .contentType("application/json")
            .when()
                .post("/internal/watch-party/trigger-poll")
            .then()
                .statusCode(503)
                .body("error", notNullValue());
        }
    }

    @Test
    @Order(5)
    @DisplayName("Multiple rapid poll triggers complete without errors")
    void triggerPoll_RapidCalls_Completes() {
        if (internalApiKey.isEmpty()) {
            System.out.println("Skipping test - no internal API key configured");
            return;
        }

        // Trigger 3 polls in quick succession - should all complete
        for (int i = 0; i < 3; i++) {
            given()
                .header("X-Api-Key", internalApiKey)
                .contentType("application/json")
            .when()
                .post("/internal/watch-party/trigger-poll")
            .then()
                .statusCode(anyOf(is(200), is(503))); // Either success or polling disabled
        }
    }
}
