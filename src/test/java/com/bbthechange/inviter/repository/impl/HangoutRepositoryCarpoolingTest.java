package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.exception.RepositoryException;
import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.util.InviterKeyFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for carpooling-related operations in HangoutRepositoryImpl.
 *
 * Coverage:
 * - Needs ride requests (save, query, delete)
 * - Car management (save, delete, error handling)
 * - Car rider relationships (save, delete)
 */
class HangoutRepositoryCarpoolingTest extends HangoutRepositoryTestBase {

    // ============================================================================
    // NEEDS RIDE TESTS
    // ============================================================================

    @Test
    void saveNeedsRide_WithValidData_SavesSuccessfully() {
        // Given
        NeedsRide needsRide = new NeedsRide(eventId, userId, "Need a ride from downtown");
        PutItemResponse response = PutItemResponse.builder().build();
        when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);

        // When
        NeedsRide result = repository.saveNeedsRide(needsRide);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEventId()).isEqualTo(eventId);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getNotes()).isEqualTo("Need a ride from downtown");

        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(captor.capture());

        PutItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.item()).isNotEmpty();
    }

    @Test
    void saveNeedsRide_WithNullNotes_SavesSuccessfully() {
        // Given
        NeedsRide needsRide = new NeedsRide(eventId, userId, null);
        PutItemResponse response = PutItemResponse.builder().build();
        when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);

        // When
        NeedsRide result = repository.saveNeedsRide(needsRide);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEventId()).isEqualTo(eventId);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getNotes()).isNull();
        verify(dynamoDbClient).putItem(any(PutItemRequest.class));
    }

    @Test
    void saveNeedsRide_WithEmptyNotes_SavesSuccessfully() {
        // Given
        NeedsRide needsRide = new NeedsRide(eventId, userId, "");
        PutItemResponse response = PutItemResponse.builder().build();
        when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);

        // When
        NeedsRide result = repository.saveNeedsRide(needsRide);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEventId()).isEqualTo(eventId);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getNotes()).isEqualTo("");
        verify(dynamoDbClient).putItem(any(PutItemRequest.class));
    }

    @Test
    void getNeedsRideListForEvent_WithExistingRequests_QueriesCorrectly() {
        // Given
        QueryResponse response = QueryResponse.builder()
                .items(new ArrayList<>())
                .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        // When
        List<NeedsRide> result = repository.getNeedsRideListForEvent(eventId);

        // Then
        assertThat(result).isNotNull();

        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());

        QueryRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.keyConditionExpression()).isEqualTo("pk = :pk AND begins_with(sk, :sk_prefix)");
        assertThat(request.expressionAttributeValues().get(":pk").s()).isEqualTo("EVENT#" + eventId);
        assertThat(request.expressionAttributeValues().get(":sk_prefix").s()).isEqualTo("NEEDS_RIDE#");
    }

    @Test
    void getNeedsRideListForEvent_WithNoRequests_ReturnsEmptyList() {
        // Given
        QueryResponse response = QueryResponse.builder()
                .items(new ArrayList<>())
                .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        // When
        List<NeedsRide> result = repository.getNeedsRideListForEvent(eventId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
        verify(dynamoDbClient).query(any(QueryRequest.class));
    }

    @Test
    void deleteNeedsRide_WithValidIds_DeletesSuccessfully() {
        // Given
        DeleteItemResponse response = DeleteItemResponse.builder().build();
        when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class))).thenReturn(response);

        // When
        repository.deleteNeedsRide(eventId, userId);

        // Then
        ArgumentCaptor<DeleteItemRequest> captor = ArgumentCaptor.forClass(DeleteItemRequest.class);
        verify(dynamoDbClient).deleteItem(captor.capture());

        DeleteItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.key().get("pk").s()).isEqualTo("EVENT#" + eventId);
        assertThat(request.key().get("sk").s()).isEqualTo("NEEDS_RIDE#" + userId);
    }

    @Test
    void getNeedsRideRequestForUser_WithExistingRequest_ReturnsOptionalWithRequest() {
        // Given
        Map<String, AttributeValue> itemMap = Map.of(
                "pk", AttributeValue.builder().s("EVENT#" + eventId).build(),
                "sk", AttributeValue.builder().s("NEEDS_RIDE#" + userId).build(),
                "eventId", AttributeValue.builder().s(eventId).build(),
                "userId", AttributeValue.builder().s(userId).build(),
                "notes", AttributeValue.builder().s("Need a ride").build()
        );
        GetItemResponse response = GetItemResponse.builder()
                .item(itemMap)
                .build();
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

        // When
        Optional<NeedsRide> result = repository.getNeedsRideRequestForUser(eventId, userId);

        // Then
        assertThat(result).isPresent();

        ArgumentCaptor<GetItemRequest> captor = ArgumentCaptor.forClass(GetItemRequest.class);
        verify(dynamoDbClient).getItem(captor.capture());

        GetItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.key().get("pk").s()).isEqualTo("EVENT#" + eventId);
        assertThat(request.key().get("sk").s()).isEqualTo("NEEDS_RIDE#" + userId);
    }

    @Test
    void getNeedsRideRequestForUser_WithNoExistingRequest_ReturnsEmptyOptional() {
        // Given
        GetItemResponse response = GetItemResponse.builder()
                .item(Map.of()) // Empty item map indicates no item found
                .build();
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

        // When
        Optional<NeedsRide> result = repository.getNeedsRideRequestForUser(eventId, userId);

        // Then
        assertThat(result).isEmpty();

        ArgumentCaptor<GetItemRequest> captor = ArgumentCaptor.forClass(GetItemRequest.class);
        verify(dynamoDbClient).getItem(captor.capture());

        GetItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.key().get("pk").s()).isEqualTo("EVENT#" + eventId);
        assertThat(request.key().get("sk").s()).isEqualTo("NEEDS_RIDE#" + userId);
    }

    @Test
    void getNeedsRideRequestForUser_WithDifferentEventAndUser_QueriesCorrectKeys() {
        // Given
        String differentEventId = UUID.randomUUID().toString();
        String differentUserId = UUID.randomUUID().toString();
        GetItemResponse response = GetItemResponse.builder()
                .item(Map.of())
                .build();
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

        // When
        repository.getNeedsRideRequestForUser(differentEventId, differentUserId);

        // Then
        ArgumentCaptor<GetItemRequest> captor = ArgumentCaptor.forClass(GetItemRequest.class);
        verify(dynamoDbClient).getItem(captor.capture());

        GetItemRequest request = captor.getValue();
        assertThat(request.key().get("pk").s()).isEqualTo("EVENT#" + differentEventId);
        assertThat(request.key().get("sk").s()).isEqualTo("NEEDS_RIDE#" + differentUserId);
    }

    // ============================================================================
    // CAR TESTS
    // ============================================================================

    @Test
    void saveCar_WithValidCar_SavesSuccessfully() {
        // Given
        String eventId = UUID.randomUUID().toString();
        String driverId = UUID.randomUUID().toString();
        Car car = new Car();
        car.setEventId(eventId);
        car.setDriverId(driverId);
        car.setAvailableSeats(4);
        car.setPk(InviterKeyFactory.getEventPk(eventId));
        car.setSk(InviterKeyFactory.getCarSk(driverId));

        PutItemResponse response = PutItemResponse.builder().build();
        when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);

        // When
        Car result = repository.saveCar(car);

        // Then
        assertThat(result).isSameAs(car);
        assertThat(result.getUpdatedAt()).isNotNull();

        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(captor.capture());

        PutItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.item()).containsKey("itemType");
        assertThat(request.item().get("itemType").s()).isEqualTo("CAR");
    }

    @Test
    void saveCar_WithDynamoDbException_ThrowsRepositoryException() {
        // Given
        Car car = new Car();
        car.setDriverId("driverId");

        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
            .thenThrow(DynamoDbException.builder().message("DynamoDB error").build());

        // When/Then
        assertThatThrownBy(() -> repository.saveCar(car))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("Failed to save car")
            .hasCauseInstanceOf(DynamoDbException.class);
    }

    @Test
    void deleteCar_WithValidIds_DeletesSuccessfully() {
        // Given
        String eventId = UUID.randomUUID().toString();
        String driverId = UUID.randomUUID().toString();

        DeleteItemResponse response = DeleteItemResponse.builder().build();
        when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class))).thenReturn(response);

        // When
        repository.deleteCar(eventId, driverId);

        // Then
        ArgumentCaptor<DeleteItemRequest> captor = ArgumentCaptor.forClass(DeleteItemRequest.class);
        verify(dynamoDbClient).deleteItem(captor.capture());

        DeleteItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.key().get("pk").s()).isEqualTo("EVENT#" + eventId);
        assertThat(request.key().get("sk").s()).isEqualTo("CAR#" + driverId);
    }

    // ============================================================================
    // CAR RIDER TESTS
    // ============================================================================

    @Test
    void saveCarRider_WithValidCarRider_SavesSuccessfully() {
        // Given
        String eventId = UUID.randomUUID().toString();
        String driverId = UUID.randomUUID().toString();
        String riderId = UUID.randomUUID().toString();

        CarRider carRider = new CarRider();
        carRider.setEventId(eventId);
        carRider.setDriverId(driverId);
        carRider.setRiderId(riderId);
        carRider.setPk(InviterKeyFactory.getEventPk(eventId));
        carRider.setSk(InviterKeyFactory.getCarRiderSk(driverId, riderId));

        PutItemResponse response = PutItemResponse.builder().build();
        when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);

        // When
        CarRider result = repository.saveCarRider(carRider);

        // Then
        assertThat(result).isSameAs(carRider);
        assertThat(result.getUpdatedAt()).isNotNull();

        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(captor.capture());

        PutItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.item()).containsKey("itemType");
        assertThat(request.item().get("itemType").s()).isEqualTo("CAR_RIDER");
    }

    @Test
    void deleteCarRider_WithValidIds_DeletesSuccessfully() {
        // Given
        String eventId = UUID.randomUUID().toString();
        String driverId = UUID.randomUUID().toString();
        String riderId = UUID.randomUUID().toString();

        DeleteItemResponse response = DeleteItemResponse.builder().build();
        when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class))).thenReturn(response);

        // When
        repository.deleteCarRider(eventId, driverId, riderId);

        // Then
        ArgumentCaptor<DeleteItemRequest> captor = ArgumentCaptor.forClass(DeleteItemRequest.class);
        verify(dynamoDbClient).deleteItem(captor.capture());

        DeleteItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.key().get("pk").s()).isEqualTo("EVENT#" + eventId);
        assertThat(request.key().get("sk").s()).contains("CAR#");
        assertThat(request.key().get("sk").s()).contains("RIDER#");
    }
}
