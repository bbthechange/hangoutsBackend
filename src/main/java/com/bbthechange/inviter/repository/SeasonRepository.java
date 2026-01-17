package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.model.Season;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Repository interface for Season management operations in the InviterTable.
 * Provides operations for managing TV seasons for Watch Party features.
 */
public interface SeasonRepository {

    /**
     * Save a Season to the database.
     * Performs upsert operation - creates new or updates existing season.
     *
     * @param season The season to save
     * @return The saved season
     */
    Season save(Season season);

    /**
     * Find a Season by its show ID and season number.
     *
     * @param showId The TVMaze show ID
     * @param seasonNumber The season number (1-based)
     * @return Optional containing the season if found, empty otherwise
     */
    Optional<Season> findByShowIdAndSeasonNumber(Integer showId, Integer seasonNumber);

    /**
     * Find all Seasons for a specific show.
     * Uses the ShowIdIndex GSI for efficient lookup.
     *
     * @param showId The TVMaze show ID
     * @return List of Seasons for the show, ordered by season number
     */
    List<Season> findByShowId(Integer showId);

    /**
     * Delete a Season by its show ID and season number.
     * This is an idempotent operation - succeeds even if season doesn't exist.
     *
     * @param showId The TVMaze show ID
     * @param seasonNumber The season number
     */
    void delete(Integer showId, Integer seasonNumber);

    /**
     * Update the lastCheckedTimestamp for a Season.
     * This is used to track when episodes were last fetched from TVMaze.
     *
     * @param showId The TVMaze show ID
     * @param seasonNumber The season number
     * @param timestamp The new timestamp (epoch millis)
     */
    void updateLastCheckedTimestamp(Integer showId, Integer seasonNumber, Long timestamp);

    /**
     * Get all distinct show IDs that have at least one Season record.
     * Used for polling TVMaze for updates to tracked shows.
     *
     * @return Set of TVMaze show IDs we're tracking
     */
    Set<Integer> findAllDistinctShowIds();
}
