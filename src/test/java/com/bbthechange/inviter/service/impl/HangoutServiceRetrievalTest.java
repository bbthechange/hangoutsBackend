package com.bbthechange.inviter.service.impl;

import com.bbthechange.inviter.dto.*;
import com.bbthechange.inviter.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for hangout retrieval operations.
 *
 * Covers:
 * - Getting hangouts for user from both group and user pointers
 * - GSI query operations and error handling
 * - Hangout detail retrieval with all related data
 * - NeedsRide data transformation
 * - Time info formatting and conversion
 * - HangoutSummaryDTO construction from pointers
 */
class HangoutServiceRetrievalTest extends HangoutServiceTestBase {

    @Test
    void getHangoutsForUser_WithGroupAndUserPointers_Success() {
        // Given
        String userId = "87654321-4321-4321-4321-210987654321";

        // Mock user's groups
        List<GroupMembership> userGroups = List.of(
            createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group One"),
            createTestMembership("22222222-2222-2222-2222-222222222222", userId, "Group Two")
        );
        when(groupRepository.findGroupsByUserId(userId)).thenReturn(userGroups);

        // Create mock hangout pointers from both groups and user
        HangoutPointer groupPointer1 = createTestHangoutPointer("11111111-1111-1111-1111-111111111111", "hangout-1");
        HangoutPointer groupPointer2 = createTestHangoutPointer("22222222-2222-2222-2222-222222222222", "hangout-2");
        HangoutPointer userPointer = createTestHangoutPointer(userId, "hangout-3");

        // Mock GSI queries
        when(hangoutRepository.findUpcomingHangoutsForParticipant("USER#" + userId, "T#"))
            .thenReturn(List.of(userPointer));
        when(hangoutRepository.findUpcomingHangoutsForParticipant("GROUP#11111111-1111-1111-1111-111111111111", "T#"))
            .thenReturn(List.of(groupPointer1));
        when(hangoutRepository.findUpcomingHangoutsForParticipant("GROUP#22222222-2222-2222-2222-222222222222", "T#"))
            .thenReturn(List.of(groupPointer2));

        // No need to mock findHangoutById - convertToSummaryDTO now uses denormalized pointer data

        // When
        List<HangoutSummaryDTO> result = hangoutService.getHangoutsForUser(userId);

        // Then
        assertThat(result).hasSize(3);
        assertThat(result.stream().map(HangoutSummaryDTO::getHangoutId))
            .containsExactlyInAnyOrder("hangout-1", "hangout-2", "hangout-3");

        // Verify GSI queries were made for user and both groups
        verify(hangoutRepository).findUpcomingHangoutsForParticipant("USER#" + userId, "T#");
        verify(hangoutRepository).findUpcomingHangoutsForParticipant("GROUP#11111111-1111-1111-1111-111111111111", "T#");
        verify(hangoutRepository).findUpcomingHangoutsForParticipant("GROUP#22222222-2222-2222-2222-222222222222", "T#");
    }

    @Test
    void getHangoutsForUser_NoGroups_OnlyUserPointers() {
        // Given
        String userId = "87654321-4321-4321-4321-210987654321";

        // Mock user has no groups
        when(groupRepository.findGroupsByUserId(userId)).thenReturn(List.of());

        // Create mock user hangout pointer
        HangoutPointer userPointer = createTestHangoutPointer(userId, "hangout-1");
        when(hangoutRepository.findUpcomingHangoutsForParticipant("USER#" + userId, "T#"))
            .thenReturn(List.of(userPointer));


        // When
        List<HangoutSummaryDTO> result = hangoutService.getHangoutsForUser(userId);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getHangoutId()).isEqualTo("hangout-1");

