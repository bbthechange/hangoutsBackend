package com.bbthechange.inviter.service;

import com.bbthechange.inviter.model.User;
import com.bbthechange.inviter.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserService
 * 
 * Test Coverage:
 * - updateDisplayName - Update user display name
 * - changePassword - Change user password with validation
 * - getUserById - Retrieve user by ID
 * - Error handling and validation scenarios
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Tests")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordService passwordService;

    @InjectMocks
    private UserService userService;

    private UUID testUserId;
    private User testUser;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testUser = new User("+1234567890", "testuser", "Test User", "hashedpassword");
        testUser.setId(testUserId);
    }

    @Nested
    @DisplayName("updateDisplayName - Display Name Update Tests")
    class UpdateDisplayNameTests {

        @Test
        @DisplayName("Should update display name successfully")
        void updateDisplayName_Success() {
            // Arrange
            User updatedUser = new User(testUser.getPhoneNumber(), testUser.getUsername(), "Updated Name", testUser.getPassword());
            updatedUser.setId(testUserId);
            
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(updatedUser);

            // Act
            User result = userService.updateDisplayName(testUserId, "Updated Name");

            // Assert
            assertNotNull(result);
            assertEquals("Updated Name", result.getDisplayName());
            verify(userRepository).findById(testUserId);
            verify(userRepository).save(argThat(user ->
                "Updated Name".equals(user.getDisplayName()) &&
                testUserId.equals(user.getId())
            ));
        }

        @Test
        @DisplayName("Should throw exception when user not found")
        void updateDisplayName_UserNotFound() {
            // Arrange
            when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

            // Act & Assert
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                userService.updateDisplayName(testUserId, "New Name")
            );
            
            assertEquals("User not found", exception.getMessage());
            verify(userRepository).findById(testUserId);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should handle null display name")
        void updateDisplayName_NullDisplayName() {
            // Arrange
            User updatedUser = new User(testUser.getPhoneNumber(), testUser.getUsername(), null, testUser.getPassword());
            updatedUser.setId(testUserId);
            
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(updatedUser);

            // Act
            User result = userService.updateDisplayName(testUserId, null);

            // Assert
            assertNotNull(result);
            assertNull(result.getDisplayName());
            verify(userRepository).save(argThat(user -> user.getDisplayName() == null));
        }

        @Test
        @DisplayName("Should handle empty display name")
        void updateDisplayName_EmptyDisplayName() {
            // Arrange
            User updatedUser = new User(testUser.getPhoneNumber(), testUser.getUsername(), "", testUser.getPassword());
            updatedUser.setId(testUserId);
            
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(updatedUser);

            // Act
            User result = userService.updateDisplayName(testUserId, "");

            // Assert
            assertNotNull(result);
            assertEquals("", result.getDisplayName());
            verify(userRepository).save(argThat(user -> "".equals(user.getDisplayName())));
        }
    }

    @Nested
    @DisplayName("changePassword - Password Change Tests")
    class ChangePasswordTests {

        @Test
        @DisplayName("Should change password successfully with valid current password")
        void changePassword_Success() {
            // Arrange
            User updatedUser = new User(testUser.getPhoneNumber(), testUser.getUsername(), testUser.getDisplayName(), "newhashed");
            updatedUser.setId(testUserId);
            
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
            when(passwordService.matches("currentpass", "hashedpassword")).thenReturn(true);
            when(passwordService.encryptPassword("newpass")).thenReturn("newhashed");
            when(userRepository.save(any(User.class))).thenReturn(updatedUser);

            // Act
            User result = userService.changePassword(testUserId, "currentpass", "newpass");

            // Assert
            assertNotNull(result);
            assertEquals("newhashed", result.getPassword());
            verify(userRepository).findById(testUserId);
            verify(passwordService).matches("currentpass", "hashedpassword");
            verify(passwordService).encryptPassword("newpass");
            verify(userRepository).save(argThat(user -> "newhashed".equals(user.getPassword())));
        }

        @Test
        @DisplayName("Should throw exception when user not found")
        void changePassword_UserNotFound() {
            // Arrange
            when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

            // Act & Assert
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                userService.changePassword(testUserId, "currentpass", "newpass")
            );
            
            assertEquals("User not found", exception.getMessage());
            verify(userRepository).findById(testUserId);
            verify(passwordService, never()).matches(any(), any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when current password is incorrect")
        void changePassword_IncorrectCurrentPassword() {
            // Arrange
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
            when(passwordService.matches("wrongpass", "hashedpassword")).thenReturn(false);

            // Act & Assert
            IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                userService.changePassword(testUserId, "wrongpass", "newpass")
            );
            
            assertEquals("Current password is incorrect", exception.getMessage());
            verify(passwordService).matches("wrongpass", "hashedpassword");
            verify(passwordService, never()).encryptPassword(any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw exception when user has no password")
        void changePassword_UserHasNoPassword() {
            // Arrange
            User userWithoutPassword = new User("+1234567890", "testuser", "Test User", null);
            userWithoutPassword.setId(testUserId);
            
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(userWithoutPassword));

            // Act & Assert
            IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                userService.changePassword(testUserId, "currentpass", "newpass")
            );
            
            assertEquals("Current password is incorrect", exception.getMessage());
            verify(passwordService, never()).matches(any(), any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should handle null current password")
        void changePassword_NullCurrentPassword() {
            // Arrange
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
            when(passwordService.matches(null, "hashedpassword")).thenReturn(false);

            // Act & Assert
            IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                userService.changePassword(testUserId, null, "newpass")
            );
            
            assertEquals("Current password is incorrect", exception.getMessage());
            verify(passwordService).matches(null, "hashedpassword");
        }

        @Test
        @DisplayName("Should handle null new password")
        void changePassword_NullNewPassword() {
            // Arrange
            User updatedUser = new User(testUser.getPhoneNumber(), testUser.getUsername(), testUser.getDisplayName(), "nullhashed");
            updatedUser.setId(testUserId);
            
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
            when(passwordService.matches("currentpass", "hashedpassword")).thenReturn(true);
            when(passwordService.encryptPassword(null)).thenReturn("nullhashed");
            when(userRepository.save(any(User.class))).thenReturn(updatedUser);

            // Act
            User result = userService.changePassword(testUserId, "currentpass", null);

            // Assert
            assertNotNull(result);
            verify(passwordService).encryptPassword(null);
        }
    }

    @Nested
    @DisplayName("getUserById - User Retrieval Tests")
    class GetUserByIdTests {

        @Test
        @DisplayName("Should return user when found")
        void getUserById_UserFound() {
            // Arrange
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

            // Act
            Optional<User> result = userService.getUserById(testUserId);

            // Assert
            assertTrue(result.isPresent());
            assertEquals(testUser, result.get());
            verify(userRepository).findById(testUserId);
        }

        @Test
        @DisplayName("Should return empty optional when user not found")
        void getUserById_UserNotFound() {
            // Arrange
            when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

            // Act
            Optional<User> result = userService.getUserById(testUserId);

            // Assert
            assertTrue(result.isEmpty());
            verify(userRepository).findById(testUserId);
        }

        @Test
        @DisplayName("Should handle null userId")
        void getUserById_NullUserId() {
            // Arrange
            when(userRepository.findById(null)).thenReturn(Optional.empty());

            // Act
            Optional<User> result = userService.getUserById(null);

            // Assert
            assertTrue(result.isEmpty());
            verify(userRepository).findById(null);
        }
    }

}