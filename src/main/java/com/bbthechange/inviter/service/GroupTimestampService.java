package com.bbthechange.inviter.service;

import java.util.List;

/**
 * Service for managing group timestamp updates.
 * Updates Group.lastHangoutModified when hangout data changes.
 */
public interface GroupTimestampService {

    /**
     * Update lastHangoutModified timestamp for all specified groups.
     * Called whenever hangout data changes (hangout itself or nested data like polls/carpools/interest levels).
     *
     * @param groupIds List of group IDs to update (can be null or empty)
     */
    void updateGroupTimestamps(List<String> groupIds);
}
