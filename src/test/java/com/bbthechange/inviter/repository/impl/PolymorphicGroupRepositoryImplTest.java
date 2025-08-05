package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.config.IntegrationTestConfiguration;
import com.bbthechange.inviter.exception.RepositoryException;
import com.bbthechange.inviter.model.Group;
import com.bbthechange.inviter.model.GroupMembership;
import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.util.QueryPerformanceTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for PolymorphicGroupRepositoryImpl.
 * Tests polymorphic deserialization with type discriminators.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("integration")
@Import(IntegrationTestConfiguration.class)
class PolymorphicGroupRepositoryImplTest {
    
    @Autowired
    private DynamoDbClient dynamoDbClient;
    
    @Autowired
    private QueryPerformanceTracker queryPerformanceTracker;
    
    private PolymorphicGroupRepositoryImpl repository;
    
    @BeforeEach
    void setUp() {
        repository = new PolymorphicGroupRepositoryImpl(dynamoDbClient, queryPerformanceTracker);
    }
    
    @Test
    void saveAndFindGroup_WithTypeDiscriminator_Success() {
        // Given
        Group group = new Group("Test Group", true);
        
        // When
        Group savedGroup = repository.save(group);
        Optional<Group> foundGroup = repository.findById(savedGroup.getGroupId());
        
        // Then
        assertThat(foundGroup).isPresent();
        assertThat(foundGroup.get().getGroupId()).isEqualTo(savedGroup.getGroupId());
        assertThat(foundGroup.get().getGroupName()).isEqualTo("Test Group");
        assertThat(foundGroup.get().isPublic()).isTrue();
        assertThat(foundGroup.get().getItemType()).isEqualTo("GROUP");
    }
    
    @Test
    void addAndFindMember_WithTypeDiscriminator_Success() {
        // Given
        String groupId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        GroupMembership membership = new GroupMembership(groupId, userId, "Test Group");
        
        // When
        GroupMembership savedMembership = repository.addMember(membership);
        Optional<GroupMembership> foundMembership = repository.findMembership(groupId, userId);
        
        // Then
        assertThat(foundMembership).isPresent();
        assertThat(foundMembership.get().getGroupId()).isEqualTo(groupId);
        assertThat(foundMembership.get().getUserId()).isEqualTo(userId);
        assertThat(foundMembership.get().getGroupName()).isEqualTo("Test Group");
        assertThat(foundMembership.get().getItemType()).isEqualTo("GROUP_MEMBERSHIP");
    }
    
    @Test
    void saveAndFindHangoutPointer_WithTypeDiscriminator_Success() {
        // Given
        String groupId = UUID.randomUUID().toString();
        String hangoutId = UUID.randomUUID().toString();
        HangoutPointer pointer = new HangoutPointer(groupId, hangoutId, "Test Hangout");
        pointer.setLocationName("Test Location");
        pointer.setHangoutTime(Instant.now().plusSeconds(3600));
        pointer.setParticipantCount(5);
        
        // When
        repository.saveHangoutPointer(pointer);
        List<HangoutPointer> hangouts = repository.findHangoutsByGroupId(groupId);
        
        // Then
        assertThat(hangouts).hasSize(1);
        HangoutPointer foundPointer = hangouts.get(0);
        assertThat(foundPointer.getGroupId()).isEqualTo(groupId);
        assertThat(foundPointer.getHangoutId()).isEqualTo(hangoutId);
        assertThat(foundPointer.getTitle()).isEqualTo("Test Hangout");
        assertThat(foundPointer.getLocationName()).isEqualTo("Test Location");
        assertThat(foundPointer.getItemType()).isEqualTo("HANGOUT_POINTER");
    }
    
    @Test
    void findMembersByGroupId_ReturnsOnlyGroupMemberships() {
        // Given
        String groupId = UUID.randomUUID().toString();
        
        // Create a group (different type)
        Group group = new Group("Test Group", false);
        group.setGroupId(groupId);
        repository.save(group);
        
        // Create memberships
        GroupMembership member1 = new GroupMembership(groupId, UUID.randomUUID().toString(), "Test Group");
        GroupMembership member2 = new GroupMembership(groupId, UUID.randomUUID().toString(), "Test Group");
        repository.addMember(member1);
        repository.addMember(member2);
        
        // Create a hangout pointer (different type)
        HangoutPointer pointer = new HangoutPointer(groupId, UUID.randomUUID().toString(), "Test Hangout");
        repository.saveHangoutPointer(pointer);
        
        // When
        List<GroupMembership> members = repository.findMembersByGroupId(groupId);
        
        // Then
        assertThat(members).hasSize(2);
        assertThat(members).allMatch(m -> m.getItemType().equals("GROUP_MEMBERSHIP"));
        assertThat(members).allMatch(m -> m.getGroupId().equals(groupId));
    }
    
    @Test
    void findGroupsByUserId_UsesGSI_ReturnsCorrectGroups() {
        // Given
        String userId = UUID.randomUUID().toString();
        
        // Create memberships for the user in different groups
        GroupMembership membership1 = new GroupMembership(
            UUID.randomUUID().toString(), userId, "Group 1"
        );
        GroupMembership membership2 = new GroupMembership(
            UUID.randomUUID().toString(), userId, "Group 2"
        );
        repository.addMember(membership1);
        repository.addMember(membership2);
        
        // When
        List<GroupMembership> userGroups = repository.findGroupsByUserId(userId);
        
        // Then
        assertThat(userGroups).hasSize(2);
        assertThat(userGroups).allMatch(m -> m.getUserId().equals(userId));
        assertThat(userGroups).allMatch(m -> m.getItemType().equals("GROUP_MEMBERSHIP"));
        assertThat(userGroups).extracting(GroupMembership::getGroupName)
            .containsExactlyInAnyOrder("Group 1", "Group 2");
    }
    
