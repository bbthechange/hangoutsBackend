package com.bbthechange.inviter.integration;

import com.bbthechange.inviter.config.BaseIntegrationTest;
import com.bbthechange.inviter.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for ProfileController endpoints
 * Tests complete HTTP request/response cycles with real DynamoDB via TestContainers
 */
@DisplayName("ProfileController Integration Tests")
public class ProfileControllerIntegrationTest extends BaseIntegrationTest {

    @Nested
    @DisplayName("GET /profile - Profile Retrieval Integration Tests")
    class ProfileRetrievalIntegrationTests {

        @Test
        @DisplayName("Should retrieve user profile successfully with valid token")
        void getProfile_Success_ValidToken() throws Exception {
            // Arrange
            String token = createUserAndGetToken("+1234567890", "profileuser", "Profile User", "password123");

            // Act & Assert
            mockMvc.perform(get("/profile")
                    .header("Authorization", authHeader(token)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.phoneNumber").value("+1234567890"))
                    .andExpect(jsonPath("$.username").value("profileuser"))
                    .andExpect(jsonPath("$.displayName").value("Profile User"))
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.password").doesNotExist()); // Password should not be exposed
        }

        @Test
        @DisplayName("Should reject profile retrieval without authentication")
        void getProfile_Unauthorized_NoAuth() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/profile"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should reject profile retrieval with invalid token")
        void getProfile_Unauthorized_InvalidToken() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/profile")
                    .header("Authorization", "Bearer invalid-token"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("PUT /profile - Profile Update Integration Tests")
    class ProfileUpdateIntegrationTests {

        @Test
        @DisplayName("Should update display name successfully")
        void updateProfile_Success_DisplayNameChange() throws Exception {
            // Arrange
            String token = createUserAndGetToken("+1234567890", "updateuser", "Original Name", "password123");
            
            Map<String, String> updateRequest = Map.of("displayName", "Updated Display Name");

            // Act & Assert
            mockMvc.perform(put("/profile")
                    .header("Authorization", authHeader(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("Profile updated successfully"));

            // Verify update was persisted to DynamoDB
            User updatedUser = getUserByPhoneNumber("+1234567890");
            assertNotNull(updatedUser);
            assertEquals("Updated Display Name", updatedUser.getDisplayName());
            assertEquals("updateuser", updatedUser.getUsername()); // Should remain unchanged
            assertEquals("+1234567890", updatedUser.getPhoneNumber()); // Should remain unchanged
        }

        @Test
        @DisplayName("Should handle empty display name update")
        void updateProfile_Success_EmptyDisplayName() throws Exception {
            // Arrange
            String token = createUserAndGetToken("+1234567890", "emptyuser", "Original Name", "password123");
            
            Map<String, String> updateRequest = Map.of("displayName", "");

            // Act & Assert
            mockMvc.perform(put("/profile")
                    .header("Authorization", authHeader(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Profile updated successfully"));

            // Verify update was persisted
            User updatedUser = getUserByPhoneNumber("+1234567890");
            assertNotNull(updatedUser);
            assertEquals("", updatedUser.getDisplayName());
        }

        @Test
        @DisplayName("Should reject profile update without authentication")
        void updateProfile_Unauthorized_NoAuth() throws Exception {
            // Arrange
            Map<String, String> updateRequest = Map.of("displayName", "Unauthorized Update");

            // Act & Assert
            mockMvc.perform(put("/profile")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should handle malformed JSON in profile update")
        void updateProfile_BadRequest_MalformedJson() throws Exception {
            // Arrange
            String token = createDefaultUserAndGetToken();

            // Act & Assert
            mockMvc.perform(put("/profile")
                    .header("Authorization", authHeader(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{invalid json}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PUT /profile/password - Password Change Integration Tests")
    class PasswordChangeIntegrationTests {

        @Test
        @DisplayName("Should change password successfully with correct current password")
        void changePassword_Success_ValidCurrentPassword() throws Exception {
            // Arrange
            String token = createUserAndGetToken("+1234567890", "pwduser", "Password User", "oldpassword");
            
            Map<String, String> passwordRequest = Map.of(
                "currentPassword", "oldpassword",
                "newPassword", "newpassword123"
            );

            // Act & Assert
            mockMvc.perform(put("/profile/password")
                    .header("Authorization", authHeader(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(passwordRequest)))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("Password updated successfully"));

            // Verify password was changed - try logging in with new password
            User updatedUser = getUserByPhoneNumber("+1234567890");
            assertNotNull(updatedUser);
            assertNotEquals("oldpassword", updatedUser.getPassword()); // Should be encrypted
            assertNotEquals("newpassword123", updatedUser.getPassword()); // Should be encrypted, not plain text

            // Verify login works with new password
            Map<String, String> loginRequest = Map.of(
                "phoneNumber", "+1234567890",
                "password", "newpassword123"
            );

            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").exists());
        }

        @Test
        @DisplayName("Should reject password change with incorrect current password")
        void changePassword_Unauthorized_WrongCurrentPassword() throws Exception {
            // Arrange
            String token = createUserAndGetToken("+1234567890", "secureuser", "Secure User", "correctpassword");
            
            Map<String, String> passwordRequest = Map.of(
                "currentPassword", "wrongpassword",
                "newPassword", "newpassword123"
            );

            // Act & Assert
            mockMvc.perform(put("/profile/password")
                    .header("Authorization", authHeader(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(passwordRequest)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error").value("Current password is incorrect"));

            // Verify password was not changed - original password should still work
            Map<String, String> loginRequest = Map.of(
                "phoneNumber", "+1234567890",
                "password", "correctpassword"
            );

            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").exists());
        }

        @Test
        @DisplayName("Should reject password change without authentication")
        void changePassword_Unauthorized_NoAuth() throws Exception {
            // Arrange
            Map<String, String> passwordRequest = Map.of(
                "currentPassword", "oldpassword",
                "newPassword", "newpassword123"
            );

            // Act & Assert
            mockMvc.perform(put("/profile/password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(passwordRequest)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should handle missing current password in request")
        void changePassword_BadRequest_MissingCurrentPassword() throws Exception {
            // Arrange
            String token = createDefaultUserAndGetToken();
            
            Map<String, String> passwordRequest = Map.of("newPassword", "newpassword123");

            // Act & Assert
            mockMvc.perform(put("/profile/password")
                    .header("Authorization", authHeader(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(passwordRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should handle missing new password in request")
        void changePassword_BadRequest_MissingNewPassword() throws Exception {
            // Arrange
            String token = createDefaultUserAndGetToken();
            
            Map<String, String> passwordRequest = Map.of("currentPassword", "oldpassword");

            // Act & Assert
            mockMvc.perform(put("/profile/password")
                    .header("Authorization", authHeader(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(passwordRequest)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Profile Management Flow Integration Tests")
    class ProfileManagementFlowTests {

        @Test
        @DisplayName("Should complete full profile management flow")
        void completeProfileManagementFlow() throws Exception {
            // Step 1: Register and login
            String token = createUserAndGetToken("+1555000001", "flowuser", "Flow User", "initialpassword");

            // Step 2: Get initial profile
            mockMvc.perform(get("/profile")
                    .header("Authorization", authHeader(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.displayName").value("Flow User"));

            // Step 3: Update display name
            Map<String, String> displayNameUpdate = Map.of("displayName", "Updated Flow User");
            
            mockMvc.perform(put("/profile")
                    .header("Authorization", authHeader(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(displayNameUpdate)))
                    .andExpect(status().isOk());

            // Step 4: Verify display name was updated
            mockMvc.perform(get("/profile")
                    .header("Authorization", authHeader(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.displayName").value("Updated Flow User"));

            // Step 5: Change password
            Map<String, String> passwordChange = Map.of(
                "currentPassword", "initialpassword",
                "newPassword", "newflowpassword"
            );
            
            mockMvc.perform(put("/profile/password")
                    .header("Authorization", authHeader(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(passwordChange)))
                    .andExpect(status().isOk());

            // Step 6: Verify login with new password works
            Map<String, String> loginWithNewPassword = Map.of(
                "phoneNumber", "+1555000001",
                "password", "newflowpassword"
            );

            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginWithNewPassword)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").exists());

            // Step 7: Verify old password no longer works
            Map<String, String> loginWithOldPassword = Map.of(
                "phoneNumber", "+1555000001",
                "password", "initialpassword"
            );

            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginWithOldPassword)))
                    .andExpect(status().isUnauthorized());
        }
    }
}