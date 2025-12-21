package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.model.Device;
import com.bbthechange.inviter.model.Group;
import com.bbthechange.inviter.model.GroupMembership;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.dto.UserSummaryDTO;
import com.bbthechange.inviter.service.DeviceService;
import com.bbthechange.inviter.service.FcmNotificationService;
import com.bbthechange.inviter.service.NotificationService;
import com.bbthechange.inviter.service.PushNotificationService;
import com.bbthechange.inviter.service.UserService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Implementation of NotificationService that handles user deduplication,
 * device resolution, and delegation to specific notification channels.
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final GroupRepository groupRepository;
    private final DeviceService deviceService;
    private final PushNotificationService pushNotificationService;
    private final FcmNotificationService fcmNotificationService;
    private final UserService userService;
    private final MeterRegistry meterRegistry;

    @Autowired
    public NotificationServiceImpl(GroupRepository groupRepository,
                                   DeviceService deviceService,
                                   PushNotificationService pushNotificationService,
                                   FcmNotificationService fcmNotificationService,
                                   UserService userService,
                                   MeterRegistry meterRegistry) {
        this.groupRepository = groupRepository;
        this.deviceService = deviceService;
        this.pushNotificationService = pushNotificationService;
        this.fcmNotificationService = fcmNotificationService;
        this.userService = userService;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void notifyNewHangout(Hangout hangout, String creatorUserId, String creatorName) {
        List<String> groupIds = hangout.getAssociatedGroups();
        if (groupIds == null || groupIds.isEmpty()) {
            logger.debug("No groups specified for hangout {}, skipping notifications", hangout.getHangoutId());
            return;
        }

        try {
            // Collect all unique user IDs from all groups (deduplication happens here)
            Set<String> uniqueUserIds = collectUniqueUsersFromGroups(groupIds);

            // Remove creator from notification list
            uniqueUserIds.remove(creatorUserId);

            if (uniqueUserIds.isEmpty()) {
                logger.debug("No users to notify for hangout {} (only creator in groups)", hangout.getHangoutId());
                return;
            }

            logger.info("Sending new hangout notifications for {} to {} unique users",
                       hangout.getHangoutId(), uniqueUserIds.size());

            // Send notifications to each unique user
            int successCount = 0;
            int failureCount = 0;

            for (String userId : uniqueUserIds) {
                try {
                    boolean sent = sendNewHangoutNotificationToUser(
                        userId, hangout.getHangoutId(), hangout.getTitle(), groupIds, creatorName
                    );
                    if (sent) {
                        successCount++;
                    }
                } catch (Exception e) {
                    failureCount++;
                    logger.warn("Failed to send notification to user {}: {}", userId, e.getMessage());
                }
            }

            logger.info("New hangout notification summary for {}: {} sent, {} failed",
                       hangout.getHangoutId(), successCount, failureCount);

        } catch (Exception e) {
            // Log error but don't throw - notifications shouldn't break hangout creation
            logger.error("Error sending new hangout notifications for {}: {}", hangout.getHangoutId(), e.getMessage(), e);
        }
    }

    /**
     * Collect all unique user IDs from the specified groups.
     * Automatically deduplicates users who are members of multiple groups.
     */
    private Set<String> collectUniqueUsersFromGroups(List<String> groupIds) {
        Set<String> uniqueUserIds = new HashSet<>();

        for (String groupId : groupIds) {
            try {
                List<GroupMembership> members = groupRepository.findMembersByGroupId(groupId);
                members.stream()
                    .map(GroupMembership::getUserId)
                    .forEach(uniqueUserIds::add);
            } catch (Exception e) {
                logger.warn("Failed to get members for group {}: {}", groupId, e.getMessage());
            }
        }

        return uniqueUserIds;
    }

    /**
     * Send new hangout notification to all active devices for a single user.
     * Returns true if at least one notification was sent successfully.
     */
    private boolean sendNewHangoutNotificationToUser(String userId, String hangoutId,
                                                     String hangoutTitle, List<String> groupIds,
                                                     String creatorName) {
        try {
            // Get all active devices for this user
            List<Device> devices = deviceService.getActiveDevicesForUser(UUID.fromString(userId));

            if (devices.isEmpty()) {
                logger.debug("No active devices for user {}", userId);
                return false;
            }

            boolean anySent = false;

            // Use first group for notification context (all groups share same hangout)
            String primaryGroupId = groupIds.get(0);
            String groupName = getGroupName(primaryGroupId);

            // Send notification to each device
            for (Device device : devices) {
                try {
                    if (device.getPlatform() == Device.Platform.IOS) {
                        pushNotificationService.sendNewHangoutNotification(
                            device.getToken(),
                            hangoutId,
                            primaryGroupId,
                            hangoutTitle,
                            groupName,
                            creatorName
                        );
                        anySent = true;
                    } else if (device.getPlatform() == Device.Platform.ANDROID) {
                        fcmNotificationService.sendNewHangoutNotification(
                            device.getToken(),
                            hangoutId,
                            primaryGroupId,
                            hangoutTitle,
                            groupName,
                            creatorName
                        );
                        anySent = true;
                    }
                } catch (Exception e) {
                    logger.error("Failed to send notification to device {}: {}",
                        device.getToken().substring(0, Math.min(8, device.getToken().length())),
                        e.getMessage());
                }
            }

            return anySent;

        } catch (Exception e) {
            logger.warn("Failed to send notifications to user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Get group name with fallback to "Unknown Group".
     */
    private String getGroupName(String groupId) {
        try {
            Optional<Group> groupOpt = groupRepository.findById(groupId);
            if (groupOpt.isPresent()) {
                return groupOpt.get().getGroupName();
            }
        } catch (Exception e) {
            logger.warn("Failed to get group name for {}: {}", groupId, e.getMessage());
        }
        return "Unknown Group";
    }

    @Override
    public void notifyGroupMemberAdded(String groupId, String groupName, String addedUserId, String adderUserId) {
        // Skip notification for self-join
        if (addedUserId.equals(adderUserId)) {
            logger.debug("Skipping group member added notification - user {} added themselves to group {}",
                    addedUserId, groupId);
            return;
        }

        String adderName = getAdderDisplayName(adderUserId);
        boolean sent = sendGroupMemberAddedNotificationToUser(addedUserId, groupId, groupName, adderName);

        if (sent) {
            logger.info("Group member added notification sent for user {} added to group {} by {}",
                    addedUserId, groupId, adderUserId);
        } else {
            logger.debug("No devices found for user {} to send group member added notification", addedUserId);
        }
    }

    /**
     * Get the display name for the user who added the member.
     * Falls back to "Unknown" if user cannot be found.
     */
    private String getAdderDisplayName(String adderUserId) {
        try {
            Optional<UserSummaryDTO> userOpt = userService.getUserSummary(UUID.fromString(adderUserId));
            if (userOpt.isPresent() && userOpt.get().getDisplayName() != null) {
                return userOpt.get().getDisplayName();
            }
        } catch (Exception e) {
            logger.warn("Failed to get display name for adder {}: {}", adderUserId, e.getMessage());
            meterRegistry.counter("group_member_added_notification_total",
                    "status", "error", "error_type", "adder_lookup_failed").increment();
        }
        return "Unknown";
    }

    /**
     * Send group member added notification to all active devices for the added user.
     * Returns true if at least one notification was sent.
     */
    private boolean sendGroupMemberAddedNotificationToUser(String userId, String groupId,
                                                            String groupName, String adderName) {
        try {
            List<Device> devices = deviceService.getActiveDevicesForUser(UUID.fromString(userId));

            if (devices.isEmpty()) {
                logger.debug("No active devices for user {}", userId);
                meterRegistry.counter("group_member_added_notification_total",
                        "status", "skipped", "reason", "no_devices").increment();
                return false;
            }

            boolean anySent = false;

            for (Device device : devices) {
                try {
                    if (device.getPlatform() == Device.Platform.IOS) {
                        pushNotificationService.sendGroupMemberAddedNotification(
                            device.getToken(),
                            groupId,
                            groupName,
                            adderName
                        );
                        anySent = true;
                    } else if (device.getPlatform() == Device.Platform.ANDROID) {
                        fcmNotificationService.sendGroupMemberAddedNotification(
                            device.getToken(),
                            groupId,
                            groupName,
                            adderName
                        );
                        anySent = true;
                    }
                } catch (Exception e) {
                    logger.error("Failed to send group member added notification to device {}: {}",
                        device.getToken().substring(0, Math.min(8, device.getToken().length())),
                        e.getMessage());
                    meterRegistry.counter("group_member_added_notification_total",
                            "status", "error", "error_type", "device_send_failed").increment();
                }
            }

            if (anySent) {
                meterRegistry.counter("group_member_added_notification_total",
                        "status", "success").increment();
            }

            return anySent;

        } catch (Exception e) {
            logger.warn("Failed to send group member added notifications to user {}: {}", userId, e.getMessage());
            meterRegistry.counter("group_member_added_notification_total",
                    "status", "error", "error_type", "device_lookup_failed").increment();
            return false;
        }
    }

    @Override
    public void notifyHangoutUpdated(String hangoutId, String hangoutTitle, List<String> groupIds,
                                      String changeType, String updatedByUserId, Set<String> interestedUserIds) {
        if (interestedUserIds == null || interestedUserIds.isEmpty()) {
            logger.debug("No interested users to notify for hangout update {}", hangoutId);
            return;
        }

        if (groupIds == null || groupIds.isEmpty()) {
            logger.debug("No groups associated with hangout {}, skipping update notifications", hangoutId);
            return;
        }

        try {
            // Remove the user who made the change from notification list
            Set<String> usersToNotify = new HashSet<>(interestedUserIds);
            usersToNotify.remove(updatedByUserId);

            if (usersToNotify.isEmpty()) {
                logger.debug("No users to notify for hangout update {} (only updater is interested)", hangoutId);
                return;
            }

            logger.info("Sending hangout update ({}) notifications for {} to {} users",
                       changeType, hangoutId, usersToNotify.size());

            // Use first group for notification context
            String primaryGroupId = groupIds.get(0);

            int successCount = 0;
            int failureCount = 0;

            for (String userId : usersToNotify) {
                try {
                    boolean sent = sendHangoutUpdatedNotificationToUser(
                        userId, hangoutId, primaryGroupId, hangoutTitle, changeType
                    );
                    if (sent) {
                        successCount++;
                    }
                } catch (Exception e) {
                    failureCount++;
                    logger.warn("Failed to send hangout update notification to user {}: {}", userId, e.getMessage());
                }
            }

            logger.info("Hangout update notification summary for {}: {} sent, {} failed",
                       hangoutId, successCount, failureCount);

        } catch (Exception e) {
            logger.error("Error sending hangout update notifications for {}: {}", hangoutId, e.getMessage(), e);
        }
    }

    /**
     * Send hangout update notification to all active devices for a single user.
     * Returns true if at least one notification was sent successfully.
     */
    private boolean sendHangoutUpdatedNotificationToUser(String userId, String hangoutId,
                                                          String groupId, String hangoutTitle,
                                                          String changeType) {
        try {
            List<Device> devices = deviceService.getActiveDevicesForUser(UUID.fromString(userId));

            if (devices.isEmpty()) {
                logger.debug("No active devices for user {}", userId);
                return false;
            }

            boolean anySent = false;

            for (Device device : devices) {
                try {
                    if (device.getPlatform() == Device.Platform.IOS) {
                        pushNotificationService.sendHangoutUpdatedNotification(
                            device.getToken(),
                            hangoutId,
                            groupId,
                            hangoutTitle,
                            changeType
                        );
                        anySent = true;
                    } else if (device.getPlatform() == Device.Platform.ANDROID) {
                        fcmNotificationService.sendHangoutUpdatedNotification(
                            device.getToken(),
                            hangoutId,
                            groupId,
                            hangoutTitle,
                            changeType
                        );
                        anySent = true;
                    }
                } catch (Exception e) {
                    logger.error("Failed to send hangout update notification to device {}: {}",
                        device.getToken().substring(0, Math.min(8, device.getToken().length())),
                        e.getMessage());
                }
            }

            return anySent;

        } catch (Exception e) {
            logger.warn("Failed to send hangout update notifications to user {}: {}", userId, e.getMessage());
            return false;
        }
    }
}