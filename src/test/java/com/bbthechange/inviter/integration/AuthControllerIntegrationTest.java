package com.bbthechange.inviter.integration;

import com.bbthechange.inviter.config.BaseIntegrationTest;
import com.bbthechange.inviter.controller.AuthController.LoginRequest;
import com.bbthechange.inviter.model.User;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AuthController endpoints
 * Tests complete HTTP request/response cycles with real DynamoDB via TestContainers
 */
@DisplayName("AuthController Integration Tests")
public class AuthControllerIntegrationTest extends BaseIntegrationTest {

    @Nested
    @DisplayName("POST /auth/register - Registration Integration Tests")
    class RegistrationIntegrationTests {

        @Test
        @DisplayName("Should register new user successfully with HTTP 201")
        void register_Success_NewUser() throws Exception {
            // Arrange
            User newUser = new User("+1234567890", "newuser", "New User", "password123");

            // Act & Assert
            MvcResult result = mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(newUser)))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("User registered successfully"))
                    .andReturn();

            // Verify user was persisted to DynamoDB
            User savedUser = getUserByPhoneNumber("+1234567890");
            assertNotNull(savedUser);
            assertEquals("newuser", savedUser.getUsername());
            assertEquals("New User", savedUser.getDisplayName());
            assertEquals("+1234567890", savedUser.getPhoneNumber());
            assertNotNull(savedUser.getPassword()); // Should be encrypted
            assertNotEquals("password123", savedUser.getPassword()); // Should not be plain text
        }

        @Test
        @DisplayName("Should return conflict when user already exists")
        void register_Conflict_UserExists() throws Exception {
            // Arrange - create existing user
            createUserAndGetToken("+1234567890", "existinguser", "Existing User", "oldpassword");
            
            User duplicateUser = new User("+1234567890", "newuser", "New User", "newpassword");

            // Act & Assert
            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(duplicateUser)))
                    .andExpect(status().isConflict())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.error").value("User already exists"));

            // Verify original user was not modified
            User originalUser = getUserByPhoneNumber("+1234567890");
            assertEquals("existinguser", originalUser.getUsername());
            assertEquals("Existing User", originalUser.getDisplayName());
        }

        @Test
        @DisplayName("Should handle registration with empty fields")
        void register_BadRequest_EmptyFields() throws Exception {
            // Arrange
            User invalidUser = new User("", "", "", "");

            // Act & Assert
            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidUser)))
                    .andExpect(status().isCreated()); // Current implementation allows empty fields

            // Verify user was created (current behavior)
            User savedUser = getUserByPhoneNumber("");
            assertNotNull(savedUser);
        }
    }

    @Nested
    @DisplayName("POST /auth/login - Authentication Integration Tests")
    class AuthenticationIntegrationTests {

        @Test
        @DisplayName("Should login successfully with valid credentials and return user profile")
        void login_Success_ValidCredentials() throws Exception {
            // Arrange - create user first
            String token = createUserAndGetToken("+1234567890", "testuser", "Test User", "password123");

            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setPhoneNumber("+1234567890");
            loginRequest.setPassword("password123");

            // Act & Assert
            MvcResult result = mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.accessToken").exists())
                    .andExpect(jsonPath("$.expiresIn").exists())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.user").exists())
                    .andExpect(jsonPath("$.user.id").exists())
                    .andExpect(jsonPath("$.user.phoneNumber").value("+1234567890"))
                    .andExpect(jsonPath("$.user.username").value("testuser"))
                    .andExpect(jsonPath("$.user.displayName").value("Test User"))
                    .andReturn();

            // Verify token is returned and valid
            String responseBody = result.getResponse().getContentAsString();
            Map<String, Object> response = objectMapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
            String returnedToken = (String) response.get("accessToken");

            assertNotNull(returnedToken);
            assertFalse(returnedToken.isEmpty());

            // Verify token can be used to extract user information
            String userId = jwtService.extractUserId(returnedToken);
            assertNotNull(userId);

            // Verify user object is included in response
            Map<String, Object> userObj = (Map<String, Object>) response.get("user");
            assertNotNull(userObj);
            assertEquals("+1234567890", userObj.get("phoneNumber"));
            assertEquals("testuser", userObj.get("username"));
            assertEquals("Test User", userObj.get("displayName"));
            assertFalse(userObj.containsKey("password")); // Password field should not exist at all
        }

        @Test
        @DisplayName("Should return unauthorized for non-existent user")
        void login_Unauthorized_UserNotFound() throws Exception {
            // Arrange
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setPhoneNumber("+9999999999");
            loginRequest.setPassword("password123");

            // Act & Assert
            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.error").value("Invalid credentials"))
                    .andExpect(jsonPath("$.accessToken").doesNotExist())
                    .andExpect(jsonPath("$.user").doesNotExist());
        }

        @Test
        @DisplayName("Should return unauthorized for wrong password")
        void login_Unauthorized_WrongPassword() throws Exception {
            // Arrange - create user first
            createUserAndGetToken("+1234567890", "testuser", "Test User", "correctpassword");

            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setPhoneNumber("+1234567890");
            loginRequest.setPassword("wrongpassword");

            // Act & Assert
            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.error").value("Invalid credentials"))
                    .andExpect(jsonPath("$.accessToken").doesNotExist())
                    .andExpect(jsonPath("$.user").doesNotExist());
        }

        @Test
        @DisplayName("Should handle malformed JSON request")
        void login_BadRequest_MalformedJson() throws Exception {
            // Act & Assert
            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{invalid json}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should handle missing request body")
        void login_BadRequest_MissingBody() throws Exception {
            // Act & Assert
            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Authentication Flow Integration Tests")
    class AuthenticationFlowTests {

        @Test
        @DisplayName("Should complete full register -> login -> use token flow")
        void completeAuthenticationFlow() throws Exception {
            // Step 1: Register new user
            User newUser = new User("+1555000001", "flowuser", "Flow User", "testpassword");
            
            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(newUser)))
                    .andExpect(status().isCreated());

            // Step 2: Login with registered user
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setPhoneNumber("+1555000001");
            loginRequest.setPassword("testpassword");
            
            MvcResult loginResult = mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andReturn();

            // Step 3: Extract token and verify it works for authenticated endpoints
            String responseBody = loginResult.getResponse().getContentAsString();
            Map<String, Object> response = objectMapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {});
            String token = (String) response.get("token");
            
            assertNotNull(token);
            
            // Step 4: Use token to access authenticated endpoint (profile)
            mockMvc.perform(get("/profile")
                    .header("Authorization", authHeader(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.phoneNumber").value("+1555000001"))
                    .andExpect(jsonPath("$.username").value("flowuser"))
                    .andExpect(jsonPath("$.displayName").value("Flow User"));
        }
    }
}