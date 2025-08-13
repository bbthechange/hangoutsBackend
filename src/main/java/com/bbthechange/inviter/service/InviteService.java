package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.InviteResponse;
import com.bbthechange.inviter.model.Device;
import com.bbthechange.inviter.model.Event;
import com.bbthechange.inviter.model.Invite;
import com.bbthechange.inviter.model.Invite.InviteType;
import com.bbthechange.inviter.model.User;
import com.bbthechange.inviter.repository.EventRepository;
import com.bbthechange.inviter.repository.InviteRepository;
import com.bbthechange.inviter.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InviteService {
    
    @Autowired
    private InviteRepository inviteRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private EventRepository eventRepository;
    
    @Autowired
    private PushNotificationService pushNotificationService;
    
    @Autowired
    private DeviceService deviceService;
    
    public boolean isUserInvitedToEvent(UUID userId, UUID eventId) {
        List<Invite> userInvites = inviteRepository.findByUserId(userId);
        return userInvites.stream()
                .anyMatch(invite -> invite.getEventId().equals(eventId));
    }
    
    public List<InviteResponse> getInvitesForEvent(UUID eventId) {
        List<Invite> invites = inviteRepository.findByEventId(eventId);
        
        return invites.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }
    
    public Invite addInviteToEvent(UUID eventId, String phoneNumber) {
        return addInviteToEvent(eventId, phoneNumber, InviteType.GUEST);
    }
    
    public Invite addInviteToEvent(UUID eventId, String phoneNumber, InviteType type) {
        User user = findOrCreateUserByPhoneNumber(phoneNumber);
        
        // Check if invite already exists
        List<Invite> existingInvites = inviteRepository.findByEventId(eventId);
        boolean alreadyInvited = existingInvites.stream()
                .anyMatch(invite -> invite.getUserId().equals(user.getId()));
                
        if (alreadyInvited) {
            throw new IllegalStateException("User is already invited to this event");
        }
        
        Invite invite = new Invite(eventId, user.getId(), type);
        Invite savedInvite = inviteRepository.save(invite);
        
        // Send push notification to all active devices for user
        try {
            List<Device> activeDevices = deviceService.getActiveDevicesForUser(user.getId());
            if (!activeDevices.isEmpty()) {
                Optional<Event> eventOpt = eventRepository.findById(eventId);
                if (eventOpt.isPresent()) {
                    Event event = eventOpt.get();
                    // Get a host name from existing host invites or legacy hosts field
                    String hostName = getAnyHostDisplayName(eventId);
                    
                    for (Device device : activeDevices) {
                        try {
                            pushNotificationService.sendInviteNotification(
                                device.getToken(), 
                                event.getName(), 
                                hostName
                            );
                        } catch (Exception e) {
                            // Log error for individual device but continue with others
                            System.err.println("Failed to send push notification to device " + device.getToken() + ": " + e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Log error but don't fail invite creation
            System.err.println("Failed to send push notifications: " + e.getMessage());
        }
        
        return savedInvite;
    }
    
    public void removeInvite(UUID inviteId) {
        Optional<Invite> inviteOpt = inviteRepository.findById(inviteId);
        if (inviteOpt.isEmpty()) {
            throw new IllegalArgumentException("Invite not found");
        }
        
        Invite invite = inviteOpt.get();
        
        // If this is a host invite, check if it's the last host
        if (invite.getType() == InviteType.HOST) {
            List<Invite> eventInvites = inviteRepository.findByEventId(invite.getEventId());
            long hostCount = eventInvites.stream()
                    .filter(inv -> inv.getType() == InviteType.HOST)
                    .count();
            
            if (hostCount <= 1) {
                throw new IllegalStateException("Cannot remove the last host from an event");
            }
        }
        
        inviteRepository.delete(invite);
    }
    
    public Invite updateInviteResponse(UUID inviteId, UUID userId, com.bbthechange.inviter.model.Invite.InviteResponse response) {
        Optional<Invite> inviteOpt = inviteRepository.findById(inviteId);
        if (inviteOpt.isEmpty()) {
            throw new IllegalArgumentException("Invite not found");
        }
        
        Invite invite = inviteOpt.get();
        
        // Verify that the user is the one invited (can only edit their own response)
        if (!invite.getUserId().equals(userId)) {
            throw new IllegalStateException("You can only edit your own invite response");
        }
        
        invite.setResponse(response);
        return inviteRepository.save(invite);
    }
    
    private InviteResponse convertToResponse(Invite invite) {
        Optional<User> userOpt = userRepository.findById(invite.getUserId());
        User user = userOpt.orElse(new User()); // fallback if user not found
        
        return new InviteResponse(
            invite.getId(),
            invite.getEventId(),
            invite.getUserId(),
            user.getPhoneNumber(),
            user.getUsername(),
            user.getDisplayName(),
            invite.getType(),
            invite.getResponse(),
            invite.getEventPassed()
        );
    }
    
    public User findOrCreateUserByPhoneNumber(String phoneNumber) {
        Optional<User> existingUser = userRepository.findByPhoneNumber(phoneNumber);
        if (existingUser.isPresent()) {
            return existingUser.get();
        } else {
            User newUser = new User(phoneNumber, null, null);
            return userRepository.save(newUser);
        }
    }
    
    private String getHostDisplayName(UUID hostId) {
        Optional<User> hostOpt = userRepository.findById(hostId);
        if (hostOpt.isPresent()) {
            User host = hostOpt.get();
            if (host.getDisplayName() != null && !host.getDisplayName().trim().isEmpty()) {
                return host.getDisplayName();
            }
            if (host.getUsername() != null && !host.getUsername().trim().isEmpty()) {
                return host.getUsername();
            }
        }
        return null; // Return null instead of "Unknown Host" to let push notification handle it
    }
    
    private String getAnyHostDisplayName(UUID eventId) {
        // First try to get from host invites
        List<Invite> eventInvites = inviteRepository.findByEventId(eventId);
        Optional<Invite> hostInvite = eventInvites.stream()
                .filter(invite -> invite.getType() == InviteType.HOST)
                .findFirst();
        
        if (hostInvite.isPresent()) {
            return getHostDisplayName(hostInvite.get().getUserId());
        }
        
        return null; // Return null when no valid host name found
    }
}