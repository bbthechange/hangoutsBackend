# Integration Tests Context

## Overview

**Staging integration tests** verify API behavior against the live staging environment. They run automatically in the deployment pipeline after staging deployment succeeds, and block production deployment if they fail.

**Purpose**: Catch integration issues, API contract violations, and database consistency problems before production deployment.

## How They Work

### Execution Flow

```
Deploy to Staging → Wait 30s → Health Check → Run Integration Tests → Deploy to Production
                                                         ↓ (if fail)
                                                   Rollback Staging
```

### Test Environment

- **Target**: Live staging API (`STAGING_URL` secret)
- **Database**: Real DynamoDB staging tables (shared with staging environment)
- **Authentication**: Uses dedicated test accounts (see Test Accounts section)
- **Execution**: Sequential (not parallel) to avoid race conditions
- **Timeout**: 10 minutes total job timeout

## Running Tests

### Locally

```bash
# Set environment variables
export STAGING_URL=https://v7ihwy6uv9.execute-api.us-west-2.amazonaws.com/prod
export STAGING_TEST_USER_PHONE=+15551111001
export STAGING_TEST_USER_PASSWORD=StagingTest123!

# Run all staging tests
./gradlew stagingTest

# Run specific test class
./gradlew stagingTest --tests "GroupCrudTests"

# Run with fail-fast disabled (see all failures)
STAGING_TEST_FAIL_FAST=false ./gradlew stagingTest
```

### In CI/CD (GitHub Actions)

Tests run automatically after staging deployment. To trigger manually:
1. Go to Actions tab → Latest workflow run
2. Click "Re-run failed jobs" (if tests failed)
3. Or trigger new deployment by pushing to main

**Required GitHub Secrets**:
- `STAGING_URL`
- `STAGING_TEST_USER_PHONE`
- `STAGING_TEST_USER_PASSWORD`

## Test Accounts

### Requirements

Each test account must:
- Exist in staging environment
- Have `accountStatus: ACTIVE`
- Have phone number verified (not in `UNVERIFIED` state)
- Have valid password

### Current Test Accounts

**Primary**: `+15551111001` (used by all tests)
**Secondary**: `+15551111002`, `+15551111003` (used by collaboration tests)

**Setup**: Test accounts are created manually in staging environment and must persist across test runs.

## Test Structure

### Directory Layout

```
src/stagingTest/java/com/bbthechange/inviter/staging/
├── StagingTestBase.java           # Base class with common setup/cleanup
├── auth/
│   └── AuthenticationTests.java   # Auth endpoints
├── groups/
│   ├── GroupCrudTests.java        # Group CRUD operations
│   └── CollaborationTests.java    # Multi-user interactions
├── hangouts/
│   └── HangoutCrudTests.java      # Hangout CRUD operations
└── infrastructure/
    └── InfrastructureTests.java   # Health checks, CORS, etc.
```

### Base Class Pattern

All test classes extend `StagingTestBase` which provides:
- Automatic login and token management
- Helper methods for creating test data (`createTestGroup`, `createTestHangout`)
- Automatic cleanup in `@AfterEach`
- Resource tracking for orphan detection

## Critical Patterns

### 1. UUID-Based Naming

**Always use UUID suffixes** to prevent name conflicts:

```java
String groupName = "Test Group " + UUID.randomUUID().toString().substring(0, 8);
```

**Why**: Tests may run multiple times, leaving data in staging. UUID ensures uniqueness.

### 2. DynamoDB GSI Consistency

**Problem**: DynamoDB GSIs have eventual consistency (~100ms-2s delay).

**Solution**: Use Awaitility for queries that depend on GSIs:

```java
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

// Wait for GSI propagation before asserting
await()
    .atMost(3, SECONDS)
    .pollInterval(200, MILLISECONDS)
    .untilAsserted(() -> {
        given()
            .get("/groups")
        .then()
            .statusCode(200)
            .body("find { it.groupId == '" + groupId + "' }", notNullValue());
    });
```

**When to use**:
- List operations (`GET /groups`, `GET /hangouts`)
- Queries by non-primary-key attributes
- After create/update operations that affect list queries

