package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.model.EventSeries;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.model.SeriesPointer;
import com.bbthechange.inviter.service.EventSeriesService;
import com.bbthechange.inviter.dto.CreateHangoutRequest;
import com.bbthechange.inviter.dto.EventSeriesDetailDTO;
import com.bbthechange.inviter.dto.HangoutDetailDTO;
import com.bbthechange.inviter.dto.UpdateSeriesRequest;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.repository.EventSeriesRepository;
import com.bbthechange.inviter.repository.SeriesTransactionRepository;
import com.bbthechange.inviter.repository.UserRepository;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.exception.ResourceNotFoundException;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.exception.RepositoryException;
import com.bbthechange.inviter.exception.ValidationException;
import com.bbthechange.inviter.service.HangoutService;
import com.bbthechange.inviter.service.FuzzyTimeService;
import com.bbthechange.inviter.service.GroupTimestampService;
import com.bbthechange.inviter.util.InviterKeyFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
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
    private final GroupRepository groupRepository;
    private final FuzzyTimeService fuzzyTimeService;
    private final GroupTimestampService groupTimestampService;
    
    @Autowired
    public EventSeriesServiceImpl(
            HangoutRepository hangoutRepository,
            EventSeriesRepository eventSeriesRepository,
            SeriesTransactionRepository seriesTransactionRepository,
            UserRepository userRepository,
            HangoutService hangoutService,
            GroupRepository groupRepository,
            FuzzyTimeService fuzzyTimeService,
            GroupTimestampService groupTimestampService) {
        this.hangoutRepository = hangoutRepository;
        this.eventSeriesRepository = eventSeriesRepository;
        this.seriesTransactionRepository = seriesTransactionRepository;
        this.userRepository = userRepository;
        this.hangoutService = hangoutService;
        this.groupRepository = groupRepository;
        this.fuzzyTimeService = fuzzyTimeService;
        this.groupTimestampService = groupTimestampService;
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
        newSeries.setSeriesTitle(existingHangout.getTitle());
        newSeries.setPrimaryEventId(existingHangoutId);
        newSeries.setMainImagePath(existingHangout.getMainImagePath()); // Copy image from primary hangout
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
        
        // Calculate endTimestamp for the series (latest of all start/end timestamps)
        List<Hangout> allHangouts = Arrays.asList(existingHangout, newHangout);
        Long latestTimestamp = calculateLatestTimestamp(allHangouts);
        if (latestTimestamp != null) {
            newSeries.setEndTimestamp(latestTimestamp);
        }
        
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
            newPointer.setMainImagePath(newHangout.getMainImagePath()); // Denormalize image path
            // Copy timestamps from hangout if available
            if (newHangout.getStartTimestamp() != null) {
                newPointer.setStartTimestamp(newHangout.getStartTimestamp());
                newPointer.setEndTimestamp(newHangout.getEndTimestamp());
            }
            if (newHangout.getLocation() != null) {
                newPointer.setLocation(newHangout.getLocation());
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
        
        // Create SeriesPointers for all groups that this series is associated with
        // Groups are determined from the existing hangout's pointers (which represent all associated groups)
        List<SeriesPointer> seriesPointers = new ArrayList<>();
        for (HangoutPointer existingPointer : existingPointers) {
            SeriesPointer seriesPointer = SeriesPointer.fromEventSeries(newSeries, existingPointer.getGroupId());
            
            // Create combined list of all HangoutPointers for this group (existing + new)
            List<HangoutPointer> allPartsForGroup = new ArrayList<>();
            
            // Add existing pointers for this group
            for (HangoutPointer existing : existingPointers) {
                if (existing.getGroupId().equals(existingPointer.getGroupId())) {
                    allPartsForGroup.add(existing);
                }
            }
            
            // Add new pointers for this group  
            for (HangoutPointer newPointer : newPointers) {
                if (newPointer.getGroupId().equals(existingPointer.getGroupId())) {
                    allPartsForGroup.add(newPointer);
                }
            }
            
            // Set the parts field on the SeriesPointer
            seriesPointer.setParts(allPartsForGroup);
            seriesPointers.add(seriesPointer);
        }
        
        // 3. Persistence (The Single Transactional Call)
        try {
            seriesTransactionRepository.createSeriesWithNewPart(
                newSeries,
                existingHangout,
                existingPointers,
                newHangout,
                newPointers,
                seriesPointers
            );
            
            logger.info("Successfully created series {} from hangout {}", seriesId, existingHangoutId);

            // 4. Update group timestamps for ETag invalidation
            List<String> affectedGroups = existingPointers.stream()
                .map(HangoutPointer::getGroupId)
                .distinct()
                .toList();
            groupTimestampService.updateGroupTimestamps(affectedGroups);

            // 5. Return Value
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
        // Create new Hangout using HangoutService (includes timestamp conversion)
        Hangout newHangout = hangoutService.hangoutFromHangoutRequest(newMemberRequest, userId);
        newHangout.setSeriesId(seriesId);
        
        // Set the proper DynamoDB keys
        newHangout.setPk(InviterKeyFactory.getEventPk(newHangout.getHangoutId()));
        newHangout.setSk(InviterKeyFactory.getMetadataSk());
        
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
            newPointer.setMainImagePath(newHangout.getMainImagePath()); // Denormalize image path
            // Copy timestamps from hangout if available
            if (newHangout.getStartTimestamp() != null) {
                newPointer.setStartTimestamp(newHangout.getStartTimestamp());
                newPointer.setEndTimestamp(newHangout.getEndTimestamp());
            }
            if (newHangout.getLocation() != null) {
                newPointer.setLocation(newHangout.getLocation());
            }
            
            // Set GSI1PK for the pointer (required for EntityTimeIndex)
            newPointer.setGsi1pk(InviterKeyFactory.getGroupPk(groupId));
            
            newPointers.add(newPointer);
        }
        
        // Create updated SeriesPointers for all groups
        // We need to update all SeriesPointers to include the new hangout ID
        // Since SeriesPointers are denormalized, we create updated versions with the new hangout added
        series.getHangoutIds().add(newHangout.getHangoutId()); // Add to series first so SeriesPointer gets updated list
        
        // Update series endTimestamp based on all hangouts in the series
        List<Hangout> allHangouts = new ArrayList<>();
        for (String hangoutId : series.getHangoutIds()) {
            hangoutRepository.findHangoutById(hangoutId).ifPresent(allHangouts::add);
        }
        allHangouts.add(newHangout);
        Long latestTimestamp = calculateLatestTimestamp(allHangouts);
        if (latestTimestamp != null) {
            series.setEndTimestamp(latestTimestamp);
        }
        
        List<SeriesPointer> updatedSeriesPointers = new ArrayList<>();
        for (String groupId : newHangout.getAssociatedGroups()) {
            SeriesPointer updatedPointer = SeriesPointer.fromEventSeries(series, groupId);
            
            // Get all existing hangouts in this series for this group
            List<HangoutPointer> allPartsForGroup = new ArrayList<>();
            
            // Add all existing hangouts in the series for this group
            for (String hangoutId : series.getHangoutIds()) {
                if (!hangoutId.equals(newHangout.getHangoutId())) { // Skip the new one, we'll add it separately
                    Optional<Hangout> existingHangoutOpt = hangoutRepository.findHangoutById(hangoutId);
                    if (existingHangoutOpt.isPresent()) {
                        List<HangoutPointer> existingPointers = hangoutRepository.findPointersForHangout(existingHangoutOpt.get());
                        for (HangoutPointer existingPointer : existingPointers) {
                            if (existingPointer.getGroupId().equals(groupId)) {
                                allPartsForGroup.add(existingPointer);
                            }
                        }
                    }
                }
            }
            
            // Add the new hangout pointers for this group
            for (HangoutPointer newPointer : newPointers) {
                if (newPointer.getGroupId().equals(groupId)) {
                    allPartsForGroup.add(newPointer);
                }
            }
            
            // Set the parts field on the SeriesPointer
            updatedPointer.setParts(allPartsForGroup);
            updatedSeriesPointers.add(updatedPointer);
        }
        
        // 3. Persistence
        try {
            seriesTransactionRepository.addPartToExistingSeries(
                seriesId,
                newHangout,
                newPointers,
                updatedSeriesPointers
            );
            
            logger.info("Successfully added hangout {} to series {}", newHangout.getHangoutId(), seriesId);

            // 4. Update group timestamps for ETag invalidation
            groupTimestampService.updateGroupTimestamps(newHangout.getAssociatedGroups());

            // 5. Return Value
            return series;

        } catch (Exception e) {
            logger.error("Failed to add hangout to series {}", seriesId, e);
            throw new RepositoryException("Failed to add hangout to series atomically", e);
        }
    }

    @Override
    public void unlinkHangoutFromSeries(String seriesId, String hangoutId, String userId) {
        logger.info("Unlinking hangout {} from series {} by user {}", hangoutId, seriesId, userId);
        
        // 1. Validation
        Optional<EventSeries> seriesOpt = eventSeriesRepository.findById(seriesId);
        if (seriesOpt.isEmpty()) {
            throw new ResourceNotFoundException("EventSeries not found: " + seriesId);
        }
        
        Optional<Hangout> hangoutOpt = hangoutRepository.findHangoutById(hangoutId);
        if (hangoutOpt.isEmpty()) {
            throw new ResourceNotFoundException("Hangout not found: " + hangoutId);
        }
        
        EventSeries series = seriesOpt.get();
        Hangout hangout = hangoutOpt.get();
        
        // Verify hangout is actually part of this series
        if (!hangout.getSeriesId().equals(seriesId)) {
            throw new ValidationException("Hangout " + hangoutId + " is not part of series " + seriesId);
        }
        
        // TODO: Implement proper authorization logic
        if (userRepository.findById(userId).isEmpty()) {
            throw new UnauthorizedException("User " + userId + " not found");
        }
        
        // 2. Data Preparation
        // Remove hangout from series
        series.getHangoutIds().remove(hangoutId);
        series.incrementVersion();
        
        // Clear series ID from hangout
        hangout.setSeriesId(null);
        
        // Update all pointers for this hangout to clear series ID
        List<HangoutPointer> hangoutPointers = hangoutRepository.findPointersForHangout(hangout);
        for (HangoutPointer pointer : hangoutPointers) {
            pointer.setSeriesId(null);
        }
        
        // Check if this was the last hangout in the series
        boolean isLastHangout = series.getHangoutIds().isEmpty();
        
        // 3. Persistence
        try {
            if (isLastHangout) {
                // Delete the entire series since it has no more hangouts
                seriesTransactionRepository.deleteEntireSeries(series, hangout, hangoutPointers);
                logger.info("Deleted series {} as hangout {} was the last part", seriesId, hangoutId);
            } else {
                // Update series and unlink hangout
                List<SeriesPointer> updatedSeriesPointers = createUpdatedSeriesPointers(series);
                seriesTransactionRepository.unlinkHangoutFromSeries(
                    series, hangout, hangoutPointers, updatedSeriesPointers);
                logger.info("Successfully unlinked hangout {} from series {}", hangoutId, seriesId);
            }

            // Update group timestamps for ETag invalidation
            List<String> affectedGroups = hangoutPointers.stream()
                .map(HangoutPointer::getGroupId)
                .distinct()
                .toList();
            groupTimestampService.updateGroupTimestamps(affectedGroups);
        } catch (Exception e) {
            logger.error("Failed to unlink hangout {} from series {}", hangoutId, seriesId, e);
            throw new RepositoryException("Failed to unlink hangout from series atomically", e);
        }
    }

    @Override
    public void updateSeriesAfterHangoutModification(String hangoutId) {
        logger.info("Updating series after modification of hangout {}", hangoutId);
        
        // 1. Get hangout and its series
        Optional<Hangout> hangoutOpt = hangoutRepository.findHangoutById(hangoutId);
        if (hangoutOpt.isEmpty()) {
            throw new ResourceNotFoundException("Hangout not found: " + hangoutId);
        }
        
        Hangout hangout = hangoutOpt.get();
        String seriesId = hangout.getSeriesId();
        
        if (seriesId == null) {
            // Hangout is not part of a series, nothing to update
            logger.debug("Hangout {} is not part of a series, skipping series update", hangoutId);
            return;
        }
        
        Optional<EventSeries> seriesOpt = eventSeriesRepository.findById(seriesId);
        if (seriesOpt.isEmpty()) {
            logger.warn("Series {} not found for hangout {}, skipping series update", seriesId, hangoutId);
            return;
        }
        
        EventSeries series = seriesOpt.get();
        
        // 2. Update series timestamps if needed
        updateSeriesTimestamps(series);
        
        // 3. Update SeriesPointers with new hangout data
        List<SeriesPointer> updatedSeriesPointers = createUpdatedSeriesPointers(series);
        
        // 4. Persistence
        try {
            seriesTransactionRepository.updateSeriesAfterHangoutChange(series, updatedSeriesPointers);
            logger.info("Successfully updated series {} after hangout {} modification", seriesId, hangoutId);

            // Update group timestamps for ETag invalidation
            if (series.getGroupId() != null) {
                groupTimestampService.updateGroupTimestamps(List.of(series.getGroupId()));
            }
        } catch (Exception e) {
            logger.error("Failed to update series {} after hangout {} modification", seriesId, hangoutId, e);
            throw new RepositoryException("Failed to update series after hangout modification", e);
        }
    }

    @Override
    public void removeHangoutFromSeries(String hangoutId) {
        logger.info("Removing hangout {} from its series", hangoutId);
        
        // 1. Get hangout and its series
        Optional<Hangout> hangoutOpt = hangoutRepository.findHangoutById(hangoutId);
        if (hangoutOpt.isEmpty()) {
            throw new ResourceNotFoundException("Hangout not found: " + hangoutId);
        }
        
        Hangout hangout = hangoutOpt.get();
        String seriesId = hangout.getSeriesId();
        
        if (seriesId == null) {
            // Hangout is not part of a series, use standard deletion
            deleteStandaloneHangout(hangout);
            return;
        }
        
        Optional<EventSeries> seriesOpt = eventSeriesRepository.findById(seriesId);
        if (seriesOpt.isEmpty()) {
            logger.warn("Series {} not found for hangout {}, deleting as standalone", seriesId, hangoutId);
            deleteStandaloneHangout(hangout);
            return;
        }
        
        EventSeries series = seriesOpt.get();
        
        // 2. Data Preparation
        // Remove hangout from series
        series.getHangoutIds().remove(hangoutId);
        series.incrementVersion();
        
        // Get all pointers for this hangout
        List<HangoutPointer> hangoutPointers = hangoutRepository.findPointersForHangout(hangout);
        
        // Check if this was the last hangout in the series
        boolean isLastHangout = series.getHangoutIds().isEmpty();
        
        // 3. Persistence
        try {
            if (isLastHangout) {
                // Delete the entire series and the hangout
                seriesTransactionRepository.deleteSeriesAndFinalHangout(series, hangout, hangoutPointers);
                logger.info("Deleted series {} and final hangout {}", seriesId, hangoutId);
            } else {
                // Remove hangout and update series
                List<SeriesPointer> updatedSeriesPointers = createUpdatedSeriesPointers(series);
                seriesTransactionRepository.removeHangoutFromSeries(
                    series, hangout, hangoutPointers, updatedSeriesPointers);
                logger.info("Successfully removed hangout {} from series {}", hangoutId, seriesId);
            }

            // Update group timestamps for ETag invalidation
            List<String> affectedGroups = hangoutPointers.stream()
                .map(HangoutPointer::getGroupId)
                .distinct()
                .toList();
            groupTimestampService.updateGroupTimestamps(affectedGroups);
        } catch (Exception e) {
            logger.error("Failed to remove hangout {} from series {}", hangoutId, seriesId, e);
            throw new RepositoryException("Failed to remove hangout from series atomically", e);
        }
    }

    // Helper Methods
    
    private void updateSeriesTimestamps(EventSeries series) {
        // Recalculate series start and end timestamps based on all hangouts
        Long earliestStart = null;
        Long latestEnd = null;
        
        for (String hangoutId : series.getHangoutIds()) {
            Optional<Hangout> hangoutOpt = hangoutRepository.findHangoutById(hangoutId);
            if (hangoutOpt.isPresent()) {
                Hangout hangout = hangoutOpt.get();
                if (hangout.getStartTimestamp() != null) {
                    if (earliestStart == null || hangout.getStartTimestamp() < earliestStart) {
                        earliestStart = hangout.getStartTimestamp();
                    }
                }
                if (hangout.getEndTimestamp() != null) {
                    if (latestEnd == null || hangout.getEndTimestamp() > latestEnd) {
                        latestEnd = hangout.getEndTimestamp();
                    }
                }
            }
        }
        
        series.setStartTimestamp(earliestStart);
        series.setEndTimestamp(latestEnd);
        series.incrementVersion();
    }
    
    private List<SeriesPointer> createUpdatedSeriesPointers(EventSeries series) {
        // Create updated SeriesPointers for all groups associated with the series
        List<SeriesPointer> updatedPointers = new ArrayList<>();
        
        // Get the primary group from the series
        String primaryGroupId = series.getGroupId();
        if (primaryGroupId != null) {
            SeriesPointer primaryPointer = SeriesPointer.fromEventSeries(series, primaryGroupId);
            
            // Populate the parts field with all HangoutPointers for this group
            List<HangoutPointer> allPartsForGroup = new ArrayList<>();
            for (String hangoutId : series.getHangoutIds()) {
                Optional<Hangout> hangoutOpt = hangoutRepository.findHangoutById(hangoutId);
                if (hangoutOpt.isPresent()) {
                    List<HangoutPointer> hangoutPointers = hangoutRepository.findPointersForHangout(hangoutOpt.get());
                    for (HangoutPointer pointer : hangoutPointers) {
                        if (pointer.getGroupId().equals(primaryGroupId)) {
                            allPartsForGroup.add(pointer);
                        }
                    }
                }
            }
            primaryPointer.setParts(allPartsForGroup);
            updatedPointers.add(primaryPointer);
        }
        
        // Also create pointers for any other groups that might be associated
        // This would need to be expanded based on how groups are managed in series
        
        return updatedPointers;
    }
    
    private void deleteStandaloneHangout(Hangout hangout) {
        // Standard deletion process for hangouts not in a series
        List<HangoutPointer> pointers = hangoutRepository.findPointersForHangout(hangout);
        
        try {
            // Delete pointers first
            for (HangoutPointer pointer : pointers) {
                groupRepository.deleteHangoutPointer(pointer.getGroupId(), hangout.getHangoutId());
            }
            
            // Delete canonical record and all associated data
            hangoutRepository.deleteHangout(hangout.getHangoutId());
            
            logger.info("Deleted standalone hangout {}", hangout.getHangoutId());
        } catch (Exception e) {
            logger.error("Failed to delete standalone hangout {}", hangout.getHangoutId(), e);
            throw new RepositoryException("Failed to delete standalone hangout", e);
        }
    }
    
    @Override
    public EventSeriesDetailDTO getSeriesDetail(String seriesId, String userId) {
        logger.info("Getting detailed view for series {} by user {}", seriesId, userId);
        
        // Validation and Authorization
        // Verify user exists
        if (userRepository.findById(userId).isEmpty()) {
            throw new UnauthorizedException("User " + userId + " not found");
        }
        
        // Fetch the EventSeries
        Optional<EventSeries> seriesOpt = eventSeriesRepository.findById(seriesId);
        if (seriesOpt.isEmpty()) {
            throw new ResourceNotFoundException("EventSeries not found: " + seriesId);
        }
        
        EventSeries series = seriesOpt.get();
        
        // TODO: Implement proper authorization logic based on group membership
        // For now, we'll allow access if the user exists
        
        // Get Hangout details
        List<HangoutDetailDTO> hangoutDetails = new ArrayList<>();
        for (String hangoutId : series.getHangoutIds()) {
            // Get detailed hangout data including polls, cars, etc.
            try {
                HangoutDetailDTO detailDTO = hangoutService.getHangoutDetail(hangoutId, userId);
                hangoutDetails.add(detailDTO);
            } catch (Exception e) {
                logger.warn("Failed to get details for hangout {} in series {}: {}", 
                    hangoutId, seriesId, e.getMessage());
                // Continue with other hangouts even if one fails
            }
        }
        
        // Sort hangouts by start timestamp for consistent ordering
        hangoutDetails.sort((a, b) -> {
            Long timestampA = a.getHangout() != null ? a.getHangout().getStartTimestamp() : null;
            Long timestampB = b.getHangout() != null ? b.getHangout().getStartTimestamp() : null;
            
            if (timestampA == null && timestampB == null) return 0;
            if (timestampA == null) return 1;
            if (timestampB == null) return -1;
            return timestampA.compareTo(timestampB);
        });
        
        // Create and return the detailed DTO
        EventSeriesDetailDTO detailDTO = new EventSeriesDetailDTO(series, hangoutDetails);
        
        logger.info("Successfully retrieved detailed view for series {} with {} hangouts", 
            seriesId, hangoutDetails.size());
        
        return detailDTO;
    }
    
    @Override
    public EventSeries updateSeries(String seriesId, UpdateSeriesRequest updateRequest, String userId) {
        logger.info("Updating series {} by user {}", seriesId, userId);
        
        // Validation and Authorization
        // Verify user exists
        if (userRepository.findById(userId).isEmpty()) {
            throw new UnauthorizedException("User " + userId + " not found");
        }
        
        // Fetch the EventSeries
        Optional<EventSeries> seriesOpt = eventSeriesRepository.findById(seriesId);
        if (seriesOpt.isEmpty()) {
            throw new ResourceNotFoundException("EventSeries not found: " + seriesId);
        }
        
        EventSeries series = seriesOpt.get();
        
        // TODO: Implement proper authorization logic based on group membership
        // For now, we'll allow access if the user exists
        
        // Validate that there are actually updates to apply
        if (!updateRequest.hasUpdates()) {
            logger.debug("No updates provided for series {}", seriesId);
            return series; // Return unchanged series
        }
        
        // Validate primaryEventId if provided
        if (updateRequest.getPrimaryEventId() != null) {
            String requestedPrimaryId = updateRequest.getPrimaryEventId();
            if (!series.containsHangout(requestedPrimaryId)) {
                throw new ValidationException("Primary event ID " + requestedPrimaryId + 
                    " is not a member of series " + seriesId);
            }
        }
        
        // TODO: Implement optimistic locking check
        // For now, we accept the version but don't enforce it
        // if (!updateRequest.getVersion().equals(series.getVersion())) {
        //     throw new OptimisticLockingException("Series version has changed");
        // }
        
        // Apply Updates
        boolean hasChanges = false;
        
        if (updateRequest.getSeriesTitle() != null) {
            series.setSeriesTitle(updateRequest.getSeriesTitle());
            hasChanges = true;
        }
        
        if (updateRequest.getSeriesDescription() != null) {
            series.setSeriesDescription(updateRequest.getSeriesDescription());
            hasChanges = true;
        }
        
        if (updateRequest.getPrimaryEventId() != null) {
            series.setPrimaryEventId(updateRequest.getPrimaryEventId());
            hasChanges = true;
        }

        // External source fields
        if (updateRequest.getExternalId() != null) {
            series.setExternalId(updateRequest.getExternalId());
            hasChanges = true;
        }

        if (updateRequest.getExternalSource() != null) {
            series.setExternalSource(updateRequest.getExternalSource());
            hasChanges = true;
        }

        if (updateRequest.getIsGeneratedTitle() != null) {
            series.setIsGeneratedTitle(updateRequest.getIsGeneratedTitle());
            hasChanges = true;
        }

        if (!hasChanges) {
            logger.debug("No actual changes detected for series {}", seriesId);
            return series;
        }
        
        // Increment version for the update
        series.incrementVersion();
        
        // Persistence
        try {
            // For simple field updates, we don't need transactions since we're not 
            // adding/removing members. We just need to update the series and sync 
            // the SeriesPointer records.
            
            // Save the updated series
            eventSeriesRepository.save(series);
            
            // Update all SeriesPointer records to maintain consistency
            List<SeriesPointer> updatedSeriesPointers = createUpdatedSeriesPointers(series);
            for (SeriesPointer pointer : updatedSeriesPointers) {
                pointer.syncWithEventSeries(series);
                // Save each pointer - this could be optimized with batch operations
                // but for now we'll keep it simple
                groupRepository.saveSeriesPointer(pointer);
            }
            
            logger.info("Successfully updated series {}", seriesId);

            // Update group timestamps for ETag invalidation
            if (series.getGroupId() != null) {
                groupTimestampService.updateGroupTimestamps(List.of(series.getGroupId()));
            }

            return series;

        } catch (Exception e) {
            logger.error("Failed to update series {}", seriesId, e);
            throw new RepositoryException("Failed to update series", e);
        }
    }

    @Override
    public void deleteEntireSeries(String seriesId, String userId) {
        logger.info("Deleting entire series {} by user {}", seriesId, userId);
        
        // 1. Validation and Authorization
        // Verify user exists
        if (userRepository.findById(userId).isEmpty()) {
            throw new UnauthorizedException("User " + userId + " not found");
        }
        
        // Fetch the EventSeries
        Optional<EventSeries> seriesOpt = eventSeriesRepository.findById(seriesId);
        if (seriesOpt.isEmpty()) {
            throw new ResourceNotFoundException("EventSeries not found: " + seriesId);
        }
        
        EventSeries series = seriesOpt.get();
        
        // TODO: Implement proper authorization logic based on group membership
        // For now, we'll allow access if the user exists
        
        // 2. Data Collection - Gather all records that need to be deleted
        List<Hangout> hangoutsToDelete = new ArrayList<>();
        List<HangoutPointer> pointersToDelete = new ArrayList<>();
        
        // Collect all hangouts in the series
        for (String hangoutId : series.getHangoutIds()) {
            Optional<Hangout> hangoutOpt = hangoutRepository.findHangoutById(hangoutId);
            if (hangoutOpt.isPresent()) {
                Hangout hangout = hangoutOpt.get();
                hangoutsToDelete.add(hangout);
                
                // Collect all pointers for this hangout
                List<HangoutPointer> hangoutPointers = hangoutRepository.findPointersForHangout(hangout);
                pointersToDelete.addAll(hangoutPointers);
            }
        }
        
        // Create the single SeriesPointer that needs to be deleted
        // Since each series only has one group associated, there's only one SeriesPointer
        List<SeriesPointer> seriesPointersToDelete = new ArrayList<>();
        String primaryGroupId = series.getGroupId();
        if (primaryGroupId != null) {
            SeriesPointer seriesPointer = SeriesPointer.fromEventSeries(series, primaryGroupId);
            seriesPointersToDelete.add(seriesPointer);
        }
        
        // 3. Persistence - Execute the atomic deletion
        try {
            seriesTransactionRepository.deleteEntireSeriesWithAllHangouts(
                series,
                hangoutsToDelete,
                pointersToDelete,
                seriesPointersToDelete
            );
            
            logger.info("Successfully deleted entire series {} with {} hangouts",
                seriesId, hangoutsToDelete.size());

            // Update group timestamps for ETag invalidation
            List<String> affectedGroups = pointersToDelete.stream()
                .map(HangoutPointer::getGroupId)
                .distinct()
                .toList();
            groupTimestampService.updateGroupTimestamps(affectedGroups);

        } catch (Exception e) {
            logger.error("Failed to delete entire series {}", seriesId, e);
            throw new RepositoryException("Failed to delete entire series atomically", e);
        }
    }

    /**
     * Calculate the latest timestamp from a list of hangouts.
     * This considers both startTimestamp and endTimestamp.
     * If a hangout has no endTimestamp, we use its startTimestamp.
     * 
     * @param hangouts List of hangouts to check
     * @return The latest timestamp found, or null if no timestamps exist
     */
    private Long calculateLatestTimestamp(List<Hangout> hangouts) {
        Long latestTimestamp = null;
        
        for (Hangout hangout : hangouts) {
            Long hangoutLatest = null;
            
            // Use endTimestamp if available, otherwise use startTimestamp
            if (hangout.getEndTimestamp() != null) {
                hangoutLatest = hangout.getEndTimestamp();
            } else if (hangout.getStartTimestamp() != null) {
                hangoutLatest = hangout.getStartTimestamp();
            }
            
            // Update latest if this hangout has a later timestamp
            if (hangoutLatest != null) {
                if (latestTimestamp == null || hangoutLatest > latestTimestamp) {
                    latestTimestamp = hangoutLatest;
                }
            }
        }
        
        return latestTimestamp;
    }
}