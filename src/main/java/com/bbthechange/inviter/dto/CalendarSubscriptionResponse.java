package com.bbthechange.inviter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Response DTO for calendar subscription operations.
 * Contains subscription details and URLs for subscribing to a group's calendar.
 */
public class CalendarSubscriptionResponse {

    @JsonProperty("subscriptionId")
    private String subscriptionId;

    @JsonProperty("groupId")
    private String groupId;

    @JsonProperty("groupName")
    private String groupName;

    @JsonProperty("subscriptionUrl")
    private String subscriptionUrl;

    @JsonProperty("webcalUrl")
    private String webcalUrl;

    @JsonProperty("createdAt")
    private Instant createdAt;

    // Default constructor for Jackson
    public CalendarSubscriptionResponse() {
    }

    public CalendarSubscriptionResponse(String subscriptionId, String groupId, String groupName,
                                      String subscriptionUrl, String webcalUrl, Instant createdAt) {
        this.subscriptionId = subscriptionId;
        this.groupId = groupId;
        this.groupName = groupName;
        this.subscriptionUrl = subscriptionUrl;
        this.webcalUrl = webcalUrl;
        this.createdAt = createdAt;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(String subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getSubscriptionUrl() {
        return subscriptionUrl;
    }

    public void setSubscriptionUrl(String subscriptionUrl) {
        this.subscriptionUrl = subscriptionUrl;
    }

    public String getWebcalUrl() {
        return webcalUrl;
    }

    public void setWebcalUrl(String webcalUrl) {
        this.webcalUrl = webcalUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
