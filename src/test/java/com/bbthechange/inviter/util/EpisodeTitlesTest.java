package com.bbthechange.inviter.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EpisodeTitlesTest {

    @Test
    void isTba_null_returnsFalse() {
        assertThat(EpisodeTitles.isTba(null)).isFalse();
    }

    @Test
    void isTba_blank_returnsFalse() {
        assertThat(EpisodeTitles.isTba("")).isFalse();
        assertThat(EpisodeTitles.isTba("   ")).isFalse();
    }

    @Test
    void isTba_exactTba_returnsTrue() {
        assertThat(EpisodeTitles.isTba("TBA")).isTrue();
    }

    @Test
    void isTba_lowercaseTba_returnsTrue() {
        assertThat(EpisodeTitles.isTba("tba")).isTrue();
    }

    @Test
    void isTba_mixedCaseTba_returnsTrue() {
        assertThat(EpisodeTitles.isTba("Tba")).isTrue();
        assertThat(EpisodeTitles.isTba("tBa")).isTrue();
    }

    @Test
    void isTba_paddedTba_returnsTrue() {
        assertThat(EpisodeTitles.isTba("  TBA  ")).isTrue();
        assertThat(EpisodeTitles.isTba("\tTBA\n")).isTrue();
    }

    @Test
    void isTba_tbd_returnsFalse() {
        // We do not speculate beyond TVMaze's documented "TBA" sentinel.
        assertThat(EpisodeTitles.isTba("tbd")).isFalse();
        assertThat(EpisodeTitles.isTba("TBD")).isFalse();
    }

    @Test
    void isTba_tbaMountain_returnsFalse() {
        // Real episode titles that happen to begin with "TBA" are NOT the sentinel.
        assertThat(EpisodeTitles.isTba("TBA Mountain")).isFalse();
    }

    @Test
    void isTba_tbaWithPeriod_returnsFalse() {
        // The ad-hoc Phase 2 detector matched "tba." — that was speculation; drop it.
        assertThat(EpisodeTitles.isTba("tba.")).isFalse();
        assertThat(EpisodeTitles.isTba("TBA.")).isFalse();
    }

    @Test
    void isTba_realEpisodeTitle_returnsFalse() {
        assertThat(EpisodeTitles.isTba("The Pilot")).isFalse();
        assertThat(EpisodeTitles.isTba("tbd productions")).isFalse();
    }
}
