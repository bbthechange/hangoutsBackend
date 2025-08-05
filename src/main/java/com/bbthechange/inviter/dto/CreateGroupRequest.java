package com.bbthechange.inviter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new group.
 */
public class CreateGroupRequest {
    
    @NotBlank(message = "Group name is required")
    @Size(min = 1, max = 100, message = "Group name must be between 1 and 100 characters")
    private String groupName;
    
    @NotNull(message = "Public setting is required")
    private Boolean isPublic;
    
    public CreateGroupRequest() {}
    
    public CreateGroupRequest(String groupName, Boolean isPublic) {
        this.groupName = groupName;
        this.isPublic = isPublic;
    }
    
    // Input sanitization in getter
    public String getGroupName() { 
        return groupName != null ? groupName.trim() : null; 
    }
    
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
    
    public Boolean isPublic() {
        return isPublic;
    }
    
    public void setPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }
}