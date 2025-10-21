package com.bbthechange.inviter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response DTO for listing all calendar subscriptions for a user.
 */
public class CalendarSubscriptionListResponse {

    @JsonProperty("subscriptions")
    private List<CalendarSubscriptionResponse> subscriptions;

    // Default constructor for Jackson
    public CalendarSubscriptionListResponse() {
    }

    public CalendarSubscriptionListResponse(List<CalendarSubscriptionResponse> subscriptions) {
        this.subscriptions = subscriptions;
    }

    public List<CalendarSubscriptionResponse> getSubscriptions() {
        return subscriptions;
    }

    public void setSubscriptions(List<CalendarSubscriptionResponse> subscriptions) {
        this.subscriptions = subscriptions;
    }
}
