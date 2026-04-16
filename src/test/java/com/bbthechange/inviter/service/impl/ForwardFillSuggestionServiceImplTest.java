package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.config.MomentumTuningProperties;
import com.bbthechange.inviter.dto.HangoutSummaryDTO;
import com.bbthechange.inviter.dto.IdeaDTO;
import com.bbthechange.inviter.dto.IdeaFeedItemDTO;
import com.bbthechange.inviter.dto.IdeaListDTO;
import com.bbthechange.inviter.service.ForwardFillSuggestionService;
import com.bbthechange.inviter.service.IdeaListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForwardFillSuggestionServiceImplTest {

    @Mock private WeekCoverageCalculator weekCoverageCalculator;
    @Mock private IdeaListService ideaListService;

    private MomentumTuningProperties tuning;
    private ForwardFillSuggestionServiceImpl service;

    private static final String GROUP_ID = "group-abc";
    private static final String USER_ID = "user-123";
    private static final long NOW = 1767787200L;

    @BeforeEach
    void setUp() {
        tuning = new MomentumTuningProperties();
        service = new ForwardFillSuggestionServiceImpl(weekCoverageCalculator, ideaListService, tuning);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private HangoutSummaryDTO stale(String id, int interestSize, long createdAtSeconds) {
        HangoutSummaryDTO dto = HangoutSummaryDTO.builder()
                .withHangoutId(id)
                .withTitle(id)
                .build();
        dto.setCreatedAt(createdAtSeconds);
        List<com.bbthechange.inviter.model.InterestLevel> levels = new ArrayList<>();
        for (int i = 0; i < interestSize; i++) {
            com.bbthechange.inviter.model.InterestLevel il = new com.bbthechange.inviter.model.InterestLevel();
            il.setUserId("u" + i);
            il.setUserName("User " + i);
            il.setStatus("INTERESTED");
            il.setUpdatedAt(Instant.ofEpochSecond(createdAtSeconds));
            levels.add(il);
        }
        dto.setInterestLevels(levels);
        return dto;
    }

    private IdeaListDTO list(String id, String name, List<IdeaDTO> ideas) {
        IdeaListDTO l = new IdeaListDTO();
        l.setId(id);
        l.setName(name);
        l.setCreatedBy(USER_ID);
        l.setCreatedAt(Instant.now());
        ideas.forEach(l::addIdea);
        return l;
    }

    private IdeaDTO idea(String id, String name, int interestCount) {
        IdeaDTO i = new IdeaDTO();
        i.setId(id);
        i.setName(name);
        i.setInterestCount(interestCount);
        i.setAddedBy(USER_ID);
        i.setAddedTime(Instant.now());
        return i;
    }

    // -------------------------------------------------------------------------
    // Budget handling
    // -------------------------------------------------------------------------

    @Test
    void allWeeksCovered_returnsEmpty_noIdeaListCall() {
        when(weekCoverageCalculator.countEmptyWeeks(eq(GROUP_ID), anyLong())).thenReturn(0);

        ForwardFillSuggestionService.ForwardFillResult r =
                service.getForwardFill(GROUP_ID, NOW, USER_ID, List.of());

        assertThat(r.getStaleFloats()).isEmpty();
        assertThat(r.getIdeas()).isEmpty();
        verify(ideaListService, never()).getIdeaListsForGroup(anyString(), anyString());
    }

    @Test
    void weekCoverageThrows_returnsEmpty() {
        when(weekCoverageCalculator.countEmptyWeeks(eq(GROUP_ID), anyLong()))
                .thenThrow(new RuntimeException("boom"));

        ForwardFillSuggestionService.ForwardFillResult r =
                service.getForwardFill(GROUP_ID, NOW, USER_ID, List.of());

        assertThat(r.getStaleFloats()).isEmpty();
        assertThat(r.getIdeas()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Priority: stale floats first
    // -------------------------------------------------------------------------

    @Test
    void staleFloatsFirst_upToBudget() {
        when(weekCoverageCalculator.countEmptyWeeks(eq(GROUP_ID), anyLong())).thenReturn(2);

        HangoutSummaryDTO s1 = stale("s1", 1, NOW - 86400);
        HangoutSummaryDTO s2 = stale("s2", 1, NOW - 2 * 86400);
        HangoutSummaryDTO s3 = stale("s3", 1, NOW - 3 * 86400);

        ForwardFillSuggestionService.ForwardFillResult r =
                service.getForwardFill(GROUP_ID, NOW, USER_ID, List.of(s1, s2, s3));

        assertThat(r.getStaleFloats()).hasSize(2);
        assertThat(r.getIdeas()).isEmpty();
        verify(ideaListService, never()).getIdeaListsForGroup(anyString(), anyString());
    }

    @Test
    void staleFloatsSortedByInterestSizeDesc_thenCreatedAtDesc() {
        when(weekCoverageCalculator.countEmptyWeeks(eq(GROUP_ID), anyLong())).thenReturn(3);

        HangoutSummaryDTO s0older = stale("older-1", 1, NOW - 10 * 86400); // size=1, older
        HangoutSummaryDTO s0newer = stale("newer-1", 1, NOW - 2 * 86400);  // size=1, newer
        HangoutSummaryDTO s1 = stale("with-2", 2, NOW - 5 * 86400);        // size=2 → should sort first

        ForwardFillSuggestionService.ForwardFillResult r =
                service.getForwardFill(GROUP_ID, NOW, USER_ID, List.of(s0older, s0newer, s1));

        List<String> ids = r.getStaleFloats().stream().map(HangoutSummaryDTO::getHangoutId).toList();
        // size=2 first, then among size=1: newer before older
        assertThat(ids).containsExactly("with-2", "newer-1", "older-1");
    }

    @Test
    void staleFloatsMarkedAsStaleFiller() {
        when(weekCoverageCalculator.countEmptyWeeks(eq(GROUP_ID), anyLong())).thenReturn(1);

        HangoutSummaryDTO s = stale("s", 1, NOW - 86400);

        ForwardFillSuggestionService.ForwardFillResult r =
                service.getForwardFill(GROUP_ID, NOW, USER_ID, List.of(s));

        assertThat(r.getStaleFloats().get(0).getSurfaceReason())
                .isEqualTo(FeedSortingService.REASON_STALE_FILLER);
    }

    // -------------------------------------------------------------------------
    // Priority: supported ideas next
    // -------------------------------------------------------------------------

    @Test
    void supportedIdeasFillAfterStaleFloats() {
        when(weekCoverageCalculator.countEmptyWeeks(eq(GROUP_ID), anyLong())).thenReturn(3);

        HangoutSummaryDTO s = stale("s", 1, NOW - 86400);
        IdeaDTO supported = idea("i-sup", "Ramen", 5);
        IdeaDTO supported2 = idea("i-sup2", "Bar", 3);
        when(ideaListService.getIdeaListsForGroup(GROUP_ID, USER_ID))
                .thenReturn(List.of(list("list-1", "Food", List.of(supported, supported2))));

        ForwardFillSuggestionService.ForwardFillResult r =
                service.getForwardFill(GROUP_ID, NOW, USER_ID, List.of(s));

        assertThat(r.getStaleFloats()).hasSize(1);
        assertThat(r.getIdeas()).hasSize(2);
        r.getIdeas().forEach(i ->
                assertThat(i.getSurfaceReason())
                        .isEqualTo(ForwardFillSuggestionServiceImpl.REASON_SUPPORTED_IDEA));
        // Sorted by interest desc
        assertThat(r.getIdeas().get(0).getIdeaId()).isEqualTo("i-sup");
    }

    // -------------------------------------------------------------------------
    // Priority: unsupported ideas as last resort
    // -------------------------------------------------------------------------

    @Test
    void unsupportedIdeasUsedAsLastResort() {
        when(weekCoverageCalculator.countEmptyWeeks(eq(GROUP_ID), anyLong())).thenReturn(3);

        IdeaDTO supported = idea("i-sup", "Sushi", 4);   // meets threshold (default 3)
        IdeaDTO unsup1    = idea("i-un1", "Park", 1);    // below threshold
        IdeaDTO unsup2    = idea("i-un2", "Bowling", 2); // below threshold
        when(ideaListService.getIdeaListsForGroup(GROUP_ID, USER_ID))
                .thenReturn(List.of(list("l1", "All", List.of(supported, unsup1, unsup2))));

        ForwardFillSuggestionService.ForwardFillResult r =
                service.getForwardFill(GROUP_ID, NOW, USER_ID, List.of());

        assertThat(r.getIdeas()).hasSize(3);
        assertThat(r.getIdeas().get(0).getIdeaId()).isEqualTo("i-sup");
        assertThat(r.getIdeas().get(0).getSurfaceReason())
                .isEqualTo(ForwardFillSuggestionServiceImpl.REASON_SUPPORTED_IDEA);
        // Unsupported sorted by interest desc: unsup2 (2) before unsup1 (1)
        assertThat(r.getIdeas().get(1).getIdeaId()).isEqualTo("i-un2");
        assertThat(r.getIdeas().get(1).getSurfaceReason())
                .isEqualTo(ForwardFillSuggestionServiceImpl.REASON_UNSUPPORTED_IDEA);
        assertThat(r.getIdeas().get(2).getIdeaId()).isEqualTo("i-un1");
    }

    @Test
    void zeroInterestIdeasExcluded() {
        when(weekCoverageCalculator.countEmptyWeeks(eq(GROUP_ID), anyLong())).thenReturn(3);

        IdeaDTO zero = idea("zero", "Unwanted", 0);
        when(ideaListService.getIdeaListsForGroup(GROUP_ID, USER_ID))
                .thenReturn(List.of(list("l1", "x", List.of(zero))));

        ForwardFillSuggestionService.ForwardFillResult r =
                service.getForwardFill(GROUP_ID, NOW, USER_ID, List.of());

        assertThat(r.getIdeas()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Budget capping across the priority ladder
    // -------------------------------------------------------------------------

    @Test
    void budgetTotalCappedAcrossAllPriorities() {
        when(weekCoverageCalculator.countEmptyWeeks(eq(GROUP_ID), anyLong())).thenReturn(2);

        HangoutSummaryDTO s = stale("s", 1, NOW - 86400);
        IdeaDTO idea1 = idea("i1", "A", 5);
        IdeaDTO idea2 = idea("i2", "B", 4);
        when(ideaListService.getIdeaListsForGroup(GROUP_ID, USER_ID))
                .thenReturn(List.of(list("l", "x", List.of(idea1, idea2))));

        ForwardFillSuggestionService.ForwardFillResult r =
                service.getForwardFill(GROUP_ID, NOW, USER_ID, List.of(s));

        // budget=2: 1 stale + 1 supported idea
        assertThat(r.getStaleFloats()).hasSize(1);
        assertThat(r.getIdeas()).hasSize(1);
        assertThat(r.getIdeas().get(0).getIdeaId()).isEqualTo("i1");
    }

    // -------------------------------------------------------------------------
    // Graceful degradation
    // -------------------------------------------------------------------------

    @Test
    void ideaServiceThrows_staleFloatsStillSurface() {
        when(weekCoverageCalculator.countEmptyWeeks(eq(GROUP_ID), anyLong())).thenReturn(3);

        HangoutSummaryDTO s = stale("s", 1, NOW - 86400);
        when(ideaListService.getIdeaListsForGroup(GROUP_ID, USER_ID))
                .thenThrow(new RuntimeException("db down"));

        ForwardFillSuggestionService.ForwardFillResult r =
                service.getForwardFill(GROUP_ID, NOW, USER_ID, List.of(s));

        assertThat(r.getStaleFloats()).hasSize(1);
        assertThat(r.getIdeas()).isEmpty();
    }

    @Test
    void nullHeldStaleFloats_handledGracefully() {
        when(weekCoverageCalculator.countEmptyWeeks(eq(GROUP_ID), anyLong())).thenReturn(1);
        when(ideaListService.getIdeaListsForGroup(GROUP_ID, USER_ID)).thenReturn(List.of());

        ForwardFillSuggestionService.ForwardFillResult r =
                service.getForwardFill(GROUP_ID, NOW, USER_ID, null);

        assertThat(r.getStaleFloats()).isEmpty();
        assertThat(r.getIdeas()).isEmpty();
    }
}
