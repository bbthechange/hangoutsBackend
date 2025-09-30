package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.service.DeviceService;
import com.bbthechange.inviter.service.PushNotificationService;
import org.junit.jupiter.api.BeforeEach;
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
}