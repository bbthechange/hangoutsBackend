package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.CreateEventRequest;
import com.bbthechange.inviter.dto.CreateEventWithInvitesRequest;
import com.bbthechange.inviter.dto.UpdateEventRequest;
import com.bbthechange.inviter.model.EventVisibility;
import com.bbthechange.inviter.model.Event;
import com.bbthechange.inviter.repository.UserRepository;
import com.bbthechange.inviter.service.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/events")
public class EventController {
    
    @Autowired
    private EventService eventService;
    
    @Autowired
    private UserRepository userRepository;
    
    @PostMapping("/new")
    public ResponseEntity<Map<String, UUID>> createEvent(@RequestBody CreateEventWithInvitesRequest request, HttpServletRequest httpRequest) {
        String userIdStr = (String) httpRequest.getAttribute("userId");
        
        if (userIdStr == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        
        UUID userId = UUID.fromString(userIdStr);
        
        // Set default visibility if not provided
        EventVisibility visibility = request.getVisibility();
        if (visibility == null) {
            visibility = EventVisibility.INVITE_ONLY;
        }
        
        try {
            Event newEvent = eventService.createEventWithInvites(
                request.getName(),
                request.getDescription(),
                request.getStartTime(),
                request.getEndTime(),
                request.getLocation(),
                visibility,
                request.getMainImagePath(),
                request.getInvites()
            );
            
            Map<String, UUID> response = new HashMap<>();
            response.put("id", newEvent.getId());
            
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }
    
    @GetMapping
    public ResponseEntity<List<Event>> getAllEvents(HttpServletRequest request) {
        String userIdStr = (String) request.getAttribute("userId");
        
        if (userIdStr == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        
        UUID userId = UUID.fromString(userIdStr);
        List<Event> userEvents = eventService.getEventsForUser(userId);
            
        return new ResponseEntity<>(userEvents, HttpStatus.OK);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Event> getEvent(@PathVariable UUID id, HttpServletRequest request) {
        String userIdStr = (String) request.getAttribute("userId");
        
        if (userIdStr == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        
        UUID userId = UUID.fromString(userIdStr);
        Optional<Event> eventOpt = eventService.getEventForUser(id, userId);
        
        if (eventOpt.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        
        return new ResponseEntity<>(eventOpt.get(), HttpStatus.OK);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<Event> updateEvent(@PathVariable UUID id, @RequestBody UpdateEventRequest request, HttpServletRequest httpRequest) {
        String userIdStr = (String) httpRequest.getAttribute("userId");
        
        if (userIdStr == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        
        UUID userId = UUID.fromString(userIdStr);
        
        // Validate that user is a host of this event
        if (!eventService.isUserHostOfEvent(userId, id)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        
        try {
            Event updatedEvent = eventService.updateEvent(
                id,
                request.getName(),
                request.getDescription(),
                request.getStartTime(),
                request.getEndTime(),
                request.getLocation(),
                request.getVisibility(),
                request.getMainImagePath(),
                request.getHostUserIds()
            );
            
            return new ResponseEntity<>(updatedEvent, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteEvent(@PathVariable UUID id, HttpServletRequest httpRequest) {
        String userIdStr = (String) httpRequest.getAttribute("userId");
        
        if (userIdStr == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        
        UUID userId = UUID.fromString(userIdStr);
        
        // Validate that user is a host of this event
        if (!eventService.isUserHostOfEvent(userId, id)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        
        try {
            eventService.deleteEvent(id);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Event deleted successfully");
            
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}