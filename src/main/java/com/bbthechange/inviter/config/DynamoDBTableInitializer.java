package com.bbthechange.inviter.config;

import com.bbthechange.inviter.model.Device;
import com.bbthechange.inviter.model.Event;
import com.bbthechange.inviter.model.Invite;
import com.bbthechange.inviter.model.User;
import com.bbthechange.inviter.model.BaseItem;
import com.bbthechange.inviter.model.RefreshToken;
import com.bbthechange.inviter.model.VerificationCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.CreateTableEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.EnhancedGlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.concurrent.CompletableFuture;

@Component
@ConditionalOnProperty(name = "dynamodb.table.init.enabled", havingValue = "true", matchIfMissing = true)
public class DynamoDBTableInitializer implements ApplicationRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(DynamoDBTableInitializer.class);
    
    @Autowired
    private DynamoDbEnhancedClient dynamoDbEnhancedClient;
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        // Existing tables
        createTableIfNotExists("Users", User.class);
        createTableIfNotExists("Events", Event.class);  
        createTableIfNotExists("Invites", Invite.class);
        createTableIfNotExists("Devices", Device.class);
        
        // New InviterTable with GSI for hangout features and refresh tokens
        createTableIfNotExists("InviterTable", BaseItem.class);
        
        // Verification codes table with TTL
        createTableIfNotExists("VerificationCodes", VerificationCode.class);
        
        // Configure TTL for refresh tokens and verification codes
        configureTTL("InviterTable", "expiryDate");
        configureTTL("VerificationCodes", "expiresAt");
    }
    
    private <T> void createTableIfNotExists(String tableName, Class<T> entityClass) {
        try {
            DynamoDbTable<T> table = dynamoDbEnhancedClient.table(tableName, TableSchema.fromBean(entityClass));
            
            // Try to describe the table (this will throw an exception if it doesn't exist)
            table.describeTable();
            logger.info("Table {} already exists", tableName);
            
        } catch (ResourceNotFoundException e) {
            logger.info("Creating table: {}", tableName);
            createTableWithGSIs(tableName, entityClass);
            logger.info("Table {} created successfully with GSIs", tableName);
        } catch (Exception e) {
            logger.error("Error creating table {}: {}", tableName, e.getMessage());
            throw e;
        }
    }
    
    private <T> void createTableWithGSIs(String tableName, Class<T> entityClass) {
        DynamoDbTable<T> table = dynamoDbEnhancedClient.table(tableName, TableSchema.fromBean(entityClass));
        
        CreateTableEnhancedRequest.Builder requestBuilder = CreateTableEnhancedRequest.builder()
            .provisionedThroughput(ProvisionedThroughput.builder()
                .readCapacityUnits(5L)
                .writeCapacityUnits(5L)
                .build());
        
        // Add GSIs based on table name
        switch (tableName) {
            case "Users":
                requestBuilder.globalSecondaryIndices(
                    createGSI("PhoneNumberIndex")
                );
                break;
            case "Invites":
                requestBuilder.globalSecondaryIndices(
                    createGSI("EventIndex"),
                    createGSI("UserIndex")
                );
                break;
            case "Devices":
                requestBuilder.globalSecondaryIndices(
                    createGSI("UserIndex")
                );
                break;
            case "InviterTable":
                requestBuilder.globalSecondaryIndices(
                    createGSI("UserGroupIndex"),
                    createGSI("EntityTimeIndex"),
                    createGSI("TokenHashIndex"),  // Add new GSI for refresh token lookups
                    createGSI("InviteCodeIndex")  // Add new GSI for group invite code lookups
                );
                break;
            // Events table has no GSIs
        }
        
        table.createTable(requestBuilder.build());
    }
    
    private EnhancedGlobalSecondaryIndex createGSI(String indexName) {
        return EnhancedGlobalSecondaryIndex.builder()
            .indexName(indexName)
            .provisionedThroughput(ProvisionedThroughput.builder()
                .readCapacityUnits(5L)
                .writeCapacityUnits(5L)
                .build())
            .projection(Projection.builder()
                .projectionType(ProjectionType.ALL)
                .build())
            .build();
    }
    
    private void configureTTL(String tableName, String ttlAttributeName) {
        try {
            logger.info("Configuring TTL for table {} on attribute {}", tableName, ttlAttributeName);
            // Note: In production, you may need to configure TTL manually via AWS Console
            // or add explicit DynamoDbClient injection if needed for programmatic TTL configuration
            
        } catch (Exception e) {
            logger.warn("Could not configure TTL for table {} on attribute {}: {}", 
                tableName, ttlAttributeName, e.getMessage());
        }
    }
}