package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InviterKeyFactory;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

import java.util.List;

/**
 * DynamoDB entity for pending batched idea addition notifications.
 *
 * Key Pattern: PK = GROUP#{groupId}, SK = IDEA_NOTIFICATION_BATCH#{listId}#{adderId}
 *
 * One batch per list per user. Different users adding to the same list get separate batches.
 * TTL-based cleanup ensures orphaned records are removed.
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@DynamoDbBean
public class IdeaNotificationBatch extends BaseItem {

    private String groupId;
    private String listId;
    private String adderId;
    private List<String> ideaNames;
    private Long windowStart;
    private Long fireAt;
    private Long capAt;
    private String scheduleName;
    private String listName;
    private Long expiryDate;

    public IdeaNotificationBatch(String groupId, String listId, String adderId) {
        super();
        setItemType("IDEA_NOTIFICATION_BATCH");
        this.groupId = groupId;
        this.listId = listId;
        this.adderId = adderId;
        setPk(InviterKeyFactory.getGroupPk(groupId));
        setSk("IDEA_NOTIFICATION_BATCH#" + listId + "#" + adderId);
    }
}
