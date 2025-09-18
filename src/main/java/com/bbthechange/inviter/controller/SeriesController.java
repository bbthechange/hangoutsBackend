package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.CreateSeriesRequest;
import com.bbthechange.inviter.dto.CreateHangoutRequest;
import com.bbthechange.inviter.dto.EventSeriesDTO;
import com.bbthechange.inviter.dto.EventSeriesDetailDTO;
import com.bbthechange.inviter.dto.UpdateSeriesRequest;
import com.bbthechange.inviter.model.EventSeries;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.exception.RepositoryException;
import com.bbthechange.inviter.exception.ResourceNotFoundException;
import com.bbthechange.inviter.exception.ValidationException;
import com.bbthechange.inviter.service.EventSeriesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;

/**
 * REST controller for managing event series.
 * Handles creation, reading, updating, and deletion of multi-part event series.
 */
@RestController
@RequestMapping("/series")
public class SeriesController extends BaseController {
    
    private static final Logger logger = LoggerFactory.getLogger(SeriesController.class);
    
    private final EventSeriesService eventSeriesService;
    
    @Autowired
    public SeriesController(EventSeriesService eventSeriesService) {
        this.eventSeriesService = eventSeriesService;
    }
    
    /**
     * Create a new event series by converting an existing hangout and adding a new part.
     * 
     * @param request Contains the initial hangout ID and new member details
     * @return The created event series information
     */
    @PostMapping
    public ResponseEntity<EventSeriesDTO> createSeries(@Valid @RequestBody CreateSeriesRequest request, 
                                                        HttpServletRequest httpRequest) {
        try {
            String requestingUserId = extractUserId(httpRequest);
            logger.info("Creating series from hangout {} by user {}", 
                       request.getInitialHangoutId(), requestingUserId);
            
            EventSeries series = eventSeriesService.convertToSeriesWithNewMember(
                request.getInitialHangoutId(),
                request.getNewMemberRequest(),
                requestingUserId
            );
            
            EventSeriesDTO seriesDTO = new EventSeriesDTO(series);
            
            logger.info("Successfully created series {} from hangout {}", 
                       series.getSeriesId(), request.getInitialHangoutId());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(seriesDTO);
            
        } catch (ResourceNotFoundException e) {
            logger.warn("Resource not found when creating series: {}", e.getMessage());
            return ResponseEntity.notFound().build();
            
        } catch (UnauthorizedException e) {
            logger.warn("Unauthorized access when creating series: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            
        } catch (RepositoryException e) {
            logger.error("Repository error when creating series", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            
        } catch (Exception e) {
            logger.error("Unexpected error when creating series", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get detailed view of a single series including all its hangout details.
     * 
     * @param seriesId The ID of the series to retrieve
     * @return Detailed series information with full hangout details
     */
    @GetMapping("/{seriesId}")
    public ResponseEntity<EventSeriesDetailDTO> getSeriesDetail(@PathVariable String seriesId,
                                                                HttpServletRequest httpRequest) {
        try {
            String requestingUserId = extractUserId(httpRequest);
            logger.info("Getting detailed view for series {} by user {}", seriesId, requestingUserId);
            
            EventSeriesDetailDTO seriesDetail = eventSeriesService.getSeriesDetail(seriesId, requestingUserId);
            
            logger.info("Successfully retrieved detailed view for series {}", seriesId);
            return ResponseEntity.ok(seriesDetail);
            
        } catch (ResourceNotFoundException e) {
            logger.warn("Resource not found when getting series detail: {}", e.getMessage());
            return ResponseEntity.notFound().build();
            
        } catch (UnauthorizedException e) {
            logger.warn("Unauthorized access when getting series detail: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            
        } catch (RepositoryException e) {
            logger.error("Repository error when getting series detail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            
        } catch (Exception e) {
            logger.error("Unexpected error when getting series detail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Add a new hangout to an existing event series.
     * 
     * @param seriesId The ID of the series to add to
     * @param newMemberRequest The details for the new hangout to create
     * @return The updated series information
     */
    @PostMapping("/{seriesId}/hangouts")
    public ResponseEntity<EventSeriesDTO> addHangoutToSeries(@PathVariable String seriesId,
                                                              @Valid @RequestBody CreateHangoutRequest newMemberRequest,
                                                              HttpServletRequest httpRequest) {
        try {
            String requestingUserId = extractUserId(httpRequest);
            logger.info("Adding hangout to series {} by user {}", seriesId, requestingUserId);
            
            EventSeries updatedSeries = eventSeriesService.createHangoutInExistingSeries(
                seriesId, newMemberRequest, requestingUserId);
            
            EventSeriesDTO seriesDTO = new EventSeriesDTO(updatedSeries);
            
            logger.info("Successfully added hangout to series {}", seriesId);
            return ResponseEntity.status(HttpStatus.CREATED).body(seriesDTO);
            
        } catch (ResourceNotFoundException e) {
            logger.warn("Resource not found when adding hangout to series: {}", e.getMessage());
            return ResponseEntity.notFound().build();
            
        } catch (UnauthorizedException e) {
            logger.warn("Unauthorized access when adding hangout to series: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            
        } catch (ValidationException e) {
            logger.warn("Validation error when adding hangout to series: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
            
        } catch (RepositoryException e) {
            logger.error("Repository error when adding hangout to series", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            
        } catch (Exception e) {
            logger.error("Unexpected error when adding hangout to series", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Remove a hangout from a series without deleting the hangout.
     * 
     * @param seriesId The ID of the series
     * @param hangoutId The ID of the hangout to unlink
     * @return Success response with no content
     */
    @DeleteMapping("/{seriesId}/hangouts/{hangoutId}")
    public ResponseEntity<Void> unlinkHangoutFromSeries(@PathVariable String seriesId,
                                                         @PathVariable String hangoutId,
                                                         HttpServletRequest httpRequest) {
        try {
            String requestingUserId = extractUserId(httpRequest);
            logger.info("Unlinking hangout {} from series {} by user {}", 
                       hangoutId, seriesId, requestingUserId);
            
            eventSeriesService.unlinkHangoutFromSeries(seriesId, hangoutId, requestingUserId);
            
            logger.info("Successfully unlinked hangout {} from series {}", hangoutId, seriesId);
            return ResponseEntity.noContent().build();
            
        } catch (ResourceNotFoundException e) {
            logger.warn("Resource not found when unlinking hangout: {}", e.getMessage());
            return ResponseEntity.notFound().build();
            
        } catch (UnauthorizedException e) {
            logger.warn("Unauthorized access when unlinking hangout: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            
        } catch (RepositoryException e) {
            logger.error("Repository error when unlinking hangout", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            
        } catch (Exception e) {
            logger.error("Unexpected error when unlinking hangout", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Update the properties of an existing event series.
     * 
     * @param seriesId The ID of the series to update
     * @param updateRequest The update request containing new values and current version
     * @return The updated series information
     */
    @PutMapping("/{seriesId}")
    public ResponseEntity<EventSeriesDTO> updateSeries(@PathVariable String seriesId,
                                                        @Valid @RequestBody UpdateSeriesRequest updateRequest,
                                                        HttpServletRequest httpRequest) {
        try {
            String requestingUserId = extractUserId(httpRequest);
            logger.info("Updating series {} by user {}", seriesId, requestingUserId);
            
            EventSeries updatedSeries = eventSeriesService.updateSeries(seriesId, updateRequest, requestingUserId);
            
            EventSeriesDTO seriesDTO = new EventSeriesDTO(updatedSeries);
            
            logger.info("Successfully updated series {}", seriesId);
            return ResponseEntity.ok(seriesDTO);
            
        } catch (ResourceNotFoundException e) {
            logger.warn("Resource not found when updating series: {}", e.getMessage());
            return ResponseEntity.notFound().build();
            
        } catch (UnauthorizedException e) {
            logger.warn("Unauthorized access when updating series: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            
        } catch (ValidationException e) {
            logger.warn("Validation error when updating series: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
            
        } catch (RepositoryException e) {
            logger.error("Repository error when updating series", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            
        } catch (Exception e) {
            logger.error("Unexpected error when updating series", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Delete an entire event series and all of its constituent hangouts.
     * This is a cascading delete operation that removes all associated records atomically.
     * 
     * @param seriesId The ID of the series to delete
     * @return Success response with no content
     */
    @DeleteMapping("/{seriesId}")
    public ResponseEntity<Void> deleteEntireSeries(@PathVariable String seriesId,
                                                    HttpServletRequest httpRequest) {
        try {
            String requestingUserId = extractUserId(httpRequest);
            logger.info("Deleting entire series {} by user {}", seriesId, requestingUserId);
            
            eventSeriesService.deleteEntireSeries(seriesId, requestingUserId);
            
            logger.info("Successfully deleted entire series {}", seriesId);
            return ResponseEntity.noContent().build();
            
        } catch (ResourceNotFoundException e) {
            logger.warn("Resource not found when deleting series: {}", e.getMessage());
            return ResponseEntity.notFound().build();
            
        } catch (UnauthorizedException e) {
            logger.warn("Unauthorized access when deleting series: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            
        } catch (RepositoryException e) {
            logger.error("Repository error when deleting series", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            
        } catch (Exception e) {
            logger.error("Unexpected error when deleting series", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}