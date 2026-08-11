import os
import json
import logging
from datetime import datetime
import joblib
import numpy as np
from sklearn.linear_model import LogisticRegression
from training.dataset import load_historical_interactions
from training.negative_sampling import generate_positive_negative_samples
from training.features import extract_features_for_dataset
from training.evaluate import evaluate_model_performance, print_evaluation_summary

logger = logging.getLogger("training")
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")


def train_and_save_model():
    logger.info("Starting Susume ML Ranker training pipeline...")

    # 1. Load historical interactions
    interactions_df = load_historical_interactions()

    # 2. Negative sampling
    samples_df = generate_positive_negative_samples(interactions_df, negative_ratio=3)

    if samples_df.empty:
        logger.error("No training samples available.")
        return

    # 3. Temporal Train/Test Split (80% train, 20% test based on timestamp)
    samples_df = samples_df.sort_values(by="timestamp").reset_index(drop=True)
    split_idx = int(len(samples_df) * 0.8)

    train_df = samples_df.iloc[:split_idx]
    test_df = samples_df.iloc[split_idx:]

    logger.info(f"Temporal Split: Train set = {len(train_df)} samples, Test set = {len(test_df)} samples.")

    # 4. Extract features
    X_train, y_train = extract_features_for_dataset(train_df)
    X_test, y_test = extract_features_for_dataset(test_df)

    # 5. Train Logistic Regression baseline model
    model = LogisticRegression(max_iter=1000, random_state=42, solver="lbfgs")
    model.fit(X_train, y_train)

    # 6. Evaluate model on test set
    if len(X_test) > 0:
        y_test_probs = model.predict_proba(X_test)[:, 1]
        metrics = evaluate_model_performance(y_test, y_test_probs, k=10)
    else:
        metrics = {"ndcgAt10": 0.0, "recallAt10": 0.0, "precisionAt10": 0.0}

    # 7. Print evaluation metrics
    model_name = "susume-ranker"
    model_version = "v1"
    print_evaluation_summary(model_name, model_version, metrics, k=10)

    # 8. Save model and metadata artifacts
    models_dir = os.getenv("MODELS_DIR", "models")
    os.makedirs(models_dir, exist_ok=True)

    model_file_path = os.path.join(models_dir, "susume_ranker_v1.joblib")
    meta_file_path = os.path.join(models_dir, "susume_ranker_v1_meta.json")

    joblib.dump(model, model_file_path)
    logger.info(f"Saved trained model to {model_file_path}")

    metadata = {
        "name": model_name,
        "version": model_version,
        "featureVersion": "v1",
        "trainedAt": datetime.now().isoformat(),
        "ndcgAt10": metrics.get("ndcgAt10"),
        "recallAt10": metrics.get("recallAt10"),
        "precisionAt10": metrics.get("precisionAt10"),
        "trainSamples": len(X_train),
        "testSamples": len(X_test)
    }

    with open(meta_file_path, "w", encoding="utf-8") as f:
        json.dump(metadata, f, indent=2)

    logger.info(f"Saved model metadata to {meta_file_path}")


if __name__ == "__main__":
    train_and_save_model()
