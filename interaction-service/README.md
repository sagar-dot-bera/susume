# Susume Interaction Service

A FastAPI service that records and serves user–item interactions (views, clicks, purchases, …) for Susume tenants. These are the behavioural signals the recommendation engine learns from.

**Stack:** FastAPI · async SQLAlchemy 2.0 (asyncpg) · Pydantic v2 · Redis · pytest

## Endpoints

All `/api/v1/interactions` routes authenticate with a tenant API key in the `X-API-KEY` header. Interactive docs are at `http://localhost:8003/docs`.

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/interactions` | Record an interaction with an active item → `201` |
| `GET` | `/api/v1/interactions/history?externalUserId=…` | A user's interactions, newest first. Optional `interactionType`, `since`, `page`, `limit` |
| `GET` | `/api/v1/interactions/export?page=&limit=` | All of the tenant's interactions, oldest first, for the ML training pipeline |
| `GET` | `/health`, `/health/ready` | Liveness, and readiness (checks the database) |
| `GET` | `/internal/v1/tenants/{tenantId}/events` | *Service-to-service.* All of a tenant's raw events, keyset-paginated; read by the ML service to build its feature snapshot |
| `GET` | `/internal/v1/tenants/{tenantId}/users/{userId}/events` | *Service-to-service.* A user's latest events, oldest first |

`/internal/*` routes authenticate with an `X-Internal-Token` header matching `INTERNAL_API_TOKEN` and are disabled when it is unset. Timestamps there are epoch milliseconds.

Errors use the same `{"code": "...", "message": "..."}` shape as the Spring Boot backend.

```bash
curl -X POST http://localhost:8003/api/v1/interactions \
  -H "X-API-KEY: $API_KEY" -H "Content-Type: application/json" \
  -d '{"externalUserId": "user-42", "externalItemId": "sku-123", "interactionType": "PURCHASE"}'
```

## Design

```
app/
  main.py              app factory (create_app), lifespan, health checks
  routers/             HTTP layer: validation, status codes, OpenAPI docs
  dependencies.py      DI wiring: DB session -> repository -> service, API-key auth
  service.py           business rules (item must exist and be ACTIVE, cache eviction)
  repository.py        SQLAlchemy queries; filtering, ordering and pagination in SQL
  models.py / schemas.py   ORM mappings / Pydantic request & response models
  cache.py             Redis eviction of the backend's cached recommendations
```

- **Multi-tenant by construction.** The tenant comes only from the API key (SHA-256 hashed, the same as the backend), never from the request body, and every query is scoped by it.
- **Shared schema.** Postgres tables are owned by the backend's Flyway migrations. This service maps only the columns it needs and never runs DDL.
- **Cache consistency.** Recording an interaction evicts that user's cached recommendations from Redis (the same key the Spring cache uses). If Redis is down, the request still succeeds and is logged; the entry expires on its own TTL.
- **Testable.** `create_app()` accepts an engine and a cache, so the tests run against in-memory SQLite and a fake cache, with no Docker needed.

## Running

```bash
python -m venv .venv && .venv/Scripts/activate      # source .venv/bin/activate on macOS/Linux
pip install -r requirements-dev.txt
pytest
uvicorn app.main:app --reload --port 8003           # needs Postgres; see app/config.py for env vars
```

Or run it with the rest of the stack: `docker compose up interaction-service`.
