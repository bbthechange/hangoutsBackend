package com.bbthechange.inviter.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Wire shape for a single option when creating a poll.
 *
 * Legacy clients send plain strings (`["Pizza", "Tacos"]`); newer clients — and any TIME poll —
 * send objects (`[{"text": "...", "timeInput": {...}}]`). Both shapes deserialize to this class
 * via {@link PollOptionInputDeserializer}.
 */
@JsonDeserialize(using = PollOptionInputDeserializer.class)
public class PollOptionInput {
    private String text;
    private TimeInfo timeInput;

    public PollOptionInput() {}

    public PollOptionInput(String text) {
        this.text = text;
    }

    public PollOptionInput(String text, TimeInfo timeInput) {
        this.text = text;
        this.timeInput = timeInput;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public TimeInfo getTimeInput() {
        return timeInput;
    }

    public void setTimeInput(TimeInfo timeInput) {
        this.timeInput = timeInput;
    }
}
