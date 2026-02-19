# Username Cache Context

## Overview
User display names and profile images are cached in-memory using Spring Cache with Caffeine to reduce DynamoDB read operations. Whenever a service needs a user's display name or profile image (e.g., for denormalizing hangout participations, group members, notifications), it calls `UserService.getUserSummary()` which returns a cached `UserSummaryDTO` instead of fetching the full `User` entity from DynamoDB.

## Architecture

```
Service code calls UserService.getUserSummary(userId)
        │
        ▼
  @Cacheable check ─── cache HIT ──► return cached UserSummaryDTO
        │
     cache MISS
        │
        ▼
  UserRepository.findById(userId) ──► map to UserSummaryDTO ──► cache & return
```

## Key Files

| File | Purpose |
|------|---------|
| `config/CacheConfig.java` | Caffeine cache manager configuration |
| `dto/UserSummaryDTO.java` | Lightweight cached DTO (id, displayName, mainImagePath) |
| `service/UserService.java` | `getUserSummary()` with `@Cacheable`, eviction on `updateProfile()` and `deleteUser()` |

## Cache Configuration

**Cache name**: `friendlyNames`
**Provider**: Caffeine (in-memory, no external infrastructure)
**TTL**: 60 minutes (`expireAfterWrite`)
**Max entries**: 10,000 (~5MB memory footprint)
**Stats**: Enabled for monitoring via Spring Boot Actuator

Configuration lives in `CacheConfig.java`:
```java
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("friendlyNames");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.MINUTES)
                .maximumSize(10000)
                .recordStats());
        return cacheManager;
    }
}
```

## UserSummaryDTO

A lightweight DTO containing only the fields needed for UI display. Using this instead of the full `User` entity avoids caching sensitive data (password hash, phone number, etc.).

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryDTO {
    private UUID id;
    private String displayName;
    private String mainImagePath;
}
```

## How to Use in Service Code

### Reading user display info (use `getUserSummary`, NOT `getUserById`)

When you need a user's display name or profile image, always use `getUserSummary()`:

```java
// CORRECT - uses cache
UserSummaryDTO user = userService.getUserSummary(UUID.fromString(userId))
    .orElse(null);
String displayName = user != null ? user.getDisplayName() : "Unknown User";
String imagePath = user != null ? user.getMainImagePath() : null;

// WRONG - bypasses cache, fetches full User from DynamoDB every time
User user = userService.getUserById(UUID.fromString(userId)).orElse(null);
```

Only use `getUserById()` when you need the full `User` entity (e.g., for authentication, password changes, or updating fields not in `UserSummaryDTO`).

### Injecting UserService

If your service doesn't already have `UserService`, inject it:

```java
@Service
public class MyServiceImpl implements MyService {
    private final UserService userService;

    @Autowired
    public MyServiceImpl(UserService userService) {
        this.userService = userService;
    }
}
```

## Cache Eviction

The cache is automatically evicted in two scenarios via `@CacheEvict` annotations on `UserService`:

| Method | Trigger | Why |
|--------|---------|-----|
| `updateProfile()` | User changes display name or profile image | Cached data is now stale |
| `deleteUser()` | User account is deleted | User no longer exists |

Both use `@CacheEvict(value = "friendlyNames", key = "#userId.toString()")` to invalidate the specific user's entry.

The 60-minute TTL provides a safety net for any edge cases where eviction doesn't fire (e.g., direct DB edits).

## Where the Cache Is Currently Used

### HangoutServiceImpl
- **Participation DTOs**: Denormalizes display name and image onto `ParticipationDTO`
- **Reservation Offer DTOs**: Denormalizes display name and image onto `ReservationOfferDTO`
- **Interest Levels**: Resolves user display name when setting interest
- **Creator Display Name**: Resolves hangout creator name for notifications
- **Host-at-Place**: Resolves host user display name

### GroupServiceImpl
- **Member List**: Resolves display name and image for group membership listings

### NotificationServiceImpl
- **Push Notifications**: Resolves adder display name for group-add notifications

## Unit Testing

In unit tests, mock `userService.getUserSummary()` to return a `UserSummaryDTO`:

```java
// Setup
UserSummaryDTO user = new UserSummaryDTO();
user.setId(UUID.fromString(userId));
user.setDisplayName("Alice");
user.setMainImagePath("alice.jpg");
when(userService.getUserSummary(UUID.fromString(userId))).thenReturn(Optional.of(user));

// For user-not-found scenarios
when(userService.getUserSummary(UUID.fromString(userId))).thenReturn(Optional.empty());
```

The test base class `HangoutServiceTestBase` has a helper:
```java
protected UserSummaryDTO createTestUser(String userId) {
    UserSummaryDTO user = new UserSummaryDTO();
    user.setDisplayName("John Doe");
    return user;
}
```

Note: `createTestUser` does not set `id` — set it explicitly if your test needs `user.getId()`.

## Adding a New Cache

If you need to cache something other than user summaries:

1. Add the cache name to `CacheConfig.cacheManager()`:
   ```java
   new CaffeineCacheManager("friendlyNames", "myNewCache");
   ```
2. Add `@Cacheable(value = "myNewCache", key = "...")` to the method
3. Add `@CacheEvict(value = "myNewCache", key = "...")` to any methods that mutate the cached data

## Dependency

Caffeine is pulled in via:
```gradle
implementation 'org.springframework:spring-context-support'
```
Caffeine itself is a transitive dependency via Spring Boot's cache starter.

## Important Notes

- **Spring proxy limitation**: `@Cacheable` only works on calls from outside the class. A method in `UserService` calling `this.getUserSummary()` will bypass the cache. This is a standard Spring AOP proxy limitation.
- **No distributed cache**: This is in-memory only. Each application instance has its own cache. If running multiple instances, a user profile update on one instance won't evict the cache on another. The 60-minute TTL limits staleness.
- **Optional caching**: The cache stores `Optional<UserSummaryDTO>`. Both present and empty results are cached, so a non-existent user ID won't repeatedly hit DynamoDB.
