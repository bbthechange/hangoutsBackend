package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.exception.*;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.IdeaListRepository;
import com.bbthechange.inviter.service.PlaceEnrichmentService;
import com.bbthechange.inviter.service.S3Service;
import com.bbthechange.inviter.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

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

    @Mock
    private UserService userService;

    @Mock
    private PlaceEnrichmentService placeEnrichmentService;

    @Mock
    private S3Service s3Service;

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
        ideaListService = new IdeaListServiceImpl(ideaListRepository, groupRepository, userService, s3Service, placeEnrichmentService);
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

    // ===== IDEA INTEREST TESTS =====

    @Test
    void addIdeaInterest_ValidRequest_ReturnsIdeaDTOWithInterestedUser() {
        // Given: User is group member, idea exists with interest added
        when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);

        IdeaListMember member = new IdeaListMember(testGroupId, testListId, "Fun idea", null, null, "creator-id");
        member.setIdeaId(testIdeaId);
        member.setInterestedUserIds(new HashSet<>(Set.of(testUserId)));
        when(ideaListRepository.findIdeaListMemberById(testGroupId, testListId, testIdeaId))
                .thenReturn(Optional.of(member));

        UserSummaryDTO userSummary = new UserSummaryDTO(UUID.fromString(testUserId), "TestUser", "users/profile.jpg");
        when(userService.getUserSummary(UUID.fromString(testUserId))).thenReturn(Optional.of(userSummary));

        // When: Add interest
        IdeaDTO result = ideaListService.addIdeaInterest(testGroupId, testListId, testIdeaId, testUserId);

        // Then: Verify interest data populated
        verify(ideaListRepository).addIdeaInterest(testGroupId, testListId, testIdeaId, testUserId);
        assertThat(result.getInterestedUsers()).hasSize(1);
        assertThat(result.getInterestedUsers().get(0).getUserId()).isEqualTo(testUserId);
        assertThat(result.getInterestedUsers().get(0).getDisplayName()).isEqualTo("TestUser");
        assertThat(result.getInterestedUsers().get(0).getProfileImagePath()).isEqualTo("users/profile.jpg");
        assertThat(result.getInterestCount()).isEqualTo(2); // 1 explicit + 1 implicit creator
    }

    @Test
    void addIdeaInterest_UserNotGroupMember_ThrowsUnauthorizedException() {
        // Given: User is not a group member
        when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(false);

        // When/Then: Exception thrown, no repository calls made
        assertThatThrownBy(() -> ideaListService.addIdeaInterest(testGroupId, testListId, testIdeaId, testUserId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("User is not a member of group");

        verify(groupRepository).isUserMemberOfGroup(testGroupId, testUserId);
        verifyNoInteractions(ideaListRepository);
    }

    @Test
    void removeIdeaInterest_ValidRequest_ReturnsIdeaDTOWithoutUser() {
        // Given: User is group member, idea exists with no interest after removal
        when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);

        IdeaListMember member = new IdeaListMember(testGroupId, testListId, "Fun idea", null, null, "creator-id");
        member.setIdeaId(testIdeaId);
        // After removal, interestedUserIds is null (empty set)
        when(ideaListRepository.findIdeaListMemberById(testGroupId, testListId, testIdeaId))
                .thenReturn(Optional.of(member));

        // When: Remove interest
        IdeaDTO result = ideaListService.removeIdeaInterest(testGroupId, testListId, testIdeaId, testUserId);

        // Then: Verify interest removed
        verify(ideaListRepository).removeIdeaInterest(testGroupId, testListId, testIdeaId, testUserId);
        assertThat(result.getInterestedUsers()).isEmpty();
        assertThat(result.getInterestCount()).isEqualTo(1); // Only implicit creator
    }

    @Test
    void removeIdeaInterest_UserNotGroupMember_ThrowsUnauthorizedException() {
        // Given: User is not a group member
        when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(false);

        // When/Then: Exception thrown
        assertThatThrownBy(() -> ideaListService.removeIdeaInterest(testGroupId, testListId, testIdeaId, testUserId))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("User is not a member of group");

        verify(groupRepository).isUserMemberOfGroup(testGroupId, testUserId);
        verifyNoInteractions(ideaListRepository);
    }

    @Test
    void addIdeaInterest_IdeaNotFound_ThrowsResourceNotFoundException() {
        // Given: User is group member but idea doesn't exist after add (repo throws)
        when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
        doThrow(new ResourceNotFoundException("Idea not found: " + testIdeaId))
                .when(ideaListRepository).addIdeaInterest(testGroupId, testListId, testIdeaId, testUserId);

        // When/Then: ResourceNotFoundException propagated
        assertThatThrownBy(() -> ideaListService.addIdeaInterest(testGroupId, testListId, testIdeaId, testUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Idea not found");
    }

    @Test
    void convertToDTO_WithInterestData_SortsByInterestCountDesc() {
        // Given: User is group member, list with ideas having different interest levels
        when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);

        IdeaList ideaList = new IdeaList(testGroupId, "Test List", IdeaListCategory.ACTIVITY, null, testUserId);
        ideaList.setListId(testListId);

        String user1 = UUID.randomUUID().toString();
        String user2 = UUID.randomUUID().toString();

        // Idea with 0 explicit interest (added first chronologically)
        IdeaListMember idea1 = new IdeaListMember(testGroupId, testListId, "Boring idea", null, null, testUserId);
        // Idea with 2 explicit interests (added second)
        IdeaListMember idea2 = new IdeaListMember(testGroupId, testListId, "Popular idea", null, null, testUserId);
        idea2.setInterestedUserIds(new HashSet<>(Set.of(user1, user2)));

        ideaList.getMembers().addAll(Arrays.asList(idea1, idea2));
        when(ideaListRepository.findIdeaListWithMembersById(testGroupId, testListId))
                .thenReturn(Optional.of(ideaList));

        UserSummaryDTO summary1 = new UserSummaryDTO(UUID.fromString(user1), "User1", null);
        UserSummaryDTO summary2 = new UserSummaryDTO(UUID.fromString(user2), "User2", null);
        when(userService.getUserSummary(UUID.fromString(user1))).thenReturn(Optional.of(summary1));
        when(userService.getUserSummary(UUID.fromString(user2))).thenReturn(Optional.of(summary2));

        // When: Get idea list
        IdeaListDTO result = ideaListService.getIdeaList(testGroupId, testListId, testUserId);

        // Then: Popular idea (interestCount=3) sorted before boring idea (interestCount=1)
        assertThat(result.getIdeas()).hasSize(2);
        assertThat(result.getIdeas().get(0).getName()).isEqualTo("Popular idea");
        assertThat(result.getIdeas().get(0).getInterestCount()).isEqualTo(3); // 2 explicit + 1 creator
        assertThat(result.getIdeas().get(0).getInterestedUsers()).hasSize(2);
        assertThat(result.getIdeas().get(1).getName()).isEqualTo("Boring idea");
        assertThat(result.getIdeas().get(1).getInterestCount()).isEqualTo(1); // 0 explicit + 1 creator
        assertThat(result.getIdeas().get(1).getInterestedUsers()).isEmpty();
    }

    @Test
    void removeIdeaInterest_IdeaNotFoundInRepo_ThrowsResourceNotFoundException() {
        // Given: User is group member, but repo throws on remove because idea doesn't exist
        when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
        doThrow(new ResourceNotFoundException("Idea not found: " + testIdeaId))
                .when(ideaListRepository).removeIdeaInterest(testGroupId, testListId, testIdeaId, testUserId);

        // When/Then: ResourceNotFoundException propagated from repository
        assertThatThrownBy(() -> ideaListService.removeIdeaInterest(testGroupId, testListId, testIdeaId, testUserId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Idea not found");
    }

    @Test
    void addIdeaInterest_MultipleUsers_ReturnsAllInterestedUsers() {
        // Given: User is group member, idea has 3 interested users
        when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);

        String user1 = UUID.randomUUID().toString();
        String user2 = UUID.randomUUID().toString();
        String user3 = UUID.randomUUID().toString();

        IdeaListMember member = new IdeaListMember(testGroupId, testListId, "Popular idea", null, null, "creator-id");
        member.setIdeaId(testIdeaId);
        member.setInterestedUserIds(new HashSet<>(Set.of(user1, user2, user3)));
        when(ideaListRepository.findIdeaListMemberById(testGroupId, testListId, testIdeaId))
                .thenReturn(Optional.of(member));

        UserSummaryDTO summary1 = new UserSummaryDTO(UUID.fromString(user1), "Alice", "users/alice.jpg");
        UserSummaryDTO summary2 = new UserSummaryDTO(UUID.fromString(user2), "Bob", "users/bob.jpg");
        UserSummaryDTO summary3 = new UserSummaryDTO(UUID.fromString(user3), "Charlie", "users/charlie.jpg");
        when(userService.getUserSummary(UUID.fromString(user1))).thenReturn(Optional.of(summary1));
        when(userService.getUserSummary(UUID.fromString(user2))).thenReturn(Optional.of(summary2));
        when(userService.getUserSummary(UUID.fromString(user3))).thenReturn(Optional.of(summary3));

        // When: Add interest
        IdeaDTO result = ideaListService.addIdeaInterest(testGroupId, testListId, testIdeaId, testUserId);

        // Then: All 3 interested users resolved, interestCount = 3 explicit + 1 creator = 4
        assertThat(result.getInterestedUsers()).hasSize(3);
        assertThat(result.getInterestCount()).isEqualTo(4);
    }

    @Test
    void convertToDTO_SameInterestCount_SortsByAddedTimeDesc() {
        // Given: User is group member, list with 2 ideas both having same interestCount (1)
        when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);

        IdeaList ideaList = new IdeaList(testGroupId, "Test List", IdeaListCategory.ACTIVITY, null, testUserId);
        ideaList.setListId(testListId);

        // Idea1 added 1 hour ago
        IdeaListMember idea1 = new IdeaListMember(testGroupId, testListId, "Older idea", null, null, testUserId);
        // Override addedTime to 1 hour ago using reflection-free approach: create then set
        idea1.setAddedTime(Instant.now().minusSeconds(3600));

        // Idea2 added now
        IdeaListMember idea2 = new IdeaListMember(testGroupId, testListId, "Newer idea", null, null, testUserId);
        idea2.setAddedTime(Instant.now());

        // Both have null interestedUserIds, so interestCount = 1 for both
        ideaList.getMembers().addAll(Arrays.asList(idea1, idea2));
        when(ideaListRepository.findIdeaListWithMembersById(testGroupId, testListId))
                .thenReturn(Optional.of(ideaList));

        // When: Get idea list
        IdeaListDTO result = ideaListService.getIdeaList(testGroupId, testListId, testUserId);

        // Then: Newer idea (idea2) appears first due to addedTime desc secondary sort
        assertThat(result.getIdeas()).hasSize(2);
        assertThat(result.getIdeas().get(0).getName()).isEqualTo("Newer idea");
        assertThat(result.getIdeas().get(1).getName()).isEqualTo("Older idea");
    }

    @Test
    void resolveInterestedUsers_DeletedUser_SkippedGracefully() {
        // Given: User is group member, idea has interest from a deleted user
        when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);

        IdeaListMember member = new IdeaListMember(testGroupId, testListId, "Idea", null, null, "creator-id");
        member.setIdeaId(testIdeaId);
        String deletedUserId = UUID.randomUUID().toString();
        member.setInterestedUserIds(new HashSet<>(Set.of(deletedUserId)));
        when(ideaListRepository.findIdeaListMemberById(testGroupId, testListId, testIdeaId))
                .thenReturn(Optional.of(member));

        // Deleted user returns empty
        when(userService.getUserSummary(UUID.fromString(deletedUserId))).thenReturn(Optional.empty());

        // When: Add interest (re-fetch shows deleted user interest)
        IdeaDTO result = ideaListService.addIdeaInterest(testGroupId, testListId, testIdeaId, testUserId);

        // Then: Deleted user skipped, empty interested users
        assertThat(result.getInterestedUsers()).isEmpty();
        assertThat(result.getInterestCount()).isEqualTo(1); // 0 resolved + 1 creator
    }

    // ===== PLACE ENRICHMENT TESTS =====

    @Nested
    class PlaceEnrichmentTests {

        @Test
        void addIdeaToList_CacheMiss_SetsEnrichmentPendingAndTriggersEnrichment() {
            // Given: User is group member, list exists, request has googlePlaceId, cache miss
            when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
            when(ideaListRepository.ideaListExists(testGroupId, testListId)).thenReturn(true);
            when(ideaListRepository.saveIdeaListMember(any(IdeaListMember.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(placeEnrichmentService.isEnabled()).thenReturn(true);
            when(placeEnrichmentService.lookupCache(any(), any(), any(), any()))
                    .thenReturn(Optional.empty());

            CreateIdeaRequest request = new CreateIdeaRequest();
            request.setName("Joe's Pizza");
            request.setGooglePlaceId("ChIJN1t_tDeuEmsRUsoyG83frY4");
            request.setAddress("123 Main St");

            // When: Add idea with Google Place ID
            IdeaDTO result = ideaListService.addIdeaToList(testGroupId, testListId, request, testUserId);

            // Then: returned DTO has PENDING status and place data
            assertThat(result.getEnrichmentStatus()).isEqualTo("PENDING");
            assertThat(result.getGooglePlaceId()).isEqualTo("ChIJN1t_tDeuEmsRUsoyG83frY4");

            // And: lookupCache was called, async enrichment was triggered
            verify(placeEnrichmentService).lookupCache(any(), any(), any(), any());
            verify(placeEnrichmentService).enrichPlaceAsync(
                    eq(testGroupId), eq(testListId), any(String.class),
                    any(), any(), any(), eq("ChIJN1t_tDeuEmsRUsoyG83frY4"), any());
        }

        @Test
        void addIdeaToList_CacheHit_ReturnsEnrichedIdeaImmediately() {
            // Given: Cache has enriched data for this place
            when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
            when(ideaListRepository.ideaListExists(testGroupId, testListId)).thenReturn(true);
            when(ideaListRepository.saveIdeaListMember(any(IdeaListMember.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(placeEnrichmentService.isEnabled()).thenReturn(true);

            PlaceEnrichmentCacheEntry cachedEntry = new PlaceEnrichmentCacheEntry();
            cachedEntry.setStatus("ENRICHED");
            cachedEntry.setCachedRating(4.6);
            cachedEntry.setCachedPriceLevel(4);
            cachedEntry.setCachedPhotoUrl("places/sushi-nakazawa_40.7295_-74.0028/photo.jpg");
            cachedEntry.setPhoneNumber("+12125240500");
            cachedEntry.setWebsiteUrl("https://sushinakazawa.com");
            cachedEntry.setCachedHoursJson("[\"Monday: 5:00 – 10:00 PM\"]");
            cachedEntry.setGooglePlaceId("ChIJN1t_tDeuEmsRUsoyG83frY4");
            when(placeEnrichmentService.lookupCache(any(), any(), any(), any()))
                    .thenReturn(Optional.of(cachedEntry));

            CreateIdeaRequest request = new CreateIdeaRequest();
            request.setName("Sushi Nakazawa");
            request.setLatitude(40.7295);
            request.setLongitude(-74.0028);
            request.setApplePlaceId("apple-123");

            // When: Add idea — cache hit
            IdeaDTO result = ideaListService.addIdeaToList(testGroupId, testListId, request, testUserId);

            // Then: returned DTO has ENRICHED status with cached data
            assertThat(result.getEnrichmentStatus()).isEqualTo("ENRICHED");
            assertThat(result.getCachedRating()).isEqualTo(4.6);
            assertThat(result.getCachedPriceLevel()).isEqualTo(4);
            assertThat(result.getCachedPhotoUrl()).isEqualTo("places/sushi-nakazawa_40.7295_-74.0028/photo.jpg");
            assertThat(result.getPhoneNumber()).isEqualTo("+12125240500");
            assertThat(result.getWebsiteUrl()).isEqualTo("https://sushinakazawa.com");
            assertThat(result.getGooglePlaceId()).isEqualTo("ChIJN1t_tDeuEmsRUsoyG83frY4");

            // And: NO async enrichment triggered (cache hit)
            verify(placeEnrichmentService, never()).enrichPlaceAsync(
                    any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void addIdeaToList_WithoutGooglePlaceId_SetsPendingStatus() {
            // Given: User is group member, list exists, request has address but no googlePlaceId
            when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
            when(ideaListRepository.ideaListExists(testGroupId, testListId)).thenReturn(true);
            when(ideaListRepository.saveIdeaListMember(any(IdeaListMember.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            CreateIdeaRequest request = new CreateIdeaRequest();
            request.setName("Custom Place");
            request.setAddress("456 Elm St");

            // When: Add place idea without Google Place ID
            IdeaDTO result = ideaListService.addIdeaToList(testGroupId, testListId, request, testUserId);

            // Then: returned DTO has PENDING status (place idea), async enrichment IS triggered (place idea with address)
            assertThat(result.getEnrichmentStatus()).isEqualTo("PENDING");
        }

        @Test
        void addIdeaToList_WithPlaceFields_SavesAllPlaceFields() {
            // Given: User is group member, list exists, request has all place fields, cache miss
            when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
            when(ideaListRepository.ideaListExists(testGroupId, testListId)).thenReturn(true);
            when(ideaListRepository.saveIdeaListMember(any(IdeaListMember.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(placeEnrichmentService.isEnabled()).thenReturn(true);
            when(placeEnrichmentService.lookupCache(any(), any(), any(), any()))
                    .thenReturn(Optional.empty());

            CreateIdeaRequest request = new CreateIdeaRequest();
            request.setName("Joe's Pizza");
            request.setGooglePlaceId("ChIJN1t_tDeuEmsRUsoyG83frY4");
            request.setApplePlaceId("apple-place-123");
            request.setAddress("123 Main St, New York, NY");
            request.setLatitude(40.7128);
            request.setLongitude(-74.0060);
            request.setPlaceCategory("restaurant");

            // When: Add idea with all place fields
            IdeaDTO result = ideaListService.addIdeaToList(testGroupId, testListId, request, testUserId);

            // Then: returned DTO has all place fields
            assertThat(result.getGooglePlaceId()).isEqualTo("ChIJN1t_tDeuEmsRUsoyG83frY4");
            assertThat(result.getApplePlaceId()).isEqualTo("apple-place-123");
            assertThat(result.getAddress()).isEqualTo("123 Main St, New York, NY");
            assertThat(result.getLatitude()).isEqualTo(40.7128);
            assertThat(result.getLongitude()).isEqualTo(-74.0060);
            assertThat(result.getPlaceCategory()).isEqualTo("restaurant");
            assertThat(result.getEnrichmentStatus()).isEqualTo("PENDING");
        }

        @Test
        void addIdeaToList_PlaceCategoryOnly_IsPlaceIdea() {
            // Given: request has only placeCategory (no coords, no googlePlaceId, no address)
            when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
            when(ideaListRepository.ideaListExists(testGroupId, testListId)).thenReturn(true);
            when(ideaListRepository.saveIdeaListMember(any(IdeaListMember.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            CreateIdeaRequest request = new CreateIdeaRequest();
            request.setName("Neighborhood Park");
            request.setPlaceCategory("park");

            // When: Add idea with just placeCategory
            IdeaDTO result = ideaListService.addIdeaToList(testGroupId, testListId, request, testUserId);

            // Then: treated as a place idea (PENDING status)
            assertThat(result.getEnrichmentStatus()).isEqualTo("PENDING");
            assertThat(result.getPlaceCategory()).isEqualTo("park");
        }

        @Test
        void updateIdea_WithPlaceFields_UpdatesPlaceFields() {
            // Given: Existing idea, PATCH request with place fields
            when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);

            IdeaListMember existingMember = new IdeaListMember(testGroupId, testListId, "Old Restaurant", null, null, testUserId);
            existingMember.setIdeaId(testIdeaId);
            existingMember.setAddress("Old Address");
            existingMember.setEnrichmentStatus(null);
            when(ideaListRepository.findIdeaListMemberById(testGroupId, testListId, testIdeaId))
                    .thenReturn(Optional.of(existingMember));
            when(ideaListRepository.saveIdeaListMember(any(IdeaListMember.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            UpdateIdeaRequest request = new UpdateIdeaRequest();
            request.setAddress("New Address");
            request.setLatitude(34.0522);
            request.setLongitude(-118.2437);
            request.setPlaceCategory("bar");

            // When: Update idea with place fields
            IdeaDTO result = ideaListService.updateIdea(testGroupId, testListId, testIdeaId, request, testUserId);

            // Then: Place fields updated, original name unchanged
            assertThat(result.getName()).isEqualTo("Old Restaurant");
            assertThat(result.getAddress()).isEqualTo("New Address");
            assertThat(result.getLatitude()).isEqualTo(34.0522);
            assertThat(result.getLongitude()).isEqualTo(-118.2437);
            assertThat(result.getPlaceCategory()).isEqualTo("bar");
        }

        @Test
        void updateIdea_WithNewGooglePlaceId_ResetsEnrichmentAndTriggersReEnrichment() {
            // Given: Existing idea with old googlePlaceId, update with new one
            when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
            when(placeEnrichmentService.isEnabled()).thenReturn(true);

            IdeaListMember existingMember = new IdeaListMember(testGroupId, testListId, "Restaurant", null, null, testUserId);
            existingMember.setIdeaId(testIdeaId);
            existingMember.setGooglePlaceId("old-place-id");
            existingMember.setEnrichmentStatus("ENRICHED");
            existingMember.setLastEnrichedAt(Instant.now().minusSeconds(86400));
            when(ideaListRepository.findIdeaListMemberById(testGroupId, testListId, testIdeaId))
                    .thenReturn(Optional.of(existingMember));
            when(ideaListRepository.saveIdeaListMember(any(IdeaListMember.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            UpdateIdeaRequest request = new UpdateIdeaRequest();
            request.setGooglePlaceId("new-place-id");

            // When: Update with new Google Place ID
            IdeaDTO result = ideaListService.updateIdea(testGroupId, testListId, testIdeaId, request, testUserId);

            // Then: returned DTO shows enrichment reset to PENDING with cleared timestamp
            assertThat(result.getEnrichmentStatus()).isEqualTo("PENDING");
            assertThat(result.getLastEnrichedAt()).isNull();
            assertThat(result.getGooglePlaceId()).isEqualTo("new-place-id");

            // And: async enrichment was triggered for the new place
            verify(placeEnrichmentService).enrichPlaceAsync(
                    eq(testGroupId), eq(testListId), eq(testIdeaId),
                    any(), any(), any(), eq("new-place-id"), any());
        }

        @Test
        void updateIdea_WithSameGooglePlaceId_DoesNotTriggerReEnrichment() {
            // Given: Existing idea, update with same googlePlaceId
            when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);

            IdeaListMember existingMember = new IdeaListMember(testGroupId, testListId, "Restaurant", null, null, testUserId);
            existingMember.setIdeaId(testIdeaId);
            existingMember.setGooglePlaceId("same-place-id");
            existingMember.setEnrichmentStatus("ENRICHED");
            when(ideaListRepository.findIdeaListMemberById(testGroupId, testListId, testIdeaId))
                    .thenReturn(Optional.of(existingMember));
            when(ideaListRepository.saveIdeaListMember(any(IdeaListMember.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            UpdateIdeaRequest request = new UpdateIdeaRequest();
            request.setGooglePlaceId("same-place-id");
            request.setName("Updated Name");

            // When: Update with same Google Place ID
            IdeaDTO result = ideaListService.updateIdea(testGroupId, testListId, testIdeaId, request, testUserId);

            // Then: returned DTO retains ENRICHED status, no re-enrichment triggered
            assertThat(result.getEnrichmentStatus()).isEqualTo("ENRICHED");
            assertThat(result.getName()).isEqualTo("Updated Name");
            verify(placeEnrichmentService, never()).enrichPlaceAsync(
                    any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void getIdeaList_TriggersStaleReEnrichment() {
            // Given: User is group member, list exists with members, enrichment enabled
            when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
            when(placeEnrichmentService.isEnabled()).thenReturn(true);

            IdeaList ideaList = new IdeaList(testGroupId, "Places List", IdeaListCategory.RESTAURANT, null, testUserId);
            ideaList.setListId(testListId);
            IdeaListMember member = new IdeaListMember(testGroupId, testListId, "Old Place", null, null, testUserId);
            ideaList.getMembers().add(member);

            when(ideaListRepository.findIdeaListWithMembersById(testGroupId, testListId))
                    .thenReturn(Optional.of(ideaList));

            // When: Get idea list
            ideaListService.getIdeaList(testGroupId, testListId, testUserId);

            // Then: triggerReadPathEnrichment called with the list members
            verify(placeEnrichmentService).triggerReadPathEnrichment(
                    eq(ideaList.getMembers()), eq(testGroupId), eq(testListId));
        }

        @Test
        void addIdeaToList_WithoutGooglePlaceId_CustomPlace_NoEnrichmentTriggered() {
            // Given: User is group member, list exists, custom place (no Google/Apple IDs)
            when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
            when(ideaListRepository.ideaListExists(testGroupId, testListId)).thenReturn(true);
            when(ideaListRepository.saveIdeaListMember(any(IdeaListMember.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            CreateIdeaRequest request = new CreateIdeaRequest();
            request.setName("My Home");
            request.setAddress("789 Custom St");
            request.setLatitude(37.7749);
            request.setLongitude(-122.4194);
            request.setPlaceCategory("home");

            // When: Add custom place (no googlePlaceId)
            IdeaDTO result = ideaListService.addIdeaToList(testGroupId, testListId, request, testUserId);

            // Then: PENDING status (place idea with coords), enrichment triggered (isPlaceIdea=true)
            assertThat(result.getEnrichmentStatus()).isEqualTo("PENDING");
            assertThat(result.getAddress()).isEqualTo("789 Custom St");
            assertThat(result.getPlaceCategory()).isEqualTo("home");
        }

        @Test
        void addIdeaToList_NonPlaceIdea_BackwardCompatible() {
            // Given: Traditional idea without any place fields (backward compat)
            when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
            when(ideaListRepository.ideaListExists(testGroupId, testListId)).thenReturn(true);
            when(ideaListRepository.saveIdeaListMember(any(IdeaListMember.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            CreateIdeaRequest request = new CreateIdeaRequest();
            request.setName("Watch The Matrix");
            request.setNote("Classic sci-fi movie");

            // When: Add non-place idea
            IdeaDTO result = ideaListService.addIdeaToList(testGroupId, testListId, request, testUserId);

            // Then: Place fields are null, enrichmentStatus is null (non-place idea)
            assertThat(result.getGooglePlaceId()).isNull();
            assertThat(result.getApplePlaceId()).isNull();
            assertThat(result.getAddress()).isNull();
            assertThat(result.getLatitude()).isNull();
            assertThat(result.getLongitude()).isNull();
            assertThat(result.getPlaceCategory()).isNull();
            assertThat(result.getEnrichmentStatus()).isNull();
            verify(placeEnrichmentService, never()).enrichPlaceAsync(
                    any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void addIdeaToList_EnrichmentServiceDisabled_NoEnrichmentTriggered() {
            // Given: Enrichment service is disabled
            when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
            when(ideaListRepository.ideaListExists(testGroupId, testListId)).thenReturn(true);
            when(ideaListRepository.saveIdeaListMember(any(IdeaListMember.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(placeEnrichmentService.isEnabled()).thenReturn(false);

            CreateIdeaRequest request = new CreateIdeaRequest();
            request.setName("Joe's Pizza");
            request.setGooglePlaceId("ChIJN1t_tDeuEmsRUsoyG83frY4");

            // When: Add idea with Google Place ID but enrichment disabled
            IdeaDTO result = ideaListService.addIdeaToList(testGroupId, testListId, request, testUserId);

            // Then: returned DTO has PENDING status but enrichPlaceAsync NOT called
            assertThat(result.getEnrichmentStatus()).isEqualTo("PENDING");
            verify(placeEnrichmentService, never()).enrichPlaceAsync(
                    any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void addIdeaToList_NullEnrichmentService_DoesNotThrow() {
            // Given: PlaceEnrichmentService is null (optional dependency)
            IdeaListServiceImpl serviceWithoutEnrichment = new IdeaListServiceImpl(
                    ideaListRepository, groupRepository, userService, s3Service, null);

            when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
            when(ideaListRepository.ideaListExists(testGroupId, testListId)).thenReturn(true);
            when(ideaListRepository.saveIdeaListMember(any(IdeaListMember.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            CreateIdeaRequest request = new CreateIdeaRequest();
            request.setName("Joe's Pizza");
            request.setGooglePlaceId("ChIJN1t_tDeuEmsRUsoyG83frY4");

            // When/Then: No exception thrown even with null enrichment service
            assertThatCode(() -> serviceWithoutEnrichment.addIdeaToList(
                    testGroupId, testListId, request, testUserId))
                    .doesNotThrowAnyException();
        }

        @Test
        void getIdeaList_NullEnrichmentService_DoesNotThrow() {
            // Given: PlaceEnrichmentService is null (optional dependency)
            IdeaListServiceImpl serviceWithoutEnrichment = new IdeaListServiceImpl(
                    ideaListRepository, groupRepository, userService, s3Service, null);

            when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
            IdeaList ideaList = new IdeaList(testGroupId, "Test List", IdeaListCategory.RESTAURANT, null, testUserId);
            ideaList.setListId(testListId);
            when(ideaListRepository.findIdeaListWithMembersById(testGroupId, testListId))
                    .thenReturn(Optional.of(ideaList));

            // When/Then: No exception thrown
            assertThatCode(() -> serviceWithoutEnrichment.getIdeaList(
                    testGroupId, testListId, testUserId))
                    .doesNotThrowAnyException();
        }

        @Test
        void deleteIdea_WithCachedPhoto_DeletesPhotoFromS3() {
            // Given: Idea with a cached photo
            when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);

            IdeaListMember member = new IdeaListMember(testGroupId, testListId, "Restaurant", null, null, testUserId);
            member.setIdeaId(testIdeaId);
            member.setCachedPhotoUrl("places/photos/" + testIdeaId + ".jpg");
            when(ideaListRepository.findIdeaListMemberById(testGroupId, testListId, testIdeaId))
                    .thenReturn(Optional.of(member));

            // When: Delete the idea
            ideaListService.deleteIdea(testGroupId, testListId, testIdeaId, testUserId);

            // Then: S3 cleanup triggered and idea deleted
            verify(s3Service).deleteImageAsync("places/photos/" + testIdeaId + ".jpg");
            verify(ideaListRepository).deleteIdeaListMember(testGroupId, testListId, testIdeaId);
        }

        @Test
        void deleteIdea_WithoutCachedPhoto_SkipsS3Cleanup() {
            // Given: Idea without a cached photo
            when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);

            IdeaListMember member = new IdeaListMember(testGroupId, testListId, "Custom Place", null, null, testUserId);
            member.setIdeaId(testIdeaId);
            when(ideaListRepository.findIdeaListMemberById(testGroupId, testListId, testIdeaId))
                    .thenReturn(Optional.of(member));

            // When: Delete the idea
            ideaListService.deleteIdea(testGroupId, testListId, testIdeaId, testUserId);

            // Then: No S3 cleanup, but idea still deleted
            verify(s3Service, never()).deleteImageAsync(any());
            verify(ideaListRepository).deleteIdeaListMember(testGroupId, testListId, testIdeaId);
        }

        @Test
        void addIdeaToList_LatitudeOnly_ThrowsValidation() {
            // Given
            when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
            when(ideaListRepository.ideaListExists(testGroupId, testListId)).thenReturn(true);

            CreateIdeaRequest request = new CreateIdeaRequest();
            request.setName("Test Place");
            request.setLatitude(40.7128);

            // When/Then
            assertThatThrownBy(() -> ideaListService.addIdeaToList(testGroupId, testListId, request, testUserId))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("together");
        }

        @Test
        void addIdeaToList_LatitudeOutOfRange_ThrowsValidation() {
            // Given
            when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
            when(ideaListRepository.ideaListExists(testGroupId, testListId)).thenReturn(true);

            CreateIdeaRequest request = new CreateIdeaRequest();
            request.setName("Test Place");
            request.setLatitude(999.0);
            request.setLongitude(-74.0);

            // When/Then
            assertThatThrownBy(() -> ideaListService.addIdeaToList(testGroupId, testListId, request, testUserId))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Latitude");
        }

        @Test
        void addIdeaToList_LongitudeOutOfRange_ThrowsValidation() {
            // Given
            when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
            when(ideaListRepository.ideaListExists(testGroupId, testListId)).thenReturn(true);

            CreateIdeaRequest request = new CreateIdeaRequest();
            request.setName("Test Place");
            request.setLatitude(40.7128);
            request.setLongitude(-200.0);

            // When/Then
            assertThatThrownBy(() -> ideaListService.addIdeaToList(testGroupId, testListId, request, testUserId))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Longitude");
        }

        @Test
        void addIdeaToList_LongitudeWithoutLatitude_ThrowsValidationException() {
            when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
            when(ideaListRepository.ideaListExists(testGroupId, testListId)).thenReturn(true);

            CreateIdeaRequest request = new CreateIdeaRequest();
            request.setName("Test Place");
            request.setLongitude(-74.0060);

            assertThatThrownBy(() -> ideaListService.addIdeaToList(testGroupId, testListId, request, testUserId))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("together");
        }

        @Test
        void addIdeaToList_InvalidLatitude91_ThrowsValidationException() {
            when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
            when(ideaListRepository.ideaListExists(testGroupId, testListId)).thenReturn(true);

            CreateIdeaRequest request = new CreateIdeaRequest();
            request.setName("Test Place");
            request.setLatitude(91.0);
            request.setLongitude(-74.0);

            assertThatThrownBy(() -> ideaListService.addIdeaToList(testGroupId, testListId, request, testUserId))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Latitude");
        }

        @Test
        void addIdeaToList_InvalidLongitude181_ThrowsValidationException() {
            when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
            when(ideaListRepository.ideaListExists(testGroupId, testListId)).thenReturn(true);

            CreateIdeaRequest request = new CreateIdeaRequest();
            request.setName("Test Place");
            request.setLatitude(40.0);
            request.setLongitude(181.0);

            assertThatThrownBy(() -> ideaListService.addIdeaToList(testGroupId, testListId, request, testUserId))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Longitude");
        }

        @Test
        void updateIdea_InvalidCoordinates_ThrowsValidationException() {
            when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);

            IdeaListMember existingMember = new IdeaListMember(testGroupId, testListId, "Restaurant", null, null, testUserId);
            existingMember.setIdeaId(testIdeaId);
            when(ideaListRepository.findIdeaListMemberById(testGroupId, testListId, testIdeaId))
                    .thenReturn(Optional.of(existingMember));

            UpdateIdeaRequest request = new UpdateIdeaRequest();
            request.setLatitude(95.0);
            request.setLongitude(-74.0);

            assertThatThrownBy(() -> ideaListService.updateIdea(testGroupId, testListId, testIdeaId, request, testUserId))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Latitude");
        }

        @Test
        void addIdeaToList_EnrichmentServiceNull_PlaceIdeaStillSetsPending() {
            IdeaListServiceImpl serviceWithoutEnrichment = new IdeaListServiceImpl(
                    ideaListRepository, groupRepository, userService, s3Service, null);

            when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
            when(ideaListRepository.ideaListExists(testGroupId, testListId)).thenReturn(true);
            when(ideaListRepository.saveIdeaListMember(any(IdeaListMember.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            CreateIdeaRequest request = new CreateIdeaRequest();
            request.setName("Test Restaurant");
            request.setAddress("123 Main St");
            request.setGooglePlaceId("ChIJ_test");

            IdeaDTO result = serviceWithoutEnrichment.addIdeaToList(testGroupId, testListId, request, testUserId);

            assertThat(result.getEnrichmentStatus()).isEqualTo("PENDING");
        }

        @Test
        void addIdeaToList_EnrichmentDisabled_NoCacheOrAsyncCalls() {
            when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
            when(ideaListRepository.ideaListExists(testGroupId, testListId)).thenReturn(true);
            when(ideaListRepository.saveIdeaListMember(any(IdeaListMember.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(placeEnrichmentService.isEnabled()).thenReturn(false);

            CreateIdeaRequest request = new CreateIdeaRequest();
            request.setName("Joe's Pizza");
            request.setGooglePlaceId("ChIJ_test");
            request.setLatitude(40.7);
            request.setLongitude(-74.0);

            IdeaDTO result = ideaListService.addIdeaToList(testGroupId, testListId, request, testUserId);

            assertThat(result.getEnrichmentStatus()).isEqualTo("PENDING");
            verify(placeEnrichmentService, never()).lookupCache(any(), any(), any(), any());
            verify(placeEnrichmentService, never()).enrichPlaceAsync(
                    any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void addIdeaToList_NonPlaceIdea_EnrichmentStatusIsNull() {
            when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
            when(ideaListRepository.ideaListExists(testGroupId, testListId)).thenReturn(true);
            when(ideaListRepository.saveIdeaListMember(any(IdeaListMember.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            CreateIdeaRequest request = new CreateIdeaRequest();
            request.setName("Movie Night Ideas");
            request.setNote("List of movies to watch");

            IdeaDTO result = ideaListService.addIdeaToList(testGroupId, testListId, request, testUserId);

            assertThat(result.getEnrichmentStatus()).isNull();
            verify(placeEnrichmentService, never()).lookupCache(any(), any(), any(), any());
        }

        @Test
        void getIdeaList_WithPlaceIdeas_TriggersReadPathEnrichment() {
            when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
            when(placeEnrichmentService.isEnabled()).thenReturn(true);

            IdeaList ideaList = new IdeaList(testGroupId, "Places", IdeaListCategory.RESTAURANT, null, testUserId);
            ideaList.setListId(testListId);
            IdeaListMember m1 = new IdeaListMember(testGroupId, testListId, "Restaurant A", null, null, testUserId);
            m1.setGooglePlaceId("ChIJ_a");
            IdeaListMember m2 = new IdeaListMember(testGroupId, testListId, "Restaurant B", null, null, testUserId);
            m2.setGooglePlaceId("ChIJ_b");
            ideaList.getMembers().addAll(List.of(m1, m2));

            when(ideaListRepository.findIdeaListWithMembersById(testGroupId, testListId))
                    .thenReturn(Optional.of(ideaList));

            ideaListService.getIdeaList(testGroupId, testListId, testUserId);

            verify(placeEnrichmentService).triggerReadPathEnrichment(
                    eq(ideaList.getMembers()), eq(testGroupId), eq(testListId));
        }

        @Test
        void getIdeaListsForGroup_MultipleListsWithPlaces_TriggersEnrichmentForEachList() {
            when(groupRepository.isUserMemberOfGroup(testGroupId, testUserId)).thenReturn(true);
            when(placeEnrichmentService.isEnabled()).thenReturn(true);

            IdeaList list1 = new IdeaList(testGroupId, "Restaurants", IdeaListCategory.RESTAURANT, null, testUserId);
            list1.setListId(UUID.randomUUID().toString());
            list1.getMembers().add(new IdeaListMember(testGroupId, list1.getListId(), "Place A", null, null, testUserId));

            IdeaList list2 = new IdeaList(testGroupId, "Bars", IdeaListCategory.BAR, null, testUserId);
            list2.setListId(UUID.randomUUID().toString());
            list2.getMembers().add(new IdeaListMember(testGroupId, list2.getListId(), "Place B", null, null, testUserId));

            when(ideaListRepository.findAllIdeaListsWithMembersByGroupId(testGroupId))
                    .thenReturn(List.of(list1, list2));

            ideaListService.getIdeaListsForGroup(testGroupId, testUserId);

            verify(placeEnrichmentService).triggerReadPathEnrichment(
                    eq(list1.getMembers()), eq(testGroupId), eq(list1.getListId()));
            verify(placeEnrichmentService).triggerReadPathEnrichment(
                    eq(list2.getMembers()), eq(testGroupId), eq(list2.getListId()));
        }
    }
}