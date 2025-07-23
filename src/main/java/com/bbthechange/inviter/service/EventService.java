package com.bbthechange.inviter.service;

import com.bbthechange.inviter.model.Event;
import com.bbthechange.inviter.model.Invite;
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
                                       List<UUID> hostUserIds, List<String> invitePhoneNumbers) {
        
        Event event = new Event(name, description, startTime, endTime, location, visibility, mainImagePath, hostUserIds);
        eventRepository.save(event);
        
        for (String phoneNumber : invitePhoneNumbers) {
            User user = findOrCreateUserByPhoneNumber(phoneNumber);
            Invite invite = new Invite(event.getId(), user.getId());
            inviteRepository.save(invite);
        }
        
        return event;
    }
    
    public List<Event> getEventsForUser(UUID userId) {
        List<Event> events = new ArrayList<>();
        
        // Get events where user is invited
        List<Invite> userInvites = inviteRepository.findByUserId(userId);
        for (Invite invite : userInvites) {
            Optional<Event> eventOpt = eventRepository.findById(invite.getEventId());
            eventOpt.ifPresent(events::add);
        }
        
        // Get events where user is a host (using table scan - could be optimized with GSI)
        List<Event> allEvents = eventRepository.findAll();
        for (Event event : allEvents) {
            if (event.getHosts().contains(userId) && !events.contains(event)) {
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
        
        // Check if user is a host (always has access)
        if (event.getHosts().contains(userId)) {
            return eventOpt;
        }
        
        // Check if user is invited
        List<Invite> userInvites = inviteRepository.findByUserId(userId);
        boolean userInvited = userInvites.stream()
                .anyMatch(invite -> invite.getEventId().equals(eventId));
        
        if (!userInvited) {
            return Optional.empty();
        }
        
        return eventOpt;
    }
    
    public boolean isUserHostOfEvent(UUID userId, UUID eventId) {
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            return false;
        }
        
        Event event = eventOpt.get();
        return event.getHosts().contains(userId);
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
}