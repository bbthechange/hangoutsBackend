package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.client.TvMazeClient;
import com.bbthechange.inviter.dto.watchparty.PollResult;
import com.bbthechange.inviter.dto.watchparty.sqs.ShowUpdatedMessage;
import com.bbthechange.inviter.repository.SeasonRepository;
import com.bbthechange.inviter.service.TvMazePollingService;
import com.bbthechange.inviter.service.WatchPartySqsService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementation of TvMazePollingService.
 * Polls TVMaze for show updates and emits SHOW_UPDATED messages for tracked shows.
 *
 * Only active when watchparty.polling.enabled=true.
 */
@Service
@ConditionalOnProperty(name = "watchparty.polling.enabled", havingValue = "true")
public class TvMazePollingServiceImpl implements TvMazePollingService {

    private static final Logger logger = LoggerFactory.getLogger(TvMazePollingServiceImpl.class);

    private final TvMazeClient tvMazeClient;
    private final SeasonRepository seasonRepository;
    private final WatchPartySqsService sqsService;
    private final MeterRegistry meterRegistry;
    private final String sincePeriod;
    private final long cacheTtlMs;

    // Cache for tracked show IDs to reduce DynamoDB scans
    private volatile Set<Integer> cachedTrackedShows;
    private final AtomicLong cacheTimestamp = new AtomicLong(0);
    private final Object cacheLock = new Object();

    // Gauge value for tracked shows count
    private final AtomicInteger trackedShowsGaugeValue = new AtomicInteger(0);

    @Autowired
    public TvMazePollingServiceImpl(
            TvMazeClient tvMazeClient,
            SeasonRepository seasonRepository,
            WatchPartySqsService sqsService,
            MeterRegistry meterRegistry,
            @Value("${watchparty.polling.since-period:day}") String sincePeriod,
            @Value("${watchparty.polling.cache-ttl-minutes:15}") int cacheTtlMinutes) {
        this.tvMazeClient = tvMazeClient;
        this.seasonRepository = seasonRepository;
        this.sqsService = sqsService;
        this.meterRegistry = meterRegistry;
        this.sincePeriod = sincePeriod;
        this.cacheTtlMs = cacheTtlMinutes * 60 * 1000L;

        // Register gauge once with the AtomicInteger as the value source
        meterRegistry.gauge("watchparty_poll_tracked_shows", trackedShowsGaugeValue);
    }

    /**
     * Package-private constructor for testing.
     */
    TvMazePollingServiceImpl(
            TvMazeClient tvMazeClient,
            SeasonRepository seasonRepository,
            WatchPartySqsService sqsService,
            MeterRegistry meterRegistry,
            String sincePeriod,
            long cacheTtlMs) {
        this.tvMazeClient = tvMazeClient;
        this.seasonRepository = seasonRepository;
        this.sqsService = sqsService;
        this.meterRegistry = meterRegistry;
        this.sincePeriod = sincePeriod;
        this.cacheTtlMs = cacheTtlMs;

        // Register gauge once with the AtomicInteger as the value source
        meterRegistry.gauge("watchparty_poll_tracked_shows", trackedShowsGaugeValue);
    }

    @Override
    public PollResult pollForUpdates() {
        Timer.Sample timer = Timer.start(meterRegistry);
        long startTime = System.currentTimeMillis();

        try {
            // 1. Get tracked shows (with caching)
            Set<Integer> trackedShows = getTrackedShowsWithCache();

            // Update gauge value
            trackedShowsGaugeValue.set(trackedShows.size());

            // 2. Early return if no tracked shows
            if (trackedShows.isEmpty()) {
                logger.info("No tracked shows found, skipping poll");
                long durationMs = System.currentTimeMillis() - startTime;
                recordPollMetrics(timer, "skipped", 0);
                return PollResult.noTrackedShows(durationMs);
            }

            logger.info("Polling TVMaze for updates to {} tracked shows", trackedShows.size());

            // 3. Fetch TVMaze updates
            Map<Integer, Long> tvMazeUpdates = tvMazeClient.getShowUpdates(sincePeriod);

            // 4. Find intersection (tracked shows that were updated)
            Set<Integer> updatedTrackedShows = new HashSet<>(trackedShows);
            updatedTrackedShows.retainAll(tvMazeUpdates.keySet());

            logger.info("Found {} tracked shows with updates out of {} total TVMaze updates",
                    updatedTrackedShows.size(), tvMazeUpdates.size());

            // 5. Emit SHOW_UPDATED messages
            int messagesEmitted = 0;
            for (Integer showId : updatedTrackedShows) {
                try {
                    ShowUpdatedMessage message = new ShowUpdatedMessage(showId);
                    message.setMessageId(UUID.randomUUID().toString());
                    sqsService.sendToTvMazeUpdatesQueue(message);
                    messagesEmitted++;

                    meterRegistry.counter("watchparty_poll_messages_emitted",
                            "showId", showId.toString()).increment();

                } catch (Exception e) {
                    logger.error("Failed to emit SHOW_UPDATED message for showId {}", showId, e);
                    meterRegistry.counter("watchparty_poll_message_errors",
                            "showId", showId.toString()).increment();
                }
            }

            // 6. Record metrics and return result
            long durationMs = System.currentTimeMillis() - startTime;
            recordPollMetrics(timer, "success", messagesEmitted);

            PollResult result = PollResult.success(
                    trackedShows.size(),
                    updatedTrackedShows.size(),
                    messagesEmitted,
                    durationMs
            );

            logger.info("Poll completed: {}", result);
            return result;

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            recordPollMetrics(timer, "error", 0);
            logger.error("Poll failed after {}ms", durationMs, e);
            throw e;
        }
    }

    /**
     * Get tracked show IDs with caching to reduce DynamoDB scans.
     */
    private Set<Integer> getTrackedShowsWithCache() {
        long now = System.currentTimeMillis();
        long lastUpdate = cacheTimestamp.get();

        // Check if cache is still valid
        if (cachedTrackedShows != null && (now - lastUpdate) < cacheTtlMs) {
            logger.debug("Using cached tracked shows ({} shows, cache age {}ms)",
                    cachedTrackedShows.size(), now - lastUpdate);
            return cachedTrackedShows;
        }

        // Refresh cache
        synchronized (cacheLock) {
            // Double-check in case another thread updated while we were waiting
            if (cachedTrackedShows != null && (System.currentTimeMillis() - cacheTimestamp.get()) < cacheTtlMs) {
                return cachedTrackedShows;
            }

            logger.debug("Refreshing tracked shows cache");
            Set<Integer> freshShows = seasonRepository.findAllDistinctShowIds();
            cachedTrackedShows = freshShows;
            cacheTimestamp.set(System.currentTimeMillis());
            return freshShows;
        }
    }

    /**
     * Invalidate the cached tracked shows.
     * Can be called when seasons are added/removed.
     */
    public void invalidateCache() {
        synchronized (cacheLock) {
            cachedTrackedShows = null;
            cacheTimestamp.set(0);
        }
    }

    private void recordPollMetrics(Timer.Sample timer, String status, int messagesEmitted) {
        timer.stop(meterRegistry.timer("watchparty_poll_duration", "status", status));
        meterRegistry.counter("watchparty_poll_total", "status", status).increment();
    }
}
