import logging
import threading
import time
from concurrent.futures import Executor, ThreadPoolExecutor
from dataclasses import dataclass
from typing import Callable, Dict, Iterable, List, Optional

import numpy as np
import pandas as pd

from app.features.events import INTERACTION_TYPE_CODES, build_event_log
from app.features.snapshot import Snapshot, build_snapshot
from app.serving.interaction_client import InteractionClient, RawEvent

logger = logging.getLogger("recommendation_service")


class FeaturesUnavailable(Exception):
    """No snapshot for the tenant yet (still building, or the build failed)."""


@dataclass
class TenantFeatures:
    snapshot: Snapshot
    first_seen: np.ndarray
    item_ids: np.ndarray          # snapshot item index -> external item id
    item_index: Dict[str, int]    # external item id -> snapshot item index
    built_at: float
    n_events: int

    @property
    def n_items(self) -> int:
        return len(self.item_ids)


def events_frame(events: Iterable[RawEvent]) -> pd.DataFrame:
    """Raw events -> the frame build_event_log expects; unknown interaction types are dropped."""
    rows = [(e.timestamp_ms, e.user_id, e.item_id, INTERACTION_TYPE_CODES[e.interaction_type])
            for e in events if e.interaction_type in INTERACTION_TYPE_CODES]
    df = pd.DataFrame(rows, columns=["timestamp", "user", "item", "etype"])
    df["visitor"] = pd.factorize(df["user"])[0].astype(np.int64)
    df["etype"] = df["etype"].astype(np.int8)
    df["timestamp"] = df["timestamp"].astype(np.int64)
    return df.drop_duplicates(["timestamp", "visitor", "etype", "item"])[["timestamp", "visitor", "item", "etype"]]


def build_tenant_features(events: List[RawEvent], cutoff_ms: int, lookback_days: int, top_n: int,
                          built_at: float) -> TenantFeatures:
    log = build_event_log(events_frame(events))
    snapshot = build_snapshot(log, cutoff_ms, lookback_days, top_n)
    item_ids = log.item_ids.astype(str)
    return TenantFeatures(
        snapshot=snapshot,
        first_seen=log.first_seen,
        item_ids=item_ids,
        item_index={item: i for i, item in enumerate(item_ids)},
        built_at=built_at,
        n_events=len(log.ts),
    )


class FeatureStore:
    """
    Per-tenant item statistics (the training pipeline's Snapshot), rebuilt from
    interaction-service in the background every `ttl_s` seconds. Requests never wait on a
    build: a tenant's first request triggers one and gets FeaturesUnavailable; after that
    requests use the latest snapshot while refreshes run.
    """

    def __init__(self, client: InteractionClient, lookback_days: int, top_n: int, ttl_s: float,
                 executor: Optional[Executor] = None, clock: Callable[[], float] = time.time):
        self._client = client
        self._lookback_days = lookback_days
        self._top_n = top_n
        self._ttl_s = ttl_s
        self._executor = executor or ThreadPoolExecutor(max_workers=2, thread_name_prefix="snapshot")
        self._clock = clock
        self._entries: Dict[str, TenantFeatures] = {}
        self._building: set = set()
        self._lock = threading.Lock()

    def get(self, tenant_id: str) -> TenantFeatures:
        entry = self._entries.get(tenant_id)
        if entry is None or self._clock() - entry.built_at >= self._ttl_s:
            self._schedule(tenant_id)
        if entry is None:
            raise FeaturesUnavailable(f"Feature snapshot for tenant {tenant_id} is being built")
        return entry

    def refresh(self, tenant_id: str) -> TenantFeatures:
        """Build and store a snapshot synchronously."""
        now = self._clock()
        events = list(self._client.tenant_events(tenant_id))
        # Cutoff just after "now" so every event fetched so far is included.
        entry = build_tenant_features(events, int(now * 1000) + 1, self._lookback_days, self._top_n, built_at=now)
        self._entries[tenant_id] = entry
        logger.info(f"Built feature snapshot for tenant {tenant_id}: {entry.n_events} events, {entry.n_items} items "
                    f"in {self._clock() - now:.2f}s")
        return entry

    def _schedule(self, tenant_id: str) -> None:
        with self._lock:
            if tenant_id in self._building:
                return
            self._building.add(tenant_id)
        self._executor.submit(self._refresh_in_background, tenant_id)

    def _refresh_in_background(self, tenant_id: str) -> None:
        try:
            self.refresh(tenant_id)
        except Exception:
            # Keep serving the previous snapshot (if any); the next request retries.
            logger.exception(f"Failed to build feature snapshot for tenant {tenant_id}")
        finally:
            with self._lock:
                self._building.discard(tenant_id)
