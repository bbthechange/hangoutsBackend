package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.watchparty.sqs.*;

/**
 * Service for sending messages to Watch Party SQS queues.
 */
public interface WatchPartySqsService {

    /**
     * Send a message to the TVMaze updates queue.
     * Used to trigger show update processing.
     *
     * @param message The message to send
     */
    void sendToTvMazeUpdatesQueue(WatchPartyMessage message);

    /**
     * Send a message to the episode actions queue.
     * Used for NEW_EPISODE, UPDATE_TITLE, REMOVE_EPISODE actions.
     *
     * @param message The message to send
     */
    void sendToEpisodeActionsQueue(WatchPartyMessage message);

    /**
     * Send a new episode message for processing.
     *
     * @param seasonKey The season key (e.g., "TVMAZE#SHOW#123|SEASON#1")
     * @param episode The episode data
     */
    void sendNewEpisode(String seasonKey, EpisodeData episode);

    /**
     * Send a title update message for processing.
     *
     * @param externalId The TVMaze episode ID as string
     * @param newTitle The new episode title
     */
    void sendUpdateTitle(String externalId, String newTitle);

    /**
     * Send an episode removal message for processing.
     *
     * @param externalId The TVMaze episode ID as string
     */
    void sendRemoveEpisode(String externalId);
}
