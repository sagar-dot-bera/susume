from typing import List, Optional
from pydantic import BaseModel, Field


class CandidateItem(BaseModel):
    """An item the backend's strategies proposed. Only the id is used: features are computed here."""
    itemId: str


class UserPayload(BaseModel):
    id: Optional[str] = None


class ContextPayload(BaseModel):
    surface: Optional[str] = "homepage"
    hour: Optional[int] = None
    dayOfWeek: Optional[int] = None


class RankRequest(BaseModel):
    tenantId: Optional[str] = None
    user: Optional[UserPayload] = None
    context: Optional[ContextPayload] = None
    candidates: List[CandidateItem] = Field(default_factory=list)
    limit: int = Field(default=10, ge=1)


class RankedItem(BaseModel):
    itemId: str
    score: float


class ModelMetadata(BaseModel):
    name: str = "susume-ranker"
    version: str = "v1"


class RankResponse(BaseModel):
    recommendations: List[RankedItem]
    model: ModelMetadata
    # "ml": model scores on training-identical features; "cold_start_popularity": the user has
    # no history yet, so items are ordered by tenant popularity.
    rankingMode: str
    candidateCount: int
