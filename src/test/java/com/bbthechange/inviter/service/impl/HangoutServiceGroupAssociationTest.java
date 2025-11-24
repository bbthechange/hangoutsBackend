package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.HangoutDetailData;
import com.bbthechange.inviter.dto.TimeInfo;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.service.FuzzyTimeService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for hangout group association operations.
 *
 * Covers:
 * - Associating hangouts with groups
 * - Creating pointers with proper GSI fields
 * - Denormalizing interest levels to pointers
 * - Authorization checks for group membership
 * - Disassociating hangouts from groups
 */
class HangoutServiceGroupAssociationTest extends HangoutServiceTestBase {

    @Test
    void associateEventWithGroups_Success() {
        // Given
        String eventId = "12345678-1234-1234-1234-123456789012";
        List<String> groupIds = List.of("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222");
        String userId = "87654321-4321-4321-4321-210987654321";

        // Use hangout with timeInfo to test timeInfo propagation to pointer
        Hangout hangout = createTestHangoutWithTimeInput(eventId);
        hangout.setStartTimestamp(1754558100L); // Set timestamps that would come from fuzzy time service
        hangout.setEndTimestamp(1754566200L);
        // Set up hangout so user can edit it (they're admin in one of the groups they want to associate)
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111")));

        HangoutDetailData data = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(data);

        // Mock fuzzy time service conversion
        FuzzyTimeService.TimeConversionResult timeResult = new FuzzyTimeService.TimeConversionResult(1754558100L, 1754566200L);
        when(fuzzyTimeService.convert(any(TimeInfo.class))).thenReturn(timeResult);

        // Mock authorization - user is admin in first group and member of second
        GroupMembership adminMembership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group One");
        adminMembership.setRole(GroupRole.ADMIN);
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(
            Optional.of(adminMembership));
        when(groupRepository.findMembership("22222222-2222-2222-2222-222222222222", userId)).thenReturn(
            Optional.of(createTestMembership("22222222-2222-2222-2222-222222222222", userId, "Group Two")));

        when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(hangout);

        // When
        assertThatCode(() -> hangoutService.associateEventWithGroups(eventId, groupIds, userId))
            .doesNotThrowAnyException();

        // Then
        verify(hangoutRepository).createHangout(any(Hangout.class)); // Update canonical record
        verify(groupRepository, times(2)).saveHangoutPointer(any(HangoutPointer.class)); // Create pointers

        // Verify that pointers are created with timeInfo properly set
        verify(groupRepository, times(2)).saveHangoutPointer(argThat(pointer ->
            pointer.getTimeInput() != null &&
            pointer.getStartTimestamp() != null &&
            pointer.getStartTimestamp().equals(1754558100L)
        ));
    }

