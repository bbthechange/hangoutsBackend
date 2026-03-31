package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.UserSummaryDTO;
import com.bbthechange.inviter.model.Device;
import com.bbthechange.inviter.model.GroupMembership;
import com.bbthechange.inviter.repository.EventSeriesRepository;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.DeviceService;
import com.bbthechange.inviter.service.FcmNotificationService;
import com.bbthechange.inviter.service.NotificationTextGenerator;
import com.bbthechange.inviter.service.PushNotificationService;
import com.bbthechange.inviter.service.UserService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplIdeaTest {

    @Mock private GroupRepository groupRepository;
    @Mock private HangoutRepository hangoutRepository;
    @Mock private EventSeriesRepository eventSeriesRepository;
    @Mock private DeviceService deviceService;
    @Mock private PushNotificationService pushNotificationService;
    @Mock private FcmNotificationService fcmNotificationService;
    @Mock private UserService userService;
    @Mock private NotificationTextGenerator textGenerator;
    @Mock private MeterRegistry meterRegistry;
    @Mock private Counter counter;

    @InjectMocks
    private NotificationServiceImpl service;

    private static final String GROUP_ID = "00000000-0000-0000-0000-000000000010";
    private static final String LIST_ID = "00000000-0000-0000-0000-000000000020";
    private static final String LIST_NAME = "Restaurants";
    private static final String IDEA_ID = "00000000-0000-0000-0000-000000000040";

    // Use valid UUID strings so UUID.fromString() works in the implementation
    private static final String USER_1 = "00000000-0000-0000-0000-000000000001";
    private static final String USER_2 = "00000000-0000-0000-0000-000000000002";
    private static final String USER_3 = "00000000-0000-0000-0000-000000000003";

    @BeforeEach
    void setUp() {
        lenient().when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);
    }

    private GroupMembership createMembership(String userId) {
        GroupMembership m = new GroupMembership();
        m.setUserId(userId);
        return m;
    }

    private Device createDevice(String token, UUID userId, Device.Platform platform) {
        Device device = new Device(token, userId, platform);
        return device;
    }

    @Nested
    class NotifyIdeasAdded {

        @Test
        void notifyIdeasAdded_sendsToAllMembersExceptAdder() {
            // Given
            String adderId = USER_1;
            List<GroupMembership> members = List.of(
                    createMembership(USER_1),
                    createMembership(USER_2),
                    createMembership(USER_3));
            when(groupRepository.findMembersByGroupId(GROUP_ID)).thenReturn(members);

            UserSummaryDTO adderSummary = new UserSummaryDTO(UUID.fromString(adderId), "Alice", null);
            when(userService.getUserSummary(UUID.fromString(adderId))).thenReturn(Optional.of(adderSummary));
            when(textGenerator.getIdeasAddedBody(eq("Alice"), eq(LIST_NAME), any())).thenReturn("Alice added ideas");

            Device iosDevice2 = createDevice("token2", UUID.fromString(USER_2), Device.Platform.IOS);
            Device iosDevice3 = createDevice("token3", UUID.fromString(USER_3), Device.Platform.IOS);
            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_2))).thenReturn(List.of(iosDevice2));
            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_3))).thenReturn(List.of(iosDevice3));

            List<String> ideaNames = List.of("Sushi", "Pizza");

            // When
            service.notifyIdeasAdded(GROUP_ID, LIST_ID, LIST_NAME, adderId, ideaNames);

            // Then
            verify(userService).getUserSummary(UUID.fromString(adderId));
            verify(textGenerator).getIdeasAddedBody("Alice", LIST_NAME, ideaNames);
            verify(pushNotificationService).sendIdeaListNotification(
                    eq("token2"), eq(GROUP_ID), eq(LIST_ID), anyString(), eq("Alice added ideas"), eq("ideas_added"));
            verify(pushNotificationService).sendIdeaListNotification(
                    eq("token3"), eq(GROUP_ID), eq(LIST_ID), anyString(), eq("Alice added ideas"), eq("ideas_added"));
            // Should NOT send to the adder
            verify(deviceService, never()).getActiveDevicesForUser(UUID.fromString(USER_1));
        }

        @Test
        void notifyIdeasAdded_emptyGroup_noNotificationsSent() {
            // Given
            when(groupRepository.findMembersByGroupId(GROUP_ID)).thenReturn(Collections.emptyList());

            // When
            service.notifyIdeasAdded(GROUP_ID, LIST_ID, LIST_NAME, USER_1, List.of("Idea"));

            // Then
            verify(deviceService, never()).getActiveDevicesForUser(any());
            verify(pushNotificationService, never()).sendIdeaListNotification(
                    anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        }

        @Test
        void notifyIdeasAdded_onlyAdderInGroup_noNotificationsSent() {
            // Given
            List<GroupMembership> members = List.of(createMembership(USER_1));
            when(groupRepository.findMembersByGroupId(GROUP_ID)).thenReturn(members);

            // When
            service.notifyIdeasAdded(GROUP_ID, LIST_ID, LIST_NAME, USER_1, List.of("Idea"));

            // Then
            verify(deviceService, never()).getActiveDevicesForUser(any());
        }
    }

    @Nested
    class NotifyIdeaListCreated {

        @Test
        void notifyIdeaListCreated_sendsToAllMembersExceptCreator() {
            // Given
            String creatorId = USER_1;
            List<GroupMembership> members = List.of(
                    createMembership(USER_1),
                    createMembership(USER_2),
                    createMembership(USER_3));
            when(groupRepository.findMembersByGroupId(GROUP_ID)).thenReturn(members);

            UserSummaryDTO creatorSummary = new UserSummaryDTO(UUID.fromString(creatorId), "Bob", null);
            when(userService.getUserSummary(UUID.fromString(creatorId))).thenReturn(Optional.of(creatorSummary));
            when(textGenerator.getIdeaListCreatedBody("Bob", LIST_NAME)).thenReturn("Bob created a list");

            Device iosDevice2 = createDevice("token2", UUID.fromString(USER_2), Device.Platform.IOS);
            Device iosDevice3 = createDevice("token3", UUID.fromString(USER_3), Device.Platform.IOS);
            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_2))).thenReturn(List.of(iosDevice2));
            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_3))).thenReturn(List.of(iosDevice3));

            // When
            service.notifyIdeaListCreated(GROUP_ID, LIST_ID, LIST_NAME, creatorId);

            // Then
            verify(textGenerator).getIdeaListCreatedBody("Bob", LIST_NAME);
            verify(pushNotificationService).sendIdeaListNotification(
                    eq("token2"), eq(GROUP_ID), eq(LIST_ID), anyString(), eq("Bob created a list"), eq("idea_list_created"));
            verify(pushNotificationService).sendIdeaListNotification(
                    eq("token3"), eq(GROUP_ID), eq(LIST_ID), anyString(), eq("Bob created a list"), eq("idea_list_created"));
            verify(deviceService, never()).getActiveDevicesForUser(UUID.fromString(USER_1));
        }

        @Test
        void notifyIdeaListCreated_deviceLookupFails_continuesWithOtherUsers() {
            // Given
            String creatorId = USER_1;
            List<GroupMembership> members = List.of(
                    createMembership(USER_1),
                    createMembership(USER_2),
                    createMembership(USER_3));
            when(groupRepository.findMembersByGroupId(GROUP_ID)).thenReturn(members);

            UserSummaryDTO creatorSummary = new UserSummaryDTO(UUID.fromString(creatorId), "Bob", null);
            when(userService.getUserSummary(UUID.fromString(creatorId))).thenReturn(Optional.of(creatorSummary));
            when(textGenerator.getIdeaListCreatedBody("Bob", LIST_NAME)).thenReturn("Bob created a list");

            // First user's device lookup throws
            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_2)))
                    .thenThrow(new RuntimeException("DynamoDB timeout"));
            Device iosDevice3 = createDevice("token3", UUID.fromString(USER_3), Device.Platform.IOS);
            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_3))).thenReturn(List.of(iosDevice3));

            // When
            service.notifyIdeaListCreated(GROUP_ID, LIST_ID, LIST_NAME, creatorId);

            // Then - should still send to USER_3 despite USER_2 failure
            verify(pushNotificationService).sendIdeaListNotification(
                    eq("token3"), eq(GROUP_ID), eq(LIST_ID), anyString(), eq("Bob created a list"), eq("idea_list_created"));
        }
    }

    @Nested
    class NotifyIdeaInterestMilestone {

        @Test
        void notifyIdeaInterestMilestone_sendsToAllRecipients_iOS() {
            // Given
            Set<String> recipientIds = Set.of(USER_1, USER_2);
            String body = "3 people are interested in Sushi";

            Device iosDevice1 = createDevice("token1", UUID.fromString(USER_1), Device.Platform.IOS);
            Device iosDevice2 = createDevice("token2", UUID.fromString(USER_2), Device.Platform.IOS);
            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_1))).thenReturn(List.of(iosDevice1));
            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_2))).thenReturn(List.of(iosDevice2));

            // When
            service.notifyIdeaInterestMilestone(GROUP_ID, LIST_ID, IDEA_ID, recipientIds, body);

            // Then
            verify(pushNotificationService).sendIdeaInterestNotification(
                    "token1", GROUP_ID, LIST_ID, IDEA_ID,
                    NotificationTextGenerator.IDEA_INTEREST_TITLE, body);
            verify(pushNotificationService).sendIdeaInterestNotification(
                    "token2", GROUP_ID, LIST_ID, IDEA_ID,
                    NotificationTextGenerator.IDEA_INTEREST_TITLE, body);
        }

        @Test
        void notifyIdeaInterestMilestone_sendsToAllRecipients_android() {
            // Given
            Set<String> recipientIds = Set.of(USER_1);
            String body = "3 people are interested in Sushi";

            Device androidDevice = createDevice("fcm-token", UUID.fromString(USER_1), Device.Platform.ANDROID);
            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_1))).thenReturn(List.of(androidDevice));

            // When
            service.notifyIdeaInterestMilestone(GROUP_ID, LIST_ID, IDEA_ID, recipientIds, body);

            // Then
            verify(fcmNotificationService).sendIdeaInterestNotification(
                    "fcm-token", GROUP_ID, LIST_ID, IDEA_ID,
                    NotificationTextGenerator.IDEA_INTEREST_TITLE, body);
            verify(pushNotificationService, never()).sendIdeaInterestNotification(
                    anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        }

        @Test
        void notifyIdeaInterestMilestone_emptyRecipients_noDeviceLookups() {
            // Given
            Set<String> recipientIds = Collections.emptySet();
            String body = "body";

            // When
            service.notifyIdeaInterestMilestone(GROUP_ID, LIST_ID, IDEA_ID, recipientIds, body);

            // Then
            verify(deviceService, never()).getActiveDevicesForUser(any());
        }
    }
}
