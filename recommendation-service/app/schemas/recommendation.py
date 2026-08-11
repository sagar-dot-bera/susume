from typing import Dict, List, Optional, Any
from pydantic import BaseModel, Field


class CandidateItem(BaseModel):
    itemId: str
    strategyScores: Dict[str, float] = Field(default_factory=dict)
    itemFeatures: Dict[str, Any] = Field(default_factory=dict)
    userItemFeatures: Dict[str, Any] = Field(default_factory=dict)


class UserPayload(BaseModel):
    id: Optional[str] = "anonymous"
    features: Dict[str, Any] = Field(default_factory=dict)


class ContextPayload(BaseModel):
    surface: Optional[str] = "homepage"
    hour: Optional[int] = 12
    dayOfWeek: Optional[int] = 1


class RankRequest(BaseModel):
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
