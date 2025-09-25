package com.bbthechange.inviter.dto;

import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating group attributes via PATCH.
 * Fields are optional - only provided fields will be updated.
 */
public class UpdateGroupRequest {

    @Size(min = 1, max = 100, message = "Group name must be between 1 and 100 characters")
    private String groupName;

    private Boolean isPublic;

    public UpdateGroupRequest() {}

    public UpdateGroupRequest(String groupName, Boolean isPublic) {
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

    /**
     * Check if any field is provided for update
     */
    public boolean hasUpdates() {
        return groupName != null || isPublic != null;
    }
}