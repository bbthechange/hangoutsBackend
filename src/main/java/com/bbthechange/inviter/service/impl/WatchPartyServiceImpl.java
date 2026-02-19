package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.client.TvMazeClient;
import com.bbthechange.inviter.dto.TimeInfo;
import com.bbthechange.inviter.dto.tvmaze.TvMazeEpisodeResponse;
import com.bbthechange.inviter.dto.watchparty.*;
import com.bbthechange.inviter.exception.ResourceNotFoundException;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.exception.ValidationException;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.*;
import com.bbthechange.inviter.service.GroupTimestampService;
import com.bbthechange.inviter.service.WatchPartyService;
import com.bbthechange.inviter.util.HangoutPointerFactory;
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
    private static final String TVMAZE_IMAGE_PREFIX = "https://static.tvmaze.com/";

    private final GroupRepository groupRepository;
    private final HangoutRepository hangoutRepository;
    private final EventSeriesRepository eventSeriesRepository;
    private final SeasonRepository seasonRepository;
    private final UserRepository userRepository;
    private final GroupTimestampService groupTimestampService;
    private final TvMazeClient tvMazeClient;
    private final PointerUpdateService pointerUpdateService;

    @Autowired
    public WatchPartyServiceImpl(
            GroupRepository groupRepository,
            HangoutRepository hangoutRepository,
            EventSeriesRepository eventSeriesRepository,
            SeasonRepository seasonRepository,
            UserRepository userRepository,
            GroupTimestampService groupTimestampService,
            TvMazeClient tvMazeClient,
            PointerUpdateService pointerUpdateService) {
        this.groupRepository = groupRepository;
        this.hangoutRepository = hangoutRepository;
        this.eventSeriesRepository = eventSeriesRepository;
        this.seasonRepository = seasonRepository;
        this.userRepository = userRepository;
        this.groupTimestampService = groupTimestampService;
        this.tvMazeClient = tvMazeClient;
        this.pointerUpdateService = pointerUpdateService;
    }

    @Override
    public WatchPartyResponse createWatchParty(String groupId, CreateWatchPartyRequest request, String requestingUserId) {
        // 1. Validate user is member of group
        validateGroupMembership(groupId, requestingUserId);

        // 2. Validate timezone is valid
        validateTimezone(request.getTimezone());

        // 3. Resolve episodes (fetch from TVMaze or use provided)
        List<CreateWatchPartyEpisodeRequest> episodes = resolveEpisodes(request);

        // 4. Create or update Season record (using resolved episodes)
        Season season = createOrUpdateSeason(request, episodes);

        // 5. Apply episode combination logic
        List<CombinedEpisode> combinedEpisodes = combineEpisodes(episodes);
        logger.info("Combined {} episodes into {} groups", episodes.size(), combinedEpisodes.size());

        // 6. Create EventSeries
        String seriesTitle = request.getShowName() + " Season " + request.getSeasonNumber();
        EventSeries eventSeries = createEventSeries(groupId, request, seriesTitle, season);

        // 7. Create Hangouts and HangoutPointers for each combined episode
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
                    startTimestamp, endTimestamp, request.getDefaultHostId(), request.getTimezone());
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

        // 8. Create SeriesPointer
        SeriesPointer seriesPointer = SeriesPointer.fromEventSeries(eventSeries, groupId);
        seriesPointer.setGsi1sk(String.valueOf(minTimestamp)); // For EntityTimeIndex sorting
        seriesPointer.setParts(pointers); // Denormalize hangout pointers for feed filtering

        // 9. Save all records
        saveAllRecords(season, eventSeries, hangouts, pointers, seriesPointer);

        // 10. Update group timestamp for cache invalidation
        groupTimestampService.updateGroupTimestamps(List.of(groupId));

        logger.info("Created watch party {} with {} hangouts for group {}",
                eventSeries.getSeriesId(), hangouts.size(), groupId);

        return WatchPartyResponse.builder()
                .seriesId(eventSeries.getSeriesId())
                .seriesTitle(seriesTitle)
                .mainImagePath(eventSeries.getMainImagePath())
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

        // 5. Get SeriesPointer for interest levels
        List<SeriesInterestLevelDTO> interestLevelDTOs = new ArrayList<>();
        Optional<SeriesPointer> pointerOpt = groupRepository.findSeriesPointer(groupId, seriesId);
        if (pointerOpt.isPresent() && pointerOpt.get().getInterestLevels() != null) {
            interestLevelDTOs = pointerOpt.get().getInterestLevels().stream()
                    .map(SeriesInterestLevelDTO::fromInterestLevel)
                    .collect(Collectors.toList());
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
                .mainImagePath(series.getMainImagePath())
                .hangouts(hangoutSummaries)
                .interestLevels(interestLevelDTOs)
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

    @Override
    public WatchPartyDetailResponse updateWatchParty(String groupId, String seriesId,
                                                      UpdateWatchPartyRequest request,
                                                      String requestingUserId) {
        // 1. Validate user is member of group
        validateGroupMembership(groupId, requestingUserId);

        // 2. Get EventSeries and validate
        EventSeries series = eventSeriesRepository.findById(seriesId)
                .orElseThrow(() -> new ResourceNotFoundException("Watch party not found: " + seriesId));

        // Verify it's a watch party and belongs to this group
        if (!WATCH_PARTY_TYPE.equals(series.getEventSeriesType())) {
            throw new ResourceNotFoundException("Watch party not found: " + seriesId);
        }
        if (!groupId.equals(series.getGroupId())) {
            throw new ResourceNotFoundException("Watch party not found in group: " + groupId);
        }

        // 3. Validate timezone if provided
        String effectiveTimezone = request.getTimezone() != null ? request.getTimezone() : series.getTimezone();
        if (request.getTimezone() != null) {
            validateTimezone(request.getTimezone());
        }

        // 4. Determine effective values (use request if provided, otherwise keep existing)
        String effectiveDefaultTime = request.getDefaultTime() != null ? request.getDefaultTime() : series.getDefaultTime();
        Integer effectiveDayOverride = request.getDayOverride() != null ? request.getDayOverride() : series.getDayOverride();
        String effectiveDefaultHostId = request.getDefaultHostId() != null
                ? (request.getDefaultHostId().isEmpty() ? null : request.getDefaultHostId())
                : series.getDefaultHostId();

        // 5. Apply show image update if provided
        if (request.getShowImageUrl() != null) {
            if (request.getShowImageUrl().isEmpty()) {
                series.setMainImagePath(null);
            } else {
                validateShowImageUrl(request.getShowImageUrl());
                series.setMainImagePath(request.getShowImageUrl());
            }
        }

        // 6. Check if time-related settings changed
        boolean timeSettingsChanged = !Objects.equals(effectiveDefaultTime, series.getDefaultTime()) ||
                !Objects.equals(effectiveTimezone, series.getTimezone()) ||
                !Objects.equals(effectiveDayOverride, series.getDayOverride());

        boolean hostChanged = !Objects.equals(effectiveDefaultHostId, series.getDefaultHostId());

        // 7. Apply time/host settings to series
        series.setDefaultTime(effectiveDefaultTime);
        series.setTimezone(effectiveTimezone);
        series.setDayOverride(effectiveDayOverride);
        series.setDefaultHostId(effectiveDefaultHostId);

        // 8. If cascade is enabled and time/host changed, update future hangouts
        Boolean shouldCascade = request.getChangeExistingUpcomingHangouts();
        if (shouldCascade == null) {
            shouldCascade = true; // Default to true
        }

        Long minTimestamp = series.getStartTimestamp();
        Long maxTimestamp = series.getEndTimestamp();

        if (shouldCascade && (timeSettingsChanged || hostChanged) && series.getHangoutIds() != null) {
            // Get Season to look up original air timestamps
            Season season = getSeasonFromSeries(series);

            long nowTimestamp = Instant.now().getEpochSecond();

            for (String hangoutId : series.getHangoutIds()) {
                Optional<Hangout> hangoutOpt = hangoutRepository.findHangoutById(hangoutId);
                if (hangoutOpt.isEmpty()) {
                    continue;
                }
                Hangout hangout = hangoutOpt.get();

                // Only update future hangouts
                if (hangout.getStartTimestamp() != null && hangout.getStartTimestamp() <= nowTimestamp) {
                    continue;
                }

                boolean hangoutUpdated = false;

                // Update timestamps if time settings changed
                if (timeSettingsChanged && season != null) {
                    // Get original air timestamp from episode
                    Long originalAirTimestamp = getOriginalAirTimestamp(hangout, season);
                    if (originalAirTimestamp != null) {
                        // Preserve duration
                        long originalDuration = (hangout.getEndTimestamp() != null && hangout.getStartTimestamp() != null)
                                ? hangout.getEndTimestamp() - hangout.getStartTimestamp()
                                : 3600L; // Default 1 hour if not set

                        // Calculate new timestamp
                        long newStartTimestamp = calculateStartTimestamp(
                                originalAirTimestamp,
                                effectiveDefaultTime,
                                effectiveTimezone,
                                effectiveDayOverride
                        );
                        long newEndTimestamp = newStartTimestamp + originalDuration;

                        hangout.setStartTimestamp(newStartTimestamp);
                        hangout.setEndTimestamp(newEndTimestamp);

                        // Update TimeInfo with new ISO-8601 formatted times
                        TimeInfo timeInfo = new TimeInfo();
                        ZoneId zone = ZoneId.of(effectiveTimezone);
                        ZonedDateTime startZdt = Instant.ofEpochSecond(newStartTimestamp).atZone(zone);
                        ZonedDateTime endZdt = Instant.ofEpochSecond(newEndTimestamp).atZone(zone);
                        timeInfo.setStartTime(startZdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                        timeInfo.setEndTime(endZdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                        hangout.setTimeInput(timeInfo);

                        hangoutUpdated = true;

                        // Track min/max for series timestamps
                        if (minTimestamp == null || newStartTimestamp < minTimestamp) {
                            minTimestamp = newStartTimestamp;
                        }
                        if (maxTimestamp == null || newEndTimestamp > maxTimestamp) {
                            maxTimestamp = newEndTimestamp;
                        }
                    }
                }

                // Update host if changed
                if (hostChanged) {
                    hangout.setHostAtPlaceUserId(effectiveDefaultHostId);
                    hangoutUpdated = true;
                }

                if (hangoutUpdated) {
                    // Save hangout
                    hangoutRepository.save(hangout);

                    // Update HangoutPointer
                    updateHangoutPointer(hangout, groupId);
                }
            }
        }

        // 9. Update series timestamps if needed
        if (minTimestamp != null) {
            series.setStartTimestamp(minTimestamp);
        }
        if (maxTimestamp != null) {
            series.setEndTimestamp(maxTimestamp);
        }

        // 10. Save series
        eventSeriesRepository.save(series);

        // 11. Update series pointer (preserve existing parts and interestLevels)
        Optional<SeriesPointer> existingPointerOpt = groupRepository.findSeriesPointer(groupId, seriesId);
        SeriesPointer seriesPointer;
        if (existingPointerOpt.isPresent()) {
            seriesPointer = existingPointerOpt.get();
            // Update mutable fields from series
            seriesPointer.setSeriesTitle(series.getSeriesTitle());
            seriesPointer.setSeriesDescription(series.getSeriesDescription());
            seriesPointer.setStartTimestamp(series.getStartTimestamp());
            seriesPointer.setEndTimestamp(series.getEndTimestamp());
            seriesPointer.setHangoutIds(series.getHangoutIds() != null ? new ArrayList<>(series.getHangoutIds()) : new ArrayList<>());
            seriesPointer.setDefaultTime(series.getDefaultTime());
            seriesPointer.setDayOverride(series.getDayOverride());
            seriesPointer.setTimezone(series.getTimezone());
            seriesPointer.setDefaultHostId(series.getDefaultHostId());
            seriesPointer.setMainImagePath(series.getMainImagePath());

            // Update parts for any hangouts that were modified in the cascade loop
            if (seriesPointer.getParts() != null && shouldCascade && (timeSettingsChanged || hostChanged)) {
                for (int i = 0; i < seriesPointer.getParts().size(); i++) {
                    HangoutPointer part = seriesPointer.getParts().get(i);
                    String partHangoutId = part.getHangoutId();
                    // Re-fetch the pointer that was saved during the cascade
                    if (partHangoutId != null) {
                        Optional<Hangout> updatedHangoutOpt = hangoutRepository.findHangoutById(partHangoutId);
                        if (updatedHangoutOpt.isPresent()) {
                            Hangout updatedHangout = updatedHangoutOpt.get();
                            part.setTitle(updatedHangout.getTitle());
                            part.setStartTimestamp(updatedHangout.getStartTimestamp());
                            part.setEndTimestamp(updatedHangout.getEndTimestamp());
                            part.setTimeInput(updatedHangout.getTimeInput());
                            part.setHostAtPlaceUserId(updatedHangout.getHostAtPlaceUserId());
                            part.setGsi1sk(String.valueOf(updatedHangout.getStartTimestamp()));
                        }
                    }
                }
            }
        } else {
            logger.warn("SeriesPointer not found for series {} in group {}, creating new (parts/interestLevels will be empty)",
                    seriesId, groupId);
            seriesPointer = SeriesPointer.fromEventSeries(series, groupId);
        }
        seriesPointer.setGsi1sk(String.valueOf(series.getStartTimestamp()));
        groupRepository.saveSeriesPointer(seriesPointer);

        // 12. Update group timestamp for cache invalidation
        groupTimestampService.updateGroupTimestamps(List.of(groupId));

        logger.info("Updated watch party {} in group {} with cascade={}", seriesId, groupId, shouldCascade);

        // Return updated details using existing getWatchParty method
        return getWatchParty(groupId, seriesId, requestingUserId);
    }

    @Override
    public void setUserInterest(String seriesId, String level, String requestingUserId) {
        SeriesInterestContext ctx = validateAndGetSeriesInterestContext(seriesId, requestingUserId);

        // Get user profile for denormalized data
        User user = userRepository.findById(requestingUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + requestingUserId));

        // Create InterestLevel with denormalized user data
        InterestLevel interestLevel = new InterestLevel();
        interestLevel.setUserId(requestingUserId);
        interestLevel.setStatus(level);  // GOING, INTERESTED, NOT_GOING
        interestLevel.setUserName(user.getDisplayName() != null ? user.getDisplayName() : user.getUsername());
        interestLevel.setMainImagePath(user.getMainImagePath());

        // Find and update the SeriesPointer
        if (ctx.pointer() == null) {
            throw new ResourceNotFoundException("Series pointer not found for group: " + ctx.groupId());
        }

        ctx.pointer().setOrUpdateInterestLevel(interestLevel);

        // Save the updated SeriesPointer
        groupRepository.saveSeriesPointer(ctx.pointer());

        // Update group timestamp for ETag invalidation
        groupTimestampService.updateGroupTimestamps(List.of(ctx.groupId()));

        logger.info("User {} set interest level {} on watch party series {}", requestingUserId, level, seriesId);
    }

    @Override
    public void removeUserInterest(String seriesId, String requestingUserId) {
        SeriesInterestContext ctx = validateAndGetSeriesInterestContext(seriesId, requestingUserId);

        // If no pointer exists, nothing to remove — idempotent return
        if (ctx.pointer() == null) {
            return;
        }

        boolean removed = ctx.pointer().removeInterestLevel(requestingUserId);

        if (removed) {
            // Only persist and invalidate cache if something was actually removed
            groupRepository.saveSeriesPointer(ctx.pointer());
            groupTimestampService.updateGroupTimestamps(List.of(ctx.groupId()));
            logger.info("User {} removed interest from watch party series {}", requestingUserId, seriesId);
        }
    }

    /**
     * Shared validation for series interest operations.
     * Looks up the series, verifies it's a watch party, validates group membership,
     * and returns the context needed for interest operations.
     */
    private SeriesInterestContext validateAndGetSeriesInterestContext(String seriesId, String requestingUserId) {
        EventSeries series = eventSeriesRepository.findById(seriesId)
                .orElseThrow(() -> new ResourceNotFoundException("Watch party series not found: " + seriesId));

        if (!WATCH_PARTY_TYPE.equals(series.getEventSeriesType())) {
            throw new ResourceNotFoundException("Series is not a watch party: " + seriesId);
        }

        String groupId = series.getGroupId();
        if (groupId == null) {
            throw new ResourceNotFoundException("Watch party has no associated group: " + seriesId);
        }

        if (!groupRepository.isUserMemberOfGroup(groupId, requestingUserId)) {
            throw new UnauthorizedException("User is not a member of the watch party group");
        }

        SeriesPointer pointer = groupRepository.findSeriesPointer(groupId, seriesId)
                .orElse(null);
        return new SeriesInterestContext(groupId, pointer);
    }

    private record SeriesInterestContext(String groupId, SeriesPointer pointer) {}

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

    private void validateShowImageUrl(String url) {
        if (url.length() > 2048) {
            throw new ValidationException("showImageUrl exceeds maximum length");
        }
        if (!url.startsWith(TVMAZE_IMAGE_PREFIX)) {
            throw new ValidationException("showImageUrl must be a TVMaze image URL");
        }
    }

    // ============================================================================
    // HELPER METHODS - EPISODE RESOLUTION
    // ============================================================================

    /**
     * Resolve episodes for a watch party request.
     * If tvmazeSeasonId is provided and episodes are empty, fetches from TVMaze.
     * Otherwise, uses the provided episodes.
     *
     * @param request The watch party request
     * @return List of episode requests to use for creating the watch party
     * @throws ValidationException if neither episodes nor tvmazeSeasonId is provided
     */
    private List<CreateWatchPartyEpisodeRequest> resolveEpisodes(CreateWatchPartyRequest request) {
        if (request.shouldFetchFromTvMaze()) {
            logger.info("Fetching episodes from TVMaze for season {}", request.getTvmazeSeasonId());
            List<TvMazeEpisodeResponse> tvMazeEpisodes = tvMazeClient.getEpisodes(request.getTvmazeSeasonId());
            List<CreateWatchPartyEpisodeRequest> episodes = convertTvMazeEpisodes(tvMazeEpisodes);
            if (episodes.isEmpty()) {
                throw new ValidationException("No episodes with valid air dates found for TVMaze season " + request.getTvmazeSeasonId());
            }
            return episodes;
        }

        // Use provided episodes
        if (request.getEpisodes() == null || request.getEpisodes().isEmpty()) {
            throw new ValidationException("Either tvmazeSeasonId or episodes must be provided");
        }

        return request.getEpisodes();
    }

    /**
     * Convert TVMaze episode responses to CreateWatchPartyEpisodeRequest objects.
     *
     * @param tvMazeEpisodes Episodes from TVMaze API
     * @return List of episode requests for watch party creation
     */
    private List<CreateWatchPartyEpisodeRequest> convertTvMazeEpisodes(List<TvMazeEpisodeResponse> tvMazeEpisodes) {
        return tvMazeEpisodes.stream()
                .filter(ep -> TvMazeClient.parseAirstamp(ep.getAirstamp()) != null) // Exclude episodes without air dates
                .map(ep -> CreateWatchPartyEpisodeRequest.builder()
                        .episodeId(ep.getId())
                        .episodeNumber(ep.getNumber())
                        .title(ep.getName())
                        .airTimestamp(TvMazeClient.parseAirstamp(ep.getAirstamp()))
                        .runtime(ep.getRuntime())
                        .build())
                .collect(Collectors.toList());
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

        // Filter out episodes without air timestamps and sort
        List<CreateWatchPartyEpisodeRequest> sorted = episodes.stream()
                .filter(ep -> ep.getAirTimestamp() != null)
                .sorted(Comparator.comparing(CreateWatchPartyEpisodeRequest::getAirTimestamp))
                .collect(Collectors.toList());

        if (sorted.isEmpty()) {
            return List.of();
        }

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

    private Season createOrUpdateSeason(CreateWatchPartyRequest request, List<CreateWatchPartyEpisodeRequest> episodes) {
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
        season.setTvmazeSeasonId(request.getTvmazeSeasonId());

        // Add episodes (either from request or fetched from TVMaze)
        for (CreateWatchPartyEpisodeRequest ep : episodes) {
            int episodeNumber = ep.getEpisodeNumber() != null ? ep.getEpisodeNumber() : 0;
            Episode episode = new Episode(ep.getEpisodeId(), episodeNumber, ep.getTitle());
            episode.setAirTimestamp(ep.getAirTimestamp());
            episode.setRuntime(ep.getRuntime());
            episode.setType("regular");
            season.addEpisode(episode);
        }

        // Note: Season is saved later in saveAllRecords() to avoid duplicate writes
        return season;
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

        // Set show image if provided
        if (request.getShowImageUrl() != null) {
            validateShowImageUrl(request.getShowImageUrl());
            series.setMainImagePath(request.getShowImageUrl());
        }

        return series;
    }

    private Hangout createHangout(CombinedEpisode combined, String seriesId, String groupId,
                                  long startTimestamp, long endTimestamp, String defaultHostId,
                                  String timezone) {
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

        // Set TimeInfo with ISO-8601 formatted times (required for frontend display)
        TimeInfo timeInfo = new TimeInfo();
        ZoneId zone = ZoneId.of(timezone);
        ZonedDateTime startZdt = Instant.ofEpochSecond(startTimestamp).atZone(zone);
        ZonedDateTime endZdt = Instant.ofEpochSecond(endTimestamp).atZone(zone);
        timeInfo.setStartTime(startZdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        timeInfo.setEndTime(endZdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        hangout.setTimeInput(timeInfo);

        // Set DynamoDB keys
        hangout.setPk(InviterKeyFactory.getEventPk(hangout.getHangoutId()));
        hangout.setSk(InviterKeyFactory.getMetadataSk());

        return hangout;
    }

    private HangoutPointer createHangoutPointer(Hangout hangout, String groupId) {
        return HangoutPointerFactory.fromHangout(hangout, groupId);
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
    // HELPER METHODS - UPDATE OPERATIONS
    // ============================================================================

    /**
     * Get the Season record associated with an EventSeries.
     * Parses the seasonId field to look up showId and seasonNumber.
     *
     * @param series The EventSeries to get Season for
     * @return The Season record, or null if not found
     */
    private Season getSeasonFromSeries(EventSeries series) {
        if (series.getSeasonId() == null) {
            return null;
        }

        // Parse seasonId: "TVMAZE#SHOW#{showId}|SEASON#{seasonNumber}"
        String[] parts = series.getSeasonId().split("\\|");
        if (parts.length != 2) {
            logger.warn("Invalid seasonId format: {}", series.getSeasonId());
            return null;
        }

        Integer showId = parseShowIdFromPk(parts[0]);
        Integer seasonNumber = parseSeasonNumberFromSk(parts[1]);

        if (showId == null || seasonNumber == null) {
            return null;
        }

        return seasonRepository.findByShowIdAndSeasonNumber(showId, seasonNumber).orElse(null);
    }

    /**
     * Get the original air timestamp for a hangout from its episode.
     * Uses the hangout's externalId to find the matching episode in the Season.
     *
     * @param hangout The hangout to get air timestamp for
     * @param season The Season containing episode data
     * @return The original air timestamp, or null if not found
     */
    private Long getOriginalAirTimestamp(Hangout hangout, Season season) {
        if (hangout.getExternalId() == null || season.getEpisodes() == null) {
            return null;
        }

        try {
            // externalId is the TVMaze episode ID
            Integer episodeId = Integer.parseInt(hangout.getExternalId());
            return season.findEpisodeById(episodeId)
                    .map(Episode::getAirTimestamp)
                    .orElse(null);
        } catch (NumberFormatException e) {
            logger.warn("Failed to parse episode ID from externalId: {}", hangout.getExternalId());
            return null;
        }
    }

    /**
     * Update the HangoutPointer to match the canonical Hangout record.
     * This keeps the denormalized data in sync after updates.
     *
     * @param hangout The updated Hangout
     * @param groupId The group the hangout belongs to
     */
    private void updateHangoutPointer(Hangout hangout, String groupId) {
        pointerUpdateService.upsertPointerWithRetry(groupId, hangout.getHangoutId(), hangout,
            pointer -> HangoutPointerFactory.applyHangoutFields(pointer, hangout),
            "watch party cascade");
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
