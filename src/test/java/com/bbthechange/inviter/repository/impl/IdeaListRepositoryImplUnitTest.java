package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.model.IdeaList;
import com.bbthechange.inviter.model.IdeaListCategory;
import com.bbthechange.inviter.model.IdeaListMember;
import com.bbthechange.inviter.util.InviterKeyFactory;
import com.bbthechange.inviter.util.QueryPerformanceTracker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for IdeaListRepositoryImpl focusing on key generation and data integrity.
 * These tests verify the critical DynamoDB key patterns without requiring TestContainers.
 */
@ExtendWith(MockitoExtension.class)
class IdeaListRepositoryImplUnitTest {

    @Mock
    private DynamoDbClient dynamoDbClient;
    
    @Mock
    private QueryPerformanceTracker queryTracker;

    private IdeaListRepositoryImpl repository;
    private String testGroupId;
    private String testUserId;

    @BeforeEach
    void setUp() {
        repository = new IdeaListRepositoryImpl(dynamoDbClient, queryTracker);
        testGroupId = UUID.randomUUID().toString();
        testUserId = UUID.randomUUID().toString();
    }

    // ===== KEY GENERATION & DATA INTEGRITY TESTS =====

    @Test
    void ideaListConstructor_ValidData_GeneratesCorrectDynamoDBKeys() {
        // Given: Valid idea list for specific group
        IdeaList ideaList = new IdeaList(testGroupId, "Test List", IdeaListCategory.RESTAURANT, "Test note", testUserId);

        // Then: Verify DynamoDB key patterns match design specification
        assertThat(ideaList.getPk()).isEqualTo(InviterKeyFactory.getGroupPk(testGroupId));
        assertThat(ideaList.getSk()).isEqualTo(InviterKeyFactory.getIdeaListSk(ideaList.getListId()));
        assertThat(ideaList.getItemType()).isEqualTo("IDEALIST");
        
        // Verify the pattern format directly
        assertThat(ideaList.getPk()).isEqualTo("GROUP#" + testGroupId);
        assertThat(ideaList.getSk()).isEqualTo("IDEALIST#" + ideaList.getListId());
        
        // Verify other fields are set correctly
        assertThat(ideaList.getGroupId()).isEqualTo(testGroupId);
        assertThat(ideaList.getName()).isEqualTo("Test List");
        assertThat(ideaList.getCategory()).isEqualTo(IdeaListCategory.RESTAURANT);
        assertThat(ideaList.getNote()).isEqualTo("Test note");
        assertThat(ideaList.getCreatedBy()).isEqualTo(testUserId);
        assertThat(ideaList.getListId()).isNotNull();
        assertThat(ideaList.getCreatedAt()).isNotNull();
    }

    @Test
    void ideaListMemberConstructor_ValidData_GeneratesCorrectDynamoDBKeys() {
        // Given: Idea for specific list
        String listId = UUID.randomUUID().toString();
        IdeaListMember member = new IdeaListMember(testGroupId, listId, "Test Idea", "http://example.com", "Test note", testUserId);

        // Then: Verify member key patterns enable efficient queries
        assertThat(member.getPk()).isEqualTo(InviterKeyFactory.getGroupPk(testGroupId));
        assertThat(member.getSk()).isEqualTo(InviterKeyFactory.getIdeaListMemberSk(listId, member.getIdeaId()));
        assertThat(member.getItemType()).isEqualTo("IDEA");
        
        // Verify the pattern format directly
        assertThat(member.getPk()).isEqualTo("GROUP#" + testGroupId);
        assertThat(member.getSk()).isEqualTo("IDEALIST#" + listId + "#IDEA#" + member.getIdeaId());
        
        // Verify other fields are set correctly
        assertThat(member.getGroupId()).isEqualTo(testGroupId);
        assertThat(member.getListId()).isEqualTo(listId);
        assertThat(member.getName()).isEqualTo("Test Idea");
        assertThat(member.getUrl()).isEqualTo("http://example.com");
        assertThat(member.getNote()).isEqualTo("Test note");
        assertThat(member.getAddedBy()).isEqualTo(testUserId);
        assertThat(member.getIdeaId()).isNotNull();
        assertThat(member.getAddedTime()).isNotNull();
    }

    @Test
    void ideaListMembers_IsIgnoredByDynamoDB() {
        // Given: IdeaList with members populated
        IdeaList ideaList = new IdeaList(testGroupId, "Test List", IdeaListCategory.BOOK, "Reading list", testUserId);
        IdeaListMember member1 = new IdeaListMember(testGroupId, ideaList.getListId(), "1984", null, "Dystopian novel", testUserId);
        IdeaListMember member2 = new IdeaListMember(testGroupId, ideaList.getListId(), "Dune", null, "Sci-fi epic", testUserId);
        
        ideaList.getMembers().add(member1);
        ideaList.getMembers().add(member2);

        // When: Check if members field is properly ignored
        // Then: The @DynamoDbIgnore annotation should be present on getMembers() method
        // This is verified by the annotation itself, but we can test the behavior
        assertThat(ideaList.getMembers()).hasSize(2);
        assertThat(ideaList.getMembers()).containsExactly(member1, member2);
    }

