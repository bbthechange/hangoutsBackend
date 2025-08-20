package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.*;
import static com.bbthechange.inviter.dto.FeedItemType.*;
import com.bbthechange.inviter.model.*;
import java.util.stream.Collectors;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.GroupFeedService;
import com.bbthechange.inviter.service.GroupService;
import com.bbthechange.inviter.util.GroupFeedPaginationToken;
import com.bbthechange.inviter.util.InviterKeyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of GroupFeedService using the backend loop aggregator pattern.
 * Loops through upcoming events in a group to find actionable items (polls, undecided attributes).
 */
@Service
public class GroupFeedServiceImpl implements GroupFeedService {
    
    private static final Logger logger = LoggerFactory.getLogger(GroupFeedServiceImpl.class);
    private static final int EVENTS_BATCH_SIZE = 10;
    
    private final HangoutRepository hangoutRepository;
    private final GroupService groupService;
    
    @Autowired
    public GroupFeedServiceImpl(HangoutRepository hangoutRepository, GroupService groupService) {
        this.hangoutRepository = hangoutRepository;
        this.groupService = groupService;
    }
    
    @Override
    public GroupFeedItemsResponse getFeedItems(String groupId, Integer limit, String startToken, String requestingUserId) {
        logger.info("Getting feed items for group {} with limit {} for user {}", groupId, limit, requestingUserId);
        
        // Verify user has access to the group
        if (!groupService.isUserInGroup(requestingUserId, groupId)) {
            throw new IllegalArgumentException("User does not have access to group: " + groupId);
        }
        
        List<FeedItemDTO> feedItems = new ArrayList<>();
        GroupFeedPaginationToken currentToken = null;
        
        // Decode start token if provided
        if (startToken != null && !startToken.trim().isEmpty()) {
            try {
                currentToken = GroupFeedPaginationToken.decode(startToken);
                logger.debug("Decoded start token: lastEventId={}, lastTimestamp={}", 
                    currentToken.getLastEventId(), currentToken.getLastTimestamp());
            } catch (Exception e) {
                logger.warn("Invalid start token provided: {}", startToken);
                throw new IllegalArgumentException("Invalid pagination token");
            }
        }
        
        // Build participant key for GSI query (following existing pattern from HangoutServiceImpl)
        String participantKey = "GROUP#" + groupId;
        logger.debug("Querying upcoming hangouts for participant key: {}", participantKey);
        
        // Get upcoming hangouts for the group using GSI
        List<BaseItem> upcomingHangouts = hangoutRepository.findUpcomingHangoutsForParticipant(participantKey, "T#");
        
        // Filter and sort hangout pointers
        List<HangoutPointer> hangoutPointers = upcomingHangouts.stream()
            .filter(item -> item instanceof HangoutPointer)
            .map(item -> (HangoutPointer) item)
            .sorted((h1, h2) -> Long.compare(h1.getStartTimestamp(), h2.getStartTimestamp()))
            .collect(Collectors.toList());
        
        logger.debug("Found {} upcoming hangouts for group {}", hangoutPointers.size(), groupId);
        
        // Apply pagination cursor if provided
        if (currentToken != null) {
            String lastEventId = currentToken.getLastEventId();
            hangoutPointers = hangoutPointers.stream()
                .dropWhile(pointer -> !pointer.getHangoutId().equals(lastEventId))
                .skip(1) // Skip the last processed event
                .collect(Collectors.toList());
            logger.debug("After applying cursor, {} hangouts remaining", hangoutPointers.size());
        }
        
        // Process events in batches until we have enough items
        String lastProcessedEventId = null;
        LocalDateTime lastProcessedTimestamp = null;
        
        for (int i = 0; i < hangoutPointers.size() && feedItems.size() < limit; i += EVENTS_BATCH_SIZE) {
            int endIndex = Math.min(i + EVENTS_BATCH_SIZE, hangoutPointers.size());
            List<HangoutPointer> batch = hangoutPointers.subList(i, endIndex);
            
            logger.debug("Processing batch of {} events (items {}-{})", batch.size(), i, endIndex - 1);
            
            // Process each event in the batch
            for (HangoutPointer pointer : batch) {
                String eventId = pointer.getHangoutId();
                String eventTitle = pointer.getTitle();
                
                // Track last processed event for pagination
                lastProcessedEventId = eventId;
                if (pointer.getStartTimestamp() != null) {
                    lastProcessedTimestamp = LocalDateTime.ofEpochSecond(pointer.getStartTimestamp(), 0, 
                        java.time.ZoneOffset.UTC);
                } else {
                    lastProcessedTimestamp = LocalDateTime.now();
                }
                
                // Get actionable items for this event
                List<FeedItemDTO> eventFeedItems = getActionableItemsForEvent(eventId, eventTitle);
                feedItems.addAll(eventFeedItems);
                
                logger.debug("Found {} actionable items for event {} ({})", 
                    eventFeedItems.size(), eventId, eventTitle);
                
                // If we've reached our target, include all items from this final event
                if (feedItems.size() >= limit) {
                    logger.debug("Reached target limit of {} items, stopping at event {}", limit, eventId);
                    break;
                }
            }
        }
        
        // Generate next page token if there are more events
        String nextPageToken = null;
        if (lastProcessedEventId != null && !hangoutPointers.isEmpty()) {
            // Check if there are more events after the last processed one
            final String finalLastProcessedEventId = lastProcessedEventId;
            final List<HangoutPointer> finalHangoutPointers = hangoutPointers;
            
            boolean hasMoreEvents = hangoutPointers.stream()
                .anyMatch(pointer -> {
                    int currentIndex = finalHangoutPointers.indexOf(pointer);
                    return pointer.getHangoutId().equals(finalLastProcessedEventId) && 
                           currentIndex < finalHangoutPointers.size() - 1;
                });
            
            if (hasMoreEvents) {
                GroupFeedPaginationToken nextToken = new GroupFeedPaginationToken(
                    lastProcessedEventId, lastProcessedTimestamp);
                nextPageToken = nextToken.encode();
                logger.debug("Generated next page token for continuation");
            }
        }
        
        logger.info("Returning {} feed items for group {} (requested limit: {})", 
            feedItems.size(), groupId, limit);
        
        return new GroupFeedItemsResponse(feedItems, nextPageToken);
    }
    
