# Invite Codes Context

**Feature:** Group invite codes for Universal Links and shareable join URLs
**Last Updated:** 2025-11-13

---

## Overview

Invite codes allow group members to generate shareable links that let others join the group without manual invitation. Each code is a separate canonical entity with full audit trail and usage tracking.

---

## Database Design

### InviteCode Entity

**Canonical Record:**
```
PK: INVITE_CODE#{inviteCodeId}
SK: METADATA
```

**GSI Keys:**
- **InviteCodeIndex** (`gsi3pk`/`gsi3sk`): Lookup by code string
  - `gsi3pk = CODE#{code}`
  - `gsi3sk = METADATA`
- **UserGroupIndex** (`gsi1pk`/`gsi1sk`): List codes for a group
  - `gsi1pk = GROUP#{groupId}`
  - `gsi1sk = CREATED#{createdAt}`

### Key Fields

```java
private String inviteCodeId;        // UUID
private String code;                // 8-char lowercase alphanumeric
private String groupId;             // Which group
private String groupName;           // Denormalized
private String createdBy;           // userId who generated
private Instant createdAt;
private boolean isActive;           // Can be used?
private boolean isSingleUse;        // Auto-deactivate after first use
private Instant expiresAt;          // Optional expiration
private String deactivatedBy;       // Who disabled it
private Instant deactivatedAt;
private String deactivationReason;
private List<String> usages;        // User IDs who joined via this code
```

---

## Repository Layer

### InviteCodeRepository

**Key Methods:**
```java
Optional<InviteCode> findByCode(String code)           // Lookup via InviteCodeIndex GSI
Optional<InviteCode> findById(String inviteCodeId)     // Direct GetItem
List<InviteCode> findAllByGroupId(String groupId)      // Query UserGroupIndex GSI
Optional<InviteCode> findActiveCodeForGroup(String groupId) // For idempotent generation
boolean codeExists(String code)                        // Collision detection
void save(InviteCode inviteCode)
void delete(String inviteCodeId)
```

**Implementation Notes:**
- Uses DynamoDB Enhanced Client with `TableSchema.fromBean(InviteCode.class)`
- `findActiveCodeForGroup()` queries all codes for group, filters for `isUsable()`
- All operations tracked via `QueryPerformanceTracker`

---

## Service Layer

### GroupServiceImpl Methods

#### generateInviteCode()
**Behavior:** Idempotent - returns existing active code if one exists

```java
1. Verify group exists and user is member
2. Check for existing active code via findActiveCodeForGroup()
3. If exists: return existing code
4. If not: generate unique 8-char code, create InviteCode entity, save
5. Return InviteCodeResponse with code and shareable URL
```

**Authorization:** Any group member can generate

#### getGroupPreviewByInviteCode()
**Behavior:** Public endpoint for Universal Link preview (unauthenticated)

```java
1. Rate limit check (60/hour per IP, 100/hour per code)
2. Find code via findByCode()
3. Get group from code.groupId
4. Return GroupPreviewDTO (full info if public, just isPrivate flag if private)
```

**Rate Limiting:** Dual limits prevent brute force scanning

#### joinGroupByInviteCode()
**Behavior:** Authenticated user joins group via code

```java
1. Find code via findByCode()
2. Check code.isUsable() (active && not expired)
3. Get group from code.groupId
4. If already member: return existing membership (idempotent)
5. Create GroupMembership with MEMBER role
6. Record usage: code.recordUsage(userId, username)
7. Save updated code (auto-deactivates if single-use)
8. Return GroupDTO
```

---

## Business Rules

### Code Generation
- **Format:** 8 lowercase alphanumeric characters (36^8 = 2.8T combinations)
- **Uniqueness:** `InviteCodeGenerator.generateUnique()` with collision checking
- **Idempotent:** Reuses existing active code for a group
- **Who Can Generate:** Any group member

### Code Validation
```java
public boolean isUsable() {
    if (!isActive) return false;
    if (expiresAt != null && Instant.now().isAfter(expiresAt)) return false;
    return true;
}
```

### Single-Use Codes
```java
if (isSingleUse && usages.size() >= 1) {
    this.isActive = false;
    this.deactivationReason = "Single-use code exhausted";
}
```
Auto-deactivates in `recordUsage()` after first join.

