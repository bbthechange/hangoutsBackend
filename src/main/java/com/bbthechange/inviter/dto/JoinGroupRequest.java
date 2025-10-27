package com.bbthechange.inviter.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request DTO for joining a group via invite code.
 */
@Data
public class JoinGroupRequest {

    @NotBlank(message = "Invite code is required")
    private String inviteCode;

    public JoinGroupRequest() {}

    public JoinGroupRequest(String inviteCode) {
        this.inviteCode = inviteCode;
    }
}
