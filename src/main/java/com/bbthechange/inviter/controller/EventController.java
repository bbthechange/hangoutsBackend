package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.CreateEventRequest;
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
    public ResponseEntity<Map<String, UUID>> createEvent(@RequestBody CreateEventRequest request, HttpServletRequest httpRequest) {
        String userIdStr = (String) httpRequest.getAttribute("userId");
        
        if (userIdStr == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        
        UUID userId = UUID.fromString(userIdStr);
        List<UUID> hosts = Arrays.asList(userId);
        
        Event newEvent = eventService.createEventWithInvites(
            request.getName(),
            request.getDescription(),
            request.getStartTime(),
            request.getEndTime(),
            hosts,
            request.getInvitePhoneNumbers()
        );
        
        Map<String, UUID> response = new HashMap<>();
        response.put("id", newEvent.getId());
        
        return new ResponseEntity<>(response, HttpStatus.CREATED);
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
}