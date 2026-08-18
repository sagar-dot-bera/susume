import logging
import pandas as pd
import numpy as np
from typing import Any, Optional, cast
from datetime import datetime, timedelta

logger = logging.getLogger("training")


def load_historical_interactions(db_url: Optional[str] = None) -> pd.DataFrame:
    """
    Loads historical interaction dataset from PostgreSQL using SQLAlchemy ORM or generates
    a structured synthetic interaction dataset for baseline model training.
    """
    try:
        from sqlalchemy import select, create_engine
        from app.db import Interaction, sync_engine

        engine = create_engine(db_url, pool_pre_ping=True) if db_url else sync_engine
        try:
            with engine.connect() as conn:
                stmt = select(
                    Interaction.id,
                    Interaction.tenant_id,
                    Interaction.external_user_id,
                    Interaction.external_item_id,
                    Interaction.interaction_type,
                    Interaction.timestamp
                ).order_by(Interaction.timestamp.asc())
                df = pd.read_sql_query(cast(Any, stmt),cast(Any,conn))
        finally:
            if db_url:
                engine.dispose()

        if df is not None and not df.empty:
            logger.info(f"Loaded {len(df)} historical interactions from database via SQLAlchemy.")
            return df
    except Exception as e:
        logger.warning(f"Could not load interactions from database via SQLAlchemy ({e}). Generating synthetic interaction dataset.")

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

