package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.IdeaDTO;
import com.bbthechange.inviter.dto.IdeaFeedItemDTO;
import com.bbthechange.inviter.dto.IdeaListDTO;
import com.bbthechange.inviter.model.BaseItem;
import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.model.MomentumCategory;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.IdeaFeedSurfacingService;
import com.bbthechange.inviter.service.IdeaListService;
import com.bbthechange.inviter.util.PaginatedResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implementation of IdeaFeedSurfacingService.
 *
 * <p>Scans the group's idea lists for ideas with interestCount >= 3 and
 * injects them into the feed unless suppression applies.
 *
 * <p>Suppression rule: if every one of the next 3 calendar weeks (starting from
 * the current week) already has at least one CONFIRMED hangout, no ideas are
 * surfaced. A "week" is defined as Mon–Sun (ISO week).
 */
@Service
public class IdeaFeedSurfacingServiceImpl implements IdeaFeedSurfacingService {

    private static final Logger logger = LoggerFactory.getLogger(IdeaFeedSurfacingServiceImpl.class);

    /** Minimum interest count for an idea to be surfaced in the feed. */
    static final int MIN_INTEREST_COUNT = 3;

    /**
     * Number of upcoming weeks to check. If all weeks have a confirmed hangout,
     * idea surfacing is suppressed.
     */
    static final int WEEKS_TO_CHECK = 3;

    /** DynamoDB query page size for fetching confirmed hangouts during suppression check. */
    private static final int HANGOUT_PAGE_SIZE = 50;

    private final IdeaListService ideaListService;
    private final HangoutRepository hangoutRepository;

    public IdeaFeedSurfacingServiceImpl(IdeaListService ideaListService,
                                        HangoutRepository hangoutRepository) {
        this.ideaListService = ideaListService;
        this.hangoutRepository = hangoutRepository;
    }

    @Override
    public List<IdeaFeedItemDTO> getSurfacedIdeas(String groupId, long nowTimestamp, String requestingUserId) {
        // Suppression check: if every upcoming week is covered, return nothing
        if (allUpcomingWeeksCovered(groupId, nowTimestamp)) {
            logger.debug("Idea surfacing suppressed for group {} — all {} upcoming weeks have confirmed hangouts",
                    groupId, WEEKS_TO_CHECK);
            return List.of();
        }

        // Fetch all idea lists. The requestingUserId has already been verified as a group member
        // by GroupServiceImpl before this method is called.
        List<IdeaListDTO> lists;
        try {
            lists = ideaListService.getIdeaListsForGroup(groupId, requestingUserId);
        } catch (Exception e) {
            // Non-fatal: degraded gracefully — feed works without idea surfacing
            logger.warn("Failed to fetch idea lists for group {} during feed surfacing", groupId, e);
            return List.of();
        }

        List<IdeaFeedItemDTO> candidates = new ArrayList<>();

        for (IdeaListDTO list : lists) {
            if (list.getIdeas() == null) {
                continue;
            }
            for (IdeaDTO idea : list.getIdeas()) {
                if (idea.getInterestCount() >= MIN_INTEREST_COUNT) {
                    candidates.add(toFeedItem(idea, list, groupId));
                }
            }
        }

        // Sort by interestCount descending so most-wanted ideas appear first
        candidates.sort((a, b) -> Integer.compare(b.getInterestCount(), a.getInterestCount()));

        return candidates;
    }

    // -------------------------------------------------------------------------
    // Suppression logic
    // -------------------------------------------------------------------------

    /**
     * Returns true if every one of the next {@value #WEEKS_TO_CHECK} calendar weeks
     * (Mon–Sun, ISO) already contains at least one CONFIRMED hangout for this group.
     */
    boolean allUpcomingWeeksCovered(String groupId, long nowTimestamp) {
        Set<Integer> weeksWithConfirmed = findWeeksWithConfirmedHangouts(groupId, nowTimestamp);

        ZonedDateTime now = Instant.ofEpochSecond(nowTimestamp).atZone(ZoneOffset.UTC);

        for (int offset = 0; offset < WEEKS_TO_CHECK; offset++) {
            ZonedDateTime weekStart = now.plusWeeks(offset);
            int weekKey = weekKey(weekStart.get(IsoFields.WEEK_BASED_YEAR), weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            if (!weeksWithConfirmed.contains(weekKey)) {
                return false; // Found a week without coverage
            }
        }
        return true;
    }

    /**
     * Returns a set of week keys ({@code weekBasedYear * 100 + isoWeek}) for weeks that
     * have at least one CONFIRMED hangout within the look-ahead window.
     */
    private Set<Integer> findWeeksWithConfirmedHangouts(String groupId, long nowTimestamp) {
        Set<Integer> coveredWeeks = new HashSet<>();

        long lookAheadEnd = nowTimestamp + (WEEKS_TO_CHECK * 7L * 24 * 3600);

        // Use getFutureEventsPage to scan upcoming hangouts without loading the full feed
        PaginatedResult<BaseItem> page;
        String token = null;

        do {
            try {
                page = hangoutRepository.getFutureEventsPage(groupId, nowTimestamp, HANGOUT_PAGE_SIZE, token);
            } catch (Exception e) {
                logger.warn("Error querying hangouts for suppression check on group {}", groupId, e);
                break;
            }

            for (BaseItem item : page.getResults()) {
                if (!(item instanceof HangoutPointer hp)) {
                    continue;
                }
                // Only care about CONFIRMED hangouts within the look-ahead window
                if (hp.getStartTimestamp() == null) {
                    continue;
                }
                if (hp.getStartTimestamp() > lookAheadEnd) {
                    // Beyond our window — stop iterating pages too
                    return coveredWeeks;
                }
                if (MomentumCategory.CONFIRMED == hp.getMomentumCategory()) {
                    ZonedDateTime dt = Instant.ofEpochSecond(hp.getStartTimestamp()).atZone(ZoneOffset.UTC);
                    coveredWeeks.add(weekKey(dt.get(IsoFields.WEEK_BASED_YEAR), dt.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)));
                }
            }

            token = page.getNextToken();
        } while (page.hasMore() && token != null);

        return coveredWeeks;
    }

    private static int weekKey(int year, int isoWeek) {
        return year * 100 + isoWeek;
    }

    // -------------------------------------------------------------------------
    // Mapping
    // -------------------------------------------------------------------------

    private IdeaFeedItemDTO toFeedItem(IdeaDTO idea, IdeaListDTO list, String groupId) {
        return new IdeaFeedItemDTO(
                idea.getId(),
                list.getId(),
                groupId,
                idea.getName(),
                list.getName(),
                idea.getImageUrl(),
                idea.getNote(),
                idea.getInterestCount(),
                idea.getGooglePlaceId(),
                idea.getAddress(),
                idea.getLatitude(),
                idea.getLongitude(),
                idea.getPlaceCategory()
        );
    }
}
