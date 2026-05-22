package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.HangoutDetailData;
import com.bbthechange.inviter.model.EventSeries;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.model.SeriesPointer;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.repository.SeriesNotificationPreferenceRepository;
import com.bbthechange.inviter.util.InterestLevelQueries;
import com.bbthechange.inviter.util.NudgeTypes;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the user-id set for the watch-party host nudge per the UX rules:
 *   include: GOING/INTERESTED on hangout, GOING/INTERESTED on series,
 *            past hosters in this series, series creator
 *   exclude: NOT_GOING on hangout (overrides series-level GOING),
 *            NOT_GOING on series (unless GOING on hangout),
 *            users muted for {@link NudgeTypes#HOST_NUDGE} on this series
 *
 * Filter primitives come from {@link InterestLevelQueries} — this resolver
 * composes them, never duplicates the logic inline.
 */
@Component
public class WatchPartyHostNudgeRecipientResolver {

    private static final Logger logger = LoggerFactory.getLogger(WatchPartyHostNudgeRecipientResolver.class);

    /**
     * Safety cap on host-nudge blast radius. A series that resolves more than this many
     * candidate recipients is treated as an accidental "huge group" — recipients are
     * truncated to the cap and a warning metric is emitted so operators can investigate.
     */
    static final int MAX_RECIPIENTS = 200;

    private final HangoutRepository hangoutRepository;
    private final GroupRepository groupRepository;
    private final SeriesNotificationPreferenceRepository preferenceRepository;
    private final MeterRegistry meterRegistry;

    @Autowired
    public WatchPartyHostNudgeRecipientResolver(HangoutRepository hangoutRepository,
                                                GroupRepository groupRepository,
                                                SeriesNotificationPreferenceRepository preferenceRepository,
                                                MeterRegistry meterRegistry) {
        this.hangoutRepository = hangoutRepository;
        this.groupRepository = groupRepository;
        this.preferenceRepository = preferenceRepository;
        this.meterRegistry = meterRegistry;
    }

    public Set<String> resolve(EventSeries series, Hangout hangout) {
        HangoutDetailData detailData = hangoutRepository.getHangoutDetailData(hangout.getHangoutId());

        SeriesPointer pointer = null;
        if (series.getGroupId() != null && series.getSeriesId() != null) {
            Optional<SeriesPointer> pointerOpt = groupRepository.findSeriesPointer(
                series.getGroupId(), series.getSeriesId());
            pointer = pointerOpt.orElse(null);
        }

        Set<String> goingOrInterestedHangout = InterestLevelQueries.goingOrInterestedOnHangout(detailData);
        Set<String> goingOrInterestedSeries = InterestLevelQueries.goingOrInterestedOnSeries(pointer);
        Set<String> goingOnHangout = InterestLevelQueries.goingOnHangout(detailData);
        Set<String> notGoingOnHangout = InterestLevelQueries.notGoingOnHangout(detailData);
        Set<String> notGoingOnSeries = InterestLevelQueries.notGoingOnSeries(pointer);

        Set<String> candidates = new HashSet<>();
        candidates.addAll(goingOrInterestedHangout);
        candidates.addAll(goingOrInterestedSeries);
        candidates.addAll(collectPastHosters(series));
        if (series.getCreatedBy() != null && !series.getCreatedBy().isEmpty()) {
            candidates.add(series.getCreatedBy());
        }

        // Series-level NOT_GOING excludes UNLESS user has GOING at the episode level.
        Set<String> seriesNotGoingMinusEpisodeGoing = new HashSet<>(notGoingOnSeries);
        seriesNotGoingMinusEpisodeGoing.removeAll(goingOnHangout);
        candidates.removeAll(seriesNotGoingMinusEpisodeGoing);

        // Episode-level NOT_GOING is the strongest signal — apply LAST so it overrides everything,
        // including series-level GOING/INTERESTED.
        candidates.removeAll(notGoingOnHangout);

        if (candidates.isEmpty()) {
            return candidates;
        }

        Set<String> muted = preferenceRepository.findMutedUsersForSeries(
            series.getSeriesId(), NudgeTypes.HOST_NUDGE, candidates);
        candidates.removeAll(muted);

        if (candidates.size() > MAX_RECIPIENTS) {
            // The cap is a safety guard, not a fairness mechanism — which 200 of the N
            // candidates are kept is intentionally arbitrary (HashSet iteration order).
            // The capped-counter metric is the signal operators care about; if a real
            // series ever hits this, the right fix is to investigate the membership,
            // not to deterministically reshuffle the dropped users.
            logger.warn("Host nudge: capping recipients from {} to {} for series {} (hangout {})",
                candidates.size(), MAX_RECIPIENTS, series.getSeriesId(), hangout.getHangoutId());
            meterRegistry.counter("watchparty_host_nudge_recipients_capped").increment();
            return candidates.stream().limit(MAX_RECIPIENTS).collect(Collectors.toSet());
        }

        logger.debug("Resolved {} host-nudge recipients for hangout {} in series {}",
            candidates.size(), hangout.getHangoutId(), series.getSeriesId());

        return candidates;
    }

    private Set<String> collectPastHosters(EventSeries series) {
        // Reads the denormalized set maintained by HangoutServiceImpl.handleWatchPartyHostChange.
        // Legacy series written before this field existed may return empty until next host change.
        Set<String> stored = series.getPastHosterUserIds();
        if (stored == null || stored.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(stored);
    }
}
