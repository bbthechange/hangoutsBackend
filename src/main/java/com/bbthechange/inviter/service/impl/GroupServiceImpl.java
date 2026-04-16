package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.config.ClientInfo;
import com.bbthechange.inviter.service.GroupService;
import com.bbthechange.inviter.service.HangoutService;
import com.bbthechange.inviter.service.S3Service;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.repository.UserRepository;
import com.bbthechange.inviter.repository.InviteCodeRepository;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.exception.*;
import com.bbthechange.inviter.service.ForwardFillSuggestionService;
import com.bbthechange.inviter.service.InviteService;
import com.bbthechange.inviter.service.NotificationService;
import com.bbthechange.inviter.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import com.bbthechange.inviter.util.PaginatedResult;
import com.bbthechange.inviter.util.GroupFeedPaginationToken;
import com.bbthechange.inviter.util.RepositoryTokenData;
import com.bbthechange.inviter.util.InviteCodeGenerator;
import com.bbthechange.inviter.util.HangoutDataTransformer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;

/**
 * Implementation of GroupService with atomic operations and efficient GSI patterns.
 * Follows multi-step pattern from implementation plan.
 */
@Service
@Transactional
public class GroupServiceImpl implements GroupService {
    
    private static final Logger logger = LoggerFactory.getLogger(GroupServiceImpl.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Minimum app version required to see watch party series as grouped SeriesSummaryDTO.
     * Clients with versions < 3.0.0 see watch party episodes as standalone enriched HangoutSummaryDTOs.
     */
    private static final String WATCH_PARTY_MIN_VERSION = "3.0.0";

    /**
     * Minimum app version required for new feed features (idea suggestions, nudges).
     * Old clients can't deserialize idea_suggestion feed items and may choke on nudges.
     */
    private static final String NEW_FEED_FEATURES_MIN_VERSION = "2.0.0";

    private final GroupRepository groupRepository;
    private final HangoutRepository hangoutRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final InviteService inviteService;
    private final NotificationService notificationService;
    private final HangoutService hangoutService;
    private final S3Service s3Service;
    private final InviteCodeRepository inviteCodeRepository;
    private final String appBaseUrl;
    private final FeedSortingService feedSortingService;
    private final ForwardFillSuggestionService forwardFillSuggestionService;
    private final com.bbthechange.inviter.service.AttributeSuggestionService attributeSuggestionService;
    private final com.bbthechange.inviter.service.NudgeService nudgeService;

    @Value("${inviter.attendance.backward-compat-interested:true}")
    private boolean attendanceBackwardCompatEnabled;

    @Autowired
    public GroupServiceImpl(GroupRepository groupRepository, HangoutRepository hangoutRepository,
                           UserRepository userRepository, UserService userService, InviteService inviteService,
                           NotificationService notificationService, @Lazy HangoutService hangoutService,
                           S3Service s3Service, InviteCodeRepository inviteCodeRepository,
                           @Value("${app.base-url}") String appBaseUrl,
                           FeedSortingService feedSortingService,
                           ForwardFillSuggestionService forwardFillSuggestionService,
                           com.bbthechange.inviter.service.AttributeSuggestionService attributeSuggestionService,
                           com.bbthechange.inviter.service.NudgeService nudgeService) {
        this.groupRepository = groupRepository;
        this.hangoutRepository = hangoutRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.inviteService = inviteService;
        this.notificationService = notificationService;
        this.hangoutService = hangoutService;
        this.s3Service = s3Service;
        this.inviteCodeRepository = inviteCodeRepository;
        this.appBaseUrl = appBaseUrl;
        this.feedSortingService = feedSortingService;
        this.forwardFillSuggestionService = forwardFillSuggestionService;
        this.attributeSuggestionService = attributeSuggestionService;
        this.nudgeService = nudgeService;
    }
    
    @Override
    public GroupDTO createGroup(CreateGroupRequest request, String creatorId) {
        // Input validation
        validateCreateGroupRequest(request);
        
        // Verify creator exists and get user info for denormalization
        User creator = userRepository.findById(UUID.fromString(creatorId))
            .orElseThrow(() -> new UserNotFoundException("Creator not found: " + creatorId));

        // Create both records for atomic operation
        Group group = new Group(request.getGroupName(), request.isPublic());
        group.setMainImagePath(request.getMainImagePath());
        group.setBackgroundImagePath(request.getBackgroundImagePath());

        GroupMembership membership = new GroupMembership(
            group.getGroupId(),
            creatorId,
            group.getGroupName() // Denormalize for GSI efficiency
        );
        membership.setRole(GroupRole.ADMIN); // Creator is admin

        // Denormalize group image paths to membership
        membership.setGroupMainImagePath(group.getMainImagePath());
        membership.setGroupBackgroundImagePath(group.getBackgroundImagePath());

        // Denormalize user image path to membership
        membership.setUserMainImagePath(creator.getMainImagePath());
        
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
        boolean imagePathsChanged = false;
        String oldMainImagePath = null;
        String oldBackgroundImagePath = null;

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
        if (request.getMainImagePath() != null &&
            !request.getMainImagePath().equals(group.getMainImagePath())) {
            oldMainImagePath = group.getMainImagePath(); // Store old path for cleanup
            group.setMainImagePath(request.getMainImagePath());
            imagePathsChanged = true;
            updated = true;
        }
        if (request.getBackgroundImagePath() != null &&
            !request.getBackgroundImagePath().equals(group.getBackgroundImagePath())) {
            oldBackgroundImagePath = group.getBackgroundImagePath(); // Store old path for cleanup
            group.setBackgroundImagePath(request.getBackgroundImagePath());
            imagePathsChanged = true;
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

        // Update denormalized group image paths in membership records if changed
        if (imagePathsChanged) {
            groupRepository.updateMembershipGroupImagePaths(groupId, savedGroup.getMainImagePath(), savedGroup.getBackgroundImagePath());
            logger.info("Updated group {} image paths and synchronized membership records", groupId);

            // Delete old images from S3 asynchronously
            if (oldMainImagePath != null) {
                s3Service.deleteImageAsync(oldMainImagePath);
                logger.info("Initiated async deletion of old group main image: {}", oldMainImagePath);
            }
            if (oldBackgroundImagePath != null) {
                s3Service.deleteImageAsync(oldBackgroundImagePath);
                logger.info("Initiated async deletion of old group background image: {}", oldBackgroundImagePath);
            }
        }

        // Update the membership object we're returning to reflect the new values
        if (nameChanged) {
            membership.setGroupName(newGroupName);
        }
        if (imagePathsChanged) {
            membership.setGroupMainImagePath(savedGroup.getMainImagePath());
            membership.setGroupBackgroundImagePath(savedGroup.getBackgroundImagePath());
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

        User userToAdd;
        final String finalUserId;
        if (phoneNumber != null) {
            userToAdd = inviteService.findOrCreateUserByPhoneNumber(phoneNumber);
            finalUserId = userToAdd.getId().toString();
        } else {
            final String userIdForLambda = userId;
            userToAdd = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UserNotFoundException("Cannot add user to group, user not found: " + userIdForLambda));
            finalUserId = userId;
        }
        userId = finalUserId;


        // Check if user is already a member
        if (groupRepository.isUserMemberOfGroup(groupId, userId)) {
            throw new ValidationException("User is already a member of this group");
        }

        // Create membership record
        GroupMembership membership = new GroupMembership(groupId, userId, group.getGroupName());
        membership.setRole(GroupRole.MEMBER); // Default role

        // Denormalize group image paths to membership
        membership.setGroupMainImagePath(group.getMainImagePath());
        membership.setGroupBackgroundImagePath(group.getBackgroundImagePath());

        // Denormalize user image path to membership
        membership.setUserMainImagePath(userToAdd.getMainImagePath());

        groupRepository.addMember(membership);

        // Send notification to the added user
        notificationService.notifyGroupMemberAdded(groupId, group.getGroupName(), finalUserId, addedBy);

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
        
        // Convert to DTOs (need to get current user info)
        return memberships.stream()
            .map(membership -> {
                // Get user details for current display name and profile image
                UserSummaryDTO user = userService.getUserSummary(UUID.fromString(membership.getUserId()))
                    .orElse(null);
                String userName = user != null ? user.getDisplayName() : "Unknown User";
                String mainImagePath = user != null ? user.getMainImagePath() : null;

                return new GroupMemberDTO(membership, userName, mainImagePath);
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
                membership.getCreatedAt(),
                membership.getGroupMainImagePath(),
                membership.getGroupBackgroundImagePath(),
                membership.getUserMainImagePath()
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
        // Delegate to version with null clientInfo (backwards compatibility)
        return getGroupFeed(groupId, requestingUserId, limit, startingAfter, endingBefore, null);
    }

    @Override
    public GroupFeedDTO getGroupFeed(String groupId, String requestingUserId, Integer limit,
                                    String startingAfter, String endingBefore, ClientInfo clientInfo) {
        return getGroupFeed(groupId, requestingUserId, limit, startingAfter, endingBefore, clientInfo, "ALL");
    }

    @Override
    public GroupFeedDTO getGroupFeed(String groupId, String requestingUserId, Integer limit,
                                    String startingAfter, String endingBefore, ClientInfo clientInfo, String filter) {
        // Authorization check
        if (!isUserInGroup(requestingUserId, groupId)) {
            throw new UnauthorizedException("User not in group");
        }

        long nowTimestamp = System.currentTimeMillis() / 1000; // Unix timestamp in seconds

        GroupFeedDTO feed;
        // Determine query type based on pagination parameters
        if (endingBefore != null) {
            // Backward pagination - get past events
            feed = getPastEvents(groupId, nowTimestamp, limit, endingBefore, requestingUserId, clientInfo);
        } else {
            // Default or forward pagination - get current/future events
            feed = getCurrentAndFutureEvents(groupId, nowTimestamp, limit, startingAfter, requestingUserId, clientInfo);
        }

        // Apply momentum filter post-query (filter=EVERYTHING is same as ALL for now)
        if ("CONFIRMED".equalsIgnoreCase(filter)) {
            List<FeedItem> filteredWithDay = feed.getWithDay().stream()
                .filter(item -> {
                    if (item instanceof HangoutSummaryDTO h) {
                        // null momentum = legacy hangout, treated as confirmed
                        return h.getMomentum() == null ||
                               "CONFIRMED".equals(h.getMomentum().getCategory());
                    }
                    return true; // Keep series items
                })
                .collect(Collectors.toList());

            List<HangoutSummaryDTO> filteredNeedsDay = feed.getNeedsDay().stream()
                .filter(h -> h.getMomentum() == null ||
                             "CONFIRMED".equals(h.getMomentum().getCategory()))
                .collect(Collectors.toList());

            return new GroupFeedDTO(groupId, filteredWithDay, filteredNeedsDay,
                    feed.getNextPageToken(), feed.getPreviousPageToken());
        }

        return feed;
    }

    /**
     * Get current and future events using parallel queries for optimal performance.
     * Now includes hydration logic to support both standalone hangouts and multi-part series.
     */
    private GroupFeedDTO getCurrentAndFutureEvents(String groupId, long nowTimestamp,
                                                  Integer limit, String startingAfter, String requestingUserId, ClientInfo clientInfo) {
        try {
            // Parallel queries for maximum efficiency
            CompletableFuture<PaginatedResult<BaseItem>> futureEventsFuture = 
                CompletableFuture.supplyAsync(() -> 
                    hangoutRepository.getFutureEventsPage(groupId, nowTimestamp, limit, startingAfter));
                    
            CompletableFuture<PaginatedResult<BaseItem>> inProgressEventsFuture =
                CompletableFuture.supplyAsync(() ->
                    hangoutRepository.getInProgressEventsPage(groupId, nowTimestamp, limit, startingAfter));

            CompletableFuture<PaginatedResult<BaseItem>> floatingEventsFuture =
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return hangoutRepository.getFloatingHangoutsPage(groupId, null);
                    } catch (RepositoryException e) {
                        logger.warn("Failed to query floating hangouts for group {}, degrading gracefully", groupId, e);
                        return new PaginatedResult<>(List.of(), null);
                    }
                });

            // Wait for all queries to complete
            PaginatedResult<BaseItem> futureEvents = futureEventsFuture.get();
            PaginatedResult<BaseItem> inProgressEvents = inProgressEventsFuture.get();
            PaginatedResult<BaseItem> floatingEvents = floatingEventsFuture.get();

            // Merge results
            List<BaseItem> allItems = new ArrayList<>();
            allItems.addAll(futureEvents.getResults());
            allItems.addAll(inProgressEvents.getResults());
            allItems.addAll(floatingEvents.getResults());

            // Hydrate the mixed feed into FeedItem objects (with version filtering)
            List<FeedItem> allFeedItems = hydrateFeed(allItems, requestingUserId, clientInfo);
            
            // Split into withDay (scheduled) and needsDay (unscheduled)
            List<FeedItem> feedItems = new ArrayList<>();
            List<HangoutSummaryDTO> needsDay = new ArrayList<>();

            // Build a map once for O(1) lookups instead of O(n) searches
            Map<String, HangoutPointer> hangoutMap = allItems.stream()
                .filter(bi -> bi instanceof HangoutPointer)
                .map(bi -> (HangoutPointer) bi)
                .collect(Collectors.toMap(HangoutPointer::getHangoutId, hp -> hp));

            for (FeedItem item : allFeedItems) {
                if (item instanceof HangoutSummaryDTO hangoutSummary) {
                    // O(1) lookup instead of O(n) stream filter
                    HangoutPointer hangoutPointer = hangoutMap.get(hangoutSummary.getHangoutId());

                    if (hangoutPointer != null && hangoutPointer.getStartTimestamp() != null) {
                        feedItems.add(item); // Has timestamp, goes to withDay
                    } else {
                        needsDay.add(hangoutSummary); // No timestamp, goes to needsDay
                    }
                } else {
                    feedItems.add(item); // Non-hangout items go to withDay
                }
            }
            
            // Apply slot-based interleaving sort
            FeedSortingService.SortResult sorted =
                    feedSortingService.sortFeed(feedItems, needsDay, nowTimestamp);

            // Forward-fill: surface stale floats into needsDay and ideas into withDay
            // to cover empty weeks in the forward horizon. Ideas are gated to 2.0.0+ clients
            // (old clients can't deserialize idea_suggestion feed items).
            boolean supportsNewFeedFeatures = clientInfo == null || clientInfo.isVersionAtLeast(NEW_FEED_FEATURES_MIN_VERSION);
            List<FeedItem> finalWithDay = new ArrayList<>(sorted.withDay);
            List<HangoutSummaryDTO> finalNeedsDay = new ArrayList<>(sorted.needsDay);
            if (supportsNewFeedFeatures) {
                ForwardFillSuggestionService.ForwardFillResult fill =
                        forwardFillSuggestionService.getForwardFill(
                                groupId, nowTimestamp, requestingUserId, sorted.heldStaleFloats);
                finalNeedsDay.addAll(fill.getStaleFloats());
                finalWithDay.addAll(fill.getIdeas());
            }

            // Repository is the source of truth for pagination tokens
            String nextPageToken = null;
            if (futureEvents.hasMore()) {
                // Convert repository token to custom format for API response
                nextPageToken = getCustomToken(futureEvents.getNextToken(), true);
            }

            // Always provide a token to access past events when viewing current/future events
            String previousPageToken = generatePreviousPageTokenFromBaseItems(allItems, nowTimestamp);

            return new GroupFeedDTO(groupId, finalWithDay, finalNeedsDay, nextPageToken, previousPageToken);
            
        } catch (Exception e) {
            logger.error("Failed to get current and future events for group {}", groupId, e);
            throw new RepositoryException("Failed to retrieve group feed", e);
        }
    }
    
    /**
     * Get past events for backward pagination.
     * Now supports both standalone hangouts and multi-part series.
     */
    private GroupFeedDTO getPastEvents(String groupId, long nowTimestamp, Integer limit, String endingBefore, String requestingUserId, ClientInfo clientInfo) {
        try {
            // Convert our custom pagination token to the format the repository expects
            String repositoryToken = getRepositoryToken(endingBefore, groupId);

            PaginatedResult<BaseItem> pastEvents =
                hangoutRepository.getPastEventsPage(groupId, nowTimestamp, limit, repositoryToken);

            // Hydrate the mixed feed into FeedItem objects (with version filtering)
            List<FeedItem> feedItems = hydrateFeed(pastEvents.getResults(), requestingUserId, clientInfo);
            
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
     *
     * @param baseItems List of base items from repository
     * @param requestingUserId User ID for calculating poll voting status
     * @return List of transformed feed items
     */
    List<FeedItem> hydrateFeed(List<BaseItem> baseItems, String requestingUserId, ClientInfo clientInfo) {
        // Determine if client supports watch parties (version >= 2.0.0 or null/unknown version)
        boolean supportsWatchParty = clientInfo == null || clientInfo.isVersionAtLeast(WATCH_PARTY_MIN_VERSION);
        boolean supportsNewFeedFeatures = clientInfo == null || clientInfo.isVersionAtLeast(NEW_FEED_FEATURES_MIN_VERSION);

        // First Pass: Identify all hangouts that are part of series that will be shown
        // For watch parties with old clients, we DON'T add their hangouts here so they
        // appear as standalone items in the feed instead of being hidden
        Set<String> hangoutIdsInSeries = new HashSet<>();
        for (BaseItem item : baseItems) {
            if (item instanceof SeriesPointer seriesPointer) {
                // Skip watch party series for old clients - their hangouts should appear standalone
                if (seriesPointer.isWatchParty() && !supportsWatchParty) {
                    continue;
                }

                // Add all hangout IDs from the series' denormalized parts list
                // Use hangoutIds as fallback for older data that may not have parts populated
                if (seriesPointer.getParts() != null && !seriesPointer.getParts().isEmpty()) {
                    for (HangoutPointer part : seriesPointer.getParts()) {
                        hangoutIdsInSeries.add(part.getHangoutId());
                    }
                } else if (seriesPointer.getHangoutIds() != null) {
                    hangoutIdsInSeries.addAll(seriesPointer.getHangoutIds());
                }
            }
        }

        // Build series metadata lookup from SeriesPointers in this page
        Map<String, SeriesPointer> seriesPointerMap = new HashMap<>();
        for (BaseItem item : baseItems) {
            if (item instanceof SeriesPointer sp) {
                seriesPointerMap.put(sp.getSeriesId(), sp);
            }
        }

        // Second Pass: Build the final feed list

        List<FeedItem> feedItems = new ArrayList<>();
        for (BaseItem item : baseItems) {
            if (item instanceof SeriesPointer seriesPointer) {
                // Filter out watch parties for old clients
                if (seriesPointer.isWatchParty() && !supportsWatchParty) {
                    logger.debug("Filtering watch party {} for client version < {}",
                        seriesPointer.getSeriesId(), WATCH_PARTY_MIN_VERSION);
                    continue;
                }

                // Convert SeriesPointer to SeriesSummaryDTO
                SeriesSummaryDTO seriesDTO = createSeriesSummaryDTO(seriesPointer, requestingUserId, seriesPointerMap);
                feedItems.add(seriesDTO);

            } else if (item instanceof HangoutPointer hangoutPointer) {
                // Only include standalone hangouts (not already part of a series)
                if (!hangoutIdsInSeries.contains(hangoutPointer.getHangoutId())) {
                    HangoutSummaryDTO hangoutDTO = buildEnrichedHangoutDTO(
                        hangoutPointer, requestingUserId, supportsNewFeedFeatures, seriesPointerMap);
                    feedItems.add(hangoutDTO);
                }
                // If it's part of a series, ignore it (already included in SeriesSummaryDTO)
            }
        }
        
        // The list is already sorted by the database query, so no additional sorting needed
        return feedItems;
    }
    
    /**
     * Build a HangoutSummaryDTO from a HangoutPointer with all standard enrichment.
     * Used for both standalone hangouts and series parts to ensure consistent processing.
     */
    private HangoutSummaryDTO buildEnrichedHangoutDTO(HangoutPointer pointer, String requestingUserId,
                                                       boolean applyNudges,
                                                       Map<String, SeriesPointer> seriesPointerMap) {
        HangoutSummaryDTO dto = new HangoutSummaryDTO(pointer, requestingUserId);
        dto.setInterestLevels(HangoutDataTransformer.transformAttendanceForBackwardCompatibility(
                dto.getInterestLevels(), attendanceBackwardCompatEnabled));
        hangoutService.enrichHostAtPlaceInfo(dto);
        if (pointer.getSeriesId() != null) {
            enrichSeriesMetadata(dto, pointer.getSeriesId(), pointer.getGroupId(), seriesPointerMap);
        }
        if (applyNudges) {
            dto.setSuggestedAttributes(
                attributeSuggestionService.computeSuggestedAttributes(null, dto.getPolls()));
            dto.setNudges(
                nudgeService.computeNudgesFromPointer(pointer, dto.getPolls()));
        }
        return dto;
    }

    /**
     * Enrich a HangoutSummaryDTO with series-level metadata.
     * Usually the SeriesPointer is already in the query results (same GSI page).
     * On rare pagination boundaries it may not be, so we fall back to a direct lookup.
     */
    private void enrichSeriesMetadata(HangoutSummaryDTO dto, String seriesId,
                                       String groupId, Map<String, SeriesPointer> seriesPointerMap) {
        if (!seriesPointerMap.containsKey(seriesId)) {
            // Pagination boundary: SeriesPointer on a different page — fetch and cache (hit or miss)
            SeriesPointer fetched = groupRepository.findSeriesPointer(groupId, seriesId).orElse(null);
            seriesPointerMap.put(seriesId, fetched);
        }

        SeriesPointer sp = seriesPointerMap.get(seriesId);
        if (sp != null) {
            dto.setSeriesTitle(sp.getSeriesTitle());
            dto.setSeriesImagePath(sp.getMainImagePath());
            dto.setEventSeriesType(sp.getEventSeriesType());
        }
    }

    /**
     * Create a SeriesSummaryDTO from a SeriesPointer with all its denormalized parts.
     *
     * @param seriesPointer The series pointer with denormalized hangout parts
     * @param requestingUserId User ID for calculating poll voting status in parts
     * @return SeriesSummaryDTO with transformed parts
     */
    SeriesSummaryDTO createSeriesSummaryDTO(SeriesPointer seriesPointer, String requestingUserId,
                                               Map<String, SeriesPointer> seriesPointerMap) {
        SeriesSummaryDTO dto = new SeriesSummaryDTO();

        // Copy series-level information
        dto.setSeriesId(seriesPointer.getSeriesId());
        dto.setSeriesTitle(seriesPointer.getSeriesTitle());
        dto.setSeriesDescription(seriesPointer.getSeriesDescription());
        dto.setPrimaryEventId(seriesPointer.getPrimaryEventId());
        dto.setStartTimestamp(seriesPointer.getStartTimestamp());
        dto.setEndTimestamp(seriesPointer.getEndTimestamp());
        dto.setMainImagePath(seriesPointer.getMainImagePath());
        dto.setEventSeriesType(seriesPointer.getEventSeriesType());

        // Convert denormalized parts to HangoutSummaryDTO objects with transformed data
        List<HangoutSummaryDTO> parts = new ArrayList<>();
        if (seriesPointer.getParts() != null) {
            for (HangoutPointer part : seriesPointer.getParts()) {
                HangoutSummaryDTO partDTO = buildEnrichedHangoutDTO(part, requestingUserId, false, seriesPointerMap);
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

    @Override
    public Group getGroupForEtagCheck(String groupId, String requestingUserId) {
        logger.debug("ETag check for group {} by user {}", groupId, requestingUserId);

        // Verify membership (cheap - 1 RCU)
        if (!isUserInGroup(requestingUserId, groupId)) {
            throw new ForbiddenException("User is not a member of this group");
        }

        // Return group metadata (1 RCU)
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("Group not found"));
    }

    @Override
    public InviteCodeResponse generateInviteCode(String groupId, String requestingUserId) {
        // Verify group exists
        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));

        // Verify user is a member
        if (!groupRepository.isUserMemberOfGroup(groupId, requestingUserId)) {
            throw new UnauthorizedException("Only group members can generate invite codes");
        }

        // Check if there's already an active code for this group (idempotent)
        Optional<InviteCode> existingCode = inviteCodeRepository.findActiveCodeForGroup(groupId);

        if (existingCode.isPresent()) {
            InviteCode inviteCode = existingCode.get();
            logger.debug("Returning existing active invite code {} for group {}", inviteCode.getCode(), groupId);

            // Build shareable URL
            String shareUrl = String.format("%s/join-group/%s", appBaseUrl, inviteCode.getCode());
            return new InviteCodeResponse(inviteCode.getCode(), shareUrl);
        }

        // No active code exists, generate a new one
        String codeString = InviteCodeGenerator.generateUnique(inviteCodeRepository::codeExists);

        // Create new InviteCode entity
        InviteCode inviteCode = new InviteCode(groupId, codeString, requestingUserId, group.getGroupName());

        // Save invite code
        inviteCodeRepository.save(inviteCode);

        logger.info("Generated new invite code {} for group {}", codeString, groupId);

        // Build shareable URL
        String shareUrl = String.format("%s/join-group/%s", appBaseUrl, codeString);

        return new InviteCodeResponse(codeString, shareUrl);
    }

    @Override
    public GroupPreviewDTO getGroupPreviewByInviteCode(String inviteCode) {
        // Public endpoint - no auth required
        InviteCode code = inviteCodeRepository.findByCode(inviteCode)
            .orElseThrow(() -> new ResourceNotFoundException("Invalid invite code: " + inviteCode));

        // Get the group
        Group group = groupRepository.findById(code.getGroupId())
            .orElseThrow(() -> new ResourceNotFoundException("Group not found for invite code"));

        return new GroupPreviewDTO(group);
    }

    @Override
    public GroupDTO joinGroupByInviteCode(String inviteCodeString, String userId) {
        // Find invite code
        InviteCode inviteCode = inviteCodeRepository.findByCode(inviteCodeString)
            .orElseThrow(() -> new ResourceNotFoundException("Invalid invite code: " + inviteCodeString));

        // Check if code is usable (active and not expired)
        if (!inviteCode.isUsable()) {
            throw new ResourceNotFoundException("This invite code is no longer valid");
        }

        // Get the group
        Group group = groupRepository.findById(inviteCode.getGroupId())
            .orElseThrow(() -> new ResourceNotFoundException("Group not found for invite code"));

        // Check if user is already a member
        if (groupRepository.isUserMemberOfGroup(group.getGroupId(), userId)) {
            logger.info("User {} already a member of group {}, returning group info", userId, group.getGroupId());
            // Return existing membership info
            GroupMembership membership = groupRepository.findMembership(group.getGroupId(), userId)
                .orElseThrow(() -> new IllegalStateException("Membership check passed but findMembership failed"));
            return new GroupDTO(group, membership);
        }

        // Get user info for denormalization
        User user = userRepository.findById(UUID.fromString(userId))
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        // Create membership
        GroupMembership membership = new GroupMembership(
            group.getGroupId(),
            userId,
            group.getGroupName()
        );
        membership.setRole(GroupRole.MEMBER);  // New members join as regular members
        membership.setGroupMainImagePath(group.getMainImagePath());
        membership.setGroupBackgroundImagePath(group.getBackgroundImagePath());
        membership.setUserMainImagePath(user.getMainImagePath());

        groupRepository.addMember(membership);

        // Record usage on invite code
        inviteCode.recordUsage(userId);
        inviteCodeRepository.save(inviteCode);

        logger.info("User {} joined group {} via invite code {}", userId, group.getGroupId(), inviteCodeString);

        return new GroupDTO(group, membership);
    }
}