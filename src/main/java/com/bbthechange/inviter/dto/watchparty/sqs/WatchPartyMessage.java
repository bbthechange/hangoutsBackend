package com.bbthechange.inviter.dto.watchparty.sqs;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Base class for all Watch Party SQS messages.
 * Uses Jackson polymorphic type handling for deserialization.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = ShowUpdatedMessage.class, name = "SHOW_UPDATED"),
    @JsonSubTypes.Type(value = NewEpisodeMessage.class, name = "NEW_EPISODE"),
    @JsonSubTypes.Type(value = UpdateTitleMessage.class, name = "UPDATE_TITLE"),
    @JsonSubTypes.Type(value = RemoveEpisodeMessage.class, name = "REMOVE_EPISODE")
})
public abstract class WatchPartyMessage {

    private String type;
    private String messageId;

    public WatchPartyMessage() {}

    public WatchPartyMessage(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
}
