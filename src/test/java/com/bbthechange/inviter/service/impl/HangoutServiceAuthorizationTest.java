package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.model.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests for authorization logic in HangoutServiceImpl.
 *
 * Covers:
 * - canUserViewHangout: public visibility, invite-only with group membership, no access cases
 * - canUserEditHangout: group membership validation, multiple groups, no access cases
 * - Edge cases: null associated groups, empty groups, non-existent memberships
 */
class HangoutServiceAuthorizationTest extends HangoutServiceTestBase {

    @Nested
    class CanUserViewHangoutTests {

        @Test
        void canUserViewHangout_PublicHangout_ReturnsTrue() {
            // Given
            String userId = "87654321-4321-4321-4321-210987654321";
            Hangout hangout = createTestHangout("hangout-1");
            hangout.setVisibility(EventVisibility.PUBLIC);
            hangout.setAssociatedGroups(List.of("11111111-1111-1111-1111-111111111111"));

            // When
            boolean result = hangoutService.canUserViewHangout(userId, hangout);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        void canUserViewHangout_InviteOnlyWithUserInGroup_ReturnsTrue() {
            // Given
            String userId = "87654321-4321-4321-4321-210987654321";
            String groupId = "11111111-1111-1111-1111-111111111111";

            Hangout hangout = createTestHangout("hangout-1");
            hangout.setVisibility(EventVisibility.INVITE_ONLY);
            hangout.setAssociatedGroups(List.of(groupId));

            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));

            // When
            boolean result = hangoutService.canUserViewHangout(userId, hangout);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        void canUserViewHangout_InviteOnlyWithUserInOneOfMultipleGroups_ReturnsTrue() {
            // Given
            String userId = "87654321-4321-4321-4321-210987654321";
            String groupId1 = "11111111-1111-1111-1111-111111111111";
            String groupId2 = "22222222-2222-2222-2222-222222222222";
            String groupId3 = "33333333-3333-3333-3333-333333333333";

            Hangout hangout = createTestHangout("hangout-1");
            hangout.setVisibility(EventVisibility.INVITE_ONLY);
            hangout.setAssociatedGroups(List.of(groupId1, groupId2, groupId3));

            // User is only in group 2
            when(groupRepository.findMembership(groupId1, userId)).thenReturn(Optional.empty());

            GroupMembership membership2 = createTestMembership(groupId2, userId, "Test Group 2");
            when(groupRepository.findMembership(groupId2, userId)).thenReturn(Optional.of(membership2));

            // When
            boolean result = hangoutService.canUserViewHangout(userId, hangout);

            // Then - Should return true because user is in at least one group
            assertThat(result).isTrue();
        }

        @Test
        void canUserViewHangout_InviteOnlyWithUserNotInAnyGroup_ReturnsFalse() {
            // Given
            String userId = "87654321-4321-4321-4321-210987654321";
            String groupId = "11111111-1111-1111-1111-111111111111";

            Hangout hangout = createTestHangout("hangout-1");
            hangout.setVisibility(EventVisibility.INVITE_ONLY);
            hangout.setAssociatedGroups(List.of(groupId));

            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.empty());

