package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.model.RefreshToken;
import com.bbthechange.inviter.repository.RefreshTokenRepository;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class RefreshTokenRepositoryImpl implements RefreshTokenRepository {
    
    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<RefreshToken> refreshTokenTable;
    private final DynamoDbIndex<RefreshToken> tokenHashIndex;
    
    public RefreshTokenRepositoryImpl(DynamoDbEnhancedClient enhancedClient) {
        this.enhancedClient = enhancedClient;
        this.refreshTokenTable = enhancedClient.table("InviterTable", 
            TableSchema.fromBean(RefreshToken.class));
        this.tokenHashIndex = refreshTokenTable.index("TokenHashIndex");
    }
    
    @Override
    public RefreshToken save(RefreshToken token) {
        token.touch(); // Update timestamp
        refreshTokenTable.putItem(token);
        return token;
    }
    
    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        try {
            QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder()
                    .partitionValue(tokenHash)
                    .build());
                    
            SdkIterable<Page<RefreshToken>> results = tokenHashIndex.query(
                QueryEnhancedRequest.builder()
                    .queryConditional(queryConditional)
                    .limit(1)
                    .build());
                    
            return results.stream().flatMap(page -> page.items().stream()).findFirst();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
    
    @Override
    public List<RefreshToken> findAllByUserId(String userId) {
        QueryConditional queryConditional = QueryConditional
            .sortBeginsWith(Key.builder()
                .partitionValue("USER#" + userId)
                .sortValue("REFRESH_TOKEN#")
                .build());
                
        return refreshTokenTable.query(
            QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .build())
            .stream()
            .flatMap(page -> page.items().stream())
            .collect(Collectors.toList());
    }
    
    @Override
    public void deleteByTokenId(String userId, String tokenId) {
        Key key = Key.builder()
            .partitionValue("USER#" + userId)
            .sortValue("REFRESH_TOKEN#" + tokenId)
            .build();
        refreshTokenTable.deleteItem(key);
    }
    
    @Override
    public void deleteAllUserTokens(String userId) {
        List<RefreshToken> userTokens = findAllByUserId(userId);
        
        if (!userTokens.isEmpty()) {
            WriteBatch.Builder<RefreshToken> batchBuilder = WriteBatch.builder(RefreshToken.class)
                .mappedTableResource(refreshTokenTable);
                
            userTokens.forEach(token -> 
                batchBuilder.addDeleteItem(Key.builder()
                    .partitionValue(token.getPk())
                    .sortValue(token.getSk())
                    .build())
            );
            
            enhancedClient.batchWriteItem(BatchWriteItemEnhancedRequest.builder()
                .writeBatches(batchBuilder.build())
                .build());
        }
    }
}