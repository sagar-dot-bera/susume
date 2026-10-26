# How Susume's Recommendation Model Works (This Project, Explained Simply)

> **Out of date.** This page predates the LightGBM ranker and the shared feature pipeline. For how features are computed and served today, see [ml-ranking-serving.md](ml-ranking-serving.md).

This document explains **exactly what happens in this codebase** — not a generic textbook model — when a user asks for recommendations, and how the ranking model behind it is trained. Technical terms are explained in plain language as they come up.

The system has two halves that talk to each other over HTTP:

- **`backend/`** — a Java Spring Boot service. It knows about users, items, and interactions (views, clicks, likes, purchases). It generates *candidate* items using 10 simple strategies.
- **`recommendation-service/`** — a Python FastAPI service. It takes those candidates and **re-scores/reorders them** using a trained machine-learning model. This is the "ML ranker."

They're separate because the Java side is good at querying the database, and the Python side is where the data-science/ML tooling (scikit-learn, pandas, numpy) lives.

---

## 1. The big picture (end-to-end flow)

```
 User requests recommendations
          │
          ▼
 RecommendationFacade.executeRecommendation()   (backend, Java)
          │
          │ 1. Run the requested strategy (e.g. "trending") to get a baseline list
          │
          ▼
 CandidateAggregator.aggregateCandidates()       (backend, Java)
          │
          │ 2. Runs ALL 10 strategies, not just one, and merges their outputs
          │    into one big candidate pool (each item gets a score PER strategy)
          │ 3. Attaches extra features per candidate: item age, user's past
          │    interactions with that specific item, etc.
          │
          ▼
 RecommendationRankingClient  ──POST /api/v1/recommendations/rank──▶  FastAPI (Python)
          │                                                                │
          │                                                                ▼
          │                                                     MLRanker.rank()
          │                                                                │
          │                                                                │ 4. Turns each candidate
          │                                                                │    into a 32-number
          │                                                                │    "feature vector"
          │                                                                │ 5. Feeds vectors into the
          │                                                                │    trained model
          │                                                                │ 6. Model outputs a
          │                                                                │    relevance probability
          │                                                                │    per item
          │                                                                │ 7. Sort items by that
          │                                                                │    probability
          │◀──────────────── ranked itemId + score list ─────────────────┘
          │
          ▼
 finalRecommendations returned to the user, impressions logged for future training
```

If the Python service is down, times out, or errors, the backend just **falls back to the original single-strategy list** — the ML ranker is an enhancement layer, not a hard dependency (see `RecommendationRankingClient.rankCandidates`, which catches exceptions and returns `Optional.empty()`).

---

## 2. Stage 1 (Java backend): generating candidates with 10 strategies

Before any ML happens, the backend needs a *pool of candidate items* to choose from. It gets this by running **10 independent, hand-written strategies** (`backend/.../strategy/*.java`), each implementing a different, simple idea of "what's a good recommendation":

| Strategy | Plain-English idea |
|---|---|
| `contentBased` | Recommend items similar in *content/description* to what the user liked before |
| `collaborativeFiltering` | Recommend items that *similar users* interacted with ("people like you also liked...") |
| `frequentlyBoughtTogether` | Items commonly purchased alongside items the user already has |
| `hybrid` | A blend of multiple simpler strategies |
| `personalized` | Tailored using the specific user's own interaction history |
| `popularity` | Just recommend generally popular items (good fallback, no personalization) |
| `randomDiscovery` | Inject some randomness so users don't get stuck seeing the same items forever |
| `ruleBased` | Hard-coded business rules (e.g. category, tenant-specific logic) |
| `similarItems` | Items similar to one specific item (e.g. "you're viewing X, here's similar Y") |
| `trending` | Items with a recent spike in interactions |

**`CandidateAggregator`** runs *all 10* of these for every recommendation request, and merges the results into one list. If three strategies all suggest the same item, that item ends up with three different scores attached — one per strategy (see `candidateScores` map in `CandidateAggregator.java:68`). Any strategy that didn't suggest a given item just gets a score of `0.0` for that strategy.

This step is deliberately **not intelligent** — it's cheap and just casts a wide net (this is the *retrieval* half of a retrieve-then-rank system: recall over precision, get a reasonably-sized pool of plausible items cheaply).

### Extra context attached per candidate
For every candidate item, the backend also attaches:
- **Item features**: how old the item is (`itemAge`), its popularity, recent view/click/like/purchase counts.
- **User-item features**: has *this specific user* viewed/clicked/liked/purchased *this specific item* before, and how long ago (`timeSinceLastInteraction`).
- **User features** (`buildUserFeatures`): the user's overall totals — total views, clicks, likes, purchases, and how active they've been in the last 24 hours / 7 days / 30 days.
- **Context**: what surface they're on (homepage, product page, cart, search, category), the current hour, and day of week.

All of this gets packaged into a `RankRequest` and POSTed to the Python service.

---

## 3. Stage 2 (Python service): turning candidates into numbers ("feature engineering")

