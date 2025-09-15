package com.bbthechange.inviter.testutil;

import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.model.EventVisibility;
import com.bbthechange.inviter.dto.Address;
import com.bbthechange.inviter.dto.TimeInfo;
import com.bbthechange.inviter.util.InviterKeyFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class HangoutTestBuilder {
    
    private String hangoutId;
    private String title;
    private String description;
    private List<String> associatedGroups;
    private TimeInfo timeInput;
    private Address location;
    private EventVisibility visibility;
    private String mainImagePath;
    private boolean carpoolEnabled;
    private String seriesId;
    private Long startTimestamp;
    private Long endTimestamp;
    
    private HangoutTestBuilder() {
        // Default values
        this.hangoutId = UUID.randomUUID().toString();
        this.title = "Test Hangout";
        this.description = "Test Description";
        this.associatedGroups = new ArrayList<>();
        this.timeInput = new TimeInfo();
        this.location = new Address();
        this.visibility = EventVisibility.PUBLIC;
        this.mainImagePath = "test/image.jpg";
        this.carpoolEnabled = false;
    }
    
    public static HangoutTestBuilder aHangout() {
        return new HangoutTestBuilder();
    }
    
    public HangoutTestBuilder withId(String hangoutId) {
        this.hangoutId = hangoutId;
        return this;
    }
    
    public HangoutTestBuilder withTitle(String title) {
        this.title = title;
        return this;
    }
    
    public HangoutTestBuilder withDescription(String description) {
        this.description = description;
        return this;
    }
    
    public HangoutTestBuilder withGroups(String... groupIds) {
        this.associatedGroups = Arrays.asList(groupIds);
        return this;
    }
    
    public HangoutTestBuilder withGroups(List<String> groupIds) {
        this.associatedGroups = new ArrayList<>(groupIds);
        return this;
    }
    
    public HangoutTestBuilder withTimeInput(TimeInfo timeInput) {
        this.timeInput = timeInput;
        return this;
    }
    
    public HangoutTestBuilder withLocation(Address location) {
        this.location = location;
        return this;
    }
    
    public HangoutTestBuilder withVisibility(EventVisibility visibility) {
        this.visibility = visibility;
        return this;
    }
    
    public HangoutTestBuilder withMainImagePath(String mainImagePath) {
        this.mainImagePath = mainImagePath;
        return this;
    }
    
    public HangoutTestBuilder withCarpoolEnabled(boolean carpoolEnabled) {
        this.carpoolEnabled = carpoolEnabled;
        return this;
    }
    
    public HangoutTestBuilder withSeriesId(String seriesId) {
        this.seriesId = seriesId;
        return this;
    }
    
    public HangoutTestBuilder withStartTimestamp(Long startTimestamp) {
        this.startTimestamp = startTimestamp;
        return this;
    }
    
    public HangoutTestBuilder withEndTimestamp(Long endTimestamp) {
        this.endTimestamp = endTimestamp;
        return this;
    }
    
    public Hangout build() {
        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setTitle(title);
        hangout.setDescription(description);
        hangout.setAssociatedGroups(associatedGroups);
        hangout.setTimeInput(timeInput);
        hangout.setLocation(location);
        hangout.setVisibility(visibility);
        hangout.setMainImagePath(mainImagePath);
        hangout.setCarpoolEnabled(carpoolEnabled);
        hangout.setSeriesId(seriesId);
        hangout.setStartTimestamp(startTimestamp);
        hangout.setEndTimestamp(endTimestamp);
        
        // Set DynamoDB keys
        hangout.setPk(InviterKeyFactory.getEventPk(hangoutId));
        hangout.setSk(InviterKeyFactory.getMetadataSk());
        
        return hangout;
    }
}