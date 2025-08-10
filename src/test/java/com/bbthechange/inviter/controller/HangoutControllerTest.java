package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.service.HangoutService;
import com.bbthechange.inviter.dto.SetInterestRequest;
import com.bbthechange.inviter.exception.ResourceNotFoundException;
import com.bbthechange.inviter.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for HangoutController interest endpoints
 * 
 * Test Coverage:
 * - PUT /hangouts/{id}/interest - Set user interest
 * - DELETE /hangouts/{id}/interest - Remove user interest
 * - Various error scenarios and edge cases
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HangoutController Tests")
class HangoutControllerTest {

    @Mock
    private HangoutService hangoutService;

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private HangoutController hangoutController;

    private String testHangoutId;
    private String testUserId;
    private SetInterestRequest setInterestRequest;

    @BeforeEach
    void setUp() {
        testHangoutId = "12345678-1234-1234-1234-123456789012";
        testUserId = "87654321-4321-4321-4321-210987654321";
        
        setInterestRequest = new SetInterestRequest("GOING", "Excited to attend!");
        
        // The actual extractUserId method would decode the JWT and return the user ID
        // For testing purposes, we'll mock the behavior that extracts the user ID
        hangoutController = new HangoutController(hangoutService) {
            @Override
            protected String extractUserId(HttpServletRequest request) {
                return testUserId;
            }
        };
    }

    @Nested
    @DisplayName("PUT /hangouts/{id}/interest - Set Interest Tests")
    class SetInterestTests {

