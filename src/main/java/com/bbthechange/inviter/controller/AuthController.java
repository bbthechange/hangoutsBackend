package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.model.User;
import com.bbthechange.inviter.service.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {
    
    @Autowired
    private JwtService jwtService;
    
    private final Map<String, User> userStore = new HashMap<>();
    
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody User user) {
        if (userStore.containsKey(user.getPhoneNumber())) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "User already exists");
            return new ResponseEntity<>(error, HttpStatus.CONFLICT);
        }
        
        userStore.put(user.getPhoneNumber(), user);
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "User registered successfully");
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest loginRequest) {
        User user = userStore.get(loginRequest.getPhoneNumber());
        
        if (user == null || !user.getPassword().equals(loginRequest.getPassword())) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Invalid credentials");
            return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
        }
        
        String token = jwtService.generateToken(user.getPhoneNumber());
        
        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("expiresIn", 86400); // 24 hours in seconds
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
    
    public static class LoginRequest {
        private String phoneNumber;
        private String password;
        
        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}