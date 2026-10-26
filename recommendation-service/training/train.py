import os
import json
import time
import argparse
import logging
from datetime import datetime
from dataclasses import asdict
from typing import Dict, List

import joblib
import numpy as np
import lightgbm as lgb
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler

from app.model.loader import DummyFallbackModel
from app.ranking.features import FEATURE_NAMES, FEATURE_VERSION
from training.build_dataset import DatasetConfig, SplitData, build_datasets, split_periods, COL, EVAL_K, _fmt
from training.evaluate import evaluate_grouped, format_results_table
from training.retailrocket import load_events, DEFAULT_DATA_DIR

logger = logging.getLogger("training")
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")

MODEL_NAME = "susume-ranker"
MODEL_VERSION = "v2"

# Single-strategy baselines: rank candidates by one generator's score.
BASELINES = {
    "Popularity": "popularityScore",
    "Trending (last 7 days)": "trendingScore",
    "Similar items (session co-occurrence)": "similarItemsScore",
    "Collaborative filtering (item-item)": "collaborativeFilteringScore",
    "Frequently bought together": "frequentlyBoughtTogetherScore",
    "Personalized (recency-weighted history)": "personalizedScore",
}

STRATEGY_FEATURES = [name for name in FEATURE_NAMES if name.endswith("Score")]

LGBM_PARAMS = dict(
    objective="lambdarank",
    n_estimators=1000,
    learning_rate=0.05,
    num_leaves=63,
    min_child_samples=100,
    subsample=0.8,
    subsample_freq=1,
    colsample_bytree=0.8,
    lambdarank_truncation_level=20,
    random_state=42,
    verbose=-1,
)


def _evaluate(scores: np.ndarray, split: SplitData) -> Dict[str, float]:
    return evaluate_grouped(scores, split.y, split.qid, split.idcg, split.n_rel, k=EVAL_K)


def train_ranker(train: SplitData, val: SplitData, features: List[str]) -> lgb.LGBMRanker:
    cols = [COL[f] for f in features]
    model = lgb.LGBMRanker(**LGBM_PARAMS)
    model.fit(
        train.X[:, cols], train.y,
        group=train.group_sizes,
        eval_X=(val.X[:, cols],),
        eval_y=(val.y,),
        eval_group=[val.group_sizes],
        eval_at=[EVAL_K],
        callbacks=[lgb.early_stopping(50, verbose=False), lgb.log_evaluation(100)],
    )
    return model


