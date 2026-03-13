package com.bbthechange.inviter.model;

/**
 * Status of a TimeSuggestion.
 */
public enum TimeSuggestionStatus {
    /** Suggestion is live and open for support. */
    ACTIVE,
    /** Suggestion was adopted and the hangout time was set to this suggestion. */
    ADOPTED,
    /** Suggestion was rejected (never auto-set; reserved for future manual rejection). */
    REJECTED
}
