import logging
import os

from fastapi import APIRouter, HTTPException

from app.model.loader import ModelLoader
from app.ranking.ranker import MLRanker, RankingUnavailable
from app.schemas.recommendation import RankRequest, RankResponse
from app.serving.feature_store import FeatureStore, FeaturesUnavailable
from app.serving.interaction_client import InteractionClient

logger = logging.getLogger("recommendation_service")

router = APIRouter(prefix="/api/v1/recommendations", tags=["Recommendations"])


def _build_ranker() -> MLRanker:
    model_loader = ModelLoader()
    url = os.getenv("INTERACTION_SERVICE_URL", "http://localhost:8003")
    token = os.getenv("INTERNAL_API_TOKEN")
    if not token:
        logger.warning("INTERNAL_API_TOKEN is not set: /rank will return 503 and the backend will use its own ordering.")
        return MLRanker(model_loader, None, None)

    client = InteractionClient(url, token)
    config = (model_loader.metadata.get("dataset") or {}).get("config") or {}
    store = FeatureStore(
        client,
        lookback_days=config.get("lookback_days", 42),
        top_n=config.get("candidates_per_strategy", 20),
        ttl_s=float(os.getenv("SNAPSHOT_TTL_SECONDS", "3600")),
    )
    return MLRanker(model_loader, store, client)


ranker = _build_ranker()


@router.post("/rank", response_model=RankResponse, responses={503: {"description": "Features not available yet"}})
def rank_candidates(request: RankRequest) -> RankResponse:
    """
    Re-rank candidates with the trained model. Features are computed here from raw events,
    with the same code as training. 503 means the backend should keep its own ordering.
    """
    try:
        return ranker.rank(request)
    except (RankingUnavailable, FeaturesUnavailable) as e:
        raise HTTPException(status_code=503, detail=str(e))
    except Exception as e:
        logger.exception("Ranking failed")
        raise HTTPException(status_code=503, detail=f"Ranking failed: {e}")
