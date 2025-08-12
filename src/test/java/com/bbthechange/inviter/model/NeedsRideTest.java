package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InviterKeyFactory;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the NeedsRide model class.
 */
class NeedsRideTest {
    
    @Test
    void defaultConstructor_SetsItemTypeCorrectly() {
        // When
        NeedsRide needsRide = new NeedsRide();
        
        // Then
        assertThat(needsRide.getItemType()).isEqualTo("NEEDS_RIDE");
    }
    
    @Test
    void parameterizedConstructor_SetsAllFieldsCorrectly() {
        // Given
        String eventId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String notes = "Need a ride from downtown";
        
        // When
        NeedsRide needsRide = new NeedsRide(eventId, userId, notes);
        
        // Then
        assertThat(needsRide.getEventId()).isEqualTo(eventId);
        assertThat(needsRide.getUserId()).isEqualTo(userId);
        assertThat(needsRide.getNotes()).isEqualTo(notes);
        assertThat(needsRide.getItemType()).isEqualTo("NEEDS_RIDE");
    }
    
    @Test
    void parameterizedConstructor_SetsKeysCorrectly() {
        // Given
        String eventId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String notes = "Need a ride from downtown";
        
        // When
        NeedsRide needsRide = new NeedsRide(eventId, userId, notes);
        
        // Then
        assertThat(needsRide.getPk()).isEqualTo(InviterKeyFactory.getEventPk(eventId));
        assertThat(needsRide.getSk()).isEqualTo(InviterKeyFactory.getNeedsRideSk(userId));
    }
    
    @Test
    void parameterizedConstructor_HandlesNullNotes() {
        // Given
        String eventId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        
        // When
        NeedsRide needsRide = new NeedsRide(eventId, userId, null);
        
        // Then
        assertThat(needsRide.getEventId()).isEqualTo(eventId);
        assertThat(needsRide.getUserId()).isEqualTo(userId);
        assertThat(needsRide.getNotes()).isNull();
        assertThat(needsRide.getItemType()).isEqualTo("NEEDS_RIDE");
    }
    
    @Test
    void parameterizedConstructor_HandlesEmptyNotes() {
        // Given
        String eventId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String emptyNotes = "";
        
        // When
        NeedsRide needsRide = new NeedsRide(eventId, userId, emptyNotes);
        
        // Then
        assertThat(needsRide.getEventId()).isEqualTo(eventId);
        assertThat(needsRide.getUserId()).isEqualTo(userId);
        assertThat(needsRide.getNotes()).isEqualTo(emptyNotes);
        assertThat(needsRide.getItemType()).isEqualTo("NEEDS_RIDE");
    }
    
    @Test
    void setEventId_UpdatesEventIdCorrectly() {
        // Given
        NeedsRide needsRide = new NeedsRide();
        String eventId = UUID.randomUUID().toString();
        
        // When
        needsRide.setEventId(eventId);
        
        // Then
        assertThat(needsRide.getEventId()).isEqualTo(eventId);
    }
    
    @Test
    void setUserId_UpdatesUserIdCorrectly() {
        // Given
        NeedsRide needsRide = new NeedsRide();
        String userId = UUID.randomUUID().toString();
        
        // When
        needsRide.setUserId(userId);
        
        // Then
        assertThat(needsRide.getUserId()).isEqualTo(userId);
    }
    
    @Test
    void setNotes_UpdatesNotesCorrectly() {
        // Given
        NeedsRide needsRide = new NeedsRide();
        String notes = "Need a ride from the airport";
        
        // When
        needsRide.setNotes(notes);
        
        // Then
        assertThat(needsRide.getNotes()).isEqualTo(notes);
    }
    
    @Test
    void setNotes_HandlesNullValue() {
        // Given
        NeedsRide needsRide = new NeedsRide();
        needsRide.setNotes("Initial notes");
        
        // When
        needsRide.setNotes(null);
        
        // Then
        assertThat(needsRide.getNotes()).isNull();
    }
    
    @Test
    void setNotes_HandlesEmptyString() {
        // Given
        NeedsRide needsRide = new NeedsRide();
        needsRide.setNotes("Initial notes");
        
        // When
        needsRide.setNotes("");
        
        // Then
        assertThat(needsRide.getNotes()).isEqualTo("");
    }
    
    @Test
    void touch_UpdatesTimestampWhenInherited() throws InterruptedException {
        // Given
        String eventId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        NeedsRide needsRide = new NeedsRide(eventId, userId, "Need a ride");
        Thread.sleep(10); // Ensure time passes
        
        // When
        needsRide.touch();
        
        // Then
        assertThat(needsRide.getUpdatedAt()).isAfter(needsRide.getCreatedAt());
    }
    
    @Test
    void baseItemMethods_WorkCorrectly() {
        // Given
        String eventId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        NeedsRide needsRide = new NeedsRide(eventId, userId, "Need a ride");
        
        // Then - verify BaseItem functionality is available
        assertThat(needsRide.getCreatedAt()).isNotNull();
        assertThat(needsRide.getUpdatedAt()).isNotNull();
        assertThat(needsRide.getPk()).isNotNull();
        assertThat(needsRide.getSk()).isNotNull();
        assertThat(needsRide.getItemType()).isEqualTo("NEEDS_RIDE");
    }
}