import os
import json
import hashlib
import logging
from dataclasses import dataclass, asdict, field
from typing import Dict, List, Tuple

import numpy as np

from app.features.query import COL, HOUR_MS, QueryParams, feature_matrix, generate_candidates, user_signals  # noqa: F401
from app.features.snapshot import Snapshot, build_snapshot, DAY_MS
from app.ranking.features import FEATURE_NAMES
from training.retailrocket import EventLog, EVENT_RELEVANCE

logger = logging.getLogger("training")

EVAL_K = 10


@dataclass
class DatasetConfig:
    train_days: int = 42
    val_days: int = 14
    test_days: int = 14
    label_window_hours: int = 24
    lookback_days: int = 42
    candidates_per_strategy: int = 20
    history_items: int = 5
    personalized_half_life_hours: float = 72.0
    max_user_events: int = 500
    max_queries: Dict[str, int] = field(default_factory=lambda: {"train": 40000, "val": 8000, "test": 10000})
    seed: int = 42

    def query_params(self) -> QueryParams:
        return QueryParams(
            history_items=self.history_items,
            personalized_half_life_hours=self.personalized_half_life_hours,
            candidates_per_strategy=self.candidates_per_strategy,
        )

    def cache_key(self) -> str:
        payload = json.dumps(asdict(self), sort_keys=True) + "|" + ",".join(FEATURE_NAMES)
        return hashlib.sha1(payload.encode()).hexdigest()[:10]


@dataclass
class SplitData:
    """
    One row per (query, candidate). Rows of the same query are contiguous.
    `idcg` and `n_rel` are per query and count *every* item the user engaged with in the
    label window, including ones the candidate generators missed, so metrics are not
    inflated by only scoring what the candidate pool happened to contain.
    """
    X: np.ndarray
    y: np.ndarray
    qid: np.ndarray
    idcg: np.ndarray
    n_rel: np.ndarray

    @property
    def n_queries(self) -> int:
        return len(self.idcg)

    @property
    def group_sizes(self) -> np.ndarray:
        return np.bincount(self.qid, minlength=self.n_queries)

    def save(self, path: str):
        np.savez(path, X=self.X, y=self.y, qid=self.qid, idcg=self.idcg, n_rel=self.n_rel)

    @classmethod
    def load(cls, path: str) -> "SplitData":
        with np.load(path) as f:
            return cls(X=f["X"], y=f["y"], qid=f["qid"], idcg=f["idcg"], n_rel=f["n_rel"])


