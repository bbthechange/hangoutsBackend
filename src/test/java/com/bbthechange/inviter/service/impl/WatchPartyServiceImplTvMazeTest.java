package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.client.TvMazeClient;
import com.bbthechange.inviter.dto.tvmaze.TvMazeEpisodeResponse;
import com.bbthechange.inviter.dto.watchparty.CreateWatchPartyEpisodeRequest;
import com.bbthechange.inviter.dto.watchparty.CreateWatchPartyRequest;
import com.bbthechange.inviter.dto.watchparty.WatchPartyResponse;
import com.bbthechange.inviter.exception.TvMazeException;
import com.bbthechange.inviter.exception.ValidationException;
import com.bbthechange.inviter.model.GroupMembership;
import com.bbthechange.inviter.model.GroupRole;
import com.bbthechange.inviter.model.Season;
import com.bbthechange.inviter.repository.EventSeriesRepository;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.repository.SeasonRepository;
import com.bbthechange.inviter.service.GroupTimestampService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Tests for TVMaze episode resolution in WatchPartyServiceImpl.
 *
 * Coverage:
 * - Fetching episodes from TVMaze when tvmazeSeasonId is provided
 * - Using provided episodes when available
 * - Handling priority when both TVMaze ID and episodes are provided
 * - Validation when neither source is provided
 * - TVMaze episode conversion and field mapping
 * - Filtering episodes with null airstamps
 * - TvMazeException propagation
 */
