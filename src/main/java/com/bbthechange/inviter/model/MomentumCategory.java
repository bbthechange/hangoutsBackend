package com.bbthechange.inviter.model;

/**
 * Represents the momentum state of a hangout.
 * States progress from BUILDING -> GAINING_MOMENTUM -> CONFIRMED.
 * Once CONFIRMED, a hangout is never demoted.
 */
public enum MomentumCategory {
    BUILDING,
    GAINING_MOMENTUM,
    CONFIRMED
}
