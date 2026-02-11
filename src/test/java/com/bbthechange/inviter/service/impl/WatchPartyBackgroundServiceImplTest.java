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
    private PointerUpdateService pointerUpdateService;

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
                meterRegistry,
                pointerUpdateService
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

        String groupId = "c8c3f5d4-5e8b-4c2a-a9f2-b3c2d1e4f5a6";

        Hangout hangout = new Hangout();
        hangout.setHangoutId("a1b2c3d4-e5f6-47c8-9d1e-2f3a4b5c6d7e");
        hangout.setTitle("Old Title");
        hangout.setIsGeneratedTitle(true);
        hangout.setTitleNotificationSent(false);
        hangout.setStartTimestamp(System.currentTimeMillis() / 1000 + 3600); // Future hangout
        hangout.setEndTimestamp(System.currentTimeMillis() / 1000 + 7200);
        hangout.setAssociatedGroups(List.of(groupId));
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

        // Verify pointer update uses upsert (read-modify-write) instead of PutItem
        verify(pointerUpdateService).upsertPointerWithRetry(
            eq(groupId), eq(hangout.getHangoutId()), eq(hangout), any(), eq("title update"));

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
    void processUpdateTitle_WhenTitleNotificationAlreadySent_StillUpdatesTitle() {
        // Given
        UpdateTitleMessage message = new UpdateTitleMessage("456", "New Episode Title");

        String seriesId = "d7e8f9a0-b1c2-43d4-a5f6-7c8d9e0f1a2b";
        String groupId = "c8c3f5d4-5e8b-4c2a-a9f2-b3c2d1e4f5a6";

        Hangout hangout = new Hangout();
        hangout.setHangoutId("a1b2c3d4-e5f6-47c8-9d1e-2f3a4b5c6d7e");
        hangout.setTitle("Old Title");
        hangout.setIsGeneratedTitle(true);
        hangout.setTitleNotificationSent(true); // Already notified
        hangout.setStartTimestamp(System.currentTimeMillis() / 1000 + 3600);
        hangout.setEndTimestamp(System.currentTimeMillis() / 1000 + 7200);
        hangout.setAssociatedGroups(List.of(groupId));
        hangout.setSeriesId(seriesId);

        when(hangoutRepository.findAllByExternalIdAndSource("456", "TVMAZE"))
                .thenReturn(List.of(hangout));

        // When
        service.processUpdateTitle(message);

        // Then - title IS updated
        ArgumentCaptor<Hangout> hangoutCaptor = ArgumentCaptor.forClass(Hangout.class);
        verify(hangoutRepository).save(hangoutCaptor.capture());
        assertEquals("New Episode Title", hangoutCaptor.getValue().getTitle());

        // But notification is NOT sent
        verify(notificationService, never()).notifyWatchPartyUpdate(anySet(), anyString(), anyString());
    }

    @Test
    void processUpdateTitle_WhenTitleUnchanged_SkipsUpdate() {
        // Given
        UpdateTitleMessage message = new UpdateTitleMessage("456", "Same Title");

        Hangout hangout = new Hangout();
        hangout.setHangoutId("a1b2c3d4-e5f6-47c8-9d1e-2f3a4b5c6d7e");
        hangout.setTitle("Same Title"); // Same as new title
        hangout.setIsGeneratedTitle(true);
        hangout.setTitleNotificationSent(false);
        hangout.setStartTimestamp(System.currentTimeMillis() / 1000 + 3600);

        when(hangoutRepository.findAllByExternalIdAndSource("456", "TVMAZE"))
                .thenReturn(List.of(hangout));

        // When
        service.processUpdateTitle(message);

        // Then - no save since title hasn't changed
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
    void processUpdateTitle_PointerUsesUpsertInsteadOfPutItem() {
        // Given
        UpdateTitleMessage message = new UpdateTitleMessage("456", "New Episode Title");

        TimeInfo timeInfo = new TimeInfo();
        timeInfo.setStartTime("2025-02-14T20:00:00-05:00");
        timeInfo.setEndTime("2025-02-14T21:00:00-05:00");

        String groupId = "c8c3f5d4-5e8b-4c2a-a9f2-b3c2d1e4f5a6";
        String hangoutId = "a1b2c3d4-e5f6-47c8-9d1e-2f3a4b5c6d7e";

        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setTitle("Old Title");
        hangout.setIsGeneratedTitle(true);
        hangout.setTitleNotificationSent(false);
        hangout.setStartTimestamp(System.currentTimeMillis() / 1000 + 3600); // Future hangout
        hangout.setEndTimestamp(System.currentTimeMillis() / 1000 + 7200);
        hangout.setAssociatedGroups(List.of(groupId));
        hangout.setTimeInput(timeInfo);

        when(hangoutRepository.findAllByExternalIdAndSource("456", "TVMAZE"))
                .thenReturn(List.of(hangout));

        // When
        service.processUpdateTitle(message);

        // Then - Verify upsert is used (preserves collections) instead of PutItem (which wipes them)
        verify(pointerUpdateService).upsertPointerWithRetry(
            eq(groupId), eq(hangoutId), eq(hangout), any(), eq("title update"));
        // Direct saveHangoutPointer should NOT be called for title updates
        verify(groupRepository, never()).saveHangoutPointer(any(HangoutPointer.class));
    }

    @Test
    void processUpdateTitle_WithNullNewTitle_ReturnsEarlyWithMetric() {
        // Given
        UpdateTitleMessage message = new UpdateTitleMessage("456", null);

        Hangout hangout = new Hangout();
        hangout.setHangoutId("a1b2c3d4-e5f6-47c8-9d1e-2f3a4b5c6d7e");
        hangout.setTitle("Old Title");
        hangout.setIsGeneratedTitle(true);
        hangout.setTitleNotificationSent(false);
        hangout.setStartTimestamp(System.currentTimeMillis() / 1000 + 3600); // Future hangout

        when(hangoutRepository.findAllByExternalIdAndSource("456", "TVMAZE"))
                .thenReturn(List.of(hangout));

        // When
        service.processUpdateTitle(message);

        // Then
        verify(hangoutRepository, never()).save(any(Hangout.class));
        verify(meterRegistry).counter("watchparty_background_total", "action", "update_title", "status", "invalid_message");
    }

    @Test
    void processUpdateTitle_WhenAlreadyNotified_PointerAndSeriesPointerStillUpdated() {
        // Given
        UpdateTitleMessage message = new UpdateTitleMessage("456", "New Episode Title");

        String hangoutId = "a1b2c3d4-e5f6-47c8-9d1e-2f3a4b5c6d7e";
        String groupId = "c8c3f5d4-5e8b-4c2a-a9f2-b3c2d1e4f5a6";
        String seriesId = "d7e8f9a0-b1c2-43d4-a5f6-7c8d9e0f1a2b";

        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setTitle("Old Title");
        hangout.setIsGeneratedTitle(true);
        hangout.setTitleNotificationSent(true); // Already notified
        hangout.setStartTimestamp(System.currentTimeMillis() / 1000 + 3600);
        hangout.setEndTimestamp(System.currentTimeMillis() / 1000 + 7200);
        hangout.setAssociatedGroups(List.of(groupId));
        hangout.setSeriesId(seriesId);

        when(hangoutRepository.findAllByExternalIdAndSource("456", "TVMAZE"))
                .thenReturn(List.of(hangout));

        // Create a HangoutPointer returned by findHangoutPointer after upsert
        HangoutPointer updatedPointer = new HangoutPointer(groupId, hangoutId, "New Episode Title");
        when(groupRepository.findHangoutPointer(groupId, hangoutId))
                .thenReturn(Optional.of(updatedPointer));

        // Create a SeriesPointer with a parts list containing one HangoutPointer
        SeriesPointer seriesPointer = new SeriesPointer();
        HangoutPointer existingPart = new HangoutPointer(groupId, hangoutId, "Old Title");
        seriesPointer.setParts(new ArrayList<>(List.of(existingPart)));

        when(groupRepository.findSeriesPointer(groupId, seriesId))
                .thenReturn(Optional.of(seriesPointer));

        // When
        service.processUpdateTitle(message);

        // Then - hangout IS saved
        verify(hangoutRepository).save(any(Hangout.class));

        // HangoutPointer IS updated via upsert (not direct PutItem)
        verify(pointerUpdateService).upsertPointerWithRetry(
            eq(groupId), eq(hangoutId), eq(hangout), any(), eq("title update"));

        // SeriesPointer IS saved (denormalized parts updated)
        verify(groupRepository).saveSeriesPointer(any(SeriesPointer.class));

        // Group timestamps ARE updated
        verify(groupTimestampService).updateGroupTimestamps(anyList());
    }

    @Test
    void processUpdateTitle_WhenAlreadyNotified_TitleNotificationSentStaysTrue() {
        // Given
        UpdateTitleMessage message = new UpdateTitleMessage("456", "Second Title Change");

        String groupId = "c8c3f5d4-5e8b-4c2a-a9f2-b3c2d1e4f5a6";

        Hangout hangout = new Hangout();
        hangout.setHangoutId("a1b2c3d4-e5f6-47c8-9d1e-2f3a4b5c6d7e");
        hangout.setTitle("First Updated Title");
        hangout.setIsGeneratedTitle(true);
        hangout.setTitleNotificationSent(true); // Already notified
        hangout.setStartTimestamp(System.currentTimeMillis() / 1000 + 3600);
        hangout.setEndTimestamp(System.currentTimeMillis() / 1000 + 7200);
        hangout.setAssociatedGroups(List.of(groupId));

        when(hangoutRepository.findAllByExternalIdAndSource("456", "TVMAZE"))
                .thenReturn(List.of(hangout));

        // When
        service.processUpdateTitle(message);

        // Then
        ArgumentCaptor<Hangout> hangoutCaptor = ArgumentCaptor.forClass(Hangout.class);
        verify(hangoutRepository).save(hangoutCaptor.capture());

        Hangout savedHangout = hangoutCaptor.getValue();
        assertTrue(savedHangout.getTitleNotificationSent(), "titleNotificationSent should remain true");
        assertEquals("Second Title Change", savedHangout.getTitle());
    }
}
