"""Online serving: interaction-service client, feature store lifecycle and ranking modes."""
import json

import httpx
import numpy as np
import pytest
from fastapi.testclient import TestClient

import app.api.recommendations as api
from app.features.snapshot import DAY_MS
from app.main import app
from app.ranking.ranker import MLRanker, RankingUnavailable
from app.schemas.recommendation import CandidateItem, RankRequest, UserPayload
from app.serving.feature_store import FeatureStore, FeaturesUnavailable
from app.serving.interaction_client import InteractionClient

TENANT = "00000000-0000-0000-0000-00000000000a"
NOW_MS = 30 * DAY_MS
MIN = 60_000


def _event(user, item, kind, ts):
    return {"externalUserId": user, "externalItemId": item, "interactionType": kind, "timestampMs": ts}


# Other shoppers view a and b together; b and c are bought together.
TENANT_EVENTS = [
    _event(f"shopper-{k}", item, kind, NOW_MS - 2 * DAY_MS + k * 60 * MIN + j * MIN)
    for k in range(8)
    for j, (item, kind) in enumerate([("a", "VIEW"), ("b", "VIEW"), ("b", "CART"), ("c", "PURCHASE")])
]
USER_EVENTS = {
    "user-1": [_event("user-1", "a", "VIEW", NOW_MS - 10 * MIN), _event("user-1", "a", "CLICK", NOW_MS - 5 * MIN)],
    "new-user": [],
}


def _transport(page_size_seen=None):
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.headers["X-Internal-Token"] == "secret"
        path = request.url.path
        if path.endswith("/events") and "/users/" in path:
            user = path.split("/users/")[1].split("/")[0]
            return httpx.Response(200, json={"data": USER_EVENTS.get(user, [])})
        if path == f"/internal/v1/tenants/{TENANT}/events":
            # Two pages, to exercise the cursor loop.
            if "cursor" not in request.url.params:
                return httpx.Response(200, json={"data": TENANT_EVENTS[:10], "nextCursor": "page2"})
            return httpx.Response(200, json={"data": TENANT_EVENTS[10:], "nextCursor": None})
        return httpx.Response(404)
    return httpx.MockTransport(handler)


class _InlineExecutor:
    def __init__(self):
        self.submitted = 0

    def submit(self, fn, *args):
        self.submitted += 1
        fn(*args)


class _DeferredExecutor:
    """Collects background jobs; run them explicitly."""
    def __init__(self):
        self.jobs = []

    def submit(self, fn, *args):
        self.jobs.append((fn, args))

    def run_all(self):
        jobs, self.jobs = self.jobs, []
        for fn, args in jobs:
            fn(*args)


class _ScoreByPopularityModel:
    """Deterministic stand-in for the LightGBM ranker: score = popularityScore column."""
    def predict(self, X):
        from app.features.query import COL
        return X[:, COL["popularityScore"]] + 0.1 * X[:, COL["collaborativeFilteringScore"]]


class _Loader:
    def get_model(self):
        return _ScoreByPopularityModel(), {"name": "susume-ranker", "version": "test"}


@pytest.fixture
def client():
    return InteractionClient("http://interaction-service", "secret", transport=_transport())


def _store(client, executor, clock=lambda: NOW_MS / 1000, ttl_s=3600):
    return FeatureStore(client, lookback_days=42, top_n=20, ttl_s=ttl_s, executor=executor, clock=clock)


def test_client_follows_cursor_pages(client):
    events = list(client.tenant_events(TENANT))
    assert len(events) == len(TENANT_EVENTS)
    assert events[0].item_id == "a" and isinstance(events[0].timestamp_ms, int)


def test_first_request_triggers_background_build_and_is_unavailable(client):
    executor = _DeferredExecutor()
    store = _store(client, executor)

    with pytest.raises(FeaturesUnavailable):
        store.get(TENANT)
    with pytest.raises(FeaturesUnavailable):
        store.get(TENANT)
    assert len(executor.jobs) == 1  # not scheduled twice while building

    executor.run_all()
    assert store.get(TENANT).n_events == len(TENANT_EVENTS)


def test_stale_snapshot_is_served_while_refreshing(client):
    now = [NOW_MS / 1000]
    executor = _DeferredExecutor()
    store = _store(client, executor, clock=lambda: now[0], ttl_s=60)
    store.refresh(TENANT)
    first = store.get(TENANT)

    now[0] += 120
    assert store.get(TENANT) is first  # stale but still served
    assert len(executor.jobs) == 1
    executor.run_all()
    assert store.get(TENANT) is not first


def test_failed_refresh_keeps_previous_snapshot(client):
    executor = _DeferredExecutor()
    now = [NOW_MS / 1000]
    store = _store(client, executor, clock=lambda: now[0], ttl_s=60)
    first = store.refresh(TENANT)

    store._client = InteractionClient("http://down", "secret",
                                      transport=httpx.MockTransport(lambda r: httpx.Response(500)))
    now[0] += 120
    store.get(TENANT)
    executor.run_all()  # logs the failure
    assert store.get(TENANT) is first


def _ranker(client):
    store = _store(client, _InlineExecutor())
    store.refresh(TENANT)
    return MLRanker(_Loader(), store, client, clock=lambda: NOW_MS / 1000)


def test_ml_mode_uses_history_excludes_anchor_and_adds_backend_candidates(client):
    ranker = _ranker(client)
    request = RankRequest(tenantId=TENANT, user=UserPayload(id="user-1"),
                          candidates=[CandidateItem(itemId="brand-new-item")], limit=10)

    response = ranker.rank(request)

    ids = [r.itemId for r in response.recommendations]
    assert response.rankingMode == "ml"
    assert "a" not in ids                  # anchor: the item the user just interacted with
    assert {"b", "c"} <= set(ids)          # generated from co-occurrence / popularity
    assert "brand-new-item" in ids         # backend candidate unknown to the snapshot is still ranked
    assert response.candidateCount == len(ids)
    scores = [r.score for r in response.recommendations]
    assert scores == sorted(scores, reverse=True)


def test_cold_start_user_gets_popularity_order(client):
    ranker = _ranker(client)

    response = ranker.rank(RankRequest(tenantId=TENANT, user=UserPayload(id="new-user"), limit=3))

    assert response.rankingMode == "cold_start_popularity"
    assert response.recommendations[0].score == pytest.approx(1.0)  # most popular item, max-normalized


def test_limit_is_applied(client):
    response = _ranker(client).rank(RankRequest(tenantId=TENANT, user=UserPayload(id="user-1"), limit=1))
    assert len(response.recommendations) == 1


@pytest.mark.parametrize("request_", [
    RankRequest(user=UserPayload(id="user-1")),
    RankRequest(tenantId=TENANT, user=None),
])
def test_requires_tenant_and_user(client, request_):
    with pytest.raises(RankingUnavailable):
        _ranker(client).rank(request_)


def test_endpoint_maps_unavailable_features_to_503_then_serves(client, monkeypatch):
    executor = _DeferredExecutor()
    store = _store(client, executor)
    monkeypatch.setattr(api, "ranker", MLRanker(_Loader(), store, client, clock=lambda: NOW_MS / 1000))
    http = TestClient(app)
    payload = {"tenantId": TENANT, "user": {"id": "user-1"}, "candidates": [], "limit": 5}

    assert http.post("/api/v1/recommendations/rank", json=payload).status_code == 503
    executor.run_all()
    response = http.post("/api/v1/recommendations/rank", json=payload)

    assert response.status_code == 200
    assert response.json()["rankingMode"] == "ml"
