package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.InviteResponse;
import com.bbthechange.inviter.model.Invite;
import com.bbthechange.inviter.model.User;
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
        User user = findOrCreateUserByPhoneNumber(phoneNumber);
        
        // Check if invite already exists
        List<Invite> existingInvites = inviteRepository.findByEventId(eventId);
        boolean alreadyInvited = existingInvites.stream()
                .anyMatch(invite -> invite.getUserId().equals(user.getId()));
                
        if (alreadyInvited) {
            throw new IllegalStateException("User is already invited to this event");
        }
        
        Invite invite = new Invite(eventId, user.getId());
        return inviteRepository.save(invite);
    }
    
    public void removeInvite(UUID inviteId) {
        Optional<Invite> inviteOpt = inviteRepository.findById(inviteId);
        if (inviteOpt.isPresent()) {
            inviteRepository.delete(inviteOpt.get());
        } else {
            throw new IllegalArgumentException("Invite not found");
        }
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
            invite.getResponse(),
            invite.getEventPassed()
        );
    }
    
    private User findOrCreateUserByPhoneNumber(String phoneNumber) {
        Optional<User> existingUser = userRepository.findByPhoneNumber(phoneNumber);
        if (existingUser.isPresent()) {
            return existingUser.get();
        } else {
            User newUser = new User(phoneNumber, null, null);
            return userRepository.save(newUser);
        }
    }
}