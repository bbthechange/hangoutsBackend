# CLAUDE.md - Inviter Backend API

## ⚠️ CRITICAL REQUIREMENTS

- **MANDATORY TESTING**: Every new method/field MUST have unit tests before completion
- **DATABASE ARCHITECTURE**: MUST read `DATABASE_ARCHITECTURE_CRITICAL.md` before ANY database modifications or hangout retrievals
- **TDD Protocol**: Never exit until `./gradlew test` shows "BUILD SUCCESSFUL"
- **No Claude References**: Never include Claude in commit messages
- **Java 21 Required**: Development and production use Corretto 21

## Quick Commands

```bash
# Daily development
./gradlew bootRun              # Start API (port 8080)
./gradlew test                # Run tests (MANDATORY after changes)

# Build and deploy  
./gradlew clean build         # Clean build
eb deploy                     # Deploy to production

# Integration testing
./gradlew integrationTest     # Full test suite with TestContainers
```

## TDD Protocol (MANDATORY)

### Success Criteria
```bash
./gradlew test
# Must show: BUILD SUCCESSFUL with 0 test failures
```

### Process Requirements
1. **Run tests after every code change** - compilation errors = test failures
2. **Use agents correctly**:
   - `unit-test-runner` - Only for running tests and reporting
   - `tdd-failure-investigator` - For ANY error (compilation, test, runtime)
   - `code-verification-guard` - After every code modification
3. **No shortcuts**: No stubs, no skipped tests, no large changes

### TDD Constraints
- Make one small change at a time
- Compilation errors count as test failures
- YOU write code - agents analyze and run tests only

## Architecture Overview

**Spring Boot 3.5.3 REST API** with JWT authentication and DynamoDB backend.

### Package Structure
```
com.bbthechange.inviter/
├── controller/     # REST endpoints
├── service/        # Business logic  
├── repository/     # DynamoDB access
├── model/          # Entity classes
├── security/       # JWT and Spring Security
└── config/         # Application configuration
```

### Design Pattern: Controller → Service → Repository
- **Controllers**: Handle HTTP requests, validation, responses
- **Services**: Business logic, authorization rules
- **Repositories**: DynamoDB operations with Enhanced Client

## API Endpoints Quick Reference

### Authentication (Public)
```bash
POST /auth/register    # Register new user
POST /auth/login       # Get JWT token (24h expiration)
```

### Events (JWT Required)
```bash
POST   /events/new     # Create event  
GET    /events         # List user's events
GET    /events/{id}    # Get event details
PUT    /events/{id}    # Update event (hosts only)
```

### Invitations (JWT Required) 
```bash
GET    /events/{eventId}/invites           # List invites
POST   /events/{eventId}/invites           # Add invite (hosts only)
PUT    /events/{eventId}/invites/{id}      # Update RSVP response
DELETE /events/{eventId}/invites/{id}      # Remove invite (hosts only)
```

### Profile (JWT Required)
```bash
GET /profile           # Get user profile
PUT /profile           # Update display name  
PUT /profile/password  # Change password
```

### Images (Mixed Auth)
```bash
GET  /images/predefined    # Public: Get predefined image options
POST /images/upload-url    # JWT: Get presigned S3 upload URL
```

## Core Models

### Event
```java
// Key fields
private String id;              // UUID primary key
private String title;           // Event name
private String description;     // Event details  
private List<String> hosts;     // Host user IDs
private EventVisibility visibility; // INVITE_ONLY or PUBLIC
private String imageUrl;        // S3 image path
private Long version;           // Optimistic locking
```

### User  
```java
// Key fields
private String id;              // UUID primary key
private String phoneNumber;     // Unique identifier (GSI)
private String username;        // Display username
private String displayName;     // Full display name
private String passwordHash;    // BCrypt hashed password
```

### Invite
```java
// Key fields  
private String id;              // UUID primary key
private String eventId;         // Foreign key to Event (GSI)
private String userId;          // Foreign key to User (GSI)
private InviteStatus status;    // PENDING, ACCEPTED, DECLINED
private String phoneNumber;     // Invite recipient phone
```

## Authorization Rules

### Event Access
- **View**: User must be invited OR be a host
- **Update**: Host only
- **Delete**: Host only

### Invite Management
- **Add/Remove**: Hosts only
- **Update Response**: Invite recipient only
- **View All**: Event hosts and invitees

## Security Implementation

### JWT Configuration
- **Expiration**: 24 hours
- **Algorithm**: HS256
- **Header**: `Authorization: Bearer <token>`
- **Claims**: userId, phoneNumber, username

### Password Security
- **Hashing**: BCrypt with salt
- **Validation**: Minimum requirements enforced
- **Change**: Requires current password verification

### Spring Security Setup
```java
// Key security filters
JwtAuthenticationFilter    # Validates JWT tokens
SecurityConfig            # Configures endpoints and CORS
```

## DynamoDB Configuration

### Tables (Auto-created on startup)
1. **Users**: PK `id`, GSI `PhoneNumberIndex` on `phoneNumber`
2. **Events**: PK `id`
3. **Invites**: PK `id`, GSI `EventIndex` on `eventId`, GSI `UserIndex` on `userId`  
4. **Devices**: PK `token`, GSI `UserIndex` on `userId`

### Enhanced Client Usage
```java
// Repository pattern with Enhanced Client
@Repository
public class EventRepositoryImpl implements EventRepository {
    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<Event> eventTable;
}
```

