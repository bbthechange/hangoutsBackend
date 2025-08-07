package com.bbthechange.inviter.integration.repository;

import com.bbthechange.inviter.config.BaseIntegrationTest;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.dto.EventDetailData;
import com.bbthechange.inviter.dto.HangoutDetailData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for HangoutRepositoryImpl using TestContainers.
 * Tests the powerful item collection pattern for event data retrieval.
 */
@TestMethodOrder(OrderAnnotation.class)
@Testcontainers
class HangoutRepositoryImplTest extends BaseIntegrationTest {
    
    @Autowired
    private HangoutRepository hangoutRepository;
    
    private static final String TEST_EVENT_ID = "12345678-1234-1234-1234-123456789012";
    private static final String TEST_USER_ID = "87654321-4321-4321-4321-210987654321";
    private static final String TEST_POLL_ID = "11111111-1111-1111-1111-111111111111";
    
    @Test
    @Order(1)
    void savePoll_Success() {
        // Given
        Poll poll = new Poll(TEST_EVENT_ID, "What time works best?", "Pick a time", false);
        
        // When
        Poll saved = hangoutRepository.savePoll(poll);
        
        // Then
        assertThat(saved.getPollId()).isNotNull();
        assertThat(saved.getTitle()).isEqualTo("What time works best?");
        assertThat(saved.isActive()).isTrue();
    }
    
    @Test
    @Order(2)
    void saveCar_Success() {
        // Given
        Car car = new Car(TEST_EVENT_ID, TEST_USER_ID, "John Doe", 4);
        
        // When
        Car saved = hangoutRepository.saveCar(car);
        
        // Then
        assertThat(saved.getDriverId()).isEqualTo(TEST_USER_ID);
        assertThat(saved.getDriverName()).isEqualTo("John Doe");
        assertThat(saved.getTotalCapacity()).isEqualTo(4);
        assertThat(saved.getAvailableSeats()).isEqualTo(3); // Driver takes one seat
    }
    
    @Test
    @Order(3)
    void saveCarRider_Success() {
        // Given
        CarRider rider = new CarRider(TEST_EVENT_ID, TEST_USER_ID, "22222222-2222-2222-2222-222222222222", "Jane Smith");
        rider.setPlusOneCount(1);
        
        // When
        CarRider saved = hangoutRepository.saveCarRider(rider);
        
        // Then
        assertThat(saved.getRiderName()).isEqualTo("Jane Smith");
        assertThat(saved.getPlusOneCount()).isEqualTo(1);
        assertThat(saved.getTotalSeatsNeeded()).isEqualTo(2);
    }
    
    @Test
    @Order(4)
    void saveVote_Success() {
        // Given
        Vote vote = new Vote(TEST_EVENT_ID, TEST_POLL_ID, "33333333-3333-3333-3333-333333333333", TEST_USER_ID, "John Doe", "YES");
        
        // When
        Vote saved = hangoutRepository.saveVote(vote);
        
        // Then
        assertThat(saved.getUserName()).isEqualTo("John Doe");
        assertThat(saved.getVoteType()).isEqualTo("YES");
    }
    
    @Test
    @Order(5)
    void saveInterestLevel_Success() {
        // Given
        InterestLevel interest = new InterestLevel(TEST_EVENT_ID, TEST_USER_ID, "John Doe", "GOING");
        interest.setNotes("Looking forward to it!");
        
        // When
        InterestLevel saved = hangoutRepository.saveInterestLevel(interest);
        
        // Then
        assertThat(saved.getStatus()).isEqualTo("GOING");
        assertThat(saved.getNotes()).isEqualTo("Looking forward to it!");
    }
    
    @Test
    @Order(6)
    void getEventDetailData_ItemCollectionPattern() {
        // This test would require a mock Event to be set up since we're using EventRepository
        // For now, let's test that it doesn't crash with a non-existent event
        
        // When/Then - should throw ResourceNotFoundException for non-existent event
        assertThatThrownBy(() -> hangoutRepository.getEventDetailData("99999999-9999-9999-9999-999999999999"))
            .hasMessageContaining("Event not found");
    }
    
    @Test
    @Order(7)
    void deletePoll_Success() {
        // Given - we know there's a poll from earlier test
        
        // When
        assertThatCode(() -> hangoutRepository.deletePoll(TEST_EVENT_ID, TEST_POLL_ID))
            .doesNotThrowAnyException();
    }
    
    @Test
    @Order(8)
    void deleteCar_Success() {
        // Given - we know there's a car from earlier test
        
        // When
        assertThatCode(() -> hangoutRepository.deleteCar(TEST_EVENT_ID, TEST_USER_ID))
            .doesNotThrowAnyException();
    }
    
