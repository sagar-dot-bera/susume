import numpy as np
import pandas as pd
from typing import Dict, Any


def compute_dcg(relevance_scores: np.ndarray, k: int) -> float:
    relevance_scores = np.asarray(relevance_scores)[:k]
    if not relevance_scores.size:
        return 0.0
    discounts = np.log2(np.arange(2, relevance_scores.size + 2))
    return float(np.sum((2 ** relevance_scores - 1) / discounts))


def compute_ndcg_at_k(actual_labels: np.ndarray, predicted_scores: np.ndarray, k: int = 10) -> float:
    if len(actual_labels) == 0:
        return 0.0
    order = np.argsort(predicted_scores)[::-1]
    sorted_labels = actual_labels[order]

    dcg = compute_dcg(sorted_labels, k)
    ideal_labels = np.sort(actual_labels)[::-1]
    idcg = compute_dcg(ideal_labels, k)

    if idcg == 0:
        return 0.0
    return dcg / idcg


def compute_recall_at_k(actual_labels: np.ndarray, predicted_scores: np.ndarray, k: int = 10) -> float:
    total_positives = np.sum(actual_labels == 1)
    if total_positives == 0:
        return 0.0

    order = np.argsort(predicted_scores)[::-1][:k]
    positives_in_top_k = np.sum(actual_labels[order] == 1)
    return float(positives_in_top_k / total_positives)


def compute_precision_at_k(actual_labels: np.ndarray, predicted_scores: np.ndarray, k: int = 10) -> float:
    if k <= 0:
        return 0.0
    order = np.argsort(predicted_scores)[::-1][:k]
    positives_in_top_k = np.sum(actual_labels[order] == 1)
    return float(positives_in_top_k / k)


def evaluate_model_performance(y_true: np.ndarray, y_pred_probs: np.ndarray, k: int = 10) -> Dict[str, float]:
    ndcg = compute_ndcg_at_k(y_true, y_pred_probs, k=k)
    recall = compute_recall_at_k(y_true, y_pred_probs, k=k)
    precision = compute_precision_at_k(y_true, y_pred_probs, k=k)

    return {
        f"ndcgAt{k}": round(ndcg, 4),
        f"recallAt{k}": round(recall, 4),
        f"precisionAt{k}": round(precision, 4)
    }


def print_evaluation_summary(model_name: str, version: str, metrics: Dict[str, float], k: int = 10):
    print("\n" + "="*45)
    print(f"  OFFLINE EVALUATION METRICS SUMMARY")
    print("="*45)
    print(f"Model:         {model_name}")
    print(f"Version:       {version}")
    print(f"NDCG@{k}:        {metrics.get(f'ndcgAt{k}', 0.0):.4f}")
    print(f"Recall@{k}:      {metrics.get(f'recallAt{k}', 0.0):.4f}")
    print(f"Precision@{k}:   {metrics.get(f'precisionAt{k}', 0.0):.4f}")
    print("="*45 + "\n")