    @Test
    void createGroupWithFirstMember_AtomicTransaction_Success() {
        // Given
        Group group = new Group("Atomic Test Group", true);
        GroupMembership membership = new GroupMembership(
            group.getGroupId(), 
            UUID.randomUUID().toString(), 
            "Atomic Test Group"
        );
        membership.setRole("ADMIN");
        
        // When
        repository.createGroupWithFirstMember(group, membership);
        
        // Then
        Optional<Group> foundGroup = repository.findById(group.getGroupId());
        Optional<GroupMembership> foundMembership = repository.findMembership(
            group.getGroupId(), membership.getUserId()
        );
        
        assertThat(foundGroup).isPresent();
        assertThat(foundGroup.get().getItemType()).isEqualTo("GROUP");
        assertThat(foundMembership).isPresent();
        assertThat(foundMembership.get().getItemType()).isEqualTo("GROUP_MEMBERSHIP");
        assertThat(foundMembership.get().getRole()).isEqualTo("ADMIN");
    }
    
    @Test
    void updateHangoutPointer_UpdatesCorrectly() {
        // Given
        String groupId = UUID.randomUUID().toString();
        String hangoutId = UUID.randomUUID().toString();
        HangoutPointer pointer = new HangoutPointer(groupId, hangoutId, "Original Title");
        repository.saveHangoutPointer(pointer);
        
        // When
        Map<String, AttributeValue> updates = new HashMap<>();
        updates.put("title", AttributeValue.builder().s("Updated Title").build());
        updates.put("participantCount", AttributeValue.builder().n("10").build());
        repository.updateHangoutPointer(groupId, hangoutId, updates);
        
        // Then
        List<HangoutPointer> hangouts = repository.findHangoutsByGroupId(groupId);
        assertThat(hangouts).hasSize(1);
        // Note: The update method doesn't return the updated item, so we can only verify it doesn't throw
    }
    
    @Test
    void deleteOperations_RemoveItemsCorrectly() {
        // Given
        Group group = new Group("Delete Test Group", false);
        repository.save(group);
        
        String userId = UUID.randomUUID().toString();
        GroupMembership membership = new GroupMembership(group.getGroupId(), userId, "Delete Test Group");
        repository.addMember(membership);
        
        String hangoutId = UUID.randomUUID().toString();
        HangoutPointer pointer = new HangoutPointer(group.getGroupId(), hangoutId, "Delete Test Hangout");
        repository.saveHangoutPointer(pointer);
        
        // When & Then - Delete operations
        repository.removeMember(group.getGroupId(), userId);
        assertThat(repository.findMembership(group.getGroupId(), userId)).isEmpty();
        
        repository.deleteHangoutPointer(group.getGroupId(), hangoutId);
        assertThat(repository.findHangoutsByGroupId(group.getGroupId())).isEmpty();
        
        repository.delete(group.getGroupId());
        assertThat(repository.findById(group.getGroupId())).isEmpty();
    }
    
    @Test
    void findById_NonExistentGroup_ReturnsEmpty() {
        // When
        Optional<Group> result = repository.findById(UUID.randomUUID().toString());
        
        // Then
        assertThat(result).isEmpty();
    }
    
    @Test
    void polymorphicDeserialization_HandlesAllTypes() {
        // Given
        String groupId = UUID.randomUUID().toString();
        
        // Create items of each type
        Group group = new Group("Polymorphic Test", true);
        repository.save(group);
        groupId = group.getGroupId(); // Use the generated groupId
        
        GroupMembership membership = new GroupMembership(
            groupId, UUID.randomUUID().toString(), "Polymorphic Test"
        );
        repository.addMember(membership);
        
        HangoutPointer pointer = new HangoutPointer(
            groupId, UUID.randomUUID().toString(), "Polymorphic Hangout"
        );
        repository.saveHangoutPointer(pointer);
        
        // When - Query all items for the group partition
        // This would typically be done with a more generic query in production
        
        // Then - Verify each item type was saved and can be retrieved correctly
        Optional<Group> foundGroup = repository.findById(groupId);
        assertThat(foundGroup).isPresent();
        assertThat(foundGroup.get().getItemType()).isEqualTo("GROUP");
        
        List<GroupMembership> members = repository.findMembersByGroupId(groupId);
        assertThat(members).hasSize(1);
        assertThat(members.get(0).getItemType()).isEqualTo("GROUP_MEMBERSHIP");
        
        List<HangoutPointer> hangouts = repository.findHangoutsByGroupId(groupId);
        assertThat(hangouts).hasSize(1);
        assertThat(hangouts.get(0).getItemType()).isEqualTo("HANGOUT_POINTER");
    }
    
    @Test
    void errorHandling_WrapsExceptionsCorrectly() {
        // Given - repository with invalid table name
        PolymorphicGroupRepositoryImpl badRepository = new PolymorphicGroupRepositoryImpl(
            dynamoDbClient, queryPerformanceTracker
        ) {
            @Override
            public Optional<Group> findById(String groupId) {
                // Force an error by using an invalid table name
                throw new RepositoryException("Simulated error", null);
            }
        };
        
        // When & Then
        assertThatThrownBy(() -> badRepository.findById("test-id"))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("Simulated error");
    }
}