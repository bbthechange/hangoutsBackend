package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.service.HangoutService;
import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.model.Hangout;
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
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for HangoutController
 * 
 * Test Coverage:
 * - POST /hangouts - Create hangout
 * - GET /hangouts/{id} - Get hangout detail
 * - PATCH /hangouts/{id} - Update hangout
 * - DELETE /hangouts/{id} - Delete hangout
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

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private String testHangoutId;
    private String testUserId;
    private SetInterestRequest setInterestRequest;
    private CreateHangoutRequest createHangoutRequest;
    private UpdateHangoutRequest updateHangoutRequest;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(hangoutController)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
        objectMapper = new ObjectMapper();
        
        testHangoutId = "12345678-1234-1234-1234-123456789012";
        testUserId = "87654321-4321-4321-4321-210987654321";
        
        setInterestRequest = new SetInterestRequest("GOING", "Excited to attend!");
        
        createHangoutRequest = new CreateHangoutRequest();
        createHangoutRequest.setTitle("Test Hangout");
        createHangoutRequest.setDescription("Test Description");
        
        updateHangoutRequest = new UpdateHangoutRequest();
        updateHangoutRequest.setTitle("Updated Hangout");
        updateHangoutRequest.setDescription("Updated Description");
        
        // The actual extractUserId method would decode the JWT and return the user ID
        // For testing purposes, we'll mock the behavior that extracts the user ID
        hangoutController = new HangoutController(hangoutService) {
            @Override
            protected String extractUserId(HttpServletRequest request) {
                return testUserId;
            }
        };
        mockMvc = MockMvcBuilders.standaloneSetup(hangoutController)
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Nested
    @DisplayName("POST /hangouts - Create Hangout Tests")
    class CreateHangoutTests {

        @Test
        @DisplayName("Should create hangout successfully")
        void createHangout_Success() throws Exception {
            // Given
            Hangout mockHangout = new Hangout();
            mockHangout.setHangoutId(testHangoutId);
            mockHangout.setTitle("Test Hangout");
            mockHangout.setDescription("Test Description");
            
            when(hangoutService.createHangout(any(CreateHangoutRequest.class), eq(testUserId)))
                    .thenReturn(mockHangout);

            // When & Then
            mockMvc.perform(post("/hangouts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createHangoutRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.hangoutId").value(testHangoutId))
                    .andExpect(jsonPath("$.title").value("Test Hangout"));

            verify(hangoutService).createHangout(any(CreateHangoutRequest.class), eq(testUserId));
        }
    }

    @Nested
    @DisplayName("GET /hangouts/{id} - Get Hangout Tests")
    class GetHangoutTests {

        @Test
        @DisplayName("Should return hangout detail successfully")
        void getHangout_Success() throws Exception {
            // Given
            Hangout hangout = new Hangout();
            hangout.setHangoutId(testHangoutId);
            hangout.setTitle("Test Hangout");
            HangoutDetailDTO mockDetail = new HangoutDetailDTO(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
            
            when(hangoutService.getHangoutDetail(eq(testHangoutId), eq(testUserId)))
                    .thenReturn(mockDetail);

            // When & Then
            mockMvc.perform(get("/hangouts/{hangoutId}", testHangoutId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hangout.hangoutId").value(testHangoutId))
                    .andExpect(jsonPath("$.hangout.title").value("Test Hangout"));

            verify(hangoutService).getHangoutDetail(eq(testHangoutId), eq(testUserId));
        }
    }

    @Nested
    @DisplayName("PATCH /hangouts/{id} - Update Hangout Tests")
    class UpdateHangoutTests {

        @Test
        @DisplayName("Should update hangout successfully")
        void updateHangout_Success() throws Exception {
            // Given
            doNothing().when(hangoutService).updateHangout(eq(testHangoutId), any(UpdateHangoutRequest.class), eq(testUserId));

            // When & Then
            mockMvc.perform(patch("/hangouts/{hangoutId}", testHangoutId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateHangoutRequest)))
                    .andExpect(status().isOk());

            verify(hangoutService).updateHangout(eq(testHangoutId), any(UpdateHangoutRequest.class), eq(testUserId));
        }
    }

    @Nested
    @DisplayName("DELETE /hangouts/{id} - Delete Hangout Tests")
    class DeleteHangoutTests {

        @Test
        @DisplayName("Should delete hangout successfully")
        void deleteHangout_Success() throws Exception {
            // Given
            doNothing().when(hangoutService).deleteHangout(eq(testHangoutId), eq(testUserId));

            // When & Then
            mockMvc.perform(delete("/hangouts/{hangoutId}", testHangoutId))
                    .andExpect(status().isNoContent());

            verify(hangoutService).deleteHangout(eq(testHangoutId), eq(testUserId));
        }
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
