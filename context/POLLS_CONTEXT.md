# Context: Polls

**AUDIENCE:** This document is for developers and AI agents working on the polling feature. It assumes familiarity with the `DYNAMODB_DESIGN_GUIDE.md` and `HANGOUT_CRUD_CONTEXT.md`.

## 1. Overview

Polls are a feature within a Hangout that allow group members to vote on options. The entire data model for polls is self-contained within a Hangout's item collection, making it a prime example of the single-table design pattern.

All poll-related entities share the same partition key as their parent hangout (`PK=EVENT#{hangoutId}`) but have distinct sort keys, allowing for efficient, targeted queries.

## 2. Data Model & Key Structure

| Entity | Sort Key (SK) Structure | Purpose |
| :--- | :--- | :--- |
| `Poll` | `POLL#{pollId}` | The canonical record for the poll itself, containing the title and settings. |
| `PollOption` | `POLL#{pollId}#OPTION#{optionId}` | A single option that users can vote for within a poll. |
| `Vote` | `POLL#{pollId}#VOTE#{userId}#OPTION#{optionId}` | Records a single user's vote for a specific option. The composite SK ensures each user can only vote once per option. |

This hierarchical key structure is powerful because it allows fetching all data for a single poll with one query using a `begins_with` condition on the sort key.

## 3. Key Files & Classes

| File | Purpose |
| :--- | :--- |
| `PollController.java` | Exposes REST endpoints for poll operations, nested under `/hangouts/{eventId}/polls`. |
| `PollServiceImpl.java` | Implements the business logic for creating polls, voting, and calculating results. |
| `HangoutRepositoryImpl.java` | Contains the methods (`savePoll`, `savePollOption`, `saveVote`, `getSpecificPollData`) that interact with DynamoDB for poll entities. |
| `Poll.java` | The `@DynamoDbBean` for the poll record. |
| `PollOption.java` | The `@DynamoDbBean` for a poll option record. |
| `Vote.java` | The `@DynamoDbBean` for a user's vote record. |
| `PollWithOptionsDTO.java` | A DTO used to return poll data with calculated vote counts. |

## 4. Core Flows

### Create a Poll

1.  **Endpoint:** `POST /hangouts/{eventId}/polls`
2.  **Controller:** `PollController.createPoll()`
3.  **Service:** `PollServiceImpl.createPoll()`:
    *   Creates a `Poll` entity.
    *   Iterates through the list of option strings in the request and creates a `PollOption` entity for each one.
4.  **Repository:** `HangoutRepositoryImpl` is called multiple times to save the `Poll` and each `PollOption` individually.

### Vote on a Poll

1.  **Endpoint:** `POST /hangouts/{eventId}/polls/{pollId}/vote`
2.  **Controller:** `PollController.voteOnPoll()`
3.  **Service:** `PollServiceImpl.voteOnPoll()`:
    *   First, it fetches all data for the poll to check its settings (e.g., `multipleChoice`).
    *   If the poll is single-choice, it removes the user's previous vote (if any) before creating the new one.
    *   It then creates a `Vote` entity with the composite sort key.
4.  **Repository:** `HangoutRepositoryImpl.saveVote()` saves the new `Vote` record.

### Get Poll Results

This is the most critical flow to understand.

1.  **Endpoint:** `GET /hangouts/{eventId}/polls/{pollId}`
2.  **Controller:** `PollController.getPoll()`
3.  **Service:** `PollServiceImpl.getPollDetail()`
4.  **Repository:** `HangoutRepositoryImpl.getSpecificPollData()`:
    *   Executes a single DynamoDB **Query**.
    *   `PK` = `EVENT#{eventId}`
    *   `SK` = `begins_with(POLL#{pollId})`
    *   This one query efficiently retrieves the `Poll` record, all of its `PollOption` records, and all of its `Vote` records.
5.  **Runtime Calculation:** Back in `PollServiceImpl`, the `transformToPollDetailDTO` method receives this list of items. It then iterates through the `Vote` records in application memory to calculate the total votes for each option before building the final DTO to send to the client. **Vote counts are not stored or updated in DynamoDB.**
