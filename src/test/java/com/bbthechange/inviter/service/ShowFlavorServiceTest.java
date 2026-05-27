package com.bbthechange.inviter.service;

import com.bbthechange.inviter.model.ShowFlavor;
import com.bbthechange.inviter.repository.ShowFlavorRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShowFlavorServiceTest {

    @Mock private ShowFlavorRepository repository;
    @InjectMocks private ShowFlavorService service;

    @Nested
    class GetFlavor {

        @Test
        void nullShowId_ReturnsEmptyWithoutHittingRepository() {
            // Locks the documented contract: null collapses to empty without I/O.
            // Also guards the @Cacheable(condition="#showId != null") contract — if
            // someone ever reverts the condition, the SpEL key would NPE before the
            // method body and this test would fail.
            Optional<ShowFlavor> result = service.getFlavor(null);

            assertThat(result).isEmpty();
            verify(repository, never()).findByShowId(anyInt());
        }

        @Test
        void repositoryReturnsEmpty_ReturnsEmpty() {
            when(repository.findByShowId(4596)).thenReturn(Optional.empty());

            Optional<ShowFlavor> result = service.getFlavor(4596);

            assertThat(result).isEmpty();
        }

        @Test
        void repositoryReturnsFlavor_ReturnsIt() {
            ShowFlavor flavor = new ShowFlavor(4596);
            flavor.setShortName("All Stars");
            when(repository.findByShowId(4596)).thenReturn(Optional.of(flavor));

            Optional<ShowFlavor> result = service.getFlavor(4596);

            assertThat(result).isPresent();
            assertThat(result.get().getShortName()).isEqualTo("All Stars");
        }

        @Test
        void repositoryThrows_CollapsesToEmpty() {
            // Documented contract: repository exceptions never propagate. Treating
            // a DDB blip as "uncurated" keeps the formatter fallback uniform.
            when(repository.findByShowId(4596)).thenThrow(new RuntimeException("DDB unavailable"));

            Optional<ShowFlavor> result = service.getFlavor(4596);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class GetShortName {

        @Test
        void noFlavor_ReturnsEmpty() {
            when(repository.findByShowId(4596)).thenReturn(Optional.empty());

            Optional<String> result = service.getShortName(4596);

            assertThat(result).isEmpty();
        }

        @Test
        void flavorWithShortName_ReturnsName() {
            ShowFlavor flavor = new ShowFlavor(4596);
            flavor.setShortName("All Stars");
            when(repository.findByShowId(4596)).thenReturn(Optional.of(flavor));

            Optional<String> result = service.getShortName(4596);

            assertThat(result).hasValue("All Stars");
        }

        @Test
        void flavorWithNullShortName_ReturnsEmpty() {
            // Defensive: a flavor record might exist with shortName not yet populated
            // (future fields may justify the row before a curator picks a nickname).
            ShowFlavor flavor = new ShowFlavor(4596);
            flavor.setShortName(null);
            when(repository.findByShowId(4596)).thenReturn(Optional.of(flavor));

            Optional<String> result = service.getShortName(4596);

            assertThat(result).isEmpty();
        }

        @Test
        void flavorWithBlankShortName_ReturnsEmpty() {
            ShowFlavor flavor = new ShowFlavor(4596);
            flavor.setShortName("   ");
            when(repository.findByShowId(4596)).thenReturn(Optional.of(flavor));

            Optional<String> result = service.getShortName(4596);

            assertThat(result).isEmpty();
        }

        @Test
        void nullShowId_ReturnsEmpty() {
            Optional<String> result = service.getShortName(null);

            assertThat(result).isEmpty();
            verify(repository, never()).findByShowId(anyInt());
        }
    }

    @Nested
    class ResolveShortName {

        @Test
        void flavorPresent_returnsCuratedShortName() {
            ShowFlavor flavor = new ShowFlavor(4596);
            flavor.setShortName("All Stars");
            when(repository.findByShowId(4596)).thenReturn(Optional.of(flavor));

            String result = service.resolveShortName(4596, "RuPaul's Drag Race: All Stars Season 11");

            assertThat(result).isEqualTo("All Stars");
        }

        @Test
        void flavorAbsent_stripsTrailingSeasonNumberFromFallback() {
            when(repository.findByShowId(4596)).thenReturn(Optional.empty());

            String result = service.resolveShortName(4596, "Survivor Season 47");

            assertThat(result).isEqualTo("Survivor");
        }

        @Test
        void flavorAbsent_andFallbackHasNoSeasonSuffix_returnsFallbackAsIs() {
            when(repository.findByShowId(4596)).thenReturn(Optional.empty());

            String result = service.resolveShortName(4596, "My Show");

            assertThat(result).isEqualTo("My Show");
        }

        @Test
        void nullShowIdAndNullFallback_returnsShowSentinel() {
            String result = service.resolveShortName(null, null);

            assertThat(result).isEqualTo("Show");
            verify(repository, never()).findByShowId(anyInt());
        }

        @Test
        void nullShowId_derivesFromFallback() {
            String result = service.resolveShortName(null, "RuPaul's Drag Race: All Stars Season 11");

            assertThat(result).isEqualTo("RuPaul's Drag Race: All Stars");
        }

        @Test
        void seasonStripper_isCaseInsensitive() {
            assertThat(ShowFlavorService.deriveShortShowName("Foo SEASON 3")).isEqualTo("Foo");
            assertThat(ShowFlavorService.deriveShortShowName("Foo season 3")).isEqualTo("Foo");
            assertThat(ShowFlavorService.deriveShortShowName("Foo  Season  3  ")).isEqualTo("Foo");
        }

        @Test
        void seasonStripper_doesNotMatchMidString() {
            // "Season 4" in the middle of a title is left alone — only trailing matches strip.
            assertThat(ShowFlavorService.deriveShortShowName("Show Season 4 Special"))
                .isEqualTo("Show Season 4 Special");
        }

        @Test
        void seasonStripper_blankReturnsShowSentinel() {
            assertThat(ShowFlavorService.deriveShortShowName(null)).isEqualTo("Show");
            assertThat(ShowFlavorService.deriveShortShowName("")).isEqualTo("Show");
            assertThat(ShowFlavorService.deriveShortShowName("   ")).isEqualTo("Show");
        }
    }
}
