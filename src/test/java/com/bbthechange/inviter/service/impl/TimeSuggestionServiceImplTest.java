package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.CreateTimeSuggestionRequest;
import com.bbthechange.inviter.dto.TimeSuggestionDTO;
import com.bbthechange.inviter.exception.ResourceNotFoundException;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.exception.ValidationException;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.MomentumService;
import com.bbthechange.inviter.service.TimeSuggestionSchedulerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TimeSuggestionServiceImpl.
 */
@ExtendWith(MockitoExtension.class)
class TimeSuggestionServiceImplTest {

    // Fixed IDs for test data
    private static final String GROUP_ID    = "11111111-1111-1111-1111-111111111111";
    private static final String HANGOUT_ID  = "22222222-2222-2222-2222-222222222222";
    private static final String USER_ID     = "33333333-3333-3333-3333-333333333333";
    private static final String OTHER_USER  = "44444444-4444-4444-4444-444444444444";
    private static final String SUGGESTION_ID = "55555555-5555-5555-5555-555555555555";

    @Mock private HangoutRepository hangoutRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private MomentumService momentumService;
    @Mock private PointerUpdateService pointerUpdateService;
    @Mock private TimeSuggestionSchedulerService timeSuggestionSchedulerService;

    @InjectMocks
    private TimeSuggestionServiceImpl service;

    // ============================================================================
    // HELPERS
    // ============================================================================

    private Hangout timelessHangout() {
        Hangout h = new Hangout("Movie Night", "desc", null, null, null,
                EventVisibility.INVITE_ONLY, null);
        h.setHangoutId(HANGOUT_ID);
        h.setAssociatedGroups(List.of(GROUP_ID));
        // startTimestamp = null  ← key: no time set
        return h;
    }

    private Hangout hangoutWithTime() {
        Hangout h = timelessHangout();
        h.setStartTimestamp(1_800_000_000L);
        return h;
    }

    private TimeSuggestion activeSuggestion() {
        TimeSuggestion ts = new TimeSuggestion(HANGOUT_ID, GROUP_ID, USER_ID, FuzzyTime.THIS_WEEKEND, null);
        ts.setSuggestionId(SUGGESTION_ID);
        return ts;
    }

    private CreateTimeSuggestionRequest createRequest(FuzzyTime fuzzy, Long specific) {
        CreateTimeSuggestionRequest req = new CreateTimeSuggestionRequest();
        req.setFuzzyTime(fuzzy);
        req.setSpecificTime(specific);
        return req;
    }

    // ============================================================================
    // createSuggestion
    // ============================================================================

    @Nested
    class CreateSuggestion {

        @BeforeEach
        void setup() {
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
        }