        // Verify only user query was made
        verify(hangoutRepository).findUpcomingHangoutsForParticipant("USER#" + userId, "T#");
        verify(hangoutRepository, never()).findUpcomingHangoutsForParticipant(startsWith("GROUP#"), anyString());
    }

    @Test
    void getHangoutsForUser_GSIQueryFails_ContinuesWithOtherQueries() {
        // Given
        String userId = "87654321-4321-4321-4321-210987654321";

        // Mock user's groups
        List<GroupMembership> userGroups = List.of(
            createTestMembership("11111111-1111-1111-1111-111111111111", userId, "Group One")
        );
        when(groupRepository.findGroupsByUserId(userId)).thenReturn(userGroups);

        // Mock one GSI query fails, other succeeds
        when(hangoutRepository.findUpcomingHangoutsForParticipant("USER#" + userId, "T#"))
            .thenThrow(new RuntimeException("GSI query failed"));
        when(hangoutRepository.findUpcomingHangoutsForParticipant("GROUP#11111111-1111-1111-1111-111111111111", "T#"))
            .thenReturn(List.of(createTestHangoutPointer("11111111-1111-1111-1111-111111111111", "hangout-1")));


        // When
        List<HangoutSummaryDTO> result = hangoutService.getHangoutsForUser(userId);

        // Then - should still return results from successful queries
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getHangoutId()).isEqualTo("hangout-1");
    }

    @Test
    void getHangoutDetail_WithNeedsRideData_IncludesNeedsRideInDTO() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String requesterUserId = UUID.randomUUID().toString();

        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setTitle("Test Hangout");
        hangout.setVisibility(EventVisibility.PUBLIC);

        NeedsRide needsRide1 = new NeedsRide(hangoutId, userId, "Need a ride from downtown");
        NeedsRide needsRide2 = new NeedsRide(hangoutId, requesterUserId, "Need a ride from airport");
        List<NeedsRide> needsRideList = List.of(needsRide1, needsRide2);

        HangoutDetailData data = HangoutDetailData.builder()
            .withHangout(hangout)
            .withNeedsRide(needsRideList)
            .build();

        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
        when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());

        // When
        HangoutDetailDTO result = hangoutService.getHangoutDetail(hangoutId, requesterUserId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getHangout()).isEqualTo(hangout);
        assertThat(result.getNeedsRide()).hasSize(2);
        assertThat(result.getNeedsRide().get(0).getUserId()).isEqualTo(userId);
        assertThat(result.getNeedsRide().get(0).getNotes()).isEqualTo("Need a ride from downtown");
        assertThat(result.getNeedsRide().get(1).getUserId()).isEqualTo(requesterUserId);
        assertThat(result.getNeedsRide().get(1).getNotes()).isEqualTo("Need a ride from airport");

        verify(hangoutRepository).getHangoutDetailData(hangoutId);
        verify(hangoutRepository).findAttributesByHangoutId(hangoutId);
    }

    @Test
    void getHangoutDetail_WithEmptyNeedsRideData_ReturnsEmptyNeedsRideList() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String requesterUserId = UUID.randomUUID().toString();

        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setTitle("Test Hangout");
        hangout.setVisibility(EventVisibility.PUBLIC);

        HangoutDetailData data = HangoutDetailData.builder().withHangout(hangout).build();

        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
        when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());

        // When
        HangoutDetailDTO result = hangoutService.getHangoutDetail(hangoutId, requesterUserId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getHangout()).isEqualTo(hangout);
        assertThat(result.getNeedsRide()).isEmpty();

        verify(hangoutRepository).getHangoutDetailData(hangoutId);
    }

    @Test
    void getHangoutDetail_WithNullNotesInNeedsRide_HandlesNullGracefully() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String requesterUserId = UUID.randomUUID().toString();

        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setTitle("Test Hangout");
        hangout.setVisibility(EventVisibility.PUBLIC);

        NeedsRide needsRideWithNull = new NeedsRide(hangoutId, userId, null);
        List<NeedsRide> needsRideList = List.of(needsRideWithNull);

        HangoutDetailData data = HangoutDetailData.builder()
            .withHangout(hangout)
            .withNeedsRide(needsRideList)
            .build();

        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
        when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());

        // When
        HangoutDetailDTO result = hangoutService.getHangoutDetail(hangoutId, requesterUserId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNeedsRide()).hasSize(1);
        assertThat(result.getNeedsRide().get(0).getUserId()).isEqualTo(userId);
        assertThat(result.getNeedsRide().get(0).getNotes()).isNull();

        verify(hangoutRepository).getHangoutDetailData(hangoutId);
    }

    @Test
    void getHangoutDetail_WithMixedNeedsRideData_TransformsAllCorrectly() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String user1Id = UUID.randomUUID().toString();
        String user2Id = UUID.randomUUID().toString();
        String user3Id = UUID.randomUUID().toString();
        String requesterUserId = UUID.randomUUID().toString();

        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setTitle("Test Hangout");
        hangout.setVisibility(EventVisibility.PUBLIC);

        NeedsRide needsRide1 = new NeedsRide(hangoutId, user1Id, "Need a ride from downtown");
        NeedsRide needsRide2 = new NeedsRide(hangoutId, user2Id, null);
        NeedsRide needsRide3 = new NeedsRide(hangoutId, user3Id, "");
        List<NeedsRide> needsRideList = List.of(needsRide1, needsRide2, needsRide3);

        HangoutDetailData data = HangoutDetailData.builder()
            .withHangout(hangout)
            .withNeedsRide(needsRideList)
            .build();

        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
        when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());

        // When
        HangoutDetailDTO result = hangoutService.getHangoutDetail(hangoutId, requesterUserId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNeedsRide()).hasSize(3);

        // Verify each needs ride DTO is correctly transformed
        assertThat(result.getNeedsRide().get(0).getUserId()).isEqualTo(user1Id);
        assertThat(result.getNeedsRide().get(0).getNotes()).isEqualTo("Need a ride from downtown");

        assertThat(result.getNeedsRide().get(1).getUserId()).isEqualTo(user2Id);
        assertThat(result.getNeedsRide().get(1).getNotes()).isNull();

        assertThat(result.getNeedsRide().get(2).getUserId()).isEqualTo(user3Id);
        assertThat(result.getNeedsRide().get(2).getNotes()).isEqualTo("");

        verify(hangoutRepository).getHangoutDetailData(hangoutId);
    }

    @Test
    void getHangoutDetail_WithNonExistentHangout_ThrowsException() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String requesterUserId = UUID.randomUUID().toString();

        // Return data with null hangout (simulating not found)
        HangoutDetailData data = HangoutDetailData.builder().withHangout(null).build();

        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);

        // When/Then - Service should throw exception for null hangout (no data exposure)
        assertThatThrownBy(() -> hangoutService.getHangoutDetail(hangoutId, requesterUserId))
                .isInstanceOf(RuntimeException.class); // Could be NPE or other runtime exception

        verify(hangoutRepository).getHangoutDetailData(hangoutId);
    }

    @Test
    void getHangoutDetail_WithComplexData_IncludesNeedsRideAlongsideOtherData() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String requesterUserId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setTitle("Complex Hangout");
        hangout.setVisibility(EventVisibility.PUBLIC);

        // Set up various data types
        List<Car> cars = List.of(new Car(hangoutId, requesterUserId, "Test Driver", 4));
        List<CarRider> carRiders = List.of(new CarRider(hangoutId, requesterUserId, userId, "Test Rider"));
        List<NeedsRide> needsRideList = List.of(new NeedsRide(hangoutId, userId, "Still need a ride"));
        List<InterestLevel> attendance = List.of();
        List<Vote> votes = List.of();

        HangoutDetailData data = HangoutDetailData.builder()
            .withHangout(hangout)
            .withCars(cars)
            .withVotes(votes)
            .withAttendance(attendance)
            .withCarRiders(carRiders)
            .withNeedsRide(needsRideList)
            .build();

        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
        when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());

        // When
        HangoutDetailDTO result = hangoutService.getHangoutDetail(hangoutId, requesterUserId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getHangout()).isEqualTo(hangout);
        assertThat(result.getCars()).hasSize(1);
        assertThat(result.getCarRiders()).hasSize(1);
        assertThat(result.getNeedsRide()).hasSize(1);
        assertThat(result.getNeedsRide().get(0).getUserId()).isEqualTo(userId);
        assertThat(result.getNeedsRide().get(0).getNotes()).isEqualTo("Still need a ride");

        verify(hangoutRepository).getHangoutDetailData(hangoutId);
    }

    @Test
    void formatTimeInfoForResponse_WithUnixTimestamps_ConvertsToISO() {
        // This tests the private method through the public getHangoutDetail method
        // Given
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String userId = "87654321-4321-4321-4321-210987654321";

        Hangout hangout = createTestHangout(hangoutId);
        hangout.setVisibility(EventVisibility.PUBLIC);

        // Set timeInput with Unix timestamps for fuzzy time (only periodGranularity and periodStart)
        TimeInfo timeInput = new TimeInfo();
        timeInput.setPeriodGranularity("evening");
        timeInput.setPeriodStart("1754557200"); // Unix timestamp for 2025-08-05T19:00:00Z
        hangout.setTimeInput(timeInput);

        HangoutDetailData data = HangoutDetailData.builder().withHangout(hangout).build();
        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);

        // When
        HangoutDetailDTO result = hangoutService.getHangoutDetail(hangoutId, userId);

        // Then - fuzzy time only returns periodGranularity and periodStart
        TimeInfo timeInfo = result.getHangout().getTimeInput();
        assertThat(timeInfo.getPeriodStart()).isEqualTo("2025-08-07T09:00:00Z");
        assertThat(timeInfo.getPeriodGranularity()).isEqualTo("evening");
        assertThat(timeInfo.getStartTime()).isNull(); // Not returned for fuzzy time
    }

    @Test
    void hangoutSummaryDTO_Constructor_SetsTimeInfoFromPointer() {
        // Given
        String groupId = "11111111-1111-1111-1111-111111111111";
        String hangoutId = "12345678-1234-1234-1234-123456789012";
        String title = "Test Hangout";

        TimeInfo timeInfo = new TimeInfo();
        timeInfo.setPeriodGranularity("evening");
        timeInfo.setPeriodStart("2025-08-05T19:00:00Z");

        HangoutPointer pointer = new HangoutPointer(groupId, hangoutId, title);
        pointer.setStatus("ACTIVE");
        Address testLocation = new Address();
        testLocation.setName("Test Location");
        pointer.setLocation(testLocation);
        pointer.setParticipantCount(5);
        pointer.setTimeInput(timeInfo);

        // When
        String testUserId = "user-123";
        HangoutSummaryDTO summary = new HangoutSummaryDTO(pointer, testUserId);

        // Then
        assertThat(summary.getHangoutId()).isEqualTo(hangoutId);
        assertThat(summary.getTitle()).isEqualTo(title);
        assertThat(summary.getStatus()).isEqualTo("ACTIVE");
        assertThat(summary.getLocation().getName()).isEqualTo("Test Location");
        assertThat(summary.getParticipantCount()).isEqualTo(5);

        // Verify timeInfo is properly set from pointer's timeInput
        assertThat(summary.getTimeInfo()).isNotNull();
        assertThat(summary.getTimeInfo().getPeriodGranularity()).isEqualTo("evening");
        assertThat(summary.getTimeInfo().getPeriodStart()).isEqualTo("2025-08-05T19:00:00Z");
    }

    // ============================================================================
    // PARTICIPATION & RESERVATION OFFER DENORMALIZATION TESTS
    // ============================================================================

    @Test
    void getHangoutDetail_WithParticipations_DenormalizesUserInfo() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String user1Id = UUID.randomUUID().toString();
        String user2Id = UUID.randomUUID().toString();
        String requesterUserId = UUID.randomUUID().toString();

        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setTitle("Test Hangout");
        hangout.setVisibility(EventVisibility.PUBLIC);

        Participation p1 = new Participation(hangoutId, UUID.randomUUID().toString(), user1Id, ParticipationType.TICKET_NEEDED);
        Participation p2 = new Participation(hangoutId, UUID.randomUUID().toString(), user2Id, ParticipationType.TICKET_PURCHASED);

        UserSummaryDTO user1 = new UserSummaryDTO();
        user1.setId(UUID.fromString(user1Id));
        user1.setDisplayName("Alice");
        user1.setMainImagePath("alice.jpg");
        when(userService.getUserSummary(UUID.fromString(user1Id))).thenReturn(Optional.of(user1));

        UserSummaryDTO user2 = new UserSummaryDTO();
        user2.setId(UUID.fromString(user2Id));
        user2.setDisplayName("Bob");
        user2.setMainImagePath("bob.jpg");

        HangoutDetailData data = HangoutDetailData.builder()
            .withHangout(hangout)
            .withParticipations(List.of(p1, p2))
            .build();

        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
        when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());
        when(userService.getUserSummary(UUID.fromString(user1Id))).thenReturn(Optional.of(user1));
        when(userService.getUserSummary(UUID.fromString(user2Id))).thenReturn(Optional.of(user2));

        // When
        HangoutDetailDTO result = hangoutService.getHangoutDetail(hangoutId, requesterUserId);

        // Then
        assertThat(result.getParticipations()).hasSize(2);
        assertThat(result.getParticipations().get(0).getDisplayName()).isEqualTo("Alice");
        assertThat(result.getParticipations().get(0).getMainImagePath()).isEqualTo("alice.jpg");
        assertThat(result.getParticipations().get(1).getDisplayName()).isEqualTo("Bob");
        assertThat(result.getParticipations().get(1).getMainImagePath()).isEqualTo("bob.jpg");

        verify(userService).getUserSummary(UUID.fromString(user1Id));
        verify(userService).getUserSummary(UUID.fromString(user2Id));
    }

    @Test
    void getHangoutDetail_WithReservationOffers_DenormalizesUserInfo() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String user1Id = UUID.randomUUID().toString();
        String user2Id = UUID.randomUUID().toString();
        String requesterUserId = UUID.randomUUID().toString();

        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setTitle("Test Hangout");
        hangout.setVisibility(EventVisibility.PUBLIC);

        ReservationOffer offer1 = new ReservationOffer(hangoutId, UUID.randomUUID().toString(), user1Id, OfferType.TICKET);
        ReservationOffer offer2 = new ReservationOffer(hangoutId, UUID.randomUUID().toString(), user2Id, OfferType.RESERVATION);

        UserSummaryDTO user1 = new UserSummaryDTO();
        user1.setId(UUID.fromString(user1Id));
        user1.setDisplayName("Charlie");
        user1.setMainImagePath("charlie.jpg");

        UserSummaryDTO user2 = new UserSummaryDTO();
        user2.setId(UUID.fromString(user2Id));
        user2.setDisplayName("Diana");
        user2.setMainImagePath("diana.jpg");

        HangoutDetailData data = HangoutDetailData.builder()
            .withHangout(hangout)
            .withReservationOffers(List.of(offer1, offer2))
            .build();

        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
        when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());
        when(userService.getUserSummary(UUID.fromString(user1Id))).thenReturn(Optional.of(user1));
        when(userService.getUserSummary(UUID.fromString(user2Id))).thenReturn(Optional.of(user2));

        // When
        HangoutDetailDTO result = hangoutService.getHangoutDetail(hangoutId, requesterUserId);

        // Then
        assertThat(result.getReservationOffers()).hasSize(2);
        assertThat(result.getReservationOffers().get(0).getDisplayName()).isEqualTo("Charlie");
        assertThat(result.getReservationOffers().get(0).getMainImagePath()).isEqualTo("charlie.jpg");
        assertThat(result.getReservationOffers().get(1).getDisplayName()).isEqualTo("Diana");
        assertThat(result.getReservationOffers().get(1).getMainImagePath()).isEqualTo("diana.jpg");

        verify(userService).getUserSummary(UUID.fromString(user1Id));
        verify(userService).getUserSummary(UUID.fromString(user2Id));
    }

    @Test
    void getHangoutDetail_WithMissingUser_FiltersOutParticipation() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String user1Id = UUID.randomUUID().toString();
        String user2Id = UUID.randomUUID().toString();
        String requesterUserId = UUID.randomUUID().toString();

        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setTitle("Test Hangout");
        hangout.setVisibility(EventVisibility.PUBLIC);

        Participation p1 = new Participation(hangoutId, UUID.randomUUID().toString(), user1Id, ParticipationType.TICKET_NEEDED);
        Participation p2 = new Participation(hangoutId, UUID.randomUUID().toString(), user2Id, ParticipationType.TICKET_PURCHASED);

        UserSummaryDTO user1 = new UserSummaryDTO();
        user1.setId(UUID.fromString(user1Id));
        user1.setDisplayName("Alice");
        user1.setMainImagePath("alice.jpg");

        HangoutDetailData data = HangoutDetailData.builder()
            .withHangout(hangout)
            .withParticipations(List.of(p1, p2))
            .build();

        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
        when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());
        when(userService.getUserSummary(UUID.fromString(user1Id))).thenReturn(Optional.of(user1));
        when(userService.getUserSummary(UUID.fromString(user2Id))).thenReturn(Optional.empty()); // User not found

        // When
        HangoutDetailDTO result = hangoutService.getHangoutDetail(hangoutId, requesterUserId);

        // Then - only 1 participation (user2's was filtered out)
        assertThat(result.getParticipations()).hasSize(1);
        assertThat(result.getParticipations().get(0).getDisplayName()).isEqualTo("Alice");
        assertThat(result.getParticipations().get(0).getUserId()).isEqualTo(user1Id);
    }

    @Test
    void getHangoutDetail_WithEmptyParticipationsAndOffers_ReturnsEmptyLists() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String requesterUserId = UUID.randomUUID().toString();

        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setTitle("Test Hangout");
        hangout.setVisibility(EventVisibility.PUBLIC);

        HangoutDetailData data = HangoutDetailData.builder()
            .withHangout(hangout)
            .build();  // Empty participations and offers (builder defaults)

        when(hangoutRepository.getHangoutDetailData(hangoutId)).thenReturn(data);
        when(hangoutRepository.findAttributesByHangoutId(hangoutId)).thenReturn(List.of());

        // When
        HangoutDetailDTO result = hangoutService.getHangoutDetail(hangoutId, requesterUserId);

        // Then - empty lists (not null)
        assertThat(result.getParticipations()).isNotNull().isEmpty();
        assertThat(result.getReservationOffers()).isNotNull().isEmpty();

        // Verify user service was never called (no users to fetch)
        verify(userService, never()).getUserById(any(UUID.class));
    }
}
