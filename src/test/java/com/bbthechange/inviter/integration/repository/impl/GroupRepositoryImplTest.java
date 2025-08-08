package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.config.BaseIntegrationTest;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.exception.TransactionFailedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for GroupRepositoryImpl using TestContainers.
 * Tests the atomic operations and efficient query patterns.
 */
@TestMethodOrder(OrderAnnotation.class)
@Testcontainers
class GroupRepositoryImplTest extends BaseIntegrationTest {
    
    @Autowired
    private GroupRepository groupRepository;
    
    private static String testGroupId;
    private static final String TEST_USER_ID = "12345678-1234-1234-1234-123456789012";
    private static final String TEST_GROUP_NAME = "Test Group";
    
    @Test
    @Order(1)
    void createGroupWithFirstMember_AtomicSuccess() {
        // Given
        Group group = new Group(TEST_GROUP_NAME, true);
        testGroupId = group.getGroupId(); // Store for later tests
        
        GroupMembership membership = new GroupMembership(
            group.getGroupId(), TEST_USER_ID, TEST_GROUP_NAME);
        membership.setRole(GroupRole.ADMIN);
        
        // When
        assertThatCode(() -> groupRepository.createGroupWithFirstMember(group, membership))
            .doesNotThrowAnyException();
        
        // Then - verify both items created atomically
        Optional<Group> savedGroup = groupRepository.findById(group.getGroupId());
        assertThat(savedGroup).isPresent();
        assertThat(savedGroup.get().getGroupName()).isEqualTo(TEST_GROUP_NAME);
        assertThat(savedGroup.get().isPublic()).isTrue();
        
        Optional<GroupMembership> savedMembership = groupRepository.findMembership(
            group.getGroupId(), TEST_USER_ID);
        assertThat(savedMembership).isPresent();
        assertThat(savedMembership.get().getRole()).isEqualTo(GroupRole.ADMIN);
        assertThat(savedMembership.get().getGroupName()).isEqualTo(TEST_GROUP_NAME);
    }
    
    @Test
    @Order(2)
    void findGroupsByUserId_GSIQuery() {
        // When
        List<GroupMembership> result = groupRepository.findGroupsByUserId(TEST_USER_ID);
        
        // Then - verify GSI query returns denormalized data
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getGroupName()).isEqualTo(TEST_GROUP_NAME); // Denormalized
        assertThat(result.get(0).getGsi1pk()).isEqualTo("USER#" + TEST_USER_ID);
        assertThat(result.get(0).getRole()).isEqualTo(GroupRole.ADMIN);
    }
    
    @Test
    @Order(3)
    void addMember_Success() {
        // Given
        String newUserId = "87654321-4321-4321-4321-210987654321";
        GroupMembership membership = new GroupMembership(testGroupId, newUserId, TEST_GROUP_NAME);
        
        // When
        GroupMembership saved = groupRepository.addMember(membership);
        
        // Then
        assertThat(saved.getUserId()).isEqualTo(newUserId);
        assertThat(saved.getRole()).isEqualTo(GroupRole.MEMBER);
        
        // Verify it's findable
        Optional<GroupMembership> found = groupRepository.findMembership(testGroupId, newUserId);
        assertThat(found).isPresent();
    }
    
    @Test
    @Order(4)
    void findMembersByGroupId_ReturnsAllMembers() {
        // When
        List<GroupMembership> members = groupRepository.findMembersByGroupId(testGroupId);
        
        // Then
        assertThat(members).hasSize(2);
        assertThat(members).extracting("userId").containsExactlyInAnyOrder(TEST_USER_ID, "87654321-4321-4321-4321-210987654321");
        assertThat(members).extracting("role").containsExactlyInAnyOrder(GroupRole.ADMIN, GroupRole.MEMBER);
    }
    
    @Test
    @Order(5)
    void saveHangoutPointer_Success() {
        // Given
        String hangoutId = "11111111-1111-1111-1111-111111111111";
        HangoutPointer pointer = new HangoutPointer(testGroupId, hangoutId, "Test Hangout");
        pointer.setStatus("ACTIVE");
        pointer.setParticipantCount(5);
        
        // When
        assertThatCode(() -> groupRepository.saveHangoutPointer(pointer))
            .doesNotThrowAnyException();
        
        // Then - verify it's retrievable
        List<HangoutPointer> hangouts = groupRepository.findHangoutsByGroupId(testGroupId);
        assertThat(hangouts).hasSize(1);
        assertThat(hangouts.get(0).getTitle()).isEqualTo("Test Hangout");
        assertThat(hangouts.get(0).getStatus()).isEqualTo("ACTIVE");
    }
    
    @Test
    @Order(6)
    void removeMember_Success() {
        // Given
        String userToRemove = "87654321-4321-4321-4321-210987654321";
        
        // When
        assertThatCode(() -> groupRepository.removeMember(testGroupId, userToRemove))
            .doesNotThrowAnyException();
        
        // Then
        Optional<GroupMembership> found = groupRepository.findMembership(testGroupId, userToRemove);
        assertThat(found).isEmpty();
        
        // Admin should still be there
        List<GroupMembership> remainingMembers = groupRepository.findMembersByGroupId(testGroupId);
        assertThat(remainingMembers).hasSize(1);
        assertThat(remainingMembers.get(0).getUserId()).isEqualTo(TEST_USER_ID);
    }
    
    @Test
    void findById_NonExistentGroup_ReturnsEmpty() {
        // When
        Optional<Group> result = groupRepository.findById("99999999-9999-9999-9999-999999999999");
        
        // Then
        assertThat(result).isEmpty();
    }
    
    @Test
    void findMembership_NonExistentMembership_ReturnsEmpty() {
        // When
        Optional<GroupMembership> result = groupRepository.findMembership("99999999-9999-9999-9999-999999999999", "88888888-8888-8888-8888-888888888888");
        
        // Then
        assertThat(result).isEmpty();
    }
}