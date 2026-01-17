package com.bbthechange.inviter.dto.watchparty.sqs;

/**
 * Message indicating a show has been updated in TVMaze.
 * Triggers fetch of latest episode data and comparison with Season record.
 * May result in NEW_EPISODE, UPDATE_TITLE, or REMOVE_EPISODE messages.
 */
public class ShowUpdatedMessage extends WatchPartyMessage {

    public static final String TYPE = "SHOW_UPDATED";

    private Integer showId;

    public ShowUpdatedMessage() {
        super(TYPE);
    }

    public ShowUpdatedMessage(Integer showId) {
        super(TYPE);
        this.showId = showId;
    }

    public Integer getShowId() {
        return showId;
    }

    public void setShowId(Integer showId) {
        this.showId = showId;
    }

    @Override
    public String toString() {
        return "ShowUpdatedMessage{" +
                "showId=" + showId +
                ", messageId='" + getMessageId() + '\'' +
                '}';
    }
}
