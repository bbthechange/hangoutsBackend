package com.bbthechange.inviter.config;

import com.bbthechange.inviter.model.Event;
import com.bbthechange.inviter.model.Invite;
import com.bbthechange.inviter.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

import jakarta.annotation.PostConstruct;

@TestConfiguration
public class IntegrationTestConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(IntegrationTestConfiguration.class);
    
    @Autowired
    private DynamoDbEnhancedClient dynamoDbEnhancedClient;
    
    @PostConstruct
    public void initializeTables() {
        logger.info("Initializing DynamoDB tables for integration tests");
        createTableIfNotExists("Users", User.class);
        createTableIfNotExists("Events", Event.class);  
        createTableIfNotExists("Invites", Invite.class);
        logger.info("DynamoDB tables initialized successfully");
    }
    
    private <T> void createTableIfNotExists(String tableName, Class<T> entityClass) {
        try {
            DynamoDbTable<T> table = dynamoDbEnhancedClient.table(tableName, TableSchema.fromBean(entityClass));
            
            // Try to describe the table (this will throw an exception if it doesn't exist)
            table.describeTable();
            logger.info("Table {} already exists", tableName);
            
        } catch (ResourceNotFoundException e) {
            logger.info("Creating table: {}", tableName);
            DynamoDbTable<T> table = dynamoDbEnhancedClient.table(tableName, TableSchema.fromBean(entityClass));
            
            // Create table for integration tests
            table.createTable(builder -> builder
                .provisionedThroughput(throughput -> throughput
                    .readCapacityUnits(5L)
                    .writeCapacityUnits(5L)));
            logger.info("Table {} created successfully", tableName);
        } catch (Exception e) {
            logger.error("Error creating table {}: {}", tableName, e.getMessage());
        }
    }
}