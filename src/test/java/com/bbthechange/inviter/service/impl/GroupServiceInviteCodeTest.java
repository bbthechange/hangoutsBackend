package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.GroupDTO;
import com.bbthechange.inviter.dto.GroupPreviewDTO;
import com.bbthechange.inviter.dto.InviteCodeResponse;
import com.bbthechange.inviter.exception.ResourceNotFoundException;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.exception.UserNotFoundException;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.repository.InviteCodeRepository;
import com.bbthechange.inviter.repository.UserRepository;
import com.bbthechange.inviter.service.InviteService;
import com.bbthechange.inviter.service.NotificationService;
import com.bbthechange.inviter.service.S3Service;
import com.bbthechange.inviter.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GroupServiceImpl invite code functionality.
 * Tests invite code generation, preview, and joining logic.
 */
@ExtendWith(MockitoExtension.class)
class GroupServiceInviteCodeTest {

    private static final String GROUP_ID = "12345678-1234-1234-1234-123456789012";
    private static final String USER_ID = "87654321-4321-4321-4321-210987654321";
    private static final String GROUP_NAME = "Test Group";
    private static final String INVITE_CODE = "abc123xy";
    private static final String APP_BASE_URL = "https://example.com";

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private HangoutRepository hangoutRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    @Mock
    private InviteService inviteService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private S3Service s3Service;

    @Mock
    private InviteCodeRepository inviteCodeRepository;

    private GroupServiceImpl groupService;

    @BeforeEach
    void setUp() {
        groupService = new GroupServiceImpl(
            groupRepository,
            hangoutRepository,
            userRepository,
            userService,
            inviteService,
            notificationService,
            s3Service,
            inviteCodeRepository,
            APP_BASE_URL
        );
    }

    // Helper methods
    private Group createTestGroup(boolean isPublic) {
        Group group = new Group(GROUP_NAME, isPublic);
        group.setGroupId(GROUP_ID);
        group.setMainImagePath("images/group.jpg");
        return group;
    }

    private InviteCode createValidInviteCode() {
        InviteCode code = new InviteCode(GROUP_ID, INVITE_CODE, USER_ID, GROUP_NAME);
        return code;
    }

    private InviteCode createExpiredInviteCode() {
        InviteCode code = createValidInviteCode();
        code.setExpiresAt(Instant.now().minusSeconds(3600));
        return code;
    }

    private InviteCode createSingleUseInviteCode() {
        InviteCode code = createValidInviteCode();
        code.setSingleUse(true);
        return code;
    }

    private User createTestUser() {
        User user = new User();
        user.setId(UUID.fromString(USER_ID));
        user.setPhoneNumber("+12345678900");
        user.setUsername("testuser");
        user.setMainImagePath("images/user.jpg");
        return user;
    }

    private GroupMembership createTestMembership() {
        GroupMembership membership = new GroupMembership(GROUP_ID, USER_ID, GROUP_NAME);
        membership.setRole(GroupRole.MEMBER);
        return membership;
    }

    @Nested
    class GenerateInviteCodeTests {

        @Test
        void generateInviteCode_WhenNoActiveCode_CreatesNewCode() {
            // Given
            Group group = createTestGroup(true);
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(inviteCodeRepository.findActiveCodeForGroup(GROUP_ID)).thenReturn(Optional.empty());
            when(inviteCodeRepository.codeExists(anyString())).thenReturn(false);

            // When
            InviteCodeResponse response = groupService.generateInviteCode(GROUP_ID, USER_ID);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getInviteCode()).isNotNull().hasSize(8);
            assertThat(response.getShareUrl()).contains(response.getInviteCode());
            verify(inviteCodeRepository).save(any(InviteCode.class));
        }

        @Test
        void generateInviteCode_WithExistingActiveCode_ReturnsExistingCode() {
            // Given
            Group group = createTestGroup(true);
            InviteCode existingCode = createValidInviteCode();
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(inviteCodeRepository.findActiveCodeForGroup(GROUP_ID)).thenReturn(Optional.of(existingCode));

            // When
            InviteCodeResponse response = groupService.generateInviteCode(GROUP_ID, USER_ID);

            // Then
            assertThat(response.getInviteCode()).isEqualTo(INVITE_CODE);
            assertThat(response.getShareUrl()).contains(INVITE_CODE);
            verify(inviteCodeRepository, never()).save(any(InviteCode.class));
        }

