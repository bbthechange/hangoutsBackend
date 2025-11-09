package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.model.EventSeries;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.model.HangoutPointer;
import com.bbthechange.inviter.model.SeriesPointer;
import com.bbthechange.inviter.exception.RepositoryException;
import com.bbthechange.inviter.util.QueryPerformanceTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.reset;

@ExtendWith(MockitoExtension.class)
class SeriesTransactionRepositoryTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private QueryPerformanceTracker performanceTracker;

    private SeriesTransactionRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new SeriesTransactionRepositoryImpl(dynamoDbClient, performanceTracker);
        
        // Mock performance tracker to execute the supplier directly
        when(performanceTracker.trackQuery(anyString(), anyString(), any()))
            .thenAnswer(invocation -> {
                Supplier<?> supplier = invocation.getArgument(2);
                return supplier.get();
            });
    }

    // Test fixture creation methods
    private EventSeries createTestEventSeries() {
        String seriesId = UUID.randomUUID().toString();
        String groupId = UUID.randomUUID().toString();
        EventSeries series = new EventSeries();
        series.setSeriesId(seriesId);
        series.setSeriesTitle("Test Series");
        series.setSeriesDescription("Test Description");
        series.setGroupId(groupId);
        series.setVersion(1L);
        return series;
    }

    private Hangout createTestHangout(String hangoutId) {
        if (hangoutId == null) {
            hangoutId = UUID.randomUUID().toString();
        }
        Hangout hangout = new Hangout();
        hangout.setHangoutId(hangoutId);
        hangout.setTitle("Test Hangout " + hangoutId);
        hangout.setDescription("Test Description");
        hangout.setVersion(1L);
        return hangout;
    }

    private HangoutPointer createTestPointer(String hangoutId, String groupId) {
        if (hangoutId == null) {
            hangoutId = UUID.randomUUID().toString();
        }
        if (groupId == null) {
            groupId = UUID.randomUUID().toString();
        }
        HangoutPointer pointer = new HangoutPointer();
        pointer.setPk("GROUP#" + groupId);
        pointer.setSk("HANGOUT#" + hangoutId);
        pointer.setHangoutId(hangoutId);
        pointer.setTitle("Test Hangout " + hangoutId);
        return pointer;
    }

    // 1. createSeriesWithNewPart() Tests

    @Test
    void createSeriesWithNewPart_WithValidInputs_ShouldCreateTransactionWithCorrectItems() {
        // Given
        EventSeries series = createTestEventSeries();
        String hangout1Id = UUID.randomUUID().toString();
        String hangout2Id = UUID.randomUUID().toString();
        Hangout existingHangout = createTestHangout(hangout1Id);
        List<HangoutPointer> existingPointers = Arrays.asList(
            createTestPointer(hangout1Id, series.getGroupId()),
            createTestPointer(hangout1Id, "group-2")
        );
        Hangout newHangout = createTestHangout(hangout2Id);
        List<HangoutPointer> newPointers = Arrays.asList(
            createTestPointer(hangout2Id, series.getGroupId()),
            createTestPointer(hangout2Id, "group-2")
        );

        // When
        repository.createSeriesWithNewPart(
            series, existingHangout, existingPointers, 
            newHangout, newPointers, Collections.emptyList()
        );

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor = 
            ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());
        
        TransactWriteItemsRequest request = captor.getValue();
        // Should have: 1 series + 1 hangout update + 2 pointer updates + 1 new hangout + 2 new pointers = 7 items
        assertThat(request.transactItems()).hasSize(7);
        
        // Verify operation types in correct order
        List<TransactWriteItem> items = request.transactItems();
        assertThat(items.get(0).put()).isNotNull(); // Create series
        assertThat(items.get(1).update()).isNotNull(); // Update existing hangout
        assertThat(items.get(2).update()).isNotNull(); // Update existing pointer 1
        assertThat(items.get(3).update()).isNotNull(); // Update existing pointer 2
        assertThat(items.get(4).put()).isNotNull(); // Create new hangout
        assertThat(items.get(5).put()).isNotNull(); // Create new pointer 1
        assertThat(items.get(6).put()).isNotNull(); // Create new pointer 2
        
        // Verify performance tracking
        verify(performanceTracker).trackQuery(eq("createSeriesWithNewPart"), eq("InviterTable"), any());
    }

    @Test
    void createSeriesWithNewPart_WithTransactionCancellation_ShouldThrowRepositoryException() {
        // Given
        EventSeries series = createTestEventSeries();
        String hangout1Id = UUID.randomUUID().toString();
        String hangout2Id = UUID.randomUUID().toString();
        Hangout existingHangout = createTestHangout(hangout1Id);
        List<HangoutPointer> existingPointers = Arrays.asList(
            createTestPointer(hangout1Id, series.getGroupId())
        );
        Hangout newHangout = createTestHangout(hangout2Id);
        List<HangoutPointer> newPointers = Arrays.asList(
            createTestPointer(hangout2Id, series.getGroupId())
        );
        
        TransactionCanceledException canceledException = TransactionCanceledException.builder()
            .message("Transaction cancelled")
            .build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
            .thenThrow(canceledException);

        // When & Then
        assertThatThrownBy(() -> repository.createSeriesWithNewPart(
            series, existingHangout, existingPointers, newHangout, newPointers, Collections.emptyList()))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("transaction cancelled")
            .hasCause(canceledException);
    }

    @Test
    void createSeriesWithNewPart_WithDynamoDbError_ShouldThrowRepositoryException() {
        // Given
        EventSeries series = createTestEventSeries();
        String hangout1Id = UUID.randomUUID().toString();
        String hangout2Id = UUID.randomUUID().toString();
        Hangout existingHangout = createTestHangout(hangout1Id);
        List<HangoutPointer> existingPointers = Arrays.asList(
            createTestPointer(hangout1Id, series.getGroupId())
        );
        Hangout newHangout = createTestHangout(hangout2Id);
        List<HangoutPointer> newPointers = Arrays.asList(
            createTestPointer(hangout2Id, series.getGroupId())
        );
        
        DynamoDbException dynamoException = ProvisionedThroughputExceededException.builder()
            .message("DynamoDB error")
            .build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
            .thenThrow(dynamoException);

        // When & Then
        assertThatThrownBy(() -> repository.createSeriesWithNewPart(
            series, existingHangout, existingPointers, newHangout, newPointers, Collections.emptyList()))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("DynamoDB error")
            .hasCause(dynamoException);
    }

    @Test
    void createSeriesWithNewPart_ShouldHaveCorrectUpdateExpressions() {
        // Given
        EventSeries series = createTestEventSeries();
        String hangout1Id = UUID.randomUUID().toString();
        String hangout2Id = UUID.randomUUID().toString();
        Hangout existingHangout = createTestHangout(hangout1Id);
        List<HangoutPointer> existingPointers = Arrays.asList(
            createTestPointer(hangout1Id, series.getGroupId())
        );
        Hangout newHangout = createTestHangout(hangout2Id);
        List<HangoutPointer> newPointers = Arrays.asList(
            createTestPointer(hangout2Id, series.getGroupId())
        );

        // When
        repository.createSeriesWithNewPart(
            series, existingHangout, existingPointers, 
            newHangout, newPointers, Collections.emptyList()
        );

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor = 
            ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());
        
        List<TransactWriteItem> items = captor.getValue().transactItems();
        
        // Verify hangout update expression
        Update hangoutUpdate = items.get(1).update();
        assertThat(hangoutUpdate.updateExpression())
            .contains("SET seriesId = :sid")
            .contains("updatedAt = :updated")
            .contains("version = version + :inc");
        assertThat(hangoutUpdate.expressionAttributeValues()).containsKeys(":sid", ":updated", ":inc");
        
        // Verify pointer update expression
        Update pointerUpdate = items.get(2).update();
        assertThat(pointerUpdate.updateExpression())
            .contains("SET seriesId = :sid")
            .contains("updatedAt = :updated");
        assertThat(pointerUpdate.expressionAttributeValues()).containsKeys(":sid", ":updated");
    }

    @Test
    void createSeriesWithNewPart_ShouldHaveCorrectKeysForUpdates() {
        // Given
        EventSeries series = createTestEventSeries();
        String hangout1Id = UUID.randomUUID().toString();
        String hangout2Id = UUID.randomUUID().toString();
        Hangout existingHangout = createTestHangout(hangout1Id);
        HangoutPointer existingPointer = createTestPointer(hangout1Id, series.getGroupId());
        List<HangoutPointer> existingPointers = Arrays.asList(existingPointer);
        Hangout newHangout = createTestHangout(hangout2Id);
        List<HangoutPointer> newPointers = Arrays.asList(
            createTestPointer(hangout2Id, series.getGroupId())
        );

        // When
        repository.createSeriesWithNewPart(
            series, existingHangout, existingPointers, 
            newHangout, newPointers, Collections.emptyList()
        );

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor = 
            ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());
        
        List<TransactWriteItem> items = captor.getValue().transactItems();
        
        // Verify hangout update key uses EVENT# pattern and METADATA
        Update hangoutUpdate = items.get(1).update();
        assertThat(hangoutUpdate.key().get("pk").s()).startsWith("EVENT#").contains(hangout1Id);
        assertThat(hangoutUpdate.key().get("sk").s()).isEqualTo("METADATA");
        
        // Verify pointer update key uses the actual pointer's pk/sk
        Update pointerUpdate = items.get(2).update();
        assertThat(pointerUpdate.key().get("pk").s()).isEqualTo(existingPointer.getPk());
        assertThat(pointerUpdate.key().get("sk").s()).isEqualTo(existingPointer.getSk());
    }

    // 2. addPartToExistingSeries() Tests

    @Test
    void addPartToExistingSeries_WithValidInputs_ShouldCreateTransactionWithCorrectItems() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String hangout3Id = UUID.randomUUID().toString();
        String groupId = UUID.randomUUID().toString();
        Hangout newHangout = createTestHangout(hangout3Id);
        List<HangoutPointer> newPointers = Arrays.asList(
            createTestPointer(hangout3Id, groupId),
            createTestPointer(hangout3Id, "group-2")
        );

        // When
        repository.addPartToExistingSeries(seriesId, newHangout, newPointers, Collections.emptyList());

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor = 
            ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());
        
        TransactWriteItemsRequest request = captor.getValue();
        // Should have: 1 series update + 1 new hangout + 2 new pointers = 4 items
        assertThat(request.transactItems()).hasSize(4);
        
        // Verify operation types in correct order
        List<TransactWriteItem> items = request.transactItems();
        assertThat(items.get(0).update()).isNotNull(); // Update series
        assertThat(items.get(1).put()).isNotNull(); // Create new hangout
        assertThat(items.get(2).put()).isNotNull(); // Create new pointer 1
        assertThat(items.get(3).put()).isNotNull(); // Create new pointer 2
        
        // Verify performance tracking
        verify(performanceTracker).trackQuery(eq("addPartToExistingSeries"), eq("InviterTable"), any());
    }

    @Test
    void addPartToExistingSeries_ShouldHaveCorrectListAppendExpression() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String hangout3Id = UUID.randomUUID().toString();
        String groupId = UUID.randomUUID().toString();
        Hangout newHangout = createTestHangout(hangout3Id);
        List<HangoutPointer> newPointers = Arrays.asList(
            createTestPointer(hangout3Id, groupId)
        );

        // When
        repository.addPartToExistingSeries(seriesId, newHangout, newPointers, Collections.emptyList());

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor = 
            ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());
        
        List<TransactWriteItem> items = captor.getValue().transactItems();
        
        // Verify series update expression uses list_append
        Update seriesUpdate = items.get(0).update();
        assertThat(seriesUpdate.updateExpression())
            .contains("SET hangoutIds = list_append(hangoutIds, :newId)")
            .contains("updatedAt = :updated")
            .contains("version = version + :inc");
        
        // Verify :newId is a DynamoDB List with single string element
        AttributeValue newIdValue = seriesUpdate.expressionAttributeValues().get(":newId");
        assertThat(newIdValue.l()).hasSize(1);
        assertThat(newIdValue.l().get(0).s()).isEqualTo(newHangout.getHangoutId());
        
        assertThat(seriesUpdate.expressionAttributeValues()).containsKeys(":newId", ":updated", ":inc");
    }

    @Test
    void addPartToExistingSeries_WithTransactionCancellation_ShouldThrowRepositoryException() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String hangout3Id = UUID.randomUUID().toString();
        String groupId = UUID.randomUUID().toString();
        Hangout newHangout = createTestHangout(hangout3Id);
        List<HangoutPointer> newPointers = Arrays.asList(
            createTestPointer(hangout3Id, groupId)
        );
        
        TransactionCanceledException canceledException = TransactionCanceledException.builder()
            .message("Transaction cancelled")
            .build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
            .thenThrow(canceledException);

        // When & Then
        assertThatThrownBy(() -> repository.addPartToExistingSeries(seriesId, newHangout, newPointers, Collections.emptyList()))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("transaction cancelled")
            .hasCause(canceledException);
    }

    @Test
    void addPartToExistingSeries_WithDynamoDbError_ShouldThrowRepositoryException() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String hangout3Id = UUID.randomUUID().toString();
        String groupId = UUID.randomUUID().toString();
        Hangout newHangout = createTestHangout(hangout3Id);
        List<HangoutPointer> newPointers = Arrays.asList(
            createTestPointer(hangout3Id, groupId)
        );
        
        DynamoDbException dynamoException = ProvisionedThroughputExceededException.builder()
            .message("DynamoDB error")
            .build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
            .thenThrow(dynamoException);

        // When & Then
        assertThatThrownBy(() -> repository.addPartToExistingSeries(seriesId, newHangout, newPointers, Collections.emptyList()))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("DynamoDB error")
            .hasCause(dynamoException);
    }

    // 3. Performance Tracking Tests

    @Test
    void createSeriesWithNewPart_ShouldUsePerformanceTracker() {
        // Given
        EventSeries series = createTestEventSeries();
        String hangout1Id = UUID.randomUUID().toString();
        String hangout2Id = UUID.randomUUID().toString();
        Hangout existingHangout = createTestHangout(hangout1Id);
        List<HangoutPointer> existingPointers = Arrays.asList(
            createTestPointer(hangout1Id, series.getGroupId())
        );
        Hangout newHangout = createTestHangout(hangout2Id);
        List<HangoutPointer> newPointers = Arrays.asList(
            createTestPointer(hangout2Id, series.getGroupId())
        );

        // When
        repository.createSeriesWithNewPart(
            series, existingHangout, existingPointers, 
            newHangout, newPointers, Collections.emptyList()
        );

        // Then
        verify(performanceTracker).trackQuery(
            eq("createSeriesWithNewPart"), 
            eq("InviterTable"), 
            any(Supplier.class)
        );
    }

    @Test
    void addPartToExistingSeries_ShouldUsePerformanceTracker() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String hangout3Id = UUID.randomUUID().toString();
        String groupId = UUID.randomUUID().toString();
        Hangout newHangout = createTestHangout(hangout3Id);
        List<HangoutPointer> newPointers = Arrays.asList(
            createTestPointer(hangout3Id, groupId)
        );

        // When
        repository.addPartToExistingSeries(seriesId, newHangout, newPointers, Collections.emptyList());

        // Then
        verify(performanceTracker).trackQuery(
            eq("addPartToExistingSeries"), 
            eq("InviterTable"), 
            any(Supplier.class)
        );
    }

    @Test
    void createSeriesWithNewPart_WhenPerformanceTrackerThrowsException_ShouldPropagateException() {
        // Given
        EventSeries series = createTestEventSeries();
        String hangout1Id = UUID.randomUUID().toString();
        String hangout2Id = UUID.randomUUID().toString();
        Hangout existingHangout = createTestHangout(hangout1Id);
        List<HangoutPointer> existingPointers = Arrays.asList(
            createTestPointer(hangout1Id, series.getGroupId())
        );
        Hangout newHangout = createTestHangout(hangout2Id);
        List<HangoutPointer> newPointers = Arrays.asList(
            createTestPointer(hangout2Id, series.getGroupId())
        );
        
        RuntimeException trackingException = new RuntimeException("Tracking failed");
        // Reset the mock to override the default behavior
        reset(performanceTracker);
        when(performanceTracker.trackQuery(anyString(), anyString(), any()))
            .thenThrow(trackingException);

        // When & Then
        assertThatThrownBy(() -> repository.createSeriesWithNewPart(
            series, existingHangout, existingPointers, newHangout, newPointers, Collections.emptyList()))
            .isEqualTo(trackingException);
    }

    // 4. Schema Mapping Tests

    @Test
    void createSeriesWithNewPart_ShouldUseTableSchemasForSerialization() {
        // Given
        EventSeries series = createTestEventSeries();
        String hangout1Id = UUID.randomUUID().toString();
        String hangout2Id = UUID.randomUUID().toString();
        Hangout existingHangout = createTestHangout(hangout1Id);
        List<HangoutPointer> existingPointers = Arrays.asList(
            createTestPointer(hangout1Id, series.getGroupId())
        );
        Hangout newHangout = createTestHangout(hangout2Id);
        List<HangoutPointer> newPointers = Arrays.asList(
            createTestPointer(hangout2Id, series.getGroupId())
        );

        // When
        repository.createSeriesWithNewPart(
            series, existingHangout, existingPointers, 
            newHangout, newPointers, Collections.emptyList()
        );

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor = 
            ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());
        
        List<TransactWriteItem> items = captor.getValue().transactItems();
        
        // Verify PUT operations have serialized item data
        assertThat(items.get(0).put().item()).isNotEmpty(); // Series creation
        assertThat(items.get(3).put().item()).isNotEmpty(); // New hangout creation
        assertThat(items.get(4).put().item()).isNotEmpty(); // New pointer creation
    }

    @Test
    void addPartToExistingSeries_ShouldUseTableSchemasForSerialization() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String hangout3Id = UUID.randomUUID().toString();
        String groupId = UUID.randomUUID().toString();
        Hangout newHangout = createTestHangout(hangout3Id);
        List<HangoutPointer> newPointers = Arrays.asList(
            createTestPointer(hangout3Id, groupId)
        );

        // When
        repository.addPartToExistingSeries(seriesId, newHangout, newPointers, Collections.emptyList());

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor = 
            ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());
        
        List<TransactWriteItem> items = captor.getValue().transactItems();
        
        // Verify PUT operations have serialized item data
        assertThat(items.get(1).put().item()).isNotEmpty(); // New hangout creation
        assertThat(items.get(2).put().item()).isNotEmpty(); // New pointer creation
    }

    // Test helper for SeriesPointer creation
    private SeriesPointer createTestSeriesPointer(String seriesId, String groupId) {
        SeriesPointer pointer = new SeriesPointer();
        pointer.setSeriesId(seriesId);
        pointer.setGroupId(groupId);
        pointer.setSeriesTitle("Test Series " + seriesId);
        pointer.setPk("GROUP#" + groupId);
        pointer.setSk("SERIES#" + seriesId);
        pointer.setVersion(1L);
        return pointer;
    }

    // 8. deleteEntireSeriesWithAllHangouts() Tests

    @Test
    void deleteEntireSeriesWithAllHangouts_WithValidInputs_ShouldCreateTransactionWithCorrectItems() {
        // Given
        EventSeries series = createTestEventSeries();
        String hangout1Id = UUID.randomUUID().toString();
        String hangout2Id = UUID.randomUUID().toString();
        List<Hangout> hangoutsToDelete = Arrays.asList(
            createTestHangout(hangout1Id),
            createTestHangout(hangout2Id)
        );
        List<HangoutPointer> pointersToDelete = Arrays.asList(
            createTestPointer(hangout1Id, series.getGroupId()),
            createTestPointer(hangout1Id, "group-2"),
            createTestPointer(hangout2Id, series.getGroupId()),
            createTestPointer(hangout2Id, "group-2")
        );
        List<SeriesPointer> seriesPointersToDelete = Arrays.asList(
            createTestSeriesPointer(series.getSeriesId(), series.getGroupId())
        );

        // When
        repository.deleteEntireSeriesWithAllHangouts(series, hangoutsToDelete, pointersToDelete, seriesPointersToDelete);

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor = 
            ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());
        
        TransactWriteItemsRequest request = captor.getValue();
        // Should have: 1 series + 2 hangouts + 4 pointers + 1 series pointer = 8 DELETE operations
        assertThat(request.transactItems()).hasSize(8);
        
        // Verify all operations are DELETE operations
        List<TransactWriteItem> items = request.transactItems();
        assertThat(items.get(0).delete()).isNotNull(); // Delete series
        assertThat(items.get(1).delete()).isNotNull(); // Delete hangout 1
        assertThat(items.get(2).delete()).isNotNull(); // Delete hangout 2
        assertThat(items.get(3).delete()).isNotNull(); // Delete pointer 1
        assertThat(items.get(4).delete()).isNotNull(); // Delete pointer 2
        assertThat(items.get(5).delete()).isNotNull(); // Delete pointer 3
        assertThat(items.get(6).delete()).isNotNull(); // Delete pointer 4
        assertThat(items.get(7).delete()).isNotNull(); // Delete series pointer
        
        // Verify performance tracking
        verify(performanceTracker).trackQuery(eq("deleteEntireSeriesWithAllHangouts"), eq("InviterTable"), any());
    }

    @Test
    void deleteEntireSeriesWithAllHangouts_WithEmptyLists_ShouldOnlyDeleteSeries() {
        // Given
        EventSeries series = createTestEventSeries();
        List<Hangout> hangoutsToDelete = Collections.emptyList();
        List<HangoutPointer> pointersToDelete = Collections.emptyList();
        List<SeriesPointer> seriesPointersToDelete = Collections.emptyList();

        // When
        repository.deleteEntireSeriesWithAllHangouts(series, hangoutsToDelete, pointersToDelete, seriesPointersToDelete);

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor = 
            ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());
        
        TransactWriteItemsRequest request = captor.getValue();
        // Should have exactly 1 DELETE operation for the series only
        assertThat(request.transactItems()).hasSize(1);
        
        // Verify it's a DELETE operation for the series
        TransactWriteItem item = request.transactItems().get(0);
        assertThat(item.delete()).isNotNull();
        assertThat(item.delete().key().get("pk").s()).isEqualTo(series.getPk());
        assertThat(item.delete().key().get("sk").s()).isEqualTo(series.getSk());
        
        // Verify performance tracking
        verify(performanceTracker).trackQuery(eq("deleteEntireSeriesWithAllHangouts"), eq("InviterTable"), any());
    }

    @Test
    void deleteEntireSeriesWithAllHangouts_WithTransactionCancellation_ShouldThrowRepositoryException() {
        // Given
        EventSeries series = createTestEventSeries();
        String hangout1Id = UUID.randomUUID().toString();
        List<Hangout> hangoutsToDelete = Arrays.asList(createTestHangout(hangout1Id));
        List<HangoutPointer> pointersToDelete = Arrays.asList(
            createTestPointer(hangout1Id, series.getGroupId())
        );
        List<SeriesPointer> seriesPointersToDelete = Arrays.asList(
            createTestSeriesPointer(series.getSeriesId(), series.getGroupId())
        );
        
        TransactionCanceledException canceledException = TransactionCanceledException.builder()
            .message("Transaction cancelled")
            .build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
            .thenThrow(canceledException);

        // When & Then
        assertThatThrownBy(() -> repository.deleteEntireSeriesWithAllHangouts(
            series, hangoutsToDelete, pointersToDelete, seriesPointersToDelete))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("transaction cancelled")
            .hasCause(canceledException);
    }

    @Test
    void deleteEntireSeriesWithAllHangouts_WithDynamoDbError_ShouldThrowRepositoryException() {
        // Given
        EventSeries series = createTestEventSeries();
        String hangout1Id = UUID.randomUUID().toString();
        List<Hangout> hangoutsToDelete = Arrays.asList(createTestHangout(hangout1Id));
        List<HangoutPointer> pointersToDelete = Arrays.asList(
            createTestPointer(hangout1Id, series.getGroupId())
        );
        List<SeriesPointer> seriesPointersToDelete = Arrays.asList(
            createTestSeriesPointer(series.getSeriesId(), series.getGroupId())
        );

        DynamoDbException dynamoException = ProvisionedThroughputExceededException.builder()
            .message("DynamoDB error")
            .build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
            .thenThrow(dynamoException);

        // When & Then
        assertThatThrownBy(() -> repository.deleteEntireSeriesWithAllHangouts(
            series, hangoutsToDelete, pointersToDelete, seriesPointersToDelete))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("DynamoDB error")
            .hasCause(dynamoException);
    }

    // 3. unlinkHangoutFromSeries() Tests

    @Test
    void unlinkHangoutFromSeries_WithValidInputs_ShouldCreateTransactionWithCorrectItems() {
        // Given
        EventSeries series = createTestEventSeries();
        series.setHangoutIds(Arrays.asList("hangout-1", "hangout-2"));
        String hangout1Id = "hangout-1";
        Hangout hangoutToUnlink = createTestHangout(hangout1Id);
        List<HangoutPointer> pointersToUpdate = Arrays.asList(
            createTestPointer(hangout1Id, series.getGroupId()),
            createTestPointer(hangout1Id, "group-2")
        );
        List<SeriesPointer> seriesPointersToUpdate = Arrays.asList(
            createTestSeriesPointer(series.getSeriesId(), series.getGroupId())
        );

        // When
        repository.unlinkHangoutFromSeries(series, hangoutToUnlink, pointersToUpdate, seriesPointersToUpdate);

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor =
            ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());

        TransactWriteItemsRequest request = captor.getValue();
        // Should have: 1 series update + 1 hangout update + 2 pointer updates + 1 series pointer = 5 items
        assertThat(request.transactItems()).hasSize(5);

        List<TransactWriteItem> items = request.transactItems();
        assertThat(items.get(0).update()).isNotNull(); // Update series
        assertThat(items.get(1).update()).isNotNull(); // Update hangout (REMOVE seriesId)
        assertThat(items.get(2).update()).isNotNull(); // Update pointer 1 (REMOVE seriesId)
        assertThat(items.get(3).update()).isNotNull(); // Update pointer 2 (REMOVE seriesId)
        assertThat(items.get(4).put()).isNotNull(); // Update series pointer

        verify(performanceTracker).trackQuery(eq("unlinkHangoutFromSeries"), eq("InviterTable"), any());
    }

    @Test
    void unlinkHangoutFromSeries_ShouldRemoveSeriesIdFromHangoutAndPointers() {
        // Given
        EventSeries series = createTestEventSeries();
        series.setHangoutIds(Arrays.asList("hangout-1"));
        String hangout1Id = "hangout-1";
        Hangout hangoutToUnlink = createTestHangout(hangout1Id);
        List<HangoutPointer> pointersToUpdate = Arrays.asList(
            createTestPointer(hangout1Id, series.getGroupId())
        );

        // When
        repository.unlinkHangoutFromSeries(series, hangoutToUnlink, pointersToUpdate, Collections.emptyList());

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor =
            ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());

        List<TransactWriteItem> items = captor.getValue().transactItems();

        // Verify hangout update removes seriesId
        Update hangoutUpdate = items.get(1).update();
        assertThat(hangoutUpdate.updateExpression())
            .contains("REMOVE seriesId")
            .contains("SET updatedAt = :updated");

        // Verify pointer update removes seriesId
        Update pointerUpdate = items.get(2).update();
        assertThat(pointerUpdate.updateExpression())
            .contains("REMOVE seriesId")
            .contains("SET updatedAt = :updated");
    }

    @Test
    void unlinkHangoutFromSeries_ShouldUpdateSeriesWithModifiedHangoutList() {
        // Given
        EventSeries series = createTestEventSeries();
        series.setHangoutIds(Arrays.asList("hangout-2")); // Already has hangout removed
        String hangout1Id = "hangout-1";
        Hangout hangoutToUnlink = createTestHangout(hangout1Id);

        // When
        repository.unlinkHangoutFromSeries(series, hangoutToUnlink, Collections.emptyList(), Collections.emptyList());

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor =
            ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());

        List<TransactWriteItem> items = captor.getValue().transactItems();

        // Verify series update expression sets the new hangoutIds list
        Update seriesUpdate = items.get(0).update();
        assertThat(seriesUpdate.updateExpression())
            .contains("SET hangoutIds = :hangoutIds")
            .contains("updatedAt = :updated")
            .contains("version = :version");
    }

    @Test
    void unlinkHangoutFromSeries_WithTransactionCancellation_ShouldThrowRepositoryException() {
        // Given
        EventSeries series = createTestEventSeries();
        series.setHangoutIds(Arrays.asList("hangout-1"));
        Hangout hangoutToUnlink = createTestHangout("hangout-1");

        TransactionCanceledException canceledException = TransactionCanceledException.builder()
            .message("Transaction cancelled")
            .build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
            .thenThrow(canceledException);

        // When & Then
        assertThatThrownBy(() -> repository.unlinkHangoutFromSeries(
            series, hangoutToUnlink, Collections.emptyList(), Collections.emptyList()))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("transaction cancelled")
            .hasCause(canceledException);
    }

    @Test
    void unlinkHangoutFromSeries_WithDynamoDbError_ShouldThrowRepositoryException() {
        // Given
        EventSeries series = createTestEventSeries();
        series.setHangoutIds(Arrays.asList("hangout-1"));
        Hangout hangoutToUnlink = createTestHangout("hangout-1");

        DynamoDbException dynamoException = ProvisionedThroughputExceededException.builder()
            .message("DynamoDB error")
            .build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
            .thenThrow(dynamoException);

        // When & Then
        assertThatThrownBy(() -> repository.unlinkHangoutFromSeries(
            series, hangoutToUnlink, Collections.emptyList(), Collections.emptyList()))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("DynamoDB error")
            .hasCause(dynamoException);
    }

    // 4. deleteEntireSeries() Tests

    @Test
    void deleteEntireSeries_WithValidInputs_ShouldCreateTransactionWithCorrectItems() {
        // Given
        EventSeries series = createTestEventSeries();
        String hangout1Id = UUID.randomUUID().toString();
        Hangout hangoutToUpdate = createTestHangout(hangout1Id);
        List<HangoutPointer> pointersToUpdate = Arrays.asList(
            createTestPointer(hangout1Id, series.getGroupId()),
            createTestPointer(hangout1Id, "group-2")
        );

        // When
        repository.deleteEntireSeries(series, hangoutToUpdate, pointersToUpdate);

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor =
            ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());

        TransactWriteItemsRequest request = captor.getValue();
        // Should have: 1 series delete + 1 hangout update + 2 pointer updates + 1 series pointer delete = 5 items
        assertThat(request.transactItems()).hasSize(5);

        List<TransactWriteItem> items = request.transactItems();
        assertThat(items.get(0).delete()).isNotNull(); // Delete series
        assertThat(items.get(1).update()).isNotNull(); // Update hangout (REMOVE seriesId)
        assertThat(items.get(2).update()).isNotNull(); // Update pointer 1 (REMOVE seriesId)
        assertThat(items.get(3).update()).isNotNull(); // Update pointer 2 (REMOVE seriesId)
        assertThat(items.get(4).delete()).isNotNull(); // Delete series pointer

        verify(performanceTracker).trackQuery(eq("deleteEntireSeries"), eq("InviterTable"), any());
    }

    @Test
    void deleteEntireSeries_ShouldDeleteSeriesRecord() {
        // Given
        EventSeries series = createTestEventSeries();
        Hangout hangoutToUpdate = createTestHangout(UUID.randomUUID().toString());

        // When
        repository.deleteEntireSeries(series, hangoutToUpdate, Collections.emptyList());

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor =
            ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());

        List<TransactWriteItem> items = captor.getValue().transactItems();

        // Verify series delete operation
        Delete seriesDelete = items.get(0).delete();
        assertThat(seriesDelete.key().get("pk").s()).isEqualTo(series.getPk());
        assertThat(seriesDelete.key().get("sk").s()).isEqualTo(series.getSk());
    }

    @Test
    void deleteEntireSeries_ShouldDeleteSeriesPointerWithConstructedKey() {
        // Given
        EventSeries series = createTestEventSeries();
        Hangout hangoutToUpdate = createTestHangout(UUID.randomUUID().toString());

        // When
        repository.deleteEntireSeries(series, hangoutToUpdate, Collections.emptyList());

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor =
            ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());

        List<TransactWriteItem> items = captor.getValue().transactItems();

        // Verify series pointer delete uses GROUP# and SERIES# pattern
        Delete seriesPointerDelete = items.get(items.size() - 1).delete();
        assertThat(seriesPointerDelete.key().get("pk").s())
            .startsWith("GROUP#")
            .contains(series.getGroupId());
        assertThat(seriesPointerDelete.key().get("sk").s())
            .startsWith("SERIES#")
            .contains(series.getSeriesId());
    }

    @Test
    void deleteEntireSeries_WithTransactionCancellation_ShouldThrowRepositoryException() {
        // Given
        EventSeries series = createTestEventSeries();
        Hangout hangoutToUpdate = createTestHangout(UUID.randomUUID().toString());

        TransactionCanceledException canceledException = TransactionCanceledException.builder()
            .message("Transaction cancelled")
            .build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
            .thenThrow(canceledException);

        // When & Then
        assertThatThrownBy(() -> repository.deleteEntireSeries(series, hangoutToUpdate, Collections.emptyList()))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("transaction cancelled")
            .hasCause(canceledException);
    }

    @Test
    void deleteEntireSeries_WithDynamoDbError_ShouldThrowRepositoryException() {
        // Given
        EventSeries series = createTestEventSeries();
        Hangout hangoutToUpdate = createTestHangout(UUID.randomUUID().toString());

        DynamoDbException dynamoException = ProvisionedThroughputExceededException.builder()
            .message("DynamoDB error")
            .build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
            .thenThrow(dynamoException);

        // When & Then
        assertThatThrownBy(() -> repository.deleteEntireSeries(series, hangoutToUpdate, Collections.emptyList()))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("DynamoDB error")
            .hasCause(dynamoException);
    }

    // 5. removeHangoutFromSeries() Tests

    @Test
    void removeHangoutFromSeries_WithValidInputs_ShouldCreateTransactionWithCorrectItems() {
        // Given
        EventSeries series = createTestEventSeries();
        series.setHangoutIds(Arrays.asList("hangout-2")); // Already has hangout removed
        String hangout1Id = "hangout-1";
        Hangout hangoutToDelete = createTestHangout(hangout1Id);
        List<HangoutPointer> pointersToDelete = Arrays.asList(
            createTestPointer(hangout1Id, series.getGroupId()),
            createTestPointer(hangout1Id, "group-2")
        );
        List<SeriesPointer> seriesPointersToUpdate = Arrays.asList(
            createTestSeriesPointer(series.getSeriesId(), series.getGroupId())
        );

        // When
        repository.removeHangoutFromSeries(series, hangoutToDelete, pointersToDelete, seriesPointersToUpdate);

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor =
            ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());

        TransactWriteItemsRequest request = captor.getValue();
        // Should have: 1 series update + 1 hangout delete + 2 pointer deletes + 1 series pointer update = 5 items
        assertThat(request.transactItems()).hasSize(5);

        List<TransactWriteItem> items = request.transactItems();
        assertThat(items.get(0).update()).isNotNull(); // Update series
        assertThat(items.get(1).delete()).isNotNull(); // Delete hangout
        assertThat(items.get(2).delete()).isNotNull(); // Delete pointer 1
        assertThat(items.get(3).delete()).isNotNull(); // Delete pointer 2
        assertThat(items.get(4).put()).isNotNull(); // Update series pointer

        verify(performanceTracker).trackQuery(eq("removeHangoutFromSeries"), eq("InviterTable"), any());
    }

    @Test
    void removeHangoutFromSeries_ShouldUpdateSeriesWithModifiedHangoutList() {
        // Given
        EventSeries series = createTestEventSeries();
        series.setHangoutIds(Arrays.asList("hangout-2")); // Already has hangout removed
        Hangout hangoutToDelete = createTestHangout("hangout-1");

        // When
        repository.removeHangoutFromSeries(series, hangoutToDelete, Collections.emptyList(), Collections.emptyList());

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor =
            ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());

        List<TransactWriteItem> items = captor.getValue().transactItems();

        // Verify series update sets the modified hangoutIds list
        Update seriesUpdate = items.get(0).update();
        assertThat(seriesUpdate.updateExpression())
            .contains("SET hangoutIds = :hangoutIds")
            .contains("updatedAt = :updated")
            .contains("version = :version");
    }

    @Test
    void removeHangoutFromSeries_ShouldDeleteHangoutAndPointers() {
        // Given
        EventSeries series = createTestEventSeries();
        series.setHangoutIds(Arrays.asList("hangout-2"));
        String hangout1Id = "hangout-1";
        Hangout hangoutToDelete = createTestHangout(hangout1Id);
        HangoutPointer pointer1 = createTestPointer(hangout1Id, series.getGroupId());
        HangoutPointer pointer2 = createTestPointer(hangout1Id, "group-2");
        List<HangoutPointer> pointersToDelete = Arrays.asList(pointer1, pointer2);

        // When
        repository.removeHangoutFromSeries(series, hangoutToDelete, pointersToDelete, Collections.emptyList());

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor =
            ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());

        List<TransactWriteItem> items = captor.getValue().transactItems();

        // Verify hangout delete
        Delete hangoutDelete = items.get(1).delete();
        assertThat(hangoutDelete.key().get("pk").s()).isEqualTo(hangoutToDelete.getPk());
        assertThat(hangoutDelete.key().get("sk").s()).isEqualTo(hangoutToDelete.getSk());

        // Verify pointer deletes
        Delete pointer1Delete = items.get(2).delete();
        assertThat(pointer1Delete.key().get("pk").s()).isEqualTo(pointer1.getPk());
        assertThat(pointer1Delete.key().get("sk").s()).isEqualTo(pointer1.getSk());

        Delete pointer2Delete = items.get(3).delete();
        assertThat(pointer2Delete.key().get("pk").s()).isEqualTo(pointer2.getPk());
        assertThat(pointer2Delete.key().get("sk").s()).isEqualTo(pointer2.getSk());
    }

    @Test
    void removeHangoutFromSeries_WithTransactionCancellation_ShouldThrowRepositoryException() {
        // Given
        EventSeries series = createTestEventSeries();
        series.setHangoutIds(Arrays.asList("hangout-2"));
        Hangout hangoutToDelete = createTestHangout("hangout-1");

        TransactionCanceledException canceledException = TransactionCanceledException.builder()
            .message("Transaction cancelled")
            .build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
            .thenThrow(canceledException);

        // When & Then
        assertThatThrownBy(() -> repository.removeHangoutFromSeries(
            series, hangoutToDelete, Collections.emptyList(), Collections.emptyList()))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("transaction cancelled")
            .hasCause(canceledException);
    }

    @Test
    void removeHangoutFromSeries_WithDynamoDbError_ShouldThrowRepositoryException() {
        // Given
        EventSeries series = createTestEventSeries();
        series.setHangoutIds(Arrays.asList("hangout-2"));
        Hangout hangoutToDelete = createTestHangout("hangout-1");

        DynamoDbException dynamoException = ProvisionedThroughputExceededException.builder()
            .message("DynamoDB error")
            .build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
            .thenThrow(dynamoException);

        // When & Then
        assertThatThrownBy(() -> repository.removeHangoutFromSeries(
            series, hangoutToDelete, Collections.emptyList(), Collections.emptyList()))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("DynamoDB error")
            .hasCause(dynamoException);
    }

    // 6. deleteSeriesAndFinalHangout() Tests

    @Test
    void deleteSeriesAndFinalHangout_WithValidInputs_ShouldCreateTransactionWithCorrectItems() {
        // Given
        EventSeries series = createTestEventSeries();
        String hangout1Id = UUID.randomUUID().toString();
        Hangout hangoutToDelete = createTestHangout(hangout1Id);
        List<HangoutPointer> pointersToDelete = Arrays.asList(
            createTestPointer(hangout1Id, series.getGroupId()),
            createTestPointer(hangout1Id, "group-2")
        );

        // When
        repository.deleteSeriesAndFinalHangout(series, hangoutToDelete, pointersToDelete);

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor =
            ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());

        TransactWriteItemsRequest request = captor.getValue();
        // Should have: 1 series delete + 1 hangout delete + 2 pointer deletes + 1 series pointer delete = 5 items
        assertThat(request.transactItems()).hasSize(5);

        List<TransactWriteItem> items = request.transactItems();
        assertThat(items.get(0).delete()).isNotNull(); // Delete series
        assertThat(items.get(1).delete()).isNotNull(); // Delete hangout
        assertThat(items.get(2).delete()).isNotNull(); // Delete pointer 1
        assertThat(items.get(3).delete()).isNotNull(); // Delete pointer 2
        assertThat(items.get(4).delete()).isNotNull(); // Delete series pointer

        verify(performanceTracker).trackQuery(eq("deleteSeriesAndFinalHangout"), eq("InviterTable"), any());
    }

    @Test
    void deleteSeriesAndFinalHangout_ShouldDeleteAllRecordsAtomically() {
        // Given
        EventSeries series = createTestEventSeries();
        String hangout1Id = UUID.randomUUID().toString();
        Hangout hangoutToDelete = createTestHangout(hangout1Id);
        HangoutPointer pointer = createTestPointer(hangout1Id, series.getGroupId());
        List<HangoutPointer> pointersToDelete = Arrays.asList(pointer);

        // When
        repository.deleteSeriesAndFinalHangout(series, hangoutToDelete, pointersToDelete);

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor =
            ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());

        List<TransactWriteItem> items = captor.getValue().transactItems();

        // Verify series delete
        Delete seriesDelete = items.get(0).delete();
        assertThat(seriesDelete.key().get("pk").s()).isEqualTo(series.getPk());
        assertThat(seriesDelete.key().get("sk").s()).isEqualTo(series.getSk());

        // Verify hangout delete
        Delete hangoutDelete = items.get(1).delete();
        assertThat(hangoutDelete.key().get("pk").s()).isEqualTo(hangoutToDelete.getPk());
        assertThat(hangoutDelete.key().get("sk").s()).isEqualTo(hangoutToDelete.getSk());

        // Verify pointer delete
        Delete pointerDelete = items.get(2).delete();
        assertThat(pointerDelete.key().get("pk").s()).isEqualTo(pointer.getPk());
        assertThat(pointerDelete.key().get("sk").s()).isEqualTo(pointer.getSk());

        // Verify series pointer delete with constructed key
        Delete seriesPointerDelete = items.get(3).delete();
        assertThat(seriesPointerDelete.key().get("pk").s())
            .startsWith("GROUP#")
            .contains(series.getGroupId());
        assertThat(seriesPointerDelete.key().get("sk").s())
            .startsWith("SERIES#")
            .contains(series.getSeriesId());
    }

    @Test
    void deleteSeriesAndFinalHangout_WithEmptyPointerList_ShouldOnlyDeleteSeriesAndHangout() {
        // Given
        EventSeries series = createTestEventSeries();
        Hangout hangoutToDelete = createTestHangout(UUID.randomUUID().toString());

        // When
        repository.deleteSeriesAndFinalHangout(series, hangoutToDelete, Collections.emptyList());

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor =
            ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());

        TransactWriteItemsRequest request = captor.getValue();
        // Should have: 1 series delete + 1 hangout delete + 1 series pointer delete = 3 items
        assertThat(request.transactItems()).hasSize(3);
    }

    @Test
    void deleteSeriesAndFinalHangout_WithTransactionCancellation_ShouldThrowRepositoryException() {
        // Given
        EventSeries series = createTestEventSeries();
        Hangout hangoutToDelete = createTestHangout(UUID.randomUUID().toString());

        TransactionCanceledException canceledException = TransactionCanceledException.builder()
            .message("Transaction cancelled")
            .build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
            .thenThrow(canceledException);

        // When & Then
        assertThatThrownBy(() -> repository.deleteSeriesAndFinalHangout(
            series, hangoutToDelete, Collections.emptyList()))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("transaction cancelled")
            .hasCause(canceledException);
    }

    @Test
    void deleteSeriesAndFinalHangout_WithDynamoDbError_ShouldThrowRepositoryException() {
        // Given
        EventSeries series = createTestEventSeries();
        Hangout hangoutToDelete = createTestHangout(UUID.randomUUID().toString());

        DynamoDbException dynamoException = ProvisionedThroughputExceededException.builder()
            .message("DynamoDB error")
            .build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
            .thenThrow(dynamoException);

        // When & Then
        assertThatThrownBy(() -> repository.deleteSeriesAndFinalHangout(
            series, hangoutToDelete, Collections.emptyList()))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("DynamoDB error")
            .hasCause(dynamoException);
    }

    // 7. updateSeriesAfterHangoutChange() Tests

    @Test
    void updateSeriesAfterHangoutChange_WithValidInputs_ShouldCreateTransactionWithCorrectItems() {
        // Given
        EventSeries series = createTestEventSeries();
        List<SeriesPointer> seriesPointersToUpdate = Arrays.asList(
            createTestSeriesPointer(series.getSeriesId(), series.getGroupId()),
            createTestSeriesPointer(series.getSeriesId(), "group-2")
        );

        // When
        repository.updateSeriesAfterHangoutChange(series, seriesPointersToUpdate);

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor =
            ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());

        TransactWriteItemsRequest request = captor.getValue();
        // Should have: 1 series update + 2 series pointer updates = 3 items
        assertThat(request.transactItems()).hasSize(3);

        List<TransactWriteItem> items = request.transactItems();
        assertThat(items.get(0).put()).isNotNull(); // Update series (PUT)
        assertThat(items.get(1).put()).isNotNull(); // Update series pointer 1 (PUT)
        assertThat(items.get(2).put()).isNotNull(); // Update series pointer 2 (PUT)

        verify(performanceTracker).trackQuery(eq("updateSeriesAfterHangoutChange"), eq("InviterTable"), any());
    }

    @Test
    void updateSeriesAfterHangoutChange_ShouldUsePutOperationsForUpdates() {
        // Given
        EventSeries series = createTestEventSeries();
        SeriesPointer seriesPointer = createTestSeriesPointer(series.getSeriesId(), series.getGroupId());
        List<SeriesPointer> seriesPointersToUpdate = Arrays.asList(seriesPointer);

        // When
        repository.updateSeriesAfterHangoutChange(series, seriesPointersToUpdate);

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor =
            ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());

        List<TransactWriteItem> items = captor.getValue().transactItems();

        // Verify series is updated with PUT (complete replacement)
        assertThat(items.get(0).put()).isNotNull();
        assertThat(items.get(0).put().item()).isNotEmpty();

        // Verify series pointer is updated with PUT (complete replacement)
        assertThat(items.get(1).put()).isNotNull();
        assertThat(items.get(1).put().item()).isNotEmpty();
    }

    @Test
    void updateSeriesAfterHangoutChange_WithEmptyPointerList_ShouldOnlyUpdateSeries() {
        // Given
        EventSeries series = createTestEventSeries();

        // When
        repository.updateSeriesAfterHangoutChange(series, Collections.emptyList());

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor =
            ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());

        TransactWriteItemsRequest request = captor.getValue();
        // Should have only 1 series update
        assertThat(request.transactItems()).hasSize(1);

        TransactWriteItem item = request.transactItems().get(0);
        assertThat(item.put()).isNotNull();
    }

    @Test
    void updateSeriesAfterHangoutChange_WithTransactionCancellation_ShouldThrowRepositoryException() {
        // Given
        EventSeries series = createTestEventSeries();

        TransactionCanceledException canceledException = TransactionCanceledException.builder()
            .message("Transaction cancelled")
            .build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
            .thenThrow(canceledException);

        // When & Then
        assertThatThrownBy(() -> repository.updateSeriesAfterHangoutChange(series, Collections.emptyList()))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("transaction cancelled")
            .hasCause(canceledException);
    }

    @Test
    void updateSeriesAfterHangoutChange_WithDynamoDbError_ShouldThrowRepositoryException() {
        // Given
        EventSeries series = createTestEventSeries();

        DynamoDbException dynamoException = ProvisionedThroughputExceededException.builder()
            .message("DynamoDB error")
            .build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
            .thenThrow(dynamoException);

        // When & Then
        assertThatThrownBy(() -> repository.updateSeriesAfterHangoutChange(series, Collections.emptyList()))
            .isInstanceOf(RepositoryException.class)
            .hasMessageContaining("DynamoDB error")
            .hasCause(dynamoException);
    }
}