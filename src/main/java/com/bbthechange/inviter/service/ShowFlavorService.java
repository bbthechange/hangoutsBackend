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
    @Cacheable(value = "showFlavors", key = "#showId.toString()")
    public Optional<ShowFlavor> getFlavor(Integer showId) {
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
}
