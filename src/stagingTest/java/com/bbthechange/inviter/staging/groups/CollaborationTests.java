package com.bbthechange.inviter.staging.groups;

import com.bbthechange.inviter.staging.StagingTestBase;
import org.junit.jupiter.api.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@DisplayName("Tier 3: Collaboration Features - Live Staging")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CollaborationTests extends StagingTestBase {

    private static final String SECONDARY_USER_PHONE = "+15551111002";
    private static final String SECONDARY_USER_PASSWORD = "StagingTest123";

    @Test
    @Order(1)
    @DisplayName("Add group member succeeds")
    void addGroupMember_ValidUser_Success() {
        String groupId = createTestGroup("Member Test Group");

        // Get secondary user ID
        String secondaryToken = loginAndGetToken(SECONDARY_USER_PHONE, SECONDARY_USER_PASSWORD);
        String secondaryUserId = given()
            .header("Authorization", "Bearer " + secondaryToken)
        .when()
            .get("/profile")
        .then()
            .statusCode(200)
        .extract()
            .jsonPath()
            .getString("id");

        // Add member
        given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType("application/json")
            .body(String.format("{\"userId\":\"%s\"}", secondaryUserId))
        .when()
            .post("/groups/" + groupId + "/members")
        .then()
            .statusCode(200);

        // Verify member added
        given()
            .header("Authorization", "Bearer " + testUserToken)
        .when()
            .get("/groups/" + groupId + "/members")
        .then()
            .statusCode(200)
            .body("userId", hasItem(secondaryUserId));
    }

    @Test
    @Order(2)
    @DisplayName("Set interest on hangout succeeds")
    void setInterest_ValidHangout_Success() {
        String groupId = createTestGroup("Interest Test Group");
        String hangoutId = createTestHangout(groupId, "Interest Test Hangout");

        given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType("application/json")
            .body("{\"status\":\"INTERESTED\"}")
        .when()
            .put("/hangouts/" + hangoutId + "/interest")
        .then()
            .statusCode(200);

        // Note: userInterest field is not returned in GET /hangouts/{id} response
        // The PUT succeeding is sufficient verification
    }

    @Test
    @Order(3)
    @DisplayName("Create poll on hangout succeeds")
    void createPoll_ValidHangout_Success() {
        String groupId = createTestGroup("Poll Test Group");
        String hangoutId = createTestHangout(groupId, "Poll Test Hangout");

        String pollId = given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType("application/json")
            .body("{\"title\":\"Where should we meet?\",\"options\":[\"Park\",\"Cafe\",\"Beach\"]}")
        .when()
            .post("/hangouts/" + hangoutId + "/polls")
        .then()
            .statusCode(201)
            .body("pollId", notNullValue())
        .extract()
            .jsonPath()
            .getString("pollId");

        // Verify poll exists
        given()
            .header("Authorization", "Bearer " + testUserToken)
        .when()
            .get("/hangouts/" + hangoutId)
        .then()
            .statusCode(200)
            .body("polls.find { it.pollId == '" + pollId + "' }", notNullValue());
    }

    @Test
    @Order(4)
    @DisplayName("Vote on poll succeeds")
    void voteOnPoll_ValidPoll_Success() {
        String groupId = createTestGroup("Vote Test Group");
        String hangoutId = createTestHangout(groupId, "Vote Test Hangout");

        // Create poll
        io.restassured.response.Response pollResponse = given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType("application/json")
            .body("{\"title\":\"Favorite activity?\",\"options\":[\"Hiking\",\"Swimming\",\"Biking\"]}")
        .when()
            .post("/hangouts/" + hangoutId + "/polls")
        .then()
            .statusCode(201)
        .extract()
            .response();

        String pollId = pollResponse.jsonPath().getString("pollId");

        // Get poll details to extract optionId
        io.restassured.response.Response pollDetail = given()
            .header("Authorization", "Bearer " + testUserToken)
        .when()
            .get("/hangouts/" + hangoutId + "/polls/" + pollId)
        .then()
            .statusCode(200)
        .extract()
            .response();

        // Get the second option ID (Swimming)
        String optionId = pollDetail.jsonPath().getString("options[1].optionId");

        // Vote on poll using optionId
        given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType("application/json")
            .body(String.format("{\"optionId\":\"%s\"}", optionId))
        .when()
            .post("/hangouts/" + hangoutId + "/polls/" + pollId + "/vote")
        .then()
            .statusCode(200);

        // Verify vote recorded - votes are nested under options[].votes
        given()
            .header("Authorization", "Bearer " + testUserToken)
        .when()
            .get("/hangouts/" + hangoutId + "/polls/" + pollId)
        .then()
            .statusCode(200)
            .body("options.find { it.optionId == '" + optionId + "' }.userVoted", equalTo(true));
    }

    @Test
    @Order(5)
    @DisplayName("Generate invite code succeeds")
    void generateInviteCode_ValidGroup_ReturnsCode() {
        String groupId = createTestGroup("Invite Code Test Group");

        String inviteCode = given()
            .header("Authorization", "Bearer " + testUserToken)
        .when()
            .post("/groups/" + groupId + "/invite-code")
        .then()
            .statusCode(200)
            .body("inviteCode", notNullValue())
            .body("inviteCode", matchesPattern("[A-Za-z0-9]{8}"))
            .body("shareUrl", notNullValue())
            .body("shareUrl", containsString("/join-group/"))
        .extract()
            .jsonPath()
            .getString("inviteCode");

        // Verify code is valid
        Assertions.assertNotNull(inviteCode);
        Assertions.assertTrue(inviteCode.length() >= 6);
    }

    @Test
    @Order(6)
    @DisplayName("Join group via invite code succeeds")
    void joinViaInviteCode_ValidCode_Success() {
        String groupId = createTestGroup("Join Test Group");

        // Generate invite code
        String inviteCode = given()
            .header("Authorization", "Bearer " + testUserToken)
        .when()
            .post("/groups/" + groupId + "/invite-code")
        .then()
            .statusCode(200)
        .extract()
            .jsonPath()
            .getString("inviteCode");

        // Join with secondary user
        String secondaryToken = loginAndGetToken(SECONDARY_USER_PHONE, SECONDARY_USER_PASSWORD);

        given()
            .header("Authorization", "Bearer " + secondaryToken)
            .contentType("application/json")
            .body(String.format("{\"inviteCode\":\"%s\"}", inviteCode))
        .when()
            .post("/groups/invite/join")
        .then()
            .statusCode(200)
            .body("groupId", equalTo(groupId));

        // Verify secondary user is member
        given()
            .header("Authorization", "Bearer " + secondaryToken)
        .when()
            .get("/groups/" + groupId)
        .then()
            .statusCode(200)
            .body("groupId", equalTo(groupId));
    }
}
