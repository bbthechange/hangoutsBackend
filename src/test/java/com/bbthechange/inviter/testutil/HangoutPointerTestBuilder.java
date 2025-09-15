package com.bbthechange.inviter.testutil;

import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.dto.TimeInfo;
import com.bbthechange.inviter.util.InviterKeyFactory;

import java.time.Instant;

public class HangoutPointerTestBuilder {
    
    private String groupId;
    private String hangoutId;
    private String title;
    private String status;
    private Instant hangoutTime;
    private String locationName;
    private int participantCount;
    private TimeInfo timeInput;
    private Long startTimestamp;
    private Long endTimestamp;
    private String seriesId;
    
    private HangoutPointerTestBuilder() {
        // Default values
        this.groupId = "group-1";
        this.hangoutId = "hangout-1";
        this.title = "Test Hangout";
        this.status = "ACTIVE";
        this.hangoutTime = Instant.now();
        this.locationName = "Test Location";
        this.participantCount = 1;
        this.timeInput = new TimeInfo();
    }
    
    public static HangoutPointerTestBuilder aPointer() {
        return new HangoutPointerTestBuilder();
    }
    
    public HangoutPointerTestBuilder forGroup(String groupId) {
        this.groupId = groupId;
        return this;
    }
    
    public HangoutPointerTestBuilder forHangout(String hangoutId) {
        this.hangoutId = hangoutId;
        return this;
    }
    
    public HangoutPointerTestBuilder withTitle(String title) {
        this.title = title;
        return this;
    }
    
    public HangoutPointerTestBuilder withStatus(String status) {
        this.status = status;
        return this;
    }
    
    public HangoutPointerTestBuilder withHangoutTime(Instant hangoutTime) {
        this.hangoutTime = hangoutTime;
        return this;
    }
    
    public HangoutPointerTestBuilder withLocationName(String locationName) {
        this.locationName = locationName;
        return this;
    }
    
    public HangoutPointerTestBuilder withParticipantCount(int participantCount) {
        this.participantCount = participantCount;
        return this;
    }
    
    public HangoutPointerTestBuilder withTimeInput(TimeInfo timeInput) {
        this.timeInput = timeInput;
        return this;
    }
    
    public HangoutPointerTestBuilder withStartTimestamp(Long startTimestamp) {
        this.startTimestamp = startTimestamp;
        return this;
    }
    
    public HangoutPointerTestBuilder withEndTimestamp(Long endTimestamp) {
        this.endTimestamp = endTimestamp;
        return this;
    }
    
    public HangoutPointerTestBuilder withSeriesId(String seriesId) {
        this.seriesId = seriesId;
        return this;
    }
    
    public HangoutPointer build() {
        HangoutPointer pointer = new HangoutPointer(groupId, hangoutId, title);
        pointer.setStatus(status);
        pointer.setHangoutTime(hangoutTime);
        pointer.setLocationName(locationName);
        pointer.setParticipantCount(participantCount);
        pointer.setTimeInput(timeInput);
        pointer.setStartTimestamp(startTimestamp);
        pointer.setEndTimestamp(endTimestamp);
        pointer.setSeriesId(seriesId);
        
        // Set DynamoDB keys
        pointer.setPk(InviterKeyFactory.getGroupPk(groupId));
        pointer.setSk(InviterKeyFactory.getHangoutSk(hangoutId));
        pointer.setGsi1pk(InviterKeyFactory.getGroupPk(groupId));
        
        return pointer;
    }
}