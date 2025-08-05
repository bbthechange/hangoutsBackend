package com.bbthechange.inviter.config;

import com.bbthechange.inviter.model.Event;
import com.bbthechange.inviter.model.Invite;
import com.bbthechange.inviter.model.User;
import com.bbthechange.inviter.model.BaseItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import com.bbthechange.inviter.util.QueryPerformanceTracker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import jakarta.annotation.PostConstruct;

@TestConfiguration
public class IntegrationTestConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(IntegrationTestConfiguration.class);
    
    @Autowired
    private DynamoDbEnhancedClient dynamoDbEnhancedClient;
    
    @Autowired
    private DynamoDbClient dynamoDbClient;
    
    @PostConstruct
    public void initializeTables() {
        logger.info("Initializing DynamoDB tables for integration tests");
        createTableIfNotExists("Users", User.class);
        createTableIfNotExists("Events", Event.class);  
        createTableIfNotExists("Invites", Invite.class);
        createInviterTableIfAbsent();
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
    
    /**
     * Create the InviterTable with GSI for hangout features
     */
    private void createInviterTableIfAbsent() {
        try {
            // Check if table exists
            dynamoDbClient.describeTable(DescribeTableRequest.builder()
                .tableName("InviterTable")
                .build());
            logger.info("Table InviterTable already exists");
            return;
            
        } catch (ResourceNotFoundException e) {
            logger.info("Creating InviterTable with GSI for integration tests");
            
            CreateTableRequest request = CreateTableRequest.builder()
                .tableName("InviterTable")
                .keySchema(
                    KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
                    KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build()
                )
                .attributeDefinitions(
                    AttributeDefinition.builder().attributeName("pk").attributeType(ScalarAttributeType.S).build(),
                    AttributeDefinition.builder().attributeName("sk").attributeType(ScalarAttributeType.S).build(),
                    AttributeDefinition.builder().attributeName("gsi1pk").attributeType(ScalarAttributeType.S).build(),
                    AttributeDefinition.builder().attributeName("gsi1sk").attributeType(ScalarAttributeType.S).build()
                )
                .globalSecondaryIndexes(
                    GlobalSecondaryIndex.builder()
                        .indexName("UserGroupIndex")
                        .keySchema(
                            KeySchemaElement.builder().attributeName("gsi1pk").keyType(KeyType.HASH).build(),
                            KeySchemaElement.builder().attributeName("gsi1sk").keyType(KeyType.RANGE).build()
                        )
                        .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                        .provisionedThroughput(ProvisionedThroughput.builder()
                            .readCapacityUnits(5L).writeCapacityUnits(5L).build())
                        .build()
                )
                .provisionedThroughput(ProvisionedThroughput.builder()
                    .readCapacityUnits(5L).writeCapacityUnits(5L).build())
                .build();
                
            dynamoDbClient.createTable(request);
            
            // Wait for table to be active
            dynamoDbClient.waiter().waitUntilTableExists(r -> r.tableName("InviterTable"));
            logger.info("Created InviterTable successfully");
            
        } catch (Exception e) {
            logger.error("Error creating InviterTable: {}", e.getMessage());
        }
    }
    
    /**
     * Provide QueryPerformanceTracker for tests
     */
    @Bean
    public QueryPerformanceTracker queryPerformanceTracker() {
        return new QueryPerformanceTracker(simpleMeterRegistry());
    }
    
    /**
     * Provide MeterRegistry for tests
     */
    @Bean
    public MeterRegistry simpleMeterRegistry() {
        return new SimpleMeterRegistry();
    }
}