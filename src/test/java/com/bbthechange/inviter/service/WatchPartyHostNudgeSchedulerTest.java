package com.bbthechange.inviter.service;

import com.bbthechange.inviter.client.EventBridgeSchedulerClient;
import com.bbthechange.inviter.model.EventSeries;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.repository.HangoutRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WatchPartyHostNudgeSchedulerTest {

    @Mock
    private EventBridgeSchedulerClient eventBridgeClient;

    @Mock
    private HangoutRepository hangoutRepository;

    private MeterRegistry meterRegistry;
    private WatchPartyHostNudgeScheduler scheduler;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        lenient().when(eventBridgeClient.isEnabled()).thenReturn(true);
        scheduler = new WatchPartyHostNudgeScheduler(eventBridgeClient, hangoutRepository, meterRegistry);
    }

    private double counter(String name, String status) {
        return meterRegistry.counter(name, "status", status).count();
    }

    private double activeGauge() {
        return meterRegistry.get("watchparty_host_nudge_schedules_active").gauge().value();
    }

    private static final String HG_1 = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String HG_2 = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    private static final String HG_3 = "cccccccc-cccc-cccc-cccc-cccccccccccc";
    private static final String HG_4 = "dddddddd-dddd-dddd-dddd-dddddddddddd";
    private static final String SERIES = "11111111-1111-1111-1111-111111111111";
    private static final String GROUP = "22222222-2222-2222-2222-222222222222";

    private Hangout futureHangout(String hangoutId, String seriesId) {
        // 60h in the future → fire time is 12h from now, safely > MIN_SCHEDULE_ADVANCE_SECONDS
        Hangout h = WatchPartyTestFixtures.inPersonHangout(hangoutId, seriesId);
        h.setStartTimestamp(Instant.now().getEpochSecond() + 60L * 3600);
        h.setHostNudgeScheduleName(null);
        h.setHostNudgeSentAt(null);
        h.setHostAtPlaceUserId(null);
        return h;
    }

    // ================= scheduleHostNudge =================

    @Test
    void scheduleHostNudge_disabled_silentSkip() {
        when(eventBridgeClient.isEnabled()).thenReturn(false);
        Hangout h = futureHangout(HG_1, SERIES);
        EventSeries s = WatchPartyTestFixtures.inPersonSeries(SERIES, GROUP);

        scheduler.scheduleHostNudge(h, s);

        verify(eventBridgeClient, never())
            .createOrUpdateSchedule(anyString(), anyString(), anyString(), anyBoolean());
        verifyNoInteractions(hangoutRepository);
        assertThat(counter("watchparty_host_nudge_schedule_created", "skipped_virtual")).isZero();
        assertThat(counter("watchparty_host_nudge_schedule_created", "success")).isZero();
    }

    @Test
    void scheduleHostNudge_virtualSeries_skipsWithCounter() {
        Hangout h = futureHangout(HG_1, SERIES);
        EventSeries s = WatchPartyTestFixtures.virtualSeries(SERIES, GROUP);

        scheduler.scheduleHostNudge(h, s);

        verify(eventBridgeClient, never())
            .createOrUpdateSchedule(anyString(), anyString(), anyString(), anyBoolean());
        verifyNoInteractions(hangoutRepository);
        assertThat(counter("watchparty_host_nudge_schedule_created", "skipped_virtual")).isEqualTo(1.0);
    }

    @Test
    void scheduleHostNudge_hostAlreadyClaimed_skipsWithCounter() {
        Hangout h = futureHangout(HG_1, SERIES);
        h.setHostAtPlaceUserId("user-claimed");
        EventSeries s = WatchPartyTestFixtures.inPersonSeries(SERIES, GROUP);

        scheduler.scheduleHostNudge(h, s);

        verify(eventBridgeClient, never())
            .createOrUpdateSchedule(anyString(), anyString(), anyString(), anyBoolean());
        verifyNoInteractions(hangoutRepository);
        assertThat(counter("watchparty_host_nudge_schedule_created", "skipped_has_host")).isEqualTo(1.0);
    }

    @Test
    void scheduleHostNudge_alreadySent_skipsWithCounter() {
        Hangout h = futureHangout(HG_1, SERIES);
        h.setHostNudgeSentAt(System.currentTimeMillis() - 60_000);
        EventSeries s = WatchPartyTestFixtures.inPersonSeries(SERIES, GROUP);

        scheduler.scheduleHostNudge(h, s);

        verify(eventBridgeClient, never())
            .createOrUpdateSchedule(anyString(), anyString(), anyString(), anyBoolean());
        verifyNoInteractions(hangoutRepository);
        assertThat(counter("watchparty_host_nudge_schedule_created", "skipped_past")).isEqualTo(1.0);
    }

    @Test
    void scheduleHostNudge_noStartTime_skipsWithCounter() {
        Hangout h = futureHangout(HG_1, SERIES);
        h.setStartTimestamp(null);
        EventSeries s = WatchPartyTestFixtures.inPersonSeries(SERIES, GROUP);

        scheduler.scheduleHostNudge(h, s);

        verify(eventBridgeClient, never())
            .createOrUpdateSchedule(anyString(), anyString(), anyString(), anyBoolean());
        verifyNoInteractions(hangoutRepository);
        assertThat(counter("watchparty_host_nudge_schedule_created", "skipped_past")).isEqualTo(1.0);
    }

    @Test
    void scheduleHostNudge_fireTimeInPast_skipsWithCounter() {
        Hangout h = futureHangout(HG_1, SERIES);
        // Start time only 10 minutes from now → fire time (48h before) is 47h 50m in the past
        h.setStartTimestamp(Instant.now().getEpochSecond() + 600);
        EventSeries s = WatchPartyTestFixtures.inPersonSeries(SERIES, GROUP);

        scheduler.scheduleHostNudge(h, s);

        verify(eventBridgeClient, never())
            .createOrUpdateSchedule(anyString(), anyString(), anyString(), anyBoolean());
        verify(hangoutRepository, never()).updateHostNudgeScheduleName(anyString(), anyString());
        assertThat(counter("watchparty_host_nudge_schedule_created", "skipped_past")).isEqualTo(1.0);
    }

    @Test
    void scheduleHostNudge_success_persistsExpectedNameAndPayload() {
        String hangoutId = HG_2;
        Hangout h = futureHangout(hangoutId, SERIES);
        EventSeries s = WatchPartyTestFixtures.inPersonSeries(SERIES, GROUP);

        scheduler.scheduleHostNudge(h, s);

        ArgumentCaptor<String> nameCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> exprCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> inputCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> expectedCap = ArgumentCaptor.forClass(Boolean.class);
        verify(eventBridgeClient).createOrUpdateSchedule(
            nameCap.capture(), exprCap.capture(), inputCap.capture(), expectedCap.capture());

        assertThat(nameCap.getValue()).isEqualTo("hostnudge-" + hangoutId);
        assertThat(expectedCap.getValue()).isFalse();
        assertThat(inputCap.getValue())
            .isEqualTo("{\"type\":\"WATCH_PARTY_HOST_NUDGE\",\"hangoutId\":\"" + hangoutId + "\"}");
        assertThat(exprCap.getValue()).startsWith("at(").endsWith(")");

        verify(hangoutRepository).updateHostNudgeScheduleName(hangoutId, "hostnudge-" + hangoutId);
        assertThat(counter("watchparty_host_nudge_schedule_created", "success")).isEqualTo(1.0);
    }

    @Test
    void scheduleHostNudge_existingScheduleName_useUpdateFirst() {
        String hangoutId = HG_3;
        Hangout h = futureHangout(hangoutId, SERIES);
        h.setHostNudgeScheduleName("hostnudge-" + hangoutId);
        EventSeries s = WatchPartyTestFixtures.inPersonSeries(SERIES, GROUP);

        scheduler.scheduleHostNudge(h, s);

        ArgumentCaptor<Boolean> expectedCap = ArgumentCaptor.forClass(Boolean.class);
        verify(eventBridgeClient).createOrUpdateSchedule(
            anyString(), anyString(), anyString(), expectedCap.capture());
        assertThat(expectedCap.getValue()).isTrue();
    }

    @Test
    void scheduleHostNudge_clientThrows_emitsErrorCounter() {
        String hangoutId = HG_4;
        Hangout h = futureHangout(hangoutId, SERIES);
        EventSeries s = WatchPartyTestFixtures.inPersonSeries(SERIES, GROUP);
        doThrow(new RuntimeException("boom"))
            .when(eventBridgeClient).createOrUpdateSchedule(anyString(), anyString(), anyString(), anyBoolean());

        scheduler.scheduleHostNudge(h, s);

        assertThat(counter("watchparty_host_nudge_schedule_created", "error")).isEqualTo(1.0);
        verify(hangoutRepository, never()).updateHostNudgeScheduleName(anyString(), anyString());
    }

    // ================= cancelHostNudge =================

    @Test
    void cancelHostNudge_disabled_silentSkip() {
        when(eventBridgeClient.isEnabled()).thenReturn(false);
        Hangout h = futureHangout(HG_1, SERIES);

        scheduler.cancelHostNudge(h);

        verify(eventBridgeClient, never()).deleteSchedule(anyString());
    }

    @Test
    void cancelHostNudge_withStoredName_deletesStoredName() {
        Hangout h = futureHangout(HG_1, SERIES);
        h.setHostNudgeScheduleName("custom-name");

        scheduler.cancelHostNudge(h);

        verify(eventBridgeClient).deleteSchedule("custom-name");
        assertThat(counter("watchparty_host_nudge_schedule_deleted", "success")).isEqualTo(1.0);
    }

    @Test
    void cancelHostNudge_withoutStoredName_deletesGeneratedName() {
        Hangout h = futureHangout(HG_2, SERIES);
        h.setHostNudgeScheduleName(null);

        scheduler.cancelHostNudge(h);

        verify(eventBridgeClient).deleteSchedule("hostnudge-" + HG_2);
    }

    @Test
    void cancelHostNudge_clientThrows_emitsErrorCounter() {
        Hangout h = futureHangout(HG_1, SERIES);
        h.setHostNudgeScheduleName("schedule-x");
        doThrow(new RuntimeException("boom")).when(eventBridgeClient).deleteSchedule(eq("schedule-x"));

        scheduler.cancelHostNudge(h);

        assertThat(counter("watchparty_host_nudge_schedule_deleted", "error")).isEqualTo(1.0);
    }

    // ================= active-schedules gauge =================

    @Test
    void activeGauge_startsAtZero() {
        assertThat(activeGauge()).isZero();
    }

    @Test
    void activeGauge_incrementsOnNewSchedule() {
        Hangout h = futureHangout(HG_1, SERIES);
        EventSeries s = WatchPartyTestFixtures.inPersonSeries(SERIES, GROUP);

        scheduler.scheduleHostNudge(h, s);

        assertThat(activeGauge()).isEqualTo(1.0);
    }

    @Test
    void activeGauge_doesNotIncrementOnUpdate() {
        // existing schedule name → expectedToExist=true → update path, no net new schedule
        Hangout h = futureHangout(HG_1, SERIES);
        h.setHostNudgeScheduleName("hostnudge-" + HG_1);
        EventSeries s = WatchPartyTestFixtures.inPersonSeries(SERIES, GROUP);

        scheduler.scheduleHostNudge(h, s);

        assertThat(activeGauge()).isZero();
    }

    @Test
    void activeGauge_doesNotIncrementOnSkippedOrError() {
        EventSeries virtual = WatchPartyTestFixtures.virtualSeries(SERIES, GROUP);
        scheduler.scheduleHostNudge(futureHangout(HG_1, SERIES), virtual);
        assertThat(activeGauge()).isZero();

        Hangout errH = futureHangout(HG_2, SERIES);
        doThrow(new RuntimeException("boom"))
            .when(eventBridgeClient).createOrUpdateSchedule(anyString(), anyString(), anyString(), anyBoolean());
        scheduler.scheduleHostNudge(errH, WatchPartyTestFixtures.inPersonSeries(SERIES, GROUP));
        assertThat(activeGauge()).isZero();
    }

    @Test
    void activeGauge_decrementsOnSuccessfulCancel() {
        Hangout h = futureHangout(HG_1, SERIES);
        EventSeries s = WatchPartyTestFixtures.inPersonSeries(SERIES, GROUP);
        scheduler.scheduleHostNudge(h, s);
        assertThat(activeGauge()).isEqualTo(1.0);

        h.setHostNudgeScheduleName("hostnudge-" + HG_1);
        scheduler.cancelHostNudge(h);

        assertThat(activeGauge()).isZero();
    }

    @Test
    void activeGauge_doesNotDecrementOnCancelError() {
        Hangout h = futureHangout(HG_1, SERIES);
        h.setHostNudgeScheduleName("schedule-x");
        doThrow(new RuntimeException("boom")).when(eventBridgeClient).deleteSchedule(eq("schedule-x"));

        scheduler.cancelHostNudge(h);

        assertThat(activeGauge()).isZero();
    }

    @Test
    void activeGauge_cancelWithoutStoredName_doesNotGoNegative() {
        // No prior scheduleHostNudge call → activeSchedules was never incremented.
        // Calling cancel must not push gauge below zero.
        Hangout h = futureHangout(HG_1, SERIES);
        h.setHostNudgeScheduleName(null);

        scheduler.cancelHostNudge(h);

        assertThat(activeGauge()).isZero();
    }

    @Test
    void activeGauge_pastFireTimeCleanup_decrementsWhenScheduleExisted() {
        // Set up: a hangout with an existing stored schedule, but fire time has passed.
        // The cleanup delete should decrement the gauge.
        Hangout h = futureHangout(HG_1, SERIES);
        EventSeries s = WatchPartyTestFixtures.inPersonSeries(SERIES, GROUP);
        scheduler.scheduleHostNudge(h, s);
        assertThat(activeGauge()).isEqualTo(1.0);

        // Now flip fire time into the past and re-call: the past-fire-time branch
        // sees existing schedule name and triggers cleanup delete.
        h.setHostNudgeScheduleName("hostnudge-" + HG_1);
        h.setStartTimestamp(Instant.now().getEpochSecond() + 600); // 10min away → fire time 47h50m past
        scheduler.scheduleHostNudge(h, s);

        assertThat(activeGauge()).isZero();
    }
}
