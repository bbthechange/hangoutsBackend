package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.CreateHangoutRequest;
import com.bbthechange.inviter.dto.CreateSeriesRequest;
import com.bbthechange.inviter.dto.EventSeriesDTO;
import com.bbthechange.inviter.exception.RepositoryException;
import com.bbthechange.inviter.exception.ResourceNotFoundException;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.model.EventSeries;
import com.bbthechange.inviter.service.EventSeriesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SeriesController.
 * Tests series creation endpoint with various scenarios and exception handling.
 */
@ExtendWith(MockitoExtension.class)
class SeriesControllerTest {

    @Mock
    private EventSeriesService eventSeriesService;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private SeriesController seriesController;

    private CreateSeriesRequest validRequest;
    private EventSeries mockEventSeries;
    private String testUserId;
    private String testHangoutId;

    @BeforeEach
    void setUp() {
        testUserId = "12345678-1234-1234-1234-123456789012";
        testHangoutId = "12345678-1234-1234-1234-123456789013";
        
        // Set up valid request
        CreateHangoutRequest newMemberRequest = createValidCreateHangoutRequest();
        validRequest = new CreateSeriesRequest(testHangoutId, newMemberRequest);
        
        // Set up mock EventSeries
        mockEventSeries = createMockEventSeries();
        
        // Set up httpRequest mock
        when(httpRequest.getAttribute("userId")).thenReturn(testUserId);
    }

    @Test
    void createSeries_WithValidRequest_ReturnsCreatedSeries() {
        // Given
        when(eventSeriesService.convertToSeriesWithNewMember(
            eq(testHangoutId), 
            eq(validRequest.getNewMemberRequest()), 
            eq(testUserId)))
            .thenReturn(mockEventSeries);

        // When
        ResponseEntity<EventSeriesDTO> response = seriesController.createSeries(validRequest, httpRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSeriesId()).isEqualTo(mockEventSeries.getSeriesId());
        assertThat(response.getBody().getSeriesTitle()).isEqualTo(mockEventSeries.getSeriesTitle());
        
        // Verify service was called with correct parameters
        verify(eventSeriesService).convertToSeriesWithNewMember(testHangoutId, validRequest.getNewMemberRequest(), testUserId);
    }

    @Test
    void createSeries_WithResourceNotFound_Returns404() {
        // Given
        when(eventSeriesService.convertToSeriesWithNewMember(
            eq(testHangoutId), 
            eq(validRequest.getNewMemberRequest()), 
            eq(testUserId)))
            .thenThrow(new ResourceNotFoundException("Hangout not found"));

        // When
        ResponseEntity<EventSeriesDTO> response = seriesController.createSeries(validRequest, httpRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void createSeries_WithUnauthorizedUser_Returns403() {
        // Given
        when(eventSeriesService.convertToSeriesWithNewMember(
            eq(testHangoutId), 
            eq(validRequest.getNewMemberRequest()), 
            eq(testUserId)))
            .thenThrow(new UnauthorizedException("User not authorized"));

        // When
        ResponseEntity<EventSeriesDTO> response = seriesController.createSeries(validRequest, httpRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void createSeries_WithRepositoryException_Returns500() {
        // Given
        when(eventSeriesService.convertToSeriesWithNewMember(
            eq(testHangoutId), 
            eq(validRequest.getNewMemberRequest()), 
            eq(testUserId)))
            .thenThrow(new RepositoryException("Database error"));

        // When
        ResponseEntity<EventSeriesDTO> response = seriesController.createSeries(validRequest, httpRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void createSeries_WithUnexpectedException_Returns500() {
        // Given
        when(eventSeriesService.convertToSeriesWithNewMember(
            eq(testHangoutId), 
            eq(validRequest.getNewMemberRequest()), 
            eq(testUserId)))
            .thenThrow(new RuntimeException("Unexpected error"));

        // When
        ResponseEntity<EventSeriesDTO> response = seriesController.createSeries(validRequest, httpRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void createSeries_ExtractsUserIdFromRequest() {
        // Given
        when(eventSeriesService.convertToSeriesWithNewMember(
            eq(testHangoutId), 
            eq(validRequest.getNewMemberRequest()), 
            eq(testUserId)))
            .thenReturn(mockEventSeries);

        // When
        seriesController.createSeries(validRequest, httpRequest);

        // Then
        verify(httpRequest).getAttribute("userId");
        verify(eventSeriesService).convertToSeriesWithNewMember(testHangoutId, validRequest.getNewMemberRequest(), testUserId);
    }

    @Test
    void createSeries_LogsCreationAndSuccess() {
        // Given
        when(eventSeriesService.convertToSeriesWithNewMember(
            eq(testHangoutId), 
            eq(validRequest.getNewMemberRequest()), 
            eq(testUserId)))
            .thenReturn(mockEventSeries);

        // When
        ResponseEntity<EventSeriesDTO> response = seriesController.createSeries(validRequest, httpRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        // Note: Logging verification would require additional setup with LogCaptor or similar library
        // For now, we verify the successful flow completed
        verify(eventSeriesService).convertToSeriesWithNewMember(testHangoutId, validRequest.getNewMemberRequest(), testUserId);
    }

    // Helper methods
    private CreateHangoutRequest createValidCreateHangoutRequest() {
        CreateHangoutRequest request = new CreateHangoutRequest();
        request.setTitle("Test New Member Hangout");
        request.setDescription("Description for new member");
        return request;
    }

    private EventSeries createMockEventSeries() {
        EventSeries series = new EventSeries();
        series.setSeriesId("12345678-1234-1234-1234-123456789014");
        series.setSeriesTitle("Test Series");
        series.setSeriesDescription("Test series description");
        series.setGroupId("12345678-1234-1234-1234-123456789015");
        series.setVersion(1L);
        return series;
    }
}