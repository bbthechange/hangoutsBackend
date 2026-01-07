package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.FeedItemDTO;
import com.bbthechange.inviter.dto.GroupFeedItemsResponse;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.GroupService;
import com.bbthechange.inviter.util.GroupFeedPaginationToken;
import com.bbthechange.inviter.util.PaginatedResult;
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
        
        // Mock paginated hangouts - first page with two events
        List<HangoutPointer> hangoutPointers = createMockHangoutPointers();
        PaginatedResult<HangoutPointer> firstPage = new PaginatedResult<>(hangoutPointers, null); // No more pages
        when(hangoutRepository.findUpcomingHangoutsPage("GROUP#" + GROUP_ID, "T#", 10, null))
            .thenReturn(firstPage);
        
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
        
        // Verify repository interactions - now uses paginated method
        verify(groupService).isUserInGroup(USER_ID, GROUP_ID);
        verify(hangoutRepository).findUpcomingHangoutsPage("GROUP#" + GROUP_ID, "T#", 10, null);
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
        
        // Create a database pagination token (simulating continuation from previous page)
        String dbPaginationToken = "some-db-token";
        
        // Mock empty page when starting from continuation token (no more events)
        PaginatedResult<HangoutPointer> emptyPage = new PaginatedResult<>(new ArrayList<>(), null);
        when(hangoutRepository.findUpcomingHangoutsPage("GROUP#" + GROUP_ID, "T#", 10, dbPaginationToken))
            .thenReturn(emptyPage);
        
        // When
        GroupFeedItemsResponse response = groupFeedService.getFeedItems(GROUP_ID, 10, dbPaginationToken, USER_ID);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getItems()).isEmpty(); // No items in continuation page
        assertThat(response.getNextPageToken()).isNull(); // No more pages
        
        // Verify correct repository call with pagination token
        verify(hangoutRepository).findUpcomingHangoutsPage("GROUP#" + GROUP_ID, "T#", 10, dbPaginationToken);
        
        // Should not query any specific events since page was empty
        verify(hangoutRepository, never()).getAllPollData(any());
        verify(hangoutRepository, never()).findAttributesByHangoutId(any());
    }
    
    @Test
    void getFeedItems_WithInvalidPaginationToken_ThrowsException() {
        // Given
        when(groupService.isUserInGroup(USER_ID, GROUP_ID)).thenReturn(true);
        String invalidToken = "invalid-token";
        
        // Mock repository to throw exception for invalid token
        when(hangoutRepository.findUpcomingHangoutsPage("GROUP#" + GROUP_ID, "T#", 10, invalidToken))
            .thenThrow(new IllegalArgumentException("Invalid pagination token"));
        
        // When & Then
        assertThatThrownBy(() -> groupFeedService.getFeedItems(GROUP_ID, 10, invalidToken, USER_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid pagination token");
        
        verify(groupService).isUserInGroup(USER_ID, GROUP_ID);
        verify(hangoutRepository).findUpcomingHangoutsPage("GROUP#" + GROUP_ID, "T#", 10, invalidToken);
    }
    
    @Test
    void getFeedItems_WithNoUpcomingEvents_ReturnsEmptyResponse() {
        // Given
        when(groupService.isUserInGroup(USER_ID, GROUP_ID)).thenReturn(true);
        
        // Mock empty page (no upcoming events)
        PaginatedResult<HangoutPointer> emptyPage = new PaginatedResult<>(new ArrayList<>(), null);
        when(hangoutRepository.findUpcomingHangoutsPage("GROUP#" + GROUP_ID, "T#", 10, null))
            .thenReturn(emptyPage);
        
        // When
        GroupFeedItemsResponse response = groupFeedService.getFeedItems(GROUP_ID, 10, null, USER_ID);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getItems()).isEmpty();
        assertThat(response.getNextPageToken()).isNull();
        
        verify(hangoutRepository).findUpcomingHangoutsPage("GROUP#" + GROUP_ID, "T#", 10, null);
    }
    
    private List<HangoutPointer> createMockHangoutPointers() {
        List<HangoutPointer> pointers = new ArrayList<>();
        
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

    @Test
    void getFeedItems_ReachesLimitWithMoreEventsAvailable_ReturnsNextPageToken() {
        // Given - user has access
        when(groupService.isUserInGroup(USER_ID, GROUP_ID)).thenReturn(true);

        // First page with one event (and more available)
        List<HangoutPointer> hangoutPointers = new ArrayList<>();
        HangoutPointer pointer1 = new HangoutPointer();
        pointer1.setHangoutId(EVENT_ID_1);
        pointer1.setTitle("First Event");
        pointer1.setStartTimestamp(System.currentTimeMillis() / 1000 + 3600);
        hangoutPointers.add(pointer1);

        String nextDbToken = "next-db-page-token";
        PaginatedResult<HangoutPointer> firstPage = new PaginatedResult<>(hangoutPointers, nextDbToken);
        when(hangoutRepository.findUpcomingHangoutsPage("GROUP#" + GROUP_ID, "T#", 10, null))
            .thenReturn(firstPage);

        // Mock poll data that produces 2 feed items
        List<BaseItem> pollData1 = createMockPollData(EVENT_ID_1, POLL_ID_1);
        when(hangoutRepository.getAllPollData(EVENT_ID_1)).thenReturn(pollData1);

        // Mock 1 undecided attribute
        List<HangoutAttribute> attributes1 = createMockUndecidedAttributes(EVENT_ID_1, ATTRIBUTE_ID_1);
        when(hangoutRepository.findAttributesByHangoutId(EVENT_ID_1)).thenReturn(attributes1);

        // When - request limit of 1 (will be exceeded by 2 items from first event)
        GroupFeedItemsResponse response = groupFeedService.getFeedItems(GROUP_ID, 1, null, USER_ID);

        // Then - should return items and continuation token
        assertThat(response).isNotNull();
        assertThat(response.getItems()).isNotEmpty();
        assertThat(response.getNextPageToken()).isEqualTo(nextDbToken);
    }

    @Test
    void getFeedItems_WithPollOptionsAndVotes_ReturnsPollWithVoterInfo() {
        // Given
        when(groupService.isUserInGroup(USER_ID, GROUP_ID)).thenReturn(true);

        // Mock paginated hangouts
        List<HangoutPointer> hangoutPointers = new ArrayList<>();
        HangoutPointer pointer1 = new HangoutPointer();
        pointer1.setHangoutId(EVENT_ID_1);
        pointer1.setTitle("Event With Full Poll");
        pointer1.setStartTimestamp(System.currentTimeMillis() / 1000 + 3600);
        hangoutPointers.add(pointer1);
        PaginatedResult<HangoutPointer> firstPage = new PaginatedResult<>(hangoutPointers, null);
        when(hangoutRepository.findUpcomingHangoutsPage("GROUP#" + GROUP_ID, "T#", 10, null))
            .thenReturn(firstPage);

        // Mock full poll data with Poll, PollOptions, and Votes
        List<BaseItem> pollData = createFullPollData(EVENT_ID_1, POLL_ID_1);
        when(hangoutRepository.getAllPollData(EVENT_ID_1)).thenReturn(pollData);

        // No attributes for this test
        when(hangoutRepository.findAttributesByHangoutId(EVENT_ID_1)).thenReturn(new ArrayList<>());

        // When
        GroupFeedItemsResponse response = groupFeedService.getFeedItems(GROUP_ID, 10, null, USER_ID);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getItems()).hasSize(1);

        FeedItemDTO pollItem = response.getItems().get(0);
        assertThat(pollItem.getItemType()).isEqualTo("POLL");
        assertThat(pollItem.getData().get("pollId")).isEqualTo(POLL_ID_1);

        // Verify options are included
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> options =
            (List<java.util.Map<String, Object>>) pollItem.getData().get("options");
        assertThat(options).hasSize(2);

        // First option should have voter
        assertThat(options.get(0).get("optionId")).isEqualTo("option-1");
        assertThat(options.get(0).get("text")).isEqualTo("Option A");
        @SuppressWarnings("unchecked")
        List<String> voters = (List<String>) options.get(0).get("voters");
        assertThat(voters).containsExactly("voter-user-1");
    }

    @Test
    void getFeedItems_WithDecidedAndUndecidedAttributes_OnlyReturnsUndecided() {
        // Given
        when(groupService.isUserInGroup(USER_ID, GROUP_ID)).thenReturn(true);

        // Mock paginated hangouts
        List<HangoutPointer> hangoutPointers = new ArrayList<>();
        HangoutPointer pointer1 = new HangoutPointer();
        pointer1.setHangoutId(EVENT_ID_1);
        pointer1.setTitle("Event With Attributes");
        pointer1.setStartTimestamp(System.currentTimeMillis() / 1000 + 3600);
        hangoutPointers.add(pointer1);
        PaginatedResult<HangoutPointer> firstPage = new PaginatedResult<>(hangoutPointers, null);
        when(hangoutRepository.findUpcomingHangoutsPage("GROUP#" + GROUP_ID, "T#", 10, null))
            .thenReturn(firstPage);

        // No polls
        when(hangoutRepository.getAllPollData(EVENT_ID_1)).thenReturn(new ArrayList<>());

        // Mock attributes - mix of decided and undecided
        List<HangoutAttribute> attributes = createMixedAttributes(EVENT_ID_1);
        when(hangoutRepository.findAttributesByHangoutId(EVENT_ID_1)).thenReturn(attributes);

        // When
        GroupFeedItemsResponse response = groupFeedService.getFeedItems(GROUP_ID, 10, null, USER_ID);

        // Then - only undecided attributes should appear (null, empty, and whitespace-only)
        assertThat(response).isNotNull();
        assertThat(response.getItems()).hasSize(3); // null, empty string, and whitespace-only

        // Verify all returned items are undecided attributes
        for (FeedItemDTO item : response.getItems()) {
            assertThat(item.getItemType()).isEqualTo("ATTRIBUTE");
            assertThat(item.getData().get("isDecided")).isEqualTo(false);
        }
    }

    @Test
    void getFeedItems_WithInactivePoll_SkipsInactivePoll() {
        // Given
        when(groupService.isUserInGroup(USER_ID, GROUP_ID)).thenReturn(true);

        List<HangoutPointer> hangoutPointers = new ArrayList<>();
        HangoutPointer pointer1 = new HangoutPointer();
        pointer1.setHangoutId(EVENT_ID_1);
        pointer1.setTitle("Event With Inactive Poll");
        pointer1.setStartTimestamp(System.currentTimeMillis() / 1000 + 3600);
        hangoutPointers.add(pointer1);
        PaginatedResult<HangoutPointer> firstPage = new PaginatedResult<>(hangoutPointers, null);
        when(hangoutRepository.findUpcomingHangoutsPage("GROUP#" + GROUP_ID, "T#", 10, null))
            .thenReturn(firstPage);

        // Mock inactive poll
        List<BaseItem> pollData = new ArrayList<>();
        Poll inactivePoll = new Poll();
        inactivePoll.setEventId(EVENT_ID_1);
        inactivePoll.setPollId(POLL_ID_1);
        inactivePoll.setTitle("Inactive Poll");
        inactivePoll.setActive(false); // Inactive!
        pollData.add(inactivePoll);
        when(hangoutRepository.getAllPollData(EVENT_ID_1)).thenReturn(pollData);

        when(hangoutRepository.findAttributesByHangoutId(EVENT_ID_1)).thenReturn(new ArrayList<>());

        // When
        GroupFeedItemsResponse response = groupFeedService.getFeedItems(GROUP_ID, 10, null, USER_ID);

        // Then - inactive poll should be skipped
        assertThat(response).isNotNull();
        assertThat(response.getItems()).isEmpty();
    }

    private List<BaseItem> createFullPollData(String eventId, String pollId) {
        List<BaseItem> pollData = new ArrayList<>();

        // Add Poll
        Poll poll = new Poll();
        poll.setEventId(eventId);
        poll.setPollId(pollId);
        poll.setTitle("Full Poll Question");
        poll.setDescription("Poll with options and votes");
        poll.setMultipleChoice(false);
        poll.setActive(true);
        pollData.add(poll);

        // Add PollOptions
        PollOption option1 = new PollOption();
        option1.setEventId(eventId);
        option1.setPollId(pollId);
        option1.setOptionId("option-1");
        option1.setText("Option A");
        pollData.add(option1);

        PollOption option2 = new PollOption();
        option2.setEventId(eventId);
        option2.setPollId(pollId);
        option2.setOptionId("option-2");
        option2.setText("Option B");
        pollData.add(option2);

        // Add Vote for option 1
        Vote vote1 = new Vote();
        vote1.setEventId(eventId);
        vote1.setPollId(pollId);
        vote1.setOptionId("option-1");
        vote1.setUserId("voter-user-1");
        pollData.add(vote1);

        return pollData;
    }

    private List<HangoutAttribute> createMixedAttributes(String eventId) {
        List<HangoutAttribute> attributes = new ArrayList<>();

        // Undecided - null value
        HangoutAttribute nullAttr = new HangoutAttribute();
        nullAttr.setHangoutId(eventId);
        nullAttr.setAttributeId("attr-null");
        nullAttr.setAttributeName("Undecided Null");
        nullAttr.setStringValue(null);
        attributes.add(nullAttr);

        // Undecided - empty string
        HangoutAttribute emptyAttr = new HangoutAttribute();
        emptyAttr.setHangoutId(eventId);
        emptyAttr.setAttributeId("attr-empty");
        emptyAttr.setAttributeName("Undecided Empty");
        emptyAttr.setStringValue("");
        attributes.add(emptyAttr);

        // Decided - has value
        HangoutAttribute decidedAttr = new HangoutAttribute();
        decidedAttr.setHangoutId(eventId);
        decidedAttr.setAttributeId("attr-decided");
        decidedAttr.setAttributeName("Decided Attribute");
        decidedAttr.setStringValue("Central Park");
        attributes.add(decidedAttr);

        // Undecided - whitespace only (should be filtered as undecided)
        HangoutAttribute whitespaceAttr = new HangoutAttribute();
        whitespaceAttr.setHangoutId(eventId);
        whitespaceAttr.setAttributeId("attr-whitespace");
        whitespaceAttr.setAttributeName("Whitespace Only");
        whitespaceAttr.setStringValue("   ");
        attributes.add(whitespaceAttr);

        return attributes;
    }
}