package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.NeedsRide;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the NeedsRideDTO class.
 */
class NeedsRideDTOTest {
    
    @Test
    void defaultConstructor_CreatesEmptyDTO() {
        // When
        NeedsRideDTO dto = new NeedsRideDTO();
        
        // Then
        assertThat(dto.getUserId()).isNull();
        assertThat(dto.getNotes()).isNull();
    }
    
    @Test
    void parameterizedConstructor_SetsFieldsCorrectly() {
        // Given
        String userId = UUID.randomUUID().toString();
        String notes = "Need a ride from downtown";
        
        // When
        NeedsRideDTO dto = new NeedsRideDTO(userId, notes);
        
        // Then
        assertThat(dto.getUserId()).isEqualTo(userId);
        assertThat(dto.getNotes()).isEqualTo(notes);
    }
    
    @Test
    void parameterizedConstructor_HandlesNullValues() {
        // When
        NeedsRideDTO dto = new NeedsRideDTO(null, null);
        
        // Then
        assertThat(dto.getUserId()).isNull();
        assertThat(dto.getNotes()).isNull();
    }
    
    @Test
    void parameterizedConstructor_HandlesEmptyStrings() {
        // When
        NeedsRideDTO dto = new NeedsRideDTO("", "");
        
        // Then
        assertThat(dto.getUserId()).isEqualTo("");
        assertThat(dto.getNotes()).isEqualTo("");
    }
    
    @Test
    void needsRideConstructor_SetsFieldsFromModel() {
        // Given
        String eventId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String notes = "Need a ride from the airport";
        NeedsRide needsRide = new NeedsRide(eventId, userId, notes);
        
        // When
        NeedsRideDTO dto = new NeedsRideDTO(needsRide);
        
        // Then
        assertThat(dto.getUserId()).isEqualTo(userId);
        assertThat(dto.getNotes()).isEqualTo(notes);
    }
    
    @Test
    void needsRideConstructor_HandlesNullNotes() {
        // Given
        String eventId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        NeedsRide needsRide = new NeedsRide(eventId, userId, null);
        
        // When
        NeedsRideDTO dto = new NeedsRideDTO(needsRide);
        
        // Then
        assertThat(dto.getUserId()).isEqualTo(userId);
        assertThat(dto.getNotes()).isNull();
    }
    
    @Test
    void needsRideConstructor_HandlesEmptyNotes() {
        // Given
        String eventId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String emptyNotes = "";
        NeedsRide needsRide = new NeedsRide(eventId, userId, emptyNotes);
        
        // When
        NeedsRideDTO dto = new NeedsRideDTO(needsRide);
        
        // Then
        assertThat(dto.getUserId()).isEqualTo(userId);
        assertThat(dto.getNotes()).isEqualTo(emptyNotes);
    }
    
    @Test
    void setUserId_UpdatesUserIdCorrectly() {
        // Given
        NeedsRideDTO dto = new NeedsRideDTO();
        String userId = UUID.randomUUID().toString();
        
        // When
        dto.setUserId(userId);
        
        // Then
        assertThat(dto.getUserId()).isEqualTo(userId);
    }
    
    @Test
    void setUserId_HandlesNullValue() {
        // Given
        NeedsRideDTO dto = new NeedsRideDTO("initial-user-id", "notes");
        
        // When
        dto.setUserId(null);
        
        // Then
        assertThat(dto.getUserId()).isNull();
    }
    
    @Test
    void setUserId_HandlesEmptyString() {
        // Given
        NeedsRideDTO dto = new NeedsRideDTO("initial-user-id", "notes");
        
        // When
        dto.setUserId("");
        
        // Then
        assertThat(dto.getUserId()).isEqualTo("");
    }
    
    @Test
    void setNotes_UpdatesNotesCorrectly() {
        // Given
        NeedsRideDTO dto = new NeedsRideDTO();
        String notes = "Need a ride from the train station";
        
        // When
        dto.setNotes(notes);
        
        // Then
        assertThat(dto.getNotes()).isEqualTo(notes);
    }
    
    @Test
    void setNotes_HandlesNullValue() {
        // Given
        NeedsRideDTO dto = new NeedsRideDTO("user-id", "initial notes");
        
        // When
        dto.setNotes(null);
        
        // Then
        assertThat(dto.getNotes()).isNull();
    }
    
    @Test
    void setNotes_HandlesEmptyString() {
        // Given
        NeedsRideDTO dto = new NeedsRideDTO("user-id", "initial notes");
        
        // When
        dto.setNotes("");
        
        // Then
        assertThat(dto.getNotes()).isEqualTo("");
    }
    
    @Test
    void setNotes_WithLongNotes_SetsCorrectly() {
        // Given
        NeedsRideDTO dto = new NeedsRideDTO();
        String longNotes = "I need a ride from downtown to the event venue. I can meet at the subway station at 7 PM. I'm happy to contribute to gas money and can also help with navigation. Please let me know if anyone has space!";
        
        // When
        dto.setNotes(longNotes);
        
        // Then
        assertThat(dto.getNotes()).isEqualTo(longNotes);
    }
    
    @Test
    void setNotes_WithUnicodeCharacters_HandlesCorrectly() {
        // Given
        NeedsRideDTO dto = new NeedsRideDTO();
        String unicodeNotes = "需要搭车 🚗 市内到会场";
        
        // When
        dto.setNotes(unicodeNotes);
        
        // Then
        assertThat(dto.getNotes()).isEqualTo(unicodeNotes);
    }
    
    @Test
    void multipleUpdates_UpdateFieldsCorrectly() {
        // Given
        NeedsRideDTO dto = new NeedsRideDTO();
        String userId1 = UUID.randomUUID().toString();
        String userId2 = UUID.randomUUID().toString();
        
        // When/Then - Multiple updates
        dto.setUserId(userId1);
        dto.setNotes("First note");
        assertThat(dto.getUserId()).isEqualTo(userId1);
        assertThat(dto.getNotes()).isEqualTo("First note");
        
        dto.setUserId(userId2);
        dto.setNotes("Second note");
        assertThat(dto.getUserId()).isEqualTo(userId2);
        assertThat(dto.getNotes()).isEqualTo("Second note");
        
        dto.setUserId(null);
        dto.setNotes(null);
        assertThat(dto.getUserId()).isNull();
        assertThat(dto.getNotes()).isNull();
    }
    
    @Test
    void needsRideConstructor_WithComplexModel_MapsCorrectly() {
        // Given
        String eventId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String complexNotes = "Multi-line notes:\n- Pickup location: Downtown subway\n- Time: 7:00 PM\n- Will pay gas 💰";
        NeedsRide needsRide = new NeedsRide(eventId, userId, complexNotes);
        
        // When
        NeedsRideDTO dto = new NeedsRideDTO(needsRide);
        
        // Then
        assertThat(dto.getUserId()).isEqualTo(userId);
        assertThat(dto.getNotes()).isEqualTo(complexNotes);
    }
    
    @Test
    void needsRideConstructor_DoesNotCopyEventId() {
        // Given
        String eventId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String notes = "Need a ride";
        NeedsRide needsRide = new NeedsRide(eventId, userId, notes);
        
        // When
        NeedsRideDTO dto = new NeedsRideDTO(needsRide);
        
        // Then - DTO should only contain userId and notes, not eventId
        assertThat(dto.getUserId()).isEqualTo(userId);
        assertThat(dto.getNotes()).isEqualTo(notes);
        // No eventId field in DTO, as expected
    }
}