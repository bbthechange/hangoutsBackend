package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.Invite;
import lombok.Data;

import java.util.UUID;

@Data
public class InviteResponse {
    private UUID id;
    private UUID eventId;
    private UUID userId;
    private String userPhoneNumber;
    private String username;
    private String displayName;
    private Invite.InviteResponse response;
    private Boolean eventPassed;
    
    public InviteResponse(UUID id, UUID eventId, UUID userId, String userPhoneNumber, 
                         String username, String displayName, Invite.InviteResponse response, Boolean eventPassed) {
        this.id = id;
        this.eventId = eventId;
        this.userId = userId;
        this.userPhoneNumber = userPhoneNumber;
        this.username = username;
        this.displayName = displayName;
        this.response = response;
        this.eventPassed = eventPassed;
    }
}