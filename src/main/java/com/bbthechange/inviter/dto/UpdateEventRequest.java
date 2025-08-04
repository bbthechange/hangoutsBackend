package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.EventVisibility;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class UpdateEventRequest {
    private String name;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Address location;
    private EventVisibility visibility;
    private String mainImagePath;
    private List<String> associatedGroups; // Groups this hangout/event is associated with
    private Boolean carpoolEnabled; // Whether carpooling features are enabled
}