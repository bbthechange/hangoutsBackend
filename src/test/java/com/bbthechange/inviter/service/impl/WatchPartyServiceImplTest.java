package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.client.TvMazeClient;
import com.bbthechange.inviter.dto.TimeInfo;
import com.bbthechange.inviter.dto.watchparty.*;
import com.bbthechange.inviter.exception.ResourceNotFoundException;
import com.bbthechange.inviter.exception.UnauthorizedException;
import com.bbthechange.inviter.exception.ValidationException;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.*;
import com.bbthechange.inviter.service.GroupTimestampService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WatchPartyServiceImpl.
 * Tests business logic for watch party operations.
 */
@ExtendWith(MockitoExtension.class)
class WatchPartyServiceImplTest {

    private static final String GROUP_ID = "12345678-1234-1234-1234-123456789012";
    private static final String USER_ID = "87654321-4321-4321-4321-210987654321";
    private static final String SERIES_ID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
    private static final String HANGOUT_ID = "11111111-2222-3333-4444-555555555555";
    private static final String DEFAULT_HOST_ID = "host-user-id-123";

    // Base timestamp: January 1, 2025, 00:00 UTC
    private static final long BASE_TIMESTAMP = 1735689600L;
    private static final long ONE_HOUR = 3600L;
    private static final long TWENTY_HOURS_SECONDS = 20 * 60 * 60; // 72000 seconds

    private static final int SHOW_ID = 123;
    private static final int SEASON_NUMBER = 1;
    private static final String SHOW_NAME = "Test Show";
    private static final String TIMEZONE = "America/Los_Angeles";
    private static final String DEFAULT_TIME = "20:00";

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private HangoutRepository hangoutRepository;

    @Mock
    private EventSeriesRepository eventSeriesRepository;

    @Mock
    private SeasonRepository seasonRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupTimestampService groupTimestampService;

    @Mock
    private TvMazeClient tvMazeClient;

    @Mock
    private PointerUpdateService pointerUpdateService;

    @InjectMocks
    private WatchPartyServiceImpl watchPartyService;

    private EventSeries testSeries;
    private Season testSeason;
    private Hangout testHangout;

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    private CreateWatchPartyEpisodeRequest createEpisode(int id, String title, long airTimestamp, int runtime) {
        return CreateWatchPartyEpisodeRequest.builder()
                .episodeId(id)
                .title(title)
                .airTimestamp(airTimestamp)
                .runtime(runtime)
                .build();
    }

    private CreateWatchPartyRequest createValidRequest(List<CreateWatchPartyEpisodeRequest> episodes) {
        return CreateWatchPartyRequest.builder()
                .showId(SHOW_ID)
                .seasonNumber(SEASON_NUMBER)
                .showName(SHOW_NAME)
                .defaultTime(DEFAULT_TIME)
                .timezone(TIMEZONE)
                .episodes(episodes)
                .build();
    }

    private EventSeries createWatchPartySeries() {
        EventSeries series = new EventSeries("Test Show Season 1", null, GROUP_ID);
        series.setSeriesId(SERIES_ID);
        series.setEventSeriesType("WATCH_PARTY");
        series.setGroupId(GROUP_ID);
        series.setSeasonId("TVMAZE#SHOW#" + SHOW_ID + "|SEASON#" + SEASON_NUMBER);
        series.setHangoutIds(new ArrayList<>(List.of("hangout-1", "hangout-2")));
        series.setDefaultTime(DEFAULT_TIME);
        series.setTimezone(TIMEZONE);
        return series;
    }

    private Hangout createHangout(String hangoutId, String title) {
        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setTitle(title);
        hangout.setStartTimestamp(BASE_TIMESTAMP);
        hangout.setEndTimestamp(BASE_TIMESTAMP + ONE_HOUR);
        hangout.setExternalId("101");
        return hangout;
    }

    @BeforeEach
    void setUp() {
        // Create a test series
        testSeries = new EventSeries("Test Show Season 1", null, GROUP_ID);
        testSeries.setSeriesId(SERIES_ID);
        testSeries.setEventSeriesType("WATCH_PARTY");
        testSeries.setGroupId(GROUP_ID);
        testSeries.setSeasonId("TVMAZE#SHOW#123|SEASON#1");
        testSeries.setDefaultTime("20:00");
        testSeries.setTimezone("America/Los_Angeles");
        testSeries.setDayOverride(null);
        testSeries.setDefaultHostId(null);
        testSeries.setHangoutIds(new ArrayList<>(List.of(HANGOUT_ID)));
        testSeries.setStartTimestamp(1704067200L); // Future timestamp
        testSeries.setEndTimestamp(1704070800L);

        // Create test season with episodes
        testSeason = new Season(123, 1, "Test Show");
        Episode episode = new Episode(456, 1, "Pilot");
        episode.setAirTimestamp(1704067200L); // Original air timestamp
        episode.setRuntime(60);
        testSeason.addEpisode(episode);

        // Create test hangout
        testHangout = new Hangout();
        testHangout.setHangoutId(HANGOUT_ID);
        testHangout.setTitle("Pilot");
        testHangout.setSeriesId(SERIES_ID);
        testHangout.setExternalId("456"); // Episode ID
        testHangout.setStartTimestamp(Instant.now().getEpochSecond() + 86400); // Tomorrow
        testHangout.setEndTimestamp(Instant.now().getEpochSecond() + 86400 + 3600);
        testHangout.setHostAtPlaceUserId(null);
    }

    // ============================================================================
    // EPISODE COMBINATION TESTS
    // ============================================================================

    @Nested
    class CombineEpisodesTests {

        @Test
        void combineEpisodes_withEmptyList_returnsEmptyList() {
            // Given
            List<CreateWatchPartyEpisodeRequest> episodes = List.of();

            // When
            List<WatchPartyServiceImpl.CombinedEpisode> result = watchPartyService.combineEpisodes(episodes);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        void combineEpisodes_withNullList_returnsEmptyList() {
            // When
            List<WatchPartyServiceImpl.CombinedEpisode> result = watchPartyService.combineEpisodes(null);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        void combineEpisodes_withSingleEpisode_returnsSingleCombined() {
            // Given
            List<CreateWatchPartyEpisodeRequest> episodes = List.of(
                    createEpisode(101, "Pilot", BASE_TIMESTAMP, 60)
            );

            // When
            List<WatchPartyServiceImpl.CombinedEpisode> result = watchPartyService.combineEpisodes(episodes);

            // Then
            assertThat(result).hasSize(1);
            WatchPartyServiceImpl.CombinedEpisode combined = result.get(0);
            assertThat(combined.getTitle()).isEqualTo("Pilot");
            assertThat(combined.getPrimaryEpisodeId()).isEqualTo(101);
            assertThat(combined.getAllEpisodeIds()).containsExactly("101");
            assertThat(combined.getTotalRuntime()).isEqualTo(60);
        }

        @Test
        void combineEpisodes_twoEpisodesWithin20Hours_combinesIntoOne() {
            // Given - Episodes 10 hours apart (within 20-hour threshold)
            long tenHoursLater = BASE_TIMESTAMP + (10 * ONE_HOUR);
            List<CreateWatchPartyEpisodeRequest> episodes = List.of(
                    createEpisode(101, "Episode 1", BASE_TIMESTAMP, 45),
                    createEpisode(102, "Episode 2", tenHoursLater, 45)
            );

            // When
            List<WatchPartyServiceImpl.CombinedEpisode> result = watchPartyService.combineEpisodes(episodes);

            // Then
            assertThat(result).hasSize(1);
            WatchPartyServiceImpl.CombinedEpisode combined = result.get(0);
            assertThat(combined.getTitle()).isEqualTo("Double Episode: Episode 1, Episode 2");
            assertThat(combined.getAllEpisodeIds()).containsExactly("101", "102");
            assertThat(combined.getTotalRuntime()).isEqualTo(90); // 45 + 45
        }

        @Test
        void combineEpisodes_twoEpisodesMoreThan20HoursApart_remainsSeparate() {
            // Given - Episodes 25 hours apart (beyond 20-hour threshold)
            long twentyFiveHoursLater = BASE_TIMESTAMP + (25 * ONE_HOUR);
            List<CreateWatchPartyEpisodeRequest> episodes = List.of(
                    createEpisode(101, "Episode 1", BASE_TIMESTAMP, 60),
                    createEpisode(102, "Episode 2", twentyFiveHoursLater, 60)
            );

            // When
            List<WatchPartyServiceImpl.CombinedEpisode> result = watchPartyService.combineEpisodes(episodes);

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getTitle()).isEqualTo("Episode 1");
            assertThat(result.get(1).getTitle()).isEqualTo("Episode 2");
        }

        @Test
        void combineEpisodes_threeConsecutiveEpisodesWithin20Hours_allCombined() {
            // Given - 3 episodes within 20 hours of each other
            List<CreateWatchPartyEpisodeRequest> episodes = List.of(
                    createEpisode(101, "Episode 1", BASE_TIMESTAMP, 30),
                    createEpisode(102, "Episode 2", BASE_TIMESTAMP + (5 * ONE_HOUR), 30),
                    createEpisode(103, "Episode 3", BASE_TIMESTAMP + (10 * ONE_HOUR), 30)
            );

            // When
            List<WatchPartyServiceImpl.CombinedEpisode> result = watchPartyService.combineEpisodes(episodes);

            // Then
            assertThat(result).hasSize(1);
            WatchPartyServiceImpl.CombinedEpisode combined = result.get(0);
            assertThat(combined.getTitle()).isEqualTo("Triple Episode");
            assertThat(combined.getAllEpisodeIds()).containsExactly("101", "102", "103");
            assertThat(combined.getTotalRuntime()).isEqualTo(90); // 30 + 30 + 30
        }

        @Test
        void combineEpisodes_mixedGaps_correctlyGroups() {
            // Given
            // Group 1: E1, E2 close (5 hours apart)
            // Gap: 30 hours
            // E3 standalone
            // Gap: 25 hours
            // Group 2: E4, E5 close (8 hours apart)
            long e1Time = BASE_TIMESTAMP;
            long e2Time = e1Time + (5 * ONE_HOUR);    // 5 hours after E1
            long e3Time = e2Time + (30 * ONE_HOUR);   // 30 hours after E2 (standalone)
            long e4Time = e3Time + (25 * ONE_HOUR);   // 25 hours after E3
            long e5Time = e4Time + (8 * ONE_HOUR);    // 8 hours after E4 (grouped)

            List<CreateWatchPartyEpisodeRequest> episodes = List.of(
                    createEpisode(101, "Episode 1", e1Time, 45),
                    createEpisode(102, "Episode 2", e2Time, 45),
                    createEpisode(103, "Episode 3", e3Time, 60),
                    createEpisode(104, "Episode 4", e4Time, 45),
                    createEpisode(105, "Episode 5", e5Time, 45)
            );

            // When
            List<WatchPartyServiceImpl.CombinedEpisode> result = watchPartyService.combineEpisodes(episodes);

            // Then
            assertThat(result).hasSize(3);
            // Group 1: E1 + E2
            assertThat(result.get(0).getAllEpisodeIds()).containsExactly("101", "102");
            assertThat(result.get(0).getTitle()).isEqualTo("Double Episode: Episode 1, Episode 2");
            // E3 standalone
            assertThat(result.get(1).getAllEpisodeIds()).containsExactly("103");
            assertThat(result.get(1).getTitle()).isEqualTo("Episode 3");
            // Group 2: E4 + E5
            assertThat(result.get(2).getAllEpisodeIds()).containsExactly("104", "105");
        }

        @Test
        void combineEpisodes_withNullAirTimestamps_filtersOutNulls() {
            // Given
            CreateWatchPartyEpisodeRequest epWithNull = CreateWatchPartyEpisodeRequest.builder()
                    .episodeId(102)
                    .title("TBA")
                    .airTimestamp(null) // No air timestamp
                    .runtime(60)
                    .build();

            List<CreateWatchPartyEpisodeRequest> episodes = List.of(
                    createEpisode(101, "Episode 1", BASE_TIMESTAMP, 60),
                    epWithNull,
                    createEpisode(103, "Episode 3", BASE_TIMESTAMP + (5 * ONE_HOUR), 60)
            );

            // When
            List<WatchPartyServiceImpl.CombinedEpisode> result = watchPartyService.combineEpisodes(episodes);

            // Then - Only episodes with air timestamps are combined
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getAllEpisodeIds()).containsExactly("101", "103");
        }

