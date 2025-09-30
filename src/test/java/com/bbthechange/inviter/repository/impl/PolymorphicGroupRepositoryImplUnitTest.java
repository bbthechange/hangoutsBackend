package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.exception.RepositoryException;
import com.bbthechange.inviter.util.QueryPerformanceTracker;
import com.bbthechange.inviter.util.InviterKeyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PolymorphicGroupRepositoryImpl focusing on behavior verification
 * without using DynamoDBLocal.
 */
@ExtendWith(MockitoExtension.class)
class PolymorphicGroupRepositoryImplUnitTest {

    private static final String TABLE_NAME = "InviterTable";

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private QueryPerformanceTracker performanceTracker;

    private PolymorphicGroupRepositoryImpl repository;

    private String groupId;
    private String userId;
    private String hangoutId;
    private String seriesId;

    @BeforeEach
    void setUp() throws Exception {
        // Set up performance tracker to call suppliers and properly propagate exceptions
        lenient().when(performanceTracker.trackQuery(anyString(), anyString(), any())).thenAnswer(invocation -> {
            try {
                java.util.function.Supplier<?> supplier = invocation.getArgument(2);
                return supplier.get();
            } catch (Exception e) {
                // Re-throw the exception to ensure proper test behavior
                throw e;
            }
        });

        repository = new PolymorphicGroupRepositoryImpl(dynamoDbClient, performanceTracker);

        groupId = UUID.randomUUID().toString();
        userId = UUID.randomUUID().toString();
        hangoutId = UUID.randomUUID().toString();
        seriesId = UUID.randomUUID().toString();
    }

    // ============================================================================
    // POLYMORPHIC DESERIALIZATION TESTS (VIA PUBLIC METHODS)
    // ============================================================================
    
    @Test
    void findById_WithNoItemFound_ReturnsEmpty() {
        // Given
        GetItemResponse response = GetItemResponse.builder()
            .build(); // No item
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

        // When
        Optional<Group> result = repository.findById(groupId);

        // Then
        assertThat(result).isEmpty();
    }
    
