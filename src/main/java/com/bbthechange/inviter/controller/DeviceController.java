package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.DeviceRegistrationRequest;
import com.bbthechange.inviter.dto.DeviceTokenRequest;
import com.bbthechange.inviter.model.Device;
import com.bbthechange.inviter.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/devices")
@Tag(name = "Devices", description = "Device management for push notifications")
@SecurityRequirement(name = "Bearer Authentication")
public class DeviceController {
    
    @Autowired
    private DeviceService deviceService;
    
    @PostMapping
    @Operation(summary = "Register device", 
               description = "Registers or updates a device token for push notifications (iOS or Android)")
    public ResponseEntity<Map<String, String>> registerDevice(
            @RequestBody DeviceRegistrationRequest request,
            HttpServletRequest httpRequest) {
        
        String userIdStr = (String) httpRequest.getAttribute("userId");
        if (userIdStr == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        
        UUID userId = UUID.fromString(userIdStr);
        
        try {
            Device.Platform platform;
            if ("ios".equalsIgnoreCase(request.getPlatform())) {
                platform = Device.Platform.IOS;
            } else if ("android".equalsIgnoreCase(request.getPlatform())) {
                platform = Device.Platform.ANDROID;
            } else {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Invalid platform. Must be 'ios' or 'android'");
                return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
            }
            
            deviceService.registerDevice(request.getToken(), userId, platform);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Device registered successfully");
            
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to register device: " + e.getMessage());
            return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    @DeleteMapping
    @Operation(summary = "Remove device", 
               description = "Removes a device token (used when user logs out)")
    public ResponseEntity<Map<String, String>> removeDevice(
            @RequestBody DeviceTokenRequest request,
            HttpServletRequest httpRequest) {
        
        String userIdStr = (String) httpRequest.getAttribute("userId");
        if (userIdStr == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        
        try {
            deviceService.deleteDevice(request.getToken());
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Device removed successfully");
            
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to remove device: " + e.getMessage());
            return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}