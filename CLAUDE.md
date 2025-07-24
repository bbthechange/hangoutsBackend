# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Inviter** is a Spring Boot 3.5.3 REST API for event management with DynamoDB backend. Features JWT-based authentication, user management, event creation, and phone number invitation system.

## Development Commands

```bash
# Run the application (requires Java 17+)
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

**Important**: The project requires Java 17+ due to Spring Boot 3.5.3. If build fails with Java version error, either upgrade Java or downgrade Spring Boot version in `build.gradle`.

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

#### Images (`/images`) - Public
| Method | URL | Purpose | Response |
|--------|-----|---------|----------|
| GET | `/images/predefined` | Get predefined images | Array of image options |

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
- **Tables**: Events, Users, Invites (plural names, created automatically on app startup via `DynamoDBTableInitializer`)

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