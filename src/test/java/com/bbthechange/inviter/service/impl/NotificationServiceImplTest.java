package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.UserSummaryDTO;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.service.DeviceService;
import com.bbthechange.inviter.service.FcmNotificationService;
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

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock(lenient = true)
    private GroupRepository groupRepository;

    @Mock(lenient = true)
    private DeviceService deviceService;

    @Mock(lenient = true)
    private PushNotificationService pushNotificationService;

    @Mock(lenient = true)
    private FcmNotificationService fcmNotificationService;

    @Mock(lenient = true)
    private UserService userService;

    @Mock(lenient = true)
    private MeterRegistry meterRegistry;

    @Mock(lenient = true)
    private Counter mockCounter;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private Hangout testHangout;
    private Group testGroup;
    private User testCreator;
    private User testMember1;
    private User testMember2;

    @BeforeEach
    void setUp() {
        // Create test hangout
        testHangout = new Hangout();
        testHangout.setHangoutId("00000000-0000-0000-0000-000000000100");
        testHangout.setTitle("Test Hangout");

        // Create test group
        testGroup = new Group("Test Group", false);
        testGroup.setGroupId("00000000-0000-0000-0000-000000000201");

        // Create test users
        testCreator = new User();
        testCreator.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        testCreator.setDisplayName("Creator Name");

        testMember1 = new User();
        testMember1.setId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        testMember1.setDisplayName("Member One");

        testMember2 = new User();
        testMember2.setId(UUID.fromString("00000000-0000-0000-0000-000000000003"));
        testMember2.setDisplayName("Member Two");
    }

    @Test
    void notifyNewHangout_WithMultipleGroups_DeduplicatesUsers() {
        // Given: User is in both Group A and Group B
        String userId = "00000000-0000-0000-0000-000000000002";
        String creatorId = "00000000-0000-0000-0000-000000000001";
        String hangoutId = "00000000-0000-0000-0000-000000000100";
        String groupAId = "00000000-0000-0000-0000-000000000201";
        String groupBId = "00000000-0000-0000-0000-000000000202";
        List<String> groupIds = Arrays.asList(groupAId, groupBId);

        // Setup hangout with associated groups
        testHangout.setAssociatedGroups(groupIds);

        // User belongs to both groups
        GroupMembership membership1 = new GroupMembership(groupAId, userId, "Group A");
        GroupMembership membership2 = new GroupMembership(groupBId, userId, "Group B");

        when(groupRepository.findMembersByGroupId(groupAId)).thenReturn(Arrays.asList(membership1));
        when(groupRepository.findMembersByGroupId(groupBId)).thenReturn(Arrays.asList(membership2));

        // Setup groups
        Group groupA = new Group("Group A", false);
        groupA.setGroupId(groupAId);
        Group groupB = new Group("Group B", false);
        groupB.setGroupId(groupBId);
        when(groupRepository.findById(groupAId)).thenReturn(Optional.of(groupA));
        when(groupRepository.findById(groupBId)).thenReturn(Optional.of(groupB));

        // User has one device
        Device device = new Device("token-123", UUID.fromString(userId), Device.Platform.IOS);
        when(deviceService.getActiveDevicesForUser(UUID.fromString(userId))).thenReturn(Arrays.asList(device));

        // When
        notificationService.notifyNewHangout(testHangout, creatorId, "Creator Name");

        // Then: Should send notification only once (deduplication)
        verify(pushNotificationService, times(1)).sendNewHangoutNotification(
            eq("token-123"),
            eq(hangoutId),
            eq(groupAId),
            eq("Test Hangout"),
            anyString(),
            eq("Creator Name")
        );
    }

    @Test
    void notifyNewHangout_ExcludesCreator() {
        // Given: Creator is a member of the group
        String creatorId = "00000000-0000-0000-0000-000000000001";
        String hangoutId = "00000000-0000-0000-0000-000000000100";
        String groupId = "00000000-0000-0000-0000-000000000201";
        List<String> groupIds = Arrays.asList(groupId);

        testHangout.setAssociatedGroups(groupIds);

        // Group has creator and one other member
        GroupMembership creatorMembership = new GroupMembership(groupId, creatorId, "Test Group");
        GroupMembership memberMembership = new GroupMembership(groupId, "00000000-0000-0000-0000-000000000002", "Test Group");
        when(groupRepository.findMembersByGroupId(groupId))
            .thenReturn(Arrays.asList(creatorMembership, memberMembership));

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(testGroup));

        Device memberDevice = new Device("token-member", UUID.fromString("00000000-0000-0000-0000-000000000002"), Device.Platform.IOS);
        when(deviceService.getActiveDevicesForUser(UUID.fromString("00000000-0000-0000-0000-000000000002"))).thenReturn(Arrays.asList(memberDevice));
        when(deviceService.getActiveDevicesForUser(UUID.fromString(creatorId))).thenReturn(Arrays.asList());

        // When
        notificationService.notifyNewHangout(testHangout, creatorId, "Creator Name");

        // Then: Should only send to member, not creator
        verify(pushNotificationService, times(1)).sendNewHangoutNotification(
            eq("token-member"),
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            anyString()
        );
        verify(deviceService, never()).getActiveDevicesForUser(UUID.fromString(creatorId));
    }

    @Test
    void notifyNewHangout_WithMultipleDevices_SendsToAllDevices() {
        // Given: User has multiple iOS devices
        String userId = "00000000-0000-0000-0000-000000000002";
        String creatorId = "00000000-0000-0000-0000-000000000001";
        String hangoutId = "00000000-0000-0000-0000-000000000100";
        String groupId = "00000000-0000-0000-0000-000000000201";
        List<String> groupIds = Arrays.asList(groupId);

        testHangout.setAssociatedGroups(groupIds);

        GroupMembership membership = new GroupMembership(groupId, userId, "Test Group");
        when(groupRepository.findMembersByGroupId(groupId)).thenReturn(Arrays.asList(membership));
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(testGroup));

        // User has 3 devices
        Device device1 = new Device("token-1", UUID.fromString(userId), Device.Platform.IOS);
        Device device2 = new Device("token-2", UUID.fromString(userId), Device.Platform.IOS);
        Device device3 = new Device("token-3", UUID.fromString(userId), Device.Platform.IOS);
        when(deviceService.getActiveDevicesForUser(UUID.fromString(userId)))
            .thenReturn(Arrays.asList(device1, device2, device3));

        // When
        notificationService.notifyNewHangout(testHangout, creatorId, "Creator Name");

        // Then: Should send to all 3 devices
        verify(pushNotificationService, times(3)).sendNewHangoutNotification(
            anyString(),
            eq(hangoutId),
            eq(groupId),
            eq("Test Hangout"),
            eq("Test Group"),
            eq("Creator Name")
        );
    }

    @Test
    void notifyNewHangout_WithNoGroups_DoesNothing() {
        // Given: Empty group list
        String creatorId = "00000000-0000-0000-0000-000000000001";
        String hangoutId = "00000000-0000-0000-0000-000000000100";

        testHangout.setAssociatedGroups(Arrays.asList());

        // When
        notificationService.notifyNewHangout(testHangout, creatorId, "Creator Name");

        // Then: Should not query anything or send notifications
        verify(groupRepository, never()).findMembersByGroupId(anyString());
        verify(pushNotificationService, never()).sendNewHangoutNotification(
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
        );
    }

    @Test
    void notifyNewHangout_WithNullGroups_DoesNothing() {
        // Given: Null group list
        String creatorId = "00000000-0000-0000-0000-000000000001";
        String hangoutId = "00000000-0000-0000-0000-000000000100";

        testHangout.setAssociatedGroups(null);

        // When
        notificationService.notifyNewHangout(testHangout, creatorId, "Creator Name");

        // Then: Should not query anything or send notifications
        verify(groupRepository, never()).findMembersByGroupId(anyString());
        verify(pushNotificationService, never()).sendNewHangoutNotification(
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
        );
    }

    @Test
    void notifyNewHangout_WhenUserHasNoDevices_HandlesGracefully() {
        // Given: User has no devices
        String userId = "00000000-0000-0000-0000-000000000002";
        String creatorId = "00000000-0000-0000-0000-000000000001";
        String hangoutId = "00000000-0000-0000-0000-000000000100";
        String groupId = "00000000-0000-0000-0000-000000000201";
        List<String> groupIds = Arrays.asList(groupId);

        testHangout.setAssociatedGroups(groupIds);

        GroupMembership membership = new GroupMembership(groupId, userId, "Test Group");
        when(groupRepository.findMembersByGroupId(groupId)).thenReturn(Arrays.asList(membership));
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(testGroup));

        // User has no devices
        when(deviceService.getActiveDevicesForUser(UUID.fromString(userId))).thenReturn(Arrays.asList());

        // When
        notificationService.notifyNewHangout(testHangout, creatorId, "Creator Name");

        // Then: Should not crash, just log and continue
        verify(pushNotificationService, never()).sendNewHangoutNotification(
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
        );
    }

    @Test
    void notifyNewHangout_WhenGroupNotFound_SkipsThatGroup() {
        // Given: One group doesn't exist
        String userId = "00000000-0000-0000-0000-000000000002";
        String creatorId = "00000000-0000-0000-0000-000000000001";
        String hangoutId = "00000000-0000-0000-0000-000000000100";
        String groupId = "00000000-0000-0000-0000-000000000201";
        String nonExistentGroupId = "00000000-0000-0000-0000-000000000299";
        List<String> groupIds = Arrays.asList(groupId, nonExistentGroupId);

        testHangout.setAssociatedGroups(groupIds);

        GroupMembership membership = new GroupMembership(groupId, userId, "Test Group");
        when(groupRepository.findMembersByGroupId(groupId)).thenReturn(Arrays.asList(membership));
        when(groupRepository.findMembersByGroupId(nonExistentGroupId)).thenReturn(Arrays.asList());

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(testGroup));
        when(groupRepository.findById(nonExistentGroupId)).thenReturn(Optional.empty());

        Device device = new Device("token-123", UUID.fromString(userId), Device.Platform.IOS);
        when(deviceService.getActiveDevicesForUser(UUID.fromString(userId))).thenReturn(Arrays.asList(device));

        // When
        notificationService.notifyNewHangout(testHangout, creatorId, "Creator Name");

        // Then: Should still send notification for the valid group
        verify(pushNotificationService, times(1)).sendNewHangoutNotification(
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
        );
    }

    @Test
    void notifyNewHangout_WhenPushNotificationFails_ContinuesWithOtherUsers() {
        // Given: Multiple users, one fails
        String creatorId = "00000000-0000-0000-0000-000000000001";
        String hangoutId = "00000000-0000-0000-0000-000000000100";
        String groupId = "00000000-0000-0000-0000-000000000201";
        List<String> groupIds = Arrays.asList(groupId);

        testHangout.setAssociatedGroups(groupIds);

        GroupMembership membership1 = new GroupMembership(groupId, "00000000-0000-0000-0000-000000000002", "Test Group");
        GroupMembership membership2 = new GroupMembership(groupId, "00000000-0000-0000-0000-000000000003", "Test Group");
        when(groupRepository.findMembersByGroupId(groupId))
            .thenReturn(Arrays.asList(membership1, membership2));

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(testGroup));

        Device device1 = new Device("token-1", UUID.fromString("00000000-0000-0000-0000-000000000002"), Device.Platform.IOS);
        Device device2 = new Device("token-2", UUID.fromString("00000000-0000-0000-0000-000000000003"), Device.Platform.IOS);
        when(deviceService.getActiveDevicesForUser(UUID.fromString("00000000-0000-0000-0000-000000000002"))).thenReturn(Arrays.asList(device1));
        when(deviceService.getActiveDevicesForUser(UUID.fromString("00000000-0000-0000-0000-000000000003"))).thenReturn(Arrays.asList(device2));

        // First notification fails, second succeeds
        doThrow(new RuntimeException("Push notification failed"))
            .when(pushNotificationService).sendNewHangoutNotification(
                eq("token-1"), anyString(), anyString(), anyString(), anyString(), anyString()
            );

        // When
        notificationService.notifyNewHangout(testHangout, creatorId, "Creator Name");

        // Then: Should still send to second user
        verify(pushNotificationService, times(2)).sendNewHangoutNotification(
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
        );
    }

    @Test
    void notifyNewHangout_WithAndroidDevice_SendsViaFcm() {
        // Given: User has Android device
        String userId = "00000000-0000-0000-0000-000000000002";
        String creatorId = "00000000-0000-0000-0000-000000000001";
        String hangoutId = "00000000-0000-0000-0000-000000000100";
        String groupId = "00000000-0000-0000-0000-000000000201";
        List<String> groupIds = Arrays.asList(groupId);

        testHangout.setAssociatedGroups(groupIds);

        GroupMembership membership = new GroupMembership(groupId, userId, "Test Group");
        when(groupRepository.findMembersByGroupId(groupId)).thenReturn(Arrays.asList(membership));
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(testGroup));

        // User has Android device
        Device androidDevice = new Device("android-token-123", UUID.fromString(userId), Device.Platform.ANDROID);
        when(deviceService.getActiveDevicesForUser(UUID.fromString(userId))).thenReturn(Arrays.asList(androidDevice));

        // When
        notificationService.notifyNewHangout(testHangout, creatorId, "Creator Name");

        // Then: Should send via FCM, not APNs
        verify(fcmNotificationService, times(1)).sendNewHangoutNotification(
            eq("android-token-123"),
            eq(hangoutId),
            eq(groupId),
            eq("Test Hangout"),
            eq("Test Group"),
            eq("Creator Name")
        );
        verify(pushNotificationService, never()).sendNewHangoutNotification(
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
        );
    }

    @Test
    void notifyNewHangout_WithMixedDevices_SendsToBothPlatforms() {
        // Given: User has both iOS and Android devices
        String userId = "00000000-0000-0000-0000-000000000002";
        String creatorId = "00000000-0000-0000-0000-000000000001";
        String hangoutId = "00000000-0000-0000-0000-000000000100";
        String groupId = "00000000-0000-0000-0000-000000000201";
        List<String> groupIds = Arrays.asList(groupId);

        testHangout.setAssociatedGroups(groupIds);

        GroupMembership membership = new GroupMembership(groupId, userId, "Test Group");
        when(groupRepository.findMembersByGroupId(groupId)).thenReturn(Arrays.asList(membership));
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(testGroup));

        // User has both iOS and Android devices
        Device iosDevice = new Device("ios-token-123", UUID.fromString(userId), Device.Platform.IOS);
        Device androidDevice = new Device("android-token-456", UUID.fromString(userId), Device.Platform.ANDROID);
        when(deviceService.getActiveDevicesForUser(UUID.fromString(userId)))
            .thenReturn(Arrays.asList(iosDevice, androidDevice));

        // When
        notificationService.notifyNewHangout(testHangout, creatorId, "Creator Name");

        // Then: Should send to both platforms
        verify(pushNotificationService, times(1)).sendNewHangoutNotification(
            eq("ios-token-123"),
            eq(hangoutId),
            eq(groupId),
            eq("Test Hangout"),
            eq("Test Group"),
            eq("Creator Name")
        );
        verify(fcmNotificationService, times(1)).sendNewHangoutNotification(
            eq("android-token-456"),
            eq(hangoutId),
            eq(groupId),
            eq("Test Hangout"),
            eq("Test Group"),
            eq("Creator Name")
        );
    }

    // ================= Group Member Added Notification Tests =================

    @Nested
    class NotifyGroupMemberAddedTests {

        private static final String GROUP_ID = "00000000-0000-0000-0000-000000000201";
        private static final String GROUP_NAME = "Test Group";
        private static final String ADDED_USER_ID = "00000000-0000-0000-0000-000000000002";
        private static final String ADDER_USER_ID = "00000000-0000-0000-0000-000000000001";
        private static final String ADDER_NAME = "John Adder";

        private Device createDevice(String token, Device.Platform platform) {
            Device device = new Device();
            device.setToken(token);
            device.setPlatform(platform);
            device.setActive(true);
            return device;
        }

        @Test
        void notifyGroupMemberAdded_WhenSelfJoin_SkipsNotification() {
            // Given: User adds themselves to the group (self-join)
            String userId = ADDED_USER_ID;

            // When
            notificationService.notifyGroupMemberAdded(GROUP_ID, GROUP_NAME, userId, userId);

            // Then: No notifications sent
            verify(pushNotificationService, never()).sendGroupMemberAddedNotification(
                anyString(), anyString(), anyString(), anyString());
            verify(fcmNotificationService, never()).sendGroupMemberAddedNotification(
                anyString(), anyString(), anyString(), anyString());
            verify(deviceService, never()).getActiveDevicesForUser(any());
            verify(userService, never()).getUserSummary(any());
        }

        @Test
        void notifyGroupMemberAdded_WithIosDevice_SendsViaPushNotificationService() {
            // Given: User has iOS device and adder has a display name
            Device iosDevice = createDevice("ios-token-123", Device.Platform.IOS);
            when(deviceService.getActiveDevicesForUser(UUID.fromString(ADDED_USER_ID)))
                .thenReturn(List.of(iosDevice));

            UserSummaryDTO adderSummary = new UserSummaryDTO(
                UUID.fromString(ADDER_USER_ID), ADDER_NAME, null);
            when(userService.getUserSummary(UUID.fromString(ADDER_USER_ID)))
                .thenReturn(Optional.of(adderSummary));

            when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(mockCounter);

            // When
            notificationService.notifyGroupMemberAdded(GROUP_ID, GROUP_NAME, ADDED_USER_ID, ADDER_USER_ID);

            // Then: Notification sent via push notification service
            verify(pushNotificationService).sendGroupMemberAddedNotification(
                eq("ios-token-123"),
                eq(GROUP_ID),
                eq(GROUP_NAME),
                eq(ADDER_NAME)
            );
            verify(fcmNotificationService, never()).sendGroupMemberAddedNotification(
                anyString(), anyString(), anyString(), anyString());
        }

        @Test
        void notifyGroupMemberAdded_WithAndroidDevice_SendsViaFcmNotificationService() {
            // Given: User has Android device
            Device androidDevice = createDevice("android-token-456", Device.Platform.ANDROID);
            when(deviceService.getActiveDevicesForUser(UUID.fromString(ADDED_USER_ID)))
                .thenReturn(List.of(androidDevice));

            UserSummaryDTO adderSummary = new UserSummaryDTO(
                UUID.fromString(ADDER_USER_ID), ADDER_NAME, null);
            when(userService.getUserSummary(UUID.fromString(ADDER_USER_ID)))
                .thenReturn(Optional.of(adderSummary));

            when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(mockCounter);

            // When
            notificationService.notifyGroupMemberAdded(GROUP_ID, GROUP_NAME, ADDED_USER_ID, ADDER_USER_ID);

            // Then: Notification sent via FCM
            verify(fcmNotificationService).sendGroupMemberAddedNotification(
                eq("android-token-456"),
                eq(GROUP_ID),
                eq(GROUP_NAME),
                eq(ADDER_NAME)
            );
            verify(pushNotificationService, never()).sendGroupMemberAddedNotification(
                anyString(), anyString(), anyString(), anyString());
        }

        @Test
        void notifyGroupMemberAdded_WithNoDevices_IncrementsSkippedMetric() {
            // Given: User has no active devices
            when(deviceService.getActiveDevicesForUser(UUID.fromString(ADDED_USER_ID)))
                .thenReturn(List.of());

            UserSummaryDTO adderSummary = new UserSummaryDTO(
                UUID.fromString(ADDER_USER_ID), ADDER_NAME, null);
            when(userService.getUserSummary(UUID.fromString(ADDER_USER_ID)))
                .thenReturn(Optional.of(adderSummary));

            when(meterRegistry.counter(eq("group_member_added_notification_total"),
                eq("status"), eq("skipped"), eq("reason"), eq("no_devices")))
                .thenReturn(mockCounter);

            // When
            notificationService.notifyGroupMemberAdded(GROUP_ID, GROUP_NAME, ADDED_USER_ID, ADDER_USER_ID);

            // Then: Skipped metric incremented with reason="no_devices"
            verify(meterRegistry).counter("group_member_added_notification_total",
                "status", "skipped", "reason", "no_devices");
            verify(mockCounter).increment();
            verify(pushNotificationService, never()).sendGroupMemberAddedNotification(
                anyString(), anyString(), anyString(), anyString());
            verify(fcmNotificationService, never()).sendGroupMemberAddedNotification(
                anyString(), anyString(), anyString(), anyString());
        }

        @Test
        void notifyGroupMemberAdded_WhenAdderLookupFails_UsesUnknownAndIncrementsErrorMetric() {
            // Given: User has iOS device, but adder lookup throws exception
            Device iosDevice = createDevice("ios-token-123", Device.Platform.IOS);
            when(deviceService.getActiveDevicesForUser(UUID.fromString(ADDED_USER_ID)))
                .thenReturn(List.of(iosDevice));

            when(userService.getUserSummary(UUID.fromString(ADDER_USER_ID)))
                .thenThrow(new RuntimeException("Database error"));

            when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(mockCounter);

            // When
            notificationService.notifyGroupMemberAdded(GROUP_ID, GROUP_NAME, ADDED_USER_ID, ADDER_USER_ID);

            // Then: "Unknown" used as adder name, error metric incremented
            verify(meterRegistry).counter("group_member_added_notification_total",
                "status", "error", "error_type", "adder_lookup_failed");
            verify(pushNotificationService).sendGroupMemberAddedNotification(
                eq("ios-token-123"),
                eq(GROUP_ID),
                eq(GROUP_NAME),
                eq("Unknown")
            );
        }

        @Test
        void notifyGroupMemberAdded_WhenDeviceSendFails_ContinuesToNextDevice() {
            // Given: User has multiple devices, first one fails
            Device iosDevice = createDevice("ios-token-fail", Device.Platform.IOS);
            Device androidDevice = createDevice("android-token-success", Device.Platform.ANDROID);
            when(deviceService.getActiveDevicesForUser(UUID.fromString(ADDED_USER_ID)))
                .thenReturn(List.of(iosDevice, androidDevice));

            UserSummaryDTO adderSummary = new UserSummaryDTO(
                UUID.fromString(ADDER_USER_ID), ADDER_NAME, null);
            when(userService.getUserSummary(UUID.fromString(ADDER_USER_ID)))
                .thenReturn(Optional.of(adderSummary));

            // iOS notification fails
            doThrow(new RuntimeException("APNS error"))
                .when(pushNotificationService).sendGroupMemberAddedNotification(
                    eq("ios-token-fail"), anyString(), anyString(), anyString());

            when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(mockCounter);

            // When
            notificationService.notifyGroupMemberAdded(GROUP_ID, GROUP_NAME, ADDED_USER_ID, ADDER_USER_ID);

            // Then: Both services attempted, Android notification sent successfully
            verify(pushNotificationService).sendGroupMemberAddedNotification(
                eq("ios-token-fail"), eq(GROUP_ID), eq(GROUP_NAME), eq(ADDER_NAME));
            verify(fcmNotificationService).sendGroupMemberAddedNotification(
                eq("android-token-success"), eq(GROUP_ID), eq(GROUP_NAME), eq(ADDER_NAME));
            // Error metric incremented for failed device
            verify(meterRegistry).counter("group_member_added_notification_total",
                "status", "error", "error_type", "device_send_failed");
            // Success metric also incremented since at least one succeeded
            verify(meterRegistry).counter("group_member_added_notification_total",
                "status", "success");
        }
    }
}