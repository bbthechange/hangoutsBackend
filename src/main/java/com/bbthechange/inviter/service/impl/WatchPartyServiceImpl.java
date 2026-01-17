package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.watchparty.*;
import com.bbthechange.inviter.exception.ResourceNotFoundException;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.exception.ValidationException;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.*;
import com.bbthechange.inviter.service.GroupTimestampService;
import com.bbthechange.inviter.service.WatchPartyService;
import com.bbthechange.inviter.util.InviterKeyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of WatchPartyService for managing TV Watch Party series.
 */
@Service
public class WatchPartyServiceImpl implements WatchPartyService {

    private static final Logger logger = LoggerFactory.getLogger(WatchPartyServiceImpl.class);

    private static final String WATCH_PARTY_TYPE = "WATCH_PARTY";
    private static final String TVMAZE_SOURCE = "TVMAZE";
    private static final long TWENTY_HOURS_SECONDS = 20 * 60 * 60; // 72000 seconds

    private final GroupRepository groupRepository;
    private final HangoutRepository hangoutRepository;
    private final EventSeriesRepository eventSeriesRepository;
    private final SeasonRepository seasonRepository;
    private final GroupTimestampService groupTimestampService;

    @Autowired
    public WatchPartyServiceImpl(
            GroupRepository groupRepository,
            HangoutRepository hangoutRepository,
            EventSeriesRepository eventSeriesRepository,
            SeasonRepository seasonRepository,
            GroupTimestampService groupTimestampService) {
        this.groupRepository = groupRepository;
        this.hangoutRepository = hangoutRepository;
        this.eventSeriesRepository = eventSeriesRepository;
        this.seasonRepository = seasonRepository;
        this.groupTimestampService = groupTimestampService;
    }

    @Override
    public WatchPartyResponse createWatchParty(String groupId, CreateWatchPartyRequest request, String requestingUserId) {
        // 1. Validate user is member of group
        validateGroupMembership(groupId, requestingUserId);

        // 2. Validate timezone is valid
        validateTimezone(request.getTimezone());

        // 3. Create or update Season record
        Season season = createOrUpdateSeason(request);

        // 4. Apply episode combination logic
        List<CombinedEpisode> combinedEpisodes = combineEpisodes(request.getEpisodes());
        logger.info("Combined {} episodes into {} groups", request.getEpisodes().size(), combinedEpisodes.size());

        // 5. Create EventSeries
        String seriesTitle = request.getShowName() + " Season " + request.getSeasonNumber();
        EventSeries eventSeries = createEventSeries(groupId, request, seriesTitle, season);

        // 6. Create Hangouts and HangoutPointers for each combined episode
        List<Hangout> hangouts = new ArrayList<>();
        List<HangoutPointer> pointers = new ArrayList<>();
        List<WatchPartyHangoutSummary> hangoutSummaries = new ArrayList<>();

        Long minTimestamp = null;
        Long maxTimestamp = null;

        for (CombinedEpisode combined : combinedEpisodes) {
            // Calculate timestamps
            long startTimestamp = calculateStartTimestamp(
                    combined.getAirTimestamp(),
                    request.getDefaultTime(),
                    request.getTimezone(),
                    request.getDayOverride()
            );
            long endTimestamp = calculateEndTimestamp(startTimestamp, combined.getTotalRuntime());

            // Track min/max for series timestamps
            if (minTimestamp == null || startTimestamp < minTimestamp) {
                minTimestamp = startTimestamp;
            }
            if (maxTimestamp == null || endTimestamp > maxTimestamp) {
                maxTimestamp = endTimestamp;
            }

            // Create Hangout
            Hangout hangout = createHangout(combined, eventSeries.getSeriesId(), groupId,
                    startTimestamp, endTimestamp, request.getDefaultHostId());
            hangouts.add(hangout);

            // Create HangoutPointer
            HangoutPointer pointer = createHangoutPointer(hangout, groupId);
            pointers.add(pointer);

            // Build summary
            hangoutSummaries.add(WatchPartyHangoutSummary.builder()
                    .hangoutId(hangout.getHangoutId())
                    .title(hangout.getTitle())
                    .startTimestamp(startTimestamp)
                    .endTimestamp(endTimestamp)
                    .externalId(hangout.getExternalId())
                    .combinedExternalIds(hangout.getCombinedExternalIds())
                    .build());

            // Add hangout to series
            eventSeries.addHangout(hangout.getHangoutId());
        }

        // Update series timestamps
        eventSeries.setStartTimestamp(minTimestamp);
        eventSeries.setEndTimestamp(maxTimestamp);

        // 7. Create SeriesPointer
        SeriesPointer seriesPointer = SeriesPointer.fromEventSeries(eventSeries, groupId);
        seriesPointer.setGsi1sk(String.valueOf(minTimestamp)); // For EntityTimeIndex sorting

        // 8. Save all records
        saveAllRecords(season, eventSeries, hangouts, pointers, seriesPointer);

        // 9. Update group timestamp for cache invalidation
        groupTimestampService.updateGroupTimestamps(List.of(groupId));

        logger.info("Created watch party {} with {} hangouts for group {}",
                eventSeries.getSeriesId(), hangouts.size(), groupId);

        return WatchPartyResponse.builder()
                .seriesId(eventSeries.getSeriesId())
                .seriesTitle(seriesTitle)
                .hangouts(hangoutSummaries)
                .build();
    }