A machine-learning model can't read words like `"homepage"` or objects like "a candidate item" — it can only do math on numbers. So the first job of the Python side (`app/ranking/features.py`) is converting each candidate into a fixed-length list of **32 numbers**, called a **feature vector**. Think of it as a spreadsheet row with 32 columns, always in the same order, one row per candidate item:

| Group | Count | Examples |
|---|---|---|
| Strategy scores | 10 | `contentBasedScore`, `collaborativeFilteringScore`, `popularityScore`, `trendingScore`, ... |
| User features | 8 | `user_totalViews`, `user_totalPurchases`, `user_interactionsLast7Days`, ... |
| Item features | 6 | `item_itemPopularity`, `item_recentClicks`, `item_itemAge`, ... |
| User-item features | 5 | `user_item_previousViews`, `user_item_timeSinceLastInteraction`, ... |
| Context features | 3 | `context_surfaceCode` (homepage=1, product_detail=2, cart=3, search=4, category=5), `context_hour`, `context_dayOfWeek` |

**Why this matters:** the exact same feature-building code (`build_feature_vector`) is used both at **training time** and **serving time** (the training code literally imports it from the app package — see `training/features.py:4`). This is important because if training and serving computed features differently even slightly, the model would learn patterns that don't match what it sees in production — a very common real-world ML bug called **training/serving skew**. This project avoids that by sharing one function for both.

Missing values default to `0` (or `-1` for "time since last interaction" meaning "never interacted") so the vector is always fully populated even for a brand-new user or item — this is called **cold start**: having little or no history for a user/item.

---

## 4. How the model is trained (`training/train.py`)

Training happens **offline**, as a separate script (`python -m training.train`), not live in the API. Here's the pipeline step by step:

### Step 1 — Load historical interaction data
`training/dataset.py` calls the backend's `/api/v1/interactions/export` endpoint (paginated, needs an API key) to pull real historical interaction records: which user did what (VIEW/CLICK/LIKE/PURCHASE) on which item, and when. If the backend isn't reachable or no API key is configured, it **generates synthetic fake data instead** (50 fake users, 100 fake items, 500 fake interactions) — a deliberate bootstrap fallback so the pipeline always runs, even before real production data exists.

### Step 2 — Negative sampling
Real interaction logs only tell you what a user **did** do — they never explicitly say "user X does NOT like item Y." But a classifier needs both positive and negative examples to learn a boundary. So `training/negative_sampling.py` does this:
- Every real interaction (e.g. "user-003 clicked item-042") becomes a **positive example**, labeled `1`.
- For each positive, it randomly picks a few items that user *never* interacted with, and labels those `0` — **negative examples**, standing in for "probably not relevant."
- The ratio is 1 positive to 3 negatives (`negative_ratio=3`), so the model sees more "not relevant" examples than "relevant" ones, which mirrors reality (most items are irrelevant to any given user).

This turns the problem into: **given a (user, item) pair, predict whether it's a real positive interaction (1) or not (0).**

### Step 3 — Temporal train/test split
Instead of randomly shuffling and splitting 80/20, the samples are sorted **by timestamp** first, and the first 80% (older) becomes the **training set**, the last 20% (newer) becomes the **test set** (`train.py:31`). This is called a **temporal split**, and it matters because it simulates the real deployment condition: you always train on the past and predict the future, never the reverse. A random shuffle-split can leak future information into training and make offline metrics look better than the model will actually perform in production.

### Step 4 — Extract features
Each `(user_id, item_id, label)` training row is converted into a 32-number feature vector using the *exact same* `build_feature_vector` function described above (via `training/features.py`), so training sees the same "shape" of data the live API will see.

> Current caveat visible in the code: because real strategy scores/features aren't recomputed historically for training rows, `training/features.py` currently *simulates* plausible strategy scores from the label itself (with some random noise) rather than pulling true historical strategy outputs. This is a reasonable placeholder for bootstrapping the pipeline, but means the model is currently learning from semi-synthetic signal until this is replaced with real point-in-time feature computation.

### Step 5 — Train a Logistic Regression model
```python
model = LogisticRegression(max_iter=1000, random_state=42, solver="lbfgs")
model.fit(X_train, y_train)
```
**What Logistic Regression is, in plain words:** it's one of the simplest, most interpretable machine-learning models. It learns one weight (a number) per input feature, multiplies each feature by its weight, adds them up, and squashes the result into a probability between 0 and 1 using a **sigmoid function** (an S-shaped curve). Conceptually:

```
score = (w1 × contentBasedScore) + (w2 × collaborativeFilteringScore) + ... + (w32 × dayOfWeek) + bias
probability = sigmoid(score)   # squashes any number into a 0–1 range
```

During training, it automatically adjusts the 32 weights so that the predicted probability is high for real positive interactions and low for the sampled negatives, across all training examples. It's the same math family as a single-layer neural network with no hidden layers — simple, fast to train, cheap to run at inference time, and easy to inspect (you can literally look at which features got the highest weights to understand what the model "cares about" most).

