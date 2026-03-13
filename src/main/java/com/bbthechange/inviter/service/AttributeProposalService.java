package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.AttributeProposalDTO;
import com.bbthechange.inviter.model.AttributeProposalType;

import java.util.List;

/**
 * Business logic for the attribute proposal / silence=consent flow.
 *
 * When a non-creator updates a hangout's location or description, a proposal is
 * created instead of applying the change directly. After 24 hours with no
 * alternatives, the proposal is auto-adopted (silence=consent).
 */
public interface AttributeProposalService {

    /**
     * Create a proposal for a hangout attribute change by a non-creator.
     * Any existing PENDING proposal for the same hangout + attribute type is superseded.
     * Notifications are sent to all group members.
     *
     * @param hangoutId      The hangout being updated
     * @param groupId        The primary group of the hangout (for membership lookup)
     * @param proposedBy     User ID of the proposer
     * @param attributeType  LOCATION or DESCRIPTION
     * @param proposedValue  Serialized proposed value
     * @return The created proposal DTO
     */
    AttributeProposalDTO createProposal(String hangoutId, String groupId, String proposedBy,
                                         AttributeProposalType attributeType, String proposedValue);

    /**
     * List all active (PENDING) proposals for a hangout.
     * Caller must be a member of the group.
     *
     * @param hangoutId        The hangout ID
     * @param requestingUserId User performing the request
     */
    List<AttributeProposalDTO> listProposals(String hangoutId, String requestingUserId);

    /**
     * Add an alternative value to an existing PENDING proposal.
     * This puts the proposal into lightweight poll mode.
     *
     * @param hangoutId        The hangout ID
     * @param proposalId       The proposal ID
     * @param alternativeValue The alternative serialized value
     * @param requestingUserId User submitting the alternative
     * @return Updated proposal DTO
     */
    AttributeProposalDTO addAlternative(String hangoutId, String proposalId,
                                         String alternativeValue, String requestingUserId);

    /**
     * Record a vote on a proposal or one of its alternatives.
     * Index 0 = original proposed value; index 1..N = alternatives.
     *
     * @param hangoutId        The hangout ID
     * @param proposalId       The proposal ID
     * @param optionIndex      0-based index; 0 = proposedValue, else alternatives
     * @param requestingUserId User casting the vote
     * @return Updated proposal DTO
     */
    AttributeProposalDTO vote(String hangoutId, String proposalId,
                               int optionIndex, String requestingUserId);

    /**
     * Auto-adopt all PENDING proposals that are past their 24h expiry and have no alternatives.
     * Called by the scheduled task.
     */
    void autoAdoptExpiredProposals();
}
