package com.bbthechange.inviter.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class User {
    private UUID id;
    private String phoneNumber;
    private String username;
    private String displayName;
    private String password;
    private String deviceToken;
    
    public User(String phoneNumber, String username, String password) {
        this.id = UUID.randomUUID();
        this.phoneNumber = phoneNumber;
        this.username = username;
        this.displayName = null;
        this.password = password;
    }
    
    public User(String phoneNumber, String username, String displayName, String password) {
        this.id = UUID.randomUUID();
        this.phoneNumber = phoneNumber;
        this.username = username;
        this.displayName = displayName;
        this.password = password;
    }
    
    @DynamoDbPartitionKey
    public UUID getId() {
        return id;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "PhoneNumberIndex")
    public String getPhoneNumber() {
        return phoneNumber;
    }
}