package com.bbthechange.inviter.util;

import com.bbthechange.inviter.service.ShowFlavorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Single source of truth for watch-party EPISODE titles.
 *
 * <p>This is the only sanctioned path for producing the {@code Hangout.title} string on a
 * watch-party hangout. Every write site (initial creation, NEW_EPISODE SQS handler,
 * UPDATE_TITLE SQS handler, admin backfill) MUST route through here so a curated
 * {@link com.bbthechange.inviter.model.ShowFlavor#getShortName() shortName} can be applied
 * uniformly.
 *
 * <p><b>Hard contracts (do not break):</b>
 * <ul>
 *   <li>{@code formatEpisodeTitle(showId, raw)} returns {@code raw} unchanged when
 *       {@code raw == null}, {@code raw.isBlank()}, or {@link EpisodeTitles#isTba(String)}
 *       returns true — even when a {@code shortName} exists. Downstream consumers
 *       (notably {@code WatchPartyHostNudgeService}) call {@code EpisodeTitles.isTba(title)}
 *       on the stored title; prefixing a TBA with "All Stars: " would defeat that
 *       sentinel and silently change push copy.</li>
 *   <li>Series titles ({@code EventSeries.seriesTitle}, {@code SeriesPointer.seriesTitle})
 *       are intentionally NOT formatted by this helper — the series stays the full
 *       "{showName} Season {n}" string. The short name is an episode-level prefix only.</li>
 *   <li>{@code formatCombinedEpisodeTitle} treats the structural marker (Double/Triple/
 *       Quadruple/Multi-Episode) as the surface that carries meaning — constituent
 *       titles inside a Double are passed through verbatim, including TBA.</li>
 *   <li>{@code formatCombinedEpisodeTitle} returns the canonical {@code "TBA"} sentinel
 *       when EVERY constituent is null/blank/TBA. Otherwise the host-nudge fallback
 *       branch (which keys on {@link EpisodeTitles#isTba(String)}) would miss
 *       "All Stars Double: TBA, TBA" and render the verbose default copy.</li>
 * </ul>
 */
@Component
public class WatchPartyTitleFormatter {

    private final ShowFlavorService showFlavorService;

    @Autowired
    public WatchPartyTitleFormatter(ShowFlavorService showFlavorService) {
        this.showFlavorService = showFlavorService;
    }

    /**
     * Format a single-episode hangout title. Honors null/blank/TBA pass-through.
     *
     * @param showId   TVMaze show ID, or null when unknown (treated as uncurated)
     * @param rawTitle the raw episode title from TVMaze (or as supplied by the creator)
     * @return rawTitle unchanged if uncurated/sentinel, otherwise {@code "{shortName}: {rawTitle}"}
     */
    public String formatEpisodeTitle(Integer showId, String rawTitle) {
        if (rawTitle == null || rawTitle.isBlank() || EpisodeTitles.isTba(rawTitle)) {
            return rawTitle;
        }
        Optional<String> shortName = lookupShortName(showId);
        if (shortName.isEmpty()) {
            return rawTitle;
        }
        return shortName.get() + ": " + rawTitle;
    }

    /**
     * Format the title for a combined episode hangout. The list contains the constituent
     * raw episode titles, in air order. Pass {@code [title]} for a single episode and the
     * call collapses to {@link #formatEpisodeTitle}.
     *
     * <p>Output schema (per design):
     * <pre>
     *   count = 1   with flavor    → "All Stars: How To Videos"
     *   count = 1   no flavor      → "How To Videos"
     *   count = 2   with flavor    → "All Stars Double: T1, T2"
     *   count = 2   no flavor      → "Double Episode: T1, T2"
     *   count = 3   with flavor    → "All Stars Triple Episode"
     *   count = 3   no flavor      → "Triple Episode"
     *   count = 4   with flavor    → "All Stars Quadruple Episode"
     *   count = 4   no flavor      → "Quadruple Episode"
     *   count >= 5  with flavor    → "All Stars Multi-Episode (N)"
     *   count >= 5  no flavor      → "Multi-Episode (N episodes)"
     * </pre>
     *
     * @return formatted combined title, or null if {@code titles} is null/empty.
     */
    public String formatCombinedEpisodeTitle(Integer showId, List<String> titles) {
        if (titles == null || titles.isEmpty()) {
            return null;
        }
        int count = titles.size();
        if (count == 1) {
            return formatEpisodeTitle(showId, titles.get(0));
        }

        // All-TBA pass-through: when every constituent is null/blank/TBA, collapse to the
        // canonical "TBA" sentinel so EpisodeTitles.isTba(title) still fires downstream.
        if (titles.stream().allMatch(t -> t == null || t.isBlank() || EpisodeTitles.isTba(t))) {
            return "TBA";
        }

        Optional<String> shortName = lookupShortName(showId);

        if (count == 2) {
            String constituents = nullToEmpty(titles.get(0)) + ", " + nullToEmpty(titles.get(1));
            return shortName
                    .map(sn -> sn + " Double: " + constituents)
                    .orElse("Double Episode: " + constituents);
        }
        if (count == 3) {
            return shortName.map(sn -> sn + " Triple Episode").orElse("Triple Episode");
        }
        if (count == 4) {
            return shortName.map(sn -> sn + " Quadruple Episode").orElse("Quadruple Episode");
        }
        return shortName
                .map(sn -> sn + " Multi-Episode (" + count + ")")
                .orElse("Multi-Episode (" + count + " episodes)");
    }

    private Optional<String> lookupShortName(Integer showId) {
        if (showId == null) {
            return Optional.empty();
        }
        return showFlavorService.getShortName(showId);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
