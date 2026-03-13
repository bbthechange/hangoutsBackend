package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.NudgeType;

/**
 * DTO for an action-oriented nudge shown to users on hangout cards and detail views.
 * Nudges are computed server-side on each request — never stored in the database.
 */
public class NudgeDTO {
    private NudgeType type;
    private String message;
    private String actionUrl; // Optional deep-link URL for the action

    public NudgeDTO() {}

    public NudgeDTO(NudgeType type, String message, String actionUrl) {
        this.type = type;
        this.message = message;
        this.actionUrl = actionUrl;
    }

    public NudgeType getType() {
        return type;
    }

    public void setType(NudgeType type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getActionUrl() {
        return actionUrl;
    }

    public void setActionUrl(String actionUrl) {
        this.actionUrl = actionUrl;
    }
}
