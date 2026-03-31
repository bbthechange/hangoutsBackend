package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.model.IdeaNotificationBatch;

import java.util.Optional;

/**
 * Repository for idea notification batch records in the InviterTable.
 */
public interface IdeaNotificationBatchRepository {

    Optional<IdeaNotificationBatch> findBatch(String groupId, String listId, String adderId);

    void save(IdeaNotificationBatch batch);

    void delete(String groupId, String listId, String adderId);
}
