package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.model.Device;
import com.bbthechange.inviter.model.Group;
import com.bbthechange.inviter.model.GroupMembership;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.service.DeviceService;
import com.bbthechange.inviter.service.NotificationService;
import com.bbthechange.inviter.service.PushNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

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

    @Autowired
    public NotificationServiceImpl(GroupRepository groupRepository,
                                   DeviceService deviceService,
                                   PushNotificationService pushNotificationService) {
        this.groupRepository = groupRepository;
        this.deviceService = deviceService;
        this.pushNotificationService = pushNotificationService;
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

            // Send notification to each device
            for (Device device : devices) {
                if (device.getPlatform() == Device.Platform.IOS) {
                    try {
                        // Use first group for notification context (all groups share same hangout)
                        String primaryGroupId = groupIds.get(0);
                        String groupName = getGroupName(primaryGroupId);

                        pushNotificationService.sendNewHangoutNotification(
                            device.getToken(),
                            hangoutId,
                            primaryGroupId,
                            hangoutTitle,
                            groupName,
                            creatorName
                        );
                        anySent = true;
                    } catch (Exception e) {
                        logger.error("Failed to send notification to device {}: {}",
                            device.getToken().substring(0, Math.min(8, device.getToken().length())),
                            e.getMessage());
                    }
                }
                // Future: Add Android support here
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
}