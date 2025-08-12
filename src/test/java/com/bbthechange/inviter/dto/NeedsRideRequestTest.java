package com.bbthechange.inviter.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the NeedsRideRequest DTO class.
 */
class NeedsRideRequestTest {
    
    @Test
    void defaultConstructor_CreatesEmptyRequest() {
        // When
        NeedsRideRequest request = new NeedsRideRequest();
        
        // Then
        assertThat(request.getNotes()).isNull();
    }
    
    @Test
    void parameterizedConstructor_SetsNotesCorrectly() {
        // Given
        String notes = "Need a ride from downtown";
        
        // When
        NeedsRideRequest request = new NeedsRideRequest(notes);
        
        // Then
        assertThat(request.getNotes()).isEqualTo(notes);
    }
    
    @Test
    void parameterizedConstructor_HandlesNullNotes() {
        // When
        NeedsRideRequest request = new NeedsRideRequest(null);
        
        // Then
        assertThat(request.getNotes()).isNull();
    }
    
    @Test
    void parameterizedConstructor_HandlesEmptyNotes() {
        // Given
        String emptyNotes = "";
        
        // When
        NeedsRideRequest request = new NeedsRideRequest(emptyNotes);
        
        // Then
        assertThat(request.getNotes()).isEqualTo(emptyNotes);
    }
    
    @Test
    void setNotes_UpdatesNotesCorrectly() {
        // Given
        NeedsRideRequest request = new NeedsRideRequest();
        String notes = "Need a ride from the airport";
        
        // When
        request.setNotes(notes);
        
        // Then
        assertThat(request.getNotes()).isEqualTo(notes);
    }
    
    @Test
    void setNotes_HandlesNullValue() {
        // Given
        NeedsRideRequest request = new NeedsRideRequest("Initial notes");
        
        // When
        request.setNotes(null);
        
        // Then
        assertThat(request.getNotes()).isNull();
    }
    
    @Test
    void setNotes_HandlesEmptyString() {
        // Given
        NeedsRideRequest request = new NeedsRideRequest("Initial notes");
        
        // When
        request.setNotes("");
        
        // Then
        assertThat(request.getNotes()).isEqualTo("");
    }
    
    @Test
    void setNotes_WithValidLengthNotes_SetsSuccessfully() {
        // Given
        NeedsRideRequest request = new NeedsRideRequest();
        String notes = "Need a ride from downtown, can meet at the subway station at 7 PM";
        
        // When
        request.setNotes(notes);
        
        // Then
        assertThat(request.getNotes()).isEqualTo(notes);
    }
    
    @Test
    void setNotes_WithMaxLengthNotes_SetsSuccessfully() {
        // Given - Max length is 500 characters according to @Size annotation
        NeedsRideRequest request = new NeedsRideRequest();
        String maxLengthNotes = "a".repeat(500);
        
        // When
        request.setNotes(maxLengthNotes);
        
        // Then
        assertThat(request.getNotes()).isEqualTo(maxLengthNotes);
        assertThat(request.getNotes()).hasSize(500);
    }
    
    @Test
    void setNotes_WithOverMaxLengthNotes_StillSetsValue() {
        // Given - Note: Validation happens at controller level, not in the DTO
        NeedsRideRequest request = new NeedsRideRequest();
        String overLengthNotes = "a".repeat(501);
        
        // When
        request.setNotes(overLengthNotes);
        
        // Then
        assertThat(request.getNotes()).isEqualTo(overLengthNotes);
        assertThat(request.getNotes()).hasSize(501);
    }
    
    @ParameterizedTest
    @ValueSource(strings = {
        "Need a ride",
        "Can pay for gas",
        "Flexible on pickup location",
        "Need to arrive by 8 PM",
        "Can meet at train station",
        "Need ride back too",
        "Happy to split costs"
    })
    void setNotes_WithVariousValidNotes_SetsCorrectly(String notes) {
        // Given
        NeedsRideRequest request = new NeedsRideRequest();
        
        // When
        request.setNotes(notes);
        
        // Then
        assertThat(request.getNotes()).isEqualTo(notes);
    }
    
    @Test
    void setNotes_WithUnicodeCharacters_HandlesCorrectly() {
        // Given
        NeedsRideRequest request = new NeedsRideRequest();
        String unicodeNotes = "需要搭车 🚗 から駅まで";
        
        // When
        request.setNotes(unicodeNotes);
        
        // Then
        assertThat(request.getNotes()).isEqualTo(unicodeNotes);
    }
    
    @Test
    void setNotes_WithNewlinesAndSpecialCharacters_HandlesCorrectly() {
        // Given
        NeedsRideRequest request = new NeedsRideRequest();
        String specialNotes = "Need a ride\nFrom: Downtown\nTo: Event venue\nTime: 7:00 PM\nContact: @user123";
        
        // When
        request.setNotes(specialNotes);
        
        // Then
        assertThat(request.getNotes()).isEqualTo(specialNotes);
    }
    
    @Test
    void setNotes_WithWhitespaceOnly_SetsValue() {
        // Given
        NeedsRideRequest request = new NeedsRideRequest();
        String whitespaceNotes = "   \t\n   ";
        
        // When
        request.setNotes(whitespaceNotes);
        
        // Then
        assertThat(request.getNotes()).isEqualTo(whitespaceNotes);
    }
    
    @Test
    void setNotes_MultipleTimes_UpdatesCorrectly() {
        // Given
        NeedsRideRequest request = new NeedsRideRequest();
        
        // When/Then - Multiple updates
        request.setNotes("First note");
        assertThat(request.getNotes()).isEqualTo("First note");
        
        request.setNotes("Second note");
        assertThat(request.getNotes()).isEqualTo("Second note");
        
        request.setNotes(null);
        assertThat(request.getNotes()).isNull();
        
        request.setNotes("Final note");
        assertThat(request.getNotes()).isEqualTo("Final note");
    }
}