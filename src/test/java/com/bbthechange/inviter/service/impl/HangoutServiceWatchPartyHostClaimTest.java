package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.HangoutDetailData;
import com.bbthechange.inviter.dto.UpdateHangoutRequest;
import com.bbthechange.inviter.dto.UserSummaryDTO;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.testutil.WatchPartyTestFixtures;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Phase 3 lifecycle tests for HangoutServiceImpl: the watch-party host-claim
 * cascade, the location-change coalesce, host abdication re-scheduling, and
 * cancel-on-delete. Regression coverage for non-watch-party hangouts is
 * included so that the new code paths stay scoped.
 */
class HangoutServiceWatchPartyHostClaimTest extends HangoutServiceTestBase {

    // ============================================================================
    // updateHangout — host-claim cascade
    // ============================================================================

    @Nested
    class HostClaim {

        @Test
        void updateHangout_OnWatchPartyHostClaim_CancelsNudgeAndNotifies() {
            String groupId = "11111111-1111-1111-1111-111111111111";
            String hangoutId = UUID.randomUUID().toString();
            String seriesId = UUID.randomUUID().toString();
            String claimerId = UUID.randomUUID().toString();

            Hangout existingHangout = WatchPartyTestFixtures.inPersonHangout(hangoutId, seriesId);
            existingHangout.setHostAtPlaceUserId(null);
            existingHangout.setAssociatedGroups(new ArrayList<>(List.of(groupId)));

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setHostAtPlaceUserId(claimerId);

            EventSeries series = WatchPartyTestFixtures.inPersonSeries(seriesId, groupId);

            // Authorization
            GroupMembership membership = createTestMembership(groupId, claimerId, "Group");
            when(groupRepository.findMembership(groupId, claimerId)).thenReturn(Optional.of(membership));

            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            // Claimer validation
            UserSummaryDTO claimerUser = new UserSummaryDTO();
            claimerUser.setDisplayName("Claimer");
            when(userService.getUserSummary(UUID.fromString(claimerId))).thenReturn(Optional.of(claimerUser));

            // Series lookup for cascade
            when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));

            // HangoutDetailData for recipient resolution + auto-RSVP setUserInterest path
            HangoutDetailData detail = HangoutDetailData.builder().withHangout(existingHangout).build();
            when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(detail);

            // When
            hangoutService.updateHangout(hangoutId, request, claimerId);

