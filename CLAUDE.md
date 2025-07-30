# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Inviter** is a Spring Boot 3.5.3 REST API for event management with DynamoDB backend. Features JWT-based authentication, user management, event creation, and phone number invitation system.

## Development Commands

```bash
# Run the application (requires Java 21)
./gradlew bootRun

# Build and test
./gradlew build

# Run tests only
./gradlew test

# Clean build
./gradlew clean build

# Create executable JAR
./gradlew bootJar
```

**Important**: The project is compiled with Java 21. Local development requires Java 21+. Production deployment uses Corretto 21 on Elastic Beanstalk.

## Commit Guidelines

- Don't include references to Claude in commit logs

## Architecture

### Core Structure
- **Package**: `com.bbthechange.inviter`
- **Pattern**: MVC with Controller → Service → Repository
- **Database**: AWS DynamoDB with Enhanced Client
- **Security**: JWT-based authentication with Spring Security
- **API**: RESTful JSON endpoints with OpenAPI/Swagger documentation

### Key Components

#### Models (`model/`)
- **Event**: DynamoDB entity with location, visibility, hosts, and versioning
- **User**: User authentication and profile data with phone number indexing
- **Invite**: Event invitation management with response tracking
- **EventVisibility**: Enum for INVITE_ONLY vs PUBLIC events

#### Controllers (`controller/`)
- **AuthController**: User registration and JWT login
- **EventController**: CRUD operations for events (create, list, get, update)
- **InviteController**: Invitation management (add, remove, update responses)
- **ProfileController**: User profile and password management
- **ImageController**: Predefined image options

#### Services (`service/`)
- **EventService**: Event business logic and host validation
- **InviteService**: Invitation creation and management
- **UserService**: User profile operations
- **JwtService**: JWT token generation and validation
- **PasswordService**: BCrypt password hashing

#### Security
- **SecurityConfig**: Spring Security configuration with JWT filter
- **JwtAuthenticationFilter**: Request interceptor for JWT validation

### API Endpoints

#### Authentication (`/auth`) - Public
| Method | URL | Purpose | Response |
|--------|-----|---------|----------|
| POST | `/auth/register` | Register user | `{"message": "User registered successfully"}` |
| POST | `/auth/login` | Login user | `{"token": "jwt", "expiresIn": 86400}` |

#### Events (`/events`) - Requires JWT
| Method | URL | Purpose | Response |
|--------|-----|---------|----------|
| POST | `/events/new` | Create event | `{"id": "uuid"}` |
| GET | `/events` | List user events | Array of events |
| GET | `/events/{id}` | Get specific event | Event object or 404 |
| PUT | `/events/{id}` | Update event (hosts only) | Updated event object |

#### Invites (`/events/{eventId}/invites`) - Requires JWT
| Method | URL | Purpose | Response |
|--------|-----|---------|----------|
| GET | `/events/{eventId}/invites` | List event invites | Array of invites |
| POST | `/events/{eventId}/invites` | Add invite (hosts only) | `{"inviteId": "uuid"}` |
| PUT | `/events/{eventId}/invites/{inviteId}` | Update response | Updated invite |
| DELETE | `/events/{eventId}/invites/{inviteId}` | Remove invite (hosts only) | Success message |

#### Profile (`/profile`) - Requires JWT
| Method | URL | Purpose | Response |
|--------|-----|---------|----------|
| GET | `/profile` | Get user profile | User object |
| PUT | `/profile` | Update display name | Success message |
| PUT | `/profile/password` | Change password | Success message |
| DELETE | `/profile` | Delete user account | Success message |

#### Images (`/images`) - Public/Protected
| Method | URL | Purpose | Response |
|--------|-----|---------|----------|
| GET | `/images/predefined` | Get predefined images | Array of image options |
| POST | `/images/upload-url` | Get presigned S3 upload URL | Presigned URL for direct S3 upload |

**Request Body (for upload-url):**
```json
{
  "key": "events/{userId}/{timestamp}_{randomId}_{filename}",
  "contentType": "image/jpeg"
}
```

**Response (from upload-url):**
```json
{
  "uploadUrl": "https://inviter-event-images-prod.s3.us-west-2.amazonaws.com/events/user123/12345_abc_image.jpg?X-Amz-Credential=...",
  "key": "events/user123/12345_abc_image.jpg"
}
```