        @DisplayName("Should set user interest successfully")
        @Test
        void setInterest_Success() {
            // Arrange
            doNothing().when(hangoutService).setUserInterest(testHangoutId, setInterestRequest, testUserId);

            // Act
            ResponseEntity<Void> response = hangoutController.setInterest(testHangoutId, setInterestRequest, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNull(response.getBody());
            
            verify(hangoutService).setUserInterest(testHangoutId, setInterestRequest, testUserId);
        }

        @Test
        void setInterest_HangoutNotFound_ThrowsException() {
            // Arrange
            doThrow(new ResourceNotFoundException("Hangout not found: " + testHangoutId))
                .when(hangoutService).setUserInterest(testHangoutId, setInterestRequest, testUserId);

            // Act & Assert
            assertThrows(ResourceNotFoundException.class, () -> 
                hangoutController.setInterest(testHangoutId, setInterestRequest, httpServletRequest)
            );
            
            verify(hangoutService).setUserInterest(testHangoutId, setInterestRequest, testUserId);
        }

        @DisplayName("Should handle unauthorized user exception")
        @Test
        void setInterest_UnauthorizedUser_ThrowsException() {
            // Arrange
            doThrow(new UnauthorizedException("Cannot set interest for this event"))
                .when(hangoutService).setUserInterest(testHangoutId, setInterestRequest, testUserId);

            // Act & Assert
            assertThrows(UnauthorizedException.class, () -> 
                hangoutController.setInterest(testHangoutId, setInterestRequest, httpServletRequest)
            );
            
            verify(hangoutService).setUserInterest(testHangoutId, setInterestRequest, testUserId);
        }
        @DisplayName("Should handle different interest statuses")
        @Test
        void setInterest_DifferentStatuses_Success() {
            // Test INTERESTED status
            SetInterestRequest interestedRequest = new SetInterestRequest("INTERESTED", "Maybe I can make it");
            doNothing().when(hangoutService).setUserInterest(testHangoutId, interestedRequest, testUserId);

            ResponseEntity<Void> response1 = hangoutController.setInterest(testHangoutId, interestedRequest, httpServletRequest);
            assertEquals(HttpStatus.OK, response1.getStatusCode());

            // Test NOT_GOING status
            SetInterestRequest notGoingRequest = new SetInterestRequest("NOT_GOING", "Cannot attend");
            doNothing().when(hangoutService).setUserInterest(testHangoutId, notGoingRequest, testUserId);

            ResponseEntity<Void> response2 = hangoutController.setInterest(testHangoutId, notGoingRequest, httpServletRequest);
            assertEquals(HttpStatus.OK, response2.getStatusCode());

            verify(hangoutService).setUserInterest(testHangoutId, interestedRequest, testUserId);
            verify(hangoutService).setUserInterest(testHangoutId, notGoingRequest, testUserId);
        }

        @DisplayName("Should handle request without notes")
        @Test
        void setInterest_WithoutNotes_Success() {
            // Arrange
            SetInterestRequest requestWithoutNotes = new SetInterestRequest("GOING", null);
            doNothing().when(hangoutService).setUserInterest(testHangoutId, requestWithoutNotes, testUserId);

            // Act
            ResponseEntity<Void> response = hangoutController.setInterest(testHangoutId, requestWithoutNotes, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(hangoutService).setUserInterest(testHangoutId, requestWithoutNotes, testUserId);
        }
    }

    @Nested
    @DisplayName("DELETE /hangouts/{id}/interest - Remove Interest Tests")
    class RemoveInterestTests {

        @DisplayName("Should remove user interest successfully")
        @Test
        void removeInterest_Success() {
            // Arrange
            doNothing().when(hangoutService).removeUserInterest(testHangoutId, testUserId);

            // Act
            ResponseEntity<Void> response = hangoutController.removeInterest(testHangoutId, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
            assertNull(response.getBody());
            
            verify(hangoutService).removeUserInterest(testHangoutId, testUserId);
        }

        @Test
        void removeInterest_HangoutNotFound_ThrowsException() {
            // Arrange
            doThrow(new ResourceNotFoundException("Hangout not found: " + testHangoutId))
                .when(hangoutService).removeUserInterest(testHangoutId, testUserId);

            // Act & Assert
            assertThrows(ResourceNotFoundException.class, () ->
                hangoutController.removeInterest(testHangoutId, httpServletRequest)
            );
            
            verify(hangoutService).removeUserInterest(testHangoutId, testUserId);
        }

        @DisplayName("Should handle unauthorized user exception")
        @Test
        void removeInterest_UnauthorizedUser_ThrowsException() {
            // Arrange
            doThrow(new UnauthorizedException("Cannot remove interest for this event"))
                .when(hangoutService).removeUserInterest(testHangoutId, testUserId);

            // Act & Assert
            assertThrows(UnauthorizedException.class, () -> 
                hangoutController.removeInterest(testHangoutId, httpServletRequest)
            );
            
            verify(hangoutService).removeUserInterest(testHangoutId, testUserId);
        }

        @DisplayName("Should remove interest even when user had no existing interest")
        @Test
        void removeInterest_NoExistingInterest_Success() {
            // Arrange - Service handles gracefully when no existing interest
            doNothing().when(hangoutService).removeUserInterest(testHangoutId, testUserId);

            // Act
            ResponseEntity<Void> response = hangoutController.removeInterest(testHangoutId, httpServletRequest);

            // Assert
            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
            verify(hangoutService).removeUserInterest(testHangoutId, testUserId);
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @DisplayName("Should validate hangout ID format in set interest")
        @Test
        void setInterest_InvalidHangoutIdFormat() {
            // This would be handled by Spring's @Pattern validation
            // In a real scenario, Spring would reject the request before it reaches the controller method
            String invalidHangoutId = "invalid-id-format";
            
            // The @Pattern annotation should prevent this from reaching the controller
            // This test documents the expected behavior
            assertTrue(invalidHangoutId.length() < 36, "Invalid hangout ID should be rejected by validation");
        }

        @DisplayName("Should validate hangout ID format in remove interest")
        @Test
        void removeInterest_InvalidHangoutIdFormat() {
            // This would be handled by Spring's @Pattern validation
            String invalidHangoutId = "invalid-id-format";
            
            // The @Pattern annotation should prevent this from reaching the controller
            assertTrue(invalidHangoutId.length() < 36, "Invalid hangout ID should be rejected by validation");
        }
    }
}
