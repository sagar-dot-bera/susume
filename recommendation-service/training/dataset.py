import os
import logging
import pandas as pd
import numpy as np
import httpx
from typing import Optional
from datetime import datetime, timedelta

logger = logging.getLogger("training")

DEFAULT_PAGE_LIMIT = 500


def load_historical_interactions(backend_url: Optional[str] = None, api_key: Optional[str] = None) -> pd.DataFrame:
    """
    Loads historical interaction dataset by paging through the Spring Boot backend's
    interaction export API, or generates a structured synthetic interaction dataset
    for baseline model training if the backend is unavailable.

    The recommendation-service holds no database connection of its own; all interaction
    data lives in and is served by the Spring Boot backend.
    """
    backend_url = backend_url or os.getenv("BACKEND_SERVICE_URL", "http://localhost:8080")
    api_key = api_key or os.getenv("BACKEND_API_KEY")

    try:
        if not api_key:
            raise RuntimeError("BACKEND_API_KEY is not set; cannot authenticate with backend export API.")

        records = []
        page = 0
        with httpx.Client(base_url=backend_url, headers={"X-API-KEY": api_key}, timeout=30.0) as client:
            while True:
                response = client.get(
                    "/api/v1/interactions/export",
                    params={"page": page, "limit": DEFAULT_PAGE_LIMIT},
                )
                response.raise_for_status()
                body = response.json()
                records.extend(body["data"])

                if page + 1 >= body["totalPages"]:
                    break
                page += 1

        df = pd.DataFrame(records)
        if not df.empty:
            df["timestamp"] = pd.to_datetime(df["timestamp"])
            df = df.rename(columns={
                "externalUserId": "external_user_id",
                "externalItemId": "external_item_id",
                "interactionType": "interaction_type",
            })
            df = df.sort_values(by="timestamp").reset_index(drop=True)
            logger.info(f"Loaded {len(df)} historical interactions from backend export API.")
            return df
    except Exception as e:
        logger.warning(f"Could not load interactions from backend export API ({e}). Generating synthetic interaction dataset.")

    # Generate synthetic historical interaction dataset for training
    rng = np.random.default_rng(42)
    n_users = 50
    n_items = 100
    n_interactions = 500

    users = [f"user-{i:03d}" for i in range(1, n_users + 1)]
    items = [f"item-{i:03d}" for i in range(1, n_items + 1)]
    types = ["VIEW", "CLICK", "LIKE", "PURCHASE"]
    weights = [0.5, 0.3, 0.15, 0.05]

    start_date = datetime.now() - timedelta(days=60)

    user_choices = rng.choice(users, size=n_interactions)
    item_choices = rng.choice(items, size=n_interactions)
    type_choices = rng.choice(types, size=n_interactions, p=weights)
    minute_offsets = rng.integers(0, 60 * 24 * 60, size=n_interactions)

    data = [
        {
            "id": f"interaction-{i}",
            "tenant_id": "00000000-0000-0000-0000-000000000000",
            "external_user_id": str(user_choices[i]),
            "external_item_id": str(item_choices[i]),
            "interaction_type": str(type_choices[i]),
            "timestamp": start_date + timedelta(minutes=int(minute_offsets[i]))
        }
        for i in range(n_interactions)
    ]

    df = pd.DataFrame(data)
    df = df.sort_values(by="timestamp").reset_index(drop=True)
    logger.info(f"Generated synthetic historical dataset with {len(df)} interactions.")
    return df