            // Then
            verify(watchPartyHostNudgeScheduler).cancelHostNudge(any(Hangout.class));
            verify(hangoutRepository).setHostNudgeSentAtIfNull(eq(hangoutId), anyLong());
            verify(notificationService).notifyWatchPartyHostClaimed(eq(series), any(Hangout.class),
                    eq(claimerId), anySet());
            // Auto-RSVP path saves the claimer's interest level at GOING.
            ArgumentCaptor<InterestLevel> levelCaptor = ArgumentCaptor.forClass(InterestLevel.class);
            verify(hangoutRepository).saveInterestLevel(levelCaptor.capture());
            assertThat(levelCaptor.getValue().getUserId()).isEqualTo(claimerId);
            assertThat(levelCaptor.getValue().getStatus()).isEqualTo("GOING");
        }

        @Test
        void updateHangout_OnHostAbdication_ReschedulesNudge() {
            String groupId = "11111111-1111-1111-1111-111111111111";
            String hangoutId = UUID.randomUUID().toString();
            String seriesId = UUID.randomUUID().toString();
            String requestingUserId = UUID.randomUUID().toString();
            String oldHostId = UUID.randomUUID().toString();

            Hangout existingHangout = WatchPartyTestFixtures.inPersonHangout(hangoutId, seriesId);
            existingHangout.setHostAtPlaceUserId(oldHostId);
            existingHangout.setAssociatedGroups(new ArrayList<>(List.of(groupId)));

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setHostAtPlaceUserId(null); // abdicate

            EventSeries series = WatchPartyTestFixtures.inPersonSeries(seriesId, groupId);

            GroupMembership membership = createTestMembership(groupId, requestingUserId, "Group");
            when(groupRepository.findMembership(groupId, requestingUserId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);
            when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));

            // When
            hangoutService.updateHangout(hangoutId, request, requestingUserId);

            // Then: re-schedules; does NOT fire claim notification
            verify(watchPartyHostNudgeScheduler).scheduleHostNudge(any(Hangout.class), eq(series));
            verify(notificationService, never()).notifyWatchPartyHostClaimed(any(), any(), any(), any());
            verify(hangoutRepository, never()).setHostNudgeSentAtIfNull(anyString(), anyLong());
        }

        @Test
        void updateHangout_HostClaimOnNonWatchPartyHangout_DoesNotFireWatchPartyPaths() {
            String groupId = "11111111-1111-1111-1111-111111111111";
            String hangoutId = UUID.randomUUID().toString();
            String seriesId = UUID.randomUUID().toString();
            String claimerId = UUID.randomUUID().toString();

            // Series exists and is in a series, but is NOT a watch party.
            Hangout existingHangout = createTestHangout(hangoutId);
            existingHangout.setSeriesId(seriesId);
            existingHangout.setHostAtPlaceUserId(null);
            existingHangout.setAssociatedGroups(new ArrayList<>(List.of(groupId)));

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setHostAtPlaceUserId(claimerId);

            EventSeries nonWatchPartySeries = new EventSeries("Generic Series", null, groupId);
            nonWatchPartySeries.setSeriesId(seriesId);
            nonWatchPartySeries.setEventSeriesType(null); // not WATCH_PARTY

            GroupMembership membership = createTestMembership(groupId, claimerId, "Group");
            when(groupRepository.findMembership(groupId, claimerId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);
            UserSummaryDTO claimerUser = new UserSummaryDTO();
            claimerUser.setDisplayName("Claimer");
            when(userService.getUserSummary(UUID.fromString(claimerId))).thenReturn(Optional.of(claimerUser));
            when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(nonWatchPartySeries));

            // When
            hangoutService.updateHangout(hangoutId, request, claimerId);

            // Then: watch-party-specific paths are NOT triggered.
            verify(watchPartyHostNudgeScheduler, never()).cancelHostNudge(any());
            verify(watchPartyHostNudgeScheduler, never()).scheduleHostNudge(any(), any());
            verify(notificationService, never()).notifyWatchPartyHostClaimed(any(), any(), any(), any());
            verify(hangoutRepository, never()).setHostNudgeSentAtIfNull(anyString(), anyLong());
            verify(hangoutRepository, never()).saveInterestLevel(any());
        }
    }

    // ============================================================================
    // Coalesce — location-change suppression within window
    // ============================================================================

    @Nested
    class CoalesceLocationChange {

        @Test
        void updateHangout_LocationChangeWithinCoalesceWindow_SkipsNotificationAndIncrementsCounter() {
            String groupId = "11111111-1111-1111-1111-111111111111";
            String hangoutId = UUID.randomUUID().toString();
            String seriesId = UUID.randomUUID().toString();
            String requestingUserId = UUID.randomUUID().toString();

            // Recent host-claim notification — coalesce window is 10 min.
            Hangout existingHangout = WatchPartyTestFixtures.inPersonHangout(hangoutId, seriesId);
            existingHangout.setHostAtPlaceUserId(UUID.randomUUID().toString());
            existingHangout.setAssociatedGroups(new ArrayList<>(List.of(groupId)));
            existingHangout.setLastHostNotificationAt(System.currentTimeMillis() - 60_000L); // 1 min ago

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            com.bbthechange.inviter.dto.Address newAddress = new com.bbthechange.inviter.dto.Address();
            newAddress.setName("Sarah's place");
            newAddress.setStreetAddress("123 Main St");
            request.setLocation(newAddress);

            EventSeries series = WatchPartyTestFixtures.inPersonSeries(seriesId, groupId);

            GroupMembership membership = createTestMembership(groupId, requestingUserId, "Group");
            when(groupRepository.findMembership(groupId, requestingUserId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);
            when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));

            Counter coalesceCounter = mock(Counter.class);
            when(meterRegistry.counter("notification_coalesced", "reason", "host_claim_recent"))
                    .thenReturn(coalesceCounter);

            // When
            hangoutService.updateHangout(hangoutId, request, requestingUserId);

            // Then: location-change notification is NOT sent, but the counter is bumped.
            verify(notificationService, never()).notifyHangoutUpdated(anyString(), anyString(), anyList(),
                    anyString(), anyString(), anySet(), any());
            verify(coalesceCounter).increment();
        }

        @Test
        void updateHangout_SameCallHostClaimAndLocation_CoalescesLocationPush() {
            // Single PUT mutating BOTH hostAtPlaceUserId (claim) and location. The host-claim
            // cascade runs first, stamping lastHostNotificationAt on the in-memory hangout, so
            // the location-change block then coalesces the location push.
            String groupId = "11111111-1111-1111-1111-111111111111";
            String hangoutId = UUID.randomUUID().toString();
            String seriesId = UUID.randomUUID().toString();
            String claimerId = UUID.randomUUID().toString();

            Hangout existingHangout = WatchPartyTestFixtures.inPersonHangout(hangoutId, seriesId);
            existingHangout.setHostAtPlaceUserId(null);
            existingHangout.setAssociatedGroups(new ArrayList<>(List.of(groupId)));

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setHostAtPlaceUserId(claimerId);
            com.bbthechange.inviter.dto.Address newAddress = new com.bbthechange.inviter.dto.Address();
            newAddress.setName("Sarah's place");
            newAddress.setStreetAddress("123 Main St");
            request.setLocation(newAddress);

            EventSeries series = WatchPartyTestFixtures.inPersonSeries(seriesId, groupId);

            GroupMembership membership = createTestMembership(groupId, claimerId, "Group");
            when(groupRepository.findMembership(groupId, claimerId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);
            when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));

            UserSummaryDTO claimerUser = new UserSummaryDTO();
            claimerUser.setDisplayName("Claimer");
            when(userService.getUserSummary(UUID.fromString(claimerId))).thenReturn(Optional.of(claimerUser));

            HangoutDetailData detail = HangoutDetailData.builder().withHangout(existingHangout).build();
            when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(detail);

            Counter coalesceCounter = mock(Counter.class);
            when(meterRegistry.counter("notification_coalesced", "reason", "host_claim_recent"))
                    .thenReturn(coalesceCounter);

            hangoutService.updateHangout(hangoutId, request, claimerId);

            // Host-claim push DOES fire.
            verify(notificationService).notifyWatchPartyHostClaimed(eq(series), any(Hangout.class),
                    eq(claimerId), anySet());
            // Location push is suppressed; coalesce counter is bumped.
            verify(notificationService, never()).notifyHangoutUpdated(anyString(), anyString(), anyList(),
                    anyString(), anyString(), anySet(), any());
            verify(coalesceCounter).increment();
        }

        @Test
        void updateHangout_LocationChangeOutsideCoalesceWindow_StillNotifies() {
            String groupId = "11111111-1111-1111-1111-111111111111";
            String hangoutId = UUID.randomUUID().toString();
            String seriesId = UUID.randomUUID().toString();
            String requestingUserId = UUID.randomUUID().toString();

            Hangout existingHangout = WatchPartyTestFixtures.inPersonHangout(hangoutId, seriesId);
            existingHangout.setHostAtPlaceUserId(UUID.randomUUID().toString());
            existingHangout.setAssociatedGroups(new ArrayList<>(List.of(groupId)));
            // Outside the 10-min window
            existingHangout.setLastHostNotificationAt(System.currentTimeMillis() - 700_000L);

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            com.bbthechange.inviter.dto.Address newAddress = new com.bbthechange.inviter.dto.Address();
            newAddress.setName("Sarah's place");
            request.setLocation(newAddress);

            EventSeries series = WatchPartyTestFixtures.inPersonSeries(seriesId, groupId);

            GroupMembership membership = createTestMembership(groupId, requestingUserId, "Group");
            when(groupRepository.findMembership(groupId, requestingUserId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);
            when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));

            HangoutDetailData detail = HangoutDetailData.builder().withHangout(existingHangout).build();
            when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(detail);

            // When
            hangoutService.updateHangout(hangoutId, request, requestingUserId);

            // Then
            verify(notificationService).notifyHangoutUpdated(eq(hangoutId), anyString(), anyList(),
                    eq("location"), eq(requestingUserId), anySet(), any());
        }
    }

    // ============================================================================
    // deleteHangout — host-nudge cancellation
    // ============================================================================

    @Nested
    class DeleteHangout {

        @Test
        void deleteHangout_CancelsHostNudge() {
            String groupId = "11111111-1111-1111-1111-111111111111";
            String hangoutId = UUID.randomUUID().toString();
            String requestingUserId = UUID.randomUUID().toString();

            Hangout existingHangout = createTestHangout(hangoutId);
            existingHangout.setAssociatedGroups(new ArrayList<>(List.of(groupId)));

            GroupMembership membership = createTestMembership(groupId, requestingUserId, "Group");
            when(groupRepository.findMembership(groupId, requestingUserId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));

            // When
            hangoutService.deleteHangout(hangoutId, requestingUserId);

            // Then: scheduler cancel runs for every deletion (idempotent inside the scheduler).
            verify(watchPartyHostNudgeScheduler).cancelHostNudge(existingHangout);
        }
    }
}
