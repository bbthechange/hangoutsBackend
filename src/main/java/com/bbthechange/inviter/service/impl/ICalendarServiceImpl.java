package com.bbthechange.inviter.service.impl;

import biweekly.Biweekly;
import biweekly.ICalendar;
import biweekly.component.VEvent;
import biweekly.property.Method;
import biweekly.property.Status;
import biweekly.property.Uid;
import com.bbthechange.inviter.model.Group;
import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.service.ICalendarService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Implementation of ICalendarService using Biweekly library.
 * Generates RFC 5545 compliant iCalendar feeds for calendar subscription.
 */
@Service
public class ICalendarServiceImpl implements ICalendarService {

    private static final Logger logger = LoggerFactory.getLogger(ICalendarServiceImpl.class);

    @Override
    public String generateICS(Group group, List<HangoutPointer> hangouts) {
        logger.debug("Generating ICS feed for group {} with {} hangouts", group.getGroupId(), hangouts.size());

        ICalendar ical = new ICalendar();

        // Calendar metadata
        ical.setProductId("-//Inviter//HangOut Calendar//EN");
        ical.setMethod(Method.publish());

        // Calendar properties (X-WR extensions for calendar name and description)
        ical.setExperimentalProperty("X-WR-CALNAME", group.getGroupName());
        ical.setExperimentalProperty("X-WR-TIMEZONE", "America/Los_Angeles");
        ical.setExperimentalProperty("X-WR-CALDESC",
            "Hangouts for " + group.getGroupName() + " group");

        // Add each hangout as a calendar event
        for (HangoutPointer hangout : hangouts) {
            try {
                VEvent event = createEventFromHangout(hangout);
                ical.addEvent(event);
            } catch (Exception e) {
                logger.warn("Failed to add hangout {} to ICS feed: {}", hangout.getHangoutId(), e.getMessage());
                // Continue with other events - don't fail the entire feed
            }
        }

        // Generate ICS string
        String icsContent = Biweekly.write(ical).go();

        logger.debug("Generated ICS feed with {} events for group {}", ical.getEvents().size(), group.getGroupId());

        return icsContent;
    }

    /**
     * Create a VEvent (calendar event) from a hangout pointer.
     *
     * @param hangout The hangout pointer containing event details
     * @return VEvent configured with hangout data
     */
    private VEvent createEventFromHangout(HangoutPointer hangout) {
        VEvent event = new VEvent();

        // Unique ID (required by iCalendar spec)
        event.setUid(new Uid(hangout.getHangoutId() + "@inviter.app"));

        // Timestamp when event was last modified (DTSTAMP is required)
        event.setDateTimeStamp(Date.from(Instant.now()));

        // Event title (SUMMARY)
        if (hangout.getTitle() != null) {
            event.setSummary(hangout.getTitle());
        }

        // Start and end times
        if (hangout.getStartTimestamp() != null) {
            event.setDateStart(Date.from(Instant.ofEpochSecond(hangout.getStartTimestamp())));
        }

        if (hangout.getEndTimestamp() != null) {
            event.setDateEnd(Date.from(Instant.ofEpochSecond(hangout.getEndTimestamp())));
        }

        // Description with link to RSVP
        String description = buildDescription(hangout);
        event.setDescription(description);

        // Location
        if (hangout.getLocation() != null && hangout.getLocation().getName() != null) {
            event.setLocation(hangout.getLocation().getName());
        }

        // Status (always confirmed for published hangouts)
        event.setStatus(Status.confirmed());

        // Sequence number (for event versioning - start at 0)
        event.setSequence(0);

        return event;
    }

    /**
     * Build event description including hangout details and participant count.
     *
     * @param hangout The hangout pointer
     * @return Formatted description string
     */
    private String buildDescription(HangoutPointer hangout) {
        StringBuilder description = new StringBuilder();

        // Add hangout description if present
        if (hangout.getDescription() != null && !hangout.getDescription().trim().isEmpty()) {
            description.append(hangout.getDescription());
            description.append("\n\n");
        }

        // Add participant count if available
        if (hangout.getParticipantCount() > 0) {
            description.append("👥 ");
            description.append(hangout.getParticipantCount());
            description.append(hangout.getParticipantCount() == 1 ? " person going" : " people going");
        }

        // TODO: Add RSVP link once we have a proper web frontend URL
        // description.append("\n\nRSVP: https://..../hangouts/").append(hangout.getHangoutId());

        return description.toString();
    }
}
