package com.bbthechange.inviter.client;

import com.bbthechange.inviter.dto.tvmaze.TvMazeEpisodeResponse;
import com.bbthechange.inviter.exception.TvMazeException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TvMazeClientTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private ObjectMapper objectMapper;
    private TvMazeClient tvMazeClient;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        tvMazeClient = new TvMazeClient(httpClient, objectMapper, "https://api.tvmaze.com");
    }

    @Nested
    @DisplayName("getEpisodes")
    class GetEpisodesTests {

        @Test
        @DisplayName("should return filtered episodes for valid season")
        void getEpisodes_ValidSeason_ReturnsFilteredEpisodes() throws Exception {
            // Given
            String jsonResponse = """
                [
                    {"id": 1, "name": "Episode 1", "type": "regular", "airstamp": "2020-01-01T21:00:00-05:00", "runtime": 60},
                    {"id": 2, "name": "Episode 2", "type": "regular", "airstamp": "2020-01-08T21:00:00-05:00", "runtime": 60},
                    {"id": 3, "name": "Special", "type": "insignificant_special", "airstamp": "2020-01-15T21:00:00-05:00", "runtime": 30},
                    {"id": 4, "name": "Important Special", "type": "significant_special", "airstamp": "2020-01-22T21:00:00-05:00", "runtime": 45}
                ]
                """;

            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(jsonResponse);

            // When
            List<TvMazeEpisodeResponse> episodes = tvMazeClient.getEpisodes(83);

            // Then
            assertThat(episodes).hasSize(3);
            assertThat(episodes).extracting(TvMazeEpisodeResponse::getName)
                    .containsExactly("Episode 1", "Episode 2", "Important Special");
            assertThat(episodes).extracting(TvMazeEpisodeResponse::getType)
                    .containsExactly("regular", "regular", "significant_special");
        }

        @Test
        @DisplayName("should throw TvMazeException when season not found")
        void getEpisodes_SeasonNotFound_ThrowsException() throws Exception {
            // Given
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(404);

            // When/Then
            assertThatThrownBy(() -> tvMazeClient.getEpisodes(999))
                    .isInstanceOf(TvMazeException.class)
                    .hasMessageContaining("TVMaze season not found: 999")
                    .satisfies(e -> {
                        TvMazeException ex = (TvMazeException) e;
                        assertThat(ex.getErrorType()).isEqualTo(TvMazeException.ErrorType.SEASON_NOT_FOUND);
                        assertThat(ex.getSeasonId()).isEqualTo(999);
                    });
        }

        @Test
        @DisplayName("should throw TvMazeException when no includable episodes")
        void getEpisodes_NoIncludableEpisodes_ThrowsException() throws Exception {
            // Given
            String jsonResponse = """
                [
                    {"id": 1, "name": "Behind the Scenes", "type": "behind_the_scenes", "airstamp": "2020-01-01T21:00:00-05:00", "runtime": 30}
                ]
                """;

            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(200);
            when(httpResponse.body()).thenReturn(jsonResponse);

            // When/Then
            assertThatThrownBy(() -> tvMazeClient.getEpisodes(83))
                    .isInstanceOf(TvMazeException.class)
                    .hasMessageContaining("No includable episodes found")
                    .satisfies(e -> {
                        TvMazeException ex = (TvMazeException) e;
                        assertThat(ex.getErrorType()).isEqualTo(TvMazeException.ErrorType.NO_EPISODES);
                    });
        }

        @Test
        @DisplayName("should retry on rate limit (429) and succeed")
        void getEpisodes_RateLimitThenSuccess_RetriesAndSucceeds() throws Exception {
            // Given
            String jsonResponse = """
                [
                    {"id": 1, "name": "Episode 1", "type": "regular", "airstamp": "2020-01-01T21:00:00-05:00", "runtime": 60}
                ]
                """;

            HttpResponse<String> rateLimitResponse = mock(HttpResponse.class);
            when(rateLimitResponse.statusCode()).thenReturn(429);

            HttpResponse<String> successResponse = mock(HttpResponse.class);
            when(successResponse.statusCode()).thenReturn(200);
            when(successResponse.body()).thenReturn(jsonResponse);

            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenReturn(rateLimitResponse)
                    .thenReturn(successResponse);

            // Spy to skip actual sleep
            TvMazeClient spyClient = spy(tvMazeClient);
            doNothing().when(spyClient).sleep(anyLong());

            // When
            List<TvMazeEpisodeResponse> episodes = spyClient.getEpisodes(83);

            // Then
            assertThat(episodes).hasSize(1);
            verify(httpClient, times(2)).send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString()));
            verify(spyClient).sleep(2000L);
        }

        @Test
        @DisplayName("should throw TvMazeException after max retries on rate limit")
        void getEpisodes_RateLimitExhausted_ThrowsException() throws Exception {
            // Given
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenReturn(httpResponse);
            when(httpResponse.statusCode()).thenReturn(429);

            // Spy to skip actual sleep
            TvMazeClient spyClient = spy(tvMazeClient);
            doNothing().when(spyClient).sleep(anyLong());

            // When/Then
            assertThatThrownBy(() -> spyClient.getEpisodes(83))
                    .isInstanceOf(TvMazeException.class)
                    .hasMessageContaining("unavailable")
                    .satisfies(e -> {
                        TvMazeException ex = (TvMazeException) e;
                        assertThat(ex.getErrorType()).isEqualTo(TvMazeException.ErrorType.SERVICE_UNAVAILABLE);
                    });

            // Verify 3 attempts were made
            verify(httpClient, times(3)).send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString()));
        }

        @Test
        @DisplayName("should throw TvMazeException on network error")
        void getEpisodes_NetworkError_ThrowsException() throws Exception {
            // Given
            when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                    .thenThrow(new RuntimeException("Connection refused"));

            // When/Then
            assertThatThrownBy(() -> tvMazeClient.getEpisodes(83))
                    .isInstanceOf(TvMazeException.class)
                    .hasMessageContaining("unavailable")
                    .satisfies(e -> {
                        TvMazeException ex = (TvMazeException) e;
                        assertThat(ex.getErrorType()).isEqualTo(TvMazeException.ErrorType.SERVICE_UNAVAILABLE);
                    });
        }
    }

    @Nested
    @DisplayName("parseAirstamp")
    class ParseAirstampTests {

        @Test
        @DisplayName("should parse ISO 8601 timestamp with timezone offset")
        void parseAirstamp_WithTimezoneOffset_ReturnsEpochSeconds() {
            // Given
            String airstamp = "2011-04-17T21:00:00-04:00";

            // When
            Long result = TvMazeClient.parseAirstamp(airstamp);

            // Then
            assertThat(result).isNotNull();
            // 2011-04-17T21:00:00-04:00 = 2011-04-18T01:00:00Z = 1303088400
            assertThat(result).isEqualTo(1303088400L);
        }

        @Test
        @DisplayName("should parse ISO 8601 timestamp with Z suffix")
        void parseAirstamp_WithZSuffix_ReturnsEpochSeconds() {
            // Given
            String airstamp = "2011-04-18T01:00:00Z";

            // When
            Long result = TvMazeClient.parseAirstamp(airstamp);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isEqualTo(1303088400L);
        }

        @Test
        @DisplayName("should return null for null airstamp")
        void parseAirstamp_Null_ReturnsNull() {
            // When
            Long result = TvMazeClient.parseAirstamp(null);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null for empty airstamp")
        void parseAirstamp_Empty_ReturnsNull() {
            // When
            Long result = TvMazeClient.parseAirstamp("");

            // Then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null for invalid airstamp format")
        void parseAirstamp_InvalidFormat_ReturnsNull() {
            // When
            Long result = TvMazeClient.parseAirstamp("not-a-date");

            // Then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("TvMazeEpisodeResponse.isIncludable")
    class IsIncludableTests {

        @Test
        @DisplayName("should return true for regular episodes")
        void isIncludable_Regular_ReturnsTrue() {
            TvMazeEpisodeResponse episode = TvMazeEpisodeResponse.builder()
                    .type("regular")
                    .build();

            assertThat(episode.isIncludable()).isTrue();
        }

        @Test
        @DisplayName("should return true for significant_special episodes")
        void isIncludable_SignificantSpecial_ReturnsTrue() {
            TvMazeEpisodeResponse episode = TvMazeEpisodeResponse.builder()
                    .type("significant_special")
                    .build();

            assertThat(episode.isIncludable()).isTrue();
        }

        @Test
        @DisplayName("should return false for other episode types")
        void isIncludable_OtherType_ReturnsFalse() {
            TvMazeEpisodeResponse episode = TvMazeEpisodeResponse.builder()
                    .type("behind_the_scenes")
                    .build();

            assertThat(episode.isIncludable()).isFalse();
        }

        @Test
        @DisplayName("should return false for null type")
        void isIncludable_NullType_ReturnsFalse() {
            TvMazeEpisodeResponse episode = TvMazeEpisodeResponse.builder()
                    .type(null)
                    .build();

            assertThat(episode.isIncludable()).isFalse();
        }
    }
}
