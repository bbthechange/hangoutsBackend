package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InviterKeyFactory;
import com.bbthechange.inviter.util.InstantAsLongAttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Group invite code canonical record.
 *
 * DynamoDB Keys:
 *   PK: INVITE_CODE#{inviteCodeId}
 *   SK: METADATA
 *
 * GSI (InviteCodeIndex) for lookup by code string:
 *   gsi3pk: CODE#{code}
 *   gsi3sk: METADATA
 *
 * GSI (UserGroupIndex) for listing codes by group:
 *   gsi1pk: GROUP#{groupId}
 *   gsi1sk: CREATED#{createdAt}
 */
@DynamoDbBean
public class InviteCode extends BaseItem {

    private String inviteCodeId;        // UUID
    private String code;                // 8-char alphanumeric (e.g., "abc123xy")
    private String groupId;             // Which group this belongs to
    private String groupName;           // Denormalized for display

    // Audit fields
    private String createdBy;           // userId who generated it
    private Instant createdAt;          // When generated
    private String deactivatedBy;       // userId who disabled it (optional)
    private Instant deactivatedAt;      // When disabled (optional)

    // Usage controls
    private boolean isSingleUse;        // true = disable after first use
    private Instant expiresAt;          // Optional expiration timestamp

    // Status
    private boolean isActive;           // Can be used?
    private String deactivationReason;  // Why disabled (optional)

    // Usage tracking (list of user IDs who joined)
    private List<String> usages;

    // Default constructor for DynamoDB
    public InviteCode() {
        super();
        setItemType("InviteCode");
        this.usages = new ArrayList<>();
    }

    /**
     * Create a new invite code.
     *
     * @param groupId Group this code belongs to
     * @param code The 8-character alphanumeric code
     * @param createdBy User ID who created this code
     * @param groupName Group name (denormalized)
     */
    public InviteCode(String groupId, String code, String createdBy, String groupName) {
        this();
        this.inviteCodeId = UUID.randomUUID().toString();
        this.groupId = groupId;
        this.code = code;
        this.createdBy = createdBy;
        this.groupName = groupName;
        this.createdAt = Instant.now();
        this.isActive = true;
        this.isSingleUse = false;  // Default: unlimited use

        // Set main table keys (Canonical Record)
        setPk(InviterKeyFactory.getInviteCodePk(this.inviteCodeId));
        setSk(InviterKeyFactory.getMetadataSk());

        // Set GSI keys for code string lookup (InviteCodeIndex)
        setGsi3pk(InviterKeyFactory.getCodeLookupGsi3pk(this.code));
        setGsi3sk(InviterKeyFactory.getMetadataSk());

        // Set GSI keys for group listing (UserGroupIndex)
        setGsi1pk(InviterKeyFactory.getGroupPk(this.groupId));
        setGsi1sk(InviterKeyFactory.getCreatedSk(this.createdAt));
    }

    /**
     * Record a user joining via this code.
     * Auto-deactivates if single-use code.
     */
    public void recordUsage(String userId) {
        if (this.usages == null) {
            this.usages = new ArrayList<>();
        }

        this.usages.add(userId);

        // Auto-deactivate if single-use
        if (isSingleUse && usages.size() >= 1) {
            this.isActive = false;
            this.deactivationReason = "Single-use code exhausted";
        }

        touch(); // Update timestamp
    }

    /**
     * Manually deactivate the code.
     */
    public void deactivate(String deactivatedBy, String reason) {
        this.isActive = false;
        this.deactivatedBy = deactivatedBy;
        this.deactivatedAt = Instant.now();
        this.deactivationReason = reason;
        touch();
    }

    /**
     * Check if code is currently usable.
     * A code is usable if it's active AND not expired.
     */
    public boolean isUsable() {
        if (!isActive) return false;
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) return false;
        return true;
    }

    /**
     * Get count of users who joined via this code.
     */
    public int getUsageCount() {
        return usages != null ? usages.size() : 0;
    }

    // Getters and Setters

    public String getInviteCodeId() {
        return inviteCodeId;
    }

    public void setInviteCodeId(String inviteCodeId) {
        this.inviteCodeId = inviteCodeId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
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

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    @DynamoDbConvertedBy(InstantAsLongAttributeConverter.class)
    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getDeactivatedBy() {
        return deactivatedBy;
    }

    public void setDeactivatedBy(String deactivatedBy) {
        this.deactivatedBy = deactivatedBy;
    }

    @DynamoDbConvertedBy(InstantAsLongAttributeConverter.class)
    public Instant getDeactivatedAt() {
        return deactivatedAt;
    }

    public void setDeactivatedAt(Instant deactivatedAt) {
        this.deactivatedAt = deactivatedAt;
    }

    public boolean isSingleUse() {
        return isSingleUse;
    }

    public void setSingleUse(boolean singleUse) {
        isSingleUse = singleUse;
    }

    @DynamoDbConvertedBy(InstantAsLongAttributeConverter.class)
    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public String getDeactivationReason() {
        return deactivationReason;
    }

    public void setDeactivationReason(String deactivationReason) {
        this.deactivationReason = deactivationReason;
    }

    public List<String> getUsages() {
        return usages;
    }

    public void setUsages(List<String> usages) {
        this.usages = usages;
    }
}
