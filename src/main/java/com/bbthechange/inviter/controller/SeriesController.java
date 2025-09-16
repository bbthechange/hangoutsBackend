package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.CreateSeriesRequest;
import com.bbthechange.inviter.dto.EventSeriesDTO;
import com.bbthechange.inviter.model.EventSeries;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.exception.RepositoryException;
import com.bbthechange.inviter.exception.ResourceNotFoundException;
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
}