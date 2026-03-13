package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.exception.RepositoryException;
import com.bbthechange.inviter.model.AttributeProposal;
import com.bbthechange.inviter.model.AttributeProposalStatus;
import com.bbthechange.inviter.model.AttributeProposalType;
import com.bbthechange.inviter.repository.AttributeProposalRepository;
import com.bbthechange.inviter.util.InviterKeyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * DynamoDB implementation for AttributeProposalRepository.
 *
 * Proposals are stored inside the hangout's item collection:
 *   PK = EVENT#{hangoutId}
 *   SK = PROPOSAL#{proposalId}
 */
@Repository
public class AttributeProposalRepositoryImpl implements AttributeProposalRepository {

    private static final Logger logger = LoggerFactory.getLogger(AttributeProposalRepositoryImpl.class);
    private static final String TABLE_NAME = "InviterTable";

    private final DynamoDbClient dynamoDbClient;
    private final TableSchema<AttributeProposal> proposalSchema;

    @Autowired
    public AttributeProposalRepositoryImpl(DynamoDbClient dynamoDbClient,
                                           DynamoDbEnhancedClient dynamoDbEnhancedClient) {
        this.dynamoDbClient = dynamoDbClient;
        this.proposalSchema = TableSchema.fromBean(AttributeProposal.class);
    }

    @Override
    public AttributeProposal save(AttributeProposal proposal) {
        try {
            Map<String, AttributeValue> item = proposalSchema.itemToMap(proposal, true);
            dynamoDbClient.putItem(PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(item)
                    .build());
            return proposal;
        } catch (DynamoDbException e) {
            logger.error("Failed to save proposal {} for hangout {}", proposal.getProposalId(), proposal.getHangoutId(), e);
            throw new RepositoryException("Failed to save attribute proposal", e);
        }
    }

    @Override
    public Optional<AttributeProposal> findById(String hangoutId, String proposalId) {
        try {
            GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of(
                            "pk", AttributeValue.builder().s(InviterKeyFactory.getEventPk(hangoutId)).build(),
                            "sk", AttributeValue.builder().s(InviterKeyFactory.getProposalSk(proposalId)).build()
                    ))
                    .build());

            if (!response.hasItem() || response.item().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(proposalSchema.mapToItem(response.item()));
        } catch (DynamoDbException e) {
            logger.error("Failed to find proposal {} for hangout {}", proposalId, hangoutId, e);
            throw new RepositoryException("Failed to retrieve attribute proposal", e);
        }
    }

    @Override
    public List<AttributeProposal> findByHangoutId(String hangoutId) {
        try {
            QueryResponse response = dynamoDbClient.query(QueryRequest.builder()
                    .tableName(TABLE_NAME)
                    .keyConditionExpression("pk = :pk AND begins_with(sk, :prefix)")
                    .expressionAttributeValues(Map.of(
                            ":pk", AttributeValue.builder().s(InviterKeyFactory.getEventPk(hangoutId)).build(),
                            ":prefix", AttributeValue.builder().s(AttributeProposal.PROPOSAL_PREFIX + "#").build()
                    ))
                    .build());

            return response.items().stream()
                    .map(item -> {
                        try {
                            return proposalSchema.mapToItem(item);
                        } catch (Exception e) {
                            logger.warn("Failed to deserialize proposal item: {}", e.getMessage());
                            return null;
                        }
                    })
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (DynamoDbException e) {
            logger.error("Failed to find proposals for hangout {}", hangoutId, e);
            throw new RepositoryException("Failed to retrieve attribute proposals", e);
        }
    }

    @Override
    public List<AttributeProposal> findPendingByHangoutIdAndType(String hangoutId,
                                                                   AttributeProposalType attributeType) {
        return findByHangoutId(hangoutId).stream()
                .filter(p -> AttributeProposalStatus.PENDING.equals(p.getStatus()))
                .filter(p -> attributeType.equals(p.getAttributeType()))
                .collect(Collectors.toList());
    }

    @Override
    public List<AttributeProposal> findExpiredPendingProposals(long nowMillis) {
        // Scan with filter for PENDING proposals past their expiresAt.
        // This is used by the hourly scheduled task so infrequent full-table scan is acceptable.
        try {
            List<AttributeProposal> results = new ArrayList<>();
            Map<String, AttributeValue> lastKey = null;

            do {
                ScanRequest.Builder builder = ScanRequest.builder()
                        .tableName(TABLE_NAME)
                        .filterExpression(
                                "begins_with(sk, :prefix) AND #status = :pending AND expiresAt <= :now"
                        )
                        .expressionAttributeNames(Map.of("#status", "status"))
                        .expressionAttributeValues(Map.of(
                                ":prefix", AttributeValue.builder().s(AttributeProposal.PROPOSAL_PREFIX + "#").build(),
                                ":pending", AttributeValue.builder().s(AttributeProposalStatus.PENDING.name()).build(),
                                ":now", AttributeValue.builder().n(String.valueOf(nowMillis)).build()
                        ));

                if (lastKey != null) {
                    builder.exclusiveStartKey(lastKey);
                }

                ScanResponse response = dynamoDbClient.scan(builder.build());
                response.items().forEach(item -> {
                    try {
                        results.add(proposalSchema.mapToItem(item));
                    } catch (Exception e) {
                        logger.warn("Failed to deserialize expired proposal: {}", e.getMessage());
                    }
                });
                lastKey = response.lastEvaluatedKey().isEmpty() ? null : response.lastEvaluatedKey();
            } while (lastKey != null);

            return results;
        } catch (DynamoDbException e) {
            logger.error("Failed to scan for expired pending proposals", e);
            throw new RepositoryException("Failed to scan for expired proposals", e);
        }
    }

    @Override
    public void delete(String hangoutId, String proposalId) {
        try {
            dynamoDbClient.deleteItem(DeleteItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(Map.of(
                            "pk", AttributeValue.builder().s(InviterKeyFactory.getEventPk(hangoutId)).build(),
                            "sk", AttributeValue.builder().s(InviterKeyFactory.getProposalSk(proposalId)).build()
                    ))
                    .build());
        } catch (DynamoDbException e) {
            logger.error("Failed to delete proposal {} for hangout {}", proposalId, hangoutId, e);
            throw new RepositoryException("Failed to delete attribute proposal", e);
        }
    }
}
