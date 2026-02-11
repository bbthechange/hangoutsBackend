package com.bbthechange.inviter.util;

import com.bbthechange.inviter.dto.Address;
import com.bbthechange.inviter.dto.TimeInfo;
import com.bbthechange.inviter.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HangoutPointerFactoryTest {

    private static final String GROUP_ID = "12345678-1234-1234-1234-123456789012";
    private static final String HANGOUT_ID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    private Hangout hangout;
    private String groupId;

    @BeforeEach
    void setUp() {
        groupId = GROUP_ID;

        hangout = new Hangout();
        hangout.setHangoutId(HANGOUT_ID);
        hangout.setTitle("Movie Night");
        hangout.setDescription("Watch a great movie");
        hangout.setStartTimestamp(1700000000L);
        hangout.setEndTimestamp(1700007200L);
        hangout.setVisibility(EventVisibility.INVITE_ONLY);
        hangout.setSeriesId("test-series-id");
        hangout.setMainImagePath("images/movie.jpg");
        hangout.setCarpoolEnabled(true);
        hangout.setHostAtPlaceUserId("host-user-id");

        TimeInfo timeInfo = new TimeInfo();
        timeInfo.setStartTime("2023-11-14T20:00:00-05:00");
        timeInfo.setEndTime("2023-11-14T22:00:00-05:00");
        hangout.setTimeInput(timeInfo);

        Address location = new Address();
        location.setName("AMC Theater");
        location.setStreetAddress("123 Main St");
        hangout.setLocation(location);

        hangout.setExternalId("ext-123");
        hangout.setExternalSource("TICKETMASTER");
        hangout.setIsGeneratedTitle(true);

        hangout.setTicketLink("https://tickets.example.com");
        hangout.setTicketsRequired(true);
        hangout.setDiscountCode("SAVE20");
    }

    // ============================================================================
    // fromHangout TESTS
    // ============================================================================

    @Nested
    class FromHangoutTests {

        @Test
        void fromHangout_CopiesAllFields() {
            HangoutPointer pointer = HangoutPointerFactory.fromHangout(hangout, groupId);

            assertThat(pointer.getGroupId()).isEqualTo(groupId);
            assertThat(pointer.getHangoutId()).isEqualTo(HANGOUT_ID);
            assertThat(pointer.getTitle()).isEqualTo("Movie Night");
            assertThat(pointer.getDescription()).isEqualTo("Watch a great movie");
            assertThat(pointer.getStartTimestamp()).isEqualTo(1700000000L);
            assertThat(pointer.getEndTimestamp()).isEqualTo(1700007200L);
            assertThat(pointer.getVisibility()).isEqualTo(EventVisibility.INVITE_ONLY);
            assertThat(pointer.getSeriesId()).isEqualTo("test-series-id");
            assertThat(pointer.getMainImagePath()).isEqualTo("images/movie.jpg");
            assertThat(pointer.isCarpoolEnabled()).isTrue();
            assertThat(pointer.getHostAtPlaceUserId()).isEqualTo("host-user-id");

            assertThat(pointer.getTimeInput()).isNotNull();
            assertThat(pointer.getTimeInput().getStartTime()).isEqualTo("2023-11-14T20:00:00-05:00");
            assertThat(pointer.getTimeInput().getEndTime()).isEqualTo("2023-11-14T22:00:00-05:00");

            assertThat(pointer.getLocation()).isNotNull();
            assertThat(pointer.getLocation().getName()).isEqualTo("AMC Theater");

            assertThat(pointer.getExternalId()).isEqualTo("ext-123");
            assertThat(pointer.getExternalSource()).isEqualTo("TICKETMASTER");
            assertThat(pointer.getIsGeneratedTitle()).isTrue();
        }

        @Test
        void fromHangout_SetsStatusActive() {
            HangoutPointer pointer = HangoutPointerFactory.fromHangout(hangout, groupId);
            assertThat(pointer.getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        void fromHangout_SetsParticipantCountZero() {
            HangoutPointer pointer = HangoutPointerFactory.fromHangout(hangout, groupId);
            assertThat(pointer.getParticipantCount()).isZero();
        }

        @Test
        void fromHangout_SetsGsiKeys() {
            HangoutPointer pointer = HangoutPointerFactory.fromHangout(hangout, groupId);

            assertThat(pointer.getGsi1pk()).isEqualTo(InviterKeyFactory.getGroupPk(groupId));
            assertThat(pointer.getGsi1sk()).isEqualTo("1700000000");
        }

        @Test
        void fromHangout_NullStartTimestamp_SkipsGsi1sk() {
            hangout.setStartTimestamp(null);
            HangoutPointer pointer = HangoutPointerFactory.fromHangout(hangout, groupId);

            assertThat(pointer.getGsi1pk()).isEqualTo(InviterKeyFactory.getGroupPk(groupId));
            assertThat(pointer.getGsi1sk()).isNull();
        }

        @Test
        void fromHangout_InitializesEmptyCollections() {
            HangoutPointer pointer = HangoutPointerFactory.fromHangout(hangout, groupId);

            // Constructor initializes empty collections
            assertThat(pointer.getPolls()).isEmpty();
            assertThat(pointer.getPollOptions()).isEmpty();
            assertThat(pointer.getVotes()).isEmpty();
            assertThat(pointer.getCars()).isEmpty();
            assertThat(pointer.getCarRiders()).isEmpty();
            assertThat(pointer.getNeedsRide()).isEmpty();
            assertThat(pointer.getAttributes()).isEmpty();
            assertThat(pointer.getInterestLevels()).isEmpty();
        }

        @Test
        void fromHangout_CopiesTicketFields() {
            HangoutPointer pointer = HangoutPointerFactory.fromHangout(hangout, groupId);

            assertThat(pointer.getTicketLink()).isEqualTo("https://tickets.example.com");
            assertThat(pointer.getTicketsRequired()).isTrue();
            assertThat(pointer.getDiscountCode()).isEqualTo("SAVE20");
        }
    }

    // ============================================================================
    // applyHangoutFields TESTS
    // ============================================================================

    @Nested
    class ApplyHangoutFieldsTests {

        @Test
        void applyHangoutFields_PreservesCollections() {
            HangoutPointer pointer = new HangoutPointer(groupId, HANGOUT_ID, "Old Title");

            // Set some collections
            Poll poll = new Poll(HANGOUT_ID, "Best Movie?", null, false);
            pointer.setPolls(new ArrayList<>(List.of(poll)));

            Vote vote = new Vote(HANGOUT_ID, UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), "UP");
            pointer.setVotes(new ArrayList<>(List.of(vote)));

            // Apply hangout fields
            HangoutPointerFactory.applyHangoutFields(pointer, hangout);

            // Collections should be preserved
            assertThat(pointer.getPolls()).hasSize(1);
            assertThat(pointer.getVotes()).hasSize(1);
        }

        @Test
        void applyHangoutFields_PreservesParticipantCount() {
            HangoutPointer pointer = new HangoutPointer(groupId, HANGOUT_ID, "Old Title");
            pointer.setParticipantCount(5);

            HangoutPointerFactory.applyHangoutFields(pointer, hangout);

            assertThat(pointer.getParticipantCount()).isEqualTo(5);
        }

        @Test
        void applyHangoutFields_PreservesVersion() {
            HangoutPointer pointer = new HangoutPointer(groupId, HANGOUT_ID, "Old Title");
            pointer.setVersion(42L);

            HangoutPointerFactory.applyHangoutFields(pointer, hangout);

            assertThat(pointer.getVersion()).isEqualTo(42L);
        }

        @Test
        void applyHangoutFields_UpdatesGsi1sk() {
            HangoutPointer pointer = new HangoutPointer(groupId, HANGOUT_ID, "Old Title");
            pointer.setGsi1sk("999999999");

            HangoutPointerFactory.applyHangoutFields(pointer, hangout);

            assertThat(pointer.getGsi1sk()).isEqualTo("1700000000");
        }

        @Test
        void applyHangoutFields_NullStartTimestamp_DoesNotOverwriteGsi1sk() {
            HangoutPointer pointer = new HangoutPointer(groupId, HANGOUT_ID, "Old Title");
            pointer.setGsi1sk("999999999");

            hangout.setStartTimestamp(null);
            HangoutPointerFactory.applyHangoutFields(pointer, hangout);

            // Should NOT overwrite with null or "null" string
            assertThat(pointer.getGsi1sk()).isEqualTo("999999999");
        }

        @Test
        void applyHangoutFields_UpdatesAllHangoutFields() {
            HangoutPointer pointer = new HangoutPointer(groupId, HANGOUT_ID, "Old Title");
            pointer.setDescription("Old description");
            pointer.setVisibility(EventVisibility.PUBLIC);

            HangoutPointerFactory.applyHangoutFields(pointer, hangout);

            assertThat(pointer.getTitle()).isEqualTo("Movie Night");
            assertThat(pointer.getDescription()).isEqualTo("Watch a great movie");
            assertThat(pointer.getStartTimestamp()).isEqualTo(1700000000L);
            assertThat(pointer.getEndTimestamp()).isEqualTo(1700007200L);
            assertThat(pointer.getVisibility()).isEqualTo(EventVisibility.INVITE_ONLY);
            assertThat(pointer.getSeriesId()).isEqualTo("test-series-id");
            assertThat(pointer.getMainImagePath()).isEqualTo("images/movie.jpg");
            assertThat(pointer.isCarpoolEnabled()).isTrue();
            assertThat(pointer.getHostAtPlaceUserId()).isEqualTo("host-user-id");
            assertThat(pointer.getTimeInput().getStartTime()).isEqualTo("2023-11-14T20:00:00-05:00");
            assertThat(pointer.getLocation().getName()).isEqualTo("AMC Theater");
            assertThat(pointer.getExternalId()).isEqualTo("ext-123");
            assertThat(pointer.getExternalSource()).isEqualTo("TICKETMASTER");
            assertThat(pointer.getIsGeneratedTitle()).isTrue();
            assertThat(pointer.getTicketLink()).isEqualTo("https://tickets.example.com");
            assertThat(pointer.getTicketsRequired()).isTrue();
            assertThat(pointer.getDiscountCode()).isEqualTo("SAVE20");
        }
    }
}