        @Test
        void generateInviteCode_WhenGroupNotFound_ThrowsException() {
            // Given
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> groupService.generateInviteCode(GROUP_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Group not found");

            verify(inviteCodeRepository, never()).save(any());
        }

        @Test
        void generateInviteCode_WhenUserNotMember_ThrowsException() {
            // Given
            Group group = createTestGroup(true);
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> groupService.generateInviteCode(GROUP_ID, USER_ID))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("members can generate invite codes");

            verify(inviteCodeRepository, never()).save(any());
        }

        @Test
        void generateInviteCode_AfterPreviousCodeDeactivated_CreatesNewCode() {
            // Given
            Group group = createTestGroup(true);
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(inviteCodeRepository.findActiveCodeForGroup(GROUP_ID)).thenReturn(Optional.empty());
            when(inviteCodeRepository.codeExists(anyString())).thenReturn(false);

            // When
            InviteCodeResponse response = groupService.generateInviteCode(GROUP_ID, USER_ID);

            // Then
            assertThat(response.getInviteCode()).isNotNull();
            verify(inviteCodeRepository).save(any(InviteCode.class));
        }
    }

    @Nested
    class GetGroupPreviewByInviteCodeTests {

        @Test
        void getGroupPreviewByInviteCode_ForPublicGroup_ReturnsPreviewWithDetails() {
            // Given
            InviteCode code = createValidInviteCode();
            Group group = createTestGroup(true);
            when(inviteCodeRepository.findByCode(INVITE_CODE)).thenReturn(Optional.of(code));
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

            // When
            GroupPreviewDTO preview = groupService.getGroupPreviewByInviteCode(INVITE_CODE);

            // Then
            assertThat(preview.isPrivate()).isFalse();
            assertThat(preview.getGroupName()).isEqualTo(GROUP_NAME);
            assertThat(preview.getMainImagePath()).isNotNull();
        }

        @Test
        void getGroupPreviewByInviteCode_ForPrivateGroup_ReturnsPreviewWithoutDetails() {
            // Given
            InviteCode code = createValidInviteCode();
            Group group = createTestGroup(false); // Private group
            when(inviteCodeRepository.findByCode(INVITE_CODE)).thenReturn(Optional.of(code));
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

            // When
            GroupPreviewDTO preview = groupService.getGroupPreviewByInviteCode(INVITE_CODE);

            // Then
            assertThat(preview.isPrivate()).isTrue();
            assertThat(preview.getGroupName()).isNull();
            assertThat(preview.getMainImagePath()).isNull();
        }

        @Test
        void getGroupPreviewByInviteCode_WithInvalidCode_ThrowsException() {
            // Given
            when(inviteCodeRepository.findByCode("invalid99")).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> groupService.getGroupPreviewByInviteCode("invalid99"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Invalid invite code");
        }

        @Test
        void getGroupPreviewByInviteCode_WhenGroupNotFound_ThrowsException() {
            // Given
            InviteCode code = createValidInviteCode();
            when(inviteCodeRepository.findByCode(INVITE_CODE)).thenReturn(Optional.of(code));
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> groupService.getGroupPreviewByInviteCode(INVITE_CODE))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Group not found");
        }
    }

    @Nested
    class JoinGroupByInviteCodeTests {

        @Test
        void joinGroupByInviteCode_WithValidCode_CreatesNewMembership() {
            // Given
            InviteCode code = createValidInviteCode();
            Group group = createTestGroup(true);
            User user = createTestUser();

            when(inviteCodeRepository.findByCode(INVITE_CODE)).thenReturn(Optional.of(code));
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(false);
            when(userRepository.findById(UUID.fromString(USER_ID))).thenReturn(Optional.of(user));
            when(groupRepository.addMember(any(GroupMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));
            doNothing().when(inviteCodeRepository).save(any(InviteCode.class));

            // When
            GroupDTO result = groupService.joinGroupByInviteCode(INVITE_CODE, USER_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getGroupId()).isEqualTo(GROUP_ID);
            assertThat(result.getUserRole()).isEqualTo(GroupRole.MEMBER.toString());
            verify(groupRepository).addMember(any(GroupMembership.class));
            verify(inviteCodeRepository).save(any(InviteCode.class));
            // Verify the code was mutated to include the usage
            assertThat(code.getUsages()).contains(USER_ID);
        }

        @Test
        void joinGroupByInviteCode_WhenAlreadyMember_ReturnsExistingMembership() {
            // Given
            InviteCode code = createValidInviteCode();
            Group group = createTestGroup(true);
            GroupMembership existingMembership = createTestMembership();

            when(inviteCodeRepository.findByCode(INVITE_CODE)).thenReturn(Optional.of(code));
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(groupRepository.findMembership(GROUP_ID, USER_ID)).thenReturn(Optional.of(existingMembership));

            // When
            GroupDTO result = groupService.joinGroupByInviteCode(INVITE_CODE, USER_ID);

            // Then
            assertThat(result).isNotNull();
            verify(groupRepository, never()).addMember(any());
            verify(inviteCodeRepository, never()).save(any());
        }

        @Test
        void joinGroupByInviteCode_WithInvalidCode_ThrowsException() {
            // Given
            when(inviteCodeRepository.findByCode("invalid99")).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> groupService.joinGroupByInviteCode("invalid99", USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Invalid invite code");

            verify(groupRepository, never()).addMember(any());
        }

        @Test
        void joinGroupByInviteCode_WithUnusableCode_ThrowsException() {
            // Given
            InviteCode expiredCode = createExpiredInviteCode();
            when(inviteCodeRepository.findByCode(INVITE_CODE)).thenReturn(Optional.of(expiredCode));

            // When/Then
            assertThatThrownBy(() -> groupService.joinGroupByInviteCode(INVITE_CODE, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("no longer valid");

            verify(groupRepository, never()).addMember(any());
        }

        @Test
        void joinGroupByInviteCode_WhenGroupNotFound_ThrowsException() {
            // Given
            InviteCode code = createValidInviteCode();
            when(inviteCodeRepository.findByCode(INVITE_CODE)).thenReturn(Optional.of(code));
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> groupService.joinGroupByInviteCode(INVITE_CODE, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Group not found");

            verify(groupRepository, never()).addMember(any());
        }

        @Test
        void joinGroupByInviteCode_WhenUserNotFound_ThrowsException() {
            // Given
            InviteCode code = createValidInviteCode();
            Group group = createTestGroup(true);

            when(inviteCodeRepository.findByCode(INVITE_CODE)).thenReturn(Optional.of(code));
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(false);
            when(userRepository.findById(UUID.fromString(USER_ID))).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> groupService.joinGroupByInviteCode(INVITE_CODE, USER_ID))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("User not found");

            verify(groupRepository, never()).addMember(any());
        }

        @Test
        void joinGroupByInviteCode_WithSingleUseCode_RecordsUsageAndDeactivates() {
            // Given
            InviteCode code = createSingleUseInviteCode();
            Group group = createTestGroup(true);
            User user = createTestUser();

            when(inviteCodeRepository.findByCode(INVITE_CODE)).thenReturn(Optional.of(code));
            when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(false);
            when(userRepository.findById(UUID.fromString(USER_ID))).thenReturn(Optional.of(user));
            when(groupRepository.addMember(any(GroupMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));
            doNothing().when(inviteCodeRepository).save(any(InviteCode.class));

            // When
            GroupDTO result = groupService.joinGroupByInviteCode(INVITE_CODE, USER_ID);

            // Then
            assertThat(result).isNotNull();
            verify(inviteCodeRepository).save(any(InviteCode.class));
            // Verify the code was mutated - usage recorded and deactivated
            assertThat(code.getUsages()).contains(USER_ID);
            assertThat(code.isActive()).isFalse();
        }
    }
}
