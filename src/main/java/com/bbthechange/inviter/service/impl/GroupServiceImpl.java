package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.service.GroupService;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.repository.UserRepository;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.exception.*;
import com.bbthechange.inviter.service.InviteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import com.bbthechange.inviter.util.PaginatedResult;
import com.bbthechange.inviter.util.GroupFeedPaginationToken;
import com.bbthechange.inviter.util.RepositoryTokenData;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Implementation of GroupService with atomic operations and efficient GSI patterns.
 * Follows multi-step pattern from implementation plan.
 */
@Service
@Transactional
public class GroupServiceImpl implements GroupService {
    
    private static final Logger logger = LoggerFactory.getLogger(GroupServiceImpl.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final GroupRepository groupRepository;
    private final HangoutRepository hangoutRepository;
    private final UserRepository userRepository;
    private final InviteService inviteService;
    
    @Autowired
    public GroupServiceImpl(GroupRepository groupRepository, HangoutRepository hangoutRepository,
                           UserRepository userRepository, InviteService inviteService) {
        this.groupRepository = groupRepository;
        this.hangoutRepository = hangoutRepository;
        this.userRepository = userRepository;
        this.inviteService = inviteService;
    }
    
    @Override
    public GroupDTO createGroup(CreateGroupRequest request, String creatorId) {
        // Input validation
        validateCreateGroupRequest(request);
        
        // Verify creator exists
        userRepository.findById(UUID.fromString(creatorId))
            .orElseThrow(() -> new UserNotFoundException("Creator not found: " + creatorId));
        
        // Create both records for atomic operation
        Group group = new Group(request.getGroupName(), request.isPublic());
        GroupMembership membership = new GroupMembership(
            group.getGroupId(), 
            creatorId, 
            group.getGroupName() // Denormalize for GSI efficiency
        );
        membership.setRole(GroupRole.ADMIN); // Creator is admin
        
        // Atomic creation using TransactWriteItems pattern
        groupRepository.createGroupWithFirstMember(group, membership);
        
        logger.info("Created group {} with creator {}", group.getGroupId(), creatorId);
        return new GroupDTO(group, membership);
    }
    
    @Override
    public GroupDTO getGroup(String groupId, String requestingUserId) {
        // Get group metadata
        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));

        // Get user's membership in this group
        GroupMembership membership = groupRepository.findMembership(groupId, requestingUserId)
            .orElseThrow(() -> new UnauthorizedException("User not in group"));

