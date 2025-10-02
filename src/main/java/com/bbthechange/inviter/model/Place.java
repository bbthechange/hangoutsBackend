package com.bbthechange.inviter.model;

import com.bbthechange.inviter.dto.Address;
import com.bbthechange.inviter.util.InviterKeyFactory;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

import java.util.UUID;

/**
 * Place entity for the InviterTable.
 * Represents a saved place that can be owned by either a user or a group.
 *
 * Key Patterns:
 * - User-owned: PK = USER#{userId}, SK = PLACE#{placeId}
 * - Group-owned: PK = GROUP#{groupId}, SK = PLACE#{placeId}
 *
 * GSI (UserGroupIndex) for primary place (set on read/update):
 * - gsi1pk = USER#{userId}
 * - gsi1sk = "PRIMARY_PLACE"
 */
@DynamoDbBean
public class Place extends BaseItem {

    private String placeId;
    private String nickname;
    private Address address;
    private String notes; // Optional notes about the place (e.g., access codes, parking instructions)
    private boolean isPrimary; // Only meaningful for USER-owned places
    private String status; // ACTIVE or ARCHIVED
    private String ownerType; // USER or GROUP
    private String createdBy; // userId who created it

    // Default constructor for DynamoDB
    public Place() {
        super();
        setItemType(InviterKeyFactory.PLACE_PREFIX);
        this.status = InviterKeyFactory.STATUS_ACTIVE;
        this.isPrimary = false;
    }

    /**
     * Create a new user-owned place.
     * Note: GSI keys should be set separately based on isPrimary flag.
     */
    public Place(String userId, String nickname, Address address, String notes, boolean isPrimary, String createdBy) {
        super();
        setItemType(InviterKeyFactory.PLACE_PREFIX);
        this.placeId = UUID.randomUUID().toString();
        this.nickname = nickname;
        this.address = address;
        this.notes = notes;
        this.isPrimary = isPrimary;
        this.status = InviterKeyFactory.STATUS_ACTIVE;
        this.ownerType = InviterKeyFactory.OWNER_TYPE_USER;
        this.createdBy = createdBy;

        // Set keys
        setPk(InviterKeyFactory.getUserGsi1Pk(userId));
        setSk(InviterKeyFactory.getPlaceSk(this.placeId));
    }

    /**
     * Create a new group-owned place.
     * Note: Groups don't have primary places, so no GSI keys needed.
     */
    public Place(String groupId, String createdBy, String nickname, Address address, String notes) {
        super();
        setItemType(InviterKeyFactory.PLACE_PREFIX);
        this.placeId = UUID.randomUUID().toString();
        this.nickname = nickname;
        this.address = address;
        this.notes = notes;
        this.isPrimary = false; // Groups don't have primary places
        this.status = InviterKeyFactory.STATUS_ACTIVE;
        this.ownerType = InviterKeyFactory.OWNER_TYPE_GROUP;
        this.createdBy = createdBy;

        // Set keys
        setPk(InviterKeyFactory.getGroupPk(groupId));
        setSk(InviterKeyFactory.getPlaceSk(this.placeId));
    }

    public String getPlaceId() {
        return placeId;
    }

    public void setPlaceId(String placeId) {
        this.placeId = placeId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
        touch();
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
        touch();
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
        touch();
    }

    public boolean isPrimary() {
        return isPrimary;
    }

    public void setPrimary(boolean isPrimary) {
        this.isPrimary = isPrimary;
        touch();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        touch();
    }

    public String getOwnerType() {
        return ownerType;
    }

    public void setOwnerType(String ownerType) {
        this.ownerType = ownerType;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