    @Test
    void findById_WithValidGroupData_ReturnsGroupCorrectly() {
        // Given
        Map<String, AttributeValue> groupItem = createGroupItemMap();
        assertThat(groupItem).isNotEmpty(); // Verify our test data is correct
        
        GetItemResponse response = GetItemResponse.builder()
            .item(groupItem)
            .build();
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

        // When
        Optional<Group> result = repository.findById(groupId);

        // Then
        assertThat(result).isPresent();
        Group group = result.get();
        assertThat(group.getGroupId()).isEqualTo(groupId);
        assertThat(group.getGroupName()).isEqualTo("Test Group");
        assertThat(group.isPublic()).isTrue();
        
        // Verify the correct key was used in the request
        ArgumentCaptor<GetItemRequest> captor = ArgumentCaptor.forClass(GetItemRequest.class);
        verify(dynamoDbClient).getItem(captor.capture());
        
        GetItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.key().get("pk").s()).isEqualTo("GROUP#" + groupId);
        assertThat(request.key().get("sk").s()).isEqualTo("METADATA");
    }
    
    @Test
    void findMembersByGroupId_WithMultipleMembers_ReturnsCorrectGroupMemberships() {
        // Given
        String userId2 = UUID.randomUUID().toString();
        List<Map<String, AttributeValue>> memberItems = Arrays.asList(
            createGroupMembershipItemMap(),
            createGroupMembershipItemMap(userId2)
        );
        
        QueryResponse response = QueryResponse.builder()
            .items(memberItems)
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        // When
        List<GroupMembership> result = repository.findMembersByGroupId(groupId);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isInstanceOf(GroupMembership.class);
        assertThat(result.get(0).getGroupId()).isEqualTo(groupId);
        assertThat(result.get(1)).isInstanceOf(GroupMembership.class);
        assertThat(result.get(1).getGroupId()).isEqualTo(groupId);
    }
    
    @Test
    void findHangoutsByGroupId_WithMultipleHangouts_ReturnsCorrectHangoutPointers() {
        // Given
        String hangoutId2 = UUID.randomUUID().toString();
        List<Map<String, AttributeValue>> hangoutItems = Arrays.asList(
            createHangoutPointerItemMap(),
            createHangoutPointerItemMap(hangoutId2)
        );
        
        QueryResponse response = QueryResponse.builder()
            .items(hangoutItems)
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        // When
        List<HangoutPointer> result = repository.findHangoutsByGroupId(groupId);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isInstanceOf(HangoutPointer.class);
        assertThat(result.get(0).getGroupId()).isEqualTo(groupId);
        assertThat(result.get(1)).isInstanceOf(HangoutPointer.class);
        assertThat(result.get(1).getGroupId()).isEqualTo(groupId);
    }

    // ============================================================================
    // ERROR HANDLING TESTS
    // ============================================================================

    @Test
    void save_WithDynamoDbException_WrapsInRepositoryException() {
        // Given
        Group group = new Group("Test Group", true);
        group.setGroupId(groupId);
        group.setPk(InviterKeyFactory.getGroupPk(groupId));
        group.setSk(InviterKeyFactory.getMetadataSk());

        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
            .thenThrow(DynamoDbException.builder().message("DynamoDB error").build());

        // When/Then
        assertThatThrownBy(() -> repository.save(group))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("Failed to save group")
            .hasCauseInstanceOf(DynamoDbException.class);
    }

    @Test
    void findById_WithDynamoDbException_WrapsInRepositoryException() {
        // Given
        when(dynamoDbClient.getItem(any(GetItemRequest.class)))
            .thenThrow(DynamoDbException.builder().message("DynamoDB error").build());

        // When/Then
        assertThatThrownBy(() -> repository.findById(groupId))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("Failed to retrieve group")
            .hasCauseInstanceOf(DynamoDbException.class);
    }

    @Test
    void addMember_WithDynamoDbException_WrapsInRepositoryException() {
        // Given
        GroupMembership membership = new GroupMembership(groupId, userId, "Test Group");
        membership.setPk(InviterKeyFactory.getGroupPk(groupId));
        membership.setSk(InviterKeyFactory.getUserSk(userId));

        when(dynamoDbClient.putItem(any(PutItemRequest.class)))
            .thenThrow(DynamoDbException.builder().message("DynamoDB error").build());

        // When/Then
        assertThatThrownBy(() -> repository.addMember(membership))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("Failed to add group member")
            .hasCauseInstanceOf(DynamoDbException.class);
    }

    // ============================================================================
    // QUERY CONSTRUCTION TESTS
    // ============================================================================

    @Test
    void findMembersByGroupId_ConstructsCorrectQuery() {
        // Given
        QueryResponse response = QueryResponse.builder()
            .items(new ArrayList<>())
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        // When
        repository.findMembersByGroupId(groupId);

        // Then
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());

        QueryRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.keyConditionExpression()).isEqualTo("pk = :pk AND begins_with(sk, :sk)");
        assertThat(request.expressionAttributeValues().get(":pk").s()).isEqualTo("GROUP#" + groupId);
        assertThat(request.expressionAttributeValues().get(":sk").s()).isEqualTo("USER");
    }

    @Test
    void findGroupsByUserId_ConstructsCorrectGSIQuery() {
        // Given
        QueryResponse response = QueryResponse.builder()
            .items(new ArrayList<>())
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        // When
        repository.findGroupsByUserId(userId);

        // Then
        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());

        QueryRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.indexName()).isEqualTo("UserGroupIndex");
        assertThat(request.keyConditionExpression()).isEqualTo("gsi1pk = :gsi1pk");
        assertThat(request.expressionAttributeValues().get(":gsi1pk").s()).isEqualTo("USER#" + userId);
    }

    @Test
    void isUserMemberOfGroup_UsesCountQuery() {
        // Given
        QueryResponse response = QueryResponse.builder()
            .count(1)
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        // When
        boolean result = repository.isUserMemberOfGroup(groupId, userId);

        // Then
        assertThat(result).isTrue();

        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());

        QueryRequest request = captor.getValue();
        assertThat(request.select()).isEqualTo(Select.COUNT);
        assertThat(request.keyConditionExpression()).isEqualTo("pk = :pk AND sk = :sk");
        assertThat(request.expressionAttributeValues().get(":pk").s()).isEqualTo("GROUP#" + groupId);
        assertThat(request.expressionAttributeValues().get(":sk").s()).isEqualTo("USER#" + userId);
    }

    @Test
    void isUserMemberOfGroup_WithZeroCount_ReturnsFalse() {
        // Given
        QueryResponse response = QueryResponse.builder()
            .count(0)
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

        // When
        boolean result = repository.isUserMemberOfGroup(groupId, userId);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void atomicallyUpdateParticipantCount_WithZeroDelta_ReturnsEarly() {
        // Given - delta is 0, should return early without any DynamoDB call

        // When
        repository.atomicallyUpdateParticipantCount(groupId, hangoutId, 0);

        // Then
        verifyNoInteractions(dynamoDbClient);
    }

    @Test
    void atomicallyUpdateParticipantCount_WithPositiveDelta_ConstructsCorrectUpdateExpression() {
        // Given
        UpdateItemResponse response = UpdateItemResponse.builder().build();
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenReturn(response);

        // When
        repository.atomicallyUpdateParticipantCount(groupId, hangoutId, 5);

        // Then
        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDbClient).updateItem(captor.capture());

        UpdateItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo("InviterTable");
        assertThat(request.key().get("pk").s()).isEqualTo("GROUP#" + groupId);
        assertThat(request.key().get("sk").s()).isEqualTo("HANGOUT#" + hangoutId);
        assertThat(request.updateExpression()).isEqualTo("SET participantCount = if_not_exists(participantCount, :zero) + :delta, updatedAt = :timestamp");
        assertThat(request.expressionAttributeValues().get(":delta").n()).isEqualTo("5");
        assertThat(request.expressionAttributeValues().get(":zero").n()).isEqualTo("0");
        assertThat(request.expressionAttributeValues()).containsKey(":timestamp");
    }

    @Test
    void atomicallyUpdateParticipantCount_WithNegativeDelta_ConstructsCorrectUpdateExpression() {
        // Given
        UpdateItemResponse response = UpdateItemResponse.builder().build();
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenReturn(response);

        // When
        repository.atomicallyUpdateParticipantCount(groupId, hangoutId, -3);

        // Then
        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDbClient).updateItem(captor.capture());

        UpdateItemRequest request = captor.getValue();
        assertThat(request.expressionAttributeValues().get(":delta").n()).isEqualTo("-3");
    }

    // ============================================================================
    // HELPER METHODS FOR CREATING MOCK ITEMS
    // ============================================================================

    private Map<String, AttributeValue> createGroupItemMap() {
        return Map.of(
            "pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build(),
            "sk", AttributeValue.builder().s("METADATA").build(),
            "itemType", AttributeValue.builder().s("GROUP").build(),
            "groupId", AttributeValue.builder().s(groupId).build(),
            "groupName", AttributeValue.builder().s("Test Group").build(),
            "public", AttributeValue.builder().bool(true).build()
        );
    }

    private Map<String, AttributeValue> createGroupMembershipItemMap() {
        return createGroupMembershipItemMap(userId);
    }
    
    private Map<String, AttributeValue> createGroupMembershipItemMap(String memberId) {
        return Map.of(
            "pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build(),
            "sk", AttributeValue.builder().s(InviterKeyFactory.getUserSk(memberId)).build(),
            "itemType", AttributeValue.builder().s("GROUP_MEMBERSHIP").build(),
            "groupId", AttributeValue.builder().s(groupId).build(),
            "userId", AttributeValue.builder().s(memberId).build(),
            "groupName", AttributeValue.builder().s("Test Group").build()
        );
    }

    private Map<String, AttributeValue> createHangoutPointerItemMap() {
        return createHangoutPointerItemMap(hangoutId);
    }
    
    private Map<String, AttributeValue> createHangoutPointerItemMap(String eventId) {
        return Map.of(
            "pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build(),
            "sk", AttributeValue.builder().s(InviterKeyFactory.getHangoutSk(eventId)).build(),
            "itemType", AttributeValue.builder().s("HANGOUT_POINTER").build(),
            "groupId", AttributeValue.builder().s(groupId).build(),
            "hangoutId", AttributeValue.builder().s(eventId).build(),
            "title", AttributeValue.builder().s("Test Hangout").build(),
            "participantCount", AttributeValue.builder().n("10").build()
        );
    }

    private Map<String, AttributeValue> createSeriesPointerItemMap() {
        return Map.of(
            "pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build(),
            "sk", AttributeValue.builder().s(InviterKeyFactory.getSeriesSk(seriesId)).build(),
            "itemType", AttributeValue.builder().s("SERIES_POINTER").build(),
            "seriesId", AttributeValue.builder().s(seriesId).build(),
            "seriesTitle", AttributeValue.builder().s("Test Series").build()
        );
    }

    // ============================================================================
    // IMAGE PATH DENORMALIZATION TESTS
    // ============================================================================

    @Test
    void updateMembershipGroupImagePaths_UpdatesAllMembershipsForGroup() {
        // Given
        String userId1 = UUID.randomUUID().toString();
        String userId2 = UUID.randomUUID().toString();
        String userId3 = UUID.randomUUID().toString();

        // Mock findMembersByGroupId to return 3 memberships
        List<GroupMembership> memberships = Arrays.asList(
            createTestMembership(groupId, userId1),
            createTestMembership(groupId, userId2),
            createTestMembership(groupId, userId3)
        );

        QueryResponse queryResponse = QueryResponse.builder()
            .items(Arrays.asList(
                createGroupMembershipItemMap(userId1),
                createGroupMembershipItemMap(userId2),
                createGroupMembershipItemMap(userId3)
            ))
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);

        // Mock transactWriteItems to succeed
        TransactWriteItemsResponse transactResponse = TransactWriteItemsResponse.builder().build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class))).thenReturn(transactResponse);

        // When
        repository.updateMembershipGroupImagePaths(groupId, "/group-main.jpg", "/group-bg.jpg");

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());

        TransactWriteItemsRequest request = captor.getValue();
        assertThat(request.transactItems()).hasSize(3);

        // Verify all 3 membership updates have correct structure
        for (int i = 0; i < 3; i++) {
            TransactWriteItem item = request.transactItems().get(i);
            assertThat(item.update()).isNotNull();
            assertThat(item.update().tableName()).isEqualTo("InviterTable");
            assertThat(item.update().updateExpression())
                .isEqualTo("SET groupMainImagePath = :mainImagePath, groupBackgroundImagePath = :backgroundImagePath, updatedAt = :timestamp");
            assertThat(item.update().expressionAttributeValues().get(":mainImagePath").s()).isEqualTo("/group-main.jpg");
            assertThat(item.update().expressionAttributeValues().get(":backgroundImagePath").s()).isEqualTo("/group-bg.jpg");
            assertThat(item.update().expressionAttributeValues()).containsKey(":timestamp");
        }
    }

    @Test
    void updateMembershipGroupImagePaths_HandlesNullImagePaths() {
        // Given
        String userId1 = UUID.randomUUID().toString();

        QueryResponse queryResponse = QueryResponse.builder()
            .items(Arrays.asList(createGroupMembershipItemMap(userId1)))
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);

        TransactWriteItemsResponse transactResponse = TransactWriteItemsResponse.builder().build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class))).thenReturn(transactResponse);

        // When
        repository.updateMembershipGroupImagePaths(groupId, null, null);

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());

        TransactWriteItemsRequest request = captor.getValue();
        assertThat(request.transactItems()).hasSize(1);

        TransactWriteItem item = request.transactItems().get(0);
        // Verify null values are converted to empty strings for DynamoDB
        assertThat(item.update().expressionAttributeValues().get(":mainImagePath").s()).isEqualTo("");
        assertThat(item.update().expressionAttributeValues().get(":backgroundImagePath").s()).isEqualTo("");
    }

    @Test
    void updateMembershipGroupImagePaths_BatchesLargeGroups() {
        // Given - 60 memberships should be split into 3 batches (25, 25, 10)
        List<Map<String, AttributeValue>> membershipItems = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            String memberId = UUID.randomUUID().toString();
            membershipItems.add(createGroupMembershipItemMap(memberId));
        }

        QueryResponse queryResponse = QueryResponse.builder()
            .items(membershipItems)
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);

        TransactWriteItemsResponse transactResponse = TransactWriteItemsResponse.builder().build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class))).thenReturn(transactResponse);

        // When
        repository.updateMembershipGroupImagePaths(groupId, "/group-main.jpg", "/group-bg.jpg");

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient, times(3)).transactWriteItems(captor.capture());

        List<TransactWriteItemsRequest> requests = captor.getAllValues();
        assertThat(requests).hasSize(3);

        // Verify batch sizes: 25, 25, 10
        assertThat(requests.get(0).transactItems()).hasSize(25);
        assertThat(requests.get(1).transactItems()).hasSize(25);
        assertThat(requests.get(2).transactItems()).hasSize(10);
    }

    @Test
    void updateMembershipGroupImagePaths_ReturnsEarlyWhenNoMemberships() {
        // Given - no memberships
        QueryResponse queryResponse = QueryResponse.builder()
            .items(new ArrayList<>())
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);

        // When
        repository.updateMembershipGroupImagePaths(groupId, "/group-main.jpg", "/group-bg.jpg");

        // Then - should not call transactWriteItems at all
        verify(dynamoDbClient, never()).transactWriteItems(any(TransactWriteItemsRequest.class));
    }

    @Test
    void updateMembershipUserImagePath_UpdatesAllMembershipsForUser() {
        // Given
        String groupId1 = UUID.randomUUID().toString();
        String groupId2 = UUID.randomUUID().toString();

        // Mock findGroupsByUserId to return 2 memberships (user is in 2 groups)
        List<Map<String, AttributeValue>> membershipItems = Arrays.asList(
            createGroupMembershipItemForGroup(groupId1, userId),
            createGroupMembershipItemForGroup(groupId2, userId)
        );

        QueryResponse queryResponse = QueryResponse.builder()
            .items(membershipItems)
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);

        TransactWriteItemsResponse transactResponse = TransactWriteItemsResponse.builder().build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class))).thenReturn(transactResponse);

        // When
        repository.updateMembershipUserImagePath(userId, "/user-avatar.jpg");

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());

        TransactWriteItemsRequest request = captor.getValue();
        assertThat(request.transactItems()).hasSize(2);

        // Verify both membership updates have correct structure
        for (int i = 0; i < 2; i++) {
            TransactWriteItem item = request.transactItems().get(i);
            assertThat(item.update()).isNotNull();
            assertThat(item.update().tableName()).isEqualTo("InviterTable");
            assertThat(item.update().updateExpression())
                .isEqualTo("SET userMainImagePath = :mainImagePath, updatedAt = :timestamp");
            assertThat(item.update().expressionAttributeValues().get(":mainImagePath").s()).isEqualTo("/user-avatar.jpg");
            assertThat(item.update().expressionAttributeValues()).containsKey(":timestamp");
        }
    }

    @Test
    void updateMembershipUserImagePath_BatchesUsersInManyGroups() {
        // Given - 40 memberships should be split into 2 batches (25, 15)
        List<Map<String, AttributeValue>> membershipItems = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            String groupIdForMembership = UUID.randomUUID().toString();
            membershipItems.add(createGroupMembershipItemForGroup(groupIdForMembership, userId));
        }

        QueryResponse queryResponse = QueryResponse.builder()
            .items(membershipItems)
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);

        TransactWriteItemsResponse transactResponse = TransactWriteItemsResponse.builder().build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class))).thenReturn(transactResponse);

        // When
        repository.updateMembershipUserImagePath(userId, "/user-avatar.jpg");

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient, times(2)).transactWriteItems(captor.capture());

        List<TransactWriteItemsRequest> requests = captor.getAllValues();
        assertThat(requests).hasSize(2);

        // Verify batch sizes: 25, 15
        assertThat(requests.get(0).transactItems()).hasSize(25);
        assertThat(requests.get(1).transactItems()).hasSize(15);
    }

    // Helper method for creating test membership
    private GroupMembership createTestMembership(String groupId, String userId) {
        GroupMembership membership = new GroupMembership(groupId, userId, "Test Group");
        membership.setPk(InviterKeyFactory.getGroupPk(groupId));
        membership.setSk(InviterKeyFactory.getUserSk(userId));
        return membership;
    }

    // Helper method for creating membership item with specific groupId
    private Map<String, AttributeValue> createGroupMembershipItemForGroup(String groupIdParam, String userIdParam) {
        return Map.of(
            "pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupIdParam)).build(),
            "sk", AttributeValue.builder().s(InviterKeyFactory.getUserSk(userIdParam)).build(),
            "itemType", AttributeValue.builder().s("GROUP_MEMBERSHIP").build(),
            "groupId", AttributeValue.builder().s(groupIdParam).build(),
            "userId", AttributeValue.builder().s(userIdParam).build(),
            "groupName", AttributeValue.builder().s("Test Group").build()
        );
    }
}