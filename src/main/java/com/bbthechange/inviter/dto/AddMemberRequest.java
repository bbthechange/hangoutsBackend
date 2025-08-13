package com.bbthechange.inviter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * Request DTO for adding a member to a group.
 */
@Data
public class AddMemberRequest {
    
    @Pattern(regexp = "[0-9a-f-]{36}", message = "Invalid user ID format")
    private String userId;

    private String phoneNumber;
    
    public AddMemberRequest() {}
    
    public AddMemberRequest(String userId) {
        this.userId = userId;
    }

}