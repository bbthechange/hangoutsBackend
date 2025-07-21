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
                                       LocalDateTime endTime, List<UUID> hosts, List<String> invitePhoneNumbers) {
        
        Event event = new Event(name, description, startTime, endTime, hosts);
        eventRepository.save(event);
        
        for (String phoneNumber : invitePhoneNumbers) {
            User user = findOrCreateUserByPhoneNumber(phoneNumber);
            Invite invite = new Invite(event.getId(), user.getId());
            inviteRepository.save(invite);
        }
        
        return event;
    }
    
    public List<Event> getEventsForUser(UUID userId) {
        List<Invite> userInvites = inviteRepository.findByUserId(userId);
        List<Event> events = new ArrayList<>();
        
        for (Invite invite : userInvites) {
            Optional<Event> eventOpt = eventRepository.findById(invite.getEventId());
            eventOpt.ifPresent(events::add);
        }
        
        return events;
    }
    
    public Optional<Event> getEventForUser(UUID eventId, UUID userId) {
        List<Invite> userInvites = inviteRepository.findByUserId(userId);
        boolean userInvited = userInvites.stream()
                .anyMatch(invite -> invite.getEventId().equals(eventId));
        
        if (!userInvited) {
            return Optional.empty();
        }
        
        return eventRepository.findById(eventId);
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