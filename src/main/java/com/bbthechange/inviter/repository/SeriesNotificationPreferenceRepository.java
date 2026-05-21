package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.model.SeriesNotificationPreference;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * Per-user, per-series notification preferences.
 *
 * The repository writes single map keys atomically — there is no full
 * save(pref) method, because concurrent writes to other keys in the map
 * should not clobber each other.
 */
public interface SeriesNotificationPreferenceRepository {

    Optional<SeriesNotificationPreference> find(String userId, String seriesId);

    /**
     * Bulk-lookup for the recipient resolver. Returns the subset of
     * candidateUserIds whose preference row has
     * mutedNudgeTypes[nudgeType] == true for this series.
     */
    Set<String> findMutedUsersForSeries(
        String seriesId,
        String nudgeType,
        Collection<String> candidateUserIds);

    /**
     * Set or clear a single nudge-type mute for one user/series.
     * Upserts the row if absent; removes the row when the map becomes empty.
     */
    void setMuted(String userId, String seriesId, String nudgeType, boolean muted);

    void delete(String userId, String seriesId);
}
