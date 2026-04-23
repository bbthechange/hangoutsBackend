package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.CreateTimeSuggestionRequest;
import com.bbthechange.inviter.dto.TimeInfo;
import com.bbthechange.inviter.dto.TimeSuggestionDTO;
import com.bbthechange.inviter.exception.ResourceNotFoundException;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.exception.ValidationException;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.FuzzyTimeService;
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
    @Mock private FuzzyTimeService fuzzyTimeService;

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
        return h;
    }

    private Hangout hangoutWithTime() {
        Hangout h = timelessHangout();
        h.setStartTimestamp(1_800_000_000L);
        return h;
    }

    private TimeInfo fuzzyWeekend() {
        TimeInfo ti = new TimeInfo();
        ti.setPeriodGranularity("weekend");
        ti.setPeriodStart("2026-04-25T00:00:00-07:00");
        return ti;
    }

    private TimeInfo exactTime() {
        TimeInfo ti = new TimeInfo();
        ti.setStartTime("2026-04-25T18:00:00-07:00");
        return ti;
    }

    private TimeSuggestion activeSuggestion() {
        TimeSuggestion ts = new TimeSuggestion(HANGOUT_ID, GROUP_ID, USER_ID, fuzzyWeekend());
        ts.setSuggestionId(SUGGESTION_ID);
        return ts;
    }

    private CreateTimeSuggestionRequest createRequest(TimeInfo timeInput) {
        CreateTimeSuggestionRequest req = new CreateTimeSuggestionRequest();
        req.setTimeInput(timeInput);
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
        void validFuzzyRequest_SavesSuggestionAndReturnsDTO() {
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(timelessHangout()));
            when(hangoutRepository.saveTimeSuggestion(any())).thenAnswer(inv -> inv.getArgument(0));
            when(fuzzyTimeService.convert(any(TimeInfo.class)))
                    .thenReturn(new FuzzyTimeService.TimeConversionResult(1L, 2L));

            TimeSuggestionDTO dto = service.createSuggestion(GROUP_ID, HANGOUT_ID,
                    createRequest(fuzzyWeekend()), USER_ID);

            assertThat(dto.getTimeInput()).isNotNull();
            assertThat(dto.getTimeInput().getPeriodGranularity()).isEqualTo("weekend");
            assertThat(dto.getSuggestedBy()).isEqualTo(USER_ID);
            assertThat(dto.getHangoutId()).isEqualTo(HANGOUT_ID);
            assertThat(dto.getStatus()).isEqualTo(TimeSuggestionStatus.ACTIVE);
            verify(hangoutRepository).saveTimeSuggestion(any(TimeSuggestion.class));
            verify(fuzzyTimeService).convert(any(TimeInfo.class));
        }

        @Test
        void validExactRequest_PersistsTimeInput() {
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(timelessHangout()));
            when(hangoutRepository.saveTimeSuggestion(any())).thenAnswer(inv -> inv.getArgument(0));
            when(fuzzyTimeService.convert(any(TimeInfo.class)))
                    .thenReturn(new FuzzyTimeService.TimeConversionResult(1L, 2L));

            TimeSuggestionDTO dto = service.createSuggestion(GROUP_ID, HANGOUT_ID,
                    createRequest(exactTime()), USER_ID);

            assertThat(dto.getTimeInput()).isNotNull();
            assertThat(dto.getTimeInput().getStartTime()).isNotNull();
        }

        @Test
        void invalidTimeInput_ConvertThrows_PropagatesValidationException() {
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(timelessHangout()));
            TimeInfo bad = new TimeInfo();
            bad.setPeriodGranularity("notARealGranularity");
            bad.setPeriodStart("2026-04-25T00:00:00-07:00");
            when(fuzzyTimeService.convert(any(TimeInfo.class)))
                    .thenThrow(new ValidationException("Unsupported periodGranularity"));

            assertThatThrownBy(() -> service.createSuggestion(GROUP_ID, HANGOUT_ID,
                    createRequest(bad), USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("periodGranularity");

            verify(hangoutRepository, never()).saveTimeSuggestion(any());
        }

        @Test
        void hangoutNotFound_ThrowsResourceNotFoundException() {
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createSuggestion(GROUP_ID, HANGOUT_ID,
                    createRequest(fuzzyWeekend()), USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void hangoutAlreadyHasTime_ThrowsValidationException() {
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(hangoutWithTime()));

            assertThatThrownBy(() -> service.createSuggestion(GROUP_ID, HANGOUT_ID,
                    createRequest(fuzzyWeekend()), USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("already has a time set");
        }

        @Test
        void notGroupMember_ThrowsUnauthorizedException() {
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(false);

            assertThatThrownBy(() -> service.createSuggestion(GROUP_ID, HANGOUT_ID,
                    createRequest(fuzzyWeekend()), USER_ID))
                    .isInstanceOf(UnauthorizedException.class);

            verifyNoInteractions(hangoutRepository);
        }

        @Test
        void timeInputNull_ThrowsValidationException() {
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(timelessHangout()));

            assertThatThrownBy(() -> service.createSuggestion(GROUP_ID, HANGOUT_ID,
                    createRequest(null), USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("timeInput");

            verify(hangoutRepository, never()).saveTimeSuggestion(any());
            verifyNoInteractions(fuzzyTimeService);
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
            TimeSuggestion ts = activeSuggestion();
            when(hangoutRepository.findTimeSuggestionById(HANGOUT_ID, SUGGESTION_ID))
                    .thenReturn(Optional.of(ts));
            when(hangoutRepository.saveTimeSuggestion(any())).thenAnswer(inv -> inv.getArgument(0));

            TimeSuggestionDTO dto = service.supportSuggestion(GROUP_ID, HANGOUT_ID, SUGGESTION_ID, OTHER_USER);

            assertThat(dto.getSupporterIds()).contains(OTHER_USER);
            assertThat(dto.getSupportCount()).isEqualTo(1);
            verify(hangoutRepository).saveTimeSuggestion(any());
        }

        @Test
        void alreadySupporting_IdempotentNoSave() {
            TimeSuggestion ts = activeSuggestion();
            ts.addSupporter(OTHER_USER);
            when(hangoutRepository.findTimeSuggestionById(HANGOUT_ID, SUGGESTION_ID))
                    .thenReturn(Optional.of(ts));

            TimeSuggestionDTO dto = service.supportSuggestion(GROUP_ID, HANGOUT_ID, SUGGESTION_ID, OTHER_USER);

            assertThat(dto.getSupportCount()).isEqualTo(1);
            verify(hangoutRepository, never()).saveTimeSuggestion(any());
        }

        @Test
        void suggestionNotActive_ThrowsValidationException() {
            TimeSuggestion ts = activeSuggestion();
            ts.setStatus(TimeSuggestionStatus.ADOPTED);
            when(hangoutRepository.findTimeSuggestionById(HANGOUT_ID, SUGGESTION_ID))
                    .thenReturn(Optional.of(ts));

            assertThatThrownBy(() -> service.supportSuggestion(GROUP_ID, HANGOUT_ID, SUGGESTION_ID, OTHER_USER))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("no longer active");
        }

        @Test
        void suggestionNotFound_ThrowsResourceNotFoundException() {
            when(hangoutRepository.findTimeSuggestionById(HANGOUT_ID, SUGGESTION_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.supportSuggestion(GROUP_ID, HANGOUT_ID, SUGGESTION_ID, OTHER_USER))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void notGroupMember_ThrowsUnauthorizedException() {
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, OTHER_USER)).thenReturn(false);

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
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(timelessHangout()));
            TimeSuggestion ts1 = activeSuggestion();
            TimeSuggestion ts2 = new TimeSuggestion(HANGOUT_ID, GROUP_ID, OTHER_USER, fuzzyWeekend());
            when(hangoutRepository.findActiveTimeSuggestions(HANGOUT_ID)).thenReturn(List.of(ts1, ts2));

            List<TimeSuggestionDTO> results = service.listSuggestions(GROUP_ID, HANGOUT_ID, USER_ID);

            assertThat(results).hasSize(2);
        }

        @Test
        void emptyList_ReturnsEmpty() {
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(timelessHangout()));
            when(hangoutRepository.findActiveTimeSuggestions(HANGOUT_ID)).thenReturn(List.of());

            List<TimeSuggestionDTO> results = service.listSuggestions(GROUP_ID, HANGOUT_ID, USER_ID);

            assertThat(results).isEmpty();
        }

        @Test
        void notGroupMember_ThrowsUnauthorizedException() {
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(false);

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
            when(hangoutRepository.findActiveTimeSuggestions(HANGOUT_ID)).thenReturn(List.of());

            service.adoptForHangout(HANGOUT_ID, 24, 48);

            verify(hangoutRepository, never()).saveTimeSuggestion(any());
        }

        @Test
        void competingSuggestions_SkipsAdoption() {
            TimeSuggestion ts1 = activeSuggestion();
            TimeSuggestion ts2 = new TimeSuggestion(HANGOUT_ID, GROUP_ID, OTHER_USER, fuzzyWeekend());
            when(hangoutRepository.findActiveTimeSuggestions(HANGOUT_ID)).thenReturn(List.of(ts1, ts2));

            service.adoptForHangout(HANGOUT_ID, 24, 48);

            verify(hangoutRepository, never()).saveTimeSuggestion(any());
        }

        @Test
        void singleSuggestionWithSupportPastWindow_Adopted() {
            TimeSuggestion ts = activeSuggestion();
            ts.setCreatedAt(Instant.now().minusSeconds(25 * 3600));
            ts.addSupporter(OTHER_USER);

            Hangout hangout = timelessHangout();
            when(hangoutRepository.findActiveTimeSuggestions(HANGOUT_ID)).thenReturn(List.of(ts));
            when(hangoutRepository.saveTimeSuggestion(any())).thenAnswer(inv -> inv.getArgument(0));
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(hangout));
            when(hangoutRepository.save(any(Hangout.class))).thenAnswer(inv -> inv.getArgument(0));
            when(fuzzyTimeService.convert(any(TimeInfo.class)))
                    .thenReturn(new FuzzyTimeService.TimeConversionResult(1_800_000_000L, 1_800_172_800L));

            service.adoptForHangout(HANGOUT_ID, 24, 48);

            ArgumentCaptor<TimeSuggestion> captor = ArgumentCaptor.forClass(TimeSuggestion.class);
            verify(hangoutRepository).saveTimeSuggestion(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(TimeSuggestionStatus.ADOPTED);
            verify(momentumService).recomputeMomentum(HANGOUT_ID);
        }

        @Test
        void singleSuggestionWithSupportBeforeWindow_NotAdopted() {
            TimeSuggestion ts = activeSuggestion();
            ts.setCreatedAt(Instant.now().minusSeconds(10 * 3600));
            ts.addSupporter(OTHER_USER);

            when(hangoutRepository.findActiveTimeSuggestions(HANGOUT_ID)).thenReturn(List.of(ts));

            service.adoptForHangout(HANGOUT_ID, 24, 48);

            verify(hangoutRepository, never()).saveTimeSuggestion(any());
        }

        @Test
        void zeroVoteSuggestionPastLongWindow_Adopted() {
            TimeSuggestion ts = activeSuggestion();
            ts.setCreatedAt(Instant.now().minusSeconds(50 * 3600));

            Hangout hangout = timelessHangout();
            when(hangoutRepository.findActiveTimeSuggestions(HANGOUT_ID)).thenReturn(List.of(ts));
            when(hangoutRepository.saveTimeSuggestion(any())).thenAnswer(inv -> inv.getArgument(0));
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(hangout));
            when(hangoutRepository.save(any(Hangout.class))).thenAnswer(inv -> inv.getArgument(0));
            when(fuzzyTimeService.convert(any(TimeInfo.class)))
                    .thenReturn(new FuzzyTimeService.TimeConversionResult(1L, 2L));

            service.adoptForHangout(HANGOUT_ID, 24, 48);

            ArgumentCaptor<TimeSuggestion> captor = ArgumentCaptor.forClass(TimeSuggestion.class);
            verify(hangoutRepository).saveTimeSuggestion(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(TimeSuggestionStatus.ADOPTED);
        }

        @Test
        void zeroVoteSuggestionBeforeLongWindow_NotAdopted() {
            TimeSuggestion ts = activeSuggestion();
            ts.setCreatedAt(Instant.now().minusSeconds(30 * 3600));

            when(hangoutRepository.findActiveTimeSuggestions(HANGOUT_ID)).thenReturn(List.of(ts));

            service.adoptForHangout(HANGOUT_ID, 24, 48);

            verify(hangoutRepository, never()).saveTimeSuggestion(any());
        }

        @Test
        void adoption_AppliesFullTimeBlockToHangout() {
            // Adopted suggestion sets timeInput AND startTimestamp AND endTimestamp.
            TimeSuggestion ts = activeSuggestion();
            ts.setCreatedAt(Instant.now().minusSeconds(25 * 3600));
            ts.addSupporter(OTHER_USER);

            Hangout hangout = timelessHangout();
            when(hangoutRepository.findActiveTimeSuggestions(HANGOUT_ID)).thenReturn(List.of(ts));
            when(hangoutRepository.saveTimeSuggestion(any())).thenAnswer(inv -> inv.getArgument(0));
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(hangout));
            when(hangoutRepository.save(any(Hangout.class))).thenAnswer(inv -> inv.getArgument(0));
            when(fuzzyTimeService.convert(any(TimeInfo.class)))
                    .thenReturn(new FuzzyTimeService.TimeConversionResult(1_800_000_000L, 1_800_172_800L));

            service.adoptForHangout(HANGOUT_ID, 24, 48);

            ArgumentCaptor<Hangout> hangoutCaptor = ArgumentCaptor.forClass(Hangout.class);
            verify(hangoutRepository).save(hangoutCaptor.capture());
            Hangout saved = hangoutCaptor.getValue();
            assertThat(saved.getStartTimestamp()).isEqualTo(1_800_000_000L);
            assertThat(saved.getEndTimestamp()).isEqualTo(1_800_172_800L);
            assertThat(saved.getTimeInput()).isNotNull();
            assertThat(saved.getTimeInput().getPeriodGranularity()).isEqualTo("weekend");
        }

        @Test
        void adoption_CallsPointerUpdateForEachGroup() {
            TimeSuggestion ts = activeSuggestion();
            ts.setCreatedAt(Instant.now().minusSeconds(25 * 3600));
            ts.addSupporter(OTHER_USER);

            Hangout hangout = timelessHangout();
            when(hangoutRepository.findActiveTimeSuggestions(HANGOUT_ID)).thenReturn(List.of(ts));
            when(hangoutRepository.saveTimeSuggestion(any())).thenAnswer(inv -> inv.getArgument(0));
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(hangout));
            when(hangoutRepository.save(any(Hangout.class))).thenAnswer(inv -> inv.getArgument(0));
            when(fuzzyTimeService.convert(any(TimeInfo.class)))
                    .thenReturn(new FuzzyTimeService.TimeConversionResult(1L, 2L));

            service.adoptForHangout(HANGOUT_ID, 24, 48);

            verify(pointerUpdateService).updatePointerWithRetry(eq(GROUP_ID), eq(HANGOUT_ID), any(), any());
        }

        @Test
        void adoption_NullTimeInput_SkipsHangoutUpdate() {
            // Defensive guard for legacy rows without timeInput: suggestion is
            // marked ADOPTED but the hangout is left untouched.
            TimeSuggestion ts = activeSuggestion();
            ts.setTimeInput(null);
            ts.setCreatedAt(Instant.now().minusSeconds(25 * 3600));
            ts.addSupporter(OTHER_USER);

            when(hangoutRepository.findActiveTimeSuggestions(HANGOUT_ID)).thenReturn(List.of(ts));
            when(hangoutRepository.saveTimeSuggestion(any())).thenAnswer(inv -> inv.getArgument(0));

            service.adoptForHangout(HANGOUT_ID, 24, 48);

            ArgumentCaptor<TimeSuggestion> captor = ArgumentCaptor.forClass(TimeSuggestion.class);
            verify(hangoutRepository).saveTimeSuggestion(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(TimeSuggestionStatus.ADOPTED);
            verify(hangoutRepository, never()).save(any(Hangout.class));
            verifyNoInteractions(pointerUpdateService);
            verifyNoInteractions(fuzzyTimeService);
            verify(momentumService).recomputeMomentum(HANGOUT_ID);
        }

        @Test
        void momentumRecomputeThrows_DoesNotPropagateException() {
            TimeSuggestion ts = activeSuggestion();
            ts.setTimeInput(null); // skip hangout-update path so we isolate momentum
            ts.setCreatedAt(Instant.now().minusSeconds(25 * 3600));
            ts.addSupporter(OTHER_USER);

            when(hangoutRepository.findActiveTimeSuggestions(HANGOUT_ID)).thenReturn(List.of(ts));
            when(hangoutRepository.saveTimeSuggestion(any())).thenAnswer(inv -> inv.getArgument(0));
            doThrow(new RuntimeException("Momentum service unavailable"))
                    .when(momentumService).recomputeMomentum(HANGOUT_ID);

            service.adoptForHangout(HANGOUT_ID, 24, 48);

            ArgumentCaptor<TimeSuggestion> captor = ArgumentCaptor.forClass(TimeSuggestion.class);
            verify(hangoutRepository).saveTimeSuggestion(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(TimeSuggestionStatus.ADOPTED);
        }

        @Test
        void nullCreatedAt_SkipsAdoption() {
            TimeSuggestion ts = activeSuggestion();
            ts.setCreatedAt(null);

            when(hangoutRepository.findActiveTimeSuggestions(HANGOUT_ID)).thenReturn(List.of(ts));

            service.adoptForHangout(HANGOUT_ID, 24, 48);

            verify(hangoutRepository, never()).saveTimeSuggestion(any());
            verifyNoInteractions(momentumService);
        }
    }
}
