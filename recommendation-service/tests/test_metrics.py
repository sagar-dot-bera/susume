import pytest
import numpy as np
from training.evaluate import (
    compute_dcg,
    compute_ndcg_at_k,
    compute_recall_at_k,
    compute_precision_at_k,
    evaluate_model_performance
)


def test_ndcg_calculation():
    actual = np.array([1, 0, 1, 0, 0])
    pred = np.array([0.9, 0.8, 0.7, 0.6, 0.5])
    ndcg = compute_ndcg_at_k(actual, pred, k=5)
    assert 0.0 <= ndcg <= 1.0
    assert ndcg > 0.5


def test_perfect_ndcg():
    actual = np.array([1, 1, 0, 0])
    pred = np.array([0.9, 0.8, 0.2, 0.1])
    ndcg = compute_ndcg_at_k(actual, pred, k=4)
    assert ndcg == pytest.approx(1.0)


def test_recall_and_precision_at_k():
    actual = np.array([1, 0, 1, 0, 1])
    pred = np.array([0.9, 0.8, 0.7, 0.6, 0.5])
    
    recall = compute_recall_at_k(actual, pred, k=3)
    precision = compute_precision_at_k(actual, pred, k=3)
    
    # Top 3 predicted items: indices 0 (act=1), 1 (act=0), 2 (act=1) -> 2 positives in top 3
    assert recall == pytest.approx(2.0 / 3.0)
    assert precision == pytest.approx(2.0 / 3.0)
