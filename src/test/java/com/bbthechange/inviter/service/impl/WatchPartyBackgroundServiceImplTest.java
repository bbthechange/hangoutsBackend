package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.watchparty.sqs.EpisodeData;
import com.bbthechange.inviter.dto.watchparty.sqs.NewEpisodeMessage;
import com.bbthechange.inviter.dto.watchparty.sqs.RemoveEpisodeMessage;
import com.bbthechange.inviter.dto.watchparty.sqs.UpdateTitleMessage;
import com.bbthechange.inviter.model.EventSeries;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.model.InterestLevel;
import com.bbthechange.inviter.model.SeriesPointer;
import com.bbthechange.inviter.repository.EventSeriesRepository;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.repository.SeasonRepository;
import com.bbthechange.inviter.service.GroupTimestampService;
import com.bbthechange.inviter.service.NotificationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WatchPartyBackgroundServiceImplTest {

    @Mock
    private EventSeriesRepository eventSeriesRepository;

    @Mock
    private HangoutRepository hangoutRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private SeasonRepository seasonRepository;

    @Mock
    private GroupTimestampService groupTimestampService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    private WatchPartyBackgroundServiceImpl service;

    @BeforeEach
    void setUp() {
        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);
        service = new WatchPartyBackgroundServiceImpl(
                eventSeriesRepository,
                hangoutRepository,
                groupRepository,
                seasonRepository,
                groupTimestampService,
                notificationService,
                meterRegistry
        );
    }

    // ============================================================================
    // processNewEpisode Tests
    // ============================================================================

    @Test
    void processNewEpisode_WithValidData_CreatesHangout() {
        // Given
        EpisodeData episode = new EpisodeData(456, "Pilot", 1705363200L);
        episode.setRuntime(60);

        NewEpisodeMessage message = new NewEpisodeMessage("TVMAZE#SHOW#123|SEASON#1", episode);

        EventSeries series = new EventSeries("Test Show Season 1", null, "c8c3f5d4-5e8b-4c2a-a9f2-b3c2d1e4f5a6");
        series.setDefaultTime("20:00");
        series.setTimezone("America/New_York");
        series.setHangoutIds(new ArrayList<>());

        when(eventSeriesRepository.findAllByExternalIdAndSource("123", "TVMAZE"))
                .thenReturn(List.of(series));

        // When
        service.processNewEpisode(message);

        // Then
        verify(hangoutRepository).save(any(Hangout.class));
        verify(groupRepository).saveHangoutPointer(any(HangoutPointer.class));
        verify(eventSeriesRepository).save(any(EventSeries.class));
        verify(groupTimestampService).updateGroupTimestamps(anyList());
        verify(meterRegistry).counter("watchparty_background_total", "action", "new_episode", "status", "success");
    }

    @Test
    void processNewEpisode_WithDeletedEpisode_SkipsCreation() {
        // Given
        EpisodeData episode = new EpisodeData(456, "Pilot", 1705363200L);
        NewEpisodeMessage message = new NewEpisodeMessage("TVMAZE#SHOW#123|SEASON#1", episode);

        EventSeries series = new EventSeries("Test Show Season 1", null, "c8c3f5d4-5e8b-4c2a-a9f2-b3c2d1e4f5a6");
        series.setDeletedEpisodeIds(Set.of("456")); // Episode marked as deleted

        when(eventSeriesRepository.findAllByExternalIdAndSource("123", "TVMAZE"))
                .thenReturn(List.of(series));

        // When
        service.processNewEpisode(message);

        // Then
        verify(hangoutRepository, never()).save(any(Hangout.class));
    }

    @Test
    void processNewEpisode_WithNoSeries_SkipsProcessing() {
        // Given
        EpisodeData episode = new EpisodeData(456, "Pilot", 1705363200L);
        NewEpisodeMessage message = new NewEpisodeMessage("TVMAZE#SHOW#123|SEASON#1", episode);

        when(eventSeriesRepository.findAllByExternalIdAndSource("123", "TVMAZE"))
                .thenReturn(List.of());

        // When
        service.processNewEpisode(message);

        // Then
        verify(hangoutRepository, never()).save(any(Hangout.class));
        verify(meterRegistry).counter("watchparty_background_total", "action", "new_episode", "status", "no_series");
    }

    @Test
    void processNewEpisode_WithInvalidSeasonKey_LogsError() {
        // Given
        EpisodeData episode = new EpisodeData(456, "Pilot", 1705363200L);
        NewEpisodeMessage message = new NewEpisodeMessage("invalid-key", episode);

        // When
        service.processNewEpisode(message);

        // Then
        verify(eventSeriesRepository, never()).findAllByExternalIdAndSource(anyString(), anyString());
        verify(meterRegistry).counter("watchparty_background_total", "action", "new_episode", "status", "invalid_key");
    }

    // ============================================================================
    // processUpdateTitle Tests
    // ============================================================================

    @Test
    void processUpdateTitle_WithValidHangout_UpdatesTitle() {
        // Given
        UpdateTitleMessage message = new UpdateTitleMessage("456", "New Episode Title");

        Hangout hangout = new Hangout();
        hangout.setHangoutId("a1b2c3d4-e5f6-47c8-9d1e-2f3a4b5c6d7e");
        hangout.setTitle("Old Title");
        hangout.setIsGeneratedTitle(true);
        hangout.setTitleNotificationSent(false);
        hangout.setStartTimestamp(System.currentTimeMillis() / 1000 + 3600); // Future hangout
        hangout.setAssociatedGroups(List.of("c8c3f5d4-5e8b-4c2a-a9f2-b3c2d1e4f5a6"));

        when(hangoutRepository.findAllByExternalIdAndSource("456", "TVMAZE"))
                .thenReturn(List.of(hangout));

        // When
        service.processUpdateTitle(message);

        // Then
        ArgumentCaptor<Hangout> hangoutCaptor = ArgumentCaptor.forClass(Hangout.class);
        verify(hangoutRepository).save(hangoutCaptor.capture());

        Hangout savedHangout = hangoutCaptor.getValue();
        assertEquals("New Episode Title", savedHangout.getTitle());
        assertTrue(savedHangout.getTitleNotificationSent());

        verify(groupRepository).saveHangoutPointer(any(HangoutPointer.class));
        verify(meterRegistry).counter("watchparty_background_total", "action", "update_title", "status", "success");
    }

    @Test
    void processUpdateTitle_WithCustomTitle_SkipsUpdate() {
        // Given
        UpdateTitleMessage message = new UpdateTitleMessage("456", "New Episode Title");

        Hangout hangout = new Hangout();
        hangout.setHangoutId("a1b2c3d4-e5f6-47c8-9d1e-2f3a4b5c6d7e");
        hangout.setTitle("Custom Title");
        hangout.setIsGeneratedTitle(false); // User set custom title
        hangout.setStartTimestamp(System.currentTimeMillis() / 1000 + 3600);

        when(hangoutRepository.findAllByExternalIdAndSource("456", "TVMAZE"))
                .thenReturn(List.of(hangout));

        // When
        service.processUpdateTitle(message);

        // Then
        verify(hangoutRepository, never()).save(any(Hangout.class));
    }

    @Test
    void processUpdateTitle_WithPastHangout_SkipsUpdate() {
        // Given
        UpdateTitleMessage message = new UpdateTitleMessage("456", "New Episode Title");

        Hangout hangout = new Hangout();
        hangout.setHangoutId("a1b2c3d4-e5f6-47c8-9d1e-2f3a4b5c6d7e");
        hangout.setTitle("Old Title");
        hangout.setIsGeneratedTitle(true);
        hangout.setTitleNotificationSent(false);
        hangout.setStartTimestamp(System.currentTimeMillis() / 1000 - 3600); // Past hangout

        when(hangoutRepository.findAllByExternalIdAndSource("456", "TVMAZE"))
                .thenReturn(List.of(hangout));

        // When
        service.processUpdateTitle(message);

        // Then
        verify(hangoutRepository, never()).save(any(Hangout.class));
    }

    @Test
    void processUpdateTitle_WithAlreadyNotified_SkipsUpdate() {
        // Given
        UpdateTitleMessage message = new UpdateTitleMessage("456", "New Episode Title");

        Hangout hangout = new Hangout();
        hangout.setHangoutId("a1b2c3d4-e5f6-47c8-9d1e-2f3a4b5c6d7e");
        hangout.setTitle("Old Title");
        hangout.setIsGeneratedTitle(true);
        hangout.setTitleNotificationSent(true); // Already notified
        hangout.setStartTimestamp(System.currentTimeMillis() / 1000 + 3600);

        when(hangoutRepository.findAllByExternalIdAndSource("456", "TVMAZE"))
                .thenReturn(List.of(hangout));

        // When
        service.processUpdateTitle(message);

        // Then
        verify(hangoutRepository, never()).save(any(Hangout.class));
    }

    @Test
    void processUpdateTitle_WithNoHangouts_SkipsProcessing() {
        // Given
        UpdateTitleMessage message = new UpdateTitleMessage("456", "New Episode Title");

        when(hangoutRepository.findAllByExternalIdAndSource("456", "TVMAZE"))
                .thenReturn(List.of());

        // When
        service.processUpdateTitle(message);

        // Then
        verify(hangoutRepository, never()).save(any(Hangout.class));
        verify(meterRegistry).counter("watchparty_background_total", "action", "update_title", "status", "no_hangouts");
    }

    @Test
    void processUpdateTitle_WithValidHangout_NotifiesInterestedUsers() {
        // Given
        UpdateTitleMessage message = new UpdateTitleMessage("456", "New Episode Title");

        String seriesId = "d7e8f9a0-b1c2-43d4-a5f6-7c8d9e0f1a2b";
        String groupId = "c8c3f5d4-5e8b-4c2a-a9f2-b3c2d1e4f5a6";

        Hangout hangout = new Hangout();
        hangout.setHangoutId("a1b2c3d4-e5f6-47c8-9d1e-2f3a4b5c6d7e");
        hangout.setTitle("Old Title");
        hangout.setIsGeneratedTitle(true);
        hangout.setTitleNotificationSent(false);
        hangout.setStartTimestamp(System.currentTimeMillis() / 1000 + 3600); // Future hangout
        hangout.setAssociatedGroups(List.of(groupId));
        hangout.setSeriesId(seriesId);

        EventSeries series = new EventSeries("Test Series", null, groupId);
        series.setSeriesId(seriesId);

        SeriesPointer pointer = new SeriesPointer();
        InterestLevel interestLevel = new InterestLevel();
        interestLevel.setUserId("b4c5d6e7-f8a9-4b0c-1d2e-3f4a5b6c7d8e");
        interestLevel.setStatus("GOING");
        pointer.setInterestLevels(List.of(interestLevel));

        when(hangoutRepository.findAllByExternalIdAndSource("456", "TVMAZE"))
                .thenReturn(List.of(hangout));
        when(eventSeriesRepository.findById(seriesId))
                .thenReturn(Optional.of(series));
        when(groupRepository.findSeriesPointer(groupId, seriesId))
                .thenReturn(Optional.of(pointer));

        // When
        service.processUpdateTitle(message);

        // Then
        verify(notificationService).notifyWatchPartyUpdate(
            anySet(),
            eq(seriesId),
            contains("renamed")
        );
    }

    // ============================================================================
    // processRemoveEpisode Tests
    // ============================================================================

    @Test
    void processRemoveEpisode_WithValidHangout_DeletesHangout() {
        // Given
        RemoveEpisodeMessage message = new RemoveEpisodeMessage("456");

        Hangout hangout = new Hangout();
        hangout.setHangoutId("a1b2c3d4-e5f6-47c8-9d1e-2f3a4b5c6d7e");
        hangout.setSeriesId("d7e8f9a0-b1c2-43d4-a5f6-7c8d9e0f1a2b");
        hangout.setAssociatedGroups(List.of("c8c3f5d4-5e8b-4c2a-a9f2-b3c2d1e4f5a6"));

        EventSeries series = new EventSeries("Test Series", null, "c8c3f5d4-5e8b-4c2a-a9f2-b3c2d1e4f5a6");
        series.setHangoutIds(new ArrayList<>(List.of("a1b2c3d4-e5f6-47c8-9d1e-2f3a4b5c6d7e")));

        when(hangoutRepository.findAllByExternalIdAndSource("456", "TVMAZE"))
                .thenReturn(List.of(hangout));
        when(eventSeriesRepository.findById("d7e8f9a0-b1c2-43d4-a5f6-7c8d9e0f1a2b"))
                .thenReturn(Optional.of(series));
        when(hangoutRepository.findPointersForHangout(hangout))
                .thenReturn(List.of());

        // When
        service.processRemoveEpisode(message);

        // Then
        verify(groupRepository).deleteHangoutPointer("c8c3f5d4-5e8b-4c2a-a9f2-b3c2d1e4f5a6", "a1b2c3d4-e5f6-47c8-9d1e-2f3a4b5c6d7e");
        verify(hangoutRepository).deleteHangout("a1b2c3d4-e5f6-47c8-9d1e-2f3a4b5c6d7e");
        verify(eventSeriesRepository).save(any(EventSeries.class));
        verify(groupTimestampService).updateGroupTimestamps(anyList());
        verify(meterRegistry).counter("watchparty_background_total", "action", "remove_episode", "status", "success");
    }

    @Test
    void processRemoveEpisode_WithNoHangouts_SkipsProcessing() {
        // Given
        RemoveEpisodeMessage message = new RemoveEpisodeMessage("456");

        when(hangoutRepository.findAllByExternalIdAndSource("456", "TVMAZE"))
                .thenReturn(List.of());

        // When
        service.processRemoveEpisode(message);

        // Then
        verify(hangoutRepository, never()).deleteHangout(any());
        verify(meterRegistry).counter("watchparty_background_total", "action", "remove_episode", "status", "no_hangouts");
    }

    @Test
    void processRemoveEpisode_NotifiesInterestedUsers() {
        // Given
        RemoveEpisodeMessage message = new RemoveEpisodeMessage("456");

        Hangout hangout = new Hangout();
        hangout.setHangoutId("a1b2c3d4-e5f6-47c8-9d1e-2f3a4b5c6d7e");
        hangout.setTitle("Pilot");
        hangout.setSeriesId("d7e8f9a0-b1c2-43d4-a5f6-7c8d9e0f1a2b");
        hangout.setAssociatedGroups(List.of("c8c3f5d4-5e8b-4c2a-a9f2-b3c2d1e4f5a6"));

        HangoutPointer pointer = new HangoutPointer("c8c3f5d4-5e8b-4c2a-a9f2-b3c2d1e4f5a6", "a1b2c3d4-e5f6-47c8-9d1e-2f3a4b5c6d7e", "Pilot");
        InterestLevel interestLevel = new InterestLevel();
        interestLevel.setUserId("b4c5d6e7-f8a9-4b0c-1d2e-3f4a5b6c7d8e");
        interestLevel.setStatus("GOING");
        pointer.setInterestLevels(List.of(interestLevel));

        when(hangoutRepository.findAllByExternalIdAndSource("456", "TVMAZE"))
                .thenReturn(List.of(hangout));
        when(hangoutRepository.findPointersForHangout(hangout))
                .thenReturn(List.of(pointer));
        when(eventSeriesRepository.findById("d7e8f9a0-b1c2-43d4-a5f6-7c8d9e0f1a2b"))
                .thenReturn(Optional.empty());

        // When
        service.processRemoveEpisode(message);

        // Then
        verify(notificationService).notifyWatchPartyUpdate(anySet(), eq("d7e8f9a0-b1c2-43d4-a5f6-7c8d9e0f1a2b"), contains("cancelled"));
    }
}
