package com.bbthechange.inviter.util;

import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.model.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for HangoutDataTransformer utility class.
 * Tests transformation of denormalized data into nested DTOs.
 */
class HangoutDataTransformerTest {

    // ============================================================================
    // transformPollData() tests
    // ============================================================================

    @Test
    void transformPollData_WithEmptyLists_ReturnsEmptyList() {
        // Given
        List<Poll> polls = new ArrayList<>();
        List<PollOption> options = new ArrayList<>();
        List<Vote> votes = new ArrayList<>();
        String userId = UUID.randomUUID().toString();

        // When
        List<PollWithOptionsDTO> result = HangoutDataTransformer.transformPollData(
                polls, options, votes, userId);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void transformPollData_WithSinglePollNoVotes_CalculatesZeroVoteCounts() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        Poll poll = new Poll(hangoutId, "What time?", "Select a start time", false);
        String pollId = poll.getPollId();

        PollOption option1 = new PollOption(hangoutId, pollId, "8:00 AM");
        PollOption option2 = new PollOption(hangoutId, pollId, "9:00 AM");

        List<Poll> polls = List.of(poll);
        List<PollOption> options = List.of(option1, option2);
        List<Vote> votes = new ArrayList<>();

        // When
        List<PollWithOptionsDTO> result = HangoutDataTransformer.transformPollData(
                polls, options, votes, userId);

        // Then
        assertThat(result).hasSize(1);
        PollWithOptionsDTO dto = result.get(0);
        assertThat(dto.getPollId()).isEqualTo(pollId);
        assertThat(dto.getTitle()).isEqualTo("What time?");
        assertThat(dto.getTotalVotes()).isEqualTo(0);
        assertThat(dto.getOptions()).hasSize(2);
        assertThat(dto.getOptions().get(0).getVoteCount()).isEqualTo(0);
        assertThat(dto.getOptions().get(1).getVoteCount()).isEqualTo(0);
        assertThat(dto.getOptions().get(0).isUserVoted()).isFalse();
        assertThat(dto.getOptions().get(1).isUserVoted()).isFalse();
    }

    @Test
    void transformPollData_WithVotes_CalculatesVoteCountsCorrectly() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        Poll poll = new Poll(hangoutId, "What time?", "Select a start time", false);
        String pollId = poll.getPollId();

        PollOption option1 = new PollOption(hangoutId, pollId, "8:00 AM");
        PollOption option2 = new PollOption(hangoutId, pollId, "9:00 AM");
        String option1Id = option1.getOptionId();
        String option2Id = option2.getOptionId();

        String user2Id = UUID.randomUUID().toString();
        String user3Id = UUID.randomUUID().toString();
        String user4Id = UUID.randomUUID().toString();
        
        Vote vote1 = new Vote(hangoutId, pollId, option1Id, userId, "YES");
        Vote vote2 = new Vote(hangoutId, pollId, option1Id, user2Id, "YES");
        Vote vote3 = new Vote(hangoutId, pollId, option1Id, user3Id, "YES");
        Vote vote4 = new Vote(hangoutId, pollId, option2Id, user4Id, "YES");

        List<Poll> polls = List.of(poll);
        List<PollOption> options = List.of(option1, option2);
        List<Vote> votes = List.of(vote1, vote2, vote3, vote4);

        // When
        List<PollWithOptionsDTO> result = HangoutDataTransformer.transformPollData(
                polls, options, votes, userId);

        // Then
        assertThat(result).hasSize(1);
        PollWithOptionsDTO dto = result.get(0);
        assertThat(dto.getTotalVotes()).isEqualTo(4);
        assertThat(dto.getOptions()).hasSize(2);

        // Option 1 should have 3 votes
        PollOptionDTO option1DTO = dto.getOptions().stream()
                .filter(o -> o.getOptionId().equals(option1Id))
                .findFirst().orElseThrow();
        assertThat(option1DTO.getVoteCount()).isEqualTo(3);
        assertThat(option1DTO.isUserVoted()).isTrue(); // userId voted for option-1

