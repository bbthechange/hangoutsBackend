package com.bbthechange.inviter.dto;

import jakarta.validation.constraints.Size;

/**
 * Request DTO for adding a new option to an existing poll.
 *
 * For LOCATION/DESCRIPTION polls callers send {@code text}. For TIME polls callers send
 * {@code timeInput}; the server generates a human-readable {@code text} from it. Exactly one
 * of the two must be present; the service layer enforces the choice per poll attributeType.
 */
public class AddPollOptionRequest {

    @Size(min = 1, max = 100, message = "Option text must be between 1 and 100 characters")
    private String text;

    private TimeInfo timeInput;

    public AddPollOptionRequest() {}

    public AddPollOptionRequest(String text) {
        this.text = text;
    }

    public AddPollOptionRequest(String text, TimeInfo timeInput) {
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
