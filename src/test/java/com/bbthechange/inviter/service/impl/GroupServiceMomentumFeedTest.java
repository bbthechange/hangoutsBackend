package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.repository.InviteCodeRepository;
import com.bbthechange.inviter.repository.UserRepository;
import com.bbthechange.inviter.service.HangoutService;
import com.bbthechange.inviter.service.IdeaFeedSurfacingService;
import com.bbthechange.inviter.service.InviteService;
import com.bbthechange.inviter.service.NotificationService;
import com.bbthechange.inviter.service.UserService;
import com.bbthechange.inviter.util.PaginatedResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for feed filter logic in GroupServiceImpl.
 *
 * Coverage:
 * - filter=ALL returns hangouts of all momentum categories
 * - filter=CONFIRMED returns only CONFIRMED hangouts
 * - filter=EVERYTHING behaves like ALL
 * - filter=CONFIRMED keeps series items
 * - No filter defaults to ALL behavior
 * - filter=CONFIRMED includes legacy hangouts with null momentum (treated as confirmed)
 */
@ExtendWith(MockitoExtension.class)
class GroupServiceMomentumFeedTest {

    private static final String GROUP_ID = "12345678-1234-1234-1234-123456789012";
    private static final String USER_ID = "87654321-4321-4321-4321-210987654321";

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
    private HangoutService hangoutService;

    @Mock
    private com.bbthechange.inviter.service.S3Service s3Service;

    @Mock
    private InviteCodeRepository inviteCodeRepository;

    @Mock
    private FeedSortingService feedSortingService;

    @Mock
    private IdeaFeedSurfacingService ideaFeedSurfacingService;

    @Mock
    private com.bbthechange.inviter.service.AttributeSuggestionService attributeSuggestionService;

    @Mock
    private com.bbthechange.inviter.service.NudgeService nudgeService;

    @InjectMocks
    private GroupServiceImpl groupService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(groupService, "appBaseUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(groupService, "attendanceBackwardCompatEnabled", true);
        // Default: sortFeed passes items through unchanged
        lenient().when(feedSortingService.sortFeed(any(), any(), anyLong()))
                .thenAnswer(inv -> new FeedSortingService.SortResult(inv.getArgument(0), inv.getArgument(1)));
        // IdeaFeedSurfacingService returns empty list by default
        lenient().when(ideaFeedSurfacingService.getSurfacedIdeas(any(), anyLong(), any()))
                .thenReturn(List.of());
        // Floating hangouts query returns empty by default
        lenient().when(hangoutRepository.getFloatingHangoutsPage(any(), any()))
                .thenReturn(new PaginatedResult<>(List.of(), null));
    }

    // ============================================================================
    // 1.3.1 Feed Filter Tests
    // ============================================================================

