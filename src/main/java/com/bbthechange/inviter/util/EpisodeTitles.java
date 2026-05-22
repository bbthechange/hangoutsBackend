package com.bbthechange.inviter.util;

/**
 * Canonical helpers for inspecting TV episode titles returned by TVMaze.
 *
 * <p>TVMaze emits the literal string {@code "TBA"} for episodes whose title
 * has not been announced yet (see {@code CreateWatchPartyEpisodeRequest}).
 * Any additional sentinels must be added here with a comment citing the
 * upstream evidence — we do not speculate.</p>
 */
public final class EpisodeTitles {

    private EpisodeTitles() {}

    /**
     * Returns true iff the title matches TVMaze's "TBA" sentinel
     * (case-insensitive, surrounding whitespace ignored).
     */
    public static boolean isTba(String title) {
        if (title == null) {
            return false;
        }
        return title.trim().equalsIgnoreCase("TBA");
    }
}
