package com.bbthechange.inviter.dto;

import com.bbthechange.inviter.model.EventVisibility;
import lombok.Data;

@Data
public class UpdateHangoutRequest {
    private String title;
    private String description;
    private TimeInfo timeInfo; // Fuzzy time input object
    private Address location;
    private EventVisibility visibility;
    private String mainImagePath;
    private boolean carpoolEnabled;

    // Ticket/participation coordination fields
    private String ticketLink;
    private Boolean ticketsRequired;
    private String discountCode;

    // External source fields
    private String externalId;          // ID from external source (Ticketmaster, Yelp, etc.)
    private String externalSource;      // Source system name
    private Boolean isGeneratedTitle;   // Whether title was auto-generated
}