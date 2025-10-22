package com.bbthechange.inviter.controller;

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
import com.bbthechange.inviter.service.CalendarSubscriptionService;
import com.bbthechange.inviter.service.ICalendarService;
import com.bbthechange.inviter.util.PaginatedResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for CalendarFeedController
 *
 * Test Coverage:
 * - POST /v1/calendar/subscriptions/{groupId} - Create subscription (2 tests)
 * - GET /v1/calendar/subscriptions - List subscriptions (2 tests)
 * - DELETE /v1/calendar/subscriptions/{groupId} - Delete subscription (2 tests)
 * - GET /v1/calendar/subscribe/{groupId}/{token} - Calendar feed (9 tests)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CalendarFeedController Tests")
class CalendarFeedControllerTest {

    @Mock
    private CalendarSubscriptionService subscriptionService;

    @Mock
    private ICalendarService iCalendarService;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private HangoutRepository hangoutRepository;

    @Mock
    private HttpServletRequest httpServletRequest;

    private CalendarFeedController controller;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final String TEST_USER_ID = "user-123";
    private static final String TEST_GROUP_ID = "group-456";
    private static final String TEST_TOKEN = "valid-token-uuid";
    private static final String TEST_GROUP_NAME = "Test Group";

    @BeforeEach
    void setUp() {
        // Override extractUserId to return test user ID
        controller = new CalendarFeedController(subscriptionService, iCalendarService,
                                                groupRepository, hangoutRepository) {
            @Override
            protected String extractUserId(HttpServletRequest request) {
                return TEST_USER_ID;
            }
        };

        // Add StringHttpMessageConverter to handle text/calendar content type
        StringHttpMessageConverter stringConverter = new StringHttpMessageConverter(StandardCharsets.UTF_8);
        stringConverter.setWriteAcceptCharset(false);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(), stringConverter)
                .build();
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("POST /v1/calendar/subscriptions/{groupId}")
    class CreateSubscription {

        @Test
        @DisplayName("When authenticated, returns 201 with subscription")
        void createSubscription_WhenAuthenticated_Returns201WithSubscription() throws Exception {
            // Given
            CalendarSubscriptionResponse expectedResponse = new CalendarSubscriptionResponse(
                "sub-123",
                TEST_GROUP_ID,
                TEST_GROUP_NAME,
                "https://example.com/calendar/subscribe/group-456/token",
                "webcal://example.com/calendar/subscribe/group-456/token",
                Instant.now()
            );

            when(subscriptionService.createSubscription(TEST_GROUP_ID, TEST_USER_ID))
                .thenReturn(expectedResponse);

            // When/Then
            mockMvc.perform(post("/v1/calendar/subscriptions/" + TEST_GROUP_ID)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.subscriptionId").value("sub-123"))
                    .andExpect(jsonPath("$.groupId").value(TEST_GROUP_ID))
                    .andExpect(jsonPath("$.groupName").value(TEST_GROUP_NAME))
                    .andExpect(jsonPath("$.subscriptionUrl").value("https://example.com/calendar/subscribe/group-456/token"))
                    .andExpect(jsonPath("$.webcalUrl").value("webcal://example.com/calendar/subscribe/group-456/token"));

            verify(subscriptionService).createSubscription(TEST_GROUP_ID, TEST_USER_ID);
        }

        @Test
        @DisplayName("When not member, returns 403")
        void createSubscription_WhenNotMember_Returns403() throws Exception {
            // Given
            when(subscriptionService.createSubscription(TEST_GROUP_ID, TEST_USER_ID))
                .thenThrow(new ForbiddenException("User is not a member of this group"));

            // When/Then
            mockMvc.perform(post("/v1/calendar/subscriptions/" + TEST_GROUP_ID)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());

            verify(subscriptionService).createSubscription(TEST_GROUP_ID, TEST_USER_ID);
        }
    }

    @Nested
    @DisplayName("GET /v1/calendar/subscriptions")
    class ListSubscriptions {

