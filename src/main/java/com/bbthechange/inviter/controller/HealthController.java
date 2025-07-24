package com.bbthechange.inviter.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    
    @GetMapping("/")
    public String home() {
        System.out.println("=== DEBUG: ROOT endpoint called! Web server is working! ===");
        return "SUCCESS: Inviter API is running on port 5000";
    }
    
    @GetMapping("/health")
    public String health() {
        System.out.println("=== DEBUG: HEALTH endpoint called! ===");
        return "OK";
    }
}