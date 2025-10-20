package com.bbthechange.inviter.model;

import com.bbthechange.inviter.dto.Address;
import com.bbthechange.inviter.dto.TimeInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for HangoutPointer model class.
 * Tests the new denormalized fields added for single-query feed loading.
 */
class HangoutPointerTest {

    private String validGroupId;
    private String validHangoutId;
    private String validTitle;

    @BeforeEach
    void setUp() {
        validGroupId = UUID.randomUUID().toString();
        validHangoutId = UUID.randomUUID().toString();
        validTitle = "Test Hangout";
    }

    // ============================================================================
    // CONSTRUCTOR TESTS
    // ============================================================================

    @Test
    void constructor_WithParameters_ShouldSetCorrectValues() {
        // When
        HangoutPointer pointer = new HangoutPointer(validGroupId, validHangoutId, validTitle);

        // Then
        assertThat(pointer.getGroupId()).isEqualTo(validGroupId);
        assertThat(pointer.getHangoutId()).isEqualTo(validHangoutId);
        assertThat(pointer.getTitle()).isEqualTo(validTitle);
        assertThat(pointer.getItemType()).isEqualTo("HANGOUT_POINTER");
        assertThat(pointer.getParticipantCount()).isZero();
    }

    @Test
    void constructor_WithParameters_ShouldInitializeCollections() {
        // When
        HangoutPointer pointer = new HangoutPointer(validGroupId, validHangoutId, validTitle);

        // Then - All collections should be initialized to empty lists
        assertThat(pointer.getPolls()).isNotNull().isEmpty();
        assertThat(pointer.getPollOptions()).isNotNull().isEmpty();
        assertThat(pointer.getVotes()).isNotNull().isEmpty();
        assertThat(pointer.getCars()).isNotNull().isEmpty();
        assertThat(pointer.getCarRiders()).isNotNull().isEmpty();
        assertThat(pointer.getNeedsRide()).isNotNull().isEmpty();
        assertThat(pointer.getAttributes()).isNotNull().isEmpty();
        assertThat(pointer.getInterestLevels()).isNotNull().isEmpty();
    }

    @Test
    void defaultConstructor_ShouldInitializeCollections() {
        // When
        HangoutPointer pointer = new HangoutPointer();

        // Then
        assertThat(pointer.getItemType()).isEqualTo("HANGOUT_POINTER");
        assertThat(pointer.getPolls()).isNotNull().isEmpty();
        assertThat(pointer.getPollOptions()).isNotNull().isEmpty();
        assertThat(pointer.getVotes()).isNotNull().isEmpty();
        assertThat(pointer.getCars()).isNotNull().isEmpty();
        assertThat(pointer.getCarRiders()).isNotNull().isEmpty();
        assertThat(pointer.getNeedsRide()).isNotNull().isEmpty();
        assertThat(pointer.getAttributes()).isNotNull().isEmpty();
        assertThat(pointer.getInterestLevels()).isNotNull().isEmpty();
    }

    // ============================================================================
    // OPTIMISTIC LOCKING TESTS
    // ============================================================================

    @Test
    void setVersion_ShouldUpdateVersionField() {
        // Given
        HangoutPointer pointer = new HangoutPointer(validGroupId, validHangoutId, validTitle);

        // When
        pointer.setVersion(5L);

        // Then
        assertThat(pointer.getVersion()).isEqualTo(5L);
    }

    @Test
    void version_InitiallyNull() {
        // Given/When
        HangoutPointer pointer = new HangoutPointer(validGroupId, validHangoutId, validTitle);

        // Then
        assertThat(pointer.getVersion()).isNull();
    }

    // ============================================================================
    // BASIC HANGOUT FIELDS TESTS
    // ============================================================================

    @Test
    void setDescription_ShouldUpdateDescriptionAndTimestamp() throws InterruptedException {
        // Given
        HangoutPointer pointer = new HangoutPointer(validGroupId, validHangoutId, validTitle);
        Instant initialTimestamp = pointer.getUpdatedAt();
        Thread.sleep(10); // Ensure timestamp difference

        // When
        pointer.setDescription("Test description");

        // Then
        assertThat(pointer.getDescription()).isEqualTo("Test description");
        assertThat(pointer.getUpdatedAt()).isAfter(initialTimestamp);
    }

