package com.bbthechange.inviter.service.impl;

import biweekly.Biweekly;
import biweekly.ICalendar;
import biweekly.component.VEvent;
import biweekly.property.Method;
import biweekly.property.Status;
import com.bbthechange.inviter.dto.Address;
import com.bbthechange.inviter.model.Group;
import com.bbthechange.inviter.model.HangoutPointer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ICalendarServiceImpl.
 * Tests ICS calendar feed generation from group and hangout data.
 */
@ExtendWith(MockitoExtension.class)
class ICalendarServiceImplTest {

    private ICalendarServiceImpl iCalendarService;

    @BeforeEach
    void setUp() {
        iCalendarService = new ICalendarServiceImpl();
    }

    @Test
    void generateICS_WithEmptyHangoutList_ReturnsValidICSWithNoEvents() {
        // Given
        Group group = new Group();
        group.setGroupId("group-123");
        group.setGroupName("Test Group");
        List<HangoutPointer> hangouts = new ArrayList<>();

        // When
        String icsContent = iCalendarService.generateICS(group, hangouts);

        // Then
        assertThat(icsContent).isNotNull();
        assertThat(icsContent).isNotEmpty();
        assertThat(icsContent).contains("BEGIN:VCALENDAR");
        assertThat(icsContent).contains("END:VCALENDAR");
        assertThat(icsContent).contains("X-WR-CALNAME:Test Group");
        assertThat(icsContent).contains("PRODID:-//Inviter//HangOut Calendar//EN");
        assertThat(icsContent).doesNotContain("BEGIN:VEVENT");

        // Verify it can be parsed back as valid ICS
        ICalendar parsed = Biweekly.parse(icsContent).first();
        assertThat(parsed).isNotNull();
        assertThat(parsed.getEvents()).isEmpty();
    }

    @Test
    void generateICS_WithSingleHangout_IncludesEventWithAllFields() {
        // Given
        Group group = new Group();
        group.setGroupId("group-123");
        group.setGroupName("Seattle Hikers");

        HangoutPointer hangout = createFullHangout();
        List<HangoutPointer> hangouts = List.of(hangout);

        // When
        String icsContent = iCalendarService.generateICS(group, hangouts);

        // Then
        assertThat(icsContent).isNotNull();
        ICalendar parsed = Biweekly.parse(icsContent).first();
        assertThat(parsed.getEvents()).hasSize(1);

        VEvent event = parsed.getEvents().get(0);
        assertThat(event.getUid().getValue()).isEqualTo("22222222-2222-2222-2222-222222222222@inviter.app");
        assertThat(event.getSummary().getValue()).isEqualTo("Mountain Hike");
        assertThat(event.getDateStart().getValue().getTime()).isEqualTo(1697904000000L); // 2023-10-21 14:00:00 UTC
        assertThat(event.getDateEnd().getValue().getTime()).isEqualTo(1697914800000L); // 2023-10-21 17:00:00 UTC
        assertThat(event.getLocation().getValue()).isEqualTo("Mount Rainier");
        assertThat(event.getDescription().getValue()).contains("Bring water and snacks");
        assertThat(event.getDescription().getValue()).contains("5 people going");
        assertThat(event.getDescription().getValue()).contains("RSVP: https://app.inviter.app/hangouts/22222222-2222-2222-2222-222222222222");
        assertThat(event.getStatus()).isEqualTo(Status.confirmed());
        assertThat(event.getSequence().getValue()).isEqualTo(0);
    }

