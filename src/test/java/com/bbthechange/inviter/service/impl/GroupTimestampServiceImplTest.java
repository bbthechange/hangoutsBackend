package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.model.Group;
import com.bbthechange.inviter.repository.GroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupTimestampServiceImplTest {

    @Mock
    private GroupRepository groupRepository;

    @InjectMocks
    private GroupTimestampServiceImpl groupTimestampService;

    private Group testGroup;
    private static final String GROUP_ID = "group-123";

    @BeforeEach
    void setUp() {
        testGroup = new Group();
        testGroup.setGroupId(GROUP_ID);
        testGroup.setGroupName("Test Group");
    }

    @Test
    void updateGroupTimestamps_WithValidGroups_UpdatesLastHangoutModified() {
        // Given
        List<String> groupIds = Arrays.asList(GROUP_ID);
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));

        Instant beforeCall = Instant.now();

        // When
        groupTimestampService.updateGroupTimestamps(groupIds);

        Instant afterCall = Instant.now();

        // Then
        ArgumentCaptor<Group> groupCaptor = ArgumentCaptor.forClass(Group.class);
        verify(groupRepository).save(groupCaptor.capture());

        Group savedGroup = groupCaptor.getValue();
        assertThat(savedGroup.getLastHangoutModified()).isNotNull();
        assertThat(savedGroup.getLastHangoutModified()).isBetween(beforeCall, afterCall);
    }

    @Test
    void updateGroupTimestamps_WithMultipleGroups_UpdatesAll() {
        // Given
        String groupId2 = "group-456";
        Group group2 = new Group();
        group2.setGroupId(groupId2);

        List<String> groupIds = Arrays.asList(GROUP_ID, groupId2);
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(testGroup));
        when(groupRepository.findById(groupId2)).thenReturn(Optional.of(group2));

        // When
        groupTimestampService.updateGroupTimestamps(groupIds);

        // Then
        verify(groupRepository, times(2)).save(any(Group.class));
    }

    @Test
    void updateGroupTimestamps_WithNullList_DoesNothing() {
        // When
        groupTimestampService.updateGroupTimestamps(null);

        // Then
        verify(groupRepository, never()).findById(any());
        verify(groupRepository, never()).save(any());
    }

    @Test
    void updateGroupTimestamps_WithEmptyList_DoesNothing() {
        // When
        groupTimestampService.updateGroupTimestamps(Arrays.asList());

        // Then
        verify(groupRepository, never()).findById(any());
        verify(groupRepository, never()).save(any());
    }

    @Test
    void updateGroupTimestamps_WithNonExistentGroup_ContinuesWithOthers() {
        // Given
        String validGroupId = "group-valid";
        String invalidGroupId = "group-invalid";
        Group validGroup = new Group();
        validGroup.setGroupId(validGroupId);

        List<String> groupIds = Arrays.asList(invalidGroupId, validGroupId);
        when(groupRepository.findById(invalidGroupId)).thenReturn(Optional.empty());
        when(groupRepository.findById(validGroupId)).thenReturn(Optional.of(validGroup));

        // When
        groupTimestampService.updateGroupTimestamps(groupIds);

        // Then
        verify(groupRepository, times(1)).save(any(Group.class)); // Only valid group saved
        ArgumentCaptor<Group> groupCaptor = ArgumentCaptor.forClass(Group.class);
        verify(groupRepository).save(groupCaptor.capture());
        assertThat(groupCaptor.getValue().getGroupId()).isEqualTo(validGroupId);
    }
}
