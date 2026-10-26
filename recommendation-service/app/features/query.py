"""
Per-query candidate generation and feature construction.

A query is "user u, at time t, after interacting with anchor item a": the anchor is the
user's most recent event. Training builds one query per sampled event; serving builds one
per /rank request from the user's live history. Both go through `user_signals` ->
`generate_candidates` -> `feature_matrix`, so the model sees identically defined inputs.
"""
from dataclasses import dataclass
from typing import Tuple

import numpy as np
import scipy.sparse as sp

from app.features.events import EVENT_WEIGHTS, VIEW, CART, PURCHASE
from app.features.snapshot import Snapshot, DAY_MS
from app.ranking.features import FEATURE_NAMES, SURFACE_MAP

HOUR_MS = 60 * 60 * 1000
COL = {name: i for i, name in enumerate(FEATURE_NAMES)}


@dataclass(frozen=True)
class QueryParams:
    history_items: int = 5
    personalized_half_life_hours: float = 72.0
    candidates_per_strategy: int = 20


@dataclass
class UserSignals:
    """Everything about the user's history that candidate generation and features need."""
    t: int
    anchor: int
    h_type: np.ndarray
    h_ts: np.ndarray
    hist_items: np.ndarray    # distinct items the user interacted with, sorted
    hist_counts: np.ndarray   # (len(hist_items), 3) views / carts / purchases per item
    hist_last: np.ndarray     # last interaction time per item
    pers: np.ndarray          # recency-weighted engagement per item, max-normalized
    sim_idx: np.ndarray       # items co-occurring with the anchor in sessions
    sim_val: np.ndarray
    cf_idx: np.ndarray        # session co-occurrence with the recent items, recency-weighted
    cf_val: np.ndarray
    fbt_idx: np.ndarray       # cart/purchase co-occurrence with the recent items
    fbt_val: np.ndarray


def _top_k(indices: np.ndarray, data: np.ndarray, k: int) -> np.ndarray:
    if len(data) > k:
        part = np.argpartition(-data, k)[:k]
        return indices[part]
    return indices


def _lookup(indices: np.ndarray, data: np.ndarray, keys: np.ndarray) -> np.ndarray:
    if len(indices) == 0:
        return np.zeros(len(keys), dtype=np.float32)
    pos = np.minimum(np.searchsorted(indices, keys), len(indices) - 1)
    return np.where(indices[pos] == keys, data[pos], 0.0).astype(np.float32)


def take(values: np.ndarray, idx: np.ndarray, fill=0) -> np.ndarray:
    """values[idx], with `fill` for indices past the end (items newer than the snapshot)."""
    known = idx < len(values)
    out = np.full(idx.shape + values.shape[1:], fill, dtype=values.dtype)
    out[known] = values[idx[known]]
    return out


def _weighted_rows(matrix: sp.csr_matrix, rows: np.ndarray, weights: np.ndarray) -> Tuple[np.ndarray, np.ndarray]:
    vec = sp.csr_matrix(weights[None, :]) @ matrix[rows]
    vec.sum_duplicates()
    vec.sort_indices()
    return vec.indices, vec.data


def user_signals(snap: Snapshot, h_item: np.ndarray, h_type: np.ndarray, h_ts: np.ndarray, t: int,
                 params: QueryParams) -> UserSignals:
    """
    h_item / h_type / h_ts: the user's events up to and including the anchor (their latest
    event), oldest first. Must be non-empty.
    """
    anchor = int(h_item[-1])

    # Most recent distinct items, newest first, for the item-to-item generators.
    _, last_pos = np.unique(h_item[::-1], return_index=True)
    recent = h_item[::-1][np.sort(last_pos)][:params.history_items]
    rec_w = 1.0 / np.arange(1, len(recent) + 1, dtype=np.float32)
    rec_w /= rec_w.sum()

    # Per-item history aggregates (user-item features and the personalized generator).
    hist_items, h_inv = np.unique(h_item, return_inverse=True)
    hist_counts = np.zeros((len(hist_items), 3), dtype=np.float32)
    np.add.at(hist_counts, (h_inv, h_type), 1.0)
    hist_last = np.zeros(len(hist_items), dtype=np.int64)
    np.maximum.at(hist_last, h_inv, h_ts)
    decay = np.power(0.5, (t - h_ts) / (params.personalized_half_life_hours * HOUR_MS))
    pers = np.zeros(len(hist_items), dtype=np.float32)
    np.add.at(pers, h_inv, EVENT_WEIGHTS[h_type] * decay)
    pers /= pers.max()

    # Items at or past n_known are newer than the snapshot; like any item without snapshot
    # history they co-occur with nothing, so they are left out of the lookups.
    n_known = snap.co_session.shape[0]
    if anchor < n_known:
        row = slice(snap.co_session.indptr[anchor], snap.co_session.indptr[anchor + 1])
        sim_idx, sim_val = snap.co_session.indices[row], snap.co_session.data[row]
    else:
        sim_idx, sim_val = np.empty(0, dtype=np.int32), np.empty(0, dtype=np.float32)
    known = recent < n_known
    cf_idx, cf_val = _weighted_rows(snap.co_session, recent[known], rec_w[known])
    fbt_idx, fbt_val = _weighted_rows(snap.co_buy, recent[known], rec_w[known])

    return UserSignals(
        t=t, anchor=anchor, h_type=h_type, h_ts=h_ts,
        hist_items=hist_items, hist_counts=hist_counts, hist_last=hist_last, pers=pers,
        sim_idx=sim_idx, sim_val=sim_val, cf_idx=cf_idx, cf_val=cf_val, fbt_idx=fbt_idx, fbt_val=fbt_val,
    )


