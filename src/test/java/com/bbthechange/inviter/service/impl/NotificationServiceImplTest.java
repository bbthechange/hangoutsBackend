package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.HangoutDetailData;
import com.bbthechange.inviter.dto.UserSummaryDTO;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.EventSeriesRepository;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.HangoutRepository;
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
    private HangoutRepository hangoutRepository;

    @Mock(lenient = true)
    private EventSeriesRepository eventSeriesRepository;

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

    // ================= Hangout Updated Notification Tests =================

    @Nested
    class NotifyHangoutUpdated {

        private static final String HANGOUT_ID = "00000000-0000-0000-0000-000000000100";
        private static final String HANGOUT_TITLE = "Test Hangout";
        private static final String GROUP_ID = "00000000-0000-0000-0000-000000000201";
        private static final String UPDATER_USER_ID = "00000000-0000-0000-0000-000000000001";
        private static final String USER_1_ID = "00000000-0000-0000-0000-000000000002";
        private static final String USER_2_ID = "00000000-0000-0000-0000-000000000003";

        private Device createDevice(String token, Device.Platform platform) {
            Device device = new Device();
            device.setToken(token);
            device.setPlatform(platform);
            device.setActive(true);
            return device;
        }

        @Test
        void notifyHangoutUpdated_WithInterestedUsers_SendsNotificationsToEachDevice() {
            // Given: 2 interested users, each with 1 iOS device
            Set<String> interestedUserIds = new HashSet<>(Set.of(USER_1_ID, USER_2_ID));
            List<String> groupIds = List.of(GROUP_ID);

            Device device1 = createDevice("ios-token-user1", Device.Platform.IOS);
            Device device2 = createDevice("ios-token-user2", Device.Platform.IOS);

            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_1_ID)))
                .thenReturn(List.of(device1));
            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_2_ID)))
                .thenReturn(List.of(device2));

            // When
            notificationService.notifyHangoutUpdated(HANGOUT_ID, HANGOUT_TITLE, groupIds,
                "time", UPDATER_USER_ID, interestedUserIds, null);

            // Then: notifications sent to both users
            verify(pushNotificationService).sendHangoutUpdatedNotification(
                eq("ios-token-user1"), eq(HANGOUT_ID), eq(GROUP_ID), eq(HANGOUT_TITLE), eq("time"), isNull());
            verify(pushNotificationService).sendHangoutUpdatedNotification(
                eq("ios-token-user2"), eq(HANGOUT_ID), eq(GROUP_ID), eq(HANGOUT_TITLE), eq("time"), isNull());
        }

        @Test
        void notifyHangoutUpdated_ExcludesUpdater_DoesNotNotifyPersonWhoMadeChange() {
            // Given: 2 interested users including the updater
            Set<String> interestedUserIds = new HashSet<>(Set.of(UPDATER_USER_ID, USER_1_ID));
            List<String> groupIds = List.of(GROUP_ID);

            Device updaterDevice = createDevice("ios-token-updater", Device.Platform.IOS);
            Device userDevice = createDevice("ios-token-user1", Device.Platform.IOS);

            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_1_ID)))
                .thenReturn(List.of(userDevice));

            // When
            notificationService.notifyHangoutUpdated(HANGOUT_ID, HANGOUT_TITLE, groupIds,
                "time", UPDATER_USER_ID, interestedUserIds, null);

            // Then: only 1 notification sent (to the other user, not the updater)
            verify(pushNotificationService, times(1)).sendHangoutUpdatedNotification(
                eq("ios-token-user1"), eq(HANGOUT_ID), eq(GROUP_ID), eq(HANGOUT_TITLE), eq("time"), isNull());
            verify(deviceService, never()).getActiveDevicesForUser(UUID.fromString(UPDATER_USER_ID));
        }

        @Test
        void notifyHangoutUpdated_OnlyUpdaterInterested_SkipsNotifications() {
            // Given: only interested user is the one who made the change
            Set<String> interestedUserIds = new HashSet<>(Set.of(UPDATER_USER_ID));
            List<String> groupIds = List.of(GROUP_ID);

            // When
            notificationService.notifyHangoutUpdated(HANGOUT_ID, HANGOUT_TITLE, groupIds,
                "time", UPDATER_USER_ID, interestedUserIds, null);

            // Then: no calls to notification services
            verify(pushNotificationService, never()).sendHangoutUpdatedNotification(
                anyString(), anyString(), anyString(), anyString(), anyString(), any());
            verify(fcmNotificationService, never()).sendHangoutUpdatedNotification(
                anyString(), anyString(), anyString(), anyString(), anyString(), any());
            verify(deviceService, never()).getActiveDevicesForUser(any());
        }

        @Test
        void notifyHangoutUpdated_WithNoInterestedUsers_SkipsNotifications() {
            // Given: empty Set for interestedUserIds
            Set<String> interestedUserIds = new HashSet<>();
            List<String> groupIds = List.of(GROUP_ID);

            // When
            notificationService.notifyHangoutUpdated(HANGOUT_ID, HANGOUT_TITLE, groupIds,
                "time", UPDATER_USER_ID, interestedUserIds, null);

            // Then: no calls to device service
            verify(deviceService, never()).getActiveDevicesForUser(any());
            verify(pushNotificationService, never()).sendHangoutUpdatedNotification(
                anyString(), anyString(), anyString(), anyString(), anyString(), any());
        }

        @Test
        void notifyHangoutUpdated_WithNullInterestedUsers_SkipsNotifications() {
            // Given: null for interestedUserIds
            List<String> groupIds = List.of(GROUP_ID);

            // When
            notificationService.notifyHangoutUpdated(HANGOUT_ID, HANGOUT_TITLE, groupIds,
                "time", UPDATER_USER_ID, null, null);

            // Then: no exceptions thrown, no calls to device service
            verify(deviceService, never()).getActiveDevicesForUser(any());
        }

        @Test
        void notifyHangoutUpdated_WithNoGroups_SkipsNotifications() {
            // Given: null or empty list for groupIds
            Set<String> interestedUserIds = new HashSet<>(Set.of(USER_1_ID));

            // When - with null groupIds
            notificationService.notifyHangoutUpdated(HANGOUT_ID, HANGOUT_TITLE, null,
                "time", UPDATER_USER_ID, interestedUserIds, null);

            // Then: no calls to device service
            verify(deviceService, never()).getActiveDevicesForUser(any());

            // When - with empty groupIds
            notificationService.notifyHangoutUpdated(HANGOUT_ID, HANGOUT_TITLE, List.of(),
                "location", UPDATER_USER_ID, interestedUserIds, null);

            // Then: still no calls to device service
            verify(deviceService, never()).getActiveDevicesForUser(any());
        }

        @Test
        void notifyHangoutUpdated_WithMixedDevices_SendsToBothPlatforms() {
            // Given: 1 user with 1 iOS device AND 1 Android device
            Set<String> interestedUserIds = new HashSet<>(Set.of(USER_1_ID));
            List<String> groupIds = List.of(GROUP_ID);

            Device iosDevice = createDevice("ios-token", Device.Platform.IOS);
            Device androidDevice = createDevice("android-token", Device.Platform.ANDROID);

            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_1_ID)))
                .thenReturn(List.of(iosDevice, androidDevice));

            // When
            notificationService.notifyHangoutUpdated(HANGOUT_ID, HANGOUT_TITLE, groupIds,
                "location", UPDATER_USER_ID, interestedUserIds, "Central Park");

            // Then: both platforms receive notifications with location name
            verify(pushNotificationService).sendHangoutUpdatedNotification(
                eq("ios-token"), eq(HANGOUT_ID), eq(GROUP_ID), eq(HANGOUT_TITLE), eq("location"), eq("Central Park"));
            verify(fcmNotificationService).sendHangoutUpdatedNotification(
                eq("android-token"), eq(HANGOUT_ID), eq(GROUP_ID), eq(HANGOUT_TITLE), eq("location"), eq("Central Park"));
        }

        @Test
        void notifyHangoutUpdated_WhenDeviceFails_ContinuesToNextUser() {
            // Given: 2 users, first user's device throws exception
            Set<String> interestedUserIds = new HashSet<>(Set.of(USER_1_ID, USER_2_ID));
            List<String> groupIds = List.of(GROUP_ID);

            Device device1 = createDevice("ios-token-fail", Device.Platform.IOS);
            Device device2 = createDevice("ios-token-success", Device.Platform.IOS);

            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_1_ID)))
                .thenReturn(List.of(device1));
            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_2_ID)))
                .thenReturn(List.of(device2));

            // First device throws exception
            doThrow(new RuntimeException("APNS error"))
                .when(pushNotificationService).sendHangoutUpdatedNotification(
                    eq("ios-token-fail"), anyString(), anyString(), anyString(), anyString(), any());

            // When
            notificationService.notifyHangoutUpdated(HANGOUT_ID, HANGOUT_TITLE, groupIds,
                "time", UPDATER_USER_ID, interestedUserIds, null);

            // Then: second user still receives notification, no exception propagated
            verify(pushNotificationService).sendHangoutUpdatedNotification(
                eq("ios-token-success"), eq(HANGOUT_ID), eq(GROUP_ID), eq(HANGOUT_TITLE), eq("time"), isNull());
        }

        @Test
        void notifyHangoutUpdated_WhenUserHasNoDevices_ContinuesToNextUser() {
            // Given: 2 users, first has no devices, second has 1 device
            Set<String> interestedUserIds = new HashSet<>(Set.of(USER_1_ID, USER_2_ID));
            List<String> groupIds = List.of(GROUP_ID);

            Device device2 = createDevice("ios-token-user2", Device.Platform.IOS);

            // First user has no devices
            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_1_ID)))
                .thenReturn(List.of());
            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_2_ID)))
                .thenReturn(List.of(device2));

            // When
            notificationService.notifyHangoutUpdated(HANGOUT_ID, HANGOUT_TITLE, groupIds,
                "time_and_location", UPDATER_USER_ID, interestedUserIds, "Coffee Shop");

            // Then: second user still receives notification with location
            verify(pushNotificationService).sendHangoutUpdatedNotification(
                eq("ios-token-user2"), eq(HANGOUT_ID), eq(GROUP_ID), eq(HANGOUT_TITLE), eq("time_and_location"), eq("Coffee Shop"));
        }
    }

    // ================= Send Hangout Reminder Tests =================

    @Nested
    class SendHangoutReminderTests {

        private static final String HANGOUT_ID = "00000000-0000-0000-0000-000000000100";
        private static final String GROUP_ID = "00000000-0000-0000-0000-000000000201";
        private static final String USER_1_ID = "00000000-0000-0000-0000-000000000002";
        private static final String USER_2_ID = "00000000-0000-0000-0000-000000000003";
        private static final String USER_3_ID = "00000000-0000-0000-0000-000000000004";

        private Hangout createHangout() {
            Hangout hangout = new Hangout();
            hangout.setHangoutId(HANGOUT_ID);
            hangout.setTitle("Test Hangout");
            hangout.setAssociatedGroups(List.of(GROUP_ID));
            return hangout;
        }

        private Device createDevice(String token, Device.Platform platform) {
            Device device = new Device();
            device.setToken(token);
            device.setPlatform(platform);
            device.setActive(true);
            return device;
        }

        private InterestLevel createInterestLevel(String userId, String status) {
            InterestLevel interestLevel = new InterestLevel();
            interestLevel.setUserId(userId);
            interestLevel.setStatus(status);
            return interestLevel;
        }

        @Test
        void sendHangoutReminder_NoAssociatedGroups_SkipsNotification() {
            // Given: Hangout with null groups
            Hangout hangout = createHangout();
            hangout.setAssociatedGroups(null);

            // When
            notificationService.sendHangoutReminder(hangout);

            // Then: No calls to repository or notification services
            verifyNoInteractions(hangoutRepository);
            verifyNoInteractions(pushNotificationService);
            verifyNoInteractions(fcmNotificationService);
        }

        @Test
        void sendHangoutReminder_EmptyAssociatedGroups_SkipsNotification() {
            // Given: Hangout with empty groups
            Hangout hangout = createHangout();
            hangout.setAssociatedGroups(List.of());

            // When
            notificationService.sendHangoutReminder(hangout);

            // Then: No calls to repository or notification services
            verifyNoInteractions(hangoutRepository);
            verifyNoInteractions(pushNotificationService);
            verifyNoInteractions(fcmNotificationService);
        }

        @Test
        void sendHangoutReminder_NoAttendanceRecords_SkipsNotification() {
            // Given: Hangout with no attendance
            Hangout hangout = createHangout();
            HangoutDetailData detailData = HangoutDetailData.builder()
                    .withHangout(hangout)
                    .withAttendance(List.of())
                    .build();
            when(hangoutRepository.getHangoutDetailData(HANGOUT_ID)).thenReturn(detailData);

            // When
            notificationService.sendHangoutReminder(hangout);

            // Then: No notification sent
            verify(hangoutRepository).getHangoutDetailData(HANGOUT_ID);
            verifyNoInteractions(deviceService);
            verifyNoInteractions(pushNotificationService);
            verifyNoInteractions(fcmNotificationService);
        }

        @Test
        void sendHangoutReminder_OnlyNotGoingUsers_SkipsNotification() {
            // Given: Only NOT_GOING users
            Hangout hangout = createHangout();
            List<InterestLevel> attendance = List.of(
                createInterestLevel(USER_1_ID, "NOT_GOING"),
                createInterestLevel(USER_2_ID, "NOT_GOING")
            );
            HangoutDetailData detailData = HangoutDetailData.builder()
                    .withHangout(hangout)
                    .withAttendance(attendance)
                    .build();
            when(hangoutRepository.getHangoutDetailData(HANGOUT_ID)).thenReturn(detailData);

            // When
            notificationService.sendHangoutReminder(hangout);

            // Then: No notification sent
            verifyNoInteractions(deviceService);
            verifyNoInteractions(pushNotificationService);
            verifyNoInteractions(fcmNotificationService);
        }

        @Test
        void sendHangoutReminder_GoingUsers_SendsToIosDevices() {
            // Given: User with GOING status and iOS device
            Hangout hangout = createHangout();
            List<InterestLevel> attendance = List.of(
                createInterestLevel(USER_1_ID, "GOING")
            );
            HangoutDetailData detailData = HangoutDetailData.builder()
                    .withHangout(hangout)
                    .withAttendance(attendance)
                    .build();
            when(hangoutRepository.getHangoutDetailData(HANGOUT_ID)).thenReturn(detailData);

            Device iosDevice = createDevice("ios-token-123", Device.Platform.IOS);
            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_1_ID)))
                .thenReturn(List.of(iosDevice));

            when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(mockCounter);

            // When
            notificationService.sendHangoutReminder(hangout);

            // Then: iOS notification sent
            verify(pushNotificationService).sendHangoutReminderNotification(
                eq("ios-token-123"), eq(hangout), eq(GROUP_ID));
            verifyNoInteractions(fcmNotificationService);
        }

        @Test
        void sendHangoutReminder_InterestedUsers_SendsToAndroidDevices() {
            // Given: User with INTERESTED status and Android device
            Hangout hangout = createHangout();
            List<InterestLevel> attendance = List.of(
                createInterestLevel(USER_1_ID, "INTERESTED")
            );
            HangoutDetailData detailData = HangoutDetailData.builder()
                    .withHangout(hangout)
                    .withAttendance(attendance)
                    .build();
            when(hangoutRepository.getHangoutDetailData(HANGOUT_ID)).thenReturn(detailData);

            Device androidDevice = createDevice("android-token-456", Device.Platform.ANDROID);
            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_1_ID)))
                .thenReturn(List.of(androidDevice));

            when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(mockCounter);

            // When
            notificationService.sendHangoutReminder(hangout);

            // Then: Android notification sent
            verify(fcmNotificationService).sendHangoutReminderNotification(
                eq("android-token-456"), eq(hangout), eq(GROUP_ID));
            verifyNoInteractions(pushNotificationService);
        }

        @Test
        void sendHangoutReminder_MixedStatuses_OnlySendsToGoingAndInterested() {
            // Given: Mix of GOING, INTERESTED, and NOT_GOING users
            Hangout hangout = createHangout();
            List<InterestLevel> attendance = List.of(
                createInterestLevel(USER_1_ID, "GOING"),
                createInterestLevel(USER_2_ID, "INTERESTED"),
                createInterestLevel(USER_3_ID, "NOT_GOING")
            );
            HangoutDetailData detailData = HangoutDetailData.builder()
                    .withHangout(hangout)
                    .withAttendance(attendance)
                    .build();
            when(hangoutRepository.getHangoutDetailData(HANGOUT_ID)).thenReturn(detailData);

            Device device1 = createDevice("ios-token-1", Device.Platform.IOS);
            Device device2 = createDevice("ios-token-2", Device.Platform.IOS);
            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_1_ID)))
                .thenReturn(List.of(device1));
            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_2_ID)))
                .thenReturn(List.of(device2));

            when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(mockCounter);

            // When
            notificationService.sendHangoutReminder(hangout);

            // Then: Only 2 notifications sent (GOING and INTERESTED)
            verify(pushNotificationService, times(2)).sendHangoutReminderNotification(
                anyString(), eq(hangout), eq(GROUP_ID));
            verify(deviceService, never()).getActiveDevicesForUser(UUID.fromString(USER_3_ID));
        }

        @Test
        void sendHangoutReminder_UserWithNoDevices_ContinuesToNextUser() {
            // Given: Two users, first has no devices
            Hangout hangout = createHangout();
            List<InterestLevel> attendance = List.of(
                createInterestLevel(USER_1_ID, "GOING"),
                createInterestLevel(USER_2_ID, "GOING")
            );
            HangoutDetailData detailData = HangoutDetailData.builder()
                    .withHangout(hangout)
                    .withAttendance(attendance)
                    .build();
            when(hangoutRepository.getHangoutDetailData(HANGOUT_ID)).thenReturn(detailData);

            // First user has no devices
            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_1_ID)))
                .thenReturn(List.of());
            Device device2 = createDevice("ios-token-2", Device.Platform.IOS);
            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_2_ID)))
                .thenReturn(List.of(device2));

            when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(mockCounter);

            // When
            notificationService.sendHangoutReminder(hangout);

            // Then: Second user still receives notification
            verify(pushNotificationService).sendHangoutReminderNotification(
                eq("ios-token-2"), eq(hangout), eq(GROUP_ID));
        }

        @Test
        void sendHangoutReminder_DeviceSendFails_ContinuesToNextDevice() {
            // Given: User has two devices, first fails
            Hangout hangout = createHangout();
            List<InterestLevel> attendance = List.of(
                createInterestLevel(USER_1_ID, "GOING")
            );
            HangoutDetailData detailData = HangoutDetailData.builder()
                    .withHangout(hangout)
                    .withAttendance(attendance)
                    .build();
            when(hangoutRepository.getHangoutDetailData(HANGOUT_ID)).thenReturn(detailData);

            Device iosDevice = createDevice("ios-token-fail", Device.Platform.IOS);
            Device androidDevice = createDevice("android-token-success", Device.Platform.ANDROID);
            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_1_ID)))
                .thenReturn(List.of(iosDevice, androidDevice));

            // iOS notification fails
            doThrow(new RuntimeException("APNS error"))
                .when(pushNotificationService).sendHangoutReminderNotification(
                    eq("ios-token-fail"), any(), anyString());

            when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(mockCounter);

            // When
            notificationService.sendHangoutReminder(hangout);

            // Then: Both services attempted, Android still receives notification
            verify(pushNotificationService).sendHangoutReminderNotification(
                eq("ios-token-fail"), eq(hangout), eq(GROUP_ID));
            verify(fcmNotificationService).sendHangoutReminderNotification(
                eq("android-token-success"), eq(hangout), eq(GROUP_ID));
        }

        @Test
        void sendHangoutReminder_EmitsSuccessMetrics() {
            // Given: Two users with devices
            Hangout hangout = createHangout();
            List<InterestLevel> attendance = List.of(
                createInterestLevel(USER_1_ID, "GOING"),
                createInterestLevel(USER_2_ID, "GOING")
            );
            HangoutDetailData detailData = HangoutDetailData.builder()
                    .withHangout(hangout)
                    .withAttendance(attendance)
                    .build();
            when(hangoutRepository.getHangoutDetailData(HANGOUT_ID)).thenReturn(detailData);

            Device device1 = createDevice("ios-token-1", Device.Platform.IOS);
            Device device2 = createDevice("ios-token-2", Device.Platform.IOS);
            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_1_ID)))
                .thenReturn(List.of(device1));
            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_2_ID)))
                .thenReturn(List.of(device2));

            when(meterRegistry.counter("hangout_reminder_notification_total", "status", "success"))
                .thenReturn(mockCounter);

            // When
            notificationService.sendHangoutReminder(hangout);

            // Then: Success metric incremented
            verify(meterRegistry).counter("hangout_reminder_notification_total", "status", "success");
            verify(mockCounter).increment(2);
        }

        @Test
        void sendHangoutReminder_DeviceLookupFails_NoSuccessNoFailure() {
            // Given: User with device lookup that fails
            // Note: sendHangoutReminderToUser catches exceptions internally and returns false,
            // so this scenario results in sent=false but not counted as a "failure"
            Hangout hangout = createHangout();
            List<InterestLevel> attendance = List.of(
                createInterestLevel(USER_1_ID, "GOING")
            );
            HangoutDetailData detailData = HangoutDetailData.builder()
                    .withHangout(hangout)
                    .withAttendance(attendance)
                    .build();
            when(hangoutRepository.getHangoutDetailData(HANGOUT_ID)).thenReturn(detailData);

            // Device lookup fails - exception is caught inside sendHangoutReminderToUser
            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_1_ID)))
                .thenThrow(new RuntimeException("Device lookup failed"));

            Counter successCounter = mock(Counter.class);
            when(meterRegistry.counter("hangout_reminder_notification_total", "status", "success"))
                .thenReturn(successCounter);

            // When
            notificationService.sendHangoutReminder(hangout);

            // Then: Success counter called with 0 (no successes)
            // Failure counter not called since exception was caught internally
            verify(successCounter).increment(0);
            verify(meterRegistry, never()).counter("hangout_reminder_notification_total", "status", "failure");
        }

        @Test
        void sendHangoutReminder_ExceptionDuringProcessing_EmitsErrorMetric() {
            // Given: Repository throws unexpected exception
            Hangout hangout = createHangout();
            when(hangoutRepository.getHangoutDetailData(HANGOUT_ID))
                .thenThrow(new RuntimeException("Unexpected database error"));

            when(meterRegistry.counter("hangout_reminder_notification_total",
                "status", "error", "error_type", "unexpected")).thenReturn(mockCounter);

            // When
            notificationService.sendHangoutReminder(hangout);

            // Then: Error metric emitted
            verify(meterRegistry).counter("hangout_reminder_notification_total",
                "status", "error", "error_type", "unexpected");
            verify(mockCounter).increment();
        }
    }

    // ================= Watch Party Update Notification Tests =================

    @Nested
    class NotifyWatchPartyUpdateTests {

        private static final String SERIES_ID = "00000000-0000-0000-0000-000000000300";
        private static final String GROUP_ID = "00000000-0000-0000-0000-000000000201";
        private static final String USER_1_ID = "00000000-0000-0000-0000-000000000002";
        private static final String USER_2_ID = "00000000-0000-0000-0000-000000000003";
        private static final String TEST_MESSAGE = "New episode: Breaking Bad S5E3";

        private Device createDevice(String token, Device.Platform platform) {
            Device device = new Device();
            device.setToken(token);
            device.setPlatform(platform);
            device.setActive(true);
            return device;
        }

        @Test
        void notifyWatchPartyUpdate_WithIosDevices_SendsViaPushNotificationService() {
            // Given
            Set<String> userIds = new HashSet<>(Set.of(USER_1_ID));
            Device iosDevice = createDevice("ios-token-123", Device.Platform.IOS);

            EventSeries series = new EventSeries();
            series.setSeriesId(SERIES_ID);
            series.setGroupId(GROUP_ID);

            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_1_ID)))
                .thenReturn(List.of(iosDevice));
            when(eventSeriesRepository.findById(SERIES_ID))
                .thenReturn(Optional.of(series));
            when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(mockCounter);

            // When
            notificationService.notifyWatchPartyUpdate(userIds, SERIES_ID, TEST_MESSAGE);

            // Then
            verify(pushNotificationService).sendWatchPartyNotification(
                eq("ios-token-123"),
                eq(SERIES_ID),
                eq(GROUP_ID),
                eq(TEST_MESSAGE)
            );
            verify(fcmNotificationService, never()).sendWatchPartyNotification(
                anyString(), anyString(), anyString(), anyString());
        }

        @Test
        void notifyWatchPartyUpdate_WithAndroidDevices_SendsViaFcmNotificationService() {
            // Given
            Set<String> userIds = new HashSet<>(Set.of(USER_1_ID));
            Device androidDevice = createDevice("android-token-456", Device.Platform.ANDROID);

            EventSeries series = new EventSeries();
            series.setSeriesId(SERIES_ID);
            series.setGroupId(GROUP_ID);

            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_1_ID)))
                .thenReturn(List.of(androidDevice));
            when(eventSeriesRepository.findById(SERIES_ID))
                .thenReturn(Optional.of(series));
            when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(mockCounter);

            // When
            notificationService.notifyWatchPartyUpdate(userIds, SERIES_ID, TEST_MESSAGE);

            // Then
            verify(fcmNotificationService).sendWatchPartyNotification(
                eq("android-token-456"),
                eq(SERIES_ID),
                eq(GROUP_ID),
                eq(TEST_MESSAGE)
            );
            verify(pushNotificationService, never()).sendWatchPartyNotification(
                anyString(), anyString(), anyString(), anyString());
        }

        @Test
        void notifyWatchPartyUpdate_WithMixedDevices_SendsToBothPlatforms() {
            // Given
            Set<String> userIds = new HashSet<>(Set.of(USER_1_ID));
            Device iosDevice = createDevice("ios-token-123", Device.Platform.IOS);
            Device androidDevice = createDevice("android-token-456", Device.Platform.ANDROID);

            EventSeries series = new EventSeries();
            series.setSeriesId(SERIES_ID);
            series.setGroupId(GROUP_ID);

            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_1_ID)))
                .thenReturn(List.of(iosDevice, androidDevice));
            when(eventSeriesRepository.findById(SERIES_ID))
                .thenReturn(Optional.of(series));
            when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(mockCounter);

            // When
            notificationService.notifyWatchPartyUpdate(userIds, SERIES_ID, TEST_MESSAGE);

            // Then
            verify(pushNotificationService).sendWatchPartyNotification(
                eq("ios-token-123"), eq(SERIES_ID), eq(GROUP_ID), eq(TEST_MESSAGE));
            verify(fcmNotificationService).sendWatchPartyNotification(
                eq("android-token-456"), eq(SERIES_ID), eq(GROUP_ID), eq(TEST_MESSAGE));
        }

        @Test
        void notifyWatchPartyUpdate_WithNullUserIds_DoesNothing() {
            // When
            notificationService.notifyWatchPartyUpdate(null, SERIES_ID, TEST_MESSAGE);

            // Then
            verifyNoInteractions(deviceService);
            verifyNoInteractions(pushNotificationService);
            verifyNoInteractions(fcmNotificationService);
        }

        @Test
        void notifyWatchPartyUpdate_WithEmptyUserIds_DoesNothing() {
            // When
            notificationService.notifyWatchPartyUpdate(new HashSet<>(), SERIES_ID, TEST_MESSAGE);

            // Then
            verifyNoInteractions(deviceService);
            verifyNoInteractions(pushNotificationService);
            verifyNoInteractions(fcmNotificationService);
        }

        @Test
        void notifyWatchPartyUpdate_WithNoDevices_ContinuesGracefully() {
            // Given
            Set<String> userIds = new HashSet<>(Set.of(USER_1_ID));

            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_1_ID)))
                .thenReturn(List.of());
            when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(mockCounter);

            // When
            notificationService.notifyWatchPartyUpdate(userIds, SERIES_ID, TEST_MESSAGE);

            // Then
            verifyNoInteractions(pushNotificationService);
            verifyNoInteractions(fcmNotificationService);
        }

        @Test
        void notifyWatchPartyUpdate_WhenSeriesNotFound_SendsWithNullGroupId() {
            // Given
            Set<String> userIds = new HashSet<>(Set.of(USER_1_ID));
            Device iosDevice = createDevice("ios-token-123", Device.Platform.IOS);

            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_1_ID)))
                .thenReturn(List.of(iosDevice));
            when(eventSeriesRepository.findById(SERIES_ID))
                .thenReturn(Optional.empty());
            when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(mockCounter);

            // When
            notificationService.notifyWatchPartyUpdate(userIds, SERIES_ID, TEST_MESSAGE);

            // Then
            verify(pushNotificationService).sendWatchPartyNotification(
                eq("ios-token-123"),
                eq(SERIES_ID),
                isNull(),
                eq(TEST_MESSAGE)
            );
        }

        @Test
        void notifyWatchPartyUpdate_WithMultipleUsers_SendsToEachUser() {
            // Given
            Set<String> userIds = new HashSet<>(Set.of(USER_1_ID, USER_2_ID));
            Device device1 = createDevice("ios-token-1", Device.Platform.IOS);
            Device device2 = createDevice("ios-token-2", Device.Platform.IOS);

            EventSeries series = new EventSeries();
            series.setSeriesId(SERIES_ID);
            series.setGroupId(GROUP_ID);

            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_1_ID)))
                .thenReturn(List.of(device1));
            when(deviceService.getActiveDevicesForUser(UUID.fromString(USER_2_ID)))
                .thenReturn(List.of(device2));
            when(eventSeriesRepository.findById(SERIES_ID))
                .thenReturn(Optional.of(series));
            when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(mockCounter);

            // When
            notificationService.notifyWatchPartyUpdate(userIds, SERIES_ID, TEST_MESSAGE);

            // Then
            verify(pushNotificationService, times(2)).sendWatchPartyNotification(
                anyString(), eq(SERIES_ID), eq(GROUP_ID), eq(TEST_MESSAGE));
        }
    }
}