**When NOT to use**:
- Direct GET by ID (`GET /groups/{id}`)
- Primary key lookups (consistent reads)

### 3. Automatic Cleanup

Tests automatically clean up created resources in `@AfterEach`:

```java
protected String createTestGroup(String namePrefix) {
    String groupId = /* create group */;
    createdGroupIds.add(groupId);  // IMPORTANT: Track for cleanup
    return groupId;
}
```

**Order matters**: Hangouts deleted before groups (foreign key constraints).

**Retry logic**: Cleanup retries 3 times with exponential backoff. Failed cleanups logged as orphaned resources.

### 4. Test Isolation

Each test is isolated via:
- UUID-based naming (no shared data)
- `@AfterEach` cleanup (no state leakage)
- Sequential execution (no race conditions)

**Do NOT**:
- Share data between tests
- Rely on test execution order (except within a class using `@Order`)
- Use hardcoded IDs or names

## REST Assured Patterns

### Making Requests

```java
import static io.restassured.RestAssured.given;

// POST with authentication
String groupId = given()
    .header("Authorization", "Bearer " + testUserToken)
    .contentType("application/json")
    .body("{\"groupName\":\"My Group\",\"public\":true}")
.when()
    .post("/groups")
.then()
    .statusCode(201)
    .body("groupId", notNullValue())
.extract()
    .jsonPath()
    .getString("groupId");

// GET with assertions
given()
    .header("Authorization", "Bearer " + testUserToken)
.when()
    .get("/groups/" + groupId)
.then()
    .statusCode(200)
    .body("groupName", equalTo("My Group"))
    .body("members", hasSize(1));
```

### Common Matchers

```java
import static org.hamcrest.Matchers.*;

.body("field", notNullValue())
.body("field", equalTo("value"))
.body("field", containsString("partial"))
.body("list", hasSize(3))
.body("list", hasItem("value"))
.body("nested.field", equalTo("value"))
.body("find { it.id == '123' }", notNullValue())  // Groovy path
```

## Deployment Pipeline Integration

### Normal Flow

1. **Build** - Compile and run unit tests
2. **Deploy Staging** - Deploy to staging, capture previous version
3. **Staging Tests** - Run integration tests (this is us!)
4. **Deploy Production** - Only if tests pass
5. **Production Smoke Tests** - Quick validation

### Failure Handling

**If staging tests fail**:
1. Staging automatically rolls back to previous version
2. Production deployment blocked
3. GitHub issue created with failure details
4. Test results uploaded as artifact

**Test failures trigger rollback because**:
- They indicate API contract violations
- They catch database consistency issues
- They verify authentication/authorization works
- They validate multi-user interactions

## Adding New Tests

### Checklist

1. **Extend StagingTestBase**
   ```java
   class MyNewTests extends StagingTestBase {
   ```

2. **Use UUID naming for all created data**
   ```java
   String name = "Test " + UUID.randomUUID().toString().substring(0, 8);
   ```

3. **Add created resources to tracking lists**
   ```java
   createdGroupIds.add(groupId);
   createdHangoutIds.add(hangoutId);
   ```

4. **Use Awaitility for GSI-dependent queries**
   ```java
   await().atMost(3, SECONDS).untilAsserted(() -> { /* assertion */ });
   ```

5. **Write descriptive display names**
   ```java
   @DisplayName("Create group with valid data returns 201")
   ```

6. **Test both success and error cases**
   ```java
   void createGroup_InvalidData_Returns400()
   void createGroup_Unauthorized_Returns401()
   ```

### Example Test Class

