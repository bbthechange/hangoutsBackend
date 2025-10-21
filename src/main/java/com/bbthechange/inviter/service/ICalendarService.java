package com.bbthechange.inviter.service;

import com.bbthechange.inviter.model.Group;
import com.bbthechange.inviter.model.HangoutPointer;

import java.util.List;

/**
 * Service for generating iCalendar (ICS) formatted calendar feeds.
 * Used by the calendar subscription feature to provide standard calendar feeds
 * that can be consumed by iOS Calendar, Google Calendar, and other calendar applications.
 */
public interface ICalendarService {

    /**
     * Generate an ICS calendar feed for a group containing all its future hangouts.
     *
     * @param group The group whose calendar feed to generate
     * @param hangouts List of future hangout pointers for the group (sorted by start time)
     * @return ICS formatted string conforming to RFC 5545 (iCalendar)
     */
    String generateICS(Group group, List<HangoutPointer> hangouts);
}
