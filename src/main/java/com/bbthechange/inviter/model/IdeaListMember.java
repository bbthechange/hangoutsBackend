package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InviterKeyFactory;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

import java.time.Instant;
import java.util.UUID;

/**
 * IdeaListMember entity for the InviterTable.
 * Represents an individual idea within an idea list.
 * 
 * Key Pattern: PK = GROUP#{GroupID}, SK = IDEALIST#{ListID}#IDEA#{IdeaID}
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@DynamoDbBean
public class IdeaListMember extends BaseItem {
    
    private String ideaId;
    private String listId;
    private String groupId;
    private String name;
    private String url;
    private String note;
    private String addedBy;
    private Instant addedTime;

    /**
     * Create a new idea list member with generated UUID.
     */
    public IdeaListMember(String groupId, String listId, String name, String url, String note, String addedBy) {
        super();
        setItemType("IDEA");
        this.ideaId = UUID.randomUUID().toString();
        this.listId = listId;
        this.groupId = groupId;
        this.name = name;
        this.url = url;
        this.note = note;
        this.addedBy = addedBy;
        this.addedTime = Instant.now();
        
        // Set keys using InviterKeyFactory
        setPk(InviterKeyFactory.getGroupPk(this.groupId));
        setSk(InviterKeyFactory.getIdeaListMemberSk(this.listId, this.ideaId));
    }
}