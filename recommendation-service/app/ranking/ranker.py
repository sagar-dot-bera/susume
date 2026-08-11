import logging
import numpy as np
from typing import List, Tuple
from app.schemas.recommendation import RankRequest, RankResponse, RankedItem, ModelMetadata
from app.ranking.features import build_candidate_matrix
from app.ranking.diversity import apply_diversity_rerank
from app.model.loader import ModelLoader

logger = logging.getLogger("recommendation_service")


class MLRanker:
    def __init__(self, model_loader: ModelLoader):
        self.model_loader = model_loader

    def rank(self, request: RankRequest) -> RankResponse:
        candidates = request.candidates
        if not candidates:
            model_obj, meta = self.model_loader.get_model()
            return RankResponse(
                recommendations=[],
                model=ModelMetadata(
                    name=meta.get("name", "susume-ranker"),
                    version=meta.get("version", "v1")
                )
            )

        # Build feature matrix
        X = build_candidate_matrix(candidates, request.user, request.context)
        model, meta = self.model_loader.get_model()

        try:
            if hasattr(model, "predict_proba"):
                probs = model.predict_proba(X)
                # Probabilities of positive class (column 1 if 2D, else 1D)
                if probs.ndim == 2 and probs.shape[1] >= 2:
                    scores = probs[:, 1]
                else:
                    scores = probs.flatten()
            elif hasattr(model, "predict"):
                scores = model.predict(X).astype(float)
            else:
                scores = np.zeros(len(candidates))
        except Exception as e:
            logger.error(f"Error during model prediction: {e}. Falling back to default scoring.")
            scores = np.array([float(c.strategyScores.get("popularity", 0.0)) for c in candidates])

        # Pair candidates with predicted scores
        ranked_list: List[RankedItem] = []
        for candidate, score in zip(candidates, scores):
            ranked_list.append(RankedItem(itemId=candidate.itemId, score=float(score)))

        # Sort descending by predicted score
        ranked_list.sort(key=lambda x: x.score, reverse=True)

        # Apply optional diversity re-ranking & Top-K limit
        final_recommendations = apply_diversity_rerank(ranked_list, request.limit)

        return RankResponse(
            recommendations=final_recommendations,
            model=ModelMetadata(
                name=meta.get("name", "susume-ranker"),
                version=meta.get("version", "v1")
            )
        )
