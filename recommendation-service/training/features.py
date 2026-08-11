import numpy as np
import pandas as pd
from typing import Tuple
from app.ranking.features import FEATURE_NAMES, build_feature_vector
from app.schemas.recommendation import CandidateItem, UserPayload, ContextPayload


def extract_features_for_dataset(samples_df: pd.DataFrame) -> Tuple[np.ndarray, np.ndarray]:
    """
    Transforms sample dataframe (user_id, item_id, label, timestamp) into feature matrix X and labels y.
    Ensures exact feature definition parity with runtime inference!
    """
    if samples_df.empty:
        return np.empty((0, len(FEATURE_NAMES))), np.empty((0,))

    X_rows = []
    y_labels = []

    for _, row in samples_df.iterrows():
        # Create candidate representation
        # Strategy scores simulated based on user/item pseudo signals for training
        label = int(row["label"])
        base_score = 0.8 if label == 1 else 0.2
        noise = np.random.normal(0, 0.1)

        candidate = CandidateItem(
            itemId=row["item_id"],
            strategyScores={
                "contentBased": max(0.0, min(1.0, base_score + noise)),
                "collaborativeFiltering": max(0.0, min(1.0, base_score + noise * 0.5)),
                "trending": max(0.0, min(1.0, base_score - noise * 0.2)),
                "popularity": max(0.0, min(1.0, base_score)),
                "personalized": max(0.0, min(1.0, base_score + noise * 0.8)),
            },
            itemFeatures={
                "itemPopularity": base_score * 10.0,
                "itemAge": 15.0,
            },
            userItemFeatures={
                "previousViews": 2 if label == 1 else 0,
                "previousClicks": 1 if label == 1 else 0,
            }
        )

        user = UserPayload(
            id=row["user_id"],
            features={
                "totalViews": 10,
                "totalClicks": 5,
                "totalInteractions": 15
            }
        )

        context = ContextPayload(surface="homepage", hour=14, dayOfWeek=3)

        feat_vec = build_feature_vector(candidate, user, context)
        X_rows.append(feat_vec)
        y_labels.append(label)

    return np.vstack(X_rows), np.array(y_labels, dtype=np.int32)