@ExtendWith(MockitoExtension.class)
class WatchPartyServiceImplTvMazeTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private HangoutRepository hangoutRepository;

    @Mock
    private EventSeriesRepository eventSeriesRepository;

    @Mock
    private SeasonRepository seasonRepository;

    @Mock
    private GroupTimestampService groupTimestampService;

    @Mock
    private TvMazeClient tvMazeClient;

    @InjectMocks
    private WatchPartyServiceImpl watchPartyService;

    // Test constants
    private static final String GROUP_ID = "11111111-1111-1111-1111-111111111111";
    private static final String USER_ID = "87654321-4321-4321-4321-210987654321";
    private static final Integer TVMAZE_SEASON_ID = 83;
    private static final Integer SHOW_ID = 73;
    private static final Integer SEASON_NUMBER = 1;
    private static final String SHOW_NAME = "Game of Thrones";

    // ============================================================================
    // resolveEpisodes - TVMaze Integration Tests
    // ============================================================================

    @Test
    void resolveEpisodes_WithTvMazeSeasonId_FetchesFromTvMaze() {
        // Given: Request with tvmazeSeasonId=83, no episodes provided
        CreateWatchPartyRequest request = CreateWatchPartyRequest.builder()
                .showId(SHOW_ID)
                .seasonNumber(SEASON_NUMBER)
                .showName(SHOW_NAME)
                .defaultTime("20:00")
                .timezone("America/New_York")
                .tvmazeSeasonId(TVMAZE_SEASON_ID)
                .episodes(null) // No episodes provided, should fetch from TVMaze
                .build();

        // Mock group membership validation
        mockGroupMembership();

        // Mock TVMaze client to return episodes
        List<TvMazeEpisodeResponse> tvMazeEpisodes = List.of(
                createTvMazeEpisode(123, "Winter Is Coming", 1, "2011-04-17T21:00:00-04:00", 62),
                createTvMazeEpisode(124, "The Kingsroad", 2, "2011-04-24T21:00:00-04:00", 56)
        );
        when(tvMazeClient.getEpisodes(TVMAZE_SEASON_ID)).thenReturn(tvMazeEpisodes);

        // Mock season repository (no existing season)
        when(seasonRepository.findByShowIdAndSeasonNumber(SHOW_ID, SEASON_NUMBER))
                .thenReturn(Optional.empty());

        // When: createWatchParty is called
        WatchPartyResponse response = watchPartyService.createWatchParty(GROUP_ID, request, USER_ID);

        // Then: tvMazeClient.getEpisodes(83) is invoked
        verify(tvMazeClient).getEpisodes(TVMAZE_SEASON_ID);

        // And hangouts are created with the fetched episodes
        assertThat(response).isNotNull();
        assertThat(response.getHangouts()).isNotEmpty();
    }

    @Test
    void resolveEpisodes_WithProvidedEpisodes_UsesProvidedEpisodes() {
        // Given: Request with episodes list provided, no tvmazeSeasonId
        List<CreateWatchPartyEpisodeRequest> providedEpisodes = List.of(
                CreateWatchPartyEpisodeRequest.builder()
                        .episodeId(123)
                        .episodeNumber(1)
                        .title("Winter Is Coming")
                        .airTimestamp(1303088400L)
                        .runtime(62)
                        .build()
        );

        CreateWatchPartyRequest request = CreateWatchPartyRequest.builder()
                .showId(SHOW_ID)
                .seasonNumber(SEASON_NUMBER)
                .showName(SHOW_NAME)
                .defaultTime("20:00")
                .timezone("America/New_York")
                .tvmazeSeasonId(null) // No TVMaze season ID
                .episodes(providedEpisodes)
                .build();

        // Mock group membership validation
        mockGroupMembership();

        // Mock season repository (no existing season)
        when(seasonRepository.findByShowIdAndSeasonNumber(SHOW_ID, SEASON_NUMBER))
                .thenReturn(Optional.empty());

        // When: createWatchParty is called
        WatchPartyResponse response = watchPartyService.createWatchParty(GROUP_ID, request, USER_ID);

        // Then: tvMazeClient.getEpisodes() is NOT called
        verify(tvMazeClient, never()).getEpisodes(anyInt());

        // And hangouts are created using the provided episodes
        assertThat(response).isNotNull();
        assertThat(response.getHangouts()).hasSize(1);
    }

    @Test
    void resolveEpisodes_WithBothTvMazeAndEpisodes_UsesProvidedEpisodes() {
        // Given: Request with BOTH tvmazeSeasonId AND episodes provided
        List<CreateWatchPartyEpisodeRequest> providedEpisodes = List.of(
                CreateWatchPartyEpisodeRequest.builder()
                        .episodeId(999)
                        .episodeNumber(1)
                        .title("Custom Episode")
                        .airTimestamp(1303088400L)
                        .runtime(45)
                        .build()
        );

        CreateWatchPartyRequest request = CreateWatchPartyRequest.builder()
                .showId(SHOW_ID)
                .seasonNumber(SEASON_NUMBER)
                .showName(SHOW_NAME)
                .defaultTime("20:00")
                .timezone("America/New_York")
                .tvmazeSeasonId(TVMAZE_SEASON_ID) // TVMaze ID provided
                .episodes(providedEpisodes) // But episodes also provided
                .build();

        // Mock group membership validation
        mockGroupMembership();

        // Mock season repository (no existing season)
        when(seasonRepository.findByShowIdAndSeasonNumber(SHOW_ID, SEASON_NUMBER))
                .thenReturn(Optional.empty());

        // When: createWatchParty is called
        WatchPartyResponse response = watchPartyService.createWatchParty(GROUP_ID, request, USER_ID);

        // Then: tvMazeClient is NOT called (provided episodes take precedence)
        verify(tvMazeClient, never()).getEpisodes(anyInt());

        // And hangouts are created using the provided episodes
        assertThat(response).isNotNull();
        assertThat(response.getHangouts()).hasSize(1);
        assertThat(response.getHangouts().get(0).getTitle()).isEqualTo("Custom Episode");
    }

    @Test
    void resolveEpisodes_WithNeitherSource_ThrowsValidationException() {
        // Given: Request with no tvmazeSeasonId and no episodes
        CreateWatchPartyRequest request = CreateWatchPartyRequest.builder()
                .showId(SHOW_ID)
                .seasonNumber(SEASON_NUMBER)
                .showName(SHOW_NAME)
                .defaultTime("20:00")
                .timezone("America/New_York")
                .tvmazeSeasonId(null)
                .episodes(null)
                .build();

        // Mock group membership validation
        mockGroupMembership();

        // When/Then: ValidationException is thrown
        assertThatThrownBy(() -> watchPartyService.createWatchParty(GROUP_ID, request, USER_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Either tvmazeSeasonId or episodes must be provided");

        // Verify no repository operations were attempted
        verify(seasonRepository, never()).save(any());
        verify(eventSeriesRepository, never()).save(any());
    }

    @Test
    void resolveEpisodes_TvMazeReturnsNoValidAirDates_ThrowsValidationException() {
        // Given: Request with tvmazeSeasonId, TVMaze returns episodes with null airstamps
        CreateWatchPartyRequest request = CreateWatchPartyRequest.builder()
                .showId(SHOW_ID)
                .seasonNumber(SEASON_NUMBER)
                .showName(SHOW_NAME)
                .defaultTime("20:00")
                .timezone("America/New_York")
                .tvmazeSeasonId(TVMAZE_SEASON_ID)
                .episodes(null)
                .build();

        // Mock group membership validation
        mockGroupMembership();

        // Mock TVMaze client to return episodes with null airstamps
        List<TvMazeEpisodeResponse> episodesWithNullAirstamps = List.of(
                createTvMazeEpisode(123, "TBA Episode 1", 1, null, 60),
                createTvMazeEpisode(124, "TBA Episode 2", 2, null, 60)
        );
        when(tvMazeClient.getEpisodes(TVMAZE_SEASON_ID)).thenReturn(episodesWithNullAirstamps);

        // When/Then: ValidationException is thrown
        assertThatThrownBy(() -> watchPartyService.createWatchParty(GROUP_ID, request, USER_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("No episodes with valid air dates");

        // Verify TVMaze was called
        verify(tvMazeClient).getEpisodes(TVMAZE_SEASON_ID);

        // But no save operations occurred
        verify(seasonRepository, never()).save(any());
    }

    // ============================================================================
    // convertTvMazeEpisodes Tests
    // ============================================================================

    @Test
    void convertTvMazeEpisodes_MapsFieldsCorrectly() {
        // Given: Request with tvmazeSeasonId
        CreateWatchPartyRequest request = CreateWatchPartyRequest.builder()
                .showId(SHOW_ID)
                .seasonNumber(SEASON_NUMBER)
                .showName(SHOW_NAME)
                .defaultTime("20:00")
                .timezone("America/New_York")
                .tvmazeSeasonId(TVMAZE_SEASON_ID)
                .build();

        // Mock group membership validation
        mockGroupMembership();

        // Mock TVMaze client with a specific episode
        // Note: "2011-04-17T21:00:00-04:00" = 1303088400 epoch seconds
        TvMazeEpisodeResponse episode = createTvMazeEpisode(
                123,
                "Winter Is Coming",
                1,
                "2011-04-17T21:00:00-04:00",
                62
        );
        when(tvMazeClient.getEpisodes(TVMAZE_SEASON_ID)).thenReturn(List.of(episode));

        // Mock season repository (no existing season)
        when(seasonRepository.findByShowIdAndSeasonNumber(SHOW_ID, SEASON_NUMBER))
                .thenReturn(Optional.empty());

        // When: createWatchParty is called
        WatchPartyResponse response = watchPartyService.createWatchParty(GROUP_ID, request, USER_ID);

        // Then: Episode fields are mapped correctly
        assertThat(response.getHangouts()).hasSize(1);

        // Capture the saved Season to verify episode conversion
        ArgumentCaptor<Season> seasonCaptor = ArgumentCaptor.forClass(Season.class);
        verify(seasonRepository).save(seasonCaptor.capture());
        Season savedSeason = seasonCaptor.getValue();

        assertThat(savedSeason.getEpisodes()).hasSize(1);
        var savedEpisode = savedSeason.getEpisodes().get(0);
        assertThat(savedEpisode.getEpisodeId()).isEqualTo(123);
        assertThat(savedEpisode.getEpisodeNumber()).isEqualTo(1);
        assertThat(savedEpisode.getTitle()).isEqualTo("Winter Is Coming");
        assertThat(savedEpisode.getRuntime()).isEqualTo(62);
        // airTimestamp is parsed from the ISO 8601 airstamp
        assertThat(savedEpisode.getAirTimestamp()).isEqualTo(1303088400L);
    }

    @Test
    void convertTvMazeEpisodes_FiltersOutNullAirstamps() {
        // Given: Request with tvmazeSeasonId
        CreateWatchPartyRequest request = CreateWatchPartyRequest.builder()
                .showId(SHOW_ID)
                .seasonNumber(SEASON_NUMBER)
                .showName(SHOW_NAME)
                .defaultTime("20:00")
                .timezone("America/New_York")
                .tvmazeSeasonId(TVMAZE_SEASON_ID)
                .build();

        // Mock group membership validation
        mockGroupMembership();

        // Mock TVMaze client with 3 episodes, one with null airstamp
        List<TvMazeEpisodeResponse> episodes = List.of(
                createTvMazeEpisode(123, "Episode 1", 1, "2011-04-17T21:00:00-04:00", 60),
                createTvMazeEpisode(124, "Episode 2 TBA", 2, null, 60), // null airstamp
                createTvMazeEpisode(125, "Episode 3", 3, "2011-05-01T21:00:00-04:00", 60)
        );
        when(tvMazeClient.getEpisodes(TVMAZE_SEASON_ID)).thenReturn(episodes);

        // Mock season repository (no existing season)
        when(seasonRepository.findByShowIdAndSeasonNumber(SHOW_ID, SEASON_NUMBER))
                .thenReturn(Optional.empty());

        // When: createWatchParty is called
        WatchPartyResponse response = watchPartyService.createWatchParty(GROUP_ID, request, USER_ID);

        // Then: Only 2 episodes are used (the one with null airstamp is filtered out)
        // Note: Episodes within 20 hours are combined, but these are 14 days apart
        assertThat(response.getHangouts()).hasSize(2);

        // Verify the Season has only 2 episodes
        ArgumentCaptor<Season> seasonCaptor = ArgumentCaptor.forClass(Season.class);
        verify(seasonRepository).save(seasonCaptor.capture());
        Season savedSeason = seasonCaptor.getValue();

        assertThat(savedSeason.getEpisodes()).hasSize(2);
        assertThat(savedSeason.getEpisodes().stream().map(e -> e.getTitle()))
                .containsExactlyInAnyOrder("Episode 1", "Episode 3");
    }

    @Test
    void createWatchParty_WithTvMaze_PropagatesEpisodeNumber() {
        // Given: TVMaze episode with number=5
        CreateWatchPartyRequest request = CreateWatchPartyRequest.builder()
                .showId(SHOW_ID)
                .seasonNumber(SEASON_NUMBER)
                .showName(SHOW_NAME)
                .defaultTime("20:00")
                .timezone("America/New_York")
                .tvmazeSeasonId(TVMAZE_SEASON_ID)
                .build();

        // Mock group membership validation
        mockGroupMembership();

        // Mock TVMaze client with episode number = 5
        TvMazeEpisodeResponse episode = createTvMazeEpisode(
                500,
                "Episode Five",
                5, // Episode number is 5
                "2011-04-17T21:00:00-04:00",
                60
        );
        when(tvMazeClient.getEpisodes(TVMAZE_SEASON_ID)).thenReturn(List.of(episode));

        // Mock season repository (no existing season)
        when(seasonRepository.findByShowIdAndSeasonNumber(SHOW_ID, SEASON_NUMBER))
                .thenReturn(Optional.empty());

        // When: createWatchParty is called
        watchPartyService.createWatchParty(GROUP_ID, request, USER_ID);

        // Then: Season episode has episodeNumber=5 (not 0)
        ArgumentCaptor<Season> seasonCaptor = ArgumentCaptor.forClass(Season.class);
        verify(seasonRepository).save(seasonCaptor.capture());
        Season savedSeason = seasonCaptor.getValue();

        assertThat(savedSeason.getEpisodes()).hasSize(1);
        assertThat(savedSeason.getEpisodes().get(0).getEpisodeNumber()).isEqualTo(5);
    }

    // ============================================================================
    // TvMazeException Propagation Tests
    // ============================================================================

    @Test
    void createWatchParty_TvMazeThrowsSeasonNotFound_PropagatesException() {
        // Given: Request with tvmazeSeasonId
        CreateWatchPartyRequest request = CreateWatchPartyRequest.builder()
                .showId(SHOW_ID)
                .seasonNumber(SEASON_NUMBER)
                .showName(SHOW_NAME)
                .defaultTime("20:00")
                .timezone("America/New_York")
                .tvmazeSeasonId(999) // Non-existent season
                .build();

        // Mock group membership validation
        mockGroupMembership();

        // Mock TVMaze client to throw seasonNotFound exception
        when(tvMazeClient.getEpisodes(999)).thenThrow(TvMazeException.seasonNotFound(999));

        // When/Then: TvMazeException propagates up (not wrapped)
        assertThatThrownBy(() -> watchPartyService.createWatchParty(GROUP_ID, request, USER_ID))
                .isInstanceOf(TvMazeException.class)
                .hasMessageContaining("TVMaze season not found: 999");

        // Verify no save operations occurred
        verify(seasonRepository, never()).save(any());
        verify(eventSeriesRepository, never()).save(any());
    }

    @Test
    void createWatchParty_TvMazeThrowsServiceUnavailable_PropagatesException() {
        // Given: Request with tvmazeSeasonId
        CreateWatchPartyRequest request = CreateWatchPartyRequest.builder()
                .showId(SHOW_ID)
                .seasonNumber(SEASON_NUMBER)
                .showName(SHOW_NAME)
                .defaultTime("20:00")
                .timezone("America/New_York")
                .tvmazeSeasonId(TVMAZE_SEASON_ID)
                .build();

        // Mock group membership validation
        mockGroupMembership();

        // Mock TVMaze client to throw serviceUnavailable exception
        when(tvMazeClient.getEpisodes(TVMAZE_SEASON_ID))
                .thenThrow(TvMazeException.serviceUnavailable(TVMAZE_SEASON_ID, new RuntimeException("Connection timeout")));

        // When/Then: TvMazeException propagates up (not wrapped)
        assertThatThrownBy(() -> watchPartyService.createWatchParty(GROUP_ID, request, USER_ID))
                .isInstanceOf(TvMazeException.class)
                .hasMessageContaining("TVMaze API is unavailable");

        // Verify no save operations occurred
        verify(seasonRepository, never()).save(any());
        verify(eventSeriesRepository, never()).save(any());
    }

    // ============================================================================
    // Helper Methods
    // ============================================================================

    private void mockGroupMembership() {
        GroupMembership membership = new GroupMembership();
        membership.setGroupId(GROUP_ID);
        membership.setUserId(USER_ID);
        membership.setGroupName("Test Watch Party Group");
        membership.setRole(GroupRole.MEMBER);
        membership.setPk("GROUP#" + GROUP_ID);
        membership.setSk("USER#" + USER_ID);

        when(groupRepository.isUserMemberOfGroup(GROUP_ID, USER_ID)).thenReturn(true);
    }

    private TvMazeEpisodeResponse createTvMazeEpisode(
            Integer id,
            String name,
            Integer number,
            String airstamp,
            Integer runtime) {
        return TvMazeEpisodeResponse.builder()
                .id(id)
                .name(name)
                .number(number)
                .season(SEASON_NUMBER)
                .type("regular")
                .airstamp(airstamp)
                .runtime(runtime)
                .build();
    }
}
