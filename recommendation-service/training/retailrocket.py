import os
import logging

import numpy as np
import pandas as pd

# Re-exported so training code has one import site for the event vocabulary.
from app.features.events import (  # noqa: F401
    EventLog, EVENT_WEIGHTS, SESSION_GAP_MS, VIEW, CART, PURCHASE, build_event_log,
)

logger = logging.getLogger("training")

DEFAULT_DATA_DIR = os.path.join("data", "retailrocket")

EVENT_CODES = {"view": VIEW, "addtocart": CART, "transaction": PURCHASE}

# Graded relevance used as the ranking label: 0 = no interaction, 1 = view, 2 = cart, 3 = purchase.
# NDCG gain is 2^rel - 1, i.e. 0 / 1 / 3 / 7.
EVENT_RELEVANCE = np.array([1, 2, 3], dtype=np.int8)


def load_events(data_dir: str = DEFAULT_DATA_DIR) -> EventLog:
    path = os.path.join(data_dir, "events.csv")
    if not os.path.exists(path):
        raise FileNotFoundError(
            f"{path} not found. Download the Retailrocket dataset from "
            "https://www.kaggle.com/datasets/retailrocket/ecommerce-dataset and place events.csv there."
        )

    df = pd.read_csv(path, usecols=["timestamp", "visitorid", "event", "itemid"])
    raw_rows = len(df)
    df = df.drop_duplicates(["timestamp", "visitorid", "event", "itemid"])
    df = pd.DataFrame({
        "timestamp": df["timestamp"],
        "visitor": df["visitorid"],
        "item": df["itemid"],
        "etype": df["event"].map(EVENT_CODES).astype(np.int8),
    })
    events = build_event_log(df)

    logger.info(
        f"Loaded {len(df):,} events ({raw_rows - len(df):,} duplicates dropped): "
        f"{len(np.unique(events.visitor)):,} visitors, {events.n_items:,} items, {events.session[-1] + 1:,} sessions, "
        f"{pd.to_datetime(events.t_min, unit='ms').date()} to {pd.to_datetime(events.t_max, unit='ms').date()}."
    )
    return events
