package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.EventVisibility;
import lombok.Data;

import java.util.List;

@Data
public class CreateHangoutRequest {
    private String title;
    private String description;
    private TimeInfo timeInfo; // Fuzzy time input object
    private Address location;
    private EventVisibility visibility;
    private String mainImagePath;
    private List<String> associatedGroups; // Groups to associate this hangout with
    private List<CreateAttributeRequest> attributes;
    private boolean carpoolEnabled;
    private List<CreatePollRequest> polls; // Polls to create with the hangout
}