        @Test
        void combineEpisodes_allNullAirTimestamps_returnsEmptyList() {
            // Given
            List<CreateWatchPartyEpisodeRequest> episodes = List.of(
                    CreateWatchPartyEpisodeRequest.builder()
                            .episodeId(101)
                            .title("TBA 1")
                            .airTimestamp(null)
                            .runtime(60)
                            .build(),
                    CreateWatchPartyEpisodeRequest.builder()
                            .episodeId(102)
                            .title("TBA 2")
                            .airTimestamp(null)
                            .runtime(60)
                            .build()
            );

            // When
            List<WatchPartyServiceImpl.CombinedEpisode> result = watchPartyService.combineEpisodes(episodes);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        void combineEpisodes_unsortedInput_sortsBeforeCombining() {
            // Given - Episodes provided out of order
            List<CreateWatchPartyEpisodeRequest> episodes = List.of(
                    createEpisode(103, "Episode 3", BASE_TIMESTAMP + (25 * ONE_HOUR), 60),
                    createEpisode(101, "Episode 1", BASE_TIMESTAMP, 60),
                    createEpisode(102, "Episode 2", BASE_TIMESTAMP + (5 * ONE_HOUR), 60)
            );

            // When
            List<WatchPartyServiceImpl.CombinedEpisode> result = watchPartyService.combineEpisodes(episodes);

            // Then - Should sort and combine E1+E2 (close), E3 separate
            assertThat(result).hasSize(2);
            // First combined group: E1 + E2 (within 20 hours)
            assertThat(result.get(0).getAllEpisodeIds()).containsExactly("101", "102");
            // Second: E3 standalone (25 hours from E2)
            assertThat(result.get(1).getAllEpisodeIds()).containsExactly("103");
        }
    }

    // ============================================================================
    // CREATE WATCH PARTY TESTS
    // ============================================================================

    @Nested
    class CreateWatchPartyTests {

        @Test
        void createWatchParty_withValidRequest_createsSeriesAndHangouts() {
            // Given
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);

            List<CreateWatchPartyEpisodeRequest> episodes = List.of(
                    createEpisode(101, "Pilot", BASE_TIMESTAMP, 60),
                    createEpisode(102, "Episode 2", BASE_TIMESTAMP + (25 * ONE_HOUR), 45)
            );
            CreateWatchPartyRequest request = createValidRequest(episodes);

            when(seasonRepository.findByShowIdAndSeasonNumber(SHOW_ID, SEASON_NUMBER))
                    .thenReturn(Optional.empty());

            // When
            WatchPartyResponse response = watchPartyService.createWatchParty(GROUP_ID, request, USER_ID);

            // Then - Verify response
            assertThat(response.getSeriesTitle()).isEqualTo("Test Show Season 1");
            assertThat(response.getHangouts()).hasSize(2); // 2 separate episodes

            // Verify season was saved
            verify(seasonRepository).save(any(Season.class));

            // Verify event series was saved
            ArgumentCaptor<EventSeries> seriesCaptor = ArgumentCaptor.forClass(EventSeries.class);
            verify(eventSeriesRepository).save(seriesCaptor.capture());
            EventSeries savedSeries = seriesCaptor.getValue();
            assertThat(savedSeries.getEventSeriesType()).isEqualTo("WATCH_PARTY");
            assertThat(savedSeries.getGroupId()).isEqualTo(GROUP_ID);

            // Verify hangouts were saved
            verify(hangoutRepository, times(2)).save(any(Hangout.class));

            // Verify pointers were saved
            verify(groupRepository, times(2)).saveHangoutPointer(any(HangoutPointer.class));
            verify(groupRepository).saveSeriesPointer(any(SeriesPointer.class));

            // Verify group timestamp was updated
            verify(groupTimestampService).updateGroupTimestamps(List.of(GROUP_ID));
        }

        @Test
        void createWatchParty_whenUserNotInGroup_throwsUnauthorized() {
            // Given
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(false);
            CreateWatchPartyRequest request = createValidRequest(List.of(
                    createEpisode(101, "Pilot", BASE_TIMESTAMP, 60)
            ));

            // When/Then
            assertThatThrownBy(() -> watchPartyService.createWatchParty(GROUP_ID, request, USER_ID))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("User is not a member of group");
        }

        @Test
        void createWatchParty_withInvalidTimezone_throwsValidation() {
            // Given
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);

            CreateWatchPartyRequest request = CreateWatchPartyRequest.builder()
                    .showId(SHOW_ID)
                    .seasonNumber(SEASON_NUMBER)
                    .showName(SHOW_NAME)
                    .defaultTime(DEFAULT_TIME)
                    .timezone("Invalid/Timezone")
                    .episodes(List.of(createEpisode(101, "Pilot", BASE_TIMESTAMP, 60)))
                    .build();

            // When/Then
            assertThatThrownBy(() -> watchPartyService.createWatchParty(GROUP_ID, request, USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Invalid timezone");
        }

        @Test
        void createWatchParty_withNoEpisodesAndNoTvMaze_throwsValidation() {
            // Given
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);

            // No tvmazeSeasonId and no episodes
            CreateWatchPartyRequest request = CreateWatchPartyRequest.builder()
                    .showId(SHOW_ID)
                    .seasonNumber(SEASON_NUMBER)
                    .showName(SHOW_NAME)
                    .defaultTime(DEFAULT_TIME)
                    .timezone(TIMEZONE)
                    .tvmazeSeasonId(null)
                    .episodes(null)
                    .build();

            // When/Then
            assertThatThrownBy(() -> watchPartyService.createWatchParty(GROUP_ID, request, USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Either tvmazeSeasonId or episodes must be provided");
        }

        @Test
        void createWatchParty_withExistingSeason_reusesSeason() {
            // Given
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);

            Season existingSeason = new Season(SHOW_ID, SEASON_NUMBER, SHOW_NAME);
            when(seasonRepository.findByShowIdAndSeasonNumber(SHOW_ID, SEASON_NUMBER))
                    .thenReturn(Optional.of(existingSeason));

            CreateWatchPartyRequest request = createValidRequest(List.of(
                    createEpisode(101, "Pilot", BASE_TIMESTAMP, 60)
            ));

            // When
            watchPartyService.createWatchParty(GROUP_ID, request, USER_ID);

            // Then - Season is saved but it's the existing one (upsert behavior)
            verify(seasonRepository).save(existingSeason);
        }

        @Test
        void createWatchParty_generatesCorrectSeriesTitle() {
            // Given
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);

            CreateWatchPartyRequest request = CreateWatchPartyRequest.builder()
                    .showId(SHOW_ID)
                    .seasonNumber(3)
                    .showName("Breaking Bad")
                    .defaultTime(DEFAULT_TIME)
                    .timezone(TIMEZONE)
                    .episodes(List.of(createEpisode(101, "Pilot", BASE_TIMESTAMP, 60)))
                    .build();

            when(seasonRepository.findByShowIdAndSeasonNumber(SHOW_ID, 3))
                    .thenReturn(Optional.empty());

            // When
            WatchPartyResponse response = watchPartyService.createWatchParty(GROUP_ID, request, USER_ID);

            // Then
            assertThat(response.getSeriesTitle()).isEqualTo("Breaking Bad Season 3");
        }

