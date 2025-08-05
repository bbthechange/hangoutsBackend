package com.bbthechange.inviter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for adding a member to a group.
 */
public class AddMemberRequest {
    
    @NotBlank(message = "User ID is required")
    @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid user ID format")
    private String userId;
    
    public AddMemberRequest() {}
    
    public AddMemberRequest(String userId) {
        this.userId = userId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
}