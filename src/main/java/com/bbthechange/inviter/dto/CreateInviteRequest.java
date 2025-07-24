package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.Invite.InviteType;
import lombok.Data;

@Data
public class CreateInviteRequest {
    private String phoneNumber;
    private InviteType type;
}