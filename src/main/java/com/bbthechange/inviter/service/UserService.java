package com.bbthechange.inviter.service;

import com.bbthechange.inviter.model.Device;
import com.bbthechange.inviter.model.Event;
import com.bbthechange.inviter.model.Invite;
import com.bbthechange.inviter.model.User;
import com.bbthechange.inviter.dto.UpdateProfileRequest;
import com.bbthechange.inviter.repository.EventRepository;
import com.bbthechange.inviter.repository.InviteRepository;
import com.bbthechange.inviter.repository.UserRepository;
import com.bbthechange.inviter.repository.GroupRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private InviteRepository inviteRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private GroupRepository groupRepository;
    
    public User updateProfile(UUID userId, UpdateProfileRequest request) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }

        User user = userOpt.get();
        boolean updated = false;
        boolean mainImagePathChanged = false;

        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName());
            updated = true;
        }

        if (request.getMainImagePath() != null && !request.getMainImagePath().equals(user.getMainImagePath())) {
            user.setMainImagePath(request.getMainImagePath());
            mainImagePathChanged = true;
            updated = true;
        }

        if (!updated) {
            return user; // No changes needed
        }

        User savedUser = userRepository.save(user);

        // Update denormalized user image path in all membership records if changed
        if (mainImagePathChanged) {
            groupRepository.updateMembershipUserImagePath(userId.toString(), savedUser.getMainImagePath());
            logger.info("Updated user {} mainImagePath and synchronized membership records", userId);
        }

        return savedUser;
    }

    @Deprecated
    public User updateDisplayName(UUID userId, String displayName) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }

        User user = userOpt.get();
        user.setDisplayName(displayName);
        return userRepository.save(user);
    }
    
    public User changePassword(UUID userId, String currentPassword, String newPassword) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }
        
        User user = userOpt.get();
        
        // Verify current password
        if (user.getPassword() == null || !passwordService.matches(currentPassword, user.getPassword())) {
            throw new IllegalStateException("Current password is incorrect");
        }
        
        // Update to new password
        user.setPassword(passwordService.encryptPassword(newPassword));
        return userRepository.save(user);
    }
    
    public Optional<User> getUserById(UUID userId) {
        return userRepository.findById(userId);
    }
    
    public void deleteUser(UUID userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }
        
        // Get all invites for this user
        List<Invite> userInvites = inviteRepository.findByUserId(userId);
        
        // Collect events where user is the sole host
        List<UUID> eventsToDelete = new ArrayList<>();
        for (Invite invite : userInvites) {
            if (invite.getType() == Invite.InviteType.HOST) {
                // Check if this is the only host for the event
                List<Invite> eventInvites = inviteRepository.findByEventId(invite.getEventId());
                long hostCount = eventInvites.stream()
                        .filter(inv -> inv.getType() == Invite.InviteType.HOST)
                        .count();
                
                if (hostCount <= 1) {
                    eventsToDelete.add(invite.getEventId());
                }
            }
        }
        
        // Delete events where user is the sole host
        for (UUID eventId : eventsToDelete) {
            eventRepository.deleteById(eventId);
        }
        
        // Delete remaining invites for this user (excluding invites for deleted events)
        for (Invite invite : userInvites) {
            if (!eventsToDelete.contains(invite.getEventId())) {
                inviteRepository.delete(invite);
            }
        }
        
        // Delete all devices for this user
        List<Device> userDevices = deviceService.getAllDevicesForUser(userId);
        for (Device device : userDevices) {
            deviceService.deleteDevice(device.getToken());
        }
        
        // Finally, delete the user
        userRepository.delete(userOpt.get());
    }
    
}