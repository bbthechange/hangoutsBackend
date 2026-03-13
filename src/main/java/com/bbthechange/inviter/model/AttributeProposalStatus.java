package com.bbthechange.inviter.model;

/**
 * Status of an attribute proposal in the silence=consent flow.
 */
public enum AttributeProposalStatus {
    /** Proposal is active, waiting for alternatives or expiry. */
    PENDING,
    /** Proposal was applied to the hangout (auto-adopted after 24h or voted in). */
    ADOPTED,
    /** Proposal was explicitly rejected. */
    REJECTED,
    /** A newer proposal for the same attribute was submitted, superseding this one. */
    SUPERSEDED
}