        @Test
        void validRequest_SavesSuggestionAndReturnDTO() {
            // Given
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(timelessHangout()));
            when(hangoutRepository.saveTimeSuggestion(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            TimeSuggestionDTO dto = service.createSuggestion(GROUP_ID, HANGOUT_ID,
                    createRequest(FuzzyTime.THIS_WEEKEND, null), USER_ID);

            // Then
            assertThat(dto.getFuzzyTime()).isEqualTo(FuzzyTime.THIS_WEEKEND);
            assertThat(dto.getSuggestedBy()).isEqualTo(USER_ID);
            assertThat(dto.getHangoutId()).isEqualTo(HANGOUT_ID);
            assertThat(dto.getStatus()).isEqualTo(TimeSuggestionStatus.ACTIVE);
            verify(hangoutRepository).saveTimeSuggestion(any(TimeSuggestion.class));
        }

        @Test
        void withSpecificTime_SpecificTimePersisted() {
            // Given
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(timelessHangout()));
            when(hangoutRepository.saveTimeSuggestion(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            TimeSuggestionDTO dto = service.createSuggestion(GROUP_ID, HANGOUT_ID,
                    createRequest(FuzzyTime.SATURDAY, 1_800_000_000L), USER_ID);

            // Then
            assertThat(dto.getSpecificTime()).isEqualTo(1_800_000_000L);
        }

        @Test
        void hangoutNotFound_ThrowsResourceNotFoundException() {
            // Given
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> service.createSuggestion(GROUP_ID, HANGOUT_ID,
                    createRequest(FuzzyTime.TONIGHT, null), USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void hangoutAlreadyHasTime_ThrowsValidationException() {
            // Given
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(hangoutWithTime()));

            // When / Then
            assertThatThrownBy(() -> service.createSuggestion(GROUP_ID, HANGOUT_ID,
                    createRequest(FuzzyTime.TONIGHT, null), USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("already has a time set");
        }

        @Test
        void notGroupMember_ThrowsUnauthorizedException() {
            // Given
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(false);

            // When / Then
            assertThatThrownBy(() -> service.createSuggestion(GROUP_ID, HANGOUT_ID,
                    createRequest(FuzzyTime.TONIGHT, null), USER_ID))
                    .isInstanceOf(UnauthorizedException.class);

            verifyNoInteractions(hangoutRepository);
        }

        @Test
        void fuzzyTimeNull_ThrowsValidationException() {
            // Given
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(timelessHangout()));

            // When / Then
            assertThatThrownBy(() -> service.createSuggestion(GROUP_ID, HANGOUT_ID,
                    createRequest(null, null), USER_ID))
                    .isInstanceOf(ValidationException.class);

            verify(hangoutRepository, never()).saveTimeSuggestion(any());
        }
    }

    // ============================================================================
    // supportSuggestion
    // ============================================================================

    @Nested
    class SupportSuggestion {

        @BeforeEach
        void setup() {
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, OTHER_USER)).thenReturn(true);
        }

        @Test
        void validSupport_AddsSupporterAndSaves() {
            // Given
            TimeSuggestion ts = activeSuggestion();
            when(hangoutRepository.findTimeSuggestionById(HANGOUT_ID, SUGGESTION_ID))
                    .thenReturn(Optional.of(ts));
            when(hangoutRepository.saveTimeSuggestion(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            TimeSuggestionDTO dto = service.supportSuggestion(GROUP_ID, HANGOUT_ID, SUGGESTION_ID, OTHER_USER);

            // Then
            assertThat(dto.getSupporterIds()).contains(OTHER_USER);
            assertThat(dto.getSupportCount()).isEqualTo(1);
            verify(hangoutRepository).saveTimeSuggestion(any());
        }

        @Test
        void alreadySupporting_IdempotentNoSave() {
            // Given
            TimeSuggestion ts = activeSuggestion();
            ts.addSupporter(OTHER_USER); // already added
            when(hangoutRepository.findTimeSuggestionById(HANGOUT_ID, SUGGESTION_ID))
                    .thenReturn(Optional.of(ts));

            // When
            TimeSuggestionDTO dto = service.supportSuggestion(GROUP_ID, HANGOUT_ID, SUGGESTION_ID, OTHER_USER);

            // Then
            assertThat(dto.getSupportCount()).isEqualTo(1);
            verify(hangoutRepository, never()).saveTimeSuggestion(any());
        }

        @Test
        void suggestionNotActive_ThrowsValidationException() {
            // Given
            TimeSuggestion ts = activeSuggestion();
            ts.setStatus(TimeSuggestionStatus.ADOPTED);
            when(hangoutRepository.findTimeSuggestionById(HANGOUT_ID, SUGGESTION_ID))
                    .thenReturn(Optional.of(ts));

            // When / Then
            assertThatThrownBy(() -> service.supportSuggestion(GROUP_ID, HANGOUT_ID, SUGGESTION_ID, OTHER_USER))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("no longer active");
        }

        @Test
        void suggestionNotFound_ThrowsResourceNotFoundException() {
            // Given
            when(hangoutRepository.findTimeSuggestionById(HANGOUT_ID, SUGGESTION_ID))
                    .thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> service.supportSuggestion(GROUP_ID, HANGOUT_ID, SUGGESTION_ID, OTHER_USER))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void notGroupMember_ThrowsUnauthorizedException() {
            // Given
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, OTHER_USER)).thenReturn(false);

            // When / Then
            assertThatThrownBy(() -> service.supportSuggestion(GROUP_ID, HANGOUT_ID, SUGGESTION_ID, OTHER_USER))
                    .isInstanceOf(UnauthorizedException.class);
        }
    }

    // ============================================================================
    // listSuggestions
    // ============================================================================

    @Nested
    class ListSuggestions {

        @Test
        void returnsMappedDTOs() {
            // Given
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(timelessHangout()));
            TimeSuggestion ts1 = activeSuggestion();
            TimeSuggestion ts2 = new TimeSuggestion(HANGOUT_ID, GROUP_ID, OTHER_USER, FuzzyTime.TOMORROW, null);
            when(hangoutRepository.findActiveTimeSuggestions(HANGOUT_ID)).thenReturn(List.of(ts1, ts2));

            // When
            List<TimeSuggestionDTO> results = service.listSuggestions(GROUP_ID, HANGOUT_ID, USER_ID);

            // Then
            assertThat(results).hasSize(2);
        }

        @Test
        void emptyList_ReturnsEmpty() {
            // Given
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(timelessHangout()));
            when(hangoutRepository.findActiveTimeSuggestions(HANGOUT_ID)).thenReturn(List.of());

            // When
            List<TimeSuggestionDTO> results = service.listSuggestions(GROUP_ID, HANGOUT_ID, USER_ID);

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        void notGroupMember_ThrowsUnauthorizedException() {
            // Given — unauthorized check happens before any hangout lookup
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(false);

            // When / Then
            assertThatThrownBy(() -> service.listSuggestions(GROUP_ID, HANGOUT_ID, USER_ID))
                    .isInstanceOf(UnauthorizedException.class);

            verify(hangoutRepository, never()).findActiveTimeSuggestions(any());
        }
    }

