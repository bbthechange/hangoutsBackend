package com.bbthechange.inviter.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests to verify that all BaseItem subclasses properly set their itemType.
 */
class BaseItemTypeTest {
    
    @Test
    void group_SetsItemTypeInDefaultConstructor() {
        // When
        Group group = new Group();
        
        // Then
        assertThat(group.getItemType()).isEqualTo("GROUP");
    }
    
    @Test
    void group_SetsItemTypeInParameterizedConstructor() {
        // When
        Group group = new Group("Test Group", true);
        
        // Then
        assertThat(group.getItemType()).isEqualTo("GROUP");
        assertThat(group.getGroupName()).isEqualTo("Test Group");
        assertThat(group.isPublic()).isTrue();
    }
    
    @Test
    void groupMembership_SetsItemTypeInDefaultConstructor() {
        // When
        GroupMembership membership = new GroupMembership();
        
        // Then
        assertThat(membership.getItemType()).isEqualTo("GROUP_MEMBERSHIP");
    }
    
    @Test
    void groupMembership_SetsItemTypeInParameterizedConstructor() {
        // Given
        String groupId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        
        // When
        GroupMembership membership = new GroupMembership(groupId, userId, "Group Name");
        
        // Then
        assertThat(membership.getItemType()).isEqualTo("GROUP_MEMBERSHIP");
        assertThat(membership.getGroupId()).isEqualTo(groupId);
        assertThat(membership.getUserId()).isEqualTo(userId);
        assertThat(membership.getGroupName()).isEqualTo("Group Name");
    }
    
    @Test
    void hangoutPointer_SetsItemTypeInDefaultConstructor() {
        // When
        HangoutPointer pointer = new HangoutPointer();
        
        // Then
        assertThat(pointer.getItemType()).isEqualTo("HANGOUT_POINTER");
    }
    
    @Test
    void hangoutPointer_SetsItemTypeInParameterizedConstructor() {
        // Given
        String groupId = UUID.randomUUID().toString();
        String hangoutId = UUID.randomUUID().toString();
        
        // When
        HangoutPointer pointer = new HangoutPointer(groupId, hangoutId, "Hangout Title");
        
        // Then
        assertThat(pointer.getItemType()).isEqualTo("HANGOUT_POINTER");
        assertThat(pointer.getGroupId()).isEqualTo(groupId);
        assertThat(pointer.getHangoutId()).isEqualTo(hangoutId);
        assertThat(pointer.getTitle()).isEqualTo("Hangout Title");
    }
    
    @Test
    void itemType_CanBeSetAndRetrieved() {
        // Given
        Group group = new Group();
        
        // When
        group.setItemType("CUSTOM_TYPE");
        
        // Then
        assertThat(group.getItemType()).isEqualTo("CUSTOM_TYPE");
    }
    
    @Test
    void baseItem_TouchUpdatesTimestamp() throws InterruptedException {
        // Given
        Group group = new Group("Test", false);
        Thread.sleep(10); // Ensure time passes
        
        // When
        group.touch();
        
        // Then
        assertThat(group.getUpdatedAt()).isAfter(group.getCreatedAt());
    }
}