## Testing Strategy

### Unit Tests (MANDATORY)
- **Coverage**: Every new method/field must have tests
- **Framework**: JUnit 5 with Mockito
- **Pattern**: Test service logic, mock repository dependencies
- **Naming**: `{MethodName}_{Scenario}_{ExpectedResult}`

### Integration Tests
```bash
./gradlew integrationTest
# Uses TestContainers for real DynamoDB testing
```

### Test Examples
```java
@Test
void createEvent_WithValidData_ReturnsEventId() {
    // Given
    EventRequest request = new EventRequest("Test Event", "Description");
    
    // When  
    String eventId = eventService.createEvent(request, userId);
    
    // Then
    assertThat(eventId).isNotNull();
    verify(eventRepository).save(any(Event.class));
}
```

## Development Environment

### Local Setup
```bash
# Start application
./gradlew bootRun

# Access points
API: http://localhost:8080
Swagger: http://localhost:8080/swagger-ui.html
Health: http://localhost:8080/health
```

### Configuration Files
- **application.properties**: Default/development config
- **application-prod.properties**: Production overrides
- **application-test.properties**: Test configuration

## AWS Production Deployment

### Elastic Beanstalk Setup
- **Account**: 871070087012
- **Application**: `inviter-app-new`
- **Environment**: `inviter-test` (production)
- **Platform**: Corretto 21 on Amazon Linux 2023
- **URL**: http://inviter-test.eba-meudu6bv.us-west-2.elasticbeanstalk.com

### Deployment Process

**⚠️ CRITICAL: Always verify artifact freshness to prevent stale deployments**

```bash
# 1. Build application with latest code
./gradlew clean build

# 2. Copy fresh JAR for EB deployment  
cp build/libs/inviter-0.0.1-SNAPSHOT.jar ./application.jar

# 3. MANDATORY: Verify artifact timestamp is current
ls -la application.jar
# Must show CURRENT timestamp - if stale, repeat steps 1-2

# 4. Deploy to production
eb deploy

# 5. MANDATORY: Verify deployment version after completion
eb status | grep "Deployed Version"
# Should show version matching current commit hash and timestamp
```

**Deployment Verification Checklist:**
- [ ] Fresh build completed (`BUILD SUCCESSFUL`)
- [ ] Artifact timestamp is current (within last few minutes)
- [ ] Deployment version reflects current commit
- [ ] Production health check passes
- [ ] API behavior matches localhost (if using same database)

### Production Configuration
- **Procfile**: `web: java -jar application.jar --server.port=5000 --spring.profiles.active=prod`
- **IAM Role**: `aws-elasticbeanstalk-ec2-role` with DynamoDB access
- **Environment Variables**: Set via EB console or `.ebextensions/`

## API Gateway Integration

### HTTPS Endpoints
- **Production**: https://am6c8sp6kh.execute-api.us-west-2.amazonaws.com/prod/
- **Staging**: https://v7ihwy6uv9.execute-api.us-west-2.amazonaws.com/prod/

### SSL Configuration
- **Certificate**: AWS ACM managed
- **Custom Domain**: `api.inviter.app` (pending DNS validation)
- **CORS**: Configured for web frontend origins

## S3 Image Management

### Presigned Upload URLs
```java
// Generate upload URL for direct client → S3 uploads
@PostMapping("/images/upload-url")
public UploadUrlResponse getUploadUrl(@RequestBody UploadUrlRequest request) {
    return s3Service.generatePresignedUploadUrl(request.getKey(), request.getContentType());
}
```

### S3 Configuration
- **Bucket**: `inviter-event-images-871070087012`
- **Region**: us-west-2
- **Expiration**: 15 minutes for upload URLs
- **Path Structure**: `events/{userId}/{filename}` for custom uploads

## Troubleshooting Guide

### Common Issues

#### JWT Token Problems
```bash
# Get valid token for testing
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "+19285251044", "password": "mypass"}'
```

#### DynamoDB Access Issues  
- **Problem**: "Cannot do operations on a non-existent table"
- **Solution**: Use API endpoints, not direct AWS CLI
- **Root Cause**: Enhanced Client creates tables differently than standard AWS CLI

#### Spring Boot Startup Failures
- **Check**: Java 21 compatibility
- **Verify**: DynamoDB table initialization doesn't block web server
- **Debug**: Temporarily disable `@Component` annotations for isolation

### Test User Credentials
```json
{
  "phoneNumber": "+19285251044",
  "password": "mypass",
  "username": "jeana",
  "displayName": "Jeana"
}
```

## Manual Operations

### Emergency Data Access
If you need to bypass normal authorization:
1. Temporarily modify controller validation for specific operations
2. Use any valid JWT token for authentication  
3. Restore authorization immediately after operation

### Getting JWT for Manual Testing
```bash
# Login
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "YOUR_PHONE", "password": "YOUR_PASSWORD"}'

# Use token
curl http://localhost:8080/events \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

## Key Dependencies

```gradle
// Major dependencies
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.springframework.boot:spring-boot-starter-security'  
implementation 'software.amazon.awssdk:dynamodb-enhanced'
implementation 'io.jsonwebtoken:jjwt-api'
implementation 'org.springframework.security:spring-security-crypto'
```

**Version Requirements**: Java 21, Spring Boot 3.5.3, AWS SDK 2.x