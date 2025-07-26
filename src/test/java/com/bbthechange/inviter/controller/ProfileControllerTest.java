package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.ChangePasswordRequest;
import com.bbthechange.inviter.dto.UpdateProfileRequest;
import com.bbthechange.inviter.model.User;
import com.bbthechange.inviter.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProfileController
 * 
 * Test Coverage:
 * - GET /profile - Get user profile
 * - PUT /profile - Update user profile
 * - PUT /profile/password - Change password
 * - Authorization and error handling scenarios
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileController Tests")
class ProfileControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private ProfileController profileController;

    private UUID testUserId;
    private User testUser;
    private UpdateProfileRequest updateProfileRequest;
    private ChangePasswordRequest changePasswordRequest;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        
        testUser = new User("+1234567890", "testuser", "Test User", "hashedpassword");
        testUser.setId(testUserId);

        updateProfileRequest = new UpdateProfileRequest();
        updateProfileRequest.setDisplayName("Updated User");

        changePasswordRequest = new ChangePasswordRequest();
        changePasswordRequest.setCurrentPassword("oldpassword");
        changePasswordRequest.setNewPassword("newpassword123");
    }

    @Nested
    @DisplayName("GET /profile - Get Profile Tests")
    class GetProfileTests {

        @Test
        @DisplayName("Should return user profile successfully")
        void getProfile_Success() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(userService.getUserById(testUserId)).thenReturn(Optional.of(testUser));

            // Act
            ResponseEntity<User> response = profileController.getProfile(httpServletRequest);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(testUserId, response.getBody().getId());
            assertEquals("Test User", response.getBody().getDisplayName());
            assertEquals("+1234567890", response.getBody().getPhoneNumber());
            assertNull(response.getBody().getPassword()); // Password should be null in response
            
            verify(userService).getUserById(testUserId);
        }

        @Test
        @DisplayName("Should return NOT_FOUND when user doesn't exist")
        void getProfile_NotFound() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(userService.getUserById(testUserId)).thenReturn(Optional.empty());

            // Act
            ResponseEntity<User> response = profileController.getProfile(httpServletRequest);

            // Assert
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            verify(userService).getUserById(testUserId);
        }

        @Test
        @DisplayName("Should return UNAUTHORIZED when userId is null")
        void getProfile_Unauthorized() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(null);

            // Act
            ResponseEntity<User> response = profileController.getProfile(httpServletRequest);

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("Should mask password in response")
        void getProfile_PasswordMasked() {
            // Arrange
            User userWithPassword = new User("+1234567890", "testuser", "Test User", "secretpassword");
            userWithPassword.setId(testUserId);
            
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(userService.getUserById(testUserId)).thenReturn(Optional.of(userWithPassword));

            // Act
            ResponseEntity<User> response = profileController.getProfile(httpServletRequest);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertNull(response.getBody().getPassword());
        }
    }

    @Nested
    @DisplayName("PUT /profile - Update Profile Tests")
    class UpdateProfileTests {

        @Test
        @DisplayName("Should update display name successfully")
        void updateProfile_Success() {
            // Arrange
            User updatedUser = new User(testUser.getPhoneNumber(), testUser.getUsername(), "Updated User", testUser.getPassword());
            updatedUser.setId(testUserId);
            
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(userService.updateDisplayName(testUserId, "Updated User")).thenReturn(updatedUser);

            // Act
            ResponseEntity<Map<String, Object>> response = profileController.updateProfile(updateProfileRequest, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Profile updated successfully", response.getBody().get("message"));
            assertEquals("Updated User", response.getBody().get("displayName"));
            
            verify(userService).updateDisplayName(testUserId, "Updated User");
        }

        @Test
        @DisplayName("Should return NOT_FOUND when user doesn't exist")
        void updateProfile_NotFound() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(userService.updateDisplayName(testUserId, "Updated User"))
                .thenThrow(new IllegalArgumentException("User not found"));

            // Act
            ResponseEntity<Map<String, Object>> response = profileController.updateProfile(updateProfileRequest, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("User not found", response.getBody().get("error"));
        }

        @Test
        @DisplayName("Should return UNAUTHORIZED when userId is null")
        void updateProfile_Unauthorized() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(null);

            // Act
            ResponseEntity<Map<String, Object>> response = profileController.updateProfile(updateProfileRequest, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("Should handle empty display name")
        void updateProfile_EmptyDisplayName() {
            // Arrange
            updateProfileRequest.setDisplayName("");
            User updatedUser = new User(testUser.getPhoneNumber(), testUser.getUsername(), "", testUser.getPassword());
            updatedUser.setId(testUserId);
            
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(userService.updateDisplayName(testUserId, "")).thenReturn(updatedUser);

            // Act
            ResponseEntity<Map<String, Object>> response = profileController.updateProfile(updateProfileRequest, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("", response.getBody().get("displayName"));
            verify(userService).updateDisplayName(testUserId, "");
        }
    }

    @Nested
    @DisplayName("PUT /profile/password - Change Password Tests")
    class ChangePasswordTests {

        @Test
        @DisplayName("Should change password successfully")
        void changePassword_Success() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            when(userService.changePassword(testUserId, "oldpassword", "newpassword123")).thenReturn(testUser);

            // Act
            ResponseEntity<Map<String, String>> response = profileController.changePassword(changePasswordRequest, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Password changed successfully", response.getBody().get("message"));
            
            verify(userService).changePassword(testUserId, "oldpassword", "newpassword123");
        }

        @Test
        @DisplayName("Should return NOT_FOUND when user doesn't exist")
        void changePassword_NotFound() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            doThrow(new IllegalArgumentException("User not found"))
                .when(userService).changePassword(testUserId, "oldpassword", "newpassword123");

            // Act
            ResponseEntity<Map<String, String>> response = profileController.changePassword(changePasswordRequest, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("User not found", response.getBody().get("error"));
        }

        @Test
        @DisplayName("Should return BAD_REQUEST when current password is incorrect")
        void changePassword_BadRequest_InvalidCurrentPassword() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            doThrow(new IllegalStateException("Current password is incorrect"))
                .when(userService).changePassword(testUserId, "oldpassword", "newpassword123");

            // Act
            ResponseEntity<Map<String, String>> response = profileController.changePassword(changePasswordRequest, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("Current password is incorrect", response.getBody().get("error"));
        }

        @Test
        @DisplayName("Should return UNAUTHORIZED when userId is null")
        void changePassword_Unauthorized() {
            // Arrange
            when(httpServletRequest.getAttribute("userId")).thenReturn(null);

            // Act
            ResponseEntity<Map<String, String>> response = profileController.changePassword(changePasswordRequest, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("Should handle service error for null passwords")
        void changePassword_NullPasswords() {
            // Arrange
            changePasswordRequest.setCurrentPassword(null);
            changePasswordRequest.setNewPassword(null);
            
            when(httpServletRequest.getAttribute("userId")).thenReturn(testUserId.toString());
            doThrow(new IllegalStateException("Invalid password")).when(userService).changePassword(testUserId, null, null);

            // Act
            ResponseEntity<Map<String, String>> response = profileController.changePassword(changePasswordRequest, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            verify(userService).changePassword(testUserId, null, null);
        }
    }

}