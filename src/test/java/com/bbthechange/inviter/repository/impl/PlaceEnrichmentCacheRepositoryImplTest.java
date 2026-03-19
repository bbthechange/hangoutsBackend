package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.model.PlaceEnrichmentCacheEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaceEnrichmentCacheRepositoryImplTest {

    @Mock
    private DynamoDbEnhancedClient enhancedClient;

    @Mock
    private DynamoDbTable<PlaceEnrichmentCacheEntry> table;

    @Mock
    private DynamoDbIndex<PlaceEnrichmentCacheEntry> googlePlaceIdIndex;

    private PlaceEnrichmentCacheRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        when(enhancedClient.table(eq("PlaceEnrichmentCache"), any(TableSchema.class))).thenReturn(table);
        when(table.index("GooglePlaceIdIndex")).thenReturn(googlePlaceIdIndex);
        repository = new PlaceEnrichmentCacheRepositoryImpl(enhancedClient);
    }

    // ===== findByCacheKey =====

    @Test
    void findByCacheKey_EntryExists_ReturnsEntry() {
        PlaceEnrichmentCacheEntry entry = createEntry("sushi-nakazawa_40.7295_-74.0028", "ChIJ123");
        when(table.getItem(any(Key.class))).thenReturn(entry);

        Optional<PlaceEnrichmentCacheEntry> result = repository.findByCacheKey("sushi-nakazawa_40.7295_-74.0028");

        assertThat(result).isPresent();
        assertThat(result.get().getCacheKey()).isEqualTo("sushi-nakazawa_40.7295_-74.0028");
    }

    @Test
    void findByCacheKey_NoEntry_ReturnsEmpty() {
        when(table.getItem(any(Key.class))).thenReturn(null);

        Optional<PlaceEnrichmentCacheEntry> result = repository.findByCacheKey("nonexistent_0.0000_0.0000");

        assertThat(result).isEmpty();
    }

    @Test
    void findByCacheKey_UsesCorrectKey() {
        when(table.getItem(any(Key.class))).thenReturn(null);

        repository.findByCacheKey("cafe_37.7749_-122.4194");

        ArgumentCaptor<Key> keyCaptor = ArgumentCaptor.forClass(Key.class);
        verify(table).getItem(keyCaptor.capture());
        assertThat(keyCaptor.getValue().partitionKeyValue().s()).isEqualTo("cafe_37.7749_-122.4194");
    }

    // ===== findByGooglePlaceId =====

    @Test
    void findByGooglePlaceId_EntryExists_ReturnsEntry() {
        PlaceEnrichmentCacheEntry entry = createEntry("sushi-nakazawa_40.7295_-74.0028", "ChIJ123");
        mockGsiResults(List.of(entry));

        Optional<PlaceEnrichmentCacheEntry> result = repository.findByGooglePlaceId("ChIJ123");

        assertThat(result).isPresent();
        assertThat(result.get().getGooglePlaceId()).isEqualTo("ChIJ123");
    }

    @Test
    void findByGooglePlaceId_NoEntry_ReturnsEmpty() {
        mockGsiResults(List.of());

        Optional<PlaceEnrichmentCacheEntry> result = repository.findByGooglePlaceId("ChIJNotFound");

        assertThat(result).isEmpty();
    }

    @Test
    void findByGooglePlaceId_QueriesGsiWithLimit1() {
        mockGsiResults(List.of());

        repository.findByGooglePlaceId("ChIJ123");

        ArgumentCaptor<QueryEnhancedRequest> requestCaptor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(googlePlaceIdIndex).query(requestCaptor.capture());
        assertThat(requestCaptor.getValue().limit()).isEqualTo(1);
    }

    @Test
    void findByGooglePlaceId_QueriesCorrectGsiKeyValue() {
        // Verify GSI index is queried (not table.getItem) and the entry with that googlePlaceId is returned
        String googlePlaceId = "ChIJN1t_tDeuEmsRUsoyG83frY4";
        PlaceEnrichmentCacheEntry entry = createEntry("sushi-nakazawa_40.7295_-74.0028", googlePlaceId);
        mockGsiResults(List.of(entry));

        Optional<PlaceEnrichmentCacheEntry> result = repository.findByGooglePlaceId(googlePlaceId);

        // GSI was queried, not the PK table.getItem
        verify(googlePlaceIdIndex).query(any(QueryEnhancedRequest.class));
        verify(table, never()).getItem(any(Key.class));
        // Correct entry returned
        assertThat(result).isPresent();
        assertThat(result.get().getGooglePlaceId()).isEqualTo(googlePlaceId);
    }

    @Test
    void findByGooglePlaceId_MultipleResultsInGsi_ReturnsFirst() {
        // DynamoDB GSIs can have duplicate partition key values — findByGooglePlaceId returns first
        PlaceEnrichmentCacheEntry first = createEntry("place-a_40.7000_-74.0000", "ChIJDuplicate");
        PlaceEnrichmentCacheEntry second = createEntry("place-b_40.8000_-74.1000", "ChIJDuplicate");
        mockGsiResults(List.of(first, second));

        Optional<PlaceEnrichmentCacheEntry> result = repository.findByGooglePlaceId("ChIJDuplicate");

        assertThat(result).isPresent();
        assertThat(result.get().getCacheKey()).isEqualTo("place-a_40.7000_-74.0000");
    }

    // ===== save =====

    @Test
    void save_CallsPutItem() {
        PlaceEnrichmentCacheEntry entry = createEntry("cafe_37.7749_-122.4194", null);

        repository.save(entry);

        verify(table).putItem(entry);
    }

    @Test
    void save_EnrichedEntry_PersistedCorrectly() {
        PlaceEnrichmentCacheEntry entry = createEntry("starbucks_47.6062_-122.3321", "ChIJXYZ");
        entry.setStatus("ENRICHED");
        entry.setCachedRating(4.2);

        repository.save(entry);

        ArgumentCaptor<PlaceEnrichmentCacheEntry> captor = ArgumentCaptor.forClass(PlaceEnrichmentCacheEntry.class);
        verify(table).putItem(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("ENRICHED");
        assertThat(captor.getValue().getCachedRating()).isEqualTo(4.2);
    }

    // ===== helpers =====

    private PlaceEnrichmentCacheEntry createEntry(String cacheKey, String googlePlaceId) {
        PlaceEnrichmentCacheEntry entry = new PlaceEnrichmentCacheEntry();
        entry.setCacheKey(cacheKey);
        entry.setGooglePlaceId(googlePlaceId);
        entry.setStatus("ENRICHED");
        return entry;
    }

    @SuppressWarnings("unchecked")
    private void mockGsiResults(List<PlaceEnrichmentCacheEntry> entries) {
        Page<PlaceEnrichmentCacheEntry> page = mock(Page.class);
        when(page.items()).thenReturn(entries);

        PageIterable<PlaceEnrichmentCacheEntry> pageIterable = mock(PageIterable.class);
        when(pageIterable.stream()).thenReturn(java.util.stream.Stream.of(page));

        when(googlePlaceIdIndex.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);
    }
}
