package com.bbthechange.inviter.model;

/**
 * Enumeration of action-oriented nudge types shown on hangouts with sufficient interest.
 * Nudges are computed server-side and never stored in the database.
 */
public enum NudgeType {
    /** Restaurant/food-type hangout has enough traction — time to make a reservation. */
    MAKE_RESERVATION,

    /** Event/entertainment hangout has enough interest — consider buying tickets before they sell out. */
    CONSIDER_TICKETS,

    /** Hangout has interested people but no time set — someone should suggest a time. */
    SUGGEST_TIME,

    /** Hangout has interested people but no location set — someone should add a location. */
    ADD_LOCATION
}
