package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.service.AuthorizationService;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.model.Event;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.model.GroupMembership;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Implementation of AuthorizationService.
 * Contains authorization logic extracted from HangoutService to break circular dependencies.
 */
@Service
public class AuthorizationServiceImpl implements AuthorizationService {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthorizationServiceImpl.class);
    
    private final GroupRepository groupRepository;
    
    @Autowired
    public AuthorizationServiceImpl(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }
    
    
    @Override
    public boolean canUserViewHangout(String userId, Hangout hangout) {
        // Check if user is in any of the hangout's associated groups
        List<String> hangoutGroupIds = hangout.getAssociatedGroups();
        if (hangoutGroupIds == null || hangoutGroupIds.isEmpty()) {
            return false; // Hangouts without groups are not viewable
        }
        
        return hangoutGroupIds.stream().anyMatch(groupId -> isUserInGroup(userId, groupId));
    }
    
    @Override
    public boolean canUserEditHangout(String userId, Hangout hangout) {
        // For now, same logic as canUserViewHangout - all group members can edit
        return canUserViewHangout(userId, hangout);
    }
    
    private boolean isUserInGroup(String userId, String groupId) {
        try {
            List<GroupMembership> memberships = groupRepository.findMembersByGroupId(groupId);
            return memberships.stream()
                .anyMatch(membership -> membership.getUserId().equals(userId));
        } catch (Exception e) {
            logger.warn("Error checking group membership for user {} in group {}: {}", 
                       userId, groupId, e.getMessage());
            return false;
        }
    }
}