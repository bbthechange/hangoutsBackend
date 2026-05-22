package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.HangoutDetailData;
import com.bbthechange.inviter.model.EventSeries;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.model.InterestLevel;
import com.bbthechange.inviter.model.SeriesPointer;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.repository.SeriesNotificationPreferenceRepository;
import com.bbthechange.inviter.testutil.WatchPartyTestFixtures;
import com.bbthechange.inviter.util.NudgeTypes;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchPartyHostNudgeRecipientResolverTest {

    @Mock
    private HangoutRepository hangoutRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private SeriesNotificationPreferenceRepository preferenceRepository;

    private WatchPartyHostNudgeRecipientResolver resolver;
    private MeterRegistry meterRegistry;

    private static final String GROUP_ID = "33333333-3333-3333-3333-333333333333";
    private static final String SERIES_ID = "22222222-2222-2222-2222-222222222222";
    private static final String HANGOUT_ID = "11111111-1111-1111-1111-111111111111";
    private static final String PAST_HG_ID = "44444444-4444-4444-4444-444444444444";

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        resolver = new WatchPartyHostNudgeRecipientResolver(
            hangoutRepository, groupRepository, preferenceRepository, meterRegistry);
        // Default: nobody muted; resolver-specific tests override.
        lenient().when(preferenceRepository.findMutedUsersForSeries(
            anyString(), anyString(), any())).thenReturn(Set.of());
    }

    private InterestLevel level(String userId, String status) {
        InterestLevel il = new InterestLevel();
        il.setUserId(userId);
        il.setStatus(status);
        return il;
    }

    private HangoutDetailData detail(List<InterestLevel> attendance) {
        return HangoutDetailData.builder().withAttendance(attendance).build();
    }

    private SeriesPointer pointerWithInterest(List<InterestLevel> levels) {
        SeriesPointer p = new SeriesPointer();
        p.setGroupId(GROUP_ID);
        p.setSeriesId(SERIES_ID);
        p.setInterestLevels(new ArrayList<>(levels));
        return p;
    }

    private EventSeries series() {
        EventSeries s = WatchPartyTestFixtures.inPersonSeries(SERIES_ID, GROUP_ID);
        s.setHangoutIds(new ArrayList<>());
        return s;
    }

    private Hangout hangout() {
        Hangout h = WatchPartyTestFixtures.inPersonHangout(HANGOUT_ID, SERIES_ID);
        return h;
    }

    @Test
    void episode_NOT_GOING_overrides_series_GOING() {
        EventSeries s = series();
        Hangout h = hangout();

        when(hangoutRepository.getHangoutDetailData(HANGOUT_ID))
            .thenReturn(detail(List.of(level("u-veto", "NOT_GOING"))));
        when(groupRepository.findSeriesPointer(GROUP_ID, SERIES_ID))
            .thenReturn(Optional.of(pointerWithInterest(List.of(level("u-veto", "GOING")))));

        Set<String> result = resolver.resolve(s, h);

        assertThat(result).doesNotContain("u-veto");
    }

    @Test
    void series_NOT_GOING_does_not_exclude_episode_GOING() {
        EventSeries s = series();
        Hangout h = hangout();

        when(hangoutRepository.getHangoutDetailData(HANGOUT_ID))
            .thenReturn(detail(List.of(level("u-going", "GOING"))));
        when(groupRepository.findSeriesPointer(GROUP_ID, SERIES_ID))
            .thenReturn(Optional.of(pointerWithInterest(List.of(level("u-going", "NOT_GOING")))));

        Set<String> result = resolver.resolve(s, h);

        assertThat(result).contains("u-going");
    }

    @Test
    void series_NOT_GOING_excludes_when_no_episode_signal() {
        EventSeries s = series();
        Hangout h = hangout();
        // also add another series-going user to be sure we filter only the NOT_GOING one
        when(hangoutRepository.getHangoutDetailData(HANGOUT_ID))
            .thenReturn(detail(List.of()));
        when(groupRepository.findSeriesPointer(GROUP_ID, SERIES_ID))
            .thenReturn(Optional.of(pointerWithInterest(List.of(
                level("u-out", "NOT_GOING"),
                level("u-in", "GOING")
            ))));

        Set<String> result = resolver.resolve(s, h);

        assertThat(result).contains("u-in").doesNotContain("u-out");
    }

    @Test
    void series_creator_included() {
        EventSeries s = series();
        s.setCreatedBy("creator-id");
        Hangout h = hangout();

        when(hangoutRepository.getHangoutDetailData(HANGOUT_ID))
            .thenReturn(detail(List.of()));
        when(groupRepository.findSeriesPointer(GROUP_ID, SERIES_ID))
            .thenReturn(Optional.empty());

        Set<String> result = resolver.resolve(s, h);

        assertThat(result).contains("creator-id");
    }

    @Test
    void past_hoster_included() {
        EventSeries s = series();
        s.addPastHosterUserId("past-host");
        Hangout h = hangout();

        when(hangoutRepository.getHangoutDetailData(HANGOUT_ID))
            .thenReturn(detail(List.of()));
        when(groupRepository.findSeriesPointer(GROUP_ID, SERIES_ID))
            .thenReturn(Optional.empty());

        Set<String> result = resolver.resolve(s, h);

        assertThat(result).contains("past-host");
        // Past hosters are read from the denormalized set; no per-episode lookup.
        verify(hangoutRepository, org.mockito.Mockito.never()).findHangoutById(anyString());
    }

    @Test
    void emptyPastHosters_doesNotIssuePerEpisodeReads() {
        // Regression for hangoutsBackend-4l4: even with N hangouts in the series,
        // recipient resolution must not iterate hangoutRepository.findHangoutById.
        EventSeries s = series();
        s.getHangoutIds().add(PAST_HG_ID);
        Hangout h = hangout();

        when(hangoutRepository.getHangoutDetailData(HANGOUT_ID))
            .thenReturn(detail(List.of(level("u-going", "GOING"))));
        when(groupRepository.findSeriesPointer(GROUP_ID, SERIES_ID))
            .thenReturn(Optional.empty());

        resolver.resolve(s, h);

        verify(hangoutRepository, org.mockito.Mockito.never()).findHangoutById(anyString());
    }

    @Test
    void muted_user_excluded_only_for_host_nudge() {
        EventSeries s = series();
        Hangout h = hangout();

        when(hangoutRepository.getHangoutDetailData(HANGOUT_ID))
            .thenReturn(detail(List.of(level("u-muted", "GOING"), level("u-unmuted", "GOING"))));
        when(groupRepository.findSeriesPointer(GROUP_ID, SERIES_ID))
            .thenReturn(Optional.empty());
        when(preferenceRepository.findMutedUsersForSeries(
                eq(SERIES_ID), eq(NudgeTypes.HOST_NUDGE), any()))
            .thenReturn(Set.of("u-muted"));

        Set<String> result = resolver.resolve(s, h);

        assertThat(result).containsExactly("u-unmuted");

        ArgumentCaptor<String> nudgeTypeCap = ArgumentCaptor.forClass(String.class);
        verify(preferenceRepository).findMutedUsersForSeries(
            eq(SERIES_ID), nudgeTypeCap.capture(), any());
        assertThat(nudgeTypeCap.getValue()).isEqualTo(NudgeTypes.HOST_NUDGE);
    }

    @Test
    void union_of_hangout_and_series_interest() {
        EventSeries s = series();
        Hangout h = hangout();

        when(hangoutRepository.getHangoutDetailData(HANGOUT_ID))
            .thenReturn(detail(List.of(level("u-hangout-going", "GOING"))));
        when(groupRepository.findSeriesPointer(GROUP_ID, SERIES_ID))
            .thenReturn(Optional.of(pointerWithInterest(List.of(level("u-series-interested", "INTERESTED")))));

        Set<String> result = resolver.resolve(s, h);

        assertThat(result).containsExactlyInAnyOrder("u-hangout-going", "u-series-interested");
    }

    @Test
    void emptyCandidates_skipsMuteLookup() {
        EventSeries s = series();
        Hangout h = hangout();

        when(hangoutRepository.getHangoutDetailData(HANGOUT_ID))
            .thenReturn(detail(List.of()));
        when(groupRepository.findSeriesPointer(GROUP_ID, SERIES_ID))
            .thenReturn(Optional.empty());

        Set<String> result = resolver.resolve(s, h);

        assertThat(result).isEmpty();
        verify(preferenceRepository, org.mockito.Mockito.never())
            .findMutedUsersForSeries(anyString(), anyString(), any());
    }

    @Test
    void over_max_recipients_caps_and_emits_metric() {
        // 201 candidate users (all GOING on the hangout) — must truncate to MAX_RECIPIENTS
        // and bump watchparty_host_nudge_recipients_capped exactly once. Protects against
        // an accidental "huge group" series fanning out unbounded pushes.
        EventSeries s = series();
        Hangout h = hangout();

        List<com.bbthechange.inviter.model.InterestLevel> attendance = IntStream.range(0, 201)
            .mapToObj(i -> level("u-" + i, "GOING"))
            .collect(Collectors.toList());
        when(hangoutRepository.getHangoutDetailData(HANGOUT_ID))
            .thenReturn(detail(attendance));
        when(groupRepository.findSeriesPointer(GROUP_ID, SERIES_ID))
            .thenReturn(Optional.empty());

        Set<String> result = resolver.resolve(s, h);

        assertThat(result).hasSize(WatchPartyHostNudgeRecipientResolver.MAX_RECIPIENTS);
        assertThat(meterRegistry.counter("watchparty_host_nudge_recipients_capped").count())
            .isEqualTo(1.0);
    }

    @Test
    void exactly_max_recipients_does_not_emit_cap_metric() {
        // Boundary: a series with exactly 200 candidates is at the cap but not over.
        // No cap metric should fire — otherwise operators get noise at the edge.
        EventSeries s = series();
        Hangout h = hangout();

        List<com.bbthechange.inviter.model.InterestLevel> attendance = IntStream.range(0, 200)
            .mapToObj(i -> level("u-" + i, "GOING"))
            .collect(Collectors.toList());
        when(hangoutRepository.getHangoutDetailData(HANGOUT_ID))
            .thenReturn(detail(attendance));
        when(groupRepository.findSeriesPointer(GROUP_ID, SERIES_ID))
            .thenReturn(Optional.empty());

        Set<String> result = resolver.resolve(s, h);

        assertThat(result).hasSize(200);
        assertThat(meterRegistry.counter("watchparty_host_nudge_recipients_capped").count())
            .isEqualTo(0.0);
    }
}
