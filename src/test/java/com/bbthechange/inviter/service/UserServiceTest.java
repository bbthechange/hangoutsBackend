package com.bbthechange.inviter.service;

import com.bbthechange.inviter.model.Device;
import com.bbthechange.inviter.model.Invite;
import com.bbthechange.inviter.model.User;
import com.bbthechange.inviter.repository.EventRepository;
import com.bbthechange.inviter.repository.InviteRepository;
import com.bbthechange.inviter.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
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
    
    @Mock
    private InviteRepository inviteRepository;
    
    @Mock
    private EventRepository eventRepository;
    
    @Mock
    private DeviceService deviceService;

    @Mock
    private com.bbthechange.inviter.repository.GroupRepository groupRepository;

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
            // No need to setup mock for null input - just test the behavior

            // Act
            Optional<User> result = userService.getUserById(null);

            // Assert
            assertTrue(result.isEmpty());
            // Don't verify the null call as it causes stubbing issues
        }
    }

    @Nested
    @DisplayName("deleteUser - User Deletion Tests")
    class DeleteUserTests {

        private UUID eventId1;
        private UUID eventId2;
        private Invite hostInvite;
        private Invite guestInvite;
        private Invite coHostInvite;
        private Device device1;
        private Device device2;

        @BeforeEach
        void setUpDeleteTests() {
            eventId1 = UUID.randomUUID();
            eventId2 = UUID.randomUUID();
            
            hostInvite = new Invite(eventId1, testUserId, Invite.InviteType.HOST);
            hostInvite.setId(UUID.randomUUID());
            
            guestInvite = new Invite(eventId2, testUserId, Invite.InviteType.GUEST);
            guestInvite.setId(UUID.randomUUID());
            
            coHostInvite = new Invite(eventId1, UUID.randomUUID(), Invite.InviteType.HOST);
            coHostInvite.setId(UUID.randomUUID());
            
            device1 = new Device("token1", testUserId, Device.Platform.IOS);
            device2 = new Device("token2", testUserId, Device.Platform.ANDROID);
        }

        @Test
        @DisplayName("Should delete user successfully when user is not sole host")
        void deleteUser_Success_NotSoleHost() {
            // Arrange
            List<Invite> userInvites = List.of(hostInvite, guestInvite);
            List<Invite> eventInvites = List.of(hostInvite, coHostInvite); // Multiple hosts
            List<Device> userDevices = List.of(device1, device2);
            
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
            when(inviteRepository.findByUserId(testUserId)).thenReturn(userInvites);
            when(inviteRepository.findByEventId(eventId1)).thenReturn(eventInvites); // Multiple hosts
            when(deviceService.getAllDevicesForUser(testUserId)).thenReturn(userDevices);

            // Act
            userService.deleteUser(testUserId);

            // Assert
            verify(userRepository).findById(testUserId);
            verify(inviteRepository).findByUserId(testUserId);
            verify(inviteRepository).findByEventId(eventId1);
            verify(eventRepository, never()).deleteById(any()); // Event should not be deleted
            verify(inviteRepository).delete(hostInvite);
            verify(inviteRepository).delete(guestInvite);
            verify(deviceService).deleteDevice("token1");
            verify(deviceService).deleteDevice("token2");
            verify(userRepository).delete(testUser);
        }

        @Test
        @DisplayName("Should delete event when user is sole host")
        void deleteUser_DeletesEventWhenSoleHost() {
            // Arrange
            List<Invite> userInvites = List.of(hostInvite, guestInvite);
            List<Invite> eventInvites = List.of(hostInvite); // Only one host
            List<Device> userDevices = List.of(device1);
            
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
            when(inviteRepository.findByUserId(testUserId)).thenReturn(userInvites);
            when(inviteRepository.findByEventId(eventId1)).thenReturn(eventInvites); // Single host
            when(deviceService.getAllDevicesForUser(testUserId)).thenReturn(userDevices);

            // Act
            userService.deleteUser(testUserId);

            // Assert
            verify(eventRepository).deleteById(eventId1); // Event should be deleted
            verify(inviteRepository).delete(guestInvite); // Only remaining invite
            verify(deviceService).deleteDevice("token1");
            verify(userRepository).delete(testUser);
        }

        @Test
        @DisplayName("Should handle user with no invites")
        void deleteUser_NoInvites() {
            // Arrange
            List<Invite> userInvites = Collections.emptyList();
            List<Device> userDevices = List.of(device1);
            
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
            when(inviteRepository.findByUserId(testUserId)).thenReturn(userInvites);
            when(deviceService.getAllDevicesForUser(testUserId)).thenReturn(userDevices);

            // Act
            userService.deleteUser(testUserId);

            // Assert
            verify(eventRepository, never()).deleteById(any());
            verify(inviteRepository, never()).delete(any());
            verify(deviceService).deleteDevice("token1");
            verify(userRepository).delete(testUser);
        }

        @Test
        @DisplayName("Should handle user with no devices")
        void deleteUser_NoDevices() {
            // Arrange
            List<Invite> userInvites = List.of(guestInvite);
            List<Device> userDevices = Collections.emptyList();
            
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
            when(inviteRepository.findByUserId(testUserId)).thenReturn(userInvites);
            when(deviceService.getAllDevicesForUser(testUserId)).thenReturn(userDevices);

            // Act
            userService.deleteUser(testUserId);

            // Assert
            verify(inviteRepository).delete(guestInvite);
            verify(deviceService, never()).deleteDevice(any());
            verify(userRepository).delete(testUser);
        }

        @Test
        @DisplayName("Should throw exception when user not found")
        void deleteUser_UserNotFound() {
            // Arrange
            when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

            // Act & Assert
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                userService.deleteUser(testUserId)
            );
            
            assertEquals("User not found", exception.getMessage());
            verify(userRepository).findById(testUserId);
            verify(inviteRepository, never()).findByUserId(any());
            verify(deviceService, never()).getAllDevicesForUser(any());
            verify(userRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Should handle multiple events where user is sole host")
        void deleteUser_MultipleSoleHostEvents() {
            // Arrange
            UUID eventId3 = UUID.randomUUID();
            Invite hostInvite2 = new Invite(eventId3, testUserId, Invite.InviteType.HOST);
            hostInvite2.setId(UUID.randomUUID());
            
            List<Invite> userInvites = List.of(hostInvite, hostInvite2, guestInvite);
            List<Invite> event1Invites = List.of(hostInvite); // Single host
            List<Invite> event3Invites = List.of(hostInvite2); // Single host
            List<Device> userDevices = Collections.emptyList();
            
            when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
            when(inviteRepository.findByUserId(testUserId)).thenReturn(userInvites);
            when(inviteRepository.findByEventId(eventId1)).thenReturn(event1Invites);
            when(inviteRepository.findByEventId(eventId3)).thenReturn(event3Invites);
            when(deviceService.getAllDevicesForUser(testUserId)).thenReturn(userDevices);

            // Act
            userService.deleteUser(testUserId);

            // Assert
            verify(eventRepository).deleteById(eventId1); // Both events deleted
            verify(eventRepository).deleteById(eventId3);
            verify(inviteRepository).delete(guestInvite); // Only remaining invite
            verify(userRepository).delete(testUser);
        }
    }

    @Nested
    @DisplayName("updateProfile - Profile Update with Image Path Tests")
    class UpdateProfileWithImageTests {

        @Test
        @DisplayName("Should call repository to update memberships when mainImagePath changes")
        void updateProfile_CallsRepositoryToUpdateMembershipsWhenMainImagePathChanges() {
            // Arrange
            User user = new User("+1234567890", "testuser", "Test User", "password");
            user.setId(testUserId);
            user.setMainImagePath("/old-avatar.jpg");

            com.bbthechange.inviter.dto.UpdateProfileRequest request = new com.bbthechange.inviter.dto.UpdateProfileRequest();
            request.setMainImagePath("/new-avatar.jpg");

            when(userRepository.findById(testUserId)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenReturn(user);
            doNothing().when(groupRepository).updateMembershipUserImagePath(anyString(), anyString());

            // Act
            userService.updateProfile(testUserId, request);

            // Assert
            verify(groupRepository).updateMembershipUserImagePath(eq(testUserId.toString()), eq("/new-avatar.jpg"));
        }

        @Test
        @DisplayName("Should not call repository when mainImagePath unchanged")
        void updateProfile_DoesNotCallRepositoryWhenMainImagePathUnchanged() {
            // Arrange
            User user = new User("+1234567890", "testuser", "Test User", "password");
            user.setId(testUserId);
            user.setMainImagePath("/same.jpg");

            com.bbthechange.inviter.dto.UpdateProfileRequest request = new com.bbthechange.inviter.dto.UpdateProfileRequest();
            request.setMainImagePath("/same.jpg");

            when(userRepository.findById(testUserId)).thenReturn(Optional.of(user));

            // Act
            userService.updateProfile(testUserId, request);

            // Assert
            verify(groupRepository, never()).updateMembershipUserImagePath(anyString(), anyString());
            verify(userRepository, never()).save(any(User.class)); // No changes, so no save
        }

        @Test
        @DisplayName("Should handle both displayName and imagePath changes")
        void updateProfile_HandlesBothDisplayNameAndImagePathChanges() {
            // Arrange
            User user = new User("+1234567890", "testuser", "Old Name", "password");
            user.setId(testUserId);
            user.setMainImagePath("/old.jpg");

            com.bbthechange.inviter.dto.UpdateProfileRequest request = new com.bbthechange.inviter.dto.UpdateProfileRequest();
            request.setDisplayName("New Name");
            request.setMainImagePath("/new.jpg");

            when(userRepository.findById(testUserId)).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenReturn(user);
            doNothing().when(groupRepository).updateMembershipUserImagePath(anyString(), anyString());

            // Act
            User result = userService.updateProfile(testUserId, request);

            // Assert
            assertEquals("New Name", result.getDisplayName());
            assertEquals("/new.jpg", result.getMainImagePath());
            verify(userRepository).save(any(User.class));
            verify(groupRepository).updateMembershipUserImagePath(eq(testUserId.toString()), eq("/new.jpg"));
        }

        @Test
        @DisplayName("Should handle null mainImagePath (no change requested)")
        void updateProfile_HandlesNullMainImagePath() {
            // Arrange
            User user = new User("+1234567890", "testuser", "Test User", "password");
            user.setId(testUserId);
            user.setMainImagePath("/old.jpg");

            com.bbthechange.inviter.dto.UpdateProfileRequest request = new com.bbthechange.inviter.dto.UpdateProfileRequest();
            request.setMainImagePath(null); // null means no change requested

            when(userRepository.findById(testUserId)).thenReturn(Optional.of(user));

            // Act
            userService.updateProfile(testUserId, request);

            // Assert
            verify(groupRepository, never()).updateMembershipUserImagePath(anyString(), anyString());
            verify(userRepository, never()).save(any(User.class)); // No changes
        }
    }

}