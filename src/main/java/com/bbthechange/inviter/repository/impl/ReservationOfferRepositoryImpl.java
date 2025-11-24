package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.model.ReservationOffer;
import com.bbthechange.inviter.repository.ReservationOfferRepository;
import com.bbthechange.inviter.util.InviterKeyFactory;
import com.bbthechange.inviter.util.QueryPerformanceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DynamoDB implementation of ReservationOfferRepository.
 * Uses Enhanced Client with QueryPerformanceTracker for monitoring.
 */
@Repository
public class ReservationOfferRepositoryImpl implements ReservationOfferRepository {

    private static final Logger logger = LoggerFactory.getLogger(ReservationOfferRepositoryImpl.class);
    private static final String TABLE_NAME = "InviterTable";

    private final DynamoDbClient dynamoDbClient;
    private final DynamoDbTable<ReservationOffer> offerTable;
    private final QueryPerformanceTracker performanceTracker;

    @Autowired
    public ReservationOfferRepositoryImpl(DynamoDbClient dynamoDbClient,
                                         DynamoDbEnhancedClient enhancedClient,
                                         QueryPerformanceTracker performanceTracker) {
        this.dynamoDbClient = dynamoDbClient;
        this.offerTable = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(ReservationOffer.class));
        this.performanceTracker = performanceTracker;
    }

    @Override
    public ReservationOffer save(ReservationOffer offer) {
        return performanceTracker.trackQuery("saveReservationOffer", TABLE_NAME, () -> {
            offer.touch();  // Update timestamp
            offerTable.putItem(offer);
            logger.debug("Saved reservation offer: {}", offer.getOfferId());
            return offer;
        });
    }

    @Override
    public Optional<ReservationOffer> findById(String hangoutId, String offerId) {
        return performanceTracker.trackQuery("findReservationOfferById", TABLE_NAME, () -> {
            Key key = Key.builder()
                    .partitionValue(InviterKeyFactory.getEventPk(hangoutId))
                    .sortValue(InviterKeyFactory.getReservationOfferSk(offerId))
                    .build();

            ReservationOffer offer = offerTable.getItem(key);
            logger.debug("Found reservation offer: {} for hangout: {}", offerId, hangoutId);
            return Optional.ofNullable(offer);
        });
    }

    @Override
    public List<ReservationOffer> findByHangoutId(String hangoutId) {
        return performanceTracker.trackQuery("findReservationOffersByHangoutId", TABLE_NAME, () -> {
            // Query all items with PK=HANGOUT#{hangoutId} and SK starting with RESERVEOFFER#
            QueryConditional conditional = QueryConditional.sortBeginsWith(
                    Key.builder()
                            .partitionValue(InviterKeyFactory.getEventPk(hangoutId))
                            .sortValue(InviterKeyFactory.RESERVEOFFER_PREFIX + "#")
                            .build()
            );

            QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                    .queryConditional(conditional)
                    .build();

            List<ReservationOffer> offers = new ArrayList<>();
            offerTable.query(request).items().forEach(offers::add);

            logger.debug("Found {} reservation offers for hangout: {}", offers.size(), hangoutId);
            return offers;
        });
    }

    @Override
    public void delete(String hangoutId, String offerId) {
        performanceTracker.trackQuery("deleteReservationOffer", TABLE_NAME, () -> {
            Key key = Key.builder()
                    .partitionValue(InviterKeyFactory.getEventPk(hangoutId))
                    .sortValue(InviterKeyFactory.getReservationOfferSk(offerId))
                    .build();

            offerTable.deleteItem(key);
            logger.debug("Deleted reservation offer: {} for hangout: {}", offerId, hangoutId);
            return null;
        });
    }
}
