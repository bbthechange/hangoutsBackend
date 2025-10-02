package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.dto.Address;
import com.bbthechange.inviter.model.Place;
import com.bbthechange.inviter.util.InviterKeyFactory;
import com.bbthechange.inviter.util.QueryPerformanceTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaceRepositoryImplTest {

    private static final String TABLE_NAME = "InviterTable";
    private static final String USER_GROUP_INDEX = "UserGroupIndex";

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private QueryPerformanceTracker performanceTracker;

    private PlaceRepositoryImpl repository;
    private TableSchema<Place> placeSchema;

    @BeforeEach
    void setUp() {
        // Set up tracking to properly propagate calls
        lenient().when(performanceTracker.trackQuery(anyString(), anyString(), any())).thenAnswer(invocation -> {
            try {
                java.util.function.Supplier<?> supplier = invocation.getArgument(2);
                return supplier.get();
            } catch (Exception e) {
                throw e;
            }
        });

        repository = new PlaceRepositoryImpl(dynamoDbClient, performanceTracker);
        placeSchema = TableSchema.fromBean(Place.class);
    }

    @Test
    void findPlacesByOwner_UserOwner_ReturnsAllUserPlaces() {
        // Given
        String userId = "12345678-1234-1234-1234-123456789abc";
        String ownerPk = InviterKeyFactory.getUserGsi1Pk(userId);

        Place place1 = createUserPlace(userId, "Home", true);
        Place place2 = createUserPlace(userId, "Work", false);
        Place place3 = createUserPlace(userId, "Gym", false);

        Map<String, AttributeValue> item1 = placeSchema.itemToMap(place1, true);
        Map<String, AttributeValue> item2 = placeSchema.itemToMap(place2, true);
        Map<String, AttributeValue> item3 = placeSchema.itemToMap(place3, true);

        QueryResponse response = QueryResponse.builder()
            .items(item1, item2, item3)
            .build();

        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        // When
        List<Place> result = repository.findPlacesByOwner(ownerPk);

        // Then
        assertThat(result).hasSize(3);
        assertThat(result).extracting(Place::getNickname)
            .containsExactlyInAnyOrder("Home", "Work", "Gym");

        // Verify query uses correct KeyConditionExpression
        ArgumentCaptor<QueryRequest> requestCaptor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(requestCaptor.capture());

        QueryRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.tableName()).isEqualTo(TABLE_NAME);
        assertThat(capturedRequest.keyConditionExpression()).isEqualTo("pk = :pk AND begins_with(sk, :sk_prefix)");
        assertThat(capturedRequest.expressionAttributeValues().get(":pk").s()).isEqualTo(ownerPk);
        assertThat(capturedRequest.expressionAttributeValues().get(":sk_prefix").s()).isEqualTo("PLACE#");
    }

    @Test
    void findPlacesByOwner_GroupOwner_ReturnsGroupPlaces() {
        // Given
        String groupId = "45678901-2345-2345-2345-456789abcdef";
        String ownerPk = InviterKeyFactory.getGroupPk(groupId);

        Place place1 = createGroupPlace(groupId, "Group HQ", "11111111-1111-1111-1111-111111111111");
        Place place2 = createGroupPlace(groupId, "Meeting Room", "22222222-2222-2222-2222-222222222222");

        Map<String, AttributeValue> item1 = placeSchema.itemToMap(place1, true);
        Map<String, AttributeValue> item2 = placeSchema.itemToMap(place2, true);

        QueryResponse response = QueryResponse.builder()
            .items(item1, item2)
            .build();

        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        // When
        List<Place> result = repository.findPlacesByOwner(ownerPk);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Place::getNickname)
            .containsExactlyInAnyOrder("Group HQ", "Meeting Room");

        // Verify partition key
        ArgumentCaptor<QueryRequest> requestCaptor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(requestCaptor.capture());

        QueryRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.expressionAttributeValues().get(":pk").s()).isEqualTo(ownerPk);
    }

    @Test
    void findPrimaryPlaceForUser_ExistsPrimary_ReturnsPlace() {
        // Given
        String userId = "12345678-1234-1234-1234-123456789abc";
        Place primaryPlace = createUserPlace(userId, "Primary Home", true);
        primaryPlace.setGsi1pk(InviterKeyFactory.getUserGsi1Pk(userId));
        primaryPlace.setGsi1sk(InviterKeyFactory.PRIMARY_PLACE);

        Map<String, AttributeValue> item = placeSchema.itemToMap(primaryPlace, true);

        QueryResponse response = QueryResponse.builder()
            .items(item)
            .build();

        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        // When
        Optional<Place> result = repository.findPrimaryPlaceForUser(userId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getNickname()).isEqualTo("Primary Home");

        // Verify GSI query
        ArgumentCaptor<QueryRequest> requestCaptor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(requestCaptor.capture());

        QueryRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.tableName()).isEqualTo(TABLE_NAME);
        assertThat(capturedRequest.indexName()).isEqualTo(USER_GROUP_INDEX);
        assertThat(capturedRequest.keyConditionExpression()).isEqualTo("gsi1pk = :gsi1pk AND gsi1sk = :gsi1sk");
        assertThat(capturedRequest.expressionAttributeValues().get(":gsi1pk").s())
            .isEqualTo(InviterKeyFactory.getUserGsi1Pk(userId));
        assertThat(capturedRequest.expressionAttributeValues().get(":gsi1sk").s())
            .isEqualTo(InviterKeyFactory.PRIMARY_PLACE);
    }

    @Test
    void findPrimaryPlaceForUser_NoPrimary_ReturnsEmpty() {
        // Given
        String userId = "78901234-5678-5678-5678-789012345678";

        QueryResponse response = QueryResponse.builder()
            .items(Collections.emptyList())
            .build();

        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        // When
        Optional<Place> result = repository.findPrimaryPlaceForUser(userId);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void findByOwnerAndPlaceId_Exists_ReturnsPlace() {
        // Given
        String userId = "12345678-1234-1234-1234-123456789abc";
        String placeId = "abcdef01-2345-2345-2345-abcdef012345";
        String ownerPk = InviterKeyFactory.getUserGsi1Pk(userId);

        Place place = createUserPlace(userId, "Test Place", false);
        place.setPlaceId(placeId);

        Map<String, AttributeValue> item = placeSchema.itemToMap(place, true);

        GetItemResponse response = GetItemResponse.builder()
            .item(item)
            .build();

        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

        // When
        Optional<Place> result = repository.findByOwnerAndPlaceId(ownerPk, placeId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getPlaceId()).isEqualTo(placeId);
        assertThat(result.get().getNickname()).isEqualTo("Test Place");

        // Verify GetItem uses exact keys
        ArgumentCaptor<GetItemRequest> requestCaptor = ArgumentCaptor.forClass(GetItemRequest.class);
        verify(dynamoDbClient).getItem(requestCaptor.capture());

        GetItemRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.tableName()).isEqualTo(TABLE_NAME);
        assertThat(capturedRequest.key().get("pk").s()).isEqualTo(ownerPk);
        assertThat(capturedRequest.key().get("sk").s()).isEqualTo(InviterKeyFactory.getPlaceSk(placeId));
    }

    // Helper methods to create test places
    private Place createUserPlace(String userId, String nickname, boolean isPrimary) {
        Address address = new Address();
        address.setStreetAddress("123 Main St");
        address.setCity("TestCity");
        address.setState("TS");
        address.setPostalCode("12345");

        Place place = new Place(userId, nickname, address, "Test notes", isPrimary, userId);
        place.setStatus(InviterKeyFactory.STATUS_ACTIVE);
        return place;
    }

    private Place createGroupPlace(String groupId, String nickname, String createdBy) {
        Address address = new Address();
        address.setStreetAddress("456 Group Ave");
        address.setCity("GroupCity");
        address.setState("GC");
        address.setPostalCode("67890");

        Place place = new Place(groupId, createdBy, nickname, address, "Group notes");
        place.setStatus(InviterKeyFactory.STATUS_ACTIVE);
        return place;
    }
}
