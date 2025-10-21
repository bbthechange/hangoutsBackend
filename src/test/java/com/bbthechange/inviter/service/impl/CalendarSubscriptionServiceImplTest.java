package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.CalendarSubscriptionListResponse;
import com.bbthechange.inviter.dto.CalendarSubscriptionResponse;
import com.bbthechange.inviter.exception.ForbiddenException;
import com.bbthechange.inviter.exception.NotFoundException;
import com.bbthechange.inviter.model.GroupMembership;
import com.bbthechange.inviter.repository.GroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CalendarSubscriptionServiceImpl.
 * Tests calendar subscription management, token generation, and URL formatting.
 */
@ExtendWith(MockitoExtension.class)
class CalendarSubscriptionServiceImplTest {

    @Mock
    private GroupRepository groupRepository;

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

        String expectedSubscriptionUrl = String.format("%s/v1/calendar/subscribe/%s/%s", TEST_BASE_URL, groupId, token);
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
        assertThat(response.getSubscriptionUrl()).isEqualTo(String.format("https://test.inviter.app/v1/calendar/subscribe/%s/%s", groupId, token));
        assertThat(response.getWebcalUrl()).isEqualTo(String.format("webcal://test.inviter.app/v1/calendar/subscribe/%s/%s", groupId, token));
        assertThat(response.getCreatedAt()).isEqualTo(createdAt);
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
}