    @Test
    @Order(9)
    void deleteCarRider_Success() {
        // Given - we know there's a rider from earlier test
        
        // When
        assertThatCode(() -> hangoutRepository.deleteCarRider(TEST_EVENT_ID, TEST_USER_ID, "22222222-2222-2222-2222-222222222222"))
            .doesNotThrowAnyException();
    }
    
    @Test
    @Order(10)
    void deleteVote_Success() {
        // Given - we know there's a vote from earlier test
        
        // When
        assertThatCode(() -> hangoutRepository.deleteVote(TEST_EVENT_ID, TEST_POLL_ID, TEST_USER_ID, "33333333-3333-3333-3333-333333333333"))
            .doesNotThrowAnyException();
    }
    
    @Test
    @Order(11)
    void deleteInterestLevel_Success() {
        // Given - we know there's an interest level from earlier test
        
        // When
        assertThatCode(() -> hangoutRepository.deleteInterestLevel(TEST_EVENT_ID, TEST_USER_ID))
            .doesNotThrowAnyException();
    }
    
    // ===== Integration Tests for F-1-T3 Implementation =====
    
    @Test
    @Order(12)
    void createHangoutWithGSIFields_Success() {
        // Given
        Hangout hangout = new Hangout("GSI Test Hangout", "Testing EntityTimeIndex GSI", 
            null, null, null, EventVisibility.INVITE_ONLY, null);
        hangout.setHangoutId("gsi-test-hangout-1");
        
        // Set timeInput and timestamps for GSI
        Map<String, String> timeInput = new HashMap<>();
        timeInput.put("startTime", "1754558100");
        timeInput.put("endTime", "1754566200");
        hangout.setTimeInput(timeInput);
        hangout.setStartTimestamp(1754558100L);
        hangout.setEndTimestamp(1754566200L);
        
        // When
        Hangout saved = hangoutRepository.createHangout(hangout);
        
        // Then
        assertThat(saved.getHangoutId()).isEqualTo("gsi-test-hangout-1");
        assertThat(saved.getTimeInput()).isEqualTo(timeInput);
        assertThat(saved.getStartTimestamp()).isEqualTo(1754558100L);
        assertThat(saved.getEndTimestamp()).isEqualTo(1754566200L);
    }
    
    @Test
    @Order(13) 
    void findHangoutById_WithTimeInput_Success() {
        // Given - hangout created in previous test
        String hangoutId = "gsi-test-hangout-1";
        
        // When
        Optional<Hangout> result = hangoutRepository.findHangoutById(hangoutId);
        
        // Then
        assertThat(result).isPresent();
        Hangout hangout = result.get();
        assertThat(hangout.getHangoutId()).isEqualTo(hangoutId);
        assertThat(hangout.getTimeInput()).isNotNull();
        assertThat(hangout.getTimeInput().get("startTime")).isEqualTo("1754558100");
        assertThat(hangout.getStartTimestamp()).isEqualTo(1754558100L);
    }
    
    @Test  
    @Order(14)
    void findItemsByGSI1PKAndGSI1SKPrefix_WithCurrentTimestamp_ReturnsEmpty() {
        // Given - we're querying for future events but test hangout is in past
        String gsi1PK = "GROUP#test-group-1";
        String gsi1SKPrefix = "T#";
        
        // When - this should return empty since our test data has past timestamps
        List<BaseItem> result = hangoutRepository.findItemsByGSI1PKAndGSI1SKPrefix(gsi1PK, gsi1SKPrefix);
        
        // Then
        assertThat(result).isEmpty(); // Should be empty since test data is in the past
    }
    
    @Test
    @Order(15)
    void findItemsByGSI1PKAndGSI1SKPrefix_BasicTest() {
        // Given - test the method without creating complex GSI data
        // This is a basic test to verify the method doesn't crash
        String gsi1PK = "GROUP#nonexistent-group";
        String gsi1SKPrefix = "T#";
        
        // When - query for non-existent data
        assertThatCode(() -> {
            List<BaseItem> result = hangoutRepository.findItemsByGSI1PKAndGSI1SKPrefix(gsi1PK, gsi1SKPrefix);
            // Should return empty list, not crash
            assertThat(result).isEmpty();
        }).doesNotThrowAnyException();
    }
    
    @Test
    @Order(16)
    void getHangoutDetailData_WithTimeInput_Success() {
        // Given - hangout created in earlier test
        String hangoutId = "gsi-test-hangout-1";
        
        // When
        HangoutDetailData result = hangoutRepository.getHangoutDetailData(hangoutId);
        
        // Then
        assertThat(result.getHangout()).isNotNull();
        assertThat(result.getHangout().getHangoutId()).isEqualTo(hangoutId);
        assertThat(result.getHangout().getTimeInput()).isNotNull();
        assertThat(result.getHangout().getStartTimestamp()).isEqualTo(1754558100L);
    }
}