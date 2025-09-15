package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.model.EventSeries;
import com.bbthechange.inviter.model.Hangout;
import com.bbthechange.inviter.model.HangoutPointer;
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
    void createSeriesWithNewPart_WithValidInputs_ShouldCreateTransactionWith5Items() {
        // Given
        EventSeries series = createTestEventSeries();
        String hangout1Id = UUID.randomUUID().toString();
        String hangout2Id = UUID.randomUUID().toString();
        Hangout existingHangout = createTestHangout(hangout1Id);
        HangoutPointer existingPointer = createTestPointer(hangout1Id, series.getGroupId());
        Hangout newHangout = createTestHangout(hangout2Id);
        HangoutPointer newPointer = createTestPointer(hangout2Id, series.getGroupId());

        // When
        repository.createSeriesWithNewPart(
            series, existingHangout, existingPointer, 
            newHangout, newPointer
        );

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor = 
            ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());
        
        TransactWriteItemsRequest request = captor.getValue();
        assertThat(request.transactItems()).hasSize(5);
        
        // Verify operation types in correct order
        List<TransactWriteItem> items = request.transactItems();
        assertThat(items.get(0).put()).isNotNull(); // Create series
        assertThat(items.get(1).update()).isNotNull(); // Update existing hangout
        assertThat(items.get(2).update()).isNotNull(); // Update existing pointer
        assertThat(items.get(3).put()).isNotNull(); // Create new hangout
        assertThat(items.get(4).put()).isNotNull(); // Create new pointer
        
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
        HangoutPointer existingPointer = createTestPointer(hangout1Id, series.getGroupId());
        Hangout newHangout = createTestHangout(hangout2Id);
        HangoutPointer newPointer = createTestPointer(hangout2Id, series.getGroupId());
        
        TransactionCanceledException canceledException = TransactionCanceledException.builder()
            .message("Transaction cancelled")
            .build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
            .thenThrow(canceledException);

        // When & Then
        assertThatThrownBy(() -> repository.createSeriesWithNewPart(
            series, existingHangout, existingPointer, newHangout, newPointer))
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
        HangoutPointer existingPointer = createTestPointer(hangout1Id, series.getGroupId());
        Hangout newHangout = createTestHangout(hangout2Id);
        HangoutPointer newPointer = createTestPointer(hangout2Id, series.getGroupId());
        
        DynamoDbException dynamoException = ProvisionedThroughputExceededException.builder()
            .message("DynamoDB error")
            .build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
            .thenThrow(dynamoException);

        // When & Then
        assertThatThrownBy(() -> repository.createSeriesWithNewPart(
            series, existingHangout, existingPointer, newHangout, newPointer))
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
        HangoutPointer existingPointer = createTestPointer(hangout1Id, series.getGroupId());
        Hangout newHangout = createTestHangout(hangout2Id);
        HangoutPointer newPointer = createTestPointer(hangout2Id, series.getGroupId());

        // When
        repository.createSeriesWithNewPart(
            series, existingHangout, existingPointer, 
            newHangout, newPointer
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
        Hangout newHangout = createTestHangout(hangout2Id);
        HangoutPointer newPointer = createTestPointer(hangout2Id, series.getGroupId());

        // When
        repository.createSeriesWithNewPart(
            series, existingHangout, existingPointer, 
            newHangout, newPointer
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
    void addPartToExistingSeries_WithValidInputs_ShouldCreateTransactionWith3Items() {
        // Given
        String seriesId = UUID.randomUUID().toString();
        String hangout3Id = UUID.randomUUID().toString();
        String groupId = UUID.randomUUID().toString();
        Hangout newHangout = createTestHangout(hangout3Id);
        HangoutPointer newPointer = createTestPointer(hangout3Id, groupId);

        // When
        repository.addPartToExistingSeries(seriesId, newHangout, newPointer);

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor = 
            ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());
        
        TransactWriteItemsRequest request = captor.getValue();
        assertThat(request.transactItems()).hasSize(3);
        
        // Verify operation types in correct order
        List<TransactWriteItem> items = request.transactItems();
        assertThat(items.get(0).update()).isNotNull(); // Update series
        assertThat(items.get(1).put()).isNotNull(); // Create new hangout
        assertThat(items.get(2).put()).isNotNull(); // Create new pointer
        
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
        HangoutPointer newPointer = createTestPointer(hangout3Id, groupId);

        // When
        repository.addPartToExistingSeries(seriesId, newHangout, newPointer);

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
        HangoutPointer newPointer = createTestPointer(hangout3Id, groupId);
        
        TransactionCanceledException canceledException = TransactionCanceledException.builder()
            .message("Transaction cancelled")
            .build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
            .thenThrow(canceledException);

        // When & Then
        assertThatThrownBy(() -> repository.addPartToExistingSeries(seriesId, newHangout, newPointer))
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
        HangoutPointer newPointer = createTestPointer(hangout3Id, groupId);
        
        DynamoDbException dynamoException = ProvisionedThroughputExceededException.builder()
            .message("DynamoDB error")
            .build();
        when(dynamoDbClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
            .thenThrow(dynamoException);

        // When & Then
        assertThatThrownBy(() -> repository.addPartToExistingSeries(seriesId, newHangout, newPointer))
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
        HangoutPointer existingPointer = createTestPointer(hangout1Id, series.getGroupId());
        Hangout newHangout = createTestHangout(hangout2Id);
        HangoutPointer newPointer = createTestPointer(hangout2Id, series.getGroupId());

        // When
        repository.createSeriesWithNewPart(
            series, existingHangout, existingPointer, 
            newHangout, newPointer
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
        HangoutPointer newPointer = createTestPointer(hangout3Id, groupId);

        // When
        repository.addPartToExistingSeries(seriesId, newHangout, newPointer);

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
        HangoutPointer existingPointer = createTestPointer(hangout1Id, series.getGroupId());
        Hangout newHangout = createTestHangout(hangout2Id);
        HangoutPointer newPointer = createTestPointer(hangout2Id, series.getGroupId());
        
        RuntimeException trackingException = new RuntimeException("Tracking failed");
        // Reset the mock to override the default behavior
        reset(performanceTracker);
        when(performanceTracker.trackQuery(anyString(), anyString(), any()))
            .thenThrow(trackingException);

        // When & Then
        assertThatThrownBy(() -> repository.createSeriesWithNewPart(
            series, existingHangout, existingPointer, newHangout, newPointer))
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
        HangoutPointer existingPointer = createTestPointer(hangout1Id, series.getGroupId());
        Hangout newHangout = createTestHangout(hangout2Id);
        HangoutPointer newPointer = createTestPointer(hangout2Id, series.getGroupId());

        // When
        repository.createSeriesWithNewPart(
            series, existingHangout, existingPointer, 
            newHangout, newPointer
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
        HangoutPointer newPointer = createTestPointer(hangout3Id, groupId);

        // When
        repository.addPartToExistingSeries(seriesId, newHangout, newPointer);

        // Then
        ArgumentCaptor<TransactWriteItemsRequest> captor = 
            ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(dynamoDbClient).transactWriteItems(captor.capture());
        
        List<TransactWriteItem> items = captor.getValue().transactItems();
        
        // Verify PUT operations have serialized item data
        assertThat(items.get(1).put().item()).isNotEmpty(); // New hangout creation
        assertThat(items.get(2).put().item()).isNotEmpty(); // New pointer creation
    }
}