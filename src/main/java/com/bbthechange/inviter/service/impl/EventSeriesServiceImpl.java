package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.model.EventSeries;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.service.EventSeriesService;
import com.bbthechange.inviter.dto.CreateHangoutRequest;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.repository.EventSeriesRepository;
import com.bbthechange.inviter.repository.SeriesTransactionRepository;
import com.bbthechange.inviter.repository.UserRepository;
import com.bbthechange.inviter.exception.ResourceNotFoundException;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.exception.RepositoryException;
import com.bbthechange.inviter.service.HangoutService;
import com.bbthechange.inviter.util.InviterKeyFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

/**
 * Implementation of EventSeriesService for managing multi-part event series.
 * Orchestrates the business logic for creating series and adding new parts.
 */
@Service
public class EventSeriesServiceImpl implements EventSeriesService {
    
    private static final Logger logger = LoggerFactory.getLogger(EventSeriesServiceImpl.class);
    
    private final HangoutRepository hangoutRepository;
    private final EventSeriesRepository eventSeriesRepository;
    private final SeriesTransactionRepository seriesTransactionRepository;
    private final UserRepository userRepository;
    private final HangoutService hangoutService;
    
    @Autowired
    public EventSeriesServiceImpl(
            HangoutRepository hangoutRepository,
            EventSeriesRepository eventSeriesRepository,
            SeriesTransactionRepository seriesTransactionRepository,
            UserRepository userRepository,
            HangoutService hangoutService) {
        this.hangoutRepository = hangoutRepository;
        this.eventSeriesRepository = eventSeriesRepository;
        this.seriesTransactionRepository = seriesTransactionRepository;
        this.userRepository = userRepository;
        this.hangoutService = hangoutService;
    }
    
    @Override
    public EventSeries convertToSeriesWithNewMember(
            String existingHangoutId, 
            CreateHangoutRequest newMemberRequest, 
            String userId) {
        
        logger.info("Converting hangout {} to series with new member for user {}", 
            existingHangoutId, userId);
        
        // 1. Validation and Permissions
        // Fetch existing hangout and pointer
        Optional<Hangout> existingHangoutOpt = hangoutRepository.findHangoutById(existingHangoutId);
        if (existingHangoutOpt.isEmpty()) {
            throw new ResourceNotFoundException("Hangout not found: " + existingHangoutId);
        }
        
        Hangout existingHangout = existingHangoutOpt.get();
        
        // Verify user has permission
        // For now, we'll check if the user exists (basic validation)
        // TODO: Implement proper authorization logic based on group membership or event participation
        if (userRepository.findById(userId).isEmpty()) {
            throw new UnauthorizedException("User " + userId + " not found");
        }
        
        // Fetch all existing pointers for this hangout (one per associated group)
        List<HangoutPointer> existingPointers = hangoutRepository.findPointersForHangout(existingHangout);
        if (existingPointers.isEmpty()) {
            throw new ResourceNotFoundException("No HangoutPointer records found for hangout: " + existingHangoutId);
        }
        
        // 2. Data Preparation (In Memory)
        // Create new EventSeries
        String seriesId = UUID.randomUUID().toString();
        EventSeries newSeries = new EventSeries();
        newSeries.setSeriesId(seriesId);
        newSeries.setSeriesTitle(existingHangout.getTitle() + " Series");
        newSeries.setPrimaryEventId(existingHangoutId);
        newSeries.setHangoutIds(new ArrayList<>());
        newSeries.getHangoutIds().add(existingHangoutId);
        
        // Set the groupId on the series (use the first group from existing pointers)
        String primaryGroupId = existingPointers.get(0).getGroupId();
        newSeries.setGroupId(primaryGroupId);
        
        // Set DynamoDB keys for the series
        newSeries.setPk(InviterKeyFactory.getSeriesPk(seriesId));
        newSeries.setSk(InviterKeyFactory.getMetadataSk());
        
        // Set GSI keys for querying by group
        newSeries.setGsi1pk(InviterKeyFactory.getGroupPk(primaryGroupId));
        
        // Set startTimestamp from the existing hangout for sorting
        if (existingHangout.getStartTimestamp() != null) {
            newSeries.setStartTimestamp(existingHangout.getStartTimestamp());
        }
        
        // Create new Hangout for the new member
        Hangout newHangout = hangoutService.hangoutFromHangoutRequest(newMemberRequest, userId);
        newHangout.setSeriesId(seriesId);
        
        // Set the proper DynamoDB keys
        newHangout.setPk(InviterKeyFactory.getEventPk(newHangout.getHangoutId()));
        newHangout.setSk(InviterKeyFactory.getMetadataSk());
        
        // Timestamps should be set by time processing logic that converts TimeInfo to timestamps
        // For now, we leave them null - they should be set by a separate time processing service
        
        // Add new hangout ID to series
        newSeries.getHangoutIds().add(newHangout.getHangoutId());
        
        // Create new HangoutPointers for each group (one per associated group)
        List<HangoutPointer> newPointers = new ArrayList<>();
        for (String groupId : newHangout.getAssociatedGroups()) {
            HangoutPointer newPointer = new HangoutPointer(
                groupId,
                newHangout.getHangoutId(),
                newHangout.getTitle()
            );
            newPointer.setSeriesId(seriesId);
            newPointer.setTimeInput(newHangout.getTimeInput());
            // Copy timestamps from hangout if available
            if (newHangout.getStartTimestamp() != null) {
                newPointer.setStartTimestamp(newHangout.getStartTimestamp());
                newPointer.setEndTimestamp(newHangout.getEndTimestamp());
            }
            if (newHangout.getLocation() != null) {
                newPointer.setLocationName(newHangout.getLocation().getName());
            }
            
            // Set GSI1PK for the pointer (required for EntityTimeIndex)
            newPointer.setGsi1pk(InviterKeyFactory.getGroupPk(groupId));
            
            newPointers.add(newPointer);
        }
        
        // Update existing hangout with series ID
        existingHangout.setSeriesId(seriesId);
        
        // Update all existing pointers with series ID
        for (HangoutPointer existingPointer : existingPointers) {
            existingPointer.setSeriesId(seriesId);
        }
        
        // 3. Persistence (The Single Transactional Call)
        try {
            seriesTransactionRepository.createSeriesWithNewPart(
                newSeries,
                existingHangout,
                existingPointers,
                newHangout,
                newPointers
            );
            
            logger.info("Successfully created series {} from hangout {}", seriesId, existingHangoutId);
            
            // 4. Return Value
            return newSeries;
            
        } catch (Exception e) {
            logger.error("Failed to create series from hangout {}", existingHangoutId, e);
            throw new RepositoryException("Failed to create series atomically", e);
        }
    }
    
