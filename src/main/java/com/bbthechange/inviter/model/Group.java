package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InviterKeyFactory;
import com.bbthechange.inviter.util.InstantAsLongAttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;

import java.time.Instant;
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
    private String mainImagePath;
    private String backgroundImagePath;
    private Instant lastHangoutModified;  // Track last time any hangout was created/updated/deleted
    private String inviteCode;  // Unique code for joining via shareable link
    
    // Default constructor for DynamoDB
    public Group() {
        super();
        setItemType("GROUP");
    }

    /**
     * Create a new group with generated UUID.
     */
    public Group(String groupName, boolean isPublic) {
        super();
        setItemType("GROUP");
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

    public String getMainImagePath() {
        return mainImagePath;
    }

    public void setMainImagePath(String mainImagePath) {
        this.mainImagePath = mainImagePath;
        touch(); // Update timestamp
    }

    public String getBackgroundImagePath() {
        return backgroundImagePath;
    }

    public void setBackgroundImagePath(String backgroundImagePath) {
        this.backgroundImagePath = backgroundImagePath;
        touch(); // Update timestamp
    }

    @DynamoDbConvertedBy(InstantAsLongAttributeConverter.class)
    public Instant getLastHangoutModified() {
        return lastHangoutModified;
    }

    public void setLastHangoutModified(Instant lastHangoutModified) {
        this.lastHangoutModified = lastHangoutModified;
        touch(); // Update timestamp
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
        touch(); // Update timestamp
    }
}