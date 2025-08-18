package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InviterKeyFactory;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * IdeaList entity for the InviterTable.
 * Represents an idea list canonical record within a group.
 * 
 * Key Pattern: PK = GROUP#{GroupID}, SK = IDEALIST#{ListID}
 */
@ToString
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@DynamoDbBean
public class IdeaList extends BaseItem {

    @Getter @Setter
    private String listId;
    @Getter @Setter
    private String groupId;
    @Getter @Setter
    private String name;
    @Getter @Setter
    private IdeaListCategory category;
    @Getter @Setter
    private String note;
    @Getter @Setter
    private String createdBy;
    
    // Transient field - not stored in DynamoDB but populated by repository
    private List<IdeaListMember> members = new ArrayList<>();
    
    @DynamoDbIgnore
    public List<IdeaListMember> getMembers() {
        return members;
    }
    
    public void setMembers(List<IdeaListMember> members) {
        this.members = members;
    }

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