    @Test
    void generateICS_WithMultipleHangouts_IncludesAllEvents() {
        // Given
        Group group = new Group();
        group.setGroupId("11111111-1111-1111-1111-111111111111");
        group.setGroupName("Test Group");

        HangoutPointer hangout1 = createBasicHangout("33333333-3333-3333-3333-333333333333", "First Hangout", 1697904000L);
        HangoutPointer hangout2 = createBasicHangout("44444444-4444-4444-4444-444444444444", "Second Hangout", 1697914800L);
        HangoutPointer hangout3 = createBasicHangout("55555555-5555-5555-5555-555555555555", "Third Hangout", 1697925600L);
        List<HangoutPointer> hangouts = List.of(hangout1, hangout2, hangout3);

        // When
        String icsContent = iCalendarService.generateICS(group, hangouts);

        // Then
        ICalendar parsed = Biweekly.parse(icsContent).first();
        assertThat(parsed.getEvents()).hasSize(3);

        // Verify each event has unique UID and correct title
        List<VEvent> events = parsed.getEvents();
        assertThat(events.get(0).getUid().getValue()).isEqualTo("33333333-3333-3333-3333-333333333333@inviter.app");
        assertThat(events.get(0).getSummary().getValue()).isEqualTo("First Hangout");
        assertThat(events.get(1).getUid().getValue()).isEqualTo("44444444-4444-4444-4444-444444444444@inviter.app");
        assertThat(events.get(1).getSummary().getValue()).isEqualTo("Second Hangout");
        assertThat(events.get(2).getUid().getValue()).isEqualTo("55555555-5555-5555-5555-555555555555@inviter.app");
        assertThat(events.get(2).getSummary().getValue()).isEqualTo("Third Hangout");
    }

    @Test
    void generateICS_WithHangoutMissingOptionalFields_HandlesGracefully() {
        // Given
        Group group = new Group();
        group.setGroupId("11111111-1111-1111-1111-111111111111");
        group.setGroupName("Test Group");

        String groupId = "11111111-1111-1111-1111-111111111111";
        String hangoutId = "22222222-2222-2222-2222-222222222222";
        HangoutPointer hangout = new HangoutPointer(groupId, hangoutId, "Minimal Hangout");
        hangout.setStartTimestamp(1697904000L);
        // Missing: description, location, endTimestamp, participantCount=0

        List<HangoutPointer> hangouts = List.of(hangout);

        // When
        String icsContent = iCalendarService.generateICS(group, hangouts);

        // Then
        assertThat(icsContent).isNotNull();
        ICalendar parsed = Biweekly.parse(icsContent).first();
        assertThat(parsed.getEvents()).hasSize(1);

        VEvent event = parsed.getEvents().get(0);
        assertThat(event.getSummary().getValue()).isEqualTo("Minimal Hangout");
        assertThat(event.getDescription().getValue()).contains("RSVP: https://app.inviter.app/hangouts/22222222-2222-2222-2222-222222222222");
        assertThat(event.getDescription().getValue()).doesNotContain("people going");
        assertThat(event.getLocation()).isNull();
        assertThat(event.getDateEnd()).isNull();
    }

    @Test
    void generateICS_WithNullHangoutTitle_HandlesGracefully() {
        // Given
        Group group = new Group();
        group.setGroupId("11111111-1111-1111-1111-111111111111");
        group.setGroupName("Test Group");

        String groupId = "11111111-1111-1111-1111-111111111111";
        String hangoutId = "22222222-2222-2222-2222-222222222222";
        HangoutPointer hangout = new HangoutPointer(groupId, hangoutId, null);
        hangout.setStartTimestamp(1697904000L);

        List<HangoutPointer> hangouts = List.of(hangout);

        // When
        String icsContent = iCalendarService.generateICS(group, hangouts);

        // Then
        assertThat(icsContent).isNotNull();
        ICalendar parsed = Biweekly.parse(icsContent).first();
        assertThat(parsed.getEvents()).hasSize(1);

        VEvent event = parsed.getEvents().get(0);
        // Summary should be null or empty
        if (event.getSummary() != null) {
            assertThat(event.getSummary().getValue()).isNullOrEmpty();
        }
    }