    @Override
    public WatchPartyDetailResponse getWatchParty(String groupId, String seriesId, String requestingUserId) {
        // 1. Validate user is member of group
        validateGroupMembership(groupId, requestingUserId);

        // 2. Get EventSeries
        EventSeries series = eventSeriesRepository.findById(seriesId)
                .orElseThrow(() -> new ResourceNotFoundException("Watch party not found: " + seriesId));

        // Verify it's a watch party and belongs to this group
        if (!WATCH_PARTY_TYPE.equals(series.getEventSeriesType())) {
            throw new ResourceNotFoundException("Watch party not found: " + seriesId);
        }
        if (!groupId.equals(series.getGroupId())) {
            throw new ResourceNotFoundException("Watch party not found in group: " + groupId);
        }

        // 3. Get all hangouts in the series
        List<WatchPartyHangoutSummary> hangoutSummaries = new ArrayList<>();
        if (series.getHangoutIds() != null) {
            for (String hangoutId : series.getHangoutIds()) {
                hangoutRepository.findHangoutById(hangoutId).ifPresent(hangout -> {
                    hangoutSummaries.add(WatchPartyHangoutSummary.builder()
                            .hangoutId(hangout.getHangoutId())
                            .title(hangout.getTitle())
                            .startTimestamp(hangout.getStartTimestamp())
                            .endTimestamp(hangout.getEndTimestamp())
                            .externalId(hangout.getExternalId())
                            .combinedExternalIds(hangout.getCombinedExternalIds())
                            .build());
                });
            }
        }

        // Sort by start timestamp
        hangoutSummaries.sort(Comparator.comparing(WatchPartyHangoutSummary::getStartTimestamp,
                Comparator.nullsLast(Comparator.naturalOrder())));

        // 4. Parse seasonId to get showId and seasonNumber
        Integer showId = null;
        Integer seasonNumber = null;
        if (series.getSeasonId() != null) {
            // Format: "TVMAZE#SHOW#{showId}|SEASON#{seasonNumber}"
            String[] parts = series.getSeasonId().split("\\|");
            if (parts.length == 2) {
                showId = parseShowIdFromPk(parts[0]);
                seasonNumber = parseSeasonNumberFromSk(parts[1]);
            }
        }

        return WatchPartyDetailResponse.builder()
                .seriesId(seriesId)
                .seriesTitle(series.getSeriesTitle())
                .groupId(groupId)
                .eventSeriesType(series.getEventSeriesType())
                .showId(showId)
                .seasonNumber(seasonNumber)
                .defaultTime(series.getDefaultTime())
                .timezone(series.getTimezone())
                .dayOverride(series.getDayOverride())
                .defaultHostId(series.getDefaultHostId())
                .hangouts(hangoutSummaries)
                .build();
    }

    @Override
    public void deleteWatchParty(String groupId, String seriesId, String requestingUserId) {
        // 1. Validate user is member of group
        validateGroupMembership(groupId, requestingUserId);

        // 2. Get EventSeries
        EventSeries series = eventSeriesRepository.findById(seriesId)
                .orElseThrow(() -> new ResourceNotFoundException("Watch party not found: " + seriesId));

        // Verify it's a watch party and belongs to this group
        if (!WATCH_PARTY_TYPE.equals(series.getEventSeriesType())) {
            throw new ResourceNotFoundException("Watch party not found: " + seriesId);
        }
        if (!groupId.equals(series.getGroupId())) {
            throw new ResourceNotFoundException("Watch party not found in group: " + groupId);
        }

        // 3. Delete all hangouts and their pointers
        if (series.getHangoutIds() != null) {
            for (String hangoutId : series.getHangoutIds()) {
                // Delete hangout pointer
                groupRepository.deleteHangoutPointer(groupId, hangoutId);
                // Delete hangout
                hangoutRepository.deleteHangout(hangoutId);
            }
        }

        // 4. Delete series pointer
        // Note: SeriesPointer has PK=GROUP#{groupId}, SK=SERIES#{seriesId}
        deleteSeriesPointer(groupId, seriesId);

        // 5. Delete event series
        eventSeriesRepository.deleteById(seriesId);

        // Note: Season record is NOT deleted (other groups may use it)

        // 6. Update group timestamp for cache invalidation
        groupTimestampService.updateGroupTimestamps(List.of(groupId));

        logger.info("Deleted watch party {} with {} hangouts from group {}",
                seriesId, series.getHangoutCount(), groupId);
    }

