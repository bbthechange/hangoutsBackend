package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.ChangePasswordRequest;
import com.bbthechange.inviter.dto.UpdateProfileRequest;
import com.bbthechange.inviter.model.User;
import com.bbthechange.inviter.service.UserService;
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
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/profile")
@Tag(name = "Profile", description = "User profile management")
@SecurityRequirement(name = "Bearer Authentication")
public class ProfileController {
    
    @Autowired
    private UserService userService;
    
    @GetMapping
    @Operation(summary = "Get user profile", description = "Returns the current user's profile information")
    public ResponseEntity<User> getProfile(HttpServletRequest request) {
        String userIdStr = (String) request.getAttribute("userId");
        if (userIdStr == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        
        UUID userId = UUID.fromString(userIdStr);
        Optional<User> userOpt = userService.getUserById(userId);
        
        if (userOpt.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        
        User user = userOpt.get();
        // Don't expose password in response
        user.setPassword(null);
        
        return new ResponseEntity<>(user, HttpStatus.OK);
    }
    
    @PutMapping
    @Operation(summary = "Update user profile", description = "Updates the current user's profile information")
    public ResponseEntity<Map<String, Object>> updateProfile(
            @RequestBody UpdateProfileRequest request,
            HttpServletRequest httpRequest) {
        
        String userIdStr = (String) httpRequest.getAttribute("userId");
        if (userIdStr == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        
        UUID userId = UUID.fromString(userIdStr);
        
        try {
            User updatedUser = userService.updateDisplayName(userId, request.getDisplayName());
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Profile updated successfully");
            response.put("displayName", updatedUser.getDisplayName());
            
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
        }
    }
    
    @PutMapping("/password")
    @Operation(summary = "Change password", 
               description = "Changes the current user's password. Requires current password for verification.")
    public ResponseEntity<Map<String, String>> changePassword(
            @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest) {
        
        String userIdStr = (String) httpRequest.getAttribute("userId");
        if (userIdStr == null) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        
        UUID userId = UUID.fromString(userIdStr);
        
        try {
            userService.changePassword(userId, request.getCurrentPassword(), request.getNewPassword());
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Password changed successfully");
            
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
        } catch (IllegalStateException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
        }
    }
    
}