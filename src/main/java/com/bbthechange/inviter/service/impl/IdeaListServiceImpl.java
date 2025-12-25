package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.service.IdeaListService;
import com.bbthechange.inviter.repository.IdeaListRepository;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of IdeaListService with authorization checks and efficient query patterns.
 */
@Service
@Transactional
public class IdeaListServiceImpl implements IdeaListService {
    
    private static final Logger logger = LoggerFactory.getLogger(IdeaListServiceImpl.class);
    
    private final IdeaListRepository ideaListRepository;
    private final GroupRepository groupRepository;
    
    @Autowired
    public IdeaListServiceImpl(IdeaListRepository ideaListRepository, GroupRepository groupRepository) {
        this.ideaListRepository = ideaListRepository;
        this.groupRepository = groupRepository;
    }
    
    @Override
    public List<IdeaListDTO> getIdeaListsForGroup(String groupId, String requestingUserId) {
        // Verify user is group member
        ensureUserIsGroupMember(groupId, requestingUserId);
        
        // Get all idea lists with their members
        List<IdeaList> ideaLists = ideaListRepository.findAllIdeaListsWithMembersByGroupId(groupId);
        
        // Get all members for all lists in one query (already done by repository)
        // Convert to DTOs
        return ideaLists.stream()
                .map(this::convertToDTO)
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
    }
    
    @Override
    public IdeaListDTO getIdeaList(String groupId, String listId, String requestingUserId) {
        // Verify user is group member
        ensureUserIsGroupMember(groupId, requestingUserId);
        
        // Get idea list with all its members
        IdeaList ideaList = ideaListRepository.findIdeaListWithMembersById(groupId, listId)
                .orElseThrow(() -> new ResourceNotFoundException("Idea list not found: " + listId));
        
        return convertToDTO(ideaList);
    }
    
    @Override
    public IdeaListDTO createIdeaList(String groupId, CreateIdeaListRequest request, String requestingUserId) {
        // Input validation
        validateCreateIdeaListRequest(request);
        
        // Verify user is group member
        ensureUserIsGroupMember(groupId, requestingUserId);
        
        // Create idea list (empty initially)
        IdeaList ideaList = new IdeaList(
                groupId,
                request.getName(),
                request.getCategory(),
                request.getNote(),
                requestingUserId
        );
        ideaList.setIsLocation(request.getIsLocation());

        IdeaList savedList = ideaListRepository.saveIdeaList(ideaList);
        logger.debug("Created idea list: {} in group: {} by user: {}", savedList.getListId(), groupId, requestingUserId);
        
        return new IdeaListDTO(savedList);
    }
    
    @Override
    public IdeaListDTO updateIdeaList(String groupId, String listId, UpdateIdeaListRequest request, String requestingUserId) {
        // Verify user is group member
        ensureUserIsGroupMember(groupId, requestingUserId);
        
        // Get existing idea list
        IdeaList existingList = ideaListRepository.findIdeaListById(groupId, listId)
                .orElseThrow(() -> new ResourceNotFoundException("Idea list not found: " + listId));
        
        // Update fields if provided
        boolean updated = false;
        if (request.getName() != null) {
            existingList.setName(request.getName());
            updated = true;
        }
        if (request.getCategory() != null) {
            existingList.setCategory(request.getCategory());
            updated = true;
        }
        if (request.getNote() != null) {
            existingList.setNote(request.getNote());
            updated = true;
        }
        if (request.getIsLocation() != null) {
            existingList.setIsLocation(request.getIsLocation());
            updated = true;
        }

        if (updated) {
            existingList.touch(); // Update timestamp
            IdeaList savedList = ideaListRepository.saveIdeaList(existingList);
            logger.debug("Updated idea list: {} in group: {} by user: {}", listId, groupId, requestingUserId);
            return convertToDTO(savedList);
        }
        
        return convertToDTO(existingList);
    }
    
    @Override
    public void deleteIdeaList(String groupId, String listId, String requestingUserId) {
        // Verify user is group member
        ensureUserIsGroupMember(groupId, requestingUserId);
        
        // Verify idea list exists
        if (!ideaListRepository.ideaListExists(groupId, listId)) {
            throw new ResourceNotFoundException("Idea list not found: " + listId);
        }
        
        // Delete idea list with all its members
        ideaListRepository.deleteIdeaListWithAllMembers(groupId, listId);
        logger.debug("Deleted idea list: {} from group: {} by user: {}", listId, groupId, requestingUserId);
    }
    