    @Test
    void setVisibility_ShouldUpdateVisibilityAndTimestamp() throws InterruptedException {
        // Given
        HangoutPointer pointer = new HangoutPointer(validGroupId, validHangoutId, validTitle);
        Instant initialTimestamp = pointer.getUpdatedAt();
        Thread.sleep(10);

        // When
        pointer.setVisibility(EventVisibility.PUBLIC);

        // Then
        assertThat(pointer.getVisibility()).isEqualTo(EventVisibility.PUBLIC);
        assertThat(pointer.getUpdatedAt()).isAfter(initialTimestamp);
    }

    @Test
    void setCarpoolEnabled_ShouldUpdateCarpoolEnabledAndTimestamp() throws InterruptedException {
        // Given
        HangoutPointer pointer = new HangoutPointer(validGroupId, validHangoutId, validTitle);
        Instant initialTimestamp = pointer.getUpdatedAt();
        Thread.sleep(10);

        // When
        pointer.setCarpoolEnabled(true);

        // Then
        assertThat(pointer.isCarpoolEnabled()).isTrue();
        assertThat(pointer.getUpdatedAt()).isAfter(initialTimestamp);
    }

    // ============================================================================
    // POLL DATA COLLECTION TESTS
    // ============================================================================

    @Test
    void setPolls_WithValidList_ShouldUpdateAndTouch() throws InterruptedException {
        // Given
        HangoutPointer pointer = new HangoutPointer(validGroupId, validHangoutId, validTitle);
        Instant initialTimestamp = pointer.getUpdatedAt();
        Thread.sleep(10);

        Poll poll1 = createTestPoll("1");
        Poll poll2 = createTestPoll("2");
        List<Poll> polls = Arrays.asList(poll1, poll2);

        // When
        pointer.setPolls(polls);

        // Then
        assertThat(pointer.getPolls()).hasSize(2);
        assertThat(pointer.getPolls()).containsExactly(poll1, poll2);
        assertThat(pointer.getUpdatedAt()).isAfter(initialTimestamp);
    }

    @Test
    void setPolls_WithNull_ShouldCreateEmptyList() {
        // Given
        HangoutPointer pointer = new HangoutPointer(validGroupId, validHangoutId, validTitle);

        // When
        pointer.setPolls(null);

        // Then
        assertThat(pointer.getPolls()).isNotNull().isEmpty();
    }

