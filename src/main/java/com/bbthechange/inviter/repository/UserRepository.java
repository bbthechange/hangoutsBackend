package com.bbthechange.inviter.repository;

import com.bbthechange.inviter.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepository {
    
    private final DynamoDbTable<User> userTable;
    private final DynamoDbIndex<User> phoneNumberIndex;
    
    @Autowired
    public UserRepository(DynamoDbEnhancedClient dynamoDbEnhancedClient) {
        this.userTable = dynamoDbEnhancedClient.table("Users", TableSchema.fromBean(User.class));
        this.phoneNumberIndex = userTable.index("PhoneNumberIndex");
    }
    
    public User save(User user) {
        userTable.putItem(user);
        return user;
    }
    
    public Optional<User> findById(UUID id) {
        User user = userTable.getItem(Key.builder().partitionValue(id.toString()).build());
        return Optional.ofNullable(user);
    }
    
    public Optional<User> findByPhoneNumber(String phoneNumber) {
        return phoneNumberIndex.query(QueryConditional.keyEqualTo(Key.builder()
                .partitionValue(phoneNumber)
                .build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .findFirst();
    }
    
    public void delete(User user) {
        userTable.deleteItem(user);
    }
}