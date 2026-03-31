package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.model.IdeaNotificationBatch;
import com.bbthechange.inviter.repository.IdeaNotificationBatchRepository;
import com.bbthechange.inviter.util.InviterKeyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.Optional;

/**
 * DynamoDB implementation for idea notification batch records.
 */
@Repository
public class IdeaNotificationBatchRepositoryImpl implements IdeaNotificationBatchRepository {

    private static final Logger logger = LoggerFactory.getLogger(IdeaNotificationBatchRepositoryImpl.class);
    private static final String TABLE_NAME = "InviterTable";

    private final DynamoDbTable<IdeaNotificationBatch> table;

    @Autowired
    public IdeaNotificationBatchRepositoryImpl(DynamoDbEnhancedClient enhancedClient) {
        this.table = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(IdeaNotificationBatch.class));
    }

    @Override
    public Optional<IdeaNotificationBatch> findBatch(String groupId, String listId, String adderId) {
        try {
            Key key = Key.builder()
                    .partitionValue(InviterKeyFactory.getGroupPk(groupId))
                    .sortValue("IDEA_NOTIFICATION_BATCH#" + listId + "#" + adderId)
                    .build();
            IdeaNotificationBatch batch = table.getItem(key);
            return Optional.ofNullable(batch);
        } catch (Exception e) {
            logger.error("Error finding idea notification batch: group={}, list={}, adder={}",
                    groupId, listId, adderId, e);
            return Optional.empty();
        }
    }

    @Override
    public void save(IdeaNotificationBatch batch) {
        try {
            table.putItem(batch);
        } catch (Exception e) {
            logger.error("Error saving idea notification batch: group={}, list={}, adder={}",
                    batch.getGroupId(), batch.getListId(), batch.getAdderId(), e);
            throw e;
        }
    }

    @Override
    public void delete(String groupId, String listId, String adderId) {
        try {
            Key key = Key.builder()
                    .partitionValue(InviterKeyFactory.getGroupPk(groupId))
                    .sortValue("IDEA_NOTIFICATION_BATCH#" + listId + "#" + adderId)
                    .build();
            table.deleteItem(key);
        } catch (Exception e) {
            logger.error("Error deleting idea notification batch: group={}, list={}, adder={}",
                    groupId, listId, adderId, e);
        }
    }
}
