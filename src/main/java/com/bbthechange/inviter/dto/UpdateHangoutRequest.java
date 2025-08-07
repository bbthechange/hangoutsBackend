package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.EventVisibility;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class UpdateHangoutRequest {
    private String title;
    private String description;
    private Map<String, String> timeInput; // Fuzzy time input object
    private Address location;
    private EventVisibility visibility;
    private String mainImagePath;
    private boolean carpoolEnabled;
}