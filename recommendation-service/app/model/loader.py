import os
import json
import logging
import joblib
import numpy as np
from typing import Tuple, Dict, Any, Optional

logger = logging.getLogger("recommendation_service")


class DummyFallbackModel:
    """
    Fallback baseline model used when no trained joblib model file is found on disk.
    Computes a weighted linear combination of candidate strategy scores.
    """
    def __init__(self):
        # Weights for 32 feature vector
        # First 10 strategy scores get heavy weights
        self.weights = np.zeros(32, dtype=np.float32)
        self.weights[0] = 0.20  # contentBased
        self.weights[1] = 0.25  # collaborativeFiltering
        self.weights[2] = 0.10  # frequentlyBoughtTogether
        self.weights[3] = 0.15  # hybrid
        self.weights[4] = 0.20  # personalized
        self.weights[5] = 0.10  # popularity
        self.weights[6] = 0.05  # randomDiscovery
        self.weights[7] = 0.10  # ruleBased
        self.weights[8] = 0.15  # similarItems
        self.weights[9] = 0.15  # trending

    def predict_proba(self, X: np.ndarray) -> np.ndarray:
        if X.shape[0] == 0:
            return np.empty((0, 2))
        raw = np.dot(X, self.weights)
        # Sigmoid activation to scale between 0 and 1
        probs = 1.0 / (1.0 + np.exp(-raw))
        return np.column_stack((1.0 - probs, probs))


class ModelLoader:
    def __init__(self, model_path: Optional[str] = None):
        self.model_path = model_path or os.getenv("MODEL_PATH", "models/susume_ranker_v1.joblib")
        self.model = None
        self.metadata = {
            "name": "susume-ranker",
            "version": os.getenv("MODEL_VERSION", "v1-fallback"),
            "featureVersion": "v1",
        }
        self.load_model()

    def load_model(self):
        if os.path.exists(self.model_path):
            try:
                self.model = joblib.load(self.model_path)
                logger.info(f"Successfully loaded trained ML model from {self.model_path}")
                meta_path = self.model_path.replace(".joblib", "_meta.json")
                if os.path.exists(meta_path):
                    with open(meta_path, "r", encoding="utf-8") as f:
                        self.metadata = json.load(f)
            except Exception as e:
                logger.error(f"Failed to load model from {self.model_path}: {e}. Initializing dummy fallback model.")
                self.model = DummyFallbackModel()
        else:
            logger.warning(f"No model artifact found at {self.model_path}. Initializing baseline heuristic ranker.")
            self.model = DummyFallbackModel()

    def get_model(self) -> Tuple[Any, Dict[str, Any]]:
        if self.model is None:
            self.load_model()
        return self.model, self.metadata
