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
}
