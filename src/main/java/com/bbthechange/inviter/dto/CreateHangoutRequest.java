package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.EventVisibility;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class CreateHangoutRequest {
    private String title;
    private String description;
    private Map<String, String> timeInput; // Fuzzy time input object
    private Address location;
    private EventVisibility visibility;
    private String mainImagePath;
    private List<String> associatedGroups; // Groups to associate this hangout with
    private boolean carpoolEnabled;
}