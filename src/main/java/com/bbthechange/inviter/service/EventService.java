package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.CreateInviteRequest;
import com.bbthechange.inviter.model.Event;
import com.bbthechange.inviter.model.Invite;
import com.bbthechange.inviter.model.Invite.InviteType;
import com.bbthechange.inviter.model.User;
import com.bbthechange.inviter.repository.EventRepository;
import com.bbthechange.inviter.repository.InviteRepository;
import com.bbthechange.inviter.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class EventService {
    
    @Autowired
    private EventRepository eventRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private InviteRepository inviteRepository;
    
    public Event createEventWithInvites(String name, String description, LocalDateTime startTime, 
                                       LocalDateTime endTime, com.bbthechange.inviter.dto.Address location, 
                                       com.bbthechange.inviter.model.EventVisibility visibility, String mainImagePath,
                                       List<CreateInviteRequest> inviteRequests) {
        
        // Validate that at least one invite is a host
        boolean hasHost = inviteRequests.stream()
                .anyMatch(invite -> invite.getType() == InviteType.HOST);
        if (!hasHost) {
            throw new IllegalArgumentException("Event must have at least one host");
        }
        
        // Create event with empty hosts list (will be managed via invites)
        Event event = new Event(name, description, startTime, endTime, location, visibility, mainImagePath, new ArrayList<>());
        eventRepository.save(event);
        
        // Create invites
        for (CreateInviteRequest inviteRequest : inviteRequests) {
            User user = findOrCreateUserByPhoneNumber(inviteRequest.getPhoneNumber());
            Invite invite = new Invite(event.getId(), user.getId(), inviteRequest.getType());
            inviteRepository.save(invite);
        }
        
        return event;
    }
    
    // Backward compatibility method for tests - converts old API to new API
    public Event createEventWithInvites(String name, String description, LocalDateTime startTime, 
                                       LocalDateTime endTime, com.bbthechange.inviter.dto.Address location, 
                                       com.bbthechange.inviter.model.EventVisibility visibility, String mainImagePath,
                                       List<UUID> hostUserIds, List<String> invitePhoneNumbers) {
        
        List<CreateInviteRequest> inviteRequests = new ArrayList<>();
        
        // Add host invites
        if (hostUserIds != null) {
            for (UUID hostId : hostUserIds) {
                // Convert UUID to phone number by looking up user - this is for test compatibility
                Optional<User> userOpt = userRepository.findById(hostId);
                if (userOpt.isPresent()) {
                    CreateInviteRequest hostInvite = new CreateInviteRequest();
                    hostInvite.setPhoneNumber(userOpt.get().getPhoneNumber());
                    hostInvite.setType(InviteType.HOST);
                    inviteRequests.add(hostInvite);
                }
            }
        }
        
        // Add guest invites
        if (invitePhoneNumbers != null) {
            for (String phoneNumber : invitePhoneNumbers) {
                CreateInviteRequest guestInvite = new CreateInviteRequest();
                guestInvite.setPhoneNumber(phoneNumber);
                guestInvite.setType(InviteType.GUEST);
                inviteRequests.add(guestInvite);
            }
        }
        
        return createEventWithInvites(name, description, startTime, endTime, location, visibility, mainImagePath, inviteRequests);
    }
    
    public List<Event> getEventsForUser(UUID userId) {
        List<Event> events = new ArrayList<>();
        
        // Get events where user is invited (includes both HOST and GUEST invites)
        List<Invite> userInvites = inviteRepository.findByUserId(userId);
        for (Invite invite : userInvites) {
            Optional<Event> eventOpt = eventRepository.findById(invite.getEventId());
            eventOpt.ifPresent(events::add);
        }
        
        // Legacy: Get events where user is a host via the old hosts field (for migration)
        List<Event> allEvents = eventRepository.findAll();
        for (Event event : allEvents) {
            if (event.getHosts() != null && event.getHosts().contains(userId) && !events.contains(event)) {
                events.add(event);
            }
        }
        
        return events;
    }
    
    public Optional<Event> getEventForUser(UUID eventId, UUID userId) {
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            return Optional.empty();
        }
        
        Event event = eventOpt.get();
        
        // Check if user has access via invites (HOST or GUEST)
        List<Invite> userInvites = inviteRepository.findByUserId(userId);
        boolean userHasInvite = userInvites.stream()
                .anyMatch(invite -> invite.getEventId().equals(eventId));
        
        if (userHasInvite) {
            return eventOpt;
        }
        
        // Legacy: Check if user is a host via the old hosts field (for migration)
        if (event.getHosts() != null && event.getHosts().contains(userId)) {
            return eventOpt;
        }
        
        return Optional.empty();
    }
    
    public boolean isUserHostOfEvent(UUID userId, UUID eventId) {
        // Check via invites first (new system)
        List<Invite> userInvites = inviteRepository.findByUserId(userId);
        boolean isHostViaInvite = userInvites.stream()
                .anyMatch(invite -> invite.getEventId().equals(eventId) && invite.getType() == InviteType.HOST);
        
        if (isHostViaInvite) {
            return true;
        }
        
        // Fallback to legacy hosts field for migration
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            return false;
        }
        
        Event event = eventOpt.get();
        return event.getHosts() != null && event.getHosts().contains(userId);
    }
    
    public Event updateEvent(UUID eventId, String name, String description, LocalDateTime startTime, 
                           LocalDateTime endTime, com.bbthechange.inviter.dto.Address location, 
                           com.bbthechange.inviter.model.EventVisibility visibility, String mainImagePath, List<UUID> hostUserIds) {
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            throw new IllegalArgumentException("Event not found");
        }
        
        Event event = eventOpt.get();
        
        // Update fields (only if provided)
        if (name != null) event.setName(name);
        if (description != null) event.setDescription(description);
        if (startTime != null) event.setStartTime(startTime);
        if (endTime != null) event.setEndTime(endTime);
        if (location != null) event.setLocation(location);
        if (visibility != null) event.setVisibility(visibility);
        if (mainImagePath != null) event.setMainImagePath(mainImagePath);
        if (hostUserIds != null) event.setHosts(hostUserIds);
        
        return eventRepository.save(event);
    }
    
    public void deleteEvent(UUID eventId) {
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            throw new IllegalArgumentException("Event not found");
        }
        
        // Delete all invites for this event first
        List<Invite> eventInvites = inviteRepository.findByEventId(eventId);
        for (Invite invite : eventInvites) {
            inviteRepository.delete(invite);
        }
        
        // Then delete the event
        eventRepository.deleteById(eventId);
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
    
    // Migration utility method - can be called to migrate existing events
    public void migrateEventHostsToInvites(UUID eventId) {
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            return;
        }
        
        Event event = eventOpt.get();
        if (event.getHosts() == null || event.getHosts().isEmpty()) {
            return;
        }
        
        // Check if host invites already exist
        List<Invite> eventInvites = inviteRepository.findByEventId(eventId);
        boolean hasHostInvites = eventInvites.stream()
                .anyMatch(invite -> invite.getType() == InviteType.HOST);
        
        if (hasHostInvites) {
            return; // Already migrated
        }
        
        // Create host invites for each host in the legacy hosts field
        for (UUID hostId : event.getHosts()) {
            // Check if this user already has any invite (guest or host)
            boolean userAlreadyInvited = eventInvites.stream()
                    .anyMatch(invite -> invite.getUserId().equals(hostId));
            
            if (!userAlreadyInvited) {
                Invite hostInvite = new Invite(eventId, hostId, InviteType.HOST);
                inviteRepository.save(hostInvite);
            } else {
                // User already has an invite - update it to HOST type
                Invite existingInvite = eventInvites.stream()
                        .filter(invite -> invite.getUserId().equals(hostId))
                        .findFirst()
                        .orElse(null);
                
                if (existingInvite != null) {
                    existingInvite.setType(InviteType.HOST);
                    inviteRepository.save(existingInvite);
                }
            }
        }
    }
    
    // Utility method to migrate all events
    public void migrateAllEventHostsToInvites() {
        List<Event> allEvents = eventRepository.findAll();
        for (Event event : allEvents) {
            migrateEventHostsToInvites(event.getId());
        }
    }
}