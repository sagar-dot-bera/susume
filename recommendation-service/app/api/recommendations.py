from fastapi import APIRouter, HTTPException, Depends
from app.schemas.recommendation import RankRequest, RankResponse
from app.model.loader import ModelLoader
from app.ranking.ranker import MLRanker

router = APIRouter(prefix="/api/v1/recommendations", tags=["Recommendations"])

model_loader = ModelLoader()
ranker = MLRanker(model_loader)


@router.post("/rank", response_model=RankResponse)
def rank_candidates(request: RankRequest) -> RankResponse:
    """
    Re-rank candidate items using trained ML model and contextual signals.
    """
    try:
        return ranker.rank(request)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Internal ranking error: {str(e)}")
