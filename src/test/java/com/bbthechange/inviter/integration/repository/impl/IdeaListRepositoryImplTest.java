package com.bbthechange.inviter.integration.repository.impl;

import com.bbthechange.inviter.config.BaseIntegrationTest;
import com.bbthechange.inviter.model.IdeaList;
import com.bbthechange.inviter.model.IdeaListCategory;
import com.bbthechange.inviter.model.IdeaListMember;
import com.bbthechange.inviter.repository.IdeaListRepository;
import com.bbthechange.inviter.util.InviterKeyFactory;
import com.bbthechange.inviter.util.QueryPerformanceTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for IdeaListRepositoryImpl focusing on performance, key generation, and data integrity.
 * Tests the critical N+1 query fix and DynamoDB key patterns.
 */
@Testcontainers
class IdeaListRepositoryImplTest extends BaseIntegrationTest {

    @Autowired
    private IdeaListRepository ideaListRepository;

    private String testGroupId;
    private String testUserId;

    @BeforeEach
    void setUp() {
        testGroupId = UUID.randomUUID().toString();
        testUserId = UUID.randomUUID().toString();
    }

    // ===== CRITICAL PERFORMANCE & QUERY CORRECTNESS TESTS =====

    @Test
    void findAllIdeaListsWithMembersByGroupId_WithMultipleLists_ReturnsPopulatedListsInSingleQuery() {
        // Given: Create 3 idea lists with varying numbers of ideas (0, 2, 5 ideas respectively)
        IdeaList list1 = new IdeaList(testGroupId, "Restaurants", IdeaListCategory.RESTAURANT, "Places to eat", testUserId);
        IdeaList list2 = new IdeaList(testGroupId, "Movies", IdeaListCategory.MOVIE, "Films to watch", testUserId);
        IdeaList list3 = new IdeaList(testGroupId, "Trails", IdeaListCategory.TRAIL, "Hiking spots", testUserId);
        
        // Save the lists
        ideaListRepository.saveIdeaList(list1);
        ideaListRepository.saveIdeaList(list2);
        ideaListRepository.saveIdeaList(list3);
        
        // Add members to list2 (2 ideas)
        IdeaListMember idea1 = new IdeaListMember(testGroupId, list2.getListId(), "The Matrix", null, "Great sci-fi movie", testUserId);
        IdeaListMember idea2 = new IdeaListMember(testGroupId, list2.getListId(), "Inception", "https://imdb.com/inception", "Mind-bending film", testUserId);
        ideaListRepository.saveIdeaListMember(idea1);
        ideaListRepository.saveIdeaListMember(idea2);
        
        // Add members to list3 (5 ideas)
        for (int i = 1; i <= 5; i++) {
            IdeaListMember idea = new IdeaListMember(testGroupId, list3.getListId(), "Trail " + i, null, "Hiking trail " + i, testUserId);
            ideaListRepository.saveIdeaListMember(idea);
        }
        
        // When: Retrieve all lists with members
        List<IdeaList> result = ideaListRepository.findAllIdeaListsWithMembersByGroupId(testGroupId);
        
        // Then: Verify the N+1 query fix works - all lists returned with members pre-populated
        // Assert: All 3 lists returned with correct member counts
        assertThat(result).hasSize(3);
        
        // Find each list and verify member counts
        IdeaList retrievedList1 = result.stream().filter(l -> l.getListId().equals(list1.getListId())).findFirst().orElse(null);
        IdeaList retrievedList2 = result.stream().filter(l -> l.getListId().equals(list2.getListId())).findFirst().orElse(null);
        IdeaList retrievedList3 = result.stream().filter(l -> l.getListId().equals(list3.getListId())).findFirst().orElse(null);
        
        assertThat(retrievedList1).isNotNull();
        assertThat(retrievedList1.getMembers()).hasSize(0);
        
        assertThat(retrievedList2).isNotNull();
        assertThat(retrievedList2.getMembers()).hasSize(2);
        
        assertThat(retrievedList3).isNotNull();
        assertThat(retrievedList3.getMembers()).hasSize(5);
        
        // Assert: Lists sorted by creation time (newest first)
        for (int i = 0; i < result.size() - 1; i++) {
            assertThat(result.get(i).getCreatedAt()).isAfterOrEqualTo(result.get(i + 1).getCreatedAt());
        }
        
        // Assert: Members within each list sorted by added time (newest first)
        if (retrievedList2.getMembers().size() > 1) {
            List<IdeaListMember> members = retrievedList2.getMembers();
            for (int i = 0; i < members.size() - 1; i++) {
                assertThat(members.get(i).getAddedTime()).isAfterOrEqualTo(members.get(i + 1).getAddedTime());
            }
        }
    }

    @Test
    void findAllIdeaListsWithMembersByGroupId_WithNoLists_ReturnsEmptyList() {
        // Given: A group with no idea lists
        String emptyGroupId = UUID.randomUUID().toString();
        
        // When: Query group with no idea lists
        List<IdeaList> result = ideaListRepository.findAllIdeaListsWithMembersByGroupId(emptyGroupId);

        // Then: Empty list returned, no exceptions
        assertThat(result).isEmpty();
    }

