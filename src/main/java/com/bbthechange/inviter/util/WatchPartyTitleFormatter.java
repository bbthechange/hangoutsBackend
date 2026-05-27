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
 * UPDATE_TITLE SQS handler, admin backfill) MUST route through here so the show-context
 * prefix is applied uniformly.
 *
 * <p><b>Display-name precedence:</b> {@code ShowFlavor.shortName} when present, otherwise
 * the supplied {@code showName} (denormalized on {@code Season.showName}). Only when both
 * are missing/blank does the formatter fall back to the bare body — the long-standing
 * "uncurated" form that left episode titles decontextualized. In practice every Season
 * row carries {@code showName} so the bare-body branch is just a safety net for tests /
 * legacy callers.
 *
 * <p>The separator between display name and episode body is the mid-dot {@code " · "}
 * (U+00B7). This visually disambiguates from colons that may appear inside show names
 * (e.g. "RuPaul's Drag Race: All Stars · How To Videos"). Inside the structural marker
 * for multi-episode hangouts a regular colon is still used because it expresses
 * containment, not metadata ("Double Episode: T1, T2").
 *
 * <p><b>Hard contracts (do not break):</b>
 * <ul>
 *   <li>{@code formatEpisodeTitle(showId, showName, raw)} returns {@code raw} unchanged when
 *       {@code raw == null}, {@code raw.isBlank()}, or {@link EpisodeTitles#isTba(String)}
 *       returns true — even when a display name is available. Downstream consumers
 *       (notably {@code WatchPartyHostNudgeService}) call {@code EpisodeTitles.isTba(title)}
 *       on the stored title; prefixing a TBA with "All Stars · " would defeat that
 *       sentinel and silently change push copy.</li>
 *   <li>Series titles ({@code EventSeries.seriesTitle}, {@code SeriesPointer.seriesTitle})
 *       are intentionally NOT formatted by this helper — the series stays the full
 *       "{showName} Season {n}" string. The display name is an episode-level prefix only.</li>
 *   <li>{@code formatCombinedEpisodeTitle} treats the structural marker (Double/Triple/
 *       Quadruple/Multi-Episode) as the surface that carries meaning — constituent
 *       titles inside a Double are passed through verbatim, including TBA.</li>
 *   <li>{@code formatCombinedEpisodeTitle} returns the canonical {@code "TBA"} sentinel
 *       when EVERY constituent is null/blank/TBA. Otherwise the host-nudge fallback
 *       branch (which keys on {@link EpisodeTitles#isTba(String)}) would miss
 *       "All Stars · Double Episode: TBA, TBA" and render the verbose default copy.</li>
 * </ul>
 */
@Component
public class WatchPartyTitleFormatter {

    /** Mid-dot (U+00B7) framed by single spaces. */
    private static final String SEPARATOR = " · ";

    private final ShowFlavorService showFlavorService;

    @Autowired
    public WatchPartyTitleFormatter(ShowFlavorService showFlavorService) {
        this.showFlavorService = showFlavorService;
    }

    /**
     * Format a single-episode hangout title. Honors null/blank/TBA pass-through.
     *
     * @param showId   TVMaze show ID, or null when unknown
     * @param showName denormalized show name from {@code Season.showName}, used when no
     *                 {@code ShowFlavor.shortName} exists for {@code showId}
     * @param rawTitle the raw episode title from TVMaze (or as supplied by the creator)
     * @return rawTitle unchanged if sentinel; otherwise {@code "{displayName} · {rawTitle}"}
     *         using shortName→showName precedence; bare rawTitle when neither resolves.
     */
    public String formatEpisodeTitle(Integer showId, String showName, String rawTitle) {
        if (rawTitle == null || rawTitle.isBlank() || EpisodeTitles.isTba(rawTitle)) {
            return rawTitle;
        }
        String displayName = resolveDisplayName(showId, showName);
        return prefixWith(displayName, rawTitle);
    }

    /**
     * Format the title for a combined episode hangout. The list contains the constituent
     * raw episode titles, in air order. Pass {@code [title]} for a single episode and the
     * call collapses to {@link #formatEpisodeTitle}.
     *
     * <p>Output schema:
     * <pre>
     *   count = 1                  → "{displayName} · {title}"        (or "{title}" if no displayName)
     *   count = 2                  → "{displayName} · Double Episode: T1, T2"
     *   count = 3                  → "{displayName} · Triple Episode"
     *   count = 4                  → "{displayName} · Quadruple Episode"
     *   count >= 5                 → "{displayName} · Multi-Episode (N)"
     *   all constituents TBA/blank → "TBA"
     * </pre>
     *
     * @return formatted combined title, or null if {@code titles} is null/empty.
     */
    public String formatCombinedEpisodeTitle(Integer showId, String showName, List<String> titles) {
        if (titles == null || titles.isEmpty()) {
            return null;
        }
        int count = titles.size();
        if (count == 1) {
            return formatEpisodeTitle(showId, showName, titles.get(0));
        }

        // All-TBA pass-through: when every constituent is null/blank/TBA, collapse to the
        // canonical "TBA" sentinel so EpisodeTitles.isTba(title) still fires downstream.
        if (titles.stream().allMatch(t -> t == null || t.isBlank() || EpisodeTitles.isTba(t))) {
            return "TBA";
        }

        String body;
        if (count == 2) {
            body = "Double Episode: " + nullToEmpty(titles.get(0)) + ", " + nullToEmpty(titles.get(1));
        } else if (count == 3) {
            body = "Triple Episode";
        } else if (count == 4) {
            body = "Quadruple Episode";
        } else {
            body = "Multi-Episode (" + count + ")";
        }

        return prefixWith(resolveDisplayName(showId, showName), body);
    }

    private String resolveDisplayName(Integer showId, String showName) {
        Optional<String> shortName = lookupShortName(showId);
        if (shortName.isPresent()) {
            return shortName.get();
        }
        if (showName != null && !showName.isBlank()) {
            return showName;
        }
        return null;
    }

    private Optional<String> lookupShortName(Integer showId) {
        if (showId == null) {
            return Optional.empty();
        }
        return showFlavorService.getShortName(showId);
    }

    private static String prefixWith(String displayName, String body) {
        if (displayName == null) {
            return body;
        }
        return displayName + SEPARATOR + body;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