```java
package com.bbthechange.inviter.staging.myfeature;

import com.bbthechange.inviter.staging.StagingTestBase;
import org.junit.jupiter.api.*;
import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.Matchers.*;

@DisplayName("My Feature - Live Staging")
class MyFeatureTests extends StagingTestBase {

    @Test
    @DisplayName("Feature operation succeeds with valid data")
    void myOperation_ValidData_Succeeds() {
        // Arrange
        String groupId = createTestGroup("Test Group");

        // Act
        String result = given()
            .header("Authorization", "Bearer " + testUserToken)
            .contentType("application/json")
            .body("{\"groupId\":\"" + groupId + "\"}")
        .when()
            .post("/my-endpoint")
        .then()
            .statusCode(200)
        .extract()
            .jsonPath()
            .getString("resultId");

        // Assert - wait for GSI if needed
        await()
            .atMost(3, SECONDS)
            .pollInterval(200, MILLISECONDS)
            .untilAsserted(() -> {
                given()
                    .header("Authorization", "Bearer " + testUserToken)
                .when()
                    .get("/my-endpoint")
                .then()
                    .statusCode(200)
                    .body("find { it.id == '" + result + "' }", notNullValue());
            });
    }
}
```

## Troubleshooting

### Tests Fail: "Test account login failed"

**Cause**: Test account doesn't exist or has wrong credentials.

**Fix**:
1. Verify account exists in staging: Login manually with test credentials
2. Check GitHub secrets match actual test account
3. Ensure account is verified (not UNVERIFIED status)

### Tests Fail: "No data found" after create

**Cause**: GSI consistency delay.

**Fix**: Wrap list queries in `await()` (see DynamoDB GSI Consistency section).

### Tests Fail: Cleanup errors

**Cause**:
- Resource already deleted
- Foreign key constraint (deleting group before hangout)
- Network timeout

**Fix**: Check orphaned resources report at end of test run. Cleanup retries automatically, so persistent failures indicate data integrity issue.

### Tests Timeout in CI/CD

**Current limit**: 10 minutes for entire test job.

**If exceeded**:
1. Check for infinite loops in `await()` (should have `atMost()`)
2. Verify staging environment is responsive
3. Consider splitting into multiple test jobs

### Parallel Execution Issues

**Current**: Tests run sequentially (`parallel.enabled = false`).

**If enabling parallel execution**:
- Ensure UUID naming prevents conflicts
- Test with `./gradlew stagingTest --info` locally
- Watch for race conditions in shared resources

## Best Practices

### DO

✅ Use UUID-based naming for all test data
✅ Use Awaitility for GSI-dependent queries
✅ Add created resources to cleanup lists
✅ Test both success and error cases
✅ Write descriptive test names
✅ Keep tests independent (no shared state)
✅ Use helper methods from `StagingTestBase`

### DON'T

❌ Use hardcoded IDs or names
❌ Skip GSI waits on list operations
❌ Share data between tests
❌ Forget to track created resources for cleanup
❌ Make assertions without waiting for consistency
❌ Use sleep instead of Awaitility
❌ Test business logic (that's for unit tests)

## Configuration

### build.gradle

```gradle
// Staging test dependencies
stagingTestImplementation 'io.rest-assured:rest-assured:5.4.0'
stagingTestImplementation 'io.rest-assured:json-path:5.4.0'
stagingTestImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
stagingTestImplementation 'org.awaitility:awaitility:4.2.0'
```

### Task Configuration

```gradle
task stagingTest(type: Test) {
    // Sequential execution (change to true after stability proven)
    systemProperty 'junit.jupiter.execution.parallel.enabled', 'false'

    // 10 minute timeout
    timeout = Duration.ofMinutes(10)

    // Fail fast (stop on first failure)
    failFast = System.getenv('STAGING_TEST_FAIL_FAST') != 'false'
}
```

## Metrics

**Current test suite**:
- 25 tests across 5 test classes
- ~10-15 seconds execution time
- ~400 API calls total
- 100% pass rate requirement (blocks production on failure)

**Target metrics**:
- Execution time: <30 seconds
- Pass rate: 100%
- Coverage: Critical user paths only (not exhaustive)

## Related Documentation

- `DATABASE_ARCHITECTURE_CRITICAL.md` - DynamoDB GSI behavior
- `.github/workflows/backend-ci-cd.yml` - Deployment pipeline
- `build.gradle` - Test configuration
- `DEPLOYMENT_INTEGRATION_TEST_PLAN.md` - Original implementation plan
