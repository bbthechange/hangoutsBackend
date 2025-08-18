package com.bbthechange.inviter.service;

import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.model.IdeaListCategory;

import java.util.List;

/**
 * Service interface for idea list management operations.
 * Provides business logic for idea lists and their members within groups.
 */
public interface IdeaListService {
    
    /**
     * Get all idea lists for a group with their members.
     * Only group members can access idea lists.
     */
    List<IdeaListDTO> getIdeaListsForGroup(String groupId, String requestingUserId);
    
    /**
     * Get a single idea list with all its members.
     * Only group members can access idea lists.
     */
    IdeaListDTO getIdeaList(String groupId, String listId, String requestingUserId);
    
    /**
     * Create a new idea list (empty initially).
     * Only group members can create idea lists.
     */
    IdeaListDTO createIdeaList(String groupId, CreateIdeaListRequest request, String requestingUserId);
    
    /**
     * Update an existing idea list (name, category, note).
     * Only group members can update idea lists.
     */
    IdeaListDTO updateIdeaList(String groupId, String listId, UpdateIdeaListRequest request, String requestingUserId);
    
    /**
     * Delete an idea list and all its members.
     * Only group members can delete idea lists.
     */
    void deleteIdeaList(String groupId, String listId, String requestingUserId);
    
    /**
     * Add a new idea to an idea list.
     * Only group members can add ideas.
     */
    IdeaDTO addIdeaToList(String groupId, String listId, CreateIdeaRequest request, String requestingUserId);
    
    /**
     * Update an existing idea (partial update with PATCH semantics).
     * Only group members can update ideas.
     */
    IdeaDTO updateIdea(String groupId, String listId, String ideaId, UpdateIdeaRequest request, String requestingUserId);
    
    /**
     * Delete an idea from a list.
     * Only group members can delete ideas.
     */
    void deleteIdea(String groupId, String listId, String ideaId, String requestingUserId);
}