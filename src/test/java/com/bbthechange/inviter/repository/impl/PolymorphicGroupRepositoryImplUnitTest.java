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

    // ============================================================================
    // DELETE METHOD TESTS (Complex batch deletion)
    // ============================================================================

    @Test
    void delete_WithMultipleRecords_DeletesAllInBatches() {
        // Given - 30 records (group metadata + 29 memberships)
        List<Map<String, AttributeValue>> items = new ArrayList<>();

        // Add group metadata
        items.add(createGroupItemMap());

        // Add 29 membership records
        for (int i = 0; i < 29; i++) {
            String memberId = UUID.randomUUID().toString();
            items.add(createGroupMembershipItemMap(memberId));
        }

        QueryResponse queryResponse = QueryResponse.builder()
            .items(items)
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);

        BatchWriteItemResponse batchResponse = BatchWriteItemResponse.builder().build();
        when(dynamoDbClient.batchWriteItem(any(BatchWriteItemRequest.class))).thenReturn(batchResponse);

        // When
        repository.delete(groupId);

        // Then
        // Verify query was executed
        ArgumentCaptor<QueryRequest> queryCaptor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(queryCaptor.capture());

        QueryRequest queryRequest = queryCaptor.getValue();
        assertThat(queryRequest.tableName()).isEqualTo("InviterTable");
        assertThat(queryRequest.keyConditionExpression()).isEqualTo("pk = :pk");
        assertThat(queryRequest.expressionAttributeValues().get(":pk").s()).isEqualTo("GROUP#" + groupId);

        // Verify batch delete was called twice (30 items = 25 + 5)
        ArgumentCaptor<BatchWriteItemRequest> batchCaptor = ArgumentCaptor.forClass(BatchWriteItemRequest.class);
        verify(dynamoDbClient, times(2)).batchWriteItem(batchCaptor.capture());

        List<BatchWriteItemRequest> batchRequests = batchCaptor.getAllValues();
        assertThat(batchRequests.get(0).requestItems().get(TABLE_NAME)).hasSize(25);
        assertThat(batchRequests.get(1).requestItems().get(TABLE_NAME)).hasSize(5);
    }

    @Test
    void delete_WithNoRecords_ReturnsEarly() {
        // Given
        QueryResponse queryResponse = QueryResponse.builder()
            .items(new ArrayList<>())
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);

        // When
        repository.delete(groupId);

        // Then - should not call batchWriteItem
        verify(dynamoDbClient, never()).batchWriteItem(any(BatchWriteItemRequest.class));
    }

    @Test
    void delete_WithExactly25Records_DeletesInOneBatch() {
        // Given - exactly 25 records
        List<Map<String, AttributeValue>> items = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            items.add(createGroupMembershipItemMap(UUID.randomUUID().toString()));
        }

        QueryResponse queryResponse = QueryResponse.builder()
            .items(items)
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);

        BatchWriteItemResponse batchResponse = BatchWriteItemResponse.builder().build();
        when(dynamoDbClient.batchWriteItem(any(BatchWriteItemRequest.class))).thenReturn(batchResponse);

        // When
        repository.delete(groupId);

        // Then - should call batchWriteItem exactly once
        ArgumentCaptor<BatchWriteItemRequest> batchCaptor = ArgumentCaptor.forClass(BatchWriteItemRequest.class);
        verify(dynamoDbClient, times(1)).batchWriteItem(batchCaptor.capture());

        BatchWriteItemRequest request = batchCaptor.getValue();
        assertThat(request.requestItems().get(TABLE_NAME)).hasSize(25);
    }

    @Test
    void delete_WithDynamoDbException_WrapsInRepositoryException() {
        // Given
        when(dynamoDbClient.query(any(QueryRequest.class)))
            .thenThrow(DynamoDbException.builder().message("DynamoDB error").build());

        // When/Then
        assertThatThrownBy(() -> repository.delete(groupId))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("Failed to delete group completely")
            .hasCauseInstanceOf(DynamoDbException.class);
    }

    // ============================================================================
    // TRANSACTION TESTS (createGroupWithFirstMember)
    // ============================================================================

    @Test
    void createGroupWithFirstMember_CreatesGroupAndMembershipAtomically() {
        // Given
        Group group = new Group("Test Group", true);
        group.setGroupId(groupId);
        group.setPk(InviterKeyFactory.getGroupPk(groupId));
        group.setSk(InviterKeyFactory.getMetadataSk());

        GroupMembership membership = new GroupMembership(groupId, userId, "Test Group");
        membership.setPk(InviterKeyFactory.getGroupPk(groupId));
        membership.setSk(InviterKeyFactory.getUserSk(userId));

        TransactWriteItemsResponse response = TransactWriteItemsResponse.builder().build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class))).thenReturn(response);

        // When
        repository.createGroupWithFirstMember(group, membership);

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());

        TransactWriteItemsRequest request = captor.getValue();
        assertThat(request.transactItems()).hasSize(2);

        // Verify first item is the group
        TransactWriteItem groupItem = request.transactItems().get(0);
        assertThat(groupItem.put()).isNotNull();
        assertThat(groupItem.put().tableName()).isEqualTo(TABLE_NAME);
        assertThat(groupItem.put().item().get("groupId").s()).isEqualTo(groupId);

        // Verify second item is the membership
        TransactWriteItem membershipItem = request.transactItems().get(1);
        assertThat(membershipItem.put()).isNotNull();
        assertThat(membershipItem.put().tableName()).isEqualTo(TABLE_NAME);
        assertThat(membershipItem.put().item().get("userId").s()).isEqualTo(userId);
    }

    @Test
    void createGroupWithFirstMember_WithDynamoDbException_WrapsInRepositoryException() {
        // Given
        Group group = new Group("Test Group", true);
        group.setGroupId(groupId);
        group.setPk(InviterKeyFactory.getGroupPk(groupId));
        group.setSk(InviterKeyFactory.getMetadataSk());

        GroupMembership membership = new GroupMembership(groupId, userId, "Test Group");

        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
            .thenThrow(DynamoDbException.builder().message("Transaction failed").build());

        // When/Then
        assertThatThrownBy(() -> repository.createGroupWithFirstMember(group, membership))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("Failed to create group with first member")
            .hasCauseInstanceOf(DynamoDbException.class);
    }

    // ============================================================================
    // DYNAMIC UPDATE EXPRESSION TESTS (updateHangoutPointer)
    // ============================================================================

    @Test
    void updateHangoutPointer_WithSingleUpdate_BuildsCorrectExpression() {
        // Given
        Map<String, AttributeValue> updates = Map.of(
            "title", AttributeValue.builder().s("Updated Title").build()
        );

        UpdateItemResponse response = UpdateItemResponse.builder().build();
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenReturn(response);

        // When
        repository.updateHangoutPointer(groupId, hangoutId, updates);

        // Then
        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDbClient).updateItem(captor.capture());

        UpdateItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo(TABLE_NAME);
        assertThat(request.key().get("pk").s()).isEqualTo("GROUP#" + groupId);
        assertThat(request.key().get("sk").s()).isEqualTo("HANGOUT#" + hangoutId);

        // Verify update expression includes both the provided field and updatedAt
        assertThat(request.updateExpression()).contains("SET title = :val0");
        assertThat(request.updateExpression()).contains("updatedAt = :updatedAt");
        assertThat(request.expressionAttributeValues().get(":val0").s()).isEqualTo("Updated Title");
        assertThat(request.expressionAttributeValues()).containsKey(":updatedAt");
    }

    @Test
    void updateHangoutPointer_WithMultipleUpdates_BuildsCorrectExpression() {
        // Given
        Map<String, AttributeValue> updates = new LinkedHashMap<>();
        updates.put("title", AttributeValue.builder().s("New Title").build());
        updates.put("participantCount", AttributeValue.builder().n("15").build());
        updates.put("description", AttributeValue.builder().s("New Description").build());

        UpdateItemResponse response = UpdateItemResponse.builder().build();
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenReturn(response);

        // When
        repository.updateHangoutPointer(groupId, hangoutId, updates);

        // Then
        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDbClient).updateItem(captor.capture());

        UpdateItemRequest request = captor.getValue();

        // Verify all three fields are in the update expression
        assertThat(request.updateExpression()).contains("title = :val0");
        assertThat(request.updateExpression()).contains("participantCount = :val1");
        assertThat(request.updateExpression()).contains("description = :val2");
        assertThat(request.updateExpression()).contains("updatedAt = :updatedAt");

        // Verify all values are present
        assertThat(request.expressionAttributeValues().get(":val0").s()).isEqualTo("New Title");
        assertThat(request.expressionAttributeValues().get(":val1").n()).isEqualTo("15");
        assertThat(request.expressionAttributeValues().get(":val2").s()).isEqualTo("New Description");
    }

    @Test
    void updateHangoutPointer_WithDynamoDbException_WrapsInRepositoryException() {
        // Given
        Map<String, AttributeValue> updates = Map.of(
            "title", AttributeValue.builder().s("Updated Title").build()
        );

        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
            .thenThrow(DynamoDbException.builder().message("Update failed").build());

        // When/Then
        assertThatThrownBy(() -> repository.updateHangoutPointer(groupId, hangoutId, updates))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("Failed to update hangout pointer")
            .hasCauseInstanceOf(DynamoDbException.class);
    }

    // ============================================================================
    // BATCH UPDATE TESTS (updateMembershipGroupNames)
    // ============================================================================

    @Test
    void updateMembershipGroupNames_UpdatesAllMembershipNames() {
        // Given
        String userId1 = UUID.randomUUID().toString();
        String userId2 = UUID.randomUUID().toString();

        QueryResponse queryResponse = QueryResponse.builder()
            .items(Arrays.asList(
                createGroupMembershipItemMap(userId1),
                createGroupMembershipItemMap(userId2)
            ))
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);

        TransactWriteItemsResponse transactResponse = TransactWriteItemsResponse.builder().build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class))).thenReturn(transactResponse);

        // When
        repository.updateMembershipGroupNames(groupId, "Updated Group Name");

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());

        TransactWriteItemsRequest request = captor.getValue();
        assertThat(request.transactItems()).hasSize(2);

        // Verify both updates have correct structure
        for (TransactWriteItem item : request.transactItems()) {
            assertThat(item.update()).isNotNull();
            assertThat(item.update().updateExpression())
                .isEqualTo("SET groupName = :newName, updatedAt = :timestamp");
            assertThat(item.update().expressionAttributeValues().get(":newName").s())
                .isEqualTo("Updated Group Name");
            assertThat(item.update().expressionAttributeValues()).containsKey(":timestamp");
        }
    }

    @Test
    void updateMembershipGroupNames_BatchesLargeGroups() {
        // Given - 50 memberships should be split into 2 batches (25, 25)
        List<Map<String, AttributeValue>> membershipItems = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            membershipItems.add(createGroupMembershipItemMap(UUID.randomUUID().toString()));
        }

        QueryResponse queryResponse = QueryResponse.builder()
            .items(membershipItems)
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);

        TransactWriteItemsResponse transactResponse = TransactWriteItemsResponse.builder().build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class))).thenReturn(transactResponse);

        // When
        repository.updateMembershipGroupNames(groupId, "New Name");

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient, times(2)).transactWriteItems(captor.capture());

        List<TransactWriteItemsRequest> requests = captor.getAllValues();
        assertThat(requests.get(0).transactItems()).hasSize(25);
        assertThat(requests.get(1).transactItems()).hasSize(25);
    }

    @Test
    void updateMembershipGroupNames_ReturnsEarlyWhenNoMemberships() {
        // Given
        QueryResponse queryResponse = QueryResponse.builder()
            .items(new ArrayList<>())
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);

        // When
        repository.updateMembershipGroupNames(groupId, "New Name");

        // Then - should not call transactWriteItems
        verify(dynamoDbClient, never()).transactWriteItems(any(TransactWriteItemsRequest.class));
    }

    // ============================================================================
    // POLYMORPHIC DESERIALIZATION EDGE CASES
    // ============================================================================

    @Test
    void findById_WithMissingItemTypeButValidSk_UsesBackwardCompatibility() {
        // Given - old data format without itemType discriminator
        Map<String, AttributeValue> groupItem = Map.of(
            "pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build(),
            "sk", AttributeValue.builder().s("METADATA").build(), // No itemType, but SK indicates group
            "groupId", AttributeValue.builder().s(groupId).build(),
            "groupName", AttributeValue.builder().s("Test Group").build(),
            "public", AttributeValue.builder().bool(true).build()
        );

        GetItemResponse response = GetItemResponse.builder()
            .item(groupItem)
            .build();
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

        // When
        Optional<Group> result = repository.findById(groupId);

        // Then - should still deserialize correctly using SK pattern matching
        assertThat(result).isPresent();
        assertThat(result.get().getGroupId()).isEqualTo(groupId);
    }

    @Test
    void findMembersByGroupId_WithMissingItemTypeButValidSk_UsesBackwardCompatibility() {
        // Given - membership without itemType
        Map<String, AttributeValue> membershipItem = Map.of(
            "pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build(),
            "sk", AttributeValue.builder().s(InviterKeyFactory.getUserSk(userId)).build(), // No itemType
            "groupId", AttributeValue.builder().s(groupId).build(),
            "userId", AttributeValue.builder().s(userId).build(),
            "groupName", AttributeValue.builder().s("Test Group").build()
        );

        QueryResponse queryResponse = QueryResponse.builder()
            .items(List.of(membershipItem))
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);

        // When
        List<GroupMembership> result = repository.findMembersByGroupId(groupId);

        // Then - should deserialize using SK pattern
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(userId);
    }

    @Test
    void findHangoutsByGroupId_FiltersOutUnknownItemTypes() {
        // Given - mix of valid and unknown item types
        List<Map<String, AttributeValue>> items = Arrays.asList(
            createHangoutPointerItemMap(hangoutId),
            Map.of(
                "pk", AttributeValue.builder().s(InviterKeyFactory.getGroupPk(groupId)).build(),
                "sk", AttributeValue.builder().s("HANGOUT#unknown").build(),
                "itemType", AttributeValue.builder().s("UNKNOWN_TYPE").build() // Unknown type
            ),
            createHangoutPointerItemMap(UUID.randomUUID().toString())
        );

        QueryResponse queryResponse = QueryResponse.builder()
            .items(items)
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);

        // When
        List<HangoutPointer> result = repository.findHangoutsByGroupId(groupId);

        // Then - should only return valid hangout pointers, filtering out unknown type
        assertThat(result).hasSize(2);
    }

    // ============================================================================
    // REMAINING UNCOVERED METHODS
    // ============================================================================

    @Test
    void save_UpdatesTimestampAndSavesGroup() {
        // Given
        Group group = new Group("Test Group", true);
        group.setGroupId(groupId);
        group.setPk(InviterKeyFactory.getGroupPk(groupId));
        group.setSk(InviterKeyFactory.getMetadataSk());

        PutItemResponse response = PutItemResponse.builder().build();
        when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);

        // When
        Group result = repository.save(group);

        // Then
        assertThat(result).isSameAs(group);
        assertThat(result.getUpdatedAt()).isNotNull(); // touch() should set this

        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(captor.capture());

        PutItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo(TABLE_NAME);
        assertThat(request.item().get("groupId").s()).isEqualTo(groupId);
    }

    @Test
    void findMembership_WithExistingMembership_ReturnsMembership() {
        // Given
        Map<String, AttributeValue> membershipItem = createGroupMembershipItemMap();
        GetItemResponse response = GetItemResponse.builder()
            .item(membershipItem)
            .build();
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

        // When
        Optional<GroupMembership> result = repository.findMembership(groupId, userId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getGroupId()).isEqualTo(groupId);
        assertThat(result.get().getUserId()).isEqualTo(userId);

        ArgumentCaptor<GetItemRequest> captor = ArgumentCaptor.forClass(GetItemRequest.class);
        verify(dynamoDbClient).getItem(captor.capture());

        GetItemRequest request = captor.getValue();
        assertThat(request.key().get("pk").s()).isEqualTo("GROUP#" + groupId);
        assertThat(request.key().get("sk").s()).isEqualTo("USER#" + userId);
    }

    @Test
    void findMembership_WithNoMembership_ReturnsEmpty() {
        // Given
        GetItemResponse response = GetItemResponse.builder().build(); // No item
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

        // When
        Optional<GroupMembership> result = repository.findMembership(groupId, userId);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void removeMember_DeletesMembershipRecord() {
        // Given
        DeleteItemResponse response = DeleteItemResponse.builder().build();
        when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class))).thenReturn(response);

        // When
        repository.removeMember(groupId, userId);

        // Then
        ArgumentCaptor<DeleteItemRequest> captor = ArgumentCaptor.forClass(DeleteItemRequest.class);
        verify(dynamoDbClient).deleteItem(captor.capture());

        DeleteItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo(TABLE_NAME);
        assertThat(request.key().get("pk").s()).isEqualTo("GROUP#" + groupId);
        assertThat(request.key().get("sk").s()).isEqualTo("USER#" + userId);
    }

    @Test
    void saveHangoutPointer_UpdatesTimestampAndSavesPointer() {
        // Given
        HangoutPointer pointer = new HangoutPointer(groupId, hangoutId, "Test Hangout");
        pointer.setPk(InviterKeyFactory.getGroupPk(groupId));
        pointer.setSk(InviterKeyFactory.getHangoutSk(hangoutId));

        PutItemResponse response = PutItemResponse.builder().build();
        when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);

        // When
        repository.saveHangoutPointer(pointer);

        // Then
        assertThat(pointer.getUpdatedAt()).isNotNull(); // touch() should set this

        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(captor.capture());

        PutItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo(TABLE_NAME);
        assertThat(request.item().get("hangoutId").s()).isEqualTo(hangoutId);
    }

    @Test
    void findHangoutPointer_WithExistingPointer_ReturnsPointer() {
        // Given
        Map<String, AttributeValue> pointerItem = createHangoutPointerItemMap();
        GetItemResponse response = GetItemResponse.builder()
            .item(pointerItem)
            .build();
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

        // When
        Optional<HangoutPointer> result = repository.findHangoutPointer(groupId, hangoutId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getGroupId()).isEqualTo(groupId);
        assertThat(result.get().getHangoutId()).isEqualTo(hangoutId);
    }

    @Test
    void findHangoutPointer_WithNoPointer_ReturnsEmpty() {
        // Given
        GetItemResponse response = GetItemResponse.builder().build(); // No item
        when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

        // When
        Optional<HangoutPointer> result = repository.findHangoutPointer(groupId, hangoutId);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void deleteHangoutPointer_DeletesPointerRecord() {
        // Given
        DeleteItemResponse response = DeleteItemResponse.builder().build();
        when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class))).thenReturn(response);

        // When
        repository.deleteHangoutPointer(groupId, hangoutId);

        // Then
        ArgumentCaptor<DeleteItemRequest> captor = ArgumentCaptor.forClass(DeleteItemRequest.class);
        verify(dynamoDbClient).deleteItem(captor.capture());

        DeleteItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo(TABLE_NAME);
        assertThat(request.key().get("pk").s()).isEqualTo("GROUP#" + groupId);
        assertThat(request.key().get("sk").s()).isEqualTo("HANGOUT#" + hangoutId);
    }

    @Test
    void saveSeriesPointer_UpdatesTimestampAndSavesPointer() {
        // Given
        SeriesPointer pointer = new SeriesPointer(groupId, seriesId, "Test Series");
        pointer.setPk(InviterKeyFactory.getGroupPk(groupId));
        pointer.setSk(InviterKeyFactory.getSeriesSk(seriesId));

        PutItemResponse response = PutItemResponse.builder().build();
        when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(response);

        // When
        repository.saveSeriesPointer(pointer);

        // Then
        assertThat(pointer.getUpdatedAt()).isNotNull(); // touch() should set this

        ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamoDbClient).putItem(captor.capture());

        PutItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo(TABLE_NAME);
        assertThat(request.item().get("seriesId").s()).isEqualTo(seriesId);
    }

    @Test
    void findMembershipByToken_QueriesCalendarTokenIndex() {
        // Given
        String token = "test-calendar-token-123";
        Map<String, AttributeValue> membershipItem = createGroupMembershipItemMap();

        QueryResponse queryResponse = QueryResponse.builder()
            .items(List.of(membershipItem))
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);

        // When
        Optional<GroupMembership> result = repository.findMembershipByToken(token);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getGroupId()).isEqualTo(groupId);

        ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
        verify(dynamoDbClient).query(captor.capture());

        QueryRequest request = captor.getValue();
        assertThat(request.indexName()).isEqualTo("CalendarTokenIndex");
        assertThat(request.keyConditionExpression()).isEqualTo("gsi2pk = :gsi2pk");
        assertThat(request.expressionAttributeValues().get(":gsi2pk").s()).isEqualTo("TOKEN#" + token);
        assertThat(request.limit()).isEqualTo(1);
    }

    @Test
    void findMembershipByToken_WithNoResults_ReturnsEmpty() {
        // Given
        QueryResponse queryResponse = QueryResponse.builder()
            .items(new ArrayList<>())
            .build();
        when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);

        // When
        Optional<GroupMembership> result = repository.findMembershipByToken("invalid-token");

        // Then
        assertThat(result).isEmpty();
    }
}