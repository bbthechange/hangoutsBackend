package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.model.Invite;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class InviteRepository {
    
    private final DynamoDbTable<Invite> inviteTable;
    private final DynamoDbIndex<Invite> eventIndex;
    private final DynamoDbIndex<Invite> userIndex;
    
    @Autowired
    public InviteRepository(DynamoDbEnhancedClient dynamoDbEnhancedClient) {
        this.inviteTable = dynamoDbEnhancedClient.table("Invites", TableSchema.fromBean(Invite.class));
        this.eventIndex = inviteTable.index("EventIndex");
        this.userIndex = inviteTable.index("UserIndex");
    }
    
    public Invite save(Invite invite) {
        inviteTable.putItem(invite);
        return invite;
    }
    
    public Optional<Invite> findById(UUID id) {
        Invite invite = inviteTable.getItem(Key.builder().partitionValue(id.toString()).build());
        return Optional.ofNullable(invite);
    }
    
    public List<Invite> findByEventId(UUID eventId) {
        // Temporary: scan table instead of using GSI
        return inviteTable.scan()
                .items()
                .stream()
                .filter(invite -> eventId.equals(invite.getEventId()))
                .collect(Collectors.toList());
    }
    
    public List<Invite> findByUserId(UUID userId) {
        // Temporary: scan table instead of using GSI
        return inviteTable.scan()
                .items()
                .stream()
                .filter(invite -> userId.equals(invite.getUserId()))
                .collect(Collectors.toList());
    }
    
    public void delete(Invite invite) {
        inviteTable.deleteItem(invite);
    }
}