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
}