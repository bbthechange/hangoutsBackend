package com.bbthechange.inviter.dto.watchparty;

/**
 * Result object for TVMaze polling operations.
 * Contains statistics about the polling run.
 */
public class PollResult {

    private int totalTrackedShows;
    private int updatedShowsFound;
    private int messagesEmitted;
    private long durationMs;

    // Default constructor
    public PollResult() {
    }

    private PollResult(int totalTrackedShows, int updatedShowsFound, int messagesEmitted, long durationMs) {
        this.totalTrackedShows = totalTrackedShows;
        this.updatedShowsFound = updatedShowsFound;
        this.messagesEmitted = messagesEmitted;
        this.durationMs = durationMs;
    }

    /**
     * Create a PollResult for a successful poll.
     */
    public static PollResult success(int totalTrackedShows, int updatedShowsFound, int messagesEmitted, long durationMs) {
        return new PollResult(totalTrackedShows, updatedShowsFound, messagesEmitted, durationMs);
    }

    /**
     * Create a PollResult for when there are no tracked shows (skip scenario).
     */
    public static PollResult noTrackedShows(long durationMs) {
        return new PollResult(0, 0, 0, durationMs);
    }

    public int getTotalTrackedShows() {
        return totalTrackedShows;
    }

    public void setTotalTrackedShows(int totalTrackedShows) {
        this.totalTrackedShows = totalTrackedShows;
    }

    public int getUpdatedShowsFound() {
        return updatedShowsFound;
    }

    public void setUpdatedShowsFound(int updatedShowsFound) {
        this.updatedShowsFound = updatedShowsFound;
    }

    public int getMessagesEmitted() {
        return messagesEmitted;
    }

    public void setMessagesEmitted(int messagesEmitted) {
        this.messagesEmitted = messagesEmitted;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    @Override
    public String toString() {
        return "PollResult{" +
                "totalTrackedShows=" + totalTrackedShows +
                ", updatedShowsFound=" + updatedShowsFound +
                ", messagesEmitted=" + messagesEmitted +
                ", durationMs=" + durationMs +
                '}';
    }
}
