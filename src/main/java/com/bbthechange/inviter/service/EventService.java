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
        
        // Handle null or empty invite requests
        if (inviteRequests == null) {
            inviteRequests = new ArrayList<>();
        }
        
        // Create event (hosts managed via invites)
        Event event = new Event(name, description, startTime, endTime, location, visibility, mainImagePath);
        eventRepository.save(event);
        
        // Create invites
        for (CreateInviteRequest inviteRequest : inviteRequests) {
            User user = findOrCreateUserByPhoneNumber(inviteRequest.getPhoneNumber());
            Invite invite = new Invite(event.getId(), user.getId(), inviteRequest.getType());
            inviteRepository.save(invite);
        }
        
        return event;
    }
    
    
    public List<Event> getEventsForUser(UUID userId) {
        List<Event> events = new ArrayList<>();
        
        // Get events where user is invited (includes both HOST and GUEST invites)
        List<Invite> userInvites = inviteRepository.findByUserId(userId);
        for (Invite invite : userInvites) {
            Optional<Event> eventOpt = eventRepository.findById(invite.getEventId());
            eventOpt.ifPresent(events::add);
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
        
        return Optional.empty();
    }
    
    public boolean isUserHostOfEvent(UUID userId, UUID eventId) {
        // Check via invites first (new system)
        List<Invite> userInvites = inviteRepository.findByUserId(userId);
        boolean isHostViaInvite = userInvites.stream()
                .anyMatch(invite -> invite.getEventId().equals(eventId) && invite.getType() == InviteType.HOST);
        
        return isHostViaInvite;
    }
    
    public Event updateEvent(UUID eventId, String name, String description, LocalDateTime startTime, 
                           LocalDateTime endTime, com.bbthechange.inviter.dto.Address location, 
                           com.bbthechange.inviter.model.EventVisibility visibility, String mainImagePath) {
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
    
}