        @Test
        void createWatchParty_SetsTvmazeSeasonIdOnNewSeason() {
            // Given
            Integer tvmazeSeasonId = 12345;
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);

            CreateWatchPartyRequest request = CreateWatchPartyRequest.builder()
                    .showId(SHOW_ID)
                    .seasonNumber(SEASON_NUMBER)
                    .showName(SHOW_NAME)
                    .defaultTime(DEFAULT_TIME)
                    .timezone(TIMEZONE)
                    .tvmazeSeasonId(tvmazeSeasonId)
                    .episodes(List.of(createEpisode(101, "Pilot", BASE_TIMESTAMP, 60)))
                    .build();

            // Mock season repository to return empty (new season)
            when(seasonRepository.findByShowIdAndSeasonNumber(SHOW_ID, SEASON_NUMBER))
                    .thenReturn(Optional.empty());

            // When
            watchPartyService.createWatchParty(GROUP_ID, request, USER_ID);

            // Then - Capture the Season object passed to seasonRepository.save()
            ArgumentCaptor<Season> seasonCaptor = ArgumentCaptor.forClass(Season.class);
            verify(seasonRepository).save(seasonCaptor.capture());
            Season savedSeason = seasonCaptor.getValue();

            // Verify tvmazeSeasonId is set correctly from the request
            assertThat(savedSeason.getTvmazeSeasonId()).isEqualTo(tvmazeSeasonId);
        }

        @Test
        void createWatchParty_PreservesNullTvmazeSeasonId() {
            // Given - request with null tvmazeSeasonId
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);

            CreateWatchPartyRequest request = CreateWatchPartyRequest.builder()
                    .showId(SHOW_ID)
                    .seasonNumber(SEASON_NUMBER)
                    .showName(SHOW_NAME)
                    .defaultTime(DEFAULT_TIME)
                    .timezone(TIMEZONE)
                    .tvmazeSeasonId(null) // Explicitly null
                    .episodes(List.of(createEpisode(101, "Pilot", BASE_TIMESTAMP, 60)))
                    .build();

            // Mock season repository to return empty (new season)
            when(seasonRepository.findByShowIdAndSeasonNumber(SHOW_ID, SEASON_NUMBER))
                    .thenReturn(Optional.empty());

            // When
            watchPartyService.createWatchParty(GROUP_ID, request, USER_ID);

            // Then - Capture the Season object passed to seasonRepository.save()
            ArgumentCaptor<Season> seasonCaptor = ArgumentCaptor.forClass(Season.class);
            verify(seasonRepository).save(seasonCaptor.capture());
            Season savedSeason = seasonCaptor.getValue();

