package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.model.EventSeries;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.model.InterestLevel;
import com.bbthechange.inviter.model.SeriesPointer;
import com.bbthechange.inviter.repository.EventSeriesRepository;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.NotificationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WatchPartyHostCheckServiceTest {

    @Mock
    private EventSeriesRepository eventSeriesRepository;

    @Mock
    private HangoutRepository hangoutRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    private WatchPartyHostCheckService service;

    private static final String SERIES_ID = "00000000-0000-0000-0000-000000000300";
    private static final String GROUP_ID = "00000000-0000-0000-0000-000000000201";
    private static final String HANGOUT_ID = "00000000-0000-0000-0000-000000000100";
    private static final String USER_ID = "00000000-0000-0000-0000-000000000002";

    @BeforeEach
    void setUp() {
        lenient().when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);
        lenient().when(meterRegistry.gauge(anyString(), any(Number.class))).thenReturn(0);
        service = new WatchPartyHostCheckService(
                eventSeriesRepository,
                hangoutRepository,
                groupRepository,
                notificationService,
                meterRegistry
        );
    }

    @Test
    void checkHostlessHangouts_WithNoWatchPartySeries_DoesNothing() {
        // Given
        when(eventSeriesRepository.findAllWatchPartySeries()).thenReturn(List.of());

        // When
        service.checkHostlessHangouts();

        // Then - early return before metrics, so no meter calls expected
        verifyNoInteractions(hangoutRepository);
        verifyNoInteractions(notificationService);
    }

    @Test
    void checkHostlessHangouts_WithHostlessHangoutIn24To48HourWindow_NotifiesUsers() {
        // Given
        long now = Instant.now().getEpochSecond();
        long hangoutStart = now + (30 * 60 * 60); // 30 hours from now (within 24-48 hour window)

        EventSeries series = createWatchPartySeries();
        series.setHangoutIds(new ArrayList<>(List.of(HANGOUT_ID)));

        Hangout hangout = createHostlessHangout();
        hangout.setStartTimestamp(hangoutStart);

        SeriesPointer pointer = createSeriesPointerWithGoingUser();

        when(eventSeriesRepository.findAllWatchPartySeries()).thenReturn(List.of(series));
        when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(hangout));
        when(groupRepository.findSeriesPointer(GROUP_ID, SERIES_ID)).thenReturn(Optional.of(pointer));

        // When
        service.checkHostlessHangouts();

        // Then
        verify(notificationService).notifyWatchPartyUpdate(
            anySet(),
            eq(SERIES_ID),
            contains("needs a host")
        );
        verify(meterRegistry).counter("watchparty_host_check_total", "status", "success");
    }

    @Test
    void checkHostlessHangouts_WithHangoutTooSoon_SkipsNotification() {
        // Given: Hangout is within 24 hours (too soon)
        long now = Instant.now().getEpochSecond();
        long hangoutStart = now + (12 * 60 * 60); // 12 hours from now (before 24-hour window)

        EventSeries series = createWatchPartySeries();
        series.setHangoutIds(new ArrayList<>(List.of(HANGOUT_ID)));

        Hangout hangout = createHostlessHangout();
        hangout.setStartTimestamp(hangoutStart);

        when(eventSeriesRepository.findAllWatchPartySeries()).thenReturn(List.of(series));
        when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(hangout));

        // When
        service.checkHostlessHangouts();

        // Then
        verifyNoInteractions(notificationService);
        verify(meterRegistry).counter("watchparty_host_check_total", "status", "success");
    }

    @Test
    void checkHostlessHangouts_WithHangoutTooFarAway_SkipsNotification() {
        // Given: Hangout is beyond 48 hours
        long now = Instant.now().getEpochSecond();
        long hangoutStart = now + (60 * 60 * 60); // 60 hours from now (beyond 48-hour window)

        EventSeries series = createWatchPartySeries();
        series.setHangoutIds(new ArrayList<>(List.of(HANGOUT_ID)));

        Hangout hangout = createHostlessHangout();
        hangout.setStartTimestamp(hangoutStart);

        when(eventSeriesRepository.findAllWatchPartySeries()).thenReturn(List.of(series));
        when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(hangout));

        // When
        service.checkHostlessHangouts();

        // Then
        verifyNoInteractions(notificationService);
        verify(meterRegistry).counter("watchparty_host_check_total", "status", "success");
    }

    @Test
    void checkHostlessHangouts_WithHangoutHavingHost_SkipsNotification() {
        // Given: Hangout has a host assigned
        long now = Instant.now().getEpochSecond();
        long hangoutStart = now + (30 * 60 * 60); // 30 hours from now

        EventSeries series = createWatchPartySeries();
        series.setHangoutIds(new ArrayList<>(List.of(HANGOUT_ID)));

        Hangout hangout = new Hangout();
        hangout.setHangoutId(HANGOUT_ID);
        hangout.setTitle("Test Hangout");
        hangout.setStartTimestamp(hangoutStart);
        hangout.setHostAtPlaceUserId("host-user-id"); // Has a host

        when(eventSeriesRepository.findAllWatchPartySeries()).thenReturn(List.of(series));
        when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(hangout));

        // When
        service.checkHostlessHangouts();

        // Then
        verifyNoInteractions(notificationService);
        verify(meterRegistry).counter("watchparty_host_check_total", "status", "success");
    }

    @Test
    void checkHostlessHangouts_WithNoInterestedUsers_DoesNotNotify() {
        // Given: No users with GOING or INTERESTED status
        long now = Instant.now().getEpochSecond();
        long hangoutStart = now + (30 * 60 * 60);

        EventSeries series = createWatchPartySeries();
        series.setHangoutIds(new ArrayList<>(List.of(HANGOUT_ID)));

        Hangout hangout = createHostlessHangout();
        hangout.setStartTimestamp(hangoutStart);

        SeriesPointer pointer = new SeriesPointer();
        InterestLevel notGoing = new InterestLevel();
        notGoing.setUserId(USER_ID);
        notGoing.setStatus("NOT_GOING");
        pointer.setInterestLevels(List.of(notGoing));

        when(eventSeriesRepository.findAllWatchPartySeries()).thenReturn(List.of(series));
        when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(hangout));
        when(groupRepository.findSeriesPointer(GROUP_ID, SERIES_ID)).thenReturn(Optional.of(pointer));

        // When
        service.checkHostlessHangouts();

        // Then
        verifyNoInteractions(notificationService);
        verify(meterRegistry).counter("watchparty_host_check_total", "status", "success");
    }

    @Test
    void checkHostlessHangouts_WithException_EmitsErrorMetric() {
        // Given
        when(eventSeriesRepository.findAllWatchPartySeries()).thenThrow(new RuntimeException("Database error"));

        // When
        service.checkHostlessHangouts();

        // Then - verify error metric only
        verify(meterRegistry).counter("watchparty_host_check_total", "status", "error");
    }

    @Test
    void checkHostlessHangouts_WithMultipleSeries_ChecksAll() {
        // Given
        long now = Instant.now().getEpochSecond();
        long hangoutStart = now + (30 * 60 * 60);

        EventSeries series1 = createWatchPartySeries();
        series1.setSeriesId("series-1");
        series1.setHangoutIds(new ArrayList<>(List.of("hangout-1")));

        EventSeries series2 = createWatchPartySeries();
        series2.setSeriesId("series-2");
        series2.setHangoutIds(new ArrayList<>(List.of("hangout-2")));

        Hangout hangout1 = createHostlessHangout();
        hangout1.setHangoutId("hangout-1");
        hangout1.setStartTimestamp(hangoutStart);

        Hangout hangout2 = createHostlessHangout();
        hangout2.setHangoutId("hangout-2");
        hangout2.setStartTimestamp(hangoutStart);

        SeriesPointer pointer = createSeriesPointerWithGoingUser();

        when(eventSeriesRepository.findAllWatchPartySeries()).thenReturn(List.of(series1, series2));
        when(hangoutRepository.findHangoutById("hangout-1")).thenReturn(Optional.of(hangout1));
        when(hangoutRepository.findHangoutById("hangout-2")).thenReturn(Optional.of(hangout2));
        when(groupRepository.findSeriesPointer(eq(GROUP_ID), anyString())).thenReturn(Optional.of(pointer));

        // When
        service.checkHostlessHangouts();

        // Then
        verify(notificationService, times(2)).notifyWatchPartyUpdate(
            anySet(),
            anyString(),
            contains("needs a host")
        );
        verify(meterRegistry).counter("watchparty_host_check_total", "status", "success");
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    private EventSeries createWatchPartySeries() {
        EventSeries series = new EventSeries();
        series.setSeriesId(SERIES_ID);
        series.setGroupId(GROUP_ID);
        series.setEventSeriesType("WATCH_PARTY");
        return series;
    }

    private Hangout createHostlessHangout() {
        Hangout hangout = new Hangout();
        hangout.setHangoutId(HANGOUT_ID);
        hangout.setTitle("Test Episode");
        hangout.setHostAtPlaceUserId(null); // No host
        return hangout;
    }

    private SeriesPointer createSeriesPointerWithGoingUser() {
        SeriesPointer pointer = new SeriesPointer();
        InterestLevel interestLevel = new InterestLevel();
        interestLevel.setUserId(USER_ID);
        interestLevel.setStatus("GOING");
        pointer.setInterestLevels(List.of(interestLevel));
        return pointer;
    }
}
