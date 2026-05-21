package com.bbthechange.inviter.util;

/**
 * Constants for notification "nudge" types. Used as keys in
 * SeriesNotificationPreference.mutedNudgeTypes and as the discriminator in
 * the mute endpoint request body.
 */
public final class NudgeTypes {
    public static final String HOST_NUDGE = "HOST_NUDGE";
    // Future: LOCATION_NUDGE, TIME_NUDGE, etc.
    private NudgeTypes() {}
}
