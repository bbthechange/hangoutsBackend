package com.bbthechange.inviter.dto;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;

@DynamoDbBean
public class TimeInput {
    private String periodGranularity;
    private String periodStart; // ISO 8601 with timezone
    private String startTime;   // ISO 8601 with timezone  
    private String endTime;     // ISO 8601 with timezone
    
    public TimeInput() {}
    
    public TimeInput(String periodGranularity, String periodStart, String startTime, String endTime) {
        this.periodGranularity = periodGranularity;
        this.periodStart = periodStart;
        this.startTime = startTime;
        this.endTime = endTime;
    }
    
    @DynamoDbAttribute("periodGranularity")
    public String getPeriodGranularity() { return periodGranularity; }
    public void setPeriodGranularity(String periodGranularity) { this.periodGranularity = periodGranularity; }
    
    @DynamoDbAttribute("periodStart")
    public String getPeriodStart() { return periodStart; }
    public void setPeriodStart(String periodStart) { this.periodStart = periodStart; }
    
    @DynamoDbAttribute("startTime")
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    
    @DynamoDbAttribute("endTime")
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
}