    @Test
    void getPolls_WhenNull_ShouldReturnEmptyList() {
        // Given
        HangoutPointer pointer = new HangoutPointer();
        // Force polls to null by reflection or direct field access would be needed
        // But the getter should protect against this

        // When
        List<Poll> result = pointer.getPolls();

        // Then
        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    void setPollOptions_WithValidList_ShouldUpdateAndTouch() throws InterruptedException {
        // Given
        HangoutPointer pointer = new HangoutPointer(validGroupId, validHangoutId, validTitle);
        Instant initialTimestamp = pointer.getUpdatedAt();
        Thread.sleep(10);

        PollOption option1 = createTestPollOption("1");
        PollOption option2 = createTestPollOption("2");
        List<PollOption> options = Arrays.asList(option1, option2);

        // When
        pointer.setPollOptions(options);

        // Then
        assertThat(pointer.getPollOptions()).hasSize(2);
        assertThat(pointer.getPollOptions()).containsExactly(option1, option2);
        assertThat(pointer.getUpdatedAt()).isAfter(initialTimestamp);
    }

    @Test
    void setPollOptions_WithNull_ShouldCreateEmptyList() {
        // Given
        HangoutPointer pointer = new HangoutPointer(validGroupId, validHangoutId, validTitle);

        // When
        pointer.setPollOptions(null);

        // Then
        assertThat(pointer.getPollOptions()).isNotNull().isEmpty();
    }

    @Test
    void setVotes_WithValidList_ShouldUpdateAndTouch() throws InterruptedException {
        // Given
        HangoutPointer pointer = new HangoutPointer(validGroupId, validHangoutId, validTitle);
        Instant initialTimestamp = pointer.getUpdatedAt();
        Thread.sleep(10);

        Vote vote1 = createTestVote("1", "1");
        Vote vote2 = createTestVote("2", "2");
        List<Vote> votes = Arrays.asList(vote1, vote2);

        // When
        pointer.setVotes(votes);

        // Then
        assertThat(pointer.getVotes()).hasSize(2);
        assertThat(pointer.getVotes()).containsExactly(vote1, vote2);
        assertThat(pointer.getUpdatedAt()).isAfter(initialTimestamp);
    }

    @Test
    void setVotes_WithNull_ShouldCreateEmptyList() {
        // Given
        HangoutPointer pointer = new HangoutPointer(validGroupId, validHangoutId, validTitle);

        // When
        pointer.setVotes(null);

        // Then
        assertThat(pointer.getVotes()).isNotNull().isEmpty();
    }

    // ============================================================================
    // CARPOOL DATA COLLECTION TESTS
    // ============================================================================

    @Test
    void setCars_WithValidList_ShouldUpdateAndTouch() throws InterruptedException {
        // Given
        HangoutPointer pointer = new HangoutPointer(validGroupId, validHangoutId, validTitle);
        Instant initialTimestamp = pointer.getUpdatedAt();
        Thread.sleep(10);

        Car car1 = createTestCar("1");
        Car car2 = createTestCar("2");
        List<Car> cars = Arrays.asList(car1, car2);

        // When
        pointer.setCars(cars);

        // Then
        assertThat(pointer.getCars()).hasSize(2);
        assertThat(pointer.getCars()).containsExactly(car1, car2);
        assertThat(pointer.getUpdatedAt()).isAfter(initialTimestamp);
    }

    @Test
    void setCars_WithNull_ShouldCreateEmptyList() {
        // Given
        HangoutPointer pointer = new HangoutPointer(validGroupId, validHangoutId, validTitle);

        // When
        pointer.setCars(null);

        // Then
        assertThat(pointer.getCars()).isNotNull().isEmpty();
    }

    @Test
    void setCarRiders_WithValidList_ShouldUpdateAndTouch() throws InterruptedException {
        // Given
        HangoutPointer pointer = new HangoutPointer(validGroupId, validHangoutId, validTitle);
        Instant initialTimestamp = pointer.getUpdatedAt();
        Thread.sleep(10);

        CarRider rider1 = createTestCarRider("1", "1");
        CarRider rider2 = createTestCarRider("2", "2");
        List<CarRider> riders = Arrays.asList(rider1, rider2);

        // When
        pointer.setCarRiders(riders);

        // Then
        assertThat(pointer.getCarRiders()).hasSize(2);
        assertThat(pointer.getCarRiders()).containsExactly(rider1, rider2);
        assertThat(pointer.getUpdatedAt()).isAfter(initialTimestamp);
    }

    @Test
    void setCarRiders_WithNull_ShouldCreateEmptyList() {
        // Given
        HangoutPointer pointer = new HangoutPointer(validGroupId, validHangoutId, validTitle);

        // When
        pointer.setCarRiders(null);

        // Then
        assertThat(pointer.getCarRiders()).isNotNull().isEmpty();
    }

    @Test
    void setNeedsRide_WithValidList_ShouldUpdateAndTouch() throws InterruptedException {
        // Given
        HangoutPointer pointer = new HangoutPointer(validGroupId, validHangoutId, validTitle);
        Instant initialTimestamp = pointer.getUpdatedAt();
        Thread.sleep(10);

        NeedsRide needsRide1 = createTestNeedsRide("1");
        NeedsRide needsRide2 = createTestNeedsRide("2");
        List<NeedsRide> needsRideList = Arrays.asList(needsRide1, needsRide2);

        // When
        pointer.setNeedsRide(needsRideList);

        // Then
        assertThat(pointer.getNeedsRide()).hasSize(2);
        assertThat(pointer.getNeedsRide()).containsExactly(needsRide1, needsRide2);
        assertThat(pointer.getUpdatedAt()).isAfter(initialTimestamp);
    }

    @Test
    void setNeedsRide_WithNull_ShouldCreateEmptyList() {
        // Given
        HangoutPointer pointer = new HangoutPointer(validGroupId, validHangoutId, validTitle);

        // When
        pointer.setNeedsRide(null);

        // Then
        assertThat(pointer.getNeedsRide()).isNotNull().isEmpty();
    }

    // ============================================================================
    // ATTRIBUTE DATA COLLECTION TESTS
    // ============================================================================

    @Test
    void setAttributes_WithValidList_ShouldUpdateAndTouch() throws InterruptedException {
        // Given
        HangoutPointer pointer = new HangoutPointer(validGroupId, validHangoutId, validTitle);
        Instant initialTimestamp = pointer.getUpdatedAt();
        Thread.sleep(10);

        HangoutAttribute attr1 = createTestAttribute("1", "name1", "value1");
        HangoutAttribute attr2 = createTestAttribute("2", "name2", "value2");
        List<HangoutAttribute> attributes = Arrays.asList(attr1, attr2);

        // When
        pointer.setAttributes(attributes);

        // Then
        assertThat(pointer.getAttributes()).hasSize(2);
        assertThat(pointer.getAttributes()).containsExactly(attr1, attr2);
        assertThat(pointer.getUpdatedAt()).isAfter(initialTimestamp);
    }

    @Test
    void setAttributes_WithNull_ShouldCreateEmptyList() {
        // Given
        HangoutPointer pointer = new HangoutPointer(validGroupId, validHangoutId, validTitle);

        // When
        pointer.setAttributes(null);

        // Then
        assertThat(pointer.getAttributes()).isNotNull().isEmpty();
    }

    // ============================================================================
    // INTEREST LEVEL DATA COLLECTION TESTS
    // ============================================================================

    @Test
    void constructor_WithParameters_ShouldInitializeInterestLevels() {
        // When
        HangoutPointer pointer = new HangoutPointer(validGroupId, validHangoutId, validTitle);

        // Then - interestLevels should be initialized to empty list
        assertThat(pointer.getInterestLevels()).isNotNull().isEmpty();
    }

    @Test
    void getInterestLevels_WhenNull_ShouldReturnEmptyList() {
        // Given
        HangoutPointer pointer = new HangoutPointer();
        // The getter should protect against null even if field is somehow null

        // When
        List<InterestLevel> result = pointer.getInterestLevels();

        // Then
        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    void setInterestLevels_WithNull_ShouldCreateEmptyList() {
        // Given
        HangoutPointer pointer = new HangoutPointer(validGroupId, validHangoutId, validTitle);

        // When
        pointer.setInterestLevels(null);

        // Then
        assertThat(pointer.getInterestLevels()).isNotNull().isEmpty();
    }

    @Test
    void setInterestLevels_WithValidList_ShouldUpdateAndTouch() throws InterruptedException {
        // Given
        HangoutPointer pointer = new HangoutPointer(validGroupId, validHangoutId, validTitle);
        Instant initialTimestamp = pointer.getUpdatedAt();
        Thread.sleep(10);

        InterestLevel interest1 = createTestInterestLevel("1", "GOING");
        InterestLevel interest2 = createTestInterestLevel("2", "INTERESTED");
        List<InterestLevel> interestLevels = Arrays.asList(interest1, interest2);

        // When
        pointer.setInterestLevels(interestLevels);

        // Then
        assertThat(pointer.getInterestLevels()).hasSize(2);
        assertThat(pointer.getInterestLevels()).containsExactly(interest1, interest2);
        assertThat(pointer.getUpdatedAt()).isAfter(initialTimestamp);
    }

    // ============================================================================
    // EXISTING FIELD TESTS (ensuring they still work)
    // ============================================================================

    @Test
    void setTitle_ShouldUpdateTitleAndTimestamp() throws InterruptedException {
        // Given
        HangoutPointer pointer = new HangoutPointer(validGroupId, validHangoutId, validTitle);
        Instant initialTimestamp = pointer.getUpdatedAt();
        Thread.sleep(10);

        // When
        pointer.setTitle("Updated Title");

        // Then
        assertThat(pointer.getTitle()).isEqualTo("Updated Title");
        assertThat(pointer.getUpdatedAt()).isAfter(initialTimestamp);
    }

    @Test
    void setStatus_ShouldUpdateStatusAndTimestamp() throws InterruptedException {
        // Given
        HangoutPointer pointer = new HangoutPointer(validGroupId, validHangoutId, validTitle);
        Instant initialTimestamp = pointer.getUpdatedAt();
        Thread.sleep(10);

        // When
        pointer.setStatus("CANCELLED");

        // Then
        assertThat(pointer.getStatus()).isEqualTo("CANCELLED");
        assertThat(pointer.getUpdatedAt()).isAfter(initialTimestamp);
    }

    @Test
    void setLocation_ShouldUpdateLocationAndTimestamp() throws InterruptedException {
        // Given
        HangoutPointer pointer = new HangoutPointer(validGroupId, validHangoutId, validTitle);
        Instant initialTimestamp = pointer.getUpdatedAt();
        Thread.sleep(10);

        Address location = new Address();
        location.setStreetAddress("123 Main St");
        location.setCity("Seattle");

        // When
        pointer.setLocation(location);

        // Then
        assertThat(pointer.getLocation()).isNotNull();
        assertThat(pointer.getLocation().getStreetAddress()).isEqualTo("123 Main St");
        assertThat(pointer.getUpdatedAt()).isAfter(initialTimestamp);
    }

    @Test
    void setMainImagePath_ShouldUpdateImagePathAndTimestamp() throws InterruptedException {
        // Given
        HangoutPointer pointer = new HangoutPointer(validGroupId, validHangoutId, validTitle);
        Instant initialTimestamp = pointer.getUpdatedAt();
        Thread.sleep(10);

        // When
        pointer.setMainImagePath("/images/test.jpg");

        // Then
        assertThat(pointer.getMainImagePath()).isEqualTo("/images/test.jpg");
        assertThat(pointer.getUpdatedAt()).isAfter(initialTimestamp);
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    private Poll createTestPoll(String suffix) {
        String pollId = UUID.randomUUID().toString();
        Poll poll = new Poll(validHangoutId, "Test Question " + suffix + "?", "Test description", false);
        poll.setPollId(pollId);
        return poll;
    }

    private PollOption createTestPollOption(String suffix) {
        String optionId = UUID.randomUUID().toString();
        String pollId = UUID.randomUUID().toString();
        PollOption option = new PollOption(validHangoutId, pollId, "Test Option " + suffix);
        option.setOptionId(optionId);
        return option;
    }

    private Vote createTestVote(String suffix, String optionSuffix) {
        String userId = UUID.randomUUID().toString();
        String optionId = UUID.randomUUID().toString();
        String pollId = UUID.randomUUID().toString();
        Vote vote = new Vote(validHangoutId, pollId, optionId, userId, "YES");
        return vote;
    }

    private Car createTestCar(String suffix) {
        String driverId = UUID.randomUUID().toString();
        Car car = new Car(validHangoutId, driverId, "Test Driver " + suffix, 4);
        return car;
    }

    private CarRider createTestCarRider(String suffix, String driverSuffix) {
        String riderId = UUID.randomUUID().toString();
        String driverId = UUID.randomUUID().toString();
        CarRider rider = new CarRider(validHangoutId, driverId, riderId, "Test Rider " + suffix);
        return rider;
    }

    private NeedsRide createTestNeedsRide(String suffix) {
        String userId = UUID.randomUUID().toString();
        NeedsRide needsRide = new NeedsRide(validHangoutId, userId, "Need a ride please " + suffix);
        return needsRide;
    }

    private HangoutAttribute createTestAttribute(String suffix, String name, String value) {
        String attrId = UUID.randomUUID().toString();
        HangoutAttribute attr = new HangoutAttribute(validHangoutId, attrId, name, value);
        return attr;
    }

    private InterestLevel createTestInterestLevel(String suffix, String status) {
        String userId = UUID.randomUUID().toString();
        String userName = "User " + suffix;
        InterestLevel interestLevel = new InterestLevel(validHangoutId, userId, userName, status);
        interestLevel.setNotes("Test notes " + suffix);
        interestLevel.setMainImagePath("/images/user" + suffix + ".jpg");
        return interestLevel;
    }
}
