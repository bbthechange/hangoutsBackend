package com.bbthechange.inviter.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class Invite {
    private UUID id;
    private UUID eventId;
    private UUID userId;
    private InviteType type;
    private InviteResponse response;
    private Boolean eventPassed;
    
    public Invite(UUID eventId, UUID userId, InviteType type) {
        this.id = UUID.randomUUID();
        this.eventId = eventId;
        this.userId = userId;
        this.type = type;
        this.response = InviteResponse.NOT_RESPONDED;
        this.eventPassed = false;
    }
    
    // Backward compatibility constructor for tests - defaults to GUEST
    public Invite(UUID eventId, UUID userId) {
        this(eventId, userId, InviteType.GUEST);
    }
    
    @DynamoDbPartitionKey
    public UUID getId() {
        return id;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "EventIndex")
    public UUID getEventId() {
        return eventId;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "UserIndex")
    public UUID getUserId() {
        return userId;
    }
    
    public enum InviteType {
        HOST,
        GUEST
    }
    
    public enum InviteResponse {
        GOING,
        NOT_GOING,
        MAYBE,
        NOT_RESPONDED
    }
}