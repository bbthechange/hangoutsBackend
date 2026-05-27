package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.model.EventSeries;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.repository.EventSeriesRepository;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.NotificationService;
import com.bbthechange.inviter.service.ShowFlavorService;
import com.bbthechange.inviter.testutil.WatchPartyTestFixtures;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WatchPartyHostNudgeServiceTest {

    @Mock
    private HangoutRepository hangoutRepository;

    @Mock
    private EventSeriesRepository eventSeriesRepository;

    @Mock
    private WatchPartyHostNudgeRecipientResolver recipientResolver;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ShowFlavorService showFlavorService;

    private MeterRegistry meterRegistry;
    private WatchPartyHostNudgeService service;

    private static final String HANGOUT_ID = "11111111-1111-1111-1111-111111111111";
    private static final String SERIES_ID = "22222222-2222-2222-2222-222222222222";
    private static final String GROUP_ID = "33333333-3333-3333-3333-333333333333";

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new WatchPartyHostNudgeService(
            hangoutRepository, eventSeriesRepository,
            recipientResolver, notificationService, showFlavorService, meterRegistry);
        // Default stub: delegate to the real derived-fallback path so this
        // stays in lockstep with production if the regex/sentinel ever changes.
        // Tests that exercise the curated-flavor branch override this explicitly.
        lenient().when(showFlavorService.resolveShortName(any(), any()))
            .thenAnswer(inv -> ShowFlavorService.deriveShortShowName(inv.getArgument(1)));
    }

    private double counter(String status) {
        return meterRegistry.counter("watchparty_host_nudge_total", "status", status).count();
    }

    private Hangout validHangout() {
        Hangout h = WatchPartyTestFixtures.inPersonHangout(HANGOUT_ID, SERIES_ID);
        // 48h ahead — well inside the 36-60h safety window
        h.setStartTimestamp(Instant.now().getEpochSecond() + 48L * 3600);
        h.setHostNudgeSentAt(null);
        h.setHostAtPlaceUserId(null);
        return h;
    }

    private EventSeries validSeries() {
        EventSeries s = WatchPartyTestFixtures.inPersonSeries(SERIES_ID, GROUP_ID);
        s.setSeriesTitle("My Show");
        s.setTimezone("UTC");
        return s;
    }

    // ===== Gate counters =====

    @Test
    void processHostNudge_notFound_emitsCounter() {
        when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.empty());

        service.processHostNudge(HANGOUT_ID);

        assertThat(counter("not_found")).isEqualTo(1.0);
        verifyNoInteractions(eventSeriesRepository, recipientResolver, notificationService);
    }

    @Test
    void processHostNudge_alreadySent_emitsCounter() {
        Hangout h = validHangout();
        h.setHostNudgeSentAt(123L);
        when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(h));

        service.processHostNudge(HANGOUT_ID);

        assertThat(counter("already_sent")).isEqualTo(1.0);
        verify(hangoutRepository, never()).setHostNudgeSentAtIfNull(anyString(), anyLong());
        verifyNoInteractions(recipientResolver, notificationService);
    }

    @Test
    void processHostNudge_hostClaimed_emitsCounter() {
        Hangout h = validHangout();
        h.setHostAtPlaceUserId("someone");
        when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(h));

        service.processHostNudge(HANGOUT_ID);

        assertThat(counter("host_claimed")).isEqualTo(1.0);
        verifyNoInteractions(recipientResolver, notificationService);
    }

    @Test
    void processHostNudge_notInSeries_emitsCounter() {
        Hangout h = validHangout();
        h.setSeriesId(null);
        when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(h));

        service.processHostNudge(HANGOUT_ID);

        assertThat(counter("not_in_series")).isEqualTo(1.0);
        verifyNoInteractions(recipientResolver, notificationService);
    }

    @Test
    void processHostNudge_seriesMissing_emitsWrongSeriesType() {
        Hangout h = validHangout();
        when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(h));
        when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.empty());

        service.processHostNudge(HANGOUT_ID);

        assertThat(counter("wrong_series_type")).isEqualTo(1.0);
        verifyNoInteractions(recipientResolver, notificationService);
    }

    @Test
    void processHostNudge_virtualSeries_emitsWrongSeriesType() {
        Hangout h = validHangout();
        EventSeries s = WatchPartyTestFixtures.virtualSeries(SERIES_ID, GROUP_ID);
        when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(h));
        when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(s));

        service.processHostNudge(HANGOUT_ID);

        assertThat(counter("wrong_series_type")).isEqualTo(1.0);
        verifyNoInteractions(recipientResolver, notificationService);
    }

    @Test
    void processHostNudge_nonWatchPartySeries_emitsWrongSeriesType() {
        Hangout h = validHangout();
        EventSeries s = WatchPartyTestFixtures.inPersonSeries(SERIES_ID, GROUP_ID);
        s.setEventSeriesType(null); // not a watch party
        when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(h));
        when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(s));

        service.processHostNudge(HANGOUT_ID);

        assertThat(counter("wrong_series_type")).isEqualTo(1.0);
    }

    @Test
    void processHostNudge_outsideWindow_emitsCounter() {
        Hangout h = validHangout();
        h.setStartTimestamp(Instant.now().getEpochSecond() + 600); // way too close
        when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(h));
        when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(validSeries()));

        service.processHostNudge(HANGOUT_ID);

        assertThat(counter("outside_window")).isEqualTo(1.0);
        verify(hangoutRepository, never()).setHostNudgeSentAtIfNull(anyString(), anyLong());
        verifyNoInteractions(recipientResolver, notificationService);
    }

    @Test
    void processHostNudge_nullStartTime_emitsOutsideWindow() {
        Hangout h = validHangout();
        h.setStartTimestamp(null);
        when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(h));
        when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(validSeries()));

        service.processHostNudge(HANGOUT_ID);

        assertThat(counter("outside_window")).isEqualTo(1.0);
    }

    @Test
    void processHostNudge_lostRace_emitsCounter() {
        Hangout h = validHangout();
        when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(h));
        when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(validSeries()));
        when(hangoutRepository.setHostNudgeSentAtIfNull(eq(HANGOUT_ID), anyLong())).thenReturn(false);

        service.processHostNudge(HANGOUT_ID);

        assertThat(counter("lost_race")).isEqualTo(1.0);
        verifyNoInteractions(recipientResolver, notificationService);
    }

    @Test
    void processHostNudge_notifyThrows_clearsClaimAndRethrows() {
        // Acceptance: simulate notifyWatchPartyHostNeeded throwing; assert hostNudgeSentAt
        // is rolled back (compensating REMOVE) and the exception propagates so the
        // listener's error path runs. Without this, FCM/APNS hiccups would leave the
        // idempotency flag set forever with no notification ever delivered.
        Hangout h = validHangout();
        EventSeries s = validSeries();
        when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(h));
        when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(s));
        when(hangoutRepository.setHostNudgeSentAtIfNull(eq(HANGOUT_ID), anyLong())).thenReturn(true);
        when(recipientResolver.resolve(s, h)).thenReturn(Set.of("u1", "u2"));
        doThrow(new RuntimeException("FCM throttle"))
            .when(notificationService).notifyWatchPartyHostNeeded(any(), any(), any(), anyString());

        assertThatThrownBy(() -> service.processHostNudge(HANGOUT_ID))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("FCM throttle");

        verify(hangoutRepository).clearHostNudgeSentAt(HANGOUT_ID);
        assertThat(counter("error")).isEqualTo(1.0);
        assertThat(counter("sent")).isEqualTo(0.0);
    }

    @Test
    void processHostNudge_clearAfterNotifyFailure_doesNotMaskOriginalException() {
        // If the compensating clear itself fails, we still propagate the original
        // notify exception so the operator sees the real cause.
        Hangout h = validHangout();
        EventSeries s = validSeries();
        when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(h));
        when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(s));
        when(hangoutRepository.setHostNudgeSentAtIfNull(eq(HANGOUT_ID), anyLong())).thenReturn(true);
        when(recipientResolver.resolve(s, h)).thenReturn(Set.of("u1"));
        doThrow(new RuntimeException("FCM down"))
            .when(notificationService).notifyWatchPartyHostNeeded(any(), any(), any(), anyString());
        doThrow(new RuntimeException("DynamoDB write throttled"))
            .when(hangoutRepository).clearHostNudgeSentAt(HANGOUT_ID);

        assertThatThrownBy(() -> service.processHostNudge(HANGOUT_ID))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("FCM down");

        verify(hangoutRepository).clearHostNudgeSentAt(HANGOUT_ID);
        assertThat(counter("error")).isEqualTo(1.0);
    }

    @Test
    void processHostNudge_success_sendsAndEmitsSent() {
        Hangout h = validHangout();
        EventSeries s = validSeries();
        when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(h));
        when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(s));
        when(hangoutRepository.setHostNudgeSentAtIfNull(eq(HANGOUT_ID), anyLong())).thenReturn(true);
        when(recipientResolver.resolve(s, h)).thenReturn(Set.of("u1", "u2"));

        service.processHostNudge(HANGOUT_ID);

        ArgumentCaptor<Set<String>> recipientsCap = ArgumentCaptor.forClass(Set.class);
        ArgumentCaptor<String> bodyCap = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notifyWatchPartyHostNeeded(
            recipientsCap.capture(), eq(s), eq(h), bodyCap.capture());

        assertThat(recipientsCap.getValue()).containsExactlyInAnyOrder("u1", "u2");
        // hangout.getTitle() ("Episode 1" from the fixture) stands alone as the
        // body subject; the series title is no longer prepended.
        assertThat(bodyCap.getValue()).contains("Episode 1").contains("still needs a host");
        assertThat(counter("sent")).isEqualTo(1.0);
    }

    // ===== Cross-episode series coalesce =====

    @Test
    void processHostNudge_seriesNudgedWithin24h_isCoalesced() {
        // Spec acceptance: two episodes in same series fire 12h apart — second is
        // skipped with series_coalesced counter. Without this gate a 22-episode
        // season would push the same recipient set 22 separate times.
        Hangout h = validHangout();
        EventSeries s = validSeries();
        s.setLastHostNudgeFiredAt(System.currentTimeMillis() - 12L * 3600 * 1000); // 12h ago
        when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(h));
        when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(s));

        service.processHostNudge(HANGOUT_ID);

        assertThat(counter("series_coalesced")).isEqualTo(1.0);
        assertThat(counter("sent")).isEqualTo(0.0);
        verify(hangoutRepository, never()).setHostNudgeSentAtIfNull(anyString(), anyLong());
        verifyNoInteractions(recipientResolver, notificationService);
        verify(eventSeriesRepository, never()).updateLastHostNudgeFiredAt(anyString(), anyLong());
    }

    @Test
    void processHostNudge_seriesNudgedOver24hAgo_proceeds() {
        // Spec acceptance: two episodes fire 25h apart — both deliver.
        Hangout h = validHangout();
        EventSeries s = validSeries();
        s.setLastHostNudgeFiredAt(System.currentTimeMillis() - 25L * 3600 * 1000); // 25h ago
        when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(h));
        when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(s));
        when(hangoutRepository.setHostNudgeSentAtIfNull(eq(HANGOUT_ID), anyLong())).thenReturn(true);
        when(recipientResolver.resolve(s, h)).thenReturn(Set.of("u1"));

        service.processHostNudge(HANGOUT_ID);

        verify(notificationService).notifyWatchPartyHostNeeded(any(), eq(s), eq(h), anyString());
        assertThat(counter("sent")).isEqualTo(1.0);
        assertThat(counter("series_coalesced")).isEqualTo(0.0);
    }

    @Test
    void processHostNudge_firstFireForSeries_persistsLastHostNudgeFiredAt() {
        Hangout h = validHangout();
        EventSeries s = validSeries();
        s.setLastHostNudgeFiredAt(null); // never fired
        when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(h));
        when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(s));
        when(hangoutRepository.setHostNudgeSentAtIfNull(eq(HANGOUT_ID), anyLong())).thenReturn(true);
        when(recipientResolver.resolve(s, h)).thenReturn(Set.of("u1"));

        service.processHostNudge(HANGOUT_ID);

        ArgumentCaptor<Long> tsCap = ArgumentCaptor.forClass(Long.class);
        verify(eventSeriesRepository).updateLastHostNudgeFiredAt(eq(SERIES_ID), tsCap.capture());
        // Sanity: the persisted timestamp is recent (within last 10s of test wall clock)
        assertThat(tsCap.getValue()).isCloseTo(System.currentTimeMillis(), within(10_000L));
        assertThat(counter("sent")).isEqualTo(1.0);
    }

    @Test
    void processHostNudge_persistFailure_doesNotMaskSuccessfulSend() {
        // If updateLastHostNudgeFiredAt throws after a successful dispatch, the nudge
        // still went out — we must NOT roll back the hangout claim or fail the SQS
        // message. The coalesce gate just won't fire for this cycle.
        Hangout h = validHangout();
        EventSeries s = validSeries();
        when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(h));
        when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(s));
        when(hangoutRepository.setHostNudgeSentAtIfNull(eq(HANGOUT_ID), anyLong())).thenReturn(true);
        when(recipientResolver.resolve(s, h)).thenReturn(Set.of("u1"));
        doThrow(new RuntimeException("DynamoDB throttled"))
            .when(eventSeriesRepository).updateLastHostNudgeFiredAt(anyString(), anyLong());

        service.processHostNudge(HANGOUT_ID); // should NOT throw

        verify(notificationService).notifyWatchPartyHostNeeded(any(), eq(s), eq(h), anyString());
        verify(hangoutRepository, never()).clearHostNudgeSentAt(anyString());
        assertThat(counter("sent")).isEqualTo(1.0);
        assertThat(counter("error")).isEqualTo(0.0);
    }

    // ===== Body formatting =====
    //
    // hangout.getTitle() now carries show context (curated short name or full
    // series title — see TitleFormatter / hangoutsBackend-3wa), so the default
    // branch no longer re-prefixes the series title. Curated short names come
    // from ShowFlavorService and only appear in the TBA / combined branches
    // where the episode title can't stand alone.

    @Test
    void buildMessageBody_defaultEpisode_usesEpisodeTitleAndDayWithoutSeriesPrefix() {
        Hangout h = validHangout();
        // After title formatting lands, hangout.getTitle() already contains the
        // show prefix — the nudge body must not re-prepend the series title.
        h.setTitle("My Show · Pilot");
        EventSeries s = validSeries();

        String body = service.buildMessageBody(s, h);

        assertThat(body).isEqualTo(
            String.format("My Show · Pilot airs %s and still needs a host!",
                formatDayHelper(h, s)));
        // Show name appears once, not twice (regression guard for triple-show-name push)
        assertThat(body.split("My Show", -1).length - 1).isEqualTo(1);
    }

    @Test
    void buildMessageBody_tbaTitle_flavorAbsent_derivesShortShowName() {
        Hangout h = validHangout();
        h.setTitle("TBA");
        EventSeries s = validSeries();
        // No seasonId → flavor service falls back to deriving from the title.
        s.setSeriesTitle("My Show Season 11");
        s.setSeasonId(null);

        String body = service.buildMessageBody(s, h);

        assertThat(body).contains("'s My Show episode still needs a host!");
        assertThat(body).doesNotContain("TBA");
        assertThat(body).doesNotContain("Season 11");
    }

    @Test
    void buildMessageBody_tbaTitle_flavorPresent_usesShortName() {
        Hangout h = validHangout();
        h.setTitle("TBA");
        EventSeries s = validSeries();
        s.setSeriesTitle("RuPaul's Drag Race: All Stars Season 11");
        s.setSeasonId("TVMAZE#SHOW#73228|SEASON#11");
        when(showFlavorService.resolveShortName(eq(73228), anyString())).thenReturn("All Stars");

        String body = service.buildMessageBody(s, h);

        assertThat(body).contains("'s All Stars episode still needs a host!");
        assertThat(body).doesNotContain("RuPaul");
        assertThat(body).doesNotContain("Season 11");
    }

    @Test
    void buildMessageBody_blankTitle_usesTbaFormat() {
        Hangout h = validHangout();
        h.setTitle("");
        EventSeries s = validSeries();

        String body = service.buildMessageBody(s, h);

        assertThat(body).contains("'s My Show episode still needs a host!");
    }

    @Test
    void buildMessageBody_combinedDouble_flavorAbsent_usesDerivedShortName() {
        Hangout h = validHangout();
        h.setCombinedExternalIds(List.of("ep1", "ep2"));
        EventSeries s = validSeries();
        s.setSeriesTitle("My Show Season 5");

        String body = service.buildMessageBody(s, h);

        assertThat(body).contains("double My Show episode still needs a host!");
        assertThat(body).doesNotContain("Season 5");
    }

    @Test
    void buildMessageBody_combinedTriple_flavorPresent_usesShortName() {
        Hangout h = validHangout();
        h.setCombinedExternalIds(List.of("ep1", "ep2", "ep3"));
        EventSeries s = validSeries();
        s.setSeriesTitle("RuPaul's Drag Race: All Stars Season 11");
        s.setSeasonId("TVMAZE#SHOW#73228|SEASON#11");
        when(showFlavorService.resolveShortName(eq(73228), anyString())).thenReturn("All Stars");

        String body = service.buildMessageBody(s, h);

        assertThat(body).contains("triple All Stars episode still needs a host!");
    }

    private String formatDayHelper(Hangout h, EventSeries s) {
        java.time.ZoneId zone = java.time.ZoneId.of(s.getTimezone());
        return Instant.ofEpochSecond(h.getStartTimestamp())
            .atZone(zone)
            .getDayOfWeek()
            .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);
    }
}
