package com.bbthechange.inviter.controller;

import com.bbthechange.inviter.dto.CalendarSubscriptionListResponse;
import com.bbthechange.inviter.dto.CalendarSubscriptionResponse;
import com.bbthechange.inviter.exception.ForbiddenException;
import com.bbthechange.inviter.exception.NotFoundException;
import com.bbthechange.inviter.service.CalendarSubscriptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for CalendarFeedController
 *
 * Test Coverage:
 * - POST /calendar/subscriptions/{groupId} - Create subscription (2 tests)
 * - GET /calendar/subscriptions - List subscriptions (2 tests)
 * - DELETE /calendar/subscriptions/{groupId} - Delete subscription (2 tests)
 * - GET /calendar/feed/{groupId}/{token} - Calendar feed (3 tests)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CalendarFeedController Tests")
class CalendarFeedControllerTest {

    @Mock
    private CalendarSubscriptionService subscriptionService;

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
        controller = new CalendarFeedController(subscriptionService) {
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
    @DisplayName("POST /calendar/subscriptions/{groupId}")
    class CreateSubscription {

        @Test
        @DisplayName("When authenticated, returns 201 with subscription")
        void createSubscription_WhenAuthenticated_Returns201WithSubscription() throws Exception {
            // Given
            CalendarSubscriptionResponse expectedResponse = new CalendarSubscriptionResponse(
                "sub-123",
                TEST_GROUP_ID,
                TEST_GROUP_NAME,
                "https://example.com/calendar/feed/group-456/token",
                "webcal://example.com/calendar/feed/group-456/token",
                Instant.now()
            );

            when(subscriptionService.createSubscription(TEST_GROUP_ID, TEST_USER_ID))
                .thenReturn(expectedResponse);

            // When/Then
            mockMvc.perform(post("/calendar/subscriptions/" + TEST_GROUP_ID)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.subscriptionId").value("sub-123"))
                    .andExpect(jsonPath("$.groupId").value(TEST_GROUP_ID))
                    .andExpect(jsonPath("$.groupName").value(TEST_GROUP_NAME))
                    .andExpect(jsonPath("$.subscriptionUrl").value("https://example.com/calendar/feed/group-456/token"))
                    .andExpect(jsonPath("$.webcalUrl").value("webcal://example.com/calendar/feed/group-456/token"));

            verify(subscriptionService).createSubscription(TEST_GROUP_ID, TEST_USER_ID);
        }

        @Test
        @DisplayName("When not member, returns 403")
        void createSubscription_WhenNotMember_Returns403() throws Exception {
            // Given
            when(subscriptionService.createSubscription(TEST_GROUP_ID, TEST_USER_ID))
                .thenThrow(new ForbiddenException("User is not a member of this group"));

            // When/Then
            mockMvc.perform(post("/calendar/subscriptions/" + TEST_GROUP_ID)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());

            verify(subscriptionService).createSubscription(TEST_GROUP_ID, TEST_USER_ID);
        }
    }

    @Nested
    @DisplayName("GET /calendar/subscriptions")
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
            mockMvc.perform(get("/calendar/subscriptions")
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
            mockMvc.perform(get("/calendar/subscriptions")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.subscriptions").isArray())
                    .andExpect(jsonPath("$.subscriptions.length()").value(0));

            verify(subscriptionService).getUserSubscriptions(TEST_USER_ID);
        }
    }

    @Nested
    @DisplayName("DELETE /calendar/subscriptions/{groupId}")
    class DeleteSubscription {

        @Test
        @DisplayName("When exists, returns 204")
        void deleteSubscription_WhenExists_Returns204() throws Exception {
            // Given
            doNothing().when(subscriptionService).deleteSubscription(TEST_GROUP_ID, TEST_USER_ID);

            // When/Then
            mockMvc.perform(delete("/calendar/subscriptions/" + TEST_GROUP_ID)
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
            mockMvc.perform(delete("/calendar/subscriptions/" + TEST_GROUP_ID)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());

            verify(subscriptionService).deleteSubscription(TEST_GROUP_ID, TEST_USER_ID);
        }
    }

    @Nested
    @DisplayName("GET /calendar/feed/{groupId}/{token}")
    class GetCalendarFeed {

        @Test
        @DisplayName("Delegates to service with correct parameters")
        void getCalendarFeed_DelegatesToService() throws Exception {
            // Given
            String icsContent = "BEGIN:VCALENDAR\nVERSION:2.0\nEND:VCALENDAR";
            String etag = "\"group-456-1234567890000\"";

            ResponseEntity<String> serviceResponse = ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(CacheControl.maxAge(2, TimeUnit.HOURS).cachePublic().mustRevalidate())
                .contentType(MediaType.parseMediaType("text/calendar; charset=utf-8"))
                .body(icsContent);

            when(subscriptionService.getCalendarFeed(TEST_GROUP_ID, TEST_TOKEN, null))
                .thenReturn(serviceResponse);

            // When/Then
            mockMvc.perform(get("/calendar/feed/" + TEST_GROUP_ID + "/" + TEST_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("text/calendar;charset=UTF-8"))
                    .andExpect(header().string("ETag", etag))
                    .andExpect(content().string(icsContent));

            verify(subscriptionService).getCalendarFeed(TEST_GROUP_ID, TEST_TOKEN, null);
        }

        @Test
        @DisplayName("Passes If-None-Match header to service")
        void getCalendarFeed_PassesIfNoneMatchToService() throws Exception {
            // Given
            String etag = "\"group-456-1234567890000\"";

            ResponseEntity<String> serviceResponse = ResponseEntity.status(304)
                .eTag(etag)
                .cacheControl(CacheControl.maxAge(2, TimeUnit.HOURS).cachePublic().mustRevalidate())
                .build();

            when(subscriptionService.getCalendarFeed(TEST_GROUP_ID, TEST_TOKEN, etag))
                .thenReturn(serviceResponse);

            // When/Then
            mockMvc.perform(get("/calendar/feed/" + TEST_GROUP_ID + "/" + TEST_TOKEN)
                    .header("If-None-Match", etag))
                    .andExpect(status().isNotModified())
                    .andExpect(header().string("ETag", etag));

            verify(subscriptionService).getCalendarFeed(TEST_GROUP_ID, TEST_TOKEN, etag);
        }

        @Test
        @DisplayName("Returns service response directly")
        void getCalendarFeed_ReturnsServiceResponse() throws Exception {
            // Given
            String icsContent = "BEGIN:VCALENDAR\nVERSION:2.0\nPRODID:test\nEND:VCALENDAR";

            ResponseEntity<String> serviceResponse = ResponseEntity.ok()
                .eTag("\"test-etag\"")
                .cacheControl(CacheControl.maxAge(2, TimeUnit.HOURS).cachePublic().mustRevalidate())
                .contentType(MediaType.parseMediaType("text/calendar; charset=utf-8"))
                .body(icsContent);

            when(subscriptionService.getCalendarFeed(TEST_GROUP_ID, TEST_TOKEN, null))
                .thenReturn(serviceResponse);

            // When/Then
            mockMvc.perform(get("/calendar/feed/" + TEST_GROUP_ID + "/" + TEST_TOKEN))
                    .andExpect(status().isOk())
                    .andExpect(content().string(icsContent))
                    .andExpect(header().string("ETag", "\"test-etag\""))
                    .andExpect(header().string("Cache-Control", "max-age=7200, must-revalidate, public"));

            verify(subscriptionService).getCalendarFeed(TEST_GROUP_ID, TEST_TOKEN, null);
        }
    }
}
