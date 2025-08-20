package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.FeedItemDTO;
import com.bbthechange.inviter.dto.GroupFeedItemsResponse;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.GroupService;
import com.bbthechange.inviter.util.GroupFeedPaginationToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GroupFeedServiceImpl using Mockito.
 * Tests the backend loop aggregation logic without database dependencies.
 */
@ExtendWith(MockitoExtension.class)
class GroupFeedServiceImplTest {

    private static final String GROUP_ID = "12345678-1234-1234-1234-123456789012";
    private static final String USER_ID = "87654321-4321-4321-4321-210987654321";
    private static final String EVENT_ID_1 = "event-123";
    private static final String EVENT_ID_2 = "event-456";
    private static final String POLL_ID_1 = "poll-123";
    private static final String ATTRIBUTE_ID_1 = "attr-123";

    @Mock
    private HangoutRepository hangoutRepository;
    
    @Mock
    private GroupService groupService;
    
    @InjectMocks
    private GroupFeedServiceImpl groupFeedService;
    
    @Test
    void getFeedItems_WithValidGroup_ReturnsPollsAndAttributes() {
        // Given
        when(groupService.isUserInGroup(USER_ID, GROUP_ID)).thenReturn(true);
        
        // Mock upcoming hangouts
        List<BaseItem> upcomingHangouts = createMockHangoutPointers();
        when(hangoutRepository.findUpcomingHangoutsForParticipant("GROUP#" + GROUP_ID, "T#"))
            .thenReturn(upcomingHangouts);
        
        // Mock poll data for first event
        List<BaseItem> pollData1 = createMockPollData(EVENT_ID_1, POLL_ID_1);
        when(hangoutRepository.getAllPollData(EVENT_ID_1)).thenReturn(pollData1);
        
        // Mock attributes for first event
        List<HangoutAttribute> attributes1 = createMockUndecidedAttributes(EVENT_ID_1, ATTRIBUTE_ID_1);
        when(hangoutRepository.findAttributesByHangoutId(EVENT_ID_1)).thenReturn(attributes1);
        
        // Mock empty data for second event
        when(hangoutRepository.getAllPollData(EVENT_ID_2)).thenReturn(new ArrayList<>());
        when(hangoutRepository.findAttributesByHangoutId(EVENT_ID_2)).thenReturn(new ArrayList<>());
        
        // When
        GroupFeedItemsResponse response = groupFeedService.getFeedItems(GROUP_ID, 10, null, USER_ID);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getItems()).hasSize(2);
        
        // Verify poll item
        FeedItemDTO pollItem = response.getItems().stream()
            .filter(item -> "POLL".equals(item.getItemType()))
            .findFirst().orElse(null);
        assertThat(pollItem).isNotNull();
        assertThat(pollItem.getEventInfo().getEventId()).isEqualTo(EVENT_ID_1);
        assertThat(pollItem.getData().get("pollId")).isEqualTo(POLL_ID_1);
        assertThat(pollItem.getData().get("question")).isEqualTo("Test Poll Question");
        
        // Verify attribute item
        FeedItemDTO attributeItem = response.getItems().stream()
            .filter(item -> "ATTRIBUTE".equals(item.getItemType()))
            .findFirst().orElse(null);
        assertThat(attributeItem).isNotNull();
        assertThat(attributeItem.getEventInfo().getEventId()).isEqualTo(EVENT_ID_1);
        assertThat(attributeItem.getData().get("attributeId")).isEqualTo(ATTRIBUTE_ID_1);
        assertThat(attributeItem.getData().get("name")).isEqualTo("Meeting Location");
        assertThat(attributeItem.getData().get("isDecided")).isEqualTo(false);
        
