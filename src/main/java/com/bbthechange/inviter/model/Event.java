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
    private LocalDateTime startTime; // TODO change to UTC
    private LocalDateTime endTime;
    private Address location;
    private EventVisibility visibility;
    private String mainImagePath;
    private Long version;
    
    public Event(String name, String description, LocalDateTime startTime, LocalDateTime endTime, 
                 Address location, EventVisibility visibility, String mainImagePath) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.description = description;
        this.startTime = startTime;
        this.endTime = endTime;
        this.location = location;
        this.visibility = visibility;
        this.mainImagePath = mainImagePath;
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