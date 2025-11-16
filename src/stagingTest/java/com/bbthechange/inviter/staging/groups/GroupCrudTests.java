package com.bbthechange.inviter.staging.groups;

import com.bbthechange.inviter.staging.StagingTestBase;
import org.junit.jupiter.api.*;
import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.Matchers.*;

@DisplayName("Tier 2: Group CRUD - Live Staging")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GroupCrudTests extends StagingTestBase {

    @Test
    @Order(1)
    @DisplayName("Create group returns 201 with group ID")
    void createGroup_ValidData_ReturnsGroupId() {
        String groupName = "Test Group " + java.util.UUID.randomUUID().toString().substring(0, 8);

        String groupId = given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType("application/json")
            .body(String.format("{\"groupName\":\"%s\",\"public\":true}", groupName))
        .when()
            .post("/groups")
        .then()
            .statusCode(201)
            .body("groupId", notNullValue())
            .body("groupName", equalTo(groupName))
        .extract()
            .jsonPath()
            .getString("groupId");

        createdGroupIds.add(groupId);
    }

    @Test
    @Order(2)
    @DisplayName("List groups includes created group")
    void listGroups_IncludesCreatedGroup() {
        String groupId = createTestGroup("List Test Group");

        // Wait for GSI propagation
        await()
            .atMost(3, SECONDS)
            .pollInterval(200, MILLISECONDS)
            .untilAsserted(() -> {
                given()
                    .header("Authorization", "Bearer " + testUserToken)
                .when()
                    .get("/groups")
                .then()
                    .statusCode(200)
                    .body("find { it.groupId == '" + groupId + "' }", notNullValue());
            });
    }

    @Test
    @Order(3)
    @DisplayName("Get group details returns full group data")
    void getGroupDetails_ValidGroupId_ReturnsFullData() {
        String groupId = createTestGroup("Details Test Group");

        given()
            .header("Authorization", "Bearer " + testUserToken)
        .when()
            .get("/groups/" + groupId)
        .then()
            .statusCode(200)
            .body("groupId", equalTo(groupId))
            .body("groupName", containsString("Details Test Group"));
    }

    @Test
    @Order(4)
    @DisplayName("Update group modifies group data")
    void updateGroup_ValidData_UpdatesGroup() {
        String groupId = createTestGroup("Update Test Group");
        String updatedName = "Updated Group " + java.util.UUID.randomUUID().toString().substring(0, 8);

        given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType("application/json")
            .body(String.format("{\"groupName\":\"%s\"}", updatedName))
        .when()
            .patch("/groups/" + groupId)
        .then()
            .statusCode(200)
            .body("groupName", equalTo(updatedName));

        // Verify update persisted
        given()
            .header("Authorization", "Bearer " + testUserToken)
        .when()
            .get("/groups/" + groupId)
        .then()
            .statusCode(200)
            .body("groupName", equalTo(updatedName));
    }
}
