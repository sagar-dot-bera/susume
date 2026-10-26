import logging
import time
from typing import Callable, Dict, List, Optional

import numpy as np

from app.features.events import INTERACTION_TYPE_CODES
from app.features.query import QueryParams, take, feature_matrix, generate_candidates, user_signals
from app.model.loader import ModelLoader
from app.ranking.diversity import apply_diversity_rerank
from app.schemas.recommendation import RankRequest, RankResponse, RankedItem, ModelMetadata
from app.serving.feature_store import FeatureStore, TenantFeatures
from app.serving.interaction_client import InteractionClient

logger = logging.getLogger("recommendation_service")


class RankingUnavailable(Exception):
    """The request can't be ranked with model features; the caller should use its own ordering."""


class _ItemIndexer:
    """Snapshot item indices, extended with fresh indices for items newer than the snapshot."""

    def __init__(self, features: TenantFeatures):
        self._features = features
        self._extra: Dict[str, int] = {}
        self._extra_ids: List[str] = []

    def index(self, item_id: str) -> int:
        idx = self._features.item_index.get(item_id)
        if idx is None:
            idx = self._extra.get(item_id)
            if idx is None:
                idx = self._features.n_items + len(self._extra_ids)
                self._extra[item_id] = idx
                self._extra_ids.append(item_id)
        return idx

    def item_id(self, idx: int) -> str:
        n = self._features.n_items
        return str(self._features.item_ids[idx]) if idx < n else self._extra_ids[idx - n]


def query_params_from_metadata(meta: Dict) -> QueryParams:
    """Serve with the exact query settings the model was trained with."""
    cfg = (meta.get("dataset") or {}).get("config") or {}
    defaults = QueryParams()
    return QueryParams(
        history_items=cfg.get("history_items", defaults.history_items),
        personalized_half_life_hours=cfg.get("personalized_half_life_hours", defaults.personalized_half_life_hours),
        candidates_per_strategy=cfg.get("candidates_per_strategy", defaults.candidates_per_strategy),
    )


class MLRanker:
    def __init__(self, model_loader: ModelLoader, feature_store: Optional[FeatureStore],
                 interaction_client: Optional[InteractionClient], clock: Callable[[], float] = time.time):
        self.model_loader = model_loader
        self.feature_store = feature_store
        self.interaction_client = interaction_client
        self.clock = clock

    def rank(self, request: RankRequest) -> RankResponse:
        model, meta = self.model_loader.get_model()
        model_meta = ModelMetadata(name=meta.get("name", "susume-ranker"), version=meta.get("version", "v1"))

        if self.feature_store is None or self.interaction_client is None:
            raise RankingUnavailable("Feature store is not configured")
        user_id = request.user.id if request.user else None
        if not request.tenantId or not user_id:
            raise RankingUnavailable("tenantId and user.id are required")

        features = self.feature_store.get(request.tenantId)
        history = [e for e in self.interaction_client.user_events(request.tenantId, user_id)
                   if e.interaction_type in INTERACTION_TYPE_CODES]
        indexer = _ItemIndexer(features)
        backend_cands = np.array([indexer.index(c.itemId) for c in request.candidates], dtype=np.int64)

        if not history:
            # The model was only trained on users with history; order by tenant popularity instead.
            cands = np.unique(np.concatenate([backend_cands, features.snapshot.top_popular]).astype(np.int64))
            scores = take(features.snapshot.popularity, cands).astype(float)
            mode = "cold_start_popularity"
        else:
            params = query_params_from_metadata(meta)
            h_item = np.array([indexer.index(e.item_id) for e in history], dtype=np.int64)
            h_type = np.array([INTERACTION_TYPE_CODES[e.interaction_type] for e in history], dtype=np.int8)
            h_ts = np.array([e.timestamp_ms for e in history], dtype=np.int64)
            now_ms = max(int(self.clock() * 1000), int(h_ts.max()))

            sig = user_signals(features.snapshot, h_item, h_type, h_ts, now_ms, params)
            cands = np.unique(np.concatenate([generate_candidates(features.snapshot, sig, params), backend_cands]))
            cands = cands[cands != sig.anchor]
            X = feature_matrix(features.snapshot, features.first_seen, sig, cands)
            scores = self._score(model, X)
            mode = "ml"

        order = np.argsort(-scores, kind="stable")
        ranked = [RankedItem(itemId=indexer.item_id(int(cands[i])), score=float(scores[i])) for i in order]
        return RankResponse(
            recommendations=apply_diversity_rerank(ranked, request.limit),
            model=model_meta,
            rankingMode=mode,
            candidateCount=len(cands),
        )

    @staticmethod
    def _score(model, X: np.ndarray) -> np.ndarray:
        if len(X) == 0:
            return np.zeros(0)
        if hasattr(model, "predict_proba"):
            probs = model.predict_proba(X)
            return (probs[:, 1] if probs.ndim == 2 and probs.shape[1] >= 2 else probs.ravel()).astype(float)
        return np.asarray(model.predict(X), dtype=float)
