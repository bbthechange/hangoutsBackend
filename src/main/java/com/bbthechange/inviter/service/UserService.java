package com.bbthechange.inviter.service;

import com.bbthechange.inviter.model.User;
import com.bbthechange.inviter.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordService passwordService;
    
    public User updateDisplayName(UUID userId, String displayName) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }
        
        User user = userOpt.get();
        user.setDisplayName(displayName);
        return userRepository.save(user);
    }
    
    public User changePassword(UUID userId, String currentPassword, String newPassword) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }
        
        User user = userOpt.get();
        
        // Verify current password
        if (user.getPassword() == null || !passwordService.matches(currentPassword, user.getPassword())) {
            throw new IllegalStateException("Current password is incorrect");
        }
        
        // Update to new password
        user.setPassword(passwordService.encryptPassword(newPassword));
        return userRepository.save(user);
    }
    
    public Optional<User> getUserById(UUID userId) {
        return userRepository.findById(userId);
    }
}