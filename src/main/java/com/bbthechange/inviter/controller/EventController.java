package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.model.Event;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/events")
public class EventController {
    
    private final Map<UUID, Event> eventStore = new HashMap<>();
    
    @PostMapping("/new")
    public ResponseEntity<Map<String, UUID>> createEvent(@RequestBody Event event) {
        Event newEvent = new Event(
            event.getName(),
            event.getDescription(),
            event.getStartTime(),
            event.getEndTime(),
            event.getInvitePhoneNumbers()
        );
        
        eventStore.put(newEvent.getId(), newEvent);
        
        Map<String, UUID> response = new HashMap<>();
        response.put("id", newEvent.getId());
        
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    @GetMapping
    public ResponseEntity<Collection<Event>> getAllEvents(HttpServletRequest request) {
        String userPhoneNumber = (String) request.getAttribute("userPhoneNumber");
        
        if (userPhoneNumber == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        
        Collection<Event> userEvents = eventStore.values().stream()
            .filter(event -> event.getInvitePhoneNumbers().contains(userPhoneNumber))
            .collect(Collectors.toList());
            
        return new ResponseEntity<>(userEvents, HttpStatus.OK);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Event> getEvent(@PathVariable UUID id, HttpServletRequest request) {
        String userPhoneNumber = (String) request.getAttribute("userPhoneNumber");
        
        if (userPhoneNumber == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        
        Event event = eventStore.get(id);
        
        if (event == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        
        if (!event.getInvitePhoneNumbers().contains(userPhoneNumber)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        
        return new ResponseEntity<>(event, HttpStatus.OK);
    }
}