package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.model.IdeaList;
import com.bbthechange.inviter.model.IdeaListMember;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository interface for idea list management operations in the InviterTable.
 * Provides efficient query patterns for idea lists and their members within groups.
 */
public interface IdeaListRepository {
    
    // Idea List CRUD operations
    IdeaList saveIdeaList(IdeaList ideaList);
    Optional<IdeaList> findIdeaListById(String groupId, String listId);
    void deleteIdeaList(String groupId, String listId);
    
    // Idea List Member CRUD operations  
    IdeaListMember saveIdeaListMember(IdeaListMember member);
    Optional<IdeaListMember> findIdeaListMemberById(String groupId, String listId, String ideaId);
    void deleteIdeaListMember(String groupId, String listId, String ideaId);
    
    /**
     * Get all idea lists for a group with their associated ideas.
     * Uses efficient single query with SK begins_with("IDEALIST#") pattern.
     * Returns fully populated idea lists with nested ideas.
     */
    List<IdeaList> findAllIdeaListsWithMembersByGroupId(String groupId);
    
    /**
     * Get a single idea list with all its members.
     * Uses efficient single query with SK begins_with("IDEALIST#{listId}") pattern.
     */
    Optional<IdeaList> findIdeaListWithMembersById(String groupId, String listId);
    
    /**
     * Delete entire idea list including all its members atomically.
     * Uses batch delete operation for consistency.
     */
    void deleteIdeaListWithAllMembers(String groupId, String listId);
    
    /**
     * Check if an idea list exists in the specified group.
     */
    boolean ideaListExists(String groupId, String listId);
    
    /**
     * Get all idea members for a specific list (without the list metadata).
     */
    List<IdeaListMember> findMembersByListId(String groupId, String listId);
}