### Manual Deactivation
```java
code.deactivate(userId, "Code leaked on social media");
// Sets isActive=false, records who/when/why
```
Old codes preserved for audit, new code generated on next request.

---

## API Endpoints

### POST `/groups/{groupId}/invite-code`
Generate or retrieve invite code (idempotent)

**Auth:** JWT - group member
**Request:** Empty or optional `{isSingleUse: true, expiresAt: "2025-12-31..."}`
**Response:**
```json
{
  "inviteCode": "abc123xy",
  "shareUrl": "https://d1713f2ygzp5es.cloudfront.net/join-group/abc123xy"
}
```

### GET `/groups/invite/{inviteCode}`
Preview group before joining (public, rate limited)

**Auth:** None
**Rate Limits:** 60/hour per IP, 100/hour per code
**Response:**
```json
// Public group:
{"isPrivate": false, "groupName": "Hiking Buddies", "mainImagePath": "..."}

// Private group:
{"isPrivate": true}
```

### POST `/groups/invite/join`
Join group via invite code

**Auth:** JWT
**Request:** `{inviteCode: "abc123xy"}`
**Response:** Full `GroupDTO` with user's membership

---

## Rate Limiting

### Implementation (RateLimitingService)
```java
public boolean isInvitePreviewAllowed(String ipAddress, String inviteCode)
```

**Limits:**
- 60 requests/hour per IP (prevents individual scanning)
- 100 requests/hour per code (prevents targeted attacks)

**Storage:** Caffeine in-memory cache with 1-hour expiration

**IP Extraction:**
```java
String xff = request.getHeader("X-Forwarded-For");
String ip = (xff != null) ? xff.split(",")[0].trim() : request.getRemoteAddr();
```

---

## Key Utilities

### InviteCodeGenerator
```java
public static String generate()  // Random 8-char lowercase alphanumeric
public static String generateUnique(Predicate<String> existsChecker)
```

### InviterKeyFactory
```java
getInviteCodePk(inviteCodeId)     // "INVITE_CODE#{id}"
getCodeLookupGsi3pk(code)         // "CODE#{code}"
getCreatedSk(createdAt)           // "CREATED#{timestamp}"
```

---

## Common Patterns

### Checking if Code Exists (Before Creating)
```java
Optional<InviteCode> existing = inviteCodeRepository.findActiveCodeForGroup(groupId);
if (existing.isPresent()) {
    return existing.get(); // Reuse
}
```

### Recording Usage
```java
code.recordUsage(userId);
inviteCodeRepository.save(code);  // Persists usage + auto-deactivates if single-use
```

### Querying All Codes for a Group
```java
List<InviteCode> codes = inviteCodeRepository.findAllByGroupId(groupId);
// Returns all codes (active and inactive) sorted by creation time
```

---

## Future Extensibility

The separate entity design enables:

### Analytics
- Track conversion rates (previews vs joins)
- Most effective codes per group
- Usage over time

### Campaign-Specific Codes
```java
POST /groups/{groupId}/invite-codes
{
  "campaign": "summer-2025",
  "maxUses": 100,
  "expiresAt": "2025-09-01T00:00:00Z"
}
```

### Code Management Dashboard
```java
GET /groups/{groupId}/invite-codes  // List all codes
POST /groups/{groupId}/invite-codes/{codeId}/deactivate
```

### Role-Specific Codes
```java
private GroupRole assignedRole; // Generate codes that assign specific role on join
```

---

## Testing Considerations

### Unit Tests
- `InviteCode.isUsable()` - active, expired, deactivated states
- `InviteCode.recordUsage()` - single-use auto-deactivation
- `InviteCodeGenerator.generateUnique()` - collision handling

### Integration Tests
- Idempotent generation (multiple calls return same code)
- Rate limiting triggers correctly
- Single-use enforcement
- Join flow end-to-end

### Edge Cases
- Concurrent joins via single-use code (last-write-wins acceptable)
- Expired code rejection
- Deactivated code rejection
- Already-member idempotency

---

## Related Documentation

- **Database Architecture:** `DYNAMODB_DESIGN_GUIDE.md` (canonical vs pointer records)
- **API Documentation:** `API_DOCUMENTATION.md` (endpoint specs)
- **Group Management:** `context/GROUP_CRUD_CONTEXT.md` (membership patterns)
