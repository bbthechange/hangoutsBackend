package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.AttributeProposal;
import com.bbthechange.inviter.model.AttributeProposalStatus;
import com.bbthechange.inviter.model.AttributeProposalType;

import java.util.List;

/**
 * Response DTO for an attribute proposal.
 */
public class AttributeProposalDTO {

    private String proposalId;
    private String hangoutId;
    private String groupId;
    private String proposedBy;
    private AttributeProposalType attributeType;
    private String proposedValue;
    private AttributeProposalStatus status;
    private List<String> alternatives;
    private List<Integer> voteCounts;
    private Long createdAtMillis;
    private Long expiresAt;

    public AttributeProposalDTO() {}

    public static AttributeProposalDTO fromEntity(AttributeProposal proposal) {
        AttributeProposalDTO dto = new AttributeProposalDTO();
        dto.proposalId = proposal.getProposalId();
        dto.hangoutId = proposal.getHangoutId();
        dto.groupId = proposal.getGroupId();
        dto.proposedBy = proposal.getProposedBy();
        dto.attributeType = proposal.getAttributeType();
        dto.proposedValue = proposal.getProposedValue();
        dto.status = proposal.getStatus();
        dto.alternatives = proposal.getAlternatives();
        dto.voteCounts = proposal.getVoteCounts();
        dto.createdAtMillis = proposal.getCreatedAtMillis();
        dto.expiresAt = proposal.getExpiresAt();
        return dto;
    }

    public String getProposalId() { return proposalId; }
    public void setProposalId(String proposalId) { this.proposalId = proposalId; }

    public String getHangoutId() { return hangoutId; }
    public void setHangoutId(String hangoutId) { this.hangoutId = hangoutId; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getProposedBy() { return proposedBy; }
    public void setProposedBy(String proposedBy) { this.proposedBy = proposedBy; }

    public AttributeProposalType getAttributeType() { return attributeType; }
    public void setAttributeType(AttributeProposalType attributeType) { this.attributeType = attributeType; }

    public String getProposedValue() { return proposedValue; }
    public void setProposedValue(String proposedValue) { this.proposedValue = proposedValue; }

    public AttributeProposalStatus getStatus() { return status; }
    public void setStatus(AttributeProposalStatus status) { this.status = status; }

    public List<String> getAlternatives() { return alternatives; }
    public void setAlternatives(List<String> alternatives) { this.alternatives = alternatives; }

    public List<Integer> getVoteCounts() { return voteCounts; }
    public void setVoteCounts(List<Integer> voteCounts) { this.voteCounts = voteCounts; }

    public Long getCreatedAtMillis() { return createdAtMillis; }
    public void setCreatedAtMillis(Long createdAtMillis) { this.createdAtMillis = createdAtMillis; }

    public Long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Long expiresAt) { this.expiresAt = expiresAt; }
}
