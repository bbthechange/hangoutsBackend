package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.EventVisibility;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreateEventWithInvitesRequest {
    private String name;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Address location;
    private EventVisibility visibility;
    private String mainImagePath;
    private List<CreateInviteRequest> invites;
}