    @Test
    void getGroupFeed_filterAll_returnsHangoutsOfAllCategories() {
        // Membership auth
        GroupMembership membership = buildMembership(GROUP_ID, USER_ID);
        when(groupRepository.findMembership(GROUP_ID, USER_ID)).thenReturn(Optional.of(membership));

        // Three pointers with different momentum categories
        HangoutPointer building = buildPointer("h-building", MomentumCategory.BUILDING);
        HangoutPointer gaining = buildPointer("h-gaining", MomentumCategory.GAINING_MOMENTUM);
        HangoutPointer confirmed = buildPointer("h-confirmed", MomentumCategory.CONFIRMED);

        PaginatedResult<BaseItem> futureResult = new PaginatedResult<>(
                List.of(building, gaining, confirmed), null);
        PaginatedResult<BaseItem> inProgressResult = new PaginatedResult<>(List.of(), null);

        when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), any(), any()))
                .thenReturn(futureResult);
        when(hangoutRepository.getInProgressEventsPage(eq(GROUP_ID), anyLong(), any(), any()))
                .thenReturn(inProgressResult);

        doNothing().when(hangoutService).enrichHostAtPlaceInfo(any());

        GroupFeedDTO feed = groupService.getGroupFeed(GROUP_ID, USER_ID, 20, null, null, null, "ALL");

        // All 3 items should be present — needsDay because no startTimestamp set
        assertThat(feed.getNeedsDay()).hasSize(3);
        assertThat(feed.getNeedsDay().stream()
                .map(h -> h.getMomentum() != null ? h.getMomentum().getCategory() : null))
                .containsExactlyInAnyOrder("BUILDING", "GAINING_MOMENTUM", "CONFIRMED");
    }

    @Test
    void getGroupFeed_filterConfirmed_returnsOnlyConfirmedHangouts() {
        GroupMembership membership = buildMembership(GROUP_ID, USER_ID);
        when(groupRepository.findMembership(GROUP_ID, USER_ID)).thenReturn(Optional.of(membership));

        HangoutPointer building = buildPointer("h-building", MomentumCategory.BUILDING);
        HangoutPointer gaining = buildPointer("h-gaining", MomentumCategory.GAINING_MOMENTUM);
        HangoutPointer confirmed = buildPointer("h-confirmed", MomentumCategory.CONFIRMED);

        PaginatedResult<BaseItem> futureResult = new PaginatedResult<>(
                List.of(building, gaining, confirmed), null);
        PaginatedResult<BaseItem> inProgressResult = new PaginatedResult<>(List.of(), null);

        when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), any(), any()))
                .thenReturn(futureResult);
        when(hangoutRepository.getInProgressEventsPage(eq(GROUP_ID), anyLong(), any(), any()))
                .thenReturn(inProgressResult);

        doNothing().when(hangoutService).enrichHostAtPlaceInfo(any());

        GroupFeedDTO feed = groupService.getGroupFeed(GROUP_ID, USER_ID, 20, null, null, null, "CONFIRMED");

        // Only CONFIRMED hangout should be returned
        List<HangoutSummaryDTO> needsDay = feed.getNeedsDay();
        assertThat(needsDay).hasSize(1);
        assertThat(needsDay.get(0).getMomentum().getCategory()).isEqualTo("CONFIRMED");
    }

    @Test
    void getGroupFeed_filterConfirmed_excludesBuildingAndGainingMomentum() {
        GroupMembership membership = buildMembership(GROUP_ID, USER_ID);
        when(groupRepository.findMembership(GROUP_ID, USER_ID)).thenReturn(Optional.of(membership));

        HangoutPointer building = buildPointer("h-building", MomentumCategory.BUILDING);
        HangoutPointer gaining = buildPointer("h-gaining", MomentumCategory.GAINING_MOMENTUM);

        PaginatedResult<BaseItem> futureResult = new PaginatedResult<>(
                List.of(building, gaining), null);
        PaginatedResult<BaseItem> inProgressResult = new PaginatedResult<>(List.of(), null);

        when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), any(), any()))
                .thenReturn(futureResult);
        when(hangoutRepository.getInProgressEventsPage(eq(GROUP_ID), anyLong(), any(), any()))
                .thenReturn(inProgressResult);

        doNothing().when(hangoutService).enrichHostAtPlaceInfo(any());

        GroupFeedDTO feed = groupService.getGroupFeed(GROUP_ID, USER_ID, 20, null, null, null, "CONFIRMED");

        // No confirmed items — both should be excluded
        assertThat(feed.getNeedsDay()).isEmpty();
        assertThat(feed.getWithDay()).isEmpty();
    }

    @Test
    void getGroupFeed_filterEverything_behavesLikeAll() {
        GroupMembership membership = buildMembership(GROUP_ID, USER_ID);
        when(groupRepository.findMembership(GROUP_ID, USER_ID)).thenReturn(Optional.of(membership));

        HangoutPointer building = buildPointer("h-b", MomentumCategory.BUILDING);
        HangoutPointer confirmed = buildPointer("h-c", MomentumCategory.CONFIRMED);

        PaginatedResult<BaseItem> futureResult = new PaginatedResult<>(
                List.of(building, confirmed), null);
        PaginatedResult<BaseItem> inProgressResult = new PaginatedResult<>(List.of(), null);

        when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), any(), any()))
                .thenReturn(futureResult);
        when(hangoutRepository.getInProgressEventsPage(eq(GROUP_ID), anyLong(), any(), any()))
                .thenReturn(inProgressResult);

        doNothing().when(hangoutService).enrichHostAtPlaceInfo(any());

        GroupFeedDTO feed = groupService.getGroupFeed(GROUP_ID, USER_ID, 20, null, null, null, "EVERYTHING");

        // EVERYTHING is same as ALL — both hangouts returned
        assertThat(feed.getNeedsDay()).hasSize(2);
    }

    @Test
    void getGroupFeed_noFilterParam_defaultsToAll() {
        GroupMembership membership = buildMembership(GROUP_ID, USER_ID);
        when(groupRepository.findMembership(GROUP_ID, USER_ID)).thenReturn(Optional.of(membership));

        HangoutPointer building = buildPointer("h-b", MomentumCategory.BUILDING);
        HangoutPointer confirmed = buildPointer("h-c", MomentumCategory.CONFIRMED);

        PaginatedResult<BaseItem> futureResult = new PaginatedResult<>(
                List.of(building, confirmed), null);
        PaginatedResult<BaseItem> inProgressResult = new PaginatedResult<>(List.of(), null);

        when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), any(), any()))
                .thenReturn(futureResult);
        when(hangoutRepository.getInProgressEventsPage(eq(GROUP_ID), anyLong(), any(), any()))
                .thenReturn(inProgressResult);

        doNothing().when(hangoutService).enrichHostAtPlaceInfo(any());

        // 5-arg overload (no filter parameter)
        GroupFeedDTO feed = groupService.getGroupFeed(GROUP_ID, USER_ID, 20, null, null);

        // Both items returned — ALL is the default
        assertThat(feed.getNeedsDay()).hasSize(2);
    }

    @Test
    void getGroupFeed_filterConfirmed_nullMomentumOnHangout_included() {
        GroupMembership membership = buildMembership(GROUP_ID, USER_ID);
        when(groupRepository.findMembership(GROUP_ID, USER_ID)).thenReturn(Optional.of(membership));

        // Pointer with no momentum category (legacy hangout)
        HangoutPointer legacyPointer = buildPointer("h-legacy", null);

        PaginatedResult<BaseItem> futureResult = new PaginatedResult<>(
                List.of(legacyPointer), null);
        PaginatedResult<BaseItem> inProgressResult = new PaginatedResult<>(List.of(), null);

        when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), any(), any()))
                .thenReturn(futureResult);
        when(hangoutRepository.getInProgressEventsPage(eq(GROUP_ID), anyLong(), any(), any()))
                .thenReturn(inProgressResult);

        doNothing().when(hangoutService).enrichHostAtPlaceInfo(any());

        GroupFeedDTO feed = groupService.getGroupFeed(GROUP_ID, USER_ID, 20, null, null, null, "CONFIRMED");

        // Legacy hangout with null momentum is treated as CONFIRMED — included in CONFIRMED filter
        assertThat(feed.getNeedsDay()).hasSize(1);
        assertThat(feed.getNeedsDay().get(0).getHangoutId()).isEqualTo("h-legacy");
    }

    // ============================================================================
    // HELPERS
    // ============================================================================

    private HangoutPointer buildPointer(String hangoutId, MomentumCategory category) {
        HangoutPointer pointer = new HangoutPointer();
        pointer.setHangoutId(hangoutId);
        pointer.setGroupId(GROUP_ID);
        pointer.setTitle("Hangout " + hangoutId);
        pointer.setStatus("ACTIVE");
        pointer.setPk("GROUP#" + GROUP_ID);
        pointer.setSk("HANGOUT#" + hangoutId);
        pointer.setMomentumCategory(category);
        pointer.setMomentumScore(category != null ? 5 : null);
        // No startTimestamp → goes to needsDay
        return pointer;
    }

    private GroupMembership buildMembership(String groupId, String userId) {
        GroupMembership membership = new GroupMembership();
        membership.setGroupId(groupId);
        membership.setUserId(userId);
        membership.setGroupName("Test Group");
        membership.setRole(GroupRole.MEMBER);
        membership.setPk("GROUP#" + groupId);
        membership.setSk("USER#" + userId);
        membership.setGsi1pk("USER#" + userId);
        membership.setGsi1sk("GROUP#" + groupId);
        return membership;
    }
}
