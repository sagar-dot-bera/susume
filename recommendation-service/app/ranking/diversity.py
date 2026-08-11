from typing import List
from app.schemas.recommendation import RankedItem


def apply_diversity_rerank(items: List[RankedItem], limit: int) -> List[RankedItem]:
    """
    Applies optional recommendation diversity rules.
    First implementation retains exact model score ranking while enforcing Top-K limit.
    """
    return items[:limit]
