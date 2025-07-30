package com.bbthechange.inviter.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DynamoDBConfigTest {

    @Test
    void dynamoDbClient_WithDefaultRegion_ShouldCreateClient() {
        DynamoDBConfig config = new DynamoDBConfig();
        ReflectionTestUtils.setField(config, "region", "us-east-1");
        ReflectionTestUtils.setField(config, "endpoint", "");

        DynamoDbClient client = config.dynamoDbClient();

        assertNotNull(client);
    }

    @Test
    void dynamoDbClient_WithCustomRegion_ShouldCreateClient() {
        DynamoDBConfig config = new DynamoDBConfig();
        ReflectionTestUtils.setField(config, "region", "us-west-2");
        ReflectionTestUtils.setField(config, "endpoint", "");

        DynamoDbClient client = config.dynamoDbClient();

        assertNotNull(client);
    }

    @Test
    void dynamoDbClient_WithLocalEndpoint_ShouldCreateClientWithEndpointOverride() {
        DynamoDBConfig config = new DynamoDBConfig();
        ReflectionTestUtils.setField(config, "region", "us-east-1");
        ReflectionTestUtils.setField(config, "endpoint", "http://localhost:8000");

        DynamoDbClient client = config.dynamoDbClient();

        assertNotNull(client);
    }

    @Test
    void dynamoDbEnhancedClient_ShouldCreateEnhancedClient() {
        DynamoDBConfig config = new DynamoDBConfig();
        DynamoDbClient mockClient = mock(DynamoDbClient.class);

        DynamoDbEnhancedClient enhancedClient = config.dynamoDbEnhancedClient(mockClient);

        assertNotNull(enhancedClient);
    }
}