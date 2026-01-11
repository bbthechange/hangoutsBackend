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

    // Ticket-related fields
    private String ticketLink;          // URL to ticket purchase page
    private Boolean ticketsRequired;    // Are tickets mandatory?
    private String discountCode;        // Optional discount code

    // External source fields
    private String externalId;          // ID from external source (Ticketmaster, Yelp, etc.)
    private String externalSource;      // Source system name
    private Boolean isGeneratedTitle;   // Whether title was auto-generated (defaults to false)
}