package com.bbthechange.inviter.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TimeOptionsController
 * 
 * Test Coverage:
 * - GET /hangouts/time-options - Retrieve fuzzy time granularity options
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TimeOptionsController Tests")
class TimeOptionsControllerTest {

    @InjectMocks
    private TimeOptionsController timeOptionsController;

    @Test
    @DisplayName("Should return predefined time options successfully")
    void getTimeOptions_Success() {
        // Act
        ResponseEntity<List<String>> response = timeOptionsController.getTimeOptions();

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(7, response.getBody().size());
        
        List<String> timeOptions = response.getBody();
        assertEquals("exact", timeOptions.get(0));
        assertEquals("morning", timeOptions.get(1));
        assertEquals("afternoon", timeOptions.get(2));
        assertEquals("evening", timeOptions.get(3));
        assertEquals("night", timeOptions.get(4));
        assertEquals("day", timeOptions.get(5));
        assertEquals("weekend", timeOptions.get(6));
    }

    @Test
    @DisplayName("Should return consistent results on multiple calls")
    void getTimeOptions_Consistent() {
        // Act
        ResponseEntity<List<String>> response1 = timeOptionsController.getTimeOptions();
        ResponseEntity<List<String>> response2 = timeOptionsController.getTimeOptions();

        // Assert
        assertEquals(response1.getStatusCode(), response2.getStatusCode());
        assertEquals(response1.getBody(), response2.getBody());
    }

    @Test
    @DisplayName("Should contain all required time options")
    void getTimeOptions_ContainsAllRequired() {
        // Act
        ResponseEntity<List<String>> response = timeOptionsController.getTimeOptions();

        // Assert
        List<String> timeOptions = response.getBody();
        assertTrue(timeOptions.contains("exact"));
        assertTrue(timeOptions.contains("morning"));
        assertTrue(timeOptions.contains("afternoon"));
        assertTrue(timeOptions.contains("evening"));
        assertTrue(timeOptions.contains("night"));
        assertTrue(timeOptions.contains("day"));
        assertTrue(timeOptions.contains("weekend"));
    }
}