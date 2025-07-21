package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.model.User;
import com.bbthechange.inviter.repository.UserRepository;
import com.bbthechange.inviter.service.JwtService;
import com.bbthechange.inviter.service.PasswordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
public class AuthController {
    
    @Autowired
    private JwtService jwtService;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordService passwordService;
    
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody User user) {
        Optional<User> existingUser = userRepository.findByPhoneNumber(user.getPhoneNumber());
        
        if (existingUser.isPresent() && existingUser.get().getPassword() != null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "User already exists");
            return new ResponseEntity<>(error, HttpStatus.CONFLICT);
        }
        
        if (existingUser.isPresent()) {
            User existingUserEntity = existingUser.get();
            existingUserEntity.setUsername(user.getUsername());
            existingUserEntity.setPassword(passwordService.encryptPassword(user.getPassword()));
            userRepository.save(existingUserEntity);
        } else {
            User newUser = new User(user.getPhoneNumber(), user.getUsername(), passwordService.encryptPassword(user.getPassword()));
            userRepository.save(newUser);
        }
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "User registered successfully");
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest loginRequest) {
        Optional<User> userOpt = userRepository.findByPhoneNumber(loginRequest.getPhoneNumber());
        
        if (userOpt.isEmpty() || userOpt.get().getPassword() == null || 
            !passwordService.matches(loginRequest.getPassword(), userOpt.get().getPassword())) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Invalid credentials");
            return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
        }
        
        User user = userOpt.get();
        String token = jwtService.generateToken(user.getId().toString());
        
        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("expiresIn", 86400);
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