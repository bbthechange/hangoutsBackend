package com.bbthechange.inviter.service;

import com.bbthechange.inviter.model.ShowFlavor;
import com.bbthechange.inviter.repository.ShowFlavorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Read-through service exposing curated {@link ShowFlavor} metadata, cached via Caffeine.
 *
 * <p>Lookup contract: a missing flavor, a repository error, and a null/invalid show ID all
 * collapse to {@link Optional#empty()}. Callers never see a thrown exception — "no flavor"
 * is the only failure mode they have to handle, which keeps the formatter fallback paths
 * uniform.
 */
@Service
public class ShowFlavorService {

    private static final Logger logger = LoggerFactory.getLogger(ShowFlavorService.class);

    private final ShowFlavorRepository repository;

    @Autowired
    public ShowFlavorService(ShowFlavorRepository repository) {
        this.repository = repository;
    }

    /**
     * Return the curated flavor record for a show, or empty if not curated.
     * Cached in the "showFlavors" Caffeine cache (60-minute TTL, see {@code CacheConfig}).
     */
    @Cacheable(value = "showFlavors", key = "#showId", condition = "#showId != null")
    public Optional<ShowFlavor> getFlavor(Integer showId) {
        // Null short-circuit: also enforced by @Cacheable condition above so the SpEL key
        // expression never evaluates on a null. Belt-and-suspenders — the service contract
        // is "null collapses to Optional.empty()".
        if (showId == null) {
            return Optional.empty();
        }
        try {
            return repository.findByShowId(showId);
        } catch (RuntimeException e) {
            logger.warn("ShowFlavor lookup failed for showId {} — treating as uncurated", showId, e);
            return Optional.empty();
        }
    }

    /**
     * Convenience accessor for the {@code shortName} field.
     */
    public Optional<String> getShortName(Integer showId) {
        return getFlavor(showId)
                .map(ShowFlavor::getShortName)
                .filter(s -> s != null && !s.isBlank());
    }

    /**
     * Returns a compact, user-facing show name. Uses the curated {@code shortName}
     * when present; otherwise derives a short form from {@code fallbackTitle} by
     * stripping a trailing " Season N". Returns "Show" if both are unusable.
     */
    public String resolveShortName(Integer showId, String fallbackTitle) {
        return getShortName(showId).orElseGet(() -> deriveShortShowName(fallbackTitle));
    }

    /**
     * Strip a trailing " Season N" (case-insensitive) from a series title so it
     * can stand in as a short show name when the curated flavor is missing.
     * Falls back to "Show" if the input is null/blank.
     */
    public static String deriveShortShowName(String fullTitle) {
        if (fullTitle == null || fullTitle.isBlank()) {
            return "Show";
        }
        return fullTitle.replaceAll("(?i)\\s+Season\\s+\\d+\\s*$", "").trim();
    }
}
