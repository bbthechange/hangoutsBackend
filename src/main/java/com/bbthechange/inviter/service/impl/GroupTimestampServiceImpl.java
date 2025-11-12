package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.model.Group;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.service.GroupTimestampService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Implementation of GroupTimestampService.
 * Handles updating Group.lastHangoutModified timestamps for ETag support and cache invalidation.
 */
@Service
public class GroupTimestampServiceImpl implements GroupTimestampService {

    private static final Logger logger = LoggerFactory.getLogger(GroupTimestampServiceImpl.class);

    private final GroupRepository groupRepository;

    @Autowired
    public GroupTimestampServiceImpl(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    @Override
    public void updateGroupTimestamps(List<String> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return;
        }

        Instant now = Instant.now();

        for (String groupId : groupIds) {
            try {
                // Read group metadata
                Optional<Group> groupOpt = groupRepository.findById(groupId);
                if (groupOpt.isEmpty()) {
                    logger.warn("Cannot update lastHangoutModified for non-existent group: {}", groupId);
                    continue;
                }

                Group group = groupOpt.get();
                group.setLastHangoutModified(now);
                groupRepository.save(group);

                logger.debug("Updated lastHangoutModified for group {} to {}", groupId, now);
            } catch (Exception e) {
                logger.error("Failed to update lastHangoutModified for group {}: {}", groupId, e.getMessage());
                // Continue with other groups - don't fail the whole operation
            }
        }
    }
}
