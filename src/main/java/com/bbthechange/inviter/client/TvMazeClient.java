package com.bbthechange.inviter.client;

import com.bbthechange.inviter.dto.tvmaze.TvMazeEpisodeResponse;
import com.bbthechange.inviter.exception.TvMazeException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Client for interacting with TVMaze API.
 * Fetches episode data for TV seasons with retry logic for rate limiting.
 */
@Component
public class TvMazeClient {

    private static final Logger logger = LoggerFactory.getLogger(TvMazeClient.class);

    private static final String TVMAZE_BASE_URL = "https://api.tvmaze.com";
    private static final String USER_AGENT = "HangoutsApp/1.0";
    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_DELAYS_MS = {2000, 3000, 4500}; // Exponential backoff
    private static final Set<String> VALID_SINCE_PERIODS = Set.of("day", "week", "month");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    @Autowired
    public TvMazeClient(
            ObjectMapper objectMapper,
            @Value("${tvmaze.base-url:" + TVMAZE_BASE_URL + "}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Constructor for testing with custom HttpClient.
     */
    TvMazeClient(HttpClient httpClient, ObjectMapper objectMapper, String baseUrl) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
    }

    /**
     * Fetch episodes for a TVMaze season.
     * Filters to only include "regular" and "significant_special" types.
     *
     * @param seasonId TVMaze season ID
     * @return List of includable episodes
     * @throws TvMazeException if the season is not found, API is unavailable, or no episodes found
     */
    public List<TvMazeEpisodeResponse> getEpisodes(Integer seasonId) {
        logger.info("Fetching episodes for TVMaze season {}", seasonId);

        String url = baseUrl + "/seasons/" + seasonId + "/episodes";
        List<TvMazeEpisodeResponse> allEpisodes = fetchWithRetry(url, seasonId);

        // Filter to includable types
        List<TvMazeEpisodeResponse> includableEpisodes = allEpisodes.stream()
                .filter(TvMazeEpisodeResponse::isIncludable)
                .collect(Collectors.toList());

        logger.info("Found {} total episodes, {} includable for season {}",
                allEpisodes.size(), includableEpisodes.size(), seasonId);

        if (includableEpisodes.isEmpty()) {
            throw TvMazeException.noEpisodes(seasonId);
        }

        return includableEpisodes;
    }

    /**
     * Fetch show updates from TVMaze.
     * Returns a map of showId -> lastUpdatedTimestamp for shows that have been updated.
     *
     * @param since Period to fetch updates for: "day", "week", or "month"
     * @return Map of showId to lastUpdatedTimestamp (Unix epoch seconds)
     * @throws IllegalArgumentException if since is not a valid period
     * @throws TvMazeException if the API is unavailable
     */
    public Map<Integer, Long> getShowUpdates(String since) {
        if (!VALID_SINCE_PERIODS.contains(since)) {
            throw new IllegalArgumentException(
                    "Invalid since period: " + since + ". Must be one of: " + VALID_SINCE_PERIODS);
        }

        logger.info("Fetching show updates from TVMaze for period: {}", since);

        String url = baseUrl + "/updates/shows?since=" + since;
        return fetchShowUpdatesWithRetry(url);
    }

    /**
     * Fetch show updates with retry logic for rate limiting (HTTP 429).
     */
    private Map<Integer, Long> fetchShowUpdatesWithRetry(String url) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < MAX_RETRIES) {
            try {
                return fetchShowUpdates(url);
            } catch (RateLimitException e) {
                attempt++;
                lastException = e;

                if (attempt < MAX_RETRIES) {
                    long delayMs = RETRY_DELAYS_MS[attempt - 1];
                    logger.warn("Rate limited by TVMaze (attempt {}/{}). Retrying in {}ms",
                            attempt, MAX_RETRIES, delayMs);
                    sleep(delayMs);
                }
            } catch (Exception e) {
                throw TvMazeException.serviceUnavailable("TVMaze API unavailable for show updates", e);
            }
        }

