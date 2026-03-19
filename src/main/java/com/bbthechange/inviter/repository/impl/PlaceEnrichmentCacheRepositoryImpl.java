package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.model.PlaceEnrichmentCacheEntry;
import com.bbthechange.inviter.repository.PlaceEnrichmentCacheRepository;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.Optional;

@Repository
public class PlaceEnrichmentCacheRepositoryImpl implements PlaceEnrichmentCacheRepository {

    private final DynamoDbTable<PlaceEnrichmentCacheEntry> table;
    private final DynamoDbIndex<PlaceEnrichmentCacheEntry> googlePlaceIdIndex;

    public PlaceEnrichmentCacheRepositoryImpl(DynamoDbEnhancedClient enhancedClient) {
        this.table = enhancedClient.table("PlaceEnrichmentCache",
            TableSchema.fromBean(PlaceEnrichmentCacheEntry.class));
        this.googlePlaceIdIndex = table.index("GooglePlaceIdIndex");
    }

    @Override
    public Optional<PlaceEnrichmentCacheEntry> findByCacheKey(String cacheKey) {
        return Optional.ofNullable(table.getItem(Key.builder().partitionValue(cacheKey).build()));
    }

    @Override
    public Optional<PlaceEnrichmentCacheEntry> findByGooglePlaceId(String googlePlaceId) {
        QueryConditional queryConditional = QueryConditional.keyEqualTo(
            Key.builder().partitionValue(googlePlaceId).build());

        return googlePlaceIdIndex.query(
                QueryEnhancedRequest.builder()
                    .queryConditional(queryConditional)
                    .limit(1)
                    .build())
            .stream()
            .flatMap(page -> page.items().stream())
            .findFirst();
    }

    @Override
    public void save(PlaceEnrichmentCacheEntry entry) {
        table.putItem(entry);
    }
}
