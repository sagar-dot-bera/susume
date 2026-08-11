import os
import logging
import pandas as pd
import numpy as np
from typing import Optional
from datetime import datetime, timedelta

logger = logging.getLogger("training")


def load_historical_interactions(db_url: Optional[str] = None) -> pd.DataFrame:
    """
    Loads historical interaction dataset from PostgreSQL or generates a structured
    synthetic interaction dataset for baseline model training.
    """
    if db_url is None:
        db_name = os.getenv("POSTGRES_DB", "susume")
        db_user = os.getenv("POSTGRES_USER", "postgres")
        db_pass = os.getenv("POSTGRES_PASSWORD", "postgres")
        db_host = os.getenv("POSTGRES_HOST", "postgres")
        db_port = os.getenv("POSTGRES_PORT", "5432")
        db_url = f"postgresql://{db_user}:{db_pass}@{db_host}:{db_port}/{db_name}"

    try:
        df = pd.read_sql_query(
            "SELECT id, tenant_id, external_user_id, external_item_id, interaction_type, timestamp FROM interactions ORDER BY timestamp ASC",
            db_url
        )
        if not df.empty:
            logger.info(f"Loaded {len(df)} historical interactions from database.")
            return df
    except Exception as e:
        logger.warning(f"Could not load interactions from database ({e}). Generating synthetic interaction dataset.")

    # Generate synthetic historical interaction dataset for training
    np.random.seed(42)
    n_users = 50
    n_items = 100
    n_interactions = 500

    users = [f"user-{i:03d}" for i in range(1, n_users + 1)]
    items = [f"item-{i:03d}" for i in range(1, n_items + 1)]
    types = ["VIEW", "CLICK", "LIKE", "PURCHASE"]
    weights = [0.5, 0.3, 0.15, 0.05]

    start_date = datetime.now() - timedelta(days=60)
    data = []
    for i in range(n_interactions):
        user_id = np.random.choice(users)
        item_id = np.random.choice(items)
        itype = np.random.choice(types, p=weights)
        ts = start_date + timedelta(minutes=int(np.random.randint(0, 60 * 24 * 60)))
        data.append({
            "id": f"interaction-{i}",
            "tenant_id": "00000000-0000-0000-0000-000000000000",
            "external_user_id": user_id,
            "external_item_id": item_id,
            "interaction_type": itype,
            "timestamp": ts
        })

    df = pd.DataFrame(data)
    df = df.sort_values(by="timestamp").reset_index(drop=True)
    logger.info(f"Generated synthetic historical dataset with {len(df)} interactions.")
    return df