`random_state=42` just means "use a fixed random seed" so re-running training gives reproducible results. `solver="lbfgs"` is the numerical optimization algorithm scikit-learn uses to find the best weights (an implementation detail; not something you need to tune here).

### Step 6 — Evaluate on the held-out test set
`training/evaluate.py` doesn't just check plain accuracy — it uses **ranking metrics**, because what actually matters is whether the *best* items end up near the *top* of the list, not just whether each individual prediction is right:

- **NDCG@10** (Normalized Discounted Cumulative Gain, top 10): rewards the model more for putting relevant items at rank 1–2 than at rank 9–10. A perfect ranking scores 1.0.
- **Recall@10**: out of all the truly relevant items that exist, what fraction did the top 10 predictions catch?
- **Precision@10**: out of the top 10 predictions shown, what fraction were actually relevant?

These three numbers together tell you not just "is the model right sometimes" but "does it put the right items at the top, where users will actually see them."

### Step 7 — Save the model to disk
```python
joblib.dump(model, "models/susume_ranker_v1.joblib")
```
`joblib` is a Python library for saving trained scikit-learn models to a file (similar idea to zipping up the model's learned weights so they can be loaded later without retraining). A companion `susume_ranker_v1_meta.json` file is saved alongside it, recording the model version, when it was trained, and its NDCG/Recall/Precision scores — so you always know how good the currently-deployed model is supposed to be.

---

## 5. How the trained model is used at request time (`app/model/loader.py`, `app/ranking/ranker.py`)

1. On startup, `ModelLoader` looks for `models/susume_ranker_v1.joblib` on disk.
   - **If found:** loads it with `joblib.load(...)` — this is the real trained Logistic Regression model.
   - **If not found:** falls back to `DummyFallbackModel` — a hand-coded backup that just does a weighted sum of the 10 strategy scores (heavier weight on `collaborativeFiltering` and `contentBased`) passed through a sigmoid, so the service still returns *something* sensible even before any real training has happened.
2. `MLRanker.rank()` builds the 32-column feature matrix for all incoming candidates (`build_candidate_matrix`), calls `model.predict_proba(X)` to get a relevance probability per item, and sorts candidates by that probability, highest first.
3. `apply_diversity_rerank()` currently just truncates to the requested `limit` (top-K) — this is a placeholder hook for future logic like "don't show 5 items from the same category in a row," which is a common real-world reranking concern but isn't implemented yet.
4. The ranked `(itemId, score)` list is sent back to the Java backend, which maps item IDs back to full item metadata and returns the final response to the user — and separately logs an **impression** (which items were shown, in what order, with what model version) so that future interactions on those impressions can become training data for the next model version.

---

## 6. Key terms used above, quick glossary

| Term | Plain meaning |
|---|---|
| **Candidate** | An item that *might* be recommended; not yet scored/ranked by the ML model |
| **Feature vector** | A fixed-length list of numbers describing one candidate, ready for a model to consume |
| **Feature engineering** | The work of deciding what those numbers should be and computing them |
| **Positive / negative sample** | Positive = a real interaction happened (label 1); negative = assumed non-relevant, since it didn't happen (label 0) |
| **Negative sampling** | Randomly picking a handful of "probably irrelevant" items per positive, since you can't train on positives alone |
| **Temporal split** | Splitting train/test data by time (old = train, new = test) instead of randomly, to avoid peeking into the future |
| **Logistic Regression** | A simple ML model that learns one weight per feature and outputs a 0–1 probability via a sigmoid |
| **Sigmoid function** | A math function that squashes any real number into the range 0 to 1, used to turn a raw score into a "probability" |
| **NDCG / Recall / Precision @ k** | Ranking-quality metrics that check whether the *most relevant* items land in the *top k* results, not just whether predictions are individually correct |
| **Cold start** | The problem of having little/no data for a new user or new item; handled here with default `0`/`-1` feature values |
| **Training/serving skew** | A bug where the features used during training don't exactly match how they're computed live — avoided here by sharing the same `build_feature_vector` function in both places |
| **joblib** | A Python tool used to save/load trained scikit-learn models to/from disk |
| **Fallback model** | A simple hard-coded backup model used when no trained model file is available, so the service never has zero output |

---

## 7. Honest limitations of the current setup (as implemented today)

- The **negative sampling** is uniform random — it doesn't do "hard negative mining" (picking negatives that look deceptively similar to positives), which would likely make the model sharper.
- The **feature extraction for training** currently simulates strategy scores from the label with added noise rather than replaying true historical strategy outputs at that point in time — a known placeholder, not a bug, but worth knowing before trusting the model's real-world accuracy.
- **Diversity re-ranking** is a no-op today (`apply_diversity_rerank` just top-K truncates) — there's no logic yet to avoid showing near-duplicate or same-category items back-to-back.
- The model is a **single global Logistic Regression** — it isn't per-tenant or deep-learning based; it's intentionally simple, fast, and interpretable, which is a reasonable v1 choice, with room to grow into more advanced techniques (e.g. gradient-boosted trees, pairwise/listwise learning-to-rank, or neural rankers) as more real interaction data accumulates.
