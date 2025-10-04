# Hangouts API Backend

This repository contains the backend API for **Hangouts**, a social platform designed to make organizing events and hangouts with friends seamless and fun. The API is built with a focus on modern, scalable, and maintainable practices.

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
1. Builds the Java application.
2. Runs all unit tests.
3. Deploys the application to a **staging** environment on AWS Elastic Beanstalk.
4. Runs a health check against the staging environment.
5. If staging is successful, it promotes the build and deploys it to the **production** environment.

## AI Agent Prompt Generation
The goal is to get the agent the correct context, which means getting it enough context without getting it *too much* context. 

### Anything that reads/writes to the database
Claude LOVES to add a full table scan or to make 3 database calls when 1 would do, even after it's parrotted back your exact plan on how to use a GSI correctly.  Any agent reading or writing to the database MUST read ./DYNAMODB_DESIGN_GUIDE.md . Have Gemini read that file and PROJECT_CONTEXT_DEEP_DIVE.md and do a code review of the code (still be extra vigilant with database read/write changes, Gemini misses things too sometimes).

### Context files
The /context directory has a document for each type of feature (Events, carpooling, adding a new attribute, etc). Include in your prompt the context file(s) for the piece(s) you're updating, these files contain everything the agent needs to know to be able to modify that feature. Once you're done, get the agent to update the context file. If you're creating something new, get it to create a succinct context file with everything future agents will need to know to do development, and they can look at an existing one as an example.

### Commit ids
Including a commit ID in a prompt is very helpful if you want to add onto a feature that was added in  a particular commit, or if you know there was a regression.

### Getting good unit tests
In order to get unit tests that actually test the logic and not just implementation, the unit tests need input from the agent creating the feature. However unit test creation generates SO MUCH extra unnecessary context, so get the main agent to create a detailed plan and then delegate to another agent.

Example prompt which has worked consistently:

>Write a unit test plan for this change, I will have another instance of Claude write the actual unit tests in order to preserve your context.  Write the plan for what needs to be tested and make sure to detail *what* each test is looking for; we don't want unit tests that just reinforce what the code already does, we ideally don't want tests that will fail when we make small implementation changes that don't change functionality, we want unit tests that verify that the methods do the thing they're intended to do.
>
>Include the methods you wrote (from your context, don't re read the file) in the test plan so that the implementor doesn't have to read the files, and add anything else that's in your context that the implementor might need.  Don't include target test coverage percentages. Do not add tests that rely on dynamoDB local.
>
>If the test plan is large, more than 15 tests, probably split it into more than one test plan. The plans will be exected in parallel so make sure that there's no overlap in which test files are being modified between the plans, and make sure each test plan has all the context it needs.