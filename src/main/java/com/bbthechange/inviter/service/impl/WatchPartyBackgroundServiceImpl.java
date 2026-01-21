package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.watchparty.sqs.*;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.*;
import com.bbthechange.inviter.service.GroupTimestampService;
import com.bbthechange.inviter.service.NotificationService;
import com.bbthechange.inviter.service.WatchPartyBackgroundService;
import com.bbthechange.inviter.util.InviterKeyFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of WatchPartyBackgroundService for processing episode events.
 * Handles creation of hangouts for new episodes, title updates, and episode removals.
 *
 * Only active when watchparty.sqs.enabled=true.
 */
@Service
@ConditionalOnProperty(name = "watchparty.sqs.enabled", havingValue = "true")
public class WatchPartyBackgroundServiceImpl implements WatchPartyBackgroundService {

    private static final Logger logger = LoggerFactory.getLogger(WatchPartyBackgroundServiceImpl.class);
    private static final String TVMAZE_SOURCE = "TVMAZE";

    private final EventSeriesRepository eventSeriesRepository;
    private final HangoutRepository hangoutRepository;
    private final GroupRepository groupRepository;
    private final SeasonRepository seasonRepository;
    private final GroupTimestampService groupTimestampService;
    private final NotificationService notificationService;
    private final MeterRegistry meterRegistry;

