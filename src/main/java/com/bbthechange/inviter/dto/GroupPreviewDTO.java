package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.Group;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO for previewing group information before joining.
 * Used by the public invite code preview endpoint.
 * Does not include groupId for security - only minimal info needed for preview.
 *
 * Privacy: If group is private, only isPrivate flag is set to true,
 * and name/image are null to prevent leaking private group information.
 * Null fields are omitted from JSON response.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GroupPreviewDTO {

    @JsonProperty("isPrivate")
    private boolean isPrivate;
    private String groupName;
    private String mainImagePath;

    public GroupPreviewDTO() {}

    public GroupPreviewDTO(Group group) {
        this.isPrivate = !group.isPublic();

        // Only expose group details if public
        if (group.isPublic()) {
            this.groupName = group.getGroupName();
            this.mainImagePath = group.getMainImagePath();
        }
        // For private groups: name and image remain null (omitted from JSON)
    }
}