            // Verify tvmazeSeasonId is null (graceful handling)
            assertThat(savedSeason.getTvmazeSeasonId()).isNull();
        }
    }

    // ============================================================================
    // GET WATCH PARTY TESTS
    // ============================================================================

    @Nested
    class GetWatchPartyTests {

        @Test
        void getWatchParty_withValidSeriesId_returnsDetails() {
            // Given
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);

            EventSeries series = createWatchPartySeries();
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series));

            Hangout hangout1 = createHangout("hangout-1", "Episode 1");
            Hangout hangout2 = createHangout("hangout-2", "Episode 2");
            hangout2.setStartTimestamp(BASE_TIMESTAMP + ONE_HOUR);

            when(hangoutRepository.findHangoutById("hangout-1")).thenReturn(Optional.of(hangout1));
            when(hangoutRepository.findHangoutById("hangout-2")).thenReturn(Optional.of(hangout2));

            // When
            WatchPartyDetailResponse response = watchPartyService.getWatchParty(GROUP_ID, SERIES_ID, USER_ID);

            // Then
            assertThat(response.getSeriesId()).isEqualTo(SERIES_ID);
            assertThat(response.getGroupId()).isEqualTo(GROUP_ID);
            assertThat(response.getEventSeriesType()).isEqualTo("WATCH_PARTY");
            assertThat(response.getShowId()).isEqualTo(SHOW_ID);
            assertThat(response.getSeasonNumber()).isEqualTo(SEASON_NUMBER);
            assertThat(response.getHangouts()).hasSize(2);
            // Hangouts should be sorted by start timestamp
            assertThat(response.getHangouts().get(0).getTitle()).isEqualTo("Episode 1");
            assertThat(response.getHangouts().get(1).getTitle()).isEqualTo("Episode 2");
        }

        @Test
        void getWatchParty_whenSeriesNotFound_throwsNotFound() {
            // Given
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> watchPartyService.getWatchParty(GROUP_ID, SERIES_ID, USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Watch party not found");
        }

        @Test
        void getWatchParty_whenUserNotInGroup_throwsUnauthorized() {
            // Given
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> watchPartyService.getWatchParty(GROUP_ID, SERIES_ID, USER_ID))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("User is not a member of group");
        }

        @Test
        void getWatchParty_whenSeriesNotWatchParty_throwsValidation() {
            // Given
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);

            // A regular series, not a watch party
            EventSeries regularSeries = new EventSeries("Regular Series", null, GROUP_ID);
            regularSeries.setSeriesId(SERIES_ID);
            regularSeries.setEventSeriesType(null); // Not a watch party
            regularSeries.setGroupId(GROUP_ID);

            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(regularSeries));

            // When/Then
            assertThatThrownBy(() -> watchPartyService.getWatchParty(GROUP_ID, SERIES_ID, USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Watch party not found");
        }

        @Test
        void getWatchParty_whenSeriesNotInGroup_throwsValidation() {
            // Given
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);

            // Series belongs to a different group
            EventSeries series = createWatchPartySeries();
            series.setGroupId("different-group");

            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series));

            // When/Then
            assertThatThrownBy(() -> watchPartyService.getWatchParty(GROUP_ID, SERIES_ID, USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Watch party not found in group");
        }
    }

    // ============================================================================
    // DELETE WATCH PARTY TESTS
    // ============================================================================

    @Nested
    class DeleteWatchPartyTests {

        @Test
        void deleteWatchParty_withValidRequest_deletesAllRecords() {
            // Given
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);

            EventSeries series = createWatchPartySeries();
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series));

            // When
            watchPartyService.deleteWatchParty(GROUP_ID, SERIES_ID, USER_ID);

            // Then - Verify all deletions
            // Delete hangout pointers
            verify(groupRepository).deleteHangoutPointer(GROUP_ID, "hangout-1");
            verify(groupRepository).deleteHangoutPointer(GROUP_ID, "hangout-2");

            // Delete hangouts
            verify(hangoutRepository).deleteHangout("hangout-1");
            verify(hangoutRepository).deleteHangout("hangout-2");

            // Delete series pointer
            verify(groupRepository).deleteSeriesPointer(GROUP_ID, SERIES_ID);

            // Delete event series
            verify(eventSeriesRepository).deleteById(SERIES_ID);
        }

        @Test
        void deleteWatchParty_preservesSeasonRecord() {
            // Given
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);

            EventSeries series = createWatchPartySeries();
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series));

            // When
            watchPartyService.deleteWatchParty(GROUP_ID, SERIES_ID, USER_ID);

            // Then - Season should NOT be deleted (other groups may use it)
            verify(seasonRepository, never()).delete(anyInt(), anyInt());
        }

        @Test
        void deleteWatchParty_whenSeriesNotFound_throwsNotFound() {
            // Given
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> watchPartyService.deleteWatchParty(GROUP_ID, SERIES_ID, USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Watch party not found");
        }

        @Test
        void deleteWatchParty_whenUserNotInGroup_throwsUnauthorized() {
            // Given
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> watchPartyService.deleteWatchParty(GROUP_ID, SERIES_ID, USER_ID))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("User is not a member of group");
        }

        @Test
        void deleteWatchParty_updatesGroupTimestamp() {
            // Given
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);

            EventSeries series = createWatchPartySeries();
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(series));

            // When
            watchPartyService.deleteWatchParty(GROUP_ID, SERIES_ID, USER_ID);

            // Then
            verify(groupTimestampService).updateGroupTimestamps(List.of(GROUP_ID));
        }
    }

    // ============================================================================
    // UPDATE WATCH PARTY TESTS
    // ============================================================================

    @Nested
    class UpdateWatchPartyTests {

        @Test
        void updateWatchParty_WithCascade_UpdatesFutureHangouts() {
            // Given
            UpdateWatchPartyRequest request = UpdateWatchPartyRequest.builder()
                    .defaultTime("21:00") // Change time from 20:00 to 21:00
                    .changeExistingUpcomingHangouts(true)
                    .build();

            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(testSeries));
            when(seasonRepository.findByShowIdAndSeasonNumber(123, 1)).thenReturn(Optional.of(testSeason));
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(testHangout));

            // When
            WatchPartyDetailResponse result = watchPartyService.updateWatchParty(
                    GROUP_ID, SERIES_ID, request, USER_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getDefaultTime()).isEqualTo("21:00");

            // Verify hangout was updated
            ArgumentCaptor<Hangout> hangoutCaptor = ArgumentCaptor.forClass(Hangout.class);
            verify(hangoutRepository).save(hangoutCaptor.capture());
            Hangout savedHangout = hangoutCaptor.getValue();
            assertThat(savedHangout.getHangoutId()).isEqualTo(HANGOUT_ID);

            // Verify pointer was updated via upsert (preserves collections)
            verify(pointerUpdateService).upsertPointerWithRetry(
                eq(GROUP_ID), eq(HANGOUT_ID), any(Hangout.class), any(), eq("watch party cascade"));

            // Verify series was saved
            verify(eventSeriesRepository).save(any(EventSeries.class));

            // Verify group timestamp was updated
            verify(groupTimestampService).updateGroupTimestamps(List.of(GROUP_ID));
        }

        @Test
        void updateWatchParty_WithoutCascade_PreservesHangouts() {
            // Given
            UpdateWatchPartyRequest request = UpdateWatchPartyRequest.builder()
                    .defaultTime("21:00")
                    .changeExistingUpcomingHangouts(false) // Don't cascade
                    .build();

            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(testSeries));
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(testHangout));

            // When
            WatchPartyDetailResponse result = watchPartyService.updateWatchParty(
                    GROUP_ID, SERIES_ID, request, USER_ID);

            // Then
            assertThat(result.getDefaultTime()).isEqualTo("21:00");

            // Verify hangout was NOT updated
            verify(hangoutRepository, never()).save(any(Hangout.class));

            // Verify pointer was NOT updated
            verify(pointerUpdateService, never()).upsertPointerWithRetry(
                anyString(), anyString(), any(Hangout.class), any(), anyString());

            // Series still saved with new settings
            verify(eventSeriesRepository).save(any(EventSeries.class));
        }

        @Test
        void updateWatchParty_NeverModifiesPastHangouts() {
            // Given
            testHangout.setStartTimestamp(Instant.now().getEpochSecond() - 86400); // Yesterday (past)
            testHangout.setEndTimestamp(Instant.now().getEpochSecond() - 86400 + 3600);

            UpdateWatchPartyRequest request = UpdateWatchPartyRequest.builder()
                    .defaultTime("21:00")
                    .changeExistingUpcomingHangouts(true)
                    .build();

            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(testSeries));
            when(seasonRepository.findByShowIdAndSeasonNumber(123, 1)).thenReturn(Optional.of(testSeason));
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(testHangout));

            // When
            watchPartyService.updateWatchParty(GROUP_ID, SERIES_ID, request, USER_ID);

            // Then - past hangout should NOT be modified
            verify(hangoutRepository, never()).save(any(Hangout.class));
        }

        @Test
        void updateWatchParty_InvalidTimezone_ThrowsValidationException() {
            // Given
            UpdateWatchPartyRequest request = UpdateWatchPartyRequest.builder()
                    .timezone("Invalid/Timezone")
                    .build();

            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(testSeries));

            // When/Then
            assertThatThrownBy(() ->
                    watchPartyService.updateWatchParty(GROUP_ID, SERIES_ID, request, USER_ID))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Invalid timezone");
        }

        @Test
        void updateWatchParty_NotWatchParty_ThrowsResourceNotFoundException() {
            // Given
            testSeries.setEventSeriesType("REGULAR"); // Not a watch party

            UpdateWatchPartyRequest request = UpdateWatchPartyRequest.builder()
                    .defaultTime("21:00")
                    .build();

            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(testSeries));

            // When/Then
            assertThatThrownBy(() ->
                    watchPartyService.updateWatchParty(GROUP_ID, SERIES_ID, request, USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Watch party not found");
        }

        @Test
        void updateWatchParty_WrongGroup_ThrowsResourceNotFoundException() {
            // Given
            testSeries.setGroupId("different-group-id"); // Different group

            UpdateWatchPartyRequest request = UpdateWatchPartyRequest.builder()
                    .defaultTime("21:00")
                    .build();

            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(testSeries));

            // When/Then
            assertThatThrownBy(() ->
                    watchPartyService.updateWatchParty(GROUP_ID, SERIES_ID, request, USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Watch party not found in group");
        }

        @Test
        void updateWatchParty_UserNotInGroup_ThrowsUnauthorizedException() {
            // Given
            UpdateWatchPartyRequest request = UpdateWatchPartyRequest.builder()
                    .defaultTime("21:00")
                    .build();

            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(false);

            // When/Then
            assertThatThrownBy(() ->
                    watchPartyService.updateWatchParty(GROUP_ID, SERIES_ID, request, USER_ID))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("not a member of group");
        }

        @Test
        void updateWatchParty_SeriesNotFound_ThrowsResourceNotFoundException() {
            // Given
            UpdateWatchPartyRequest request = UpdateWatchPartyRequest.builder()
                    .defaultTime("21:00")
                    .build();

            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() ->
                    watchPartyService.updateWatchParty(GROUP_ID, SERIES_ID, request, USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Watch party not found");
        }

        @Test
        void updateWatchParty_UpdateHostOnly_UpdatesHangoutHost() {
            // Given
            UpdateWatchPartyRequest request = UpdateWatchPartyRequest.builder()
                    .defaultHostId(DEFAULT_HOST_ID)
                    .changeExistingUpcomingHangouts(true)
                    .build();

            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(testSeries));
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(testHangout));

            // When
            WatchPartyDetailResponse result = watchPartyService.updateWatchParty(
                    GROUP_ID, SERIES_ID, request, USER_ID);

            // Then
            assertThat(result.getDefaultHostId()).isEqualTo(DEFAULT_HOST_ID);

            // Verify hangout host was updated
            ArgumentCaptor<Hangout> hangoutCaptor = ArgumentCaptor.forClass(Hangout.class);
            verify(hangoutRepository).save(hangoutCaptor.capture());
            assertThat(hangoutCaptor.getValue().getHostAtPlaceUserId()).isEqualTo(DEFAULT_HOST_ID);
        }

        @Test
        void updateWatchParty_ClearHost_SetsHostToNull() {
            // Given
            testSeries.setDefaultHostId(DEFAULT_HOST_ID); // Previously had a host
            testHangout.setHostAtPlaceUserId(DEFAULT_HOST_ID);

            UpdateWatchPartyRequest request = UpdateWatchPartyRequest.builder()
                    .defaultHostId("") // Empty string clears the host
                    .changeExistingUpcomingHangouts(true)
                    .build();

            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(testSeries));
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(testHangout));

            // When
            WatchPartyDetailResponse result = watchPartyService.updateWatchParty(
                    GROUP_ID, SERIES_ID, request, USER_ID);

            // Then
            assertThat(result.getDefaultHostId()).isNull();

            // Verify hangout host was cleared
            ArgumentCaptor<Hangout> hangoutCaptor = ArgumentCaptor.forClass(Hangout.class);
            verify(hangoutRepository).save(hangoutCaptor.capture());
            assertThat(hangoutCaptor.getValue().getHostAtPlaceUserId()).isNull();
        }

        @Test
        void updateWatchParty_NoChanges_OnlyUpdatesSeriesAndPointer() {
            // Given - request with no changes (all nulls)
            UpdateWatchPartyRequest request = UpdateWatchPartyRequest.builder()
                    .changeExistingUpcomingHangouts(true)
                    .build();

            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(testSeries));
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(testHangout));

            // When
            watchPartyService.updateWatchParty(GROUP_ID, SERIES_ID, request, USER_ID);

            // Then - no hangout updates since nothing changed
            verify(hangoutRepository, never()).save(any(Hangout.class));

            // Series still saved (could have touch() updates)
            verify(eventSeriesRepository).save(any(EventSeries.class));
        }

        @Test
        void updateWatchParty_WithDayOverride_CalculatesCorrectTimestamp() {
            // Given
            UpdateWatchPartyRequest request = UpdateWatchPartyRequest.builder()
                    .dayOverride(6) // Saturday
                    .changeExistingUpcomingHangouts(true)
                    .build();

            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(testSeries));
            when(seasonRepository.findByShowIdAndSeasonNumber(123, 1)).thenReturn(Optional.of(testSeason));
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(testHangout));

            // When
            WatchPartyDetailResponse result = watchPartyService.updateWatchParty(
                    GROUP_ID, SERIES_ID, request, USER_ID);

            // Then
            assertThat(result.getDayOverride()).isEqualTo(6);

            // Verify hangout was updated with new timestamp
            verify(hangoutRepository).save(any(Hangout.class));
        }

        @Test
        void updateWatchParty_DefaultCascadeIsTrue() {
            // Given - request without explicit cascade setting
            UpdateWatchPartyRequest request = UpdateWatchPartyRequest.builder()
                    .defaultTime("21:00")
                    .build();
            // Note: changeExistingUpcomingHangouts should default to true via @Builder.Default

            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(testSeries));
            when(seasonRepository.findByShowIdAndSeasonNumber(123, 1)).thenReturn(Optional.of(testSeason));
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(testHangout));

            // When
            watchPartyService.updateWatchParty(GROUP_ID, SERIES_ID, request, USER_ID);

            // Then - should cascade by default
            verify(hangoutRepository).save(any(Hangout.class));
        }

        @Test
        void updateWatchParty_PreservesExistingParts() {
            // Given - Set up a SeriesPointer with existing parts and interestLevels
            HangoutPointer existingPart = new HangoutPointer(GROUP_ID, HANGOUT_ID, "Pilot");
            existingPart.setStatus("ACTIVE");
            existingPart.setStartTimestamp(testHangout.getStartTimestamp());
            existingPart.setEndTimestamp(testHangout.getEndTimestamp());

            InterestLevel interestLevel = new InterestLevel();
            interestLevel.setUserId(USER_ID);
            interestLevel.setStatus("GOING");

            SeriesPointer existingPointer = SeriesPointer.fromEventSeries(testSeries, GROUP_ID);
            existingPointer.setParts(new ArrayList<>(List.of(existingPart)));
            existingPointer.setInterestLevels(new ArrayList<>(List.of(interestLevel)));

            UpdateWatchPartyRequest request = UpdateWatchPartyRequest.builder()
                    .defaultHostId(DEFAULT_HOST_ID)
                    .changeExistingUpcomingHangouts(true)
                    .build();

            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(testSeries));
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(testHangout));
            when(groupRepository.findSeriesPointer(GROUP_ID, SERIES_ID)).thenReturn(Optional.of(existingPointer));

            // When
            watchPartyService.updateWatchParty(GROUP_ID, SERIES_ID, request, USER_ID);

            // Then - Verify SeriesPointer was saved with parts and interestLevels preserved
            ArgumentCaptor<SeriesPointer> pointerCaptor = ArgumentCaptor.forClass(SeriesPointer.class);
            verify(groupRepository).saveSeriesPointer(pointerCaptor.capture());

            SeriesPointer savedPointer = pointerCaptor.getValue();
            assertThat(savedPointer.getParts()).isNotNull();
            assertThat(savedPointer.getParts()).hasSize(1);
            assertThat(savedPointer.getParts().get(0).getHangoutId()).isEqualTo(HANGOUT_ID);

            assertThat(savedPointer.getInterestLevels()).isNotNull();
            assertThat(savedPointer.getInterestLevels()).hasSize(1);
            assertThat(savedPointer.getInterestLevels().get(0).getUserId()).isEqualTo(USER_ID);
            assertThat(savedPointer.getInterestLevels().get(0).getStatus()).isEqualTo("GOING");
        }

        @Test
        void updateWatchParty_WhenSeriesPointerMissing_CreatesNewPointer() {
            // Given - SeriesPointer not found, should create new one as fallback
            UpdateWatchPartyRequest request = UpdateWatchPartyRequest.builder()
                    .defaultHostId(DEFAULT_HOST_ID)
                    .changeExistingUpcomingHangouts(true)
                    .build();

            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(testSeries));
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(testHangout));
            when(groupRepository.findSeriesPointer(GROUP_ID, SERIES_ID)).thenReturn(Optional.empty());

            // When - should not throw
            watchPartyService.updateWatchParty(GROUP_ID, SERIES_ID, request, USER_ID);

            // Then - a SeriesPointer is still saved (fallback creates new)
            ArgumentCaptor<SeriesPointer> pointerCaptor = ArgumentCaptor.forClass(SeriesPointer.class);
            verify(groupRepository).saveSeriesPointer(pointerCaptor.capture());

            SeriesPointer savedPointer = pointerCaptor.getValue();
            assertThat(savedPointer).isNotNull();
            assertThat(savedPointer.getSeriesId()).isEqualTo(SERIES_ID);
            assertThat(savedPointer.getGroupId()).isEqualTo(GROUP_ID);
        }

        @Test
        void updateWatchParty_PreservesInterestLevelsWithNoCascade() {
            // Given - SeriesPointer with 2 interestLevels (one GOING, one INTERESTED)
            InterestLevel goingLevel = new InterestLevel();
            goingLevel.setUserId(USER_ID);
            goingLevel.setStatus("GOING");

            InterestLevel interestedLevel = new InterestLevel();
            interestedLevel.setUserId("another-user-id-456");
            interestedLevel.setStatus("INTERESTED");

            SeriesPointer existingPointer = SeriesPointer.fromEventSeries(testSeries, GROUP_ID);
            existingPointer.setInterestLevels(new ArrayList<>(List.of(goingLevel, interestedLevel)));

            UpdateWatchPartyRequest request = UpdateWatchPartyRequest.builder()
                    .defaultHostId(DEFAULT_HOST_ID)
                    .changeExistingUpcomingHangouts(false) // No cascade
                    .build();

            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(testSeries));
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(testHangout));
            when(groupRepository.findSeriesPointer(GROUP_ID, SERIES_ID)).thenReturn(Optional.of(existingPointer));

            // When
            watchPartyService.updateWatchParty(GROUP_ID, SERIES_ID, request, USER_ID);

            // Then - Both interestLevels are preserved in the saved SeriesPointer
            ArgumentCaptor<SeriesPointer> pointerCaptor = ArgumentCaptor.forClass(SeriesPointer.class);
            verify(groupRepository).saveSeriesPointer(pointerCaptor.capture());

            SeriesPointer savedPointer = pointerCaptor.getValue();
            assertThat(savedPointer.getInterestLevels()).isNotNull();
            assertThat(savedPointer.getInterestLevels()).hasSize(2);
            assertThat(savedPointer.getInterestLevels())
                    .extracting(InterestLevel::getStatus)
                    .containsExactlyInAnyOrder("GOING", "INTERESTED");
            assertThat(savedPointer.getInterestLevels())
                    .extracting(InterestLevel::getUserId)
                    .containsExactlyInAnyOrder(USER_ID, "another-user-id-456");
        }
    }

    // ============================================================================
    // TITLE GENERATION TESTS
    // ============================================================================

    @Nested
    class TitleGenerationTests {

        @Test
        void generateCombinedTitle_singleEpisode_returnsTitle() {
            // Given
            List<CreateWatchPartyEpisodeRequest> episodes = List.of(
                    createEpisode(101, "The Pilot Episode", BASE_TIMESTAMP, 60)
            );

            // When
            String title = watchPartyService.generateCombinedTitle(episodes);

            // Then
            assertThat(title).isEqualTo("The Pilot Episode");
        }

        @Test
        void generateCombinedTitle_twoEpisodes_returnsDoubleFormat() {
            // Given
            List<CreateWatchPartyEpisodeRequest> episodes = List.of(
                    createEpisode(101, "Part 1", BASE_TIMESTAMP, 45),
                    createEpisode(102, "Part 2", BASE_TIMESTAMP + ONE_HOUR, 45)
            );

            // When
            String title = watchPartyService.generateCombinedTitle(episodes);

            // Then
            assertThat(title).isEqualTo("Double Episode: Part 1, Part 2");
        }

        @Test
        void generateCombinedTitle_threeEpisodes_returnsTriple() {
            // Given
            List<CreateWatchPartyEpisodeRequest> episodes = List.of(
                    createEpisode(101, "E1", BASE_TIMESTAMP, 30),
                    createEpisode(102, "E2", BASE_TIMESTAMP + ONE_HOUR, 30),
                    createEpisode(103, "E3", BASE_TIMESTAMP + (2 * ONE_HOUR), 30)
            );

            // When
            String title = watchPartyService.generateCombinedTitle(episodes);

            // Then
            assertThat(title).isEqualTo("Triple Episode");
        }

        @Test
        void generateCombinedTitle_fourEpisodes_returnsQuadruple() {
            // Given
            List<CreateWatchPartyEpisodeRequest> episodes = List.of(
                    createEpisode(101, "E1", BASE_TIMESTAMP, 30),
                    createEpisode(102, "E2", BASE_TIMESTAMP + ONE_HOUR, 30),
                    createEpisode(103, "E3", BASE_TIMESTAMP + (2 * ONE_HOUR), 30),
                    createEpisode(104, "E4", BASE_TIMESTAMP + (3 * ONE_HOUR), 30)
            );

            // When
            String title = watchPartyService.generateCombinedTitle(episodes);

            // Then
            assertThat(title).isEqualTo("Quadruple Episode");
        }

        @Test
        void generateCombinedTitle_fiveOrMoreEpisodes_returnsMultiEpisode() {
            // Given
            List<CreateWatchPartyEpisodeRequest> episodes = List.of(
                    createEpisode(101, "E1", BASE_TIMESTAMP, 30),
                    createEpisode(102, "E2", BASE_TIMESTAMP + ONE_HOUR, 30),
                    createEpisode(103, "E3", BASE_TIMESTAMP + (2 * ONE_HOUR), 30),
                    createEpisode(104, "E4", BASE_TIMESTAMP + (3 * ONE_HOUR), 30),
                    createEpisode(105, "E5", BASE_TIMESTAMP + (4 * ONE_HOUR), 30)
            );

            // When
            String title = watchPartyService.generateCombinedTitle(episodes);

            // Then
            assertThat(title).isEqualTo("Multi-Episode (5 episodes)");
        }
    }

    // ============================================================================
    // EDGE CASE TESTS
    // ============================================================================

    @Nested
    class EdgeCaseTests {

        @Test
        void combineEpisodes_exactlyAt20HourBoundary_doesNotCombine() {
            // Given - Episodes exactly 20 hours apart (boundary case)
            // The condition is < TWENTY_HOURS_SECONDS, so exactly 20 hours should NOT combine
            long exactly20HoursLater = BASE_TIMESTAMP + TWENTY_HOURS_SECONDS;
            List<CreateWatchPartyEpisodeRequest> episodes = List.of(
                    createEpisode(101, "Episode 1", BASE_TIMESTAMP, 60),
                    createEpisode(102, "Episode 2", exactly20HoursLater, 60)
            );

            // When
            List<WatchPartyServiceImpl.CombinedEpisode> result = watchPartyService.combineEpisodes(episodes);

            // Then - Should be separate (>= 20 hours)
            assertThat(result).hasSize(2);
        }

        @Test
        void combineEpisodes_justUnder20Hours_combines() {
            // Given - Episodes just under 20 hours (should combine)
            long justUnder20Hours = BASE_TIMESTAMP + TWENTY_HOURS_SECONDS - 1;
            List<CreateWatchPartyEpisodeRequest> episodes = List.of(
                    createEpisode(101, "Episode 1", BASE_TIMESTAMP, 60),
                    createEpisode(102, "Episode 2", justUnder20Hours, 60)
            );

            // When
            List<WatchPartyServiceImpl.CombinedEpisode> result = watchPartyService.combineEpisodes(episodes);

            // Then - Should combine
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getAllEpisodeIds()).containsExactly("101", "102");
        }

        @Test
        void combineEpisodes_withNullRuntime_usesDefault60() {
            // Given
            CreateWatchPartyEpisodeRequest epWithNullRuntime = CreateWatchPartyEpisodeRequest.builder()
                    .episodeId(101)
                    .title("Episode 1")
                    .airTimestamp(BASE_TIMESTAMP)
                    .runtime(null)
                    .build();

            List<CreateWatchPartyEpisodeRequest> episodes = List.of(epWithNullRuntime);

            // When
            List<WatchPartyServiceImpl.CombinedEpisode> result = watchPartyService.combineEpisodes(episodes);

            // Then - Default runtime of 60 should be used
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTotalRuntime()).isEqualTo(60);
        }
    }

    // ============================================================================
    // SET USER INTEREST TESTS
    // ============================================================================

    @Nested
    class SetUserInterestTests {

        private User testUser;
        private SeriesPointer testSeriesPointer;

        @BeforeEach
        void setUpInterestTests() {
            testUser = new User();
            testUser.setId(UUID.fromString(USER_ID));
            testUser.setDisplayName("Test User");
            testUser.setMainImagePath("users/test-image.jpg");

            testSeriesPointer = new SeriesPointer();
            testSeriesPointer.setSeriesId(SERIES_ID);
            testSeriesPointer.setGroupId(GROUP_ID);
            testSeriesPointer.setEventSeriesType("WATCH_PARTY");
        }

        @Test
        void setUserInterest_GoingLevel_StoresInterestLevel() {
            // Given
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(testSeries));
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
            when(groupRepository.findSeriesPointer(GROUP_ID, SERIES_ID)).thenReturn(Optional.of(testSeriesPointer));

            // When
            watchPartyService.setUserInterest(SERIES_ID, "GOING", USER_ID);

            // Then
            ArgumentCaptor<SeriesPointer> pointerCaptor = ArgumentCaptor.forClass(SeriesPointer.class);
            verify(groupRepository).saveSeriesPointer(pointerCaptor.capture());

            SeriesPointer savedPointer = pointerCaptor.getValue();
            assertThat(savedPointer.getInterestLevels()).hasSize(1);
            assertThat(savedPointer.getInterestLevels().get(0).getUserId()).isEqualTo(USER_ID);
            assertThat(savedPointer.getInterestLevels().get(0).getStatus()).isEqualTo("GOING");
            assertThat(savedPointer.getInterestLevels().get(0).getUserName()).isEqualTo("Test User");

            verify(groupTimestampService).updateGroupTimestamps(List.of(GROUP_ID));
        }

        @Test
        void setUserInterest_ChangeLevel_UpdatesExistingEntry() {
            // Given - User already has an interest level
            InterestLevel existingLevel = new InterestLevel();
            existingLevel.setUserId(USER_ID);
            existingLevel.setStatus("INTERESTED");
            existingLevel.setUserName("Test User");
            testSeriesPointer.setInterestLevels(new ArrayList<>(List.of(existingLevel)));

            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(testSeries));
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
            when(groupRepository.findSeriesPointer(GROUP_ID, SERIES_ID)).thenReturn(Optional.of(testSeriesPointer));

            // When - Change from INTERESTED to NOT_GOING
            watchPartyService.setUserInterest(SERIES_ID, "NOT_GOING", USER_ID);

            // Then - Should update existing, not add new
            ArgumentCaptor<SeriesPointer> pointerCaptor = ArgumentCaptor.forClass(SeriesPointer.class);
            verify(groupRepository).saveSeriesPointer(pointerCaptor.capture());

            SeriesPointer savedPointer = pointerCaptor.getValue();
            assertThat(savedPointer.getInterestLevels()).hasSize(1); // Still only one entry
            assertThat(savedPointer.getInterestLevels().get(0).getStatus()).isEqualTo("NOT_GOING");
        }

        @Test
        void setUserInterest_NonMember_ThrowsUnauthorized() {
            // Given
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(testSeries));
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> watchPartyService.setUserInterest(SERIES_ID, "GOING", USER_ID))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("not a member");
        }

        @Test
        void setUserInterest_InvalidSeriesId_ThrowsNotFound() {
            // Given
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> watchPartyService.setUserInterest(SERIES_ID, "GOING", USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Watch party series not found");
        }

        @Test
        void setUserInterest_NotWatchParty_ThrowsNotFound() {
            // Given
            testSeries.setEventSeriesType("REGULAR"); // Not a watch party
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(testSeries));

            // When/Then
            assertThatThrownBy(() -> watchPartyService.setUserInterest(SERIES_ID, "GOING", USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("not a watch party");
        }

        @Test
        void setUserInterest_SeriesPointerNotFound_ThrowsNotFound() {
            // Given
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(testSeries));
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
            when(groupRepository.findSeriesPointer(GROUP_ID, SERIES_ID)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> watchPartyService.setUserInterest(SERIES_ID, "GOING", USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Series pointer not found");
        }

        @Test
        void setUserInterest_UserNotFound_ThrowsNotFound() {
            // Given
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(testSeries));
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> watchPartyService.setUserInterest(SERIES_ID, "GOING", USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User not found");
        }

        @Test
        void setUserInterest_UsesDisplayNameWithFallback() {
            // Given - User has no display name, should use username
            testUser.setDisplayName(null);
            testUser.setUsername("testuser");

            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(testSeries));
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(testUser));
            when(groupRepository.findSeriesPointer(GROUP_ID, SERIES_ID)).thenReturn(Optional.of(testSeriesPointer));

            // When
            watchPartyService.setUserInterest(SERIES_ID, "INTERESTED", USER_ID);

            // Then
            ArgumentCaptor<SeriesPointer> pointerCaptor = ArgumentCaptor.forClass(SeriesPointer.class);
            verify(groupRepository).saveSeriesPointer(pointerCaptor.capture());

            SeriesPointer savedPointer = pointerCaptor.getValue();
            assertThat(savedPointer.getInterestLevels().get(0).getUserName()).isEqualTo("testuser");
        }
    }

    // ============================================================================
    // REMOVE USER INTEREST TESTS
    // ============================================================================

    @Nested
    class RemoveUserInterestTests {

        private SeriesPointer testSeriesPointer;

        @BeforeEach
        void setUpRemoveInterestTests() {
            testSeriesPointer = new SeriesPointer();
            testSeriesPointer.setSeriesId(SERIES_ID);
            testSeriesPointer.setGroupId(GROUP_ID);
            testSeriesPointer.setEventSeriesType("WATCH_PARTY");
        }

        @Test
        void removeUserInterest_RemovesExistingInterest() {
            // Given - User has existing interest
            InterestLevel existingLevel = new InterestLevel();
            existingLevel.setUserId(USER_ID);
            existingLevel.setStatus("GOING");
            existingLevel.setUserName("Test User");
            testSeriesPointer.setInterestLevels(new ArrayList<>(List.of(existingLevel)));

            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(testSeries));
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(groupRepository.findSeriesPointer(GROUP_ID, SERIES_ID)).thenReturn(Optional.of(testSeriesPointer));

            // When
            watchPartyService.removeUserInterest(SERIES_ID, USER_ID);

            // Then
            ArgumentCaptor<SeriesPointer> pointerCaptor = ArgumentCaptor.forClass(SeriesPointer.class);
            verify(groupRepository).saveSeriesPointer(pointerCaptor.capture());

            SeriesPointer savedPointer = pointerCaptor.getValue();
            assertThat(savedPointer.getInterestLevels()).isEmpty();
            verify(groupTimestampService).updateGroupTimestamps(List.of(GROUP_ID));
        }

        @Test
        void removeUserInterest_NoOpWhenNoInterestExists() {
            // Given - Empty interest levels list
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(testSeries));
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(groupRepository.findSeriesPointer(GROUP_ID, SERIES_ID)).thenReturn(Optional.of(testSeriesPointer));

            // When
            watchPartyService.removeUserInterest(SERIES_ID, USER_ID);

            // Then - No save or timestamp update since nothing was removed
            verify(groupRepository, never()).saveSeriesPointer(any());
            verify(groupTimestampService, never()).updateGroupTimestamps(any());
        }

        @Test
        void removeUserInterest_OtherUsersInterestNotAffected() {
            // Given - Multiple users have interest
            String otherUserId = "other-user-1234-1234-123456789012";
            InterestLevel userLevel = new InterestLevel();
            userLevel.setUserId(USER_ID);
            userLevel.setStatus("GOING");
            userLevel.setUserName("Test User");

            InterestLevel otherLevel = new InterestLevel();
            otherLevel.setUserId(otherUserId);
            otherLevel.setStatus("INTERESTED");
            otherLevel.setUserName("Other User");

            testSeriesPointer.setInterestLevels(new ArrayList<>(List.of(userLevel, otherLevel)));

            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(testSeries));
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(groupRepository.findSeriesPointer(GROUP_ID, SERIES_ID)).thenReturn(Optional.of(testSeriesPointer));

            // When
            watchPartyService.removeUserInterest(SERIES_ID, USER_ID);

            // Then - Other user's interest is preserved
            ArgumentCaptor<SeriesPointer> pointerCaptor = ArgumentCaptor.forClass(SeriesPointer.class);
            verify(groupRepository).saveSeriesPointer(pointerCaptor.capture());

            SeriesPointer savedPointer = pointerCaptor.getValue();
            assertThat(savedPointer.getInterestLevels()).hasSize(1);
            assertThat(savedPointer.getInterestLevels().get(0).getUserId()).isEqualTo(otherUserId);
        }

        @Test
        void removeUserInterest_MissingPointer_EarlyReturn() {
            // Given - No pointer exists
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(testSeries));
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(groupRepository.findSeriesPointer(GROUP_ID, SERIES_ID)).thenReturn(Optional.empty());

            // When
            watchPartyService.removeUserInterest(SERIES_ID, USER_ID);

            // Then - No save, no timestamp update
            verify(groupRepository, never()).saveSeriesPointer(any());
            verify(groupTimestampService, never()).updateGroupTimestamps(any());
        }

        @Test
        void removeUserInterest_UpdatesGroupTimestamp() {
            // Given
            InterestLevel existingLevel = new InterestLevel();
            existingLevel.setUserId(USER_ID);
            existingLevel.setStatus("GOING");
            testSeriesPointer.setInterestLevels(new ArrayList<>(List.of(existingLevel)));

            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(testSeries));
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(groupRepository.findSeriesPointer(GROUP_ID, SERIES_ID)).thenReturn(Optional.of(testSeriesPointer));

            // When
            watchPartyService.removeUserInterest(SERIES_ID, USER_ID);

            // Then
            verify(groupTimestampService).updateGroupTimestamps(List.of(GROUP_ID));
        }

        @Test
        void removeUserInterest_SeriesNotFound_ThrowsNotFound() {
            // Given
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> watchPartyService.removeUserInterest(SERIES_ID, USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Watch party series not found");
        }

        @Test
        void removeUserInterest_NotWatchParty_ThrowsNotFound() {
            // Given
            testSeries.setEventSeriesType("REGULAR");
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(testSeries));

            // When/Then
            assertThatThrownBy(() -> watchPartyService.removeUserInterest(SERIES_ID, USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("not a watch party");
        }

        @Test
        void removeUserInterest_NonMember_ThrowsUnauthorized() {
            // Given
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(testSeries));
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(false);

            // When/Then
            assertThatThrownBy(() -> watchPartyService.removeUserInterest(SERIES_ID, USER_ID))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("not a member");
        }
    }

    // ============================================================================
    // TIME INFO ISO-8601 FORMATTING TESTS
    // ============================================================================

    @Nested
    class TimeInfoFormattingTests {

        @Test
        void createHangout_setsTimeInfoWithCorrectISO8601StartTime() {
            // Given - March 15, 2024 at 21:00:00 UTC (epoch seconds)
            long startTimestamp = 1710543600L;
            long endTimestamp = startTimestamp + 3600L; // 1 hour later

            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(seasonRepository.findByShowIdAndSeasonNumber(SHOW_ID, SEASON_NUMBER))
                    .thenReturn(Optional.empty());

            List<CreateWatchPartyEpisodeRequest> episodes = List.of(
                    createEpisode(101, "Pilot", startTimestamp, 60)
            );
            CreateWatchPartyRequest request = CreateWatchPartyRequest.builder()
                    .showId(SHOW_ID)
                    .seasonNumber(SEASON_NUMBER)
                    .showName(SHOW_NAME)
                    .defaultTime("20:00")
                    .timezone("America/Chicago")
                    .episodes(episodes)
                    .build();

            // When
            watchPartyService.createWatchParty(GROUP_ID, request, USER_ID);

            // Then - Verify hangout has TimeInfo with ISO-8601 formatted startTime
            ArgumentCaptor<Hangout> hangoutCaptor = ArgumentCaptor.forClass(Hangout.class);
            verify(hangoutRepository).save(hangoutCaptor.capture());
            Hangout savedHangout = hangoutCaptor.getValue();

            assertThat(savedHangout.getTimeInput()).isNotNull();
            assertThat(savedHangout.getTimeInput().getStartTime()).isNotNull();
            // Verify ISO-8601 format with timezone offset (e.g., "2024-03-15T20:00:00-05:00")
            assertThat(savedHangout.getTimeInput().getStartTime()).containsPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[+-]\\d{2}:\\d{2}");
        }

        @Test
        void createHangout_setsTimeInfoWithCorrectISO8601EndTime() {
            // Given - March 15, 2024 at 21:00:00 UTC (epoch seconds)
            long startTimestamp = 1710547200L;

            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(seasonRepository.findByShowIdAndSeasonNumber(SHOW_ID, SEASON_NUMBER))
                    .thenReturn(Optional.empty());

            List<CreateWatchPartyEpisodeRequest> episodes = List.of(
                    createEpisode(101, "Pilot", startTimestamp, 60)
            );
            CreateWatchPartyRequest request = CreateWatchPartyRequest.builder()
                    .showId(SHOW_ID)
                    .seasonNumber(SEASON_NUMBER)
                    .showName(SHOW_NAME)
                    .defaultTime("21:00")
                    .timezone("America/New_York")
                    .episodes(episodes)
                    .build();

            // When
            watchPartyService.createWatchParty(GROUP_ID, request, USER_ID);

            // Then - Verify hangout has TimeInfo with ISO-8601 formatted endTime
            ArgumentCaptor<Hangout> hangoutCaptor = ArgumentCaptor.forClass(Hangout.class);
            verify(hangoutRepository).save(hangoutCaptor.capture());
            Hangout savedHangout = hangoutCaptor.getValue();

            assertThat(savedHangout.getTimeInput()).isNotNull();
            assertThat(savedHangout.getTimeInput().getEndTime()).isNotNull();
            // Verify ISO-8601 format with Eastern timezone offset
            assertThat(savedHangout.getTimeInput().getEndTime()).containsPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[+-]\\d{2}:\\d{2}");
        }

        @Test
        void createHangout_handlesDifferentTimezonesCorrectly() {
            // Given - Same epoch timestamp, different timezones
            long timestamp = 1710543600L; // March 15, 2024 21:00:00 UTC

            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(seasonRepository.findByShowIdAndSeasonNumber(SHOW_ID, SEASON_NUMBER))
                    .thenReturn(Optional.empty());

            // Create with Los Angeles timezone first
            List<CreateWatchPartyEpisodeRequest> episodes = List.of(
                    createEpisode(101, "Pilot", timestamp, 60)
            );
            CreateWatchPartyRequest laRequest = CreateWatchPartyRequest.builder()
                    .showId(SHOW_ID)
                    .seasonNumber(SEASON_NUMBER)
                    .showName(SHOW_NAME)
                    .defaultTime("20:00")
                    .timezone("America/Los_Angeles")
                    .episodes(episodes)
                    .build();

            // When - Create watch party with LA timezone
            watchPartyService.createWatchParty(GROUP_ID, laRequest, USER_ID);

            // Then - Verify LA timezone shows Pacific offset (-07:00 or -08:00 depending on DST)
            ArgumentCaptor<Hangout> hangoutCaptor = ArgumentCaptor.forClass(Hangout.class);
            verify(hangoutRepository).save(hangoutCaptor.capture());
            Hangout laHangout = hangoutCaptor.getValue();

            assertThat(laHangout.getTimeInput().getStartTime()).isNotNull();
            // March 15, 2024 is during PDT (-07:00)
            assertThat(laHangout.getTimeInput().getStartTime()).contains("-07:00");
        }

        @Test
        void timeInfoStartTime_matchesStartTimestampValue() {
            // Given
            long knownStartTimestamp = 1710543600L; // March 15, 2024 21:00:00 UTC

            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(seasonRepository.findByShowIdAndSeasonNumber(SHOW_ID, SEASON_NUMBER))
                    .thenReturn(Optional.empty());

            List<CreateWatchPartyEpisodeRequest> episodes = List.of(
                    createEpisode(101, "Pilot", knownStartTimestamp, 60)
            );
            CreateWatchPartyRequest request = CreateWatchPartyRequest.builder()
                    .showId(SHOW_ID)
                    .seasonNumber(SEASON_NUMBER)
                    .showName(SHOW_NAME)
                    .defaultTime("20:00")
                    .timezone("UTC")
                    .episodes(episodes)
                    .build();

            // When
            watchPartyService.createWatchParty(GROUP_ID, request, USER_ID);

            // Then - Parse timeInfo.startTime back to epoch and verify it matches hangout.startTimestamp
            ArgumentCaptor<Hangout> hangoutCaptor = ArgumentCaptor.forClass(Hangout.class);
            verify(hangoutRepository).save(hangoutCaptor.capture());
            Hangout savedHangout = hangoutCaptor.getValue();

            String isoStartTime = savedHangout.getTimeInput().getStartTime();
            java.time.ZonedDateTime parsed = java.time.ZonedDateTime.parse(isoStartTime);
            long parsedEpoch = parsed.toEpochSecond();

            assertThat(parsedEpoch).isEqualTo(savedHangout.getStartTimestamp());
        }
    }

    // ============================================================================
    // HANGOUT POINTER FIELD COPYING TESTS
    // ============================================================================

    @Nested
    class HangoutPointerFieldCopyingTests {

        @Test
        void createHangoutPointer_setsStatusToActive() {
            // Given
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(seasonRepository.findByShowIdAndSeasonNumber(SHOW_ID, SEASON_NUMBER))
                    .thenReturn(Optional.empty());

            List<CreateWatchPartyEpisodeRequest> episodes = List.of(
                    createEpisode(101, "Pilot", BASE_TIMESTAMP, 60)
            );
            CreateWatchPartyRequest request = createValidRequest(episodes);

            // When
            watchPartyService.createWatchParty(GROUP_ID, request, USER_ID);

            // Then - Verify pointer has status="ACTIVE"
            ArgumentCaptor<HangoutPointer> pointerCaptor = ArgumentCaptor.forClass(HangoutPointer.class);
            verify(groupRepository).saveHangoutPointer(pointerCaptor.capture());
            HangoutPointer savedPointer = pointerCaptor.getValue();

            assertThat(savedPointer.getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        void createHangoutPointer_copiesTimeInputFromHangout() {
            // Given
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(seasonRepository.findByShowIdAndSeasonNumber(SHOW_ID, SEASON_NUMBER))
                    .thenReturn(Optional.empty());

            List<CreateWatchPartyEpisodeRequest> episodes = List.of(
                    createEpisode(101, "Pilot", BASE_TIMESTAMP, 60)
            );
            CreateWatchPartyRequest request = createValidRequest(episodes);

            // When
            watchPartyService.createWatchParty(GROUP_ID, request, USER_ID);

            // Then - Verify pointer has same timeInput as hangout
            ArgumentCaptor<Hangout> hangoutCaptor = ArgumentCaptor.forClass(Hangout.class);
            ArgumentCaptor<HangoutPointer> pointerCaptor = ArgumentCaptor.forClass(HangoutPointer.class);
            verify(hangoutRepository).save(hangoutCaptor.capture());
            verify(groupRepository).saveHangoutPointer(pointerCaptor.capture());

            Hangout savedHangout = hangoutCaptor.getValue();
            HangoutPointer savedPointer = pointerCaptor.getValue();

            assertThat(savedPointer.getTimeInput()).isNotNull();
            assertThat(savedPointer.getTimeInput().getStartTime())
                    .isEqualTo(savedHangout.getTimeInput().getStartTime());
            assertThat(savedPointer.getTimeInput().getEndTime())
                    .isEqualTo(savedHangout.getTimeInput().getEndTime());
        }

        @Test
        void createHangoutPointer_copiesNullLocationFromHangout() {
            // Given - Watch party hangouts have null location by default
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(seasonRepository.findByShowIdAndSeasonNumber(SHOW_ID, SEASON_NUMBER))
                    .thenReturn(Optional.empty());

            List<CreateWatchPartyEpisodeRequest> episodes = List.of(
                    createEpisode(101, "Pilot", BASE_TIMESTAMP, 60)
            );
            CreateWatchPartyRequest request = createValidRequest(episodes);

            // When
            watchPartyService.createWatchParty(GROUP_ID, request, USER_ID);

            // Then - Verify pointer has null location (same as hangout)
            ArgumentCaptor<Hangout> hangoutCaptor = ArgumentCaptor.forClass(Hangout.class);
            ArgumentCaptor<HangoutPointer> pointerCaptor = ArgumentCaptor.forClass(HangoutPointer.class);
            verify(hangoutRepository).save(hangoutCaptor.capture());
            verify(groupRepository).saveHangoutPointer(pointerCaptor.capture());

            Hangout savedHangout = hangoutCaptor.getValue();
            HangoutPointer savedPointer = pointerCaptor.getValue();

            assertThat(savedHangout.getLocation()).isNull();
            assertThat(savedPointer.getLocation()).isNull();
        }

        @Test
        void updateHangoutPointer_usesUpsertToPreserveCollections() {
            // Given
            UpdateWatchPartyRequest request = UpdateWatchPartyRequest.builder()
                    .defaultHostId(DEFAULT_HOST_ID)
                    .changeExistingUpcomingHangouts(true)
                    .build();

            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(testSeries));
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(testHangout));

            // When
            watchPartyService.updateWatchParty(GROUP_ID, SERIES_ID, request, USER_ID);

            // Then - Verify pointer update uses upsert (read-modify-write) instead of PutItem
            verify(pointerUpdateService).upsertPointerWithRetry(
                eq(GROUP_ID), eq(HANGOUT_ID), eq(testHangout), any(), eq("watch party cascade"));
        }
    }

    // ============================================================================
    // UPDATE WATCH PARTY TIME INFO CASCADE TESTS
    // ============================================================================

    @Nested
    class UpdateWatchPartyTimeInfoCascadeTests {

        @Test
        void updateWatchParty_updatesTimeInfoWhenTimestampsChange() {
            // Given - Set up existing hangout with old TimeInfo
            TimeInfo oldTimeInfo = new TimeInfo();
            oldTimeInfo.setStartTime("2024-03-15T20:00:00-05:00");
            oldTimeInfo.setEndTime("2024-03-15T21:00:00-05:00");
            testHangout.setTimeInput(oldTimeInfo);

            UpdateWatchPartyRequest request = UpdateWatchPartyRequest.builder()
                    .defaultTime("21:00") // Change time from 20:00 to 21:00
                    .changeExistingUpcomingHangouts(true)
                    .build();

            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(testSeries));
            when(seasonRepository.findByShowIdAndSeasonNumber(123, 1)).thenReturn(Optional.of(testSeason));
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(testHangout));

            // When
            watchPartyService.updateWatchParty(GROUP_ID, SERIES_ID, request, USER_ID);

            // Then - Verify hangout TimeInfo was updated
            ArgumentCaptor<Hangout> hangoutCaptor = ArgumentCaptor.forClass(Hangout.class);
            verify(hangoutRepository).save(hangoutCaptor.capture());
            Hangout savedHangout = hangoutCaptor.getValue();

            assertThat(savedHangout.getTimeInput()).isNotNull();
            // The new startTime should reflect 21:00 instead of 20:00
            assertThat(savedHangout.getTimeInput().getStartTime()).contains("21:00:00");
        }

        @Test
        void updateWatchParty_timeInfoMatchesNewStartTimestamp() {
            // Given
            testHangout.setStartTimestamp(Instant.now().getEpochSecond() + 86400); // Tomorrow
            testHangout.setEndTimestamp(Instant.now().getEpochSecond() + 86400 + 3600);

            UpdateWatchPartyRequest request = UpdateWatchPartyRequest.builder()
                    .defaultTime("21:00")
                    .changeExistingUpcomingHangouts(true)
                    .build();

            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(eventSeriesRepository.findById(SERIES_ID)).thenReturn(Optional.of(testSeries));
            when(seasonRepository.findByShowIdAndSeasonNumber(123, 1)).thenReturn(Optional.of(testSeason));
            when(hangoutRepository.findHangoutById(HANGOUT_ID)).thenReturn(Optional.of(testHangout));

            // When
            watchPartyService.updateWatchParty(GROUP_ID, SERIES_ID, request, USER_ID);

            // Then - Verify TimeInfo startTime can be parsed back to match startTimestamp
            ArgumentCaptor<Hangout> hangoutCaptor = ArgumentCaptor.forClass(Hangout.class);
            verify(hangoutRepository).save(hangoutCaptor.capture());
            Hangout savedHangout = hangoutCaptor.getValue();

            String isoStartTime = savedHangout.getTimeInput().getStartTime();
            java.time.ZonedDateTime parsed = java.time.ZonedDateTime.parse(isoStartTime);
            long parsedEpoch = parsed.toEpochSecond();

            assertThat(parsedEpoch).isEqualTo(savedHangout.getStartTimestamp());
        }
    }

    // ============================================================================
    // END-TO-END FIELD POPULATION TESTS
    // ============================================================================

    @Nested
    class EndToEndFieldPopulationTests {

        @Test
        void createWatchParty_createsEpisodesWithAllRequiredFieldsPopulated() {
            // Given
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(seasonRepository.findByShowIdAndSeasonNumber(SHOW_ID, SEASON_NUMBER))
                    .thenReturn(Optional.empty());

            List<CreateWatchPartyEpisodeRequest> episodes = List.of(
                    createEpisode(101, "Pilot", BASE_TIMESTAMP, 60),
                    createEpisode(102, "Episode 2", BASE_TIMESTAMP + (25 * ONE_HOUR), 45)
            );
            CreateWatchPartyRequest request = createValidRequest(episodes);

            // When
            WatchPartyResponse response = watchPartyService.createWatchParty(GROUP_ID, request, USER_ID);

            // Then - Verify all HangoutPointers have required fields
            ArgumentCaptor<HangoutPointer> pointerCaptor = ArgumentCaptor.forClass(HangoutPointer.class);
            verify(groupRepository, times(2)).saveHangoutPointer(pointerCaptor.capture());

            List<HangoutPointer> savedPointers = pointerCaptor.getAllValues();
            for (HangoutPointer pointer : savedPointers) {
                // CRITICAL: status must be "ACTIVE"
                assertThat(pointer.getStatus())
                        .as("Pointer for hangout %s should have status=ACTIVE", pointer.getHangoutId())
                        .isEqualTo("ACTIVE");

                // CRITICAL: timeInput must be non-null with valid ISO-8601 times
                assertThat(pointer.getTimeInput())
                        .as("Pointer for hangout %s should have non-null timeInput", pointer.getHangoutId())
                        .isNotNull();
                assertThat(pointer.getTimeInput().getStartTime())
                        .as("Pointer for hangout %s should have non-null startTime", pointer.getHangoutId())
                        .isNotNull();
                assertThat(pointer.getTimeInput().getEndTime())
                        .as("Pointer for hangout %s should have non-null endTime", pointer.getHangoutId())
                        .isNotNull();

                // Verify ISO-8601 format
                assertThat(pointer.getTimeInput().getStartTime())
                        .containsPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[+-]\\d{2}:\\d{2}");

                // Location can be null for watch parties, but should be explicitly set
                // (the field exists on the pointer even if null)
            }

            // Verify response has correct number of hangouts
            assertThat(response.getHangouts()).hasSize(2);
        }

        @Test
        void createWatchParty_hangoutAndPointerTimestampsAreConsistent() {
            // Given
            when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
            when(seasonRepository.findByShowIdAndSeasonNumber(SHOW_ID, SEASON_NUMBER))
                    .thenReturn(Optional.empty());

            List<CreateWatchPartyEpisodeRequest> episodes = List.of(
                    createEpisode(101, "Pilot", BASE_TIMESTAMP, 60)
            );
            CreateWatchPartyRequest request = createValidRequest(episodes);

            // When
            watchPartyService.createWatchParty(GROUP_ID, request, USER_ID);

            // Then - Verify hangout and pointer timestamps match
            ArgumentCaptor<Hangout> hangoutCaptor = ArgumentCaptor.forClass(Hangout.class);
            ArgumentCaptor<HangoutPointer> pointerCaptor = ArgumentCaptor.forClass(HangoutPointer.class);
            verify(hangoutRepository).save(hangoutCaptor.capture());
            verify(groupRepository).saveHangoutPointer(pointerCaptor.capture());

            Hangout savedHangout = hangoutCaptor.getValue();
            HangoutPointer savedPointer = pointerCaptor.getValue();

            // Epoch timestamps should match
            assertThat(savedPointer.getStartTimestamp()).isEqualTo(savedHangout.getStartTimestamp());
            assertThat(savedPointer.getEndTimestamp()).isEqualTo(savedHangout.getEndTimestamp());

            // TimeInfo ISO strings should also match
            assertThat(savedPointer.getTimeInput().getStartTime())
                    .isEqualTo(savedHangout.getTimeInput().getStartTime());
            assertThat(savedPointer.getTimeInput().getEndTime())
                    .isEqualTo(savedHangout.getTimeInput().getEndTime());
        }
    }
}