        // Verify repository interactions
        verify(groupService).isUserInGroup(USER_ID, GROUP_ID);
        verify(hangoutRepository).findUpcomingHangoutsForParticipant("GROUP#" + GROUP_ID, "T#");
        verify(hangoutRepository).getAllPollData(EVENT_ID_1);
        verify(hangoutRepository).findAttributesByHangoutId(EVENT_ID_1);
    }
    
    @Test
    void getFeedItems_WithUnauthorizedUser_ThrowsException() {
        // Given
        when(groupService.isUserInGroup(USER_ID, GROUP_ID)).thenReturn(false);
        
        // When & Then
        assertThatThrownBy(() -> groupFeedService.getFeedItems(GROUP_ID, 10, null, USER_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("User does not have access to group");
        
        verify(groupService).isUserInGroup(USER_ID, GROUP_ID);
        verifyNoInteractions(hangoutRepository);
    }
    
    @Test
    void getFeedItems_WithValidPaginationToken_AppliesCursor() {
        // Given
        when(groupService.isUserInGroup(USER_ID, GROUP_ID)).thenReturn(true);
        
        // Create pagination token for first event
        GroupFeedPaginationToken token = new GroupFeedPaginationToken(EVENT_ID_1, LocalDateTime.now());
        String startToken = token.encode();
        
        List<BaseItem> upcomingHangouts = createMockHangoutPointers();
        when(hangoutRepository.findUpcomingHangoutsForParticipant("GROUP#" + GROUP_ID, "T#"))
            .thenReturn(upcomingHangouts);
        
        // Mock empty data for second event (after cursor)
        when(hangoutRepository.getAllPollData(EVENT_ID_2)).thenReturn(new ArrayList<>());
        when(hangoutRepository.findAttributesByHangoutId(EVENT_ID_2)).thenReturn(new ArrayList<>());
        
        // When
        GroupFeedItemsResponse response = groupFeedService.getFeedItems(GROUP_ID, 10, startToken, USER_ID);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getItems()).isEmpty(); // No items after cursor
        
        // Should not query the first event (before cursor)
        verify(hangoutRepository, never()).getAllPollData(EVENT_ID_1);
        verify(hangoutRepository, never()).findAttributesByHangoutId(EVENT_ID_1);
    }
    
    @Test
    void getFeedItems_WithInvalidPaginationToken_ThrowsException() {
        // Given
        when(groupService.isUserInGroup(USER_ID, GROUP_ID)).thenReturn(true);
        String invalidToken = "invalid-token";
        
        // When & Then
        assertThatThrownBy(() -> groupFeedService.getFeedItems(GROUP_ID, 10, invalidToken, USER_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid pagination token");
        
        verify(groupService).isUserInGroup(USER_ID, GROUP_ID);
        verifyNoInteractions(hangoutRepository);
    }
    
    @Test
    void getFeedItems_WithNoUpcomingEvents_ReturnsEmptyResponse() {
        // Given
        when(groupService.isUserInGroup(USER_ID, GROUP_ID)).thenReturn(true);
        when(hangoutRepository.findUpcomingHangoutsForParticipant("GROUP#" + GROUP_ID, "T#"))
            .thenReturn(new ArrayList<>());
        
        // When
        GroupFeedItemsResponse response = groupFeedService.getFeedItems(GROUP_ID, 10, null, USER_ID);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getItems()).isEmpty();
        assertThat(response.getNextPageToken()).isNull();
        
        verify(hangoutRepository).findUpcomingHangoutsForParticipant("GROUP#" + GROUP_ID, "T#");
    }
    
    private List<BaseItem> createMockHangoutPointers() {
        List<BaseItem> pointers = new ArrayList<>();
        
        // First event
        HangoutPointer pointer1 = new HangoutPointer();
        pointer1.setHangoutId(EVENT_ID_1);
        pointer1.setTitle("First Event");
        pointer1.setStartTimestamp(System.currentTimeMillis() / 1000 + 3600); // 1 hour from now
        pointers.add(pointer1);
        
        // Second event
        HangoutPointer pointer2 = new HangoutPointer();
        pointer2.setHangoutId(EVENT_ID_2);
        pointer2.setTitle("Second Event");
        pointer2.setStartTimestamp(System.currentTimeMillis() / 1000 + 7200); // 2 hours from now
        pointers.add(pointer2);
        
        return pointers;
    }
    
    private List<BaseItem> createMockPollData(String eventId, String pollId) {
        List<BaseItem> pollData = new ArrayList<>();
        
        Poll poll = new Poll();
        poll.setEventId(eventId);
        poll.setPollId(pollId);
        poll.setTitle("Test Poll Question");
        poll.setDescription("Poll description");
        poll.setMultipleChoice(false);
        poll.setActive(true);
        
        pollData.add(poll);
        return pollData;
    }
    
    private List<HangoutAttribute> createMockUndecidedAttributes(String eventId, String attributeId) {
        List<HangoutAttribute> attributes = new ArrayList<>();
        
        HangoutAttribute attribute = new HangoutAttribute();
        attribute.setHangoutId(eventId);
        attribute.setAttributeId(attributeId);
        attribute.setAttributeName("Meeting Location");
        attribute.setStringValue(null); // Undecided
        
        attributes.add(attribute);
        return attributes;
    }
}