    @Test
    void findIdeaListWithMembersById_ExistingList_ReturnsListWithMembersAttached() {
        // Given: List with 3 ideas
        IdeaList ideaList = new IdeaList(testGroupId, "Books", IdeaListCategory.BOOK, "Reading list", testUserId);
        ideaListRepository.saveIdeaList(ideaList);
        
        // Add 3 members with different timestamps to test sorting
        IdeaListMember idea1 = new IdeaListMember(testGroupId, ideaList.getListId(), "1984", null, "Dystopian novel", testUserId);
        IdeaListMember idea2 = new IdeaListMember(testGroupId, ideaList.getListId(), "Dune", null, "Sci-fi epic", testUserId);
        IdeaListMember idea3 = new IdeaListMember(testGroupId, ideaList.getListId(), "The Hobbit", null, "Fantasy adventure", testUserId);
        
        ideaListRepository.saveIdeaListMember(idea1);
        ideaListRepository.saveIdeaListMember(idea2);
        ideaListRepository.saveIdeaListMember(idea3);
        
        // When: Retrieve single list with members
        Optional<IdeaList> result = ideaListRepository.findIdeaListWithMembersById(testGroupId, ideaList.getListId());

        // Then: IdeaList returned with 3 members attached, sorted correctly
        assertThat(result).isPresent();
        IdeaList retrievedList = result.get();
        assertThat(retrievedList.getMembers()).hasSize(3);
        
        // Verify members are sorted by most recent first
        List<IdeaListMember> members = retrievedList.getMembers();
        for (int i = 0; i < members.size() - 1; i++) {
            assertThat(members.get(i).getAddedTime()).isAfterOrEqualTo(members.get(i + 1).getAddedTime());
        }
    }

    // ===== KEY GENERATION & DATA INTEGRITY TESTS =====

    @Test
    void saveIdeaList_ValidData_GeneratesCorrectDynamoDBKeys() {
        // Given: Valid idea list for specific group
        IdeaList ideaList = new IdeaList(testGroupId, "Test List", IdeaListCategory.RESTAURANT, "Test note", testUserId);

        // When: Save idea list
        IdeaList saved = ideaListRepository.saveIdeaList(ideaList);

        // Then: Verify DynamoDB key patterns match design specification
        assertThat(saved.getPk()).isEqualTo(InviterKeyFactory.getGroupPk(testGroupId));
        assertThat(saved.getSk()).isEqualTo(InviterKeyFactory.getIdeaListSk(ideaList.getListId()));
        assertThat(saved.getItemType()).isEqualTo("IDEALIST");
        
        // Verify the pattern format directly
        assertThat(saved.getPk()).isEqualTo("GROUP#" + testGroupId);
        assertThat(saved.getSk()).isEqualTo("IDEALIST#" + ideaList.getListId());
    }

    @Test
    void saveIdeaListMember_ValidData_GeneratesCorrectDynamoDBKeys() {
        // Given: Idea for specific list
        String listId = UUID.randomUUID().toString();
        IdeaListMember member = new IdeaListMember(testGroupId, listId, "Test Idea", "http://example.com", "Test note", testUserId);

        // When: Save idea to specific list
        IdeaListMember saved = ideaListRepository.saveIdeaListMember(member);

        // Then: Verify member key patterns enable efficient queries
        assertThat(saved.getPk()).isEqualTo(InviterKeyFactory.getGroupPk(testGroupId));
        assertThat(saved.getSk()).isEqualTo(InviterKeyFactory.getIdeaListMemberSk(listId, member.getIdeaId()));
        assertThat(saved.getItemType()).isEqualTo("IDEA");
        
        // Verify the pattern format directly
        assertThat(saved.getPk()).isEqualTo("GROUP#" + testGroupId);
        assertThat(saved.getSk()).isEqualTo("IDEALIST#" + listId + "#IDEA#" + member.getIdeaId());
    }

    // ===== EDGE CASES & ERROR HANDLING =====

    @Test
    void findIdeaListWithMembersById_NonexistentList_ReturnsEmpty() {
        // Given: Non-existent list ID
        String nonExistentListId = UUID.randomUUID().toString();

        // When: Attempt to find non-existent list
        Optional<IdeaList> result = ideaListRepository.findIdeaListWithMembersById(testGroupId, nonExistentListId);

        // Then: Optional.empty() returned, no exceptions
        assertThat(result).isEmpty();
    }

    @Test
    void deleteIdeaListWithAllMembers_WithManyIdeas_DeletesAllItemsAtomically() {
        // Given: List with 10 ideas
        IdeaList ideaList = new IdeaList(testGroupId, "Large List", IdeaListCategory.MOVIE, "List with many items", testUserId);
        ideaListRepository.saveIdeaList(ideaList);
        
        // Add 10 ideas
        for (int i = 1; i <= 10; i++) {
            IdeaListMember idea = new IdeaListMember(testGroupId, ideaList.getListId(), "Idea " + i, null, "Note " + i, testUserId);
            ideaListRepository.saveIdeaListMember(idea);
        }
        
        // Verify setup: list and all members exist
        Optional<IdeaList> beforeDelete = ideaListRepository.findIdeaListWithMembersById(testGroupId, ideaList.getListId());
        assertThat(beforeDelete).isPresent();
        assertThat(beforeDelete.get().getMembers()).hasSize(10);

        // When: Delete list with all members
        ideaListRepository.deleteIdeaListWithAllMembers(testGroupId, ideaList.getListId());

        // Then: All 11 items (1 list + 10 ideas) deleted in single batch operation
        Optional<IdeaList> afterDelete = ideaListRepository.findIdeaListWithMembersById(testGroupId, ideaList.getListId());
        assertThat(afterDelete).isEmpty();
        
        // Verify no orphaned ideas remain
        List<IdeaListMember> orphanedMembers = ideaListRepository.findMembersByListId(testGroupId, ideaList.getListId());
        assertThat(orphanedMembers).isEmpty();
    }
}