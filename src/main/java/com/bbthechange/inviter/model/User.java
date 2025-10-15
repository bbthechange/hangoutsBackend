package com.bbthechange.inviter.model;

import com.bbthechange.inviter.util.InstantAsLongAttributeConverter;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;
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
    private String mainImagePath;
    private AccountStatus accountStatus;
    private Instant creationDate;
    private Boolean isTestAccount;
    
    public User(String phoneNumber, String username, String password) {
        this.id = UUID.randomUUID();
        this.phoneNumber = phoneNumber;
        this.username = username;
        this.displayName = null;
        this.password = password;
        this.accountStatus = AccountStatus.UNVERIFIED;
        this.creationDate = Instant.now();
        this.isTestAccount = false;
    }
    
    public User(String phoneNumber, String username, String displayName, String password) {
        this.id = UUID.randomUUID();
        this.phoneNumber = phoneNumber;
        this.username = username;
        this.displayName = displayName;
        this.password = password;
        this.accountStatus = AccountStatus.UNVERIFIED;
        this.creationDate = Instant.now();
        this.isTestAccount = false;
    }
    
    @DynamoDbPartitionKey
    public UUID getId() {
        return id;
    }
    
    @DynamoDbSecondaryPartitionKey(indexNames = "PhoneNumberIndex")
    public String getPhoneNumber() {
        return phoneNumber;
    }

    @DynamoDbConvertedBy(InstantAsLongAttributeConverter.class)
    public Instant getCreationDate() {
        return creationDate;
    }
}