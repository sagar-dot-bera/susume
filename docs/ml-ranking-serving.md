# ML ranking: how the model is served

The ranker (`susume-ranker v2`, LightGBM LambdaMART) is trained offline on RetailRocket events and serves live recommendations for every tenant. This page covers how its 35 input features are computed at request time, and why they match training exactly.

## The rule: one feature implementation

Every model input is computed by one module, `recommendation-service/app/features/`, used by both:

- **training**: `training/build_dataset.py` builds each query's rows with it, and
- **serving**: `POST /api/v1/recommendations/rank` builds the request's rows with it.

The backend sends **no feature values**. It proposes candidate item ids; the ML service computes everything else from raw interaction events. (Before this change the backend computed features in Java with different definitions, so the model's two most important inputs meant different things in production than in training.)

| Module | What it holds |
|---|---|
| `events.py` | Event codes and weights, Susume interaction type mapping, sessionisation (30 min gap), `EventLog` |
| `snapshot.py` | Per-item statistics as of a cutoff: popularity, trending, all-time counts, 7-day counts, session and purchase co-occurrence |
| `query.py` | Per-query: user signals from history, the six candidate generators, and the 35-column feature matrix |

## Request flow

```
backend (Java)                         recommendation-service (Python)              interaction-service (Python)
──────────────                         ───────────────────────────────              ────────────────────────────
strategies propose candidate ids  ──▶  POST /rank {tenantId, user.id, candidates}
                                         │ tenant snapshot (in memory, rebuilt   ◀── GET /internal/v1/tenants/{t}/events
                                         │   hourly in the background)
                                         │ user's history (live, per request)   ◀── GET /internal/v1/tenants/{t}/users/{u}/events
                                         │ user_signals → generate_candidates ∪ backend candidates − anchor
                                         │ feature_matrix → model.predict
skip inactive items, cap to limit  ◀──   ranked ids + scores, rankingMode
log impressions (also on cache hits)
```

- **Anchor**: as in training, a query is "the user just interacted with item *a*". The anchor is the user's most recent item and is never recommended back.
- **Snapshot freshness**: the snapshot is rebuilt every `SNAPSHOT_TTL_SECONDS` (default 1 h); training used a snapshot taken at midnight, so serving is at least as fresh. Items newer than the snapshot get the same zero statistics they would have had in training.
- **Query settings** (history length, personalised half-life, candidates per generator, lookback) are read from the model's `_meta.json`, so serving always uses what the model was trained with.
- **Event types**: CLICK, LIKE and SHARE count as views (the training data only has view / cart / purchase).

## Fallbacks

| Situation | Result |
|---|---|
| First request for a tenant (snapshot building) | `/rank` returns 503; backend uses its strategy order (`modelVersion: spring-baseline`) |
| interaction-service or token unavailable | same |
| User has no history | `rankingMode: cold_start_popularity`: tenant popularity order (the model was only trained on users with history) |
| Ranked items inactive | skipped by the backend; it over-fetches 3× the limit |

The backend's `strategy` field shows which path served a response: `personalized_ml_reranked`, `personalized_cold_start_popularity`, or plain `personalized`.

## How parity is verified

- `tests/test_serving_parity.py` runs random event logs through the **training pipeline** (`_query_rows`) and through the **serving path** (raw events → feature store → ranker with a recording model) and asserts identical candidates and bit-identical feature rows, including items newer than the snapshot. Shifting the serving clock by one hour makes it fail.
- Rebuilding the full training dataset after the refactor reproduces the cached dataset the model was trained on exactly (1.8 M rows), and re-scoring the saved model on it reproduces the recorded test metrics, so the existing model needed no retraining.

## Known limitations

- **Trained on RetailRocket**, not tenant data. Parity makes the inputs correct; it doesn't prove the patterns transfer to a given tenant. Use the impression log (now written for every served response) to compare `personalized_ml_reranked` against the strategy order online.
- **Candidates with no interactions** (e.g. new catalogue items proposed by the content-based strategy) never occurred in training, so the model's scores for them are extrapolation.
- **Snapshot size**: a tenant snapshot holds all of the tenant's events' statistics in memory and is rebuilt from a full event export; very large tenants will need an incremental or offline snapshot job.

## Configuration

| Service | Variable | Purpose |
|---|---|---|
| recommendation-service | `INTERACTION_SERVICE_URL` | Base URL of interaction-service |
| recommendation-service, interaction-service | `INTERNAL_API_TOKEN` | Shared secret for `/internal/*`; unset disables ML ranking (backend falls back) |
| recommendation-service | `SNAPSHOT_TTL_SECONDS` | Snapshot refresh interval (default 3600) |
| backend | `RECOMMENDATION_ML_TIMEOUT_MS` | Read timeout for `/rank` (default 500) |