        @Test
        @DisplayName("Returns all user subscriptions")
        void listSubscriptions_ReturnsAllUserSubscriptions() throws Exception {
            // Given
            CalendarSubscriptionResponse sub1 = new CalendarSubscriptionResponse(
                "sub-1", "group-1", "Group 1", "https://example.com/1", "webcal://example.com/1", Instant.now()
            );
            CalendarSubscriptionResponse sub2 = new CalendarSubscriptionResponse(
                "sub-2", "group-2", "Group 2", "https://example.com/2", "webcal://example.com/2", Instant.now()
            );
            CalendarSubscriptionListResponse response = new CalendarSubscriptionListResponse(Arrays.asList(sub1, sub2));

            when(subscriptionService.getUserSubscriptions(TEST_USER_ID))
                .thenReturn(response);

            // When/Then
            mockMvc.perform(get("/v1/calendar/subscriptions")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.subscriptions").isArray())
                    .andExpect(jsonPath("$.subscriptions.length()").value(2))
                    .andExpect(jsonPath("$.subscriptions[0].subscriptionId").value("sub-1"))
                    .andExpect(jsonPath("$.subscriptions[1].subscriptionId").value("sub-2"));

            verify(subscriptionService).getUserSubscriptions(TEST_USER_ID);
        }

        @Test
        @DisplayName("With no subscriptions, returns empty list")
        void listSubscriptions_WithNoSubscriptions_ReturnsEmptyList() throws Exception {
            // Given
            CalendarSubscriptionListResponse response = new CalendarSubscriptionListResponse(Collections.emptyList());

            when(subscriptionService.getUserSubscriptions(TEST_USER_ID))
                .thenReturn(response);

            // When/Then
            mockMvc.perform(get("/v1/calendar/subscriptions")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.subscriptions").isArray())
                    .andExpect(jsonPath("$.subscriptions.length()").value(0));

            verify(subscriptionService).getUserSubscriptions(TEST_USER_ID);
        }
    }

    @Nested
    @DisplayName("DELETE /v1/calendar/subscriptions/{groupId}")
    class DeleteSubscription {

        @Test
        @DisplayName("When exists, returns 204")
        void deleteSubscription_WhenExists_Returns204() throws Exception {
            // Given
            doNothing().when(subscriptionService).deleteSubscription(TEST_GROUP_ID, TEST_USER_ID);

            // When/Then
            mockMvc.perform(delete("/v1/calendar/subscriptions/" + TEST_GROUP_ID)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));

            verify(subscriptionService).deleteSubscription(TEST_GROUP_ID, TEST_USER_ID);
        }

        @Test
        @DisplayName("When not found, returns 404")
        void deleteSubscription_WhenNotFound_Returns404() throws Exception {
            // Given
            doThrow(new NotFoundException("Subscription not found"))
                .when(subscriptionService).deleteSubscription(TEST_GROUP_ID, TEST_USER_ID);

            // When/Then
            mockMvc.perform(delete("/v1/calendar/subscriptions/" + TEST_GROUP_ID)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());

            verify(subscriptionService).deleteSubscription(TEST_GROUP_ID, TEST_USER_ID);
        }
    }

    @Nested
    @DisplayName("GET /v1/calendar/subscribe/{groupId}/{token}")
    class GetCalendarFeed {

