package com.bbthechange.inviter.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Config for TIME poll feature gating.
 *
 * {@code MIN_TIME_SUGGESTION_VERSION} is the minimum iOS/Android app version that knows how to
 * render TIME polls and submit new options with a {@code timeInput}. Requests from older
 * versions get {@code canAddOptions=false} on TIME polls (clients hide the "add" button).
 *
 * IMPORTANT: the real target version is owned by the client teammates and must match the
 * version that ships with the TIME poll UI. Until they confirm, this default is a placeholder
 * and must be overridden via {@code time-polls.min-suggestion-version} in application.properties
 * before Slice 2 ships.
 */
@Configuration
public class TimePollConfig {

    public static final String UNKNOWN_MIN_VERSION = "UNKNOWN";

    @Value("${time-polls.min-suggestion-version:UNKNOWN}")
    private String minTimeSuggestionVersion;

    public String getMinTimeSuggestionVersion() {
        return minTimeSuggestionVersion;
    }

    public boolean isMinVersionKnown() {
        return minTimeSuggestionVersion != null && !UNKNOWN_MIN_VERSION.equals(minTimeSuggestionVersion);
    }
}
