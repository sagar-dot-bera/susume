---
description: Write and run tests for the recommendation engine project. Covers unit tests for core services, integration tests for API endpoints, and end-to-end flow validation. Run this after implementing any workflow.
---

# Workflow: Testing

## Epics Covered
- E14: Unit Testing
- E15: Integration Testing
- E16: End-to-End Flow Validation

---

## Unit Tests

### 1. `VectorUtilsTest`
Test the core math utilities in isolation.

```java
@Test void weightedAverageWithSingleEmbedding() { ... }
@Test void weightedAverageWeightsHigherInteractionsMore() { ... }
@Test void cosineSimilarityOfIdenticalVectorsIsOne() { ... }
@Test void cosineSimilarityOfOrthogonalVectorsIsZero() { ... }
@Test void cosineSimilarityHandlesZeroVector() { ... }
```

### 2. `RecommendationServiceTest`
Mock `ItemRepository` and `InteractionRepository`.

```java
@Test void returnsPersonalizedResultsWhenInteractionsExist() { ... }
@Test void fallsBackToTrendingWhenUserHasNoInteractions() { ... }
@Test void excludesAlreadyInteractedItems() { ... }
@Test void excludesInactiveItems() { ... }
@Test void respectsLimitParameter() { ... }
@Test void highWeightInteractionsDominateUserVector() { ... }
```

### 3. `ItemServiceTest`
Mock `ItemRepository` and `EmbeddingServiceClient`.

```java
@Test void createItemCallsEmbeddingService() { ... }
@Test void createItemThrows409OnDuplicateExternalId() { ... }
@Test void updateItemRegeneratesEmbedding() { ... }
@Test void deleteItemSetsSoftDeleteStatus() { ... }
```

### 4. `ApiKeyFilterTest`
Test middleware in isolation.

```java
@Test void missingApiKeyReturns401() { ... }
@Test void invalidApiKeyReturns401() { ... }
@Test void validApiKeySetsSecurityContext() { ... }
@Test void apiKeyNeverAppearsInLogs() { ... }
```

### 5. `MetadataConcatenatorTest`

```java
@Test void concatenatesAllStringFields() { ... }
@Test void skipsNullAndEmptyFields() { ... }
@Test void convertsNumericFieldsToString() { ... }
@Test void truncatesLongInputToLimit() { ... }
```

---

## Integration Tests

Use `@SpringBootTest` with `TestContainers` (PostgreSQL).

### 1. Item Lifecycle Integration Test
```
POST /items → 201
GET /items → item appears in list
PUT /items/{id} → 200, embedding regenerated
DELETE /items/{id} → 200, status=INACTIVE
GET /items → item no longer in list
```

### 2. Interaction Recording Integration Test
```
POST /interactions with valid item → 201
POST /interactions with INACTIVE item → 404
POST /interactions with unknown interactionType → 400
GET /interactions/{userId} → returns recorded interactions
```

### 3. Recommendation Flow Integration Test
```
# Setup: create 5 items, record interactions for user
POST /items ×5
POST /interactions (user "u1" → item1 LIKE, item2 VIEW, item3 PURCHASE)

# Get recommendations
GET /recommendations/u1?limit=3
→ results do NOT include item1, item2, item3 (already interacted)
→ results include item4, item5 (candidates)
→ similarity scores are between 0 and 1
→ strategy = "personalized"
```

### 4. Cold-Start Integration Test
```
GET /recommendations/newuser_no_history?limit=5
→ strategy = "trending"
→ returns up to 5 items
→ all items are ACTIVE
```

### 5. Tenant Isolation Integration Test
```
# Register two tenants
POST /tenants/register → tenantA apiKey
POST /tenants/register → tenantB apiKey

# Create items for each tenant
POST /items (tenantA key) → itemA
POST /items (tenantB key) → itemB

# Verify isolation
GET /items (tenantA key) → only itemA visible
GET /items (tenantB key) → only itemB visible
GET /recommendations/user1 (tenantA key) → itemB never appears
```

---

## Running Tests

```bash
# Unit tests only
./mvnw test -Dtest="*Test"

# Integration tests (requires Docker for TestContainers)
./mvnw test -Dtest="*IntegrationTest"

# Full suite
./mvnw verify

# Embedding service tests
cd embedding-service && pytest tests/ -v
```

---

## Coverage Targets
- [ ] Service layer (RecommendationService, ItemService, InteractionService, AuthService): >= 70%
- [ ] VectorUtils: 100%
- [ ] Filter/middleware classes: >= 80%
- [ ] Integration tests cover all 4 primary flows (item ingestion, interaction, recommendation, auth)
- [ ] Tenant isolation verified in integration tests