**Implementation Details:**
- **Purpose**: Generate presigned S3 upload URLs for direct iOS → S3 uploads (industry best practice)
- **Auth**: Requires JWT token
- **S3 Config**: Bucket `inviter-event-images-871070087012` in `us-west-2`, 15-minute URL expiration
- **Components**: `S3Service.generatePresignedUploadUrl()`, `ImageController.getUploadUrl()`
- **DTOs**: `UploadUrlRequest`, `UploadUrlResponse`
- **Testing**: Full unit test coverage in `S3ServiceTest` and `ImageControllerTest`

### Authentication
- **Method**: JWT Bearer tokens (24-hour expiration)
- **Header**: `Authorization: Bearer <token>`
- **Login**: Phone number + password
- **Storage**: BCrypt password hashing

### Authorization Rules
- **Event Access**: User must be invited OR be a host
- **Event Updates**: Host only
- **Invite Management**: Hosts can add/remove, users can update own responses

## Development Notes

- **Testing**: Uses JUnit 5, currently minimal test coverage
- **Configuration**: DynamoDB connection in `application.properties`
- **Port**: Default 8080
- **Documentation**: Swagger UI at `http://localhost:8080/swagger-ui.html`
- **Dependencies**: Spring Boot, Spring Security, DynamoDB Enhanced Client, JWT, BCrypt, Lombok

## Manual Database Operations

### DynamoDB Access Issues
- **AWS CLI**: Direct DynamoDB access via AWS CLI often fails with "Cannot do operations on a non-existent table" even when application can access tables
- **Root Cause**: Application uses DynamoDB Enhanced Client which may have different table creation/access patterns than standard AWS CLI
- **Tables**: Events, Users, Invites, Devices (plural names, created automatically on app startup via `DynamoDBTableInitializer`)

### Manual Data Deletion Options
1. **API Endpoints**: Use existing REST endpoints when possible (requires JWT auth)
2. **Temporary Code Bypass**: Modify controller validation temporarily for specific operations
3. **Command Line Runner**: Create temporary Spring Boot CommandLineRunner components
4. **Direct Repository Access**: Not recommended due to Enhanced Client complexities

### Emergency Event Deletion Process
If you need to delete an event with unrecognized hosts:
1. Temporarily modify `EventController.deleteEvent()` to bypass host validation for specific event ID
2. Use any valid JWT token to call `DELETE /events/{id}`
3. Restore original host validation immediately after deletion

### Getting JWT Tokens for Manual Operations
```bash
# Login to get JWT token
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "YOUR_PHONE", "password": "YOUR_PASSWORD"}'

# Use token for authenticated requests
curl -X DELETE http://localhost:8080/events/{id} \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

## AWS Elastic Beanstalk Deployment

### Production Environment
- **Account**: 871070087012 (dedicated account with free tier)
- **Application**: `inviter-app-new`
- **Environment**: `inviter-test` (primary production environment)
- **Platform**: Corretto 21 running on 64bit Amazon Linux 2023
- **Region**: us-west-2
- **URL**: `http://inviter-test.eba-meudu6bv.us-west-2.elasticbeanstalk.com`

### Deployment Files
- **Procfile**: `web: java -jar application.jar --server.port=5000 --spring.profiles.active=prod`
- **.ebextensions/java.config**: Sets `SPRING_PROFILES_ACTIVE=prod`
- **.ebextensions/instance-profile.config**: Configures IAM role for DynamoDB access
- **.ebignore**: Excludes build artifacts except final JAR
- **application.jar**: Copy of `build/libs/inviter-0.0.1-SNAPSHOT.jar` in project root

### Deployment Process
```bash
# Build the application
./gradlew clean build

# Copy JAR to project root (required for EB deployment)
cp build/libs/inviter-0.0.1-SNAPSHOT.jar ./application.jar

# Deploy to Elastic Beanstalk
eb deploy
```

### IAM Configuration
The deployment uses the default `aws-elasticbeanstalk-ec2-role` with DynamoDB permissions:
- **Role**: `aws-elasticbeanstalk-ec2-role`
- **Policies**: `AmazonDynamoDBFullAccess` and `AmazonDynamoDBFullAccess_v2`
- **Permissions**: The `dynamodb:*` wildcard includes all DynamoDB operations including `DescribeTable`

