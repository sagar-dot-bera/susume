from typing import Dict, List


# Bump whenever FEATURE_NAMES changes; models record the version they were trained on
# and the loader refuses a model whose feature list does not match.
FEATURE_VERSION = "v2"

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
    # v2: cart signals (appended so v1 feature indices stay stable)
    "user_totalCarts",
    "item_recentCarts",
    "user_item_previousCarts",
]

SURFACE_MAP: Dict[str, float] = {
    "homepage": 1.0,
    "product_detail": 2.0,
    "cart": 3.0,
    "search": 4.0,
    "category": 5.0,
}
