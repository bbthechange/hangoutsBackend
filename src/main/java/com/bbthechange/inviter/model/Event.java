package com.bbthechange.inviter.model;

import com.bbthechange.inviter.dto.Address;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class Event {
    private UUID id;
    private String name;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Address location;
    private EventVisibility visibility;
    private String mainImagePath;
    private List<UUID> hosts; // TODO: Remove after migration - hosts are now managed via Invite.type=HOST
    private Long version;
    
    public Event(String name, String description, LocalDateTime startTime, LocalDateTime endTime, 
                 Address location, EventVisibility visibility, String mainImagePath, List<UUID> hosts) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
        this.location = location;
        this.visibility = visibility;
        this.mainImagePath = mainImagePath;
        this.hosts = hosts;
        this.version = 1L;
    }
    
    @DynamoDbPartitionKey
    public UUID getId() {
        return id;
    }
    
    public Long getVersion() {
        return version;
    }
}