def train_and_save_model(args: argparse.Namespace):
    started = time.time()
    logger.info("Starting Susume ML Ranker training pipeline (RetailRocket)...")

    events = load_events(args.data_dir)
    cfg = DatasetConfig(max_queries={"train": args.train_queries, "val": args.val_queries, "test": args.test_queries})
    splits = build_datasets(events, cfg, use_cache=not args.rebuild)
    train, val, test = splits["train"], splits["val"], splits["test"]
    periods = split_periods(events, cfg)

    results: Dict[str, Dict[str, float]] = {}
    for name, feature in BASELINES.items():
        results[name] = _evaluate(test.X[:, COL[feature]], test)
    results["Hand-weighted blend (fallback heuristic)"] = _evaluate(DummyFallbackModel().predict_proba(test.X)[:, 1], test)

    logger.info("Training pointwise logistic regression...")
    logreg = make_pipeline(StandardScaler(), LogisticRegression(max_iter=1000))
    logreg.fit(train.X, (train.y > 0).astype(np.int32))
    results["Logistic regression (pointwise)"] = _evaluate(logreg.predict_proba(test.X)[:, 1], test)

    ablation = {}
    if not args.skip_ablation:
        logger.info("Training LightGBM ranker on strategy scores only (ablation)...")
        strategy_only = train_ranker(train, val, STRATEGY_FEATURES)
        ablation["Strategy scores only"] = _evaluate(strategy_only.predict(test.X[:, [COL[f] for f in STRATEGY_FEATURES]]), test)

    logger.info("Training LightGBM LambdaMART ranker on all features...")
    model = train_ranker(train, val, FEATURE_NAMES)
    ranker_metrics = _evaluate(model.predict(test.X), test)
    results["LightGBM LambdaMART (all features)"] = ranker_metrics
    if ablation:
        ablation["All features"] = ranker_metrics

    table = format_results_table(results, k=EVAL_K)
    print("\n" + table + "\n")
    print(f"Candidate recall (share of engaged items present in the candidate pool): {ranker_metrics['candidateRecall']:.4f}\n")

    gain = model.booster_.feature_importance(importance_type="gain")
    importance = sorted(zip(FEATURE_NAMES, gain / gain.sum()), key=lambda p: -p[1])
    importance = [(name, round(float(share), 4)) for name, share in importance if share > 0]

    best_baseline_name, best_baseline = max(
        ((n, m) for n, m in results.items() if not n.startswith("LightGBM")), key=lambda p: p[1][f"ndcgAt{EVAL_K}"]
    )
    beats_baseline = ranker_metrics[f"ndcgAt{EVAL_K}"] > best_baseline[f"ndcgAt{EVAL_K}"]
    if not beats_baseline and not args.force:
        logger.error(
            f"Ranker NDCG@{EVAL_K} {ranker_metrics[f'ndcgAt{EVAL_K}']:.4f} does not beat best baseline "
            f"'{best_baseline_name}' ({best_baseline[f'ndcgAt{EVAL_K}']:.4f}). Not saving; pass --force to override."
        )
        return

    models_dir = os.getenv("MODELS_DIR", "models")
    os.makedirs(models_dir, exist_ok=True)
    model_file_path = os.path.join(models_dir, f"susume_ranker_{MODEL_VERSION}.joblib")
    meta_file_path = os.path.join(models_dir, f"susume_ranker_{MODEL_VERSION}_meta.json")
    report_file_path = os.path.join(models_dir, f"susume_ranker_{MODEL_VERSION}_report.md")

    joblib.dump(model, model_file_path)
    logger.info(f"Saved trained model to {model_file_path}")

    dataset_info = {
        "source": "RetailRocket e-commerce events",
        "periods": {split: [_fmt(a), _fmt(b)] for split, (a, b) in periods.items()},
        "queries": {split: int(s.n_queries) for split, s in splits.items()},
        "rows": {split: int(len(s.y)) for split, s in splits.items()},
        "config": asdict(cfg),
    }
    metadata = {
        "name": MODEL_NAME,
        "version": MODEL_VERSION,
        "algorithm": "LightGBM LGBMRanker (lambdarank)",
        "featureVersion": FEATURE_VERSION,
        "featureNames": FEATURE_NAMES,
        "trainedAt": datetime.now().isoformat(),
        "bestIteration": int(model.best_iteration_ or LGBM_PARAMS["n_estimators"]),
        **{key: value for key, value in ranker_metrics.items()},
        "testResults": results,
        "ablation": ablation,
        "featureImportance": dict(importance),
        "dataset": dataset_info,
        "params": LGBM_PARAMS,
    }
    with open(meta_file_path, "w", encoding="utf-8") as f:
        json.dump(metadata, f, indent=2)
    logger.info(f"Saved model metadata to {meta_file_path}")

    with open(report_file_path, "w", encoding="utf-8") as f:
        f.write(_report(metadata, table))
    logger.info(f"Saved evaluation report to {report_file_path}")
    logger.info(f"Pipeline finished in {(time.time() - started) / 60:.1f} min.")


def _report(meta: Dict, table: str) -> str:
    ds = meta["dataset"]
    lines = [
        f"# {meta['name']} {meta['version']}: offline evaluation",
        "",
        f"Trained {meta['trainedAt'][:19]} on {ds['source']}. Feature version `{meta['featureVersion']}`.",
        "",
        "| Split | Period | Queries | Rows |",
        "|---|---|---|---|",
    ]
    for split, (start, end) in ds["periods"].items():
        lines.append(f"| {split} | {start} to {end} | {ds['queries'][split]:,} | {ds['rows'][split]:,} |")
    lines += [
        "",
        f"## Test results (per-query, k={EVAL_K})",
        "",
        table,
        "",
        f"Candidate recall: {meta['candidateRecall']:.4f} (share of engaged items that the candidate generators surfaced at all; "
        "no reranker can score above this ceiling).",
        "",
    ]
    if meta["ablation"]:
        lines += ["## Ablation", "", format_results_table(meta["ablation"], k=EVAL_K), ""]
    lines += ["## Feature importance (share of total gain)", "", "| Feature | Gain |", "|---|---|"]
    lines += [f"| {name} | {share:.4f} |" for name, share in list(meta["featureImportance"].items())[:15]]
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Train the Susume LightGBM ranker on RetailRocket.")
    parser.add_argument("--data-dir", default=DEFAULT_DATA_DIR)
    parser.add_argument("--train-queries", type=int, default=40000)
    parser.add_argument("--val-queries", type=int, default=8000)
    parser.add_argument("--test-queries", type=int, default=10000)
    parser.add_argument("--rebuild", action="store_true", help="Ignore cached datasets and rebuild features.")
    parser.add_argument("--skip-ablation", action="store_true")
    parser.add_argument("--force", action="store_true", help="Save the model even if it does not beat the best baseline.")
    train_and_save_model(parser.parse_args())
