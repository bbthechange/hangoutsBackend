package com.bbthechange.inviter.util;

/**
 * Constants for notification "nudge" types. Used as keys in
 * SeriesNotificationPreference.mutedNudgeTypes and as the discriminator in
 * the mute endpoint request body.
 */
public final class NudgeTypes {
    public static final String HOST_NUDGE = "HOST_NUDGE";
    // Future: LOCATION_NUDGE, TIME_NUDGE, etc.

    private static final java.util.Set<String> ALL = java.util.Set.of(HOST_NUDGE);

    private NudgeTypes() {}

    /**
     * Returns true if {@code value} is one of the registered nudge-type constants.
     * The mute endpoint uses this to reject unknown nudge-type strings with a 400.
     */
    public static boolean isValid(String value) {
        return value != null && ALL.contains(value);
    }
}
