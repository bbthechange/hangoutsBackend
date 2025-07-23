package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.Invite;
import lombok.Data;

@Data
public class EditInviteRequest {
    private Invite.InviteResponse response;
}