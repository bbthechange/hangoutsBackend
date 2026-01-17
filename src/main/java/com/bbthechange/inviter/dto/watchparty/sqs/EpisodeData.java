package com.bbthechange.inviter.dto.watchparty.sqs;

import java.util.List;

/**
 * Embedded episode data for NEW_EPISODE messages.
 * Contains all information needed to create a hangout for the episode.
 */
public class EpisodeData {

    private Integer episodeId;
    private Integer episodeNumber;
    private String title;
    private Long airTimestamp;
    private Integer runtime;
    private List<Integer> combinedWith;

    public EpisodeData() {}

    public EpisodeData(Integer episodeId, String title, Long airTimestamp) {
        this.episodeId = episodeId;
        this.title = title;
        this.airTimestamp = airTimestamp;
    }

    public Integer getEpisodeId() {
        return episodeId;
    }

    public void setEpisodeId(Integer episodeId) {
        this.episodeId = episodeId;
    }

    public Integer getEpisodeNumber() {
        return episodeNumber;
    }

    public void setEpisodeNumber(Integer episodeNumber) {
        this.episodeNumber = episodeNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getAirTimestamp() {
        return airTimestamp;
    }

    public void setAirTimestamp(Long airTimestamp) {
        this.airTimestamp = airTimestamp;
    }

    public Integer getRuntime() {
        return runtime;
    }

    public void setRuntime(Integer runtime) {
        this.runtime = runtime;
    }

    public List<Integer> getCombinedWith() {
        return combinedWith;
    }

    public void setCombinedWith(List<Integer> combinedWith) {
        this.combinedWith = combinedWith;
    }

    @Override
    public String toString() {
        return "EpisodeData{" +
                "episodeId=" + episodeId +
                ", title='" + title + '\'' +
                ", airTimestamp=" + airTimestamp +
                ", runtime=" + runtime +
                '}';
    }
}