### Deployment Troubleshooting

#### Web Server Startup Issues
If the application fails to start or Tomcat doesn't initialize:
1. **Check Spring Boot Configuration**: Ensure `spring-boot-starter-web` dependency is present
2. **Disable Problematic Components**: Temporarily disable `@Component` classes that might cause startup failures
3. **Add Debug Logging**: Use `WebServerInitializedEvent` listener to confirm Tomcat startup
4. **Minimal Configuration**: Reduce `application-prod.properties` to minimal settings for testing

#### DynamoDB Connection Issues
1. **Table Initialization**: The `DynamoDBTableInitializer` automatically creates Users, Events, Invites, and Devices tables with their respective GSIs
2. **Permission Errors**: If getting "not authorized to perform: dynamodb:DescribeTable", verify IAM role has DynamoDB policies
3. **Temporary Disable**: Can disable table initializer with `//@Component` for testing web server independently

#### Spring Security Configuration
- **Health Check Endpoints**: Ensure `/` and `/health` endpoints are permitted in `SecurityConfig`
- **Load Balancer Access**: EB health checks must be able to access root endpoint without authentication

### Common Issues
1. **Java Version Mismatch**: Ensure EB platform is set to Corretto 21
2. **JAR Inclusion**: JAR must be copied to project root as `application.jar` for EB to find it
3. **Region Configuration**: Verify DynamoDB client is configured for us-west-2 in production profile
4. **Web Server Not Starting**: Check logs for ApplicationRunner failures that prevent Tomcat initialization

## AWS Production Account

**Primary Production Account**: 871070087012 (dedicated account with free tier benefits)

### Production Infrastructure Details

#### Elastic Beanstalk Configuration
- **Application**: `inviter-app-new`
- **Environment**: `inviter-test` (production-ready)
- **Platform**: Corretto 21 running on 64bit Amazon Linux 2023
- **Instance Type**: t3.small
- **Region**: us-west-2
- **Load Balancer**: Application Load Balancer (ALB)
- **Auto Scaling**: Min 1, Max 4 instances

#### DynamoDB Tables (Auto-created)
All tables use provisioned throughput (5 RCU/5 WCU):
1. **Users**: Partition key `id` (UUID), GSI `PhoneNumberIndex` on `phoneNumber`
2. **Events**: Partition key `id` (UUID)
3. **Invites**: Partition key `id` (UUID), GSI `EventIndex` on `eventId`, GSI `UserIndex` on `userId`
4. **Devices**: Partition key `token` (String), GSI `UserIndex` on `userId`

#### S3 Configuration
- **Bucket**: `inviter-event-images-871070087012`
- **Region**: us-west-2
- **Predefined Images**: 23 images uploaded to `predefined/` prefix
- **Custom Images**: Stored under `events/{userId}/` prefix via presigned URLs

#### IAM Roles and Policies
- **EC2 Instance Profile**: `aws-elasticbeanstalk-ec2-role`
- **Service Role**: `aws-elasticbeanstalk-service-role`
- **Managed Policies**:
  - `AWSElasticBeanstalkWebTier`
  - `AWSElasticBeanstalkWorkerTier`
  - `AWSElasticBeanstalkMulticontainerDocker`
  - `AmazonDynamoDBFullAccess`
  - `AmazonDynamoDBFullAccess_v2`
  - `AmazonS3ReadOnlyAccess`
- **Custom Inline Policy**: `S3PutObject` allows `s3:PutObject` and `s3:PutObjectAcl` on new bucket

#### AWS CLI Configuration
- **Profile Name**: `inviter`
- **Usage**: `AWS_PROFILE=inviter` or `aws --profile inviter`
- **EB CLI**: Uses profile configured in `.elasticbeanstalk/config.yml`

### Development Workflow
1. **Local Development**: Uses default AWS credentials
2. **Production Deployment**: Use `AWS_PROFILE=inviter eb deploy` to deploy to primary production environment

### Legacy Account Status
- **Old Account**: Mixed-use account (terminated)
- **Environment**: `inviter-prod-22` (terminated to save costs)
- **Status**: Environment terminated, application version and configuration preserved for emergency restoration if needed