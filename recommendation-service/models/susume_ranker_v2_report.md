# susume-ranker v2: offline evaluation

Trained 2026-10-26T01:40:30 on RetailRocket e-commerce events. Feature version `v2`.

| Split | Period | Queries | Rows |
|---|---|---|---|
| train | 2015-07-10 to 2015-08-21 | 18,925 | 1,070,939 |
| val | 2015-08-21 to 2015-09-04 | 3,858 | 209,643 |
| test | 2015-09-04 to 2015-09-18 | 10,000 | 527,601 |

## Test results (per-query, k=10)

| Model | NDCG@10 | Recall@10 | HitRate@10 | MRR@10 |
|---|---|---|---|---|
| Popularity | 0.0043 | 0.0082 | 0.0147 | 0.0040 |
| Trending (last 7 days) | 0.0048 | 0.0073 | 0.0144 | 0.0055 |
| Similar items (session co-occurrence) | 0.1312 | 0.1975 | 0.2591 | 0.1293 |
| Collaborative filtering (item-item) | 0.1358 | 0.2065 | 0.2728 | 0.1328 |
| Frequently bought together | 0.0379 | 0.0707 | 0.1059 | 0.0363 |
| Personalized (recency-weighted history) | 0.1697 | 0.1931 | 0.2673 | 0.1994 |
| Hand-weighted blend (fallback heuristic) | 0.1425 | 0.2240 | 0.3035 | 0.1399 |
| Logistic regression (pointwise) | 0.2224 | 0.2812 | 0.3684 | 0.2420 |
| LightGBM LambdaMART (all features) | 0.2602 | 0.3252 | 0.4174 | 0.2800 |

Candidate recall: 0.3655 (share of engaged items that the candidate generators surfaced at all; no reranker can score above this ceiling).

## Ablation

| Model | NDCG@10 | Recall@10 | HitRate@10 | MRR@10 |
|---|---|---|---|---|
| Strategy scores only | 0.2580 | 0.3247 | 0.4169 | 0.2769 |
| All features | 0.2602 | 0.3252 | 0.4174 | 0.2800 |

## Feature importance (share of total gain)

| Feature | Gain |
|---|---|
| collaborativeFilteringScore | 0.2942 |
| personalizedScore | 0.2687 |
| user_item_timeSinceLastInteraction | 0.0944 |
| similarItemsScore | 0.0922 |
| popularityScore | 0.0534 |
| item_recentViews | 0.0480 |
| item_itemPopularity | 0.0284 |
| user_item_previousViews | 0.0203 |
| trendingScore | 0.0199 |
| user_totalViews | 0.0146 |
| user_interactionsLast30Days | 0.0132 |
| user_interactionsLast7Days | 0.0131 |
| user_interactionsLast24Hours | 0.0111 |
| item_itemAge | 0.0070 |
| frequentlyBoughtTogetherScore | 0.0040 |
