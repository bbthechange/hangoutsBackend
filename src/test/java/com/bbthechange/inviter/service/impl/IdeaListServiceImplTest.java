package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.exception.*;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.IdeaListRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for IdeaListServiceImpl focusing on authorization, business logic, and validation.
 * Tests the critical security boundaries and business rules.
 */
@ExtendWith(MockitoExtension.class)
class IdeaListServiceImplTest {

    @Mock
    private IdeaListRepository ideaListRepository;

    @Mock
    private GroupRepository groupRepository;

    @InjectMocks
    private IdeaListServiceImpl ideaListService;

    private String testGroupId;
    private String testUserId;
    private String testListId;
    private String testIdeaId;

    @BeforeEach
    void setUp() {
        testGroupId = UUID.randomUUID().toString();
        testUserId = UUID.randomUUID().toString();
        testListId = UUID.randomUUID().toString();
        testIdeaId = UUID.randomUUID().toString();
    }

    // ===== AUTHORIZATION & SECURITY TESTS =====

    @Test
    void getIdeaListsForGroup_UserNotGroupMember_ThrowsUnauthorizedException() {
        // Given: User is not a group member
        when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(false);

        // When/Then: UnauthorizedException thrown, no repository calls made
        assertThatThrownBy(() -> ideaListService.getIdeaListsForGroup(testGroupId, testUserId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("User is not a member of group");

        // Verify authorization check was made but no data access
        verify(groupRepository).isUserMemberOfGroup(testGroupId, testUserId);
        verifyNoInteractions(ideaListRepository);
    }

    @Test
    void createIdeaList_UserNotGroupMember_ThrowsUnauthorizedException() {
        // Given: User is not a group member
        when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(false);
        CreateIdeaListRequest request = new CreateIdeaListRequest();
        request.setName("Test List");
        request.setCategory(IdeaListCategory.RESTAURANT);

        // When/Then: Exception thrown before any data persistence
        assertThatThrownBy(() -> ideaListService.createIdeaList(testGroupId, request, testUserId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("User is not a member of group");

        verify(groupRepository).isUserMemberOfGroup(testGroupId, testUserId);
        verifyNoInteractions(ideaListRepository);
    }

    @Test
    void deleteIdea_UserNotGroupMember_ThrowsUnauthorizedException() {
        // Given: User is not a group member
        when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(false);

        // When/Then: Exception thrown, idea remains intact
        assertThatThrownBy(() -> ideaListService.deleteIdea(testGroupId, testListId, testIdeaId, testUserId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("User is not a member of group");

        verify(groupRepository).isUserMemberOfGroup(testGroupId, testUserId);
        verifyNoInteractions(ideaListRepository);
    }

    @Test
    void getIdeaList_UserNotGroupMember_ThrowsUnauthorizedException() {
        // Given: User is not a group member
        when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(false);

        // When/Then: Exception thrown
        assertThatThrownBy(() -> ideaListService.getIdeaList(testGroupId, testListId, testUserId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("User is not a member of group");

        verify(groupRepository).isUserMemberOfGroup(testGroupId, testUserId);
        verifyNoInteractions(ideaListRepository);
    }

    @Test
    void updateIdeaList_UserNotGroupMember_ThrowsUnauthorizedException() {
        // Given: User is not a group member
        when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(false);
        UpdateIdeaListRequest request = new UpdateIdeaListRequest();
        request.setName("Updated Name");

        // When/Then: Exception thrown
        assertThatThrownBy(() -> ideaListService.updateIdeaList(testGroupId, testListId, request, testUserId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("User is not a member of group");

        verify(groupRepository).isUserMemberOfGroup(testGroupId, testUserId);
        verifyNoInteractions(ideaListRepository);
    }

    @Test
    void deleteIdeaList_UserNotGroupMember_ThrowsUnauthorizedException() {
        // Given: User is not a group member
        when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(false);

        // When/Then: Exception thrown
        assertThatThrownBy(() -> ideaListService.deleteIdeaList(testGroupId, testListId, testUserId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("User is not a member of group");

        verify(groupRepository).isUserMemberOfGroup(testGroupId, testUserId);
        verifyNoInteractions(ideaListRepository);
    }

    @Test
    void addIdeaToList_UserNotGroupMember_ThrowsUnauthorizedException() {
        // Given: User is not a group member
        when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(false);
        CreateIdeaRequest request = new CreateIdeaRequest();
        request.setName("Test Idea");

        // When/Then: Exception thrown
        assertThatThrownBy(() -> ideaListService.addIdeaToList(testGroupId, testListId, request, testUserId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("User is not a member of group");

        verify(groupRepository).isUserMemberOfGroup(testGroupId, testUserId);
        verifyNoInteractions(ideaListRepository);
    }

    @Test
    void updateIdea_UserNotGroupMember_ThrowsUnauthorizedException() {
        // Given: User is not a group member
        when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(false);
        UpdateIdeaRequest request = new UpdateIdeaRequest();
        request.setName("Updated Idea");

        // When/Then: Exception thrown
        assertThatThrownBy(() -> ideaListService.updateIdea(testGroupId, testListId, testIdeaId, request, testUserId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("User is not a member of group");

        verify(groupRepository).isUserMemberOfGroup(testGroupId, testUserId);
        verifyNoInteractions(ideaListRepository);
    }

    // ===== BUSINESS LOGIC & VALIDATION TESTS =====

    @Test
    void createIdeaList_ValidRequest_CreatesListWithCorrectOwnership() {
        // Given: Valid request with name, category, note
        when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
        when(ideaListRepository.saveIdeaList(any(IdeaList.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateIdeaListRequest request = new CreateIdeaListRequest();
        request.setName("My Restaurant List");
        request.setCategory(IdeaListCategory.RESTAURANT);
        request.setNote("Places we want to try");

        // When: Create idea list
        IdeaListDTO result = ideaListService.createIdeaList(testGroupId, request, testUserId);

        // Then: Verify list creation sets correct metadata
        assertThat(result.getName()).isEqualTo("My Restaurant List");
        assertThat(result.getCategory()).isEqualTo(IdeaListCategory.RESTAURANT);
        assertThat(result.getNote()).isEqualTo("Places we want to try");
        assertThat(result.getCreatedBy()).isEqualTo(testUserId);
        assertThat(result.getId()).isNotNull(); // UUID generated for listId
        assertThat(result.getCreatedAt()).isNotNull(); // Created timestamp set
        assertThat(result.getIdeas()).isEmpty(); // Starts with no ideas

        verify(groupRepository).isUserMemberOfGroup(testGroupId, testUserId);
        verify(ideaListRepository).saveIdeaList(any(IdeaList.class));
    }

    @Test
    void updateIdeaList_PartialUpdate_OnlyUpdatesProvidedFields() {
        // Given: Existing idea list and update request with only name field
        when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
        
        IdeaList existingList = new IdeaList(testGroupId, "Original Name", IdeaListCategory.MOVIE, "Original Note", testUserId);
        existingList.setListId(testListId);
        when(ideaListRepository.findIdeaListById(testGroupId, testListId)).thenReturn(Optional.of(existingList));
        when(ideaListRepository.saveIdeaList(any(IdeaList.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateIdeaListRequest request = new UpdateIdeaListRequest();
        request.setName("Updated Name");
        // Note: category and note are null (not updated)

        // When: Update idea list
        IdeaListDTO result = ideaListService.updateIdeaList(testGroupId, testListId, request, testUserId);

        // Then: Only name updated, category/note unchanged, touch() called
        assertThat(result.getName()).isEqualTo("Updated Name");
        assertThat(result.getCategory()).isEqualTo(IdeaListCategory.MOVIE); // Unchanged
        assertThat(result.getNote()).isEqualTo("Original Note"); // Unchanged

        verify(ideaListRepository).saveIdeaList(existingList); // touch() updates timestamp
    }

    @Test
    void addIdeaToList_NonexistentList_ThrowsResourceNotFoundException() {
        // Given: User is member but list doesn't exist
        when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
        when(ideaListRepository.ideaListExists(testGroupId, testListId)).thenReturn(false);

        CreateIdeaRequest request = new CreateIdeaRequest();
        request.setName("Test Idea");

        // When/Then: Exception thrown, no idea created
        assertThatThrownBy(() -> ideaListService.addIdeaToList(testGroupId, testListId, request, testUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Idea list not found");

        verify(ideaListRepository).ideaListExists(testGroupId, testListId);
        verify(ideaListRepository, never()).saveIdeaListMember(any());
    }

    @Test
    void createIdeaRequest_AllFieldsEmpty_ThrowsValidationException() {
        // Given: Request with empty/null name, url, and note
        CreateIdeaRequest request = new CreateIdeaRequest();
        request.setName("");
        request.setUrl("");
        request.setNote(null);

        // When/Then: ValidationException with clear message (validation happens before authorization)
        assertThatThrownBy(() -> ideaListService.addIdeaToList(testGroupId, testListId, request, testUserId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("At least one field (name, url, or note) is required");

        // Verify no repository interactions since validation failed first
        verifyNoInteractions(groupRepository);
        verifyNoInteractions(ideaListRepository);
    }

    @Test
    void createIdeaList_EmptyName_ThrowsValidationException() {
        // Given: Request with empty name
        CreateIdeaListRequest request = new CreateIdeaListRequest();
        request.setName("   "); // Whitespace only
        request.setCategory(IdeaListCategory.RESTAURANT);

        // When/Then: ValidationException thrown (validation happens before authorization)
        assertThatThrownBy(() -> ideaListService.createIdeaList(testGroupId, request, testUserId))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Idea list name is required");

        // Verify no repository interactions since validation failed first
        verifyNoInteractions(groupRepository);
        verifyNoInteractions(ideaListRepository);
    }

    @Test
    void deleteIdeaList_NonexistentList_ThrowsResourceNotFoundException() {
        // Given: User is member but list doesn't exist
        when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
        when(ideaListRepository.ideaListExists(testGroupId, testListId)).thenReturn(false);

        // When/Then: ResourceNotFoundException thrown
        assertThatThrownBy(() -> ideaListService.deleteIdeaList(testGroupId, testListId, testUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Idea list not found");

        verify(ideaListRepository, never()).deleteIdeaListWithAllMembers(any(), any());
    }

    @Test
    void getIdeaList_NonexistentList_ThrowsResourceNotFoundException() {
        // Given: User is member but list doesn't exist
        when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
        when(ideaListRepository.findIdeaListWithMembersById(testGroupId, testListId)).thenReturn(Optional.empty());

        // When/Then: ResourceNotFoundException thrown
        assertThatThrownBy(() -> ideaListService.getIdeaList(testGroupId, testListId, testUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Idea list not found");
    }

    @Test
    void updateIdea_NonexistentIdea_ThrowsResourceNotFoundException() {
        // Given: User is member but idea doesn't exist
        when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
        when(ideaListRepository.findIdeaListMemberById(testGroupId, testListId, testIdeaId)).thenReturn(Optional.empty());

        UpdateIdeaRequest request = new UpdateIdeaRequest();
        request.setName("Updated Name");

        // When/Then: ResourceNotFoundException thrown
        assertThatThrownBy(() -> ideaListService.updateIdea(testGroupId, testListId, testIdeaId, request, testUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Idea not found");

        verify(ideaListRepository, never()).saveIdeaListMember(any());
    }

    @Test
    void deleteIdea_NonexistentIdea_ThrowsResourceNotFoundException() {
        // Given: User is member but idea doesn't exist
        when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
        when(ideaListRepository.findIdeaListMemberById(testGroupId, testListId, testIdeaId)).thenReturn(Optional.empty());

        // When/Then: ResourceNotFoundException thrown
        assertThatThrownBy(() -> ideaListService.deleteIdea(testGroupId, testListId, testIdeaId, testUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Idea not found");

        verify(ideaListRepository, never()).deleteIdeaListMember(any(), any(), any());
    }

    // ===== DATA CONSISTENCY TESTS =====

    @Test
    void getIdeaListsForGroup_UsesRepositoryData_NoAdditionalQueries() {
        // Given: User is group member and repository returns lists with pre-populated members
        when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
        
        IdeaList list1 = new IdeaList(testGroupId, "List 1", IdeaListCategory.RESTAURANT, "Note 1", testUserId);
        IdeaList list2 = new IdeaList(testGroupId, "List 2", IdeaListCategory.MOVIE, "Note 2", testUserId);
        
        // Pre-populate members (simulating repository's N+1 fix)
        IdeaListMember member1 = new IdeaListMember(testGroupId, list1.getListId(), "Restaurant 1", null, "Good food", testUserId);
        IdeaListMember member2 = new IdeaListMember(testGroupId, list1.getListId(), "Restaurant 2", null, "Great ambiance", testUserId);
        list1.getMembers().addAll(Arrays.asList(member1, member2));
        
        when(ideaListRepository.findAllIdeaListsWithMembersByGroupId(testGroupId))
                .thenReturn(Arrays.asList(list1, list2));

        // When: Get idea lists for group
        List<IdeaListDTO> result = ideaListService.getIdeaListsForGroup(testGroupId, testUserId);

        // Then: Service returns DTOs without calling findMembersByListId()
        assertThat(result).hasSize(2);
        
        // Verify repository method called exactly once
        verify(ideaListRepository, times(1)).findAllIdeaListsWithMembersByGroupId(testGroupId);
        verify(ideaListRepository, never()).findMembersByListId(any(), any()); // No additional queries
        
        // All members from repository data appear in DTOs
        IdeaListDTO resultList1 = result.stream().filter(dto -> dto.getName().equals("List 1")).findFirst().orElse(null);
        assertThat(resultList1).isNotNull();
        assertThat(resultList1.getIdeas()).hasSize(2);
        assertThat(resultList1.getIdeas().stream().map(IdeaDTO::getName))
                .containsExactlyInAnyOrder("Restaurant 1", "Restaurant 2");
    }

    @Test
    void updateIdea_PartialUpdate_OnlyUpdatesProvidedFields() {
        // Given: Existing idea and PATCH request updating only note field
        when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
        
        IdeaListMember existingMember = new IdeaListMember(testGroupId, testListId, "Original Name", "http://original.com", "Original Note", testUserId);
        existingMember.setIdeaId(testIdeaId);
        when(ideaListRepository.findIdeaListMemberById(testGroupId, testListId, testIdeaId)).thenReturn(Optional.of(existingMember));
        when(ideaListRepository.saveIdeaListMember(any(IdeaListMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateIdeaRequest request = new UpdateIdeaRequest();
        request.setNote("Updated Note");
        // Note: name and url are null (not updated)

        // When: Update idea with partial data
        IdeaDTO result = ideaListService.updateIdea(testGroupId, testListId, testIdeaId, request, testUserId);

        // Then: Only note updated, other fields unchanged
        assertThat(result.getNote()).isEqualTo("Updated Note");
        assertThat(result.getName()).isEqualTo("Original Name"); // Unchanged
        assertThat(result.getUrl()).isEqualTo("http://original.com"); // Unchanged

        verify(ideaListRepository).saveIdeaListMember(existingMember); // touch() updates timestamp
    }

    @Test
    void updateIdeaList_NoFieldsProvided_ReturnsUnchangedList() {
        // Given: Update request with all null fields
        when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
        
        IdeaList existingList = new IdeaList(testGroupId, "Original Name", IdeaListCategory.MOVIE, "Original Note", testUserId);
        existingList.setListId(testListId);
        when(ideaListRepository.findIdeaListById(testGroupId, testListId)).thenReturn(Optional.of(existingList));

        UpdateIdeaListRequest request = new UpdateIdeaListRequest();
        // All fields are null

        // When: Update with no changes
        IdeaListDTO result = ideaListService.updateIdeaList(testGroupId, testListId, request, testUserId);

        // Then: Original data returned, no save called
        assertThat(result.getName()).isEqualTo("Original Name");
        assertThat(result.getCategory()).isEqualTo(IdeaListCategory.MOVIE);
        assertThat(result.getNote()).isEqualTo("Original Note");
        
        verify(ideaListRepository, never()).saveIdeaList(any()); // No update needed
    }
}