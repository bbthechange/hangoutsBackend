package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.CalendarSubscriptionListResponse;
import com.bbthechange.inviter.dto.CalendarSubscriptionResponse;
import com.bbthechange.inviter.exception.ForbiddenException;
import com.bbthechange.inviter.exception.NotFoundException;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.model.BaseItem;
import com.bbthechange.inviter.model.Group;
import com.bbthechange.inviter.model.GroupMembership;
import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.ICalendarService;
import com.bbthechange.inviter.util.PaginatedResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CalendarSubscriptionServiceImpl.
 * Tests calendar subscription management, token generation, and URL formatting.
 */
@ExtendWith(MockitoExtension.class)
class CalendarSubscriptionServiceImplTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private HangoutRepository hangoutRepository;

    @Mock
    private ICalendarService iCalendarService;

    private CalendarSubscriptionServiceImpl subscriptionService;

    private static final String TEST_BASE_URL = "https://test.inviter.app";
    private static final String TEST_USER_ID = "123e4567-e89b-12d3-a456-426614174000";
    private static final String TEST_GROUP_ID = "234e5678-e89b-12d3-a456-426614174001";
    private static final String TEST_GROUP_NAME = "Test Group";
    private static final Instant TEST_CREATED_AT = Instant.parse("2023-10-21T10:00:00Z");

    @BeforeEach
    void setUp() {
        subscriptionService = new CalendarSubscriptionServiceImpl(
            groupRepository,
            hangoutRepository,
            iCalendarService,
            TEST_BASE_URL
        );
    }

    // ===== TESTS FOR createSubscription() =====

    @Test
    void createSubscription_WhenUserNotMember_ThrowsForbiddenException() {
        // Given: User is not a member of the group
        String groupId = "345e6789-e89b-12d3-a456-426614174002";
        String userId = "456e7890-e89b-12d3-a456-426614174003";
        when(groupRepository.findMembership(groupId, userId))
            .thenReturn(Optional.empty());

        // When/Then: ForbiddenException is thrown
        assertThatThrownBy(() -> subscriptionService.createSubscription(groupId, userId))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("You must be a member of this group to subscribe to its calendar");

        // Verify no token was created
        verify(groupRepository).findMembership(groupId, userId);
        verify(groupRepository, never()).addMember(any());
    }

    @Test
    void createSubscription_WhenUserIsMember_CreatesNewToken() {
        // Given: User is a member without existing token
        GroupMembership membership = createMembership(TEST_GROUP_ID, TEST_USER_ID, TEST_GROUP_NAME, null);
        when(groupRepository.findMembership(TEST_GROUP_ID, TEST_USER_ID))
            .thenReturn(Optional.of(membership));

        // When: Create subscription
        CalendarSubscriptionResponse response = subscriptionService.createSubscription(TEST_GROUP_ID, TEST_USER_ID);

        // Then: Token is generated and membership is saved
        ArgumentCaptor<GroupMembership> captor = ArgumentCaptor.forClass(GroupMembership.class);
        verify(groupRepository).findMembership(TEST_GROUP_ID, TEST_USER_ID);
        verify(groupRepository).addMember(captor.capture());

        GroupMembership savedMembership = captor.getValue();
        assertThat(savedMembership.getCalendarToken()).isNotNull();
        assertThat(savedMembership.getCalendarToken()).hasSize(36); // UUID format
        assertThat(savedMembership.getCalendarToken()).matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");

        // Verify response fields
        assertThat(response.getSubscriptionId()).isEqualTo(TEST_GROUP_ID);
        assertThat(response.getGroupId()).isEqualTo(TEST_GROUP_ID);
        assertThat(response.getGroupName()).isEqualTo(TEST_GROUP_NAME);
        assertThat(response.getCreatedAt()).isEqualTo(TEST_CREATED_AT);
        assertThat(response.getSubscriptionUrl()).contains(savedMembership.getCalendarToken());
        assertThat(response.getWebcalUrl()).contains(savedMembership.getCalendarToken());
    }

    @Test
    void createSubscription_WhenAlreadySubscribed_ReturnsExistingToken() {
        // Given: User already has a calendar subscription
        String existingToken = "f23e4567-e89b-12d3-a456-426614174016";
        GroupMembership membership = createMembership(TEST_GROUP_ID, TEST_USER_ID, TEST_GROUP_NAME, existingToken);
        when(groupRepository.findMembership(TEST_GROUP_ID, TEST_USER_ID))
            .thenReturn(Optional.of(membership));

        // When: Create subscription again
        CalendarSubscriptionResponse response = subscriptionService.createSubscription(TEST_GROUP_ID, TEST_USER_ID);

        // Then: Existing token is returned, no update made
        verify(groupRepository).findMembership(TEST_GROUP_ID, TEST_USER_ID);
        verify(groupRepository, never()).addMember(any());

        // Verify response contains existing token
        assertThat(response.getSubscriptionUrl()).contains(existingToken);
        assertThat(response.getWebcalUrl()).contains(existingToken);
        assertThat(response.getGroupId()).isEqualTo(TEST_GROUP_ID);
    }

    @Test
    void createSubscription_GeneratesCorrectURLFormats() {
        // Given: User is a member
        String groupId = "567e8901-e89b-12d3-a456-426614174004";
        GroupMembership membership = createMembership(groupId, TEST_USER_ID, TEST_GROUP_NAME, null);
        when(groupRepository.findMembership(groupId, TEST_USER_ID))
            .thenReturn(Optional.of(membership));

        // When: Create subscription
        CalendarSubscriptionResponse response = subscriptionService.createSubscription(groupId, TEST_USER_ID);

        // Then: URLs are correctly formatted
        ArgumentCaptor<GroupMembership> captor = ArgumentCaptor.forClass(GroupMembership.class);
        verify(groupRepository).addMember(captor.capture());
        String token = captor.getValue().getCalendarToken();

        String expectedSubscriptionUrl = String.format("%s/calendar/feed/%s/%s", TEST_BASE_URL, groupId, token);
        String expectedWebcalUrl = expectedSubscriptionUrl.replace("https://", "webcal://");

        assertThat(response.getSubscriptionUrl()).isEqualTo(expectedSubscriptionUrl);
        assertThat(response.getWebcalUrl()).isEqualTo(expectedWebcalUrl);
        assertThat(response.getSubscriptionUrl()).startsWith("https://");
        assertThat(response.getWebcalUrl()).startsWith("webcal://");
    }

    @Test
    void createSubscription_WithHTTPBaseUrl_WebcalURLConvertsCorrectly() {
        // Given: Service with HTTP base URL
        CalendarSubscriptionServiceImpl httpService = new CalendarSubscriptionServiceImpl(
            groupRepository,
            hangoutRepository,
            iCalendarService,
            "http://test.inviter.app"
        );
        GroupMembership membership = createMembership(TEST_GROUP_ID, TEST_USER_ID, TEST_GROUP_NAME, null);
        when(groupRepository.findMembership(TEST_GROUP_ID, TEST_USER_ID))
            .thenReturn(Optional.of(membership));

        // When: Create subscription
        CalendarSubscriptionResponse response = httpService.createSubscription(TEST_GROUP_ID, TEST_USER_ID);

        // Then: Webcal URL uses webcal:// protocol
        assertThat(response.getSubscriptionUrl()).startsWith("http://");
        assertThat(response.getWebcalUrl()).startsWith("webcal://");
        assertThat(response.getWebcalUrl()).doesNotContain("http://");
    }

    // ===== TESTS FOR getUserSubscriptions() =====

    @Test
    void getUserSubscriptions_WithNoSubscriptions_ReturnsEmptyList() {
        // Given: User has memberships but no calendar subscriptions
        String group1Id = "678e9012-e89b-12d3-a456-426614174005";
        String group2Id = "789e0123-e89b-12d3-a456-426614174006";
        GroupMembership membership1 = createMembership(group1Id, TEST_USER_ID, "Group 1", null);
        GroupMembership membership2 = createMembership(group2Id, TEST_USER_ID, "Group 2", null);
        when(groupRepository.findGroupsByUserId(TEST_USER_ID))
            .thenReturn(Arrays.asList(membership1, membership2));

        // When: Get user subscriptions
        CalendarSubscriptionListResponse response = subscriptionService.getUserSubscriptions(TEST_USER_ID);

        // Then: Empty list is returned
        verify(groupRepository).findGroupsByUserId(TEST_USER_ID);
        assertThat(response.getSubscriptions()).isEmpty();
        assertThat(response.getSubscriptions()).hasSize(0);
    }

    @Test
    void getUserSubscriptions_WithMultipleSubscriptions_ReturnsAll() {
        // Given: User has 4 memberships, 2 with calendar subscriptions
        String group1Id = "890e1234-e89b-12d3-a456-426614174007";
        String group2Id = "901e2345-e89b-12d3-a456-426614174008";
        String group3Id = "012e3456-e89b-12d3-a456-426614174009";
        String group4Id = "123e4567-e89b-12d3-a456-426614174010";
        String token1 = "a23e4567-e89b-12d3-a456-426614174011";
        String token3 = "b23e4567-e89b-12d3-a456-426614174012";

        GroupMembership membership1 = createMembership(group1Id, TEST_USER_ID, "Group 1", token1);
        GroupMembership membership2 = createMembership(group2Id, TEST_USER_ID, "Group 2", null);
        GroupMembership membership3 = createMembership(group3Id, TEST_USER_ID, "Group 3", token3);
        GroupMembership membership4 = createMembership(group4Id, TEST_USER_ID, "Group 4", null);

        when(groupRepository.findGroupsByUserId(TEST_USER_ID))
            .thenReturn(Arrays.asList(membership1, membership2, membership3, membership4));

        // When: Get user subscriptions
        CalendarSubscriptionListResponse response = subscriptionService.getUserSubscriptions(TEST_USER_ID);

        // Then: Only subscriptions with tokens are returned
        verify(groupRepository).findGroupsByUserId(TEST_USER_ID);
        assertThat(response.getSubscriptions()).hasSize(2);

        List<String> groupIds = response.getSubscriptions().stream()
            .map(CalendarSubscriptionResponse::getGroupId)
            .toList();
        assertThat(groupIds).containsExactlyInAnyOrder(group1Id, group3Id);

        // Verify tokens are included in URLs
        assertThat(response.getSubscriptions().get(0).getSubscriptionUrl()).containsAnyOf(token1, token3);
        assertThat(response.getSubscriptions().get(1).getSubscriptionUrl()).containsAnyOf(token1, token3);
    }

    @Test
    void getUserSubscriptions_WithNoMemberships_ReturnsEmptyList() {
        // Given: User is not in any groups
        when(groupRepository.findGroupsByUserId(TEST_USER_ID))
            .thenReturn(Collections.emptyList());

        // When: Get user subscriptions
        CalendarSubscriptionListResponse response = subscriptionService.getUserSubscriptions(TEST_USER_ID);

        // Then: Empty list is returned without errors
        verify(groupRepository).findGroupsByUserId(TEST_USER_ID);
        assertThat(response.getSubscriptions()).isEmpty();
        assertThat(response.getSubscriptions()).hasSize(0);
    }

    // ===== TESTS FOR deleteSubscription() =====

    @Test
    void deleteSubscription_WhenNotMember_ThrowsNotFoundException() {
        // Given: User is not a member of the group
        when(groupRepository.findMembership(TEST_GROUP_ID, TEST_USER_ID))
            .thenReturn(Optional.empty());

        // When/Then: NotFoundException is thrown
        assertThatThrownBy(() -> subscriptionService.deleteSubscription(TEST_GROUP_ID, TEST_USER_ID))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Subscription not found");

        // Verify no update was made
        verify(groupRepository).findMembership(TEST_GROUP_ID, TEST_USER_ID);
        verify(groupRepository, never()).addMember(any());
    }

    @Test
    void deleteSubscription_WhenNoToken_ThrowsNotFoundException() {
        // Given: User is a member but has no calendar subscription
        GroupMembership membership = createMembership(TEST_GROUP_ID, TEST_USER_ID, TEST_GROUP_NAME, null);
        when(groupRepository.findMembership(TEST_GROUP_ID, TEST_USER_ID))
            .thenReturn(Optional.of(membership));

        // When/Then: NotFoundException is thrown
        assertThatThrownBy(() -> subscriptionService.deleteSubscription(TEST_GROUP_ID, TEST_USER_ID))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Subscription not found");

        // Verify no update was made
        verify(groupRepository).findMembership(TEST_GROUP_ID, TEST_USER_ID);
        verify(groupRepository, never()).addMember(any());
    }

    @Test
    void deleteSubscription_WhenTokenExists_ClearsToken() {
        // Given: User has a calendar subscription
        String existingToken = "c23e4567-e89b-12d3-a456-426614174013";
        GroupMembership membership = createMembership(TEST_GROUP_ID, TEST_USER_ID, TEST_GROUP_NAME, existingToken);
        when(groupRepository.findMembership(TEST_GROUP_ID, TEST_USER_ID))
            .thenReturn(Optional.of(membership));

        // When: Delete subscription
        subscriptionService.deleteSubscription(TEST_GROUP_ID, TEST_USER_ID);

        // Then: Token is cleared and membership is saved
        ArgumentCaptor<GroupMembership> captor = ArgumentCaptor.forClass(GroupMembership.class);
        verify(groupRepository).findMembership(TEST_GROUP_ID, TEST_USER_ID);
        verify(groupRepository).addMember(captor.capture());

        GroupMembership savedMembership = captor.getValue();
        assertThat(savedMembership.getCalendarToken()).isNull();
    }

    // ===== TEST FOR toResponse() (via createSubscription) =====

    @Test
    void toResponse_GeneratesCorrectResponseFields() {
        // Given: Membership with all fields populated
        String groupId = "d23e4567-e89b-12d3-a456-426614174014";
        String groupName = "Adventure Club";
        String token = "e23e4567-e89b-12d3-a456-426614174015";
        Instant createdAt = Instant.parse("2023-10-21T10:00:00Z");

        GroupMembership membership = createMembership(groupId, TEST_USER_ID, groupName, token);
        membership.setCreatedAt(createdAt);

        when(groupRepository.findMembership(groupId, TEST_USER_ID))
            .thenReturn(Optional.of(membership));

        // When: Create subscription (which calls toResponse internally)
        CalendarSubscriptionResponse response = subscriptionService.createSubscription(groupId, TEST_USER_ID);

        // Then: All response fields are correctly populated
        assertThat(response.getSubscriptionId()).isEqualTo(groupId);
        assertThat(response.getGroupId()).isEqualTo(groupId);
        assertThat(response.getGroupName()).isEqualTo(groupName);
        assertThat(response.getSubscriptionUrl()).isEqualTo(String.format("https://test.inviter.app/calendar/feed/%s/%s", groupId, token));
        assertThat(response.getWebcalUrl()).isEqualTo(String.format("webcal://test.inviter.app/calendar/feed/%s/%s", groupId, token));
        assertThat(response.getCreatedAt()).isEqualTo(createdAt);
    }

    // ===== TESTS FOR getCalendarFeed() =====

    @Test
    void getCalendarFeed_WithValidToken_Returns200WithICS() {
        // Given
        String groupId = "123e4567-e89b-12d3-a456-426614174000";
        String token = "valid-token-123";
        GroupMembership membership = createMembership(groupId, TEST_USER_ID, TEST_GROUP_NAME, token);
        Group group = createGroup(groupId, Instant.ofEpochMilli(1234567890000L));
        List<HangoutPointer> hangouts = createHangoutPointers(3);
        String icsContent = "BEGIN:VCALENDAR\nVERSION:2.0\nEND:VCALENDAR";

        when(groupRepository.findMembershipByToken(token)).thenReturn(Optional.of(membership));
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(hangoutRepository.getFutureEventsPage(eq(groupId), anyLong(), eq(100), isNull()))
            .thenReturn(new PaginatedResult<>(castToBaseItems(hangouts), null));
        when(iCalendarService.generateICS(eq(group), anyList())).thenReturn(icsContent);

        // When
        ResponseEntity<String> response = subscriptionService.getCalendarFeed(groupId, token, null);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(icsContent);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"123e4567-e89b-12d3-a456-426614174000-1234567890000\"");
        assertThat(response.getHeaders().getCacheControl()).contains("max-age=7200");
        assertThat(response.getHeaders().getCacheControl()).contains("public");
        assertThat(response.getHeaders().getCacheControl()).contains("must-revalidate");

        verify(groupRepository).findMembershipByToken(token);
        verify(groupRepository).findById(groupId);
        verify(hangoutRepository).getFutureEventsPage(eq(groupId), anyLong(), eq(100), isNull());
        verify(iCalendarService).generateICS(eq(group), anyList());
    }

    @Test
    void getCalendarFeed_WithInvalidToken_ThrowsUnauthorizedException() {
        // Given
        String invalidToken = "invalid-token";
        when(groupRepository.findMembershipByToken(invalidToken)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> subscriptionService.getCalendarFeed(TEST_GROUP_ID, invalidToken, null))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("Invalid subscription token");

        verify(groupRepository).findMembershipByToken(invalidToken);
        verify(iCalendarService, never()).generateICS(any(), anyList());
    }

    @Test
    void getCalendarFeed_WithMatchingETag_Returns304() {
        // Given
        String groupId = "456e7890-e89b-12d3-a456-426614174001";
        String token = "valid-token-456";
        GroupMembership membership = createMembership(groupId, TEST_USER_ID, TEST_GROUP_NAME, token);
        Group group = createGroup(groupId, Instant.ofEpochMilli(1234567890000L));
        String etag = "\"456e7890-e89b-12d3-a456-426614174001-1234567890000\"";

        when(groupRepository.findMembershipByToken(token)).thenReturn(Optional.of(membership));
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));

        // When
        ResponseEntity<String> response = subscriptionService.getCalendarFeed(groupId, token, etag);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(response.getBody()).isNull();
        assertThat(response.getHeaders().getETag()).isEqualTo(etag);

        verify(groupRepository).findMembershipByToken(token);
        verify(groupRepository).findById(groupId);
        verify(iCalendarService, never()).generateICS(any(), anyList());
    }

    @Test
    void getCalendarFeed_WithNonMatchingETag_Returns200WithNewContent() {
        // Given
        String groupId = "789e0123-e89b-12d3-a456-426614174002";
        String token = "valid-token-789";
        GroupMembership membership = createMembership(groupId, TEST_USER_ID, TEST_GROUP_NAME, token);
        Group group = createGroup(groupId, Instant.ofEpochMilli(9999999999000L));
        List<HangoutPointer> hangouts = createHangoutPointers(2);
        String icsContent = "BEGIN:VCALENDAR\nVERSION:2.0\nEND:VCALENDAR";
        String oldEtag = "\"789e0123-e89b-12d3-a456-426614174002-1234567890000\"";
        String newEtag = "\"789e0123-e89b-12d3-a456-426614174002-9999999999000\"";

        when(groupRepository.findMembershipByToken(token)).thenReturn(Optional.of(membership));
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(hangoutRepository.getFutureEventsPage(eq(groupId), anyLong(), eq(100), isNull()))
            .thenReturn(new PaginatedResult<>(castToBaseItems(hangouts), null));
        when(iCalendarService.generateICS(eq(group), anyList())).thenReturn(icsContent);

        // When
        ResponseEntity<String> response = subscriptionService.getCalendarFeed(groupId, token, oldEtag);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getETag()).isEqualTo(newEtag);
        assertThat(response.getBody()).isEqualTo(icsContent);

        verify(iCalendarService).generateICS(eq(group), anyList());
    }

    @Test
    void getCalendarFeed_WithGroupNotFound_ThrowsForbiddenException() {
        // Given
        String token = "valid-token-abc";
        GroupMembership membership = createMembership(TEST_GROUP_ID, TEST_USER_ID, TEST_GROUP_NAME, token);

        when(groupRepository.findMembershipByToken(token)).thenReturn(Optional.of(membership));
        when(groupRepository.findById(TEST_GROUP_ID)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> subscriptionService.getCalendarFeed(TEST_GROUP_ID, token, null))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("Group not found");

        verify(groupRepository).findMembershipByToken(token);
        verify(groupRepository).findById(TEST_GROUP_ID);
    }

    @Test
    void getCalendarFeed_WithNullLastHangoutModified_UsesZeroInETag() {
        // Given
        String groupId = "def01234-e89b-12d3-a456-426614174003";
        String token = "valid-token-def";
        GroupMembership membership = createMembership(groupId, TEST_USER_ID, TEST_GROUP_NAME, token);
        Group group = createGroup(groupId, null); // null lastHangoutModified
        String icsContent = "BEGIN:VCALENDAR\nVERSION:2.0\nEND:VCALENDAR";

        when(groupRepository.findMembershipByToken(token)).thenReturn(Optional.of(membership));
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(hangoutRepository.getFutureEventsPage(eq(groupId), anyLong(), eq(100), isNull()))
            .thenReturn(new PaginatedResult<>(Collections.emptyList(), null));
        when(iCalendarService.generateICS(eq(group), anyList())).thenReturn(icsContent);

        // When
        ResponseEntity<String> response = subscriptionService.getCalendarFeed(groupId, token, null);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getETag()).isEqualTo("\"def01234-e89b-12d3-a456-426614174003-0\"");
        assertThat(response.getBody()).isEqualTo(icsContent);

        verify(iCalendarService).generateICS(eq(group), anyList());
    }

    @Test
    void getCalendarFeed_WithPaginatedHangouts_FetchesAllPages() {
        // Given
        String groupId = "ab567890-1234-5678-9abc-def012345678";
        String token = "valid-token-ghi";
        GroupMembership membership = createMembership(groupId, TEST_USER_ID, TEST_GROUP_NAME, token);
        Group group = createGroup(groupId, Instant.ofEpochMilli(1234567890000L));

        // First page: 100 items with nextToken
        List<HangoutPointer> page1 = createHangoutPointers(100);
        String nextToken = "next-token-123";

        // Second page: 50 items, no nextToken
        List<HangoutPointer> page2 = createHangoutPointers(50);

        String icsContent = "BEGIN:VCALENDAR\nVERSION:2.0\nEND:VCALENDAR";

        when(groupRepository.findMembershipByToken(token)).thenReturn(Optional.of(membership));
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(hangoutRepository.getFutureEventsPage(eq(groupId), anyLong(), eq(100), isNull()))
            .thenReturn(new PaginatedResult<>(castToBaseItems(page1), nextToken));
        when(hangoutRepository.getFutureEventsPage(eq(groupId), anyLong(), eq(100), eq(nextToken)))
            .thenReturn(new PaginatedResult<>(castToBaseItems(page2), null));
        when(iCalendarService.generateICS(eq(group), argThat(list -> list.size() == 150)))
            .thenReturn(icsContent);

        // When
        ResponseEntity<String> response = subscriptionService.getCalendarFeed(groupId, token, null);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(icsContent);

        verify(hangoutRepository).getFutureEventsPage(eq(groupId), anyLong(), eq(100), isNull());
        verify(hangoutRepository).getFutureEventsPage(eq(groupId), anyLong(), eq(100), eq(nextToken));
        verify(iCalendarService).generateICS(eq(group), argThat(list -> list.size() == 150));
    }

    @Test
    void getCalendarFeed_TokenMismatchesGroup_ThrowsUnauthorizedException() {
        // Given
        String actualGroupId = "999e9999-e89b-12d3-a456-426614174999";
        String requestedGroupId = "000e0000-e89b-12d3-a456-426614174000";
        String token = "valid-token-jkl";
        GroupMembership membership = createMembership(actualGroupId, TEST_USER_ID, TEST_GROUP_NAME, token);

        when(groupRepository.findMembershipByToken(token)).thenReturn(Optional.of(membership));

        // When/Then
        assertThatThrownBy(() -> subscriptionService.getCalendarFeed(requestedGroupId, token, null))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("Token does not match group");

        verify(groupRepository).findMembershipByToken(token);
    }

    // ===== HELPER METHODS =====

    /**
     * Create a test GroupMembership with the specified parameters.
     */
    private GroupMembership createMembership(String groupId, String userId, String groupName, String calendarToken) {
        GroupMembership membership = new GroupMembership(groupId, userId, groupName);
        membership.setCreatedAt(TEST_CREATED_AT);
        if (calendarToken != null) {
            membership.setCalendarToken(calendarToken);
        }
        return membership;
    }

    private Group createGroup(String groupId, Instant lastHangoutModified) {
        Group group = new Group();
        group.setGroupId(groupId);
        group.setGroupName(TEST_GROUP_NAME);
        group.setLastHangoutModified(lastHangoutModified);
        return group;
    }

    private List<HangoutPointer> createHangoutPointers(int count) {
        List<HangoutPointer> pointers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            HangoutPointer pointer = new HangoutPointer();
            pointer.setHangoutId("hangout-" + i);
            pointer.setTitle("Hangout " + i);
            pointers.add(pointer);
        }
        return pointers;
    }

    private List<BaseItem> castToBaseItems(List<HangoutPointer> pointers) {
        return new ArrayList<>(pointers);
    }
}
