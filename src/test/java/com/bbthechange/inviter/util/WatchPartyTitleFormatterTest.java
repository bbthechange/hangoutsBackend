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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchPartyTitleFormatterTest {

    private static final Integer CURATED_SHOW = 4596;
    private static final Integer UNCURATED_SHOW = 9999;
    private static final String SHORT_NAME = "All Stars";

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
            assertThat(formatter.formatEpisodeTitle(CURATED_SHOW, "How To Videos"))
                    .isEqualTo("All Stars: How To Videos");
        }

        @Test
        void uncuratedShow_returnsRawTitle() {
            assertThat(formatter.formatEpisodeTitle(UNCURATED_SHOW, "How To Videos"))
                    .isEqualTo("How To Videos");
        }

        @Test
        void nullShowId_returnsRawTitle() {
            assertThat(formatter.formatEpisodeTitle(null, "How To Videos"))
                    .isEqualTo("How To Videos");
        }

        @Test
        void nullTitle_returnsNullEvenWhenCurated() {
            assertThat(formatter.formatEpisodeTitle(CURATED_SHOW, null)).isNull();
        }

        @Test
        void blankTitle_returnsBlankEvenWhenCurated() {
            assertThat(formatter.formatEpisodeTitle(CURATED_SHOW, "   ")).isEqualTo("   ");
        }

        @Test
        void tbaTitle_returnsUnchangedEvenWhenCurated() {
            // Hard contract: prefixing TBA would defeat EpisodeTitles.isTba downstream.
            assertThat(formatter.formatEpisodeTitle(CURATED_SHOW, "TBA")).isEqualTo("TBA");
            assertThat(formatter.formatEpisodeTitle(CURATED_SHOW, "tba")).isEqualTo("tba");
            assertThat(formatter.formatEpisodeTitle(CURATED_SHOW, "  TBA  ")).isEqualTo("  TBA  ");
        }
    }

    @Nested
    class FormatCombinedEpisodeTitle {

        @Test
        void singleEpisode_curated_appliesPrefix() {
            assertThat(formatter.formatCombinedEpisodeTitle(CURATED_SHOW, List.of("Pilot")))
                    .isEqualTo("All Stars: Pilot");
        }

        @Test
        void singleEpisode_uncurated_returnsRaw() {
            assertThat(formatter.formatCombinedEpisodeTitle(UNCURATED_SHOW, List.of("Pilot")))
                    .isEqualTo("Pilot");
        }

        @Test
        void singleEpisode_tba_passesThrough() {
            assertThat(formatter.formatCombinedEpisodeTitle(CURATED_SHOW, List.of("TBA")))
                    .isEqualTo("TBA");
        }

        @Test
        void doubleEpisode_curated_addsStructuralDouble() {
            assertThat(formatter.formatCombinedEpisodeTitle(CURATED_SHOW, List.of("T1", "T2")))
                    .isEqualTo("All Stars Double: T1, T2");
        }

        @Test
        void doubleEpisode_uncurated_unchangedLegacyFormat() {
            assertThat(formatter.formatCombinedEpisodeTitle(UNCURATED_SHOW, List.of("Part 1", "Part 2")))
                    .isEqualTo("Double Episode: Part 1, Part 2");
        }

        @Test
        void doubleEpisode_curated_constituentTbaPassesThroughInPosition() {
            // Constituent TBA inside a Double is fine — the structural marker carries meaning.
            assertThat(formatter.formatCombinedEpisodeTitle(CURATED_SHOW, List.of("Premiere", "TBA")))
                    .isEqualTo("All Stars Double: Premiere, TBA");
        }

        @Test
        void doubleEpisode_allConstituentsTba_collapsesToTbaSentinel() {
            // Hard contract: when every constituent is TBA, the combined title must remain
            // detectable as TBA so WatchPartyHostNudgeService's clean-fallback branch fires.
            assertThat(formatter.formatCombinedEpisodeTitle(CURATED_SHOW, List.of("TBA", "TBA")))
                    .isEqualTo("TBA");
        }

        @Test
        void doubleEpisode_allConstituentsTba_collapsesEvenWithoutFlavor() {
            assertThat(formatter.formatCombinedEpisodeTitle(UNCURATED_SHOW, List.of("TBA", "TBA")))
                    .isEqualTo("TBA");
        }

        @Test
        void doubleEpisode_mixedBlankAndTba_collapsesToTbaSentinel() {
            assertThat(formatter.formatCombinedEpisodeTitle(CURATED_SHOW, Arrays.asList(null, "  ", "TBA")))
                    .isEqualTo("TBA");
        }

        @Test
        void tripleEpisode_allConstituentsTba_collapsesToTbaSentinel() {
            assertThat(formatter.formatCombinedEpisodeTitle(CURATED_SHOW, List.of("TBA", "tba", "  TBA  ")))
                    .isEqualTo("TBA");
        }

        @Test
        void tripleEpisode_curated_addsPrefix() {
            assertThat(formatter.formatCombinedEpisodeTitle(CURATED_SHOW, List.of("A", "B", "C")))
                    .isEqualTo("All Stars Triple Episode");
        }

        @Test
        void tripleEpisode_uncurated_unchangedLegacyFormat() {
            assertThat(formatter.formatCombinedEpisodeTitle(UNCURATED_SHOW, List.of("A", "B", "C")))
                    .isEqualTo("Triple Episode");
        }

        @Test
        void quadrupleEpisode_curated_addsPrefix() {
            assertThat(formatter.formatCombinedEpisodeTitle(CURATED_SHOW, List.of("A", "B", "C", "D")))
                    .isEqualTo("All Stars Quadruple Episode");
        }

        @Test
        void quadrupleEpisode_uncurated_unchangedLegacyFormat() {
            assertThat(formatter.formatCombinedEpisodeTitle(UNCURATED_SHOW, List.of("A", "B", "C", "D")))
                    .isEqualTo("Quadruple Episode");
        }

        @Test
        void fivePlus_curated_addsPrefixWithCount() {
            assertThat(formatter.formatCombinedEpisodeTitle(
                    CURATED_SHOW, Arrays.asList("A", "B", "C", "D", "E")))
                    .isEqualTo("All Stars Multi-Episode (5)");
        }

        @Test
        void fivePlus_uncurated_unchangedLegacyFormat() {
            assertThat(formatter.formatCombinedEpisodeTitle(
                    UNCURATED_SHOW, Arrays.asList("A", "B", "C", "D", "E")))
                    .isEqualTo("Multi-Episode (5 episodes)");
        }

        @Test
        void emptyList_returnsNull() {
            assertThat(formatter.formatCombinedEpisodeTitle(CURATED_SHOW, Collections.emptyList())).isNull();
        }

        @Test
        void nullList_returnsNull() {
            assertThat(formatter.formatCombinedEpisodeTitle(CURATED_SHOW, null)).isNull();
        }
    }
}
