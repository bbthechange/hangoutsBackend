package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.watchparty.sqs.EpisodeData;
import com.bbthechange.inviter.dto.watchparty.sqs.NewEpisodeMessage;
import com.bbthechange.inviter.dto.watchparty.sqs.RemoveEpisodeMessage;
import com.bbthechange.inviter.dto.watchparty.sqs.UpdateTitleMessage;

/**
 * Service for background processing of Watch Party episode events.
 * Handles creation, updates, and removal of hangouts based on TVMaze data changes.
 */
public interface WatchPartyBackgroundService {

    /**
     * Process a new episode notification.
     * Creates hangouts for all groups watching the season.
     *
     * @param message The new episode message containing season key and episode data
     */
    void processNewEpisode(NewEpisodeMessage message);

    /**
     * Process a title update notification.
     * Updates hangout titles where isGeneratedTitle=true and titleNotificationSent=false.
     * Only updates future hangouts.
     *
     * @param message The update title message containing externalId and new title
     */
    void processUpdateTitle(UpdateTitleMessage message);

    /**
     * Process an episode removal notification.
     * Deletes associated hangouts and notifies users.
     *
     * @param message The remove episode message containing the externalId
     */
    void processRemoveEpisode(RemoveEpisodeMessage message);
}
