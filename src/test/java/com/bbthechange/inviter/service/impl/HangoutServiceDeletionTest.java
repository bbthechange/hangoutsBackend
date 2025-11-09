package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.HangoutDetailData;
import com.bbthechange.inviter.exception.RepositoryException;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.model.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for hangout deletion functionality in HangoutServiceImpl.
 *
 * Coverage:
 * - Deleting series hangouts (delegates to EventSeriesService)
 * - Deleting standalone hangouts (standard deletion)
 * - Series removal failure handling
 * - Authorization checks on deletion
 * - Group last modified timestamp updates on deletion
 */
class HangoutServiceDeletionTest extends HangoutServiceTestBase {

    @Test
    void deleteHangout_WithHangoutInSeries_UsesSeriesDeletion() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";
        String seriesId = "series-123";

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setSeriesId(seriesId);
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111")));

        // Mock authorization
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Test Group");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership));

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        doNothing().when(eventSeriesService).removeHangoutFromSeries(hangoutId);

        // When
        hangoutService.deleteHangout(hangoutId, userId);

        // Then
        // Verify series removal is called
        verify(eventSeriesService).removeHangoutFromSeries(hangoutId);

        // Verify standard deletion logic is NOT executed
        verify(groupRepository, never()).deleteHangoutPointer(anyString(), anyString());
        verify(hangoutRepository, never()).deleteHangout(anyString());
    }

    @Test
    void deleteHangout_WithStandaloneHangout_UsesStandardDeletion() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setSeriesId(null); // Standalone hangout
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111")));

        // Mock authorization
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Test Group");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership));

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        doNothing().when(groupRepository).deleteHangoutPointer(anyString(), anyString());
        doNothing().when(hangoutRepository).deleteHangout(hangoutId);

        // When
        hangoutService.deleteHangout(hangoutId, userId);

        // Then
        // Verify standard deletion logic executes (pointers deleted, then hangout)
        verify(groupRepository).deleteHangoutPointer("11111111-1111-1111-1111-111111111111", hangoutId);
        verify(hangoutRepository).deleteHangout(hangoutId);

        // Verify series removal is NOT called
        verify(eventSeriesService, never()).removeHangoutFromSeries(anyString());
    }

    @Test
    void deleteHangout_WithSeriesRemovalFailure_ThrowsRepositoryException() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";
        String seriesId = "series-123";

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setSeriesId(seriesId);
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111")));

        // Mock authorization
        GroupMembership membership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Test Group");
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(membership));

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));

        // Mock series service to throw exception
        RuntimeException originalException = new RuntimeException("Series removal failed");
        doThrow(originalException).when(eventSeriesService).removeHangoutFromSeries(hangoutId);

        // When & Then
        assertThatThrownBy(() -> hangoutService.deleteHangout(hangoutId, userId))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("Failed to remove hangout from series during deletion")
            .hasCause(originalException);

        // Verify series removal was attempted
        verify(eventSeriesService).removeHangoutFromSeries(hangoutId);

        // Verify no partial deletion occurs
        verify(groupRepository, never()).deleteHangoutPointer(anyString(), anyString());
        verify(hangoutRepository, never()).deleteHangout(anyString());
    }

    @Test
    void deleteHangout_MaintainsAuthorizationChecks() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";
        String seriesId = "series-123";

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setSeriesId(seriesId);
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111")));

        // Mock authorization failure - user is not in group
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.empty());

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));

        // When & Then
        assertThatThrownBy(() -> hangoutService.deleteHangout(hangoutId, userId))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("Cannot delete hangout");

        // Verify no deletion operations are attempted
        verify(eventSeriesService, never()).removeHangoutFromSeries(anyString());
        verify(groupRepository, never()).deleteHangoutPointer(anyString(), anyString());
        verify(hangoutRepository, never()).deleteHangout(anyString());
    }

    @Test
    void deleteHangout_UpdatesGroupLastModified() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";
        String group1Id = "11111111-1111-1111-1111-111111111111";
        String group2Id = "22222222-2222-2222-2222-222222222222";

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setSeriesId(null); // Standalone hangout
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(group1Id, group2Id)));

        // Mock authorization - user must be in at least one associated group as admin
        GroupMembership membership1 = createTestMembership(group1Id, userId, "Group 1");
        membership1.setRole(GroupRole.ADMIN);
        when(groupRepository.findMembership(group1Id, userId)).thenReturn(Optional.of(membership1));

        when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(hangout));
        doNothing().when(groupRepository).deleteHangoutPointer(anyString(), anyString());
        doNothing().when(hangoutRepository).deleteHangout(hangoutId);

        // Mock groups for timestamp updates
        Group group1 = new Group();
        group1.setGroupId(group1Id);
        group1.setGroupName("Group 1");

        Group group2 = new Group();
        group2.setGroupId(group2Id);
        group2.setGroupName("Group 2");

        when(groupRepository.findById(group1Id)).thenReturn(Optional.of(group1));
        when(groupRepository.findById(group2Id)).thenReturn(Optional.of(group2));
        when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Instant beforeCall = Instant.now().minusSeconds(1);

        // When
        hangoutService.deleteHangout(hangoutId, userId);

        Instant afterCall = Instant.now().plusSeconds(1);

        // Then - Verify timestamps were updated for both groups even though hangout was deleted
        verify(groupRepository).findById(group1Id);
        verify(groupRepository).findById(group2Id);

        ArgumentCaptor<Group> groupCaptor = ArgumentCaptor.forClass(Group.class);
        verify(groupRepository, times(2)).save(groupCaptor.capture());

        List<Group> savedGroups = groupCaptor.getAllValues();
        assertThat(savedGroups).hasSize(2);

        for (Group savedGroup : savedGroups) {
            assertThat(savedGroup.getLastHangoutModified()).isNotNull();
            assertThat(savedGroup.getLastHangoutModified()).isBetween(beforeCall, afterCall);
        }
    }
}