    @Override
    public EventSeries createHangoutInExistingSeries(
            String seriesId, 
            CreateHangoutRequest newMemberRequest, 
            String userId) {
        
        logger.info("Adding new hangout to series {} for user {}", seriesId, userId);
        
        // 1. Validation
        // Fetch the EventSeries
        Optional<EventSeries> seriesOpt = eventSeriesRepository.findById(seriesId);
        if (seriesOpt.isEmpty()) {
            throw new ResourceNotFoundException("EventSeries not found: " + seriesId);
        }
        
        EventSeries series = seriesOpt.get();
        
        // Verify user has permission
        // For now, we'll check if the user exists (basic validation)
        // TODO: Implement proper authorization logic based on group membership or event participation
        if (userRepository.findById(userId).isEmpty()) {
            throw new UnauthorizedException("User " + userId + " not found");
        }
        
        // 2. Data Preparation
        // Create new Hangout
        String newHangoutId = UUID.randomUUID().toString();
        Hangout newHangout = new Hangout();
        newHangout.setHangoutId(newHangoutId);
        newHangout.setTitle(newMemberRequest.getTitle());
        newHangout.setDescription(newMemberRequest.getDescription());
        newHangout.setTimeInput(newMemberRequest.getTimeInfo());
        newHangout.setLocation(newMemberRequest.getLocation());
        newHangout.setVisibility(newMemberRequest.getVisibility());
        newHangout.setMainImagePath(newMemberRequest.getMainImagePath());
        newHangout.setAssociatedGroups(newMemberRequest.getAssociatedGroups());
        newHangout.setCarpoolEnabled(newMemberRequest.isCarpoolEnabled());
        newHangout.setSeriesId(seriesId);
        
        // Set the proper DynamoDB keys
        newHangout.setPk(InviterKeyFactory.getEventPk(newHangoutId));
        newHangout.setSk(InviterKeyFactory.getMetadataSk());
        
        // Timestamps should be set by time processing logic that converts TimeInfo to timestamps
        // For now, we leave them null - they should be set by a separate time processing service
        
        // Create new HangoutPointers for each group (one per associated group)
        List<HangoutPointer> newPointers = new ArrayList<>();
        for (String groupId : newHangout.getAssociatedGroups()) {
            HangoutPointer newPointer = new HangoutPointer(
                groupId,
                newHangoutId,
                newHangout.getTitle()
            );
            newPointer.setSeriesId(seriesId);
            newPointer.setTimeInput(newHangout.getTimeInput());
            // Copy timestamps from hangout if available
            if (newHangout.getStartTimestamp() != null) {
                newPointer.setStartTimestamp(newHangout.getStartTimestamp());
                newPointer.setEndTimestamp(newHangout.getEndTimestamp());
            }
            if (newHangout.getLocation() != null) {
                newPointer.setLocationName(newHangout.getLocation().getName());
            }
            
            // Set GSI1PK for the pointer (required for EntityTimeIndex)
            newPointer.setGsi1pk(InviterKeyFactory.getGroupPk(groupId));
            
            newPointers.add(newPointer);
        }
        
        // 3. Persistence
        try {
            seriesTransactionRepository.addPartToExistingSeries(
                seriesId,
                newHangout,
                newPointers
            );
            
            // Update the in-memory series object to include the new hangout ID
            series.getHangoutIds().add(newHangoutId);
            
            logger.info("Successfully added hangout {} to series {}", newHangoutId, seriesId);
            
            // 4. Return Value
            return series;
            
        } catch (Exception e) {
            logger.error("Failed to add hangout to series {}", seriesId, e);
            throw new RepositoryException("Failed to add hangout to series atomically", e);
        }
    }
}