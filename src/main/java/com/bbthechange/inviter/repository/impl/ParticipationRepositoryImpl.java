package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.exception.RepositoryException;
import com.bbthechange.inviter.model.Participation;
import com.bbthechange.inviter.repository.ParticipationRepository;
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
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementation of ParticipationRepository using DynamoDB Enhanced Client.
 * Manages participation records in the InviterTable.
 */
@Repository
public class ParticipationRepositoryImpl implements ParticipationRepository {

    private static final Logger logger = LoggerFactory.getLogger(ParticipationRepositoryImpl.class);
    private static final String TABLE_NAME = "InviterTable";

    private final DynamoDbClient dynamoDbClient;
    private final DynamoDbTable<Participation> participationTable;
    private final QueryPerformanceTracker performanceTracker;

    @Autowired
    public ParticipationRepositoryImpl(
            DynamoDbClient dynamoDbClient,
            DynamoDbEnhancedClient dynamoDbEnhancedClient,
            QueryPerformanceTracker performanceTracker) {
        this.dynamoDbClient = dynamoDbClient;
        this.participationTable = dynamoDbEnhancedClient.table(TABLE_NAME, TableSchema.fromBean(Participation.class));
        this.performanceTracker = performanceTracker;
    }

    @Override
    public Participation save(Participation participation) {
        return performanceTracker.trackQuery("saveParticipation", TABLE_NAME, () -> {
            try {
                logger.debug("Saving participation {} for hangout {}",
                        participation.getParticipationId(), participation.getPk());

                // Update timestamp
                participation.touch();

                // Upsert operation - creates or updates
                participationTable.putItem(participation);

                logger.debug("Successfully saved participation {} for hangout {}",
                        participation.getParticipationId(), participation.getPk());
                return participation;

            } catch (Exception e) {
                logger.error("Failed to save participation {}", participation.getParticipationId(), e);
                throw new RepositoryException("Failed to save participation", e);
            }
        });
    }

    @Override
    public Optional<Participation> findById(String hangoutId, String participationId) {
        return performanceTracker.trackQuery("findParticipationById", TABLE_NAME, () -> {
            try {
                logger.debug("Finding participation {} for hangout {}", participationId, hangoutId);

                // Direct key access - most efficient DynamoDB operation
                Key key = Key.builder()
                        .partitionValue(InviterKeyFactory.getEventPk(hangoutId))
                        .sortValue(InviterKeyFactory.getParticipationSk(participationId))
                        .build();

                Participation participation = participationTable.getItem(r -> r.key(key));
                return Optional.ofNullable(participation);

            } catch (Exception e) {
                logger.error("Failed to find participation {} for hangout {}", participationId, hangoutId, e);
                throw new RepositoryException("Failed to retrieve participation", e);
            }
        });
    }

    @Override
    public List<Participation> findByHangoutId(String hangoutId) {
        return performanceTracker.trackQuery("findParticipationsByHangoutId", TABLE_NAME, () -> {
            try {
                logger.debug("Finding all participations for hangout {}", hangoutId);

                // Single-partition query - gets all participations in one efficient query
                QueryConditional conditional = QueryConditional.sortBeginsWith(
                        Key.builder()
                                .partitionValue(InviterKeyFactory.getEventPk(hangoutId))
                                .sortValue(InviterKeyFactory.PARTICIPATION_PREFIX + "#")
                                .build()
                );

                PageIterable<Participation> pages = participationTable.query(QueryEnhancedRequest.builder()
                        .queryConditional(conditional)
                        .scanIndexForward(true)
                        .build());

                List<Participation> participations = new ArrayList<>();
                pages.items().forEach(participations::add);

                logger.debug("Found {} participations for hangout {}", participations.size(), hangoutId);
                return participations;

            } catch (Exception e) {
                logger.error("Failed to find participations for hangout {}", hangoutId, e);
                throw new RepositoryException("Failed to retrieve participations", e);
            }
        });
    }

    @Override
    public List<Participation> findByOfferId(String hangoutId, String offerId) {
        return performanceTracker.trackQuery("findParticipationsByOfferId", TABLE_NAME, () -> {
            try {
                logger.debug("Finding participations linked to offer {} for hangout {}", offerId, hangoutId);

                // Query all participations for the hangout, then filter by offerId
                // This is efficient because all participations are in the same partition
                List<Participation> allParticipations = findByHangoutId(hangoutId);

                List<Participation> filtered = allParticipations.stream()
                        .filter(p -> offerId.equals(p.getReservationOfferId()))
                        .collect(Collectors.toList());

                logger.debug("Found {} participations linked to offer {} for hangout {}",
                        filtered.size(), offerId, hangoutId);
                return filtered;

            } catch (Exception e) {
                logger.error("Failed to find participations for offer {} in hangout {}", offerId, hangoutId, e);
                throw new RepositoryException("Failed to retrieve participations by offer", e);
            }
        });
    }

    @Override
    public void delete(String hangoutId, String participationId) {
        performanceTracker.trackQuery("deleteParticipation", TABLE_NAME, () -> {
            try {
                logger.debug("Deleting participation {} for hangout {}", participationId, hangoutId);

                Key key = Key.builder()
                        .partitionValue(InviterKeyFactory.getEventPk(hangoutId))
                        .sortValue(InviterKeyFactory.getParticipationSk(participationId))
                        .build();

                participationTable.deleteItem(r -> r.key(key));

                logger.debug("Successfully deleted participation {} for hangout {}", participationId, hangoutId);
                return null;

            } catch (Exception e) {
                logger.error("Failed to delete participation {} for hangout {}", participationId, hangoutId, e);
                throw new RepositoryException("Failed to delete participation", e);
            }
        });
    }
}
