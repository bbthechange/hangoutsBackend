package com.bbthechange.inviter.dto.watchparty;

/**
 * Result of the {@code POST /internal/watch-party/{seriesId}/reformat-titles} backfill.
 * Returned to the curator running the admin endpoint after a ShowFlavor has been written.
 */
public class ReformatTitlesResult {

    private final String seriesId;
    private final int hangoutsScanned;
    private final int hangoutsUpdated;
    private final int hangoutsSkipped;

    public ReformatTitlesResult(String seriesId, int hangoutsScanned, int hangoutsUpdated, int hangoutsSkipped) {
        this.seriesId = seriesId;
        this.hangoutsScanned = hangoutsScanned;
        this.hangoutsUpdated = hangoutsUpdated;
        this.hangoutsSkipped = hangoutsSkipped;
    }

    public String getSeriesId() {
        return seriesId;
    }

    public int getHangoutsScanned() {
        return hangoutsScanned;
    }

    public int getHangoutsUpdated() {
        return hangoutsUpdated;
    }

    public int getHangoutsSkipped() {
        return hangoutsSkipped;
    }
}