    // ============================================================================
    // adoptForHangout (auto-adoption logic)
    // ============================================================================

    @Nested
    class AdoptForHangout {

        @Test
        void noActiveSuggestions_DoesNothing() {
            // Given
            when(hangoutRepository.findActiveTimeSuggestions(HANGOUT_ID)).thenReturn(List.of());

            // When
            service.adoptForHangout(HANGOUT_ID, 24, 48);

            // Then
            verify(hangoutRepository, never()).saveTimeSuggestion(any());
        }

        @Test
        void competingSuggestions_SkipsAdoption() {
            // Given
            TimeSuggestion ts1 = activeSuggestion();
            TimeSuggestion ts2 = new TimeSuggestion(HANGOUT_ID, GROUP_ID, OTHER_USER, FuzzyTime.SATURDAY, null);
            when(hangoutRepository.findActiveTimeSuggestions(HANGOUT_ID)).thenReturn(List.of(ts1, ts2));

            // When
            service.adoptForHangout(HANGOUT_ID, 24, 48);

            // Then
            verify(hangoutRepository, never()).saveTimeSuggestion(any());
        }

        @Test
        void singleSuggestionWithSupportPastWindow_Adopted() {
            // Given — suggestion created 25h ago with 1 supporter, no specificTime
            TimeSuggestion ts = activeSuggestion(); // specificTime = null
            ts.setCreatedAt(Instant.now().minusSeconds(25 * 3600));
            ts.addSupporter(OTHER_USER);

            when(hangoutRepository.findActiveTimeSuggestions(HANGOUT_ID)).thenReturn(List.of(ts));
            when(hangoutRepository.saveTimeSuggestion(any())).thenAnswer(inv -> inv.getArgument(0));
            // findHangoutById not called because specificTime is null

            // When
            service.adoptForHangout(HANGOUT_ID, 24, 48);

            // Then — suggestion saved with ADOPTED status
            ArgumentCaptor<TimeSuggestion> captor = ArgumentCaptor.forClass(TimeSuggestion.class);
            verify(hangoutRepository).saveTimeSuggestion(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(TimeSuggestionStatus.ADOPTED);
            verify(momentumService).recomputeMomentum(HANGOUT_ID);
        }

        @Test
        void singleSuggestionWithSupportBeforeWindow_NotAdopted() {
            // Given — suggestion created only 10h ago with 1 supporter (shortWindow = 24h)
            TimeSuggestion ts = activeSuggestion();
            ts.setCreatedAt(Instant.now().minusSeconds(10 * 3600));
            ts.addSupporter(OTHER_USER);

            when(hangoutRepository.findActiveTimeSuggestions(HANGOUT_ID)).thenReturn(List.of(ts));

            // When
            service.adoptForHangout(HANGOUT_ID, 24, 48);

            // Then — not yet past window; should not save
            verify(hangoutRepository, never()).saveTimeSuggestion(any());
        }

        @Test
        void zeroVoteSuggestionPastLongWindow_Adopted() {
            // Given — suggestion created 50h ago with 0 supporters (longWindow = 48h), no specificTime
            TimeSuggestion ts = activeSuggestion(); // specificTime = null
            ts.setCreatedAt(Instant.now().minusSeconds(50 * 3600));
            // no supporters added

            when(hangoutRepository.findActiveTimeSuggestions(HANGOUT_ID)).thenReturn(List.of(ts));
            when(hangoutRepository.saveTimeSuggestion(any())).thenAnswer(inv -> inv.getArgument(0));
            // findHangoutById not called because specificTime is null

            // When
            service.adoptForHangout(HANGOUT_ID, 24, 48);

            // Then
            ArgumentCaptor<TimeSuggestion> captor = ArgumentCaptor.forClass(TimeSuggestion.class);
            verify(hangoutRepository).saveTimeSuggestion(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(TimeSuggestionStatus.ADOPTED);
        }

        @Test
        void zeroVoteSuggestionBeforeLongWindow_NotAdopted() {
            // Given — suggestion created 30h ago with 0 supporters (longWindow = 48h)
            TimeSuggestion ts = activeSuggestion();
            ts.setCreatedAt(Instant.now().minusSeconds(30 * 3600));

            when(hangoutRepository.findActiveTimeSuggestions(HANGOUT_ID)).thenReturn(List.of(ts));

            // When
            service.adoptForHangout(HANGOUT_ID, 24, 48);

            // Then
            verify(hangoutRepository, never()).saveTimeSuggestion(any());
        }

        @Test
        void adoptedSuggestionWithSpecificTime_UpdatesHangoutTimestamp() {
            // Given
            TimeSuggestion ts = new TimeSuggestion(HANGOUT_ID, GROUP_ID, USER_ID,
                    FuzzyTime.SATURDAY, 1_800_000_000L); // has specific time
            ts.setSuggestionId(SUGGESTION_ID);
            ts.setCreatedAt(Instant.now().minusSeconds(25 * 3600));
            ts.addSupporter(OTHER_USER);

            Hangout hangout = timelessHangout();

            when(hangoutRepository.findActiveTimeSuggestions(HANGOUT_ID)).thenReturn(List.of(ts));
            when(hangoutRepository.saveTimeSuggestion(any())).thenAnswer(inv -> inv.getArgument(0));
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(hangout));
            when(hangoutRepository.save(any(Hangout.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.adoptForHangout(HANGOUT_ID, 24, 48);

            // Then — hangout should have been saved with the new timestamp
            ArgumentCaptor<Hangout> hangoutCaptor = ArgumentCaptor.forClass(Hangout.class);
            verify(hangoutRepository).save(hangoutCaptor.capture());
            assertThat(hangoutCaptor.getValue().getStartTimestamp()).isEqualTo(1_800_000_000L);
        }

        @Test
        void adoptedSuggestionWithSpecificTime_CallsPointerUpdateForEachGroup() {
            // Given — hangout has 1 associated group, suggestion has specificTime
            TimeSuggestion ts = new TimeSuggestion(HANGOUT_ID, GROUP_ID, USER_ID,
                    FuzzyTime.SATURDAY, 1_800_000_000L);
            ts.setSuggestionId(SUGGESTION_ID);
            ts.setCreatedAt(Instant.now().minusSeconds(25 * 3600));
            ts.addSupporter(OTHER_USER);

            Hangout hangout = timelessHangout(); // associatedGroups = [GROUP_ID]

            when(hangoutRepository.findActiveTimeSuggestions(HANGOUT_ID)).thenReturn(List.of(ts));
            when(hangoutRepository.saveTimeSuggestion(any())).thenAnswer(inv -> inv.getArgument(0));
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(hangout));
            when(hangoutRepository.save(any(Hangout.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.adoptForHangout(HANGOUT_ID, 24, 48);

            // Then — pointer update called once (one group)
            verify(pointerUpdateService).updatePointerWithRetry(eq(GROUP_ID), eq(HANGOUT_ID), any(), any());
        }

        @Test
        void adoptedSuggestionWithoutSpecificTime_DoesNotUpdateHangoutTimestamp() {
            // Given — specificTime is null, so hangout.save should NOT be called
            TimeSuggestion ts = activeSuggestion(); // specificTime = null
            ts.setCreatedAt(Instant.now().minusSeconds(25 * 3600));
            ts.addSupporter(OTHER_USER);

            when(hangoutRepository.findActiveTimeSuggestions(HANGOUT_ID)).thenReturn(List.of(ts));
            when(hangoutRepository.saveTimeSuggestion(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.adoptForHangout(HANGOUT_ID, 24, 48);

            // Then — suggestion adopted, but hangout.save not called and no pointer update
            ArgumentCaptor<TimeSuggestion> captor = ArgumentCaptor.forClass(TimeSuggestion.class);
            verify(hangoutRepository).saveTimeSuggestion(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(TimeSuggestionStatus.ADOPTED);
            verify(hangoutRepository, never()).save(any(Hangout.class));
            verifyNoInteractions(pointerUpdateService);
            verify(momentumService).recomputeMomentum(HANGOUT_ID);
        }

        @Test
        void momentumRecomputeThrows_DoesNotPropagateException() {
            // Given — momentum throws, but method should still complete normally
            TimeSuggestion ts = activeSuggestion(); // specificTime = null
            ts.setCreatedAt(Instant.now().minusSeconds(25 * 3600));
            ts.addSupporter(OTHER_USER);

            when(hangoutRepository.findActiveTimeSuggestions(HANGOUT_ID)).thenReturn(List.of(ts));
            when(hangoutRepository.saveTimeSuggestion(any())).thenAnswer(inv -> inv.getArgument(0));
            doThrow(new RuntimeException("Momentum service unavailable"))
                    .when(momentumService).recomputeMomentum(HANGOUT_ID);

            // When — should not throw
            service.adoptForHangout(HANGOUT_ID, 24, 48);

            // Then — suggestion was still adopted despite momentum failure
            ArgumentCaptor<TimeSuggestion> captor = ArgumentCaptor.forClass(TimeSuggestion.class);
            verify(hangoutRepository).saveTimeSuggestion(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(TimeSuggestionStatus.ADOPTED);
        }

        @Test
        void nullCreatedAt_SkipsAdoption() {
            // Given — suggestion has null createdAt (defensive guard)
            TimeSuggestion ts = activeSuggestion();
            ts.setCreatedAt(null);

            when(hangoutRepository.findActiveTimeSuggestions(HANGOUT_ID)).thenReturn(List.of(ts));

            // When
            service.adoptForHangout(HANGOUT_ID, 24, 48);

            // Then — no write should occur
            verify(hangoutRepository, never()).saveTimeSuggestion(any());
            verifyNoInteractions(momentumService);
        }
    }
}