    @Test
    void associateEventWithGroups_UserNotInGroup_ThrowsException() {
        // Given
        String eventId = "12345678-1234-1234-1234-123456789012";
        List<String> groupIds = List.of("11111111-1111-1111-1111-111111111111");
        String userId = "87654321-4321-4321-4321-210987654321";

        Hangout hangout = createTestHangout(eventId);
        // Set up hangout so user can edit it (they're admin in a different group)
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("22222222-2222-2222-2222-222222222222")));

        HangoutDetailData data = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(data);

        // User is admin in existing associated group (so they can edit hangout)
        GroupMembership adminMembership = createTestMembership("22222222-2222-2222-2222-222222222222", userId, "Existing Group");
        adminMembership.setRole(GroupRole.ADMIN);
        when(groupRepository.findMembership("22222222-2222-2222-2222-222222222222", userId)).thenReturn(Optional.of(adminMembership));

        // But user is NOT in the group they're trying to associate with
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> hangoutService.associateEventWithGroups(eventId, groupIds, userId))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("User not in group");

        verify(hangoutRepository, never()).createHangout(any());
        verify(groupRepository, never()).saveHangoutPointer(any());
    }

    @Test
    void disassociateEventFromGroups_Success() {
        // Given
        String eventId = "12345678-1234-1234-1234-123456789012";
        List<String> groupIds = List.of("11111111-1111-1111-1111-111111111111");
        String userId = "87654321-4321-4321-4321-210987654321";

        Hangout hangout = createTestHangout(eventId);
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222")));

        HangoutDetailData data = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(data);

        // Mock authorization - user is admin in group-1
        GroupMembership adminMembership = createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group One");
        adminMembership.setRole(GroupRole.ADMIN);
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(Optional.of(adminMembership));

        when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(hangout);
        doNothing().when(groupRepository).deleteHangoutPointer(anyString(), anyString());

        // When
        assertThatCode(() -> hangoutService.disassociateEventFromGroups(eventId, groupIds, userId))
            .doesNotThrowAnyException();

        // Then
        verify(hangoutRepository).createHangout(any(Hangout.class)); // Update canonical record
        verify(groupRepository).deleteHangoutPointer("11111111-1111-1111-1111-111111111111", eventId); // Delete pointer
    }

    @Test
    void associateEventWithGroups_WithTimestamps_CreatesPointersWithGSIFields() {
        // Given
        String eventId = "12345678-1234-1234-1234-123456789012";
        List<String> groupIds = List.of("11111111-1111-1111-1111-111111111111");
        String userId = "87654321-4321-4321-4321-210987654321";

        // Use hangout with timeInput so GSI fields get set properly
        Hangout hangout = createTestHangoutWithTimeInput(eventId);
        hangout.setStartTimestamp(1754557200L); // Set timestamp that would come from fuzzy service
        hangout.setEndTimestamp(1754571600L);
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of("22222222-2222-2222-2222-222222222222")));

        HangoutDetailData data = new HangoutDetailData(hangout, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        when(hangoutRepository.getHangoutDetailData(eventId)).thenReturn(data);

        // Mock fuzzy time service conversion
        FuzzyTimeService.TimeConversionResult timeResult = new FuzzyTimeService.TimeConversionResult(1754557200L, 1754571600L);
        when(fuzzyTimeService.convert(any(TimeInfo.class))).thenReturn(timeResult);

        // Mock authorization
        GroupMembership adminMembership = createTestMembership("22222222-2222-2222-2222-222222222222", userId, "Existing Group");
        adminMembership.setRole(GroupRole.ADMIN);
        when(groupRepository.findMembership("22222222-2222-2222-2222-222222222222", userId)).thenReturn(Optional.of(adminMembership));
        when(groupRepository.findMembership("11111111-1111-1111-1111-111111111111", userId)).thenReturn(
            Optional.of(createTestMembership("11111111-1111-1111-1111-111111111111", userId, "New Group")));

        when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(hangout);

        // When
        assertThatCode(() -> hangoutService.associateEventWithGroups(eventId, groupIds, userId))
            .doesNotThrowAnyException();

        // Then
        verify(groupRepository).saveHangoutPointer(argThat(pointer ->
            pointer.getGsi1pk().equals("GROUP#11111111-1111-1111-1111-111111111111") &&
            pointer.getStartTimestamp().equals(1754557200L) &&
            pointer.getTimeInput() != null // Verify timeInput was set
        ));
    }

    @Test
    void associateEventWithGroups_WithExistingInterestLevels_DenormalizesToPointer() {
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String groupId = "11111111-1111-1111-1111-111111111111";
        String userId = "87654321-4321-4321-4321-210987654321";

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setVisibility(EventVisibility.PUBLIC);
        // Set up hangout so user can edit it (they're admin in a group it's already associated with)
        hangout.setAssociatedGroups(new java.util.ArrayList<>(List.of(groupId)));

        // Create interest levels that should be denormalized to the pointer
        String userId1 = java.util.UUID.randomUUID().toString();
        String userId2 = java.util.UUID.randomUUID().toString();

        InterestLevel interest1 = new InterestLevel(hangoutId, userId1, "User One", "GOING");
        InterestLevel interest2 = new InterestLevel(hangoutId, userId2, "User Two", "INTERESTED");

        HangoutDetailData data = new HangoutDetailData(
            hangout, List.of(), List.of(), List.of(), List.of(), List.of(interest1, interest2), List.of(), List.of(), List.of(), List.of()
        );
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);

        GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
        membership.setRole(GroupRole.ADMIN);
        when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));

        // When
        hangoutService.associateEventWithGroups(hangoutId, List.of(groupId), userId);

        // Then
        verify(groupRepository).saveHangoutPointer(argThat((HangoutPointer pointer) ->
            pointer.getInterestLevels() != null &&
            pointer.getInterestLevels().size() == 2 &&
            pointer.getInterestLevels().get(0).getUserId().equals(userId1) &&
            pointer.getInterestLevels().get(1).getUserId().equals(userId2)
        ));
    }
}
