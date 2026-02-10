package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.TimeInfo;
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
        ArgumentCaptor<HangoutPointer> pointerCaptor = ArgumentCaptor.forClass(HangoutPointer.class);
        verify(hangoutRepository).save(any(Hangout.class));
        verify(groupRepository).saveHangoutPointer(pointerCaptor.capture());
        verify(eventSeriesRepository).save(any(EventSeries.class));
        verify(groupTimestampService).updateGroupTimestamps(anyList());
        verify(meterRegistry).counter("watchparty_background_total", "action", "new_episode", "status", "success");

        // Verify pointer has all required fields
        HangoutPointer savedPointer = pointerCaptor.getValue();
        assertEquals("ACTIVE", savedPointer.getStatus());
        assertNotNull(savedPointer.getTimeInput());
        assertNotNull(savedPointer.getTimeInput().getStartTime());
        assertNotNull(savedPointer.getTimeInput().getEndTime());
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

    @Test
    void processNewEpisode_HangoutHasTimeInput() {
        // Given
        EpisodeData episode = new EpisodeData(456, "Drag Queens For Change", 1705363200L);
        episode.setRuntime(90);

        NewEpisodeMessage message = new NewEpisodeMessage("TVMAZE#SHOW#123|SEASON#1", episode);

        EventSeries series = new EventSeries("RuPaul Season 18", null, "c8c3f5d4-5e8b-4c2a-a9f2-b3c2d1e4f5a6");
        series.setDefaultTime("20:00");
        series.setTimezone("America/New_York");
        series.setHangoutIds(new ArrayList<>());

        when(eventSeriesRepository.findAllByExternalIdAndSource("123", "TVMAZE"))
                .thenReturn(List.of(series));

        // When
        service.processNewEpisode(message);

        // Then - Verify the Hangout created has timeInput populated with ISO-8601 strings
        ArgumentCaptor<Hangout> hangoutCaptor = ArgumentCaptor.forClass(Hangout.class);
        verify(hangoutRepository).save(hangoutCaptor.capture());

        Hangout savedHangout = hangoutCaptor.getValue();
        assertNotNull(savedHangout.getTimeInput(), "Hangout should have timeInput set");
        assertNotNull(savedHangout.getTimeInput().getStartTime(), "timeInput.startTime should be set");
        assertNotNull(savedHangout.getTimeInput().getEndTime(), "timeInput.endTime should be set");

        // Verify ISO-8601 format
        assertTrue(savedHangout.getTimeInput().getStartTime().contains("T"),
                "startTime should be ISO-8601 format");
        assertTrue(savedHangout.getTimeInput().getEndTime().contains("T"),
                "endTime should be ISO-8601 format");
    }

    // ============================================================================
    // processUpdateTitle Tests
    // ============================================================================

    @Test
    void processUpdateTitle_WithValidHangout_UpdatesTitle() {
        // Given
        UpdateTitleMessage message = new UpdateTitleMessage("456", "New Episode Title");

        TimeInfo timeInfo = new TimeInfo();
        timeInfo.setStartTime("2025-02-14T20:00:00-05:00");
        timeInfo.setEndTime("2025-02-14T21:00:00-05:00");

        Hangout hangout = new Hangout();
        hangout.setHangoutId("a1b2c3d4-e5f6-47c8-9d1e-2f3a4b5c6d7e");
        hangout.setTitle("Old Title");
        hangout.setIsGeneratedTitle(true);
        hangout.setTitleNotificationSent(false);
        hangout.setStartTimestamp(System.currentTimeMillis() / 1000 + 3600); // Future hangout
        hangout.setEndTimestamp(System.currentTimeMillis() / 1000 + 7200);
        hangout.setAssociatedGroups(List.of("c8c3f5d4-5e8b-4c2a-a9f2-b3c2d1e4f5a6"));
        hangout.setTimeInput(timeInfo);

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

        ArgumentCaptor<HangoutPointer> pointerCaptor = ArgumentCaptor.forClass(HangoutPointer.class);
        verify(groupRepository).saveHangoutPointer(pointerCaptor.capture());

        // Verify pointer has status, timeInput, and location
        HangoutPointer savedPointer = pointerCaptor.getValue();
        assertEquals("ACTIVE", savedPointer.getStatus());
        assertNotNull(savedPointer.getTimeInput());
        assertNotNull(savedPointer.getTimeInput().getStartTime());
        assertNotNull(savedPointer.getTimeInput().getEndTime());

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

    // ============================================================================
    // processNewEpisode Pointer Denormalization Tests
    // ============================================================================

    @Test
    void processNewEpisode_WithNullTimezone_SkipsTimeInfoCreation() {
        // Given
        EpisodeData episode = new EpisodeData(456, "Pilot", 1705363200L);
        episode.setRuntime(60);

        NewEpisodeMessage message = new NewEpisodeMessage("TVMAZE#SHOW#123|SEASON#1", episode);

        EventSeries series = new EventSeries("Test Show Season 1", null, "c8c3f5d4-5e8b-4c2a-a9f2-b3c2d1e4f5a6");
        series.setDefaultTime("20:00");
        series.setTimezone(null); // Null timezone
        series.setHangoutIds(new ArrayList<>());

        when(eventSeriesRepository.findAllByExternalIdAndSource("123", "TVMAZE"))
                .thenReturn(List.of(series));

        // When
        service.processNewEpisode(message);

        // Then - Hangout is saved but timeInput is null (graceful fallback)
        ArgumentCaptor<Hangout> hangoutCaptor = ArgumentCaptor.forClass(Hangout.class);
        verify(hangoutRepository).save(hangoutCaptor.capture());

        Hangout savedHangout = hangoutCaptor.getValue();
        assertNull(savedHangout.getTimeInput(), "Hangout timeInput should be null when timezone is null");

        // Verify HangoutPointer's timeInput is also null
        ArgumentCaptor<HangoutPointer> pointerCaptor = ArgumentCaptor.forClass(HangoutPointer.class);
        verify(groupRepository).saveHangoutPointer(pointerCaptor.capture());

        HangoutPointer savedPointer = pointerCaptor.getValue();
        assertNull(savedPointer.getTimeInput(), "HangoutPointer timeInput should be null when timezone is null");
        assertEquals("ACTIVE", savedPointer.getStatus(), "HangoutPointer status should still be ACTIVE");
    }

    @Test
    void processNewEpisode_PointerHasCorrectGsi1sk() {
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

        // Then - Verify gsi1sk is a valid numeric string (not "null")
        ArgumentCaptor<Hangout> hangoutCaptor = ArgumentCaptor.forClass(Hangout.class);
        verify(hangoutRepository).save(hangoutCaptor.capture());

        ArgumentCaptor<HangoutPointer> pointerCaptor = ArgumentCaptor.forClass(HangoutPointer.class);
        verify(groupRepository).saveHangoutPointer(pointerCaptor.capture());

        Hangout savedHangout = hangoutCaptor.getValue();
        HangoutPointer savedPointer = pointerCaptor.getValue();

        assertNotNull(savedPointer.getGsi1sk(), "HangoutPointer gsi1sk should not be null");
        assertNotEquals("null", savedPointer.getGsi1sk(), "HangoutPointer gsi1sk should not be the string 'null'");

        // gsi1sk should match the hangout's startTimestamp
        assertEquals(String.valueOf(savedHangout.getStartTimestamp()), savedPointer.getGsi1sk(),
                "HangoutPointer gsi1sk should match hangout startTimestamp");
    }

    // ============================================================================
    // processUpdateTitle Pointer Denormalization Tests
    // ============================================================================

    @Test
    void processUpdateTitle_PointerPreservesExistingTimeInput() {
        // Given
        UpdateTitleMessage message = new UpdateTitleMessage("456", "New Episode Title");

        TimeInfo timeInfo = new TimeInfo();
        timeInfo.setStartTime("2025-02-14T20:00:00-05:00");
        timeInfo.setEndTime("2025-02-14T21:00:00-05:00");

        Hangout hangout = new Hangout();
        hangout.setHangoutId("a1b2c3d4-e5f6-47c8-9d1e-2f3a4b5c6d7e");
        hangout.setTitle("Old Title");
        hangout.setIsGeneratedTitle(true);
        hangout.setTitleNotificationSent(false);
        hangout.setStartTimestamp(System.currentTimeMillis() / 1000 + 3600); // Future hangout
        hangout.setEndTimestamp(System.currentTimeMillis() / 1000 + 7200);
        hangout.setAssociatedGroups(List.of("c8c3f5d4-5e8b-4c2a-a9f2-b3c2d1e4f5a6"));
        hangout.setTimeInput(timeInfo);

        when(hangoutRepository.findAllByExternalIdAndSource("456", "TVMAZE"))
                .thenReturn(List.of(hangout));

        // When
        service.processUpdateTitle(message);

        // Then - Verify the saved HangoutPointer has the same timeInput as the hangout
        ArgumentCaptor<HangoutPointer> pointerCaptor = ArgumentCaptor.forClass(HangoutPointer.class);
        verify(groupRepository).saveHangoutPointer(pointerCaptor.capture());

        HangoutPointer savedPointer = pointerCaptor.getValue();
        assertNotNull(savedPointer.getTimeInput(), "Pointer timeInput should not be null");
        assertEquals("2025-02-14T20:00:00-05:00", savedPointer.getTimeInput().getStartTime(),
                "Pointer timeInput.startTime should match hangout's value");
        assertEquals("2025-02-14T21:00:00-05:00", savedPointer.getTimeInput().getEndTime(),
                "Pointer timeInput.endTime should match hangout's value");
    }
}
