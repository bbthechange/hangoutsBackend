package com.bbthechange.inviter.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for voting on an attribute proposal or one of its alternatives.
 *
 * Index 0 votes for the original proposedValue.
 * Index 1..N vote for alternatives.get(index - 1).
 */
public class ProposalVoteRequest {

    @NotNull(message = "optionIndex is required")
    @Min(value = 0, message = "optionIndex must be >= 0")
    private Integer optionIndex;

    public Integer getOptionIndex() {
        return optionIndex;
    }

    public void setOptionIndex(Integer optionIndex) {
        this.optionIndex = optionIndex;
    }
}
