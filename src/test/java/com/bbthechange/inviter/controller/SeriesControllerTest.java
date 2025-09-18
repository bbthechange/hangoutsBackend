package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.CreateHangoutRequest;
import com.bbthechange.inviter.dto.CreateSeriesRequest;
import com.bbthechange.inviter.dto.EventSeriesDTO;
import com.bbthechange.inviter.dto.EventSeriesDetailDTO;
import com.bbthechange.inviter.dto.HangoutDetailDTO;
import com.bbthechange.inviter.exception.RepositoryException;
import com.bbthechange.inviter.exception.ResourceNotFoundException;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.model.EventSeries;
import com.bbthechange.inviter.model.Hangout;
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
    private String testSeriesId;

    @BeforeEach
    void setUp() {
        testUserId = "12345678-1234-1234-1234-123456789012";
        testHangoutId = "12345678-1234-1234-1234-123456789013";
        testSeriesId = "12345678-1234-1234-1234-123456789016";
        
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

    // Tests for unlinkHangoutFromSeries endpoint

    @Test
    void unlinkHangoutFromSeries_WithValidRequest_ReturnsNoContent() {
        // Given
        doNothing().when(eventSeriesService).unlinkHangoutFromSeries(testSeriesId, testHangoutId, testUserId);

        // When
        ResponseEntity<Void> response = seriesController.unlinkHangoutFromSeries(testSeriesId, testHangoutId, httpRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        
        // Verify service was called with correct parameters
        verify(eventSeriesService).unlinkHangoutFromSeries(testSeriesId, testHangoutId, testUserId);
    }

    @Test
    void unlinkHangoutFromSeries_WithResourceNotFound_Returns404() {
        // Given
        doThrow(new ResourceNotFoundException("Series not found"))
            .when(eventSeriesService).unlinkHangoutFromSeries(testSeriesId, testHangoutId, testUserId);

        // When
        ResponseEntity<Void> response = seriesController.unlinkHangoutFromSeries(testSeriesId, testHangoutId, httpRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void unlinkHangoutFromSeries_WithUnauthorizedUser_Returns403() {
        // Given
        doThrow(new UnauthorizedException("User not authorized"))
            .when(eventSeriesService).unlinkHangoutFromSeries(testSeriesId, testHangoutId, testUserId);

        // When
        ResponseEntity<Void> response = seriesController.unlinkHangoutFromSeries(testSeriesId, testHangoutId, httpRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void unlinkHangoutFromSeries_WithRepositoryException_Returns500() {
        // Given
        doThrow(new RepositoryException("Database error"))
            .when(eventSeriesService).unlinkHangoutFromSeries(testSeriesId, testHangoutId, testUserId);

        // When
        ResponseEntity<Void> response = seriesController.unlinkHangoutFromSeries(testSeriesId, testHangoutId, httpRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void unlinkHangoutFromSeries_WithUnexpectedException_Returns500() {
        // Given
        doThrow(new RuntimeException("Unexpected error"))
            .when(eventSeriesService).unlinkHangoutFromSeries(testSeriesId, testHangoutId, testUserId);

        // When
        ResponseEntity<Void> response = seriesController.unlinkHangoutFromSeries(testSeriesId, testHangoutId, httpRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void unlinkHangoutFromSeries_ExtractsUserIdCorrectly() {
        // Given
        doNothing().when(eventSeriesService).unlinkHangoutFromSeries(testSeriesId, testHangoutId, testUserId);

        // When
        seriesController.unlinkHangoutFromSeries(testSeriesId, testHangoutId, httpRequest);

        // Then
        verify(httpRequest).getAttribute("userId");
        verify(eventSeriesService).unlinkHangoutFromSeries(testSeriesId, testHangoutId, testUserId);
    }

    // Tests for getSeriesDetail endpoint

    @Test
    void getSeriesDetail_WithValidRequest_Returns200WithSeriesDetails() {
        // Given
        EventSeriesDetailDTO mockSeriesDetail = createMockEventSeriesDetailDTO();
        
        when(eventSeriesService.getSeriesDetail(testSeriesId, testUserId))
            .thenReturn(mockSeriesDetail);

        // When
        ResponseEntity<EventSeriesDetailDTO> response = seriesController.getSeriesDetail(testSeriesId, httpRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSeriesId()).isEqualTo(mockSeriesDetail.getSeriesId());
        assertThat(response.getBody().getSeriesTitle()).isEqualTo(mockSeriesDetail.getSeriesTitle());
        assertThat(response.getBody().getHangouts()).hasSize(mockSeriesDetail.getHangouts().size());
        
        // Verify service was called with correct parameters
        verify(eventSeriesService).getSeriesDetail(testSeriesId, testUserId);
    }

    @Test
    void getSeriesDetail_WithResourceNotFound_Returns404() {
        // Given
        when(eventSeriesService.getSeriesDetail(testSeriesId, testUserId))
            .thenThrow(new ResourceNotFoundException("EventSeries not found: " + testSeriesId));

        // When
        ResponseEntity<EventSeriesDetailDTO> response = seriesController.getSeriesDetail(testSeriesId, httpRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void getSeriesDetail_WithUnauthorizedUser_Returns403() {
        // Given
        when(eventSeriesService.getSeriesDetail(testSeriesId, testUserId))
            .thenThrow(new UnauthorizedException("User " + testUserId + " not found"));

        // When
        ResponseEntity<EventSeriesDetailDTO> response = seriesController.getSeriesDetail(testSeriesId, httpRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void getSeriesDetail_WithRepositoryError_Returns500() {
        // Given
        when(eventSeriesService.getSeriesDetail(testSeriesId, testUserId))
            .thenThrow(new RepositoryException("Database connection failed"));

        // When
        ResponseEntity<EventSeriesDetailDTO> response = seriesController.getSeriesDetail(testSeriesId, httpRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void getSeriesDetail_WithUnexpectedException_Returns500() {
        // Given
        when(eventSeriesService.getSeriesDetail(testSeriesId, testUserId))
            .thenThrow(new RuntimeException("Unexpected system error"));

        // When
        ResponseEntity<EventSeriesDetailDTO> response = seriesController.getSeriesDetail(testSeriesId, httpRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void getSeriesDetail_ExtractsUserIdCorrectly() {
        // Given
        EventSeriesDetailDTO mockSeriesDetail = createMockEventSeriesDetailDTO();
        
        when(eventSeriesService.getSeriesDetail(testSeriesId, testUserId))
            .thenReturn(mockSeriesDetail);

        // When
        seriesController.getSeriesDetail(testSeriesId, httpRequest);

        // Then
        verify(httpRequest).getAttribute("userId");
        verify(eventSeriesService).getSeriesDetail(testSeriesId, testUserId);
    }

    @Test
    void getSeriesDetail_LogsRequestAndSuccess() {
        // Given
        EventSeriesDetailDTO mockSeriesDetail = createMockEventSeriesDetailDTO();
        
        when(eventSeriesService.getSeriesDetail(testSeriesId, testUserId))
            .thenReturn(mockSeriesDetail);

        // When
        ResponseEntity<EventSeriesDetailDTO> response = seriesController.getSeriesDetail(testSeriesId, httpRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Note: Logging verification would require additional setup with LogCaptor or similar library
        // For now, we verify the successful flow completed
        verify(eventSeriesService).getSeriesDetail(testSeriesId, testUserId);
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

    private EventSeriesDetailDTO createMockEventSeriesDetailDTO() {
        EventSeries series = createMockEventSeries();
        
        // Create mock hangout details
        Hangout hangout1 = new Hangout();
        hangout1.setHangoutId("12345678-1234-1234-1234-123456789017");
        hangout1.setTitle("First Hangout");
        hangout1.setStartTimestamp(1000L);
        
        Hangout hangout2 = new Hangout();
        hangout2.setHangoutId("12345678-1234-1234-1234-123456789018");
        hangout2.setTitle("Second Hangout");
        hangout2.setStartTimestamp(2000L);
        
        HangoutDetailDTO hangoutDetail1 = new HangoutDetailDTO(
            hangout1, java.util.Collections.emptyList(), java.util.Collections.emptyList(), 
            java.util.Collections.emptyList(), java.util.Collections.emptyList(), java.util.Collections.emptyList(),
            java.util.Collections.emptyList(), java.util.Collections.emptyList()
        );
        
        HangoutDetailDTO hangoutDetail2 = new HangoutDetailDTO(
            hangout2, java.util.Collections.emptyList(), java.util.Collections.emptyList(), 
            java.util.Collections.emptyList(), java.util.Collections.emptyList(), java.util.Collections.emptyList(),
            java.util.Collections.emptyList(), java.util.Collections.emptyList()
        );
        
        java.util.List<HangoutDetailDTO> hangoutDetails = java.util.Arrays.asList(hangoutDetail1, hangoutDetail2);
        
        return new EventSeriesDetailDTO(series, hangoutDetails);
    }

    // ============================================================================
    // DELETE ENTIRE SERIES TESTS - Test Plan 3
    // ============================================================================

    @Test
    void deleteEntireSeries_WithValidData_ReturnsNoContent() {
        // Given
        doNothing().when(eventSeriesService).deleteEntireSeries(testSeriesId, testUserId);

        // When
        ResponseEntity<Void> response = seriesController.deleteEntireSeries(testSeriesId, httpRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        
        // Verify service was called with correct parameters
        verify(eventSeriesService).deleteEntireSeries(testSeriesId, testUserId);
        verify(httpRequest).getAttribute("userId");
    }

    @Test
    void deleteEntireSeries_WithResourceNotFound_Returns404() {
        // Given
        doThrow(new ResourceNotFoundException("EventSeries not found: " + testSeriesId))
            .when(eventSeriesService).deleteEntireSeries(testSeriesId, testUserId);

        // When
        ResponseEntity<Void> response = seriesController.deleteEntireSeries(testSeriesId, httpRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNull();
        
        // Verify service was called
        verify(eventSeriesService).deleteEntireSeries(testSeriesId, testUserId);
    }

    @Test
    void deleteEntireSeries_WithUnauthorizedAccess_Returns403() {
        // Given
        doThrow(new UnauthorizedException("User " + testUserId + " not found"))
            .when(eventSeriesService).deleteEntireSeries(testSeriesId, testUserId);

        // When
        ResponseEntity<Void> response = seriesController.deleteEntireSeries(testSeriesId, httpRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNull();
        
        // Verify service was called
        verify(eventSeriesService).deleteEntireSeries(testSeriesId, testUserId);
    }

    @Test
    void deleteEntireSeries_WithRepositoryError_Returns500() {
        // Given
        doThrow(new RepositoryException("Failed to delete entire series atomically"))
            .when(eventSeriesService).deleteEntireSeries(testSeriesId, testUserId);

        // When
        ResponseEntity<Void> response = seriesController.deleteEntireSeries(testSeriesId, httpRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNull();
        
        // Verify service was called
        verify(eventSeriesService).deleteEntireSeries(testSeriesId, testUserId);
    }

    @Test
    void deleteEntireSeries_WithUnexpectedError_Returns500() {
        // Given
        doThrow(new RuntimeException("Unexpected system error"))
            .when(eventSeriesService).deleteEntireSeries(testSeriesId, testUserId);

        // When
        ResponseEntity<Void> response = seriesController.deleteEntireSeries(testSeriesId, httpRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNull();
        
        // Verify service was called
        verify(eventSeriesService).deleteEntireSeries(testSeriesId, testUserId);
    }
}