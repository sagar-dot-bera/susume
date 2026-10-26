from dataclasses import dataclass

import numpy as np
import pandas as pd

# Event type codes the ranker is trained on.
VIEW, CART, PURCHASE = 0, 1, 2

# Same weights as the backend's InteractionType enum (VIEW=1, CART=7, PURCHASE=10).
EVENT_WEIGHTS = np.array([1.0, 7.0, 10.0], dtype=np.float32)

# Susume interaction types -> ranker event codes. CLICK, LIKE and SHARE have no
# counterpart in the training data, so they count as views.
INTERACTION_TYPE_CODES = {
    "VIEW": VIEW,
    "CLICK": VIEW,
    "LIKE": VIEW,
    "SHARE": VIEW,
    "CART": CART,
    "PURCHASE": PURCHASE,
}

SESSION_GAP_MS = 30 * 60 * 1000


@dataclass
class EventLog:
    """
    Time-sorted (by visitor, then timestamp) event arrays. Items are re-indexed to 0..n_items-1.
    """
    ts: np.ndarray        # int64, unix ms
    visitor: np.ndarray   # int64
    item: np.ndarray      # int32 item index
    etype: np.ndarray     # int8 event code
    session: np.ndarray   # int64 global session id
    item_ids: np.ndarray  # item index -> original item id
    first_seen: np.ndarray  # item index -> first timestamp the item appears

    @property
    def n_items(self) -> int:
        return len(self.item_ids)

    @property
    def t_min(self) -> int:
        return int(self.ts.min())

    @property
    def t_max(self) -> int:
        return int(self.ts.max())


def build_event_log(df: pd.DataFrame) -> EventLog:
    """
    Builds an EventLog from a frame with columns timestamp (unix ms), visitor (int), item, etype.
    Duplicate rows should already be dropped.
    """
    df = df.sort_values(["visitor", "timestamp"], kind="stable").reset_index(drop=True)

    item_codes, item_ids = pd.factorize(df["item"], sort=True)
    ts = df["timestamp"].to_numpy(np.int64)
    visitor = df["visitor"].to_numpy(np.int64)
    item = item_codes.astype(np.int32)

    # A new session starts on a visitor change or after 30 minutes of inactivity.
    new_session = np.ones(len(df), dtype=bool)
    new_session[1:] = (visitor[1:] != visitor[:-1]) | (np.diff(ts) > SESSION_GAP_MS)
    session = np.cumsum(new_session) - 1

    first_seen = np.full(len(item_ids), np.iinfo(np.int64).max, dtype=np.int64)
    np.minimum.at(first_seen, item, ts)

    return EventLog(
        ts=ts,
        visitor=visitor,
        item=item,
        etype=df["etype"].to_numpy(np.int8),
        session=session,
        item_ids=np.asarray(item_ids),
        first_seen=first_seen,
    )
