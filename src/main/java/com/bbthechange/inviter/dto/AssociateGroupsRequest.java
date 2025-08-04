package com.bbthechange.inviter.dto;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * Request DTO for associating/disassociating events with groups.
 */
public class AssociateGroupsRequest {
    
    @NotEmpty(message = "Group IDs list cannot be empty")
    @Size(max = 10, message = "Cannot associate with more than 10 groups at once")
    private List<String> groupIds;
    
    public AssociateGroupsRequest() {}
    
    public AssociateGroupsRequest(List<String> groupIds) {
        this.groupIds = groupIds;
    }
    
    public List<String> getGroupIds() {
        return groupIds;
    }
    
    public void setGroupIds(List<String> groupIds) {
        this.groupIds = groupIds;
    }
}