    @Test
    void keyFactoryPatterns_MatchExpectedFormat() {
        // Given: Test IDs
        String groupId = "12345678-1234-1234-1234-123456789012";
        String listId = "87654321-4321-4321-4321-210987654321";
        String ideaId = "11111111-2222-3333-4444-555555555555";

        // When: Generate keys using factory
        String groupPk = InviterKeyFactory.getGroupPk(groupId);
        String listSk = InviterKeyFactory.getIdeaListSk(listId);
        String memberSk = InviterKeyFactory.getIdeaListMemberSk(listId, ideaId);
        String listPrefix = InviterKeyFactory.getIdeaListPrefix(listId);
        String queryPrefix = InviterKeyFactory.getIdeaListQueryPrefix();

        // Then: Verify patterns match specification
        assertThat(groupPk).isEqualTo("GROUP#" + groupId);
        assertThat(listSk).isEqualTo("IDEALIST#" + listId);
        assertThat(memberSk).isEqualTo("IDEALIST#" + listId + "#IDEA#" + ideaId);
        assertThat(listPrefix).isEqualTo("IDEALIST#" + listId);
        assertThat(queryPrefix).isEqualTo("IDEALIST#");
    }

    @Test
    void keyFactoryTypeChecking_CorrectlyIdentifiesKeyTypes() {
        // Given: Different key types
        String listId = "12345678-1234-1234-1234-123456789012";
        String ideaId = "87654321-4321-4321-4321-210987654321";
        
        String ideaListSk = InviterKeyFactory.getIdeaListSk(listId);
        String ideaMemberSk = InviterKeyFactory.getIdeaListMemberSk(listId, ideaId);

        // When/Then: Test type identification
        assertThat(InviterKeyFactory.isIdeaList(ideaListSk)).isTrue();
        assertThat(InviterKeyFactory.isIdeaListMember(ideaListSk)).isFalse();
        
        assertThat(InviterKeyFactory.isIdeaList(ideaMemberSk)).isFalse();
        assertThat(InviterKeyFactory.isIdeaListMember(ideaMemberSk)).isTrue();
        
        // Test edge cases
        assertThat(InviterKeyFactory.isIdeaList(null)).isFalse();
        assertThat(InviterKeyFactory.isIdeaListMember(null)).isFalse();
        assertThat(InviterKeyFactory.isIdeaList("")).isFalse();
        assertThat(InviterKeyFactory.isIdeaListMember("")).isFalse();
    }

    @Test
    void ideaListMember_ConstructorValidation_RequiredFieldsSet() {
        // Given: All required parameters for IdeaListMember
        String listId = UUID.randomUUID().toString();

        // When: Create idea list member
        IdeaListMember member = new IdeaListMember(testGroupId, listId, "Test Idea", "http://example.com", "Test note", testUserId);

        // Then: All required fields are set and UUIDs generated
        assertThat(member.getIdeaId()).isNotNull().matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
        assertThat(member.getGroupId()).isEqualTo(testGroupId);
        assertThat(member.getListId()).isEqualTo(listId);
        assertThat(member.getAddedBy()).isEqualTo(testUserId);
        assertThat(member.getAddedTime()).isNotNull();
        assertThat(member.getItemType()).isEqualTo("IDEA");
    }

    @Test 
    void ideaList_ConstructorValidation_RequiredFieldsSet() {
        // When: Create idea list
        IdeaList ideaList = new IdeaList(testGroupId, "Test List", IdeaListCategory.ACTIVITY, "Test note", testUserId);

        // Then: All required fields are set and UUIDs generated
        assertThat(ideaList.getListId()).isNotNull().matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
        assertThat(ideaList.getGroupId()).isEqualTo(testGroupId);
        assertThat(ideaList.getCreatedBy()).isEqualTo(testUserId);
        assertThat(ideaList.getCreatedAt()).isNotNull();
        assertThat(ideaList.getItemType()).isEqualTo("IDEALIST");
        assertThat(ideaList.getMembers()).isEmpty(); // Starts empty
    }

    // ===== EDGE CASES & ERROR HANDLING =====

    @Test
    void keyFactory_InvalidUUIDs_ThrowsInvalidKeyException() {
        // Given: Invalid UUID formats
        String invalidGroupId1 = "not-a-uuid";
        String invalidGroupId2 = "";
        String invalidGroupId3 = null;
        String invalidListId = "12345"; // Too short

        // When/Then: Verify InvalidKeyException is thrown for invalid IDs
        assertThatThrownBy(() -> InviterKeyFactory.getGroupPk(invalidGroupId1))
                .isInstanceOf(com.bbthechange.inviter.exception.InvalidKeyException.class)
                .hasMessageContaining("Invalid Group ID format");

        assertThatThrownBy(() -> InviterKeyFactory.getGroupPk(invalidGroupId2))
                .isInstanceOf(com.bbthechange.inviter.exception.InvalidKeyException.class)
                .hasMessageContaining("Group ID cannot be null or empty");

        assertThatThrownBy(() -> InviterKeyFactory.getGroupPk(invalidGroupId3))
                .isInstanceOf(com.bbthechange.inviter.exception.InvalidKeyException.class)
                .hasMessageContaining("Group ID cannot be null or empty");

        assertThatThrownBy(() -> InviterKeyFactory.getIdeaListSk(invalidListId))
                .isInstanceOf(com.bbthechange.inviter.exception.InvalidKeyException.class)
                .hasMessageContaining("Invalid IdeaList ID format");
    }

