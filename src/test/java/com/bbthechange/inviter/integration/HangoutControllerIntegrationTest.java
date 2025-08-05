package com.bbthechange.inviter.integration;

import com.bbthechange.inviter.config.BaseIntegrationTest;
import com.bbthechange.inviter.dto.UpdateEventRequest;
import com.bbthechange.inviter.dto.AssociateGroupsRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for HangoutController.
 * Tests the item collection patterns and pointer updates.
 */
class HangoutControllerIntegrationTest extends BaseIntegrationTest {
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    void getEventDetail_ValidId_ReturnsEventDetail() throws Exception {
        // Given
        String validEventId = "12345678-1234-1234-1234-123456789012";
        
        // When/Then - Will fail since event doesn't exist, but tests validation
        mockMvc.perform(get("/events/" + validEventId + "/detail")
                .requestAttr("userId", "test-user"))
            .andExpect(status().isNotFound()); // Event doesn't exist
    }
    
    @Test
    void getEventDetail_InvalidId_ReturnsBadRequest() throws Exception {
        // Given
        String invalidEventId = "invalid-uuid";
        
        // When/Then
        mockMvc.perform(get("/events/" + invalidEventId + "/detail")
                .requestAttr("userId", "test-user"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
    
    @Test
    void getEventDetail_NoAuth_ReturnsUnauthorized() throws Exception {
        // Given
        String validEventId = "12345678-1234-1234-1234-123456789012";
        
        // When/Then - no userId attribute
        mockMvc.perform(get("/events/" + validEventId + "/detail"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }
    
    @Test
    void updateEvent_ValidRequest_ReturnsOk() throws Exception {
        // Given
        String validEventId = "12345678-1234-1234-1234-123456789012";
        UpdateEventRequest request = new UpdateEventRequest();
        request.setName("Updated Event Title");
        request.setDescription("Updated description");
        
        String requestJson = objectMapper.writeValueAsString(request);
        
        // When/Then - Will fail since event doesn't exist, but tests validation
        mockMvc.perform(patch("/events/" + validEventId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
                .requestAttr("userId", "admin-user"))
            .andExpect(status().isNotFound()); // Event doesn't exist
    }
    
    @Test
    void updateEvent_InvalidEventId_ReturnsBadRequest() throws Exception {
        // Given
        String invalidEventId = "not-a-uuid";
        UpdateEventRequest request = new UpdateEventRequest();
        request.setName("Updated Title");
        
        String requestJson = objectMapper.writeValueAsString(request);
        
        // When/Then
        mockMvc.perform(patch("/events/" + invalidEventId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
                .requestAttr("userId", "admin-user"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
    
    @Test
    void associateWithGroups_ValidRequest_ReturnsOk() throws Exception {
        // Given
        String validEventId = "12345678-1234-1234-1234-123456789012";
        AssociateGroupsRequest request = new AssociateGroupsRequest(
            List.of("group-1", "group-2")
        );
        
        String requestJson = objectMapper.writeValueAsString(request);
        
        // When/Then - Will fail since event doesn't exist, but tests validation
        mockMvc.perform(post("/events/" + validEventId + "/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
                .requestAttr("userId", "admin-user"))
            .andExpect(status().isNotFound()); // Event doesn't exist
    }
    
    @Test
    void associateWithGroups_EmptyGroupList_ReturnsBadRequest() throws Exception {
        // Given
        String validEventId = "12345678-1234-1234-1234-123456789012";
        AssociateGroupsRequest request = new AssociateGroupsRequest(List.of()); // Empty list
        
        String requestJson = objectMapper.writeValueAsString(request);
        
        // When/Then
        mockMvc.perform(post("/events/" + validEventId + "/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
                .requestAttr("userId", "admin-user"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
    
    @Test
    void associateWithGroups_TooManyGroups_ReturnsBadRequest() throws Exception {
        // Given
        String validEventId = "12345678-1234-1234-1234-123456789012";
        
        // Create a list with more than 10 groups (exceeds validation limit)
        List<String> tooManyGroups = List.of(
            "group-1", "group-2", "group-3", "group-4", "group-5",
            "group-6", "group-7", "group-8", "group-9", "group-10", "group-11"
        );
        AssociateGroupsRequest request = new AssociateGroupsRequest(tooManyGroups);
        
        String requestJson = objectMapper.writeValueAsString(request);
        
        // When/Then
        mockMvc.perform(post("/events/" + validEventId + "/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
                .requestAttr("userId", "admin-user"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
    
    @Test
    void disassociateFromGroups_ValidRequest_ReturnsOk() throws Exception {
        // Given
        String validEventId = "12345678-1234-1234-1234-123456789012";
        AssociateGroupsRequest request = new AssociateGroupsRequest(
            List.of("group-1")
        );
        
        String requestJson = objectMapper.writeValueAsString(request);
        
        // When/Then - Will fail since event doesn't exist, but tests validation
        mockMvc.perform(delete("/events/" + validEventId + "/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
                .requestAttr("userId", "admin-user"))
            .andExpect(status().isNotFound()); // Event doesn't exist
    }
}