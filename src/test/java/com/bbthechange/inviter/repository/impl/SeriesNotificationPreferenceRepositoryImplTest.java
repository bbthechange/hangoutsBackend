package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.model.SeriesNotificationPreference;
import com.bbthechange.inviter.util.InviterKeyFactory;
import com.bbthechange.inviter.util.NudgeTypes;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchGetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeriesNotificationPreferenceRepositoryImplTest {

    private static final String TABLE_NAME = "InviterTable";

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private DynamoDbEnhancedClient dynamoDbEnhancedClient;

    @SuppressWarnings("unchecked")
    private final DynamoDbTable<SeriesNotificationPreference> preferenceTable =
        org.mockito.Mockito.mock(DynamoDbTable.class);

    private SeriesNotificationPreferenceRepositoryImpl repository;
    private MeterRegistry meterRegistry;

    private String userId;
    private String seriesId;

    @BeforeEach
    void setUp() {
        lenient().when(dynamoDbEnhancedClient.table(eq(TABLE_NAME), any(TableSchema.class)))
            .thenReturn(preferenceTable);
        meterRegistry = new SimpleMeterRegistry();
        repository = new SeriesNotificationPreferenceRepositoryImpl(
            dynamoDbClient, dynamoDbEnhancedClient, meterRegistry);

        userId = UUID.randomUUID().toString();
        seriesId = UUID.randomUUID().toString();
    }

    // ============================================================================
    // find
    // ============================================================================

    @Test
    void find_ReturnsEmpty_WhenRowAbsent() {
        when(preferenceTable.getItem(any(java.util.function.Consumer.class))).thenReturn(null);

        Optional<SeriesNotificationPreference> result = repository.find(userId, seriesId);

        assertThat(result).isEmpty();
    }

    @Test
    void find_ReturnsPreference_WhenRowPresent() {
        SeriesNotificationPreference pref = new SeriesNotificationPreference(userId, seriesId);
        pref.getMutedNudgeTypes().put(NudgeTypes.HOST_NUDGE, true);
        when(preferenceTable.getItem(any(java.util.function.Consumer.class))).thenReturn(pref);

        Optional<SeriesNotificationPreference> result = repository.find(userId, seriesId);

        assertThat(result).isPresent();
        assertThat(result.get().getMutedNudgeTypes()).containsEntry(NudgeTypes.HOST_NUDGE, true);
    }

    // ============================================================================
    // setMuted(muted=true)
    // ============================================================================

    @Test
    void setMuted_True_CreatesRow_WhenRowAbsent() {
        // Create-attempt succeeds (no conditional failure)
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
            .thenReturn(UpdateItemResponse.builder().build());

        repository.setMuted(userId, seriesId, NudgeTypes.HOST_NUDGE, true);

        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDbClient).updateItem(captor.capture());
        UpdateItemRequest request = captor.getValue();

        assertThat(request.tableName()).isEqualTo(TABLE_NAME);
        assertThat(request.key().get("pk").s()).isEqualTo(InviterKeyFactory.getUserPk(userId));
        assertThat(request.key().get("sk").s()).isEqualTo(InviterKeyFactory.getSeriesPrefSk(seriesId));
        assertThat(request.conditionExpression()).isEqualTo("attribute_not_exists(pk)");
        assertThat(request.updateExpression()).contains("SET mutedNudgeTypes = :map");
        AttributeValue mapAttr = request.expressionAttributeValues().get(":map");
        assertThat(mapAttr.m()).containsKey(NudgeTypes.HOST_NUDGE);
        assertThat(mapAttr.m().get(NudgeTypes.HOST_NUDGE).bool()).isTrue();
    }

    @Test
    void setMuted_True_FallsBackToInPlaceUpdate_WhenRowExists() {
        // First call (create) fails conditional, second call (in-place) succeeds.
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
            .thenThrow(ConditionalCheckFailedException.builder().message("exists").build())
            .thenReturn(UpdateItemResponse.builder().build());

        repository.setMuted(userId, seriesId, NudgeTypes.HOST_NUDGE, true);

        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDbClient, org.mockito.Mockito.times(2)).updateItem(captor.capture());
        List<UpdateItemRequest> requests = captor.getAllValues();

        // Second call should be the in-place SET for a single map key, guarded
        // by attribute_exists(pk) so a concurrent delete forces another retry.
        UpdateItemRequest update = requests.get(1);
        assertThat(update.updateExpression()).contains("SET mutedNudgeTypes.#k = :true");
        assertThat(update.conditionExpression()).isEqualTo("attribute_exists(pk)");
        assertThat(update.expressionAttributeNames()).containsEntry("#k", NudgeTypes.HOST_NUDGE);
        assertThat(update.expressionAttributeValues().get(":true").bool()).isTrue();
    }

    @Test
    void setMuted_True_RetriesCreate_WhenInPlaceUpdateMissesDueToConcurrentDelete() {
        // create fails (exists) → in-place fails (deleted between attempts) → create succeeds.
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
            .thenThrow(ConditionalCheckFailedException.builder().message("exists").build())
            .thenThrow(ConditionalCheckFailedException.builder().message("not exists").build())
            .thenReturn(UpdateItemResponse.builder().build());

        repository.setMuted(userId, seriesId, NudgeTypes.HOST_NUDGE, true);

        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(dynamoDbClient, org.mockito.Mockito.times(3)).updateItem(captor.capture());
        // Third call should be back to the create path (full row + map).
        UpdateItemRequest third = captor.getAllValues().get(2);
        assertThat(third.conditionExpression()).isEqualTo("attribute_not_exists(pk)");
        assertThat(third.expressionAttributeValues()).containsKey(":userId");
        assertThat(third.expressionAttributeValues()).containsKey(":seriesId");
    }

    // ============================================================================
    // setMuted(muted=false) — removes key, drops row when map empties
    // ============================================================================

    @Test
    void setMuted_False_RemovesKey_LeavesRow_WhenMapStillNonEmpty() {
        // REMOVE succeeded; returned attributes show map still has one entry.
        Map<String, AttributeValue> remainingMap = Map.of(
            "OTHER_NUDGE", AttributeValue.builder().bool(true).build());
        Map<String, AttributeValue> attrs = new HashMap<>();
        attrs.put("mutedNudgeTypes", AttributeValue.builder().m(remainingMap).build());
        UpdateItemResponse removeResponse = UpdateItemResponse.builder().attributes(attrs).build();

        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenReturn(removeResponse);

        repository.setMuted(userId, seriesId, NudgeTypes.HOST_NUDGE, false);

        verify(dynamoDbClient).updateItem(any(UpdateItemRequest.class));
        verify(dynamoDbClient, never()).deleteItem(any(DeleteItemRequest.class));
    }

    @Test
    void setMuted_False_DeletesRow_WhenMapBecomesEmpty() {
        // REMOVE succeeded; returned attributes show map is empty.
        Map<String, AttributeValue> attrs = new HashMap<>();
        attrs.put("mutedNudgeTypes",
            AttributeValue.builder().m(Collections.emptyMap()).build());
        UpdateItemResponse removeResponse = UpdateItemResponse.builder().attributes(attrs).build();

        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenReturn(removeResponse);

        repository.setMuted(userId, seriesId, NudgeTypes.HOST_NUDGE, false);

        // Should delete the row with a guard against concurrent re-population.
        ArgumentCaptor<DeleteItemRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteItemRequest.class);
        verify(dynamoDbClient).deleteItem(deleteCaptor.capture());
        DeleteItemRequest delete = deleteCaptor.getValue();
        assertThat(delete.key().get("pk").s()).isEqualTo(InviterKeyFactory.getUserPk(userId));
        assertThat(delete.key().get("sk").s()).isEqualTo(InviterKeyFactory.getSeriesPrefSk(seriesId));
        assertThat(delete.conditionExpression())
            .contains("attribute_not_exists(mutedNudgeTypes) OR size(mutedNudgeTypes) = :zero");
    }

    @Test
    void setMuted_False_NoOp_WhenRowAbsent() {
        // REMOVE conditional fails because row didn't exist.
        when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
            .thenThrow(ConditionalCheckFailedException.builder().message("missing").build());

        repository.setMuted(userId, seriesId, NudgeTypes.HOST_NUDGE, false);

        verify(dynamoDbClient, never()).deleteItem(any(DeleteItemRequest.class));
    }

    // ============================================================================
    // findMutedUsersForSeries — bulk filter by nudgeType
    // ============================================================================

    @Test
    void findMutedUsersForSeries_ReturnsEmpty_WhenNoCandidates() {
        Set<String> result = repository.findMutedUsersForSeries(
            seriesId, NudgeTypes.HOST_NUDGE, Collections.emptyList());

        assertThat(result).isEmpty();
        verify(dynamoDbClient, never()).batchGetItem(any(BatchGetItemRequest.class));
    }

    @Test
    void findMutedUsersForSeries_ReturnsOnlyUsersMutedForRequestedNudgeType() {
        String userA = UUID.randomUUID().toString();
        String userB = UUID.randomUUID().toString();
        String userC = UUID.randomUUID().toString();

        // A: muted HOST_NUDGE
        Map<String, AttributeValue> rowA = Map.of(
            "userId", AttributeValue.builder().s(userA).build(),
            "mutedNudgeTypes", AttributeValue.builder().m(Map.of(
                NudgeTypes.HOST_NUDGE, AttributeValue.builder().bool(true).build()
            )).build()
        );
        // B: muted for a DIFFERENT nudge type only — must NOT be returned
        Map<String, AttributeValue> rowB = Map.of(
            "userId", AttributeValue.builder().s(userB).build(),
            "mutedNudgeTypes", AttributeValue.builder().m(Map.of(
                "OTHER_NUDGE", AttributeValue.builder().bool(true).build()
            )).build()
        );
        // C: muted for both — must be returned
        Map<String, AttributeValue> rowC = Map.of(
            "userId", AttributeValue.builder().s(userC).build(),
            "mutedNudgeTypes", AttributeValue.builder().m(Map.of(
                NudgeTypes.HOST_NUDGE, AttributeValue.builder().bool(true).build(),
                "OTHER_NUDGE", AttributeValue.builder().bool(true).build()
            )).build()
        );

        BatchGetItemResponse response = BatchGetItemResponse.builder()
            .responses(Map.of(TABLE_NAME, Arrays.asList(rowA, rowB, rowC)))
            .build();
        when(dynamoDbClient.batchGetItem(any(BatchGetItemRequest.class))).thenReturn(response);

        Set<String> result = repository.findMutedUsersForSeries(
            seriesId, NudgeTypes.HOST_NUDGE, Arrays.asList(userA, userB, userC));

        assertThat(result).containsExactlyInAnyOrder(userA, userC);
    }

    @Test
    void findMutedUsersForSeries_IgnoresEntriesWithFlagFalse() {
        String userA = UUID.randomUUID().toString();
        Map<String, AttributeValue> row = Map.of(
            "userId", AttributeValue.builder().s(userA).build(),
            "mutedNudgeTypes", AttributeValue.builder().m(Map.of(
                NudgeTypes.HOST_NUDGE, AttributeValue.builder().bool(false).build()
            )).build()
        );
        BatchGetItemResponse response = BatchGetItemResponse.builder()
            .responses(Map.of(TABLE_NAME, List.of(row)))
            .build();
        when(dynamoDbClient.batchGetItem(any(BatchGetItemRequest.class))).thenReturn(response);

        Set<String> result = repository.findMutedUsersForSeries(
            seriesId, NudgeTypes.HOST_NUDGE, List.of(userA));

        assertThat(result).isEmpty();
    }

    @Test
    void findMutedUsersForSeries_SendsCorrectKeys() {
        String userA = UUID.randomUUID().toString();
        BatchGetItemResponse response = BatchGetItemResponse.builder()
            .responses(Map.of(TABLE_NAME, Collections.emptyList()))
            .build();
        when(dynamoDbClient.batchGetItem(any(BatchGetItemRequest.class))).thenReturn(response);

        repository.findMutedUsersForSeries(
            seriesId, NudgeTypes.HOST_NUDGE, List.of(userA));

        ArgumentCaptor<BatchGetItemRequest> captor = ArgumentCaptor.forClass(BatchGetItemRequest.class);
        verify(dynamoDbClient).batchGetItem(captor.capture());
        BatchGetItemRequest request = captor.getValue();

        List<Map<String, AttributeValue>> keys = request.requestItems().get(TABLE_NAME).keys();
        assertThat(keys).hasSize(1);
        assertThat(keys.get(0).get("pk").s()).isEqualTo(InviterKeyFactory.getUserPk(userA));
        assertThat(keys.get(0).get("sk").s()).isEqualTo(InviterKeyFactory.getSeriesPrefSk(seriesId));
    }

    @Test
    void findMutedUsersForSeries_RetriesUnprocessedKeys_AndIncludesAllMutedUsers() {
        String userA = UUID.randomUUID().toString();
        String userB = UUID.randomUUID().toString();

        // First call: returns A muted but unprocessed key for B (simulated throttle).
        Map<String, AttributeValue> rowA = Map.of(
            "userId", AttributeValue.builder().s(userA).build(),
            "mutedNudgeTypes", AttributeValue.builder().m(Map.of(
                NudgeTypes.HOST_NUDGE, AttributeValue.builder().bool(true).build()
            )).build()
        );
        Map<String, AttributeValue> bKey = new HashMap<>();
        bKey.put("pk", AttributeValue.builder().s(InviterKeyFactory.getUserPk(userB)).build());
        bKey.put("sk", AttributeValue.builder().s(InviterKeyFactory.getSeriesPrefSk(seriesId)).build());
        BatchGetItemResponse firstResponse = BatchGetItemResponse.builder()
            .responses(Map.of(TABLE_NAME, List.of(rowA)))
            .unprocessedKeys(Map.of(TABLE_NAME,
                KeysAndAttributes.builder().keys(List.of(bKey)).build()))
            .build();

        // Retry: returns B muted, no more unprocessed keys.
        Map<String, AttributeValue> rowB = Map.of(
            "userId", AttributeValue.builder().s(userB).build(),
            "mutedNudgeTypes", AttributeValue.builder().m(Map.of(
                NudgeTypes.HOST_NUDGE, AttributeValue.builder().bool(true).build()
            )).build()
        );
        BatchGetItemResponse secondResponse = BatchGetItemResponse.builder()
            .responses(Map.of(TABLE_NAME, List.of(rowB)))
            .build();

        when(dynamoDbClient.batchGetItem(any(BatchGetItemRequest.class)))
            .thenReturn(firstResponse)
            .thenReturn(secondResponse);

        Set<String> result = repository.findMutedUsersForSeries(
            seriesId, NudgeTypes.HOST_NUDGE, Arrays.asList(userA, userB));

        // Both muted users must be excluded from notifications — neither dropped.
        assertThat(result).containsExactlyInAnyOrder(userA, userB);
        verify(dynamoDbClient, org.mockito.Mockito.times(2)).batchGetItem(any(BatchGetItemRequest.class));

        // Retry should be on the unprocessed key only.
        ArgumentCaptor<BatchGetItemRequest> captor = ArgumentCaptor.forClass(BatchGetItemRequest.class);
        verify(dynamoDbClient, org.mockito.Mockito.times(2)).batchGetItem(captor.capture());
        List<Map<String, AttributeValue>> retryKeys =
            captor.getAllValues().get(1).requestItems().get(TABLE_NAME).keys();
        assertThat(retryKeys).hasSize(1);
        assertThat(retryKeys.get(0).get("pk").s()).isEqualTo(InviterKeyFactory.getUserPk(userB));

        // No drops recorded — retry succeeded before exhaustion.
        assertThat(meterRegistry.find("series_pref_batchget_dropped").counter()).isNull();
    }

    @Test
    void findMutedUsersForSeries_IncrementsDroppedCounter_WhenRetryBudgetExhausted() {
        String userA = UUID.randomUUID().toString();

        Map<String, AttributeValue> aKey = new HashMap<>();
        aKey.put("pk", AttributeValue.builder().s(InviterKeyFactory.getUserPk(userA)).build());
        aKey.put("sk", AttributeValue.builder().s(InviterKeyFactory.getSeriesPrefSk(seriesId)).build());
        BatchGetItemResponse throttledResponse = BatchGetItemResponse.builder()
            .responses(Map.of(TABLE_NAME, Collections.emptyList()))
            .unprocessedKeys(Map.of(TABLE_NAME,
                KeysAndAttributes.builder().keys(List.of(aKey)).build()))
            .build();

        // Every call returns unprocessed keys — retry budget gets exhausted.
        when(dynamoDbClient.batchGetItem(any(BatchGetItemRequest.class))).thenReturn(throttledResponse);

        Set<String> result = repository.findMutedUsersForSeries(
            seriesId, NudgeTypes.HOST_NUDGE, List.of(userA));

        // No muted users surfaced — this is the failure mode the counter tracks.
        assertThat(result).isEmpty();

        // 1 initial + 3 retries = 4 total calls before giving up.
        verify(dynamoDbClient, org.mockito.Mockito.times(4)).batchGetItem(any(BatchGetItemRequest.class));

        assertThat(meterRegistry.find("series_pref_batchget_dropped")
                .tag("nudgeType", NudgeTypes.HOST_NUDGE)
                .tag("reason", "exhausted")
                .counter().count()).isEqualTo(1.0);
    }

    // ============================================================================
    // delete
    // ============================================================================

    @Test
    void delete_SendsDeleteWithCorrectKey() {
        repository.delete(userId, seriesId);

        ArgumentCaptor<DeleteItemRequest> captor = ArgumentCaptor.forClass(DeleteItemRequest.class);
        verify(dynamoDbClient).deleteItem(captor.capture());
        DeleteItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo(TABLE_NAME);
        assertThat(request.key().get("pk").s()).isEqualTo(InviterKeyFactory.getUserPk(userId));
        assertThat(request.key().get("sk").s()).isEqualTo(InviterKeyFactory.getSeriesPrefSk(seriesId));
    }
}