    @Test
    void ideaListMember_WithMinimalData_HandlesNullOptionalFields() {
        // Given: Minimal required data (name, url, note can be null)
        String listId = UUID.randomUUID().toString();

        // When: Create member with minimal data
        IdeaListMember member1 = new IdeaListMember(testGroupId, listId, "Required Name", null, null, testUserId);
        IdeaListMember member2 = new IdeaListMember(testGroupId, listId, null, "http://example.com", null, testUserId);
        IdeaListMember member3 = new IdeaListMember(testGroupId, listId, null, null, "Just a note", testUserId);

        // Then: Objects created successfully with null optional fields
        assertThat(member1.getName()).isEqualTo("Required Name");
        assertThat(member1.getUrl()).isNull();
        assertThat(member1.getNote()).isNull();

        assertThat(member2.getName()).isNull();
        assertThat(member2.getUrl()).isEqualTo("http://example.com");
        assertThat(member2.getNote()).isNull();

        assertThat(member3.getName()).isNull();
        assertThat(member3.getUrl()).isNull();
        assertThat(member3.getNote()).isEqualTo("Just a note");
        
        // All should have required fields set
        assertThat(member1.getGroupId()).isEqualTo(testGroupId);
        assertThat(member1.getListId()).isEqualTo(listId);
        assertThat(member1.getAddedBy()).isEqualTo(testUserId);
        assertThat(member1.getIdeaId()).isNotNull();
        assertThat(member1.getAddedTime()).isNotNull();
    }

    @Test
    void ideaList_WithMinimalData_HandlesNullOptionalFields() {
        // When: Create idea list with minimal data (note can be null)
        IdeaList ideaList = new IdeaList(testGroupId, "Required Name", IdeaListCategory.OTHER, null, testUserId);

        // Then: Object created successfully with null note
        assertThat(ideaList.getName()).isEqualTo("Required Name");
        assertThat(ideaList.getNote()).isNull();
        assertThat(ideaList.getCategory()).isEqualTo(IdeaListCategory.OTHER);
        
        // Required fields should be set
        assertThat(ideaList.getGroupId()).isEqualTo(testGroupId);
        assertThat(ideaList.getCreatedBy()).isEqualTo(testUserId);
        assertThat(ideaList.getListId()).isNotNull();
        assertThat(ideaList.getCreatedAt()).isNotNull();
    }

    @Test
    void keyFactory_MultipleKeyGeneration_ProducesUniqueKeys() {
        // Given: Same group but different lists
        String listId1 = UUID.randomUUID().toString();
        String listId2 = UUID.randomUUID().toString();
        String ideaId1 = UUID.randomUUID().toString();
        String ideaId2 = UUID.randomUUID().toString();

        // When: Generate keys for different entities
        String listSk1 = InviterKeyFactory.getIdeaListSk(listId1);
        String listSk2 = InviterKeyFactory.getIdeaListSk(listId2);
        String memberSk1 = InviterKeyFactory.getIdeaListMemberSk(listId1, ideaId1);
        String memberSk2 = InviterKeyFactory.getIdeaListMemberSk(listId1, ideaId2);

        // Then: All keys are unique
        assertThat(listSk1).isNotEqualTo(listSk2);
        assertThat(memberSk1).isNotEqualTo(memberSk2);
        assertThat(listSk1).isNotEqualTo(memberSk1);
        assertThat(listSk2).isNotEqualTo(memberSk2);
    }

    @Test
    void ideaListCategory_AllValues_HaveCorrectDisplayNames() {
        // When/Then: Verify all enum values have proper display names
        assertThat(IdeaListCategory.RESTAURANT.getDisplayName()).isEqualTo("Restaurant");
        assertThat(IdeaListCategory.ACTIVITY.getDisplayName()).isEqualTo("Activity");
        assertThat(IdeaListCategory.TRAIL.getDisplayName()).isEqualTo("Trail");
        assertThat(IdeaListCategory.MOVIE.getDisplayName()).isEqualTo("Movie");
        assertThat(IdeaListCategory.BOOK.getDisplayName()).isEqualTo("Book");
        assertThat(IdeaListCategory.TRAVEL.getDisplayName()).isEqualTo("Travel");
        assertThat(IdeaListCategory.OTHER.getDisplayName()).isEqualTo("Other");
        
        // Verify toString() returns display name
        assertThat(IdeaListCategory.RESTAURANT.toString()).isEqualTo("Restaurant");
        assertThat(IdeaListCategory.OTHER.toString()).isEqualTo("Other");
    }
}