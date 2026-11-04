# Recommendation cache

The backend caches final recommendation responses (after strategies, ML re-ranking, inactive-item filtering and the limit cut) in Redis or Valkey. Code: [`backend/.../cache`](../backend/src/main/java/com/susume/recommendation/cache).

## What is cached

| Endpoint | Cached | Key varies by |
|---|---|---|
| `GET /api/v1/recommendations` | yes | tenant, user, limit (after defaulting and capping), ranker model version, tenant + user generation |
| `GET /api/v1/recommendations/trending` | yes | tenant, limit, days, tenant generation |
| `POST /api/v1/admin/recommendations/preview` | no | admins previewing a strategy always see a fresh result |

Impressions are logged for every response served to an end user, whether it came from cache or not.

## Keys and tenant isolation

```
reco:{tenant}:gen                                   tenant generation token
reco:{tenant}:user:{user}:gen                       user generation token
reco:{tenant}:personalized:user:{user}:limit:{n}:model:{version}:gen:{tenantGen}.{userGen}
reco:{tenant}:trending:limit:{n}:days:{d}:gen:{tenantGen}
```

- The tenant id comes from the authenticated API key (`TenantContext`), never from the request, and every key starts with it. A tenant can only produce or read keys under its own prefix.
- Authorization runs before the cache is consulted, so a cache hit never skips the security filters.
- `{tenant}` is a Redis Cluster hash tag: a tenant's keys share one slot, so the generation lookup is a single `MGET`.
- `model` is the version the ranker last reported (`none` before the first ranked response), so deploying a new model stops the old model's lists being served.

## TTLs

The TTL depends on which path served the response (its `strategy` field), and each TTL is randomized by ±10% so entries written together don't expire together:

| `strategy` | TTL | Why |
|---|---|---|
| `personalized_ml_reranked` | 15 min | The normal path. User changes invalidate it immediately. |
| `personalized_cold_start_popularity` | 5 min | Tenant popularity drifts. The user's first interaction invalidates it anyway. |
| `personalized` | 1 min | A degraded fallback (ML unavailable), so ranking is retried soon. |
| `trending` | 5 min | Shared by the whole tenant, so a short TTL still gives a high hit rate. |
| anything else | 5 min (`default-ttl`) | |

The TTLs are set under `recommendation.cache.*` in `application.yml`.

## Invalidation

Invalidation never deletes entries. It replaces a random **generation token** that is part of the key, so the next read looks up a new key and the orphaned entries expire on their TTL.

| Event | Invalidates | Where |
|---|---|---|
| Interaction recorded | user generation | backend `InteractionService`, interaction-service `app/cache.py` |
| Item created, updated, soft-deleted, or embedding completed | tenant generation | backend `ItemService` |

- This takes one `SET` instead of a `SCAN` over the keyspace.
- The backend bumps the token **after the transaction commits**. A request that was computing during the write stores its result under the old generation, which nobody reads any more, so it can't put stale data back.
- Generation keys expire after `generation-ttl` (24 h). This must be longer than every entry TTL, and the backend refuses to start otherwise. interaction-service uses the same value (`RECOMMENDATION_CACHE_GENERATION_TTL_SECONDS`).
- **Not invalidated:** other users' personalized lists when someone else interacts (for example collaborative-filtering drift), and trending when interactions arrive. Both rely on their TTLs.

## Stampede prevention

- **Request coalescing:** concurrent misses for the same key in one backend instance share a single computation. The other requests wait for it and receive the same result, or the same error. Coalescing also applies while Redis is down, so an outage doesn't multiply database load by the request rate.
- **TTL jitter:** ±`ttl-jitter`.
- Coalescing is per instance. With N backend instances, a cold key is computed at most N times at once.

## Failure behaviour

- If Redis errors (unreachable, timeout), the request is computed and served normally, and Redis is skipped for `failure-backoff` (30 s). This means requests don't each wait out the Redis timeout (500 ms) during an outage.
- An unreadable entry (for example from an older response format) is recomputed and overwritten.
- A failed invalidation is logged and counted. The affected entries stay stale until their TTL runs out.
- A computation error is never cached.
- `RECOMMENDATION_CACHE_ENABLED=false` turns the cache off completely.

## Monitoring

Metrics are at `/actuator/metrics/<name>`. Only `/actuator/health` is public; everything else under `/actuator` needs an ADMIN JWT.

| Metric | Tags | Use |
|---|---|---|
| `recommendation.cache.requests` | `endpoint`, `result` = hit / miss / bypass | Hit rate = hit ÷ (hit + miss). `bypass` counts requests served while Redis was skipped. |
| `recommendation.cache.latency` (+ `.percentile`, `phi` = 0.5 / 0.95) | `endpoint`, `result` | p50/p95 of hits vs misses |
| `recommendation.compute` (+ `.percentile`) | `endpoint` | How often recommendations are actually computed, and how long that takes |
| `recommendation.cache.coalesced` | `endpoint` | Requests that joined another request's computation |
| `recommendation.cache.invalidations` | `scope` = user / tenant | Invalidation rate |
| `recommendation.cache.errors` | `operation` = read / write / invalidate | Redis problems |
| `hikaricp.connections.active`, `.pending`, `.usage` | | Database load (Spring Boot built-in) |
| `http.server.requests` | `uri` | End-to-end latency, including impression logging |

Example: `GET /actuator/metrics/recommendation.cache.requests?tag=endpoint:personalized&tag=result:hit`
