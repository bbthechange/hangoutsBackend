package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.model.EventSeries;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.repository.EventSeriesRepository;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.NotificationService;
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
            recipientResolver, notificationService, meterRegistry);
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
        assertThat(bodyCap.getValue()).contains("My Show").contains("still needs a host");
        assertThat(counter("sent")).isEqualTo(1.0);
    }

    // ===== Body formatting =====

    @Test
    void buildMessageBody_defaultEpisode_includesShowAndTitleAndDay() {
        Hangout h = validHangout();
        h.setTitle("Pilot");
        EventSeries s = validSeries();

        String body = service.buildMessageBody(s, h);

        assertThat(body).contains("My Show").contains("Pilot").contains("airs").contains("needs a host");
    }

    @Test
    void buildMessageBody_tbaTitle_usesTbaFormat() {
        Hangout h = validHangout();
        h.setTitle("TBA");
        EventSeries s = validSeries();

        String body = service.buildMessageBody(s, h);

        assertThat(body).contains("'s My Show episode still needs a host!");
        assertThat(body).doesNotContain("TBA");
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
    void buildMessageBody_combinedDouble_usesCombinedFormat() {
        Hangout h = validHangout();
        h.setCombinedExternalIds(List.of("ep1", "ep2"));
        EventSeries s = validSeries();

        String body = service.buildMessageBody(s, h);

        assertThat(body).contains("double My Show episode still needs a host!");
    }

    @Test
    void buildMessageBody_combinedTriple_usesCombinedFormat() {
        Hangout h = validHangout();
        h.setCombinedExternalIds(List.of("ep1", "ep2", "ep3"));
        EventSeries s = validSeries();

        String body = service.buildMessageBody(s, h);

        assertThat(body).contains("triple My Show episode still needs a host!");
    }
}
