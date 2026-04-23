---
description: Implement the full item management lifecycle — create, update, soft-delete, and list items. Includes integration with the embedding service and tenant-scoped data isolation.
---

# Workflow: Item Management

## Epics Covered
- E6: Item CRUD
- E7: Embedding Service Integration

## User Stories

### US-06: Create Item
**As a** tenant client application,
**I want to** submit a new item with metadata,
**So that** it becomes eligible for recommendations after embedding is generated.

**Acceptance Criteria:**
- `POST /api/v1/items` (requires `X-API-KEY`)
- Request body: `{ externalItemId: string, metadata: object }`
- `externalItemId` must be unique within the tenant — HTTP 409 if duplicate
- `metadata` must contain at least one non-empty string field — HTTP 400 otherwise
- System calls embedding service synchronously: concatenates metadata values, POSTs to `{EMBEDDING_SERVICE_URL}/embed`
- Item stored with `status=ACTIVE`, `embedding=<float[]>`, `tenant_id` from auth context
- Response HTTP 201: `{ id, externalItemId, status, createdAt }`
- Embedding service timeout (>2s) returns HTTP 503 with retryable error

---

### US-07: Update Item
**As a** tenant client,
**I want to** update an item's metadata,
**So that** the embedding stays in sync with the latest content.

**Acceptance Criteria:**
- `PUT /api/v1/items/{externalItemId}` (requires `X-API-KEY`)
- Accepts partial or full metadata update
- Embedding is **always** regenerated on any metadata change (no stale embeddings)
- `updated_at` refreshed on every update
- Non-existent `externalItemId` within tenant → HTTP 404
- Response HTTP 200: `{ id, externalItemId, metadata, status, updatedAt }`

---

### US-08: Soft-Delete Item
**As a** tenant client,
**I want to** remove an item from recommendations,
**So that** discontinued items stop appearing to users.

**Acceptance Criteria:**
- `DELETE /api/v1/items/{externalItemId}` (requires `X-API-KEY`)
- Sets `status=INACTIVE` — does NOT delete the database row
- Soft-deleted items excluded from all candidate sets in recommendation queries
- Interaction history for the item is preserved
- Response HTTP 200: `{ externalItemId, status: "INACTIVE" }`

---

### US-09: List Items
**As a** tenant client or dashboard user,
**I want to** list all items in my catalog,
**So that** I can audit and manage the item inventory.

**Acceptance Criteria:**
- `GET /api/v1/items` (requires `X-API-KEY` or JWT)
- Returns only `ACTIVE` items for the tenant (no cross-tenant leakage)
- Cursor-based pagination: `?limit=20&cursor=<opaque>`
- Optional filters: `?createdAfter=ISO8601`, `?createdBefore=ISO8601`
- Response does **not** include the embedding vector (too large for list payloads)
- Response: `{ data: [{ id, externalItemId, metadata, status, createdAt, updatedAt }], nextCursor, limit }`

---

## Implementation Tasks

### Embedding Service (Python)

1. **`main.py`** — FastAPI app entry point
2. **`POST /embed` endpoint**
   ```python
   @app.post("/embed")
   def embed(request: EmbedRequest) -> EmbedResponse:
       tokens = model.encode(request.text)
       return EmbedResponse(embedding=tokens.tolist(), dimension=len(tokens))
   ```
3. **`GET /health`** → `{ "status": "ok", "model": model_name }`
4. **Model loading** — load `all-MiniLM-L6-v2` at startup (not per-request)
5. **Metadata concatenation** — implemented in the API service, not the embedding service

### Backend (Spring Boot)

1. **`EmbeddingServiceClient`** (RestTemplate or WebClient)
   - `float[] getEmbedding(String text)` — calls `POST {EMBEDDING_SERVICE_URL}/embed`
   - Timeout: 2 seconds. On timeout/error: throw `EmbeddingServiceException`

2. **`MetadataConcatenator` utility**
   - Iterate over all JSONB fields
   - Convert non-strings to string
   - Skip null/empty fields
   - Join with single space
   - Truncate to 512 tokens worth (~2000 chars as safe estimate)

3. **`ItemService`**
   - `createItem(tenantId, externalItemId, metadata)` → validate uniqueness → call embedding → persist
   - `updateItem(tenantId, externalItemId, metadata)` → fetch existing → regenerate embedding → persist
   - `deleteItem(tenantId, externalItemId)` → set `status=INACTIVE`
   - `listItems(tenantId, cursor, limit, filters)` → paginated ACTIVE items

4. **`ItemRepository extends JpaRepository<Item, UUID>`**
   - `findByTenantIdAndExternalItemId(UUID tenantId, String externalItemId)`
   - `findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable)`

5. **`ItemController`** — wire all endpoints with `@RestController`

---

## Data Isolation Checklist
- [ ] All `ItemRepository` queries include `tenantId` parameter (never query without it)
- [ ] `tenantId` sourced from `SecurityContextHolder`, never from request body
- [ ] Embedding vectors not returned in list endpoint responses
- [ ] Soft-delete verified: `status=INACTIVE` items do not appear in list or recommendations
