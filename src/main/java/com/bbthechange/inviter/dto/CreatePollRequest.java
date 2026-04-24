package com.bbthechange.inviter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

/**
 * Request DTO for creating a new poll.
 *
 * {@code options} is polymorphic at the wire level: legacy callers send a
 * {@code List<String>} (LOCATION/DESCRIPTION), newer callers (and all TIME polls) send a
 * {@code List<PollOptionInput>}. Both shapes land in {@link #options} thanks to
 * {@link PollOptionInputDeserializer}.
 */
public class CreatePollRequest {

    @NotBlank(message = "Poll title is required")
    @Size(min = 1, max = 200, message = "Poll title must be between 1 and 200 characters")
    private String title;

    @Size(max = 1000, message = "Poll description cannot exceed 1000 characters")
    private String description;

    private boolean multipleChoice = false;

    private List<PollOptionInput> options;

    private String attributeType; // nullable: "LOCATION", "DESCRIPTION", "TIME"

    public CreatePollRequest() {}

    public CreatePollRequest(String title, String description, boolean multipleChoice, List<String> options) {
        this.title = title;
        this.description = description;
        this.multipleChoice = multipleChoice;
        setOptionsFromStrings(options);
    }

    public String getTitle() {
        return title != null ? title.trim() : null;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description != null ? description.trim() : null;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isMultipleChoice() {
        return multipleChoice;
    }

    public void setMultipleChoice(boolean multipleChoice) {
        this.multipleChoice = multipleChoice;
    }

    public List<PollOptionInput> getOptions() {
        return options;
    }

    public void setOptions(List<PollOptionInput> options) {
        this.options = options;
    }

    /** Test / legacy-caller convenience: build options from a plain string list. */
    public void setOptionsFromStrings(List<String> stringOptions) {
        if (stringOptions == null) {
            this.options = null;
            return;
        }
        List<PollOptionInput> inputs = new ArrayList<>(stringOptions.size());
        for (String s : stringOptions) {
            inputs.add(new PollOptionInput(s));
        }
        this.options = inputs;
    }

    public String getAttributeType() {
        return attributeType;
    }

    public void setAttributeType(String attributeType) {
        this.attributeType = attributeType;
    }
}
