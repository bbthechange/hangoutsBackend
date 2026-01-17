package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.model.EventSeries;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.model.InterestLevel;
import com.bbthechange.inviter.model.SeriesPointer;
import com.bbthechange.inviter.repository.EventSeriesRepository;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.NotificationService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Scheduled service that checks for hostless watch party hangouts and notifies interested users.
 * Runs daily at 10 AM UTC when enabled via watchparty.host-check.enabled=true.
 */
@Service
@ConditionalOnProperty(name = "watchparty.host-check.enabled", havingValue = "true")
public class WatchPartyHostCheckService {

    private static final Logger logger = LoggerFactory.getLogger(WatchPartyHostCheckService.class);
    private static final long SECONDS_IN_24_HOURS = 24 * 60 * 60;
    private static final long SECONDS_IN_48_HOURS = 48 * 60 * 60;

    private final EventSeriesRepository eventSeriesRepository;
    private final HangoutRepository hangoutRepository;
    private final GroupRepository groupRepository;
    private final NotificationService notificationService;
    private final MeterRegistry meterRegistry;

    public WatchPartyHostCheckService(
            EventSeriesRepository eventSeriesRepository,
            HangoutRepository hangoutRepository,
            GroupRepository groupRepository,
            NotificationService notificationService,
            MeterRegistry meterRegistry) {
        this.eventSeriesRepository = eventSeriesRepository;
        this.hangoutRepository = hangoutRepository;
        this.groupRepository = groupRepository;
        this.notificationService = notificationService;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Check for hostless hangouts in all watch party series.
     * Runs daily at 10 AM UTC by default (configurable via watchparty.host-check.cron).
     */
    @Scheduled(cron = "${watchparty.host-check.cron:0 0 10 * * ?}")
    public void checkHostlessHangouts() {
        logger.info("Starting daily watch party host check");

        try {
            List<EventSeries> watchPartySeries = eventSeriesRepository.findAllWatchPartySeries();

            if (watchPartySeries.isEmpty()) {
                logger.debug("No watch party series found");
                return;
            }

            long now = Instant.now().getEpochSecond();
            long windowStart = now + SECONDS_IN_24_HOURS;
            long windowEnd = now + SECONDS_IN_48_HOURS;

            int seriesChecked = 0;
            int hostlessFound = 0;
            int notificationsSent = 0;

            for (EventSeries series : watchPartySeries) {
                seriesChecked++;
                List<Hangout> hostlessHangouts = findHostlessHangoutsInWindow(series, windowStart, windowEnd);

                for (Hangout hangout : hostlessHangouts) {
                    hostlessFound++;
                    boolean sent = notifyHostNeeded(series, hangout);
                    if (sent) {
                        notificationsSent++;
                    }
                }
            }

            logger.info("Watch party host check complete: {} series checked, {} hostless hangouts found, {} notifications sent",
                    seriesChecked, hostlessFound, notificationsSent);

            meterRegistry.counter("watchparty_host_check_total", "status", "success").increment();
            if (hostlessFound > 0) {
                meterRegistry.counter("watchparty_hostless_hangouts_found").increment(hostlessFound);
            }

        } catch (Exception e) {
            logger.error("Error during watch party host check", e);
            meterRegistry.counter("watchparty_host_check_total", "status", "error").increment();
        }
    }

    /**
     * Find hangouts in the series that have no host and start within the given time window.
     */
    private List<Hangout> findHostlessHangoutsInWindow(EventSeries series, long windowStart, long windowEnd) {
        List<String> hangoutIds = series.getHangoutIds();
        if (hangoutIds == null || hangoutIds.isEmpty()) {
            return List.of();
        }

        return hangoutIds.stream()
                .map(hangoutRepository::findHangoutById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(hangout -> isHostless(hangout))
                .filter(hangout -> isInTimeWindow(hangout, windowStart, windowEnd))
                .collect(Collectors.toList());
    }

    /**
     * Check if a hangout has no assigned host.
     */
    private boolean isHostless(Hangout hangout) {
        return hangout.getHostAtPlaceUserId() == null || hangout.getHostAtPlaceUserId().isEmpty();
    }

    /**
     * Check if hangout start time falls within the specified window.
     */
    private boolean isInTimeWindow(Hangout hangout, long windowStart, long windowEnd) {
        Long startTimestamp = hangout.getStartTimestamp();
        if (startTimestamp == null) {
            return false;
        }
        return startTimestamp >= windowStart && startTimestamp <= windowEnd;
    }

    /**
     * Notify interested users that a hangout needs a host.
     * Returns true if at least one notification was sent.
     */
    private boolean notifyHostNeeded(EventSeries series, Hangout hangout) {
        try {
            String groupId = series.getGroupId();
            String seriesId = series.getSeriesId();

            Optional<SeriesPointer> pointerOpt = groupRepository.findSeriesPointer(groupId, seriesId);
            if (pointerOpt.isEmpty() || pointerOpt.get().getInterestLevels() == null) {
                logger.debug("No series pointer or interest levels for series {}", seriesId);
                return false;
            }

            Set<String> userIds = pointerOpt.get().getInterestLevels().stream()
                    .filter(il -> "GOING".equals(il.getStatus()) || "INTERESTED".equals(il.getStatus()))
                    .map(InterestLevel::getUserId)
                    .collect(Collectors.toSet());

            if (userIds.isEmpty()) {
                logger.debug("No interested users for series {}", seriesId);
                return false;
            }

            String message = String.format("'%s' needs a host - volunteer?", hangout.getTitle());
            notificationService.notifyWatchPartyUpdate(userIds, seriesId, message);

            logger.debug("Sent host needed notification for hangout {} to {} users",
                    hangout.getHangoutId(), userIds.size());
            return true;

        } catch (Exception e) {
            logger.warn("Failed to notify about hostless hangout {}: {}", hangout.getHangoutId(), e.getMessage());
            return false;
        }
    }
}
