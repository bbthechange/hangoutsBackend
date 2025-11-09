package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.exception.RepositoryException;
import com.bbthechange.inviter.model.IdeaList;
import com.bbthechange.inviter.model.IdeaListCategory;
import com.bbthechange.inviter.model.IdeaListMember;
import com.bbthechange.inviter.util.InviterKeyFactory;
import com.bbthechange.inviter.util.QueryPerformanceTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for IdeaListRepositoryImpl repository methods.
 * Tests verify intended functionality: correct DynamoDB operations, proper data handling,
 * business logic (sorting, aggregation), and error handling.
 */
@ExtendWith(MockitoExtension.class)
class IdeaListRepositoryImplMethodsTest {

    private static final String TABLE_NAME = "InviterTable";

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private QueryPerformanceTracker queryTracker;

    private IdeaListRepositoryImpl repository;
    private TableSchema<IdeaList> ideaListSchema;
    private TableSchema<IdeaListMember> ideaMemberSchema;

    private String testGroupId;
    private String testUserId;

    @BeforeEach
    void setUp() {
        // Configure queryTracker to execute the supplier it receives
        lenient().when(queryTracker.trackQuery(anyString(), anyString(), any())).thenAnswer(invocation -> {
            try {
                java.util.function.Supplier<?> supplier = invocation.getArgument(2);
                return supplier.get();
            } catch (Exception e) {
                throw e;
            }
        });

        repository = new IdeaListRepositoryImpl(dynamoDbClient, queryTracker);
        ideaListSchema = TableSchema.fromBean(IdeaList.class);
        ideaMemberSchema = TableSchema.fromBean(IdeaListMember.class);

        testGroupId = UUID.randomUUID().toString();
        testUserId = UUID.randomUUID().toString();
    }

    @Nested
    class SaveIdeaList {

        @Test
        void saveIdeaList_ValidIdeaList_SavesSuccessfully() {
            // Given
            IdeaList ideaList = new IdeaList(testGroupId, "Coffee Shops", IdeaListCategory.RESTAURANT, "Great coffee places", testUserId);

            when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());

            // When
            IdeaList result = repository.saveIdeaList(ideaList);

            // Then
            assertThat(result).isEqualTo(ideaList);

            ArgumentCaptor<PutItemRequest> requestCaptor = ArgumentCaptor.forClass(PutItemRequest.class);
            verify(dynamoDbClient).putItem(requestCaptor.capture());

            PutItemRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.tableName()).isEqualTo(TABLE_NAME);
            assertThat(capturedRequest.item().get("pk").s()).isEqualTo(InviterKeyFactory.getGroupPk(testGroupId));
            assertThat(capturedRequest.item().get("sk").s()).isEqualTo(InviterKeyFactory.getIdeaListSk(ideaList.getListId()));
        }

        @Test
        void saveIdeaList_DynamoDBException_ThrowsRepositoryException() {
            // Given
            IdeaList ideaList = new IdeaList(testGroupId, "Test List", IdeaListCategory.BOOK, null, testUserId);

            when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenThrow(DynamoDbException.builder().message("DynamoDB error").build());

            // When/Then
            assertThatThrownBy(() -> repository.saveIdeaList(ideaList))
                .isInstanceOf(RepositoryException.class)
                .hasMessageContaining("Failed to save idea list");
        }
    }

    @Nested
    class FindIdeaListById {

        @Test
        void findIdeaListById_Exists_ReturnsIdeaList() {
            // Given
            IdeaList ideaList = new IdeaList(testGroupId, "Movie List", IdeaListCategory.MOVIE, "Must watch", testUserId);
            Map<String, AttributeValue> item = ideaListSchema.itemToMap(ideaList, true);

            GetItemResponse response = GetItemResponse.builder()
                .item(item)
                .build();

            when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

            // When
            Optional<IdeaList> result = repository.findIdeaListById(testGroupId, ideaList.getListId());

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getListId()).isEqualTo(ideaList.getListId());
            assertThat(result.get().getName()).isEqualTo("Movie List");

            ArgumentCaptor<GetItemRequest> requestCaptor = ArgumentCaptor.forClass(GetItemRequest.class);
            verify(dynamoDbClient).getItem(requestCaptor.capture());

            GetItemRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.tableName()).isEqualTo(TABLE_NAME);
            assertThat(capturedRequest.key().get("pk").s()).isEqualTo(InviterKeyFactory.getGroupPk(testGroupId));
            assertThat(capturedRequest.key().get("sk").s()).isEqualTo(InviterKeyFactory.getIdeaListSk(ideaList.getListId()));
        }

        @Test
        void findIdeaListById_NotExists_ReturnsEmpty() {
            // Given
            String nonExistentListId = UUID.randomUUID().toString();

            GetItemResponse response = GetItemResponse.builder()
                .build();

            when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

            // When
            Optional<IdeaList> result = repository.findIdeaListById(testGroupId, nonExistentListId);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        void findIdeaListById_DynamoDBException_ThrowsRepositoryException() {
            // Given
            String listId = UUID.randomUUID().toString();

            when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenThrow(DynamoDbException.builder().message("DynamoDB error").build());

            // When/Then
            assertThatThrownBy(() -> repository.findIdeaListById(testGroupId, listId))
                .isInstanceOf(RepositoryException.class)
                .hasMessageContaining("Failed to find idea list");
        }
    }

    @Nested
    class DeleteIdeaList {

        @Test
        void deleteIdeaList_ValidIds_DeletesSuccessfully() {
            // Given
            String listId = UUID.randomUUID().toString();

            when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class))).thenReturn(DeleteItemResponse.builder().build());

            // When
            repository.deleteIdeaList(testGroupId, listId);

            // Then
            ArgumentCaptor<DeleteItemRequest> requestCaptor = ArgumentCaptor.forClass(DeleteItemRequest.class);
            verify(dynamoDbClient).deleteItem(requestCaptor.capture());

            DeleteItemRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.tableName()).isEqualTo(TABLE_NAME);
            assertThat(capturedRequest.key().get("pk").s()).isEqualTo(InviterKeyFactory.getGroupPk(testGroupId));
            assertThat(capturedRequest.key().get("sk").s()).isEqualTo(InviterKeyFactory.getIdeaListSk(listId));
        }

        @Test
        void deleteIdeaList_DynamoDBException_ThrowsRepositoryException() {
            // Given
            String listId = UUID.randomUUID().toString();

            when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class)))
                .thenThrow(DynamoDbException.builder().message("DynamoDB error").build());

            // When/Then
            assertThatThrownBy(() -> repository.deleteIdeaList(testGroupId, listId))
                .isInstanceOf(RepositoryException.class)
                .hasMessageContaining("Failed to delete idea list");
        }
    }

    @Nested
    class SaveIdeaListMember {

        @Test
        void saveIdeaListMember_ValidMember_SavesSuccessfully() {
            // Given
            String listId = UUID.randomUUID().toString();
            IdeaListMember member = new IdeaListMember(testGroupId, listId, "Best Burger", "http://burger.com", "Amazing burgers", testUserId);

            when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());

            // When
            IdeaListMember result = repository.saveIdeaListMember(member);

            // Then
            assertThat(result).isEqualTo(member);

            ArgumentCaptor<PutItemRequest> requestCaptor = ArgumentCaptor.forClass(PutItemRequest.class);
            verify(dynamoDbClient).putItem(requestCaptor.capture());

            PutItemRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.tableName()).isEqualTo(TABLE_NAME);
            assertThat(capturedRequest.item().get("pk").s()).isEqualTo(InviterKeyFactory.getGroupPk(testGroupId));
            assertThat(capturedRequest.item().get("sk").s()).isEqualTo(InviterKeyFactory.getIdeaListMemberSk(listId, member.getIdeaId()));
        }

        @Test
        void saveIdeaListMember_DynamoDBException_ThrowsRepositoryException() {
            // Given
            String listId = UUID.randomUUID().toString();
            IdeaListMember member = new IdeaListMember(testGroupId, listId, "Test", null, null, testUserId);

            when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenThrow(DynamoDbException.builder().message("DynamoDB error").build());

            // When/Then
            assertThatThrownBy(() -> repository.saveIdeaListMember(member))
                .isInstanceOf(RepositoryException.class)
                .hasMessageContaining("Failed to save idea list member");
        }
    }

    @Nested
    class FindIdeaListMemberById {

        @Test
        void findIdeaListMemberById_Exists_ReturnsMember() {
            // Given
            String listId = UUID.randomUUID().toString();
            IdeaListMember member = new IdeaListMember(testGroupId, listId, "Sushi Place", "http://sushi.com", "Best sushi", testUserId);
            Map<String, AttributeValue> item = ideaMemberSchema.itemToMap(member, true);

            GetItemResponse response = GetItemResponse.builder()
                .item(item)
                .build();

            when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

            // When
            Optional<IdeaListMember> result = repository.findIdeaListMemberById(testGroupId, listId, member.getIdeaId());

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getIdeaId()).isEqualTo(member.getIdeaId());
            assertThat(result.get().getName()).isEqualTo("Sushi Place");

            ArgumentCaptor<GetItemRequest> requestCaptor = ArgumentCaptor.forClass(GetItemRequest.class);
            verify(dynamoDbClient).getItem(requestCaptor.capture());

            GetItemRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.tableName()).isEqualTo(TABLE_NAME);
            assertThat(capturedRequest.key().get("pk").s()).isEqualTo(InviterKeyFactory.getGroupPk(testGroupId));
            assertThat(capturedRequest.key().get("sk").s()).isEqualTo(InviterKeyFactory.getIdeaListMemberSk(listId, member.getIdeaId()));
        }

        @Test
        void findIdeaListMemberById_NotExists_ReturnsEmpty() {
            // Given
            String listId = UUID.randomUUID().toString();
            String ideaId = UUID.randomUUID().toString();

            GetItemResponse response = GetItemResponse.builder()
                .build();

            when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

            // When
            Optional<IdeaListMember> result = repository.findIdeaListMemberById(testGroupId, listId, ideaId);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        void findIdeaListMemberById_DynamoDBException_ThrowsRepositoryException() {
            // Given
            String listId = UUID.randomUUID().toString();
            String ideaId = UUID.randomUUID().toString();

            when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenThrow(DynamoDbException.builder().message("DynamoDB error").build());

            // When/Then
            assertThatThrownBy(() -> repository.findIdeaListMemberById(testGroupId, listId, ideaId))
                .isInstanceOf(RepositoryException.class)
                .hasMessageContaining("Failed to find idea list member");
        }
    }

    @Nested
    class DeleteIdeaListMember {

        @Test
        void deleteIdeaListMember_ValidIds_DeletesSuccessfully() {
            // Given
            String listId = UUID.randomUUID().toString();
            String ideaId = UUID.randomUUID().toString();

            when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class))).thenReturn(DeleteItemResponse.builder().build());

            // When
            repository.deleteIdeaListMember(testGroupId, listId, ideaId);

            // Then
            ArgumentCaptor<DeleteItemRequest> requestCaptor = ArgumentCaptor.forClass(DeleteItemRequest.class);
            verify(dynamoDbClient).deleteItem(requestCaptor.capture());

            DeleteItemRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.tableName()).isEqualTo(TABLE_NAME);
            assertThat(capturedRequest.key().get("pk").s()).isEqualTo(InviterKeyFactory.getGroupPk(testGroupId));
            assertThat(capturedRequest.key().get("sk").s()).isEqualTo(InviterKeyFactory.getIdeaListMemberSk(listId, ideaId));
        }

        @Test
        void deleteIdeaListMember_DynamoDBException_ThrowsRepositoryException() {
            // Given
            String listId = UUID.randomUUID().toString();
            String ideaId = UUID.randomUUID().toString();

            when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class)))
                .thenThrow(DynamoDbException.builder().message("DynamoDB error").build());

            // When/Then
            assertThatThrownBy(() -> repository.deleteIdeaListMember(testGroupId, listId, ideaId))
                .isInstanceOf(RepositoryException.class)
                .hasMessageContaining("Failed to delete idea list member");
        }
    }

    @Nested
    class FindAllIdeaListsWithMembersByGroupId {

        @Test
        void findAllIdeaListsWithMembersByGroupId_MultipleListsWithMembers_ReturnsAggregatedAndSortedLists() {
            // Given: Create lists with different creation times
            Instant now = Instant.now();

            IdeaList list1 = new IdeaList(testGroupId, "Restaurants", IdeaListCategory.RESTAURANT, "Food", testUserId);
            list1.setCreatedAt(now.minusSeconds(300)); // Older

            IdeaList list2 = new IdeaList(testGroupId, "Movies", IdeaListCategory.MOVIE, "Films", testUserId);
            list2.setCreatedAt(now.minusSeconds(100)); // Newer

            // Create members for list1 (different added times)
            IdeaListMember member1a = new IdeaListMember(testGroupId, list1.getListId(), "Pizza Place", null, null, testUserId);
            member1a.setAddedTime(now.minusSeconds(200)); // Older

            IdeaListMember member1b = new IdeaListMember(testGroupId, list1.getListId(), "Burger Joint", null, null, testUserId);
            member1b.setAddedTime(now.minusSeconds(50)); // Newer

            // Create members for list2
            IdeaListMember member2a = new IdeaListMember(testGroupId, list2.getListId(), "Action Movie", null, null, testUserId);
            member2a.setAddedTime(now.minusSeconds(150));

            // Convert to DynamoDB items
            List<Map<String, AttributeValue>> items = new ArrayList<>();
            items.add(ideaListSchema.itemToMap(list1, true));
            items.add(ideaListSchema.itemToMap(list2, true));
            items.add(ideaMemberSchema.itemToMap(member1a, true));
            items.add(ideaMemberSchema.itemToMap(member1b, true));
            items.add(ideaMemberSchema.itemToMap(member2a, true));

            QueryResponse response = QueryResponse.builder()
                .items(items)
                .build();

            when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

            // When
            List<IdeaList> result = repository.findAllIdeaListsWithMembersByGroupId(testGroupId);

            // Then: Verify lists are sorted by creation time (most recent first)
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("Movies"); // Newer list first
            assertThat(result.get(1).getName()).isEqualTo("Restaurants"); // Older list second

            // Verify members are attached and sorted by added time (most recent first)
            assertThat(result.get(0).getMembers()).hasSize(1);
            assertThat(result.get(0).getMembers().get(0).getName()).isEqualTo("Action Movie");

            assertThat(result.get(1).getMembers()).hasSize(2);
            assertThat(result.get(1).getMembers().get(0).getName()).isEqualTo("Burger Joint"); // Newer member first
            assertThat(result.get(1).getMembers().get(1).getName()).isEqualTo("Pizza Place"); // Older member second

            // Verify correct query was used
            ArgumentCaptor<QueryRequest> requestCaptor = ArgumentCaptor.forClass(QueryRequest.class);
            verify(dynamoDbClient).query(requestCaptor.capture());

            QueryRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.tableName()).isEqualTo(TABLE_NAME);
            assertThat(capturedRequest.keyConditionExpression()).isEqualTo("pk = :pk AND begins_with(sk, :skPrefix)");
            assertThat(capturedRequest.expressionAttributeValues().get(":pk").s()).isEqualTo(InviterKeyFactory.getGroupPk(testGroupId));
            assertThat(capturedRequest.expressionAttributeValues().get(":skPrefix").s()).isEqualTo(InviterKeyFactory.getIdeaListQueryPrefix());
        }

        @Test
        void findAllIdeaListsWithMembersByGroupId_EmptyGroup_ReturnsEmptyList() {
            // Given
            QueryResponse response = QueryResponse.builder()
                .items(Collections.emptyList())
                .build();

            when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

            // When
            List<IdeaList> result = repository.findAllIdeaListsWithMembersByGroupId(testGroupId);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        void findAllIdeaListsWithMembersByGroupId_ListWithoutMembers_ReturnsListWithEmptyMembers() {
            // Given
            IdeaList list = new IdeaList(testGroupId, "Empty List", IdeaListCategory.OTHER, null, testUserId);

            List<Map<String, AttributeValue>> items = new ArrayList<>();
            items.add(ideaListSchema.itemToMap(list, true));

            QueryResponse response = QueryResponse.builder()
                .items(items)
                .build();

            when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

            // When
            List<IdeaList> result = repository.findAllIdeaListsWithMembersByGroupId(testGroupId);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getMembers()).isEmpty();
        }

        @Test
        void findAllIdeaListsWithMembersByGroupId_DynamoDBException_ThrowsRepositoryException() {
            // Given
            when(dynamoDbClient.query(any(QueryRequest.class)))
                .thenThrow(DynamoDbException.builder().message("DynamoDB error").build());

            // When/Then
            assertThatThrownBy(() -> repository.findAllIdeaListsWithMembersByGroupId(testGroupId))
                .isInstanceOf(RepositoryException.class)
                .hasMessageContaining("Failed to find idea lists for group");
        }
    }

    @Nested
    class FindIdeaListWithMembersById {

        @Test
        void findIdeaListWithMembersById_ExistsWithMembers_ReturnsListWithSortedMembers() {
            // Given
            Instant now = Instant.now();

            IdeaList list = new IdeaList(testGroupId, "Travel Spots", IdeaListCategory.TRAVEL, "Places to visit", testUserId);

            IdeaListMember member1 = new IdeaListMember(testGroupId, list.getListId(), "Paris", null, "City of lights", testUserId);
            member1.setAddedTime(now.minusSeconds(300)); // Older

            IdeaListMember member2 = new IdeaListMember(testGroupId, list.getListId(), "Tokyo", null, "Modern city", testUserId);
            member2.setAddedTime(now.minusSeconds(100)); // Newer

            List<Map<String, AttributeValue>> items = new ArrayList<>();
            items.add(ideaListSchema.itemToMap(list, true));
            items.add(ideaMemberSchema.itemToMap(member1, true));
            items.add(ideaMemberSchema.itemToMap(member2, true));

            QueryResponse response = QueryResponse.builder()
                .items(items)
                .build();

            when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

            // When
            Optional<IdeaList> result = repository.findIdeaListWithMembersById(testGroupId, list.getListId());

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("Travel Spots");
            assertThat(result.get().getMembers()).hasSize(2);

            // Verify members are sorted by most recent first
            assertThat(result.get().getMembers().get(0).getName()).isEqualTo("Tokyo"); // Newer first
            assertThat(result.get().getMembers().get(1).getName()).isEqualTo("Paris"); // Older second

            // Verify correct query was used
            ArgumentCaptor<QueryRequest> requestCaptor = ArgumentCaptor.forClass(QueryRequest.class);
            verify(dynamoDbClient).query(requestCaptor.capture());

            QueryRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.tableName()).isEqualTo(TABLE_NAME);
            assertThat(capturedRequest.keyConditionExpression()).isEqualTo("pk = :pk AND begins_with(sk, :skPrefix)");
            assertThat(capturedRequest.expressionAttributeValues().get(":pk").s()).isEqualTo(InviterKeyFactory.getGroupPk(testGroupId));
            assertThat(capturedRequest.expressionAttributeValues().get(":skPrefix").s()).isEqualTo(InviterKeyFactory.getIdeaListPrefix(list.getListId()));
        }

        @Test
        void findIdeaListWithMembersById_NotExists_ReturnsEmpty() {
            // Given
            String nonExistentListId = UUID.randomUUID().toString();

            QueryResponse response = QueryResponse.builder()
                .items(Collections.emptyList())
                .build();

            when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

            // When
            Optional<IdeaList> result = repository.findIdeaListWithMembersById(testGroupId, nonExistentListId);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        void findIdeaListWithMembersById_OnlyMembersNoList_ReturnsEmpty() {
            // Given: Query returns only members without the list itself (shouldn't happen but defensive)
            String listId = UUID.randomUUID().toString();
            IdeaListMember member = new IdeaListMember(testGroupId, listId, "Orphaned Member", null, null, testUserId);

            List<Map<String, AttributeValue>> items = new ArrayList<>();
            items.add(ideaMemberSchema.itemToMap(member, true));

            QueryResponse response = QueryResponse.builder()
                .items(items)
                .build();

            when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

            // When
            Optional<IdeaList> result = repository.findIdeaListWithMembersById(testGroupId, listId);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        void findIdeaListWithMembersById_DynamoDBException_ThrowsRepositoryException() {
            // Given
            String listId = UUID.randomUUID().toString();

            when(dynamoDbClient.query(any(QueryRequest.class)))
                .thenThrow(DynamoDbException.builder().message("DynamoDB error").build());

            // When/Then
            assertThatThrownBy(() -> repository.findIdeaListWithMembersById(testGroupId, listId))
                .isInstanceOf(RepositoryException.class)
                .hasMessageContaining("Failed to find idea list with members");
        }
    }

    @Nested
    class DeleteIdeaListWithAllMembers {

        @Test
        void deleteIdeaListWithAllMembers_ListWithMembers_BatchDeletesAll() {
            // Given
            String listId = UUID.randomUUID().toString();

            IdeaList list = new IdeaList(testGroupId, "To Delete", IdeaListCategory.OTHER, null, testUserId);
            list.setListId(listId);

            IdeaListMember member1 = new IdeaListMember(testGroupId, listId, "Member 1", null, null, testUserId);
            IdeaListMember member2 = new IdeaListMember(testGroupId, listId, "Member 2", null, null, testUserId);

            List<Map<String, AttributeValue>> items = new ArrayList<>();
            items.add(ideaListSchema.itemToMap(list, true));
            items.add(ideaMemberSchema.itemToMap(member1, true));
            items.add(ideaMemberSchema.itemToMap(member2, true));

            QueryResponse queryResponse = QueryResponse.builder()
                .items(items)
                .build();

            when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);
            when(dynamoDbClient.batchWriteItem(any(BatchWriteItemRequest.class))).thenReturn(BatchWriteItemResponse.builder().build());

            // When
            repository.deleteIdeaListWithAllMembers(testGroupId, listId);

            // Then: Verify query to find items
            ArgumentCaptor<QueryRequest> queryCaptor = ArgumentCaptor.forClass(QueryRequest.class);
            verify(dynamoDbClient).query(queryCaptor.capture());

            QueryRequest capturedQuery = queryCaptor.getValue();
            assertThat(capturedQuery.keyConditionExpression()).isEqualTo("pk = :pk AND begins_with(sk, :skPrefix)");
            assertThat(capturedQuery.expressionAttributeValues().get(":skPrefix").s()).isEqualTo(InviterKeyFactory.getIdeaListPrefix(listId));

            // Then: Verify batch delete with all items
            ArgumentCaptor<BatchWriteItemRequest> batchCaptor = ArgumentCaptor.forClass(BatchWriteItemRequest.class);
            verify(dynamoDbClient).batchWriteItem(batchCaptor.capture());

            BatchWriteItemRequest capturedBatch = batchCaptor.getValue();
            assertThat(capturedBatch.requestItems().get(TABLE_NAME)).hasSize(3); // List + 2 members
        }

        @Test
        void deleteIdeaListWithAllMembers_NoItemsFound_DoesNotAttemptBatchDelete() {
            // Given
            String listId = UUID.randomUUID().toString();

            QueryResponse queryResponse = QueryResponse.builder()
                .items(Collections.emptyList())
                .build();

            when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(queryResponse);

            // When
            repository.deleteIdeaListWithAllMembers(testGroupId, listId);

            // Then: Verify query was called but batch delete was not
            verify(dynamoDbClient).query(any(QueryRequest.class));
            verify(dynamoDbClient, never()).batchWriteItem(any(BatchWriteItemRequest.class));
        }

        @Test
        void deleteIdeaListWithAllMembers_DynamoDBException_ThrowsRepositoryException() {
            // Given
            String listId = UUID.randomUUID().toString();

            when(dynamoDbClient.query(any(QueryRequest.class)))
                .thenThrow(DynamoDbException.builder().message("DynamoDB error").build());

            // When/Then
            assertThatThrownBy(() -> repository.deleteIdeaListWithAllMembers(testGroupId, listId))
                .isInstanceOf(RepositoryException.class)
                .hasMessageContaining("Failed to delete idea list with all members");
        }
    }

    @Nested
    class IdeaListExists {

        @Test
        void ideaListExists_Exists_ReturnsTrue() {
            // Given
            IdeaList ideaList = new IdeaList(testGroupId, "Existing List", IdeaListCategory.BOOK, null, testUserId);
            Map<String, AttributeValue> item = ideaListSchema.itemToMap(ideaList, true);

            GetItemResponse response = GetItemResponse.builder()
                .item(item)
                .build();

            when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

            // When
            boolean result = repository.ideaListExists(testGroupId, ideaList.getListId());

            // Then
            assertThat(result).isTrue();
        }

        @Test
        void ideaListExists_NotExists_ReturnsFalse() {
            // Given
            String nonExistentListId = UUID.randomUUID().toString();

            GetItemResponse response = GetItemResponse.builder()
                .build();

            when(dynamoDbClient.getItem(any(GetItemRequest.class))).thenReturn(response);

            // When
            boolean result = repository.ideaListExists(testGroupId, nonExistentListId);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        void ideaListExists_Exception_ReturnsFalse() {
            // Given
            String listId = UUID.randomUUID().toString();

            when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenThrow(DynamoDbException.builder().message("DynamoDB error").build());

            // When
            boolean result = repository.ideaListExists(testGroupId, listId);

            // Then: Returns false instead of throwing exception
            assertThat(result).isFalse();
        }
    }

    @Nested
    class FindMembersByListId {

        @Test
        void findMembersByListId_MultipleMembers_ReturnsSortedMembers() {
            // Given
            Instant now = Instant.now();
            String listId = UUID.randomUUID().toString();

            IdeaListMember member1 = new IdeaListMember(testGroupId, listId, "Old Idea", null, null, testUserId);
            member1.setAddedTime(now.minusSeconds(500));

            IdeaListMember member2 = new IdeaListMember(testGroupId, listId, "Recent Idea", null, null, testUserId);
            member2.setAddedTime(now.minusSeconds(100));

            IdeaListMember member3 = new IdeaListMember(testGroupId, listId, "Middle Idea", null, null, testUserId);
            member3.setAddedTime(now.minusSeconds(300));

            List<Map<String, AttributeValue>> items = new ArrayList<>();
            items.add(ideaMemberSchema.itemToMap(member1, true));
            items.add(ideaMemberSchema.itemToMap(member2, true));
            items.add(ideaMemberSchema.itemToMap(member3, true));

            QueryResponse response = QueryResponse.builder()
                .items(items)
                .build();

            when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

            // When
            List<IdeaListMember> result = repository.findMembersByListId(testGroupId, listId);

            // Then: Verify members are sorted by most recent first
            assertThat(result).hasSize(3);
            assertThat(result.get(0).getName()).isEqualTo("Recent Idea");
            assertThat(result.get(1).getName()).isEqualTo("Middle Idea");
            assertThat(result.get(2).getName()).isEqualTo("Old Idea");

            // Verify correct query
            ArgumentCaptor<QueryRequest> requestCaptor = ArgumentCaptor.forClass(QueryRequest.class);
            verify(dynamoDbClient).query(requestCaptor.capture());

            QueryRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.keyConditionExpression()).isEqualTo("pk = :pk AND begins_with(sk, :skPrefix)");
            assertThat(capturedRequest.expressionAttributeValues().get(":skPrefix").s()).isEqualTo(InviterKeyFactory.getIdeaListPrefix(listId));
        }

        @Test
        void findMembersByListId_NoMembers_ReturnsEmptyList() {
            // Given
            String listId = UUID.randomUUID().toString();

            QueryResponse response = QueryResponse.builder()
                .items(Collections.emptyList())
                .build();

            when(dynamoDbClient.query(any(QueryRequest.class))).thenReturn(response);

            // When
            List<IdeaListMember> result = repository.findMembersByListId(testGroupId, listId);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        void findMembersByListId_DynamoDBException_ThrowsRepositoryException() {
            // Given
            String listId = UUID.randomUUID().toString();

            when(dynamoDbClient.query(any(QueryRequest.class)))
                .thenThrow(DynamoDbException.builder().message("DynamoDB error").build());

            // When/Then
            assertThatThrownBy(() -> repository.findMembersByListId(testGroupId, listId))
                .isInstanceOf(RepositoryException.class)
                .hasMessageContaining("Failed to find members for idea list");
        }
    }
}
