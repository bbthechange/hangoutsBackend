package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.model.ShowFlavor;

import java.util.Optional;

/**
 * Read-only repository for {@link ShowFlavor} records. Records are written
 * offline by a curator (no in-app write path), so this repo intentionally
 * exposes lookup only.
 */
public interface ShowFlavorRepository {

    /**
     * Look up the curated flavor for a TVMaze show.
     *
     * @param showId TVMaze show ID
     * @return the flavor record if curated, otherwise empty
     */
    Optional<ShowFlavor> findByShowId(Integer showId);
}
