"""
Training/serving parity: the features /rank computes from raw interaction-service events
must equal the rows the training pipeline built for the same query.
"""
import numpy as np
import pandas as pd
import pytest

from app.features.events import VIEW, CART, PURCHASE, build_event_log
from app.features.query import QueryParams, generate_candidates, user_signals
from app.features.snapshot import DAY_MS, build_snapshot
from app.ranking.ranker import MLRanker
from app.schemas.recommendation import RankRequest, UserPayload
from app.serving.feature_store import build_tenant_features
from app.serving.interaction_client import RawEvent
from training.build_dataset import DatasetConfig, _UserIndex, _query_rows

CODE_TO_TYPE = {VIEW: "VIEW", CART: "CART", PURCHASE: "PURCHASE"}
CUTOFF = 40 * DAY_MS
LOOKBACK = 42


def _random_events(seed: int, n_users=60, n_items=40, n_events=3000):
    rng = np.random.default_rng(seed)
    # Unique timestamps over 12 days before and 1 day after the cutoff.
    ts = np.sort(rng.choice(np.arange(CUTOFF - 12 * DAY_MS, CUTOFF + DAY_MS, 1000), n_events, replace=False))
    # Users browse in bursts so sessions and co-occurrence are non-trivial.
    users = rng.integers(0, n_users, n_events)
    items = rng.zipf(1.6, n_events) % n_items
    etype = rng.choice([VIEW, CART, PURCHASE], n_events, p=[0.85, 0.1, 0.05])
    return pd.DataFrame({
        "timestamp": ts.astype(np.int64),
        "visitor": users.astype(np.int64),
        "item": np.array([f"item-{i}" for i in items]),
        "etype": etype.astype(np.int8),
    }).drop_duplicates(["timestamp", "visitor", "etype", "item"])


def _raw(df: pd.DataFrame) -> list:
    return [RawEvent(f"user-{v}", it, CODE_TO_TYPE[int(e)], int(t))
            for t, v, it, e in df[["timestamp", "visitor", "item", "etype"]].itertuples(index=False)]


class _FakeClient:
    def __init__(self, raw_history):
        self._history = raw_history

    def user_events(self, tenant_id, user_id, limit=5000):
        return self._history


class _FakeStore:
    def __init__(self, features):
        self._features = features

    def get(self, tenant_id):
        return self._features


class _RecordingModel:
    """Scores rows in decreasing order, so the response lists candidates in feature-row order."""
    def predict(self, X):
        self.X = X
        return -np.arange(len(X), dtype=float)


class _Loader:
    def __init__(self, model, cfg: DatasetConfig):
        self.model = model
        self.meta = {"name": "test", "version": "t", "dataset": {"config": {
            "history_items": cfg.history_items,
            "personalized_half_life_hours": cfg.personalized_half_life_hours,
            "candidates_per_strategy": cfg.candidates_per_strategy,
        }}}

    def get_model(self):
        return self.model, self.meta


def _training_query(log, users, snap, i, cfg):
    """Training rows plus their candidate item ids, in row order."""
    X, *_ = _query_rows(log, users, snap, i, cfg)
    u_start = users.starts[users.of_row[i]]
    sig = user_signals(snap, log.item[u_start:i + 1], log.etype[u_start:i + 1], log.ts[u_start:i + 1],
                       int(log.ts[i]), cfg.query_params())
    cands = generate_candidates(snap, sig, cfg.query_params())
    return X, [str(log.item_ids[c]) for c in cands]


def _serve(features, df, log, i, cfg):
    visitor, t = int(log.visitor[i]), int(log.ts[i])
    user_rows = df[(df["visitor"] == visitor) & (df["timestamp"] <= t)].sort_values("timestamp")
    model = _RecordingModel()
    ranker = MLRanker(_Loader(model, cfg), _FakeStore(features), _FakeClient(_raw(user_rows)), clock=lambda: t / 1000)
    response = ranker.rank(RankRequest(tenantId="tenant", user=UserPayload(id=f"user-{visitor}"), limit=10_000))
    assert response.rankingMode == "ml"
    return model.X, [r.itemId for r in response.recommendations]


def _queries(log, n=25):
    in_day = np.flatnonzero((log.ts >= CUTOFF) & (log.ts < CUTOFF + DAY_MS))
    return in_day[:: max(len(in_day) // n, 1)][:n]


@pytest.mark.parametrize("seed", [1, 2, 3])
def test_serving_features_identical_to_training_rows(seed):
    df = _random_events(seed)
    cfg = DatasetConfig(lookback_days=LOOKBACK)
    log = build_event_log(df)
    users = _UserIndex(log)
    snap = build_snapshot(log, CUTOFF, cfg.lookback_days, cfg.candidates_per_strategy)
    # Serving snapshot built the way the feature store does it, from raw events.
    features = build_tenant_features(_raw(df), CUTOFF, cfg.lookback_days, cfg.candidates_per_strategy, built_at=0)

    queries = _queries(log)
    assert len(queries) >= 10
    for i in queries:
        X_train, ids_train = _training_query(log, users, snap, int(i), cfg)
        X_serve, ids_serve = _serve(features, df, log, int(i), cfg)
        assert ids_serve == ids_train
        np.testing.assert_array_equal(X_serve, X_train)


@pytest.mark.parametrize("seed", [1, 2, 3])
def test_parity_when_history_has_items_newer_than_the_snapshot(seed):
    """In production the snapshot only knows items seen before its cutoff; newer items must still get training-identical features."""
    df = _random_events(seed)
    # Items that only ever appear after the cutoff.
    late = df["timestamp"] >= CUTOFF + DAY_MS // 2
    df.loc[late, "item"] = df.loc[late, "item"].str.replace("item-", "new-item-")
    cfg = DatasetConfig(lookback_days=LOOKBACK)
    log = build_event_log(df)
    users = _UserIndex(log)
    snap = build_snapshot(log, CUTOFF, cfg.lookback_days, cfg.candidates_per_strategy)
    features = build_tenant_features(_raw(df[df["timestamp"] < CUTOFF]), CUTOFF, cfg.lookback_days,
                                     cfg.candidates_per_strategy, built_at=0)

    late_queries = [i for i in _queries(log, n=200) if log.ts[i] >= CUTOFF + DAY_MS // 2]
    checked_new = 0
    for i in late_queries[:25]:
        X_train, ids_train = _training_query(log, users, snap, int(i), cfg)
        X_serve, ids_serve = _serve(features, df, log, int(i), cfg)
        # Candidate sets match; rows are compared per item since new items are indexed differently.
        assert set(ids_serve) == set(ids_train)
        train_rows = dict(zip(ids_train, X_train))
        for item_id, row in zip(ids_serve, X_serve):
            np.testing.assert_array_equal(row, train_rows[item_id], err_msg=item_id)
        checked_new += sum(item_id.startswith("new-") for item_id in ids_serve)
    assert checked_new > 0
