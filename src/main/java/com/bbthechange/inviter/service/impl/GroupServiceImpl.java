package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.service.GroupService;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.UserRepository;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.exception.*;
import com.bbthechange.inviter.service.InviteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of GroupService with atomic operations and efficient GSI patterns.
 * Follows multi-step pattern from implementation plan.
 */
@Service
@Transactional
public class GroupServiceImpl implements GroupService {
    
    private static final Logger logger = LoggerFactory.getLogger(GroupServiceImpl.class);
    
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final InviteService inviteService;
    
    @Autowired
    public GroupServiceImpl(GroupRepository groupRepository, UserRepository userRepository, InviteService inviteService) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.inviteService = inviteService;
    }
    
    @Override
    public GroupDTO createGroup(CreateGroupRequest request, String creatorId) {
        // Input validation
        validateCreateGroupRequest(request);
        
        // Verify creator exists
        userRepository.findById(UUID.fromString(creatorId))
            .orElseThrow(() -> new UserNotFoundException("Creator not found: " + creatorId));
        
        // Create both records for atomic operation
        Group group = new Group(request.getGroupName(), request.isPublic());
        GroupMembership membership = new GroupMembership(
            group.getGroupId(), 
            creatorId, 
            group.getGroupName() // Denormalize for GSI efficiency
        );
        membership.setRole(GroupRole.ADMIN); // Creator is admin
        
        // Atomic creation using TransactWriteItems pattern
        groupRepository.createGroupWithFirstMember(group, membership);
        
        logger.info("Created group {} with creator {}", group.getGroupId(), creatorId);
        return new GroupDTO(group, membership);
    }
    
    @Override
    public GroupDTO getGroup(String groupId, String requestingUserId) {
        // Get group metadata
        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));
        
        // Get user's membership in this group
        GroupMembership membership = groupRepository.findMembership(groupId, requestingUserId)
            .orElseThrow(() -> new UnauthorizedException("User not in group"));
        
        return new GroupDTO(group, membership);
    }
    
    @Override
    public void deleteGroup(String groupId, String requestingUserId) {
        // Verify group exists and user is admin
        GroupMembership membership = groupRepository.findMembership(groupId, requestingUserId)
            .orElseThrow(() -> new UnauthorizedException("User not in group"));
        
        if (!membership.isAdmin()) {
            throw new UnauthorizedException("Only admins can delete groups");
        }
        
        // Repository handles complete deletion: group metadata, all members, and hangout pointers
        groupRepository.delete(groupId);
        logger.info("Deleted group {} by user {}", groupId, requestingUserId);
    }
    
    @Override
    public void addMember(String groupId, String userId, String phoneNumber, String addedBy) {
        // Verify group exists
        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));
        
        // For private groups, only group members can add members
        if (!group.isPublic() && !groupRepository.isUserMemberOfGroup(groupId, addedBy)) {
            throw  new UnauthorizedException("User requesting add to group not in group");
        }

        if ((userId == null && phoneNumber == null) || (userId != null && phoneNumber != null)) {
            throw new IllegalArgumentException("You must provide exactly one of: userId or phoneNumber");
        }

        if (phoneNumber != null) {
            userId = inviteService.findOrCreateUserByPhoneNumber(phoneNumber).getId().toString();
        } else if (userRepository.findById(UUID.fromString(userId)).isEmpty()) {
            throw new UserNotFoundException("Cannot add user to group, user not found: " + userId);
        }


        // Check if user is already a member
        if (groupRepository.isUserMemberOfGroup(groupId, userId)) {
            throw new ValidationException("User is already a member of this group");
        }
        
        // Create membership record
        GroupMembership membership = new GroupMembership(groupId, userId, group.getGroupName());
        membership.setRole(GroupRole.MEMBER); // Default role
        
        groupRepository.addMember(membership);
        logger.info("Added member {} to group {} by {}", userId, groupId, addedBy);
    }
    
    @Override
    public void removeMember(String groupId, String userId, String removedBy) {
        // Verify membership exists
        GroupMembership membership = groupRepository.findMembership(groupId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not in group"));
        
        // Users can remove themselves, or admins can remove others
        if (!userId.equals(removedBy)) {
            groupRepository.findMembership(groupId, removedBy)
                .orElseThrow(() -> new UnauthorizedException("User not in group"));
        }
        
        groupRepository.removeMember(groupId, userId);
        logger.info("Removed member {} from group {} by {}", userId, groupId, removedBy);
    }
    
    @Override
    public List<GroupMemberDTO> getGroupMembers(String groupId, String requestingUserId) {
        // Verify user is in group
        if (!isUserInGroup(requestingUserId, groupId)) {
            throw new UnauthorizedException("User not in group");
        }
        
        // Get all memberships for this group
        List<GroupMembership> memberships = groupRepository.findMembersByGroupId(groupId);
        
        // Convert to DTOs (need to get user names)
        return memberships.stream()
            .map(membership -> {
                // Get user details for display name
                User user = userRepository.findById(UUID.fromString(membership.getUserId()))
                    .orElse(null);
                String userName = user != null ? user.getDisplayName() : "Unknown User";
                
                return new GroupMemberDTO(membership, userName);
            })
            .collect(Collectors.toList());
    }
    
    @Override
    public List<GroupDTO> getUserGroups(String userId) {
        // Single GSI query gets everything we need - no N+1 queries!
        List<GroupMembership> memberships = groupRepository.findGroupsByUserId(userId);
        
        // No additional queries needed! groupName is denormalized on membership records
        return memberships.stream()
            .map(membership -> new GroupDTO(
                membership.getGroupId(),
                membership.getGroupName(), // Already available - no lookup needed!
                membership.getRole(),
                membership.getCreatedAt()
            ))
            .collect(Collectors.toList());
    }
    
    @Override
    public boolean isUserInGroup(String userId, String groupId) {
        return groupRepository.findMembership(groupId, userId).isPresent();
    }
    
    @Override
    public GroupFeedDTO getGroupFeed(String groupId, String requestingUserId) {
        // Authorization check
        if (!isUserInGroup(requestingUserId, groupId)) {
            throw new UnauthorizedException("User not in group");
        }
        
        // Single query gets all hangout pointers for group
        List<HangoutPointer> hangouts = groupRepository.findHangoutsByGroupId(groupId);
        
        // Separate by status in memory (fast)
        List<HangoutSummaryDTO> withDay = hangouts.stream()
            .filter(h -> h.getStartTimestamp() != null)
            .sorted(Comparator.comparing(HangoutPointer::getStartTimestamp))
            .map(HangoutSummaryDTO::new)
            .collect(Collectors.toList());
            
        List<HangoutSummaryDTO> needsDay = hangouts.stream()
            .filter(h -> h.getStartTimestamp() == null)
            .map(HangoutSummaryDTO::new)
            .collect(Collectors.toList());
            
        return new GroupFeedDTO(groupId, withDay, needsDay);
    }
    
    private void validateCreateGroupRequest(CreateGroupRequest request) {
        if (request.getGroupName() == null || request.getGroupName().trim().isEmpty()) {
            throw new ValidationException("Group name is required");
        }
        if (request.getGroupName().length() > 100) {
            throw new ValidationException("Group name must be 100 characters or less");
        }
        if (request.isPublic() == null) {
            throw new ValidationException("Public setting is required");
        }
    }
}