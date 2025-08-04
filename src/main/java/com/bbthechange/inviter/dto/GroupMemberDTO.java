package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.GroupMembership;

import java.time.Instant;

/**
 * Data Transfer Object for Group Member information.
 */
public class GroupMemberDTO {
    
    private String userId;
    private String userName;        // We'll need to get this from User lookup
    private String role;
    private Instant joinedAt;
    
    public GroupMemberDTO(GroupMembership membership, String userName) {
        this.userId = membership.getUserId();
        this.userName = userName;
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