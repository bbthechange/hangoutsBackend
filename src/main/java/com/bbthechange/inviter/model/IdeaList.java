package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InviterKeyFactory;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

import java.util.UUID;

/**
 * IdeaList entity for the InviterTable.
 * Represents an idea list canonical record within a group.
 * 
 * Key Pattern: PK = GROUP#{GroupID}, SK = IDEALIST#{ListID}
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@DynamoDbBean
public class IdeaList extends BaseItem {
    
    private String listId;
    private String groupId;
    private String name;
    private IdeaListCategory category;
    private String note;
    private String createdBy;

    /**
     * Create a new idea list with generated UUID.
     */
    public IdeaList(String groupId, String name, IdeaListCategory category, String note, String createdBy) {
        super();
        setItemType("IDEALIST");
        this.listId = UUID.randomUUID().toString();
        this.groupId = groupId;
        this.name = name;
        this.category = category;
        this.note = note;
        this.createdBy = createdBy;
        
        // Set keys using InviterKeyFactory
        setPk(InviterKeyFactory.getGroupPk(this.groupId));
        setSk(InviterKeyFactory.getIdeaListSk(this.listId));
    }
}