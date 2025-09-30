package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InviterKeyFactory;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

/**
 * Group membership entity for the InviterTable.
 * Represents a user's membership in a group with denormalized group name for GSI efficiency.
 * 
 * Key Pattern: PK = GROUP#{GroupID}, SK = USER#{UserID}  
 * GSI Pattern: GSI1PK = USER#{UserID}, GSI1SK = GROUP#{GroupID}
 */
@DynamoDbBean
public class GroupMembership extends BaseItem {
    
    private String groupId;
    private String userId;
    private String groupName;  // Denormalized for GSI query efficiency
    private String role;       // GroupRole.ADMIN or GroupRole.MEMBER
    private String groupMainImagePath;       // Denormalized from Group
    private String groupBackgroundImagePath; // Denormalized from Group
    private String userMainImagePath;        // Denormalized from User
    
    // Default constructor for DynamoDB
    public GroupMembership() {
        super();
        setItemType("GROUP_MEMBERSHIP");
    }

    /**
     * Create a new group membership record.
     */
    public GroupMembership(String groupId, String userId, String groupName) {
        super();
        setItemType("GROUP_MEMBERSHIP");
        this.groupId = groupId;
        this.userId = userId;
        this.groupName = groupName;
        this.role = GroupRole.MEMBER; // Default role
        
        // Set main table keys
        setPk(InviterKeyFactory.getGroupPk(groupId));
        setSk(InviterKeyFactory.getUserSk(userId));
        
        // Set GSI keys for efficient user -> groups queries
        setGsi1pk(InviterKeyFactory.getUserGsi1Pk(userId));
        setGsi1sk(InviterKeyFactory.getGroupGsi1Sk(groupId));
    }
    
    public String getGroupId() {
        return groupId;
    }
    
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getGroupName() {
        return groupName;
    }
    
    public void setGroupName(String groupName) {
        this.groupName = groupName;
        touch(); // Update timestamp
    }
    
    public String getRole() {
        return role;
    }
    
    public void setRole(String role) {
        this.role = role;
        touch(); // Update timestamp
    }
    
    /**
     * Check if this member has admin privileges.
     */
    public boolean isAdmin() {
        return GroupRole.ADMIN.equals(role);
    }

    public String getGroupMainImagePath() {
        return groupMainImagePath;
    }

    public void setGroupMainImagePath(String groupMainImagePath) {
        this.groupMainImagePath = groupMainImagePath;
        touch(); // Update timestamp
    }

    public String getGroupBackgroundImagePath() {
        return groupBackgroundImagePath;
    }

    public void setGroupBackgroundImagePath(String groupBackgroundImagePath) {
        this.groupBackgroundImagePath = groupBackgroundImagePath;
        touch(); // Update timestamp
    }

    public String getUserMainImagePath() {
        return userMainImagePath;
    }

    public void setUserMainImagePath(String userMainImagePath) {
        this.userMainImagePath = userMainImagePath;
        touch(); // Update timestamp
    }
}