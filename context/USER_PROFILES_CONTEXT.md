# Context: User Profiles

**AUDIENCE:** This document is for developers and AI agents working on user profile management. It assumes familiarity with the `AUTHENTICATION_CONTEXT.md`.

## 1. Overview

The User Profile feature allows authenticated users to view and manage their own account information. Unlike other features, it does not have its own dedicated service or repository. Instead, it acts as a frontend for the existing `UserService` and the `User` data model, which is stored in the `Users` DynamoDB table.

All endpoints require a valid JWT access token for authentication.

## 2. Key Files & Classes

| File | Purpose |
| :--- | :--- |
| `ProfileController.java` | Exposes REST endpoints under the `/profile` route for managing the current user's account. |
| `UserService.java` | The service layer that contains the business logic for updating display names, changing passwords, and deleting users. |
| `UserRepository.java` | The repository responsible for all database interactions with the `Users` table. |
| `User.java` | The `@DynamoDbBean` for a user record. The profile is a view over this model. |
| `UpdateProfileRequest.java` | DTO for updating a user's display name. |
| `ChangePasswordRequest.java` | DTO for the password change flow, requiring the current and new password. |

## 3. Core Flows

### Get Profile

1.  **Endpoint:** `GET /profile`
2.  **Authentication:** The `JwtAuthenticationFilter` validates the user's access token.
3.  **Controller:** `ProfileController.getProfile()` retrieves the `userId` from the request attribute (placed there by the JWT filter).
4.  **Service:** It calls `UserService.getUserById()` to fetch the full `User` object from the `Users` table.
5.  **Response:** The controller nulls out the `password` field on the `User` object before returning it to the client to prevent exposing the password hash.

### Update Profile

1.  **Endpoint:** `PUT /profile`
2.  **Controller:** `ProfileController.updateProfile()` receives an `UpdateProfileRequest`.
3.  **Service:** It calls `UserService.updateDisplayName()`, which finds the user, updates the `displayName` field, and saves the `User` object back to the database.

### Change Password

1.  **Endpoint:** `PUT /profile/password`
2.  **Controller:** `ProfileController.changePassword()` receives a `ChangePasswordRequest`.
3.  **Service:** `UserService.changePassword()`:
    *   Verifies the `currentPassword` against the stored password hash.
    *   If valid, it encrypts the `newPassword` and updates the `password` field on the `User` object.

### Delete Account

1.  **Endpoint:** `DELETE /profile`
2.  **Controller:** `ProfileController.deleteProfile()`
3.  **Service:** `UserService.deleteUser()` performs a cascading delete:
    *   It finds and deletes all of the user's registered devices from the `Devices` table.
    *   It finds all events where the user is the *sole host* and deletes those entire events.
    *   It deletes all of the user's `Invite` records.
    *   Finally, it deletes the `User` record itself.
