package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.service.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/migration")
public class MigrationController {
    
    @Autowired
    private EventService eventService;
    
    @PostMapping("/migrate-hosts")
    public ResponseEntity<Map<String, String>> migrateHostsToInvites() {
        try {
            eventService.migrateAllEventHostsToInvites();
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Successfully migrated all event hosts to invite system");
            response.put("status", "completed");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "Migration failed: " + e.getMessage());
            response.put("status", "error");
            
            return ResponseEntity.status(500).body(response);
        }
    }
}