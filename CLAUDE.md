# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Inviter** is a Spring Boot 3.5.3 REST API for event management with phone number invitations. Events are stored in-memory using HashMap (data is lost on restart).

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
- **Pattern**: Simple MVC with Controller → Model
- **Storage**: In-memory HashMap (no database)
- **API**: RESTful JSON endpoints under `/events`

### Key Components

**Event Model** (`model/Event.java`)
- Uses Lombok for boilerplate reduction
- UUID auto-generated in constructor
- Fields: id, name, description, startTime, endTime (optional), invitePhoneNumbers

**Event Controller** (`controller/EventController.java`)
- Stores events in `Map<UUID, Event> eventStore`
- Three endpoints: POST `/events/new`, GET `/events`, GET `/events/{id}`

### API Endpoints

| Method | URL | Purpose | Response |
|--------|-----|---------|----------|
| POST | `/events/new` | Create event | `{"id": "uuid"}` |
| GET | `/events` | List all events | Array of events |
| GET | `/events/{id}` | Get specific event | Event object or 404 |

### Request Format
```json
{
  "name": "Event Name",
  "description": "Event Description", 
  "startTime": "2024-01-15T10:00:00",
  "endTime": "2024-01-15T11:00:00",
  "invitePhoneNumbers": ["+1234567890", "+0987654321"]
}
```

## Development Notes

- **Testing**: Uses JUnit 5, currently minimal test coverage
- **Configuration**: Minimal - only application name set in `application.properties`
- **Port**: Default 8080
- **Data Persistence**: None - all data lost on restart
- **Dependencies**: Spring Web, Lombok, JUnit 5