    @Test
    void generateICS_WithParticipantCount_IncludesCorrectPluralForm() {
        // Given
        Group group = new Group();
        group.setGroupId("11111111-1111-1111-1111-111111111111");
        group.setGroupName("Test Group");

        // Test with 0 participants
        HangoutPointer hangoutZero = createBasicHangout("33333333-3333-3333-3333-333333333333", "Zero Participants", 1697904000L);
        hangoutZero.setParticipantCount(0);

        // Test with 1 participant (singular)
        HangoutPointer hangoutOne = createBasicHangout("44444444-4444-4444-4444-444444444444", "One Participant", 1697914800L);
        hangoutOne.setParticipantCount(1);
        hangoutOne.setDescription("Description here");

        // Test with 5 participants (plural)
        HangoutPointer hangoutMany = createBasicHangout("55555555-5555-5555-5555-555555555555", "Many Participants", 1697925600L);
        hangoutMany.setParticipantCount(5);
        hangoutMany.setDescription("Description here");

        // When - Test zero participants
        String icsZero = iCalendarService.generateICS(group, List.of(hangoutZero));
        ICalendar parsedZero = Biweekly.parse(icsZero).first();
        VEvent eventZero = parsedZero.getEvents().get(0);
        assertThat(eventZero.getDescription().getValue()).doesNotContain("people going");
        assertThat(eventZero.getDescription().getValue()).doesNotContain("person going");

        // When - Test one participant (singular)
        String icsOne = iCalendarService.generateICS(group, List.of(hangoutOne));
        ICalendar parsedOne = Biweekly.parse(icsOne).first();
        VEvent eventOne = parsedOne.getEvents().get(0);
        assertThat(eventOne.getDescription().getValue()).contains("1 person going");
        assertThat(eventOne.getDescription().getValue()).doesNotContain("1 people going");

        // When - Test multiple participants (plural)
        String icsMany = iCalendarService.generateICS(group, List.of(hangoutMany));
        ICalendar parsedMany = Biweekly.parse(icsMany).first();
        VEvent eventMany = parsedMany.getEvents().get(0);
        assertThat(eventMany.getDescription().getValue()).contains("5 people going");
        assertThat(eventMany.getDescription().getValue()).doesNotContain("5 person going");
    }

    @Test
    void generateICS_RSVPLink_ContainsCorrectHangoutId() {
        // Given
        Group group = new Group();
        group.setGroupId("11111111-1111-1111-1111-111111111111");
        group.setGroupName("Test Group");

        HangoutPointer hangout = createBasicHangout("abcdef12-3456-7890-abcd-ef1234567890", "Test Event", 1697904000L);
        List<HangoutPointer> hangouts = List.of(hangout);

        // When
        String icsContent = iCalendarService.generateICS(group, hangouts);

        // Then
        ICalendar parsed = Biweekly.parse(icsContent).first();
        VEvent event = parsed.getEvents().get(0);
        assertThat(event.getDescription().getValue()).contains("RSVP: https://app.inviter.app/hangouts/");
        assertThat(event.getDescription().getValue()).contains("https://app.inviter.app/hangouts/abcdef12-3456-7890-abcd-ef1234567890");
    }

    @Test
    void generateICS_CalendarMetadata_IsSetCorrectly() {
        // Given
        Group group = new Group();
        group.setGroupId("group-123");
        group.setGroupName("Seattle Hikers");
        List<HangoutPointer> hangouts = new ArrayList<>();

        // When
        String icsContent = iCalendarService.generateICS(group, hangouts);

        // Then
        assertThat(icsContent).contains("PRODID:-//Inviter//HangOut Calendar//EN");
        assertThat(icsContent).contains("METHOD:PUBLISH");
        assertThat(icsContent).contains("X-WR-CALNAME:Seattle Hikers");
        assertThat(icsContent).contains("X-WR-TIMEZONE:America/Los_Angeles");
        assertThat(icsContent).contains("X-WR-CALDESC:Hangouts for Seattle Hikers group");

        // Also verify through parsing
        ICalendar parsed = Biweekly.parse(icsContent).first();
        assertThat(parsed.getProductId().getValue()).isEqualTo("-//Inviter//HangOut Calendar//EN");
        assertThat(parsed.getMethod()).isEqualTo(Method.publish());
    }

    // Helper methods

    private HangoutPointer createFullHangout() {
        String groupId = "11111111-1111-1111-1111-111111111111";
        String hangoutId = "22222222-2222-2222-2222-222222222222";
        HangoutPointer hangout = new HangoutPointer(groupId, hangoutId, "Mountain Hike");
        hangout.setDescription("Bring water and snacks");
        hangout.setStartTimestamp(1697904000L); // 2023-10-21 14:00:00 UTC
        hangout.setEndTimestamp(1697914800L);   // 2023-10-21 17:00:00 UTC
        hangout.setParticipantCount(5);

        Address location = new Address();
        location.setName("Mount Rainier");
        hangout.setLocation(location);

        return hangout;
    }

    private HangoutPointer createBasicHangout(String hangoutId, String title, Long startTimestamp) {
        String groupId = "11111111-1111-1111-1111-111111111111";
        HangoutPointer hangout = new HangoutPointer(groupId, hangoutId, title);
        hangout.setStartTimestamp(startTimestamp);
        return hangout;
    }
}
