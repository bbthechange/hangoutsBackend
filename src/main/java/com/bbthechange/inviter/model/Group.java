package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InviterKeyFactory;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

import java.util.UUID;

/**
 * Group entity for the InviterTable.
 * Represents a group metadata record.
 * 
 * Key Pattern: PK = GROUP#{GroupID}, SK = METADATA
 */
@DynamoDbBean
public class Group extends BaseItem {
    
    private String groupId;
    private String groupName;
    private boolean isPublic;
    
    // Default constructor for DynamoDB
    public Group() {
        super();
    }

    /**
     * Create a new group with generated UUID.
     */
    public Group(String groupName, boolean isPublic) {
        super();
        this.groupId = UUID.randomUUID().toString();
        this.groupName = groupName;
        this.isPublic = isPublic;
        
        // Set keys using InviterKeyFactory
        setPk(InviterKeyFactory.getGroupPk(this.groupId));
        setSk(InviterKeyFactory.getMetadataSk());
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
        touch(); // Update timestamp
    }
    
    public boolean isPublic() {
        return isPublic;
    }
    
    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
        touch(); // Update timestamp
    }
}