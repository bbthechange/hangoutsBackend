package com.bbthechange.inviter.config;

import com.bbthechange.inviter.model.Event;
import com.bbthechange.inviter.model.Invite;
import com.bbthechange.inviter.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

@Component
public class DynamoDBTableInitializer implements ApplicationRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(DynamoDBTableInitializer.class);
    
    @Autowired
    private DynamoDbEnhancedClient dynamoDbEnhancedClient;
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        createTableIfNotExists("Users", User.class);
        createTableIfNotExists("Events", Event.class);  
        createTableIfNotExists("Invites", Invite.class);
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
            
            // Create table without GSI first - GSI creation with Enhanced client is complex
            table.createTable(builder -> builder
                .provisionedThroughput(throughput -> throughput
                    .readCapacityUnits(5L)
                    .writeCapacityUnits(5L)));
            
            logger.info("Table {} created successfully", tableName);
        } catch (Exception e) {
            logger.error("Error creating table {}: {}", tableName, e.getMessage());
            throw e;
        }
    }
}