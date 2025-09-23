package com.bbthechange.inviter.integration;

import com.bbthechange.inviter.controller.AuthController.LoginRequest;
import com.bbthechange.inviter.model.User;
import com.bbthechange.inviter.repository.UserRepository;
import com.bbthechange.inviter.service.JwtService;
import com.bbthechange.inviter.service.PasswordService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Working integration tests for AuthController endpoints
 * Uses mocked repositories but tests real HTTP endpoints and Spring Security
 */
@SpringBootTest
@AutoConfigureWebMvc
@DisplayName("Simple Auth Integration Tests")
public class SimpleAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserRepository userRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private JwtService jwtService;

    @Test
    @DisplayName("Should register new user successfully")
    void register_Success_NewUser() throws Exception {
        // Arrange
        User newUser = new User("+1234567890", "testuser", "Test User", "password123");
        
        // Mock repository to return empty (user doesn't exist)
        when(userRepository.findByPhoneNumber("+1234567890")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(newUser);

        // Act & Assert
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("User registered successfully"));
    }

    @Test
    @DisplayName("Should return conflict when user already exists")
    void register_Conflict_UserExists() throws Exception {
        // Arrange
        User newUser = new User("+1234567890", "testuser", "Test User", "password123");
        User existingUser = new User("+1234567890", "existing", "Existing User", "hashedpass");
        existingUser.setId(UUID.randomUUID());
        
        // Mock repository to return existing user
        when(userRepository.findByPhoneNumber("+1234567890")).thenReturn(Optional.of(existingUser));

        // Act & Assert
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("User already exists"));
    }

    @Test
    @DisplayName("Should login successfully with valid credentials")
    void login_Success_ValidCredentials() throws Exception {
        // Arrange
        String rawPassword = "password123";
        String hashedPassword = passwordService.encryptPassword(rawPassword);
        
        User user = new User("+1234567890", "testuser", "Test User", hashedPassword);
        user.setId(UUID.randomUUID());
        
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setPhoneNumber("+1234567890");
        loginRequest.setPassword(rawPassword);

        // Mock repository to return user
        when(userRepository.findByPhoneNumber("+1234567890")).thenReturn(Optional.of(user));

        // Act & Assert
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.expiresIn").value(86400));
    }

    @Test
    @DisplayName("Should return unauthorized for non-existent user")
    void login_Unauthorized_UserNotFound() throws Exception {
        // Arrange
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setPhoneNumber("+9999999999");
        loginRequest.setPassword("password123");

        // Mock repository to return empty (user doesn't exist)
        when(userRepository.findByPhoneNumber("+9999999999")).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    @Test
    @DisplayName("Should return unauthorized for wrong password")
    void login_Unauthorized_WrongPassword() throws Exception {
        // Arrange
        String correctPassword = "correctpassword";
        String wrongPassword = "wrongpassword";
        String hashedPassword = passwordService.encryptPassword(correctPassword);
        
        User user = new User("+1234567890", "testuser", "Test User", hashedPassword);
        user.setId(UUID.randomUUID());
        
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setPhoneNumber("+1234567890");
        loginRequest.setPassword(wrongPassword);

        // Mock repository to return user
        when(userRepository.findByPhoneNumber("+1234567890")).thenReturn(Optional.of(user));

        // Act & Assert
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    @Test
    @DisplayName("Should handle malformed JSON request")
    void login_BadRequest_MalformedJson() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{invalid json}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should complete authentication workflow")
    void completeAuthenticationWorkflow() throws Exception {
        // Step 1: Register a new user
        User newUser = new User("+1555000001", "workflowuser", "Workflow User", "testpassword");
        
        when(userRepository.findByPhoneNumber("+1555000001")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(newUser);

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isCreated());

        // Step 2: Login with the registered user
        String hashedPassword = passwordService.encryptPassword("testpassword");
        User savedUser = new User("+1555000001", "workflowuser", "Workflow User", hashedPassword);
        savedUser.setId(UUID.randomUUID());
        
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setPhoneNumber("+1555000001");
        loginRequest.setPassword("testpassword");

        when(userRepository.findByPhoneNumber("+1555000001")).thenReturn(Optional.of(savedUser));

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.expiresIn").value(86400));
    }
}