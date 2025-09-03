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
import com.bbthechange.inviter.util.PaginatedResult;
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
    private static final int EVENTS_PAGE_SIZE = 10;
    
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
        String nextEventPageToken = startToken;
        boolean hasMoreEvents = true;
        
        // Build participant key for GSI query (following existing pattern from HangoutServiceImpl)
        String participantKey = "GROUP#" + groupId;
        logger.debug("Querying upcoming hangouts for participant key: {}", participantKey);
        
        // Process events in pages until we have enough feed items or no more events
        while (feedItems.size() < limit && hasMoreEvents) {
            logger.debug("Fetching page of events with token: {}", nextEventPageToken);
            
            // Get one page of events from the database using true database pagination
            PaginatedResult<HangoutPointer> eventPage = hangoutRepository.findUpcomingHangoutsPage(
                participantKey, "T#", EVENTS_PAGE_SIZE, nextEventPageToken);
            
            logger.debug("Retrieved {} events in this page (hasMore: {})", 
                eventPage.size(), eventPage.hasMore());
            
            // Process only the events in this small page
            for (HangoutPointer pointer : eventPage.getResults()) {
                String eventId = pointer.getHangoutId();
                String eventTitle = pointer.getTitle();
                
                logger.debug("Processing event {} ({})", eventId, eventTitle);
                
                // Get actionable items for this event
                List<FeedItemDTO> eventFeedItems = getActionableItemsForEvent(eventId, eventTitle);
                feedItems.addAll(eventFeedItems);
                
                logger.debug("Found {} actionable items for event {} (total feed items: {})", 
                    eventFeedItems.size(), eventId, feedItems.size());
                
                // If we've reached our target, stop processing
                if (feedItems.size() >= limit) {
                    logger.debug("Reached target limit of {} items, stopping processing", limit);
                    break;
                }
            }
            
            // Prepare for next iteration
            nextEventPageToken = eventPage.getNextToken();
            hasMoreEvents = eventPage.hasMore();
            
            // If we've reached the limit, we need to check if there are more events
            // to determine if we should return a continuation token
            if (feedItems.size() >= limit && hasMoreEvents) {
                logger.debug("Reached feed item limit but more events available - will return continuation token");
                break;
            }
        }
        
        // Generate next page token if there are more events to process
        String nextPageToken = null;
        if (hasMoreEvents && feedItems.size() >= limit) {
            // Return the database pagination token as our feed pagination token
            nextPageToken = nextEventPageToken;
            logger.debug("Generated next page token for continuation");
        }
        
        logger.info("Returning {} feed items for group {} (requested limit: {}, hasMore: {})", 
            feedItems.size(), groupId, limit, nextPageToken != null);
        
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
                
                // Add options with voter information
                List<Map<String, Object>> optionsList = new ArrayList<>();
                
                for (PollOption option : options) {
                    Map<String, Object> optionMap = new HashMap<>();
                    optionMap.put("optionId", option.getOptionId());
                    optionMap.put("text", option.getText());
                    
                    // Get user IDs who voted for this option
                    List<String> voterUserIds = votes.stream()
                        .filter(vote -> vote.getOptionId().equals(option.getOptionId()))
                        .map(Vote::getUserId)
                        .collect(Collectors.toList());
                    
                    optionMap.put("voters", voterUserIds);
                    optionsList.add(optionMap);
                }
                
                pollDataMap.put("options", optionsList);
                
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