    @Override
    public IdeaDTO addIdeaToList(String groupId, String listId, CreateIdeaRequest request, String requestingUserId) {
        // Input validation
        validateCreateIdeaRequest(request);
        
        // Verify user is group member
        ensureUserIsGroupMember(groupId, requestingUserId);
        
        // Verify idea list exists
        if (!ideaListRepository.ideaListExists(groupId, listId)) {
            throw new ResourceNotFoundException("Idea list not found: " + listId);
        }
        
        // Create idea list member
        IdeaListMember member = new IdeaListMember(
                groupId,
                listId,
                request.getName(),
                request.getUrl(),
                request.getNote(),
                requestingUserId
        );
        
        IdeaListMember savedMember = ideaListRepository.saveIdeaListMember(member);
        logger.debug("Added idea: {} to list: {} in group: {} by user: {}", 
                savedMember.getIdeaId(), listId, groupId, requestingUserId);
        
        return new IdeaDTO(savedMember);
    }
    
    @Override
    public IdeaDTO updateIdea(String groupId, String listId, String ideaId, UpdateIdeaRequest request, String requestingUserId) {
        // Verify user is group member
        ensureUserIsGroupMember(groupId, requestingUserId);
        
        // Get existing idea
        IdeaListMember existingMember = ideaListRepository.findIdeaListMemberById(groupId, listId, ideaId)
                .orElseThrow(() -> new ResourceNotFoundException("Idea not found: " + ideaId));
        
        // Update fields if provided (PATCH semantics)
        boolean updated = false;
        if (request.getName() != null) {
            existingMember.setName(request.getName());
            updated = true;
        }
        if (request.getUrl() != null) {
            existingMember.setUrl(request.getUrl());
            updated = true;
        }
        if (request.getNote() != null) {
            existingMember.setNote(request.getNote());
            updated = true;
        }
        
        if (updated) {
            existingMember.touch(); // Update timestamp
            IdeaListMember savedMember = ideaListRepository.saveIdeaListMember(existingMember);
            logger.debug("Updated idea: {} in list: {} group: {} by user: {}", ideaId, listId, groupId, requestingUserId);
            return new IdeaDTO(savedMember);
        }
        
        return new IdeaDTO(existingMember);
    }
    
    @Override
    public void deleteIdea(String groupId, String listId, String ideaId, String requestingUserId) {
        // Verify user is group member
        ensureUserIsGroupMember(groupId, requestingUserId);
        
        // Verify idea exists
        if (!ideaListRepository.findIdeaListMemberById(groupId, listId, ideaId).isPresent()) {
            throw new ResourceNotFoundException("Idea not found: " + ideaId);
        }
        
        // Delete idea
        ideaListRepository.deleteIdeaListMember(groupId, listId, ideaId);
        logger.debug("Deleted idea: {} from list: {} group: {} by user: {}", ideaId, listId, groupId, requestingUserId);
    }
    
    // Private helper methods
    
    private void ensureUserIsGroupMember(String groupId, String userId) {
        if (!groupRepository.isUserMemberOfGroup(groupId, userId)) {
            throw new UnauthorizedException("User is not a member of group: " + groupId);
        }
    }
    
    private void validateCreateIdeaListRequest(CreateIdeaListRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new ValidationException("Idea list name is required");
        }
    }
    
    private void validateCreateIdeaRequest(CreateIdeaRequest request) {
        // At least one field must be provided
        if ((request.getName() == null || request.getName().trim().isEmpty()) &&
            (request.getUrl() == null || request.getUrl().trim().isEmpty()) &&
            (request.getNote() == null || request.getNote().trim().isEmpty())) {
            throw new ValidationException("At least one field (name, url, or note) is required for an idea");
        }
    }
    
    private IdeaListDTO convertToDTO(IdeaList ideaList) {
        IdeaListDTO dto = new IdeaListDTO(ideaList);
        
        // Convert members that are already attached to the idea list (no additional DB call needed!)
        List<IdeaDTO> ideaDTOs = ideaList.getMembers().stream()
                .map(IdeaDTO::new)
                .collect(Collectors.toList()); // Already sorted in repository
        
        dto.setIdeas(ideaDTOs);
        return dto;
    }
}