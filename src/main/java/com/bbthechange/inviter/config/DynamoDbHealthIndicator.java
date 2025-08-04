package com.bbthechange.inviter.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;

/**
 * Health indicator for DynamoDB connectivity and table status.
 * Checks both existing tables and the new InviterTable.
 */
@Component
public class DynamoDbHealthIndicator implements HealthIndicator {
    
    private final DynamoDbClient dynamoDbClient;
    
    @Autowired
    public DynamoDbHealthIndicator(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }
    
    @Override
    public Health health() {
        try {
            // Check InviterTable (critical for hangout features)
            DescribeTableResponse inviterTableResponse = dynamoDbClient.describeTable(
                DescribeTableRequest.builder().tableName("InviterTable").build()
            );
            
            TableStatus inviterTableStatus = inviterTableResponse.table().tableStatus();
            
            if (inviterTableStatus == TableStatus.ACTIVE) {
                // Also check a legacy table to ensure overall connectivity
                DescribeTableResponse usersTableResponse = dynamoDbClient.describeTable(
                    DescribeTableRequest.builder().tableName("Users").build()
                );
                
                return Health.up()
                    .withDetail("inviterTable", "ACTIVE")
                    .withDetail("inviterTableGsiCount", inviterTableResponse.table().globalSecondaryIndexes().size())
                    .withDetail("usersTable", usersTableResponse.table().tableStatus().toString())
                    .withDetail("region", "us-west-2")
                    .build();
                    
            } else {
                return Health.down()
                    .withDetail("inviterTable", inviterTableStatus.toString())
                    .withReason("InviterTable not active")
                    .build();
            }
            
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", "DynamoDB connection failed")
                .withDetail("message", e.getMessage())
                .build();
        }
    }
}