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


def evaluate_grouped(scores: np.ndarray, y: np.ndarray, qid: np.ndarray, idcg: np.ndarray,
                     n_rel: np.ndarray, k: int = 10, seed: int = 0) -> Dict[str, float]:
    """
    Per-query ranking metrics averaged over queries.

    `y` holds graded relevance per candidate row (0 = not engaged), `qid` the query of each row,
    and `idcg` / `n_rel` the ideal DCG@k and number of engaged items per query computed over *all*
    engaged items, so items missing from the candidate pool count against every model.
    Ties are broken randomly so constant-score baselines are not flattered by row order.
    """
    n_queries = len(idcg)
    tie_break = np.random.default_rng(seed).random(len(scores))
    order = np.lexsort((tie_break, -scores, qid))
    q, rel = qid[order], y[order].astype(np.float64)

    group_start = np.searchsorted(q, np.arange(n_queries))
    rank = np.arange(len(q)) - group_start[q]
    top = rank < k

    gains = np.where(top, (2.0 ** rel - 1) / np.log2(rank + 2), 0.0)
    dcg = np.bincount(q, weights=gains, minlength=n_queries)
    hits = np.bincount(q, weights=(top & (rel > 0)).astype(np.float64), minlength=n_queries)
    in_pool = np.bincount(q, weights=(rel > 0).astype(np.float64), minlength=n_queries)

    first_hit = np.full(n_queries, np.inf)
    hit_rows = top & (rel > 0)
    np.minimum.at(first_hit, q[hit_rows], rank[hit_rows])

    safe_rel = np.maximum(n_rel, 1)
    return {
        f"ndcgAt{k}": round(float(np.mean(np.where(idcg > 0, dcg / np.maximum(idcg, 1e-12), 0.0))), 4),
        f"recallAt{k}": round(float(np.mean(hits / safe_rel)), 4),
        f"hitRateAt{k}": round(float(np.mean(hits > 0)), 4),
        f"mrrAt{k}": round(float(np.mean(1.0 / (first_hit + 1))), 4),
        "candidateRecall": round(float(np.mean(in_pool / safe_rel)), 4),
    }


def format_results_table(results: Dict[str, Dict[str, float]], k: int = 10) -> str:
    cols = [f"ndcgAt{k}", f"recallAt{k}", f"hitRateAt{k}", f"mrrAt{k}"]
    header = f"| Model | NDCG@{k} | Recall@{k} | HitRate@{k} | MRR@{k} |"
    lines = [header, "|---|---|---|---|---|"]
    for name, metrics in results.items():
        lines.append(f"| {name} | " + " | ".join(f"{metrics[c]:.4f}" for c in cols) + " |")
    return "\n".join(lines)


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
