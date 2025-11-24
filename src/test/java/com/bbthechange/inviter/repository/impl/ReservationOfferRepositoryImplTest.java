package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.model.OfferType;
import com.bbthechange.inviter.model.ReservationOffer;
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
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReservationOfferRepositoryImpl.
 * Tests verify correct DynamoDB key construction, query patterns, and result handling.
 */
@ExtendWith(MockitoExtension.class)
class ReservationOfferRepositoryImplTest {

    private static final String TABLE_NAME = "InviterTable";

    @Mock
    private DynamoDbClient dynamoDbClient;

    @Mock
    private DynamoDbEnhancedClient dynamoDbEnhancedClient;

    @Mock
    private DynamoDbTable<ReservationOffer> offerTable;

    @Mock
    private QueryPerformanceTracker performanceTracker;

    private ReservationOfferRepositoryImpl repository;

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
        when(dynamoDbEnhancedClient.table(eq(TABLE_NAME), any(TableSchema.class))).thenReturn(offerTable);

        repository = new ReservationOfferRepositoryImpl(dynamoDbClient, dynamoDbEnhancedClient, performanceTracker);
    }

    @Test
    void save_ValidOffer_UpdatesTimestampAndSaves() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String offerId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        ReservationOffer offer = new ReservationOffer(hangoutId, offerId, userId, OfferType.TICKET);

        // When
        ReservationOffer result = repository.save(offer);

        // Then
        assertThat(result).isSameAs(offer);
        assertThat(result.getOfferId()).isEqualTo(offerId);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getType()).isEqualTo(OfferType.TICKET);
        assertThat(result.getUpdatedAt()).isNotNull();

        // Verify putItem was called
        ArgumentCaptor<ReservationOffer> captor = ArgumentCaptor.forClass(ReservationOffer.class);
        verify(offerTable).putItem(captor.capture());
        assertThat(captor.getValue()).isSameAs(offer);
    }

    @Test
    void findById_ExistingOffer_ReturnsPopulatedOptional() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String offerId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        ReservationOffer expectedOffer = new ReservationOffer(hangoutId, offerId, userId, OfferType.RESERVATION);
        when(offerTable.getItem(any(Key.class))).thenReturn(expectedOffer);

        // When
        Optional<ReservationOffer> result = repository.findById(hangoutId, offerId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getOfferId()).isEqualTo(offerId);

        // Verify correct key construction
        ArgumentCaptor<Key> captor = ArgumentCaptor.forClass(Key.class);
        verify(offerTable).getItem(captor.capture());

        Key key = captor.getValue();
        assertThat(key.partitionKeyValue().s()).isEqualTo(InviterKeyFactory.getEventPk(hangoutId));
        assertThat(key.sortKeyValue().get().s()).isEqualTo(InviterKeyFactory.getReservationOfferSk(offerId));
    }

    @Test
    void findById_NonExistentOffer_ReturnsEmptyOptional() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String offerId = UUID.randomUUID().toString();

        when(offerTable.getItem(any(Key.class))).thenReturn(null);

        // When
        Optional<ReservationOffer> result = repository.findById(hangoutId, offerId);

        // Then
        assertThat(result).isEmpty();
        verify(offerTable).getItem(any(Key.class));
    }

    @Test
    void findByHangoutId_MultipleOffers_ReturnsAllForHangout() {
        // Given
        String hangoutId = UUID.randomUUID().toString();

        ReservationOffer offer1 = new ReservationOffer(hangoutId, UUID.randomUUID().toString(), UUID.randomUUID().toString(), OfferType.TICKET);
        ReservationOffer offer2 = new ReservationOffer(hangoutId, UUID.randomUUID().toString(), UUID.randomUUID().toString(), OfferType.RESERVATION);
        ReservationOffer offer3 = new ReservationOffer(hangoutId, UUID.randomUUID().toString(), UUID.randomUUID().toString(), OfferType.TICKET);

        List<ReservationOffer> offers = Arrays.asList(offer1, offer2, offer3);

        PageIterable<ReservationOffer> mockPages = mock(PageIterable.class);
        when(mockPages.items()).thenReturn(() -> offers.iterator());
        when(offerTable.query(any(QueryEnhancedRequest.class))).thenReturn(mockPages);

        // When
        List<ReservationOffer> result = repository.findByHangoutId(hangoutId);

        // Then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getType()).isEqualTo(OfferType.TICKET);
        assertThat(result.get(1).getType()).isEqualTo(OfferType.RESERVATION);
        assertThat(result.get(2).getType()).isEqualTo(OfferType.TICKET);

        // Verify query was called with correct conditional
        ArgumentCaptor<QueryEnhancedRequest> captor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(offerTable).query(captor.capture());

        QueryEnhancedRequest request = captor.getValue();
        assertThat(request.queryConditional()).isNotNull();
    }

    @Test
    void findByHangoutId_NoOffers_ReturnsEmptyList() {
        // Given
        String hangoutId = UUID.randomUUID().toString();

        PageIterable<ReservationOffer> mockPages = mock(PageIterable.class);
        when(mockPages.items()).thenReturn(Collections::emptyIterator);
        when(offerTable.query(any(QueryEnhancedRequest.class))).thenReturn(mockPages);

        // When
        List<ReservationOffer> result = repository.findByHangoutId(hangoutId);

        // Then
        assertThat(result).isEmpty();
        assertThat(result).hasSize(0);
        verify(offerTable).query(any(QueryEnhancedRequest.class));
    }

    @Test
    void delete_ExistingOffer_DeletesSuccessfully() {
        // Given
        String hangoutId = UUID.randomUUID().toString();
        String offerId = UUID.randomUUID().toString();

        // When
        repository.delete(hangoutId, offerId);

        // Then
        ArgumentCaptor<Key> captor = ArgumentCaptor.forClass(Key.class);
        verify(offerTable).deleteItem(captor.capture());

        Key key = captor.getValue();
        assertThat(key.partitionKeyValue().s()).isEqualTo(InviterKeyFactory.getEventPk(hangoutId));
        assertThat(key.sortKeyValue().get().s()).isEqualTo(InviterKeyFactory.getReservationOfferSk(offerId));
    }
}
