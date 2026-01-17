package com.bbthechange.inviter.listener;

import com.bbthechange.inviter.client.TvMazeClient;
import com.bbthechange.inviter.dto.tvmaze.TvMazeEpisodeResponse;
import com.bbthechange.inviter.dto.watchparty.sqs.EpisodeData;
import com.bbthechange.inviter.dto.watchparty.sqs.ShowUpdatedMessage;
import com.bbthechange.inviter.model.Episode;
import com.bbthechange.inviter.model.Season;
import com.bbthechange.inviter.repository.SeasonRepository;
import com.bbthechange.inviter.service.WatchPartySqsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SQS listener for TVMaze show update messages.
 * Processes SHOW_UPDATED messages by fetching latest episode data from TVMaze,
 * comparing with stored Season data, and emitting NEW_EPISODE, UPDATE_TITLE,
 * or REMOVE_EPISODE messages as appropriate.
 *
 * Uses maxConcurrentMessages=1 to respect TVMaze API rate limits.
 */
@Component
@ConditionalOnProperty(name = "watchparty.sqs.enabled", havingValue = "true")
public class TvMazeUpdateListener {

    private static final Logger logger = LoggerFactory.getLogger(TvMazeUpdateListener.class);

    private final SeasonRepository seasonRepository;
    private final TvMazeClient tvMazeClient;
    private final WatchPartySqsService sqsService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public TvMazeUpdateListener(
            SeasonRepository seasonRepository,
            TvMazeClient tvMazeClient,
            WatchPartySqsService sqsService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.seasonRepository = seasonRepository;
        this.tvMazeClient = tvMazeClient;
        this.sqsService = sqsService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @SqsListener(value = "${watchparty.tvmaze-updates-queue}", factory = "tvMazeUpdateListenerFactory")
    public void handleMessage(String messageBody) {
        try {
            ShowUpdatedMessage message = objectMapper.readValue(messageBody, ShowUpdatedMessage.class);
            logger.info("Processing TVMaze update for show: {}", message.getShowId());

            processShowUpdate(message);

            meterRegistry.counter("watchparty_tvmaze_update_total", "status", "success").increment();

        } catch (Exception e) {
            logger.error("Error processing TVMaze update message: {}", messageBody, e);
            meterRegistry.counter("watchparty_tvmaze_update_total", "status", "error").increment();
            // Don't rethrow - acknowledge message to prevent infinite retry
        }
    }

    private void processShowUpdate(ShowUpdatedMessage message) {
        Integer showId = message.getShowId();
        if (showId == null) {
            logger.warn("Received SHOW_UPDATED message with null showId");
            meterRegistry.counter("watchparty_tvmaze_update_total", "status", "invalid_show_id").increment();
            return;
        }

        // Find all seasons for this show
        List<Season> seasons = seasonRepository.findByShowId(showId);
        if (seasons.isEmpty()) {
            logger.debug("No seasons found for show {}", showId);
            meterRegistry.counter("watchparty_tvmaze_update_total", "status", "no_seasons").increment();
            return;
        }

        for (Season season : seasons) {
            processSeasonUpdate(season);
        }
    }

    private void processSeasonUpdate(Season season) {
        Integer tvmazeSeasonId = season.getTvmazeSeasonId();
        if (tvmazeSeasonId == null) {
            logger.debug("Season {} has no TVMaze season ID", season.getSeasonNumber());
            return;
        }

        try {
            // Fetch latest episodes from TVMaze
            List<TvMazeEpisodeResponse> tvMazeEpisodes = tvMazeClient.getEpisodes(tvmazeSeasonId);

            // Build map of current stored episodes
            Map<Integer, Episode> storedEpisodes = new HashMap<>();
            if (season.getEpisodes() != null) {
                for (Episode ep : season.getEpisodes()) {
                    if (ep.getEpisodeId() != null) {
                        storedEpisodes.put(ep.getEpisodeId(), ep);
                    }
                }
            }

            // Build set of TVMaze episode IDs
            Set<Integer> tvMazeEpisodeIds = tvMazeEpisodes.stream()
                    .map(TvMazeEpisodeResponse::getId)
                    .collect(Collectors.toSet());

            int newEpisodes = 0;
            int updatedTitles = 0;
            int removedEpisodes = 0;

            // Check for new or updated episodes
            for (TvMazeEpisodeResponse tvEpisode : tvMazeEpisodes) {
                Episode stored = storedEpisodes.get(tvEpisode.getId());

                if (stored == null) {
                    // New episode
                    emitNewEpisode(season, tvEpisode);
                    newEpisodes++;
                } else if (!Objects.equals(stored.getTitle(), tvEpisode.getName())) {
                    // Title changed
                    emitUpdateTitle(tvEpisode);
                    updatedTitles++;
                }
            }

            // Check for removed episodes
            for (Integer storedId : storedEpisodes.keySet()) {
                if (!tvMazeEpisodeIds.contains(storedId)) {
                    emitRemoveEpisode(storedId);
                    removedEpisodes++;
                }
            }

            // Update last checked timestamp
            seasonRepository.updateLastCheckedTimestamp(
                    season.getShowId(),
                    season.getSeasonNumber(),
                    System.currentTimeMillis()
            );

            logger.info("Processed season {} update: {} new, {} updated titles, {} removed",
                    season.getSeasonNumber(), newEpisodes, updatedTitles, removedEpisodes);

        } catch (Exception e) {
            logger.error("Error processing season {} for show {}: {}",
                    season.getSeasonNumber(), season.getShowId(), e.getMessage());
        }
    }

    private void emitNewEpisode(Season season, TvMazeEpisodeResponse tvEpisode) {
        EpisodeData episodeData = new EpisodeData();
        episodeData.setEpisodeId(tvEpisode.getId());
        episodeData.setEpisodeNumber(tvEpisode.getNumber());
        episodeData.setTitle(tvEpisode.getName());
        episodeData.setRuntime(tvEpisode.getRuntime());

        Long airTimestamp = TvMazeClient.parseAirstamp(tvEpisode.getAirstamp());
        episodeData.setAirTimestamp(airTimestamp);

        String seasonKey = season.getSeasonReference();
        sqsService.sendNewEpisode(seasonKey, episodeData);

        logger.debug("Emitted NEW_EPISODE for episode {} in season {}",
                tvEpisode.getId(), season.getSeasonNumber());
    }

    private void emitUpdateTitle(TvMazeEpisodeResponse tvEpisode) {
        sqsService.sendUpdateTitle(tvEpisode.getId().toString(), tvEpisode.getName());

        logger.debug("Emitted UPDATE_TITLE for episode {}: {}", tvEpisode.getId(), tvEpisode.getName());
    }

    private void emitRemoveEpisode(Integer episodeId) {
        sqsService.sendRemoveEpisode(episodeId.toString());

        logger.debug("Emitted REMOVE_EPISODE for episode {}", episodeId);
    }
}
