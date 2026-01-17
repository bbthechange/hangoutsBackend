package com.bbthechange.inviter.staging.watchparty;

import com.bbthechange.inviter.staging.StagingTestBase;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Staging tests for Watch Party SQS functionality.
 * Tests the internal endpoints for triggering SQS messages.
 *
 * Prerequisites:
 * - SQS queues must exist in staging account
 * - watchparty.sqs.enabled=true in staging config
 * - Valid X-Api-Key header required for internal endpoints
 */
@DisplayName("Watch Party SQS - Phase 6")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WatchPartySqsTests extends StagingTestBase {

    private static String internalApiKey;

    @BeforeAll
    static void setupApiKey() {
        // Get internal API key from system property
        internalApiKey = System.getProperty("internal.api-key", "");
    }

    @Test
    @Order(1)
    @DisplayName("Test message endpoint returns 200 with valid tvmaze-updates queue type")
    void testMessage_TvMazeUpdates_Returns200() {
        // Skip if no API key configured
        if (internalApiKey.isEmpty()) {
            System.out.println("Skipping test - no internal API key configured");
            return;
        }

        String messageBody = """
            {
                "type": "SHOW_UPDATED",
                "showId": 99999,
                "messageId": "%s"
            }
            """.formatted(UUID.randomUUID().toString());

        String requestBody = """
            {
                "queueType": "tvmaze-updates",
                "messageBody": %s
            }
            """.formatted(messageBody);

        given()
            .header("X-Api-Key", internalApiKey)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/internal/watch-party/test-message")
        .then()
            .statusCode(200)
            .body("status", equalTo("sent"))
            .body("messageId", notNullValue())
            .body("queue", equalTo("tvmaze-updates"));
    }

    @Test
    @Order(2)
    @DisplayName("Test message endpoint returns 200 with valid episode-actions queue type")
    void testMessage_EpisodeActions_Returns200() {
        if (internalApiKey.isEmpty()) {
            System.out.println("Skipping test - no internal API key configured");
            return;
        }

        String messageBody = """
            {
                "type": "NEW_EPISODE",
                "seasonKey": "TVMAZE#SHOW#99999|SEASON#1",
                "episode": {
                    "episodeId": 1,
                    "title": "Test Episode",
                    "airTimestamp": %d
                },
                "messageId": "%s"
            }
            """.formatted(Instant.now().plusSeconds(86400).getEpochSecond(), UUID.randomUUID().toString());

        String requestBody = """
            {
                "queueType": "episode-actions",
                "messageBody": %s
            }
            """.formatted(messageBody);

        given()
            .header("X-Api-Key", internalApiKey)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/internal/watch-party/test-message")
        .then()
            .statusCode(200)
            .body("status", equalTo("sent"))
            .body("messageId", notNullValue())
            .body("queue", equalTo("episode-actions"));
    }

    @Test
    @Order(3)
    @DisplayName("Test message endpoint returns 400 with invalid queue type")
    void testMessage_InvalidQueueType_Returns400() {
        if (internalApiKey.isEmpty()) {
            System.out.println("Skipping test - no internal API key configured");
            return;
        }

        String requestBody = """
            {
                "queueType": "invalid-queue",
                "messageBody": "{\\"type\\":\\"TEST\\"}"
            }
            """;

        given()
            .header("X-Api-Key", internalApiKey)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/internal/watch-party/test-message")
        .then()
            .statusCode(400)
            .body("error", containsString("invalid-queue"));
    }

    @Test
    @Order(4)
    @DisplayName("Test message endpoint returns 400 with missing queue type")
    void testMessage_MissingQueueType_Returns400() {
        if (internalApiKey.isEmpty()) {
            System.out.println("Skipping test - no internal API key configured");
            return;
        }

        String requestBody = """
            {
                "messageBody": "{\\"type\\":\\"TEST\\"}"
            }
            """;

        given()
            .header("X-Api-Key", internalApiKey)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/internal/watch-party/test-message")
        .then()
            .statusCode(400)
            .body("error", containsString("queueType"));
    }

    @Test
    @Order(5)
    @DisplayName("Trigger show update endpoint returns 200 with valid showId")
    void triggerShowUpdate_ValidShowId_Returns200() {
        if (internalApiKey.isEmpty()) {
            System.out.println("Skipping test - no internal API key configured");
            return;
        }

        String requestBody = """
            {
                "showId": 99999
            }
            """;

        given()
            .header("X-Api-Key", internalApiKey)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/internal/watch-party/trigger-show-update")
        .then()
            .statusCode(200)
            .body("status", equalTo("triggered"))
            .body("messageId", notNullValue())
            .body("showId", equalTo("99999"));
    }

    @Test
    @Order(6)
    @DisplayName("Trigger show update endpoint returns 400 with missing showId")
    void triggerShowUpdate_MissingShowId_Returns400() {
        if (internalApiKey.isEmpty()) {
            System.out.println("Skipping test - no internal API key configured");
            return;
        }

        String requestBody = "{}";

        given()
            .header("X-Api-Key", internalApiKey)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/internal/watch-party/trigger-show-update")
        .then()
            .statusCode(400)
            .body("error", containsString("showId"));
    }

    @Test
    @Order(7)
    @DisplayName("Internal endpoints return 401/403 without API key")
    void internalEndpoints_NoApiKey_ReturnsUnauthorized() {
        // Skip if API key bypass is enabled (local development)
        String bypassEnabled = System.getProperty("internal.api-key-bypass", "false");
        if ("true".equals(bypassEnabled)) {
            System.out.println("Skipping test - API key bypass is enabled");
            return;
        }

        // Skip if no API key is configured (means we're in local dev with bypass)
        // or if the controller isn't loaded (watchparty.sqs.enabled=false)
        if (internalApiKey.isEmpty()) {
            System.out.println("Skipping test - no internal API key configured (local development mode)");
            return;
        }

        String requestBody = """
            {
                "showId": 99999
            }
            """;

        given()
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/internal/watch-party/trigger-show-update")
        .then()
            .statusCode(anyOf(is(401), is(403)));
    }

    @Test
    @Order(8)
    @DisplayName("Update title message type is properly serialized and sent")
    void testMessage_UpdateTitle_Returns200() {
        if (internalApiKey.isEmpty()) {
            System.out.println("Skipping test - no internal API key configured");
            return;
        }

        String messageBody = """
            {
                "type": "UPDATE_TITLE",
                "externalId": "999999",
                "newTitle": "Updated Episode Title",
                "messageId": "%s"
            }
            """.formatted(UUID.randomUUID().toString());

        String requestBody = """
            {
                "queueType": "episode-actions",
                "messageBody": %s
            }
            """.formatted(messageBody);

        given()
            .header("X-Api-Key", internalApiKey)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/internal/watch-party/test-message")
        .then()
            .statusCode(200)
            .body("status", equalTo("sent"))
            .body("queue", equalTo("episode-actions"));
    }

    @Test
    @Order(9)
    @DisplayName("Remove episode message type is properly serialized and sent")
    void testMessage_RemoveEpisode_Returns200() {
        if (internalApiKey.isEmpty()) {
            System.out.println("Skipping test - no internal API key configured");
            return;
        }

        String messageBody = """
            {
                "type": "REMOVE_EPISODE",
                "externalId": "999999",
                "messageId": "%s"
            }
            """.formatted(UUID.randomUUID().toString());

        String requestBody = """
            {
                "queueType": "episode-actions",
                "messageBody": %s
            }
            """.formatted(messageBody);

        given()
            .header("X-Api-Key", internalApiKey)
            .contentType("application/json")
            .body(requestBody)
        .when()
            .post("/internal/watch-party/test-message")
        .then()
            .statusCode(200)
            .body("status", equalTo("sent"))
            .body("queue", equalTo("episode-actions"));
    }
}
