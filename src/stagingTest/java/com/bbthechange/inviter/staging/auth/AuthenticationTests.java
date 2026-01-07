package com.bbthechange.inviter.staging.auth;

import com.bbthechange.inviter.staging.StagingTestBase;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tier 1: Authentication & Access - Live Staging")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthenticationTests extends StagingTestBase {

    @Test
    @Order(1)
    @DisplayName("Health check returns OK")
    void healthCheck_ReturnsOK() {
        given()
        .when()
            .get("/health")
        .then()
            .statusCode(200)
            .body(equalTo("OK"));
    }

    @Test
    @Order(2)
    @DisplayName("User login with valid credentials returns token")
    void login_ValidCredentials_ReturnsToken() {
        given()
            .contentType("application/json")
            .body(String.format("{\"phoneNumber\":\"%s\",\"password\":\"%s\"}",
                testUserPhone, testUserPassword))
        .when()
            .post("/auth/login")
        .then()
            .statusCode(200)
            .body("accessToken", notNullValue())
            .body("user.id", notNullValue())
            .body("user.phoneNumber", equalTo(testUserPhone));
    }

    @Test
    @Order(3)
    @DisplayName("Profile access with valid token returns user data")
    void profile_ValidToken_ReturnsUserData() {
        given()
            .header("Authorization", "Bearer " + testUserToken)
        .when()
            .get("/profile")
        .then()
            .statusCode(200)
            .body("id", equalTo(testUserId))
            .body("phoneNumber", equalTo(testUserPhone));
    }

    @Test
    @Order(4)
    @DisplayName("Mobile token refresh returns same refresh token (no rotation)")
    void tokenRefresh_MobileClient_ReturnsSameRefreshToken() {
        // Login with mobile client to get refresh token
        Response loginResponse = given()
            .contentType("application/json")
            .header("X-Client-Type", "mobile")
            .body(String.format("{\"phoneNumber\":\"%s\",\"password\":\"%s\"}",
                testUserPhone, testUserPassword))
        .when()
            .post("/auth/login")
        .then()
            .statusCode(200)
        .extract()
            .response();

        String refreshToken = loginResponse.jsonPath().getString("refreshToken");
        String oldAccessToken = loginResponse.jsonPath().getString("accessToken");

        // Delay to ensure new timestamp (JWT uses millisecond precision)
        try { Thread.sleep(1000); } catch (InterruptedException e) {}

        // Use refresh token to get new tokens - mobile clients get SAME refresh token
        given()
            .contentType("application/json")
            .header("X-Client-Type", "mobile")
            .body(String.format("{\"refreshToken\":\"%s\"}", refreshToken))
        .when()
            .post("/auth/refresh")
        .then()
            .statusCode(200)
            .body("accessToken", notNullValue())
            .body("refreshToken", notNullValue())
            .body("accessToken", not(equalTo(oldAccessToken)))  // New access token
            .body("refreshToken", equalTo(refreshToken));  // Same refresh token (no rotation for mobile)

        // Verify refresh token is still valid (mobile tokens don't rotate)
        given()
            .contentType("application/json")
            .header("X-Client-Type", "mobile")
            .body(String.format("{\"refreshToken\":\"%s\"}", refreshToken))
        .when()
            .post("/auth/refresh")
        .then()
            .statusCode(200)
            .body("accessToken", notNullValue());
    }

    @Test
    @Order(5)
    @DisplayName("Web token refresh rotates token with grace period")
    void tokenRefresh_WebClient_RotatesTokenWithGracePeriod() {
        // Login as web client - refresh token returned in cookie
        Response loginResponse = given()
            .contentType("application/json")
            // No X-Client-Type header = web client
            .body(String.format("{\"phoneNumber\":\"%s\",\"password\":\"%s\"}",
                testUserPhone, testUserPassword))
        .when()
            .post("/auth/login")
        .then()
            .statusCode(200)
        .extract()
            .response();

        String originalRefreshCookie = loginResponse.getCookie("refreshToken");
        assertNotNull(originalRefreshCookie, "Login should return refresh token cookie");

        // Refresh as web client using cookie - should rotate
        Response refreshResponse = given()
            .contentType("application/json")
            .cookie("refreshToken", originalRefreshCookie)
        .when()
            .post("/auth/refresh")
        .then()
            .statusCode(200)
            .body("accessToken", notNullValue())
        .extract()
            .response();

        String newRefreshCookie = refreshResponse.getCookie("refreshToken");
        assertNotNull(newRefreshCookie, "Refresh should return new cookie");
        assertNotEquals(originalRefreshCookie, newRefreshCookie, "Web refresh should rotate token");

        // Old token should still work within grace period (5 minutes)
        given()
            .contentType("application/json")
            .cookie("refreshToken", originalRefreshCookie)
        .when()
            .post("/auth/refresh")
        .then()
            .statusCode(200)
            .body("accessToken", notNullValue());

        // New token should also work
        given()
            .contentType("application/json")
            .cookie("refreshToken", newRefreshCookie)
        .when()
            .post("/auth/refresh")
        .then()
            .statusCode(200)
            .body("accessToken", notNullValue());
    }

    @Test
    @Order(6)
    @DisplayName("Password change with valid credentials succeeds")
    void passwordChange_ValidCredentials_Success() {
        // Use dedicated test account to avoid affecting other tests
        String dedicatedPhone = "+15551111003";
        String dedicatedPassword = "StagingTest123";
        String newPassword = "NewTestPass123!";

        // Login with dedicated account
        String dedicatedToken = loginAndGetToken(dedicatedPhone, dedicatedPassword);

        // Change password
        given()
            .header("Authorization", "Bearer " + dedicatedToken)
            .contentType("application/json")
            .body(String.format("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}",
                dedicatedPassword, newPassword))
        .when()
            .put("/profile/password")
        .then()
            .statusCode(200);

        // Verify can login with new password
        given()
            .contentType("application/json")
            .body(String.format("{\"phoneNumber\":\"%s\",\"password\":\"%s\"}",
                dedicatedPhone, newPassword))
        .when()
            .post("/auth/login")
        .then()
            .statusCode(200)
            .body("accessToken", notNullValue());

        // Change password back to original
        String newToken = loginAndGetToken(dedicatedPhone, newPassword);
        given()
            .header("Authorization", "Bearer " + newToken)
            .contentType("application/json")
            .body(String.format("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}",
                newPassword, dedicatedPassword))
        .when()
            .put("/profile/password")
        .then()
            .statusCode(200);
    }
}
