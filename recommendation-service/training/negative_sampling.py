import logging
import numpy as np
import pandas as pd
from typing import Tuple, List, Set

logger = logging.getLogger("training")


def generate_positive_negative_samples(
    interactions_df: pd.DataFrame,
    negative_ratio: int = 3
) -> pd.DataFrame:
    """
    Generates positive interaction samples (label=1) and performs negative sampling (label=0).
    Configurable negative_ratio: default 1 positive to 3 negatives.
    """
    if interactions_df.empty:
        return pd.DataFrame()

    all_items: List[str] = interactions_df["external_item_id"].unique().tolist()
    user_interacted_map: pd.Series = interactions_df.groupby("external_user_id")["external_item_id"].apply(set)

    records = []
    np.random.seed(42)

    for idx, row in interactions_df.iterrows():
        user_id = row["external_user_id"]
        item_id = row["external_item_id"]
        ts = row["timestamp"]

        # Positive sample
        records.append({
            "user_id": user_id,
            "item_id": item_id,
            "label": 1,
            "timestamp": ts,
            "interaction_type": row["interaction_type"]
        })

        # Negative sampling
        interacted_set: Set[str] = user_interacted_map.get(user_id, set())
        candidate_negatives = [i for i in all_items if i not in interacted_set]

        if candidate_negatives:
            num_negatives = min(negative_ratio, len(candidate_negatives))
            sampled_negs = np.random.choice(candidate_negatives, size=num_negatives, replace=False)
            for neg_item in sampled_negs:
                records.append({
                    "user_id": user_id,
                    "item_id": neg_item,
                    "label": 0,
                    "timestamp": ts,
                    "interaction_type": "NONE"
                })

    df = pd.DataFrame(records)
    df = df.sort_values(by="timestamp").reset_index(drop=True)
    logger.info(f"Generated {len(df)} samples (Positives: {(df['label']==1).sum()}, Negatives: {(df['label']==0).sum()}).")
    return df
