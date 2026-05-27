package com.bbthechange.inviter.util;

import com.bbthechange.inviter.service.ShowFlavorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class WatchPartyTitleFormatterTest {

    private static final Integer CURATED_SHOW = 4596;
    private static final Integer UNCURATED_SHOW = 9999;
    private static final String SHORT_NAME = "All Stars";
    private static final String SHOW_NAME = "RuPaul's Drag Race: All Stars";

    @Mock
    private ShowFlavorService showFlavorService;

    private WatchPartyTitleFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new WatchPartyTitleFormatter(showFlavorService);
        // Lenient because not every test exercises the lookup (e.g. TBA pass-through tests
        // never call into the service); strict stubbing would otherwise fail those tests.
        lenient().when(showFlavorService.getShortName(CURATED_SHOW)).thenReturn(Optional.of(SHORT_NAME));
        lenient().when(showFlavorService.getShortName(UNCURATED_SHOW)).thenReturn(Optional.empty());
    }

    @Nested
    class FormatEpisodeTitle {

        @Test
        void curatedShow_prefixesShortName() {
            assertThat(formatter.formatEpisodeTitle(CURATED_SHOW, SHOW_NAME, "How To Videos"))
                    .isEqualTo("All Stars · How To Videos");
        }

        @Test
        void uncuratedShow_prefixesShowNameFallback() {
            assertThat(formatter.formatEpisodeTitle(UNCURATED_SHOW, "The Boys", "How To Videos"))
                    .isEqualTo("The Boys · How To Videos");
        }

        @Test
        void uncuratedShow_keepsColonInsideShowName() {
            // The mid-dot separator visually disambiguates from colons within show names.
            assertThat(formatter.formatEpisodeTitle(UNCURATED_SHOW, SHOW_NAME, "How To Videos"))
                    .isEqualTo("RuPaul's Drag Race: All Stars · How To Videos");
        }

        @Test
        void nullShowId_usesShowNameFallback() {
            assertThat(formatter.formatEpisodeTitle(null, "The Boys", "How To Videos"))
                    .isEqualTo("The Boys · How To Videos");
        }

        @Test
        void noContextAtAll_returnsRawTitle() {
            // Safety net: tests / legacy paths with neither showId nor showName.
            assertThat(formatter.formatEpisodeTitle(null, null, "How To Videos"))
                    .isEqualTo("How To Videos");
            assertThat(formatter.formatEpisodeTitle(UNCURATED_SHOW, "  ", "How To Videos"))
                    .isEqualTo("How To Videos");
        }

        @Test
        void shortNameWins_overShowName() {
            assertThat(formatter.formatEpisodeTitle(CURATED_SHOW, SHOW_NAME, "How To Videos"))
                    .isEqualTo("All Stars · How To Videos");
        }

        @Test
        void nullTitle_returnsNullEvenWhenCurated() {
            assertThat(formatter.formatEpisodeTitle(CURATED_SHOW, SHOW_NAME, null)).isNull();
        }

        @Test
        void blankTitle_returnsBlankEvenWhenCurated() {
            assertThat(formatter.formatEpisodeTitle(CURATED_SHOW, SHOW_NAME, "   ")).isEqualTo("   ");
        }

        @Test
        void tbaTitle_returnsUnchangedEvenWhenCurated() {
            // Hard contract: prefixing TBA would defeat EpisodeTitles.isTba downstream.
            assertThat(formatter.formatEpisodeTitle(CURATED_SHOW, SHOW_NAME, "TBA")).isEqualTo("TBA");
            assertThat(formatter.formatEpisodeTitle(CURATED_SHOW, SHOW_NAME, "tba")).isEqualTo("tba");
            assertThat(formatter.formatEpisodeTitle(CURATED_SHOW, SHOW_NAME, "  TBA  ")).isEqualTo("  TBA  ");
        }
    }

    @Nested
    class FormatCombinedEpisodeTitle {

        @Test
        void singleEpisode_curated_appliesShortNamePrefix() {
            assertThat(formatter.formatCombinedEpisodeTitle(CURATED_SHOW, SHOW_NAME, List.of("Pilot")))
                    .isEqualTo("All Stars · Pilot");
        }

        @Test
        void singleEpisode_uncurated_appliesShowNamePrefix() {
            assertThat(formatter.formatCombinedEpisodeTitle(UNCURATED_SHOW, "The Boys", List.of("Pilot")))
                    .isEqualTo("The Boys · Pilot");
        }

        @Test
        void singleEpisode_tba_passesThrough() {
            assertThat(formatter.formatCombinedEpisodeTitle(CURATED_SHOW, SHOW_NAME, List.of("TBA")))
                    .isEqualTo("TBA");
        }

        @Test
        void doubleEpisode_curated_prefixesShortNameWithDoubleEpisodeBody() {
            assertThat(formatter.formatCombinedEpisodeTitle(CURATED_SHOW, SHOW_NAME, List.of("T1", "T2")))
                    .isEqualTo("All Stars · Double Episode: T1, T2");
        }

        @Test
        void doubleEpisode_uncurated_prefixesShowNameWithDoubleEpisodeBody() {
            assertThat(formatter.formatCombinedEpisodeTitle(UNCURATED_SHOW, "The Boys", List.of("Part 1", "Part 2")))
                    .isEqualTo("The Boys · Double Episode: Part 1, Part 2");
        }

        @Test
        void doubleEpisode_noContext_fallsBackToBareBody() {
            assertThat(formatter.formatCombinedEpisodeTitle(null, null, List.of("Part 1", "Part 2")))
                    .isEqualTo("Double Episode: Part 1, Part 2");
        }

        @Test
        void doubleEpisode_curated_constituentTbaPassesThroughInPosition() {
            // Constituent TBA inside a Double is fine — the structural marker carries meaning.
            assertThat(formatter.formatCombinedEpisodeTitle(CURATED_SHOW, SHOW_NAME, List.of("Premiere", "TBA")))
                    .isEqualTo("All Stars · Double Episode: Premiere, TBA");
        }

        @Test
        void doubleEpisode_allConstituentsTba_collapsesToTbaSentinel() {
            // Hard contract: when every constituent is TBA, the combined title must remain
            // detectable as TBA so WatchPartyHostNudgeService's clean-fallback branch fires.
            assertThat(formatter.formatCombinedEpisodeTitle(CURATED_SHOW, SHOW_NAME, List.of("TBA", "TBA")))
                    .isEqualTo("TBA");
        }

        @Test
        void doubleEpisode_allConstituentsTba_collapsesEvenWithoutCuration() {
            assertThat(formatter.formatCombinedEpisodeTitle(UNCURATED_SHOW, "The Boys", List.of("TBA", "TBA")))
                    .isEqualTo("TBA");
        }

        @Test
        void doubleEpisode_mixedBlankAndTba_collapsesToTbaSentinel() {
            assertThat(formatter.formatCombinedEpisodeTitle(CURATED_SHOW, SHOW_NAME, Arrays.asList(null, "  ", "TBA")))
                    .isEqualTo("TBA");
        }

        @Test
        void tripleEpisode_allConstituentsTba_collapsesToTbaSentinel() {
            assertThat(formatter.formatCombinedEpisodeTitle(CURATED_SHOW, SHOW_NAME, List.of("TBA", "tba", "  TBA  ")))
                    .isEqualTo("TBA");
        }

        @Test
        void tripleEpisode_curated_prefixesShortName() {
            assertThat(formatter.formatCombinedEpisodeTitle(CURATED_SHOW, SHOW_NAME, List.of("A", "B", "C")))
                    .isEqualTo("All Stars · Triple Episode");
        }

        @Test
        void tripleEpisode_uncurated_prefixesShowName() {
            assertThat(formatter.formatCombinedEpisodeTitle(UNCURATED_SHOW, "The Boys", List.of("A", "B", "C")))
                    .isEqualTo("The Boys · Triple Episode");
        }

        @Test
        void quadrupleEpisode_curated_prefixesShortName() {
            assertThat(formatter.formatCombinedEpisodeTitle(CURATED_SHOW, SHOW_NAME, List.of("A", "B", "C", "D")))
                    .isEqualTo("All Stars · Quadruple Episode");
        }

        @Test
        void quadrupleEpisode_uncurated_prefixesShowName() {
            assertThat(formatter.formatCombinedEpisodeTitle(UNCURATED_SHOW, "The Boys", List.of("A", "B", "C", "D")))
                    .isEqualTo("The Boys · Quadruple Episode");
        }

        @Test
        void fivePlus_curated_prefixesShortNameWithCount() {
            assertThat(formatter.formatCombinedEpisodeTitle(
                    CURATED_SHOW, SHOW_NAME, Arrays.asList("A", "B", "C", "D", "E")))
                    .isEqualTo("All Stars · Multi-Episode (5)");
        }

        @Test
        void fivePlus_uncurated_prefixesShowNameWithCount() {
            assertThat(formatter.formatCombinedEpisodeTitle(
                    UNCURATED_SHOW, "The Boys", Arrays.asList("A", "B", "C", "D", "E")))
                    .isEqualTo("The Boys · Multi-Episode (5)");
        }

        @Test
        void fivePlus_noContext_bareBody() {
            assertThat(formatter.formatCombinedEpisodeTitle(
                    null, null, Arrays.asList("A", "B", "C", "D", "E")))
                    .isEqualTo("Multi-Episode (5)");
        }

        @Test
        void emptyList_returnsNull() {
            assertThat(formatter.formatCombinedEpisodeTitle(CURATED_SHOW, SHOW_NAME, Collections.emptyList())).isNull();
        }

        @Test
        void nullList_returnsNull() {
            assertThat(formatter.formatCombinedEpisodeTitle(CURATED_SHOW, SHOW_NAME, null)).isNull();
        }
    }
}