    public WatchPartyBackgroundServiceImpl(
            EventSeriesRepository eventSeriesRepository,
            HangoutRepository hangoutRepository,
            GroupRepository groupRepository,
            SeasonRepository seasonRepository,
            GroupTimestampService groupTimestampService,
            NotificationService notificationService,
            MeterRegistry meterRegistry) {
        this.eventSeriesRepository = eventSeriesRepository;
        this.hangoutRepository = hangoutRepository;
        this.groupRepository = groupRepository;
        this.seasonRepository = seasonRepository;
        this.groupTimestampService = groupTimestampService;
        this.notificationService = notificationService;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void processNewEpisode(NewEpisodeMessage message) {
        logger.info("Processing new episode: seasonKey={}, episodeId={}",
                message.getSeasonKey(), message.getEpisode().getEpisodeId());

        try {
            // Parse seasonKey to get showId
            Integer showId = parseShowIdFromSeasonKey(message.getSeasonKey());
            if (showId == null) {
                logger.error("Failed to parse showId from seasonKey: {}", message.getSeasonKey());
                meterRegistry.counter("watchparty_background_total", "action", "new_episode", "status", "invalid_key").increment();
                return;
            }

            // Find all EventSeries watching this show
            List<EventSeries> seriesList = eventSeriesRepository.findAllByExternalIdAndSource(
                    showId.toString(), TVMAZE_SOURCE);

            if (seriesList.isEmpty()) {
                logger.debug("No event series found watching show {}", showId);
                meterRegistry.counter("watchparty_background_total", "action", "new_episode", "status", "no_series").increment();
                return;
            }

            EpisodeData episode = message.getEpisode();
            int hangoutsCreated = 0;
            Set<String> groupsToUpdate = new HashSet<>();

            for (EventSeries series : seriesList) {
                // Check if episode was deleted by user
                if (series.getDeletedEpisodeIds() != null &&
                    series.getDeletedEpisodeIds().contains(episode.getEpisodeId().toString())) {
                    logger.debug("Episode {} was deleted from series {}, skipping",
                            episode.getEpisodeId(), series.getSeriesId());
                    continue;
                }

                // Create hangout for this series
                Hangout hangout = createHangoutFromEpisode(series, episode);
                HangoutPointer pointer = createHangoutPointer(hangout, series.getGroupId());

                // Save records
                hangoutRepository.save(hangout);
                groupRepository.saveHangoutPointer(pointer);

                // Add hangout to series
                series.addHangout(hangout.getHangoutId());
                updateSeriesTimestamps(series, hangout);
                eventSeriesRepository.save(series);

                // Update SeriesPointer.parts with the new HangoutPointer
                updateSeriesPointerWithNewPart(series, pointer);

                hangoutsCreated++;
                groupsToUpdate.add(series.getGroupId());

                // Notify interested users
                notifyInterestedUsers(series, "New episode: " + episode.getTitle());
            }

            // Update group timestamps
            if (!groupsToUpdate.isEmpty()) {
                groupTimestampService.updateGroupTimestamps(new ArrayList<>(groupsToUpdate));
            }

            logger.info("Created {} hangouts for new episode {}", hangoutsCreated, episode.getEpisodeId());
            meterRegistry.counter("watchparty_background_total", "action", "new_episode", "status", "success").increment();

        } catch (Exception e) {
            logger.error("Error processing new episode: {}", message, e);
            meterRegistry.counter("watchparty_background_total", "action", "new_episode", "status", "error").increment();
            // Don't re-throw - message will be acknowledged to prevent infinite retries
            // Errors are logged and metered for investigation
        }
    }

    @Override
    public void processUpdateTitle(UpdateTitleMessage message) {
        logger.info("Processing title update: externalId={}, newTitle={}",
                message.getExternalId(), message.getNewTitle());

        try {
            // Find all hangouts with this external ID
            List<Hangout> hangouts = hangoutRepository.findAllByExternalIdAndSource(
                    message.getExternalId(), TVMAZE_SOURCE);

            if (hangouts.isEmpty()) {
                logger.debug("No hangouts found for externalId: {}", message.getExternalId());
                meterRegistry.counter("watchparty_background_total", "action", "update_title", "status", "no_hangouts").increment();
                return;
            }

            long nowTimestamp = Instant.now().getEpochSecond();
            int updated = 0;
            Set<String> groupsToUpdate = new HashSet<>();

            for (Hangout hangout : hangouts) {
                // Skip if not using generated title
                if (!Boolean.TRUE.equals(hangout.getIsGeneratedTitle())) {
                    continue;
                }

                // Skip if already notified about title change
                if (Boolean.TRUE.equals(hangout.getTitleNotificationSent())) {
                    continue;
                }

                // Skip past hangouts
                if (hangout.getStartTimestamp() != null && hangout.getStartTimestamp() <= nowTimestamp) {
                    continue;
                }

                // Update title
                String oldTitle = hangout.getTitle();
                hangout.setTitle(message.getNewTitle());
                hangout.setTitleNotificationSent(true);
                hangoutRepository.save(hangout);

                // Update HangoutPointer
                if (hangout.getAssociatedGroups() != null) {
                    for (String groupId : hangout.getAssociatedGroups()) {
                        HangoutPointer updatedPointer = createHangoutPointer(hangout, groupId);
                        groupRepository.saveHangoutPointer(updatedPointer);
                        groupsToUpdate.add(groupId);

                        // Update the corresponding part in SeriesPointer.parts
                        if (hangout.getSeriesId() != null) {
                            updateHangoutInSeriesPointer(groupId, hangout.getSeriesId(), updatedPointer);
                        }
                    }
                }

                updated++;
                logger.debug("Updated hangout {} title from '{}' to '{}'",
                        hangout.getHangoutId(), oldTitle, message.getNewTitle());

                // Notify interested users about title change
                if (hangout.getSeriesId() != null) {
                    notifyTitleUpdate(hangout.getSeriesId(), oldTitle, message.getNewTitle());
                }
            }

            // Update group timestamps
            if (!groupsToUpdate.isEmpty()) {
                groupTimestampService.updateGroupTimestamps(new ArrayList<>(groupsToUpdate));
            }

            logger.info("Updated {} hangout titles for externalId {}", updated, message.getExternalId());
            meterRegistry.counter("watchparty_background_total", "action", "update_title", "status", "success").increment();

        } catch (Exception e) {
            logger.error("Error processing title update: {}", message, e);
            meterRegistry.counter("watchparty_background_total", "action", "update_title", "status", "error").increment();
            // Don't re-throw - message will be acknowledged to prevent infinite retries
        }
    }

    @Override
    public void processRemoveEpisode(RemoveEpisodeMessage message) {
        logger.info("Processing episode removal: externalId={}", message.getExternalId());

        try {
            // Find all hangouts with this external ID
            List<Hangout> hangouts = hangoutRepository.findAllByExternalIdAndSource(
                    message.getExternalId(), TVMAZE_SOURCE);

            if (hangouts.isEmpty()) {
                logger.debug("No hangouts found for externalId: {}", message.getExternalId());
                meterRegistry.counter("watchparty_background_total", "action", "remove_episode", "status", "no_hangouts").increment();
                return;
            }

            int deleted = 0;
            Set<String> groupsToUpdate = new HashSet<>();

            for (Hangout hangout : hangouts) {
                String hangoutId = hangout.getHangoutId();
                String seriesId = hangout.getSeriesId();

                // Delete hangout pointer for each associated group
                if (hangout.getAssociatedGroups() != null) {
                    for (String groupId : hangout.getAssociatedGroups()) {
                        groupRepository.deleteHangoutPointer(groupId, hangoutId);
                        groupsToUpdate.add(groupId);
                    }
                }

                // Delete hangout
                hangoutRepository.deleteHangout(hangoutId);

                // Remove from series and update SeriesPointer
                if (seriesId != null) {
                    eventSeriesRepository.findById(seriesId).ifPresent(series -> {
                        series.removeHangout(hangoutId);
                        eventSeriesRepository.save(series);

                        // Update SeriesPointer.parts to remove the deleted hangout
                        removeHangoutFromSeriesPointer(series, hangoutId);
                    });
                }

                deleted++;

                // Notify affected users
                notifyHangoutCancellation(hangout);
            }

            // Update group timestamps
            if (!groupsToUpdate.isEmpty()) {
                groupTimestampService.updateGroupTimestamps(new ArrayList<>(groupsToUpdate));
            }

            logger.info("Deleted {} hangouts for removed episode {}", deleted, message.getExternalId());
            meterRegistry.counter("watchparty_background_total", "action", "remove_episode", "status", "success").increment();

        } catch (Exception e) {
            logger.error("Error processing episode removal: {}", message, e);
            meterRegistry.counter("watchparty_background_total", "action", "remove_episode", "status", "error").increment();
            // Don't re-throw - message will be acknowledged to prevent infinite retries
        }
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    private Integer parseShowIdFromSeasonKey(String seasonKey) {
        // Format: "TVMAZE#SHOW#{showId}|SEASON#{seasonNumber}"
        if (seasonKey == null) return null;

        try {
            String[] parts = seasonKey.split("\\|");
            if (parts.length < 1) return null;

            // Parse "TVMAZE#SHOW#{showId}"
            String[] showParts = parts[0].split("#");
            if (showParts.length >= 3) {
                return Integer.parseInt(showParts[2]);
            }
        } catch (NumberFormatException e) {
            logger.warn("Failed to parse showId from seasonKey: {}", seasonKey);
        }
        return null;
    }

    private Hangout createHangoutFromEpisode(EventSeries series, EpisodeData episode) {
        Hangout hangout = new Hangout();
        hangout.setHangoutId(UUID.randomUUID().toString());
        hangout.setTitle(episode.getTitle());
        hangout.setVisibility(EventVisibility.INVITE_ONLY);
        hangout.setSeriesId(series.getSeriesId());
        hangout.setAssociatedGroups(List.of(series.getGroupId()));
        hangout.setCarpoolEnabled(false);

        // External ID fields
        hangout.setExternalId(episode.getEpisodeId().toString());
        hangout.setExternalSource(TVMAZE_SOURCE);
        hangout.setIsGeneratedTitle(true);
        hangout.setTitleNotificationSent(false);

        // Calculate timestamps
        if (episode.getAirTimestamp() != null) {
            long startTimestamp = calculateStartTimestamp(
                    episode.getAirTimestamp(),
                    series.getDefaultTime(),
                    series.getTimezone(),
                    series.getDayOverride()
            );
            int runtime = episode.getRuntime() != null ? episode.getRuntime() : 60;
            long endTimestamp = calculateEndTimestamp(startTimestamp, runtime);

            hangout.setStartTimestamp(startTimestamp);
            hangout.setEndTimestamp(endTimestamp);
        }

        // Combined episodes
        if (episode.getCombinedWith() != null && !episode.getCombinedWith().isEmpty()) {
            List<String> combinedIds = new ArrayList<>();
            combinedIds.add(episode.getEpisodeId().toString());
            episode.getCombinedWith().forEach(id -> combinedIds.add(id.toString()));
            hangout.setCombinedExternalIds(combinedIds);
        }

        // Set host if series has default
        if (series.getDefaultHostId() != null) {
            hangout.setHostAtPlaceUserId(series.getDefaultHostId());
        }

        // Set DynamoDB keys
        hangout.setPk(InviterKeyFactory.getEventPk(hangout.getHangoutId()));
        hangout.setSk(InviterKeyFactory.getMetadataSk());

        return hangout;
    }

    private HangoutPointer createHangoutPointer(Hangout hangout, String groupId) {
        HangoutPointer pointer = new HangoutPointer(groupId, hangout.getHangoutId(), hangout.getTitle());

        pointer.setDescription(hangout.getDescription());
        pointer.setStartTimestamp(hangout.getStartTimestamp());
        pointer.setEndTimestamp(hangout.getEndTimestamp());
        pointer.setVisibility(hangout.getVisibility());
        pointer.setSeriesId(hangout.getSeriesId());
        pointer.setMainImagePath(hangout.getMainImagePath());
        pointer.setCarpoolEnabled(hangout.isCarpoolEnabled());
        pointer.setHostAtPlaceUserId(hangout.getHostAtPlaceUserId());

        pointer.setExternalId(hangout.getExternalId());
        pointer.setExternalSource(hangout.getExternalSource());
        pointer.setIsGeneratedTitle(hangout.getIsGeneratedTitle());

        pointer.setGsi1pk(InviterKeyFactory.getGroupPk(groupId));
        pointer.setGsi1sk(String.valueOf(hangout.getStartTimestamp()));

        return pointer;
    }

    private void updateHangoutPointer(Hangout hangout, String groupId) {
        HangoutPointer pointer = createHangoutPointer(hangout, groupId);
        groupRepository.saveHangoutPointer(pointer);
    }

    private void updateSeriesTimestamps(EventSeries series, Hangout hangout) {
        if (hangout.getStartTimestamp() != null) {
            if (series.getStartTimestamp() == null || hangout.getStartTimestamp() < series.getStartTimestamp()) {
                series.setStartTimestamp(hangout.getStartTimestamp());
            }
        }
        if (hangout.getEndTimestamp() != null) {
            if (series.getEndTimestamp() == null || hangout.getEndTimestamp() > series.getEndTimestamp()) {
                series.setEndTimestamp(hangout.getEndTimestamp());
            }
        }
    }

    /**
     * Update SeriesPointer.parts with a new HangoutPointer.
     * This ensures the denormalized parts list stays in sync with new episodes.
     */
    private void updateSeriesPointerWithNewPart(EventSeries series, HangoutPointer newPointer) {
        try {
            Optional<SeriesPointer> existingPointer = groupRepository.findSeriesPointer(
                    series.getGroupId(), series.getSeriesId());

            if (existingPointer.isPresent()) {
                SeriesPointer seriesPointer = existingPointer.get();

                // Add the new HangoutPointer to the parts list
                seriesPointer.addPart(newPointer);

                // Sync timestamps with the series
                seriesPointer.setStartTimestamp(series.getStartTimestamp());
                seriesPointer.setEndTimestamp(series.getEndTimestamp());

                // Also sync the hangoutIds list
                seriesPointer.setHangoutIds(series.getHangoutIds() != null
                        ? new ArrayList<>(series.getHangoutIds()) : new ArrayList<>());

                // Save updated SeriesPointer
                groupRepository.saveSeriesPointer(seriesPointer);

                logger.debug("Updated SeriesPointer parts for series {} with new hangout {}",
                        series.getSeriesId(), newPointer.getHangoutId());
            } else {
                logger.warn("SeriesPointer not found for series {} in group {}, cannot add new part",
                        series.getSeriesId(), series.getGroupId());
            }
        } catch (Exception e) {
            logger.error("Failed to update SeriesPointer for series {}: {}",
                    series.getSeriesId(), e.getMessage());
            // Don't rethrow - the hangout was already saved, this is a denormalization issue
            // that can be fixed by a repair job if needed
        }
    }

    /**
     * Remove a HangoutPointer from SeriesPointer.parts when an episode is deleted.
     * This ensures the denormalized parts list stays in sync.
     */
    private void removeHangoutFromSeriesPointer(EventSeries series, String hangoutId) {
        try {
            Optional<SeriesPointer> existingPointer = groupRepository.findSeriesPointer(
                    series.getGroupId(), series.getSeriesId());

            if (existingPointer.isPresent()) {
                SeriesPointer seriesPointer = existingPointer.get();

                // Remove the HangoutPointer from the parts list
                List<HangoutPointer> parts = seriesPointer.getParts();
                if (parts != null) {
                    parts.removeIf(part -> hangoutId.equals(part.getHangoutId()));
                    seriesPointer.setParts(parts);
                }

                // Sync hangoutIds list
                seriesPointer.setHangoutIds(series.getHangoutIds() != null
                        ? new ArrayList<>(series.getHangoutIds()) : new ArrayList<>());

                // Recalculate timestamps based on remaining parts
                recalculateSeriesPointerTimestamps(seriesPointer);

                // Save updated SeriesPointer
                groupRepository.saveSeriesPointer(seriesPointer);

                logger.debug("Removed hangout {} from SeriesPointer parts for series {}",
                        hangoutId, series.getSeriesId());
            } else {
                logger.warn("SeriesPointer not found for series {} in group {}, cannot remove part",
                        series.getSeriesId(), series.getGroupId());
            }
        } catch (Exception e) {
            logger.error("Failed to remove hangout from SeriesPointer for series {}: {}",
                    series.getSeriesId(), e.getMessage());
            // Don't rethrow - this is a denormalization issue that can be fixed by a repair job
        }
    }

    /**
     * Recalculate SeriesPointer timestamps based on remaining parts.
     */
    private void recalculateSeriesPointerTimestamps(SeriesPointer seriesPointer) {
        List<HangoutPointer> parts = seriesPointer.getParts();
        if (parts == null || parts.isEmpty()) {
            seriesPointer.setStartTimestamp(null);
            seriesPointer.setEndTimestamp(null);
            return;
        }

        Long earliestStart = null;
        Long latestEnd = null;

        for (HangoutPointer part : parts) {
            if (part.getStartTimestamp() != null) {
                if (earliestStart == null || part.getStartTimestamp() < earliestStart) {
                    earliestStart = part.getStartTimestamp();
                }
            }
            if (part.getEndTimestamp() != null) {
                if (latestEnd == null || part.getEndTimestamp() > latestEnd) {
                    latestEnd = part.getEndTimestamp();
                }
            }
        }

        seriesPointer.setStartTimestamp(earliestStart);
        seriesPointer.setEndTimestamp(latestEnd);
    }

    /**
     * Update a HangoutPointer within SeriesPointer.parts when the hangout is modified.
     * This ensures the denormalized parts list stays in sync with title and other updates.
     * Note: This method assumes timestamps haven't changed (used for title updates).
     * If timestamps change, recalculateSeriesPointerTimestamps should be called after.
     */
    private void updateHangoutInSeriesPointer(String groupId, String seriesId, HangoutPointer updatedPointer) {
        try {
            Optional<SeriesPointer> existingPointer = groupRepository.findSeriesPointer(groupId, seriesId);

            if (existingPointer.isPresent()) {
                SeriesPointer seriesPointer = existingPointer.get();

                // Find and replace the matching HangoutPointer in parts
                List<HangoutPointer> parts = seriesPointer.getParts();
                if (parts == null) {
                    parts = new ArrayList<>();
                    logger.warn("SeriesPointer {} had null parts list, initialized it", seriesId);
                }

                boolean found = false;
                for (int i = 0; i < parts.size(); i++) {
                    if (updatedPointer.getHangoutId().equals(parts.get(i).getHangoutId())) {
                        parts.set(i, updatedPointer);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    // Hangout wasn't in parts - this shouldn't happen but add it to be safe
                    parts.add(updatedPointer);
                    logger.warn("HangoutPointer {} wasn't found in SeriesPointer parts, added it",
                            updatedPointer.getHangoutId());
                }
                seriesPointer.setParts(parts);

                // Save updated SeriesPointer
                groupRepository.saveSeriesPointer(seriesPointer);

                logger.debug("Updated HangoutPointer {} in SeriesPointer parts for series {}",
                        updatedPointer.getHangoutId(), seriesId);
            } else {
                logger.warn("SeriesPointer not found for series {} in group {}, cannot update part",
                        seriesId, groupId);
            }
        } catch (Exception e) {
            logger.error("Failed to update HangoutPointer in SeriesPointer for series {}: {}",
                    seriesId, e.getMessage());
            // Don't rethrow - this is a denormalization issue that can be fixed by a repair job
        }
    }

    private long calculateStartTimestamp(Long airTimestamp, String defaultTime, String timezone, Integer dayOverride) {
        if (airTimestamp == null || defaultTime == null || timezone == null) {
            return airTimestamp != null ? airTimestamp : Instant.now().getEpochSecond();
        }

        try {
            ZoneId zone = ZoneId.of(timezone);
            LocalTime time = LocalTime.parse(defaultTime, DateTimeFormatter.ofPattern("HH:mm"));

            Instant airInstant = Instant.ofEpochSecond(airTimestamp);
            LocalDate airDate = airInstant.atZone(ZoneOffset.UTC).toLocalDate();

            LocalDate targetDate = airDate;
            if (dayOverride != null) {
                targetDate = calculateDateWithDayOverride(airDate, dayOverride);
            }

            ZonedDateTime zdt = ZonedDateTime.of(targetDate, time, zone);
            return zdt.toEpochSecond();
        } catch (Exception e) {
            logger.warn("Failed to calculate start timestamp, using air timestamp: {}", e.getMessage());
            return airTimestamp;
        }
    }

    private LocalDate calculateDateWithDayOverride(LocalDate airDate, Integer dayOverride) {
        DayOfWeek targetDay;
        if (dayOverride == 0) {
            targetDay = DayOfWeek.SUNDAY;
        } else {
            targetDay = DayOfWeek.of(dayOverride);
        }

        if (airDate.getDayOfWeek() == targetDay) {
            return airDate;
        }

        return airDate.with(TemporalAdjusters.next(targetDay));
    }

    private long calculateEndTimestamp(long startTimestamp, int runtimeMinutes) {
        int roundedMinutes = (int) (Math.ceil(runtimeMinutes / 30.0) * 30);
        return startTimestamp + (roundedMinutes * 60L);
    }

    private void notifyInterestedUsers(EventSeries series, String message) {
        try {
            String groupId = series.getGroupId();
            String seriesId = series.getSeriesId();

            Optional<SeriesPointer> pointerOpt = groupRepository.findSeriesPointer(groupId, seriesId);
            if (pointerOpt.isEmpty() || pointerOpt.get().getInterestLevels() == null) {
                return;
            }

            Set<String> userIds = pointerOpt.get().getInterestLevels().stream()
                    .filter(il -> "GOING".equals(il.getStatus()) || "INTERESTED".equals(il.getStatus()))
                    .map(InterestLevel::getUserId)
                    .collect(Collectors.toSet());

            if (!userIds.isEmpty()) {
                notificationService.notifyWatchPartyUpdate(userIds, seriesId, message);
            }
        } catch (Exception e) {
            logger.warn("Failed to notify users for series {}: {}", series.getSeriesId(), e.getMessage());
        }
    }

    private void notifyTitleUpdate(String seriesId, String oldTitle, String newTitle) {
        try {
            Optional<EventSeries> seriesOpt = eventSeriesRepository.findById(seriesId);
            if (seriesOpt.isEmpty()) {
                logger.debug("Series {} not found for title update notification", seriesId);
                return;
            }

            EventSeries series = seriesOpt.get();
            String groupId = series.getGroupId();

            Optional<SeriesPointer> pointerOpt = groupRepository.findSeriesPointer(groupId, seriesId);
            if (pointerOpt.isEmpty() || pointerOpt.get().getInterestLevels() == null) {
                return;
            }

            Set<String> userIds = pointerOpt.get().getInterestLevels().stream()
                    .filter(il -> "GOING".equals(il.getStatus()) || "INTERESTED".equals(il.getStatus()))
                    .map(InterestLevel::getUserId)
                    .collect(Collectors.toSet());

            if (!userIds.isEmpty()) {
                String message = String.format("Episode renamed: '%s' is now '%s'", oldTitle, newTitle);
                notificationService.notifyWatchPartyUpdate(userIds, seriesId, message);
            }
        } catch (Exception e) {
            logger.warn("Failed to notify users about title update for series {}: {}", seriesId, e.getMessage());
        }
    }

    private void notifyHangoutCancellation(Hangout hangout) {
        try {
            // Find pointers and get interested users
            List<HangoutPointer> pointers = hangoutRepository.findPointersForHangout(hangout);
            Set<String> userIds = new HashSet<>();

            for (HangoutPointer pointer : pointers) {
                if (pointer.getInterestLevels() != null) {
                    pointer.getInterestLevels().stream()
                            .filter(il -> "GOING".equals(il.getStatus()) || "INTERESTED".equals(il.getStatus()))
                            .map(InterestLevel::getUserId)
                            .forEach(userIds::add);
                }
            }

            if (!userIds.isEmpty() && hangout.getSeriesId() != null) {
                notificationService.notifyWatchPartyUpdate(userIds, hangout.getSeriesId(),
                        "Episode cancelled: " + hangout.getTitle());
            }
        } catch (Exception e) {
            logger.warn("Failed to notify users about cancellation for hangout {}: {}",
                    hangout.getHangoutId(), e.getMessage());
        }
    }
}