            // When
            boolean result = hangoutService.canUserViewHangout(userId, hangout);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        void canUserViewHangout_InviteOnlyWithNullAssociatedGroups_ReturnsFalse() {
            // Given
            String userId = "87654321-4321-4321-4321-210987654321";

            Hangout hangout = createTestHangout("hangout-1");
            hangout.setVisibility(EventVisibility.INVITE_ONLY);
            hangout.setAssociatedGroups(null);

            // When
            boolean result = hangoutService.canUserViewHangout(userId, hangout);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        void canUserViewHangout_InviteOnlyWithEmptyAssociatedGroups_ReturnsFalse() {
            // Given
            String userId = "87654321-4321-4321-4321-210987654321";

            Hangout hangout = createTestHangout("hangout-1");
            hangout.setVisibility(EventVisibility.INVITE_ONLY);
            hangout.setAssociatedGroups(List.of());

            // When
            boolean result = hangoutService.canUserViewHangout(userId, hangout);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        void canUserViewHangout_PublicWithNoAssociatedGroups_ReturnsTrue() {
            // Given
            String userId = "87654321-4321-4321-4321-210987654321";

            Hangout hangout = createTestHangout("hangout-1");
            hangout.setVisibility(EventVisibility.PUBLIC);
            hangout.setAssociatedGroups(null);

            // When
            boolean result = hangoutService.canUserViewHangout(userId, hangout);

            // Then - Public hangouts are always viewable
            assertThat(result).isTrue();
        }
    }

    @Nested
    class CanUserEditHangoutTests {

        @Test
        void canUserEditHangout_UserInAssociatedGroup_ReturnsTrue() {
            // Given
            String userId = "87654321-4321-4321-4321-210987654321";
            String groupId = "11111111-1111-1111-1111-111111111111";

            Hangout hangout = createTestHangout("hangout-1");
            hangout.setAssociatedGroups(List.of(groupId));

            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));

            // When
            boolean result = hangoutService.canUserEditHangout(userId, hangout);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        void canUserEditHangout_UserInOneOfMultipleGroups_ReturnsTrue() {
            // Given
            String userId = "87654321-4321-4321-4321-210987654321";
            String groupId1 = "11111111-1111-1111-1111-111111111111";
            String groupId2 = "22222222-2222-2222-2222-222222222222";
            String groupId3 = "33333333-3333-3333-3333-333333333333";

            Hangout hangout = createTestHangout("hangout-1");
            hangout.setAssociatedGroups(List.of(groupId1, groupId2, groupId3));

            // User is only in group 3
            when(groupRepository.findMembership(groupId1, userId)).thenReturn(Optional.empty());
            when(groupRepository.findMembership(groupId2, userId)).thenReturn(Optional.empty());

            GroupMembership membership3 = createTestMembership(groupId3, userId, "Test Group 3");
            when(groupRepository.findMembership(groupId3, userId)).thenReturn(Optional.of(membership3));

            // When
            boolean result = hangoutService.canUserEditHangout(userId, hangout);

            // Then - Should return true because user is in at least one group
            assertThat(result).isTrue();
        }

        @Test
        void canUserEditHangout_UserNotInAnyGroup_ReturnsFalse() {
            // Given
            String userId = "87654321-4321-4321-4321-210987654321";
            String groupId = "11111111-1111-1111-1111-111111111111";

            Hangout hangout = createTestHangout("hangout-1");
            hangout.setAssociatedGroups(List.of(groupId));

            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.empty());

            // When
            boolean result = hangoutService.canUserEditHangout(userId, hangout);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        void canUserEditHangout_WithNullAssociatedGroups_ReturnsFalse() {
            // Given
            String userId = "87654321-4321-4321-4321-210987654321";

            Hangout hangout = createTestHangout("hangout-1");
            hangout.setAssociatedGroups(null);

            // When
            boolean result = hangoutService.canUserEditHangout(userId, hangout);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        void canUserEditHangout_WithEmptyAssociatedGroups_ReturnsFalse() {
            // Given
            String userId = "87654321-4321-4321-4321-210987654321";

            Hangout hangout = createTestHangout("hangout-1");
            hangout.setAssociatedGroups(List.of());

            // When
            boolean result = hangoutService.canUserEditHangout(userId, hangout);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        void canUserEditHangout_UserIsMember_ReturnsTrue() {
            // Given
            String userId = "87654321-4321-4321-4321-210987654321";
            String groupId = "11111111-1111-1111-1111-111111111111";

            Hangout hangout = createTestHangout("hangout-1");
            hangout.setAssociatedGroups(List.of(groupId));

            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
            membership.setRole(GroupRole.MEMBER); // Explicitly set to MEMBER
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));

            // When
            boolean result = hangoutService.canUserEditHangout(userId, hangout);

            // Then - Any group member can edit (not just admins)
            assertThat(result).isTrue();
        }

        @Test
        void canUserEditHangout_UserIsAdmin_ReturnsTrue() {
            // Given
            String userId = "87654321-4321-4321-4321-210987654321";
            String groupId = "11111111-1111-1111-1111-111111111111";

            Hangout hangout = createTestHangout("hangout-1");
            hangout.setAssociatedGroups(List.of(groupId));

            GroupMembership membership = createTestMembership(groupId, userId, "Test Group");
            membership.setRole(GroupRole.ADMIN);
            when(groupRepository.findMembership(groupId, userId)).thenReturn(Optional.of(membership));

            // When
            boolean result = hangoutService.canUserEditHangout(userId, hangout);

            // Then
            assertThat(result).isTrue();
        }
    }
}
