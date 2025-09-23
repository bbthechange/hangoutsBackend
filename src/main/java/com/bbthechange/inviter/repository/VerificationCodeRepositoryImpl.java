package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.model.VerificationCode;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.Optional;

@Repository
public class VerificationCodeRepositoryImpl implements VerificationCodeRepository {

    private final DynamoDbTable<VerificationCode> verificationCodeTable;

    public VerificationCodeRepositoryImpl(DynamoDbEnhancedClient enhancedClient) {
        this.verificationCodeTable = enhancedClient.table("VerificationCodes", 
                                                          TableSchema.fromBean(VerificationCode.class));
    }

    @Override
    public void save(VerificationCode verificationCode) {
        verificationCodeTable.putItem(verificationCode);
    }

    @Override
    public Optional<VerificationCode> findByPhoneNumber(String phoneNumber) {
        Key key = Key.builder()
                .partitionValue(phoneNumber)
                .build();
        
        VerificationCode item = verificationCodeTable.getItem(key);
        return Optional.ofNullable(item);
    }

    @Override
    public void deleteByPhoneNumber(String phoneNumber) {
        Key key = Key.builder()
                .partitionValue(phoneNumber)
                .build();
        
        verificationCodeTable.deleteItem(key);
    }
}