package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.service.GroupService;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.UserRepository;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.exception.*;
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
    
    @Autowired
    public GroupServiceImpl(GroupRepository groupRepository, UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
    }
    
    @Override
    public GroupDTO createGroup(CreateGroupRequest request, String creatorId) {
        // Input validation
        validateCreateGroupRequest(request);
        
        // Verify creator exists
        User creator = userRepository.findById(UUID.fromString(creatorId))
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
        
        // TODO: In a full implementation, we'd need to clean up all related data:
        // - Remove all members
        // - Remove all hangout pointers  
        // - Handle cascading deletes
        // For now, just delete the group metadata
        
        groupRepository.delete(groupId);
        logger.info("Deleted group {} by user {}", groupId, requestingUserId);
    }
    
    @Override
    public void addMember(String groupId, String userId, String addedBy) {
        // Verify group exists
        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));
        
        // For private groups, only admins can add members
        if (!group.isPublic()) {
            GroupMembership adderMembership = groupRepository.findMembership(groupId, addedBy)
                .orElseThrow(() -> new UnauthorizedException("User not in group"));
            
            if (!adderMembership.isAdmin()) {
                throw new UnauthorizedException("Only admins can add members to private groups");
            }
        }
        
        // Verify user to add exists
        User userToAdd = userRepository.findById(UUID.fromString(userId))
            .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));
        
        // Check if user is already a member
        if (groupRepository.findMembership(groupId, userId).isPresent()) {
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
            GroupMembership removerMembership = groupRepository.findMembership(groupId, removedBy)
                .orElseThrow(() -> new UnauthorizedException("User not in group"));
            
            if (!removerMembership.isAdmin()) {
                throw new UnauthorizedException("Only admins can remove other members");
            }
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
            .filter(h -> h.getHangoutTime() != null)
            .sorted(Comparator.comparing(HangoutPointer::getHangoutTime))
            .map(HangoutSummaryDTO::new)
            .collect(Collectors.toList());
            
        List<HangoutSummaryDTO> needsDay = hangouts.stream()
            .filter(h -> h.getHangoutTime() == null)
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