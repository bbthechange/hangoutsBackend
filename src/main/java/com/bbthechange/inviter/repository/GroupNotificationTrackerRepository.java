package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.model.GroupNotificationTracker;

import java.util.Optional;

/**
 * Repository for persisting per-group notification tracking state.
 */
public interface GroupNotificationTrackerRepository {

    /**
     * Load the tracker for a group, or empty if none exists yet.
     */
    Optional<GroupNotificationTracker> findByGroupId(String groupId);

    /**
     * Persist the tracker (upsert).
     */
    void save(GroupNotificationTracker tracker);
}
