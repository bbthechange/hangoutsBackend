package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.GroupRepository;
import com.bbthechange.inviter.repository.HangoutRepository;
import com.bbthechange.inviter.service.EventSeriesService;
import com.bbthechange.inviter.service.FuzzyTimeService;
import com.bbthechange.inviter.service.GroupTimestampService;
import com.bbthechange.inviter.service.HangoutSchedulerService;
import com.bbthechange.inviter.service.NotificationService;
import com.bbthechange.inviter.service.UserService;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Base class for HangoutServiceImpl tests.
 * Provides common mocks, setup, and test data generation methods.
 *
 * All service test files extend this class to share the setup logic and helper methods.
 */
@ExtendWith(MockitoExtension.class)
abstract class HangoutServiceTestBase {

    @Mock
    protected HangoutRepository hangoutRepository;

    @Mock
    protected GroupRepository groupRepository;

    @Mock
    protected FuzzyTimeService fuzzyTimeService;

    @Mock
    protected UserService userService;

    @Mock
    protected EventSeriesService eventSeriesService;

    @Mock
    protected NotificationService notificationService;

    @Mock
    protected PointerUpdateService pointerUpdateService;

    @Mock
    protected com.bbthechange.inviter.service.S3Service s3Service;

    @Mock
    protected GroupTimestampService groupTimestampService;

    @Mock
    protected HangoutSchedulerService hangoutSchedulerService;

    @InjectMocks
    protected HangoutServiceImpl hangoutService;

    // ============================================================================
    // HELPER METHODS FOR TEST DATA CREATION
    // ============================================================================

    protected Event createTestEvent(String eventId) {
        Event event = new Event("Test Event", "Description",
            java.time.LocalDateTime.now(), java.time.LocalDateTime.now().plusHours(2),
            null, EventVisibility.INVITE_ONLY, null);
        // Set the ID using reflection or a test-friendly constructor
        return event;
    }

    protected Hangout createTestHangout(String hangoutId) {
        Hangout hangout = new Hangout("Test Hangout", "Description",
            java.time.LocalDateTime.now(), java.time.LocalDateTime.now().plusHours(2),
            null, EventVisibility.INVITE_ONLY, null);
        hangout.setHangoutId(hangoutId);
        return hangout;
    }

    protected Hangout createTestHangoutWithTimeInput(String hangoutId) {
        Hangout hangout = createTestHangout(hangoutId);
        TimeInfo timeInfo = new TimeInfo();
        timeInfo.setStartTime("1754558100"); // Unix timestamp
        timeInfo.setEndTime("1754566200");
        hangout.setTimeInput(timeInfo);
        hangout.setStartTimestamp(1754558100L);
        hangout.setEndTimestamp(1754566200L);
        return hangout;
    }

    protected GroupMembership createTestMembership(String groupId, String userId, String groupName) {
        // For tests, create membership without going through the constructor that validates UUIDs
        GroupMembership membership = new GroupMembership();
        membership.setGroupId(groupId);
        membership.setUserId(userId);
        membership.setGroupName(groupName);
        membership.setRole(GroupRole.MEMBER);
        // Set keys directly for test purposes
        membership.setPk("GROUP#" + groupId);
        membership.setSk("USER#" + userId);
        membership.setGsi1pk("USER#" + userId);
        membership.setGsi1sk("GROUP#" + groupId);
        return membership;
    }

    protected Poll createTestPoll() {
        // Create Poll without constructor validation for tests
        Poll poll = new Poll();
        poll.setEventId("12345678-1234-1234-1234-123456789012");
        poll.setPollId("11111111-1111-1111-1111-111111111111");
        poll.setTitle("Test Poll");
        poll.setDescription("Description");
        poll.setMultipleChoice(false);
        poll.setPk("EVENT#12345678-1234-1234-1234-123456789012");
        poll.setSk("POLL#11111111-1111-1111-1111-111111111111");
        return poll;
    }

    protected Car createTestCar() {
        // Create Car without constructor validation for tests
        Car car = new Car();
        car.setEventId("12345678-1234-1234-1234-123456789012");
        car.setDriverId("87654321-4321-4321-4321-210987654321");
        car.setDriverName("John Doe");
        car.setTotalCapacity(4);
        car.setAvailableSeats(4);
        car.setPk("EVENT#12345678-1234-1234-1234-123456789012");
        car.setSk("CAR#87654321-4321-4321-4321-210987654321");
        return car;
    }

    protected Vote createTestVote() {
        // Create Vote without constructor validation for tests
        Vote vote = new Vote();
        vote.setEventId("12345678-1234-1234-1234-123456789012");
        vote.setPollId("11111111-1111-1111-1111-111111111111");
        vote.setOptionId("22222222-2222-2222-2222-222222222222");
        vote.setUserId("87654321-4321-4321-4321-210987654321");
        vote.setVoteType("YES");
        vote.setPk("EVENT#12345678-1234-1234-1234-123456789012");
        vote.setSk("POLL#11111111-1111-1111-1111-111111111111#VOTE#87654321-4321-4321-4321-210987654321#OPTION#22222222-2222-2222-2222-222222222222");
        return vote;
    }

    protected InterestLevel createTestInterestLevel() {
        // Create InterestLevel without constructor validation for tests
        InterestLevel interest = new InterestLevel();
        interest.setEventId("12345678-1234-1234-1234-123456789012");
        interest.setUserId("87654321-4321-4321-4321-210987654321");
        interest.setUserName("John Doe");
        interest.setStatus("GOING");
        interest.setPk("EVENT#12345678-1234-1234-1234-123456789012");
        interest.setSk("ATTENDANCE#87654321-4321-4321-4321-210987654321");
        return interest;
    }

    protected UserSummaryDTO createTestUser(String userId) {
        UserSummaryDTO user = new UserSummaryDTO();
        user.setDisplayName("John Doe");
//        user.setUsername("johndoe");
        return user;
    }

    protected HangoutPointer createTestHangoutPointer(String entityId, String hangoutId) {
        // Create pointer manually for tests to support both group and user entities
        HangoutPointer pointer = new HangoutPointer();
        pointer.setGroupId(entityId); // For tests, this can be either group or user ID
        pointer.setHangoutId(hangoutId);
        pointer.setTitle("Test Hangout " + hangoutId);
        pointer.setStatus("ACTIVE");
        Address testLocation = new Address();
        testLocation.setName("Test Location");
        pointer.setLocation(testLocation);
        pointer.setParticipantCount(5);

        // Set keys manually for test
        if (entityId.contains("1111") || entityId.contains("2222")) {
            // This is a group ID
            pointer.setPk("GROUP#" + entityId);
        } else {
            // This is a user ID
            pointer.setPk("USER#" + entityId);
        }
        pointer.setSk("HANGOUT#" + hangoutId);
        return pointer;
    }

    protected java.util.HashMap<String, String> createTimeInputMap(String key1, String value1, String key2, String value2) {
        java.util.HashMap<String, String> timeInput = new java.util.HashMap<>();
        timeInput.put(key1, value1);
        timeInput.put(key2, value2);
        return timeInput;
    }
}