        return new GroupDTO(group, membership);
    }

    @Override
    public GroupDTO updateGroup(String groupId, UpdateGroupRequest request, String requestingUserId) {
        // Verify user is a member of the group
        GroupMembership membership = groupRepository.findMembership(groupId, requestingUserId)
            .orElseThrow(() -> new UnauthorizedException("User not in group"));

        // Get existing group
        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));

        // Track if name changed for denormalization update
        boolean nameChanged = false;
        String newGroupName = null;

        // Update only provided fields
        boolean updated = false;
        if (request.getGroupName() != null) {
            String oldName = group.getGroupName();
            newGroupName = request.getGroupName();
            group.setGroupName(newGroupName);
            nameChanged = !oldName.equals(newGroupName);
            updated = true;
        }
        if (request.isPublic() != null) {
            group.setPublic(request.isPublic());
            updated = true;
        }

        if (!updated) {
            throw new ValidationException("No valid fields provided for update");
        }

        // Save updated group
        Group savedGroup = groupRepository.save(group);

        // Update denormalized group names in membership records if name changed
        if (nameChanged) {
            groupRepository.updateMembershipGroupNames(groupId, newGroupName);
            logger.info("Updated group {} name to '{}' and synchronized membership records", groupId, newGroupName);
        } else {
            logger.info("Updated group {} by user {}", groupId, requestingUserId);
        }

        // Update the membership object we're returning to reflect the new name
        if (nameChanged) {
            membership.setGroupName(newGroupName);
        }

        return new GroupDTO(savedGroup, membership);
    }

    @Override
    public void deleteGroup(String groupId, String requestingUserId) {
        // Verify group exists and user is admin
        GroupMembership membership = groupRepository.findMembership(groupId, requestingUserId)
            .orElseThrow(() -> new UnauthorizedException("User not in group"));
        
        if (!membership.isAdmin()) {
            throw new UnauthorizedException("Only admins can delete groups");
        }
        
        // Repository handles complete deletion: group metadata, all members, and hangout pointers
        groupRepository.delete(groupId);
        logger.info("Deleted group {} by user {}", groupId, requestingUserId);
    }
    
    @Override
    public void addMember(String groupId, String userId, String phoneNumber, String addedBy) {
        // Verify group exists
        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));
        
        // For private groups, only group members can add members
        if (!group.isPublic() && !groupRepository.isUserMemberOfGroup(groupId, addedBy)) {
            throw  new UnauthorizedException("User requesting add to group not in group");
        }

        if ((userId == null && phoneNumber == null) || (userId != null && phoneNumber != null)) {
            throw new IllegalArgumentException("You must provide exactly one of: userId or phoneNumber");
        }

        if (phoneNumber != null) {
            userId = inviteService.findOrCreateUserByPhoneNumber(phoneNumber).getId().toString();
        } else if (userRepository.findById(UUID.fromString(userId)).isEmpty()) {
            throw new UserNotFoundException("Cannot add user to group, user not found: " + userId);
        }


        // Check if user is already a member
        if (groupRepository.isUserMemberOfGroup(groupId, userId)) {
            throw new ValidationException("User is already a member of this group");
        }
        
        // Create membership record
        GroupMembership membership = new GroupMembership(groupId, userId, group.getGroupName());
        membership.setRole(GroupRole.MEMBER); // Default role
        
        groupRepository.addMember(membership);
        logger.info("Added member {} to group {} by {}", userId, groupId, addedBy);
    }
    
    @Override
    public void removeMember(String groupId, String userId, String removedBy) {
        // Verify membership exists
        GroupMembership membership = groupRepository.findMembership(groupId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not in group"));

        // Users can remove themselves, or admins can remove others
        if (!userId.equals(removedBy)) {
            groupRepository.findMembership(groupId, removedBy)
                .orElseThrow(() -> new UnauthorizedException("User not in group"));
        }

        groupRepository.removeMember(groupId, userId);
        logger.info("Removed member {} from group {} by {}", userId, groupId, removedBy);
    }

    @Override
    public void leaveGroup(String groupId, String userId) {
        // Verify user is a member of the group
        groupRepository.findMembership(groupId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not in group"));

        // Check if this user is the last member in the group
        List<GroupMembership> allMembers = groupRepository.findMembersByGroupId(groupId);

        if (allMembers.size() <= 1) {
            // Last member leaving - delete the entire group to clean up orphaned data
            groupRepository.delete(groupId);
            logger.info("User {} left group {} as the last member - group deleted", userId, groupId);
        } else {
            // Normal leave - just remove the user
            groupRepository.removeMember(groupId, userId);
            logger.info("User {} left group {}", userId, groupId);
        }
    }

    @Override
    public List<GroupMemberDTO> getGroupMembers(String groupId, String requestingUserId) {
        // Verify user is in group
        if (!isUserInGroup(requestingUserId, groupId)) {
            throw new UnauthorizedException("User not in group");
        }
        
        // Get all memberships for this group
        List<GroupMembership> memberships = groupRepository.findMembersByGroupId(groupId);
        
        // Convert to DTOs (need to get user names)
        return memberships.stream()
            .map(membership -> {
                // Get user details for display name
                User user = userRepository.findById(UUID.fromString(membership.getUserId()))
                    .orElse(null);
                String userName = user != null ? user.getDisplayName() : "Unknown User";
                
                return new GroupMemberDTO(membership, userName);
            })
            .collect(Collectors.toList());
    }
    
    @Override
    public List<GroupDTO> getUserGroups(String userId) {
        // Single GSI query gets everything we need - no N+1 queries!
        List<GroupMembership> memberships = groupRepository.findGroupsByUserId(userId);
        
        // No additional queries needed! groupName is denormalized on membership records
        return memberships.stream()
            .map(membership -> new GroupDTO(
                membership.getGroupId(),
                membership.getGroupName(), // Already available - no lookup needed!
                membership.getRole(),
                membership.getCreatedAt()
            ))
            .collect(Collectors.toList());
    }
    
    @Override
    public boolean isUserInGroup(String userId, String groupId) {
        return groupRepository.findMembership(groupId, userId).isPresent();
    }
    
    @Override
    public GroupFeedDTO getGroupFeed(String groupId, String requestingUserId, Integer limit, 
                                    String startingAfter, String endingBefore) {
        // Authorization check
        if (!isUserInGroup(requestingUserId, groupId)) {
            throw new UnauthorizedException("User not in group");
        }
        
        long nowTimestamp = System.currentTimeMillis() / 1000; // Unix timestamp in seconds
        
        // Determine query type based on pagination parameters
        if (endingBefore != null) {
            // Backward pagination - get past events
            return getPastEvents(groupId, nowTimestamp, limit, endingBefore);
        } else {
            // Default or forward pagination - get current/future events
            return getCurrentAndFutureEvents(groupId, nowTimestamp, limit, startingAfter);
        }
    }
    
    /**
     * Get current and future events using parallel queries for optimal performance.
     * Now includes hydration logic to support both standalone hangouts and multi-part series.
     */
    private GroupFeedDTO getCurrentAndFutureEvents(String groupId, long nowTimestamp, 
                                                  Integer limit, String startingAfter) {
        try {
            // Parallel queries for maximum efficiency
            CompletableFuture<PaginatedResult<BaseItem>> futureEventsFuture = 
                CompletableFuture.supplyAsync(() -> 
                    hangoutRepository.getFutureEventsPage(groupId, nowTimestamp, limit, startingAfter));
                    
            CompletableFuture<PaginatedResult<BaseItem>> inProgressEventsFuture = 
                CompletableFuture.supplyAsync(() -> 
                    hangoutRepository.getInProgressEventsPage(groupId, nowTimestamp, limit, startingAfter));
            
            // Wait for both queries to complete
            PaginatedResult<BaseItem> futureEvents = futureEventsFuture.get();
            PaginatedResult<BaseItem> inProgressEvents = inProgressEventsFuture.get();
            
            // Merge results
            List<BaseItem> allItems = new ArrayList<>();
            allItems.addAll(futureEvents.getResults());
            allItems.addAll(inProgressEvents.getResults());
            
            // Hydrate the mixed feed into FeedItem objects
            List<FeedItem> allFeedItems = hydrateFeed(allItems);
            
            // Split into withDay (scheduled) and needsDay (unscheduled)
            List<FeedItem> feedItems = new ArrayList<>();
            List<HangoutSummaryDTO> needsDay = new ArrayList<>();
            
            for (FeedItem item : allFeedItems) {
                if (item instanceof HangoutSummaryDTO hangoutSummary) {
                    // Check if hangout has a timestamp (is scheduled)
                    BaseItem baseItem = allItems.stream()
                        .filter(bi -> bi instanceof HangoutPointer hp && hp.getHangoutId().equals(hangoutSummary.getHangoutId()))
                        .findFirst()
                        .orElse(null);
                    
                    if (baseItem instanceof HangoutPointer hangoutPointer && hangoutPointer.getStartTimestamp() != null) {
                        feedItems.add(item); // Has timestamp, goes to withDay
                    } else {
                        needsDay.add(hangoutSummary); // No timestamp, goes to needsDay
                    }
                } else {
                    feedItems.add(item); // Non-hangout items go to withDay
                }
            }
            
            // Repository is the source of truth for pagination tokens
            String nextPageToken = null;
            if (futureEvents.hasMore()) {
                // Convert repository token to custom format for API response
                nextPageToken = getCustomToken(futureEvents.getNextToken(), true);
            }
            
            // Always provide a token to access past events when viewing current/future events
            String previousPageToken = generatePreviousPageTokenFromBaseItems(allItems, nowTimestamp);
            
            return new GroupFeedDTO(groupId, feedItems, needsDay, nextPageToken, previousPageToken);
            
        } catch (Exception e) {
            logger.error("Failed to get current and future events for group {}", groupId, e);
            throw new RepositoryException("Failed to retrieve group feed", e);
        }
    }
    
    /**
     * Get past events for backward pagination.
     * Now supports both standalone hangouts and multi-part series.
     */
    private GroupFeedDTO getPastEvents(String groupId, long nowTimestamp, Integer limit, String endingBefore) {
        try {
            // Convert our custom pagination token to the format the repository expects
            String repositoryToken = getRepositoryToken(endingBefore, groupId);
            
            PaginatedResult<BaseItem> pastEvents = 
                hangoutRepository.getPastEventsPage(groupId, nowTimestamp, limit, repositoryToken);
                
            // Hydrate the mixed feed into FeedItem objects
            List<FeedItem> feedItems = hydrateFeed(pastEvents.getResults());
            
            // For past events, needsDay is empty (past events must have timestamps)
            List<HangoutSummaryDTO> needsDay = List.of();
            
            // Generate pagination tokens for past events
            String nextPageToken = null; // No next page for past events (would go back to current/future)
            String previousPageToken = null;
            
            // Use repository's native token if available (more items available)
            if (pastEvents.getNextToken() != null) {
                // Convert repository token back to our custom format
                previousPageToken = getCustomToken(pastEvents.getNextToken(), false);
            }
            
            return new GroupFeedDTO(groupId, feedItems, needsDay, nextPageToken, previousPageToken);
            
        } catch (Exception e) {
            logger.error("Failed to get past events for group {}", groupId, e);
            throw new RepositoryException("Failed to retrieve past events", e);
        }
    }
    
    /**
     * Generate previous page token for backward pagination.
     */
    private String generatePreviousPageToken(List<HangoutPointer> events, long nowTimestamp) {
        // Always provide a token to access past events when viewing current/future events
        // Use the current timestamp as the boundary for past events
        GroupFeedPaginationToken token = new GroupFeedPaginationToken(
            null, // No specific event ID needed for initial past events query
            nowTimestamp, 
            false // isForward = false (backward pagination)
        );
        return token.encode();
    }
    
    /**
     * Convert custom pagination token to repository format using safe JSON handling.
     */
    private String getRepositoryToken(String customToken, String groupId) {
        if (customToken == null) {
            return null;
        }
        
        try {
            GroupFeedPaginationToken token = GroupFeedPaginationToken.decode(customToken);
            
            // If no specific event ID, this is the first query - no repository token needed
            if (token.getLastEventId() == null) {
                return null;
            }
            
            // Use Jackson for safe JSON serialization
            RepositoryTokenData tokenData = new RepositoryTokenData(
                "GROUP#" + groupId,
                String.valueOf(token.getLastTimestamp()),
                "GROUP#" + groupId,
                "HANGOUT#" + token.getLastEventId()
            );
            
            String json = objectMapper.writeValueAsString(tokenData);
            return java.util.Base64.getEncoder().encodeToString(json.getBytes());
            
        } catch (Exception e) {
            logger.warn("Failed to convert custom token to repository token: {}", customToken, e);
            return null;
        }
    }
    
    /**
     * Convert repository token back to custom format using safe JSON handling.
     */
    private String getCustomToken(String repositoryToken, boolean isForward) {
        if (repositoryToken == null) {
            return null;
        }
        
        try {
            // Decode the repository token and parse with Jackson
            String decoded = new String(java.util.Base64.getDecoder().decode(repositoryToken));
            RepositoryTokenData tokenData = objectMapper.readValue(decoded, RepositoryTokenData.class);
            
            // Extract event ID and timestamp from the structured data
            String eventId = tokenData.getSk().replace("HANGOUT#", "");
            long timestamp = Long.parseLong(tokenData.getStartTimestamp());
            
            GroupFeedPaginationToken customToken = new GroupFeedPaginationToken(eventId, timestamp, isForward);
            return customToken.encode();
            
        } catch (Exception e) {
            logger.warn("Failed to convert repository token to custom token: {}", repositoryToken, e);
            return null;
        }
    }
    
    private void validateCreateGroupRequest(CreateGroupRequest request) {
        if (request.getGroupName() == null || request.getGroupName().trim().isEmpty()) {
            throw new ValidationException("Group name is required");
        }
        if (request.getGroupName().length() > 100) {
            throw new ValidationException("Group name must be 100 characters or less");
        }
        if (request.isPublic() == null) {
            throw new ValidationException("Public setting is required");
        }
    }
    
    /**
     * Hydrate a mixed list of BaseItem objects into structured FeedItem objects.
     * Implements the two-pass algorithm to transform SeriesPointers and standalone HangoutPointers
     * into the appropriate DTOs for the feed response.
     */
    List<FeedItem> hydrateFeed(List<BaseItem> baseItems) {
        // First Pass: Identify all hangouts that are part of series
        Set<String> hangoutIdsInSeries = new HashSet<>();
        for (BaseItem item : baseItems) {
            if (item instanceof SeriesPointer) {
                SeriesPointer seriesPointer = (SeriesPointer) item;
                // Add all hangout IDs from the series' denormalized parts list
                if (seriesPointer.getParts() != null) {
                    for (HangoutPointer part : seriesPointer.getParts()) {
                        hangoutIdsInSeries.add(part.getHangoutId());
                    }
                }
            }
        }
        
        // Second Pass: Build the final feed list
        List<FeedItem> feedItems = new ArrayList<>();
        for (BaseItem item : baseItems) {
            if (item instanceof SeriesPointer) {
                // Convert SeriesPointer to SeriesSummaryDTO
                SeriesPointer seriesPointer = (SeriesPointer) item;
                SeriesSummaryDTO seriesDTO = createSeriesSummaryDTO(seriesPointer);
                feedItems.add(seriesDTO);
                
            } else if (item instanceof HangoutPointer) {
                HangoutPointer hangoutPointer = (HangoutPointer) item;
                
                // Only include standalone hangouts (not already part of a series)
                if (!hangoutIdsInSeries.contains(hangoutPointer.getHangoutId())) {
                    HangoutSummaryDTO hangoutDTO = new HangoutSummaryDTO(hangoutPointer);
                    feedItems.add(hangoutDTO);
                }
                // If it's part of a series, ignore it (already included in SeriesSummaryDTO)
            }
        }
        
        // The list is already sorted by the database query, so no additional sorting needed
        return feedItems;
    }
    
    /**
     * Create a SeriesSummaryDTO from a SeriesPointer with all its denormalized parts.
     */
    SeriesSummaryDTO createSeriesSummaryDTO(SeriesPointer seriesPointer) {
        SeriesSummaryDTO dto = new SeriesSummaryDTO();
        
        // Copy series-level information
        dto.setSeriesId(seriesPointer.getSeriesId());
        dto.setSeriesTitle(seriesPointer.getSeriesTitle());
        dto.setSeriesDescription(seriesPointer.getSeriesDescription());
        dto.setPrimaryEventId(seriesPointer.getPrimaryEventId());
        dto.setStartTimestamp(seriesPointer.getStartTimestamp());
        dto.setEndTimestamp(seriesPointer.getEndTimestamp());
        
        // Convert denormalized parts to HangoutSummaryDTO objects
        List<HangoutSummaryDTO> parts = new ArrayList<>();
        if (seriesPointer.getParts() != null) {
            for (HangoutPointer part : seriesPointer.getParts()) {
                HangoutSummaryDTO partDTO = new HangoutSummaryDTO(part);
                parts.add(partDTO);
            }
        }
        dto.setParts(parts);
        
        return dto;
    }
    
    /**
     * Generate previous page token for backward pagination using BaseItem list.
     * Updated version of generatePreviousPageToken to work with BaseItem instead of HangoutPointer.
     */
    private String generatePreviousPageTokenFromBaseItems(List<BaseItem> items, long nowTimestamp) {
        // Always provide a token to access past events when viewing current/future events
        // Use the current timestamp as the boundary for past events
        GroupFeedPaginationToken token = new GroupFeedPaginationToken(
            null, // No specific event ID needed for initial past events query
            nowTimestamp, 
            false // This is for past events (backward direction)
        );
        
        try {
            return objectMapper.writeValueAsString(token);
        } catch (Exception e) {
            logger.warn("Failed to create previous page token, returning null", e);
            return null;
        }
    }
}