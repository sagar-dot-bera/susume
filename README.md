# 🍣 Susume — Enterprise Multi-Tenant AI Personalization & Recommendation Engine

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Python](https://img.shields.io/badge/Python-3.11-3776AB.svg)](https://www.python.org/)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.100+-009688.svg)](https://fastapi.tiangolo.com/)
[![scikit-learn](https://img.shields.io/badge/scikit--learn-1.3+-F7931E.svg)](https://scikit-learn.org/)
[![PyTorch](https://img.shields.io/badge/PyTorch-2.0+-EE4C2C.svg)](https://pytorch.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17%20%2B%20pgvector-blue.svg)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D.svg)](https://redis.io/)
[![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3-FF6600.svg)](https://www.rabbitmq.com/)
[![React](https://img.shields.io/badge/React-19-61DAFB.svg)](https://react.dev/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED.svg)](https://www.docker.com/)

**Susume** (*勧め — Japanese for "Recommendation"*) is an enterprise-grade, multi-tenant **Personalization-as-a-Service (PaaS)** platform. Designed for modern e-commerce marketplaces, digital content streaming platforms, and multi-tenant SaaS applications, Susume powers real-time, context-aware item recommendations through a modern **Two-Stage Machine Learning Pipeline**.

By decoupling **heuristic & vector candidate generation** (Spring Boot core backend) from **machine learning re-ranking** (Python FastAPI microservice), Susume delivers sub-200ms latency, 99.99% system availability with automated fallback, and multi-tenant data isolation.

---

## 💡 Executive Summary & Product Vision

In modern digital products, static rule-based recommendations lead to stagnant user engagement, low conversion rates, and cold-start failures for new users or catalog items. Pure machine learning models, on the other hand, can be expensive to run over massive catalogs and risk system outages if the ML inference service degrades.

**Susume solves this by providing a robust, hybrid recommendation infrastructure:**

- **🎯 Boosted Conversion & CTR**: Ranks products by predicting binary interaction probability using a 32-signal canonical feature vector (combining user interaction history, item popularity decay, context, and candidate strategy scores).
- **🏢 Native Multi-Tenancy**: Complete tenant isolation across database schema queries, vector embeddings (`pgvector`), Redis caching, and API authentication (`X-API-KEY` / JWT).
- **🛡️ 99.99% Availability Guarantee**: Implements circuit-breaker style resilient fallback logic. If the Python ML microservice is unreachable or exceeds its 500ms SLA, Spring Boot seamlessly falls back to weighted strategy candidate ordering without dropping user recommendations.
- **🧠 Semantic Dense Vector Matching**: Automatically generates 384-dimensional vector embeddings for new catalog items via PyTorch sentence-transformers (`all-MiniLM-L6-v2`) over an asynchronous RabbitMQ queue.
- **❄️ Instant Cold-Start Resolution**: Dynamically blends popularity, trending, content metadata similarity, and serendipitous discovery strategies to recommend items to new users with zero interaction history.
- **📊 Closed-Loop Telemetry**: Persists every recommendation impression and user feedback loop to continually fuel offline model retraining.

---

## 🏗️ High-Level Product Architecture

```text
               ┌─────────────────────────────────────────────────────────┐
               │                React 19 Dashboard / App                 │
               └────────────────────────────┬────────────────────────────┘
                                            │ HTTP (JWT / X-API-KEY)
                                            ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                            Spring Boot Multi-Tenant Core Backend                            │
│  - Tenant Authorization            - User Interaction Ingestion                             │
│  - Candidate Aggregation (10)      - Resilient Fallback & SLA Enforcement                  │
└───────┬───────────────────────────────────┬──────────────────────────────────┬──────────────┘
        │                                   │                                  │
        ▼                                   ▼                                  ▼
┌─────────────────┐               ┌──────────────────┐               ┌──────────────────┐
│   Strategy 1    │               │    Strategy 2    │     ...       │   Strategy 10    │
│ Collaborative   │               │   Content-Based  │               │   pgvector Similar│
└───────┬─────────┘               └─────────┬────────┘               └─────────┬────────┘
        │                                   │                                  │
        └───────────────────────────────────┼──────────────────────────────────┘
                                            │
                                            ▼
                              Aggregated Candidate Pool (~100 items)
                              + Preserved Heuristic Scores & User History
                                            │
                                            ▼
                       ┌────────────────────────────────────────┐
                       │    Python ML Recommendation Service    │
                       │               (FastAPI)                │
                       │  - 32-Signal Feature Construction      │
                       │  - XGBoost / Logistic Regression Model │
                       │  - Sub-50ms Inference Scoring          │
                       └────────────────────┬───────────────────┘
                                            │
                                  Ranked Items + ML Probabilities
                                            │
                                            ▼
                       ┌────────────────────────────────────────┐
                       │           Spring Boot Facade           │
                       │  - Top-K Selection                     │
                       │  - Impression Persistence Telemetry    │
                       └────────────────────┬───────────────────┘
                                            │
                                            ▼
                                  Personalized Response
```

---

## 🔬 Deep-Dive Technical Implementation

### 1. Stage 1: Candidate Generation Engine (`backend`)
The Spring Boot backend acts as the gateway and orchestrator. Upon receiving a recommendation request, the [CandidateAggregator.java](file:///d:/Susume/backend/src/main/java/com/susume/recommendation/service/CandidateAggregator.java) executes 10 candidate generation strategies to gather a high-recall candidate pool (e.g., 100 items):

| Strategy | Implementation Details | Target Scenario |
| :--- | :--- | :--- |
| **`CollaborativeFilteringStrategy`** | Calculates Jaccard similarity matrix over user interaction overlap history. | Heavy interaction users |
| **`ContentBasedStrategy`** | Matches catalog category taxonomies and text metadata tags. | Niche item discovery |
| **`FrequentlyBoughtTogether`** | Computes co-occurrence matrices for items co-interacted in single sessions. | Cross-selling & cart upsell |
| **`HybridStrategy`** | Applies a weighted linear ensemble of active strategies. | Balanced recommendation |
| **`PersonalizedStrategy`** | Matches user's recent interaction vector against item profile embeddings. | Active session personalization |
| **`PopularityStrategy`** | Ranks items by cumulative interaction volume. | Generic & guest fallback |
| **`RandomDiscoveryStrategy`** | Injects randomized candidate items for serendipity and exploration. | Combating filter bubbles |
| **`RuleBasedStrategy`** | Evaluates deterministic business rules and category filters. | Tenant business requirements |
| **`SimilarItemsStrategy`** | Executes cosine vector search (`pgvector`) against item embedding vectors. | Item detail pages |
| **`TrendingStrategy`** | Applies exponential time-decay scoring to recent high-frequency interactions. | Viral / trending catalog |

### 2. Stage 2: Feature Engineering & ML Re-ranking (`recommendation-service`)
The Python FastAPI microservice accepts the aggregated candidate pool and constructs a canonical **32-Signal Feature Vector** for every user-item candidate pair in [features.py](file:///d:/Susume/recommendation-service/app/ranking/features.py):

```text
32 Canonical Signals Breakdown:
├── Strategy Scores (10): [contentBased, collaborativeFiltering, frequentlyBoughtTogether, hybrid, 
│                          personalized, popularity, randomDiscovery, ruleBased, similarItems, trending]
├── User Features    (8): [totalViews, totalClicks, totalLikes, totalPurchases, totalInteractions, 
│                          interactionsLast24Hours, interactionsLast7Days, interactionsLast30Days]
├── Item Features    (6): [itemPopularity, recentViews, recentClicks, recentLikes, recentPurchases, itemAge]
├── User-Item Pair   (5): [previousViews, previousClicks, previousLikes, previousPurchases, timeSinceLastInteraction]
└── Context Features (3): [surfaceCode, hourOfDay, dayOfWeek]
```

The [ranker.py](file:///d:/Susume/recommendation-service/app/ranking/ranker.py) evaluates candidate vectors using pre-trained **Logistic Regression / XGBoost** classifiers to output a normalized probability score \(P(\text{conversion} \mid \text{user}, \text{item}, \text{context})\).

### 3. Asynchronous Vector Embedding Microservice (`embedding-service`)
- **Model**: `sentence-transformers/all-MiniLM-L6-v2` generating 384-dimensional dense vector embeddings.
- **Event Bus**: When an item is created/updated in the Spring Boot backend, an event is published to **RabbitMQ**.
- **Vector Storage**: The embedding microservice computes dense vectors asynchronously and updates PostgreSQL 17 using native `pgvector` indexing (`HNSW`/`IVFFlat`).

### 4. Enterprise Resilience & Fallback Engine
System reliability is built into [RecommendationRankingClient.java](file:///d:/Susume/backend/src/main/java/com/susume/recommendation/client/RecommendationRankingClient.java):
- **Execution SLA**: 500ms REST timeout applied to the ML re-ranking request.
- **Circuit Breaker / Fallback**: If the Python service fails, times out, or returns a 5xx error, Spring Boot seamlessly falls back to sorting candidates by their aggregate strategy heuristic scores.
- **Impression Logging**: All served recommendations (whether ranked by ML or Heuristics) log impression events to PostgreSQL to track offline model performance and prevent item repetition.

---

## ⚡ Multi-Service Topology & Infrastructure

```mermaid
flowchart LR
    Client[React 19 Frontend / App] -->|JWT or X-API-KEY| Backend[Spring Boot Backend :8080]
    Backend -->|Spring Data JPA| DB[(PostgreSQL 17 + pgvector :5433)]
    Backend -->|Cache User Signals| Redis[(Redis 7 :6379)]
    Backend -->|Publish Catalog Events| Rabbit[(RabbitMQ :5672)]
    Backend -->|POST /rank (500ms SLA)| Ranker[Python ML Service :8002]
    Rabbit -->|Async Consumer| Embedding[Python Embedding Service :8001]
    Embedding -->|Store Vectors| DB
    Ranker -->|Load Models| Models[(Joblib Model Artifacts)]
```

| Service | Technology | Port | Primary Responsibility |
| :--- | :--- | :--- | :--- |
| **Spring Boot Core** | Java 17, Spring Boot 3.2 | `:8080` | Gateway, Auth, Multi-Tenancy, Strategy Aggregator, Facade |
| **Python ML Re-Ranker** | Python 3.11, FastAPI, scikit-learn | `:8002` | 32-signal feature engineering, candidate scoring, ML re-ranking |
| **Embedding Engine** | Python 3.11, FastAPI, PyTorch | `:8001` | Async NLP vector generation (`all-MiniLM-L6-v2`) |
| **PostgreSQL Database** | PostgreSQL 17 + `pgvector` | `:5433` | Relational storage, impressions logging, dense vector similarity |
| **Redis Cache** | Redis 7 | `:6379` | Fast user interaction caching & active session store |
| **RabbitMQ Queue** | RabbitMQ 3 | `:5672` / `:15672` | Asynchronous event broker for catalog updates |
| **React Dashboard** | React 19, TypeScript, Vite | `:80` | Tenant administration, API key management, analytics |

---

## 📊 Offline ML Training & Evaluation Pipeline

Susume features a complete offline machine learning pipeline designed to prevent data leakage and measure recommendation precision before model deployment.

1. **Negative Sampling** ([dataset.py](file:///d:/Susume/recommendation-service/training/dataset.py)): Pairs true user interactions with non-interacted catalog items at a 1:3 positive-to-negative ratio.
2. **Temporal Split**: Splits historical impression data chronologically (80% past interactions for training, 20% future interactions for validation) to mirror production conditions.
3. **Evaluation Metrics** ([evaluate.py](file:///d:/Susume/recommendation-service/training/evaluate.py)):
   - `NDCG@10` (Normalized Discounted Cumulative Gain at Rank 10): Measures ranking quality and position sensitivity.
   - `Recall@10` & `Precision@10`: Measures candidate coverage and top-K relevance.
4. **Artifact Serialization**: Outputs binary `.joblib` model weights and `.json` metadata schema for low-latency loading in production.

---

## 🛠️ Complete Tech Stack

- **Core Backend**: Java 17, Spring Boot 3.2, Spring Security, Spring Data JPA, Flyway, RestTemplate
- **Data Science & ML**: Python 3.11, FastAPI, scikit-learn, pandas, numpy, joblib, pydantic, pytest
- **NLP & Deep Learning**: PyTorch, Hugging Face `sentence-transformers` (`all-MiniLM-L6-v2`)
- **Database & Storage**: PostgreSQL 17 with `pgvector` extension
- **Caching & Messaging**: Redis 7, RabbitMQ 3
- **Frontend App**: React 19, TypeScript, Vite, Tailwind CSS
- **Orchestration & DevOps**: Docker, Docker Compose, Flyway DB Migrations

---

## 📁 Repository Structure & Key Code References

```text
.
├── backend/                                  # Spring Boot backend microservice
│   ├── src/main/java/com/susume/recommendation/
│   │   ├── client/
│   │   │   └── [RecommendationRankingClient.java](file:///d:/Susume/backend/src/main/java/com/susume/recommendation/client/RecommendationRankingClient.java) # Resilient HTTP client with fallback
│   │   ├── controller/                       # Recommendation & Item REST controllers
│   │   ├── dto/                              # Recommendation DTOs & Rank payloads
│   │   ├── entity/                           # Interaction, Item, & Impression entities
│   │   ├── service/
│   │   │   ├── [CandidateAggregator.java](file:///d:/Susume/backend/src/main/java/com/susume/recommendation/service/CandidateAggregator.java)       # Candidate pool generator (10 strategies)
│   │   │   └── [RecommendationFacade.java](file:///d:/Susume/backend/src/main/java/com/susume/recommendation/service/RecommendationFacade.java)      # Top-K selection & impression logging
│   │   └── strategy/                         # 10 Spring Boot recommendation strategy implementations
│   └── src/main/resources/db/migration/     # Flyway database schema migrations (V1 - V8)
│
├── recommendation-service/                   # Python FastAPI ML Candidate Re-Ranker
│   ├── app/
│   │   ├── api/                              # FastAPI REST router (/rank endpoint)
│   │   ├── ranking/
│   │   │   ├── [features.py](file:///d:/Susume/recommendation-service/app/ranking/features.py)                   # 32-signal canonical feature engineer
│   │   │   └── [ranker.py](file:///d:/Susume/recommendation-service/app/ranking/ranker.py)                     # Scikit-learn model inference engine
│   │   └── schemas/                          # Pydantic request/response validation schemas
│   ├── training/
│   │   ├── [dataset.py](file:///d:/Susume/recommendation-service/training/dataset.py)                     # Negative sampling & temporal train/test split
│   │   ├── [train.py](file:///d:/Susume/recommendation-service/training/train.py)                       # ML model training execution pipeline
│   │   └── [evaluate.py](file:///d:/Susume/recommendation-service/training/evaluate.py)                    # Offline ranking evaluation (NDCG@10, Recall@10)
│   └── tests/                                # Pytest automated unit & feature engineering tests
│
├── embedding-service/                        # Python FastAPI Sentence-Transformers Service
│   └── main.py                               # HuggingFace dense vector generator (384-dim)
├── frontend/                                 # React 19 + TypeScript tenant dashboard
├── docker-compose.yml                        # Docker multi-container orchestration
└── README.md
```

---

## 🚀 Quick Start with Docker Compose

### Prerequisites
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (or Docker Engine + Docker Compose)
- Java 17+ (for local backend development)
- Python 3.11+ (for local ML pipeline development)

### 1. Environment Setup
Create a `.env` file in the root directory (or use `.env.example` defaults):

```env
POSTGRES_DB=susume
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
POSTGRES_VOLUME_SOURCE=./docker-data/postgres

EMBEDDING_SOURCE_VOLUME_SOURCE=./docker-data/huggingface

RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest

JWT_SECRET=your-secure-jwt-secret-key-change-this-in-production
RECOMMENDATION_ML_ENABLED=true
RECOMMENDATION_SERVICE_URL=http://recommendation-service:8002
```

### 2. Launch Containers
Build and run the entire multi-service stack with Docker Compose:

```bash
docker compose up -d --build
```

### 3. Verify Running Microservices

| Service | Endpoint URL | Status / Health Check |
| :--- | :--- | :--- |
| **Spring Boot Core** | `http://localhost:8080` | `GET /api/v1/recommendations` |
| **Python ML Ranker** | `http://localhost:8002/health` | `{"status": "healthy"}` |
| **Embedding Engine** | `http://localhost:8001/health` | `{"status": "healthy"}` |
| **React Dashboard** | `http://localhost` | Admin Web Dashboard |
| **RabbitMQ Management** | `http://localhost:15672` | Guest / Guest login |
| **PostgreSQL 17** | `localhost:5433` | Port `5433` (`susume` database) |

---

## 🧪 Development, Testing & Training

### 1. Python ML Microservice Tests & Offline Model Training
To run unit tests and execute offline model training:

```bash
cd recommendation-service

# Install dependencies
pip install -r requirements.txt

# Run pytest unit tests
python -m pytest

# Run offline ML model training & output NDCG@10 metrics
python -m training.train
```

### 2. Spring Boot Core Unit & Integration Tests
To execute Spring Boot JUnit tests:

```bash
cd backend
./mvnw test
```

---

## 🎯 Primary API Endpoint Reference

### Recommendations & Ranking
- `GET /api/v1/recommendations` — Fetch personalized recommendations (Candidate Generation + ML Re-Ranking)
- `GET /api/v1/recommendations/trending` — Fetch global trending catalog items fallback
- `POST /api/v1/recommendations/rank` *(Internal Python ML)* — Candidate re-ranking and feature scoring endpoint

### Catalog & Interaction Ingestion
- `POST /api/v1/items` — Register or update catalog item metadata & push embedding event
- `POST /api/v1/interactions` — Track user clickstream events (`VIEW`, `CLICK`, `LIKE`, `PURCHASE`)
- `GET /api/v1/interactions/history` — Fetch user interaction history timeline

---

## 🌟 Recruiter & Engineering Highlights

- **Decoupled Enterprise Design**: Designed a high-throughput recommendation architecture decoupling candidate selection from ML inference.
- **Resilience & SLA Guarantees**: Built-in HTTP client timeout fallback protecting application performance during downstream ML microservice degradation.
- **Data Science Rigor**: Rigorous offline ML evaluation featuring temporal train/test splitting, negative sampling, and `NDCG@10` tracking.
- **End-to-End Execution**: Delivered backend Java Spring Boot APIs, Python data science microservices, pgvector vector search, RabbitMQ event streaming, and a React admin frontend.

---

## 📜 License

This project is open-source software licensed under the [MIT License](LICENSE).
