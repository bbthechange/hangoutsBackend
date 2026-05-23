package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InviterKeyFactory;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

/**
 * Curated per-show metadata for TVMaze shows backing a Watch Party.
 *
 * <p>Single record per show, sibling of {@link Season} records in the same
 * {@code TVMAZE#SHOW#{showId}} partition (SK = {@code FLAVOR}). No GSI —
 * always looked up by showId.
 *
 * <p><b>Schemaless-by-design:</b> future fields (RSVP labels, push templates,
 * emoji, accent color, etc.) are added as nullable bean attributes. Existing
 * records tolerate missing attributes natively, so no migration is required.
 * V1 populates {@code shortName} only.
 *
 * <p>Records are written offline by a curator/agent — no in-app write path.
 */
@DynamoDbBean
public class ShowFlavor extends BaseItem {

    private Integer showId;        // TVMaze show ID (also encoded in pk)
    private String shortName;      // Colloquial nickname used for episode-level title prefixes
    private Long lastUpdated;      // Epoch ms when this record was last written
    private String source;         // Free-form provenance tag (e.g. "manual", "curated-2026-05")

    public ShowFlavor() {
        super();
        setItemType("SHOW_FLAVOR");
    }

    public ShowFlavor(Integer showId) {
        super();
        setItemType("SHOW_FLAVOR");
        this.showId = showId;
        setPk(InviterKeyFactory.getSeasonPk(showId));
        setSk(InviterKeyFactory.getFlavorSk());
    }

    public Integer getShowId() {
        return showId;
    }

    public void setShowId(Integer showId) {
        this.showId = showId;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
        touch();
    }

    public Long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
