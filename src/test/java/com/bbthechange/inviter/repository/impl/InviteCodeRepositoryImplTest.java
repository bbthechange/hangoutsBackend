package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.model.InviteCode;
import com.bbthechange.inviter.util.QueryPerformanceTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for InviteCodeRepositoryImpl.
 * Tests method contracts and business logic by mocking findAllByGroupId.
 * Does NOT test DynamoDB serialization/deserialization (integration test concern).
 */
@ExtendWith(MockitoExtension.class)
class InviteCodeRepositoryImplTest {

    private static final String GROUP_ID = "12345678-1234-1234-1234-123456789012";
    private static final String CODE = "abc123xy";
    private static final String USER_ID = "87654321-4321-4321-4321-210987654321";
    private static final String GROUP_NAME = "Test Group";

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private QueryPerformanceTracker queryPerformanceTracker;

    private InviteCodeRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new InviteCodeRepositoryImpl(dynamoDbClient, queryPerformanceTracker);

        // Mock QueryPerformanceTracker to execute the lambda (lenient to allow unused stubs)
        lenient().when(queryPerformanceTracker.trackQuery(anyString(), anyString(), any()))
            .thenAnswer(invocation -> {
                java.util.function.Supplier<?> supplier = invocation.getArgument(2);
                return supplier.get();
            });
    }

    // Helper methods to create test InviteCodes
    private InviteCode createActiveCode(String codeString) {
        InviteCode code = new InviteCode(GROUP_ID, codeString, USER_ID, GROUP_NAME);
        code.setActive(true);
        code.setExpiresAt(null);
        return code;
    }

    private InviteCode createInactiveCode(String codeString) {
        InviteCode code = new InviteCode(GROUP_ID, codeString, USER_ID, GROUP_NAME);
        code.setActive(false);
        return code;
    }

    private InviteCode createExpiredCode(String codeString) {
        InviteCode code = new InviteCode(GROUP_ID, codeString, USER_ID, GROUP_NAME);
        code.setActive(true);
        code.setExpiresAt(Instant.now().minusSeconds(3600)); // 1 hour ago
        return code;
    }

    @Nested
    class FindActiveCodeForGroupTests {

        @Test
        void findActiveCodeForGroup_WithMultipleCodes_ReturnsFirstActive() {
            // Given
            InviteCode inactiveCode = createInactiveCode("inactive1");
            InviteCode activeCode1 = createActiveCode("active123");
            InviteCode activeCode2 = createActiveCode("active456");

            InviteCodeRepositoryImpl spyRepository = spy(repository);
            List<InviteCode> allCodes = Arrays.asList(inactiveCode, activeCode1, activeCode2);
            doReturn(allCodes).when(spyRepository).findAllByGroupId(GROUP_ID);

            // When
            Optional<InviteCode> result = spyRepository.findActiveCodeForGroup(GROUP_ID);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getCode()).isEqualTo("active123");
            verify(spyRepository).findAllByGroupId(GROUP_ID);
        }

        @Test
        void findActiveCodeForGroup_WithNoActiveCodes_ReturnsEmpty() {
            // Given
            InviteCode inactiveCode1 = createInactiveCode("inactive1");
            InviteCode inactiveCode2 = createInactiveCode("inactive2");

            InviteCodeRepositoryImpl spyRepository = spy(repository);
            List<InviteCode> allCodes = Arrays.asList(inactiveCode1, inactiveCode2);
            doReturn(allCodes).when(spyRepository).findAllByGroupId(GROUP_ID);

            // When
            Optional<InviteCode> result = spyRepository.findActiveCodeForGroup(GROUP_ID);

            // Then
            assertThat(result).isEmpty();
            verify(spyRepository).findAllByGroupId(GROUP_ID);
        }

        @Test
        void findActiveCodeForGroup_WithNoCodesForGroup_ReturnsEmpty() {
            // Given
            InviteCodeRepositoryImpl spyRepository = spy(repository);
            doReturn(Collections.emptyList()).when(spyRepository).findAllByGroupId(GROUP_ID);

            // When
            Optional<InviteCode> result = spyRepository.findActiveCodeForGroup(GROUP_ID);

            // Then
            assertThat(result).isEmpty();
            verify(spyRepository).findAllByGroupId(GROUP_ID);
        }

        @Test
        void findActiveCodeForGroup_FiltersOutExpiredCodes() {
            // Given - code is active=true but expired
            InviteCode expiredCode = createExpiredCode("expired99");

            InviteCodeRepositoryImpl spyRepository = spy(repository);
            List<InviteCode> allCodes = Collections.singletonList(expiredCode);
            doReturn(allCodes).when(spyRepository).findAllByGroupId(GROUP_ID);

            // When
            Optional<InviteCode> result = spyRepository.findActiveCodeForGroup(GROUP_ID);

            // Then
            assertThat(result).isEmpty(); // Expired codes are filtered out by isUsable()
            verify(spyRepository).findAllByGroupId(GROUP_ID);
        }

        @Test
        void findActiveCodeForGroup_ReturnsFirstUsableCode() {
            // Given - mix of inactive, expired, and active codes
            InviteCode inactiveCode = createInactiveCode("inactive1");
            InviteCode expiredCode = createExpiredCode("expired1");
            InviteCode activeCode = createActiveCode("active123");

            InviteCodeRepositoryImpl spyRepository = spy(repository);
            List<InviteCode> allCodes = Arrays.asList(inactiveCode, expiredCode, activeCode);
            doReturn(allCodes).when(spyRepository).findAllByGroupId(GROUP_ID);

            // When
            Optional<InviteCode> result = spyRepository.findActiveCodeForGroup(GROUP_ID);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getCode()).isEqualTo("active123");
            assertThat(result.get().isUsable()).isTrue();
        }
    }

    @Nested
    class CodeExistsTests {

        @Test
        void codeExists_WhenCodeFound_ReturnsTrue() {
            // Given
            InviteCode code = createActiveCode(CODE);
            InviteCodeRepositoryImpl spyRepository = spy(repository);
            doReturn(Optional.of(code)).when(spyRepository).findByCode(CODE);

            // When
            boolean exists = spyRepository.codeExists(CODE);

            // Then
            assertThat(exists).isTrue();
            verify(spyRepository).findByCode(CODE);
        }

        @Test
        void codeExists_WhenCodeNotFound_ReturnsFalse() {
            // Given
            InviteCodeRepositoryImpl spyRepository = spy(repository);
            doReturn(Optional.empty()).when(spyRepository).findByCode("nonexistent");

            // When
            boolean exists = spyRepository.codeExists("nonexistent");

            // Then
            assertThat(exists).isFalse();
            verify(spyRepository).findByCode("nonexistent");
        }
    }

    @Nested
    class DynamoDbIntegrationContractTests {

        @Test
        void save_CallsPutItemWithCorrectTableAndCode() {
            // Given
            InviteCode code = createActiveCode(CODE);
            when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenReturn(PutItemResponse.builder().build());

            // When
            repository.save(code);

            // Then
            ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
            verify(dynamoDbClient).putItem(captor.capture());

            PutItemRequest request = captor.getValue();
            assertThat(request.tableName()).isEqualTo("InviterTable");

            // Verify the code is in the item map
            Map<String, AttributeValue> item = request.item();
            assertThat(item).containsKey("code");
            assertThat(item.get("code").s()).isEqualTo(CODE);

            // Verify keys are set
            assertThat(item).containsKey("pk");
            assertThat(item).containsKey("sk");
            assertThat(item.get("sk").s()).isEqualTo("METADATA");
        }

        @Test
        void findByCode_QueriesInviteCodeIndexGSI() {
            // Given
            when(dynamoDbClient.query(any(QueryRequest.class)))
                .thenReturn(QueryResponse.builder().items(Collections.emptyList()).build());

            // When
            repository.findByCode(CODE);

            // Then
            ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
            verify(dynamoDbClient).query(captor.capture());

            QueryRequest request = captor.getValue();
            assertThat(request.tableName()).isEqualTo("InviterTable");
            assertThat(request.indexName()).isEqualTo("InviteCodeIndex");
            assertThat(request.keyConditionExpression()).contains("gsi3pk");
            assertThat(request.keyConditionExpression()).contains("gsi3sk");

            // Verify the code is in the query values
            assertThat(request.expressionAttributeValues()).containsKey(":gsi3pk");
            String gsi3pkValue = request.expressionAttributeValues().get(":gsi3pk").s();
            assertThat(gsi3pkValue).contains(CODE);
        }

        @Test
        void findById_UsesGetItemWithCorrectKeys() {
            // Given
            String inviteCodeId = "12345678-1234-1234-1234-123456789999";
            when(dynamoDbClient.getItem(any(GetItemRequest.class)))
                .thenReturn(GetItemResponse.builder().item(Collections.emptyMap()).build());

            // When
            repository.findById(inviteCodeId);

            // Then
            ArgumentCaptor<GetItemRequest> captor = ArgumentCaptor.forClass(GetItemRequest.class);
            verify(dynamoDbClient).getItem(captor.capture());

            GetItemRequest request = captor.getValue();
            assertThat(request.tableName()).isEqualTo("InviterTable");

            // Verify keys are present
            Map<String, AttributeValue> key = request.key();
            assertThat(key).containsKey("pk");
            assertThat(key).containsKey("sk");

            // Verify PK contains the invite code ID
            assertThat(key.get("pk").s()).contains(inviteCodeId);
            assertThat(key.get("sk").s()).isEqualTo("METADATA");
        }

        @Test
        void findAllByGroupId_QueriesUserGroupIndexGSI() {
            // Given
            when(dynamoDbClient.query(any(QueryRequest.class)))
                .thenReturn(QueryResponse.builder().items(Collections.emptyList()).build());

            // When
            repository.findAllByGroupId(GROUP_ID);

            // Then
            ArgumentCaptor<QueryRequest> captor = ArgumentCaptor.forClass(QueryRequest.class);
            verify(dynamoDbClient).query(captor.capture());

            QueryRequest request = captor.getValue();
            assertThat(request.tableName()).isEqualTo("InviterTable");
            assertThat(request.indexName()).isEqualTo("UserGroupIndex");
            assertThat(request.keyConditionExpression()).contains("gsi1pk");
            assertThat(request.keyConditionExpression()).contains("begins_with");
            assertThat(request.keyConditionExpression()).contains("gsi1sk");

            // Verify the group ID is in the query values
            assertThat(request.expressionAttributeValues()).containsKey(":gsi1pk");
            String gsi1pkValue = request.expressionAttributeValues().get(":gsi1pk").s();
            assertThat(gsi1pkValue).contains(GROUP_ID);

            // Verify we're querying for CREATED# prefix
            assertThat(request.expressionAttributeValues()).containsKey(":gsi1sk_prefix");
            String gsi1skPrefix = request.expressionAttributeValues().get(":gsi1sk_prefix").s();
            assertThat(gsi1skPrefix).isEqualTo("CREATED#");
        }

        @Test
        void delete_UsesDeleteItemWithCorrectKeys() {
            // Given
            String inviteCodeId = "12345678-1234-1234-1234-123456789999";
            when(dynamoDbClient.deleteItem(any(DeleteItemRequest.class)))
                .thenReturn(DeleteItemResponse.builder().build());

            // When
            repository.delete(inviteCodeId);

            // Then
            ArgumentCaptor<DeleteItemRequest> captor = ArgumentCaptor.forClass(DeleteItemRequest.class);
            verify(dynamoDbClient).deleteItem(captor.capture());

            DeleteItemRequest request = captor.getValue();
            assertThat(request.tableName()).isEqualTo("InviterTable");

            // Verify keys are present
            Map<String, AttributeValue> key = request.key();
            assertThat(key).containsKey("pk");
            assertThat(key).containsKey("sk");

            // Verify PK contains the invite code ID
            assertThat(key.get("pk").s()).contains(inviteCodeId);
            assertThat(key.get("sk").s()).isEqualTo("METADATA");
        }

        @Test
        void save_IncludesImportantFieldsInItem() {
            // Given
            InviteCode code = createActiveCode(CODE);
            code.setInviteCodeId("test-id-123");
            when(dynamoDbClient.putItem(any(PutItemRequest.class)))
                .thenReturn(PutItemResponse.builder().build());

            // When
            repository.save(code);

            // Then
            ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
            verify(dynamoDbClient).putItem(captor.capture());

            Map<String, AttributeValue> item = captor.getValue().item();

            // Verify critical fields are present (not exhaustive, but key ones)
            assertThat(item).containsKey("inviteCodeId");
            assertThat(item).containsKey("code");
            assertThat(item).containsKey("groupId");
            assertThat(item).containsKey("active");  // JavaBean convention: isActive() → "active"

            // Verify GSI keys are set for querying
            assertThat(item).containsKey("gsi3pk");  // For InviteCodeIndex
            assertThat(item).containsKey("gsi3sk");
            assertThat(item).containsKey("gsi1pk");  // For UserGroupIndex
            assertThat(item).containsKey("gsi1sk");
        }
    }
}
