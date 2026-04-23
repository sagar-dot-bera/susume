---
description: Implement interaction recording and retrieval. Covers the POST /interactions endpoint for logging user actions (VIEW, CLICK, LIKE, PURCHASE) and GET /interactions/{userId} for history retrieval.
---

# Workflow: Interaction Management

## Epics Covered
- E8: Interaction Recording
- E9: Interaction History Retrieval

## User Stories

### US-10: Record Interaction
**As a** tenant client application,
**I want to** record when a user interacts with an item,
**So that** the recommendation engine can learn from user behavior.

**Acceptance Criteria:**
- `POST /api/v1/interactions` (requires `X-API-KEY`)
- Request body:
  ```json
  {
    "externalUserId": "string (required)",
    "externalItemId": "string (required)",
    "interactionType": "VIEW | CLICK | LIKE | PURCHASE (required)",
    "timestamp": "ISO8601 UTC (optional, defaults to server time)"
  }
  ```
- `externalItemId` must reference an `ACTIVE` item within the tenant → HTTP 404 if not found or INACTIVE
- `interactionType` must be one of the 4 valid values → HTTP 400 if unknown
- Multiple interactions of the same type by the same user on the same item are allowed (cumulative behavior expected)
- No user profile is created — `externalUserId` is stored as an opaque string
- Response HTTP 201: `{ id, externalUserId, externalItemId, interactionType, timestamp }`
- Write latency target: < 100ms

---

### US-11: Retrieve User Interaction History
**As a** tenant client or dashboard user,
**I want to** retrieve the interaction history for a specific user,
**So that** I can audit behavior or debug recommendation issues.

**Acceptance Criteria:**
- `GET /api/v1/interactions/{externalUserId}` (requires `X-API-KEY`)
- Returns all interactions for `externalUserId` within the tenant — strictly tenant-scoped
- Cursor-based pagination: `?limit=50&cursor=<opaque>`
- Optional filter: `?interactionType=PURCHASE`, `?since=ISO8601`
- Response: `{ data: [{ id, externalItemId, interactionType, timestamp }], nextCursor, limit }`
- Returns empty data array (not 404) if user has no interactions

---

## Implementation Tasks

### Backend (Spring Boot)

1. **`Interaction` entity**
   ```java
   @Entity
   @Table(name = "interactions")
   public class Interaction {
       @Id UUID id;
       UUID tenantId;
       String externalUserId;
       String externalItemId;
       @Enumerated(EnumType.STRING) InteractionType interactionType;
       Instant timestamp;
   }
   ```

2. **`InteractionType` enum**
   ```java
   public enum InteractionType {
       VIEW, CLICK, LIKE, PURCHASE;

       public int getWeight() {
           return switch (this) {
               case VIEW     -> 1;
               case CLICK    -> 2;
               case LIKE     -> 3;
               case PURCHASE -> 5;
           };
       }
   }
   ```

3. **`InteractionRepository extends JpaRepository`**
   - `findByTenantIdAndExternalUserId(UUID tenantId, String userId, Pageable p)`
   - `findByTenantIdAndExternalUserId(UUID tenantId, String userId)` — for recommendation engine (no pagination)
   - `countByTenantIdAndExternalItemIdAndTimestampAfter(UUID tenantId, String itemId, Instant since)` — for trending

4. **`InteractionService`**
   - `recordInteraction(tenantId, dto)`:
     - Validate `externalItemId` exists and is ACTIVE in tenant
     - Validate `interactionType` enum
     - Set timestamp to now if not provided
     - Save and return
   - `getUserInteractions(tenantId, externalUserId, cursor, limit, filters)` → paginated

5. **`InteractionController`** — wire endpoints

---

## Interaction Weight Reference

| Type     | Weight | Signal Strength |
|----------|--------|-----------------|
| VIEW     | 1      | Passive / weak  |
| CLICK    | 2      | Moderate        |
| LIKE     | 3      | Strong explicit |
| PURCHASE | 5      | Highest intent  |

These weights are consumed by the recommendation engine during user vector computation (see `recommendation-engine.md`).

---

## Data Isolation Checklist
- [ ] All interaction queries include `WHERE tenant_id = :tenantId`
- [ ] `externalUserId` treated as opaque string — no user profile lookup
- [ ] Empty interaction history returns `[]`, not 404
- [ ] Interactions for soft-deleted items are preserved (not cascaded)
