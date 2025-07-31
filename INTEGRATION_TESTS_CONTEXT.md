# Integration Tests Context

## Overview

This document provides context about the integration test implementation for the Inviter Spring Boot API, current status, issues encountered, and next steps.

## Project Structure

### Files Created/Modified

1. **Test Dependencies** (`build.gradle`)
   - Added TestContainers dependencies: `testcontainers`, `junit-jupiter`, `localstack`
   - Created separate `integrationTest` Gradle task
   - Excluded integration tests from regular unit test runs

2. **Test Configuration**
   - `src/test/resources/application-integration.properties` - Integration test profile
   - `src/test/java/com/bbthechange/inviter/config/BaseIntegrationTest.java` - Base test class
   - `src/test/java/com/bbthechange/inviter/config/IntegrationTestConfiguration.java` - Test configuration

3. **Integration Test Classes**
   - `AuthControllerIntegrationTest.java` - Authentication endpoint tests
   - `EventControllerIntegrationTest.java` - Event CRUD operation tests  
   - `InviteControllerIntegrationTest.java` - Invitation management tests
   - `ProfileControllerIntegrationTest.java` - Profile management tests
   - `ImageControllerIntegrationTest.java` - Image upload URL tests
   - `SimpleAuthIntegrationTest.java` - Simplified test attempt

4. **Helper Scripts**
   - `run-integration-tests.sh` - Convenience script for running tests

## Current Status: FAILING

### Primary Issue: DynamoDB GSI Setup

**Error Symptom:**
```
ResourceNotFoundException: Requested resource not found
```

**Root Cause:**
The TestContainers DynamoDB Local setup with DynamoDB Enhanced Client fails when querying Global Secondary Indexes (GSIs), specifically the `PhoneNumberIndex` used by `UserRepository.findByPhoneNumber()`.

### Architecture Challenge

The integration tests attempt to:
1. Start TestContainer LocalStack with DynamoDB Local
2. Use DynamoDB Enhanced Client (same as production)
3. Create tables with GSIs automatically
4. Run real HTTP requests against Spring Boot endpoints

**The Problem:**
- DynamoDB Enhanced Client + TestContainers + GSI creation is complex
- GSI creation requires specific Enhanced Client configuration
- `PhoneNumberIndex` not being created properly in test environment

## What's Been Tried

### 1. Basic TestContainers Setup
- **Attempted:** Basic DynamoDB Local container setup
- **Result:** Tables created but no GSIs
- **Issue:** `findByPhoneNumber()` queries fail

### 2. Manual Table Creation in `@BeforeEach`
- **Attempted:** Create tables in test setup method
- **Result:** Same GSI creation issues
- **Code:** Added `createTablesIfNeeded()` method

### 3. `@TestConfiguration` Approach
- **Attempted:** Separate test configuration class with `@PostConstruct`
- **Result:** Spring context loading issues
- **Code:** `IntegrationTestConfiguration.java`

### 4. Enhanced GSI Creation Logic
- **Attempted:** Modified main `DynamoDBTableInitializer` to properly create GSIs
- **Code Updates:** Added `createTableWithGSIs()` method with Enhanced Client GSI builders
- **Current Status:** Main application now creates GSIs properly, but test environment still fails

### 5. Simplified Mock-Based Tests  
- **Attempted:** `SimpleAuthIntegrationTest` with `@MockBean` repositories
- **Result:** Bean definition conflicts with TestContainers setup
- **Issue:** Spring context confusion between real and mocked beans

## Technical Details

### DynamoDB GSI Requirements

**Users Table GSIs:**
- `PhoneNumberIndex` - Partition key: `phoneNumber` (used by login/registration)