def generate_candidates(snap: Snapshot, sig: UserSignals, params: QueryParams) -> np.ndarray:
    """Union of the six generators' top items, sorted, without the anchor."""
    n = params.candidates_per_strategy
    cands = np.unique(np.concatenate([
        snap.top_popular,
        snap.top_trending,
        _top_k(sig.sim_idx, sig.sim_val, n),
        _top_k(sig.cf_idx, sig.cf_val, n),
        _top_k(sig.fbt_idx, sig.fbt_val, n),
        _top_k(sig.hist_items, sig.pers, n),
    ]).astype(np.int64))
    return cands[cands != sig.anchor]


def feature_matrix(snap: Snapshot, first_seen: np.ndarray, sig: UserSignals, cands: np.ndarray) -> np.ndarray:
    """
    One row per candidate item index. Context is the homepage and the hour / weekday of `t`
    in UTC, as in training (the training data has no surfaces, so the model ignores it).
    """
    t = sig.t
    X = np.zeros((len(cands), len(FEATURE_NAMES)), dtype=np.float32)

    # Strategy scores (contentBased / hybrid / randomDiscovery / ruleBased have no training-data equivalent and stay 0).
    X[:, COL["collaborativeFilteringScore"]] = _lookup(sig.cf_idx, sig.cf_val, cands)
    X[:, COL["frequentlyBoughtTogetherScore"]] = _lookup(sig.fbt_idx, sig.fbt_val, cands)
    X[:, COL["personalizedScore"]] = _lookup(sig.hist_items, sig.pers, cands)
    X[:, COL["popularityScore"]] = take(snap.popularity, cands)
    X[:, COL["similarItemsScore"]] = _lookup(sig.sim_idx, sig.sim_val, cands)
    X[:, COL["trendingScore"]] = take(snap.trending, cands)

    # User features (same for every candidate in the query).
    h_type, h_ts = sig.h_type, sig.h_ts
    type_counts = np.bincount(h_type, minlength=3)
    X[:, COL["user_totalViews"]] = type_counts[VIEW]
    X[:, COL["user_totalCarts"]] = type_counts[CART]
    X[:, COL["user_totalPurchases"]] = type_counts[PURCHASE]
    X[:, COL["user_totalInteractions"]] = len(h_ts)
    X[:, COL["user_interactionsLast24Hours"]] = np.sum(h_ts > t - DAY_MS)
    X[:, COL["user_interactionsLast7Days"]] = np.sum(h_ts > t - 7 * DAY_MS)
    X[:, COL["user_interactionsLast30Days"]] = np.sum(h_ts > t - 30 * DAY_MS)

    # Item features.
    recent_counts = take(snap.recent_counts, cands)
    X[:, COL["item_itemPopularity"]] = take(snap.all_time, cands)
    X[:, COL["item_recentViews"]] = recent_counts[:, VIEW]
    X[:, COL["item_recentCarts"]] = recent_counts[:, CART]
    X[:, COL["item_recentPurchases"]] = recent_counts[:, PURCHASE]
    X[:, COL["item_itemAge"]] = np.floor(np.maximum(t - take(first_seen, cands, np.iinfo(np.int64).max), 0) / DAY_MS)

    # User-item features.
    hist_items = sig.hist_items
    pos = np.minimum(np.searchsorted(hist_items, cands), len(hist_items) - 1)
    seen = hist_items[pos] == cands
    X[:, COL["user_item_previousViews"]] = np.where(seen, sig.hist_counts[pos, VIEW], 0)
    X[:, COL["user_item_previousCarts"]] = np.where(seen, sig.hist_counts[pos, CART], 0)
    X[:, COL["user_item_previousPurchases"]] = np.where(seen, sig.hist_counts[pos, PURCHASE], 0)
    X[:, COL["user_item_timeSinceLastInteraction"]] = np.where(seen, (t - sig.hist_last[pos]) / HOUR_MS, -1.0)

    # Context. dayOfWeek follows java.util.Calendar (1 = Sunday ... 7 = Saturday) like the backend.
    weekday_mon0 = (t // DAY_MS + 3) % 7
    X[:, COL["context_surfaceCode"]] = SURFACE_MAP["homepage"]
    X[:, COL["context_hour"]] = (t // HOUR_MS) % 24
    X[:, COL["context_dayOfWeek"]] = (weekday_mon0 + 1) % 7 + 1
    return X
