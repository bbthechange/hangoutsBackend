package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.Group;
import com.bbthechange.inviter.model.GroupMembership;

import java.time.Instant;

/**
 * Data Transfer Object for Group information.
 * Can be constructed from Group entity or GroupMembership (for user's groups list).
 */
public class GroupDTO {
    
    private String groupId;
    private String groupName;
    private boolean isPublic;
    private String userRole;        // Role of the requesting user in this group
    private Instant joinedAt;       // When the user joined (if applicable)
    private Instant createdAt;
    private String mainImagePath;   // Group's main image
    private String backgroundImagePath; // Group's background image
    private String userMainImagePath;   // User's profile image (denormalized in membership)
    
    // Constructor from Group entity (with user membership info)
    public GroupDTO(Group group, GroupMembership membership) {
        this.groupId = group.getGroupId();
        this.groupName = group.getGroupName();
        this.isPublic = group.isPublic();
        this.createdAt = group.getCreatedAt();
        this.mainImagePath = group.getMainImagePath();
        this.backgroundImagePath = group.getBackgroundImagePath();

        if (membership != null) {
            this.userRole = membership.getRole();
            this.joinedAt = membership.getCreatedAt();
            this.userMainImagePath = membership.getUserMainImagePath();
        }
    }
    
    // Constructor from GroupMembership (for efficient user groups query - no additional lookup needed!)
    public GroupDTO(String groupId, String groupName, String userRole, Instant joinedAt,
                    String groupMainImagePath, String groupBackgroundImagePath, String userMainImagePath) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.userRole = userRole;
        this.joinedAt = joinedAt;
        this.mainImagePath = groupMainImagePath;
        this.backgroundImagePath = groupBackgroundImagePath;
        this.userMainImagePath = userMainImagePath;
    }

    // Backward compatibility constructor (deprecated)
    @Deprecated
    public GroupDTO(String groupId, String groupName, String userRole, Instant joinedAt) {
        this(groupId, groupName, userRole, joinedAt, null, null, null);
    }
    
    public String getGroupId() {
        return groupId;
    }
    
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
    
    public String getGroupName() {
        return groupName;
    }
    
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
    
    public boolean isPublic() {
        return isPublic;
    }
    
    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }
    
    public String getUserRole() {
        return userRole;
    }
    
    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }
    
    public Instant getJoinedAt() {
        return joinedAt;
    }
    
    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getMainImagePath() {
        return mainImagePath;
    }

    public void setMainImagePath(String mainImagePath) {
        this.mainImagePath = mainImagePath;
    }

    public String getBackgroundImagePath() {
        return backgroundImagePath;
    }

    public void setBackgroundImagePath(String backgroundImagePath) {
        this.backgroundImagePath = backgroundImagePath;
    }

    public String getUserMainImagePath() {
        return userMainImagePath;
    }

    public void setUserMainImagePath(String userMainImagePath) {
        this.userMainImagePath = userMainImagePath;
    }
}