package com.bbthechange.inviter.repository.impl;

import com.bbthechange.inviter.model.*;
import com.bbthechange.inviter.repository.EventRepository;
import com.bbthechange.inviter.util.QueryPerformanceTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Base class for HangoutRepositoryImpl tests.
 * Provides common mocks, setup, and test data generation.
 *
 * All repository test files extend this class to share the setup logic.
 */
@ExtendWith(MockitoExtension.class)
abstract class HangoutRepositoryTestBase {

    protected static final String TABLE_NAME = "InviterTable";

    @Mock
    protected DynamoDbClient dynamoDbClient;

    @Mock
    protected DynamoDbEnhancedClient dynamoDbEnhancedClient;

    @Mock
    protected DynamoDbTable<HangoutAttribute> inviterTable;

    @Mock
    protected QueryPerformanceTracker performanceTracker;

    @Mock
    protected EventRepository eventRepository;

    protected HangoutRepositoryImpl repository;

    protected String eventId;
    protected String pollId;
    protected String optionId;
    protected String userId;
    protected String groupId;
    protected String seriesId;

    @BeforeEach
    void setUp() {
        // Set up tracking to properly propagate exceptions
        lenient().when(performanceTracker.trackQuery(anyString(), anyString(), any())).thenAnswer(invocation -> {
            try {
                java.util.function.Supplier<?> supplier = invocation.getArgument(2);
                return supplier.get();
            } catch (Exception e) {
                // Re-throw the exception to maintain proper error handling
                throw e;
            }
        });

        // Mock the Enhanced Client to return our mocked table
        when(dynamoDbEnhancedClient.table(eq("InviterTable"), any(TableSchema.class))).thenReturn(inviterTable);

        repository = new HangoutRepositoryImpl(dynamoDbClient, dynamoDbEnhancedClient, performanceTracker, eventRepository);

        // Initialize common test IDs
        eventId = UUID.randomUUID().toString();
        pollId = UUID.randomUUID().toString();
        optionId = UUID.randomUUID().toString();
        userId = UUID.randomUUID().toString();
        groupId = UUID.randomUUID().toString();
        seriesId = UUID.randomUUID().toString();
    }
}