    /**
     * Get actionable items (polls and undecided attributes) for a specific event.
     */
    private List<FeedItemDTO> getActionableItemsForEvent(String eventId, String eventTitle) {
        List<FeedItemDTO> items = new ArrayList<>();
        
        FeedItemEventInfo eventInfo = new FeedItemEventInfo(eventId, eventTitle);
        
        try {
            // Get active polls for the event with full details (options and votes)
            List<BaseItem> pollData = hangoutRepository.getAllPollData(eventId);
            
            // Group poll data by poll ID
            Map<String, List<BaseItem>> pollDataByPollId = pollData.stream()
                .collect(Collectors.groupingBy(item -> {
                    if (item instanceof Poll) {
                        return ((Poll) item).getPollId();
                    } else if (item instanceof PollOption) {
                        return ((PollOption) item).getPollId();
                    } else if (item instanceof Vote) {
                        return ((Vote) item).getPollId();
                    }
                    return "unknown";
                }));
            
            // Process each poll with its options and votes
            for (Map.Entry<String, List<BaseItem>> entry : pollDataByPollId.entrySet()) {
                String pollId = entry.getKey();
                if ("unknown".equals(pollId)) continue;
                
                List<BaseItem> pollItems = entry.getValue();
                
                // Find the poll
                Poll poll = pollItems.stream()
                    .filter(item -> item instanceof Poll)
                    .map(item -> (Poll) item)
                    .filter(Poll::isActive)
                    .findFirst().orElse(null);
                
                if (poll == null) continue; // Skip inactive polls
                
                // Get options for this poll
                List<PollOption> options = pollItems.stream()
                    .filter(item -> item instanceof PollOption)
                    .map(item -> (PollOption) item)
                    .collect(Collectors.toList());
                
                // Get votes for this poll
                List<Vote> votes = pollItems.stream()
                    .filter(item -> item instanceof Vote)
                    .map(item -> (Vote) item)
                    .collect(Collectors.toList());
                
                // Build poll data with options and vote counts
                Map<String, Object> pollDataMap = new HashMap<>();
                pollDataMap.put("pollId", poll.getPollId());
                pollDataMap.put("question", poll.getTitle());
                pollDataMap.put("description", poll.getDescription());
                pollDataMap.put("multipleChoice", poll.isMultipleChoice());
                
                // Add options with vote counts
                List<Map<String, Object>> optionsList = new ArrayList<>();
                int totalVotes = 0;
                
                for (PollOption option : options) {
                    Map<String, Object> optionMap = new HashMap<>();
                    optionMap.put("optionId", option.getOptionId());
                    optionMap.put("text", option.getText());
                    
                    // Count votes for this option
                    int voteCount = (int) votes.stream()
                        .filter(vote -> vote.getOptionId().equals(option.getOptionId()))
                        .count();
                    
                    optionMap.put("voteCount", voteCount);
                    optionMap.put("userVoted", false); // Would need current user context to determine this
                    optionsList.add(optionMap);
                    totalVotes += voteCount;
                }
                
                pollDataMap.put("options", optionsList);
                pollDataMap.put("totalVotes", totalVotes);
                
                FeedItemDTO pollItem = new FeedItemDTO(POLL, eventInfo, pollDataMap);
                items.add(pollItem);
            }
            
            // Get undecided attributes for the event
            List<HangoutAttribute> attributes = hangoutRepository.findAttributesByHangoutId(eventId);
            List<HangoutAttribute> undecidedAttributes = attributes.stream()
                .filter(attr -> attr.getStringValue() == null || attr.getStringValue().trim().isEmpty())
                .collect(Collectors.toList());
            
            for (HangoutAttribute attribute : undecidedAttributes) {
                Map<String, Object> attributeDataMap = new HashMap<>();
                attributeDataMap.put("attributeId", attribute.getAttributeId());
                attributeDataMap.put("name", attribute.getAttributeName());
                attributeDataMap.put("isDecided", false);
                
                FeedItemDTO attributeItem = new FeedItemDTO(ATTRIBUTE, eventInfo, attributeDataMap);
                items.add(attributeItem);
            }
            
        } catch (Exception e) {
            logger.warn("Failed to get actionable items for event {}: {}", eventId, e.getMessage());
            // Continue processing other events even if one fails
        }
        
        return items;
    }
}