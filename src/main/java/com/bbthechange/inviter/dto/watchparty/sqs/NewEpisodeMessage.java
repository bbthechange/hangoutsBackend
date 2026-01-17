package com.bbthechange.inviter.dto.watchparty.sqs;

/**
 * Message indicating a new episode has been detected.
 * Triggers creation of hangouts for all groups watching this season.
 */
public class NewEpisodeMessage extends WatchPartyMessage {

    public static final String TYPE = "NEW_EPISODE";

    private String seasonKey;
    private EpisodeData episode;

    public NewEpisodeMessage() {
        super(TYPE);
    }

    public NewEpisodeMessage(String seasonKey, EpisodeData episode) {
        super(TYPE);
        this.seasonKey = seasonKey;
        this.episode = episode;
    }

    public String getSeasonKey() {
        return seasonKey;
    }

    public void setSeasonKey(String seasonKey) {
        this.seasonKey = seasonKey;
    }

    public EpisodeData getEpisode() {
        return episode;
    }

    public void setEpisode(EpisodeData episode) {
        this.episode = episode;
    }

    @Override
    public String toString() {
        return "NewEpisodeMessage{" +
                "seasonKey='" + seasonKey + '\'' +
                ", episode=" + episode +
                ", messageId='" + getMessageId() + '\'' +
                '}';
    }
}
