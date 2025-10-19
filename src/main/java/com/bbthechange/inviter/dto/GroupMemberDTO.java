package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.GroupMembership;

import java.time.Instant;

/**
 * Data Transfer Object for Group Member information.
 */
public class GroupMemberDTO {

    private String userId;
    private String userName;        // Current user display name
    private String mainImagePath;   // Current user profile image
    private String role;
    private Instant joinedAt;

    public GroupMemberDTO(GroupMembership membership, String userName, String mainImagePath) {
        this.userId = membership.getUserId();
        this.userName = userName;
        this.mainImagePath = mainImagePath;
        this.role = membership.getRole();
        this.joinedAt = membership.getCreatedAt();
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getUserName() {
        return userName;
    }
    
    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getMainImagePath() {
        return mainImagePath;
    }

    public void setMainImagePath(String mainImagePath) {
        this.mainImagePath = mainImagePath;
    }

    public String getRole() {
        return role;
    }
    
    public void setRole(String role) {
        this.role = role;
    }
    
    public Instant getJoinedAt() {
        return joinedAt;
    }
    
    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }
}