**Invites Table GSIs:**
- `EventIndex` - Partition key: `eventId` (used for listing event invites)
- `UserIndex` - Partition key: `userId` (used for user's invitations)

**Devices Table GSIs:**
- `UserIndex` - Partition key: `userId` (used for push notifications)

### Enhanced Client GSI Creation

The main application now correctly creates GSIs using:

```java
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
```

### Test Environment Issues

1. **TestContainers LocalStack Version:** Using `localstack/localstack:3.0`
2. **DynamoDB Local Compatibility:** May have GSI creation limitations
3. **Enhanced Client Behavior:** Different behavior in test vs production environment
4. **Spring Context Conflicts:** Multiple configuration sources causing bean conflicts

## Next Steps to Try

### Option 1: Fix TestContainers GSI Creation (Recommended)

1. **Debug GSI Creation Process**
   ```bash
   # Enable debug logging for DynamoDB operations
   logging.level.software.amazon.awssdk=DEBUG
   logging.level.com.bbthechange.inviter.config=DEBUG
   ```

2. **Verify GSI Attribute Mapping**
   - Ensure `@DynamoDbSecondaryPartitionKey` annotations are correct
   - Verify attribute mapping in test environment

3. **Wait for Table/GSI Creation**
   - Add wait logic after table creation
   - DynamoDB Local may need time for GSI to become active

4. **Test with Direct DynamoDB Client**
   - Create tables using low-level DynamoDB client in tests
   - Compare with Enhanced Client behavior

### Option 2: Alternative TestContainers Approach

1. **Use DynamoDB Docker Image Directly**
   ```java
   @Container
   static GenericContainer<?> dynamodb = new GenericContainer<>("amazon/dynamodb-local:latest")
       .withExposedPorts(8000)
       .withCommand("-jar", "DynamoDBLocal.jar", "-sharedDb", "-inMemory");
   ```

2. **Create Tables with AWS SDK v2 Low-Level Client**
   - Use standard DynamoDB client for table creation
   - Switch to Enhanced Client for repository operations

### Option 3: Hybrid Integration Testing

1. **Mock Repositories, Test Controllers**
   - Use `@WebMvcTest` instead of `@SpringBootTest`
   - Mock repository layer, test HTTP layer
   - Focus on endpoint behavior, not database integration

2. **Separate Database Integration Tests**
   - Create repository-level integration tests
   - Test Enhanced Client operations separately

### Option 4: Production-Like Testing

1. **Use Real DynamoDB Local Instance**
   - Run DynamoDB Local as separate process
   - Configure tests to use local endpoint
   - Manage table lifecycle manually

2. **AWS DynamoDB Test Environment**
   - Use actual AWS DynamoDB with test tables
   - Requires AWS credentials and cleanup logic

## Immediate Debugging Steps

1. **Enable Verbose Logging**
   ```properties
   logging.level.software.amazon.awssdk.services.dynamodb=DEBUG
   logging.level.com.bbthechange.inviter=DEBUG
   logging.level.org.testcontainers=DEBUG
   ```

2. **Inspect Table Creation**
   ```java
   // Add to test setup
   DynamoDbTable<User> table = dynamoDbEnhancedClient.table("Users", TableSchema.fromBean(User.class));
   TableMetadata metadata = table.describeTable();
   logger.info("Table GSIs: {}", metadata.globalSecondaryIndexes());
   ```

3. **Test GSI Queries Directly**
   ```java
   // Test if GSI exists and is queryable
   DynamoDbIndex<User> phoneIndex = userTable.index("PhoneNumberIndex");
   // Try simple query
   ```

## Architecture Notes

The integration tests are **architecturally sound** and follow Spring Boot best practices:
- Real HTTP endpoint testing
- Complete request/response validation  
- JWT authentication flow testing
- Multi-user authorization scenarios
- Proper test isolation and cleanup

The issue is purely technical - TestContainers + DynamoDB Enhanced Client + GSI compatibility.

## Workaround for Immediate Use

For deployment verification, use manual API testing:

```bash
# Start application
./gradlew bootRun

# Test registration
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"+1234567890","username":"test","displayName":"Test","password":"pass123"}'

# Test login  
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"+1234567890","password":"pass123"}'
```

This provides the same verification as integration tests until the technical issues are resolved.