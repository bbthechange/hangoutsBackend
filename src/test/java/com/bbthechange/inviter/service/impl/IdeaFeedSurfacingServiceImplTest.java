package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.IdeaDTO;
import com.bbthechange.inviter.dto.IdeaFeedItemDTO;
import com.bbthechange.inviter.dto.IdeaListDTO;
import com.bbthechange.inviter.model.BaseItem;
import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.model.MomentumCategory;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.IdeaListService;
import com.bbthechange.inviter.util.PaginatedResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for IdeaFeedSurfacingServiceImpl.
 *
 * <p>Covers idea filtering, suppression logic, sorting, and DTO mapping.
 */
@ExtendWith(MockitoExtension.class)
class IdeaFeedSurfacingServiceImplTest {

    @Mock
    private IdeaListService ideaListService;

    @Mock
    private HangoutRepository hangoutRepository;

    private IdeaFeedSurfacingServiceImpl service;

    private static final String GROUP_ID = "group-abc";
    private static final String USER_ID = "user-123";
    // nowTimestamp in the middle of a week to make week boundary tests predictable.
    // 2024-03-13T12:00:00Z = Wednesday of ISO week 11 of 2024
    private static final long NOW = 1710331200L;

    @BeforeEach
    void setUp() {
        service = new IdeaFeedSurfacingServiceImpl(ideaListService, hangoutRepository);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Creates an empty paginated result with no more pages. */
    private PaginatedResult<BaseItem> emptyPage() {
        return new PaginatedResult<>(List.of(), null);
    }

    private PaginatedResult<BaseItem> pageOf(List<BaseItem> items) {
        return new PaginatedResult<>(items, null);
    }

    private HangoutPointer confirmedPointer(long startTimestamp) {
        HangoutPointer hp = new HangoutPointer();
        hp.setHangoutId("hangout-" + startTimestamp);
        hp.setStartTimestamp(startTimestamp);
        hp.setMomentumCategory(MomentumCategory.CONFIRMED);
        return hp;
    }

    private HangoutPointer buildingPointer(long startTimestamp) {
        HangoutPointer hp = new HangoutPointer();
        hp.setHangoutId("hangout-building-" + startTimestamp);
        hp.setStartTimestamp(startTimestamp);
        hp.setMomentumCategory(MomentumCategory.BUILDING);
        return hp;
    }

    private IdeaListDTO listWithIdeas(String listId, String listName, List<IdeaDTO> ideas) {
        IdeaListDTO list = new IdeaListDTO();
        list.setId(listId);
        list.setName(listName);
        list.setCreatedBy(USER_ID);
        list.setCreatedAt(Instant.now());
        ideas.forEach(list::addIdea);
        return list;
    }

    private IdeaDTO idea(String ideaId, String name, int interestCount) {
        IdeaDTO dto = new IdeaDTO();
        dto.setId(ideaId);
        dto.setName(name);
        dto.setInterestCount(interestCount);
        dto.setAddedBy(USER_ID);
        dto.setAddedTime(Instant.now());
        return dto;
    }

    // =========================================================================
    // Filtering by interest count
    // =========================================================================

    @Nested
    class InterestCountFiltering {

        @BeforeEach
        void noSuppression() {
            // No confirmed hangouts → suppression never applies
            when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), anyInt(), any()))
                    .thenReturn(emptyPage());
        }

        @Test
        void ideasBelowThreshold_AreNotSurfaced() {
            IdeaDTO lowInterest = idea("idea-1", "Bowling", 2); // < 3
            when(ideaListService.getIdeaListsForGroup(GROUP_ID, USER_ID))
                    .thenReturn(List.of(listWithIdeas("list-1", "Fun Stuff", List.of(lowInterest))));

            List<IdeaFeedItemDTO> result = service.getSurfacedIdeas(GROUP_ID, NOW, USER_ID);

            assertThat(result).isEmpty();
        }

        @Test
        void ideasAtExactThreshold_AreSurfaced() {
            IdeaDTO atThreshold = idea("idea-1", "Bowling", 3); // exactly 3
            when(ideaListService.getIdeaListsForGroup(GROUP_ID, USER_ID))
                    .thenReturn(List.of(listWithIdeas("list-1", "Fun Stuff", List.of(atThreshold))));

            List<IdeaFeedItemDTO> result = service.getSurfacedIdeas(GROUP_ID, NOW, USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getIdeaId()).isEqualTo("idea-1");
        }

        @Test
        void ideasAboveThreshold_AreSurfaced() {
            IdeaDTO highInterest = idea("idea-1", "Concert", 7);
            when(ideaListService.getIdeaListsForGroup(GROUP_ID, USER_ID))
                    .thenReturn(List.of(listWithIdeas("list-1", "Activities", List.of(highInterest))));

            List<IdeaFeedItemDTO> result = service.getSurfacedIdeas(GROUP_ID, NOW, USER_ID);

            assertThat(result).hasSize(1);
        }

        @Test
        void mixOfAboveAndBelowThreshold_OnlySurfacesQualifying() {
            IdeaDTO low = idea("idea-low", "Bowling", 1);
            IdeaDTO mid = idea("idea-mid", "Hiking", 2);
            IdeaDTO high = idea("idea-high", "Sushi Night", 5);
            when(ideaListService.getIdeaListsForGroup(GROUP_ID, USER_ID))
                    .thenReturn(List.of(listWithIdeas("list-1", "Plans", List.of(low, mid, high))));

            List<IdeaFeedItemDTO> result = service.getSurfacedIdeas(GROUP_ID, NOW, USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getIdeaId()).isEqualTo("idea-high");
        }

        @Test
        void multipleListsWithHighInterestIdeas_AllSurfaced() {
            IdeaDTO idea1 = idea("idea-1", "Sushi", 4);
            IdeaDTO idea2 = idea("idea-2", "Hiking", 3);
            IdeaListDTO list1 = listWithIdeas("list-1", "Restaurants", List.of(idea1));
            IdeaListDTO list2 = listWithIdeas("list-2", "Activities", List.of(idea2));
            when(ideaListService.getIdeaListsForGroup(GROUP_ID, USER_ID))
                    .thenReturn(List.of(list1, list2));

            List<IdeaFeedItemDTO> result = service.getSurfacedIdeas(GROUP_ID, NOW, USER_ID);

            assertThat(result).hasSize(2);
        }

        @Test
        void emptyIdeaLists_ReturnsEmpty() {
            when(ideaListService.getIdeaListsForGroup(GROUP_ID, USER_ID))
                    .thenReturn(List.of());

            List<IdeaFeedItemDTO> result = service.getSurfacedIdeas(GROUP_ID, NOW, USER_ID);

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // Sorting
    // =========================================================================

    @Nested
    class Sorting {

        @BeforeEach
        void noSuppression() {
            when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), anyInt(), any()))
                    .thenReturn(emptyPage());
        }

        @Test
        void resultsSortedByInterestCountDescending() {
            IdeaDTO idea3 = idea("idea-3", "Bowling", 3);
            IdeaDTO idea7 = idea("idea-7", "Concert", 7);
            IdeaDTO idea5 = idea("idea-5", "Hiking", 5);
            when(ideaListService.getIdeaListsForGroup(GROUP_ID, USER_ID))
                    .thenReturn(List.of(listWithIdeas("list-1", "Plans", List.of(idea3, idea7, idea5))));

            List<IdeaFeedItemDTO> result = service.getSurfacedIdeas(GROUP_ID, NOW, USER_ID);

            assertThat(result).hasSize(3);
            assertThat(result.get(0).getInterestCount()).isEqualTo(7);
            assertThat(result.get(1).getInterestCount()).isEqualTo(5);
            assertThat(result.get(2).getInterestCount()).isEqualTo(3);
        }
    }

    // =========================================================================
    // DTO field mapping
    // =========================================================================

    @Nested
    class DtoMapping {

        @BeforeEach
        void noSuppression() {
            when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), anyInt(), any()))
                    .thenReturn(emptyPage());
        }

        @Test
        void allFieldsMappedCorrectly() {
            IdeaDTO idea = idea("idea-1", "Sushi Nakazawa", 4);
            idea.setImageUrl("https://example.com/sushi.jpg");
            idea.setNote("Omakase only");
            idea.setGooglePlaceId("ChIJ_place123");
            idea.setAddress("23 Commerce St, New York");
            idea.setLatitude(40.7271);
            idea.setLongitude(-74.0028);
            idea.setPlaceCategory("restaurant");

            IdeaListDTO list = listWithIdeas("list-1", "NYC Restaurants", List.of(idea));
            when(ideaListService.getIdeaListsForGroup(GROUP_ID, USER_ID))
                    .thenReturn(List.of(list));

            List<IdeaFeedItemDTO> result = service.getSurfacedIdeas(GROUP_ID, NOW, USER_ID);

            assertThat(result).hasSize(1);
            IdeaFeedItemDTO item = result.get(0);
            assertThat(item.getType()).isEqualTo("idea_suggestion");
            assertThat(item.getIdeaId()).isEqualTo("idea-1");
            assertThat(item.getListId()).isEqualTo("list-1");
            assertThat(item.getGroupId()).isEqualTo(GROUP_ID);
            assertThat(item.getIdeaName()).isEqualTo("Sushi Nakazawa");
            assertThat(item.getListName()).isEqualTo("NYC Restaurants");
            assertThat(item.getImageUrl()).isEqualTo("https://example.com/sushi.jpg");
            assertThat(item.getNote()).isEqualTo("Omakase only");
            assertThat(item.getInterestCount()).isEqualTo(4);
            assertThat(item.getGooglePlaceId()).isEqualTo("ChIJ_place123");
            assertThat(item.getAddress()).isEqualTo("23 Commerce St, New York");
            assertThat(item.getLatitude()).isEqualTo(40.7271);
            assertThat(item.getLongitude()).isEqualTo(-74.0028);
            assertThat(item.getPlaceCategory()).isEqualTo("restaurant");
        }

        @Test
        void optionalFieldsMissingFromIdea_MappedAsNull() {
            IdeaDTO idea = idea("idea-1", "Bowling", 3);
            // No optional fields set

            when(ideaListService.getIdeaListsForGroup(GROUP_ID, USER_ID))
                    .thenReturn(List.of(listWithIdeas("list-1", "Activities", List.of(idea))));

            List<IdeaFeedItemDTO> result = service.getSurfacedIdeas(GROUP_ID, NOW, USER_ID);

            assertThat(result).hasSize(1);
            IdeaFeedItemDTO item = result.get(0);
            assertThat(item.getImageUrl()).isNull();
            assertThat(item.getNote()).isNull();
            assertThat(item.getGooglePlaceId()).isNull();
            assertThat(item.getAddress()).isNull();
            assertThat(item.getLatitude()).isNull();
            assertThat(item.getLongitude()).isNull();
            assertThat(item.getPlaceCategory()).isNull();
        }
    }

    // =========================================================================
    // Suppression logic
    // =========================================================================

    @Nested
    class SuppressionLogic {

        @Test
        void noConfirmedHangouts_NoSuppression_IdeasSurfaced() {
            when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), anyInt(), any()))
                    .thenReturn(emptyPage());
            IdeaDTO idea = idea("idea-1", "Sushi Night", 4);
            when(ideaListService.getIdeaListsForGroup(GROUP_ID, USER_ID))
                    .thenReturn(List.of(listWithIdeas("list-1", "Plans", List.of(idea))));

            List<IdeaFeedItemDTO> result = service.getSurfacedIdeas(GROUP_ID, NOW, USER_ID);

            assertThat(result).hasSize(1);
        }

        @Test
        void confirmedHangoutsInOnly2Of3UpcomingWeeks_NoSuppression_IdeasSurfaced() {
            // NOW = Wednesday 2024-03-13 (week 11)
            // Confirmed in week 11 (current week) and week 12 — week 13 is empty
            long week11Ts = NOW + 1000;                  // current week
            long week12Ts = NOW + (7 * 24 * 3600L);     // next week
            // week 13 has nothing

            List<BaseItem> items = new ArrayList<>();
            items.add(confirmedPointer(week11Ts));
            items.add(confirmedPointer(week12Ts));

            when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), anyInt(), any()))
                    .thenReturn(pageOf(items));

            IdeaDTO idea = idea("idea-1", "Concert", 5);
            when(ideaListService.getIdeaListsForGroup(GROUP_ID, USER_ID))
                    .thenReturn(List.of(listWithIdeas("list-1", "Plans", List.of(idea))));

            List<IdeaFeedItemDTO> result = service.getSurfacedIdeas(GROUP_ID, NOW, USER_ID);

            assertThat(result).hasSize(1);
        }

        @Test
        void confirmedHangoutsInAll3UpcomingWeeks_Suppressed_EmptyReturned() {
            // NOW = Wednesday 2024-03-13 (week 11)
            // Confirmed in weeks 11, 12, 13
            long week11Ts = NOW + 1000;
            long week12Ts = NOW + (7 * 24 * 3600L);
            long week13Ts = NOW + (14 * 24 * 3600L);

            List<BaseItem> items = new ArrayList<>();
            items.add(confirmedPointer(week11Ts));
            items.add(confirmedPointer(week12Ts));
            items.add(confirmedPointer(week13Ts));

            when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), anyInt(), any()))
                    .thenReturn(pageOf(items));

            // ideaListService should NOT be called when suppressed
            List<IdeaFeedItemDTO> result = service.getSurfacedIdeas(GROUP_ID, NOW, USER_ID);

            assertThat(result).isEmpty();
            verify(ideaListService, never()).getIdeaListsForGroup(anyString(), anyString());
        }

        @Test
        void nonConfirmedHangoutsInAll3Weeks_DoesNotSuppressIdeas() {
            // BUILDING hangouts do not count for suppression
            long week11Ts = NOW + 1000;
            long week12Ts = NOW + (7 * 24 * 3600L);
            long week13Ts = NOW + (14 * 24 * 3600L);

            List<BaseItem> items = new ArrayList<>();
            items.add(buildingPointer(week11Ts));
            items.add(buildingPointer(week12Ts));
            items.add(buildingPointer(week13Ts));

            when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), anyInt(), any()))
                    .thenReturn(pageOf(items));

            IdeaDTO idea = idea("idea-1", "Hiking", 3);
            when(ideaListService.getIdeaListsForGroup(GROUP_ID, USER_ID))
                    .thenReturn(List.of(listWithIdeas("list-1", "Plans", List.of(idea))));

            List<IdeaFeedItemDTO> result = service.getSurfacedIdeas(GROUP_ID, NOW, USER_ID);

            assertThat(result).hasSize(1);
        }

        @Test
        void confirmedHangoutsInSameWeekMultipleTimes_CountsAsOnlyOneWeek() {
            // Two CONFIRMED hangouts in week 11 only — weeks 12 and 13 are uncovered
            long week11TsA = NOW + 3600;
            long week11TsB = NOW + (2 * 3600);

            List<BaseItem> items = new ArrayList<>();
            items.add(confirmedPointer(week11TsA));
            items.add(confirmedPointer(week11TsB));

            when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), anyInt(), any()))
                    .thenReturn(pageOf(items));

            IdeaDTO idea = idea("idea-1", "Dinner", 4);
            when(ideaListService.getIdeaListsForGroup(GROUP_ID, USER_ID))
                    .thenReturn(List.of(listWithIdeas("list-1", "Plans", List.of(idea))));

            List<IdeaFeedItemDTO> result = service.getSurfacedIdeas(GROUP_ID, NOW, USER_ID);

            // Only 1 of 3 weeks covered — not suppressed
            assertThat(result).hasSize(1);
        }
    }

    // =========================================================================
    // Null-safety edge cases
    // =========================================================================

    @Nested
    class NullSafety {

        @BeforeEach
        void noSuppression() {
            when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), anyInt(), any()))
                    .thenReturn(emptyPage());
        }

        @Test
        void getSurfacedIdeas_ideaListWithNoIdeas_handledGracefully() {
            // IdeaListDTO with no ideas added — no NPE, returns empty
            IdeaListDTO list = new IdeaListDTO();
            list.setId("list-empty");
            list.setName("Empty List");
            list.setCreatedBy(USER_ID);
            list.setCreatedAt(Instant.now());
            // No ideas added

            when(ideaListService.getIdeaListsForGroup(GROUP_ID, USER_ID))
                    .thenReturn(List.of(list));

            List<IdeaFeedItemDTO> result = service.getSurfacedIdeas(GROUP_ID, NOW, USER_ID);

            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // Suppression helper method unit tests
    // =========================================================================

    @Nested
    class AllUpcomingWeeksCoveredHelper {

        @Test
        void noHangouts_AllWeeksUncovered_ReturnsFalse() {
            when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), anyInt(), any()))
                    .thenReturn(emptyPage());

            assertThat(service.allUpcomingWeeksCovered(GROUP_ID, NOW)).isFalse();
        }

        @Test
        void all3WeeksCovered_ReturnsTrue() {
            long week11Ts = NOW + 1000;
            long week12Ts = NOW + (7 * 24 * 3600L);
            long week13Ts = NOW + (14 * 24 * 3600L);

            List<BaseItem> items = List.of(
                    confirmedPointer(week11Ts),
                    confirmedPointer(week12Ts),
                    confirmedPointer(week13Ts)
            );
            when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), anyInt(), any()))
                    .thenReturn(pageOf(items));

            assertThat(service.allUpcomingWeeksCovered(GROUP_ID, NOW)).isTrue();
        }

        @Test
        void allUpcomingWeeksCovered_beyondLookAheadWindow_ignored() {
            // A confirmed hangout beyond the 3-week look-ahead should not count
            long week11Ts = NOW + 1000;
            long week12Ts = NOW + (7 * 24 * 3600L);
            // This one is beyond 3 weeks — should be ignored, weeks not all covered
            long beyond3WeeksTs = NOW + (22 * 24 * 3600L);

            List<BaseItem> items = new ArrayList<>();
            items.add(confirmedPointer(week11Ts));
            items.add(confirmedPointer(week12Ts));
            items.add(confirmedPointer(beyond3WeeksTs));

            when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), anyInt(), any()))
                    .thenReturn(pageOf(items));

            // Only 2 of 3 upcoming weeks covered (beyond-window item ignored) → false
            assertThat(service.allUpcomingWeeksCovered(GROUP_ID, NOW)).isFalse();
        }

        @Test
        void allUpcomingWeeksCovered_paginationHandled_multiplePages() {
            // First page has first two weeks' confirmed hangouts with hasMore=true
            long week11Ts = NOW + 1000;
            long week12Ts = NOW + (7 * 24 * 3600L);
            long week13Ts = NOW + (14 * 24 * 3600L);

            List<BaseItem> page1Items = new ArrayList<>();
            page1Items.add(confirmedPointer(week11Ts));
            page1Items.add(confirmedPointer(week12Ts));
            PaginatedResult<BaseItem> page1 = new PaginatedResult<>(page1Items, "next-page-token");

            List<BaseItem> page2Items = new ArrayList<>();
            page2Items.add(confirmedPointer(week13Ts));
            PaginatedResult<BaseItem> page2 = new PaginatedResult<>(page2Items, null);

            when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), anyInt(), isNull()))
                    .thenReturn(page1);
            when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), anyInt(), eq("next-page-token")))
                    .thenReturn(page2);

            // All 3 weeks covered across 2 pages
            assertThat(service.allUpcomingWeeksCovered(GROUP_ID, NOW)).isTrue();
        }

        @Test
        void onlyTwoWeeksCovered_ReturnsFalse() {
            long week11Ts = NOW + 1000;
            long week12Ts = NOW + (7 * 24 * 3600L);

            List<BaseItem> items = List.of(
                    confirmedPointer(week11Ts),
                    confirmedPointer(week12Ts)
            );
            when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), anyInt(), any()))
                    .thenReturn(pageOf(items));

            assertThat(service.allUpcomingWeeksCovered(GROUP_ID, NOW)).isFalse();
        }
    }

    // =========================================================================
    // Error handling
    // =========================================================================

    @Nested
    class ErrorHandling {

        @Test
        void ideaListServiceThrows_ReturnsEmptyList() {
            when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), anyInt(), any()))
                    .thenReturn(emptyPage());
            when(ideaListService.getIdeaListsForGroup(eq(GROUP_ID), anyString()))
                    .thenThrow(new RuntimeException("DynamoDB unavailable"));

            List<IdeaFeedItemDTO> result = service.getSurfacedIdeas(GROUP_ID, NOW, USER_ID);

            assertThat(result).isEmpty();
        }

        @Test
        void hangoutRepositoryThrows_ReturnsFalseForSuppression() {
            // When repository throws, suppression check fails safely — ideas should still be surfaced
            when(hangoutRepository.getFutureEventsPage(eq(GROUP_ID), anyLong(), anyInt(), any()))
                    .thenThrow(new RuntimeException("DynamoDB unavailable"));

            IdeaDTO idea = idea("idea-1", "Sushi", 4);
            when(ideaListService.getIdeaListsForGroup(GROUP_ID, USER_ID))
                    .thenReturn(List.of(listWithIdeas("list-1", "Plans", List.of(idea))));

            // Should not throw; suppression check failed → not suppressed → ideas surfaced
            List<IdeaFeedItemDTO> result = service.getSurfacedIdeas(GROUP_ID, NOW, USER_ID);

            assertThat(result).hasSize(1);
        }
    }
}
