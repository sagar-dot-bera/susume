import pytest
import numpy as np
from app.schemas.recommendation import CandidateItem, UserPayload, ContextPayload
from app.ranking.features import build_feature_vector, build_candidate_matrix, FEATURE_NAMES


def test_feature_vector_construction():
    candidate = CandidateItem(
        itemId="item-1",
        strategyScores={"contentBased": 0.85, "popularity": 0.40},
        itemFeatures={"itemAge": 10.0},
        userItemFeatures={"previousViews": 3}
    )
    user = UserPayload(id="u1", features={"totalViews": 20})
    context = ContextPayload(surface="homepage", hour=15, dayOfWeek=2)

    vec = build_feature_vector(candidate, user, context)
    assert isinstance(vec, np.ndarray)
    assert len(vec) == len(FEATURE_NAMES)
    # Check canonical strategy scores
    assert vec[0] == pytest.approx(0.85)  # contentBased
    assert vec[1] == pytest.approx(0.0)   # collaborativeFiltering (missing -> 0.0)
    assert vec[5] == pytest.approx(0.40)  # popularity


def test_cold_start_defaults():
    candidate = CandidateItem(itemId="item-cold")
    vec = build_feature_vector(candidate, None, None)
    assert len(vec) == len(FEATURE_NAMES)
    assert np.all(np.isfinite(vec))
    assert vec[28] == pytest.approx(-1.0)  # timeSinceLastInteraction default