        // Option 2 should have 1 vote
        PollOptionDTO option2DTO = dto.getOptions().stream()
                .filter(o -> o.getOptionId().equals(option2Id))
                .findFirst().orElseThrow();
        assertThat(option2DTO.getVoteCount()).isEqualTo(1);
        assertThat(option2DTO.isUserVoted()).isFalse(); // userId did not vote for option-2
    }

    @Test
    void transformPollData_WithMultiplePolls_TransformsEachPollCorrectly() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        Poll poll1 = new Poll(hangoutId, "What time?", "Select a time", false);
        Poll poll2 = new Poll(hangoutId, "What to bring?", "Select items", true);

        String poll1Id = poll1.getPollId();
        String poll2Id = poll2.getPollId();

        PollOption poll1Option1 = new PollOption(hangoutId, poll1Id, "8:00 AM");
        PollOption poll1Option2 = new PollOption(hangoutId, poll1Id, "9:00 AM");
        PollOption poll2Option1 = new PollOption(hangoutId, poll2Id, "Sandwiches");
        PollOption poll2Option2 = new PollOption(hangoutId, poll2Id, "Trail mix");

        String opt1Id = poll1Option1.getOptionId();
        String opt2Id = poll1Option2.getOptionId();
        String opt3Id = poll2Option1.getOptionId();
        String opt4Id = poll2Option2.getOptionId();

        Vote vote1 = new Vote(hangoutId, poll1Id, opt1Id, userId, "YES");
        Vote vote2 = new Vote(hangoutId, poll2Id, opt3Id, userId, "YES");
        Vote vote3 = new Vote(hangoutId, poll2Id, opt4Id, userId, "YES");

        List<Poll> polls = List.of(poll1, poll2);
        List<PollOption> options = List.of(poll1Option1, poll1Option2, poll2Option1, poll2Option2);
        List<Vote> votes = List.of(vote1, vote2, vote3);

        // When
        List<PollWithOptionsDTO> result = HangoutDataTransformer.transformPollData(
                polls, options, votes, userId);

        // Then
        assertThat(result).hasSize(2);

        PollWithOptionsDTO poll1DTO = result.stream()
                .filter(p -> p.getPollId().equals(poll1Id))
                .findFirst().orElseThrow();
        assertThat(poll1DTO.getOptions()).hasSize(2);
        assertThat(poll1DTO.getTotalVotes()).isEqualTo(1);

        PollWithOptionsDTO poll2DTO = result.stream()
                .filter(p -> p.getPollId().equals(poll2Id))
                .findFirst().orElseThrow();
        assertThat(poll2DTO.getOptions()).hasSize(2);
        assertThat(poll2DTO.getTotalVotes()).isEqualTo(2);
    }

    @Test
    void transformPollData_WithPollNoOptions_HandlesGracefully() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        Poll poll = new Poll(hangoutId, "Question with no options yet", null, false);

        List<Poll> polls = List.of(poll);
        List<PollOption> options = new ArrayList<>();
        List<Vote> votes = new ArrayList<>();

        // When
        List<PollWithOptionsDTO> result = HangoutDataTransformer.transformPollData(
                polls, options, votes, userId);

        // Then
        assertThat(result).hasSize(1);
        PollWithOptionsDTO dto = result.get(0);
        assertThat(dto.getOptions()).isEmpty();
        assertThat(dto.getTotalVotes()).isEqualTo(0);
    }

    @Test
    void transformPollData_WithMultipleUsersVoting_TracksUserVoteCorrectly() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String requestingUserId = UUID.randomUUID().toString();

        Poll poll = new Poll(hangoutId, "What time?", "Select a time", false);
        String pollId = poll.getPollId();

        PollOption option1 = new PollOption(hangoutId, pollId, "8:00 AM");
        PollOption option2 = new PollOption(hangoutId, pollId, "9:00 AM");
        String option1Id = option1.getOptionId();
        String option2Id = option2.getOptionId();

        // Multiple users vote, including the requesting user
        String user2Id = UUID.randomUUID().toString();
        String user3Id = UUID.randomUUID().toString();
        
        Vote vote1 = new Vote(hangoutId, pollId, option1Id, user2Id, "YES");
        Vote vote2 = new Vote(hangoutId, pollId, option1Id, requestingUserId, "YES");
        Vote vote3 = new Vote(hangoutId, pollId, option2Id, user3Id, "YES");

        List<Poll> polls = List.of(poll);
        List<PollOption> options = List.of(option1, option2);
        List<Vote> votes = List.of(vote1, vote2, vote3);

        // When
        List<PollWithOptionsDTO> result = HangoutDataTransformer.transformPollData(
                polls, options, votes, requestingUserId);

        // Then
        PollWithOptionsDTO dto = result.get(0);

        PollOptionDTO option1DTO = dto.getOptions().stream()
                .filter(o -> o.getOptionId().equals(option1Id))
                .findFirst().orElseThrow();
        assertThat(option1DTO.isUserVoted()).isTrue(); // Requesting user voted here

        PollOptionDTO option2DTO = dto.getOptions().stream()
                .filter(o -> o.getOptionId().equals(option2Id))
                .findFirst().orElseThrow();
        assertThat(option2DTO.isUserVoted()).isFalse(); // Requesting user did not vote here
    }

    // ============================================================================
    // transformCarpoolData() tests
    // ============================================================================

    @Test
    void transformCarpoolData_WithEmptyLists_ReturnsEmptyList() {
        // Given
        List<Car> cars = new ArrayList<>();
        List<CarRider> riders = new ArrayList<>();

        // When
        List<CarWithRidersDTO> result = HangoutDataTransformer.transformCarpoolData(cars, riders);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void transformCarpoolData_WithCarNoRiders_ReturnsCarWithEmptyRiders() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String driverId = UUID.randomUUID().toString();

        Car car = new Car(hangoutId, driverId, "Alice Johnson", 5);

        List<Car> cars = List.of(car);
        List<CarRider> riders = new ArrayList<>();

        // When
        List<CarWithRidersDTO> result = HangoutDataTransformer.transformCarpoolData(cars, riders);

        // Then
        assertThat(result).hasSize(1);
        CarWithRidersDTO dto = result.get(0);
        assertThat(dto.getDriverId()).isEqualTo(driverId);
        assertThat(dto.getDriverName()).isEqualTo("Alice Johnson");
        assertThat(dto.getRiders()).isEmpty();
    }

    @Test
    void transformCarpoolData_WithCarAndRiders_GroupsRidersCorrectly() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String driverId = UUID.randomUUID().toString();

        Car car = new Car(hangoutId, driverId, "Alice Johnson", 5);

        String rider1Id = UUID.randomUUID().toString();
        String rider2Id = UUID.randomUUID().toString();
        CarRider rider1 = new CarRider(hangoutId, driverId, rider1Id, "Bob Smith");
        CarRider rider2 = new CarRider(hangoutId, driverId, rider2Id, "Carol White");

        List<Car> cars = List.of(car);
        List<CarRider> riders = List.of(rider1, rider2);

        // When
        List<CarWithRidersDTO> result = HangoutDataTransformer.transformCarpoolData(cars, riders);

        // Then
        assertThat(result).hasSize(1);
        CarWithRidersDTO dto = result.get(0);
        assertThat(dto.getDriverId()).isEqualTo(driverId);
        assertThat(dto.getRiders()).hasSize(2);
    }

    @Test
    void transformCarpoolData_WithMultipleCars_GroupsRidersPerCar() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String driver1Id = UUID.randomUUID().toString();
        String driver2Id = UUID.randomUUID().toString();

        Car car1 = new Car(hangoutId, driver1Id, "Alice Johnson", 5);
        Car car2 = new Car(hangoutId, driver2Id, "David Lee", 5);

        String rider1Id = UUID.randomUUID().toString();
        String rider2Id = UUID.randomUUID().toString();
        String rider3Id = UUID.randomUUID().toString();
        
        CarRider rider1 = new CarRider(hangoutId, driver1Id, rider1Id, "Bob");
        CarRider rider2 = new CarRider(hangoutId, driver1Id, rider2Id, "Carol");
        CarRider rider3 = new CarRider(hangoutId, driver2Id, rider3Id, "Eve");

        List<Car> cars = List.of(car1, car2);
        List<CarRider> riders = List.of(rider1, rider2, rider3);

        // When
        List<CarWithRidersDTO> result = HangoutDataTransformer.transformCarpoolData(cars, riders);

        // Then
        assertThat(result).hasSize(2);

        CarWithRidersDTO car1DTO = result.stream()
                .filter(c -> c.getDriverId().equals(driver1Id))
                .findFirst().orElseThrow();
        assertThat(car1DTO.getRiders()).hasSize(2);

        CarWithRidersDTO car2DTO = result.stream()
                .filter(c -> c.getDriverId().equals(driver2Id))
                .findFirst().orElseThrow();
        assertThat(car2DTO.getRiders()).hasSize(1);
    }

    @Test
    void transformCarpoolData_WithOrphanRiders_DoesNotIncludeOrphans() {
        // Given - riders for a driver that doesn't have a car entry
        String hangoutId = UUID.randomUUID().toString();
        String driverId = UUID.randomUUID().toString();
        String orphanDriverId = UUID.randomUUID().toString();

        Car car = new Car(hangoutId, driverId, "Driver Name", 5);

        String rider1Id = UUID.randomUUID().toString();
        String orphanRiderId = UUID.randomUUID().toString();
        
        CarRider validRider = new CarRider(hangoutId, driverId, rider1Id, "Rider Name");
        CarRider orphanRider = new CarRider(hangoutId, orphanDriverId, orphanRiderId, "Orphan Name");

        List<Car> cars = List.of(car);
        List<CarRider> riders = List.of(validRider, orphanRider);

        // When
        List<CarWithRidersDTO> result = HangoutDataTransformer.transformCarpoolData(cars, riders);

        // Then
        assertThat(result).hasSize(1);
        CarWithRidersDTO dto = result.get(0);
        assertThat(dto.getRiders()).hasSize(1);
    }

    // ============================================================================
    // transformNeedsRideData() tests
    // ============================================================================

    @Test
    void transformNeedsRideData_WithEmptyList_ReturnsEmptyList() {
        // Given
        List<NeedsRide> needsRideList = new ArrayList<>();

        // When
        List<NeedsRideDTO> result = HangoutDataTransformer.transformNeedsRideData(needsRideList);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void transformNeedsRideData_WithSingleEntry_TransformsCorrectly() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String notes = "Need ride from Capitol Hill";

        NeedsRide needsRide = new NeedsRide(hangoutId, userId, notes);

        List<NeedsRide> needsRideList = List.of(needsRide);

        // When
        List<NeedsRideDTO> result = HangoutDataTransformer.transformNeedsRideData(needsRideList);

        // Then
        assertThat(result).hasSize(1);
        NeedsRideDTO dto = result.get(0);
        assertThat(dto.getUserId()).isEqualTo(userId);
        assertThat(dto.getNotes()).isEqualTo(notes);
    }

    @Test
    void transformNeedsRideData_WithMultipleEntries_TransformsAll() {
        // Given
        String hangoutId = UUID.randomUUID().toString();

        String user1Id = UUID.randomUUID().toString();
        String user2Id = UUID.randomUUID().toString();
        String user3Id = UUID.randomUUID().toString();
        
        NeedsRide needsRide1 = new NeedsRide(hangoutId, user1Id, "Need ride from downtown");
        NeedsRide needsRide2 = new NeedsRide(hangoutId, user2Id, "Can meet at subway station");
        NeedsRide needsRide3 = new NeedsRide(hangoutId, user3Id, null);

        List<NeedsRide> needsRideList = List.of(needsRide1, needsRide2, needsRide3);

        // When
        List<NeedsRideDTO> result = HangoutDataTransformer.transformNeedsRideData(needsRideList);

        // Then
        assertThat(result).hasSize(3);
        assertThat(result).extracting(NeedsRideDTO::getUserId)
                .containsExactlyInAnyOrder(user1Id, user2Id, user3Id);

        NeedsRideDTO dto1 = result.stream()
                .filter(dto -> dto.getUserId().equals(user1Id))
                .findFirst().orElseThrow();
        assertThat(dto1.getNotes()).isEqualTo("Need ride from downtown");

        NeedsRideDTO dto3 = result.stream()
                .filter(dto -> dto.getUserId().equals(user3Id))
                .findFirst().orElseThrow();
        assertThat(dto3.getNotes()).isNull();
    }

    @Test
    void transformNeedsRideData_WithNullNotes_HandlesCorrectly() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        NeedsRide needsRide = new NeedsRide(hangoutId, userId, null);

        List<NeedsRide> needsRideList = List.of(needsRide);

        // When
        List<NeedsRideDTO> result = HangoutDataTransformer.transformNeedsRideData(needsRideList);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNotes()).isNull();
    }

    @Test
    void transformNeedsRideData_WithEmptyNotes_PreservesEmptyString() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        NeedsRide needsRide = new NeedsRide(hangoutId, userId, "");

        List<NeedsRide> needsRideList = List.of(needsRide);

        // When
        List<NeedsRideDTO> result = HangoutDataTransformer.transformNeedsRideData(needsRideList);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNotes()).isEqualTo("");
    }

    // ============================================================================
    // transformAttendanceForBackwardCompatibility() tests
    // ============================================================================

    @Test
    void transformAttendanceForBackwardCompatibility_WhenDisabled_ReturnsOriginalList() {
        // Given
        String eventId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        InterestLevel goingLevel = new InterestLevel(eventId, userId, "Alice", "GOING");
        List<InterestLevel> attendance = List.of(goingLevel);

        // When - disabled
        List<InterestLevel> result = HangoutDataTransformer.transformAttendanceForBackwardCompatibility(
                attendance, false);

        // Then - should return original list without synthetic entries
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("GOING");
    }

    @Test
    void transformAttendanceForBackwardCompatibility_WhenEnabled_AddsSyntheticInterestedForGoing() {
        // Given
        String eventId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        InterestLevel goingLevel = new InterestLevel(eventId, userId, "Alice", "GOING");
        goingLevel.setMainImagePath("users/alice/profile.jpg");
        goingLevel.setNotes("So excited!");
        List<InterestLevel> attendance = new ArrayList<>();
        attendance.add(goingLevel);

        // When - enabled
        List<InterestLevel> result = HangoutDataTransformer.transformAttendanceForBackwardCompatibility(
                attendance, true);

        // Then - should have original GOING plus synthetic INTERESTED
        assertThat(result).hasSize(2);

        // Verify original GOING entry is preserved
        InterestLevel going = result.stream()
                .filter(l -> "GOING".equals(l.getStatus()))
                .findFirst().orElseThrow();
        assertThat(going.getUserId()).isEqualTo(userId);
        assertThat(going.getUserName()).isEqualTo("Alice");

        // Verify synthetic INTERESTED entry is added
        InterestLevel interested = result.stream()
                .filter(l -> "INTERESTED".equals(l.getStatus()))
                .findFirst().orElseThrow();
        assertThat(interested.getUserId()).isEqualTo(userId);
        assertThat(interested.getUserName()).isEqualTo("Alice");
        assertThat(interested.getMainImagePath()).isEqualTo("users/alice/profile.jpg");
        assertThat(interested.getNotes()).isEqualTo("So excited!");
        assertThat(interested.getEventId()).isEqualTo(eventId);
    }

    @Test
    void transformAttendanceForBackwardCompatibility_WithInterestedStatus_DoesNotAddDuplicate() {
        // Given - user is already INTERESTED (not GOING)
        String eventId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        InterestLevel interestedLevel = new InterestLevel(eventId, userId, "Bob", "INTERESTED");
        List<InterestLevel> attendance = List.of(interestedLevel);

        // When
        List<InterestLevel> result = HangoutDataTransformer.transformAttendanceForBackwardCompatibility(
                attendance, true);

        // Then - no additional entries should be added
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("INTERESTED");
    }

    @Test
    void transformAttendanceForBackwardCompatibility_WithNotGoingStatus_DoesNotAddInterested() {
        // Given - user set NOT_GOING
        String eventId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        InterestLevel notGoingLevel = new InterestLevel(eventId, userId, "Carol", "NOT_GOING");
        List<InterestLevel> attendance = List.of(notGoingLevel);

        // When
        List<InterestLevel> result = HangoutDataTransformer.transformAttendanceForBackwardCompatibility(
                attendance, true);

        // Then - no additional entries should be added for NOT_GOING
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("NOT_GOING");
    }

    @Test
    void transformAttendanceForBackwardCompatibility_WithMixedStatuses_OnlyAddsForGoing() {
        // Given - multiple users with different statuses
        String eventId = UUID.randomUUID().toString();
        String goingUserId = UUID.randomUUID().toString();
        String interestedUserId = UUID.randomUUID().toString();
        String notGoingUserId = UUID.randomUUID().toString();

        InterestLevel going = new InterestLevel(eventId, goingUserId, "Alice", "GOING");
        InterestLevel interested = new InterestLevel(eventId, interestedUserId, "Bob", "INTERESTED");
        InterestLevel notGoing = new InterestLevel(eventId, notGoingUserId, "Carol", "NOT_GOING");

        List<InterestLevel> attendance = new ArrayList<>();
        attendance.add(going);
        attendance.add(interested);
        attendance.add(notGoing);

        // When
        List<InterestLevel> result = HangoutDataTransformer.transformAttendanceForBackwardCompatibility(
                attendance, true);

        // Then - should have 4 entries: original 3 + 1 synthetic INTERESTED for GOING user
        assertThat(result).hasSize(4);

        // Count statuses
        long goingCount = result.stream().filter(l -> "GOING".equals(l.getStatus())).count();
        long interestedCount = result.stream().filter(l -> "INTERESTED".equals(l.getStatus())).count();
        long notGoingCount = result.stream().filter(l -> "NOT_GOING".equals(l.getStatus())).count();

        assertThat(goingCount).isEqualTo(1);
        assertThat(interestedCount).isEqualTo(2); // Original Bob + synthetic for Alice
        assertThat(notGoingCount).isEqualTo(1);

        // Verify Alice (GOING) has a synthetic INTERESTED entry
        List<InterestLevel> aliceEntries = result.stream()
                .filter(l -> l.getUserId().equals(goingUserId))
                .toList();
        assertThat(aliceEntries).hasSize(2);
        assertThat(aliceEntries).extracting(InterestLevel::getStatus)
                .containsExactlyInAnyOrder("GOING", "INTERESTED");
    }

    @Test
    void transformAttendanceForBackwardCompatibility_WithNullList_ReturnsNull() {
        // When
        List<InterestLevel> result = HangoutDataTransformer.transformAttendanceForBackwardCompatibility(
                null, true);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void transformAttendanceForBackwardCompatibility_WithEmptyList_ReturnsEmptyList() {
        // Given
        List<InterestLevel> attendance = new ArrayList<>();

        // When
        List<InterestLevel> result = HangoutDataTransformer.transformAttendanceForBackwardCompatibility(
                attendance, true);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void transformAttendanceForBackwardCompatibility_WithMultipleGoingUsers_AddsSyntheticForEach() {
        // Given - multiple GOING users
        String eventId = UUID.randomUUID().toString();
        String user1Id = UUID.randomUUID().toString();
        String user2Id = UUID.randomUUID().toString();

        InterestLevel going1 = new InterestLevel(eventId, user1Id, "Alice", "GOING");
        InterestLevel going2 = new InterestLevel(eventId, user2Id, "Bob", "GOING");

        List<InterestLevel> attendance = new ArrayList<>();
        attendance.add(going1);
        attendance.add(going2);

        // When
        List<InterestLevel> result = HangoutDataTransformer.transformAttendanceForBackwardCompatibility(
                attendance, true);

        // Then - should have 4 entries: 2 GOING + 2 synthetic INTERESTED
        assertThat(result).hasSize(4);

        long goingCount = result.stream().filter(l -> "GOING".equals(l.getStatus())).count();
        long interestedCount = result.stream().filter(l -> "INTERESTED".equals(l.getStatus())).count();

        assertThat(goingCount).isEqualTo(2);
        assertThat(interestedCount).isEqualTo(2);
    }
}
