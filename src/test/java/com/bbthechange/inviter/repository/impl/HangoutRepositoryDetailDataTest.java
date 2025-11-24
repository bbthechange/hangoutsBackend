package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.dto.HangoutDetailData;
import com.bbthechange.inviter.exception.ResourceNotFoundException;
import com.bbthechange.inviter.exception.RepositoryException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for getHangoutDetailData() method in HangoutRepositoryImpl.
 *
 * Covers:
 * - Retrieval of complete hangout detail data with all entity types
 * - Error handling when hangout metadata is missing
 * - Resilience to deserialization errors (filtering bad items)
 *
 * Total tests: 3
 */
class HangoutRepositoryDetailDataTest extends HangoutRepositoryTestBase {

    @Test
    void getHangoutDetailData_WithCompleteEventData_ReturnsAllItems() {
        // Given
        String eventId = UUID.randomUUID().toString();
        String pollId = UUID.randomUUID().toString();
        String optionId = UUID.randomUUID().toString();
        String carId = UUID.randomUUID().toString();
        String riderId = UUID.randomUUID().toString();

        // Create comprehensive mock response with all entity types
        List<Map<String, AttributeValue>> mockItems = Arrays.asList(
            // Hangout metadata
            createMockHangoutMetadataItem(eventId),
            // Poll
            createMockPollItem(eventId, pollId),
            // Poll Option
            createMockPollOptionItem(eventId, pollId, optionId),
            // Car
            createMockCarItem(eventId, carId),
            // Vote
            createMockVoteItem(eventId, pollId, optionId, userId),
            // Interest Level (attendance)
            createMockInterestLevelItem(eventId, userId),
            // Car Rider
            createMockCarRiderItem(eventId, carId, riderId),
            // Needs Ride
            createMockNeedsRideItem(eventId, userId)
        );

        QueryResponse mockResponse = QueryResponse.builder()
            .items(mockItems)
            .build();

        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        HangoutDetailData result = repository.getHangoutDetailData(eventId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getHangout()).isNotNull();
        assertThat(result.getHangout().getHangoutId()).isEqualTo(eventId);
        assertThat(result.getPolls()).hasSize(1);
        assertThat(result.getPollOptions()).hasSize(1);
        assertThat(result.getCars()).hasSize(1);
        assertThat(result.getVotes()).hasSize(1);
        assertThat(result.getAttendance()).hasSize(1);
        assertThat(result.getCarRiders()).hasSize(1);
        assertThat(result.getNeedsRide()).hasSize(1);

        // Verify query construction
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());

        QueryRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.keyConditionExpression()).isEqualTo("pk = :pk");
        assertThat(request.expressionAttributeValues().get(":pk").s()).isEqualTo("EVENT#" + eventId);
        assertThat(request.scanIndexForward()).isTrue();
    }

    @Test
    void getHangoutDetailData_WithNoHangoutMetadata_ThrowsResourceNotFoundException() {
        // Given
        String eventId = UUID.randomUUID().toString();

        // Mock response with empty items - no hangout metadata found
        QueryResponse mockResponse = QueryResponse.builder()
            .items(Collections.emptyList())
            .build();

        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When/Then - Note: ResourceNotFoundException gets wrapped in RepositoryException
        assertThatThrownBy(() -> repository.getHangoutDetailData(eventId))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("Failed to retrieve hangout details")
            .hasCauseInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getHangoutDetailData_WithDeserializationError_FiltersOutBadItems() {
        // Given
        String eventId = UUID.randomUUID().toString();

        List<Map<String, AttributeValue>> mockItems = Arrays.asList(
            // Valid hangout metadata
            createMockHangoutMetadataItem(eventId),
            // Invalid item that will cause deserialization to fail
            createInvalidItem(eventId)
        );

        QueryResponse mockResponse = QueryResponse.builder()
            .items(mockItems)
            .build();

        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        HangoutDetailData result = repository.getHangoutDetailData(eventId);

        // Then - should return hangout but filter out the bad item
        assertThat(result).isNotNull();
        assertThat(result.getHangout()).isNotNull();
        assertThat(result.getPolls()).isEmpty();
        assertThat(result.getPollOptions()).isEmpty();
        assertThat(result.getCars()).isEmpty();
    }

    @Test
    void getHangoutDetailData_ParsesParticipationItems() {
        // Given
        String eventId = UUID.randomUUID().toString();
        String participationId1 = UUID.randomUUID().toString();
        String participationId2 = UUID.randomUUID().toString();

        List<Map<String, AttributeValue>> mockItems = Arrays.asList(
            createMockHangoutMetadataItem(eventId),
            createMockParticipationItem(eventId, participationId1, userId, "TICKET_NEEDED"),
            createMockParticipationItem(eventId, participationId2, userId, "TICKET_PURCHASED")
        );

        QueryResponse mockResponse = QueryResponse.builder()
            .items(mockItems)
            .build();

        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        HangoutDetailData result = repository.getHangoutDetailData(eventId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getHangout()).isNotNull();
        assertThat(result.getParticipations()).hasSize(2);
        assertThat(result.getParticipations().get(0).getParticipationId()).isEqualTo(participationId1);
        assertThat(result.getParticipations().get(1).getParticipationId()).isEqualTo(participationId2);
    }

    @Test
    void getHangoutDetailData_ParsesReservationOfferItems() {
        // Given
        String eventId = UUID.randomUUID().toString();
        String offerId1 = UUID.randomUUID().toString();
        String offerId2 = UUID.randomUUID().toString();

        List<Map<String, AttributeValue>> mockItems = Arrays.asList(
            createMockHangoutMetadataItem(eventId),
            createMockReservationOfferItem(eventId, offerId1, userId, "TICKET"),
            createMockReservationOfferItem(eventId, offerId2, userId, "RESERVATION")
        );

        QueryResponse mockResponse = QueryResponse.builder()
            .items(mockItems)
            .build();

        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        HangoutDetailData result = repository.getHangoutDetailData(eventId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getHangout()).isNotNull();
        assertThat(result.getReservationOffers()).hasSize(2);
        assertThat(result.getReservationOffers().get(0).getOfferId()).isEqualTo(offerId1);
        assertThat(result.getReservationOffers().get(1).getOfferId()).isEqualTo(offerId2);
    }

    @Test
    void getHangoutDetailData_ReturnsEmptyListsWhenNoParticipationsOrOffers() {
        // Given
        String eventId = UUID.randomUUID().toString();

        List<Map<String, AttributeValue>> mockItems = Arrays.asList(
            createMockHangoutMetadataItem(eventId)
            // No participation or offer items
        );

        QueryResponse mockResponse = QueryResponse.builder()
            .items(mockItems)
            .build();

        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(mockResponse);

        // When
        HangoutDetailData result = repository.getHangoutDetailData(eventId);

        // Then - empty lists (not null)
        assertThat(result).isNotNull();
        assertThat(result.getHangout()).isNotNull();
        assertThat(result.getParticipations()).isNotNull().isEmpty();
        assertThat(result.getReservationOffers()).isNotNull().isEmpty();
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    private Map<String, AttributeValue> createMockHangoutMetadataItem(String hangoutId) {
        return Map.of(
            "pk", AttributeValue.builder().s("EVENT#" + hangoutId).build(),
            "sk", AttributeValue.builder().s("METADATA").build(),
            "itemType", AttributeValue.builder().s("HANGOUT").build(),
            "hangoutId", AttributeValue.builder().s(hangoutId).build(),
            "title", AttributeValue.builder().s("Test Hangout").build(),
            "description", AttributeValue.builder().s("Test Description").build()
        );
    }

    private Map<String, AttributeValue> createMockPollItem(String eventId, String pollId) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", AttributeValue.builder().s("EVENT#" + eventId).build());
        item.put("sk", AttributeValue.builder().s("POLL#" + pollId).build());
        item.put("itemType", AttributeValue.builder().s("POLL").build());
        item.put("pollId", AttributeValue.builder().s(pollId).build());
        item.put("question", AttributeValue.builder().s("Test Poll Question").build());
        item.put("eventId", AttributeValue.builder().s(eventId).build());
        return item;
    }

    private Map<String, AttributeValue> createMockPollOptionItem(String eventId, String pollId, String optionId) {
        return Map.of(
            "pk", AttributeValue.builder().s("EVENT#" + eventId).build(),
            "sk", AttributeValue.builder().s("POLL#" + pollId + "#OPTION#" + optionId).build(),
            "itemType", AttributeValue.builder().s("POLL_OPTION").build(),
            "optionId", AttributeValue.builder().s(optionId).build(),
            "text", AttributeValue.builder().s("Test Option").build()
        );
    }

    private Map<String, AttributeValue> createMockCarItem(String eventId, String carId) {
        return Map.of(
            "pk", AttributeValue.builder().s("EVENT#" + eventId).build(),
            "sk", AttributeValue.builder().s("CAR#" + carId).build(),
            "itemType", AttributeValue.builder().s("CAR").build(),
            "driverId", AttributeValue.builder().s(carId).build(),
            "availableSeats", AttributeValue.builder().n("4").build()
        );
    }

    private Map<String, AttributeValue> createMockVoteItem(String eventId, String pollId, String optionId, String userId) {
        return Map.of(
            "pk", AttributeValue.builder().s("EVENT#" + eventId).build(),
            "sk", AttributeValue.builder().s("POLL#" + pollId + "#VOTE#" + userId + "#OPTION#" + optionId).build(),
            "itemType", AttributeValue.builder().s("VOTE").build(),
            "userId", AttributeValue.builder().s(userId).build(),
            "optionId", AttributeValue.builder().s(optionId).build()
        );
    }

    private Map<String, AttributeValue> createMockInterestLevelItem(String eventId, String userId) {
        return Map.of(
            "pk", AttributeValue.builder().s("EVENT#" + eventId).build(),
            "sk", AttributeValue.builder().s("ATTENDANCE#" + userId).build(),
            "itemType", AttributeValue.builder().s("INTEREST_LEVEL").build(),
            "userId", AttributeValue.builder().s(userId).build(),
            "level", AttributeValue.builder().s("GOING").build()
        );
    }

    private Map<String, AttributeValue> createMockCarRiderItem(String eventId, String carId, String riderId) {
        return Map.of(
            "pk", AttributeValue.builder().s("EVENT#" + eventId).build(),
            "sk", AttributeValue.builder().s("CAR#" + carId + "#RIDER#" + riderId).build(),
            "itemType", AttributeValue.builder().s("CAR_RIDER").build(),
            "driverId", AttributeValue.builder().s(carId).build(),
            "riderId", AttributeValue.builder().s(riderId).build()
        );
    }

    private Map<String, AttributeValue> createMockNeedsRideItem(String eventId, String userId) {
        return Map.of(
            "pk", AttributeValue.builder().s("EVENT#" + eventId).build(),
            "sk", AttributeValue.builder().s("NEEDS_RIDE#" + userId).build(),
            "itemType", AttributeValue.builder().s("NEEDS_RIDE").build(),
            "eventId", AttributeValue.builder().s(eventId).build(),
            "userId", AttributeValue.builder().s(userId).build()
        );
    }

    private Map<String, AttributeValue> createInvalidItem(String eventId) {
        // Item with unknown itemType that will cause deserializeItem to fail
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", AttributeValue.builder().s("EVENT#" + eventId).build());
        item.put("sk", AttributeValue.builder().s("UNKNOWN#invalid").build());
        item.put("itemType", AttributeValue.builder().s("UNKNOWN_TYPE").build());
        item.put("someField", AttributeValue.builder().s("someValue").build());
        return item;
    }

    private Map<String, AttributeValue> createMockParticipationItem(String eventId, String participationId, String userId, String type) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", AttributeValue.builder().s("EVENT#" + eventId).build());
        item.put("sk", AttributeValue.builder().s("PARTICIPATION#" + participationId).build());
        item.put("itemType", AttributeValue.builder().s("PARTICIPATION").build());
        item.put("hangoutId", AttributeValue.builder().s(eventId).build());
        item.put("participationId", AttributeValue.builder().s(participationId).build());
        item.put("userId", AttributeValue.builder().s(userId).build());
        item.put("type", AttributeValue.builder().s(type).build());
        return item;
    }

    private Map<String, AttributeValue> createMockReservationOfferItem(String eventId, String offerId, String userId, String type) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", AttributeValue.builder().s("EVENT#" + eventId).build());
        item.put("sk", AttributeValue.builder().s("RESERVEOFFER#" + offerId).build());
        item.put("itemType", AttributeValue.builder().s("RESERVEOFFER").build());
        item.put("hangoutId", AttributeValue.builder().s(eventId).build());
        item.put("offerId", AttributeValue.builder().s(offerId).build());
        item.put("userId", AttributeValue.builder().s(userId).build());
        item.put("type", AttributeValue.builder().s(type).build());
        item.put("status", AttributeValue.builder().s("COLLECTING").build());
        item.put("claimedSpots", AttributeValue.builder().n("0").build());
        return item;
    }
}
