from typing import List, Dict, Any, Optional
import numpy as np
from app.schemas.recommendation import CandidateItem, UserPayload, ContextPayload


FEATURE_NAMES: List[str] = [
    # 10 Strategy features
    "contentBasedScore",
    "collaborativeFilteringScore",
    "frequentlyBoughtTogetherScore",
    "hybridScore",
    "personalizedScore",
    "popularityScore",
    "randomDiscoveryScore",
    "ruleBasedScore",
    "similarItemsScore",
    "trendingScore",
    # 8 User features
    "user_totalViews",
    "user_totalClicks",
    "user_totalLikes",
    "user_totalPurchases",
    "user_totalInteractions",
    "user_interactionsLast24Hours",
    "user_interactionsLast7Days",
    "user_interactionsLast30Days",
    # 6 Item features
    "item_itemPopularity",
    "item_recentViews",
    "item_recentClicks",
    "item_recentLikes",
    "item_recentPurchases",
    "item_itemAge",
    # 5 User-Item features
    "user_item_previousViews",
    "user_item_previousClicks",
    "user_item_previousLikes",
    "user_item_previousPurchases",
    "user_item_timeSinceLastInteraction",
    # 3 Context features
    "context_surfaceCode",
    "context_hour",
    "context_dayOfWeek",
]

SURFACE_MAP: Dict[str, float] = {
    "homepage": 1.0,
    "product_detail": 2.0,
    "cart": 3.0,
    "search": 4.0,
    "category": 5.0,
}


def build_feature_vector(
    candidate: CandidateItem,
    user: Optional[UserPayload] = None,
    context: Optional[ContextPayload] = None
) -> np.ndarray:
    """
    Constructs a 1D numpy feature vector for a candidate item given user & context signals.
    Missing numeric signals are defaulted deterministically.
    """
    strat = candidate.strategyScores or {}
    uf = (user.features if user else {}) or {}
    itf = candidate.itemFeatures or {}
    uif = candidate.userItemFeatures or {}
    ctx_surface = context.surface if context else "homepage"
    ctx_hour = float(context.hour if (context and context.hour is not None) else 12)
    ctx_dow = float(context.dayOfWeek if (context and context.dayOfWeek is not None) else 1)

    surface_code = SURFACE_MAP.get(str(ctx_surface).lower(), 0.0)

    vec = [
        # Strategy Scores
        float(strat.get("contentBased", 0.0)),
        float(strat.get("collaborativeFiltering", 0.0)),
        float(strat.get("frequentlyBoughtTogether", 0.0)),
        float(strat.get("hybrid", 0.0)),
        float(strat.get("personalized", 0.0)),
        float(strat.get("popularity", 0.0)),
        float(strat.get("randomDiscovery", 0.0)),
        float(strat.get("ruleBased", 0.0)),
        float(strat.get("similarItems", 0.0)),
        float(strat.get("trending", 0.0)),
        # User Features
        float(uf.get("totalViews", 0)),
        float(uf.get("totalClicks", 0)),
        float(uf.get("totalLikes", 0)),
        float(uf.get("totalPurchases", 0)),
        float(uf.get("totalInteractions", 0)),
        float(uf.get("interactionsLast24Hours", 0)),
        float(uf.get("interactionsLast7Days", 0)),
        float(uf.get("interactionsLast30Days", 0)),
        # Item Features
        float(itf.get("itemPopularity", 0.0)),
        float(itf.get("recentViews", 0)),
        float(itf.get("recentClicks", 0)),
        float(itf.get("recentLikes", 0)),
        float(itf.get("recentPurchases", 0)),
        float(itf.get("itemAge", 0.0)),
        # User-Item Features
        float(uif.get("previousViews", 0)),
        float(uif.get("previousClicks", 0)),
        float(uif.get("previousLikes", 0)),
        float(uif.get("previousPurchases", 0)),
        float(uif.get("timeSinceLastInteraction", -1.0)),
        # Context Features
        surface_code,
        ctx_hour,
        ctx_dow,
    ]

    return np.array(vec, dtype=np.float32)


def build_candidate_matrix(
    candidates: List[CandidateItem],
    user: Optional[UserPayload] = None,
    context: Optional[ContextPayload] = None
) -> np.ndarray:
    if not candidates:
        return np.empty((0, len(FEATURE_NAMES)), dtype=np.float32)
    rows = [build_feature_vector(c, user, context) for c in candidates]
    return np.vstack(rows)
