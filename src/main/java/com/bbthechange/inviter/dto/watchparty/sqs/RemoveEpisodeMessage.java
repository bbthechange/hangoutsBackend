package com.bbthechange.inviter.dto.watchparty.sqs;

/**
 * Message indicating an episode has been removed from the schedule.
 * Triggers deletion of associated hangout and notification to users.
 */
public class RemoveEpisodeMessage extends WatchPartyMessage {

    public static final String TYPE = "REMOVE_EPISODE";

    private String externalId;

    public RemoveEpisodeMessage() {
        super(TYPE);
    }

    public RemoveEpisodeMessage(String externalId) {
        super(TYPE);
        this.externalId = externalId;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    @Override
    public String toString() {
        return "RemoveEpisodeMessage{" +
                "externalId='" + externalId + '\'' +
                ", messageId='" + getMessageId() + '\'' +
                '}';
    }
}