        throw TvMazeException.serviceUnavailable("TVMaze API unavailable after " + MAX_RETRIES + " retries", lastException);
    }

    /**
     * Make HTTP request to TVMaze show updates API.
     */
    private Map<Integer, Long> fetchShowUpdates(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(60)) // Longer timeout for large response
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        int statusCode = response.statusCode();
        logger.debug("TVMaze show updates API response status: {}", statusCode);

        if (statusCode == 429) {
            throw new RateLimitException("Rate limited by TVMaze");
        }

        if (statusCode < 200 || statusCode >= 300) {
            throw new RuntimeException("TVMaze API returned status " + statusCode);
        }

        // Parse response: {"1526": 1766280973, "2345": 1766198432, ...}
        Map<String, Long> rawUpdates = objectMapper.readValue(
                response.body(),
                new TypeReference<Map<String, Long>>() {}
        );

        // Convert String keys to Integer, skipping any invalid entries
        Map<Integer, Long> updates = new HashMap<>();
        for (Map.Entry<String, Long> entry : rawUpdates.entrySet()) {
            try {
                Integer showId = Integer.parseInt(entry.getKey());
                updates.put(showId, entry.getValue());
            } catch (NumberFormatException e) {
                logger.warn("Skipping invalid show ID in updates: {}", entry.getKey());
            }
        }

        logger.info("Fetched {} show updates from TVMaze", updates.size());
        return updates;
    }

    /**
     * Fetch episodes with retry logic for rate limiting (HTTP 429).
     */
    private List<TvMazeEpisodeResponse> fetchWithRetry(String url, Integer seasonId) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < MAX_RETRIES) {
            try {
                return fetchEpisodes(url, seasonId);
            } catch (TvMazeException e) {
                // Re-throw TvMazeExceptions (like SEASON_NOT_FOUND) without retry
                throw e;
            } catch (RateLimitException e) {
                attempt++;
                lastException = e;

                if (attempt < MAX_RETRIES) {
                    long delayMs = RETRY_DELAYS_MS[attempt - 1];
                    logger.warn("Rate limited by TVMaze (attempt {}/{}). Retrying in {}ms",
                            attempt, MAX_RETRIES, delayMs);
                    sleep(delayMs);
                }
            } catch (Exception e) {
                throw TvMazeException.serviceUnavailable(seasonId, e);
            }
        }

        throw TvMazeException.serviceUnavailable(seasonId, lastException);
    }

    /**
     * Make HTTP request to TVMaze API.
     */
    private List<TvMazeEpisodeResponse> fetchEpisodes(String url, Integer seasonId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        int statusCode = response.statusCode();
        logger.debug("TVMaze API response status: {} for URL: {}", statusCode, url);

        if (statusCode == 404) {
            throw TvMazeException.seasonNotFound(seasonId);
        }

        if (statusCode == 429) {
            throw new RateLimitException("Rate limited by TVMaze");
        }

        if (statusCode < 200 || statusCode >= 300) {
            throw new RuntimeException("TVMaze API returned status " + statusCode);
        }

        return objectMapper.readValue(response.body(), new TypeReference<List<TvMazeEpisodeResponse>>() {});
    }

    /**
     * Sleep for the specified duration.
     * Package-private for testing.
     */
    void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for retry", e);
        }
    }

    /**
     * Convert TVMaze airstamp (ISO 8601) to Unix epoch timestamp in seconds.
     *
     * @param airstamp ISO 8601 timestamp (e.g., "2011-04-17T21:00:00-04:00")
     * @return Unix epoch timestamp in seconds, or null if airstamp is null/empty
     */
    public static Long parseAirstamp(String airstamp) {
        if (airstamp == null || airstamp.isEmpty()) {
            return null;
        }

        try {
            Instant instant = Instant.parse(airstamp);
            return instant.getEpochSecond();
        } catch (Exception e) {
            // Try parsing with timezone offset format
            try {
                java.time.OffsetDateTime odt = java.time.OffsetDateTime.parse(airstamp);
                return odt.toInstant().getEpochSecond();
            } catch (Exception e2) {
                logger.warn("Failed to parse airstamp: {}", airstamp);
                return null;
            }
        }
    }

    /**
     * Internal exception for rate limiting that triggers retry.
     */
    private static class RateLimitException extends RuntimeException {
        RateLimitException(String message) {
            super(message);
        }
    }
}
