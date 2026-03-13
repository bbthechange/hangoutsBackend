package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InviterKeyFactory;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An attribute proposal in the silence=consent flow.
 *
 * Stored in the hangout's partition (single-table design):
 *   PK = EVENT#{hangoutId}
 *   SK = PROPOSAL#{proposalId}
 *
 * When a non-creator updates a hangout's location or description, a proposal is
 * created instead of applying the change immediately. After 24 hours with no
 * alternatives, the proposal is auto-adopted (silence=consent). If alternatives
 * are submitted during the window, a lightweight vote decides the outcome.
 */
@DynamoDbBean
public class AttributeProposal extends BaseItem {

    public static final String PROPOSAL_PREFIX = "PROPOSAL";

    private String proposalId;
    private String hangoutId;
    private String groupId;
    private String proposedBy;           // userId of the proposer
    private AttributeProposalType attributeType;  // LOCATION or DESCRIPTION
    private String proposedValue;        // serialized proposed value (JSON for location, plain text for description)
    private AttributeProposalStatus status;
    /** Other member-submitted alternatives: list of serialized values. */
    private List<String> alternatives;
    /** Vote counts parallel to alternatives list (index 0 = votes for proposedValue). */
    private List<Integer> voteCounts;
    private Long createdAtMillis;        // epoch millis
    private Long expiresAt;              // epoch millis (createdAtMillis + 24h)

    public AttributeProposal() {
        super();
        setItemType(PROPOSAL_PREFIX);
        this.alternatives = new ArrayList<>();
        this.voteCounts = new ArrayList<>();
    }

    public AttributeProposal(String hangoutId, String groupId, String proposedBy,
                              AttributeProposalType attributeType, String proposedValue) {
        super();
        setItemType(PROPOSAL_PREFIX);
        this.proposalId = UUID.randomUUID().toString();
        this.hangoutId = hangoutId;
        this.groupId = groupId;
        this.proposedBy = proposedBy;
        this.attributeType = attributeType;
        this.proposedValue = proposedValue;
        this.status = AttributeProposalStatus.PENDING;
        this.alternatives = new ArrayList<>();
        this.voteCounts = new ArrayList<>();
        this.createdAtMillis = System.currentTimeMillis();
        this.expiresAt = this.createdAtMillis + (24L * 60 * 60 * 1000); // 24 hours

        setPk(InviterKeyFactory.getEventPk(hangoutId));
        setSk(PROPOSAL_PREFIX + "#" + this.proposalId);
    }

    public String getProposalId() {
        return proposalId;
    }

    public void setProposalId(String proposalId) {
        this.proposalId = proposalId;
    }

    public String getHangoutId() {
        return hangoutId;
    }

    public void setHangoutId(String hangoutId) {
        this.hangoutId = hangoutId;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getProposedBy() {
        return proposedBy;
    }

    public void setProposedBy(String proposedBy) {
        this.proposedBy = proposedBy;
    }

    public AttributeProposalType getAttributeType() {
        return attributeType;
    }

    public void setAttributeType(AttributeProposalType attributeType) {
        this.attributeType = attributeType;
    }

    public String getProposedValue() {
        return proposedValue;
    }

    public void setProposedValue(String proposedValue) {
        this.proposedValue = proposedValue;
    }

    public AttributeProposalStatus getStatus() {
        return status;
    }

    public void setStatus(AttributeProposalStatus status) {
        this.status = status;
    }

    public List<String> getAlternatives() {
        return alternatives;
    }

    public void setAlternatives(List<String> alternatives) {
        this.alternatives = alternatives != null ? alternatives : new ArrayList<>();
    }

    public List<Integer> getVoteCounts() {
        return voteCounts;
    }

    public void setVoteCounts(List<Integer> voteCounts) {
        this.voteCounts = voteCounts != null ? voteCounts : new ArrayList<>();
    }

    public Long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public void setCreatedAtMillis(Long createdAtMillis) {
        this.createdAtMillis = createdAtMillis;
    }

    public Long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }

    /** Returns true if this proposal is past its 24h window. */
    public boolean isExpired() {
        return expiresAt != null && System.currentTimeMillis() >= expiresAt;
    }

    /** Returns true if any alternatives have been added (lightweight poll mode). */
    public boolean hasAlternatives() {
        return alternatives != null && !alternatives.isEmpty();
    }
}
