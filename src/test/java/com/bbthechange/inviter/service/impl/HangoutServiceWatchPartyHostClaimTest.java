package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.HangoutDetailData;
import com.bbthechange.inviter.dto.UpdateHangoutRequest;
import com.bbthechange.inviter.dto.UserSummaryDTO;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.service.FuzzyTimeService;
import com.bbthechange.inviter.testutil.WatchPartyTestFixtures;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

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
        void updateHangout_OnWatchPartyHostClaim_AutoRsvpsClaimerBeforeNotifying() {
            // Regression for hangoutsBackend-gcd: auto-RSVP must persist the claimer's
            // GOING status BEFORE the recipient resolver runs, otherwise a concurrent or
            // re-fired host-needed nudge could target the claimer for the hangout they
            // just claimed.
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

            GroupMembership membership = createTestMembership(groupId, claimerId, "Group");
            when(groupRepository.findMembership(groupId, claimerId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            UserSummaryDTO claimerUser = new UserSummaryDTO();
            claimerUser.setDisplayName("Claimer");
            when(userService.getUserSummary(UUID.fromString(claimerId))).thenReturn(Optional.of(claimerUser));
            when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));

            HangoutDetailData detail = HangoutDetailData.builder().withHangout(existingHangout).build();
            when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(detail);

            // When
            hangoutService.updateHangout(hangoutId, request, claimerId);

            // Then: saveInterestLevel (auto-RSVP) must occur BEFORE notifyWatchPartyHostClaimed.
            InOrder ordered = inOrder(hangoutRepository, notificationService);
            ordered.verify(hangoutRepository).saveInterestLevel(any(InterestLevel.class));
            ordered.verify(notificationService).notifyWatchPartyHostClaimed(eq(series), any(Hangout.class),
                    eq(claimerId), anySet());
        }

        @Test
        void updateHangout_OnWatchPartyHostClaim_NotifyThrows_DoesNotSetNudgeFlag() {
            // Acceptance: if notifyWatchPartyHostClaimed throws, we must NOT persist
            // the hostNudgeSentAt flag, otherwise the EventBridge nudge would be
            // suppressed despite the group never receiving a host-claim notification.
            // A future EventBridge fire will harmlessly no-op via the host_claimed
            // gate (hostAtPlaceUserId != null) in WatchPartyHostNudgeService.
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

            GroupMembership membership = createTestMembership(groupId, claimerId, "Group");
            when(groupRepository.findMembership(groupId, claimerId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            UserSummaryDTO claimerUser = new UserSummaryDTO();
            claimerUser.setDisplayName("Claimer");
            when(userService.getUserSummary(UUID.fromString(claimerId))).thenReturn(Optional.of(claimerUser));

            when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));

            HangoutDetailData detail = HangoutDetailData.builder().withHangout(existingHangout).build();
            when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(detail);

            doThrow(new RuntimeException("FCM down"))
                    .when(notificationService).notifyWatchPartyHostClaimed(any(), any(), anyString(), anySet());

            // When — the update itself should succeed (cascade exception is logged not propagated).
            hangoutService.updateHangout(hangoutId, request, claimerId);

            // Then — notify was attempted but the idempotency flag was NOT set.
            verify(notificationService).notifyWatchPartyHostClaimed(eq(series), any(Hangout.class),
                    eq(claimerId), anySet());
            verify(hangoutRepository, never()).setHostNudgeSentAtIfNull(anyString(), anyLong());
        }

        @Test
        void updateHangout_OnWatchPartyHostClaim_RecordsClaimerInPastHosters() {
            // Regression for hangoutsBackend-4l4: host-claim path must denormalize the new
            // host onto EventSeries.pastHosterUserIds so the host-nudge recipient resolver
            // can read past hosters without an N+1 fan-out across the series' hangouts.
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

            GroupMembership membership = createTestMembership(groupId, claimerId, "Group");
            when(groupRepository.findMembership(groupId, claimerId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            UserSummaryDTO claimerUser = new UserSummaryDTO();
            claimerUser.setDisplayName("Claimer");
            when(userService.getUserSummary(UUID.fromString(claimerId))).thenReturn(Optional.of(claimerUser));
            when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));

            HangoutDetailData detail = HangoutDetailData.builder().withHangout(existingHangout).build();
            when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(detail);

            // When
            hangoutService.updateHangout(hangoutId, request, claimerId);

            // Then: the new host is in the series' past-hoster set and series is persisted.
            ArgumentCaptor<EventSeries> seriesCaptor = ArgumentCaptor.forClass(EventSeries.class);
            verify(eventSeriesRepository).save(seriesCaptor.capture());
            assertThat(seriesCaptor.getValue().getPastHosterUserIds()).contains(claimerId);
        }

        @Test
        void updateHangout_OnWatchPartyHostClaim_PastHosterAlreadyPresent_SkipsSeriesSave() {
            // If the claimer is already in pastHosterUserIds (re-claim after abdicate),
            // we should not issue a redundant EventSeries write.
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
            series.addPastHosterUserId(claimerId);

            GroupMembership membership = createTestMembership(groupId, claimerId, "Group");
            when(groupRepository.findMembership(groupId, claimerId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);

            UserSummaryDTO claimerUser = new UserSummaryDTO();
            claimerUser.setDisplayName("Claimer");
            when(userService.getUserSummary(UUID.fromString(claimerId))).thenReturn(Optional.of(claimerUser));
            when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));

            HangoutDetailData detail = HangoutDetailData.builder().withHangout(existingHangout).build();
            when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(detail);

            // When
            hangoutService.updateHangout(hangoutId, request, claimerId);

            // Then: series.save was NOT called for the past-hoster update.
            verify(eventSeriesRepository, never()).save(any(EventSeries.class));
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
        void updateHangout_SameCallHostClaim_NotifyThrowsAfterPersist_StillCoalescesLocation() {
            // Acceptance for hangoutsBackend-ozx: when the host-claim cascade persists
            // lastHostNotificationAt BEFORE dispatching the push and the push then throws,
            // the in-memory hangout still reflects the timestamp, so the subsequent
            // same-call location-change block coalesces the location push instead of
            // firing it un-coalesced. Without this fix, a failed push would let the
            // location-change push leak out within the suppress window.
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

            // Notify throws AFTER the persist write happened.
            doThrow(new RuntimeException("FCM down"))
                    .when(notificationService).notifyWatchPartyHostClaimed(any(), any(), anyString(), anySet());

            hangoutService.updateHangout(hangoutId, request, claimerId);

            // Persist happened BEFORE the notify dispatch (the whole point of the fix).
            verify(hangoutRepository).updateLastHostNotificationAt(eq(hangoutId), anyLong());
            // Location-change push is still suppressed even though notify threw.
            verify(notificationService, never()).notifyHangoutUpdated(anyString(), anyString(), anyList(),
                    anyString(), anyString(), anySet(), any());
            verify(coalesceCounter).increment();
        }

        @Test
        void updateHangout_SameCallHostClaim_PersistsLastHostNotificationAt_BeforeNotify() {
            // Acceptance for hangoutsBackend-ozx: persistence must occur strictly BEFORE
            // notify dispatch so a failed push leaves the DB stamped (next request still
            // coalesces).
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

            hangoutService.updateHangout(hangoutId, request, claimerId);

            InOrder ordered = inOrder(hangoutRepository, notificationService);
            ordered.verify(hangoutRepository).updateLastHostNotificationAt(eq(hangoutId), anyLong());
            ordered.verify(notificationService).notifyWatchPartyHostClaimed(eq(series), any(Hangout.class),
                    eq(claimerId), anySet());
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
    // updateHangout — direct time-edit reschedules the host nudge (ah2)
    // ============================================================================

    @Nested
    class TimeEditReschedulesHostNudge {

        private static final String GROUP_ID = "11111111-1111-1111-1111-111111111111";

        private com.bbthechange.inviter.dto.TimeInfo originalTimeInfo() {
            com.bbthechange.inviter.dto.TimeInfo t = new com.bbthechange.inviter.dto.TimeInfo();
            t.setPeriodGranularity("DAY");
            t.setPeriodStart("2026-06-10T00:00:00Z");
            t.setStartTime("2026-06-10T02:00:00Z");
            return t;
        }

        private com.bbthechange.inviter.dto.TimeInfo shiftedTimeInfo() {
            com.bbthechange.inviter.dto.TimeInfo t = new com.bbthechange.inviter.dto.TimeInfo();
            t.setPeriodGranularity("DAY");
            t.setPeriodStart("2026-06-12T00:00:00Z");
            t.setStartTime("2026-06-12T02:00:00Z");
            return t;
        }

        @Test
        void updateHangout_TimeChangedOnHostlessInPersonWatchParty_ReschedulesHostNudge() {
            String hangoutId = UUID.randomUUID().toString();
            String seriesId = UUID.randomUUID().toString();
            String requestingUserId = UUID.randomUUID().toString();

            Hangout existingHangout = WatchPartyTestFixtures.inPersonHangout(hangoutId, seriesId);
            existingHangout.setHostAtPlaceUserId(null); // hostless
            existingHangout.setTimeInput(originalTimeInfo());
            existingHangout.setAssociatedGroups(new ArrayList<>(List.of(GROUP_ID)));

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setTimeInfo(shiftedTimeInfo());

            EventSeries series = WatchPartyTestFixtures.inPersonSeries(seriesId, GROUP_ID);

            GroupMembership membership = createTestMembership(GROUP_ID, requestingUserId, "Group");
            when(groupRepository.findMembership(GROUP_ID, requestingUserId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);
            when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));

            FuzzyTimeService.TimeConversionResult timeResult =
                    new FuzzyTimeService.TimeConversionResult(1781575200L, 1781582400L);
            when(fuzzyTimeService.convert(any(com.bbthechange.inviter.dto.TimeInfo.class))).thenReturn(timeResult);

            HangoutDetailData detail = HangoutDetailData.builder().withHangout(existingHangout).build();
            when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(detail);

            // When
            hangoutService.updateHangout(hangoutId, request, requestingUserId);

            // Then: scheduler reschedules nudge with the updated hangout + series.
            verify(watchPartyHostNudgeScheduler).scheduleHostNudge(any(Hangout.class), eq(series));
        }

        @Test
        void updateHangout_TimeChangedOnHostedWatchParty_DoesNotRescheduleHostNudge() {
            String hangoutId = UUID.randomUUID().toString();
            String seriesId = UUID.randomUUID().toString();
            String requestingUserId = UUID.randomUUID().toString();
            String hostUserId = UUID.randomUUID().toString();

            Hangout existingHangout = WatchPartyTestFixtures.inPersonHangout(hangoutId, seriesId);
            existingHangout.setHostAtPlaceUserId(hostUserId); // already hosted
            existingHangout.setTimeInput(originalTimeInfo());
            existingHangout.setAssociatedGroups(new ArrayList<>(List.of(GROUP_ID)));

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setTimeInfo(shiftedTimeInfo());
            // Match existing host so the host-claim cascade does NOT fire; this isolates the
            // new time-edit reschedule path from the abdication-driven reschedule.
            request.setHostAtPlaceUserId(hostUserId);

            // Host id validation runs because request.hostAtPlaceUserId != null.
            UserSummaryDTO hostUser = new UserSummaryDTO();
            hostUser.setDisplayName("Host");
            when(userService.getUserSummary(UUID.fromString(hostUserId))).thenReturn(Optional.of(hostUser));

            EventSeries series = WatchPartyTestFixtures.inPersonSeries(seriesId, GROUP_ID);

            GroupMembership membership = createTestMembership(GROUP_ID, requestingUserId, "Group");
            when(groupRepository.findMembership(GROUP_ID, requestingUserId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);
            lenient().when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));

            FuzzyTimeService.TimeConversionResult timeResult =
                    new FuzzyTimeService.TimeConversionResult(1781575200L, 1781582400L);
            when(fuzzyTimeService.convert(any(com.bbthechange.inviter.dto.TimeInfo.class))).thenReturn(timeResult);

            HangoutDetailData detail = HangoutDetailData.builder().withHangout(existingHangout).build();
            when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(detail);

            hangoutService.updateHangout(hangoutId, request, requestingUserId);

            verify(watchPartyHostNudgeScheduler, never()).scheduleHostNudge(any(Hangout.class), any(EventSeries.class));
        }

        @Test
        void updateHangout_TimeChangedOnVirtualWatchParty_DoesNotRescheduleHostNudge() {
            String hangoutId = UUID.randomUUID().toString();
            String seriesId = UUID.randomUUID().toString();
            String requestingUserId = UUID.randomUUID().toString();

            // Virtual series — host nudge never applies regardless of host presence.
            Hangout existingHangout = WatchPartyTestFixtures.virtualHangout(hangoutId, seriesId);
            existingHangout.setHostAtPlaceUserId(null);
            existingHangout.setTimeInput(originalTimeInfo());
            existingHangout.setAssociatedGroups(new ArrayList<>(List.of(GROUP_ID)));

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setTimeInfo(shiftedTimeInfo());

            EventSeries series = WatchPartyTestFixtures.virtualSeries(seriesId, GROUP_ID);

            GroupMembership membership = createTestMembership(GROUP_ID, requestingUserId, "Group");
            when(groupRepository.findMembership(GROUP_ID, requestingUserId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);
            when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));

            FuzzyTimeService.TimeConversionResult timeResult =
                    new FuzzyTimeService.TimeConversionResult(1781575200L, 1781582400L);
            when(fuzzyTimeService.convert(any(com.bbthechange.inviter.dto.TimeInfo.class))).thenReturn(timeResult);

            HangoutDetailData detail = HangoutDetailData.builder().withHangout(existingHangout).build();
            when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(detail);

            hangoutService.updateHangout(hangoutId, request, requestingUserId);

            verify(watchPartyHostNudgeScheduler, never()).scheduleHostNudge(any(Hangout.class), any(EventSeries.class));
        }

        @Test
        void updateHangout_TimeChangedOnNonWatchPartySeries_DoesNotRescheduleHostNudge() {
            String hangoutId = UUID.randomUUID().toString();
            String seriesId = UUID.randomUUID().toString();
            String requestingUserId = UUID.randomUUID().toString();

            Hangout existingHangout = createTestHangout(hangoutId);
            existingHangout.setSeriesId(seriesId);
            existingHangout.setHostAtPlaceUserId(null);
            existingHangout.setTimeInput(originalTimeInfo());
            existingHangout.setAssociatedGroups(new ArrayList<>(List.of(GROUP_ID)));

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setTimeInfo(shiftedTimeInfo());

            EventSeries nonWatchPartySeries = new EventSeries("Generic Series", null, GROUP_ID);
            nonWatchPartySeries.setSeriesId(seriesId);
            nonWatchPartySeries.setEventSeriesType(null); // not WATCH_PARTY

            GroupMembership membership = createTestMembership(GROUP_ID, requestingUserId, "Group");
            when(groupRepository.findMembership(GROUP_ID, requestingUserId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);
            when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(nonWatchPartySeries));

            FuzzyTimeService.TimeConversionResult timeResult =
                    new FuzzyTimeService.TimeConversionResult(1781575200L, 1781582400L);
            when(fuzzyTimeService.convert(any(com.bbthechange.inviter.dto.TimeInfo.class))).thenReturn(timeResult);

            HangoutDetailData detail = HangoutDetailData.builder().withHangout(existingHangout).build();
            when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(detail);

            hangoutService.updateHangout(hangoutId, request, requestingUserId);

            verify(watchPartyHostNudgeScheduler, never()).scheduleHostNudge(any(Hangout.class), any(EventSeries.class));
        }

        @Test
        void updateHangout_TimeChangedOnHostlessWatchParty_SchedulerThrows_UpdateStillSucceeds() {
            // Reschedule failure must not break the update — the catch+log keeps the user-facing
            // PUT successful, and the next scheduler cycle will recover.
            String hangoutId = UUID.randomUUID().toString();
            String seriesId = UUID.randomUUID().toString();
            String requestingUserId = UUID.randomUUID().toString();

            Hangout existingHangout = WatchPartyTestFixtures.inPersonHangout(hangoutId, seriesId);
            existingHangout.setHostAtPlaceUserId(null);
            existingHangout.setTimeInput(originalTimeInfo());
            existingHangout.setAssociatedGroups(new ArrayList<>(List.of(GROUP_ID)));

            UpdateHangoutRequest request = new UpdateHangoutRequest();
            request.setTimeInfo(shiftedTimeInfo());

            EventSeries series = WatchPartyTestFixtures.inPersonSeries(seriesId, GROUP_ID);

            GroupMembership membership = createTestMembership(GROUP_ID, requestingUserId, "Group");
            when(groupRepository.findMembership(GROUP_ID, requestingUserId)).thenReturn(Optional.of(membership));
            when(hangoutRepository.findHangoutById(hangoutId)).thenReturn(Optional.of(existingHangout));
            when(hangoutRepository.createHangout(any(Hangout.class))).thenReturn(existingHangout);
            when(eventSeriesRepository.findById(seriesId)).thenReturn(Optional.of(series));

            FuzzyTimeService.TimeConversionResult timeResult =
                    new FuzzyTimeService.TimeConversionResult(1781575200L, 1781582400L);
            when(fuzzyTimeService.convert(any(com.bbthechange.inviter.dto.TimeInfo.class))).thenReturn(timeResult);

            HangoutDetailData detail = HangoutDetailData.builder().withHangout(existingHangout).build();
            when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(detail);

            doThrow(new RuntimeException("EventBridge timeout"))
                    .when(watchPartyHostNudgeScheduler).scheduleHostNudge(any(Hangout.class), any(EventSeries.class));

            // Update succeeds despite scheduler failure.
            hangoutService.updateHangout(hangoutId, request, requestingUserId);

            verify(watchPartyHostNudgeScheduler).scheduleHostNudge(any(Hangout.class), eq(series));
            // Generic 2h reminder still scheduled.
            verify(hangoutSchedulerService).scheduleReminder(any(Hangout.class));
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
