import json
import numpy as np
import pytest
from app.model.loader import ModelLoader, DummyFallbackModel
from app.ranking.features import FEATURE_NAMES
from training.build_dataset import DatasetConfig, _UserIndex, _query_rows
from training.evaluate import evaluate_grouped
from training.retailrocket import EventLog, VIEW, CART, PURCHASE
from app.features.snapshot import build_snapshot, DAY_MS

HOUR = 60 * 60 * 1000
T0 = 20 * DAY_MS


def _event_log(rows):
    """rows: (ts, visitor, item, etype), any order."""
    rows = sorted(rows, key=lambda r: (r[1], r[0]))
    ts, visitor, item, etype = (np.array(col) for col in zip(*rows))
    session = np.cumsum(np.r_[True, (visitor[1:] != visitor[:-1]) | (np.diff(ts) > 30 * 60 * 1000)]) - 1
    n_items = int(item.max()) + 1
    first_seen = np.full(n_items, np.iinfo(np.int64).max, dtype=np.int64)
    np.minimum.at(first_seen, item, ts)
    return EventLog(ts=ts.astype(np.int64), visitor=visitor.astype(np.int64), item=item.astype(np.int32),
                    etype=etype.astype(np.int8), session=session, item_ids=np.arange(n_items), first_seen=first_seen)


@pytest.fixture
def small_log():
    rows = []
    # Earlier visitors who browse items 1 & 2 (and buy 2) together: drives co-occurrence.
    for v in range(10, 20):
        base = T0 - 5 * DAY_MS + v * HOUR
        rows += [(base, v, 1, VIEW), (base + 60_000, v, 2, VIEW), (base + 120_000, v, 2, CART)]
    rows += [(T0 - 3 * DAY_MS, 30, 3, VIEW), (T0 - 3 * DAY_MS + 60_000, 30, 1, VIEW)]
    # Query user: viewed 3 two days ago, views 1 now, then carts 2 and buys 3 within the label window.
    rows += [
        (T0 - 2 * DAY_MS, 1, 3, VIEW),
        (T0 + 2 * HOUR, 1, 1, VIEW),
        (T0 + 3 * HOUR, 1, 2, CART),
        (T0 + 4 * HOUR, 1, 3, PURCHASE),
        (T0 + 3 * DAY_MS, 1, 4, VIEW),  # outside the label window
    ]
    return _event_log(rows)


def _query(log, cfg=DatasetConfig()):
    users = _UserIndex(log)
    snap = build_snapshot(log, T0, lookback_days=10)
    i = int(np.flatnonzero((log.visitor == 1) & (log.ts == T0 + 2 * HOUR))[0])
    return _query_rows(log, users, snap, i, cfg), snap


def test_snapshot_ignores_events_after_cutoff(small_log):
    snap = build_snapshot(small_log, T0, lookback_days=10)
    assert snap.all_time[4] == 0  # item 4 only appears after the cutoff
    assert snap.co_session[1, 2] > 0


def test_query_labels_and_no_future_leakage(small_log):
    (X, y, idcg, n_rel), _ = _query(small_log)
    col = {n: i for i, n in enumerate(FEATURE_NAMES)}

    # Labels: item 2 carted (rel 2) and item 3 purchased (rel 3); item 4 is outside the window, anchor 1 excluded.
    assert n_rel == 2
    assert sorted(y[y > 0].tolist()) == [2, 3]
    assert idcg == pytest.approx(7 / np.log2(2) + 3 / np.log2(3))

    # Features only reflect the past: the user has 2 views so far and no carts/purchases yet.
    assert np.all(X[:, col["user_totalViews"]] == 2)
    assert np.all(X[:, col["user_totalCarts"]] == 0)
    assert np.all(X[:, col["user_totalPurchases"]] == 0)
    assert np.all(X[:, col["user_item_previousCarts"]] == 0)


def test_grouped_metrics_penalize_items_missing_from_pool():
    # One query, two engaged items, but only one made it into the candidate pool.
    y = np.array([1, 0, 0])
    qid = np.zeros(3, dtype=np.int32)
    idcg = np.array([1.0 + 1.0 / np.log2(3)])
    metrics = evaluate_grouped(np.array([0.9, 0.5, 0.1]), y, qid, idcg, np.array([2]), k=10)
    assert metrics["recallAt10"] == pytest.approx(0.5)
    assert metrics["candidateRecall"] == pytest.approx(0.5)
    assert metrics["ndcgAt10"] == pytest.approx(1.0 / idcg[0], abs=1e-4)  # metrics are rounded to 4 dp
    assert metrics["mrrAt10"] == pytest.approx(1.0)


def test_grouped_metrics_average_over_queries():
    y = np.array([1, 0, 0, 1])
    qid = np.array([0, 0, 1, 1], dtype=np.int32)
    scores = np.array([0.9, 0.1, 0.9, 0.1])  # query 0 ranked perfectly, query 1 relevant item second
    metrics = evaluate_grouped(scores, y, qid, np.array([1.0, 1.0]), np.array([1, 1]), k=1)
    assert metrics["hitRateAt1"] == pytest.approx(0.5)
    assert metrics["ndcgAt1"] == pytest.approx(0.5)


def test_loader_rejects_model_with_different_features(tmp_path):
    model_path = tmp_path / "ranker.joblib"
    import joblib
    joblib.dump(DummyFallbackModel(), model_path)
    (tmp_path / "ranker_meta.json").write_text(json.dumps({"version": "old", "featureNames": FEATURE_NAMES[:32]}))

    loader = ModelLoader(str(model_path))
    assert loader.metadata["version"] == "heuristic-fallback"

    (tmp_path / "ranker_meta.json").write_text(json.dumps({"version": "v9", "featureNames": FEATURE_NAMES}))
    assert ModelLoader(str(model_path)).metadata["version"] == "v9"