        @Test
        @DisplayName("With valid token, returns 200 with ICS")
        void getCalendarFeed_WithValidToken_Returns200WithICS() throws Exception {
            // Given
            GroupMembership membership = createMembership(TEST_USER_ID, TEST_GROUP_ID, TEST_TOKEN);
            Group group = createGroup(TEST_GROUP_ID, Instant.ofEpochMilli(1234567890000L));
            List<HangoutPointer> hangouts = createHangoutPointers(3);
            String icsContent = "BEGIN:VCALENDAR\nVERSION:2.0\nEND:VCALENDAR";

            when(groupRepository.findMembershipByToken(TEST_TOKEN))
                .thenReturn(Optional.of(membership));
            when(groupRepository.findById(TEST_GROUP_ID))
                .thenReturn(Optional.of(group));
            when(hangoutRepository.getFutureEventsPage(eq(TEST_GROUP_ID), anyLong(), eq(100), isNull()))
                .thenReturn(new PaginatedResult<>(castToBaseItems(hangouts), null));
            when(iCalendarService.generateICS(eq(group), anyList()))
                .thenReturn(icsContent);

            // When/Then
            mockMvc.perform(get("/v1/calendar/subscribe/" + TEST_GROUP_ID + "/" + TEST_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("text/calendar;charset=UTF-8"))
                    .andExpect(header().string("Cache-Control", "max-age=7200, must-revalidate, public"))
                    .andExpect(header().string("ETag", "\"group-456-1234567890000\""))
                    .andExpect(content().string(icsContent));

            verify(groupRepository).findMembershipByToken(TEST_TOKEN);
            verify(groupRepository).findById(TEST_GROUP_ID);
            verify(hangoutRepository).getFutureEventsPage(eq(TEST_GROUP_ID), anyLong(), eq(100), isNull());
            verify(iCalendarService).generateICS(eq(group), anyList());
        }

        @Test
        @DisplayName("With invalid token, returns 403")
        void getCalendarFeed_WithInvalidToken_Returns403() throws Exception {
            // Given
            when(groupRepository.findMembershipByToken("invalid-token"))
                .thenReturn(Optional.empty());

            // When/Then
            mockMvc.perform(get("/v1/calendar/subscribe/" + TEST_GROUP_ID + "/invalid-token"))
                    .andExpect(status().isForbidden());

            verify(groupRepository).findMembershipByToken("invalid-token");
            verify(iCalendarService, never()).generateICS(any(), anyList());
        }

        @Test
        @DisplayName("With matching ETag, returns 304")
        void getCalendarFeed_WithMatchingETag_Returns304() throws Exception {
            // Given
            GroupMembership membership = createMembership(TEST_USER_ID, TEST_GROUP_ID, TEST_TOKEN);
            Group group = createGroup(TEST_GROUP_ID, Instant.ofEpochMilli(1234567890000L));
            String etag = "\"group-456-1234567890000\"";

            when(groupRepository.findMembershipByToken(TEST_TOKEN))
                .thenReturn(Optional.of(membership));
            when(groupRepository.findById(TEST_GROUP_ID))
                .thenReturn(Optional.of(group));

            // When/Then
            mockMvc.perform(get("/v1/calendar/subscribe/" + TEST_GROUP_ID + "/" + TEST_TOKEN)
                    .header("If-None-Match", etag))
                    .andExpect(status().isNotModified())
                    .andExpect(header().string("ETag", etag))
                    .andExpect(header().string("Cache-Control", "max-age=7200, must-revalidate, public"))
                    .andExpect(content().string(""));

            verify(groupRepository).findMembershipByToken(TEST_TOKEN);
            verify(groupRepository).findById(TEST_GROUP_ID);
            verify(iCalendarService, never()).generateICS(any(), anyList());
        }

        @Test
        @DisplayName("With non-matching ETag, returns 200 with new content")
        void getCalendarFeed_WithNonMatchingETag_Returns200WithNewContent() throws Exception {
            // Given
            GroupMembership membership = createMembership(TEST_USER_ID, TEST_GROUP_ID, TEST_TOKEN);
            Group group = createGroup(TEST_GROUP_ID, Instant.ofEpochMilli(9999999999000L));
            List<HangoutPointer> hangouts = createHangoutPointers(2);
            String icsContent = "BEGIN:VCALENDAR\nVERSION:2.0\nEND:VCALENDAR";
            String oldEtag = "\"group-456-1234567890000\"";
            String newEtag = "\"group-456-9999999999000\"";

            when(groupRepository.findMembershipByToken(TEST_TOKEN))
                .thenReturn(Optional.of(membership));
            when(groupRepository.findById(TEST_GROUP_ID))
                .thenReturn(Optional.of(group));
            when(hangoutRepository.getFutureEventsPage(eq(TEST_GROUP_ID), anyLong(), eq(100), isNull()))
                .thenReturn(new PaginatedResult<>(castToBaseItems(hangouts), null));
            when(iCalendarService.generateICS(eq(group), anyList()))
                .thenReturn(icsContent);

            // When/Then
            mockMvc.perform(get("/v1/calendar/subscribe/" + TEST_GROUP_ID + "/" + TEST_TOKEN)
                    .header("If-None-Match", oldEtag))
                    .andExpect(status().isOk())
                    .andExpect(header().string("ETag", newEtag))
                    .andExpect(content().string(icsContent));

            verify(iCalendarService).generateICS(eq(group), anyList());
        }

        @Test
        @DisplayName("With no ETag, returns 200 with content")
        void getCalendarFeed_WithNoETag_Returns200WithContent() throws Exception {
            // Given
            GroupMembership membership = createMembership(TEST_USER_ID, TEST_GROUP_ID, TEST_TOKEN);
            Group group = createGroup(TEST_GROUP_ID, Instant.ofEpochMilli(1234567890000L));
            List<HangoutPointer> hangouts = createHangoutPointers(1);
            String icsContent = "BEGIN:VCALENDAR\nVERSION:2.0\nEND:VCALENDAR";

            when(groupRepository.findMembershipByToken(TEST_TOKEN))
                .thenReturn(Optional.of(membership));
            when(groupRepository.findById(TEST_GROUP_ID))
                .thenReturn(Optional.of(group));
            when(hangoutRepository.getFutureEventsPage(eq(TEST_GROUP_ID), anyLong(), eq(100), isNull()))
                .thenReturn(new PaginatedResult<>(castToBaseItems(hangouts), null));
            when(iCalendarService.generateICS(eq(group), anyList()))
                .thenReturn(icsContent);

            // When/Then
            mockMvc.perform(get("/v1/calendar/subscribe/" + TEST_GROUP_ID + "/" + TEST_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("ETag"))
                    .andExpect(header().exists("Cache-Control"))
                    .andExpect(content().string(icsContent));

            verify(iCalendarService).generateICS(eq(group), anyList());
        }

        @Test
        @DisplayName("With group not found, returns 403")
        void getCalendarFeed_WithGroupNotFound_Returns403() throws Exception {
            // Given
            GroupMembership membership = createMembership(TEST_USER_ID, TEST_GROUP_ID, TEST_TOKEN);

            when(groupRepository.findMembershipByToken(TEST_TOKEN))
                .thenReturn(Optional.of(membership));
            when(groupRepository.findById(TEST_GROUP_ID))
                .thenReturn(Optional.empty());

            // When/Then
            mockMvc.perform(get("/v1/calendar/subscribe/" + TEST_GROUP_ID + "/" + TEST_TOKEN))
                    .andExpect(status().isForbidden());

            verify(groupRepository).findMembershipByToken(TEST_TOKEN);
            verify(groupRepository).findById(TEST_GROUP_ID);
        }

        @Test
        @DisplayName("With null lastHangoutModified, uses zero in ETag")
        void getCalendarFeed_WithNullLastHangoutModified_UsesZeroInETag() throws Exception {
            // Given
            GroupMembership membership = createMembership(TEST_USER_ID, TEST_GROUP_ID, TEST_TOKEN);
            Group group = createGroup(TEST_GROUP_ID, null); // null lastHangoutModified
            String icsContent = "BEGIN:VCALENDAR\nVERSION:2.0\nEND:VCALENDAR";

            when(groupRepository.findMembershipByToken(TEST_TOKEN))
                .thenReturn(Optional.of(membership));
            when(groupRepository.findById(TEST_GROUP_ID))
                .thenReturn(Optional.of(group));
            when(hangoutRepository.getFutureEventsPage(eq(TEST_GROUP_ID), anyLong(), eq(100), isNull()))
                .thenReturn(new PaginatedResult<>(Collections.emptyList(), null));
            when(iCalendarService.generateICS(eq(group), anyList()))
                .thenReturn(icsContent);

            // When/Then
            mockMvc.perform(get("/v1/calendar/subscribe/" + TEST_GROUP_ID + "/" + TEST_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(header().string("ETag", "\"group-456-0\""))
                    .andExpect(content().string(icsContent));

            verify(iCalendarService).generateICS(eq(group), anyList());
        }

        @Test
        @DisplayName("With paginated hangouts, fetches all pages")
        void getCalendarFeed_WithPaginatedHangouts_FetchesAllPages() throws Exception {
            // Given
            GroupMembership membership = createMembership(TEST_USER_ID, TEST_GROUP_ID, TEST_TOKEN);
            Group group = createGroup(TEST_GROUP_ID, Instant.ofEpochMilli(1234567890000L));

            // First page: 100 items with nextToken
            List<HangoutPointer> page1 = createHangoutPointers(100);
            String nextToken = "next-token-123";

            // Second page: 50 items, no nextToken
            List<HangoutPointer> page2 = createHangoutPointers(50);

            String icsContent = "BEGIN:VCALENDAR\nVERSION:2.0\nEND:VCALENDAR";

            when(groupRepository.findMembershipByToken(TEST_TOKEN))
                .thenReturn(Optional.of(membership));
            when(groupRepository.findById(TEST_GROUP_ID))
                .thenReturn(Optional.of(group));
            when(hangoutRepository.getFutureEventsPage(eq(TEST_GROUP_ID), anyLong(), eq(100), isNull()))
                .thenReturn(new PaginatedResult<>(castToBaseItems(page1), nextToken));
            when(hangoutRepository.getFutureEventsPage(eq(TEST_GROUP_ID), anyLong(), eq(100), eq(nextToken)))
                .thenReturn(new PaginatedResult<>(castToBaseItems(page2), null));
            when(iCalendarService.generateICS(eq(group), argThat(list -> list.size() == 150)))
                .thenReturn(icsContent);

            // When/Then
            mockMvc.perform(get("/v1/calendar/subscribe/" + TEST_GROUP_ID + "/" + TEST_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(content().string(icsContent));

            verify(hangoutRepository).getFutureEventsPage(eq(TEST_GROUP_ID), anyLong(), eq(100), isNull());
            verify(hangoutRepository).getFutureEventsPage(eq(TEST_GROUP_ID), anyLong(), eq(100), eq(nextToken));
            verify(iCalendarService).generateICS(eq(group), argThat(list -> list.size() == 150));
        }

        @Test
        @DisplayName("Token mismatches group, returns 403")
        void getCalendarFeed_TokenMismatchesGroup_Returns403() throws Exception {
            // Given
            GroupMembership membership = createMembership(TEST_USER_ID, "group-999", TEST_TOKEN);

            when(groupRepository.findMembershipByToken(TEST_TOKEN))
                .thenReturn(Optional.of(membership));

            // When/Then
            mockMvc.perform(get("/v1/calendar/subscribe/" + TEST_GROUP_ID + "/" + TEST_TOKEN))
                    .andExpect(status().isForbidden());

            verify(groupRepository).findMembershipByToken(TEST_TOKEN);
        }
    }

    // Helper methods
    private GroupMembership createMembership(String userId, String groupId, String token) {
        GroupMembership membership = new GroupMembership();
        membership.setUserId(userId);
        membership.setGroupId(groupId);
        membership.setCalendarToken(token);
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
        List<HangoutPointer> pointers = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            HangoutPointer pointer = new HangoutPointer();
            pointer.setHangoutId("hangout-" + i);
            pointer.setTitle("Hangout " + i);
            pointers.add(pointer);
        }
        return pointers;
    }

    private List<BaseItem> castToBaseItems(List<HangoutPointer> pointers) {
        return new java.util.ArrayList<>(pointers);
    }
}
