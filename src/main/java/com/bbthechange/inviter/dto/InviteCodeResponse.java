package com.bbthechange.inviter.dto;

import lombok.Data;

/**
 * Response DTO for invite code generation.
 * Contains the generated code and shareable URL.
 */
@Data
public class InviteCodeResponse {

    private String inviteCode;
    private String shareUrl;

    public InviteCodeResponse() {}

    public InviteCodeResponse(String inviteCode, String shareUrl) {
        this.inviteCode = inviteCode;
        this.shareUrl = shareUrl;
    }
}