    // ============================================================================
    // HELPER METHODS - VALIDATION
    // ============================================================================

    private void validateGroupMembership(String groupId, String userId) {
        if (!groupRepository.isUserMemberOfGroup(groupId, userId)) {
            throw new UnauthorizedException("User is not a member of group: " + groupId);
        }
    }

    private void validateTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (Exception e) {
            throw new ValidationException("Invalid timezone: " + timezone);
        }
    }

    // ============================================================================
    // HELPER METHODS - EPISODE COMBINATION
    // ============================================================================

    /**
     * Combine episodes that air within 20 hours of each other.
     * Episodes must be sorted by airTimestamp.
     */
    List<CombinedEpisode> combineEpisodes(List<CreateWatchPartyEpisodeRequest> episodes) {
        if (episodes == null || episodes.isEmpty()) {
            return List.of();
        }

        // Sort by air timestamp
        List<CreateWatchPartyEpisodeRequest> sorted = episodes.stream()
                .sorted(Comparator.comparing(CreateWatchPartyEpisodeRequest::getAirTimestamp))
                .collect(Collectors.toList());

        List<CombinedEpisode> result = new ArrayList<>();
        List<CreateWatchPartyEpisodeRequest> currentGroup = new ArrayList<>();
        currentGroup.add(sorted.get(0));

        for (int i = 1; i < sorted.size(); i++) {
            CreateWatchPartyEpisodeRequest current = sorted.get(i);
            CreateWatchPartyEpisodeRequest lastInGroup = currentGroup.get(currentGroup.size() - 1);

            long timeDiff = current.getAirTimestamp() - lastInGroup.getAirTimestamp();

            if (timeDiff < TWENTY_HOURS_SECONDS) {
                // Add to current group
                currentGroup.add(current);
            } else {
                // Emit current group and start new one
                result.add(createCombinedEpisode(currentGroup));
                currentGroup = new ArrayList<>();
                currentGroup.add(current);
            }
        }

        // Don't forget the last group
        result.add(createCombinedEpisode(currentGroup));

        return result;
    }

    private CombinedEpisode createCombinedEpisode(List<CreateWatchPartyEpisodeRequest> episodes) {
        CombinedEpisode combined = new CombinedEpisode();

        // Primary episode is the first one
        CreateWatchPartyEpisodeRequest primary = episodes.get(0);
        combined.setPrimaryEpisodeId(primary.getEpisodeId());
        combined.setAirTimestamp(primary.getAirTimestamp());

        // Collect all episode IDs
        List<String> allIds = episodes.stream()
                .map(e -> String.valueOf(e.getEpisodeId()))
                .collect(Collectors.toList());
        combined.setAllEpisodeIds(allIds);

        // Sum up runtime
        int totalRuntime = episodes.stream()
                .mapToInt(e -> e.getRuntime() != null ? e.getRuntime() : 60)
                .sum();
        combined.setTotalRuntime(totalRuntime);

        // Generate title
        combined.setTitle(generateCombinedTitle(episodes));

        return combined;
    }

    /**
     * Generate title for combined episodes per design doc:
     * - 1 episode: just the title
     * - 2 episodes: "Double Episode: {name1}, {name2}"
     * - 3 episodes: "Triple Episode"
     * - 4 episodes: "Quadruple Episode"
     * - 5+ episodes: "Multi-Episode ({count} episodes)"
     */
    String generateCombinedTitle(List<CreateWatchPartyEpisodeRequest> episodes) {
        int count = episodes.size();

        if (count == 1) {
            return episodes.get(0).getTitle();
        } else if (count == 2) {
            return "Double Episode: " + episodes.get(0).getTitle() + ", " + episodes.get(1).getTitle();
        } else if (count == 3) {
            return "Triple Episode";
        } else if (count == 4) {
            return "Quadruple Episode";
        } else {
            return "Multi-Episode (" + count + " episodes)";
        }
    }

    // ============================================================================
    // HELPER METHODS - TIME CALCULATION
    // ============================================================================

    /**
     * Calculate start timestamp using defaultTime + timezone + optional dayOverride.
     * DST-aware using IANA timezone.
     */
    long calculateStartTimestamp(Long airTimestamp, String defaultTime, String timezone, Integer dayOverride) {
        ZoneId zone = ZoneId.of(timezone);
        LocalTime time = LocalTime.parse(defaultTime, DateTimeFormatter.ofPattern("HH:mm"));

        // Convert air timestamp to date in UTC to establish the correct calendar day
        Instant airInstant = Instant.ofEpochSecond(airTimestamp);
        LocalDate airDate = airInstant.atZone(ZoneOffset.UTC).toLocalDate();

        // Apply day override if specified
        LocalDate targetDate = airDate;
        if (dayOverride != null) {
            targetDate = calculateDateWithDayOverride(airDate, dayOverride);
        }

        // Combine date and time in timezone
        ZonedDateTime zdt = ZonedDateTime.of(targetDate, time, zone);
        return zdt.toEpochSecond();
    }

    /**
     * Calculate date with day override.
     * Finds the next occurrence of the target day on or after the air date.
     */
    LocalDate calculateDateWithDayOverride(LocalDate airDate, Integer dayOverride) {
        // dayOverride: 0=Sunday, 1=Monday, ... 6=Saturday
        // DayOfWeek: 1=Monday, 2=Tuesday, ... 7=Sunday
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

    /**
     * Calculate end timestamp with runtime rounding to nearest 30 minutes.
     * endTimestamp = startTimestamp + ceil(runtime/30) * 30 minutes
     */
    long calculateEndTimestamp(long startTimestamp, int runtimeMinutes) {
        int roundedMinutes = (int) (Math.ceil(runtimeMinutes / 30.0) * 30);
        return startTimestamp + (roundedMinutes * 60L);
    }

    // ============================================================================
    // HELPER METHODS - RECORD CREATION
    // ============================================================================

    private Season createOrUpdateSeason(CreateWatchPartyRequest request) {
        // Check if season already exists
        Optional<Season> existing = seasonRepository.findByShowIdAndSeasonNumber(
                request.getShowId(), request.getSeasonNumber());

        if (existing.isPresent()) {
            logger.debug("Season already exists for show {} season {}",
                    request.getShowId(), request.getSeasonNumber());
            return existing.get();
        }

        // Create new Season
        Season season = new Season(request.getShowId(), request.getSeasonNumber(), request.getShowName());

        // Add episodes from request
        for (CreateWatchPartyEpisodeRequest ep : request.getEpisodes()) {
            Episode episode = new Episode(ep.getEpisodeId(), 0, ep.getTitle()); // episodeNumber not in Phase 2 request
            episode.setAirTimestamp(ep.getAirTimestamp());
            episode.setRuntime(ep.getRuntime());
            episode.setType("regular");
            season.addEpisode(episode);
        }

        return seasonRepository.save(season);
    }

    private EventSeries createEventSeries(String groupId, CreateWatchPartyRequest request, String seriesTitle, Season season) {
        EventSeries series = new EventSeries(seriesTitle, null, groupId);

        // Set watch party specific fields
        series.setEventSeriesType(WATCH_PARTY_TYPE);
        series.setSeasonId(season.getSeasonReference());
        series.setDefaultHostId(request.getDefaultHostId());
        series.setDefaultTime(request.getDefaultTime());
        series.setDayOverride(request.getDayOverride());
        series.setTimezone(request.getTimezone());
        series.setIsGeneratedTitle(true);

        // External ID fields for GSI lookup
        series.setExternalId(String.valueOf(request.getShowId()));
        series.setExternalSource(TVMAZE_SOURCE);

        return series;
    }

    private Hangout createHangout(CombinedEpisode combined, String seriesId, String groupId,
                                  long startTimestamp, long endTimestamp, String defaultHostId) {
        Hangout hangout = new Hangout();
        hangout.setHangoutId(UUID.randomUUID().toString());
        hangout.setTitle(combined.getTitle());
        hangout.setDescription(null);
        hangout.setStartTimestamp(startTimestamp);
        hangout.setEndTimestamp(endTimestamp);
        hangout.setVisibility(EventVisibility.INVITE_ONLY);
        hangout.setSeriesId(seriesId);
        hangout.setAssociatedGroups(List.of(groupId));
        hangout.setCarpoolEnabled(false);

        // External ID is the primary episode ID
        hangout.setExternalId(String.valueOf(combined.getPrimaryEpisodeId()));
        hangout.setExternalSource(TVMAZE_SOURCE);
        hangout.setIsGeneratedTitle(true);

        // Watch party specific fields
        hangout.setTitleNotificationSent(false);
        hangout.setCombinedExternalIds(combined.getAllEpisodeIds());

        // Set host if provided
        if (defaultHostId != null) {
            hangout.setHostAtPlaceUserId(defaultHostId);
        }

        // Set DynamoDB keys
        hangout.setPk(InviterKeyFactory.getEventPk(hangout.getHangoutId()));
        hangout.setSk(InviterKeyFactory.getMetadataSk());

        return hangout;
    }

    private HangoutPointer createHangoutPointer(Hangout hangout, String groupId) {
        HangoutPointer pointer = new HangoutPointer(groupId, hangout.getHangoutId(), hangout.getTitle());

        // Denormalize fields from hangout
        pointer.setDescription(hangout.getDescription());
        pointer.setStartTimestamp(hangout.getStartTimestamp());
        pointer.setEndTimestamp(hangout.getEndTimestamp());
        pointer.setVisibility(hangout.getVisibility());
        pointer.setSeriesId(hangout.getSeriesId());
        pointer.setMainImagePath(hangout.getMainImagePath());
        pointer.setCarpoolEnabled(hangout.isCarpoolEnabled());
        pointer.setHostAtPlaceUserId(hangout.getHostAtPlaceUserId());

        // External source fields
        pointer.setExternalId(hangout.getExternalId());
        pointer.setExternalSource(hangout.getExternalSource());
        pointer.setIsGeneratedTitle(hangout.getIsGeneratedTitle());

        // GSI keys for EntityTimeIndex
        pointer.setGsi1pk(InviterKeyFactory.getGroupPk(groupId));
        pointer.setGsi1sk(String.valueOf(hangout.getStartTimestamp()));

        return pointer;
    }

    private void saveAllRecords(Season season, EventSeries eventSeries,
                                List<Hangout> hangouts, List<HangoutPointer> pointers,
                                SeriesPointer seriesPointer) {
        // Save season
        seasonRepository.save(season);

        // Save event series
        eventSeriesRepository.save(eventSeries);

        // Save hangouts and pointers
        for (int i = 0; i < hangouts.size(); i++) {
            hangoutRepository.save(hangouts.get(i));
            groupRepository.saveHangoutPointer(pointers.get(i));
        }

        // Save series pointer
        groupRepository.saveSeriesPointer(seriesPointer);
    }

    private void deleteSeriesPointer(String groupId, String seriesId) {
        groupRepository.deleteSeriesPointer(groupId, seriesId);
    }

    // ============================================================================
    // HELPER METHODS - PARSING
    // ============================================================================

    private Integer parseShowIdFromPk(String pk) {
        // Format: "TVMAZE#SHOW#{showId}"
        try {
            String[] parts = pk.split("#");
            if (parts.length >= 3) {
                return Integer.parseInt(parts[2]);
            }
        } catch (Exception e) {
            logger.warn("Failed to parse showId from pk: {}", pk);
        }
        return null;
    }

    private Integer parseSeasonNumberFromSk(String sk) {
        // Format: "SEASON#{seasonNumber}"
        try {
            String[] parts = sk.split("#");
            if (parts.length >= 2) {
                return Integer.parseInt(parts[1]);
            }
        } catch (Exception e) {
            logger.warn("Failed to parse seasonNumber from sk: {}", sk);
        }
        return null;
    }

    // ============================================================================
    // INNER CLASSES
    // ============================================================================

    /**
     * Represents a combined episode group (episodes within 20 hours of each other).
     */
    static class CombinedEpisode {
        private Integer primaryEpisodeId;
        private List<String> allEpisodeIds;
        private String title;
        private Long airTimestamp;
        private int totalRuntime;

        public Integer getPrimaryEpisodeId() { return primaryEpisodeId; }
        public void setPrimaryEpisodeId(Integer primaryEpisodeId) { this.primaryEpisodeId = primaryEpisodeId; }

        public List<String> getAllEpisodeIds() { return allEpisodeIds; }
        public void setAllEpisodeIds(List<String> allEpisodeIds) { this.allEpisodeIds = allEpisodeIds; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public Long getAirTimestamp() { return airTimestamp; }
        public void setAirTimestamp(Long airTimestamp) { this.airTimestamp = airTimestamp; }

        public int getTotalRuntime() { return totalRuntime; }
        public void setTotalRuntime(int totalRuntime) { this.totalRuntime = totalRuntime; }
    }
}