def split_periods(events: EventLog, cfg: DatasetConfig) -> Dict[str, Tuple[int, int]]:
    """
    Contiguous time periods, newest last: train -> val -> test. The final partial day is dropped.
    """
    test_end = (events.t_max // DAY_MS) * DAY_MS
    test_start = test_end - cfg.test_days * DAY_MS
    val_start = test_start - cfg.val_days * DAY_MS
    train_start = val_start - cfg.train_days * DAY_MS
    if train_start - cfg.lookback_days * DAY_MS < events.t_min:
        raise ValueError("Not enough history before the training period for the configured lookback.")
    return {"train": (train_start, val_start), "val": (val_start, test_start), "test": (test_start, test_end)}


class _UserIndex:
    def __init__(self, events: EventLog):
        new_user = np.ones(len(events.ts), dtype=bool)
        new_user[1:] = events.visitor[1:] != events.visitor[:-1]
        self.starts = np.flatnonzero(new_user)
        self.sizes = np.diff(np.append(self.starts, len(events.ts)))
        self.of_row = np.cumsum(new_user) - 1

        # Runs of consecutive events on the same item by the same user; the next run is
        # by construction the user's next *different* item.
        new_run = new_user.copy()
        new_run[1:] |= events.item[1:] != events.item[:-1]
        run_starts = np.flatnonzero(new_run)
        run_of_row = np.cumsum(new_run) - 1
        next_run = np.append(run_starts[1:], len(events.ts))[run_of_row]
        same_user = np.zeros(len(events.ts), dtype=bool)
        in_range = next_run < len(events.ts)
        same_user[in_range] = self.of_row[next_run[in_range]] == self.of_row[in_range]
        self.next_other_item_ts = np.where(same_user, events.ts[np.minimum(next_run, len(events.ts) - 1)], np.iinfo(np.int64).max)


def select_queries(events: EventLog, users: _UserIndex, start: int, end: int, max_queries: int,
                   cfg: DatasetConfig, rng: np.random.Generator) -> np.ndarray:
    """
    A query is "the user just interacted with event i at time t; what will they engage with
    over the next label window?". Valid cutoffs have at least one different item engaged
    with inside the window, which also has to fit inside the period (so labels never cross
    into the next split). At most one query per user per day.
    """
    window = cfg.label_window_hours * HOUR_MS
    ts = events.ts
    valid = (
        (ts >= start)
        & (ts + window <= end)
        & (users.next_other_item_ts <= ts + window)
        & (users.sizes[users.of_row] <= cfg.max_user_events)
    )
    rows = rng.permutation(np.flatnonzero(valid))
    _, first = np.unique(events.visitor[rows] * 100000 + ts[rows] // DAY_MS, return_index=True)
    rows = rows[np.sort(first)][:max_queries]
    return rows[np.argsort(ts[rows], kind="stable")]


def _query_rows(events: EventLog, users: _UserIndex, snap: Snapshot, i: int,
                cfg: DatasetConfig) -> Tuple[np.ndarray, np.ndarray, float, int]:
    t = int(events.ts[i])
    anchor = int(events.item[i])
    u_start = users.starts[users.of_row[i]]
    u_end = u_start + users.sizes[users.of_row[i]]

    # Labels: highest-graded interaction per item in (t, t + window], excluding the anchor item.
    l_end = u_start + np.searchsorted(events.ts[u_start:u_end], t + cfg.label_window_hours * HOUR_MS, side="right")
    l_item = events.item[i + 1:l_end]
    l_rel = EVENT_RELEVANCE[events.etype[i + 1:l_end]]
    l_mask = l_item != anchor
    label_items, inv = np.unique(l_item[l_mask], return_inverse=True)
    label_rel = np.zeros(len(label_items), dtype=np.int8)
    np.maximum.at(label_rel, inv, l_rel[l_mask])

    # Features: everything the user did up to and including the cutoff event, through the
    # same code the /rank endpoint uses.
    params = cfg.query_params()
    sig = user_signals(snap, events.item[u_start:i + 1], events.etype[u_start:i + 1], events.ts[u_start:i + 1], t, params)
    cands = generate_candidates(snap, sig, params)
    X = feature_matrix(snap, events.first_seen, sig, cands)

    y = np.zeros(len(cands), dtype=np.int8)
    lpos = np.minimum(np.searchsorted(label_items, cands), max(len(label_items) - 1, 0))
    if len(label_items):
        hit = label_items[lpos] == cands
        y[hit] = label_rel[lpos[hit]]

    ideal = np.sort(label_rel)[::-1][:EVAL_K].astype(np.float64)
    idcg = float(np.sum((2.0 ** ideal - 1) / np.log2(np.arange(2, len(ideal) + 2))))
    return X, y, idcg, len(label_items)


def build_split(events: EventLog, users: _UserIndex, period: Tuple[int, int], max_queries: int,
                drop_unanswerable: bool, cfg: DatasetConfig, rng: np.random.Generator) -> SplitData:
    rows = select_queries(events, users, period[0], period[1], max_queries, cfg, rng)
    days = events.ts[rows] // DAY_MS

    Xs: List[np.ndarray] = []
    ys: List[np.ndarray] = []
    idcgs: List[float] = []
    n_rels: List[int] = []
    unanswerable = 0

    for day in np.unique(days):
        snap = build_snapshot(events, int(day) * DAY_MS, cfg.lookback_days, cfg.candidates_per_strategy)
        for i in rows[days == day]:
            X, y, idcg, n_rel = _query_rows(events, users, snap, int(i), cfg)
            if not y.any():
                unanswerable += 1
                if drop_unanswerable:
                    continue
            Xs.append(X)
            ys.append(y)
            idcgs.append(idcg)
            n_rels.append(n_rel)

    sizes = np.array([len(y) for y in ys])
    data = SplitData(
        X=np.concatenate(Xs),
        y=np.concatenate(ys),
        qid=np.repeat(np.arange(len(ys), dtype=np.int32), sizes),
        idcg=np.array(idcgs, dtype=np.float32),
        n_rel=np.array(n_rels, dtype=np.int32),
    )
    logger.info(
        f"  {len(rows):,} queries sampled, {unanswerable:,} with no engaged item in the candidate pool"
        f"{' (dropped)' if drop_unanswerable else ''}; {data.n_queries:,} queries, {len(data.y):,} rows, "
        f"avg {sizes.mean():.0f} candidates/query."
    )
    return data


def build_datasets(events: EventLog, cfg: DatasetConfig, cache_dir: str = os.path.join("data", "cache"),
                   use_cache: bool = True) -> Dict[str, SplitData]:
    """
    Train and validation drop queries whose candidate pool contains no engaged item (they carry
    no ranking signal). The test split keeps them so reported metrics include candidate-generation misses.
    """
    os.makedirs(cache_dir, exist_ok=True)
    key = cfg.cache_key()
    paths = {split: os.path.join(cache_dir, f"{split}_{key}.npz") for split in ("train", "val", "test")}
    if use_cache and all(os.path.exists(p) for p in paths.values()):
        logger.info(f"Loading cached datasets ({key}) from {cache_dir}.")
        return {split: SplitData.load(p) for split, p in paths.items()}

    users = _UserIndex(events)
    periods = split_periods(events, cfg)
    rng = np.random.default_rng(cfg.seed)
    splits = {}
    for split, period in periods.items():
        logger.info(f"Building {split} split ({_fmt(period[0])} to {_fmt(period[1])})...")
        splits[split] = build_split(events, users, period, cfg.max_queries[split],
                                    drop_unanswerable=split != "test", cfg=cfg, rng=rng)
        splits[split].save(paths[split])
    return splits


def _fmt(ms: int) -> str:
    return str(np.datetime64(ms, "ms").astype("datetime64[D]"))
