package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.watchparty.PollResult;

/**
 * Service for polling TVMaze for show updates.
 * Compares tracked shows with TVMaze updates and emits SHOW_UPDATED messages
 * for shows that have been modified.
 */
public interface TvMazePollingService {

    /**
     * Poll TVMaze for updates to tracked shows.
     *
     * Process:
     * 1. Get all distinct show IDs we're tracking from SeasonRepository
     * 2. Fetch recent updates from TVMaze API
     * 3. Find intersection (tracked shows that were updated)
     * 4. Emit SHOW_UPDATED message for each via SQS
     *
     * @return PollResult containing statistics about the poll
     */
    PollResult pollForUpdates();
}
