package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.model.Participation;
import com.bbthechange.inviter.model.ParticipationType;
import com.bbthechange.inviter.util.InviterKeyFactory;
import com.bbthechange.inviter.util.QueryPerformanceTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ParticipationRepositoryImpl.
 * Tests verify correct DynamoDB key construction, query patterns, and result handling.
 */
@ExtendWith(MockitoExtension.class)
class ParticipationRepositoryImplTest {

    private static final String TABLE_NAME = "InviterTable";

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private DynamoDbEnhancedClient dynamoDbEnhancedClient;

    @Mock
    private DynamoDbTable<Participation> participationTable;

    @Mock
    private QueryPerformanceTracker performanceTracker;

    private ParticipationRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        // Mock performance tracker to pass through calls
        lenient().when(performanceTracker.trackQuery(anyString(), anyString(), any())).thenAnswer(invocation -> {
            try {
                java.util.function.Supplier<?> supplier = invocation.getArgument(2);
                return supplier.get();
            } catch (Exception e) {
                throw e;
            }
        });

        // Mock the Enhanced Client to return our mocked table
        when(dynamoDbEnhancedClient.table(eq(TABLE_NAME), any(TableSchema.class))).thenReturn(participationTable);

        repository = new ParticipationRepositoryImpl(dynamoDbClient, dynamoDbEnhancedClient, performanceTracker);
    }

    @Test
    void save_ValidParticipation_CallsPutItemWithUpdatedTimestamp() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String participationId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        Participation participation = new Participation(hangoutId, participationId, userId, ParticipationType.TICKET_NEEDED);

        // When
        Participation result = repository.save(participation);

        // Then
        assertThat(result).isSameAs(participation);
        assertThat(result.getParticipationId()).isEqualTo(participationId);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getType()).isEqualTo(ParticipationType.TICKET_NEEDED);
        assertThat(result.getUpdatedAt()).isNotNull();

        // Verify putItem was called
        ArgumentCaptor<Participation> captor = ArgumentCaptor.forClass(Participation.class);
        verify(participationTable).putItem(captor.capture());
        assertThat(captor.getValue()).isSameAs(participation);
    }

    @Test
    void findById_ExistingParticipation_ReturnsOptionalWithParticipation() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String participationId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        Participation expectedParticipation = new Participation(hangoutId, participationId, userId, ParticipationType.TICKET_NEEDED);
        when(participationTable.getItem(any(Consumer.class))).thenReturn(expectedParticipation);

        // When
        Optional<Participation> result = repository.findById(hangoutId, participationId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getParticipationId()).isEqualTo(participationId);

        // Verify correct key construction
        ArgumentCaptor<Consumer<GetItemEnhancedRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(participationTable).getItem(captor.capture());

        GetItemEnhancedRequest.Builder builder = GetItemEnhancedRequest.builder();
        captor.getValue().accept(builder);
        Key key = builder.build().key();

        assertThat(key.partitionKeyValue().s()).isEqualTo("EVENT#" + hangoutId);
        assertThat(key.sortKeyValue().get().s()).isEqualTo("PARTICIPATION#" + participationId);
    }

    @Test
    void findById_NonExistentParticipation_ReturnsEmptyOptional() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String participationId = UUID.randomUUID().toString();

        when(participationTable.getItem(any(Consumer.class))).thenReturn(null);

        // When
        Optional<Participation> result = repository.findById(hangoutId, participationId);

        // Then
        assertThat(result).isEmpty();
        verify(participationTable).getItem(any(Consumer.class));
    }

    @Test
    void findByHangoutId_MultipleParticipations_ReturnsAllMatching() {
        // Given
        String hangoutId = UUID.randomUUID().toString();

        Participation p1 = new Participation(hangoutId, UUID.randomUUID().toString(), UUID.randomUUID().toString(), ParticipationType.TICKET_NEEDED);
        Participation p2 = new Participation(hangoutId, UUID.randomUUID().toString(), UUID.randomUUID().toString(), ParticipationType.TICKET_PURCHASED);
        Participation p3 = new Participation(hangoutId, UUID.randomUUID().toString(), UUID.randomUUID().toString(), ParticipationType.SECTION);

        List<Participation> participations = Arrays.asList(p1, p2, p3);

        PageIterable<Participation> mockPages = mock(PageIterable.class);
        when(mockPages.items()).thenReturn(() -> participations.iterator());
        when(participationTable.query(any(QueryEnhancedRequest.class))).thenReturn(mockPages);

        // When
        List<Participation> result = repository.findByHangoutId(hangoutId);

        // Then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getType()).isEqualTo(ParticipationType.TICKET_NEEDED);
        assertThat(result.get(1).getType()).isEqualTo(ParticipationType.TICKET_PURCHASED);
        assertThat(result.get(2).getType()).isEqualTo(ParticipationType.SECTION);

        // Verify query was called with correct conditional
        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(participationTable).query(captor.capture());

        QueryEnhancedRequest request = captor.getValue();
        assertThat(request.queryConditional()).isNotNull();
        assertThat(request.scanIndexForward()).isTrue();
    }

    @Test
    void findByHangoutId_NoParticipations_ReturnsEmptyList() {
        // Given
        String hangoutId = UUID.randomUUID().toString();

        PageIterable<Participation> mockPages = mock(PageIterable.class);
        when(mockPages.items()).thenReturn(Collections::emptyIterator);
        when(participationTable.query(any(QueryEnhancedRequest.class))).thenReturn(mockPages);

        // When
        List<Participation> result = repository.findByHangoutId(hangoutId);

        // Then
        assertThat(result).isEmpty();
        assertThat(result).hasSize(0);
        verify(participationTable).query(any(QueryEnhancedRequest.class));
    }

    @Test
    void findByOfferId_MultipleMatches_ReturnsFilteredList() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String offerId = UUID.randomUUID().toString();

        String part1Id = UUID.randomUUID().toString();
        String part2Id = UUID.randomUUID().toString();
        String part3Id = UUID.randomUUID().toString();
        String part4Id = UUID.randomUUID().toString();

        Participation p1 = new Participation(hangoutId, part1Id, UUID.randomUUID().toString(), ParticipationType.TICKET_PURCHASED);
        p1.setReservationOfferId(offerId);

        Participation p2 = new Participation(hangoutId, part2Id, UUID.randomUUID().toString(), ParticipationType.TICKET_PURCHASED);
        p2.setReservationOfferId(offerId);

        Participation p3 = new Participation(hangoutId, part3Id, UUID.randomUUID().toString(), ParticipationType.TICKET_PURCHASED);
        p3.setReservationOfferId(UUID.randomUUID().toString());

        Participation p4 = new Participation(hangoutId, part4Id, UUID.randomUUID().toString(), ParticipationType.TICKET_NEEDED);
        // p4 has null reservationOfferId

        List<Participation> allParticipations = Arrays.asList(p1, p2, p3, p4);

        PageIterable<Participation> mockPages = mock(PageIterable.class);
        when(mockPages.items()).thenReturn(() -> allParticipations.iterator());
        when(participationTable.query(any(QueryEnhancedRequest.class))).thenReturn(mockPages);

        // When
        List<Participation> result = repository.findByOfferId(hangoutId, offerId);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(p -> offerId.equals(p.getReservationOfferId()));
        assertThat(result).extracting(Participation::getParticipationId).containsExactlyInAnyOrder(part1Id, part2Id);
    }

    @Test
    void findByOfferId_NoMatches_ReturnsEmptyList() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String searchOfferId = UUID.randomUUID().toString();

        Participation p1 = new Participation(hangoutId, UUID.randomUUID().toString(), UUID.randomUUID().toString(), ParticipationType.TICKET_PURCHASED);
        p1.setReservationOfferId(UUID.randomUUID().toString());

        Participation p2 = new Participation(hangoutId, UUID.randomUUID().toString(), UUID.randomUUID().toString(), ParticipationType.TICKET_PURCHASED);
        p2.setReservationOfferId(UUID.randomUUID().toString());

        List<Participation> allParticipations = Arrays.asList(p1, p2);

        PageIterable<Participation> mockPages = mock(PageIterable.class);
        when(mockPages.items()).thenReturn(() -> allParticipations.iterator());
        when(participationTable.query(any(QueryEnhancedRequest.class))).thenReturn(mockPages);

        // When
        List<Participation> result = repository.findByOfferId(hangoutId, searchOfferId);

        // Then
        assertThat(result).isEmpty();
        assertThat(result).hasSize(0);
    }

    @Test
    void findByOfferId_NullOfferIdInData_HandlesCorrectly() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String searchOfferId = UUID.randomUUID().toString();

        String part1Id = UUID.randomUUID().toString();
        String part2Id = UUID.randomUUID().toString();
        String part3Id = UUID.randomUUID().toString();

        Participation p1 = new Participation(hangoutId, part1Id, UUID.randomUUID().toString(), ParticipationType.TICKET_PURCHASED);
        p1.setReservationOfferId(searchOfferId);

        Participation p2 = new Participation(hangoutId, part2Id, UUID.randomUUID().toString(), ParticipationType.TICKET_NEEDED);
        // p2 has null reservationOfferId

        Participation p3 = new Participation(hangoutId, part3Id, UUID.randomUUID().toString(), ParticipationType.TICKET_PURCHASED);
        p3.setReservationOfferId(searchOfferId);

        List<Participation> allParticipations = Arrays.asList(p1, p2, p3);

        PageIterable<Participation> mockPages = mock(PageIterable.class);
        when(mockPages.items()).thenReturn(() -> allParticipations.iterator());
        when(participationTable.query(any(QueryEnhancedRequest.class))).thenReturn(mockPages);

        // When
        List<Participation> result = repository.findByOfferId(hangoutId, searchOfferId);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(p -> searchOfferId.equals(p.getReservationOfferId()));
        assertThat(result).extracting(Participation::getParticipationId).containsExactlyInAnyOrder(part1Id, part3Id);
        // Verify null comparison doesn't cause NullPointerException
        assertThat(result).noneMatch(p -> p.getReservationOfferId() == null);
    }

    @Test
    void delete_ValidIds_CallsDeleteItemWithCorrectKey() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String participationId = UUID.randomUUID().toString();

        // When
        repository.delete(hangoutId, participationId);

        // Then
        ArgumentCaptor<Consumer<DeleteItemEnhancedRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(participationTable).deleteItem(captor.capture());

        DeleteItemEnhancedRequest.Builder builder = DeleteItemEnhancedRequest.builder();
        captor.getValue().accept(builder);
        Key key = builder.build().key();

        assertThat(key.partitionKeyValue().s()).isEqualTo("EVENT#" + hangoutId);
        assertThat(key.sortKeyValue().get().s()).isEqualTo("PARTICIPATION#" + participationId);
    }

    @Test
    void delete_NonExistentParticipation_DoesNotThrowException() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String participationId = UUID.randomUUID().toString();

        // When/Then
        assertThatCode(() -> repository.delete(hangoutId, participationId))
                .doesNotThrowAnyException();

        verify(participationTable).deleteItem(any(Consumer.class));
    }

    // Exception Tests

    @Test
    void save_WhenDynamoDbThrowsException_ThrowsRepositoryException() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String participationId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        Participation participation = new Participation(hangoutId, participationId, userId, ParticipationType.TICKET_NEEDED);

        doThrow(new RuntimeException("DynamoDB error"))
                .when(participationTable).putItem(any(Participation.class));

        // When/Then
        assertThatThrownBy(() -> repository.save(participation))
                .isInstanceOf(com.bbthechange.inviter.exception.RepositoryException.class)
                .hasMessageContaining("Failed to save participation")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void findById_WhenDynamoDbThrowsException_ThrowsRepositoryException() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String participationId = UUID.randomUUID().toString();

        when(participationTable.getItem(any(Consumer.class)))
                .thenThrow(new RuntimeException("DynamoDB error"));

        // When/Then
        assertThatThrownBy(() -> repository.findById(hangoutId, participationId))
                .isInstanceOf(com.bbthechange.inviter.exception.RepositoryException.class)
                .hasMessageContaining("Failed to retrieve participation")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void findByHangoutId_WhenDynamoDbThrowsException_ThrowsRepositoryException() {
        // Given
        String hangoutId = UUID.randomUUID().toString();

        when(participationTable.query(any(QueryEnhancedRequest.class)))
                .thenThrow(new RuntimeException("DynamoDB error"));

        // When/Then
        assertThatThrownBy(() -> repository.findByHangoutId(hangoutId))
                .isInstanceOf(com.bbthechange.inviter.exception.RepositoryException.class)
                .hasMessageContaining("Failed to retrieve participations")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void findByOfferId_WhenDynamoDbThrowsException_ThrowsRepositoryException() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String offerId = UUID.randomUUID().toString();

        when(participationTable.query(any(QueryEnhancedRequest.class)))
                .thenThrow(new RuntimeException("DynamoDB error"));

        // When/Then
        assertThatThrownBy(() -> repository.findByOfferId(hangoutId, offerId))
                .isInstanceOf(com.bbthechange.inviter.exception.RepositoryException.class)
                .hasMessageContaining("Failed to retrieve participations by offer")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void delete_WhenDynamoDbThrowsException_ThrowsRepositoryException() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String participationId = UUID.randomUUID().toString();

        doThrow(new RuntimeException("DynamoDB error"))
                .when(participationTable).deleteItem(any(Consumer.class));

        // When/Then
        assertThatThrownBy(() -> repository.delete(hangoutId, participationId))
                .isInstanceOf(com.bbthechange.inviter.exception.RepositoryException.class)
                .hasMessageContaining("Failed to delete participation")
                .hasCauseInstanceOf(RuntimeException.class);
    }
}
