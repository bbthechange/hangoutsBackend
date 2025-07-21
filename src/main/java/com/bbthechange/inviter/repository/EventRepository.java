package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.model.Event;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.Optional;
import java.util.UUID;

@Repository
public class EventRepository {
    
    private final DynamoDbTable<Event> eventTable;
    
    @Autowired
    public EventRepository(DynamoDbEnhancedClient dynamoDbEnhancedClient) {
        this.eventTable = dynamoDbEnhancedClient.table("Events", TableSchema.fromBean(Event.class));
    }
    
    public Event save(Event event) {
        eventTable.putItem(event);
        return event;
    }
    
    public Optional<Event> findById(UUID id) {
        Event event = eventTable.getItem(Key.builder().partitionValue(id.toString()).build());
        return Optional.ofNullable(event);
    }
    
    public void delete(Event event) {
        eventTable.deleteItem(event);
    }
}