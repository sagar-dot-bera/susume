from dataclasses import dataclass

import numpy as np
import scipy.sparse as sp

from app.features.events import EventLog, EVENT_WEIGHTS, VIEW, CART, PURCHASE

DAY_MS = 24 * 60 * 60 * 1000

# Sessions/visitors with more distinct items than this are treated as bots or crawlers
# and left out of co-occurrence counts.
MAX_ITEMS_PER_BASKET = 50


@dataclass
class Snapshot:
    """
    Global item statistics computed only from events strictly before `cutoff`.

    This mirrors a nightly batch job in production: every query on a given day is served
    from the snapshot taken at midnight, so no statistic can contain information from
    the query's future.
    """
    cutoff: int
    popularity: np.ndarray      # normalized weighted interactions over the lookback window
    trending: np.ndarray        # normalized weighted interactions over the last 7 days
    all_time: np.ndarray        # weighted interactions over all history before cutoff
    recent_counts: np.ndarray   # (n_items, 3) views / carts / purchases over the last 7 days
    co_session: sp.csr_matrix   # item-item cosine co-occurrence within browsing sessions
    co_buy: sp.csr_matrix       # item-item cosine co-occurrence among carted/purchased items
    top_popular: np.ndarray
    top_trending: np.ndarray


def _cosine_cooccurrence(basket: np.ndarray, item: np.ndarray, n_items: int) -> sp.csr_matrix:
    """
    Builds an item x item matrix where entry (i, j) = |baskets with i and j| / sqrt(|baskets with i| * |baskets with j|).
    """
    if len(basket) == 0:
        return sp.csr_matrix((n_items, n_items), dtype=np.float32)

    # Unique (basket, item) pairs via a combined int64 key; much faster than np.unique(axis=0).
    keys = np.unique(basket.astype(np.int64) * n_items + item)
    pair_basket, pair_item = keys // n_items, keys % n_items
    _, basket_idx = np.unique(pair_basket, return_inverse=True)
    sizes = np.bincount(basket_idx)
    keep = (sizes[basket_idx] >= 2) & (sizes[basket_idx] <= MAX_ITEMS_PER_BASKET)
    basket_idx, pair_item = basket_idx[keep], pair_item[keep]
    if len(pair_item) == 0:
        return sp.csr_matrix((n_items, n_items), dtype=np.float32)

    m = sp.csr_matrix(
        (np.ones(len(pair_item), dtype=np.float32), (basket_idx, pair_item)),
        shape=(int(basket_idx.max()) + 1, n_items),
    )
    co = (m.T @ m).tocsr()
    counts = co.diagonal()
    co.setdiag(0)
    co.eliminate_zeros()

    norm = np.sqrt(np.maximum(counts, 1.0))
    rows = np.repeat(np.arange(n_items), np.diff(co.indptr))
    co.data = co.data / (norm[rows] * norm[co.indices])
    co.sort_indices()
    return co.astype(np.float32)


def _normalize(values: np.ndarray) -> np.ndarray:
    top = values.max() if len(values) else 0.0
    return (values / top).astype(np.float32) if top > 0 else values.astype(np.float32)


def build_snapshot(events: EventLog, cutoff: int, lookback_days: int = 42, top_n: int = 20) -> Snapshot:
    n = events.n_items
    past = events.ts < cutoff
    window = past & (events.ts >= cutoff - lookback_days * DAY_MS)
    recent = past & (events.ts >= cutoff - 7 * DAY_MS)
    weights = EVENT_WEIGHTS[events.etype]

    all_time = np.bincount(events.item[past], weights=weights[past], minlength=n).astype(np.float32)
    popularity = np.bincount(events.item[window], weights=weights[window], minlength=n)
    trending = np.bincount(events.item[recent], weights=weights[recent], minlength=n)

    recent_counts = np.zeros((n, 3), dtype=np.float32)
    for code in (VIEW, CART, PURCHASE):
        mask = recent & (events.etype == code)
        recent_counts[:, code] = np.bincount(events.item[mask], minlength=n)

    co_session = _cosine_cooccurrence(events.session[window], events.item[window], n)

    buy = window & (events.etype != VIEW)
    co_buy = _cosine_cooccurrence(events.visitor[buy], events.item[buy], n)

    return Snapshot(
        cutoff=cutoff,
        popularity=_normalize(popularity),
        trending=_normalize(trending),
        all_time=all_time,
        recent_counts=recent_counts,
        co_session=co_session,
        co_buy=co_buy,
        top_popular=np.argsort(-popularity, kind="stable")[:top_n],
        top_trending=np.argsort(-trending, kind="stable")[:top_n],
    )
