# Inviter API Backend

[![Java CI with Gradle](https://github.com/your-username/your-repo/actions/workflows/backend-ci-cd.yml/badge.svg)](https://github.com/your-username/your-repo/actions/workflows/backend-ci-cd.yml)

This repository contains the backend API for **Inviter**, a social platform designed to make organizing events and hangouts with friends seamless and fun. The API is built with a focus on modern, scalable, and maintainable practices.

## Core Features

*   **Dynamic Hangout Management**: Create, update, and manage complex events with rich details.
*   **Group System**: Organize users into groups for easy and reusable invitations.
*   **Advanced Time Selection**: Supports both exact times (e.g., "7:30 PM") and "fuzzy" times (e.g., "Saturday evening").
*   **Interactive Polls**: Attach polls to hangouts to let attendees vote on activities, food, or timing.
*   **Carpool Coordination**: Integrated tools for drivers to offer rides and attendees to claim available seats.
*   **Real-time Interest Tracking**: See who is "Going" or "Interested" in an event, with live participant counts.
*   **Custom Attributes**: Define custom key-value attributes for any hangout (e.g., "Vibe: Chill").

## Tech Stack & Architecture

This project is built on a modern, production-ready Java stack.

### Core Technologies
*   **Framework**: Spring Boot 3
*   **Language**: Java 21
*   **Security**: Spring Security with JWT for stateless authentication
*   **Database**: AWS DynamoDB
*   **Build Tool**: Gradle
*   **Deployment**: AWS Elastic Beanstalk, S3, and API Gateway
*   **CI/CD**: GitHub Actions

### Architecture
The API follows a classic three-tier architecture (`Controller` → `Service` → `Repository`) to ensure a clean separation of concerns.

A key feature of the architecture is its use of **advanced AWS DynamoDB single-table design**. It leverages a "pointer record" pattern to denormalize data, allowing for highly efficient queries at scale by avoiding expensive table scans and complex joins. This makes the application fast and cost-effective.

## Getting Started

### Prerequisites
*   JDK 21 (AWS Corretto 21 is recommended)
*   An AWS account with credentials configured (for DynamoDB interaction)

### Running Locally
1.  Clone the repository:
    ```bash
    git clone https://github.com/your-username/your-repo.git
    cd hangoutsBackend
    ```
2.  Run the application using the Gradle wrapper:
    ```bash
    ./gradlew bootRun
    ```
The API will be available at `http://localhost:8080`.

### API Documentation
A full list of endpoints is available via the integrated Swagger UI once the application is running:
**http://localhost:8080/swagger-ui.html**

## Testing

This project maintains a high standard of quality with a comprehensive and rigorous test suite.

*   **To run unit tests:**
    ```bash
    ./gradlew test
    ```
*   **To run integration tests** (requires Docker for Testcontainers):
    ```bash
    ./gradlew integrationTest
    ```

## CI/CD Pipeline

The repository is configured with a full CI/CD pipeline using GitHub Actions (`.github/workflows/backend-ci-cd.yml`). On every push to the `main` branch, the pipeline automatically:
1.  Builds the Java application.
2.  Runs all unit tests.
3.  Deploys the application to a **staging** environment on AWS Elastic Beanstalk.
4.  Runs a health check against the staging environment.
5.  If staging is successful, it promotes the build and deploys it to the **production** environment.
