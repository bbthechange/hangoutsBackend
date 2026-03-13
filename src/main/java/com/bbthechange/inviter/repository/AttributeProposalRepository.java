package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.model.AttributeProposal;
import com.bbthechange.inviter.model.AttributeProposalStatus;
import com.bbthechange.inviter.model.AttributeProposalType;

import java.util.List;
import java.util.Optional;

/**
 * Repository for AttributeProposal records stored in the hangout's item collection.
 * Records are stored with PK=EVENT#{hangoutId}, SK=PROPOSAL#{proposalId}.
 */
public interface AttributeProposalRepository {

    /**
     * Save (create or update) an attribute proposal.
     */
    AttributeProposal save(AttributeProposal proposal);

    /**
     * Find a specific proposal by hangoutId and proposalId.
     */
    Optional<AttributeProposal> findById(String hangoutId, String proposalId);

    /**
     * Find all proposals for a hangout.
     */
    List<AttributeProposal> findByHangoutId(String hangoutId);

    /**
     * Find all PENDING proposals for a hangout of a specific attribute type.
     * Used when checking if a proposal should supersede an existing one.
     */
    List<AttributeProposal> findPendingByHangoutIdAndType(String hangoutId, AttributeProposalType attributeType);

    /**
     * Find all PENDING proposals that have passed their expiresAt timestamp.
     * Used by the scheduled auto-adoption task.
     *
     * @param nowMillis current epoch millis
     */
    List<AttributeProposal> findExpiredPendingProposals(long nowMillis);

    /**
     * Delete a proposal (for cleanup or supersede replacement).
     */
    void delete(String hangoutId, String proposalId);
}
