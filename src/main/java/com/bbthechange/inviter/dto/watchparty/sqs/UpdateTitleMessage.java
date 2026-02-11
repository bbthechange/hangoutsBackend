package com.bbthechange.inviter.dto.watchparty.sqs;

/**
 * Message indicating an episode title has been updated.
 * Updates hangout titles where isGeneratedTitle=true.
 * Only sends push notifications if titleNotificationSent=false.
 */
public class UpdateTitleMessage extends WatchPartyMessage {

    public static final String TYPE = "UPDATE_TITLE";

    private String externalId;
    private String newTitle;

    public UpdateTitleMessage() {
        super(TYPE);
    }

    public UpdateTitleMessage(String externalId, String newTitle) {
        super(TYPE);
        this.externalId = externalId;
        this.newTitle = newTitle;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getNewTitle() {
        return newTitle;
    }

    public void setNewTitle(String newTitle) {
        this.newTitle = newTitle;
    }

    @Override
    public String toString() {
        return "UpdateTitleMessage{" +
                "externalId='" + externalId + '\'' +
                ", newTitle='" + newTitle + '\'' +
                ", messageId='" + getMessageId() + '\'' +
                '}';
    }
}
